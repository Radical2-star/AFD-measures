package pli;

import model.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BitSetUtils;
import utils.Trie;

import java.util.*;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
public class PLICache extends Trie<PLI> {
    private static final Logger logger = LoggerFactory.getLogger(PLICache.class);
    
    // 随机缓存概率
    public static final double CACHE_PROBABILITY = 0.5;
    
    // LRU缓存配置 - 可通过系统属性调整测试阈值
    private static final int MAX_CACHE_SIZE = Integer.getInteger("pli.cache.max", 10000);
    private static final int CLEANUP_BATCH_SIZE = Integer.getInteger("pli.cache.cleanup.batch", 1000);
    private static final int CLEANUP_THRESHOLD = Integer.getInteger("pli.cache.cleanup.threshold", 8000);
    
    private final DataSet dataset;
    
    // LRU管理：跟踪缓存项的访问顺序和统计信息
    private final LinkedHashMap<List<Integer>, Long> accessOrder;  // key -> 最后访问时间
    private int currentCacheSize = 0;  // 当前缓存的PLI数量
    private long accessCounter = 0;    // 访问计数器，用于统计
    private long hitCount = 0;         // 缓存命中计数
    private long missCount = 0;        // 缓存未命中计数

    public PLICache(DataSet dataset) {
        this.dataset = dataset;
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true); // 启用访问顺序
        initializeSingleColumnPLIs();
    }

    // 初始化单列PLI
    private void initializeSingleColumnPLIs() {
        int columnCount = dataset.getColumnCount();
        for (int col = 0; col < columnCount; col++) {
            BitSet columns = new BitSet();
            columns.set(col);
            PLI pli = new PLI(columns, dataset);
            List<Integer> key = Collections.singletonList(col);
            setWithLRU(key, pli);
        }
    }
    
    /**
     * 重写get方法，添加LRU访问跟踪
     */
    @Override
    public PLI get(List<Integer> key) {
        accessCounter++;
        PLI result = super.get(key);
        
        if (result != null) {
            hitCount++;
            // 更新访问时间
            updateAccessTime(key);
        } else {
            missCount++;
        }
        
        return result;
    }
    
    /**
     * 带LRU管理的set方法
     */
    private void setWithLRU(List<Integer> key, PLI value) {
        // 检查是否是更新现有项
        boolean isUpdate = super.get(key) != null;
        
        // 设置值
        super.set(key, value);
        updateAccessTime(key);
        
        // 如果是新项，增加计数
        if (!isUpdate) {
            currentCacheSize++;
            
            // 检查是否需要清理
            if (currentCacheSize > CLEANUP_THRESHOLD) {
                performLRUCleanup();
            }
        }
    }
    
    /**
     * 重写set方法，使用LRU管理
     */
    @Override
    public void set(List<Integer> key, PLI value) {
        setWithLRU(key, value);
    }
    
    /**
     * 更新访问时间
     */
    private void updateAccessTime(List<Integer> key) {
        accessOrder.put(new ArrayList<>(key), System.currentTimeMillis());
    }
    
    /**
     * 执行LRU清理
     */
    private void performLRUCleanup() {
        if (currentCacheSize <= CLEANUP_THRESHOLD) return;
        
        logger.info("开始PLI缓存清理，当前缓存数量: {}", currentCacheSize);
        
        Iterator<Map.Entry<List<Integer>, Long>> iterator = accessOrder.entrySet().iterator();
        int cleaned = 0;
        
        while (iterator.hasNext() && cleaned < CLEANUP_BATCH_SIZE && currentCacheSize > MAX_CACHE_SIZE - CLEANUP_BATCH_SIZE) {
            Map.Entry<List<Integer>, Long> entry = iterator.next();
            List<Integer> key = entry.getKey();
            
            // 不清理单列PLI，它们是基础缓存
            if (key.size() == 1) {
                continue;
            }
            
            // 从Trie中删除
            deletePLI(key);
            iterator.remove();
            cleaned++;
            currentCacheSize--;
        }
        
        logger.info("PLI缓存清理完成，清理数量: {}, 当前缓存数量: {}", cleaned, currentCacheSize);
    }
    
    /**
     * 安全删除PLI项
     */
    private void deletePLI(List<Integer> key) {
        try {
            super.delete(key);
        } catch (Exception e) {
            logger.warn("删除PLI缓存项失败: {}, 错误: {}", key, e.getMessage());
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        double hitRate = accessCounter > 0 ? (double) hitCount / accessCounter * 100 : 0;
        return String.format("PLI缓存统计 - 大小: %d, 访问: %d, 命中率: %.2f%%, 命中: %d, 未命中: %d", 
                           currentCacheSize, accessCounter, hitRate, hitCount, missCount);
    }

    /**
     * 获取或计算指定属性集的PLI
     * @param targetColumns 需要计算的属性集
     * @return 对应的PLI
     */
    public PLI getOrCalculatePLI(BitSet targetColumns) {
        List<Integer> sortedCols = BitSetUtils.bitSetToList(targetColumns);
        // 1. 优先从缓存获取
        PLI cached = get(sortedCols);
        if (cached != null) return cached;

        // 2. 获取所有可能子集PLI
        List<PLI> candidatePLIs = findAllSubsetPLIs(targetColumns);

        // 3. 贪心选择覆盖最多新属性的PLI（动态计算覆盖增益）
        List<PLI> selectedPLIs = new ArrayList<>();
        BitSet covered = new BitSet();
        while (!covered.equals(targetColumns)) {
            PLI bestPLI = null;
            int maxNewCover = -1;
            int minSize = Integer.MAX_VALUE;

            // 遍历候选集寻找最佳PLI
            for (PLI pli : candidatePLIs) {
                BitSet pliCols = pli.getColumns();
                // 计算新增覆盖列数
                BitSet newCover = (BitSet) pliCols.clone();
                newCover.andNot(covered);
                int newCoverCount = newCover.cardinality();

                // 选择条件：最多新覆盖列 → 若相同则选size更小的
                if (newCoverCount > maxNewCover ||
                        (newCoverCount == maxNewCover && pli.size() < minSize)) {
                    bestPLI = pli;
                    maxNewCover = newCoverCount;
                    minSize = pli.size();
                }
            }

            if (bestPLI == null) throw new RuntimeException("No PLI found.");
            selectedPLIs.add(bestPLI);
            covered.or(bestPLI.getColumns());
            candidatePLIs.remove(bestPLI);
        }

        // 4. 按size升序排序（确保先处理较小PLI）
        selectedPLIs.sort(Comparator.comparingInt(PLI::size));

        // 5. 逐步合并PLI
        PLI result = selectedPLIs.get(0);
        for (int i = 1; i < selectedPLIs.size(); i++) {
            result = result.intersect(selectedPLIs.get(i));
        }

        // 6. 概率性缓存（使用合并后的最终列）
        if (Math.random() < CACHE_PROBABILITY) {
            List<Integer> finalKey = BitSetUtils.bitSetToList(targetColumns);
            setWithLRU(finalKey, result);
        }

        return result;
    }

    /**
     * 辅助方法，通过BitSet获取PLI
     * @param columns 属性集
     * @return 对应的PLI，如果不存在则返回null
     */
    public PLI getPLI(BitSet columns) {
        return get(BitSetUtils.bitSetToList(columns));
    }

    /**
     * 辅助方法，通过BitSet检查是否存在PLI
     * @param columns 属性集
     * @return 是否存在
     */
    public boolean containsKey(BitSet columns) {
        return get(BitSetUtils.bitSetToList(columns)) != null;
    }

    /**
     * 高效查找LHS的最佳已缓存子集PLI
     * 通过遍历Trie树，寻找路径上存在的尽可能深的、已被缓存的PLI，避免任何重计算
     * 注意，这里查找到的并不一定是最深的，而是尽可能深的，是为了降低复杂度，避免多次遍历
     * 只按顺序查找子集，例如[1,2,3]，则只查找[1],[1,2],[1,2,3]，不查找[1,3]
     * @param lhs 左手边属性集
     * @return 找到的最佳子集PLI
     */
    public PLI findBestCachedSubsetPli(BitSet lhs) {
        List<Integer> columns = BitSetUtils.bitSetToList(lhs);
        if (columns.isEmpty()) {
            return null;
        }

        Node<PLI> currentNode = getRoot();
        PLI bestPli = null;

        // 沿着LHS的路径遍历Trie
        for (int col : columns) {
            Node<PLI> child = currentNode.getChild(col);
            if (child == null) {
                // 路径中断，无法继续深入
                break;
            }
            if (child.getValue() != null) {
                // 找到了一个被缓存的PLI，记录下来
                bestPli = child.getValue();
            }
            currentNode = child;
        }

        // 如果遍历完整个LHS路径都没有找到任何缓存的PLI（除了根节点）
        // 则退一步，返回单列中第一个属性的PLI（这个是保证一定存在的）
        // 理论上这部分应该不可能被执行，因为单列PLI一定被缓存
        if (bestPli == null) {
            BitSet firstCol = new BitSet();
            firstCol.set(columns.get(0));
            return getOrCalculatePLI(firstCol);
        }

        return bestPli;
    }

    private List<PLI> findAllSubsetPLIs(BitSet target) {
        List<PLI> result = new ArrayList<>();
        // 递归遍历Trie树
        traverseSubsets(
                this.getRoot(),
                new BitSet(), // 初始空路径
                BitSetUtils.bitSetToList(target),
                target,
                result
        );
        return result;
    }

    /**
     * 递归辅助函数：遍历所有可能的子集路径
     * @param node       当前Trie节点
     * @param currentPath 当前路径的列集合（BitSet）
     * @param targetOrder 目标列的有序列表（如[1,2,3]）
     * @param target     目标列集合
     * @param result     结果收集列表
     */
    private void traverseSubsets(
            Node<PLI> node,
            BitSet currentPath,
            List<Integer> targetOrder, // 传入list方便遍历，可能可以合并
            BitSet target, // 传入BitSet方便判断子集
            List<PLI> result
    ) {
        // 如果当前节点有值（非根节点）且是目标子集（这个判断在for循环里），则加入结果
        if (node != this.getRoot() && node.getValue() != null) {
            result.add(node.getValue());
        }

        // 遍历所有可能的后续列（保证递增顺序）
        for (int col : targetOrder) {
            // 剪枝1：当前路径已经包含该列则跳过
            if (currentPath.get(col)) continue;
            // 剪枝2：该列不在目标中则跳过
            if (!target.get(col)) continue;
            // 剪枝3：检查当前路径添加col后是否仍是目标子集
            BitSet newPath = (BitSet) currentPath.clone();
            newPath.set(col);
            if (!BitSetUtils.isSubSet(newPath, target)) continue;

            // 查找Trie中是否存在该列的子节点
            Node<PLI> child = node.getChildren().get(col);
            if (child == null) continue;

            // 递归深入
            traverseSubsets(child, newPath, targetOrder, target, result);
        }
    }
}