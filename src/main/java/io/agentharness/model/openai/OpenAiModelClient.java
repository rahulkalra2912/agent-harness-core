package io.agentharness.model.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseUsage;
import io.agentharness.core.ContextItem;
import io.agentharness.model.ModelClient;
import io.agentharness.model.ModelConfig;
import io.agentharness.model.ModelRequest;
import io.agentharness.model.ModelResponse;
import io.agentharness.model.Usage;
import io.agentharness.tool.ToolCall;
import io.agentharness.tool.ToolDefinition;
import io.agentharness.tool.ToolExecution;
import io.agentharness.tool.ToolId;
import io.agentharness.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI Responses API adapter for the provider-neutral {@link ModelClient} contract.
 *
 * <p>All OpenAI SDK types are contained in this adapter. Harness Core only sees generic model,
 * context, tool, and usage types, so another provider can be added without changing the runtime.</p>
 */
public final class OpenAiModelClient implements ModelClient {
    private final OpenAIClient client;
    private final ObjectMapper mapper;

    public OpenAiModelClient(OpenAIClient client, ObjectMapper mapper) {
        if (client == null) throw new IllegalArgumentException("OpenAI client is required");
        if (mapper == null) throw new IllegalArgumentException("ObjectMapper is required");
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public ModelResponse call(ModelConfig model, ModelRequest request) {
        List<ResponseInputItem> inputs = toOpenAiInputs(request);

        ResponseCreateParams.Builder params = ResponseCreateParams.builder()
                .model(model.providerModelName())
                .instructions(request.systemInstructions())
                .parallelToolCalls(true)
                .input(ResponseCreateParams.Input.ofResponse(inputs));

        for (ToolDefinition tool : request.tools()) {
            params.addTool(toOpenAiTool(tool));
        }

        Response response = client.responses().create(params.build());
        Usage usage = toUsage(response);

        // A response may contain several function calls in one turn. If any are present, the
        // harness must execute them and return their results before asking the model to continue.
        List<ToolCall> toolCalls = response.output().stream()
                .filter(ResponseOutputItem::isFunctionCall)
                .map(ResponseOutputItem::asFunctionCall)
                .map(this::toToolCall)
                .toList();

        if (!toolCalls.isEmpty()) {
            return new ModelResponse.ToolRequests(toolCalls, usage);
        }

        String finalText = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining("\n"))
                .trim();

        if (finalText.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI response contained neither function calls nor final text");
        }

        return new ModelResponse.FinalAnswer(finalText, usage);
    }

    private List<ResponseInputItem> toOpenAiInputs(ModelRequest request) {
        List<ResponseInputItem> inputs = new ArrayList<>();

        for (ContextItem item : request.context().items()) {
            if (item instanceof ContextItem.UserMessage userMessage) {
                inputs.add(ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                        .role(ResponseInputItem.Message.Role.USER)
                        .addInputTextContent(userMessage.text())
                        .build()));
            } else if (item instanceof ContextItem.ToolRequests toolRequests) {
                for (ToolCall call : toolRequests.calls()) {
                    // Reconstruct the model-issued function call so its call_id can be paired with
                    // the function_call_output supplied immediately afterwards.
                    ResponseFunctionToolCall functionCall = ResponseFunctionToolCall.builder()
                            .callId(call.callId())
                            .name(call.toolId().value())
                            .arguments(writeJson(call.arguments()))
                            .build();
                    inputs.add(ResponseInputItem.ofFunctionCall(functionCall));
                }
            } else if (item instanceof ContextItem.ToolExecutions toolExecutions) {
                for (ToolExecution execution : toolExecutions.executions()) {
                    inputs.add(ResponseInputItem.ofFunctionCallOutput(
                            ResponseInputItem.FunctionCallOutput.builder()
                                    .callId(execution.toolCallId())
                                    .output(serializeToolResult(execution.result()))
                                    .build()));
                }
            }
            // AssistantMessage is only added when a task is complete, after which the harness
            // returns immediately. It therefore does not participate in an in-flight model call.
        }

        return inputs;
    }

    private FunctionTool toOpenAiTool(ToolDefinition tool) {
        FunctionTool.Parameters.Builder parameters = FunctionTool.Parameters.builder();
        if (!tool.inputSchema().isObject()) {
            throw new IllegalArgumentException(
                    "Tool schema must be a JSON object: " + tool.id().value());
        }

        tool.inputSchema().fields().forEachRemaining(entry ->
                parameters.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue())));

        return FunctionTool.builder()
                .name(tool.id().value())
                .description(tool.description())
                .parameters(parameters.build())
                // The demo schemas contain an optional 'service' field. We validate arguments in
                // the tool implementation, so strict schema enforcement is intentionally disabled.
                .strict(false)
                .build();
    }

    private ToolCall toToolCall(ResponseFunctionToolCall functionCall) {
        try {
            JsonNode arguments = mapper.readTree(functionCall.arguments());
            return new ToolCall(
                    functionCall.callId(),
                    new ToolId(functionCall.name()),
                    arguments);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "OpenAI returned invalid function arguments for " + functionCall.name(), e);
        }
    }

    private Usage toUsage(Response response) {
        ResponseUsage usage = response.usage().orElse(null);
        if (usage == null) {
            // Keep the generic usage contract total and deterministic even if a provider response
            // unexpectedly omits usage metadata.
            return new Usage(0, 0);
        }
        return new Usage(usage.inputTokens(), usage.outputTokens());
    }

    private String serializeToolResult(ToolResult result) {
        ObjectNode envelope = mapper.createObjectNode();
        if (result instanceof ToolResult.Success success) {
            envelope.put("status", "success");
            envelope.set("data", success.data());
        } else {
            ToolResult.Failure failure = (ToolResult.Failure) result;
            envelope.put("status", "failure");
            envelope.put("error_code", failure.errorCode());
            envelope.put("message", failure.message());
        }
        return writeJson(envelope);
    }

    private String writeJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSON", e);
        }
    }
}
