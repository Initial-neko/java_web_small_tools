package com.toolbox.tools.metric;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.toolbox.tools.sqllineage.SqlInput;
import com.toolbox.tools.sqllineage.SqlParseStatus;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricSqlAnalyzerFixtureTest {

    private final MetricSqlAnalyzer analyzer = new MetricSqlAnalyzer();

    @Test
    void shouldMatchMetricFixtures() throws Exception {
        List<String> cases = Arrays.asList(
                "001-basic",
                "002-conditional",
                "003-plain-select",
                "004-cte",
                "005-window",
                "006-invalid"
        );

        for (String name : cases) {
            String sql = resource("/sql/metric/dm/" + name + ".sql");
            JSONObject expected = JSON.parseObject(
                    resource("/sql/metric/dm/" + name + ".expected.json"));

            MetricProbeResult actual = analyzer.analyze(new SqlInput(name, sql));

            assertEquals(
                    SqlParseStatus.valueOf(expected.getString("status")),
                    actual.getStatus(),
                    name + " status");
            assertEquals(expected.getIntValue("metricCount"), actual.getMetrics().size(),
                    name + " metric count");

            JSONArray dimensions = expected.getJSONArray("dimensionNames");
            if (dimensions != null) {
                for (int i = 0; i < dimensions.size(); i++) {
                    assertTrue(hasDimension(actual, dimensions.getString(i)),
                            name + " missing dimension " + dimensions.getString(i));
                }
            }

            JSONArray metrics = expected.getJSONArray("metrics");
            for (int i = 0; i < metrics.size(); i++) {
                JSONObject expectedMetric = metrics.getJSONObject(i);
                MetricCandidate actualMetric = metric(actual, expectedMetric.getString("name"));

                assertEquals(
                        MetricType.valueOf(expectedMetric.getString("type")),
                        actualMetric.getType(),
                        name + " metric type " + actualMetric.getName());

                String aggregation = expectedMetric.getString("aggregation");
                if (aggregation != null) {
                    assertEquals(aggregation, actualMetric.getAggregation(),
                            name + " aggregation " + actualMetric.getName());
                }
                assertNotNull(actualMetric.getNormalizedExpression());
            }

            if (actual.getStatus() == SqlParseStatus.PARSE_FAILED) {
                assertTrue(!actual.getErrors().isEmpty(),
                        name + " parse failure must expose diagnostics");
            }
        }
    }

    @Test
    void basicMetricShouldCarryFilterGrainAndSourceColumns() throws Exception {
        String sql = resource("/sql/metric/dm/001-basic.sql");
        MetricProbeResult result = analyzer.analyze(new SqlInput("basic", sql));

        MetricCandidate total = metric(result, "total_sales");
        assertTrue(containsColumn(total, "sale_amount"));
        assertTrue(total.getDimensions().contains("dept_id"));
        assertTrue(total.getWhereCondition().toUpperCase().contains("ORDER_STATUS"));
        assertTrue(total.getHavingCondition().toUpperCase().contains("SUM"));
        assertTrue(!total.getSourceTables().isEmpty());

        MetricCandidate ratio = metric(result, "profit_rate");
        assertTrue(containsColumn(ratio, "profit"));
        assertTrue(containsColumn(ratio, "sale_amount"));

        MetricCandidate count = metric(result, "order_count");
        assertTrue(count.isDistinct());
        assertTrue(count.getReasons().toString().contains("DISTINCT"));
    }

    private boolean hasDimension(MetricProbeResult result, String name) {
        for (DimensionCandidate dimension : result.getDimensions()) {
            if (name.equalsIgnoreCase(dimension.getName())) return true;
        }
        return false;
    }

    private MetricCandidate metric(MetricProbeResult result, String name) {
        for (MetricCandidate metric : result.getMetrics()) {
            if (name.equalsIgnoreCase(metric.getName())) return metric;
        }
        throw new AssertionError("metric not found: " + name + ", actual=" + result.getMetrics().size());
    }

    private boolean containsColumn(MetricCandidate metric, String expectedTail) {
        for (String column : metric.getSourceColumns()) {
            if (column != null && column.toLowerCase().endsWith(expectedTail.toLowerCase())) {
                return true;
            }
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
