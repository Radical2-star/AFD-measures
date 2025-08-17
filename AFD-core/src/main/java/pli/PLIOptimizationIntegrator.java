package pli;

import model.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.MemoryMonitor;

import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PLI优化集成器 - 统一管理PLI优化策略的集成和切换
 * 
 * 主要功能：
 * 1. 根据数据集大小和内存情况自动选择最优的PLI实现
 * 2. 提供统一的PLI操作接口
 * 3. 集成内存监控和性能统计
 * 4. 支持运行时策略切换
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/28
 */
public class PLIOptimizationIntegrator {
    private static final Logger logger = LoggerFactory.getLogger(PLIOptimizationIntegrator.class);
    
    // 策略选择阈值
    private static final long LARGE_DATASET_THRESHOLD = 1_000_000; // 100万行
    private static final long HUGE_DATASET_THRESHOLD = 5_000_000;  // 500万行
    private static final int MANY_COLUMNS_THRESHOLD = 20;          // 20列
    
    // 内存阈值
    private static final long LOW_MEMORY_THRESHOLD_MB = 512;       // 512MB
    private static final long HIGH_MEMORY_THRESHOLD_MB = 2048;     // 2GB
    
    private final DataSet dataset;
    private final PLICache originalCache;
    private final OptimizedPLICache optimizedCache;
    private final MemoryMonitor memoryMonitor;
    
    // 性能统计
    private final AtomicLong totalPLIRequests = new AtomicLong(0);
    private final AtomicLong optimizedPLIUsage = new AtomicLong(0);
    private final AtomicLong streamingPLIUsage = new AtomicLong(0);


    // 当前策略
    private volatile PLIStrategy currentStrategy;
    
    public PLIOptimizationIntegrator(DataSet dataset) {
        this.dataset = dataset;
        this.originalCache = new PLICache(dataset);
        this.optimizedCache = new OptimizedPLICache(dataset);
        this.memoryMonitor = MemoryMonitor.getInstance();
        
        // 启动内存监控（仅在非强制原始模式下）
        if (!isForceOriginalOnly()) {
            memoryMonitor.startMonitoring(10000, alert -> {
                if (alert.getLevel() == MemoryMonitor.AlertLevel.CRITICAL) {
                    logger.warn("检测到内存压力，切换到内存优化策略");
                    switchToMemoryOptimizedStrategy();
                }
            });
        }
        
        // 初始策略选择
        this.currentStrategy = selectInitialStrategy();
        
        logger.info("PLI优化集成器初始化完成，数据集: {}行×{}列, 初始策略: {}, 强制原始模式: {}", 
                   dataset.getRowCount(), dataset.getColumnCount(), currentStrategy, isForceOriginalOnly());
    }
    
    /**
     * 检查是否强制使用原始PLI实现
     */
    private boolean isForceOriginalOnly() {
        return Boolean.parseBoolean(System.getProperty("pli.force.original.only", "false"));
    }
    
    /**
     * 检查是否禁用优化
     */
    private boolean isOptimizationDisabled() {
        return Boolean.parseBoolean(System.getProperty("pli.disable.optimization", "false"));
    }
    
    /**
     * 检查是否禁用动态切换
     */
    private boolean isDynamicSwitchingDisabled() {
        return Boolean.parseBoolean(System.getProperty("pli.disable.dynamic.switching", "false"));
    }
    
    /**
     * 检查是否启用优化集成器
     */
    private boolean isOptimizationIntegratorEnabled() {
        return Boolean.parseBoolean(System.getProperty("pli.enable.optimization.integrator", "false"));
    }
    
