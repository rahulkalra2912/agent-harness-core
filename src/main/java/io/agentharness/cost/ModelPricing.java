package io.agentharness.cost;

import java.math.BigDecimal;

public record ModelPricing(
        BigDecimal inputPerMillionTokens,
        BigDecimal outputPerMillionTokens) {

    public ModelPricing {
        if (inputPerMillionTokens == null || inputPerMillionTokens.signum() < 0) {
            throw new IllegalArgumentException("Input price must be >= 0");
        }
        if (outputPerMillionTokens == null || outputPerMillionTokens.signum() < 0) {
            throw new IllegalArgumentException("Output price must be >= 0");
        }
    }
}
