package io.agentharness.demo.restaurant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentharness.demo.restaurant.data.RestaurantDataStore;
import io.agentharness.tool.ToolResult;

import java.time.DateTimeException;
import java.time.LocalDate;

final class RestaurantToolSupport {
    private RestaurantToolSupport() {}

    static JsonNode dateRangeSchema(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode restaurantId = mapper.createObjectNode();
        restaurantId.put("type", "string");
        restaurantId.put("description",
                "Restaurant identifier, for example rest_001");
        properties.set("restaurant_id", restaurantId);

        ObjectNode startDate = mapper.createObjectNode();
        startDate.put("type", "string");
        startDate.put("format", "date");
        startDate.put("description",
                "Inclusive start date in YYYY-MM-DD format");
        properties.set("start_date", startDate);

        ObjectNode endDate = mapper.createObjectNode();
        endDate.put("type", "string");
        endDate.put("format", "date");
        endDate.put("description",
                "Inclusive end date in YYYY-MM-DD format");
        properties.set("end_date", endDate);

        ObjectNode service = mapper.createObjectNode();
        service.put("type", "string");
        service.put("description",
                "Optional meal period such as breakfast, lunch, or dinner");
        properties.set("service", service);

        root.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("restaurant_id");
        required.add("start_date");
        required.add("end_date");

        root.set("required", required);
        root.put("additionalProperties", false);

        return root;
    }

    static ToolResult query(RestaurantDataStore store, JsonNode arguments, String collection) {
        String restaurantId = requiredText(arguments, "restaurant_id");
        String startDateText = requiredText(arguments, "start_date");
        String endDateText = requiredText(arguments, "end_date");
        String service = optionalText(arguments, "service");

        if (restaurantId == null || startDateText == null || endDateText == null) {
            return new ToolResult.Failure(
                    "INVALID_ARGUMENTS",
                    "restaurant_id, start_date, and end_date are required");
        }
        if (!store.restaurantExists(restaurantId)) {
            return new ToolResult.Failure("RESTAURANT_NOT_FOUND", "Unknown restaurant: " + restaurantId);
        }

        try {
            LocalDate startDate = LocalDate.parse(startDateText);
            LocalDate endDate = LocalDate.parse(endDateText);
            if (endDate.isBefore(startDate)) {
                return new ToolResult.Failure("INVALID_DATE_RANGE", "end_date cannot be before start_date");
            }
            return new ToolResult.Success(store.query(collection, restaurantId, startDate, endDate, service));
        } catch (DateTimeException e) {
            return new ToolResult.Failure("INVALID_DATE", "Dates must use YYYY-MM-DD format");
        }
    }

    private static String requiredText(JsonNode arguments, String field) {
        if (arguments == null || !arguments.hasNonNull(field)) return null;
        String value = arguments.path(field).asText();
        return value.isBlank() ? null : value;
    }

    private static String optionalText(JsonNode arguments, String field) {
        if (arguments == null || !arguments.hasNonNull(field)) return null;
        String value = arguments.path(field).asText();
        return value.isBlank() ? null : value;
    }
}
