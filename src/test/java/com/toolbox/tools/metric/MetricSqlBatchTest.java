package com.toolbox.tools.metric;

import com.toolbox.core.ToolResult;
import com.toolbox.tools.sqllineage.SqlInput;
import com.toolbox.tools.sqllineage.SqlParseStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricSqlBatchTest {
    @Test
    void shouldPreserveLiteralCaseWhitespaceAndEscapedQuotes() {
        String[] values = {"'PAID'", "'paid'", "'a  b'", "'a b'", "'It''s A'", "'It''s a'"};
        java.util.List<SqlInput> inputs = new java.util.ArrayList<SqlInput>();
        for (int i = 0; i < values.length; i++) {
            inputs.add(new SqlInput("literal-" + i,
                    "SELECT SUM(CASE WHEN state=" + values[i] + " THEN amount ELSE 0 END) AS m FROM APP.T"));
        }
        BatchMetricProbeResult actual = new MetricSqlAnalyzer().analyzeBatch(inputs);
        assertEquals(6, actual.getSuccess());
        assertEquals(0, actual.getDuplicateGroups().size(), "different literal semantics are not duplicate expressions");
    }

    @Test
    void shouldPreserveQuotedIdentifierCase() {
        BatchMetricProbeResult actual = new MetricSqlAnalyzer().analyzeBatch(Arrays.asList(
                new SqlInput("upper", "SELECT SUM(\"Amount\") AS m FROM APP.T"),
                new SqlInput("lower", "SELECT SUM(\"amount\") AS n FROM APP.T")));
        assertEquals(2, actual.getSuccess());
        assertTrue(actual.getDuplicateGroups().isEmpty());
    }

    @Test
    void shouldStillNormalizeUnquotedIdentifierCaseAndFormatting() {
        BatchMetricProbeResult actual = new MetricSqlAnalyzer().analyzeBatch(Arrays.asList(
                new SqlInput("a", "SELECT SUM(amount) AS a FROM APP.T"),
                new SqlInput("b", "select sum( AMOUNT ) AS b from APP.T")));
        assertEquals(1, actual.getDuplicateGroups().size());
        assertEquals(2, actual.getDuplicateGroups().get(0).getOccurrenceCount());
    }

    @Test
    void shouldDetectDuplicateCalculationsAcrossSqlAliases() {
        SqlInput a = new SqlInput("sql-a",
                "SELECT SUM(amount) AS total_amount FROM APP.DWD_ORDER");
        SqlInput b = new SqlInput("sql-b",
                "SELECT SUM(amount) AS sale_amount FROM APP.DWD_ORDER");

        BatchMetricProbeResult batch = new MetricSqlAnalyzer().analyzeBatch(Arrays.asList(a, b));

        assertEquals(2, batch.getSuccess());
        assertEquals(2, batch.getMetricCount());
        assertEquals(1, batch.getDuplicateGroups().size());

        MetricDuplicateGroup group = batch.getDuplicateGroups().get(0);
        assertEquals(2, group.getOccurrenceCount());
        assertTrue(group.getMetricNames().contains("total_amount"));
        assertTrue(group.getMetricNames().contains("sale_amount"));
        assertTrue(group.getSqlIds().contains("sql-a"));
        assertTrue(group.getSqlIds().contains("sql-b"));
    }

    @Test
    void shouldKeepBatchRunningWhenOneSqlFails() {
        SqlInput good = new SqlInput("good",
                "SELECT COUNT(*) AS order_count FROM APP.DWD_ORDER");
        SqlInput bad = new SqlInput("bad",
                "SELECT SUM( FROM APP.DWD_ORDER");

        BatchMetricProbeResult batch = new MetricSqlAnalyzer().analyzeBatch(Arrays.asList(good, bad));

        assertEquals(2, batch.getTotal());
        assertEquals(1, batch.getSuccess());
        assertEquals(1, batch.getFailed());
        assertEquals(SqlParseStatus.PARSE_FAILED, batch.getResults().get(1).getStatus());
    }

    @Test
    void shouldMarkNonSelectAsUnsupportedInsteadOfInventingMetrics() {
        MetricProbeResult result = new MetricSqlAnalyzer().analyze(
                new SqlInput("update", "UPDATE APP.T SET AMOUNT = AMOUNT + 1"));

        assertEquals(SqlParseStatus.UNSUPPORTED, result.getStatus());
        assertTrue(result.getMetrics().isEmpty());
    }

    @Test
    void batchToolShouldReuseExistingBatchInputFormat() {
        String input = "[" +
                "{\"sqlId\":\"m1\",\"sql\":\"SELECT SUM(amount) AS total_amount FROM APP.T\"}," +
                "{\"sqlId\":\"m2\",\"sql\":\"SELECT COUNT(*) AS cnt FROM APP.T\"}" +
                "]";

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", input);

        ToolResult result = new MetricSqlBatchTool().execute(params);

        assertTrue(result.isSuccess());
        BatchMetricProbeResult data = (BatchMetricProbeResult) result.getData();
        assertEquals(2, data.getTotal());
        assertEquals(2, data.getMetricCount());
    }

    @Test
    void batchToolShouldRejectEmptyInput() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", " ");

        ToolResult result = new MetricSqlBatchTool().execute(params);

        assertFalse(result.isSuccess());
    }
}
