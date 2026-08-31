package com.toolbox.tools.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON 格式化工具。
 * 支持：美化、压缩、校验、转义、反转义。
 */
@Component
public class JsonFormatTool implements Tool {

    private final ObjectMapper prettyMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ObjectMapper compactMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "json-format";
    }

    @Override
    public String getDisplayName() {
        return "JSON 格式化";
    }

    @Override
    public String getDescription() {
        return "JSON 美化、压缩、校验、转义/反转义";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String action = str(params.get("action"), "format");
        String input = str(params.get("input"), "");

        if (input.isEmpty()) {
            return ToolResult.fail("输入内容不能为空");
        }

        try {
            Map<String, Object> data = new HashMap<String, Object>();

            if ("format".equals(action)) {
                JsonNode node = prettyMapper.readTree(input);
                data.put("output", prettyMapper.writeValueAsString(node));
                return ToolResult.ok(data);
            }

            if ("compress".equals(action)) {
                JsonNode node = compactMapper.readTree(input);
                data.put("output", compactMapper.writeValueAsString(node));
                return ToolResult.ok(data);
            }

            if ("validate".equals(action)) {
                try {
                    compactMapper.readTree(input);
                    data.put("valid", true);
                    data.put("output", "JSON 格式正确");
                } catch (JsonProcessingException e) {
                    data.put("valid", false);
                    data.put("output", "错误: " + e.getOriginalMessage()
                            + " (行 " + e.getLocation().getLineNr()
                            + ", 列 " + e.getLocation().getColumnNr() + ")");
                }
                return ToolResult.ok(data);
            }

            if ("escape".equals(action)) {
                // 将 JSON 字符串转义为可嵌入字符串的形式
                String escaped = compactMapper.writeValueAsString(input);
                // 去掉首尾引号
                data.put("output", escaped.substring(1, escaped.length() - 1));
                return ToolResult.ok(data);
            }

            if ("unescape".equals(action)) {
                // 反转义：给输入包一层引号再解析
                String wrapped = "\"" + input + "\"";
                String unescaped = compactMapper.readValue(wrapped, String.class);
                data.put("output", unescaped);
                return ToolResult.ok(data);
            }

            return ToolResult.fail("未知 action: " + action + "，可选 format/compress/validate/escape/unescape");
        } catch (Exception e) {
            return ToolResult.fail("处理失败: " + e.getMessage());
        }
    }

    private String str(Object obj, String def) {
        return obj == null ? def : obj.toString();
    }
}
