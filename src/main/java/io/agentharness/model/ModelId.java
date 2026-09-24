package io.agentharness.model;

public record ModelId(String value) {
    public ModelId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ModelId cannot be blank");
        }
    }
}
