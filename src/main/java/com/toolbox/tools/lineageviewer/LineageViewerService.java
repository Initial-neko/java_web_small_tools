package com.toolbox.tools.lineageviewer;

import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 血缘图服务：启动时载入 SQL 记录并一次性构建全量血缘图，
 * 之后所有请求都走内存（Map 查找），不再做实时遍历。
 *
 * <p>构建耗时打印到日志，方便确认当前数据规模下是否够快。</p>
 */
@Service
public class LineageViewerService {

    private final LineageDataLoader loader = new LineageDataLoader();

    private volatile LineageGraph graph;

    @PostConstruct
    public void init() {
        long start = System.currentTimeMillis();
        List<LineageSqlRecord> records = loader.load();
        long loadMs = System.currentTimeMillis() - start;

        long buildStart = System.currentTimeMillis();
        this.graph = LineageGraph.build(records);
        long buildMs = System.currentTimeMillis() - buildStart;

        System.out.println("[lineage-viewer] 血缘图构建完成："
                + records.size() + " 条 SQL → "
                + graph.tableCount() + " 张表 / "
                + graph.edgeCount() + " 条边 / "
                + graph.clusterCount() + " 个连通分量"
                + "（载入 " + loadMs + "ms，计算 " + buildMs + "ms）");
        System.out.println("[lineage-viewer] 末端产出表 " + graph.leafTables().size()
                + " 张，源头表 " + graph.sourceTables().size() + " 张");
    }

    public LineageGraph graph() {
        return graph;
    }

    /** 用前端粘贴的 JSON 重建图，便于不重启进程就换数据 */
    public synchronized LineageGraph rebuildFromJson(String json) {
        List<LineageSqlRecord> records = loader.parse(json);
        LineageGraph g = LineageGraph.build(records);
        this.graph = g;
        return g;
    }

    /** 重建图，便于将来接入「数据变更后刷新」的能力 */
    public synchronized void rebuild() {
        init();
    }
}
