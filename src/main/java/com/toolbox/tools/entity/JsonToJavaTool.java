package com.toolbox.tools.entity;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JsonToJavaTool implements Tool {

    private final JsonEntitySchemaInferer inferer = new JsonEntitySchemaInferer();
    private final NormalJavaRenderer normalRenderer = new NormalJavaRenderer();
    private final LombokJavaRenderer lombokRenderer = new LombokJavaRenderer();
    private final Fastjson2MappingRenderer mappingRenderer = new Fastjson2MappingRenderer();

    @Override
    public String getName() {
        return "json-to-java";
    }

    @Override
    public String getDisplayName() {
        return "JSON → Java";
    }

    @Override
    public String getDescription() {
        return "Fastjson2 JSON 样本生成 Normal Entity、Lombok Entity 或 JSONObject 显式映射代码";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String input = str(params.get("input"), "");
        String className = str(params.get("className"), "GeneratedEntity");
        String packageName = str(params.get("packageName"), "com.example.model");
        String mode = str(params.get("mode"), "normal");

        if (input.isEmpty()) {
            return ToolResult.fail("JSON 输入不能为空");
        }

        try {
            EntitySchema schema = inferer.infer(input, className, packageName);
            String output;
            if ("normal".equals(mode)) {
                output = normalRenderer.render(schema);
            } else if ("lombok".equals(mode)) {
                output = lombokRenderer.render(schema);
            } else if ("mapping".equals(mode)) {
                output = mappingRenderer.render(schema);
            } else {
                return ToolResult.fail("未知 mode: " + mode + "，可选 normal/lombok/mapping");
            }

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("mode", mode);
            data.put("className", schema.getClassName());
            data.put("packageName", schema.getPackageName());
            data.put("fieldCount", schema.getFields().size());
            data.put("output", output);
            return ToolResult.ok(data);
        } catch (Exception e) {
            return ToolResult.fail("生成失败: " + e.getMessage());
        }
    }

    private String str(Object value, String def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }
}
