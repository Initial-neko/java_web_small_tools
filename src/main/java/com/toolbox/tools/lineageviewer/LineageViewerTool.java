package com.toolbox.tools.lineageviewer;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具注册入口：SQL 血缘展示器。
 *
 * <p>与其它工具不同，本工具的主体交互在网页上完成（列表 → 钻取 → 血缘图），
 * 因此 {@link #execute(Map)} 只返回一个说明与关键统计；
 * 真正的数据接口见 {@link LineageViewerController}。</p>
 */
@Component
public class LineageViewerTool implements Tool {

    private final LineageViewerService service;

    public LineageViewerTool(LineageViewerService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "lineage-viewer";
    }

    @Override
    public String getDisplayName() {
        return "SQL 血缘展示";
    }

    @Override
    public String getDescription() {
        return "从末端产出表出发，交互式钻取全链路血缘：搜索、分层、子图、SQL 明细";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        LineageGraph graph = service.graph();
        if (graph == null) {
            return ToolResult.fail("血缘图尚未初始化完成");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("tableCount", graph.tableCount());
        data.put("edgeCount", graph.edgeCount());
        data.put("leafCount", graph.leafTables().size());
        data.put("sourceCount", graph.sourceTables().size());
        data.put("clusterCount", graph.clusterCount());
        return ToolResult.ok("请在页面中浏览血缘图（列表 → 钻取 → 血缘图）", data);
    }
}
