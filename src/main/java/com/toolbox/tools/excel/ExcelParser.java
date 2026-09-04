package com.toolbox.tools.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

import java.io.InputStream;

public class ExcelParser {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    public ExcelFile parse(InputStream inputStream, String fileName) throws Exception {
        ExcelFile excelFile = new ExcelFile();
        excelFile.setFileName(fileName);
        Workbook workbook = WorkbookFactory.create(inputStream);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            excelFile.addSheet(parseSheet(workbook.getSheetAt(i), workbook));
        }
        workbook.close();
        return excelFile;
    }

    private ExcelSheet parseSheet(Sheet sheet, Workbook workbook) {
        ExcelSheet es = new ExcelSheet();
        es.setName(sheet.getSheetName());
        int lastRow = sheet.getLastRowNum();
        int maxCol = 0;

        for (int c = 0; c < 50; c++) {
            int w = sheet.getColumnWidth(c);
            if (w > 0) es.getColumnWidths().add(w / 36);
        }

        for (CellRangeAddress m : sheet.getMergedRegions()) {
            es.getMergedRegions().add(new ExcelSheet.MergedRegion(
                    m.getFirstRow(), m.getLastRow(), m.getFirstColumn(), m.getLastColumn()));
        }

        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            ExcelRow er = new ExcelRow(r);
            if (row == null) { es.getRows().add(er); continue; }
            int lastCell = row.getLastCellNum();
            if (lastCell > maxCol) maxCol = lastCell;
            for (int c = 0; c < lastCell; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) { er.addCell(new ExcelCell("")); }
                else { er.addCell(new ExcelCell(DATA_FORMATTER.formatCellValue(cell), extractStyle(cell, workbook))); }
            }
            es.getRows().add(er);
        }
        es.setTotalRows(lastRow + 1);
        es.setTotalCols(maxCol);
        return es;
    }

    private ExcelCellStyle extractStyle(Cell cell, Workbook workbook) {
        ExcelCellStyle style = new ExcelCellStyle();
        CellStyle cs = cell.getCellStyle();
        if (cs == null) return style;
        try {
            Font font = workbook.getFontAt(cs.getFontIndexAsInt());
            if (font != null) {
                style.setBold(font.getBold());
                style.setFontSize(font.getFontHeightInPoints());
                style.setFontColor(extractFontColor(font));
            }
        } catch (Exception e) {}
        style.setBgColor(extractBgColor(cs));
        HorizontalAlignment ha = cs.getAlignment();
        if (ha != null) {
            switch (ha) {
                case CENTER: style.setAlignment("center"); break;
                case RIGHT: style.setAlignment("right"); break;
                default: style.setAlignment("left");
            }
        }
        style.setBorder(cs.getBorderTop() != BorderStyle.NONE
                || cs.getBorderBottom() != BorderStyle.NONE
                || cs.getBorderLeft() != BorderStyle.NONE
                || cs.getBorderRight() != BorderStyle.NONE);
        return style;
    }

    private String extractFontColor(Font font) {
        try {
            if (font instanceof XSSFFont) {
                XSSFColor color = ((XSSFFont) font).getXSSFColor();
                if (color != null) {
                    byte[] rgb = color.getRGB();
                    if (rgb != null && rgb.length >= 3) return toHex(rgb[0], rgb[1], rgb[2]);
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private String extractBgColor(CellStyle cs) {
        try {
            if (cs instanceof XSSFCellStyle) {
                XSSFColor color = ((XSSFCellStyle) cs).getFillForegroundXSSFColor();
                if (color != null) {
                    byte[] rgb = color.getRGB();
                    if (rgb != null && rgb.length >= 3) {
                        String hex = toHex(rgb[0], rgb[1], rgb[2]);
                        if (!"FFFFFF".equalsIgnoreCase(hex) && !"000000".equalsIgnoreCase(hex)) return hex;
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private String toHex(byte r, byte g, byte b) {
        return String.format("%02X%02X%02X", r & 0xFF, g & 0xFF, b & 0xFF);
    }
}
