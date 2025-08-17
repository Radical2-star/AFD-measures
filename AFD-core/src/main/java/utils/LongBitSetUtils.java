package utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * 基于long的高性能位集合工具类
 * 专门为≤64列的数据集优化，提供比BitSet更高效的位操作
 * 
 * 核心优化：
 * 1. 使用long原生位操作，避免对象创建开销
 * 2. 缓存常用计算结果（cardinality、转换等）
 * 3. 批量操作优化
 * 4. 内存友好的缓存策略
 * 
 * @author Hoshi
 * @version 2.0
 * @since 2025/7/30
 */
public class LongBitSetUtils {
    
    // 性能统计
    private static long operationCount = 0;
    private static long cacheHitCount = 0;
    private static long cacheMissCount = 0;
    
    // 缓存配置
    private static final int MAX_CACHE_SIZE = 10000;
    
    // 各种缓存
    private static final Map<Long, Integer> cardinalityCache = new ConcurrentHashMap<>();
    private static final Map<Long, List<Integer>> longToListCache = new ConcurrentHashMap<>();
    private static final Map<List<Integer>, Long> listToLongCache = new ConcurrentHashMap<>();
    private static final Map<Long, Set<Long>> parentsCache = new ConcurrentHashMap<>();
    
    /**
     * 计算long表示的位集合的cardinality（设置位的数量）
     */
    public static int cardinality(long bits) {
        operationCount++;
        return cardinalityCache.computeIfAbsent(bits, Long::bitCount);
    }
    
    /**
     * 检查subset是否是superset的子集
     */
    public static boolean isSubset(long subset, long superset) {
        operationCount++;
        return (subset & superset) == subset;
    }
    
    /**
     * 检查两个位集合是否相等
     */
    public static boolean equals(long bits1, long bits2) {
        operationCount++;
        return bits1 == bits2;
    }
    
    /**
     * 计算两个位集合的并集
     */
    public static long union(long bits1, long bits2) {
        operationCount++;
        return bits1 | bits2;
    }
    
    /**
     * 计算两个位集合的交集
     */
    public static long intersection(long bits1, long bits2) {
        operationCount++;
        return bits1 & bits2;
    }
    
    /**
     * 计算位集合的补集（在指定列数范围内）
     */
    public static long complement(long bits, int columnCount) {
        operationCount++;
        if (columnCount >= 64) {
            throw new IllegalArgumentException("列数不能超过64");
        }
        long mask = (1L << columnCount) - 1;
        return (~bits) & mask;
    }
    
    /**
     * 设置指定位
     */
    public static long setBit(long bits, int bitIndex) {
        operationCount++;
        return bits | (1L << bitIndex);
    }
    
    /**
     * 清除指定位
     */
    public static long clearBit(long bits, int bitIndex) {
        operationCount++;
        return bits & ~(1L << bitIndex);
    }
    
    /**
     * 检查指定位是否设置
     */
    public static boolean testBit(long bits, int bitIndex) {
        operationCount++;
        return (bits & (1L << bitIndex)) != 0;
    }
    
    /**
     * 获取最低设置位的索引
     */
    public static int nextSetBit(long bits, int fromIndex) {
        operationCount++;
        if (fromIndex >= 64) return -1;
        
        // 清除fromIndex之前的所有位
        long mask = -(1L << fromIndex);
        long maskedBits = bits & mask;
        
        if (maskedBits == 0) return -1;
        return Long.numberOfTrailingZeros(maskedBits);
    }
    
