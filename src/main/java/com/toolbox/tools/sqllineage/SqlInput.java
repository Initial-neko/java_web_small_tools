package com.toolbox.tools.sqllineage;

import java.util.LinkedHashMap;
import java.util.Map;

public class SqlInput {

    private String sqlId;
    private String sql;
    private String source;
    private String fileName;
    private Map<String, String> metadata = new LinkedHashMap<String, String>();

    public SqlInput() {
    }

    public SqlInput(String sqlId, String sql) {
        this.sqlId = sqlId;
        this.sql = sql;
    }

    public String getSqlId() {
        return sqlId;
    }

    public void setSqlId(String sqlId) {
        this.sqlId = sqlId;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
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

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata == null
                ? new LinkedHashMap<String, String>()
                : new LinkedHashMap<String, String>(metadata);
    }
}