    /**
     * 选择初始策略
     */
    private PLIStrategy selectInitialStrategy() {
        // 首先检查系统属性配置
        if (isForceOriginalOnly()) {
            logger.info("系统配置强制使用原始PLI实现");
            return PLIStrategy.ORIGINAL;
        }
        
        if (isOptimizationDisabled()) {
            logger.info("系统配置禁用PLI优化，使用原始实现");
            return PLIStrategy.ORIGINAL;
        }
        
        // 如果明确启用了优化集成器，使用动态策略
        if (isOptimizationIntegratorEnabled()) {
            logger.info("系统配置启用优化集成器，使用动态策略选择");
        }
        
        long rowCount = dataset.getRowCount();
        int columnCount = dataset.getColumnCount();
        long availableMemoryMB = getAvailableMemoryMB();
        
        logger.info("策略选择参数 - 行数: {}, 列数: {}, 可用内存: {}MB", 
                   rowCount, columnCount, availableMemoryMB);
        
        // 超大数据集或内存不足时使用流式处理
        if (rowCount > HUGE_DATASET_THRESHOLD || availableMemoryMB < LOW_MEMORY_THRESHOLD_MB) {
            return PLIStrategy.STREAMING;
        }
        
        // 大数据集或列数较多时使用优化缓存
        if (rowCount > LARGE_DATASET_THRESHOLD || columnCount > MANY_COLUMNS_THRESHOLD || 
            availableMemoryMB < HIGH_MEMORY_THRESHOLD_MB) {
            return PLIStrategy.OPTIMIZED;
        }
        
        // 小数据集使用原始实现
        return PLIStrategy.ORIGINAL;
    }
    
