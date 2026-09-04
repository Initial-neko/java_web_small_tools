package com.toolbox.tools.excel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    @Autowired
    private ExcelSessionManager sessionManager;
    private final ExcelParser parser = new ExcelParser();

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (file == null || file.isEmpty()) {
            result.put("success", false); result.put("message", "请选择文件"); return result;
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.toLowerCase().endsWith(".xlsx") && !fileName.toLowerCase().endsWith(".xls"))) {
            result.put("success", false); result.put("message", "仅支持 .xlsx 和 .xls 格式"); return result;
        }
        try {
            ExcelFile ef = parser.parse(file.getInputStream(), fileName);
            String fileId = sessionManager.put(ef);
            List<Map<String, Object>> sheetInfos = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < ef.getSheets().size(); i++) {
                ExcelSheet s = ef.getSheets().get(i);
                Map<String, Object> info = new LinkedHashMap<String, Object>();
                info.put("index", i); info.put("name", s.getName());
                info.put("totalRows", s.getTotalRows()); info.put("totalCols", s.getTotalCols());
                sheetInfos.add(info);
            }
            result.put("success", true); result.put("fileId", fileId);
            result.put("fileName", fileName); result.put("sheets", sheetInfos);
        } catch (Exception e) {
            result.put("success", false); result.put("message", "解析失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/{fileId}/sheet/{sheetIndex}")
    public Map<String, Object> getSheet(@PathVariable String fileId, @PathVariable int sheetIndex,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "100") int size) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        ExcelFile ef = sessionManager.get(fileId);
        if (ef == null) { result.put("success", false); result.put("message", "文件已过期或不存在，请重新上传"); return result; }
        if (sheetIndex < 0 || sheetIndex >= ef.getSheets().size()) {
            result.put("success", false); result.put("message", "Sheet 索引越界"); return result;
        }
        ExcelSheet sheet = ef.getSheets().get(sheetIndex);
        int totalRows = sheet.getTotalRows();
        int totalPages = (int) Math.ceil((double) totalRows / size);
        if (page < 1) page = 1;
        if (page > totalPages && totalPages > 0) page = totalPages;
        int from = (page - 1) * size;
        int to = Math.min(from + size, totalRows);
        List<ExcelRow> pageRows = new ArrayList<ExcelRow>();
        if (from < totalRows) pageRows = new ArrayList<ExcelRow>(sheet.getRows().subList(from, to));

        result.put("success", true);
        result.put("sheetName", sheet.getName());
        result.put("sheetIndex", sheetIndex);
        result.put("totalRows", totalRows);
        result.put("totalCols", sheet.getTotalCols());
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", totalPages);
        result.put("columnWidths", sheet.getColumnWidths());
        result.put("mergedRegions", sheet.getMergedRegions());
        result.put("rows", pageRows);
        return result;
    }

    @DeleteMapping("/{fileId}")
    public Map<String, Object> remove(@PathVariable String fileId) {
        sessionManager.remove(fileId);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("success", true); result.put("message", "已清理");
        return result;
    }
}
