package io.agentharness.cost;

import io.agentharness.model.ModelId;

import java.util.Map;

public final class PricingCatalog {
    private final Map<ModelId, ModelPricing> pricing;

    public PricingCatalog(Map<ModelId, ModelPricing> pricing) {
        this.pricing = Map.copyOf(pricing);
    }

    public ModelPricing pricingFor(ModelId modelId) {
        ModelPricing result = pricing.get(modelId);
        if (result == null) {
            throw new IllegalArgumentException("No pricing configured for model: " + modelId.value());
        }
        return result;
    }
}
