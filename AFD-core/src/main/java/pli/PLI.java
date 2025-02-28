package pli;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
public class PLI {
    private final Set<Integer> columns;
    private final List<Set<Integer>> equivalenceClasses;
    private final Map<Integer, Integer> rowClusterMap;

    public PLI(Set<Integer> columns, List<Set<Integer>> equivalenceClasses) {
        this.columns = Collections.unmodifiableSet(new HashSet<>(columns));
        this.equivalenceClasses = Collections.unmodifiableList(
                clusterStream()
                        .parallel()
                        .filter(set -> set.size() > 1)
                        .collect(Collectors.toList())
        );
        // 构建行到等价类的快速查找表
        this.rowClusterMap = new LinkedHashMap<>();
        for (int i = 0; i < this.equivalenceClasses.size(); i++) {
            for (Integer row : this.equivalenceClasses.get(i)) {
                rowClusterMap.put(row, i);
            }
        }
    }

    /**
     * 计算当前PLI与另一个PLI的交集
     */
    public PLI intersect(PLI other) {
        Set<Integer> combinedColumns = new LinkedHashSet<>(this.columns);
        combinedColumns.addAll(other.columns);

        // 使用行号作为连接键进行交集计算
        Map<Integer, Set<Integer>> mergedClusters = new LinkedHashMap<>();

        for (Set<Integer> cluster : this.equivalenceClasses) {
            for (Integer row : cluster) {
                Integer otherClusterId = other.rowClusterMap.get(row);
                if (otherClusterId != null) {
                    // 生成复合键：当前cluster ID + 对方cluster ID
                    String compositeKey = rowClusterMap.get(row) + "_" + otherClusterId;

                    mergedClusters
                            .computeIfAbsent(compositeKey.hashCode(), k -> new LinkedHashSet<>())
                            .add(row);
                }
            }
        }

        List<Set<Integer>> newClusters = new ArrayList<>(mergedClusters.values());

        return new PLI(combinedColumns, newClusters);
    }

    // Getters
    public Set<Integer> getColumns() {
        return columns;
    }

    public List<Set<Integer>> getEquivalenceClasses() {
        return equivalenceClasses;
    }

    public int getClusterCount() {
        return equivalenceClasses.size();
    }

    public Stream<Set<Integer>> clusterStream() {
        if (equivalenceClasses != null) {
            return equivalenceClasses.stream();
        } else return Stream.empty();
    }
}