package com.toolbox.tools.lineageviewer;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 血缘计算引擎自测：用最小样例覆盖「拆分 → 去重 → 度数 → 连通分量 → 子图回溯」全链路，
 * 再用内置演示数据验证规模与度数口径。
 *
 * <p>刻意保持 Java 8 语法（不用 var / List.of / Stream.toList），
 * 因为本工程 CI 固定跑 JDK 8。</p>
 */
class LineageGraphTest {

    /**
     * 一个刻意设计的玩具图：
     * <pre>
     *  ods_raw_a ─┐
     *             ├─&gt; dwd_x ─┬─&gt; dws_y ─&gt; ads_z
     *  ods_raw_b ─┘          └─&gt; ads_w
     * </pre>
     * dwd_x 同时被两条 SQL 产出（用于验证边去重与权重）。
     */
    private List<LineageSqlRecord> toyData() {
        return Arrays.asList(
                rec("job1", Arrays.asList("ods_raw_a"), Arrays.asList("dwd_x")),
                rec("job2", Arrays.asList("ods_raw_b"), Arrays.asList("dwd_x")),
                rec("job3", Arrays.asList("dwd_x"), Arrays.asList("dws_y")),
                rec("job4", Arrays.asList("dws_y"), Arrays.asList("ads_z")),
                rec("job5", Arrays.asList("dwd_x"), Arrays.asList("ads_w"))
        );
    }

    private LineageSqlRecord rec(String name, List<String> in, List<String> out) {
        return new LineageSqlRecord("hive", name, name + " desc", "SELECT 1;", in, out);
    }

    private List<String> names(List<LineageTableNode> nodes) {
        List<String> out = new ArrayList<String>();
        for (LineageTableNode n : nodes) {
            out.add(n.getName());
        }
        Collections.sort(out);
        return out;
    }

    // ------------------------------------------------------------------
    // 基础结构
    // ------------------------------------------------------------------

    @Test
    void basicScaleAndDegrees() {
        LineageGraph g = LineageGraph.build(toyData());

        assertEquals(6, g.tableCount(), "表数");
        assertEquals(5, g.edgeCount(), "边数：ods_raw_a->dwd_x 与 ods_raw_b->dwd_x 是两条不同边");

        LineageTableNode rawA = g.node("ods_raw_a");
        assertTrue(rawA.isSource(), "ods_raw_a 无上游，应为源头表");
        assertEquals(0, rawA.getInDegree());
        assertEquals(1, rawA.getOutDegree());

        LineageTableNode dwdX = g.node("dwd_x");
        assertEquals(2, dwdX.getInDegree(), "dwd_x 有 2 个上游");
        assertEquals(2, dwdX.getOutDegree(), "dwd_x 有 2 个下游");
        assertFalse(dwdX.isLeaf());
        assertFalse(dwdX.isSource());

        LineageTableNode adsZ = g.node("ads_z");
        assertTrue(adsZ.isLeaf(), "ads_z 无下游，应为末端产出表");
        assertFalse(adsZ.isSource());
    }

    @Test
    void leafAndSourceListAreCorrect() {
        LineageGraph g = LineageGraph.build(toyData());
        // dws_y 有下游 ads_z，不是末端；ads_z / ads_w 是末端
        assertEquals(Arrays.asList("ads_w", "ads_z"), names(g.leafTables()));
        assertEquals(Arrays.asList("ods_raw_a", "ods_raw_b"), names(g.sourceTables()));
    }

    @Test
    void edgeDedupAndSqlTraceback() {
        LineageGraph g = LineageGraph.build(toyData());
        LineageEdge e = g.edge("dwd_x", "dws_y");
        assertNotNull(e);
        assertEquals(1, e.getWeight());
        assertEquals(Arrays.asList("job3"), new ArrayList<String>(e.getSqls()));
    }

