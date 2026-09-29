package com.toolbox.tools.compare;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JSON 结构化对比工具。
 * 项目 JSON 能力统一使用 Fastjson 1.x。
 */
@Component
public class JsonCompareTool implements Tool {

    @Override
    public String getName() {
        return "json-compare";
    }

    @Override
    public String getDisplayName() {
        return "JSON 对比";
    }

    @Override
    public String getDescription() {
        return "两个 JSON 结构化对比，按路径输出新增、删除、值差异（Fastjson）";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String jsonA = str(params.get("jsonA"), "");
        String jsonB = str(params.get("jsonB"), "");

        if (jsonA.isEmpty() || jsonB.isEmpty()) {
            return ToolResult.fail("两个 JSON 都不能为空");
        }

        try {
            Object nodeA = JSON.parse(jsonA);
            Object nodeB = JSON.parse(jsonB);

            List<Map<String, Object>> differences = new ArrayList<Map<String, Object>>();
            compare(nodeA, nodeB, "$", differences);

            Map<String, Object> data = new HashMap<String, Object>();
            data.put("differences", differences);
            data.put("diffCount", differences.size());
            data.put("identical", differences.isEmpty());
            return ToolResult.ok(data);
        } catch (Exception e) {
            return ToolResult.fail("对比失败: " + e.getMessage());
        }
    }

    private void compare(Object a, Object b, String path, List<Map<String, Object>> diffs) {
        if (a == null || b == null) {
            if (!Objects.equals(a, b)) {
                diffs.add(diff("VALUE_CHANGE", path, nodeToText(a), nodeToText(b)));
            }
            return;
        }

        String typeA = jsonType(a);
        String typeB = jsonType(b);
        if (!typeA.equals(typeB)) {
            diffs.add(diff("TYPE_CHANGE", path, nodeToText(a), nodeToText(b)));
            return;
        }

        if (a instanceof JSONObject) {
            JSONObject objectA = (JSONObject) a;
            JSONObject objectB = (JSONObject) b;
            for (String field : objectA.keySet()) {
                String childPath = path + "." + field;
                if (!objectB.containsKey(field)) {
                    diffs.add(diff("REMOVED", childPath, nodeToText(objectA.get(field)), null));
                } else {
                    compare(objectA.get(field), objectB.get(field), childPath, diffs);
                }
            }
            for (String field : objectB.keySet()) {
                if (!objectA.containsKey(field)) {
                    diffs.add(diff("ADDED", path + "." + field, null, nodeToText(objectB.get(field))));
                }
            }
            return;
        }

        if (a instanceof JSONArray) {
            JSONArray arrayA = (JSONArray) a;
            JSONArray arrayB = (JSONArray) b;
            int len = Math.max(arrayA.size(), arrayB.size());
            for (int i = 0; i < len; i++) {
                String childPath = path + "[" + i + "]";
                if (i >= arrayA.size()) {
                    diffs.add(diff("ADDED", childPath, null, nodeToText(arrayB.get(i))));
                } else if (i >= arrayB.size()) {
                    diffs.add(diff("REMOVED", childPath, nodeToText(arrayA.get(i)), null));
                } else {
                    compare(arrayA.get(i), arrayB.get(i), childPath, diffs);
                }
            }
            return;
        }

        if (!Objects.equals(a, b)) {
            diffs.add(diff("VALUE_CHANGE", path, nodeToText(a), nodeToText(b)));
        }
    }

    private String jsonType(Object value) {
        if (value instanceof JSONObject) return "OBJECT";
        if (value instanceof JSONArray) return "ARRAY";
        if (value instanceof Number) return "NUMBER";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof String) return "STRING";
        return value.getClass().getName();
    }

    private Map<String, Object> diff(String type, String path, Object oldVal, Object newVal) {
        Map<String, Object> item = new HashMap<String, Object>();
        item.put("type", type);
        item.put("path", path);
        item.put("oldValue", oldVal);
        item.put("newValue", newVal);
        return item;
    }

    private String nodeToText(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        return JSON.toJSONString(value);
    }

    private String str(Object obj, String def) {
        return obj == null ? def : obj.toString();
    }
}
