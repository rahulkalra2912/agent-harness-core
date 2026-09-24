package io.agentharness.demo.restaurant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentharness.demo.restaurant.data.RestaurantDataStore;
import io.agentharness.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantToolsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestaurantDataStore store = new RestaurantDataStore(mapper);

    @Test
    void salesToolReturnsBothComparisonDays() throws Exception {
        GetSalesTool tool = new GetSalesTool(store, mapper);
        ToolResult result = tool.execute(mapper.readTree("""
                {
                  "restaurant_id": "rest_001",
                  "start_date": "2026-09-10",
                  "end_date": "2026-09-17",
                  "service": "dinner"
                }
                """));

        assertInstanceOf(ToolResult.Success.class, result);
        ToolResult.Success success = (ToolResult.Success) result;
        assertEquals(2, success.data().path("records").size());
        assertEquals(12480.0, success.data().path("records").get(0).path("revenue_eur").asDouble());
        assertEquals(10190.0, success.data().path("records").get(1).path("revenue_eur").asDouble());
    }

    @Test
    void unknownRestaurantIsReportedAsStructuredFailure() throws Exception {
        GetReviewsTool tool = new GetReviewsTool(store, mapper);
        ToolResult result = tool.execute(mapper.readTree("""
                {
                  "restaurant_id": "missing",
                  "start_date": "2026-09-17",
                  "end_date": "2026-09-17"
                }
                """));

        assertInstanceOf(ToolResult.Failure.class, result);
        assertEquals("RESTAURANT_NOT_FOUND", ((ToolResult.Failure) result).errorCode());
    }

    @Test
    void malformedDateIsReportedAsStructuredFailure() throws Exception {
        GetStaffingTool tool = new GetStaffingTool(store, mapper);
        ToolResult result = tool.execute(mapper.readTree("""
                {
                  "restaurant_id": "rest_001",
                  "start_date": "yesterday",
                  "end_date": "2026-09-17"
                }
                """));

        assertInstanceOf(ToolResult.Failure.class, result);
        assertEquals("INVALID_DATE", ((ToolResult.Failure) result).errorCode());
    }
}