    @Test
    void edgeWeightAccumulatesWhenMultipleSqlHitSamePair() {
        List<LineageSqlRecord> data = Arrays.asList(
                rec("j1", Arrays.asList("a"), Arrays.asList("b")),
                rec("j2", Arrays.asList("a"), Arrays.asList("b")),
                rec("j3", Arrays.asList("a"), Arrays.asList("b"))
        );
        LineageGraph g = LineageGraph.build(data);
        assertEquals(1, g.edgeCount(), "三条 SQL 命中同一对表，边只保留一条");
        assertEquals(3, g.edge("a", "b").getWeight(), "权重为命中 SQL 条数");
        assertEquals(1, g.node("b").getInDegree(), "去重后上游表只有 a 一个");
    }

    @Test
    void selfLoopIsDiscarded() {
        List<LineageSqlRecord> data = Arrays.asList(
                rec("j1", Arrays.asList("t"), Arrays.asList("t"))
        );
        LineageGraph g = LineageGraph.build(data);
        assertEquals(0, g.edgeCount(), "自环没有血缘意义，应被丢弃");
    }

    @Test
    void weaklyConnectedComponentsAreGrouped() {
        List<LineageSqlRecord> data = Arrays.asList(
                rec("j1", Arrays.asList("a"), Arrays.asList("b")),
                rec("j2", Arrays.asList("c"), Arrays.asList("d")),
                rec("j3", Arrays.asList("e"), Arrays.asList("f"))
        );
        LineageGraph g = LineageGraph.build(data);
        assertEquals(3, g.clusterCount(), "三组互不相连的子图");

        assertEquals(g.clusterOf("a"), g.clusterOf("b"), "a 与 b 同属一个分量");
        assertNotEquals(g.clusterOf("a"), g.clusterOf("c"), "a 与 c 属于不同分量");
    }

    // ------------------------------------------------------------------
    // 子图回溯
    // ------------------------------------------------------------------

    @Test
    void subGraphWalksUpstreamToSources() {
        LineageGraph g = LineageGraph.build(toyData());

        // 以末端表 ads_z 为焦点，回溯到底
        LineageGraph.SubGraph sub = g.subGraph("ads_z", 0);
        assertEquals(Arrays.asList("ads_z", "dwd_x", "dws_y", "ods_raw_a", "ods_raw_b"),
                names(sub.getNodes()),
                "回溯应覆盖 ads_z 的全部上游，且不含无关的 ads_w");

        // 最长链路 ods_raw_a -> dwd_x -> dws_y -> ads_z 共 3 跳
        assertEquals(3, sub.getMaxUpstreamDepth(), "最长上游路径为 3 跳");

        // 距离口径：焦点 0，逐跳递增
        assertEquals(Integer.valueOf(0), sub.getDistanceFromFocus().get("ads_z"));
        assertEquals(Integer.valueOf(1), sub.getDistanceFromFocus().get("dws_y"));
        assertEquals(Integer.valueOf(2), sub.getDistanceFromFocus().get("dwd_x"));
        assertEquals(Integer.valueOf(3), sub.getDistanceFromFocus().get("ods_raw_a"));
    }

    @Test
    void allUpstreamOfLeafIsReachable() {
        LineageGraph g = LineageGraph.build(toyData());
        Set<String> upstream = reachableUpstream(g, "ads_z");
        Set<String> expected = new HashSet<String>(
                Arrays.asList("dws_y", "dwd_x", "ods_raw_a", "ods_raw_b"));
        assertEquals(expected, upstream);
    }

    private Set<String> reachableUpstream(LineageGraph g, String table) {
        Set<String> seen = new HashSet<String>();
        Deque<String> q = new ArrayDeque<String>();
        q.add(table);
        while (!q.isEmpty()) {
            String cur = q.poll();
            for (LineageEdge e : g.upstreamOf(cur)) {
                if (seen.add(e.getFrom())) {
                    q.add(e.getFrom());
                }
            }
        }
        return seen;
    }

