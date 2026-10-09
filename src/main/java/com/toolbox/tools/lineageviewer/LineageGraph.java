package com.toolbox.tools.lineageviewer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 血缘图 —— 系统唯一的真相来源（single source of truth）。
 *
 * <p>构建流程（一次性完成，结果常驻内存）：</p>
 * <ol>
 *   <li><b>拆分</b>：逐条 SQL 取出 inputTables / outputTables；</li>
 *   <li><b>去重 + 计数</b>：同一对 (上游, 下游) 只保留一条边，命中的 SQL 挂到边上；</li>
 *   <li><b>算度数</b>：由边推导每个表的入度/出度，据此标出叶子产出表（出度=0）与源头表（入度=0）；</li>
 *   <li><b>连通分量</b>：把弱连通的子图分组并编号，供全量图分块渲染。</li>
 * </ol>
 *
 * <p>之所以预先算好而不是每次请求实时算：几百张表的全量遍历虽然不慢，
 * 但前端每次点击都触发一遍会造成不必要的抖动。一次构建后，
 * 所有查询退化为内存中的 Map 查找，单表血缘毫秒级返回。</p>
 *
 * <p>注意：本类刻意不依赖 Spring，方便单元测试直接构造。</p>
 */
public class LineageGraph {

    /** 表名 → 节点 */
    private final Map<String, LineageTableNode> nodes = new LinkedHashMap<String, LineageTableNode>();

    /** 边唯一键 → 边 */
    private final Map<String, LineageEdge> edges = new LinkedHashMap<String, LineageEdge>();

    /** 表名 → 它作为下游时，所有指向它的边（即该表的上游） */
    private final Map<String, List<LineageEdge>> incoming = new HashMap<String, List<LineageEdge>>();

    /** 表名 → 它作为上游时，所有从它出发的边（即该表的下游） */
    private final Map<String, List<LineageEdge>> outgoing = new HashMap<String, List<LineageEdge>>();

    /** 表名 → 产出该表的所有 SQL 记录 */
    private final Map<String, List<LineageSqlRecord>> producers = new HashMap<String, List<LineageSqlRecord>>();

    /** 表名 → 使用该表的 SQL，避免列表逐表扫描整个数据集。 */
    private final Map<String, List<LineageSqlRecord>> consumers = new HashMap<String, List<LineageSqlRecord>>();

    /** 作业名 → SQL 记录，供边详情反查 */
    private final Map<String, LineageSqlRecord> sqlByName = new HashMap<String, LineageSqlRecord>();

    /** 表名 → 连通分量编号 */
    private final Map<String, Integer> clusterId = new HashMap<String, Integer>();

    /** 连通分量编号 → 该分量内的表名（有序） */
    private final Map<Integer, List<String>> clusters = new TreeMap<Integer, List<String>>();

    private int clusterCount;

    // ------------------------------------------------------------------
    // 构建
    // ------------------------------------------------------------------

    public static LineageGraph build(List<LineageSqlRecord> records) {
        LineageGraph g = new LineageGraph();
        g.buildNodesAndEdges(records);
        g.buildProducersAndSqlIndex(records);
        g.computeDegrees();
        g.computeWeaklyConnectedComponents();
        g.assignIndexes();
        return g;
    }

