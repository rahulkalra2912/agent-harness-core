package io.agentharness.core;

import java.math.BigDecimal;
import java.time.Duration;

public record ExecutionPolicy(
        int maxSteps,
        Duration timeout,
        BigDecimal maxCost) {

    public ExecutionPolicy {
        if (maxSteps <= 0) throw new IllegalArgumentException("maxSteps must be > 0");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxCost == null || maxCost.signum() < 0) {
            throw new IllegalArgumentException("maxCost must be >= 0");
        }
    }
}
