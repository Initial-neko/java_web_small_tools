package com.toolbox.tools.sqllineage;

import com.toolbox.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmSqlAnalyzeToolTest {

    private final DmSqlAnalyzeTool tool = new DmSqlAnalyzeTool();

    @Test
    void shouldExposeSuccessfulSingleSqlAnalysis() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sqlId", "manual-1");
        params.put("source", "junit");
        params.put("sql", "SELECT ID FROM APP.ODS_ORDER");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        SqlAnalysisResult data = (SqlAnalysisResult) result.getData();
        assertTrue(data.getReadTables().size() >= 1);
    }

    @Test
    void shouldReturnReadableFailureForInvalidSql() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sql", "SELECT FROM WHERE ;");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage() != null && !result.getMessage().trim().isEmpty());
    }
}
