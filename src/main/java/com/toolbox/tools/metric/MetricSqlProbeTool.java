package com.toolbox.tools.metric;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import com.toolbox.tools.sqllineage.SqlInput;
import com.toolbox.tools.sqllineage.SqlParseStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MetricSqlProbeTool implements Tool {

    private final MetricSqlAnalyzer analyzer = new MetricSqlAnalyzer();

    @Override
    public String getName() {
        return "sql-metric-probe";
    }

    @Override
    public String getDisplayName() {
        return "SQL 指标探查";
    }

    @Override
    public String getDescription() {
        return "从达梦 SELECT/WITH SQL 中确定性识别指标候选、维度、过滤口径和来源";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String sql = text(params.get("sql"), "");
        String sqlId = text(params.get("sqlId"), "manual");
        String source = text(params.get("source"), "toolbox");

        SqlInput input = new SqlInput(sqlId, sql);
        input.setSource(source);
        MetricProbeResult result = analyzer.analyze(input);

        if (result.getStatus() == SqlParseStatus.PARSE_FAILED) {
            return ToolResult.fail(result.getErrors().isEmpty()
                    ? "指标探查失败"
                    : result.getErrors().get(0));
        }
        return ToolResult.ok(result);
    }

    private String text(Object value, String def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }
}
