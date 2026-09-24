package io.agentharness.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface Tool {
    ToolDefinition definition();
    ToolResult execute(JsonNode arguments);
}
