package com.toolbox.tools.entity;

public class Fastjson2MappingRenderer {

    public String render(EntitySchema schema) {
        StringBuilder out = new StringBuilder();
        out.append("public static ").append(schema.getClassName())
                .append(" fromJson(JSONObject json) {\n")
                .append("    if (json == null) return null;\n")
                .append("    ").append(schema.getClassName()).append(" target = new ")
                .append(schema.getClassName()).append("();\n");

        for (FieldSchema field : schema.getFields()) {
            out.append("    target.set").append(JavaNameUtils.capitalize(field.getFieldName())).append("(")
                    .append(expression(field.getSourceName(), field.getType(), schema.getClassName()))
                    .append(");\n");
        }

        out.append("    return target;\n")
                .append("}\n");
        return out.toString();
    }

    private String expression(String sourceName, TypeSchema type, String rootClassName) {
        String key = "\"" + escapeJava(sourceName) + "\"";
        switch (type.getKind()) {
            case STRING:
                return "json.getString(" + key + ")";
            case INTEGER:
                return "json.getInteger(" + key + ")";
            case LONG:
                return "json.getLong(" + key + ")";
            case BIG_DECIMAL:
                return "json.getBigDecimal(" + key + ")";
            case BOOLEAN:
                return "json.getBoolean(" + key + ")";
            case OBJECT:
                return "json.getObject(" + key + ", " + rootClassName + "." +
                        type.getObjectSchema().getClassName() + ".class)";
            case LIST:
                return listExpression(key, type.getItemType(), rootClassName);
            default:
                return "json.get(" + key + ")";
        }
    }

    private String listExpression(String key, TypeSchema itemType, String rootClassName) {
        if (itemType == null || itemType.getKind() == ValueKind.UNKNOWN) {
            return "json.getJSONArray(" + key + ")";
        }
        if (itemType.getKind() == ValueKind.OBJECT) {
            return "json.getList(" + key + ", " + rootClassName + "." +
                    itemType.getObjectSchema().getClassName() + ".class)";
        }
        return "json.getList(" + key + ", " + itemType.toJavaType() + ".class)";
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
