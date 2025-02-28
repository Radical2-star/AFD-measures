package pli;

import model.DataSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
public class PLICache {
    private final Map<Set<Integer>, PLI> cache = new LinkedHashMap<>();
    private final DataSet dataSet;

    public PLICache(DataSet dataSet) {
        this.dataSet = dataSet;
        initializeSingleColumnPLIs();
    }

    /**
     * 初始化单列PLI
     */
    private void initializeSingleColumnPLIs() {
        int columnCount = dataSet.getColumnCount();
        for (int col = 0; col < columnCount; col++) {
            Set<Integer> columnSet = Collections.singleton(col);
            cache.put(columnSet, computeSingleColumnPLI(col));
        }
    }

    /**
     * 获取指定列集合的PLI（自动计算并缓存）
     */
    public PLI getPLI(Set<Integer> columns) {
        Set<Integer> sortedColumns = new TreeSet<>(columns);

        // 直接返回已缓存结果
        if (cache.containsKey(sortedColumns)) {
            return cache.get(sortedColumns);
        }

        // 递归计算PLI交集
        List<Integer> columnList = new ArrayList<>(sortedColumns);
        if (columnList.get(0) > dataSet.getColumnCount()) {
            throw new IllegalArgumentException("Invalid column index: " + columnList.get(0));
        }
        PLI basePLI = cache.get(Collections.singleton(columnList.get(0)));

        for (int i = 1; i < columnList.size(); i++) {

            PLI nextPLI = cache.get(Collections.singleton(columnList.get(i)));
            basePLI = basePLI.intersect(nextPLI);
        }

        // 缓存计算结果
        cache.put(sortedColumns, basePLI);
        return basePLI;
    }

    /**
     * 计算单列PLI
     */
    private PLI computeSingleColumnPLI(int column) {
        Map<String, Set<Integer>> valueMap = new LinkedHashMap<>();

        for (int row = 0; row < dataSet.getRowCount(); row++) {
            String value = dataSet.getValue(row, column);
            valueMap.computeIfAbsent(value, k -> new LinkedHashSet<>()).add(row);
        }

        List<Set<Integer>> clusters = valueMap.values().stream()
                .filter(set -> set.size() > 1) // 仅保留非单例簇
                .collect(Collectors.toList());

        return new PLI(Collections.singleton(column), clusters);
    }
}