package com.toolbox.tools.xml;

import com.toolbox.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlFormatToolTest {

    private final XmlFormatTool tool = new XmlFormatTool();

    @Test
    void shouldFormatWithTwoSpaceIndent() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("action", "format");
        params.put("input", "<root><a>1</a><b><c>2</c></b></root>");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertTrue(output.contains("\n  <a>1</a>"));
        assertTrue(output.contains("\n  <b>\n    <c>2</c>\n  </b>"));
        assertTrue(output.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\""));
    }

    @Test
    void shouldNormalizeMessyInputFormatting() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("action", "format");
        params.put("input", "<root>\n        <a>1</a>\n</root>");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertTrue(output.contains("\n  <a>1</a>"));
    }

    @Test
    void shouldCompressToSingleLine() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("action", "compress");
        params.put("input", "<root>\n  <a>1</a>\n  <b>2</b>\n</root>");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertFalse(output.contains("\n  "));
        assertTrue(output.contains("<root><a>1</a><b>2</b></root>"));
    }

    @Test
    void shouldPreserveMeaningfulWhitespaceInMixedContent() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("action", "compress");
        params.put("input", "<p>Hello <b>World</b>!</p>");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertTrue(output.contains("<p>Hello <b>World</b>!</p>"));
    }

    @Test
    void shouldValidateWellFormedXml() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("action", "validate");
        params.put("input", "<root><a>1</a></root>");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(Boolean.TRUE, data.get("valid"));
        assertEquals("root", data.get("rootTag"));
        assertEquals(2, data.get("elementCount"));
    }

    @Test
    void shouldReportInvalidXmlOnValidate() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("action", "validate");
        params.put("input", "<root><a></root>");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(Boolean.FALSE, data.get("valid"));
        assertTrue(String.valueOf(data.get("output")).contains("错误"));
    }

    @Test
    void shouldFailOnUnknownAction() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("action", "magic");
        params.put("input", "<root/>");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
    }
}
