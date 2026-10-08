package com.toolbox.tools.xml;

import com.toolbox.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlToJsonToolTest {

    private final XmlToJsonTool tool = new XmlToJsonTool();

    @Test
    void shouldConvertXmlToJsonEndToEnd() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "<user id=\"1\"><name>Alice</name><tags><tag>a</tag><tag>b</tag></tags></user>");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("user", data.get("rootTag"));
        String output = String.valueOf(data.get("output"));
        assertTrue(output.contains("\"@id\":1"));
        assertTrue(output.contains("\"tag\":["));
    }

    @Test
    void shouldOutputCompressedJsonWhenPrettyDisabled() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "<user><name>Alice</name></user>");
        params.put("pretty", false);

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertFalse(output.contains("\n"));
        assertEquals("{\"user\":{\"name\":\"Alice\"}}", output);
    }

    @Test
    void shouldKeepStringsWhenInferenceDisabled() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "<user><age>18</age></user>");
        params.put("inferTypes", false);

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertTrue(output.contains("\"age\":\"18\""));
    }

    @Test
    void shouldFailOnInvalidXml() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "<root><a></root>");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("XML 解析失败"));
    }

    @Test
    void shouldFailOnEmptyInput() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
    }
}
