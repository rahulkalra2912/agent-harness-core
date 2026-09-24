package io.agentharness.model;

public record ModelConfig(
        ModelId id,
        String providerModelName) {

    public ModelConfig {
        if (id == null) throw new IllegalArgumentException("Model id is required");
        if (providerModelName == null || providerModelName.isBlank()) {
            throw new IllegalArgumentException("providerModelName cannot be blank");
        }
    }
}
