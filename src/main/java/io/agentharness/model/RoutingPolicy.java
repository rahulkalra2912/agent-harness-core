package io.agentharness.model;

import io.agentharness.core.ExecutionContext;
import io.agentharness.core.Task;

import java.util.Map;

/**
 * Strategy used by the harness to choose a model for a task.
 *
 * <p>The prototype routes once at the beginning of a task. The interface is deliberately isolated
 * so a deterministic policy can later be replaced by an evaluation-driven or adaptive router
 * without changing Harness Core.</p>
 */
public interface RoutingPolicy {
    RoutingDecision select(
            Task task,
            ExecutionContext context,
            Map<ModelId, ModelConfig> availableModels);

    String description();
}
