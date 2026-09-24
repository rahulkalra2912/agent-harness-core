package io.agentharness.tool;

import java.time.Duration;

public record ToolExecution(
        String toolCallId,
        ToolId toolId,
        ToolResult result,
        Duration duration) {

    public ToolExecution {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId cannot be blank");
        }
        if (toolId == null) throw new IllegalArgumentException("toolId is required");
        if (result == null) throw new IllegalArgumentException("result is required");
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("duration cannot be negative");
        }
    }
}
