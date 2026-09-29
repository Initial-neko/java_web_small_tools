package com.toolbox.tools.sqllineage;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DmSqlBatchTool implements Tool {

    private final BatchSqlInputParser inputParser = new BatchSqlInputParser();
    private final DmSqlAnalyzer analyzer = new DmSqlAnalyzer();

    @Override
    public String getName() {
        return "dm-sql-batch";
    }

    @Override
    public String getDisplayName() {
        return "达梦 SQL 批量分析";
    }

    @Override
    public String getDescription() {
        return "批量解析 DM SQL，汇总读写表、表级依赖边和失败项；单条失败不终止整批";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String input = params.get("input") == null ? "" : String.valueOf(params.get("input"));
            List<SqlInput> items = inputParser.parse(input);
            return ToolResult.ok(analyzer.analyzeBatch(items));
        } catch (Exception e) {
            return ToolResult.fail("DM SQL 批量分析失败: " + rootMessage(e));
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
