package io.agentharness.model;

import io.agentharness.core.ExecutionContext;
import io.agentharness.core.Task;

import java.util.Map;

/**
 * Supplies the model catalog to a routing policy and validates the selected model.
 */
public final class ModelRouter {
    private final Map<ModelId, ModelConfig> models;

    public ModelRouter(Map<ModelId, ModelConfig> models) {
        this.models = Map.copyOf(models);
    }

    public RoutingDecision route(RoutingPolicy policy, Task task, ExecutionContext context) {
        RoutingDecision decision = policy.select(task, context, models);

        ModelConfig registeredModel = models.get(decision.model().id());
        if (registeredModel == null) {
            throw new IllegalStateException(
                    "Routing policy selected unknown model: " + decision.model().id().value());
        }

        return new RoutingDecision(registeredModel, decision.reason());
    }
}
