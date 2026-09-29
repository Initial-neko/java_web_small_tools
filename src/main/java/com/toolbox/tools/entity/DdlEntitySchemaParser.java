package com.toolbox.tools.entity;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DDL -> EntitySchema。
 * 使用 Druid AST，不通过正则解析 CREATE TABLE。
 */
public class DdlEntitySchemaParser {

    private final EntityTypeMapper typeMapper = new EntityTypeMapper();

    public EntitySchema parse(String ddl, String databaseType, String className, String packageName) {
        if (ddl == null || ddl.trim().isEmpty()) {
            throw new IllegalArgumentException("DDL 不能为空");
        }

        DbType dbType = dbType(databaseType);
        SQLStatement statement = SQLUtils.parseSingleStatement(ddl, dbType);
        if (!(statement instanceof SQLCreateTableStatement)) {
            throw new IllegalArgumentException("当前仅支持 CREATE TABLE DDL");
        }

        SQLCreateTableStatement create = (SQLCreateTableStatement) statement;
        String resolvedClassName = className == null || className.trim().isEmpty()
                ? create.getTableName()
                : className;

        List<FieldSchema> fields = new ArrayList<FieldSchema>();
        Map<String, Integer> nameCounts = new HashMap<String, Integer>();

        for (SQLColumnDefinition column : create.getColumnDefinitions()) {
            String sourceName = SQLUtils.normalize(column.getColumnName(), dbType);
            String fieldName = uniqueName(JavaNameUtils.toFieldName(sourceName), nameCounts);
            String sourceType = column.getDataType() == null ? "" : column.getDataType().toString();
            String comment = commentText(column.getComment());

            fields.add(new FieldSchema(
                    sourceName,
                    fieldName,
                    typeMapper.fromSourceType(sourceType),
                    sourceType,
                    comment
            ));
        }

        if (fields.isEmpty()) {
            throw new IllegalArgumentException("CREATE TABLE 中没有解析到字段");
        }

        return new EntitySchema(
                packageName,
                JavaNameUtils.toClassName(resolvedClassName),
                fields
        );
    }

    private DbType dbType(String databaseType) {
        String value = databaseType == null ? "dm" : databaseType.trim().toLowerCase(Locale.ROOT);
        if ("dm".equals(value)) return DbType.dm;
        if ("oracle".equals(value)) return DbType.oracle;
        throw new IllegalArgumentException("DDL 当前支持 databaseType=dm/oracle");
    }

    private String commentText(SQLExpr comment) {
        if (comment == null) return "";
        if (comment instanceof SQLCharExpr) {
            return ((SQLCharExpr) comment).getText();
        }
        return comment.toString();
    }

    private String uniqueName(String base, Map<String, Integer> counts) {
        Integer count = counts.get(base);
        if (count == null) {
            counts.put(base, 1);
            return base;
        }
        int next = count + 1;
        counts.put(base, next);
        return base + next;
    }
}
