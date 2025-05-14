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

    public static boolean isSubSet(BitSet subSet, BitSet superSet) {
        BitSet temp = (BitSet) subSet.clone();
        temp.and(superSet);
        return temp.equals(subSet);
    }

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
}
