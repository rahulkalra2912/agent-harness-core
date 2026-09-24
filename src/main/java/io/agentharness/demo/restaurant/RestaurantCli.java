package io.agentharness.demo.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.agentharness.core.AgentConfig;
import io.agentharness.core.AgentId;
import io.agentharness.core.ExecutionPolicy;
import io.agentharness.core.Task;
import io.agentharness.core.TaskId;
import io.agentharness.cost.ModelPricing;
import io.agentharness.cost.PricingCatalog;
import io.agentharness.demo.restaurant.tools.RestaurantToolIds;
import io.agentharness.demo.restaurant.tools.RestaurantToolSet;
import io.agentharness.model.DeterministicComplexityRoutingPolicy;
import io.agentharness.model.ModelConfig;
import io.agentharness.model.ModelId;
import io.agentharness.model.ModelRouter;
import io.agentharness.model.openai.OpenAiModelClient;
import io.agentharness.runtime.Harness;
import io.agentharness.runtime.TaskResult;
import io.agentharness.runtime.TaskStatus;
import io.agentharness.tool.ToolRegistry;
import io.agentharness.trace.ExecutionEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root and CLI for the Restaurant Operations demo.
 *
 * <p>All restaurant-specific configuration lives here (and in the restaurant tools). Harness Core
 * remains unaware of the domain, OpenAI model names, or demo-specific prompts.</p>
 */
public final class RestaurantCli {
    private static final ModelId FAST_MODEL = new ModelId("fast");
    private static final ModelId STRONG_MODEL = new ModelId("strong");

    // Current demo model choices. The pricing catalog below is intentionally kept next to these
    // mappings so the model-to-price relationship is explicit and easy to update.
    private static final String FAST_PROVIDER_MODEL = "gpt-5.6-luna";
    private static final String STRONG_PROVIDER_MODEL = "gpt-5.6-sol";

    private static final String SYSTEM_INSTRUCTIONS = """
            You are a restaurant operations analyst for Mints & Honey (restaurant_id: rest_001).

            Use the available tools to retrieve operational data before making factual claims.
            You may request multiple independent tools in the same turn when that reduces latency.
            Never invent restaurant data. If a tool fails, continue with the available evidence when
            possible and clearly state what information is missing.

            For analysis questions, distinguish evidence from inference. For recommendation questions,
            explain which observed facts support each recommendation. Keep the final answer concise and
            useful to a restaurant manager.
            """;

    private RestaurantCli() {}