    /** 步骤 1+2：拆分输入输出，建表节点，边去重并把命中的 SQL 挂到边上 */
    private void buildNodesAndEdges(List<LineageSqlRecord> records) {
        for (LineageSqlRecord r : records) {
            Set<String> ins = new LinkedHashSet<String>(r.getInputTables());
            Set<String> outs = new LinkedHashSet<String>(r.getOutputTables());

            // 输入输出都为空 —— 这条记录对血缘没有贡献
            if (ins.isEmpty() && outs.isEmpty()) {
                continue;
            }

            for (String t : ins) {
                if (!nodes.containsKey(t)) {
                    nodes.put(t, new LineageTableNode(t, layerOf(t), nodes.size()));
                }
            }
            for (String t : outs) {
                if (!nodes.containsKey(t)) {
                    nodes.put(t, new LineageTableNode(t, layerOf(t), nodes.size()));
                }
            }

            // input × output 笛卡尔积，每条 SQL 都表示「这些输入表共同产出这些输出表」
            for (String in : ins) {
                for (String out : outs) {
                    if (in.equalsIgnoreCase(out)) {
                        continue; // 自环没有血缘意义，丢弃
                    }
                    String k = LineageEdge.key(in, out);
                    LineageEdge e = edges.get(k);
                    if (e == null) {
                        e = new LineageEdge(in, out);
                        edges.put(k, e);
                    }
                    e.addSql(r.getName());
                }
            }
        }

        // 建立邻接索引
        for (LineageEdge e : edges.values()) {
            addAdjacent(outgoing, e.getFrom(), e);
            addAdjacent(incoming, e.getTo(), e);
        }
    }

    private static void addAdjacent(Map<String, List<LineageEdge>> index, String key, LineageEdge e) {
        List<LineageEdge> list = index.get(key);
        if (list == null) {
            list = new ArrayList<LineageEdge>();
            index.put(key, list);
        }
        list.add(e);
    }

    /** 建立「表 → 产出它的 SQL」倒排，以及「作业名 → SQL」索引 */
    private void buildProducersAndSqlIndex(List<LineageSqlRecord> records) {
        for (LineageSqlRecord r : records) {
            if (r.getName() != null) {
                sqlByName.put(r.getName(), r);
            }
            for (String in : new LinkedHashSet<String>(r.getInputTables())) {
                List<LineageSqlRecord> list = consumers.get(in);
                if (list == null) {
                    list = new ArrayList<LineageSqlRecord>();
                    consumers.put(in, list);
                }
                list.add(r);
            }
            for (String out : new LinkedHashSet<String>(r.getOutputTables())) {
                List<LineageSqlRecord> list = producers.get(out);
                if (list == null) {
                    list = new ArrayList<LineageSqlRecord>();
                    producers.put(out, list);
                }
                list.add(r);
            }
        }
    }

    /** 步骤 3：算度数，标出叶子产出表与源头表 */
    private void computeDegrees() {
        for (LineageTableNode n : nodes.values()) {
            n.setInDegree(distinctNeighborCount(incoming.get(n.getName()), true));
            n.setOutDegree(distinctNeighborCount(outgoing.get(n.getName()), false));
            List<LineageSqlRecord> prods = producers.get(n.getName());
            n.setProducingSqlCount(prods == null ? 0 : prods.size());
        }
    }

