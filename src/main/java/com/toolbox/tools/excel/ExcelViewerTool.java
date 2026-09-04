package com.toolbox.tools.excel;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ExcelViewerTool implements Tool {
    @Override public String getName() { return "excel-viewer"; }
    @Override public String getDisplayName() { return "Excel 浏览"; }
    @Override public String getDescription() { return "上传 Excel 文件，网页分页浏览，支持多 sheet、合并单元格、样式"; }
    @Override public ToolResult execute(Map<String, Object> params) {
        return ToolResult.ok("请使用上传功能，选择 Excel 文件后自动解析浏览");
    }
}
