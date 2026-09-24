package io.agentharness.core;

public record TaskId(String value) {
    public TaskId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskId cannot be blank");
        }
    }
}
