package io.agentharness.model;

public interface ModelClient {
    ModelResponse call(ModelConfig model, ModelRequest request);
}
