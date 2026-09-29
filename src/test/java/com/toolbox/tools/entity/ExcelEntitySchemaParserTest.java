package com.toolbox.tools.entity;

import com.toolbox.tools.excel.ExcelFile;
import com.toolbox.tools.excel.ExcelParser;
import com.toolbox.tools.excel.ExcelSheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelEntitySchemaParserTest {

    private final ExcelEntitySchemaParser parser = new ExcelEntitySchemaParser();

    @Test
    void shouldParseRealXlsxIntoEntitySchemaAndCompileNormalAndLombokSources() throws Exception {
        ExcelSheet sheet = parseWorkbook(
                new String[]{"USER_ID", "AMOUNT", "CREATED_AT", "BIRTH_DATE", "DISPLAY_NAME", "CLASS"},
                new String[]{"NUMBER(10,0)", "DECIMAL(18,2)", "TIMESTAMP", "DATE", "VARCHAR2(100)", "VARCHAR(20)"},
                new String[]{"用户ID", "金额 */ 注释", "创建时间", "生日", "显示名称", "Java关键字"}
        );

        EntitySchema schema = parser.parse(sheet, "ORDER_ROW", "demo.excel", 1, 2, 3);

        assertEquals("OrderRow", schema.getClassName());
        assertEquals("Long", field(schema, "USER_ID").getJavaType());
        assertEquals("BigDecimal", field(schema, "AMOUNT").getJavaType());
        assertEquals("LocalDateTime", field(schema, "CREATED_AT").getJavaType());
        assertEquals("LocalDate", field(schema, "BIRTH_DATE").getJavaType());
        assertEquals("String", field(schema, "DISPLAY_NAME").getJavaType());
        assertEquals("classValue", field(schema, "CLASS").getFieldName());
        assertEquals("用户ID", field(schema, "USER_ID").getComment());

        String normal = new NormalJavaRenderer().render(schema);
        assertTrue(normal.contains("金额 * / 注释"));
        Map<String, String> normalSources = new LinkedHashMap<String, String>();
        normalSources.put("demo.excel.OrderRow", normal);
        ClassLoader normalLoader = GeneratedSourceCompiler.compile(normalSources);
        assertNotNull(normalLoader.loadClass("demo.excel.OrderRow").getMethod("getUserId"));

        String lombok = new LombokJavaRenderer().render(schema);
        Map<String, String> lombokSources = new LinkedHashMap<String, String>();
        lombokSources.put("demo.excel.OrderRow", lombok);
        ClassLoader lombokLoader = GeneratedSourceCompiler.compile(lombokSources);
        assertNotNull(lombokLoader.loadClass("demo.excel.OrderRow").getMethod("getAmount"));
    }

    @Test
    void shouldSupportConfigurableRowsAndDefaultMissingTypesToString() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet poiSheet = workbook.createSheet("Schema");
        poiSheet.createRow(0).createCell(0).setCellValue("说明行");
        Row header = poiSheet.createRow(1);
        header.createCell(0).setCellValue("USER_NAME");
        header.createCell(1).setCellValue("AGE");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        ExcelFile excel = new ExcelParser().parse(
                new ByteArrayInputStream(out.toByteArray()), "schema.xlsx");

        EntitySchema schema = parser.parse(excel.getSheets().get(0), "USER_INFO", "demo", 2, 0, 0);

        assertEquals("UserInfo", schema.getClassName());
        assertEquals("String", field(schema, "USER_NAME").getJavaType());
        assertEquals("String", field(schema, "AGE").getJavaType());
    }

    @Test
    void shouldMapCommonJavaAndDatabaseTypeNames() {
        assertType("String", "VARCHAR2(200)");
        assertType("Integer", "INT");
        assertType("Long", "BIGINT");
        assertType("BigInteger", "NUMBER(30,0)");
        assertType("BigDecimal", "NUMBER");
        assertType("BigDecimal", "NUMBER(18,2)");
        assertType("Boolean", "BOOLEAN");
        assertType("LocalDate", "DATE");
        assertType("LocalDateTime", "TIMESTAMP(6)");
        assertType("Object", "CUSTOM_TYPE");
    }

    private ExcelSheet parseWorkbook(String[] headers, String[] types, String[] comments) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet poiSheet = workbook.createSheet("Schema");

        writeRow(poiSheet.createRow(0), headers);
        writeRow(poiSheet.createRow(1), types);
        writeRow(poiSheet.createRow(2), comments);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        ExcelFile excel = new ExcelParser().parse(
                new ByteArrayInputStream(out.toByteArray()), "schema.xlsx");
        return excel.getSheets().get(0);
    }

    private void writeRow(Row row, String[] values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private void assertType(String expected, String sourceType) {
        assertEquals(expected, parser.parseType(sourceType).toJavaType(), sourceType);
    }

    private FieldSchema field(EntitySchema schema, String sourceName) {
        for (FieldSchema field : schema.getFields()) {
            if (sourceName.equals(field.getSourceName())) return field;
        }
        throw new AssertionError("field not found: " + sourceName);
    }
}