    @Test
    void subGraphRespectsDepthLimit() {
        LineageGraph g = LineageGraph.build(toyData());
        // depth=1 只回溯一层：ads_z 的上游 dws_y，不再往上到 dwd_x
        LineageGraph.SubGraph sub = g.subGraph("ads_z", 1);
        assertEquals(Arrays.asList("ads_z", "dws_y"), names(sub.getNodes()));
    }

    @Test
    void focusOnMiddleTableDoesNotCrossDownstreamBranch() {
        LineageGraph g = LineageGraph.build(toyData());
        // 以中间表 dwd_x 为焦点：上游是 ods_raw_a/b，下游一级是 dws_y 与 ads_w。
        // ads_z 是 dws_y 的下游，属于焦点下游的第二跳，不应纳入。
        LineageGraph.SubGraph sub = g.subGraph("dwd_x", 0);
        assertEquals(Arrays.asList("ads_w", "dwd_x", "dws_y", "ods_raw_a", "ods_raw_b"),
                names(sub.getNodes()));

        assertEquals(Integer.valueOf(-1), sub.getDistanceFromFocus().get("dws_y"), "下游标记为 -1");
        assertEquals(Integer.valueOf(-1), sub.getDistanceFromFocus().get("ads_w"), "下游标记为 -1");
        assertEquals(Integer.valueOf(1), sub.getDistanceFromFocus().get("ods_raw_a"), "上游为正跳数");
        assertFalse(sub.getDistanceFromFocus().containsKey("ads_z"), "下游第二跳不纳入");
    }

    @Test
    void cyclicGraphDirectionIsNotContradictory() {
        // 构造一个环：a -> b -> c -> a。以 c 为焦点时，a 同时是 c 的直接下游
        // 和 c 的间接上游。此时以上游方向为准，a 只应被标记为上游。
        List<LineageSqlRecord> data = Arrays.asList(
                rec("j1", Arrays.asList("a"), Arrays.asList("b")),
                rec("j2", Arrays.asList("b"), Arrays.asList("c")),
                rec("j3", Arrays.asList("c"), Arrays.asList("a")) // 成环
        );
        LineageGraph g = LineageGraph.build(data);
        LineageGraph.SubGraph sub = g.subGraph("c", 0);

        Integer distOfA = sub.getDistanceFromFocus().get("a");
        assertNotNull(distOfA, "a 应出现在子图中");
        assertTrue(distOfA >= 0, "存在环时 a 应被视作上游，而不是下游。实际 distance=" + distOfA);
        assertFalse(sub.getDistanceFromFocus().containsValue(Integer.valueOf(-1)),
                "环内不存在真正的下游节点，不应有 -1 标记");

        assertTrue(contains(sub.getNodes(), "c"), "焦点 c 应可达");
        assertTrue(contains(sub.getNodes(), "b"), "上游 b 应可达");
    }

