package com.toolbox.tools.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonEntitySchemaInferer {

    public EntitySchema infer(String json, String className, String packageName) {
        Object root = JSON.parse(json);
        if (!(root instanceof JSONObject)) {
            throw new IllegalArgumentException("第一版要求 JSON 根节点是 Object");
        }
        String safeClassName = JavaNameUtils.toClassName(className);
        return inferObjects(Collections.singletonList((JSONObject) root), safeClassName, packageName);
    }

    private EntitySchema inferObjects(List<JSONObject> objects, String className, String packageName) {
        Set<String> sourceKeys = new LinkedHashSet<String>();
        for (JSONObject object : objects) {
            sourceKeys.addAll(object.keySet());
        }
        List<String> keys = new ArrayList<String>(sourceKeys);
        Collections.sort(keys);

        List<FieldSchema> fields = new ArrayList<FieldSchema>();
        Map<String, Integer> fieldNameCounts = new HashMap<String, Integer>();

        for (String sourceName : keys) {
            String baseFieldName = JavaNameUtils.toFieldName(sourceName);
            String fieldName = uniqueName(baseFieldName, fieldNameCounts);

            List<Object> values = new ArrayList<Object>();
            for (JSONObject object : objects) {
                values.add(object.containsKey(sourceName) ? object.get(sourceName) : null);
            }

            String nestedBase = JavaNameUtils.toClassName(fieldName);
            TypeSchema type = inferValues(values, nestedBase, packageName);
            fields.add(new FieldSchema(sourceName, fieldName, type));
        }
        return new EntitySchema(packageName, className, fields);
    }

    private TypeSchema inferValues(List<Object> values, String nestedBase, String packageName) {
        List<Object> nonNull = new ArrayList<Object>();
        for (Object value : values) {
            if (value != null) nonNull.add(value);
        }
        if (nonNull.isEmpty()) {
            return TypeSchema.scalar(ValueKind.UNKNOWN);
        }

        if (allOf(nonNull, JSONObject.class)) {
            List<JSONObject> objects = new ArrayList<JSONObject>();
            for (Object value : nonNull) objects.add((JSONObject) value);
            return TypeSchema.object(inferObjects(objects, nestedBase, packageName));
        }

        if (allOf(nonNull, JSONArray.class)) {
            List<Object> items = new ArrayList<Object>();
            for (Object value : nonNull) {
                JSONArray array = (JSONArray) value;
                items.addAll(array);
            }
            if (items.isEmpty()) {
                return TypeSchema.list(TypeSchema.scalar(ValueKind.UNKNOWN));
            }
            return TypeSchema.list(inferValues(items, nestedBase + "Item", packageName));
        }

        if (allNumbers(nonNull)) {
            return TypeSchema.scalar(numberKind(nonNull));
        }
        if (allOf(nonNull, String.class)) {
            return TypeSchema.scalar(ValueKind.STRING);
        }
        if (allOf(nonNull, Boolean.class)) {
            return TypeSchema.scalar(ValueKind.BOOLEAN);
        }

        return TypeSchema.scalar(ValueKind.UNKNOWN);
    }

    private boolean allOf(List<Object> values, Class<?> type) {
        for (Object value : values) {
            if (!type.isInstance(value)) return false;
        }
        return true;
    }

    private boolean allNumbers(List<Object> values) {
        for (Object value : values) {
            if (!(value instanceof Number)) return false;
        }
        return true;
    }

    private ValueKind numberKind(List<Object> values) {
        boolean decimal = false;
        boolean wide = false;
        for (Object value : values) {
            if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) {
                decimal = true;
            } else if (value instanceof Long || value instanceof BigInteger) {
                wide = true;
            }
        }
        if (decimal) return ValueKind.BIG_DECIMAL;
        if (wide) return ValueKind.LONG;
        return ValueKind.INTEGER;
    }

    private String uniqueName(String base, Map<String, Integer> counts) {
        Integer count = counts.get(base);
        if (count == null) {
            counts.put(base, 1);
            return base;
        }
        int next = count + 1;
        counts.put(base, next);
        return base + next;
    }
}
