package io.agentharness.demo.restaurant.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

/**
 * Demo-only data access layer backed by a JSON resource.
 * In production, the same tools could delegate to POS, reservation, staffing,
 * inventory, or review APIs without changing Harness Core.
 */
public final class RestaurantDataStore {
    private static final String RESOURCE = "/demo/restaurant/restaurant-data.json";

    private final ObjectMapper mapper;
    private final JsonNode root;

    public RestaurantDataStore(ObjectMapper mapper) {
        this.mapper = mapper;
        try (InputStream input = RestaurantDataStore.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing demo data resource: " + RESOURCE);
            }
            this.root = mapper.readTree(input);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load restaurant demo data", e);
        }
    }

    public JsonNode query(String collection, String restaurantId, LocalDate startDate,
                          LocalDate endDate, String service) {
        JsonNode source = root.path(collection);
        if (!source.isArray()) {
            throw new IllegalArgumentException("Unknown collection: " + collection);
        }

        ArrayNode records = mapper.createArrayNode();
        source.forEach(record -> {
            if (!restaurantId.equals(record.path("restaurant_id").asText())) return;

            String dateText = record.path("date").asText(null);
            if (dateText == null) return;
            LocalDate date = LocalDate.parse(dateText);
            if (date.isBefore(startDate) || date.isAfter(endDate)) return;

            if (service != null && !service.isBlank()
                    && !service.equalsIgnoreCase(record.path("service").asText())) return;

            records.add(record.deepCopy());
        });

        ObjectNode result = mapper.createObjectNode();
        result.put("restaurant_id", restaurantId);
        result.put("start_date", startDate.toString());
        result.put("end_date", endDate.toString());
        if (service != null && !service.isBlank()) result.put("service", service);
        result.set("records", records);
        return result;
    }

    public boolean restaurantExists(String restaurantId) {
        for (JsonNode restaurant : root.path("restaurants")) {
            if (restaurantId.equals(restaurant.path("restaurant_id").asText())) return true;
        }
        return false;
    }
}
