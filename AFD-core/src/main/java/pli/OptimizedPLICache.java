package pli;

import model.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BitSetUtils;
import utils.Trie;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存优化的PLI缓存实现
 * 主要优化：
 * 1. 基于内存使用量的动态缓存管理
 * 2. 分层缓存策略（热缓存+冷缓存）
 * 3. 智能缓存替换算法
 * 4. 内存压力感知的缓存清理
 * 
 * @author Hoshi
 * @version 2.0
 * @since 2025/7/28
 */
public class OptimizedPLICache extends Trie<PLI> {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedPLICache.class);
    
    // 内存管理配置
    private static final long MAX_MEMORY_MB = Long.getLong("pli.cache.max.memory.mb", 512); // 512MB默认上限
    private static final long CLEANUP_THRESHOLD_MB = Long.getLong("pli.cache.cleanup.threshold.mb", 400); // 400MB开始清理
    private static final double COLD_CACHE_RATIO = 0.3; // 30%的内存用于冷缓存
    
    private final DataSet dataset;
    
    // 分层缓存：热缓存（强引用）+ 冷缓存（软引用）
    private final Map<List<Integer>, PLI> hotCache;
    private final Map<List<Integer>, SoftReference<PLI>> coldCache;
    
    // 访问统计和内存管理
    private final Map<List<Integer>, CacheEntry> accessStats;
    private final AtomicLong currentMemoryUsage = new AtomicLong(0);
    private final AtomicLong accessCounter = new AtomicLong(0);
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    
    // 内存压力检测
    private volatile long lastCleanupTime = System.currentTimeMillis();
    private static final long MIN_CLEANUP_INTERVAL_MS = 30000; // 最小清理间隔30秒
    
    public OptimizedPLICache(DataSet dataset) {
        this.dataset = dataset;
        this.hotCache = new ConcurrentHashMap<>();
        this.coldCache = new ConcurrentHashMap<>();
        this.accessStats = new ConcurrentHashMap<>();
        
        initializeSingleColumnPLIs();
        startMemoryMonitor();
    }
    
    /**
     * 初始化单列PLI（这些PLI永远保持在热缓存中）
     */
    private void initializeSingleColumnPLIs() {
        int columnCount = dataset.getColumnCount();
        for (int col = 0; col < columnCount; col++) {
            BitSet columns = new BitSet();
            columns.set(col);
            PLI pli = new PLI(columns, dataset);
            List<Integer> key = Collections.singletonList(col);
            
            // 单列PLI直接放入热缓存，永不清理
            hotCache.put(key, pli);
            accessStats.put(key, new CacheEntry(System.currentTimeMillis(), 1, true)); // 标记为永久
            updateMemoryUsage(pli, true);
            
            // 同时放入Trie结构
            super.set(key, pli);
        }
        
        logger.info("初始化完成，单列PLI数量: {}, 内存使用: {}MB", 
                   columnCount, currentMemoryUsage.get() / (1024 * 1024));
    }
    
    @Override
    public PLI get(List<Integer> key) {
        accessCounter.incrementAndGet();
        
        // 1. 先从热缓存查找
        PLI result = hotCache.get(key);
        if (result != null) {
            hitCount.incrementAndGet();
            updateAccessStats(key);
            return result;
        }
        
        // 2. 从冷缓存查找
        SoftReference<PLI> ref = coldCache.get(key);
        if (ref != null) {
            result = ref.get();
            if (result != null) {
                hitCount.incrementAndGet();
                // 提升到热缓存
                promoteToHotCache(key, result);
                updateAccessStats(key);
                return result;
            } else {
                // 软引用已被GC，清理
                coldCache.remove(key);
                accessStats.remove(key);
            }
        }
        
        // 3. 从Trie结构查找（兼容性）
        result = super.get(key);
        if (result != null) {
            hitCount.incrementAndGet();
            // 加入缓存
            addToCache(key, result);
            updateAccessStats(key);
            return result;
        }
        
        missCount.incrementAndGet();
        return null;
    }
    
    @Override
    public void set(List<Integer> key, PLI value) {
        super.set(key, value);
        addToCache(key, value);
        updateAccessStats(key);
    }
    
    /**
     * 智能缓存添加：根据内存压力和访问模式决定缓存策略
     */
    private void addToCache(List<Integer> key, PLI pli) {
        long memoryUsageMB = currentMemoryUsage.get() / (1024 * 1024);
        
        if (memoryUsageMB < CLEANUP_THRESHOLD_MB) {
            // 内存充足，直接加入热缓存
            hotCache.put(key, pli);
            updateMemoryUsage(pli, true);
        } else {
            // 内存紧张，加入冷缓存
            coldCache.put(key, new SoftReference<>(pli));
        }
        
        // 检查是否需要清理
        if (memoryUsageMB > CLEANUP_THRESHOLD_MB) {
            triggerCleanup();
        }
    }
    
    /**
     * 从冷缓存提升到热缓存
     */
    private void promoteToHotCache(List<Integer> key, PLI pli) {
        coldCache.remove(key);
        
        long memoryUsageMB = currentMemoryUsage.get() / (1024 * 1024);
        if (memoryUsageMB < CLEANUP_THRESHOLD_MB) {
            hotCache.put(key, pli);
            updateMemoryUsage(pli, true);
        } else {
            // 内存紧张，重新放回冷缓存
            coldCache.put(key, new SoftReference<>(pli));
        }
    }
    
    /**
     * 更新访问统计
     */
    private void updateAccessStats(List<Integer> key) {
        accessStats.compute(key, (k, entry) -> {
            if (entry == null) {
                return new CacheEntry(System.currentTimeMillis(), 1, false);
            } else {
                return new CacheEntry(System.currentTimeMillis(), entry.accessCount + 1, entry.permanent);
            }
        });
    }
    
    /**
     * 更新内存使用统计
     */
    private void updateMemoryUsage(PLI pli, boolean add) {
        long pliMemory = estimatePLIMemory(pli);
        if (add) {
            currentMemoryUsage.addAndGet(pliMemory);
        } else {
            currentMemoryUsage.addAndGet(-pliMemory);
        }
    }
    
    /**
     * 估算PLI内存使用量
     */
    private long estimatePLIMemory(PLI pli) {
        // 基础对象开销
        long memory = 64; // 对象头 + 字段
        
        // 等价类内存
        memory += pli.getClusterCount() * 32; // 每个Set对象的开销
        memory += pli.size() * 4; // 每个Integer的存储
        
        // 属性向量内存（如果存在）
        memory += pli.getRowCount() * 4;
        
        return memory;
    }
    
    /**
     * 触发缓存清理
     */
    private void triggerCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < MIN_CLEANUP_INTERVAL_MS) {
            return; // 避免频繁清理
        }
        
        synchronized (this) {
            if (now - lastCleanupTime < MIN_CLEANUP_INTERVAL_MS) {
                return; // 双重检查
            }
            
            performIntelligentCleanup();
            lastCleanupTime = now;
        }
    }
    
    /**
     * 智能缓存清理：基于访问频率和内存占用
     */
    private void performIntelligentCleanup() {
        long memoryUsageMB = currentMemoryUsage.get() / (1024 * 1024);
        if (memoryUsageMB <= CLEANUP_THRESHOLD_MB) {
            return;
        }
        
        logger.info("开始智能缓存清理，当前内存使用: {}MB", memoryUsageMB);
        
        // 1. 清理已失效的软引用
        cleanupInvalidSoftReferences();
        
        // 2. 基于LFU算法清理热缓存中的非永久项
        cleanupLeastFrequentlyUsed();
        
        // 3. 将部分热缓存项降级到冷缓存
        demoteToColCache();
        
        long newMemoryUsageMB = currentMemoryUsage.get() / (1024 * 1024);
        logger.info("缓存清理完成，内存使用: {}MB -> {}MB", memoryUsageMB, newMemoryUsageMB);
    }
    
    /**
     * 清理失效的软引用
     */
    private void cleanupInvalidSoftReferences() {
        Iterator<Map.Entry<List<Integer>, SoftReference<PLI>>> it = coldCache.entrySet().iterator();
        int cleaned = 0;
        
        while (it.hasNext()) {
            Map.Entry<List<Integer>, SoftReference<PLI>> entry = it.next();
            if (entry.getValue().get() == null) {
                it.remove();
                accessStats.remove(entry.getKey());
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            logger.debug("清理失效软引用: {} 个", cleaned);
        }
    }
    
    /**
     * 基于LFU清理最少使用的缓存项
     */
    private void cleanupLeastFrequentlyUsed() {
        // 获取非永久的热缓存项，按访问频率排序
        List<Map.Entry<List<Integer>, CacheEntry>> candidates = new ArrayList<>();
        for (Map.Entry<List<Integer>, CacheEntry> entry : accessStats.entrySet()) {
            if (!entry.getValue().permanent && hotCache.containsKey(entry.getKey())) {
                candidates.add(entry);
            }
        }
        
        // 按访问频率升序排序
        candidates.sort(Comparator.comparingLong(e -> e.getValue().accessCount));
        
        // 清理最少使用的25%
        int toRemove = Math.max(1, candidates.size() / 4);
        for (int i = 0; i < toRemove && i < candidates.size(); i++) {
            List<Integer> key = candidates.get(i).getKey();
            PLI pli = hotCache.remove(key);
            if (pli != null) {
                updateMemoryUsage(pli, false);
                // 降级到冷缓存
                coldCache.put(key, new SoftReference<>(pli));
            }
        }
    }
    
    /**
     * 将部分热缓存项降级到冷缓存
     */
    private void demoteToColCache() {
        long targetMemory = MAX_MEMORY_MB * 1024 * 1024;
        long currentMemory = currentMemoryUsage.get();
        
        if (currentMemory <= targetMemory) {
            return;
        }
        
        // 选择最老的非永久项进行降级
        List<Map.Entry<List<Integer>, CacheEntry>> candidates = new ArrayList<>();
        for (Map.Entry<List<Integer>, CacheEntry> entry : accessStats.entrySet()) {
            if (!entry.getValue().permanent && hotCache.containsKey(entry.getKey())) {
                candidates.add(entry);
            }
        }
        
        // 按最后访问时间升序排序
        candidates.sort(Comparator.comparingLong(e -> e.getValue().lastAccessTime));
        
        // 降级直到内存使用降到目标以下
        for (Map.Entry<List<Integer>, CacheEntry> entry : candidates) {
            if (currentMemoryUsage.get() <= targetMemory) {
                break;
            }
            
            List<Integer> key = entry.getKey();
            PLI pli = hotCache.remove(key);
            if (pli != null) {
                updateMemoryUsage(pli, false);
                coldCache.put(key, new SoftReference<>(pli));
            }
        }
    }
    
    /**
     * 启动后台内存监控线程
     */
    private void startMemoryMonitor() {
        Thread monitor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // 每分钟检查一次
                    
                    long memoryUsageMB = currentMemoryUsage.get() / (1024 * 1024);
                    if (memoryUsageMB > CLEANUP_THRESHOLD_MB) {
                        triggerCleanup();
                    }
                    
                    // 定期清理失效的软引用
                    cleanupInvalidSoftReferences();
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "PLI-Cache-Monitor");
        
        monitor.setDaemon(true);
        monitor.start();
    }
    
    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        long memoryUsageMB = currentMemoryUsage.get() / (1024 * 1024);
        double hitRate = accessCounter.get() > 0 ? 
            (double) hitCount.get() / accessCounter.get() * 100 : 0;
        
        return String.format(
            "PLI缓存统计 - 内存: %dMB, 热缓存: %d, 冷缓存: %d, 访问: %d, 命中率: %.2f%%", 
            memoryUsageMB, hotCache.size(), coldCache.size(), 
            accessCounter.get(), hitRate);
    }
    
    /**
     * 获取或计算指定属性集的PLI（优化版本）
     */
    public PLI getOrCalculatePLI(BitSet targetColumns) {
        List<Integer> sortedCols = BitSetUtils.bitSetToList(targetColumns);

        // 1. 优先从缓存获取
        PLI cached = get(sortedCols);
        if (cached != null) return cached;

        // 2. 检查内存压力，决定计算策略
        long memoryUsageMB = currentMemoryUsage.get() / (1024 * 1024);
        if (memoryUsageMB > MAX_MEMORY_MB * 0.8) {
            // 内存压力大，使用流式计算
            return calculatePLIWithMemoryPressure(targetColumns);
        }

        // 3. 正常计算流程
        return calculatePLINormal(targetColumns);
    }

    /**
     * 内存压力下的PLI计算
     */
    private PLI calculatePLIWithMemoryPressure(BitSet targetColumns) {
        logger.info("内存压力下计算PLI: {}, 当前内存: {}MB",
                   targetColumns, currentMemoryUsage.get() / (1024 * 1024));

        // 强制清理缓存
        performIntelligentCleanup();

        // 使用最小内存占用的计算方式
        List<Integer> sortedCols = BitSetUtils.bitSetToList(targetColumns);
        PLI bestSubsetPLI = findBestCachedSubsetPli(targetColumns);

        if (bestSubsetPLI != null) {
            // 基于最佳子集PLI进行增量计算
            return calculateIncrementalPLI(bestSubsetPLI, targetColumns);
        } else {
            // 直接计算，但不缓存
            PLI result = new PLI(targetColumns, dataset);
            logger.warn("内存压力下直接计算PLI，未缓存: {}", targetColumns);
            return result;
        }
    }

    /**
     * 正常的PLI计算流程
     */
    private PLI calculatePLINormal(BitSet targetColumns) {
        // 获取所有可能子集PLI
        List<PLI> candidatePLIs = findAllSubsetPLIs(targetColumns);

        if (candidatePLIs.isEmpty()) {
            // 没有可用的子集PLI，直接计算
            PLI result = new PLI(targetColumns, dataset);
            // 智能缓存决策
            if (shouldCache(targetColumns, result)) {
                List<Integer> finalKey = BitSetUtils.bitSetToList(targetColumns);
                set(finalKey, result);
            }
            return result;
        }

        // 贪心选择覆盖最多新属性的PLI
        List<PLI> selectedPLIs = selectOptimalPLIs(candidatePLIs, targetColumns);

        // 逐步合并PLI
        PLI result = selectedPLIs.get(0);
        for (int i = 1; i < selectedPLIs.size(); i++) {
            result = result.intersect(selectedPLIs.get(i));
        }

        // 智能缓存决策
        if (shouldCache(targetColumns, result)) {
            List<Integer> finalKey = BitSetUtils.bitSetToList(targetColumns);
            set(finalKey, result);
        }

        return result;
    }

    /**
     * 智能缓存决策
     */
    private boolean shouldCache(BitSet targetColumns, PLI pli) {
        // 1. 单列PLI总是缓存
        if (targetColumns.cardinality() == 1) {
            return true;
        }

        // 2. 内存压力大时不缓存大PLI
        long memoryUsageMB = currentMemoryUsage.get() / (1024 * 1024);
        if (memoryUsageMB > CLEANUP_THRESHOLD_MB) {
            long pliMemory = estimatePLIMemory(pli);
            if (pliMemory > 10 * 1024 * 1024) { // 大于10MB的PLI不缓存
                return false;
            }
        }

        // 3. 基于PLI大小和列数的启发式决策
        int columnCount = targetColumns.cardinality();
        int clusterCount = pli.getClusterCount();

        // 小列数且簇数适中的PLI优先缓存
        return columnCount <= 3 || clusterCount < 1000;
    }

    /**
     * 选择最优的PLI组合
     */
    private List<PLI> selectOptimalPLIs(List<PLI> candidatePLIs, BitSet targetColumns) {
        List<PLI> selectedPLIs = new ArrayList<>();
        BitSet covered = new BitSet();

        while (!covered.equals(targetColumns)) {
            PLI bestPLI = null;
            int maxNewCover = -1;
            int minSize = Integer.MAX_VALUE;

            for (PLI pli : candidatePLIs) {
                BitSet pliCols = pli.getColumns();
                BitSet newCover = (BitSet) pliCols.clone();
                newCover.andNot(covered);
                int newCoverCount = newCover.cardinality();

                if (newCoverCount > maxNewCover ||
                        (newCoverCount == maxNewCover && pli.size() < minSize)) {
                    bestPLI = pli;
                    maxNewCover = newCoverCount;
                    minSize = pli.size();
                }
            }

            if (bestPLI == null) break;
            selectedPLIs.add(bestPLI);
            covered.or(bestPLI.getColumns());
            candidatePLIs.remove(bestPLI);
        }

        // 按size升序排序
        selectedPLIs.sort(Comparator.comparingInt(PLI::size));
        return selectedPLIs;
    }

    /**
     * 增量计算PLI
     */
    private PLI calculateIncrementalPLI(PLI basePLI, BitSet targetColumns) {
        BitSet baseColumns = basePLI.getColumns();
        BitSet missingColumns = (BitSet) targetColumns.clone();
        missingColumns.andNot(baseColumns);

        PLI result = basePLI;
        for (int col = missingColumns.nextSetBit(0); col >= 0; col = missingColumns.nextSetBit(col + 1)) {
            BitSet singleCol = new BitSet();
            singleCol.set(col);
            PLI singleColPLI = get(Collections.singletonList(col));
            if (singleColPLI != null) {
                result = result.intersect(singleColPLI);
            }
        }

        return result;
    }

    /**
     * 查找最佳缓存子集PLI
     */
    private PLI findBestCachedSubsetPli(BitSet lhs) {
        List<Integer> columns = BitSetUtils.bitSetToList(lhs);
        if (columns.isEmpty()) return null;

        Node<PLI> currentNode = getRoot();
        PLI bestPli = null;

        for (int col : columns) {
            Node<PLI> child = currentNode.getChild(col);
            if (child == null) break;
            if (child.getValue() != null) {
                bestPli = child.getValue();
            }
            currentNode = child;
        }

        if (bestPli == null) {
            BitSet firstCol = new BitSet();
            firstCol.set(columns.get(0));
            return get(Collections.singletonList(columns.get(0)));
        }

        return bestPli;
    }

    /**
     * 查找所有子集PLI
     */
    private List<PLI> findAllSubsetPLIs(BitSet target) {
        List<PLI> result = new ArrayList<>();
        traverseSubsets(getRoot(), new BitSet(), BitSetUtils.bitSetToList(target), target, result);
        return result;
    }

    /**
     * 递归遍历子集
     */
    private void traverseSubsets(Node<PLI> node, BitSet currentPath,
                                List<Integer> targetOrder, BitSet target, List<PLI> result) {
        if (node != getRoot() && node.getValue() != null) {
            result.add(node.getValue());
        }

        for (int col : targetOrder) {
            if (currentPath.get(col)) continue;
            if (!target.get(col)) continue;

            BitSet newPath = (BitSet) currentPath.clone();
            newPath.set(col);
            if (!BitSetUtils.isSubSet(newPath, target)) continue;

            Node<PLI> child = node.getChildren().get(col);
            if (child == null) continue;

            traverseSubsets(child, newPath, targetOrder, target, result);
        }
    }

    /**
     * 缓存条目信息
     */
    private static class CacheEntry {
        final long lastAccessTime;
        final long accessCount;
        final boolean permanent; // 是否为永久缓存项（如单列PLI）

        CacheEntry(long lastAccessTime, long accessCount, boolean permanent) {
            this.lastAccessTime = lastAccessTime;
            this.accessCount = accessCount;
            this.permanent = permanent;
        }
    }
}
