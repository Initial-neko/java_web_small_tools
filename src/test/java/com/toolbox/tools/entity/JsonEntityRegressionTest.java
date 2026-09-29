package com.toolbox.tools.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonEntityRegressionTest {

    private final JsonEntitySchemaInferer inferer = new JsonEntitySchemaInferer();

    @Test
    void shouldRespectIntegerBoundariesAndHugeIntegers() {
        EntitySchema schema = inferer.infer(
                "{\"i\":2147483647,\"l1\":2147483648,\"l2\":9223372036854775807," +
                        "\"huge\":9223372036854775808123456789,\"negative\":-2147483649,\"decimal\":1.25}",
                "Numbers", "demo");

        assertEquals("Integer", field(schema, "i").getJavaType());
        assertEquals("Long", field(schema, "l1").getJavaType());
        assertEquals("Long", field(schema, "l2").getJavaType());
        assertEquals("BigInteger", field(schema, "huge").getJavaType());
        assertEquals("Long", field(schema, "negative").getJavaType());
        assertEquals("BigDecimal", field(schema, "decimal").getJavaType());
    }

    @Test
    void shouldPromoteArrayNumbersWithoutLosingPrecision() {
        EntitySchema schema = inferer.infer(
                "{\"integers\":[1,2147483648],\"huge\":[1,9223372036854775808123]," +
                        "\"decimals\":[1,2.25]}",
                "Numbers", "demo");

        assertEquals("List<Long>", field(schema, "integers").getJavaType());
        assertEquals("List<BigInteger>", field(schema, "huge").getJavaType());
        assertEquals("List<BigDecimal>", field(schema, "decimals").getJavaType());
    }

    @Test
    void shouldInferNestedArraysAndEmptyContainers() throws Exception {
        EntitySchema schema = inferer.infer(
                "{\"matrix\":[[1,2],[3,4]],\"emptyArray\":[],\"emptyObject\":{}}",
                "Containers", "demo");

        assertEquals("List<List<Integer>>", field(schema, "matrix").getJavaType());
        assertEquals("List<Object>", field(schema, "emptyArray").getJavaType());
        assertEquals("EmptyObject", field(schema, "emptyObject").getJavaType());

        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("demo.Containers", new NormalJavaRenderer().render(schema));
        ClassLoader loader = GeneratedSourceCompiler.compile(sources);
        assertNotNull(loader.loadClass("demo.Containers"));
        assertNotNull(loader.loadClass("demo.Containers$EmptyObject"));
    }

    @Test
    void shouldIgnoreNullSamplesWhenObjectArrayContainsConcreteValues() {
        EntitySchema schema = inferer.infer(
                "{\"items\":[{\"id\":null,\"amount\":1},{\"id\":2,\"amount\":2.5}]}",
                "Sample", "demo");

        EntitySchema item = field(schema, "items").getType().getItemType().getObjectSchema();
        assertEquals("Integer", field(item, "id").getJavaType());
        assertEquals("BigDecimal", field(item, "amount").getJavaType());
    }

    @Test
    void shouldNormalizeUppercaseDatabaseStyleNames() {
        EntitySchema schema = inferer.infer(
                "{\"USER_ID\":1,\"ORDER_TOTAL_AMOUNT\":2.5}",
                "ORDER_RESULT", "demo");

        assertEquals("OrderResult", schema.getClassName());
        assertEquals("userId", field(schema, "USER_ID").getFieldName());
        assertEquals("orderTotalAmount", field(schema, "ORDER_TOTAL_AMOUNT").getFieldName());
    }

    @Test
    void mappingShouldCompileAndExecuteForNestedArraysBigIntegerAndEscapedKeys() throws Exception {
        String jsonText =
                "{\"matrix\":[[1,2],[3,4]]," +
                "\"huge\":9223372036854775808123456789," +
                "\"a\\\"b\":\"quoted\"," +
                "\"path\\\\name\":7}";

        EntitySchema schema = inferer.infer(jsonText, "Complex", "demo");
        String entitySource = new NormalJavaRenderer().render(schema);
        String mappingMethod = new Fastjson2MappingRenderer().render(schema);
        String mapperSource =
                "package demo;\n" +
                "import com.alibaba.fastjson2.JSONObject;\n" +
                "public class ComplexMapper {\n" +
                indent(mappingMethod, "    ") +
                "}\n";

        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("demo.Complex", entitySource);
        sources.put("demo.ComplexMapper", mapperSource);

        ClassLoader loader = GeneratedSourceCompiler.compile(sources);
        Class<?> mapperClass = loader.loadClass("demo.ComplexMapper");
        Method fromJson = mapperClass.getMethod("fromJson", JSONObject.class);

        Object entity = fromJson.invoke(null, JSON.parseObject(jsonText));

        BigInteger huge = (BigInteger) entity.getClass().getMethod("getHuge").invoke(entity);
        assertEquals(new BigInteger("9223372036854775808123456789"), huge);

        List<?> matrix = (List<?>) entity.getClass().getMethod("getMatrix").invoke(entity);
        assertEquals(2, matrix.size());
        assertEquals(4, ((List<?>) matrix.get(1)).get(1));

        assertEquals("quoted", entity.getClass().getMethod("getAB").invoke(entity));
        assertEquals(7, entity.getClass().getMethod("getPathName").invoke(entity));
    }

    private FieldSchema field(EntitySchema schema, String sourceName) {
        for (FieldSchema field : schema.getFields()) {
            if (sourceName.equals(field.getSourceName())) return field;
        }
        throw new AssertionError("field not found: " + sourceName);
    }

    private String indent(String text, String prefix) {
        String[] lines = text.split("\\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty()) out.append(prefix).append(line);
            out.append("\n");
        }
        return out.toString();
    }
}
