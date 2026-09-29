package com.toolbox.tools.entity;

import com.toolbox.tools.excel.ExcelFile;
import com.toolbox.tools.excel.ExcelRow;
import com.toolbox.tools.excel.ExcelCell;
import com.toolbox.tools.excel.ExcelSessionManager;
import com.toolbox.tools.excel.ExcelSheet;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelEntityControllerTest {

    @Test
    void shouldGenerateEntityFromUploadedExcelSession() {
        ExcelSessionManager sessions = new ExcelSessionManager();

        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("USER_SCHEMA");
        sheet.setTotalRows(3);
        sheet.setTotalCols(2);

        ExcelRow headers = new ExcelRow(0);
        headers.addCell(new ExcelCell("USER_ID"));
        headers.addCell(new ExcelCell("USER_NAME"));

        ExcelRow types = new ExcelRow(1);
        types.addCell(new ExcelCell("NUMBER(10,0)"));
        types.addCell(new ExcelCell("VARCHAR2(100)"));

        ExcelRow comments = new ExcelRow(2);
        comments.addCell(new ExcelCell("用户ID"));
        comments.addCell(new ExcelCell("用户名称"));

        sheet.getRows().add(headers);
        sheet.getRows().add(types);
        sheet.getRows().add(comments);

        ExcelFile file = new ExcelFile();
        file.setFileName("schema.xlsx");
        file.addSheet(sheet);

        String fileId = sessions.put(file);
        ExcelEntityController controller = new ExcelEntityController(sessions);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sheetIndex", 0);
        params.put("className", "USER_ENTITY");
        params.put("packageName", "demo");
        params.put("mode", "normal");
        params.put("headerRow", 1);
        params.put("typeRow", 2);
        params.put("commentRow", 3);

        Map<String, Object> result = controller.generate(fileId, params);

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("UserEntity", result.get("className"));
        assertEquals(2, result.get("fieldCount"));
        String output = String.valueOf(result.get("output"));
        assertTrue(output.contains("private Long userId;"));
        assertTrue(output.contains("用户名称"));
    }
}
