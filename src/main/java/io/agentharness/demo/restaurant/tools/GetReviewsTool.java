package io.agentharness.demo.restaurant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentharness.demo.restaurant.data.RestaurantDataStore;
import io.agentharness.tool.Tool;
import io.agentharness.tool.ToolDefinition;
import io.agentharness.tool.ToolResult;

public final class GetReviewsTool implements Tool {
    private final RestaurantDataStore store;
    private final ToolDefinition definition;

    public GetReviewsTool(RestaurantDataStore store, ObjectMapper mapper) {
        this.store = store;
        this.definition = new ToolDefinition(
                RestaurantToolIds.GET_REVIEWS,
                "Returns guest reviews for a date range, including rating, review text, and operational tags.",
                RestaurantToolSupport.dateRangeSchema(mapper));
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public ToolResult execute(JsonNode arguments) {
        return RestaurantToolSupport.query(store, arguments, "reviews");
    }
}
