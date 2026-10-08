package com.toolbox.tools.xml;

import com.alibaba.fastjson2.JSON;
import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import com.toolbox.tools.entity.EntitySchema;
import com.toolbox.tools.entity.Fastjson2MappingRenderer;
import com.toolbox.tools.entity.JavaNameUtils;
import com.toolbox.tools.entity.JsonEntitySchemaInferer;
import com.toolbox.tools.entity.LombokJavaRenderer;
import com.toolbox.tools.entity.NormalJavaRenderer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XML 转 Java Entity 工具。
 * 先按 XmlJsonConverter 确定性规则转成 JSON（属性 -> "@键"、文本 -> "#text"、
 * 重复元素 -> 数组），再复用 JsonEntitySchemaInferer 与现有渲染器输出
 * Normal Entity / Lombok Entity / Fastjson2 显式 Mapping Code。
 * 类名默认取 XML 根元素名。
 */
@Component
public class XmlToJavaTool implements Tool {

    private final NormalJavaRenderer normalRenderer = new NormalJavaRenderer();
    private final LombokJavaRenderer lombokRenderer = new LombokJavaRenderer();
    private final Fastjson2MappingRenderer mappingRenderer = new Fastjson2MappingRenderer();

    @Override
    public String getName() {
        return "xml-to-java";
    }

    @Override
    public String getDisplayName() {
        return "XML → Java";
    }

    @Override
    public String getDescription() {
        return "XML 样本生成 Normal/Lombok Entity 或 Fastjson2 显式映射代码；属性→@键、重复元素→List";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String input = str(params.get("input"), "");
        String classNameParam = str(params.get("className"), "");
        String packageName = str(params.get("packageName"), "com.example.model");
        String mode = str(params.get("mode"), "normal");

        if (input.isEmpty()) {
            return ToolResult.fail("XML 输入不能为空");
        }

        try {
            XmlJsonConverter converter = new XmlJsonConverter(true);
            org.w3c.dom.Document document = converter.parseToDocument(input);
            String rootTag = document.getDocumentElement().getTagName();
            Map<String, Object> rootContent = converter.toRootContent(document);

            String className = classNameParam.isEmpty()
                    ? JavaNameUtils.toClassName(rootTag)
                    : JavaNameUtils.toClassName(classNameParam);

            EntitySchema schema = new JsonEntitySchemaInferer()
                    .infer(JSON.toJSONString(rootContent), className, packageName);

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
            data.put("rootTag", rootTag);
            data.put("className", schema.getClassName());
            data.put("packageName", schema.getPackageName());
            data.put("fieldCount", schema.getFields().size());
            data.put("output", output);
            return ToolResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ToolResult.fail(e.getMessage());
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
