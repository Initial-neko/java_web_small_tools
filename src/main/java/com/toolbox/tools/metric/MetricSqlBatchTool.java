package com.toolbox.tools.metric;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import com.toolbox.tools.sqllineage.BatchSqlInputParser;
import com.toolbox.tools.sqllineage.SqlInput;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MetricSqlBatchTool implements Tool {

    private final BatchSqlInputParser inputParser = new BatchSqlInputParser();
    private final MetricSqlAnalyzer analyzer = new MetricSqlAnalyzer();

    @Override
    public String getName() {
        return "sql-metric-batch";
    }

    @Override
    public String getDisplayName() {
        return "SQL 批量指标探查";
    }

    @Override
    public String getDescription() {
        return "批量识别指标候选并聚合重复计算；单条 SQL 失败不终止整批";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String input = params.get("input") == null ? "" : String.valueOf(params.get("input"));
            List<SqlInput> items = inputParser.parse(input);
            return ToolResult.ok(analyzer.analyzeBatch(items));
        } catch (Exception e) {
            return ToolResult.fail("批量指标探查失败: " + rootMessage(e));
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName()
                : message;
    }
}
