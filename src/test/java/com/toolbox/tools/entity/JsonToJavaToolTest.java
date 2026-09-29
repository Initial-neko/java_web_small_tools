package com.toolbox.tools.entity;

import com.toolbox.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonToJavaToolTest {

    private final JsonToJavaTool tool = new JsonToJavaTool();

    @Test
    void shouldGenerateNormalEntityEndToEnd() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "{\"user_id\":1,\"name\":\"A\"}");
        params.put("className", "User");
        params.put("packageName", "demo");
        params.put("mode", "normal");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertTrue(output.contains("public class User"));
        assertTrue(output.contains("private Integer userId;"));
    }

    @Test
    void shouldRejectArrayRootForFirstVersion() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "[{\"id\":1}]");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("根节点"));
    }

    @Test
    void shouldRejectUnknownMode() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "{\"id\":1}");
        params.put("mode", "magic");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
    }
}
