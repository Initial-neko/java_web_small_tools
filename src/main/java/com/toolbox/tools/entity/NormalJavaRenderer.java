package com.toolbox.tools.entity;

import java.util.LinkedHashSet;
import java.util.Set;

public class NormalJavaRenderer {

    public String render(EntitySchema schema) {
        StringBuilder out = new StringBuilder();
        appendPackageAndImports(out, schema, false);
        renderClass(out, schema, "", false);
        return out.toString();
    }

    protected void appendPackageAndImports(StringBuilder out, EntitySchema schema, boolean lombok) {
        if (schema.getPackageName() != null && !schema.getPackageName().isEmpty()) {
            out.append("package ").append(schema.getPackageName()).append(";\n\n");
        }

        Set<String> imports = new LinkedHashSet<String>();
        collectImports(schema, imports);
        if (lombok) imports.add("lombok.Data");

        for (String item : imports) {
            out.append("import ").append(item).append(";\n");
        }
        if (!imports.isEmpty()) out.append("\n");
    }

    protected void renderClass(StringBuilder out, EntitySchema schema, String indent, boolean nested) {
        out.append(indent).append(nested ? "public static class " : "public class ")
                .append(schema.getClassName()).append(" {\n\n");

        for (FieldSchema field : schema.getFields()) {
            out.append(indent).append("    private ").append(field.getJavaType()).append(" ")
                    .append(field.getFieldName()).append(";\n");
        }

        if (!schema.getFields().isEmpty()) out.append("\n");

        for (FieldSchema field : schema.getFields()) {
            String cap = JavaNameUtils.capitalize(field.getFieldName());
            out.append(indent).append("    public ").append(field.getJavaType()).append(" get")
                    .append(cap).append("() {\n")
                    .append(indent).append("        return ").append(field.getFieldName()).append(";\n")
                    .append(indent).append("    }\n\n");

            out.append(indent).append("    public void set").append(cap).append("(")
                    .append(field.getJavaType()).append(" ").append(field.getFieldName()).append(") {\n")
                    .append(indent).append("        this.").append(field.getFieldName()).append(" = ")
                    .append(field.getFieldName()).append(";\n")
                    .append(indent).append("    }\n\n");
        }

        for (EntitySchema nestedSchema : nestedSchemas(schema)) {
            renderClass(out, nestedSchema, indent + "    ", true);
            out.append("\n");
        }

        trimTrailingBlankLine(out);
        out.append(indent).append("}\n");
    }

    protected Set<EntitySchema> nestedSchemas(EntitySchema schema) {
        Set<EntitySchema> result = new LinkedHashSet<EntitySchema>();
        for (FieldSchema field : schema.getFields()) {
            collectNested(field.getType(), result);
        }
        return result;
    }

    private void collectNested(TypeSchema type, Set<EntitySchema> out) {
        if (type == null) return;
        if (type.getKind() == ValueKind.OBJECT && type.getObjectSchema() != null) {
            out.add(type.getObjectSchema());
        } else if (type.getKind() == ValueKind.LIST) {
            collectNested(type.getItemType(), out);
        }
    }

    private void collectImports(EntitySchema schema, Set<String> imports) {
        for (FieldSchema field : schema.getFields()) {
            collectImports(field.getType(), imports);
        }
    }

    private void collectImports(TypeSchema type, Set<String> imports) {
        if (type == null) return;
        if (type.getKind() == ValueKind.BIG_DECIMAL) {
            imports.add("java.math.BigDecimal");
        } else if (type.getKind() == ValueKind.LIST) {
            imports.add("java.util.List");
            collectImports(type.getItemType(), imports);
        } else if (type.getKind() == ValueKind.OBJECT && type.getObjectSchema() != null) {
            collectImports(type.getObjectSchema(), imports);
        }
    }

    private void trimTrailingBlankLine(StringBuilder out) {
        while (out.length() >= 2 && out.substring(out.length() - 2).equals("\n\n")) {
            out.setLength(out.length() - 1);
        }
    }
}
