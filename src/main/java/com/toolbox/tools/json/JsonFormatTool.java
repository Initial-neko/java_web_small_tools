package com.toolbox.tools.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONWriter;
import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON 格式化工具。
 * 项目 JSON 能力统一使用 Fastjson 1.x。
 */
@Component
public class JsonFormatTool implements Tool {

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
        return "JSON 美化、压缩、校验、转义/反转义（Fastjson）";
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
                Object value = JSON.parse(input);
                data.put("output", JSON.toJSONString(value, JSONWriter.Feature.PrettyFormat));
                return ToolResult.ok(data);
            }

            if ("compress".equals(action)) {
                Object value = JSON.parse(input);
                data.put("output", JSON.toJSONString(value));
                return ToolResult.ok(data);
            }

            if ("validate".equals(action)) {
                try {
                    JSON.parse(input);
                    data.put("valid", true);
                    data.put("output", "JSON 格式正确");
                } catch (JSONException e) {
                    data.put("valid", false);
                    data.put("output", "错误: " + e.getMessage());
                }
                return ToolResult.ok(data);
            }

            if ("escape".equals(action)) {
                String escaped = JSON.toJSONString(input);
                data.put("output", escaped.substring(1, escaped.length() - 1));
                return ToolResult.ok(data);
            }

            if ("unescape".equals(action)) {
                String wrapped = "\"" + input + "\"";
                data.put("output", JSON.parseObject(wrapped, String.class));
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
