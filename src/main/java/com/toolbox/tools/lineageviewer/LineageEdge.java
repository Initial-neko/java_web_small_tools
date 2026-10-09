package com.toolbox.tools.lineageviewer;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 血缘图中的一条有向边：上游表 → 下游表。
 *
 * <p>方向约定为「数据流动方向」，即 inputTables 指向 outputTables。
 * 同一对表可能被多条 SQL 命中，全部记在 {@link #sqls} 上，
 * 点击边即可回溯是哪些作业产出的。</p>
 */
public class LineageEdge {

    /** 上游表全名 */
    private final String from;

    /** 下游表全名 */
    private final String to;

    /** 命中该关系的 SQL 作业名集合，保持插入顺序 */
    private final Set<String> sqls = new LinkedHashSet<String>();

    public LineageEdge(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public void addSql(String sqlName) {
        if (sqlName != null && sqlName.trim().length() > 0) {
            this.sqls.add(sqlName);
        }
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public Set<String> getSqls() {
        return sqls;
    }

    /** 权重 = 命中该关系的 SQL 条数，可用于给边加粗 */
    public int getWeight() {
        return sqls.isEmpty() ? 1 : sqls.size();
    }

    /** 图的唯一键。用 NUL 分隔，避免表名里恰好含分隔符时发生键碰撞。 */
    public static String key(String from, String to) {
        return from + "\u0000" + to;
    }

    @Override
    public String toString() {
        return from + " -> " + to + " (" + sqls.size() + " sql)";
    }
}
