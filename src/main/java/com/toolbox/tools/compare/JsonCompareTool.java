package com.toolbox.tools.compare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON 结构化对比工具。
 * 递归比较两个 JSON，按路径输出差异（新增/删除/值不同/类型不同）。
 */
@Component
public class JsonCompareTool implements Tool {

    private final ObjectMapper mapper = new ObjectMapper();

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
        return "两个 JSON 结构化对比，按路径输出新增、删除、值差异";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String jsonA = str(params.get("jsonA"), "");
        String jsonB = str(params.get("jsonB"), "");

        if (jsonA.isEmpty() || jsonB.isEmpty()) {
            return ToolResult.fail("两个 JSON 都不能为空");
        }

        try {
            JsonNode nodeA = mapper.readTree(jsonA);
            JsonNode nodeB = mapper.readTree(jsonB);

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

    private void compare(JsonNode a, JsonNode b, String path, List<Map<String, Object>> diffs) {
        // 类型不同
        if (a.getNodeType() != b.getNodeType()) {
            diffs.add(diff("TYPE_CHANGE", path,
                    a.getNodeType().toString(), b.getNodeType().toString(),
                    nodeToText(a), nodeToText(b)));
            return;
        }

        // 对象
        if (a.isObject()) {
            Iterator<String> fieldsA = a.fieldNames();
            while (fieldsA.hasNext()) {
                String field = fieldsA.next();
                String childPath = path + "." + field;
                if (!b.has(field)) {
                    diffs.add(diff("REMOVED", childPath, "存在", "不存在", nodeToText(a.get(field)), null));
                } else {
                    compare(a.get(field), b.get(field), childPath, diffs);
                }
            }
            Iterator<String> fieldsB = b.fieldNames();
            while (fieldsB.hasNext()) {
                String field = fieldsB.next();
                if (!a.has(field)) {
                    diffs.add(diff("ADDED", path + "." + field, "不存在", "存在", null, nodeToText(b.get(field))));
                }
            }
            return;
        }

        // 数组
        if (a.isArray()) {
            int len = Math.max(a.size(), b.size());
            for (int i = 0; i < len; i++) {
                String childPath = path + "[" + i + "]";
                if (i >= a.size()) {
                    diffs.add(diff("ADDED", childPath, "不存在", "存在", null, nodeToText(b.get(i))));
                } else if (i >= b.size()) {
                    diffs.add(diff("REMOVED", childPath, "存在", "不存在", nodeToText(a.get(i)), null));
                } else {
                    compare(a.get(i), b.get(i), childPath, diffs);
                }
            }
            return;
        }

        // 叶子节点（值）
        if (!a.equals(b)) {
            diffs.add(diff("VALUE_CHANGE", path,
                    nodeToText(a), nodeToText(b), nodeToText(a), nodeToText(b)));
        }
    }

    private Map<String, Object> diff(String type, String path, String oldVal, String newVal,
                                     Object oldRaw, Object newRaw) {
        Map<String, Object> item = new HashMap<String, Object>();
        item.put("type", type);
        item.put("path", path);
        item.put("oldValue", oldVal);
        item.put("newValue", newVal);
        return item;
    }

    private String nodeToText(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) return node.asText();
        return node.toString();
    }

    private String str(Object obj, String def) {
        return obj == null ? def : obj.toString();
    }
}