    private int distinctNeighborCount(List<LineageEdge> list, boolean upstream) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Set<String> seen = new HashSet<String>();
        for (LineageEdge e : list) {
            seen.add(upstream ? e.getFrom() : e.getTo());
        }
        return seen.size();
    }

    /** 步骤 4：弱连通分量分组。无边的孤立表自成一个分量。 */
    private void computeWeaklyConnectedComponents() {
        Map<String, Integer> id = new HashMap<String, Integer>();
        int next = 0;
        for (String start : nodes.keySet()) {
            if (id.containsKey(start)) {
                continue;
            }
            int cid = next++;
            Deque<String> queue = new ArrayDeque<String>();
            queue.add(start);
            id.put(start, cid);
            List<String> members = new ArrayList<String>();
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                members.add(cur);
                // 沿入边与出边双向扩散，得到弱连通分量
                List<LineageEdge> inList = incoming.get(cur);
                if (inList != null) {
                    for (LineageEdge e : inList) {
                        if (!id.containsKey(e.getFrom())) {
                            id.put(e.getFrom(), cid);
                            queue.add(e.getFrom());
                        }
                    }
                }
                List<LineageEdge> outList = outgoing.get(cur);
                if (outList != null) {
                    for (LineageEdge e : outList) {
                        if (!id.containsKey(e.getTo())) {
                            id.put(e.getTo(), cid);
                            queue.add(e.getTo());
                        }
                    }
                }
            }
            Collections.sort(members);
            clusters.put(cid, members);
        }
        clusterId.putAll(id);
        clusterCount = next;
    }

    /** 给节点重新编号：叶子优先，其次按分层和名字，保证前端渲染顺序稳定 */
    private void assignIndexes() {
        List<LineageTableNode> ordered = new ArrayList<LineageTableNode>(nodes.values());
        Collections.sort(ordered, new Comparator<LineageTableNode>() {
            @Override
            public int compare(LineageTableNode a, LineageTableNode b) {
                int byLeaf = Integer.compare(a.isLeaf() ? 0 : 1, b.isLeaf() ? 0 : 1);
                if (byLeaf != 0) {
                    return byLeaf;
                }
                int byLayer = a.getLayer().compareTo(b.getLayer());
                if (byLayer != 0) {
                    return byLayer;
                }
                return a.getName().compareTo(b.getName());
            }
        });
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setIndex(i);
        }
    }

    /**
     * 从表名推断数仓分层。识别不了就返回 UNKNOWN，
     * 不强行猜，避免给使用者错误信息。
     */
    public static String layerOf(String table) {
        String t = table.toLowerCase();
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        if (t.startsWith("ods_") || t.startsWith("ods")) {
            return "ODS";
        }
        if (t.startsWith("dwd_") || t.startsWith("dwd")) {
            return "DWD";
        }
        if (t.startsWith("dws_") || t.startsWith("dws")) {
            return "DWS";
        }
        if (t.startsWith("ads_") || t.startsWith("ads") || t.startsWith("app_") || t.startsWith("rpt_")) {
            return "ADS";
        }
        if (t.startsWith("dim_") || t.startsWith("dim")) {
            return "DIM";
        }
        return "UNKNOWN";
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public boolean contains(String table) {
        return table != null && nodes.containsKey(table);
    }

    public LineageTableNode node(String table) {
        return nodes.get(table);
    }

    public List<LineageTableNode> allNodes() {
        return new ArrayList<LineageTableNode>(nodes.values());
    }

    public List<LineageEdge> allEdges() {
        return new ArrayList<LineageEdge>(edges.values());
    }

    public int tableCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public int clusterCount() {
        return clusterCount;
    }

    public Map<Integer, List<String>> clusters() {
        return clusters;
    }

    public Integer clusterOf(String table) {
        return clusterId.get(table);
    }

    public List<LineageEdge> upstreamOf(String table) {
        List<LineageEdge> list = incoming.get(table);
        return list == null ? new ArrayList<LineageEdge>() : new ArrayList<LineageEdge>(list);
    }

    public List<LineageEdge> downstreamOf(String table) {
        List<LineageEdge> list = outgoing.get(table);
        return list == null ? new ArrayList<LineageEdge>() : new ArrayList<LineageEdge>(list);
    }

    /** 产出该表的所有 SQL 记录，按作业名排序 */
    public List<LineageSqlRecord> producersOf(String table) {
        List<LineageSqlRecord> list = producers.get(table);
        if (list == null) {
            return new ArrayList<LineageSqlRecord>();
        }
        List<LineageSqlRecord> copy = new ArrayList<LineageSqlRecord>(list);
        Collections.sort(copy, SQL_NAME_ORDER);
        return copy;
    }

    /** 消耗该表作为输入的所有 SQL 记录 */
    public List<LineageSqlRecord> consumersOf(String table) {
        if (table == null) {
            return new ArrayList<LineageSqlRecord>();
        }
        List<LineageSqlRecord> list = consumers.get(table);
        List<LineageSqlRecord> result = list == null ? new ArrayList<LineageSqlRecord>()
                : new ArrayList<LineageSqlRecord>(list);
        Collections.sort(result, SQL_NAME_ORDER);
        return result;
    }

    public int consumerCount(String table) {
        List<LineageSqlRecord> list = consumers.get(table);
        return list == null ? 0 : list.size();
    }

    public LineageSqlRecord sqlByName(String name) {
        return sqlByName.get(name);
    }

    /** 边详情：某条边由哪些 SQL 产出 */
    public LineageEdge edge(String from, String to) {
        return edges.get(LineageEdge.key(from, to));
    }

    /** 末端产出表：只被写入、没有任何下游的表 */
    public List<LineageTableNode> leafTables() {
        List<LineageTableNode> result = new ArrayList<LineageTableNode>();
        for (LineageTableNode n : nodes.values()) {
            if (n.isLeaf()) {
                result.add(n);
            }
        }
        Collections.sort(result, BY_NAME);
        return result;
    }

    /** 源头表：没有任何上游的表 */
    public List<LineageTableNode> sourceTables() {
        List<LineageTableNode> result = new ArrayList<LineageTableNode>();
        for (LineageTableNode n : nodes.values()) {
            if (n.isSource()) {
                result.add(n);
            }
        }
        Collections.sort(result, BY_NAME);
        return result;
    }

    /**
     * 全局搜索：表名模糊匹配（忽略大小写）。
     * 排序优先级：前缀命中 &gt; 末端表 &gt; 名字字典序，让最可能想找的表排在最前。
     */
    public List<LineageTableNode> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            List<LineageTableNode> all = allNodes();
            Collections.sort(all, BY_NAME);
            return all;
        }
        final String kw = keyword.trim().toLowerCase();
        List<LineageTableNode> result = new ArrayList<LineageTableNode>();
        for (LineageTableNode n : nodes.values()) {
            if (n.getName().toLowerCase().contains(kw)) {
                result.add(n);
            }
        }
        Collections.sort(result, new Comparator<LineageTableNode>() {
            @Override
            public int compare(LineageTableNode a, LineageTableNode b) {
                int byPrefix = Integer.compare(
                        a.getName().toLowerCase().startsWith(kw) ? 0 : 1,
                        b.getName().toLowerCase().startsWith(kw) ? 0 : 1);
                if (byPrefix != 0) {
                    return byPrefix;
                }
                int byLeaf = Integer.compare(a.isLeaf() ? 0 : 1, b.isLeaf() ? 0 : 1);
                if (byLeaf != 0) {
                    return byLeaf;
                }
                return a.getName().compareTo(b.getName());
            }
        });
        return result;
    }

    /**
     * 单表全景血缘子图。
     *
     * <p>语义为「以目标表为终点，向上游回溯」：</p>
     * <ul>
     *   <li>自目标表出发，沿入边（上游方向）最多回溯 {@code depth} 层，得到<b>上游闭包</b>；</li>
     *   <li>{@code depth <= 0} 表示不限层数，即完整回溯到源头表；</li>
     *   <li>同时带上目标表的下游一层，方便看清这张表在整条链路中的位置
     *       （末端产出表没有下游，这一项自然为空）。</li>
     * </ul>
     *
     * @param table 目标表
     * @param depth 向上游回溯的最大层数，&lt;=0 表示不限层数
     */
    public SubGraph subGraph(String table, int depth) {
        if (!nodes.containsKey(table)) {
            return SubGraph.empty(table);
        }
        boolean unlimited = depth <= 0;

        Set<String> included = new LinkedHashSet<String>();
        included.add(table);

        // 距离与方向：正数 = 距目标表的上游跳数；负数 = 目标表的下游
        Map<String, Integer> dist = new HashMap<String, Integer>();
        dist.put(table, 0);

        // 向上游 BFS
        Deque<String> queue = new ArrayDeque<String>();
        queue.add(table);
        int maxUpstreamDepth = 0;
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = dist.get(cur);
            if (d < 0) {
                continue; // 下游节点不再向外扩散
            }
            if (!unlimited && d >= depth) {
                continue;
            }
            List<LineageEdge> upStreams = incoming.get(cur);
            if (upStreams == null) {
                continue;
            }
            for (LineageEdge e : upStreams) {
                String up = e.getFrom();
                if (dist.containsKey(up)) {
                    included.add(up);
                    continue; // 已访问（可能是环），不重复计算跳数
                }
                included.add(up);
                dist.put(up, d + 1);
                if (d + 1 > maxUpstreamDepth) {
                    maxUpstreamDepth = d + 1;
                }
                queue.add(up);
            }
        }

        // 目标表的下游一层。已被标记为上游的节点不覆盖（存在环时不制造自相矛盾的方向）。
        List<LineageEdge> downs = outgoing.get(table);
        if (downs != null) {
            for (LineageEdge e : downs) {
                String down = e.getTo();
                if (dist.containsKey(down)) {
                    continue;
                }
                included.add(down);
                dist.put(down, -1);
            }
        }

        // 收集两端都在集合内的边
        List<LineageEdge> subEdges = new ArrayList<LineageEdge>();
        for (String from : included) {
            List<LineageEdge> adjacent = outgoing.get(from);
            if (adjacent == null) continue;
            for (LineageEdge e : adjacent) {
                if (included.contains(e.getTo())) subEdges.add(e);
            }
        }

        List<LineageTableNode> subNodes = new ArrayList<LineageTableNode>();
        for (String name : included) {
            LineageTableNode n = nodes.get(name);
            if (n != null) {
                subNodes.add(n);
            }
        }
        return new SubGraph(table, subNodes, subEdges, dist, maxUpstreamDepth);
    }

    // ------------------------------------------------------------------
    // 排序器（无状态，复用）
    // ------------------------------------------------------------------

    private static final Comparator<LineageTableNode> BY_NAME = new Comparator<LineageTableNode>() {
        @Override
        public int compare(LineageTableNode a, LineageTableNode b) {
            return a.getName().compareTo(b.getName());
        }
    };

    private static final Comparator<LineageSqlRecord> SQL_NAME_ORDER = new Comparator<LineageSqlRecord>() {
        @Override
        public int compare(LineageSqlRecord a, LineageSqlRecord b) {
            String x = a.getName();
            String y = b.getName();
            if (x == null && y == null) {
                return 0;
            }
            if (x == null) {
                return 1;
            }
            if (y == null) {
                return -1;
            }
            return x.compareTo(y);
        }
    };

    // ------------------------------------------------------------------
    // 子图结果
    // ------------------------------------------------------------------

    /** 单表血缘子图的返回结构 */
    public static class SubGraph {
        private final String focus;
        private final List<LineageTableNode> nodes;
        private final List<LineageEdge> edges;
        private final Map<String, Integer> distanceFromFocus;
        private final int maxUpstreamDepth;

        SubGraph(String focus, List<LineageTableNode> nodes, List<LineageEdge> edges,
                 Map<String, Integer> distanceFromFocus, int maxUpstreamDepth) {
            this.focus = focus;
            this.nodes = nodes;
            this.edges = edges;
            this.distanceFromFocus = distanceFromFocus;
            this.maxUpstreamDepth = maxUpstreamDepth;
        }

        static SubGraph empty(String focus) {
            return new SubGraph(focus,
                    new ArrayList<LineageTableNode>(),
                    new ArrayList<LineageEdge>(),
                    new HashMap<String, Integer>(), 0);
        }

        public String getFocus() {
            return focus;
        }

        public List<LineageTableNode> getNodes() {
            return nodes;
        }

        public List<LineageEdge> getEdges() {
            return edges;
        }

        public Map<String, Integer> getDistanceFromFocus() {
            return distanceFromFocus;
        }

        public int getMaxUpstreamDepth() {
            return maxUpstreamDepth;
        }
    }
}