    public static void main(String[] args) {
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank()) {
            System.err.println("OPENAI_API_KEY is not set.");
            System.err.println("Set it in your IntelliJ Run Configuration or shell environment, then retry.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        ToolRegistry toolRegistry = new ToolRegistry();
        RestaurantToolSet.create(mapper).forEach(toolRegistry::register);

        ModelConfig fastModel = new ModelConfig(FAST_MODEL, FAST_PROVIDER_MODEL);
        ModelConfig strongModel = new ModelConfig(STRONG_MODEL, STRONG_PROVIDER_MODEL);
        ModelRouter modelRouter = new ModelRouter(Map.of(
                FAST_MODEL, fastModel,
                STRONG_MODEL, strongModel));

        DeterministicComplexityRoutingPolicy routingPolicy =
                new DeterministicComplexityRoutingPolicy(FAST_MODEL, STRONG_MODEL);

        AgentConfig restaurantAgent = new AgentConfig(
                new AgentId("restaurant-operations"),
                SYSTEM_INSTRUCTIONS,
                Set.of(
                        RestaurantToolIds.GET_SALES,
                        RestaurantToolIds.GET_RESERVATIONS,
                        RestaurantToolIds.GET_STAFFING,
                        RestaurantToolIds.GET_INVENTORY,
                        RestaurantToolIds.GET_REVIEWS),
                routingPolicy);

        // Demo guardrails. These constrain autonomy without putting business logic in Harness Core.
        ExecutionPolicy executionPolicy = new ExecutionPolicy(
                8,
                Duration.ofSeconds(60),
                new BigDecimal("0.25"));

        // Prices are per one million text tokens and intentionally live outside the provider adapter.
        // Keeping pricing separate lets unit economics evolve independently from model execution.
        PricingCatalog pricingCatalog = new PricingCatalog(Map.of(
                FAST_MODEL, new ModelPricing(new BigDecimal("0.20"), new BigDecimal("1.20")),
                STRONG_MODEL, new ModelPricing(new BigDecimal("4.00"), new BigDecimal("20.00"))));

        OpenAIClient openAIClient = OpenAIOkHttpClient.fromEnv();
        OpenAiModelClient modelClient = new OpenAiModelClient(openAIClient, mapper);

        // Tool calls are I/O-like work in production. Virtual threads let independent calls run in
        // parallel without tying the harness to a small fixed worker pool.
        try (ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            Harness harness = new Harness(
                    modelClient,
                    modelRouter,
                    toolRegistry,
                    pricingCatalog,
                    toolExecutor);

            if (args.length > 0) {
                runTask(harness, restaurantAgent, executionPolicy, String.join(" ", args));
                return;
            }

            runInteractive(harness, restaurantAgent, executionPolicy);
        }
    }

    private static void runInteractive(
            Harness harness,
            AgentConfig agent,
            ExecutionPolicy policy) {
        System.out.println("Restaurant Operations Agent");
        System.out.println("Demo restaurant: Mints & Honey (rest_001)");
        System.out.println("Available dinner data: 2026-09-10 and 2026-09-17");
        System.out.println();
        System.out.println("Try:");
        System.out.println("  Summarize dinner performance for 2026-09-17.");
        System.out.println("  Why did dinner revenue drop on 2026-09-17 compared with 2026-09-10?");
        System.out.println("  What should the manager investigate first based on 2026-09-17 dinner performance?");
        System.out.println();
        System.out.println("Type 'exit' to quit.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\n> ");
                if (!scanner.hasNextLine()) return;

                String prompt = scanner.nextLine().trim();
                if (prompt.equalsIgnoreCase("exit") || prompt.equalsIgnoreCase("quit")) return;
                if (prompt.isBlank()) continue;

                runTask(harness, agent, policy, prompt);
            }
        }
    }

    private static void runTask(
            Harness harness,
            AgentConfig agent,
            ExecutionPolicy policy,
            String prompt) {
        Task task = new Task(new TaskId(UUID.randomUUID().toString()), prompt);

        System.out.println("\n--- Executing task " + task.id().value() + " ---");
        TaskResult result = harness.execute(agent, task, policy);

        printTrace(result);
        printResult(result);
    }

    private static void printTrace(TaskResult result) {
        System.out.println("\nExecution trace:");
        for (ExecutionEvent event : result.trace().events()) {
            if (event instanceof ExecutionEvent.ModelSelected selected) {
                System.out.printf("  MODEL_SELECTED  %-8s %s%n",
                        selected.modelId().value(), selected.reason());
            } else if (event instanceof ExecutionEvent.ModelCallCompleted call) {
                System.out.printf("  MODEL_CALL      %-8s in=%d out=%d cost=$%s%n",
                        call.modelId().value(),
                        call.usage().inputTokens(),
                        call.usage().outputTokens(),
                        money(call.cost()));
            } else if (event instanceof ExecutionEvent.ToolCallCompleted tool) {
                String outcome = tool.execution().result() instanceof io.agentharness.tool.ToolResult.Success
                        ? "SUCCESS"
                        : "FAILURE";
                System.out.printf("  TOOL_CALL       %-18s %-7s %dms args=%s%n",
                        tool.execution().toolId().value(),
                        outcome,
                        tool.execution().duration().toMillis(),
                        tool.call().arguments());
            } else if (event instanceof ExecutionEvent.TaskFailed failed) {
                System.out.println("  TASK_FAILED     " + failed.reason());
            }
        }
    }

    private static void printResult(TaskResult result) {
        System.out.println("\nResult:");
        if (result.status() == TaskStatus.COMPLETED) {
            System.out.println(result.answer());
        } else {
            System.out.println("Task ended with status: " + result.status());
        }

        System.out.println("\nTask economics:");
        System.out.println("  status:        " + result.status());
        System.out.println("  model calls:   " + result.usage().modelCalls());
        System.out.println("  tool calls:    " + result.usage().toolCalls());
        System.out.println("  input tokens:  " + result.usage().inputTokens());
        System.out.println("  output tokens: " + result.usage().outputTokens());
        System.out.println("  total cost:    $" + money(result.usage().totalCost()));
        System.out.println("  duration:      " + result.usage().duration().toMillis() + "ms");
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
