package io.agentharness.runtime;

import io.agentharness.core.TaskId;
import io.agentharness.trace.ExecutionTrace;

public record TaskResult(
        TaskId taskId,
        TaskStatus status,
        String answer,
        TaskUsage usage,
        ExecutionTrace trace) {}
