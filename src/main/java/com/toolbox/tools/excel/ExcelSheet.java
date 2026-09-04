package com.toolbox.tools.excel;

import java.util.ArrayList;
import java.util.List;

public class ExcelSheet {
    private String name;
    private int totalRows;
    private int totalCols;
    private List<Integer> columnWidths = new ArrayList<Integer>();
    private List<MergedRegion> mergedRegions = new ArrayList<MergedRegion>();
    private List<ExcelRow> rows = new ArrayList<ExcelRow>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getTotalCols() { return totalCols; }
    public void setTotalCols(int totalCols) { this.totalCols = totalCols; }
    public List<Integer> getColumnWidths() { return columnWidths; }
    public void setColumnWidths(List<Integer> columnWidths) { this.columnWidths = columnWidths; }
    public List<MergedRegion> getMergedRegions() { return mergedRegions; }
    public void setMergedRegions(List<MergedRegion> mergedRegions) { this.mergedRegions = mergedRegions; }
    public List<ExcelRow> getRows() { return rows; }
    public void setRows(List<ExcelRow> rows) { this.rows = rows; }

    public static class MergedRegion {
        private int firstRow, lastRow, firstCol, lastCol;
        public MergedRegion() {}
        public MergedRegion(int firstRow, int lastRow, int firstCol, int lastCol) {
            this.firstRow = firstRow; this.lastRow = lastRow;
            this.firstCol = firstCol; this.lastCol = lastCol;
        }
        public int getFirstRow() { return firstRow; }
        public void setFirstRow(int v) { this.firstRow = v; }
        public int getLastRow() { return lastRow; }
        public void setLastRow(int v) { this.lastRow = v; }
        public int getFirstCol() { return firstCol; }
        public void setFirstCol(int v) { this.firstCol = v; }
        public int getLastCol() { return lastCol; }
        public void setLastCol(int v) { this.lastCol = v; }
    }
}
