package io.agentharness.demo.restaurant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentharness.demo.restaurant.data.RestaurantDataStore;
import io.agentharness.tool.Tool;

import java.util.List;

public final class RestaurantToolSet {
    private RestaurantToolSet() {}

    public static List<Tool> create(ObjectMapper mapper) {
        RestaurantDataStore store = new RestaurantDataStore(mapper);
        return List.of(
                new GetSalesTool(store, mapper),
                new GetReservationsTool(store, mapper),
                new GetStaffingTool(store, mapper),
                new GetInventoryTool(store, mapper),
                new GetReviewsTool(store, mapper));
    }
}
