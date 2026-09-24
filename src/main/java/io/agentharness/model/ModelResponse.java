package io.agentharness.model;

import io.agentharness.tool.ToolCall;

import java.util.List;

public sealed interface ModelResponse permits ModelResponse.FinalAnswer, ModelResponse.ToolRequests {
    Usage usage();

    record FinalAnswer(String text, Usage usage) implements ModelResponse {
        public FinalAnswer {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("Final answer cannot be blank");
            if (usage == null) throw new IllegalArgumentException("Usage is required");
        }
    }

    record ToolRequests(List<ToolCall> calls, Usage usage) implements ModelResponse {
        public ToolRequests {
            calls = List.copyOf(calls);
            if (calls.isEmpty()) throw new IllegalArgumentException("At least one tool call is required");
            if (usage == null) throw new IllegalArgumentException("Usage is required");
        }
    }
}