    private boolean contains(List<LineageTableNode> nodes, String name) {
        for (LineageTableNode n : nodes) {
            if (n.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void missingTableReturnsEmptySubGraph() {
        LineageGraph g = LineageGraph.build(toyData());
        LineageGraph.SubGraph sub = g.subGraph("no_such_table", 3);
        assertTrue(sub.getNodes().isEmpty());
    }

    // ------------------------------------------------------------------
    // 内置演示数据校验
    // ------------------------------------------------------------------

    @Test
    void bundledDemoDataScaleMatchesExpectation() {
        List<LineageSqlRecord> records = new LineageDataLoader().load();
        assertFalse(records.isEmpty(), "内置演示数据应能被载入");

        LineageGraph g = LineageGraph.build(records);
        System.out.println("演示数据：" + records.size() + " 条 SQL -> " + g.tableCount()
                + " 张表 / " + g.edgeCount() + " 条边 / " + g.clusterCount()
                + " 个连通分量，末端表 " + g.leafTables().size() + " 张");

        assertEquals(300, records.size(), "SQL 条数");
        assertTrue(g.tableCount() >= 190 && g.tableCount() <= 210,
                "表数量应接近 200，实际 " + g.tableCount());
        assertFalse(g.leafTables().isEmpty(), "必须有末端产出表作为入口");
        assertFalse(g.sourceTables().isEmpty(), "必须有源头表");
    }

    @Test
    void everyLeafTableCanWalkUpstream() {
        List<LineageSqlRecord> records = new LineageDataLoader().load();
        LineageGraph g = LineageGraph.build(records);

        // 抽 10 张末端表，验证子图非空且包含焦点
        List<LineageTableNode> leaves = g.leafTables();
        int step = Math.max(1, leaves.size() / 10);
        for (int i = 0; i < leaves.size(); i += step) {
            String name = leaves.get(i).getName();
            LineageGraph.SubGraph sub = g.subGraph(name, 0);
            assertTrue(contains(sub.getNodes(), name), "子图必须包含焦点表 " + name);
            assertTrue(sub.getEdges().size() > 0, "末端表 " + name + " 应有上游边");
        }
    }

    @Test
    void searchIgnoresCase() {
        LineageGraph g = LineageGraph.build(new LineageDataLoader().load());
        assertFalse(g.search("ODS_ORDER").isEmpty(), "大写搜索应命中");
        assertFalse(g.search("ods_order").isEmpty(), "小写搜索应命中");
    }

    @Test
    void layerInference() {
        assertEquals("ODS", LineageGraph.layerOf("ods_order_di"));
        assertEquals("DWD", LineageGraph.layerOf("db.dwd_order_detail"));
        assertEquals("DWS", LineageGraph.layerOf("dws_trade_1d"));
        assertEquals("ADS", LineageGraph.layerOf("ads_gmv_report"));
        assertEquals("UNKNOWN", LineageGraph.layerOf("tmp_whatever"));
    }

    @Test
    void tableListCompatibilityForCommaAndSemicolon() {
        assertEquals(Arrays.asList("a", "b", "c"), LineageDataLoader.splitTables("a,b;c"));
        assertEquals(Arrays.asList("a", "b"), LineageDataLoader.splitTables("a，b"));
        assertTrue(LineageDataLoader.splitTables("").isEmpty());
        assertTrue(LineageDataLoader.splitTables(null).isEmpty());
    }

    // ------------------------------------------------------------------
    // 输入格式兼容
    // ------------------------------------------------------------------

    @Test
    void acceptsObjectWrapperWithRecordsField() {
        String json = "{\"records\":[{\"name\":\"j1\",\"source\":\"hive\","
                + "\"inputTables\":[\"a\"],\"outputTables\":[\"b\"]}]}";
        List<LineageSqlRecord> records = new LineageDataLoader().parse(json);
        assertEquals(1, records.size());
        assertEquals("j1", records.get(0).getName());
        assertEquals(Arrays.asList("b"), records.get(0).getOutputTables());
    }

    @Test
    void acceptsDelimitedStringForTableLists() {
        String json = "[{\"name\":\"j1\",\"inputTables\":\"a, b;c\",\"outputTables\":\"d\"}]";
        List<LineageSqlRecord> records = new LineageDataLoader().parse(json);
        assertEquals(1, records.size());
        assertEquals(Arrays.asList("a", "b", "c"), records.get(0).getInputTables());
    }

    @Test
    void skipsRecordsWithoutName() {
        String json = "[{\"source\":\"hive\",\"inputTables\":[\"a\"],\"outputTables\":[\"b\"]},"
                + "{\"name\":\"j2\",\"inputTables\":[\"c\"],\"outputTables\":[\"d\"]}]";
        List<LineageSqlRecord> records = new LineageDataLoader().parse(json);
        assertEquals(1, records.size(), "没有作业名的记录应被跳过");
        assertEquals("j2", records.get(0).getName());
    }
}
