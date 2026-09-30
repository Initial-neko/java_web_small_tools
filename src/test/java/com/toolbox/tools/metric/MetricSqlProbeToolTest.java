package com.toolbox.tools.metric;

import com.toolbox.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricSqlProbeToolTest {

    @Test
    void shouldExposeMetricProbeThroughToolRegistryContract() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sqlId", "manual-1");
        params.put("source", "junit");
        params.put("sql", "SELECT dept_id, SUM(amount) AS total_amount FROM APP.T GROUP BY dept_id");

        ToolResult result = new MetricSqlProbeTool().execute(params);

        assertTrue(result.isSuccess());
        MetricProbeResult data = (MetricProbeResult) result.getData();
        assertTrue(data.getMetrics().size() == 1);
        assertTrue(data.getDimensions().size() == 1);
    }

    @Test
    void shouldReturnReadableFailureForBrokenSql() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sql", "SELECT SUM( FROM APP.T");

        ToolResult result = new MetricSqlProbeTool().execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage() != null && !result.getMessage().trim().isEmpty());
    }
}
