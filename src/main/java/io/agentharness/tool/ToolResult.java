package io.agentharness.tool;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface ToolResult permits ToolResult.Success, ToolResult.Failure {

    record Success(JsonNode data) implements ToolResult {
        public Success {
            if (data == null) throw new IllegalArgumentException("Success data is required");
        }
    }

    record Failure(String errorCode, String message) implements ToolResult {
        public Failure {
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("errorCode cannot be blank");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message cannot be blank");
            }
        }
    }
}
