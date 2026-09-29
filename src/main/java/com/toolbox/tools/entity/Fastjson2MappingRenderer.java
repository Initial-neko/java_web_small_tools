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
            case BIG_INTEGER:
                return "json.getBigInteger(" + key + ")";
            case BIG_DECIMAL:
                return "json.getBigDecimal(" + key + ")";
            case BOOLEAN:
                return "json.getBoolean(" + key + ")";
            case LOCAL_DATE:
                return "json.getObject(" + key + ", java.time.LocalDate.class)";
            case LOCAL_DATE_TIME:
                return "json.getObject(" + key + ", java.time.LocalDateTime.class)";
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
        if (itemType.getKind() == ValueKind.LIST) {
            return "json.getObject(" + key +
                    ", new com.alibaba.fastjson2.TypeReference<java.util.List<" +
                    genericType(itemType, rootClassName) + ">>() {})";
        }
        return "json.getList(" + key + ", " + classLiteral(itemType) + ")";
    }

    private String genericType(TypeSchema type, String rootClassName) {
        switch (type.getKind()) {
            case BIG_INTEGER: return "java.math.BigInteger";
            case BIG_DECIMAL: return "java.math.BigDecimal";
            case LOCAL_DATE: return "java.time.LocalDate";
            case LOCAL_DATE_TIME: return "java.time.LocalDateTime";
            case OBJECT:
                return rootClassName + "." + type.getObjectSchema().getClassName();
            case LIST:
                return "java.util.List<" + genericType(type.getItemType(), rootClassName) + ">";
            default:
                return type.toJavaType();
        }
    }

    private String classLiteral(TypeSchema type) {
        switch (type.getKind()) {
            case BIG_INTEGER: return "java.math.BigInteger.class";
            case BIG_DECIMAL: return "java.math.BigDecimal.class";
            case LOCAL_DATE: return "java.time.LocalDate.class";
            case LOCAL_DATE_TIME: return "java.time.LocalDateTime.class";
            default: return type.toJavaType() + ".class";
        }
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
