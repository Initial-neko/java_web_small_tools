package com.toolbox.tools.entity;

import com.toolbox.tools.excel.ExcelFile;
import com.toolbox.tools.excel.ExcelSessionManager;
import com.toolbox.tools.excel.ExcelSheet;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/excel")
public class ExcelEntityController {

    private final ExcelSessionManager sessionManager;
    private final ExcelEntitySchemaParser parser = new ExcelEntitySchemaParser();
    private final NormalJavaRenderer normalRenderer = new NormalJavaRenderer();
    private final LombokJavaRenderer lombokRenderer = new LombokJavaRenderer();

    public ExcelEntityController(ExcelSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @PostMapping("/{fileId}/entity")
    public Map<String, Object> generate(@PathVariable String fileId,
                                        @RequestBody Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();

        try {
            ExcelFile file = sessionManager.get(fileId);
            if (file == null) {
                return fail("文件已过期或不存在，请重新上传");
            }

            int sheetIndex = intValue(params.get("sheetIndex"), 0);
            if (sheetIndex < 0 || sheetIndex >= file.getSheets().size()) {
                return fail("Sheet 索引越界: " + sheetIndex);
            }

            ExcelSheet sheet = file.getSheets().get(sheetIndex);
            String className = str(params.get("className"), sheet.getName());
            String packageName = str(params.get("packageName"), "com.example.model");
            String mode = str(params.get("mode"), "normal");
            int headerRow = intValue(params.get("headerRow"), 1);
            int typeRow = intValue(params.get("typeRow"), 2);
            int commentRow = intValue(params.get("commentRow"), 3);

            EntitySchema schema = parser.parse(
                    sheet, className, packageName, headerRow, typeRow, commentRow);

            String output;
            if ("normal".equals(mode)) {
                output = normalRenderer.render(schema);
            } else if ("lombok".equals(mode)) {
                output = lombokRenderer.render(schema);
            } else {
                return fail("未知 mode: " + mode + "，可选 normal/lombok");
            }

            List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
            for (FieldSchema field : schema.getFields()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("sourceName", field.getSourceName());
                item.put("fieldName", field.getFieldName());
                item.put("sourceType", field.getSourceType());
                item.put("javaType", field.getJavaType());
                item.put("comment", field.getComment());
                fields.add(item);
            }

            result.put("success", true);
            result.put("fileName", file.getFileName());
            result.put("sheetIndex", sheetIndex);
            result.put("sheetName", sheet.getName());
            result.put("className", schema.getClassName());
            result.put("packageName", schema.getPackageName());
            result.put("mode", mode);
            result.put("fieldCount", schema.getFields().size());
            result.put("fields", fields);
            result.put("output", output);
            return result;
        } catch (Exception e) {
            return fail("Excel Entity 生成失败: " + e.getMessage());
        }
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }

    private String str(Object value, String def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }

    private int intValue(Object value, int def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return def;
        return Integer.parseInt(text);
    }
}
