package experiment;

import model.FunctionalDependency;
import java.util.Set;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class ExperimentResult {
    private final Set<FunctionalDependency> fds;
    private final long executionTimeMs;
    private final long validationCount;

    // 新增的性能和内存统计字段
    private long peakMemoryUsageMB = 0;
    private String pliPerformanceStats = "";
    private String memoryStats = "";

    public ExperimentResult(Set<FunctionalDependency> fds, long executionTimeMs, long validationCount) {
        this.fds = fds;
        this.executionTimeMs = executionTimeMs;
        this.validationCount = validationCount;
    }

    public Set<FunctionalDependency> getFds() {
        return fds;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long getValidationCount() {
        return validationCount;
    }

    // 新增的getter和setter方法
    public long getPeakMemoryUsageMB() {
        return peakMemoryUsageMB;
    }

    public void setPeakMemoryUsageMB(long peakMemoryUsageMB) {
        this.peakMemoryUsageMB = peakMemoryUsageMB;
    }

    public String getPliPerformanceStats() {
        return pliPerformanceStats;
    }

    public void setPliPerformanceStats(String pliPerformanceStats) {
        this.pliPerformanceStats = pliPerformanceStats;
    }

    public String getMemoryStats() {
        return memoryStats;
    }

    public void setMemoryStats(String memoryStats) {
        this.memoryStats = memoryStats;
    }
}
