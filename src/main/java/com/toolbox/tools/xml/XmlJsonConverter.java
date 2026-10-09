package com.toolbox.tools.xml;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XML -> Fastjson2 JSON 确定性转换器。
 *
 * 规则：
 * - 属性 -> "@名称" 键，例如 id="1" -> {"@id": 1}
 * - 纯文本元素 -> 字符串或推断后的标量值，例如 <age>18</age> -> {"age": 18}
 * - 元素同时含子节点与文本时，文本 -> "#text" 键
 * - 同名重复子元素 -> JSON 数组，出现一次保持单值
 * - 命名空间前缀保留在键名中，例如 ns:tag -> "ns:tag"
 * - 注释、处理指令忽略；DOCTYPE 直接拒绝（防 XXE）
 * - 数字/布尔文本推断为 JSON 类型（inferTypes 开关），前导零数字保持字符串
 * - 元素文本输出前去除首尾空白（排版空白视为噪音，混合内容中间空白保留）
 */
public class XmlJsonConverter {

    /** 属性键前缀 */
    public static final String ATTR_PREFIX = "@";

    /** 混合内容文本键 */
    public static final String TEXT_KEY = "#text";

    private final boolean inferTypes;

    public XmlJsonConverter() {
        this(true);
    }

    public XmlJsonConverter(boolean inferTypes) {
        this.inferTypes = inferTypes;
    }

    /**
     * 解析 XML 并转换为 JSON，返回以根元素名为键的单键对象。
     */
    public JSONObject toJsonObject(String xml) {
        return toJsonObject(parseToDocument(xml));
    }

    /**
     * 转换已解析的 Document 为 JSON，返回以根元素名为键的单键对象。
     */
    public JSONObject toJsonObject(Document document) {
        Element root = document.getDocumentElement();
        JSONObject result = new JSONObject();
        result.put(root.getTagName(), elementValue(root));
        return result;
    }

    /**
     * 解析 XML 并返回根元素的内容（不含根元素名包装）。
     * 根为纯文本元素时返回标量（包装为单字段）；否则返回属性/子元素组成的有序 Map。
     * 适合作为实体推断的输入：根元素名由调用方用作类名，避免生成自嵌套类。
     */
    public Map<String, Object> toRootContent(String xml) {
        return toRootContent(parseToDocument(xml));
    }

    /**
     * 转换已解析的 Document，返回根元素的内容（不含根元素名包装）。
     */
    public Map<String, Object> toRootContent(Document document) {
        Element root = document.getDocumentElement();
        Object value = elementValue(root);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) value;
            return content;
        }
        // 根为纯文本/空元素：包装为单字段，保证后续实体推断仍可用
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put(root.getTagName(), value);
        return content;
    }

    /**
     * 解析 XML 为 DOM Document。禁用 DTD 与外部实体，防止 XXE 攻击。
     * @throws IllegalArgumentException XML 非法或包含 DOCTYPE 时抛出
     */
    public Document parseToDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML 解析失败: " + rootMessage(e), e);
        }
    }

    private Object elementValue(Element element) {
        Map<String, Object> obj = new LinkedHashMap<String, Object>();

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            obj.put(ATTR_PREFIX + attribute.getNodeName(), typedValue(attribute.getNodeValue()));
        }

        Map<String, List<Object>> children = new LinkedHashMap<String, List<Object>>();
        StringBuilder text = new StringBuilder();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                List<Object> values = children.get(child.getTagName());
                if (values == null) {
                    values = new ArrayList<Object>();
                    children.put(child.getTagName(), values);
                }
                values.add(elementValue(child));
            } else if (node.getNodeType() == Node.TEXT_NODE
                    || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(node.getNodeValue() == null ? "" : node.getNodeValue());
            }
            // 注释、处理指令等其他节点忽略
        }

        String trimmed = text.toString().trim();
        if (obj.isEmpty() && children.isEmpty()) {
            // 纯文本元素或空元素
            return typedValue(trimmed);
        }

        for (Map.Entry<String, List<Object>> entry : children.entrySet()) {
            List<Object> values = entry.getValue();
            if (values.size() == 1) {
                obj.put(entry.getKey(), values.get(0));
            } else {
                JSONArray array = new JSONArray();
                array.addAll(values);
                obj.put(entry.getKey(), array);
            }
        }
        if (!trimmed.isEmpty()) {
            obj.put(TEXT_KEY, typedValue(trimmed));
        }
        return obj;
    }

    private Object typedValue(String raw) {
        if (!inferTypes) {
            return raw;
        }
        if (raw.isEmpty()) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return raw;
        }
        if ("true".equals(value) || "false".equals(value)) {
            return Boolean.valueOf(value);
        }
        if (value.matches("[+-]?\\d+")) {
            if (hasLeadingZero(value)) {
                return raw;
            }
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException e) {
                // 超出 Integer 范围，继续尝试 Long
            }
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException e) {
                return new BigInteger(value);
            }
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private boolean hasLeadingZero(String literal) {
        int start = literal.charAt(0) == '+' || literal.charAt(0) == '-' ? 1 : 0;
        String digits = literal.substring(start);
        return digits.length() > 1 && digits.charAt(0) == '0';
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        String message = current.getMessage();
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current.getMessage() != null) {
                message = current.getMessage();
            }
        }
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
