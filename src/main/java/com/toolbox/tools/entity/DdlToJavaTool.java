package com.toolbox.tools.entity;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DdlToJavaTool implements Tool {

    private final DdlEntitySchemaParser parser = new DdlEntitySchemaParser();
    private final NormalJavaRenderer normalRenderer = new NormalJavaRenderer();
    private final LombokJavaRenderer lombokRenderer = new LombokJavaRenderer();

    @Override
    public String getName() {
        return "ddl-to-java";
    }

    @Override
    public String getDisplayName() {
        return "DDL → Java";
    }

    @Override
    public String getDescription() {
        return "Druid AST 解析达梦/Oracle CREATE TABLE，生成 Normal/Lombok Entity";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String ddl = str(params.get("ddl"), "");
            String databaseType = str(params.get("databaseType"), "dm");
            String className = str(params.get("className"), "");
            String packageName = str(params.get("packageName"), "com.example.model");
            String mode = str(params.get("mode"), "normal");

            EntitySchema schema = parser.parse(ddl, databaseType, className, packageName);
            String output;
            if ("normal".equals(mode)) {
                output = normalRenderer.render(schema);
            } else if ("lombok".equals(mode)) {
                output = lombokRenderer.render(schema);
            } else {
                return ToolResult.fail("未知 mode: " + mode + "，可选 normal/lombok");
            }

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("databaseType", databaseType);
            data.put("className", schema.getClassName());
            data.put("packageName", schema.getPackageName());
            data.put("fieldCount", schema.getFields().size());
            data.put("output", output);
            return ToolResult.ok(data);
        } catch (Exception e) {
            return ToolResult.fail("DDL Entity 生成失败: " + e.getMessage());
        }
    }

    private String str(Object value, String def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }
}
