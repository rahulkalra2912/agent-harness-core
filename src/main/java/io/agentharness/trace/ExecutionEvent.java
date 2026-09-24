package io.agentharness.trace;

import io.agentharness.model.ModelId;
import io.agentharness.model.Usage;
import io.agentharness.tool.ToolExecution;
import io.agentharness.tool.ToolCall;

import java.math.BigDecimal;
import java.time.Instant;

public sealed interface ExecutionEvent permits
        ExecutionEvent.TaskStarted,
        ExecutionEvent.ModelSelected,
        ExecutionEvent.ModelCallCompleted,
        ExecutionEvent.ToolCallCompleted,
        ExecutionEvent.TaskCompleted,
        ExecutionEvent.TaskFailed {

    Instant at();

    record TaskStarted(Instant at) implements ExecutionEvent {}

    record ModelSelected(Instant at, ModelId modelId, String reason) implements ExecutionEvent {}

    record ModelCallCompleted(
            Instant at,
            ModelId modelId,
            Usage usage,
            BigDecimal cost) implements ExecutionEvent {}

    record ToolCallCompleted(
            Instant at,
            ToolCall call,
            ToolExecution execution) implements ExecutionEvent {}

    record TaskCompleted(Instant at, BigDecimal totalCost) implements ExecutionEvent {}

    record TaskFailed(Instant at, String reason, BigDecimal totalCost) implements ExecutionEvent {}
}
