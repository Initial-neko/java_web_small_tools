package com.toolbox.tools.metric;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MetricCandidate {

    private String sqlId;
    private String name;
    private String expression;
    private String normalizedExpression;
    private MetricType type = MetricType.UNKNOWN;
    private String aggregation;
    private boolean distinct;
    private boolean conditional;
    private boolean window;
    private final Set<String> sourceTables = new LinkedHashSet<String>();
    private final Set<String> sourceColumns = new LinkedHashSet<String>();
    private final List<String> dimensions = new ArrayList<String>();
    private String whereCondition;
    private String havingCondition;
    private final List<String> reasons = new ArrayList<String>();

    public String getSqlId() { return sqlId; }
    public void setSqlId(String sqlId) { this.sqlId = sqlId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getNormalizedExpression() { return normalizedExpression; }
    public void setNormalizedExpression(String normalizedExpression) { this.normalizedExpression = normalizedExpression; }
    public MetricType getType() { return type; }
    public void setType(MetricType type) { this.type = type; }
    public String getAggregation() { return aggregation; }
    public void setAggregation(String aggregation) { this.aggregation = aggregation; }
    public boolean isDistinct() { return distinct; }
    public void setDistinct(boolean distinct) { this.distinct = distinct; }
    public boolean isConditional() { return conditional; }
    public void setConditional(boolean conditional) { this.conditional = conditional; }
    public boolean isWindow() { return window; }
    public void setWindow(boolean window) { this.window = window; }
    public Set<String> getSourceTables() { return sourceTables; }
    public Set<String> getSourceColumns() { return sourceColumns; }
    public List<String> getDimensions() { return dimensions; }
    public String getWhereCondition() { return whereCondition; }
    public void setWhereCondition(String whereCondition) { this.whereCondition = whereCondition; }
    public String getHavingCondition() { return havingCondition; }
    public void setHavingCondition(String havingCondition) { this.havingCondition = havingCondition; }
    public List<String> getReasons() { return reasons; }
}
