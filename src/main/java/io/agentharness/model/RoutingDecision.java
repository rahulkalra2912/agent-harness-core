package io.agentharness.model;

/**
 * The outcome of applying a routing policy to a task.
 *
 * <p>The reason is intentionally captured alongside the selected model so routing decisions are
 * observable in traces and can be explained during debugging or evaluation.</p>
 */
public record RoutingDecision(
        ModelConfig model,
        String reason) {

    public RoutingDecision {
        if (model == null) throw new IllegalArgumentException("Model is required");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Routing reason cannot be blank");
        }
    }
}
