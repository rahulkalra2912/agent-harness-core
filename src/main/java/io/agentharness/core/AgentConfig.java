package io.agentharness.core;

import io.agentharness.model.RoutingPolicy;
import io.agentharness.tool.ToolId;

import java.util.Set;

public record AgentConfig(
        AgentId id,
        String systemInstructions,
        Set<ToolId> allowedTools,
        RoutingPolicy routingPolicy) {

    public AgentConfig {
        if (id == null) throw new IllegalArgumentException("Agent id is required");
        if (systemInstructions == null || systemInstructions.isBlank()) {
            throw new IllegalArgumentException("System instructions cannot be blank");
        }
        allowedTools = Set.copyOf(allowedTools);
        if (routingPolicy == null) throw new IllegalArgumentException("Routing policy is required");
    }
}
