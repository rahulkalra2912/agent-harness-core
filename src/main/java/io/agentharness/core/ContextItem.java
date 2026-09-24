package io.agentharness.core;

import io.agentharness.tool.ToolCall;
import io.agentharness.tool.ToolExecution;

import java.util.List;

/**
 * Provider-neutral items that make up an agent execution's working context.
 *
 * <p>The harness intentionally stores both model-requested tool calls and their executions.
 * Tool-call APIs (including OpenAI Responses) require both halves of that exchange when context
 * is managed by the application rather than by provider-side conversation state.</p>
 */
public sealed interface ContextItem
        permits ContextItem.UserMessage,
                ContextItem.AssistantMessage,
                ContextItem.ToolRequests,
                ContextItem.ToolExecutions {

    record UserMessage(String text) implements ContextItem {}

    record AssistantMessage(String text) implements ContextItem {}

    /** The tool calls requested by the model in one model turn. */
    record ToolRequests(List<ToolCall> calls) implements ContextItem {
        public ToolRequests {
            calls = List.copyOf(calls);
            if (calls.isEmpty()) {
                throw new IllegalArgumentException("ToolRequests cannot be empty");
            }
        }
    }

    /** The corresponding tool execution outcomes returned to the model. */
    record ToolExecutions(List<ToolExecution> executions) implements ContextItem {
        public ToolExecutions {
            executions = List.copyOf(executions);
            if (executions.isEmpty()) {
                throw new IllegalArgumentException("ToolExecutions cannot be empty");
            }
        }
    }
}
