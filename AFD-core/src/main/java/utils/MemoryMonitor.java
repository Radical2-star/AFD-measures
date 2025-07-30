package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 内存监控工具 - 提供实时内存使用监控和预警
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/28
 */
public class MemoryMonitor {
    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitor.class);
    
    // 单例实例
    private static final MemoryMonitor INSTANCE = new MemoryMonitor();
    
    // 内存阈值配置
    private static final double WARNING_THRESHOLD = 0.7;  // 70%内存使用率触发警告
    private static final double CRITICAL_THRESHOLD = 0.85; // 85%内存使用率触发严重警告
    
    // 监控配置
    private static final long DEFAULT_MONITOR_INTERVAL_MS = 5000; // 默认5秒监控一次
    private static final int HISTORY_SIZE = 10; // 保留最近10次监控记录
    
    // 监控状态
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private final AtomicLong peakMemoryUsage = new AtomicLong(0);
    private final List<MemorySnapshot> memoryHistory = new ArrayList<>();
    
    // 监控线程
    private ScheduledExecutorService scheduler;
    
    // 回调处理器
    private Consumer<MemoryAlert> alertHandler;
    
    // JMX监控对象
    private final MemoryMXBean memoryMXBean;
    private final List<MemoryPoolMXBean> memoryPoolMXBeans;
    
    private MemoryMonitor() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        logger.info("内存监控初始化完成，最大堆内存: {}MB", 
                   memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
    }
    
    /**
     * 获取单例实例
     */
    public static MemoryMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * 启动内存监控
     * @param intervalMs 监控间隔（毫秒）
     * @param alertHandler 警报处理回调
     */
    public synchronized void startMonitoring(long intervalMs, Consumer<MemoryAlert> alertHandler) {
        if (isMonitoring.get()) {
            logger.warn("内存监控已在运行中");
            return;
        }
        
        this.alertHandler = alertHandler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Memory-Monitor");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(this::checkMemory, 0, intervalMs, TimeUnit.MILLISECONDS);
        isMonitoring.set(true);
        
        logger.info("内存监控已启动，间隔: {}ms", intervalMs);
    }
    
    /**
     * 使用默认配置启动监控
     */
    public void startMonitoring() {
        startMonitoring(DEFAULT_MONITOR_INTERVAL_MS, alert -> {
            if (alert.getLevel() == AlertLevel.CRITICAL) {
                logger.error("内存警报: {}", alert);
                System.gc(); // 触发垃圾回收
            } else if (alert.getLevel() == AlertLevel.WARNING) {
                logger.warn("内存警报: {}", alert);
            }
        });
    }
    
    /**
     * 停止内存监控
     */
    public synchronized void stopMonitoring() {
        if (!isMonitoring.get()) {
            return;
        }
        
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        
        isMonitoring.set(false);
        logger.info("内存监控已停止");
    }
    
    /**
     * 检查内存使用情况
     */
    private void checkMemory() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
            
            long usedHeap = heapUsage.getUsed();
            long maxHeap = heapUsage.getMax();
            double usageRatio = (double) usedHeap / maxHeap;
            
            // 更新峰值
            long currentPeak = peakMemoryUsage.get();
            if (usedHeap > currentPeak) {
                peakMemoryUsage.set(usedHeap);
            }
            
            // 创建快照并保存历史
            MemorySnapshot snapshot = new MemorySnapshot(
                System.currentTimeMillis(), usedHeap, maxHeap, nonHeapUsage.getUsed());
            
            synchronized (memoryHistory) {
                memoryHistory.add(snapshot);
                if (memoryHistory.size() > HISTORY_SIZE) {
                    memoryHistory.remove(0);
                }
            }
            
            // 检查是否需要触发警报
            if (usageRatio >= CRITICAL_THRESHOLD) {
                triggerAlert(AlertLevel.CRITICAL, usedHeap, maxHeap, usageRatio);
            } else if (usageRatio >= WARNING_THRESHOLD) {
                triggerAlert(AlertLevel.WARNING, usedHeap, maxHeap, usageRatio);
            }
            
            // 定期记录内存使用情况
            if (logger.isDebugEnabled()) {
                logger.debug("内存使用: {}MB/{}MB ({}%), 非堆: {}MB", 
                           usedHeap / (1024 * 1024), 
                           maxHeap / (1024 * 1024),
                           Math.round(usageRatio * 100),
                           nonHeapUsage.getUsed() / (1024 * 1024));
            }
            
        } catch (Exception e) {
            logger.error("内存监控异常", e);
        }
    }
    
    /**
     * 触发内存警报
     */
    private void triggerAlert(AlertLevel level, long usedMemory, long maxMemory, double usageRatio) {
        MemoryAlert alert = new MemoryAlert(level, usedMemory, maxMemory, usageRatio);
        
        if (alertHandler != null) {
            try {
                alertHandler.accept(alert);
            } catch (Exception e) {
                logger.error("处理内存警报异常", e);
            }
        }
    }
    
    /**
     * 获取当前内存使用情况
     */
    public MemorySnapshot getCurrentMemorySnapshot() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        return new MemorySnapshot(
            System.currentTimeMillis(),
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed()
        );
    }
    
    /**
     * 获取内存使用历史
     */
    public List<MemorySnapshot> getMemoryHistory() {
        synchronized (memoryHistory) {
            return new ArrayList<>(memoryHistory);
        }
    }
    
    /**
     * 获取峰值内存使用
     */
    public long getPeakMemoryUsage() {
        return peakMemoryUsage.get();
    }
    
    /**
     * 重置峰值内存统计
     */
    public void resetPeakMemory() {
        peakMemoryUsage.set(0);
    }
    
    /**
     * 获取详细的内存池信息
     */
    public List<MemoryPoolInfo> getMemoryPoolInfo() {
        List<MemoryPoolInfo> result = new ArrayList<>();
        
        for (MemoryPoolMXBean pool : memoryPoolMXBeans) {
            MemoryUsage usage = pool.getUsage();
            result.add(new MemoryPoolInfo(
                pool.getName(),
                pool.getType().name(),
                usage.getUsed(),
                usage.getMax() == -1 ? usage.getCommitted() : usage.getMax()
            ));
        }
        
        return result;
    }
    
    /**
     * 内存快照类
     */
    public static class MemorySnapshot {
        private final long timestamp;
        private final long usedHeapMemory;
        private final long maxHeapMemory;
        private final long usedNonHeapMemory;
        
        public MemorySnapshot(long timestamp, long usedHeapMemory, long maxHeapMemory, long usedNonHeapMemory) {
            this.timestamp = timestamp;
            this.usedHeapMemory = usedHeapMemory;
            this.maxHeapMemory = maxHeapMemory;
            this.usedNonHeapMemory = usedNonHeapMemory;
        }
        
        public long getTimestamp() { return timestamp; }
        public long getUsedHeapMemory() { return usedHeapMemory; }
        public long getMaxHeapMemory() { return maxHeapMemory; }
        public long getUsedNonHeapMemory() { return usedNonHeapMemory; }
        public double getUsageRatio() { return (double) usedHeapMemory / maxHeapMemory; }
    }
    
    /**
     * 内存池信息类
     */
    public static class MemoryPoolInfo {
        private final String name;
        private final String type;
        private final long usedMemory;
        private final long maxMemory;
        
        public MemoryPoolInfo(String name, String type, long usedMemory, long maxMemory) {
            this.name = name;
            this.type = type;
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
        }
        
        public String getName() { return name; }
        public String getType() { return type; }
        public long getUsedMemory() { return usedMemory; }
        public long getMaxMemory() { return maxMemory; }
        public double getUsageRatio() { return maxMemory > 0 ? (double) usedMemory / maxMemory : 0; }
    }
    
    /**
     * 内存警报类
     */
    public static class MemoryAlert {
        private final AlertLevel level;
        private final long usedMemory;
        private final long maxMemory;
        private final double usageRatio;
        private final long timestamp;
        
        public MemoryAlert(AlertLevel level, long usedMemory, long maxMemory, double usageRatio) {
            this.level = level;
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
            this.usageRatio = usageRatio;
            this.timestamp = System.currentTimeMillis();
        }
        
        public AlertLevel getLevel() { return level; }
        public long getUsedMemory() { return usedMemory; }
        public long getMaxMemory() { return maxMemory; }
        public double getUsageRatio() { return usageRatio; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("%s - 内存使用: %dMB/%dMB (%.1f%%)", 
                               level, usedMemory / (1024 * 1024), 
                               maxMemory / (1024 * 1024), usageRatio * 100);
        }
    }
    
    /**
     * 警报级别枚举
     */
    public enum AlertLevel {
        WARNING,
        CRITICAL
    }
}
