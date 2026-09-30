package com.toolbox.tools.metric;

import java.util.ArrayList;
import java.util.List;

public class BatchMetricProbeResult {

    private int total;
    private int success;
    private int partial;
    private int failed;
    private int unsupported;
    private int metricCount;
    private final List<MetricProbeResult> results = new ArrayList<MetricProbeResult>();
    private final List<MetricDuplicateGroup> duplicateGroups = new ArrayList<MetricDuplicateGroup>();

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getSuccess() { return success; }
    public void setSuccess(int success) { this.success = success; }
    public int getPartial() { return partial; }
    public void setPartial(int partial) { this.partial = partial; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public int getUnsupported() { return unsupported; }
    public void setUnsupported(int unsupported) { this.unsupported = unsupported; }
    public int getMetricCount() { return metricCount; }
    public void setMetricCount(int metricCount) { this.metricCount = metricCount; }
    public List<MetricProbeResult> getResults() { return results; }
    public List<MetricDuplicateGroup> getDuplicateGroups() { return duplicateGroups; }
}
