package com.toolbox.tools.sqllineage;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DmSqlAnalyzeTool implements Tool {

    private final DmSqlAnalyzer analyzer = new DmSqlAnalyzer();

    @Override
    public String getName() {
        return "dm-sql-analyze";
    }

    @Override
    public String getDisplayName() {
        return "达梦 SQL 分析";
    }

    @Override
    public String getDescription() {
        return "Druid DM 方言解析：Statement、读写表、字段与解析错误";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String sql = str(params.get("sql"), "");
        String sqlId = str(params.get("sqlId"), "manual");

        SqlInput input = new SqlInput(sqlId, sql);
        input.setSource(str(params.get("source"), "toolbox"));

        SqlAnalysisResult result = analyzer.analyze(input);
        if (result.getStatus() == SqlParseStatus.PARSE_FAILED) {
            return ToolResult.fail(result.getErrors().isEmpty()
                    ? "SQL 解析失败"
                    : result.getErrors().get(0));
        }
        return ToolResult.ok(result);
    }

    private String str(Object value, String def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }
}
