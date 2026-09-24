package io.agentharness.model;

import io.agentharness.core.ExecutionContext;
import io.agentharness.core.Task;
import io.agentharness.core.TaskId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicComplexityRoutingPolicyTest {
    private static final ModelId FAST = new ModelId("fast");
    private static final ModelId STRONG = new ModelId("strong");

    private final Map<ModelId, ModelConfig> models = Map.of(
            FAST, new ModelConfig(FAST, "fast-provider-model"),
            STRONG, new ModelConfig(STRONG, "strong-provider-model"));

    private final DeterministicComplexityRoutingPolicy policy =
            new DeterministicComplexityRoutingPolicy(FAST, STRONG);

    @Test
    void routesSimpleLookupToFastModel() {
        Task task = new Task(new TaskId("task-1"), "Show yesterday's sales.");

        RoutingDecision decision = policy.select(task, new ExecutionContext(), models);

        assertEquals(FAST, decision.model().id());
        assertTrue(decision.reason().contains("fast model"));
    }

    @Test
    void routesCausalAnalysisToStrongModel() {
        Task task = new Task(new TaskId("task-2"), "Why did dinner revenue drop yesterday?");

        RoutingDecision decision = policy.select(task, new ExecutionContext(), models);

        assertEquals(STRONG, decision.model().id());
        assertTrue(decision.reason().contains("causal/analysis request"));
    }

    @Test
    void routesRecommendationToStrongModel() {
        Task task = new Task(
                new TaskId("task-3"),
                "What should the restaurant manager investigate first based on yesterday's performance?");

        RoutingDecision decision = policy.select(task, new ExecutionContext(), models);

        assertEquals(STRONG, decision.model().id());
        assertTrue(decision.reason().contains("recommendation request"));
    }
}
