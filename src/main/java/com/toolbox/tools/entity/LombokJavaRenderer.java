package com.toolbox.tools.entity;

public class LombokJavaRenderer extends NormalJavaRenderer {

    @Override
    public String render(EntitySchema schema) {
        StringBuilder out = new StringBuilder();
        appendPackageAndImports(out, schema, true);
        renderLombokClass(out, schema, "", false);
        return out.toString();
    }

    private void renderLombokClass(StringBuilder out, EntitySchema schema, String indent, boolean nested) {
        out.append(indent).append("@Data\n");
        out.append(indent).append(nested ? "public static class " : "public class ")
                .append(schema.getClassName()).append(" {\n");

        if (!schema.getFields().isEmpty()) out.append("\n");
        for (FieldSchema field : schema.getFields()) {
            out.append(indent).append("    private ").append(field.getJavaType()).append(" ")
                    .append(field.getFieldName()).append(";\n");
        }

        for (EntitySchema nestedSchema : nestedSchemas(schema)) {
            out.append("\n");
            renderLombokClass(out, nestedSchema, indent + "    ", true);
        }
        out.append(indent).append("}\n");
    }
}
