# Agent Harness Core

A Java 21 prototype of a reusable runtime for executing tool-using agents.

The Harness Core accepts an agent task, selects an appropriate model, executes iterative model/tool interactions, enforces execution limits, and tracks task-level usage and cost. Domain-specific behavior remains outside the runtime and is supplied through agent configuration and tool implementations.

## Architecture

The runtime is built around a small set of responsibilities:

- **Agent execution** : runs the model -> tool -> model loop until completion or an execution limit is reached.
- **Model routing** : selects from configured logical models through a replaceable `RoutingPolicy`.
- **Tool access control** : exposes only the tools allowed by the current `AgentConfig`.
- **Execution context** : preserves model messages, tool requests, and tool results across turns.
- **Execution guardrails** : enforces per task limits such as maximum steps, timeout, and cost.
- **Unit economics** : aggregates token usage and model cost across the complete task.
- **Observability** : emits a structured execution trace covering routing, model calls, tool calls, and task completion.

Harness Core does not contain domain-specific workflow logic. New agents are introduced through configuration and tools rather than changes to the execution loop.

## Reference Demo: Restaurant Operations

The repository includes a Restaurant Operations agent as a reference integration.

The demo uses local JSON data for a fictional restaurant and exposes five tools:

- `get_sales`
- `get_reservations`
- `get_staffing`
- `get_inventory`
- `get_reviews`

Example tasks include:

```text
Summarize dinner performance for 2026-09-17.

Why did dinner revenue drop on 2026-09-17 compared with 2026-09-10?

What should the manager investigate first based on 2026-09-17 dinner performance?
```

The restaurant tools read from local JSON for the demo. In production, those same tool interfaces could call real POS, reservation, workforce, inventory, or review systems.

## Model Routing

Model selection is separated from execution through a `RoutingPolicy`.

The prototype uses deterministic complexity routing between two logical model configurations:

- `FAST` : straightforward lookup and summary tasks
- `STRONG` : causal analysis, comparison, investigation, and recommendation tasks

The routing decision and its reason are recorded in the execution trace.

Routing is behind a RoutingPolicy, so the current deterministic strategy can be swapped later without touching the harness loop.

## Tool Execution

Tools are registered in a shared `ToolRegistry`, while each agent declares the subset it is permitted to use.

A model may request multiple tools in a single turn. Independent tool calls are executed concurrently and correlated back to their original requests using the model-provided call ID.

Tool failures are isolated per invocation. Each execution produces either a structured success or failure result, allowing the model to continue with partial context when appropriate.

## Model Provider Integration

Harness Core interacts with model providers through the `ModelClient` abstraction.

The prototype provides an `OpenAiModelClient` implementation using the official OpenAI Java SDK and Responses API. Provider specific SDK types remain inside the adapter and do not leak into Harness Core.

`FAST` and `STRONG` are logical model configurations. Their provider specific model mappings can be changed without modifying the execution loop.

## Execution Context

`ExecutionContext` maintains the working history for a task, including:

- the original user request
- assistant/model messages
- requested tool calls
- tool execution results

After tools are executed, the expanded context is supplied to the next model turn. This allows the model to continue from the complete model/tool interaction rather than receiving isolated tool output.

The prototype keeps the complete history for each bounded task. Context pruning or summarization could be introduced later without changing the Harness Core contract.

## Unit Economics and Observability

Cost is tracked at the **task level**, rather than per individual API request.

Each model response contributes token usage, which is priced through `PricingCatalog` and aggregated by `CostTracker` across the complete execution.

For every task, the CLI reports:

- routing decision
- model calls
- tool calls
- completion status
- input and output tokens
- total model cost
- end-to-end duration

The pricing catalog is a prototype snapshot. If provider model mappings are changed, the corresponding pricing configuration should be updated as well.

## Running the Demo

### Requirements

- Java 21
- Gradle
- OpenAI API key

Set the API key through the environment:

```bash
export OPENAI_API_KEY="<your-api-key>"
```

### IntelliJ

1. Open the repository as a Gradle project.
2. Configure JDK 21 as the Project SDK.
3. Add `OPENAI_API_KEY` to the `RestaurantCli` run configuration.
4. Run:

```text
io.agentharness.demo.restaurant.RestaurantCli
```

The CLI supports both interactive and one-shot execution.

## Testing

The prototype includes JUnit 5 tests covering core runtime behavior, including:

- model routing decisions
- tool allowlist enforcement
- concurrent tool execution and result correlation
- partial tool failures
- execution guardrails
- task-level usage and cost aggregation

Run the test suite with:

```bash
./gradlew test
```

## Technology

- Java 21
- Gradle
- Jackson `JsonNode` for tool schemas and payloads
- Official OpenAI Java SDK
- OpenAI Responses API
- JUnit 5
