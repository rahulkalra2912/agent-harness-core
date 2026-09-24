package io.agentharness.core;

import io.agentharness.tool.ToolExecution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExecutionContext {
    private final List<ContextItem> items = new ArrayList<>();
    private final List<ToolExecution> toolExecutions = new ArrayList<>();

    public void add(ContextItem item) {
        items.add(item);
        if (item instanceof ContextItem.ToolExecutions toolItems) {
            toolExecutions.addAll(toolItems.executions());
        }
    }

    public List<ContextItem> items() {
        return Collections.unmodifiableList(items);
    }

    public List<ToolExecution> toolExecutions() {
        return Collections.unmodifiableList(toolExecutions);
    }
}
