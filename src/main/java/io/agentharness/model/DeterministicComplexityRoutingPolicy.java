package io.agentharness.model;

import io.agentharness.core.ExecutionContext;
import io.agentharness.core.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bootstrap routing policy for the prototype.
 *
 * <p>It uses transparent prompt heuristics rather than an additional LLM call. This keeps routing
 * cheap, deterministic, and easy to test while the system has no historical evaluation data.</p>
 *
 * <p>This is intentionally not presented as the production end-state. Execution traces capture the
 * signals needed to evolve toward evaluation-driven, cost-aware routing later.</p>
 */
public final class DeterministicComplexityRoutingPolicy implements RoutingPolicy {
    private static final int STRONG_MODEL_THRESHOLD = 2;
    private static final int LONG_PROMPT_THRESHOLD = 240;

    private final ModelId fastModelId;
    private final ModelId strongModelId;

    public DeterministicComplexityRoutingPolicy(ModelId fastModelId, ModelId strongModelId) {
        this.fastModelId = fastModelId;
        this.strongModelId = strongModelId;
    }

    @Override
    public RoutingDecision select(
            Task task,
            ExecutionContext context,
            Map<ModelId, ModelConfig> availableModels) {
        String prompt = task.prompt().toLowerCase(Locale.ROOT);
        int score = 0;
        List<String> signals = new ArrayList<>();

        // Causal/investigative requests generally require more reasoning than retrieval or summary.
        if (containsAny(prompt, "why", "analyze", "analyse", "investigate", "root cause", "cause of")) {
            score += 2;
            signals.add("causal/analysis request");
        }

        // Recommendations require the model to synthesize evidence and choose an action.
        if (containsAny(prompt, "recommend", "what should", "suggest", "prioritize", "prioritise")) {
            score += 2;
            signals.add("recommendation request");
        }

        // Comparisons often require combining multiple pieces of context before answering.
        if (containsAny(prompt, "compare", "versus", " vs ", "difference between")) {
            score += 1;
            signals.add("comparison request");
        }

        // Long prompts are a weak signal only; length alone should not force the strong model.
        if (task.prompt().length() > LONG_PROMPT_THRESHOLD) {
            score += 1;
            signals.add("long prompt");
        }

        ModelId selectedId = score >= STRONG_MODEL_THRESHOLD ? strongModelId : fastModelId;
        ModelConfig selected = availableModels.get(selectedId);
        if (selected == null) {
            throw new IllegalStateException("Configured routing model is not registered: " + selectedId.value());
        }

        String reason = signals.isEmpty()
                ? "No complex reasoning signals detected; selected fast model"
                : "Complexity score=" + score + " from " + String.join(", ", signals)
                        + "; selected " + selectedId.value();

        return new RoutingDecision(selected, reason);
    }

    @Override
    public String description() {
        return "Deterministic prompt-complexity routing";
    }

    private static boolean containsAny(String prompt, String... signals) {
        for (String signal : signals) {
            if (prompt.contains(signal)) return true;
        }
        return false;
    }
}
