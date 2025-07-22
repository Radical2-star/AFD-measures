package pli;

import model.DataSet;

import java.util.*;
import java.util.stream.Stream;

/**
 *
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
public class PLI {
    private final BitSet columns;
    private final int rowCount;
    private final List<Set<Integer>> equivalenceClasses;
    private final int[] attributeVector;
    private final int size;

    public PLI(BitSet columns, DataSet data) {
        this.columns = (BitSet) columns.clone();
        this.rowCount = data.getRowCount();
        this.equivalenceClasses = constructEquivalenceClasses(columns, data);
        this.attributeVector = toAttributeVector(); // 对单列PLI，缓存其属性向量
        this.size = clusterStream()
                .mapToInt(Set::size)
                .sum();
    }

    private PLI(BitSet columns, int rowCount, List<Set<Integer>> equivalenceClasses) {
        this.columns = (BitSet) columns.clone();
        this.rowCount = rowCount;
        this.equivalenceClasses = equivalenceClasses;
        this.attributeVector = null;
        this.size = clusterStream()
                .mapToInt(Set::size)
                .sum();
    }

    private List<Set<Integer>> constructEquivalenceClasses(BitSet columns, DataSet data) {
        // 用Map来记录每个键对应的行索引集合
        Map<String, Set<Integer>> keyToRows = new HashMap<>();
        int totalRows = data.getRowCount();

        // 遍历每一行
        for (int rowIdx = 0; rowIdx < totalRows; rowIdx++) {
            List<String> row = data.getRow(rowIdx);
            // 生成当前行在选定列上的组合键
            StringJoiner keyBuilder = new StringJoiner("|");
            columns.stream().forEach(col -> keyBuilder.add(row.get(col)));
            String key = keyBuilder.toString();

            // 将行索引加入对应的集合
            keyToRows.computeIfAbsent(key, k -> new HashSet<>()).add(rowIdx);
        }

        // 过滤并收集大小>1的集合
        List<Set<Integer>> equivalenceClasses = new ArrayList<>();
        for (Set<Integer> group : keyToRows.values()) {
            if (group.size() > 1) {
                equivalenceClasses.add(Collections.unmodifiableSet(group));
            }
        }

        return equivalenceClasses;
    }


    /**
     * 计算当前PLI与另一个PLI的交集
     */
    public PLI intersect(PLI other) {
        // 步骤①：确定较大的PLI作为Y，较小的作为X
        PLI x, y;
        if (this.size() >= other.size()) {
            y = this;
            x = other;
        } else {
            y = other;
            x = this;
        }

        // 步骤②：构建Y的属性向量（行号 -> 簇ID）
        int[] yAttributeVector = y.toAttributeVector();

        // 步骤③：用Y的属性向量探测X的每个簇
        List<Set<Integer>> newEquivalenceClasses = new ArrayList<>();
        for (Set<Integer> xCluster : x.equivalenceClasses) {
            // 按Y的属性值分组
            Map<Integer, Set<Integer>> tempGroups = new HashMap<>();
            for (Integer row : xCluster) {
                int yValue = yAttributeVector[row];
                if (yValue != 0) { // 只处理有簇的情况
                    tempGroups.computeIfAbsent(yValue, k -> new HashSet<>())
                            .add(row);
                }
            }

            // 过滤并收集有效簇（大小>1）
            for (Set<Integer> group : tempGroups.values()) {
                if (group.size() > 1) {
                    newEquivalenceClasses.add(Collections.unmodifiableSet(group));
                }
            }
        }

        // 步骤④：合并列属性（取并集）
        BitSet mergedColumns = (BitSet) x.columns.clone();
        mergedColumns.or(y.columns);

        // 创建新PLI（使用私有构造函数）
        return new PLI(mergedColumns, x.rowCount, newEquivalenceClasses);
    }

    // Getters
    public BitSet getColumns() {
        return (BitSet) columns.clone();
    }

    public int getRowCount() {
        return rowCount;
    }

    public List<Set<Integer>> getEquivalenceClasses() {
        return equivalenceClasses;
    }

    public int getClusterCount() {
        return equivalenceClasses.size();
    }

    public int size(){
        return this.size;
    }

    public Stream<Set<Integer>> clusterStream() {
        if (equivalenceClasses != null) {
            return equivalenceClasses.stream();
        } else return Stream.empty();
    }

    public int[] toAttributeVector() {
        if (this.attributeVector != null) return this.attributeVector;
        int[] attributeVector = new int[this.rowCount];
        Arrays.fill(attributeVector, 0); // 初始值0表示单例
        int clusterId = 1; // 簇ID从1开始编号
        for (Set<Integer> cluster : this.equivalenceClasses) {
            for (Integer row : cluster) {
                attributeVector[row] = clusterId;
            }
            clusterId++;
        }
        return attributeVector;
    }
}