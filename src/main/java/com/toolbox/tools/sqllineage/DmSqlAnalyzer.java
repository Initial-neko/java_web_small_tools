package com.toolbox.tools.sqllineage;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DmSqlAnalyzer {

    public SqlAnalysisResult analyze(SqlInput input) {
        long started = System.nanoTime();
        SqlAnalysisResult result = new SqlAnalysisResult();

        if (input != null) {
            result.setSqlId(input.getSqlId());
            result.setSource(input.getSource());
            result.setFileName(input.getFileName());
        }

        try {
            if (input == null || input.getSql() == null || input.getSql().trim().isEmpty()) {
                result.setStatus(SqlParseStatus.PARSE_FAILED);
                result.getErrors().add("SQL 不能为空");
                return result;
            }

            List<SQLStatement> statements = SQLUtils.parseStatements(input.getSql(), DbType.dm);
            result.setStatementCount(statements.size());

            if (statements.isEmpty()) {
                result.setStatus(SqlParseStatus.PARSE_FAILED);
                result.getErrors().add("Druid 未解析出 SQL Statement");
                return result;
            }

            boolean partial = false;
            for (SQLStatement statement : statements) {
                String type = statementType(statement);
                result.getStatementTypes().add(type);
                if ("UNKNOWN".equals(type)) {
                    partial = true;
                    result.getWarnings().add("未识别 statement type: " +
                            statement.getClass().getName());
                }

                SchemaStatVisitor visitor = SQLUtils.createSchemaStatVisitor(DbType.dm);
                statement.accept(visitor);

                for (Map.Entry<TableStat.Name, TableStat> entry : visitor.getTables().entrySet()) {
                    String tableName = entry.getKey().getName();
                    TableStat stat = entry.getValue();

                    if (stat.getSelectCount() > 0) {
                        result.getReadTables().add(tableName);
                    }

                    if (stat.getInsertCount() > 0
                            || stat.getUpdateCount() > 0
                            || stat.getDeleteCount() > 0
                            || stat.getMergeCount() > 0
                            || stat.getCreateCount() > 0
                            || stat.getAlterCount() > 0
                            || stat.getDropCount() > 0) {
                        result.getWriteTables().add(tableName);
                    }
                }

                for (TableStat.Column column : visitor.getColumns()) {
                    result.getColumns().add(column.getFullName());
                }
            }

            result.setStatus(partial ? SqlParseStatus.PARTIAL : SqlParseStatus.SUCCESS);
            return result;
        } catch (Throwable error) {
            result.setStatus(SqlParseStatus.PARSE_FAILED);
            result.getErrors().add(rootMessage(error));
            return result;
        } finally {
            result.setParseMillis((System.nanoTime() - started) / 1000000L);
        }
    }

    public BatchSqlAnalysisResult analyzeBatch(List<SqlInput> inputs) {
        BatchSqlAnalysisResult batch = new BatchSqlAnalysisResult();
        if (inputs == null) {
            return batch;
        }

        batch.setTotal(inputs.size());
        Map<String, TableLineageEdge> edges = new LinkedHashMap<String, TableLineageEdge>();

        for (SqlInput input : inputs) {
            SqlAnalysisResult result = analyze(input);
            batch.getResults().add(result);
            batch.getReadTables().addAll(result.getReadTables());
            batch.getWriteTables().addAll(result.getWriteTables());

            if (result.getStatus() == SqlParseStatus.SUCCESS) {
                batch.setSuccess(batch.getSuccess() + 1);
            } else if (result.getStatus() == SqlParseStatus.PARTIAL) {
                batch.setPartial(batch.getPartial() + 1);
            } else if (result.getStatus() == SqlParseStatus.UNSUPPORTED) {
                batch.setUnsupported(batch.getUnsupported() + 1);
            } else {
                batch.setFailed(batch.getFailed() + 1);
            }

            if (result.getStatus() == SqlParseStatus.SUCCESS
                    || result.getStatus() == SqlParseStatus.PARTIAL) {
                for (String sourceTable : result.getReadTables()) {
                    for (String targetTable : result.getWriteTables()) {
                        if (sameTable(sourceTable, targetTable)) continue;
                        String key = normalize(sourceTable) + "->" + normalize(targetTable);
                        TableLineageEdge edge = edges.get(key);
                        if (edge == null) {
                            edge = new TableLineageEdge(sourceTable, targetTable);
                            edges.put(key, edge);
                        }
                        edge.addSqlId(result.getSqlId());
                    }
                }
            }
        }

        batch.getTableLineageEdges().addAll(edges.values());
        return batch;
    }

    private boolean sameTable(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String statementType(SQLStatement statement) {
        String name = statement.getClass().getSimpleName().toUpperCase(Locale.ROOT);

        if (name.contains("CREATE") && name.contains("VIEW")) return "CREATE_VIEW";
        if (name.contains("CREATE") && name.contains("TABLE")) return "CREATE_TABLE";
        if (name.contains("SELECT")) return "SELECT";
        if (name.contains("INSERT")) return "INSERT";
        if (name.contains("UPDATE")) return "UPDATE";
        if (name.contains("DELETE")) return "DELETE";
        if (name.contains("MERGE")) return "MERGE";
        if (name.contains("ALTER")) return "ALTER";
        if (name.contains("DROP")) return "DROP";

        return "UNKNOWN";
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return current.getClass().getName();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }
}