    /**
     * 获取可用内存（MB）
     */
    private long getAvailableMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long availableMemory = maxMemory - totalMemory + freeMemory;
        return availableMemory / (1024 * 1024);
    }
    
    /**
     * 获取或计算PLI（统一接口）
     */
    public PLI getOrCalculatePLI(BitSet columns) {
        totalPLIRequests.incrementAndGet();
        
        switch (currentStrategy) {
            case ORIGINAL:
                return originalCache.getOrCalculatePLI(columns);
                
            case OPTIMIZED:
                optimizedPLIUsage.incrementAndGet();
                return optimizedCache.getOrCalculatePLI(columns);
                
            case STREAMING:
                streamingPLIUsage.incrementAndGet();
                return calculateStreamingPLI(columns);
                
            default:
                logger.warn("未知策略: {}, 回退到原始实现", currentStrategy);
                return originalCache.getOrCalculatePLI(columns);
        }
    }
    
    /**
     * 使用流式处理计算PLI
     */
    private PLI calculateStreamingPLI(BitSet columns) {
        logger.debug("使用流式处理计算PLI: {}", columns);
        
        // 首先尝试从缓存获取
        PLI cached = optimizedCache.get(utils.BitSetUtils.bitSetToList(columns));
        if (cached != null) {
            return cached;
        }
        
        // 使用流式处理
        StreamingPLI streamingPLI = new StreamingPLI(dataset, columns);
        PLI result = streamingPLI.buildStreamingPLI();
        
        // 根据内存情况决定是否缓存
        if (shouldCacheResult(result)) {
            optimizedCache.set(utils.BitSetUtils.bitSetToList(columns), result);
        }
        
        return result;
    }
    
    /**
     * 判断是否应该缓存结果
     */
    private boolean shouldCacheResult(PLI pli) {
        long availableMemoryMB = getAvailableMemoryMB();
        
        // 内存充足时缓存小PLI
        if (availableMemoryMB > HIGH_MEMORY_THRESHOLD_MB) {
            return pli.getClusterCount() < 10000;
        }
        
        // 内存紧张时只缓存很小的PLI
        if (availableMemoryMB > LOW_MEMORY_THRESHOLD_MB) {
            return pli.getClusterCount() < 1000;
        }
        
        // 内存严重不足时不缓存
        return false;
    }
    
    /**
     * 切换到内存优化策略
     */
    private void switchToMemoryOptimizedStrategy() {
        // 如果禁用动态切换或强制使用原始实现，则不进行策略切换
        if (isDynamicSwitchingDisabled() || isForceOriginalOnly()) {
            logger.info("策略切换被禁用，保持当前策略: {}", currentStrategy);
            return;
        }
        
        if (currentStrategy != PLIStrategy.STREAMING) {
            logger.info("策略切换: {} -> {}", currentStrategy, PLIStrategy.STREAMING);
            currentStrategy = PLIStrategy.STREAMING;
            
            // 清理缓存释放内存
            if (optimizedCache != null) {
                // 触发缓存清理
                System.gc();
            }
        }
    }
    
    /**
     * 获取性能统计信息
     */
    public String getPerformanceStats() {
        long total = totalPLIRequests.get();
        long optimized = optimizedPLIUsage.get();
        long streaming = streamingPLIUsage.get();
        long original = total - optimized - streaming;
        
        MemoryMonitor.MemorySnapshot snapshot = memoryMonitor.getCurrentMemorySnapshot();
        
        return String.format(
            "PLI性能统计 - 总请求: %d, 原始: %d(%.1f%%), 优化: %d(%.1f%%), 流式: %d(%.1f%%), " +
            "当前策略: %s, 内存使用: %dMB/%.1f%%, 配置: [强制原始:%s, 禁用优化:%s, 禁用切换:%s]",
            total,
            original, total > 0 ? (double) original / total * 100 : 0,
            optimized, total > 0 ? (double) optimized / total * 100 : 0,
            streaming, total > 0 ? (double) streaming / total * 100 : 0,
            currentStrategy,
            snapshot.getUsedHeapMemory() / (1024 * 1024),
            snapshot.getUsageRatio() * 100,
            isForceOriginalOnly(), isOptimizationDisabled(), isDynamicSwitchingDisabled()
        );
    }
    
    /**
     * 获取详细的内存统计
     */
    public String getDetailedMemoryStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PLI内存统计 ===\n");
        
        // 基本内存信息
        MemoryMonitor.MemorySnapshot snapshot = memoryMonitor.getCurrentMemorySnapshot();
        sb.append(String.format("堆内存: %dMB/%dMB (%.1f%%)\n",
                snapshot.getUsedHeapMemory() / (1024 * 1024),
                snapshot.getMaxHeapMemory() / (1024 * 1024),
                snapshot.getUsageRatio() * 100));
        
        sb.append(String.format("非堆内存: %dMB\n",
                snapshot.getUsedNonHeapMemory() / (1024 * 1024)));
        
        sb.append(String.format("峰值内存: %dMB\n",
                memoryMonitor.getPeakMemoryUsage() / (1024 * 1024)));
        
        // 缓存统计
        if (optimizedCache != null) {
            sb.append(optimizedCache.getCacheStats()).append("\n");
        }
        
        // 内存池信息
        sb.append("=== 内存池详情 ===\n");
        for (MemoryMonitor.MemoryPoolInfo pool : memoryMonitor.getMemoryPoolInfo()) {
            sb.append(String.format("%s (%s): %dMB/%.1f%%\n",
                    pool.getName(), pool.getType(),
                    pool.getUsedMemory() / (1024 * 1024),
                    pool.getUsageRatio() * 100));
        }
        
        return sb.toString();
    }
    
    /**
     * 手动触发内存优化
     */
    public void optimizeMemory() {
        logger.info("手动触发内存优化");
        
        // 1. 强制垃圾回收
        System.gc();
        
        // 2. 清理缓存
        if (optimizedCache != null) {
            // 触发缓存清理逻辑
        }
        
        // 3. 重置峰值统计
        memoryMonitor.resetPeakMemory();
        
        logger.info("内存优化完成: {}", memoryMonitor.getCurrentMemorySnapshot().getUsedHeapMemory() / (1024 * 1024) + "MB");
    }
    
    /**
     * 获取当前配置状态
     */
    public String getConfigurationStatus() {
        return String.format(
            "PLI配置状态 - 强制原始模式: %s, 禁用优化: %s, 禁用动态切换: %s, 启用集成器: %s, 当前策略: %s",
            isForceOriginalOnly(),
            isOptimizationDisabled(),
            isDynamicSwitchingDisabled(),
            isOptimizationIntegratorEnabled(),
            currentStrategy
        );
    }
    
    /**
     * 关闭集成器，释放资源
     */
    public void shutdown() {
        logger.info("关闭PLI优化集成器");
        
        // 停止内存监控
        if (!isForceOriginalOnly()) {
            memoryMonitor.stopMonitoring();
        }
        
        // 输出最终统计
        logger.info("最终统计: {}", getPerformanceStats());
        logger.info("配置状态: {}", getConfigurationStatus());
    }
    
    /**
     * PLI策略枚举
     */
    public enum PLIStrategy {
        ORIGINAL("原始实现"),
        OPTIMIZED("优化缓存"),
        STREAMING("流式处理");
        
        private final String description;
        
        PLIStrategy(String description) {
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }

    public PLIStrategy getCurrentStrategy() {
        return currentStrategy;
    }

    /**
     * 创建适配器，兼容现有代码
     */
    public PLICache createCompatibleCache() {
        return new PLICache(dataset) {
            @Override
            public PLI getOrCalculatePLI(BitSet targetColumns) {
                return PLIOptimizationIntegrator.this.getOrCalculatePLI(targetColumns);
            }
        };
    }
}
