package com.toolbox.tools.sqllineage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SqlAnalysisResult {

    private String sqlId;
    private String source;
    private String fileName;
    private SqlParseStatus status;
    private int statementCount;
    private List<String> statementTypes = new ArrayList<String>();
    private Set<String> readTables = new LinkedHashSet<String>();
    private Set<String> writeTables = new LinkedHashSet<String>();
    private Set<String> columns = new LinkedHashSet<String>();
    private List<String> warnings = new ArrayList<String>();
    private List<String> errors = new ArrayList<String>();
    private long parseMillis;

    public String getSqlId() {
        return sqlId;
    }

    public void setSqlId(String sqlId) {
        this.sqlId = sqlId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public SqlParseStatus getStatus() {
        return status;
    }

    public void setStatus(SqlParseStatus status) {
        this.status = status;
    }

    public int getStatementCount() {
        return statementCount;
    }

    public void setStatementCount(int statementCount) {
        this.statementCount = statementCount;
    }

    public List<String> getStatementTypes() {
        return statementTypes;
    }

    public Set<String> getReadTables() {
        return readTables;
    }

    public Set<String> getWriteTables() {
        return writeTables;
    }

    public Set<String> getColumns() {
        return columns;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    public long getParseMillis() {
        return parseMillis;
    }

    public void setParseMillis(long parseMillis) {
        this.parseMillis = parseMillis;
    }
}
