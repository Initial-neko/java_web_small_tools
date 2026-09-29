package com.toolbox.tools.entity;

import com.toolbox.tools.excel.ExcelCell;
import com.toolbox.tools.excel.ExcelRow;
import com.toolbox.tools.excel.ExcelSheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcelEntitySchemaParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "^(?:number|numeric|decimal)\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\)$",
            Pattern.CASE_INSENSITIVE);

    public EntitySchema parse(ExcelSheet sheet, String className, String packageName,
                              int headerRow, int typeRow, int commentRow) {
        if (sheet == null) throw new IllegalArgumentException("sheet 不能为空");
        if (headerRow <= 0) throw new IllegalArgumentException("headerRow 从 1 开始");

        ExcelRow headers = row(sheet, headerRow);
        ExcelRow types = typeRow > 0 ? rowOrNull(sheet, typeRow) : null;
        ExcelRow comments = commentRow > 0 ? rowOrNull(sheet, commentRow) : null;

        List<FieldSchema> fields = new ArrayList<FieldSchema>();
        Map<String, Integer> nameCounts = new HashMap<String, Integer>();
        int totalCols = Math.max(sheet.getTotalCols(), headers.getCells().size());

        for (int col = 0; col < totalCols; col++) {
            String sourceName = cell(headers, col).trim();
            if (sourceName.isEmpty()) continue;

            String base = JavaNameUtils.toFieldName(sourceName);
            String fieldName = uniqueName(base, nameCounts);
            String sourceType = types == null ? "" : cell(types, col).trim();
            String comment = comments == null ? "" : cell(comments, col).trim();

            fields.add(new FieldSchema(
                    sourceName,
                    fieldName,
                    parseType(sourceType),
                    sourceType,
                    comment
            ));
        }

        if (fields.isEmpty()) {
            throw new IllegalArgumentException("headerRow 没有找到有效字段");
        }

        return new EntitySchema(packageName, JavaNameUtils.toClassName(className), fields);
    }

    public TypeSchema parseType(String rawType) {
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
            if (scale > 0) return TypeSchema.scalar(ValueKind.BIG_DECIMAL);
            if (precision <= 9) return TypeSchema.scalar(ValueKind.INTEGER);
            if (precision <= 18) return TypeSchema.scalar(ValueKind.LONG);
            return TypeSchema.scalar(ValueKind.BIG_INTEGER);
        }

        if ("number".equals(type) || "numeric".equals(type) || "decimal".equals(type)
                || type.startsWith("number ") || type.startsWith("numeric ") || type.startsWith("decimal ")) {
            return TypeSchema.scalar(ValueKind.BIG_DECIMAL);
        }

        if ("date".equals(type) || "localdate".equals(type)) {
            return TypeSchema.scalar(ValueKind.LOCAL_DATE);
        }

        if ("datetime".equals(type) || "timestamp".equals(type)
                || type.startsWith("timestamp(") || "localdatetime".equals(type)) {
            return TypeSchema.scalar(ValueKind.LOCAL_DATE_TIME);
        }

        if ("object".equals(type)) {
            return TypeSchema.scalar(ValueKind.UNKNOWN);
        }

        return TypeSchema.scalar(ValueKind.UNKNOWN);
    }

    private boolean starts(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) return true;
        }
        return false;
    }

    private ExcelRow row(ExcelSheet sheet, int oneBasedRow) {
        ExcelRow result = rowOrNull(sheet, oneBasedRow);
        if (result == null) {
            throw new IllegalArgumentException("Excel 行不存在: " + oneBasedRow);
        }
        return result;
    }

    private ExcelRow rowOrNull(ExcelSheet sheet, int oneBasedRow) {
        int index = oneBasedRow - 1;
        if (index < 0 || index >= sheet.getRows().size()) return null;
        return sheet.getRows().get(index);
    }

    private String cell(ExcelRow row, int col) {
        if (row == null || col < 0 || col >= row.getCells().size()) return "";
        ExcelCell cell = row.getCells().get(col);
        return cell == null || cell.getValue() == null ? "" : cell.getValue();
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
