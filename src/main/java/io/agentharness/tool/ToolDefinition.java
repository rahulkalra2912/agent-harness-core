package io.agentharness.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolDefinition(
        ToolId id,
        String description,
        JsonNode inputSchema) {

    public ToolDefinition {
        if (id == null) throw new IllegalArgumentException("Tool id is required");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description cannot be blank");
        }
        if (inputSchema == null) throw new IllegalArgumentException("Input schema is required");
    }
}
