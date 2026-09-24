package io.agentharness.core;

public record Task(TaskId id, String prompt) {
    public Task {
        if (id == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Task prompt cannot be blank");
        }
    }
}
