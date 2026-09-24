package io.agentharness.tool;

public record ToolId(String value) {
    public ToolId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ToolId cannot be blank");
        }
    }
}
