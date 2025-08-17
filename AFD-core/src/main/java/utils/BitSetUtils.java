package utils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/27
 */
public class BitSetUtils {
    public static List<Integer> bitSetToList(BitSet bitSet) {
        List<Integer> list = new ArrayList<>();
        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i+1)) {
            list.add(i);
        }
        return list;
    }

    public static BitSet listToBitSet(List<Integer> list) {
        BitSet bitSet = new BitSet();
        for (int i : list) {
            bitSet.set(i);
        }
        return bitSet;
    }

    public static BitSet setToBitSet(Set<Integer> lhs) {
        BitSet bitSet = new BitSet();
        for (int i : lhs) {
            bitSet.set(i);
        }
        return bitSet;
    }

    public static Set<Integer> bitSetToSet(BitSet bitSet) {
        Set<Integer> set = new HashSet<>();
        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            set.add(i);
        }
        return set;
    }

    /**
     * Converts a BitSet to its long representation.
     * Assumes the number of bits in use (column count) is less than 64.
     * @param bs The BitSet to convert.
     * @param numColumns The total number of columns, to ensure iteration bounds.
     * @return A long representing the BitSet.
     */
    public static long bitSetToLong(BitSet bs, int numColumns) {
        long value = 0L;
        // Iterate only up to numColumns to avoid issues if BitSet has bits set beyond numColumns
        for (int i = bs.nextSetBit(0); i >= 0 && i < numColumns; i = bs.nextSetBit(i + 1)) {
            value |= (1L << i);
        }
        return value;
    }

    public static boolean isSubSet(BitSet subSet, BitSet superSet) {
        BitSet temp = (BitSet) subSet.clone();
        temp.and(superSet);
        return temp.equals(subSet);
    }

    /**
     * 计算最小命中集
     * @param sets long表示的位集合列表
     * @param columnCount 列数
     * @return 包含所有最小命中集的HittingSet
     */
    public static HittingSet calculateHittingSet(List<Long> sets, int columnCount) {
        LongBitSetUtils.validateColumnCount(columnCount);

        HittingSet hittingset = new HittingSet();
        // 按long的cardinality升序排序
        sets.sort(Comparator.comparingInt(LongBitSetUtils::cardinality));

        for (long set : sets) {
            if (hittingset.isEmpty()) {
                // 初始化：为第一个集合的每个元素创建单元素集合
                LongBitSetUtils.forEach(set, i -> {
                    long newSet = LongBitSetUtils.setBit(0L, i);
                    hittingset.add(newSet);
                });
                continue;
            }

            long sFlip = LongBitSetUtils.complement(set, columnCount);
            // 移除hittingset中sFlip的子集
            List<Long> removed = hittingset.removeSubsets(sFlip);

            for (long removedSet : removed) {
                LongBitSetUtils.forEach(set, i -> {
                    long newLongSet = LongBitSetUtils.setBit(removedSet, i);
                    hittingset.addIfNoSubset(newLongSet);
                });
            }
        }
        return hittingset;
    }

    // ==================== 原有BitSet版本方法====================

    /*
    public static HittingSet calculateHittingSet(List<BitSet> sets, int nbits) {
        HittingSet hittingset = new HittingSet();
        // 按BitSet的cardinality升序排序
        sets.sort(Comparator.comparingInt(BitSet::cardinality));
        for (BitSet set : sets) {
            if (hittingset.isEmpty()) {
                for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
                    BitSet newSet = new BitSet();
                    newSet.set(i);
                    hittingset.add(newSet);
                }
                continue;
            }
            BitSet sFlip = (BitSet) set.clone();
            sFlip.flip(0, nbits);
            // 移除hittingset中sFlip的子集
            List<BitSet> removed = hittingset.removeSubsets(sFlip);
            for (BitSet removedSet : removed) {
                for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
                    BitSet newBitSet = (BitSet) removedSet.clone();
                    newBitSet.set(i);
                    hittingset.addIfNoSubset(newBitSet);
                }
            }
        }
        return hittingset;
    }
     */
}
