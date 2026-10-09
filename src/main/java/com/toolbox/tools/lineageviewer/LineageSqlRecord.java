package com.toolbox.tools.lineageviewer;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条 SQL 作业记录 —— 与上游系统已有的字段一一对应。
 *
 * <p>这是血缘计算的唯一输入。表之间的依赖关系全部由
 * {@code inputTables → outputTables} 推导，不依赖任何人工维护的图结构。</p>
 */
public class LineageSqlRecord {

    /** 来源系统，例如 hive / spark / odps */
    private String source;

    /** SQL 作业名 */
    private String name;

    /** 描述 */
    private String description;

    /** SQL 正文 */
    private String sql;

    /** 输入表清单（已从「,」或「;」分隔的字符串拆好） */
    private List<String> inputTables;

    /** 产出表清单（已从「,」或「;」分隔的字符串拆好） */
    private List<String> outputTables;

    public LineageSqlRecord() {
    }

    public LineageSqlRecord(String source, String name, String description, String sql,
                            List<String> inputTables, List<String> outputTables) {
        this.source = source;
        this.name = name;
        this.description = description;
        this.sql = sql;
        this.inputTables = inputTables;
        this.outputTables = outputTables;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    /** 永不为 null，避免调用方到处判空 */
    public List<String> getInputTables() {
        return inputTables == null ? new ArrayList<String>() : inputTables;
    }

    public void setInputTables(List<String> inputTables) {
        this.inputTables = inputTables;
    }

    /** 永不为 null，避免调用方到处判空 */
    public List<String> getOutputTables() {
        return outputTables == null ? new ArrayList<String>() : outputTables;
    }

    public void setOutputTables(List<String> outputTables) {
        this.outputTables = outputTables;
    }

    @Override
    public String toString() {
        return "LineageSqlRecord{" + name + " in=" + inputTables + " out=" + outputTables + "}";
    }
}
