package io.agentharness.core;

public record AgentId(String value) {
    public AgentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AgentId cannot be blank");
        }
    }
}
