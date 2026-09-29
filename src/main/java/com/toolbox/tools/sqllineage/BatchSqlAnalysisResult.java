package com.toolbox.tools.sqllineage;

import java.util.ArrayList;
import java.util.List;

public class BatchSqlAnalysisResult {

    private int total;
    private int success;
    private int partial;
    private int failed;
    private int unsupported;
    private List<SqlAnalysisResult> results = new ArrayList<SqlAnalysisResult>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getPartial() {
        return partial;
    }

    public void setPartial(int partial) {
        this.partial = partial;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public int getUnsupported() {
        return unsupported;
    }

    public void setUnsupported(int unsupported) {
        this.unsupported = unsupported;
    }

    public List<SqlAnalysisResult> getResults() {
        return results;
    }
}
