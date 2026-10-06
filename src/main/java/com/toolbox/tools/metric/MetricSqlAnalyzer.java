package com.toolbox.tools.metric;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.druid.sql.ast.expr.SQLCaseExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectGroupByClause;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectQuery;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.toolbox.tools.sqllineage.SqlInput;
import com.toolbox.tools.sqllineage.SqlParseStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MetricSqlAnalyzer {

    public MetricProbeResult analyze(SqlInput input) {
        long started = System.nanoTime();
        MetricProbeResult result = new MetricProbeResult();

        if (input != null) {
            result.setSqlId(input.getSqlId());
            result.setSource(input.getSource());
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

            boolean unsupported = false;
            for (SQLStatement statement : statements) {
                if (!(statement instanceof SQLSelectStatement)) {
                    unsupported = true;
                    result.getWarnings().add("指标探查第一版仅处理 SELECT/WITH，跳过: "
                            + statement.getClass().getSimpleName());
                    continue;
                }

                Set<String> tables = readTables(statement);
                result.getSourceTables().addAll(tables);

                SQLSelect select = ((SQLSelectStatement) statement).getSelect();
                List<SQLSelectQueryBlock> blocks = new ArrayList<SQLSelectQueryBlock>();
                collectMainQueryBlocks(select.getQuery(), blocks);

                for (SQLSelectQueryBlock block : blocks) {
                    analyzeQueryBlock(input.getSqlId(), block, tables, result);
                }
            }

            if (result.getMetrics().isEmpty() && unsupported) {
                result.setStatus(SqlParseStatus.UNSUPPORTED);
            } else if (unsupported) {
                result.setStatus(SqlParseStatus.PARTIAL);
            } else {
                result.setStatus(SqlParseStatus.SUCCESS);
            }
            return result;
        } catch (Throwable error) {
            result.setStatus(SqlParseStatus.PARSE_FAILED);
            result.getErrors().add(rootMessage(error));
            return result;
        } finally {
            result.setParseMillis((System.nanoTime() - started) / 1000000L);
        }
    }

    public BatchMetricProbeResult analyzeBatch(List<SqlInput> inputs) {
        BatchMetricProbeResult batch = new BatchMetricProbeResult();
        if (inputs == null) return batch;

        batch.setTotal(inputs.size());
        Map<String, MetricDuplicateGroup> duplicateMap =
                new LinkedHashMap<String, MetricDuplicateGroup>();

        for (SqlInput input : inputs) {
            MetricProbeResult result = analyze(input);
            batch.getResults().add(result);
            batch.setMetricCount(batch.getMetricCount() + result.getMetrics().size());

            if (result.getStatus() == SqlParseStatus.SUCCESS) {
                batch.setSuccess(batch.getSuccess() + 1);
            } else if (result.getStatus() == SqlParseStatus.PARTIAL) {
                batch.setPartial(batch.getPartial() + 1);
            } else if (result.getStatus() == SqlParseStatus.UNSUPPORTED) {
                batch.setUnsupported(batch.getUnsupported() + 1);
            } else {
                batch.setFailed(batch.getFailed() + 1);
            }

            for (MetricCandidate metric : result.getMetrics()) {
                String key = metric.getNormalizedExpression();
                if (key == null || key.isEmpty()) continue;

                MetricDuplicateGroup group = duplicateMap.get(key);
                if (group == null) {
                    group = new MetricDuplicateGroup();
                    group.setNormalizedExpression(key);
                    group.setExpression(metric.getExpression());
                    duplicateMap.put(key, group);
                }

                group.setOccurrenceCount(group.getOccurrenceCount() + 1);
                if (metric.getName() != null && !metric.getName().trim().isEmpty()) {
                    group.getMetricNames().add(metric.getName());
                }
                if (metric.getSqlId() != null && !metric.getSqlId().trim().isEmpty()) {
                    group.getSqlIds().add(metric.getSqlId());
                }
            }
        }

        for (MetricDuplicateGroup group : duplicateMap.values()) {
            if (group.getOccurrenceCount() > 1) {
                batch.getDuplicateGroups().add(group);
            }
        }
        return batch;
    }

    private void analyzeQueryBlock(String sqlId,
                                   SQLSelectQueryBlock block,
                                   Set<String> tables,
                                   MetricProbeResult result) {
        List<String> dimensions = dimensions(block, result);
        String where = sqlText(block.getWhere());
        SQLSelectGroupByClause groupBy = block.getGroupBy();
        String having = groupBy == null ? null : sqlText(groupBy.getHaving());

        for (SQLSelectItem item : block.getSelectList()) {
            SQLExpr expr = item.getExpr();
            ExpressionStats stats = inspect(expr);

            if (!stats.isMetric()) {
                continue;
            }

            MetricCandidate candidate = new MetricCandidate();
            candidate.setSqlId(sqlId);
            candidate.setName(metricName(item, expr));
            candidate.setExpression(sqlText(expr));
            candidate.setNormalizedExpression(normalizeExpression(expr));
            candidate.setType(classify(stats));
            candidate.setAggregation(join(stats.aggregateMethods));
            candidate.setDistinct(stats.distinct);
            candidate.setConditional(stats.caseExpression);
            candidate.setWindow(stats.window);
            candidate.getSourceColumns().addAll(stats.columns);
            candidate.getSourceTables().addAll(tables);
            candidate.getDimensions().addAll(dimensions);
            candidate.setWhereCondition(where);
            candidate.setHavingCondition(having);
            candidate.getReasons().addAll(reasons(stats, item));

            result.getMetrics().add(candidate);
        }
    }

    private List<String> dimensions(SQLSelectQueryBlock block, MetricProbeResult result) {
        List<String> values = new ArrayList<String>();
        SQLSelectGroupByClause group = block.getGroupBy();
        if (group == null) return values;

        for (SQLExpr expr : group.getItems()) {
            String expression = sqlText(expr);
            String name = findSelectAlias(block, expr);
            if (name == null || name.trim().isEmpty()) {
                name = expression;
            }
            values.add(name);
            result.getDimensions().add(new DimensionCandidate(name, expression));
        }
        return values;
    }

    private String findSelectAlias(SQLSelectQueryBlock block, SQLExpr groupExpr) {
        String normalized = normalizeExpression(groupExpr);
        for (SQLSelectItem item : block.getSelectList()) {
            if (normalized.equals(normalizeExpression(item.getExpr()))) {
                return item.getAlias();
            }
        }
        return null;
    }

    private ExpressionStats inspect(SQLExpr expr) {
        final ExpressionStats stats = new ExpressionStats();
        expr.accept(new SQLASTVisitorAdapter() {
            @Override
            public boolean visit(SQLAggregateExpr x) {
                String method = x.getMethodName();
                if (method != null) {
                    stats.aggregateMethods.add(method.toUpperCase(Locale.ROOT));
                }
                if (x.isDistinct()) {
                    stats.distinct = true;
                }
                if (x.getOver() != null) {
                    stats.window = true;
                }
                return true;
            }

            @Override
            public boolean visit(SQLCaseExpr x) {
                stats.caseExpression = true;
                return true;
            }

            @Override
            public boolean visit(SQLBinaryOpExpr x) {
                SQLBinaryOperator op = x.getOperator();
                if (op == SQLBinaryOperator.Add
                        || op == SQLBinaryOperator.Subtract
                        || op == SQLBinaryOperator.Multiply
                        || op == SQLBinaryOperator.Divide
                        || op == SQLBinaryOperator.Modulus) {
                    stats.arithmetic = true;
                    if (op == SQLBinaryOperator.Divide) {
                        stats.division = true;
                    }
                }
                return true;
            }

            @Override
            public boolean visit(SQLPropertyExpr x) {
                stats.columns.add(x.toString());
                return false;
            }

            @Override
            public boolean visit(SQLIdentifierExpr x) {
                stats.columns.add(x.getName());
                return false;
            }
        });
        return stats;
    }

    private MetricType classify(ExpressionStats stats) {
        if (stats.window) return MetricType.WINDOW;
        if (stats.division && !stats.aggregateMethods.isEmpty()) return MetricType.RATIO;
        if (stats.arithmetic && !stats.aggregateMethods.isEmpty()) return MetricType.DERIVED;
        if (stats.caseExpression && !stats.aggregateMethods.isEmpty()) {
            return MetricType.CONDITIONAL_AGGREGATION;
        }

        if (stats.aggregateMethods.size() == 1) {
            String method = stats.aggregateMethods.iterator().next();
            if ("SUM".equals(method)) return MetricType.SUM;
            if ("COUNT".equals(method)) return stats.distinct
                    ? MetricType.COUNT_DISTINCT : MetricType.COUNT;
            if ("AVG".equals(method)) return MetricType.AVG;
            if ("MIN".equals(method)) return MetricType.MIN;
            if ("MAX".equals(method)) return MetricType.MAX;
        }
        return MetricType.DERIVED;
    }

    private List<String> reasons(ExpressionStats stats, SQLSelectItem item) {
        List<String> reasons = new ArrayList<String>();
        if (!stats.aggregateMethods.isEmpty()) {
            reasons.add("SELECT 表达式包含聚合函数: " + join(stats.aggregateMethods));
        }
        if (stats.distinct) reasons.add("聚合使用 DISTINCT");
        if (stats.caseExpression) reasons.add("聚合表达式包含 CASE WHEN 条件口径");
        if (stats.division) reasons.add("聚合结果参与除法，识别为比率候选");
        else if (stats.arithmetic) reasons.add("聚合结果参与算术表达式，识别为派生指标");
        if (stats.window) reasons.add("表达式包含 OVER 窗口定义");
        if (item.getAlias() != null && !item.getAlias().trim().isEmpty()) {
            reasons.add("存在显式别名: " + item.getAlias());
        }
        return reasons;
    }

    private Set<String> readTables(SQLStatement statement) {
        Set<String> tables = new LinkedHashSet<String>();
        SchemaStatVisitor visitor = SQLUtils.createSchemaStatVisitor(DbType.dm);
        statement.accept(visitor);
        for (Map.Entry<TableStat.Name, TableStat> entry : visitor.getTables().entrySet()) {
            if (entry.getValue().getSelectCount() > 0) {
                tables.add(entry.getKey().getName());
            }
        }
        return tables;
    }

    private void collectMainQueryBlocks(SQLSelectQuery query, List<SQLSelectQueryBlock> blocks) {
        if (query instanceof SQLSelectQueryBlock) {
            blocks.add((SQLSelectQueryBlock) query);
            return;
        }

        if (query instanceof SQLUnionQuery) {
            for (SQLSelectQuery relation : ((SQLUnionQuery) query).getRelations()) {
                collectMainQueryBlocks(relation, blocks);
            }
        }
    }

    private String metricName(SQLSelectItem item, SQLExpr expr) {
        if (item.getAlias() != null && !item.getAlias().trim().isEmpty()) {
            return SQLUtils.normalize(item.getAlias());
        }
        return sqlText(expr);
    }

    private String normalizeExpression(SQLExpr expr) {
        String text = sqlText(expr);
        if (text == null) return "";
        // Druid has already serialized the AST. Normalize only outside quoted tokens:
        // literal whitespace, escaped quotes and quoted identifier case are semantic.
        StringBuilder normalized = new StringBuilder();
        char quote = 0;
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                normalized.append(c);
                if (c == quote) {
                    if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                        normalized.append(text.charAt(++i));
                    } else {
                        quote = 0;
                    }
                }
            } else if (Character.isWhitespace(c)) {
                space = normalized.length() > 0;
            } else {
                if (space) normalized.append(' ');
                space = false;
                if (c == '\'' || c == '"' || c == '`') {
                    quote = c;
                    normalized.append(c);
                } else {
                    normalized.append(Character.toUpperCase(c));
                }
            }
        }
        return normalized.toString();
    }

    private String sqlText(SQLExpr expr) {
        return expr == null ? null : SQLUtils.toSQLString(expr, DbType.dm);
    }

    private String join(Set<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(",");
            out.append(value);
        }
        return out.toString();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName()
                : current.getClass().getSimpleName() + ": " + message;
    }

    private static class ExpressionStats {
        final Set<String> aggregateMethods = new LinkedHashSet<String>();
        final Set<String> columns = new LinkedHashSet<String>();
        boolean distinct;
        boolean caseExpression;
        boolean arithmetic;
        boolean division;
        boolean window;

        boolean isMetric() {
            return !aggregateMethods.isEmpty() || window;
        }
    }
}