    /**
     * 将long位集合转换为List<Integer>
     * 使用缓存优化性能
     */
    public static List<Integer> longToList(long bits) {
        operationCount++;
        
        List<Integer> cached = longToListCache.get(bits);
        if (cached != null) {
            cacheHitCount++;
            return new ArrayList<>(cached); // 返回副本避免修改
        }
        
        cacheMissCount++;
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) {
                result.add(i);
            }
        }
        
        // 缓存管理
        if (longToListCache.size() < MAX_CACHE_SIZE) {
            longToListCache.put(bits, new ArrayList<>(result));
        }
        
        return result;
    }
    
    /**
     * 将List<Integer>转换为long位集合
     * 使用缓存优化性能
     */
    public static long listToLong(List<Integer> list) {
        operationCount++;
        
        Long cached = listToLongCache.get(list);
        if (cached != null) {
            cacheHitCount++;
            return cached;
        }
        
        cacheMissCount++;
        long result = 0L;
        for (int bit : list) {
            if (bit >= 64) {
                throw new IllegalArgumentException("位索引不能超过63: " + bit);
            }
            result |= (1L << bit);
        }
        
        // 缓存管理
        if (listToLongCache.size() < MAX_CACHE_SIZE) {
            listToLongCache.put(new ArrayList<>(list), result);
        }
        
        return result;
    }
    
    /**
     * 获取所有父节点（移除一个位的所有可能结果）
     * 使用缓存优化性能
     */
    public static Set<Long> getAllParents(long bits) {
        operationCount++;
        
        Set<Long> cached = parentsCache.get(bits);
        if (cached != null) {
            cacheHitCount++;
            return new HashSet<>(cached);
        }
        
        cacheMissCount++;
        Set<Long> parents = new HashSet<>();
        
        // 遍历所有设置的位
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) {
                long parent = bits & ~(1L << i); // 清除第i位
                if (parent != 0) { // 避免空集
                    parents.add(parent);
                }
            }
        }
        
        // 缓存管理
        if (parentsCache.size() < MAX_CACHE_SIZE) {
            parentsCache.put(bits, new HashSet<>(parents));
        }
        
        return parents;
    }
    
    /**
     * 批量创建子节点（添加一个位的所有可能结果）
     */
    public static Set<Long> getAllChildren(long bits, int columnCount, int excludeColumn) {
        operationCount++;
        Set<Long> children = new HashSet<>();
        
        for (int i = 0; i < columnCount; i++) {
            if (i != excludeColumn && (bits & (1L << i)) == 0) {
                children.add(bits | (1L << i));
            }
        }
        
        return children;
    }
    
    /**
     * 检查位集合是否为空
     */
    public static boolean isEmpty(long bits) {
        operationCount++;
        return bits == 0L;
    }
    
    /**
     * 获取位集合的字符串表示（用于调试）
     */
    public static String toString(long bits, int columnCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (int i = 0; i < columnCount; i++) {
            if ((bits & (1L << i)) != 0) {
                if (!first) sb.append(", ");
                sb.append(i);
                first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * 获取性能统计信息
     */
    public static String getPerformanceStats() {
        double hitRate = (cacheHitCount + cacheMissCount) > 0 ? 
                        (double) cacheHitCount / (cacheHitCount + cacheMissCount) * 100 : 0;
        
        return String.format(
            "LongBitSet统计 - 操作次数: %d, 缓存命中率: %.2f%% (%d/%d), " +
            "缓存大小: cardinality=%d, longToList=%d, listToLong=%d, parents=%d",
            operationCount, hitRate, cacheHitCount, cacheHitCount + cacheMissCount,
            cardinalityCache.size(), longToListCache.size(), 
            listToLongCache.size(), parentsCache.size()
        );
    }
    
    /**
     * 清理所有缓存
     */
    public static void clearAllCaches() {
        cardinalityCache.clear();
        longToListCache.clear();
        listToLongCache.clear();
        parentsCache.clear();
        
        // 重置统计
        operationCount = 0;
        cacheHitCount = 0;
        cacheMissCount = 0;
    }
    
    /**
     * 清理大小超限的缓存
     */
    public static void cleanupCaches() {
        if (longToListCache.size() > MAX_CACHE_SIZE) {
            longToListCache.clear();
        }
        if (listToLongCache.size() > MAX_CACHE_SIZE) {
            listToLongCache.clear();
        }
        if (parentsCache.size() > MAX_CACHE_SIZE) {
            parentsCache.clear();
        }
    }

    // ==================== 新增方法：BitSet兼容性和增强功能 ====================

    /**
     * 将long位集合转换为BitSet
     * @param bits long表示的位集合
     * @param columnCount 列数，用于边界检查
     * @return 对应的BitSet
     */
    public static java.util.BitSet longToBitSet(long bits, int columnCount) {
        validateColumnCount(columnCount);
        operationCount++;

        java.util.BitSet bitSet = new java.util.BitSet(columnCount);
        for (int i = 0; i < columnCount; i++) {
            if ((bits & (1L << i)) != 0) {
                bitSet.set(i);
            }
        }
        return bitSet;
    }

    /**
     * 将BitSet转换为long位集合
     * @param bitSet BitSet对象
     * @param columnCount 列数，用于边界检查
     * @return 对应的long值
     */
    public static long bitSetToLong(java.util.BitSet bitSet, int columnCount) {
        validateColumnCount(columnCount);
        operationCount++;

        long result = 0L;
        for (int i = bitSet.nextSetBit(0); i >= 0 && i < columnCount; i = bitSet.nextSetBit(i + 1)) {
            result |= (1L << i);
        }
        return result;
    }

    /**
     * 创建包含指定位的long位集合
     * @param bitIndices 要设置的位索引数组
     * @return 对应的long值
     */
    public static long createFromBits(int... bitIndices) {
        operationCount++;
        long result = 0L;
        for (int bit : bitIndices) {
            if (bit >= 64 || bit < 0) {
                throw new IllegalArgumentException("位索引必须在0-63范围内: " + bit);
            }
            result |= (1L << bit);
        }
        return result;
    }

    /**
     * 获取位集合的IntStream，用于遍历所有设置的位
     * @param bits long表示的位集合
     * @return 包含所有设置位索引的IntStream
     */
    public static IntStream stream(long bits) {
        operationCount++;
        return longToList(bits).stream().mapToInt(Integer::intValue);
    }

    /**
     * 对位集合中的每个设置位执行指定操作
     * @param bits long表示的位集合
     * @param action 要执行的操作
     */
    public static void forEach(long bits, IntConsumer action) {
        operationCount++;
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) {
                action.accept(i);
            }
        }
    }

    /**
     * 克隆位集合（对于long来说就是返回原值）
     * @param bits 要克隆的位集合
     * @return 克隆的结果（等于原值）
     */
    public static long clone(long bits) {
        operationCount++;
        return bits; // long是值类型，直接返回即可
    }

    /**
     * 翻转位集合（在指定列数范围内）
     * @param bits 原位集合
     * @param columnCount 列数
     * @return 翻转后的位集合
     */
    public static long flip(long bits, int columnCount) {
        validateColumnCount(columnCount);
        operationCount++;
        return complement(bits, columnCount);
    }

    /**
     * 验证列数是否在有效范围内
     * @param columnCount 列数
     * @throws IllegalArgumentException 如果列数超出范围
     */
    public static void validateColumnCount(int columnCount) {
        if (columnCount < 0) {
            throw new IllegalArgumentException("列数不能为负数: " + columnCount);
        }
        if (columnCount > 64) {
            throw new IllegalArgumentException("列数不能超过64: " + columnCount +
                                             "。如需支持更多列，请使用BitSet版本。");
        }
    }

    /**
     * 验证位索引是否在有效范围内
     * @param bitIndex 位索引
     * @throws IllegalArgumentException 如果位索引超出范围
     */
    public static void validateBitIndex(int bitIndex) {
        if (bitIndex < 0) {
            throw new IllegalArgumentException("位索引不能为负数: " + bitIndex);
        }
        if (bitIndex >= 64) {
            throw new IllegalArgumentException("位索引不能超过63: " + bitIndex);
        }
    }

    /**
     * 安全的设置位操作，包含边界检查
     * @param bits 原位集合
     * @param bitIndex 要设置的位索引
     * @return 设置位后的结果
     */
    public static long setBitSafe(long bits, int bitIndex) {
        validateBitIndex(bitIndex);
        return setBit(bits, bitIndex);
    }

    /**
     * 安全的清除位操作，包含边界检查
     * @param bits 原位集合
     * @param bitIndex 要清除的位索引
     * @return 清除位后的结果
     */
    public static long clearBitSafe(long bits, int bitIndex) {
        validateBitIndex(bitIndex);
        return clearBit(bits, bitIndex);
    }

    /**
     * 安全的测试位操作，包含边界检查
     * @param bits 位集合
     * @param bitIndex 要测试的位索引
     * @return 该位是否被设置
     */
    public static boolean testBitSafe(long bits, int bitIndex) {
        validateBitIndex(bitIndex);
        return testBit(bits, bitIndex);
    }
}
