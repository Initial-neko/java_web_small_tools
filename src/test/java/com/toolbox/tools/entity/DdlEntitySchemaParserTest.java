package com.toolbox.tools.entity;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdlEntitySchemaParserTest {

    private final DdlEntitySchemaParser parser = new DdlEntitySchemaParser();

    @Test
    void shouldParseDmCreateTableAndCompileGeneratedEntity() throws Exception {
        String ddl =
                "CREATE TABLE APP.T_ORDER (" +
                "ID NUMBER(10,0), " +
                "NAME VARCHAR2(100), " +
                "AMOUNT DECIMAL(18,2), " +
                "CREATED_AT TIMESTAMP, " +
                "BIRTH_DATE DATE, " +
                "PAYLOAD BLOB" +
                ")";

        EntitySchema schema = parser.parse(ddl, "dm", "", "demo.ddl");

        assertEquals("TOrder", schema.getClassName());
        assertEquals("Long", field(schema, "ID").getJavaType());
        assertEquals("String", field(schema, "NAME").getJavaType());
        assertEquals("BigDecimal", field(schema, "AMOUNT").getJavaType());
        assertEquals("LocalDateTime", field(schema, "CREATED_AT").getJavaType());
        assertEquals("LocalDate", field(schema, "BIRTH_DATE").getJavaType());
        assertEquals("byte[]", field(schema, "PAYLOAD").getJavaType());

        String source = new NormalJavaRenderer().render(schema);
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("demo.ddl.TOrder", source);
        ClassLoader loader = GeneratedSourceCompiler.compile(sources);

        Class<?> type = loader.loadClass("demo.ddl.TOrder");
        assertNotNull(type.getMethod("getId"));
        assertNotNull(type.getMethod("getPayload"));
    }

    @Test
    void shouldParseOracleCreateTable() {
        String ddl =
                "CREATE TABLE APP.T_CUSTOMER (" +
                "CUSTOMER_ID NUMBER(18,0), " +
                "CUSTOMER_NAME VARCHAR2(64), " +
                "CREATED_AT TIMESTAMP" +
                ")";

        EntitySchema schema = parser.parse(ddl, "oracle", "CustomerRow", "demo");

        assertEquals("CustomerRow", schema.getClassName());
        assertEquals("Long", field(schema, "CUSTOMER_ID").getJavaType());
        assertEquals("String", field(schema, "CUSTOMER_NAME").getJavaType());
        assertEquals("LocalDateTime", field(schema, "CREATED_AT").getJavaType());
    }

    @Test
    void shouldRejectNonCreateTableDdl() {
        try {
            parser.parse("SELECT 1 FROM DUAL", "oracle", "X", "demo");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("CREATE TABLE"));
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private FieldSchema field(EntitySchema schema, String sourceName) {
        for (FieldSchema field : schema.getFields()) {
            if (sourceName.equalsIgnoreCase(field.getSourceName())) return field;
        }
        throw new AssertionError("field not found: " + sourceName);
    }
}
