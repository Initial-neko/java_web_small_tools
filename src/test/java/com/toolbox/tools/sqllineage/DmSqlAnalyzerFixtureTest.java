package com.toolbox.tools.sqllineage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmSqlAnalyzerFixtureTest {

    private final DmSqlAnalyzer analyzer = new DmSqlAnalyzer();

    @Test
    void shouldMatchDmSqlFixtureExpectations() throws Exception {
        List<String> cases = Arrays.asList(
                "001-select-join",
                "002-insert-select",
                "003-listagg",
                "004-merge",
                "005-invalid"
        );

        for (String name : cases) {
            String sql = resource("/sql/dm/" + name + ".sql");
            JSONObject expected = JSON.parseObject(
                    resource("/sql/dm/" + name + ".expected.json"));

            SqlAnalysisResult actual = analyzer.analyze(new SqlInput(name, sql));

            SqlParseStatus expectedStatus = SqlParseStatus.valueOf(expected.getString("status"));
            assertEquals(expectedStatus, actual.getStatus(), name + " status");
            if (expectedStatus == SqlParseStatus.PARSE_FAILED) {
                assertTrue(!actual.getErrors().isEmpty(), name + " parse failure must contain error");
            }

            JSONArray types = expected.getJSONArray("statementTypes");
            assertEquals(types.size(), actual.getStatementTypes().size(), name + " statement type count");
            for (int i = 0; i < types.size(); i++) {
                assertEquals(types.getString(i), actual.getStatementTypes().get(i), name + " statement type");
            }

            assertContainsIgnoreCase(
                    actual.getReadTables(),
                    expected.getJSONArray("containsReadTables"),
                    name + " read tables");

            assertContainsIgnoreCase(
                    actual.getWriteTables(),
                    expected.getJSONArray("containsWriteTables"),
                    name + " write tables");
        }
    }

    @Test
    void batchShouldIsolateBadSqlAndKeepInputIdentity() {
        List<SqlInput> inputs = new ArrayList<SqlInput>();

        SqlInput good1 = new SqlInput("good-select",
                "SELECT ID, AMOUNT FROM APP.ODS_ORDER WHERE STATUS = 1");
        good1.setSource("scheduler-a");

        SqlInput bad = new SqlInput("bad-sql",
                "SELECT FROM APP.ODS_ORDER WHERE ;");
        bad.setSource("scheduler-b");

        SqlInput good2 = new SqlInput("good-insert",
                "INSERT INTO APP.DWD_ORDER (ORDER_ID) SELECT ID FROM APP.ODS_ORDER");
        good2.setSource("scheduler-c");

        inputs.add(good1);
        inputs.add(bad);
        inputs.add(good2);

        BatchSqlAnalysisResult batch = analyzer.analyzeBatch(inputs);

        assertEquals(3, batch.getTotal());
        assertEquals(2, batch.getSuccess());
        assertEquals(1, batch.getFailed());
        assertEquals(3, batch.getResults().size());

        assertEquals("good-select", batch.getResults().get(0).getSqlId());
        assertEquals("scheduler-a", batch.getResults().get(0).getSource());

        assertEquals("bad-sql", batch.getResults().get(1).getSqlId());
        assertEquals(SqlParseStatus.PARSE_FAILED, batch.getResults().get(1).getStatus());

        assertEquals("good-insert", batch.getResults().get(2).getSqlId());
        assertEquals(SqlParseStatus.SUCCESS, batch.getResults().get(2).getStatus());
    }

    @Test
    void shouldAnalyzeMultipleStatementsInOneInput() {
        String sql =
                "SELECT ID FROM APP.ODS_ORDER;" +
                "INSERT INTO APP.DWD_ORDER (ORDER_ID) SELECT ID FROM APP.ODS_ORDER;";

        SqlAnalysisResult result = analyzer.analyze(new SqlInput("multi", sql));

        assertEquals(SqlParseStatus.SUCCESS, result.getStatus());
        assertEquals(2, result.getStatementCount());
        assertEquals(Arrays.asList("SELECT", "INSERT"), result.getStatementTypes());
        assertTrue(containsIgnoreCase(result.getReadTables(), "APP.ODS_ORDER"));
        assertTrue(containsIgnoreCase(result.getWriteTables(), "APP.DWD_ORDER"));
    }

    private void assertContainsIgnoreCase(Set<String> actual, JSONArray expected, String message) {
        for (int i = 0; i < expected.size(); i++) {
            String value = expected.getString(i);
            assertTrue(containsIgnoreCase(actual, value),
                    message + ": expected to contain " + value + ", actual=" + actual);
        }
    }

    private boolean containsIgnoreCase(Set<String> values, String expected) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    private String resource(String path) throws Exception {
        InputStream in = getClass().getResourceAsStream(path);
        if (in == null) throw new IllegalArgumentException("resource not found: " + path);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line).append('\n');
        }
        reader.close();
        return out.toString();
    }
}
