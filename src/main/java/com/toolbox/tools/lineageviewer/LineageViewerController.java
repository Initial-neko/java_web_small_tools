package com.toolbox.tools.lineageviewer;

import com.toolbox.core.ToolResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SQL 血缘展示器对外接口。
 *
 * <p>路径统一挂在 {@code /api/lineage-viewer/**} 下，避免与仓库里其它工具
 * （尤其是已有达梦 {@code dm-sql-*} 系列）的路由冲突。
 * 返回体统一使用仓库的 {@link ToolResult} 约定：{@code {success, message, data}}。</p>
 *
 * <p>所有接口都直接读内存中的 {@link LineageGraph}，不做任何持久化与实时遍历。</p>
 */
@RestController
@RequestMapping("/api/lineage-viewer")
public class LineageViewerController {

    /** 单表血缘默认向上游回溯的层数，避免链路过长时节点爆炸 */
    private static final int DEFAULT_DEPTH = 6;

    private final LineageViewerService service;

    public LineageViewerController(LineageViewerService service) {
        this.service = service;
    }

    private LineageGraph g() {
        return service.graph();
    }

    // ------------------------------------------------------------------
    // 总览统计
    // ------------------------------------------------------------------

    /** 首页头部指标：表数 / 边数 / 末端表数 / 源头表数 / 连通分量 */
    @GetMapping("/overview")
    public ToolResult overview() {
        LineageGraph g = g();
        if (g == null) {
            return ToolResult.fail("血缘图尚未初始化完成");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("tableCount", g.tableCount());
        data.put("edgeCount", g.edgeCount());
        data.put("leafCount", g.leafTables().size());
        data.put("sourceCount", g.sourceTables().size());
        data.put("clusterCount", g.clusterCount());

        // 各分层表数量。用 TreeMap 保证 ODS/DWD/DWS 顺序稳定，前端图例不会乱跳。
        Map<String, Integer> byLayer = new TreeMap<String, Integer>();
        for (LineageTableNode n : g.allNodes()) {
            Integer cur = byLayer.get(n.getLayer());
            byLayer.put(n.getLayer(), cur == null ? 1 : cur + 1);
        }
        data.put("layerDistribution", byLayer);
        return ToolResult.ok(data);
    }

    // ------------------------------------------------------------------
    // 末端表列表（入口）
    // ------------------------------------------------------------------

    /**
     * 入口列表：默认返回「末端产出表」（只被写入、没有任何下游）。
     *
     * @param keyword 模糊搜索表名，可空
     * @param scope   leaves=末端表（默认）| sources=源头表 | all=全部表
     */
    @GetMapping("/tables/leaves")
    public ToolResult leaves(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false, defaultValue = "leaves") String scope) {
        LineageGraph g = g();
        if (g == null) {
            return ToolResult.fail("血缘图尚未初始化完成");
        }

        String s = scope == null ? "leaves" : scope.trim().toLowerCase();
        List<LineageTableNode> base;
        if ("sources".equals(s)) {
            base = g.sourceTables();
        } else if ("all".equals(s)) {
            base = g.allNodes();
        } else {
            base = g.leafTables();
        }

        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (LineageTableNode n : base) {
            if (!kw.isEmpty() && !n.getName().toLowerCase().contains(kw)) {
                continue;
            }
            result.add(toTableCardWithConsumers(g, n));
        }
        java.util.Collections.sort(result, new Comparator<Map<String, Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                return String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name")));
            }
        });
        return ToolResult.ok(result);
    }

    private Map<String, Object> toTableCard(LineageTableNode n) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", n.getName());
        m.put("shortName", n.shortName());
        m.put("layer", n.getLayer());
        m.put("inDegree", n.getInDegree());
        m.put("outDegree", n.getOutDegree());
        m.put("producingSqlCount", n.getProducingSqlCount());
        m.put("isLeaf", n.isLeaf());
        m.put("isSource", n.isSource());
        return m;
    }

    /** 卡片用：额外带上「消费该表的 SQL 数」。源头表没有产出 SQL，但有消费 SQL。 */
    private Map<String, Object> toTableCardWithConsumers(LineageGraph g, LineageTableNode n) {
        Map<String, Object> m = toTableCard(n);
        m.put("consumingSqlCount", g.consumerCount(n.getName()));
        return m;
    }

    // ------------------------------------------------------------------
    // 全表搜索
    // ------------------------------------------------------------------

    /** 全局搜索，含中间表，用于顶部搜索框（自动提示） */
    @GetMapping("/tables")
    public ToolResult searchTables(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false, defaultValue = "50") int limit) {
        LineageGraph g = g();
        if (g == null) {
            return ToolResult.fail("血缘图尚未初始化完成");
        }
        int max = Math.max(1, limit);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (LineageTableNode n : g.search(keyword)) {
            if (result.size() >= max) {
                break;
            }
            result.add(toTableCard(n));
        }
        return ToolResult.ok(result);
    }

    // ------------------------------------------------------------------
    // 表详情
    // ------------------------------------------------------------------

    /** 单表详情：分层、度数、直接上下游表清单、产出它的 SQL 清单 */
    @GetMapping("/tables/{name}")
    public ToolResult tableDetail(@PathVariable String name) {
        LineageGraph g = g();
        if (g == null) {
            return ToolResult.fail("血缘图尚未初始化完成");
        }
        if (!g.contains(name)) {
            return ToolResult.fail("表不存在: " + name);
        }
        LineageTableNode n = g.node(name);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("name", n.getName());
        data.put("shortName", n.shortName());
        data.put("layer", n.getLayer());
        data.put("inDegree", n.getInDegree());
        data.put("outDegree", n.getOutDegree());
        data.put("isLeaf", n.isLeaf());
        data.put("isSource", n.isSource());
        data.put("clusterId", g.clusterOf(name));
        List<LineageSqlRecord> producers = g.producersOf(name);
        data.put("producerCount", producers.size());
        data.put("consumerCount", g.consumerCount(name));

        data.put("upstreamTables", distinctSorted(upstreamNames(g, name)));
        data.put("downstreamTables", distinctSorted(downstreamNames(g, name)));
        // 侧栏要直接展示产出该表的 SQL 全字段（含正文），因此返回完整记录而不是摘要
        List<Map<String, Object>> producerList = new ArrayList<Map<String, Object>>();
        for (LineageSqlRecord r : producers) {
            producerList.add(toSqlFull(r));
        }
        data.put("producers", producerList);
        return ToolResult.ok(data);
    }

    private List<String> upstreamNames(LineageGraph g, String name) {
        List<String> out = new ArrayList<String>();
        for (LineageEdge e : g.upstreamOf(name)) {
            out.add(e.getFrom());
        }
        return out;
    }

    private List<String> downstreamNames(LineageGraph g, String name) {
        List<String> out = new ArrayList<String>();
        for (LineageEdge e : g.downstreamOf(name)) {
            out.add(e.getTo());
        }
        return out;
    }

    private List<String> distinctSorted(List<String> raw) {
        Map<String, Boolean> seen = new TreeMap<String, Boolean>();
        for (String s : raw) {
            seen.put(s, Boolean.TRUE);
        }
        return new ArrayList<String>(seen.keySet());
    }

    // ------------------------------------------------------------------
    // 单表血缘子图
    // ------------------------------------------------------------------

    /**
     * 单表全景血缘子图。
     *
     * <p>语义为「下游 → 上游」：以目标表为终点，向上游回溯 depth 层，
     * 同时带上目标表的下游一层，便于看清它在链路中的位置。</p>
     *
     * @param depth 上游回溯层数，&lt;=0 表示不限层数；不传则用默认值 6
     */
    @GetMapping("/tables/{name}/lineage")
    public ToolResult lineage(@PathVariable String name,
                              @RequestParam(required = false) Integer depth) {
        LineageGraph g = g();
        if (g == null) {
            return ToolResult.fail("血缘图尚未初始化完成");
        }
        if (!g.contains(name)) {
            return ToolResult.fail("表不存在: " + name);
        }
        int depthUsed = depth == null ? DEFAULT_DEPTH : depth;
        LineageGraph.SubGraph sub = g.subGraph(name, depthUsed);

        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
        for (LineageTableNode n : sub.getNodes()) {
            Map<String, Object> m = toTableCard(n);
            Integer dist = sub.getDistanceFromFocus().get(n.getName());
            int hop = dist == null ? 0 : dist;
            boolean isFocus = n.getName().equals(name);
            m.put("distance", hop);
            m.put("isFocus", isFocus);
            // direction 让前端不必猜距离的符号含义：
            //   focus = 当前表；upstream = 上游（距焦点 hop 跳）；downstream = 焦点的一级下游
            m.put("direction", isFocus ? "focus" : (hop >= 0 ? "upstream" : "downstream"));
            nodes.add(m);
        }

        List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
        for (LineageEdge e : sub.getEdges()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", e.getFrom() + "->" + e.getTo());
            m.put("source", e.getFrom());
            m.put("target", e.getTo());
            m.put("weight", e.getWeight());
            m.put("sqlCount", e.getSqls().size());
            m.put("sqlNames", new ArrayList<String>(e.getSqls()));
            edges.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("focus", name);
        data.put("depth", depthUsed);
        data.put("maxUpstreamDepth", sub.getMaxUpstreamDepth());
        data.put("nodeCount", nodes.size());
        data.put("edgeCount", edges.size());
        data.put("nodes", nodes);
        data.put("edges", edges);
        return ToolResult.ok(data);
    }

    // ------------------------------------------------------------------
    // 边详情
    // ------------------------------------------------------------------

    /** 边详情：这条血缘关系由哪些 SQL 产出，返回 SQL 全字段 */
    @GetMapping("/edges/detail")
    public ToolResult edgeDetail(@RequestParam String from, @RequestParam String to) {
        LineageGraph g = g();
        if (g == null) {
            return ToolResult.fail("血缘图尚未初始化完成");
        }
        LineageEdge e = g.edge(from, to);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("from", from);
        data.put("to", to);

        if (e == null) {
            data.put("exists", false);
            data.put("weight", 0);
            data.put("sqls", new ArrayList<Map<String, Object>>());
            return ToolResult.ok(data);
        }

        data.put("exists", true);
        data.put("weight", e.getWeight());
        List<Map<String, Object>> sqls = new ArrayList<Map<String, Object>>();
        for (String sn : e.getSqls()) {
            LineageSqlRecord r = g.sqlByName(sn);
            sqls.add(r == null ? briefOnly(sn) : toSqlFull(r));
        }
        data.put("sqls", sqls);
        return ToolResult.ok(data);
    }

    // ------------------------------------------------------------------
    // SQL 详情
    // ------------------------------------------------------------------

    /**
     * SQL 明细查询，按传入名称自动区分两种语义：
     *
     * <ol>
     *   <li>命中 SQL 名称 → 返回该条 SQL 的完整字段</li>
     *   <li>命中表名 → 返回产出该表的全部 SQL（即该表的 producers）</li>
     * </ol>
     *
     * <p>表名优先判定：表名与 SQL 名同名时，语义更偏「看这张表是怎么产出的」，
     * 而单条 SQL 明细已经能从 {@code /edges/detail} 里拿到。</p>
     *
     * <p>两种语义的返回都是数组，前端可统一按列表渲染；单个 SQL 命中时为长度 1 的数组。</p>
     */
    @GetMapping("/sqls/{name}")
    public ToolResult sqlDetail(@PathVariable String name) {
        LineageGraph g = g();
        if (g == null) {
            return ToolResult.fail("血缘图尚未初始化完成");
        }
        return ToolResult.ok(resolveSqlList(g, name));
    }

    /** 先按表名解析 producers，再回退到按 SQL 名解析；都没有则返回空列表。 */
    private List<Map<String, Object>> resolveSqlList(LineageGraph g, String name) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (g.contains(name)) {
            for (LineageSqlRecord r : g.producersOf(name)) {
                out.add(toSqlFull(r));
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        LineageSqlRecord r = g.sqlByName(name);
        if (r != null) {
            out.add(toSqlFull(r));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 数据替换
    // ------------------------------------------------------------------

    /**
     * 用前端粘贴或上传的 JSON 直接重建血缘图。
     *
     * <p>请求体支持两种形态：</p>
     * <ol>
     *   <li>{@code {"json": "[{...}]"}} —— JSON 文本</li>
     *   <li>{@code {"records": [{...}]}} —— 直接是记录数组</li>
     * </ol>
     */
    @PostMapping("/rebuild")
    public ToolResult rebuild(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ToolResult.fail("请求体不能为空");
        }
        Object json = body.get("json");
        try {
            LineageGraph g;
            if (json != null) {
                g = service.rebuildFromJson(String.valueOf(json));
            } else if (body.get("records") != null) {
                g = service.rebuildFromJson(com.alibaba.fastjson2.JSON.toJSONString(body));
            } else {
                return ToolResult.fail("请提供 json（JSON 文本）或 records（记录数组）字段");
            }
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("tableCount", g.tableCount());
            data.put("edgeCount", g.edgeCount());
            data.put("leafCount", g.leafTables().size());
            data.put("sourceCount", g.sourceTables().size());
            data.put("clusterCount", g.clusterCount());
            return ToolResult.ok("已重建血缘图", data);
        } catch (Exception e) {
            return ToolResult.fail("JSON 解析失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // DTO 映射
    // ------------------------------------------------------------------

    private Map<String, Object> toSqlFull(LineageSqlRecord r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", r.getName());
        m.put("source", r.getSource());
        m.put("description", r.getDescription());
        m.put("sql", r.getSql());
        m.put("inputTables", r.getInputTables());
        m.put("outputTables", r.getOutputTables());
        return m;
    }

    private Map<String, Object> briefOnly(String name) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        m.put("source", null);
        m.put("description", null);
        m.put("sql", null);
        m.put("inputTables", new ArrayList<String>());
        m.put("outputTables", new ArrayList<String>());
        return m;
    }
}
