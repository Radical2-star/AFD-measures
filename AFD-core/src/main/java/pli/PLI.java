package pli;

import model.DataSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @ClassName PLI
 * @Description
 * @Author Zuoxing Xie
 * @Time 2025/2/26
 * @Version 1.0
 */

public class PLI {
    // 单列存储结构
    private Map<Object, BitSet> columnValueIndex;
    // 组合列存储结构
    private List<List<Integer>> combinedPartitions;

    // 用于单列初始化
    public void buildSingleColumn(DataSet dataset, int column) {
        this.columnValueIndex = new HashMap<>();
        for (int row = 0; row < dataset.size(); row++) {
            Object value = dataset.getValue(row, column);
            columnValueIndex.computeIfAbsent(value, k -> new BitSet()).set(row);
        }
    }

    // 用于组合列初始化
    public void setCombinedPartitions(List<List<Integer>> partitions) {
        this.combinedPartitions = partitions.stream()
                .map(Collections::unmodifiableList)
                .collect(Collectors.toList());
    }

    public List<List<Integer>> getPartitions() {
        if (combinedPartitions != null) {
            return combinedPartitions;
        }
        return computeSingleColumnPartitions();
    }

    private List<List<Integer>> computeSingleColumnPartitions() {
        return columnValueIndex.values().stream()
                .filter(bs -> bs.cardinality() > 1)
                .map(this::convertToSortedList)
                .collect(Collectors.toList());
    }

    private List<Integer> convertToSortedList(BitSet bs) {
        List<Integer> list = new ArrayList<>();
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
            list.add(i);
        }
        return Collections.unmodifiableList(list);
    }

    // 合并方法返回可直接使用的分区集合
    public static List<List<Integer>> merge(List<PLI> plis) {
        List<List<List<Integer>>> allPartitions = plis.stream()
                .map(PLI::getPartitions)
                .collect(Collectors.toList());

        return mergePartitions(allPartitions);
    }

    private static List<List<Integer>> mergePartitions(List<List<List<Integer>>> allPartitions) {
        Set<List<Integer>> result = new HashSet<>(allPartitions.get(0));

        for (int i = 1; i < allPartitions.size(); i++) {
            Set<List<Integer>> temp = new HashSet<>();
            for (List<Integer> p1 : result) {
                for (List<Integer> p2 : allPartitions.get(i)) {
                    List<Integer> intersection = intersectSorted(p1, p2);
                    if (intersection.size() > 1) {
                        temp.add(intersection);
                    }
                }
            }
            result = temp;
            if (result.isEmpty()) break;
        }

        return new ArrayList<>(result);
    }

    private static List<Integer> intersectSorted(List<Integer> a, List<Integer> b) {
        List<Integer> res = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            int x = a.get(i);
            int y = b.get(j);
            if (x == y) {
                res.add(x);
                i++;
                j++;
            } else if (x < y) {
                i++;
            } else {
                j++;
            }
        }
        return res;
    }
}

/*
public class PLI {
    // 存储值到行位置的映射（值 -> 行索引集合）
    private final Map<Object, BitSet> valueIndex;
    // 缓存已计算的分区
    private List<List<Integer>> partitionsCache;

    public PLI() {
        this.valueIndex = new HashMap<>();
    }

    // 构建单个属性的PLI
    public void buildForColumn(Dataset dataset, int column) {
        valueIndex.clear();
        for (int row = 0; row < dataset.size(); row++) {
            Object value = dataset.getValue(row, column);
            valueIndex.computeIfAbsent(value, k -> new BitSet()).set(row);
        }
        partitionsCache = null; // 使缓存失效
    }

    // 获取当前列的所有分区
    public List<List<Integer>> getPartitions() {
        if (partitionsCache == null) {
            partitionsCache = computePartitions();
        }
        return Collections.unmodifiableList(partitionsCache);
    }

    private List<List<Integer>> computePartitions() {
        return valueIndex.values().stream()
                .filter(bs -> bs.cardinality() > 1) // 仅保留重复值
                .map(PLI::convertToSortedList)
                .collect(Collectors.toList());
    }

    private static List<Integer> convertToSortedList(BitSet bitSet) {
        List<Integer> list = new ArrayList<>();
        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i+1)) {
            list.add(i);
        }
        return Collections.unmodifiableList(list);
    }

    // 合并多个PLI生成组合分区
    public static List<List<Integer>> mergePLIs(List<PLI> plis) {
        if (plis.isEmpty()) return Collections.emptyList();

        // 使用不可变局部变量解决lambda捕获问题
        final Map<BitSet, BitSet> initialMap = new HashMap<>();
        plis.get(0).valueIndex.forEach((value, bs) ->
                initialMap.put((BitSet) bs.clone(), (BitSet) bs.clone()));

        Map<BitSet, BitSet> mergedMap = new HashMap<>(initialMap);

        for (int i = 1; i < plis.size(); i++) {
            final PLI currentPLI = plis.get(i);
            Map<BitSet, BitSet> tempMap = new HashMap<>();

            mergedMap.forEach((mergedKey, mergedValue) -> {
                currentPLI.valueIndex.forEach((value, currentBs) -> {
                    BitSet intersection = (BitSet) mergedValue.clone();
                    intersection.and(currentBs);

                    if (intersection.cardinality() > 1) {
                        tempMap.merge(intersection, intersection,
                                (existing, newBs) -> {
                                    existing.or(newBs);
                                    return existing;
                                });
                    }
                });
            });

            mergedMap = tempMap;
            if (mergedMap.isEmpty()) break;
        }

        return mergedMap.values().stream()
                .map(PLI::convertToSortedList)
                .collect(Collectors.toList());
    }
}
 */