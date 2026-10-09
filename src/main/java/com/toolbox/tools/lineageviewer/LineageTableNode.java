package com.toolbox.tools.lineageviewer;

/**
 * 血缘图中的一个表节点。
 *
 * <p>度数与叶子/源头标记全部由图计算阶段反推，不依赖人工维护。</p>
 */
public class LineageTableNode {

    /** 表全名，通常形如 {@code dwd_order_detail} 或 {@code db.tbl} */
    private final String name;

    /** 数仓分层：ODS / DWD / DWS / ADS / DIM，未识别时为 UNKNOWN */
    private final String layer;

    /** 索引，用于前端稳定排序 */
    private int index;

    /** 直接上游表数量（谁喂数据给我） */
    private int inDegree;

    /** 直接下游表数量（我喂数据给谁） */
    private int outDegree;

    /** 该表由多少条 SQL 产出 */
    private int producingSqlCount;

    public LineageTableNode(String name, String layer, int index) {
        this.name = name;
        this.layer = layer;
        this.index = index;
    }

    /** 下游为 0 —— 血缘链路的终点，即「末端产出表」，首屏列表的主角 */
    public boolean isLeaf() {
        return outDegree == 0;
    }

    /** 上游为 0 —— 血缘链路的起点，即源头表 / ODS 原始表 */
    public boolean isSource() {
        return inDegree == 0;
    }

    public String getName() {
        return name;
    }

    public String getLayer() {
        return layer;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getInDegree() {
        return inDegree;
    }

    public void setInDegree(int inDegree) {
        this.inDegree = inDegree;
    }

    public int getOutDegree() {
        return outDegree;
    }

    public void setOutDegree(int outDegree) {
        this.outDegree = outDegree;
    }

    public int getProducingSqlCount() {
        return producingSqlCount;
    }

    public void setProducingSqlCount(int producingSqlCount) {
        this.producingSqlCount = producingSqlCount;
    }

    /** 展示用短名（去掉库前缀），图上节点标签用短名更清爽 */
    public String shortName() {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    @Override
    public String toString() {
        return "LineageTableNode{" + name + ", layer=" + layer + ", in=" + inDegree + ", out=" + outDegree + "}";
    }
}
