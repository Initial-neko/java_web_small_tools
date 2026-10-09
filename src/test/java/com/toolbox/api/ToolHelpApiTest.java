package com.toolbox.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import java.io.ByteArrayOutputStream;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.main.headless=false")
@AutoConfigureMockMvc
class ToolHelpApiTest {
    // Set before SpringApplication starts: its initial AWT default precedes property binding.
    static { System.setProperty("java.awt.headless", "false"); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode read(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test void shouldSeparateTaskNavigationFromCapabilityDiscovery() throws Exception {
        JsonNode catalog = read("/api/help");
        // 7 = json / text / entity / sql / metric / lineage / time
        assertEquals(7, catalog.path("pages").size());
        Set<String> documented = new HashSet<String>();
        for (JsonNode tool : catalog.path("tools")) documented.add(tool.path("name").asText());
        for (JsonNode tool : read("/api/tools")) assertTrue(documented.contains(tool.path("name").asText()));
        assertEquals("1.0", catalog.path("schemaVersion").asText());
        assertEquals(2, catalog.path("advancedPages").size());
        for (JsonNode page : catalog.path("pages")) {
            assertFalse(page.path("tools").toString().contains("mybatis-generator"));
            assertFalse(page.path("tools").toString().contains("ip-port-checker"));
        }
    }

    @Test void shouldReturnSingleHelpAnd404ForUnknownCapability() throws Exception {
        JsonNode help = read("/api/tools/json-to-java/help");
        assertEquals("json-to-java", help.path("name").asText());
        assertTrue(help.path("endpoints").get(0).path("parameters").size() >= 4);
        mvc.perform(get("/api/tools/does-not-exist/help")).andExpect(status().isNotFound());
    }

    @Test void shouldResolveLineageSqlsByTableNameNotOnlyBySqlName() throws Exception {
        // /sqls/{name} 既要能按 SQL 名取单条明细，也要能按表名取该表的全部产出 SQL。
        JsonNode overview = read("/api/lineage-viewer/overview").path("data");
        assertTrue(overview.path("tableCount").asInt() > 0, "内置演示数据应已建图");
        assertEquals(overview.path("tableCount").asInt() > 0, true);

        int checkedTables = 0;
        int checkedSqls = 0;
        for (JsonNode leaf : read("/api/lineage-viewer/tables/leaves").path("data")) {
            String table = leaf.path("name").asText();
            JsonNode byTable = read("/api/lineage-viewer/sqls/" + table).path("data");
            assertTrue(byTable.isArray() && byTable.size() > 0, "末端表 " + table + " 应能回溯到产出 SQL");
            for (JsonNode sql : byTable) {
                assertFalse(sql.path("sql").isNull(), "产出 SQL 应带正文");
                assertTrue(sql.path("outputTables").isArray());
                checkedSqls++;
            }
            // 反向：拿其中一条 SQL 的名字去查，应得到同样这条记录
            String sqlName = byTable.get(0).path("name").asText();
            JsonNode bySql = read("/api/lineage-viewer/sqls/" + sqlName).path("data");
            assertTrue(bySql.isArray() && bySql.size() == 1, "按 SQL 名应只命中一条");
            assertEquals(sqlName, bySql.get(0).path("name").asText());
            if (++checkedTables >= 5) break;
        }
        assertTrue(checkedTables == 5 && checkedSqls >= 5, "至少验证 5 张末端表");
    }

    @Test void shouldDocumentActualMyBatisBooleanDefaults() throws Exception {
        Set<String> checked = new HashSet<String>();
        for (JsonNode param : read("/api/tools/mybatis-generator/help").path("endpoints").get(0).path("parameters")) {
            String name = param.path("name").asText();
            if (name.equals("forceBigDecimals") || name.equals("trimStrings") || name.equals("useActualColumnNames")) {
                assertTrue(param.path("default").isBoolean(), name);
                assertFalse(param.path("default").asBoolean()); checked.add(name);
            }
        }
        assertEquals(3, checked.size());
    }

    @Test void shouldExecuteEveryDocumentedLocalJsonExample() throws Exception {
        int checked = 0;
        for (JsonNode tool : read("/api/help").path("tools")) {
            for (JsonNode endpoint : tool.path("endpoints")) {
                if (!endpoint.path("testable").asBoolean()) continue;
                for (JsonNode example : endpoint.path("examples")) {
                    String url = endpoint.path("path").asText();
                    java.util.Iterator<java.util.Map.Entry<String,JsonNode>> paths = example.path("path").fields();
                    while (paths.hasNext()) {
                        java.util.Map.Entry<String,JsonNode> p = paths.next();
                        url = url.replace("{" + p.getKey() + "}", p.getValue().asText());
                    }
                    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                            "GET".equals(endpoint.path("method").asText()) ? get(url) : post(url)
                            .contentType(MediaType.APPLICATION_JSON).content(example.path("body").toString());
                    java.util.Iterator<java.util.Map.Entry<String,JsonNode>> queries = example.path("query").fields();
                    while (queries.hasNext()) {
                        java.util.Map.Entry<String,JsonNode> p = queries.next();
                        request.param(p.getKey(), p.getValue().asText());
                    }
                    JsonNode result = mapper.readTree(mvc.perform(request)
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                    assertTrue(result.path("success").asBoolean(), tool.path("name") + ": " + result);
                    assertFalse(result.path("data").isNull());
                    checked++;
                }
            }
        }
        assertTrue(checked >= 14, "Must verify actual examples, including multiple modes");
    }

    @Test void shouldDescribeExcelWorkflowWithoutSuggestingPlaceholderExecute() throws Exception {
        JsonNode help = read("/api/tools/excel-viewer/help");
        assertEquals(4, help.path("endpoints").size());
        assertEquals("multipart/form-data", help.path("endpoints").get(0).path("contentType").asText());
        assertEquals("file", help.path("endpoints").get(0).path("parameters").get(0).path("name").asText());
        assertFalse(help.toString().contains("/api/tools/excel-viewer/execute"));
    }

    @Test void shouldExecuteDocumentedExcelUploadPreviewGenerateAndCleanup() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("User");
            sheet.createRow(0).createCell(0).setCellValue("USER_ID");
            sheet.createRow(1).createCell(0).setCellValue("NUMBER(10,0)");
            sheet.createRow(2).createCell(0).setCellValue("用户编号");
            workbook.write(buffer); bytes = buffer.toByteArray();
        }
        JsonNode endpoints = read("/api/tools/excel-viewer/help").path("endpoints");
        JsonNode upload = mapper.readTree(mvc.perform(multipart(endpoints.get(0).path("path").asText())
                .file(new MockMultipartFile("file", "schema.xlsx", "application/octet-stream", bytes)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(upload.path("success").asBoolean());
        String fileId = upload.path("fileId").asText();
        String previewPath = endpoints.get(1).path("path").asText().replace("{fileId}", fileId).replace("{sheetIndex}", "0");
        assertEquals(3, read(previewPath).path("totalRows").asInt());
        JsonNode generated = mapper.readTree(mvc.perform(post(endpoints.get(2).path("path").asText().replace("{fileId}", fileId))
                .contentType(MediaType.APPLICATION_JSON).content(endpoints.get(2).path("examples").get(0).path("body").toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(generated.path("success").asBoolean());
        assertTrue(generated.path("output").asText().contains("class User"));
        assertEquals(1, generated.path("fieldCount").asInt());
        mvc.perform(delete(endpoints.get(3).path("path").asText().replace("{fileId}", fileId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertFalse(read(previewPath).path("success").asBoolean());
    }
}
