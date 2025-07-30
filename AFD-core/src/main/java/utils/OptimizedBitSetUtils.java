package utils;

import java.util.*;

/**
 * 优化的BitSet工具类，专门针对Pyro算法的性能瓶颈进行优化
 * 
 * 主要优化：
 * 1. 对于小列数（≤64）使用long替代BitSet
 * 2. 缓存cardinality计算结果
 * 3. 优化BitSet操作和转换
 * 4. 减少对象创建和内存分配
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/29
 */
public class OptimizedBitSetUtils {
    
    // 使用long优化的阈值
    private static final int LONG_OPTIMIZATION_THRESHOLD = 64;
    
    // BitSet cardinality缓存
    private static final Map<BitSet, Integer> cardinalityCache = new WeakHashMap<>();
    
    // BitSet到List转换缓存
    private static final Map<BitSet, List<Integer>> bitSetToListCache = new WeakHashMap<>();
    
    /**
     * 优化的cardinality计算，使用缓存避免重复计算
     */
    public static int getCardinality(BitSet bitSet) {
        return cardinalityCache.computeIfAbsent(bitSet, BitSet::cardinality);
    }
    
    /**
     * 优化的BitSet到List转换，使用缓存避免重复计算
     */
    public static List<Integer> bitSetToListOptimized(BitSet bitSet) {
        return bitSetToListCache.computeIfAbsent(bitSet, BitSetUtils::bitSetToList);
    }
    
    /**
     * 对于小列数（≤64）使用long进行高效的子集检查
     */
    public static boolean isSubSetOptimized(BitSet subset, BitSet superset, int columnCount) {
        if (columnCount <= LONG_OPTIMIZATION_THRESHOLD) {
            long subsetLong = BitSetUtils.bitSetToLong(subset, columnCount);
            long supersetLong = BitSetUtils.bitSetToLong(superset, columnCount);
            return (subsetLong & supersetLong) == subsetLong;
        } else {
            return BitSetUtils.isSubSet(subset, superset);
        }
    }
    
    /**
     * 优化的BitSet克隆操作，对小列数使用更高效的方式
     */
    public static BitSet cloneOptimized(BitSet original, int columnCount) {
        if (columnCount <= LONG_OPTIMIZATION_THRESHOLD) {
            // 对于小列数，使用long进行快速克隆
            long value = BitSetUtils.bitSetToLong(original, columnCount);
            BitSet result = new BitSet(columnCount);
            for (int i = 0; i < columnCount; i++) {
                if ((value & (1L << i)) != 0) {
                    result.set(i);
                }
            }
            return result;
        } else {
            return (BitSet) original.clone();
        }
    }
    
    /**
     * 批量创建子节点的BitSet，减少重复的克隆操作
     */
    public static List<BitSet> createChildrenBitSets(BitSet parent, int columnCount, int rhsColumn) {
        List<BitSet> children = new ArrayList<>();
        
        if (columnCount <= LONG_OPTIMIZATION_THRESHOLD) {
            // 使用long优化
            long parentLong = BitSetUtils.bitSetToLong(parent, columnCount);
            for (int i = 0; i < columnCount; i++) {
                if (i != rhsColumn && (parentLong & (1L << i)) == 0) {
                    long childLong = parentLong | (1L << i);
                    BitSet child = new BitSet(columnCount);
                    for (int j = 0; j < columnCount; j++) {
                        if ((childLong & (1L << j)) != 0) {
                            child.set(j);
                        }
                    }
                    children.add(child);
                }
            }
        } else {
            // 传统方式
            for (int i = 0; i < columnCount; i++) {
                if (i != rhsColumn && !parent.get(i)) {
                    BitSet child = (BitSet) parent.clone();
                    child.set(i);
                    children.add(child);
                }
            }
        }
        
        return children;
    }
    
    /**
     * 优化的BitSet flip操作
     */
    public static BitSet flipOptimized(BitSet original, int columnCount) {
        if (columnCount <= LONG_OPTIMIZATION_THRESHOLD) {
            long originalLong = BitSetUtils.bitSetToLong(original, columnCount);
            long flippedLong = (~originalLong) & ((1L << columnCount) - 1);
            
            BitSet result = new BitSet(columnCount);
            for (int i = 0; i < columnCount; i++) {
                if ((flippedLong & (1L << i)) != 0) {
                    result.set(i);
                }
            }
            return result;
        } else {
            BitSet result = (BitSet) original.clone();
            result.flip(0, columnCount);
            return result;
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        return String.format("BitSet缓存统计 - cardinality缓存: %d, bitSetToList缓存: %d", 
                           cardinalityCache.size(), bitSetToListCache.size());
    }
    
    /**
     * 清理缓存（在内存压力大时调用）
     */
    public static void clearCaches() {
        cardinalityCache.clear();
        bitSetToListCache.clear();
    }
    
    /**
     * 长整型BitSet表示（用于小列数优化）
     */
    public static class LongBitSet {
        private final long bits;
        private final int cardinality;
        private final int columnCount;
        
        public LongBitSet(long bits, int columnCount) {
            this.bits = bits;
            this.columnCount = columnCount;
            this.cardinality = Long.bitCount(bits);
        }
        
        public LongBitSet(BitSet bitSet, int columnCount) {
            this.bits = BitSetUtils.bitSetToLong(bitSet, columnCount);
            this.columnCount = columnCount;
            this.cardinality = Long.bitCount(bits);
        }
        
        public boolean isSubsetOf(LongBitSet other) {
            return (this.bits & other.bits) == this.bits;
        }
        
        public LongBitSet union(LongBitSet other) {
            return new LongBitSet(this.bits | other.bits, columnCount);
        }
        
        public LongBitSet intersection(LongBitSet other) {
            return new LongBitSet(this.bits & other.bits, columnCount);
        }
        
        public LongBitSet complement() {
            long mask = (1L << columnCount) - 1;
            return new LongBitSet((~this.bits) & mask, columnCount);
        }
        
        public BitSet toBitSet() {
            BitSet result = new BitSet(columnCount);
            for (int i = 0; i < columnCount; i++) {
                if ((bits & (1L << i)) != 0) {
                    result.set(i);
                }
            }
            return result;
        }
        
        public int getCardinality() {
            return cardinality;
        }
        
        public long getBits() {
            return bits;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LongBitSet)) return false;
            LongBitSet other = (LongBitSet) obj;
            return bits == other.bits && columnCount == other.columnCount;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(bits, columnCount);
        }
        
        @Override
        public String toString() {
            return "LongBitSet{bits=" + Long.toBinaryString(bits) + ", cardinality=" + cardinality + "}";
        }
    }
}
