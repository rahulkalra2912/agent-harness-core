package io.agentharness.runtime;

import java.math.BigDecimal;
import java.time.Duration;

public record TaskUsage(
        int modelCalls,
        int toolCalls,
        long inputTokens,
        long outputTokens,
        BigDecimal totalCost,
        Duration duration) {}
