package com.toolbox.tools.excel;

public class ExcelCellStyle {
    private boolean bold;
    private int fontSize;
    private String bgColor;
    private String fontColor;
    private String alignment;
    private boolean border;

    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }
    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { this.fontSize = fontSize; }
    public String getBgColor() { return bgColor; }
    public void setBgColor(String bgColor) { this.bgColor = bgColor; }
    public String getFontColor() { return fontColor; }
    public void setFontColor(String fontColor) { this.fontColor = fontColor; }
    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }
    public boolean isBorder() { return border; }
    public void setBorder(boolean border) { this.border = border; }
}
