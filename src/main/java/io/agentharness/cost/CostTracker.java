package io.agentharness.cost;

import io.agentharness.model.ModelId;
import io.agentharness.model.Usage;

import java.math.BigDecimal;
import java.math.MathContext;

public final class CostTracker {
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final MathContext MC = MathContext.DECIMAL64;

    private final PricingCatalog pricingCatalog;
    private BigDecimal total = BigDecimal.ZERO;
    private long inputTokens;
    private long outputTokens;
    private int modelCalls;

    public CostTracker(PricingCatalog pricingCatalog) {
        this.pricingCatalog = pricingCatalog;
    }

    public BigDecimal record(ModelId modelId, Usage usage) {
        ModelPricing pricing = pricingCatalog.pricingFor(modelId);
        BigDecimal inputCost = pricing.inputPerMillionTokens()
                .multiply(BigDecimal.valueOf(usage.inputTokens()), MC)
                .divide(ONE_MILLION, MC);
        BigDecimal outputCost = pricing.outputPerMillionTokens()
                .multiply(BigDecimal.valueOf(usage.outputTokens()), MC)
                .divide(ONE_MILLION, MC);
        BigDecimal callCost = inputCost.add(outputCost, MC);

        total = total.add(callCost, MC);
        inputTokens += usage.inputTokens();
        outputTokens += usage.outputTokens();
        modelCalls++;
        return callCost;
    }

    public BigDecimal totalCost() { return total; }
    public long inputTokens() { return inputTokens; }
    public long outputTokens() { return outputTokens; }
    public int modelCalls() { return modelCalls; }
}
