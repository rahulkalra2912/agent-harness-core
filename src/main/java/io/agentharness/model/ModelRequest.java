package io.agentharness.model;

import io.agentharness.core.ExecutionContext;
import io.agentharness.core.Task;
import io.agentharness.tool.ToolDefinition;

import java.util.List;

public record ModelRequest(
        Task task,
        ExecutionContext context,
        List<ToolDefinition> tools,
        String systemInstructions) {

    public ModelRequest {
        if (task == null) throw new IllegalArgumentException("Task is required");
        if (context == null) throw new IllegalArgumentException("Execution context is required");
        tools = List.copyOf(tools);
        if (systemInstructions == null || systemInstructions.isBlank()) {
            throw new IllegalArgumentException("System instructions cannot be blank");
        }
    }
}
