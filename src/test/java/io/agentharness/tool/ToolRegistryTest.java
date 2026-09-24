package io.agentharness.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rejectsDuplicateToolIds() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool("get_sales"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(tool("get_sales")));
    }

    private Tool tool(String id) {
        return new Tool() {
            @Override
            public ToolDefinition definition() {
                JsonNode schema = MAPPER.createObjectNode()
                        .put("type", "object");
                return new ToolDefinition(new ToolId(id), "test", schema);
            }

            @Override
            public ToolResult execute(JsonNode arguments) {
                return new ToolResult.Success(MAPPER.createObjectNode().put("ok", true));
            }
        };
    }
}
