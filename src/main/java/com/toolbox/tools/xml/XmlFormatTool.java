package com.toolbox.tools.xml;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XML 格式化工具：美化、压缩、校验。
 * 解析阶段禁用 DTD 与外部实体，防止 XXE。
 */
@Component
public class XmlFormatTool implements Tool {

    private final XmlJsonConverter converter = new XmlJsonConverter();
    private final XmlFormatter formatter = new XmlFormatter();

    @Override
    public String getName() {
        return "xml-format";
    }

    @Override
    public String getDisplayName() {
        return "XML 格式化";
    }

    @Override
    public String getDescription() {
        return "XML 美化（2 空格缩进）、压缩（单行）、校验；禁用 DTD 防 XXE";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String action = str(params.get("action"), "format");
        String input = str(params.get("input"), "");

        if (input.isEmpty()) {
            return ToolResult.fail("XML 输入不能为空");
        }

        try {
            Map<String, Object> data = new LinkedHashMap<String, Object>();

            if ("format".equals(action)) {
                Document document = converter.parseToDocument(input);
                data.put("output", formatter.format(document));
                return ToolResult.ok(data);
            }

            if ("compress".equals(action)) {
                Document document = converter.parseToDocument(input);
                data.put("output", formatter.compress(document));
                return ToolResult.ok(data);
            }

            if ("validate".equals(action)) {
                try {
                    Document document = converter.parseToDocument(input);
                    data.put("valid", true);
                    data.put("rootTag", document.getDocumentElement().getTagName());
                    data.put("elementCount", countElements(document.getDocumentElement()));
                    data.put("output", "XML 格式正确，根元素: " + document.getDocumentElement().getTagName());
                } catch (IllegalArgumentException e) {
                    data.put("valid", false);
                    data.put("output", "错误: " + e.getMessage());
                }
                return ToolResult.ok(data);
            }

            return ToolResult.fail("未知 action: " + action + "，可选 format/compress/validate");
        } catch (IllegalArgumentException e) {
            return ToolResult.fail(e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("处理失败: " + e.getMessage());
        }
    }

    private int countElements(Element element) {
        int count = 1;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count += countElements((Element) child);
            }
        }
        return count;
    }

    private String str(Object value, String def) {
        return value == null ? def : value.toString();
    }
}
