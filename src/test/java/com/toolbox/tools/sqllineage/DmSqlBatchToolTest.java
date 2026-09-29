package com.toolbox.tools.sqllineage;

import com.toolbox.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmSqlBatchToolTest {

    @Test
    void shouldParseJsonBatchAndKeepFailureIsolation() {
        String input = "[" +
                "{\"sqlId\":\"read-1\",\"source\":\"job-a\",\"sql\":\"SELECT ID FROM APP.ODS_ORDER\"}," +
                "{\"sqlId\":\"bad-1\",\"sql\":\"SELECT FROM WHERE ;\"}," +
                "{\"sqlId\":\"merge-1\",\"source\":\"job-b\",\"sql\":\"MERGE INTO APP.DWD_ORDER t USING APP.ODS_ORDER s ON (t.ORDER_ID=s.ID) WHEN MATCHED THEN UPDATE SET t.AMOUNT=s.AMOUNT\"}" +
                "]";

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", input);

        ToolResult toolResult = new DmSqlBatchTool().execute(params);

        assertTrue(toolResult.isSuccess());
        BatchSqlAnalysisResult batch = (BatchSqlAnalysisResult) toolResult.getData();

        assertEquals(3, batch.getTotal());
        assertEquals(2, batch.getSuccess());
        assertEquals(1, batch.getFailed());
        assertTrue(containsIgnoreCase(batch.getReadTables(), "APP.ODS_ORDER"));
        assertTrue(containsIgnoreCase(batch.getWriteTables(), "APP.DWD_ORDER"));

        TableLineageEdge edge = findEdge(batch.getTableLineageEdges(), "APP.ODS_ORDER", "APP.DWD_ORDER");
        assertTrue(edge.getSqlIds().contains("merge-1"));
    }

    @Test
    void shouldParseDelimitedMultilineBatch() {
        String input =
                "SELECT ID, NAME\nFROM APP.CUSTOMER;\n" +
                "-- @SQL\n" +
                "SELECT ID\nFROM APP.ODS_ORDER;";

        List<SqlInput> items = new BatchSqlInputParser().parse(input);

        assertEquals(2, items.size());
        assertEquals("sql-0001", items.get(0).getSqlId());
        assertTrue(items.get(0).getSql().contains("APP.CUSTOMER"));
        assertEquals("sql-0002", items.get(1).getSqlId());
    }

    @Test
    void shouldDeduplicateTableEdgesAndCollectSqlIds() {
        DmSqlAnalyzer analyzer = new DmSqlAnalyzer();

        SqlInput first = new SqlInput("merge-a",
                "MERGE INTO APP.DWD_ORDER t USING APP.ODS_ORDER s ON (t.ORDER_ID=s.ID) " +
                        "WHEN MATCHED THEN UPDATE SET t.AMOUNT=s.AMOUNT");
        SqlInput second = new SqlInput("merge-b",
                "MERGE INTO APP.DWD_ORDER t USING APP.ODS_ORDER s ON (t.ORDER_ID=s.ID) " +
                        "WHEN MATCHED THEN UPDATE SET t.AMOUNT=s.AMOUNT");

        BatchSqlAnalysisResult batch = analyzer.analyzeBatch(java.util.Arrays.asList(first, second));

        assertEquals(1, batch.getTableLineageEdges().size());
        TableLineageEdge edge = batch.getTableLineageEdges().get(0);
        assertEquals(2, edge.getSqlIds().size());
    }

    @Test
    void shouldRejectEmptyBatch() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", " ");

        ToolResult result = new DmSqlBatchTool().execute(params);

        assertFalse(result.isSuccess());
    }

    private TableLineageEdge findEdge(List<TableLineageEdge> edges, String source, String target) {
        for (TableLineageEdge edge : edges) {
            if (edge.getSourceTable().equalsIgnoreCase(source)
                    && edge.getTargetTable().equalsIgnoreCase(target)) {
                return edge;
            }
        }
        throw new AssertionError("edge not found: " + source + " -> " + target + ", actual=" + edges.size());
    }

    private boolean containsIgnoreCase(java.util.Set<String> values, String expected) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }
}
