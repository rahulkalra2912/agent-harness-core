package io.agentharness.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentharness.core.AgentConfig;
import io.agentharness.core.AgentId;
import io.agentharness.core.ContextItem;
import io.agentharness.core.ExecutionContext;
import io.agentharness.core.ExecutionPolicy;
import io.agentharness.core.Task;
import io.agentharness.core.TaskId;
import io.agentharness.cost.ModelPricing;
import io.agentharness.cost.PricingCatalog;
import io.agentharness.model.ModelClient;
import io.agentharness.model.ModelConfig;
import io.agentharness.model.ModelId;
import io.agentharness.model.ModelResponse;
import io.agentharness.model.ModelRouter;
import io.agentharness.model.RoutingDecision;
import io.agentharness.model.RoutingPolicy;
import io.agentharness.model.Usage;
import io.agentharness.tool.Tool;
import io.agentharness.tool.ToolCall;
import io.agentharness.tool.ToolDefinition;
import io.agentharness.tool.ToolId;
import io.agentharness.tool.ToolRegistry;
import io.agentharness.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessContextTest {
    private static final ToolId LOOKUP = new ToolId("lookup");
    private static final ModelId MODEL = new ModelId("test-model");

    @Test
    void preservesToolRequestAndExecutionBeforeNextModelTurn() {
        ObjectMapper mapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override
            public ToolDefinition definition() {
                return new ToolDefinition(
                        LOOKUP,
                        "Looks something up",
                        mapper.createObjectNode()
                                .put("type", "object")
                                .set("properties", mapper.createObjectNode()));
            }

            @Override
            public ToolResult execute(com.fasterxml.jackson.databind.JsonNode arguments) {
                return new ToolResult.Success(mapper.createObjectNode().put("value", "ok"));
            }
        });

        AtomicInteger calls = new AtomicInteger();
        ModelClient modelClient = (model, request) -> {
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse.ToolRequests(
                        List.of(new ToolCall("call-1", LOOKUP, mapper.createObjectNode())),
                        new Usage(10, 2));
            }

            assertTrue(request.context().items().stream()
                    .anyMatch(item -> item instanceof ContextItem.ToolRequests));
            assertTrue(request.context().items().stream()
                    .anyMatch(item -> item instanceof ContextItem.ToolExecutions));
            return new ModelResponse.FinalAnswer("done", new Usage(12, 3));
        };

        RoutingPolicy routingPolicy = new RoutingPolicy() {
            @Override
            public RoutingDecision select(
                    Task task,
                    ExecutionContext context,
                    Map<ModelId, ModelConfig> availableModels) {
                return new RoutingDecision(availableModels.get(MODEL), "test");
            }

            @Override
            public String description() {
                return "test routing policy";
            }
        };

        ModelConfig modelConfig = new ModelConfig(MODEL, "provider-test-model");
        ModelRouter router = new ModelRouter(Map.of(MODEL, modelConfig));

        PricingCatalog prices = new PricingCatalog(Map.of(
                MODEL, new ModelPricing(BigDecimal.ZERO, BigDecimal.ZERO)));

        Harness harness = new Harness(
                modelClient,
                router,
                registry,
                prices,
                Executors.newFixedThreadPool(2));

        AgentConfig agent = new AgentConfig(
                new AgentId("test-agent"),
                "Use tools when needed.",
                Set.of(LOOKUP),
                routingPolicy);

        TaskResult result = harness.execute(
                agent,
                new Task(new TaskId("task-1"), "look it up"),
                new ExecutionPolicy(5, Duration.ofSeconds(5), BigDecimal.ONE));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("done", result.answer());
        assertEquals(2, result.usage().modelCalls());
        assertEquals(1, result.usage().toolCalls());
    }
}
