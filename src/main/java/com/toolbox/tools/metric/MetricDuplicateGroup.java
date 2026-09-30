package com.toolbox.tools.metric;

import java.util.LinkedHashSet;
import java.util.Set;

public class MetricDuplicateGroup {

    private String normalizedExpression;
    private String expression;
    private int occurrenceCount;
    private final Set<String> metricNames = new LinkedHashSet<String>();
    private final Set<String> sqlIds = new LinkedHashSet<String>();

    public String getNormalizedExpression() { return normalizedExpression; }
    public void setNormalizedExpression(String normalizedExpression) { this.normalizedExpression = normalizedExpression; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public Set<String> getMetricNames() { return metricNames; }
    public Set<String> getSqlIds() { return sqlIds; }
}
