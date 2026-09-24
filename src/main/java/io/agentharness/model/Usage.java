package io.agentharness.model;

public record Usage(long inputTokens, long outputTokens) {
    public Usage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Token counts cannot be negative");
        }
    }
}
