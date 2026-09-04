package com.toolbox.tools.excel;

import java.util.ArrayList;
import java.util.List;

public class ExcelFile {
    private String fileId;
    private String fileName;
    private long uploadTime;
    private List<ExcelSheet> sheets = new ArrayList<ExcelSheet>();

    public ExcelFile() { this.uploadTime = System.currentTimeMillis(); }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public long getUploadTime() { return uploadTime; }
    public void setUploadTime(long uploadTime) { this.uploadTime = uploadTime; }
    public List<ExcelSheet> getSheets() { return sheets; }
    public void setSheets(List<ExcelSheet> sheets) { this.sheets = sheets; }
    public void addSheet(ExcelSheet sheet) { this.sheets.add(sheet); }
}
