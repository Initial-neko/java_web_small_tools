package com.toolbox.tools.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * XML 美化 / 压缩输出。
 * 统一先移除元素之间纯空白的文本节点，再按 2 空格缩进重排或单行输出，
 * 保证同样结构的文档无论输入如何排版，输出都保持确定。
 * 含有意义空白的混合内容（如 "a <b>c</b> d"）不受影响。
 */
public class XmlFormatter {

    private static final String INDENT_AMOUNT_KEY = "{http://xml.apache.org/xslt}indent-amount";

    public String format(Document document) {
        removeWhitespaceBetweenElements(document);
        return transform(document, true);
    }

    public String compress(Document document) {
        removeWhitespaceBetweenElements(document);
        return transform(document, false);
    }

    private void removeWhitespaceBetweenElements(Document document) {
        removeWhitespace(document.getDocumentElement());
    }

    private void removeWhitespace(Node parent) {
        NodeList children = parent.getChildNodes();
        List<Node> toRemove = new ArrayList<Node>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE
                    && child.getNodeValue() != null
                    && child.getNodeValue().trim().isEmpty()) {
                toRemove.add(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeWhitespace(child);
            }
        }
        for (Node node : toRemove) {
            parent.removeChild(node);
        }
    }

    private String transform(Document document, boolean indent) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            if (indent) {
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(INDENT_AMOUNT_KEY, "2");
            } else {
                transformer.setOutputProperty(OutputKeys.INDENT, "no");
            }

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString().trim();
        } catch (Exception e) {
            throw new IllegalArgumentException("XML 输出失败: " + e.getMessage(), e);
        }
    }
}
