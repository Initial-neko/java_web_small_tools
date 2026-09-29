package com.toolbox.tools.entity;

import com.toolbox.tools.excel.ExcelCell;
import com.toolbox.tools.excel.ExcelRow;
import com.toolbox.tools.excel.ExcelSheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelEntitySchemaParser {

    private final EntityTypeMapper typeMapper = new EntityTypeMapper();

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
        return typeMapper.fromSourceType(rawType);
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
