package io.agentharness.runtime;

import io.agentharness.core.AgentConfig;
import io.agentharness.core.ContextItem;
import io.agentharness.core.ExecutionContext;
import io.agentharness.core.ExecutionPolicy;
import io.agentharness.core.Task;
import io.agentharness.cost.CostTracker;
import io.agentharness.cost.PricingCatalog;
import io.agentharness.model.ModelClient;
import io.agentharness.model.ModelConfig;
import io.agentharness.model.ModelRequest;
import io.agentharness.model.ModelResponse;
import io.agentharness.model.ModelRouter;
import io.agentharness.model.RoutingDecision;
import io.agentharness.tool.ToolDefinition;
import io.agentharness.tool.Tool;
import io.agentharness.tool.ToolCall;
import io.agentharness.tool.ToolExecution;
import io.agentharness.tool.ToolRegistry;
import io.agentharness.tool.ToolResult;
import io.agentharness.trace.ExecutionEvent;
import io.agentharness.trace.ExecutionTrace;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public final class Harness {
    private final ModelClient modelClient;
    private final ModelRouter modelRouter;
    private final ToolRegistry toolRegistry;
    private final PricingCatalog pricingCatalog;
    private final Executor toolExecutor;

    public Harness(
            ModelClient modelClient,
            ModelRouter modelRouter,
            ToolRegistry toolRegistry,
            PricingCatalog pricingCatalog,
            Executor toolExecutor) {
        this.modelClient = modelClient;
        this.modelRouter = modelRouter;
        this.toolRegistry = toolRegistry;
        this.pricingCatalog = pricingCatalog;
        this.toolExecutor = toolExecutor;
    }

    public TaskResult execute(AgentConfig agent, Task task, ExecutionPolicy policy) {
        Instant started = Instant.now();
        Instant deadline = started.plus(policy.timeout());
        ExecutionTrace trace = new ExecutionTrace();
        ExecutionContext context = new ExecutionContext();
        CostTracker costTracker = new CostTracker(pricingCatalog);
        int toolCalls = 0;

        trace.add(new ExecutionEvent.TaskStarted(started));
        context.add(new ContextItem.UserMessage(task.prompt()));

        // Tool permissions are fixed for the lifetime of this execution, so resolve the model-visible
        // tool definitions once rather than rebuilding the same list on every agent turn.
        List<ToolDefinition> allowedDefinitions = toolRegistry.definitions().stream()
                .filter(def -> agent.allowedTools().contains(def.id()))
                .toList();

        // V1 routes once per task. Keeping routing outside the agent loop makes that policy explicit
        // and prevents accidental model switching midway through an execution trajectory.
        RoutingDecision routingDecision = modelRouter.route(agent.routingPolicy(), task, context);
        ModelConfig model = routingDecision.model();
        trace.add(new ExecutionEvent.ModelSelected(
                Instant.now(), model.id(), routingDecision.reason()));

        // The harness repeatedly alternates between model reasoning and tool execution until the
        // model produces a final answer or an execution guardrail is reached.
        for (int step = 0; step < policy.maxSteps(); step++) {
            if (Instant.now().isAfter(deadline)) {
                return failed(task, TaskStatus.TIMEOUT, "Execution timeout exceeded",
                        started, toolCalls, costTracker, trace);
            }
            if (costTracker.totalCost().compareTo(policy.maxCost()) > 0) {
                return failed(task, TaskStatus.COST_LIMIT_EXCEEDED, "Cost budget exceeded",
                        started, toolCalls, costTracker, trace);
            }

            ModelRequest request = new ModelRequest(
                    task,
                    context,
                    allowedDefinitions,
                    agent.systemInstructions());

            ModelResponse response;
            try {
                response = modelClient.call(model, request);
            } catch (RuntimeException e) {
                return failed(task, TaskStatus.FAILED, "Model call failed: " + e.getMessage(),
                        started, toolCalls, costTracker, trace);
            }

            BigDecimal callCost = costTracker.record(model.id(), response.usage());
            trace.add(new ExecutionEvent.ModelCallCompleted(
                    Instant.now(), model.id(), response.usage(), callCost));

            if (response instanceof ModelResponse.FinalAnswer finalAnswer) {
                context.add(new ContextItem.AssistantMessage(finalAnswer.text()));
                trace.add(new ExecutionEvent.TaskCompleted(Instant.now(), costTracker.totalCost()));
                return new TaskResult(
                        task.id(),
                        TaskStatus.COMPLETED,
                        finalAnswer.text(),
                        usage(started, toolCalls, costTracker),
                        trace);
            }

            ModelResponse.ToolRequests toolRequests = (ModelResponse.ToolRequests) response;

            // Preserve the model's request before executing it. Provider tool-call protocols expect
            // the original call (including call ID and arguments) to be present when the tool result
            // is supplied on the next model turn.
            context.add(new ContextItem.ToolRequests(toolRequests.calls()));

            // Calls requested in the same model turn are independent from the model's perspective,
            // so execute them concurrently and correlate results by tool-call ID rather than order.
            List<CompletableFuture<ToolExecution>> futures = toolRequests.calls().stream()
                    .map(call -> CompletableFuture.supplyAsync(
                            () -> executeTool(agent, call), toolExecutor))
                    .toList();

            List<ToolExecution> executions = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            toolCalls += executions.size();

            // Successes and failures are both recorded. A single failed tool does not terminate the
            // task; the next model turn can decide whether to retry, degrade gracefully, or stop.
            Map<String, ToolCall> callsById = toolRequests.calls().stream()
                    .collect(Collectors.toMap(
                            ToolCall::callId,
                            call -> call));

            executions.forEach(execution -> {
                ToolCall call = callsById.get(execution.toolCallId());
                trace.add(new ExecutionEvent.ToolCallCompleted(
                        Instant.now(),
                        call,
                        execution));
            });
            context.add(new ContextItem.ToolExecutions(executions));
        }

        return failed(task, TaskStatus.MAX_STEPS_EXCEEDED, "Maximum execution steps exceeded",
                started, toolCalls, costTracker, trace);
    }

    private ToolExecution executeTool(AgentConfig agent, ToolCall call) {
        Instant started = Instant.now();
        ToolResult result;

        if (!agent.allowedTools().contains(call.toolId())) {
            result = new ToolResult.Failure("TOOL_NOT_ALLOWED",
                    "Agent is not allowed to use tool: " + call.toolId().value());
        } else {
            Tool tool = toolRegistry.find(call.toolId()).orElse(null);
            if (tool == null) {
                result = new ToolResult.Failure("TOOL_NOT_FOUND",
                        "Tool is not registered: " + call.toolId().value());
            } else {
                try {
                    result = tool.execute(call.arguments());
                } catch (RuntimeException e) {
                    result = new ToolResult.Failure("TOOL_EXECUTION_FAILED",
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            }
        }

        return new ToolExecution(
                call.callId(),
                call.toolId(),
                result,
                Duration.between(started, Instant.now()));
    }

    private TaskResult failed(
            Task task,
            TaskStatus status,
            String reason,
            Instant started,
            int toolCalls,
            CostTracker costTracker,
            ExecutionTrace trace) {
        trace.add(new ExecutionEvent.TaskFailed(Instant.now(), reason, costTracker.totalCost()));
        return new TaskResult(
                task.id(),
                status,
                null,
                usage(started, toolCalls, costTracker),
                trace);
    }

    private TaskUsage usage(Instant started, int toolCalls, CostTracker costTracker) {
        return new TaskUsage(
                costTracker.modelCalls(),
                toolCalls,
                costTracker.inputTokens(),
                costTracker.outputTokens(),
                costTracker.totalCost(),
                Duration.between(started, Instant.now()));
    }
}
