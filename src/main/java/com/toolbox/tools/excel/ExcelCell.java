package com.toolbox.tools.excel;

public class ExcelCell {
    private String value;
    private ExcelCellStyle style;

    public ExcelCell() {}
    public ExcelCell(String value) { this.value = value; }
    public ExcelCell(String value, ExcelCellStyle style) { this.value = value; this.style = style; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public ExcelCellStyle getStyle() { return style; }
    public void setStyle(ExcelCellStyle style) { this.style = style; }
}
