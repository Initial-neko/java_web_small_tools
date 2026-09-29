package com.toolbox.tools.sqllineage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量 SQL 输入支持两种格式：
 * 1. JSON array: [{"sqlId":"x","sql":"select ...","source":"job"}]
 * 2. 文本块：多条 SQL 用单独一行 -- @SQL 分隔。
 */
public class BatchSqlInputParser {

    public List<SqlInput> parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("批量 SQL 输入不能为空");
        }

        String trimmed = input.trim();
        if (trimmed.startsWith("[")) {
            return parseJson(trimmed);
        }
        return parseDelimited(trimmed);
    }

    private List<SqlInput> parseJson(String input) {
        JSONArray array = JSON.parseArray(input);
        List<SqlInput> results = new ArrayList<SqlInput>();

        for (int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            SqlInput item;

            if (value instanceof String) {
                item = new SqlInput(id(i), (String) value);
            } else if (value instanceof JSONObject) {
                JSONObject object = (JSONObject) value;
                item = new SqlInput(
                        text(object.get("sqlId"), id(i)),
                        text(object.get("sql"), "")
                );
                item.setSource(text(object.get("source"), ""));
                item.setFileName(text(object.get("fileName"), ""));
                JSONObject metadata = object.getJSONObject("metadata");
                if (metadata != null) {
                    java.util.Map<String, String> map = new java.util.LinkedHashMap<String, String>();
                    for (String key : metadata.keySet()) {
                        Object metadataValue = metadata.get(key);
                        map.put(key, metadataValue == null ? null : String.valueOf(metadataValue));
                    }
                    item.setMetadata(map);
                }
            } else {
                throw new IllegalArgumentException("JSON array 第 " + (i + 1) + " 项必须是 string/object");
            }

            if (item.getSql() == null || item.getSql().trim().isEmpty()) {
                throw new IllegalArgumentException("JSON array 第 " + (i + 1) + " 项 sql 不能为空");
            }
            results.add(item);
        }

        if (results.isEmpty()) {
            throw new IllegalArgumentException("JSON array 不能为空");
        }
        return results;
    }

    private List<SqlInput> parseDelimited(String input) {
        String[] blocks = input.split("(?m)^\\s*--\\s*@SQL\\s*$");
        List<SqlInput> results = new ArrayList<SqlInput>();

        for (String block : blocks) {
            String sql = block.trim();
            if (sql.isEmpty()) continue;
            results.add(new SqlInput(id(results.size()), sql));
        }

        if (results.isEmpty()) {
            throw new IllegalArgumentException("没有解析到 SQL");
        }
        return results;
    }

    private String id(int index) {
        return String.format("sql-%04d", index + 1);
    }

    private String text(Object value, String def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }
}
