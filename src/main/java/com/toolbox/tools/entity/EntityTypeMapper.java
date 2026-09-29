package com.toolbox.tools.entity;

import java.sql.Types;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entity Generator 统一类型映射。
 * Excel / DDL / JDBC ResultSetMetaData 均通过这里转换为 TypeSchema，
 * 避免不同入口维护三套不一致的数据库类型规则。
 */
public class EntityTypeMapper {

    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "^(?:number|numeric|decimal)\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\)$",
            Pattern.CASE_INSENSITIVE);

    public TypeSchema fromSourceType(String rawType) {
        if (rawType == null || rawType.trim().isEmpty()) {
            return TypeSchema.scalar(ValueKind.STRING);
        }

        String type = rawType.trim().toLowerCase(Locale.ROOT)
                .replace("java.lang.", "")
                .replace("java.math.", "")
                .replace("java.time.", "");

        if ("string".equals(type) || starts(type, "varchar", "nvarchar", "varchar2", "nvarchar2",
                "char", "nchar", "text", "clob", "nclob", "longvarchar")) {
            return TypeSchema.scalar(ValueKind.STRING);
        }

        if ("boolean".equals(type) || "bool".equals(type) || "bit".equals(type)) {
            return TypeSchema.scalar(ValueKind.BOOLEAN);
        }

        if ("integer".equals(type) || "int".equals(type) || "smallint".equals(type)
                || "tinyint".equals(type) || "short".equals(type) || "byte".equals(type)) {
            return TypeSchema.scalar(ValueKind.INTEGER);
        }

        if ("long".equals(type) || starts(type, "bigint")) {
            return TypeSchema.scalar(ValueKind.LONG);
        }

        if ("biginteger".equals(type)) {
            return TypeSchema.scalar(ValueKind.BIG_INTEGER);
        }

        if ("bigdecimal".equals(type) || starts(type, "float", "double", "real")) {
            return TypeSchema.scalar(ValueKind.BIG_DECIMAL);
        }

        Matcher number = NUMBER_PATTERN.matcher(type);
        if (number.matches()) {
            int precision = Integer.parseInt(number.group(1));
            int scale = number.group(2) == null ? 0 : Integer.parseInt(number.group(2));
            return numericType(precision, scale);
        }

        if ("number".equals(type) || "numeric".equals(type) || "decimal".equals(type)
                || type.startsWith("number ") || type.startsWith("numeric ") || type.startsWith("decimal ")) {
            return TypeSchema.scalar(ValueKind.BIG_DECIMAL);
        }

        if ("date".equals(type) || "localdate".equals(type)) {
            return TypeSchema.scalar(ValueKind.LOCAL_DATE);
        }

        if ("time".equals(type) || "localtime".equals(type)) {
            return TypeSchema.scalar(ValueKind.LOCAL_TIME);
        }

        if ("datetime".equals(type) || "timestamp".equals(type)
                || type.startsWith("timestamp(") || "localdatetime".equals(type)) {
            return TypeSchema.scalar(ValueKind.LOCAL_DATE_TIME);
        }

        if ("byte[]".equals(type) || starts(type, "binary", "varbinary", "longvarbinary", "blob", "raw")) {
            return TypeSchema.scalar(ValueKind.BYTE_ARRAY);
        }

        return TypeSchema.scalar(ValueKind.UNKNOWN);
    }

    public TypeSchema fromJdbc(int jdbcType, int precision, int scale, String typeName) {
        switch (jdbcType) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
                return TypeSchema.scalar(ValueKind.STRING);
            case Types.BOOLEAN:
            case Types.BIT:
                return TypeSchema.scalar(ValueKind.BOOLEAN);
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
                return TypeSchema.scalar(ValueKind.INTEGER);
            case Types.BIGINT:
                return TypeSchema.scalar(ValueKind.LONG);
            case Types.NUMERIC:
            case Types.DECIMAL:
                return numericType(precision, scale);
            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
                return TypeSchema.scalar(ValueKind.BIG_DECIMAL);
            case Types.DATE:
                return TypeSchema.scalar(ValueKind.LOCAL_DATE);
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
                return TypeSchema.scalar(ValueKind.LOCAL_TIME);
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return TypeSchema.scalar(ValueKind.LOCAL_DATE_TIME);
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB:
                return TypeSchema.scalar(ValueKind.BYTE_ARRAY);
            default:
                if (typeName != null && !typeName.trim().isEmpty()) {
                    TypeSchema byName = fromSourceType(typeName);
                    if (byName.getKind() != ValueKind.UNKNOWN) {
                        return byName;
                    }
                }
                return TypeSchema.scalar(ValueKind.UNKNOWN);
        }
    }

    private TypeSchema numericType(int precision, int scale) {
        if (scale > 0) return TypeSchema.scalar(ValueKind.BIG_DECIMAL);
        if (precision <= 0) return TypeSchema.scalar(ValueKind.BIG_DECIMAL);
        if (precision <= 9) return TypeSchema.scalar(ValueKind.INTEGER);
        if (precision <= 18) return TypeSchema.scalar(ValueKind.LONG);
        return TypeSchema.scalar(ValueKind.BIG_INTEGER);
    }

    private boolean starts(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) return true;
        }
        return false;
    }
}
