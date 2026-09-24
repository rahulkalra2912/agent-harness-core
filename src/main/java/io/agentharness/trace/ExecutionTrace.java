package io.agentharness.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExecutionTrace {
    private final List<ExecutionEvent> events = new ArrayList<>();

    public void add(ExecutionEvent event) {
        events.add(event);
    }

    public List<ExecutionEvent> events() {
        return Collections.unmodifiableList(events);
    }
}
