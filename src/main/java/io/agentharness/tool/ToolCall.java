package io.agentharness.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolCall(
        String callId,
        ToolId toolId,
        JsonNode arguments) {

    public ToolCall {
        if (callId == null || callId.isBlank()) throw new IllegalArgumentException("callId cannot be blank");
        if (toolId == null) throw new IllegalArgumentException("toolId is required");
        if (arguments == null) throw new IllegalArgumentException("arguments are required");
    }
}
