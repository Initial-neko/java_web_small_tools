package com.toolbox.tools.xml;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XML 转 JSON 工具。
 * 属性 -> "@键"、文本 -> "#text"、同名重复子元素 -> 数组；
 * 数字/布尔类型推断可通过参数关闭。
 */
@Component
public class XmlToJsonTool implements Tool {

    @Override
    public String getName() {
        return "xml-to-json";
    }

    @Override
    public String getDisplayName() {
        return "XML → JSON";
    }

    @Override
    public String getDescription() {
        return "XML 转 JSON：属性→@键、文本→#text、重复元素→数组，数字/布尔类型推断可开关";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String input = str(params.get("input"), "");
        boolean inferTypes = bool(params.get("inferTypes"), true);
        boolean pretty = bool(params.get("pretty"), true);

        if (input.isEmpty()) {
            return ToolResult.fail("XML 输入不能为空");
        }

        try {
            XmlJsonConverter converter = new XmlJsonConverter(inferTypes);
            Object json = converter.toJsonObject(input);
            String output = pretty
                    ? JSON.toJSONString(json, JSONWriter.Feature.PrettyFormat)
                    : JSON.toJSONString(json);

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("rootTag", rootTag(json));
            data.put("inferTypes", inferTypes);
            data.put("output", output);
            return ToolResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ToolResult.fail(e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("处理失败: " + e.getMessage());
        }
    }

    private String rootTag(Object json) {
        if (json instanceof Map<?, ?>) {
            for (Object key : ((Map<?, ?>) json).keySet()) {
                return String.valueOf(key);
            }
        }
        return null;
    }

    private String str(Object value, String def) {
        return value == null ? def : value.toString();
    }

    private boolean bool(Object value, boolean def) {
        if (value == null) return def;
        if (value instanceof Boolean) return (Boolean) value;
        String text = value.toString().trim();
        if (text.isEmpty()) return def;
        return Boolean.parseBoolean(text);
    }
}
