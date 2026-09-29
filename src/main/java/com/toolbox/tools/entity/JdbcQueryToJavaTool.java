package com.toolbox.tools.entity;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JdbcQueryToJavaTool implements Tool {

    private final JdbcQueryEntityGenerator generator = new JdbcQueryEntityGenerator();
    private final NormalJavaRenderer normalRenderer = new NormalJavaRenderer();
    private final LombokJavaRenderer lombokRenderer = new LombokJavaRenderer();

    @Override
    public String getName() {
        return "jdbc-query-to-java";
    }

    @Override
    public String getDisplayName() {
        return "JDBC Query → Java";
    }

    @Override
    public String getDescription() {
        return "外置 JDBC Driver 执行只读 SELECT/WITH，按 ResultSetMetaData 生成 Normal/Lombok Entity";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            JdbcQueryEntityRequest request = JdbcQueryEntityRequest.from(params);
            EntitySchema schema = generator.generate(request);

            String output = "lombok".equals(request.getMode())
                    ? lombokRenderer.render(schema)
                    : normalRenderer.render(schema);

            List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
            for (FieldSchema field : schema.getFields()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("sourceName", field.getSourceName());
                item.put("fieldName", field.getFieldName());
                item.put("sourceType", field.getSourceType());
                item.put("javaType", field.getJavaType());
                fields.add(item);
            }

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("databaseType", request.getDatabaseType());
            data.put("className", schema.getClassName());
            data.put("packageName", schema.getPackageName());
            data.put("fieldCount", schema.getFields().size());
            data.put("fields", fields);
            data.put("output", output);
            return ToolResult.ok(data);
        } catch (Exception e) {
            return ToolResult.fail("JDBC Query Entity 生成失败: " + rootMessage(e));
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName()
                : message;
    }
}
