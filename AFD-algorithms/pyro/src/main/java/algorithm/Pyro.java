package algorithm;

import measure.*;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLICache;
import pli.PLIOptimizationIntegrator;
import sampling.FocusedSampling;
import sampling.NeymanSampling;
import sampling.RandomSampling;
import sampling.SamplingStrategy;
import utils.DataLoader;
import utils.FunctionTimer;
import utils.MemoryMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */

public class Pyro {
    private static final Logger logger = LoggerFactory.getLogger(Pyro.class);

    private final DataSet dataset;
    private final PLICache pliCache;
    private final PLIOptimizationIntegrator pliIntegrator;
    private final ErrorMeasure measure;
    private final SamplingStrategy samplingStrategy;
    private final double maxError;
    private long executionTimeMs;
    private final boolean verbose;
    private final double sampleParam;

    // 性能统计
    private long peakMemoryUsage = 0;
    private String pliPerformanceStats = "";
    private String memoryStats = "";

    public Pyro(DataSet dataset, ErrorMeasure measure, SamplingStrategy samplingStrategy, double maxError, double sampleParam, boolean verbose) {
        this.dataset = dataset;
        this.measure = measure;
        this.samplingStrategy = samplingStrategy;
        this.maxError = maxError;
        this.sampleParam = sampleParam;
        this.verbose = verbose;

        // 初始化PLI优化集成器
        this.pliIntegrator = new PLIOptimizationIntegrator(dataset);
        this.pliCache = pliIntegrator.createCompatibleCache();

        // 启动内存监控
        if (verbose) {
            MemoryMonitor.getInstance().startMonitoring(5000, alert -> {
                if (alert.getLevel() == MemoryMonitor.AlertLevel.CRITICAL) {
                    logger.warn("Pyro算法执行中检测到严重内存压力: {}", alert);
                } else if (alert.getLevel() == MemoryMonitor.AlertLevel.WARNING) {
                    logger.info("Pyro算法执行中检测到内存压力: {}", alert);
                }
            });
        }

        if (verbose) {
            logger.info("Pyro算法初始化完成，数据集: {}行×{}列",
                       dataset.getRowCount(), dataset.getColumnCount());
        }
    }

    public List<FunctionalDependency> discover() {
        List<FunctionalDependency> result = new ArrayList<>();
        SearchSpace.resetValidateCount();

        long startTime = System.currentTimeMillis();
        MemoryMonitor memoryMonitor = MemoryMonitor.getInstance();

        // 记录初始内存状态
        MemoryMonitor.MemorySnapshot initialMemory = memoryMonitor.getCurrentMemorySnapshot();
        if (verbose) {
            logger.info("Pyro算法开始执行，初始内存使用: {}MB",
                       initialMemory.getUsedHeapMemory() / (1024 * 1024));
        }

        try {
            // 为每个属性创建搜索空间
            for (int rhs = 0; rhs < dataset.getColumnCount(); rhs++) {
                // if (rhs != 0) continue; // 仅测试一个searchSpace，真正运行时请注释掉！

                if (verbose) {
                    logger.info("处理RHS属性: {} ({}/{})", rhs, rhs + 1, dataset.getColumnCount());
                }

                SearchSpace searchSpace = new SearchSpace(rhs, dataset, pliCache, measure, samplingStrategy, maxError, sampleParam, verbose);
                searchSpace.explore();
                result.addAll(searchSpace.getValidatedFDs());

                // 定期检查内存使用
                MemoryMonitor.MemorySnapshot currentMemory = memoryMonitor.getCurrentMemorySnapshot();
                long currentMemoryMB = currentMemory.getUsedHeapMemory() / (1024 * 1024);
                if (currentMemoryMB > peakMemoryUsage) {
                    peakMemoryUsage = currentMemoryMB;
                }

                if (verbose && (rhs + 1) % 5 == 0) {
                    logger.info("已处理 {}/{} 个RHS属性，当前内存: {}MB，峰值: {}MB",
                               rhs + 1, dataset.getColumnCount(), currentMemoryMB, peakMemoryUsage);
                }
            }

            this.executionTimeMs = System.currentTimeMillis() - startTime;

            // 收集性能统计
            this.pliPerformanceStats = pliIntegrator.getPerformanceStats();
            this.memoryStats = pliIntegrator.getDetailedMemoryStats();

            if (verbose) {
                logger.info("Pyro算法执行完成，耗时: {}ms，发现FD数量: {}",
                           executionTimeMs, result.size());
                logger.info("PLI性能统计: {}", pliPerformanceStats);
                logger.info("内存峰值: {}MB", peakMemoryUsage);
            }

            return result;

        } finally {
            // 清理资源
            if (pliIntegrator != null) {
                pliIntegrator.shutdown();
            }
        }
    }

    public int getValidationCount() {
        return SearchSpace.getValidateCount();
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * 获取内存峰值使用量（MB）
     */
    public long getPeakMemoryUsageMB() {
        return peakMemoryUsage;
    }

    /**
     * 获取PLI性能统计信息
     */
    public String getPLIPerformanceStats() {
        return pliPerformanceStats;
    }

    /**
     * 获取详细内存统计信息
     */
    public String getMemoryStats() {
        return memoryStats;
    }

    public static void main(String[] args) {
        DataLoader loader = DataLoader.fromFile(
                Path.of("data/0/ditag_feature.csv")
        ).withHeader(true).withDelimiter(';');
        DataSet dataset = loader.load();
        Pyro pyro = new Pyro(dataset,
                new G3Measure(),
                new FocusedSampling(),
                0.05,
                200,
                false
        );

        List<FunctionalDependency> fdResults = pyro.discover();

        // 输出FD结果
        System.out.println("\n===== Pyro算法结果（FD格式） =====");
        List<FunctionalDependency> sortedFDs = new ArrayList<>(fdResults);
        sortedFDs.sort(Comparator.comparingDouble(FunctionalDependency::getError));
        for (int i = 0; i < Math.min(10, sortedFDs.size()); i++) {
            System.out.println(sortedFDs.get(i));
        }
        System.out.println("总共找到 " + fdResults.size() + " 个函数依赖");

        FunctionTimer timer = FunctionTimer.getInstance();
        timer.printResults();
    }
} 