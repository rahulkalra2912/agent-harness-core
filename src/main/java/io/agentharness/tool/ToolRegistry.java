package io.agentharness.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
    private final Map<ToolId, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        ToolId id = tool.definition().id();
        if (tools.putIfAbsent(id, tool) != null) {
            throw new IllegalArgumentException("Tool already registered: " + id.value());
        }
    }

    public Optional<Tool> find(ToolId id) {
        return Optional.ofNullable(tools.get(id));
    }

    public Collection<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }
}
