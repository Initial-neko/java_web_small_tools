package com.toolbox.tools.sqllineage;

import java.util.LinkedHashSet;
import java.util.Set;

public class TableLineageEdge {

    private final String sourceTable;
    private final String targetTable;
    private final Set<String> sqlIds = new LinkedHashSet<String>();

    public TableLineageEdge(String sourceTable, String targetTable) {
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public Set<String> getSqlIds() {
        return sqlIds;
    }

    public void addSqlId(String sqlId) {
        if (sqlId != null && !sqlId.trim().isEmpty()) {
            sqlIds.add(sqlId);
        }
    }
}
