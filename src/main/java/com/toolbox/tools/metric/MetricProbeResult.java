package com.toolbox.tools.metric;

import com.toolbox.tools.sqllineage.SqlParseStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MetricProbeResult {

    private String sqlId;
    private String source;
    private SqlParseStatus status;
    private int statementCount;
    private final List<MetricCandidate> metrics = new ArrayList<MetricCandidate>();
    private final List<DimensionCandidate> dimensions = new ArrayList<DimensionCandidate>();
    private final Set<String> sourceTables = new LinkedHashSet<String>();
    private final List<String> warnings = new ArrayList<String>();
    private final List<String> errors = new ArrayList<String>();
    private long parseMillis;

    public String getSqlId() { return sqlId; }
    public void setSqlId(String sqlId) { this.sqlId = sqlId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public SqlParseStatus getStatus() { return status; }
    public void setStatus(SqlParseStatus status) { this.status = status; }
    public int getStatementCount() { return statementCount; }
    public void setStatementCount(int statementCount) { this.statementCount = statementCount; }
    public List<MetricCandidate> getMetrics() { return metrics; }
    public List<DimensionCandidate> getDimensions() { return dimensions; }
    public Set<String> getSourceTables() { return sourceTables; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getErrors() { return errors; }
    public long getParseMillis() { return parseMillis; }
    public void setParseMillis(long parseMillis) { this.parseMillis = parseMillis; }
}
