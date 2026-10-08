package com.toolbox.tools.xml;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlJsonConverterTest {

    private final XmlJsonConverter converter = new XmlJsonConverter();

    @Test
    void shouldMapAttributesWithAtPrefixAndInferTypes() {
        JSONObject json = converter.toJsonObject("<user id=\"1\" active=\"true\"/>");

        JSONObject user = json.getJSONObject("user");
        assertEquals(1, user.get("@id"));
        assertEquals(Boolean.TRUE, user.get("@active"));
    }

    @Test
    void shouldMapNestedElementsAndScalars() {
        JSONObject json = converter.toJsonObject(
                "<user id=\"1\"><name>Alice</name><score>9.5</score></user>");

        JSONObject user = json.getJSONObject("user");
        assertEquals(1, user.get("@id"));
        assertEquals("Alice", user.get("name"));
        assertEquals(new BigDecimal("9.5"), user.get("score"));
    }

    @Test
    void shouldMapRepeatedChildrenToArray() {
        JSONObject json = converter.toJsonObject(
                "<tags><tag>a</tag><tag>b</tag><tag>c</tag></tags>");

        JSONArray tag = json.getJSONObject("tags").getJSONArray("tag");
        assertEquals(3, tag.size());
        assertEquals("a", tag.get(0));
        assertEquals("c", tag.get(2));
    }

    @Test
    void shouldKeepSingleChildAsScalarNotArray() {
        JSONObject json = converter.toJsonObject("<tags><tag>a</tag></tags>");

        assertEquals("a", json.getJSONObject("tags").get("tag"));
    }

    @Test
    void shouldInferIntegerLongBigDecimalBoolean() {
        JSONObject json = converter.toJsonObject(
                "<d><i>42</i><l>9999999999</l><big>99999999999999999999999999</big><x>1.50</x><b>true</b></d>");

        assertEquals(42, json.getJSONObject("d").get("i"));
        assertEquals(9999999999L, json.getJSONObject("d").get("l"));
        assertEquals(new java.math.BigInteger("99999999999999999999999999"),
                json.getJSONObject("d").get("big"));
        assertEquals(new BigDecimal("1.50"), json.getJSONObject("d").get("x"));
        assertEquals(Boolean.TRUE, json.getJSONObject("d").get("b"));
    }

    @Test
    void shouldKeepLeadingZeroNumberAsString() {
        JSONObject json = converter.toJsonObject("<d><code>007</code><zip>0</zip></d>");

        assertEquals("007", json.getJSONObject("d").get("code"));
        assertEquals(0, json.getJSONObject("d").get("zip"));
    }

    @Test
    void shouldTrimElementTextAndKeepNonNumericAsRawString() {
        JSONObject json = converter.toJsonObject("<d><a>  padded  </a><b>1,5</b><c>abc</c></d>");

        assertEquals("padded", json.getJSONObject("d").get("a"));
        assertEquals("1,5", json.getJSONObject("d").get("b"));
        assertEquals("abc", json.getJSONObject("d").get("c"));
    }

    @Test
    void shouldDisableTypeInferenceWhenConfigured() {
        XmlJsonConverter strict = new XmlJsonConverter(false);
        JSONObject json = strict.toJsonObject("<d><i>42</i><b>true</b></d>");

        assertEquals("42", json.getJSONObject("d").get("i"));
        assertEquals("true", json.getJSONObject("d").get("b"));
    }

    @Test
    void shouldUseTextKeyForMixedContent() {
        JSONObject json = converter.toJsonObject("<p>Hello <b>World</b>!</p>");

        JSONObject p = json.getJSONObject("p");
        assertEquals("World", p.get("b"));
        assertEquals("Hello !", p.get(XmlJsonConverter.TEXT_KEY));
    }

    @Test
    void shouldSkipCommentsAndProcessingInstructions() {
        JSONObject json = converter.toJsonObject(
                "<a><!-- 注释 --><?guide value?><v>1</v></a>");

        JSONObject a = json.getJSONObject("a");
        assertEquals(1, a.size());
        assertEquals(1, a.get("v"));
    }

    @Test
    void shouldPreserveNamespacePrefixes() {
        JSONObject json = converter.toJsonObject(
                "<ns:user xmlns:ns=\"http://example.com/ns\"><ns:name>A</ns:name></ns:user>");

        JSONObject user = json.getJSONObject("ns:user");
        assertEquals("A", user.get("ns:name"));
        assertEquals("http://example.com/ns", user.get("@xmlns:ns"));
    }

    @Test
    void shouldHandleCdataAsText() {
        JSONObject json = converter.toJsonObject("<a><![CDATA[<not-a-tag>]]></a>");

        assertEquals("<not-a-tag>", json.get("a"));
    }

    @Test
    void shouldReturnEmptyStringForEmptyElement() {
        JSONObject json = converter.toJsonObject("<root/>");

        assertEquals("", json.get("root"));
    }

    @Test
    void shouldRejectDoctypeToPreventXxe() {
        String malicious = "<!DOCTYPE r [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><r>&xxe;</r>";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> converter.toJsonObject(malicious));

        assertTrue(error.getMessage().contains("XML 解析失败"));
    }

    @Test
    void shouldRejectMalformedXml() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.toJsonObject("<root><a></root>"));
    }
}
