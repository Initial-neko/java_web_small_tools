package com.toolbox.tools.excel;

import java.util.ArrayList;
import java.util.List;

public class ExcelRow {
    private int rowIndex;
    private List<ExcelCell> cells = new ArrayList<ExcelCell>();

    public ExcelRow() {}
    public ExcelRow(int rowIndex) { this.rowIndex = rowIndex; }

    public int getRowIndex() { return rowIndex; }
    public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
    public List<ExcelCell> getCells() { return cells; }
    public void setCells(List<ExcelCell> cells) { this.cells = cells; }
    public void addCell(ExcelCell cell) { this.cells.add(cell); }
}
