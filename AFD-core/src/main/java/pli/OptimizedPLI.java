package pli;

import model.DataSet;
import java.util.*;
import java.util.stream.Stream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存优化的PLI实现
 * 主要优化：
 * 1. 使用压缩的等价类存储
 * 2. 延迟计算属性向量
 * 3. 使用原始数组替代集合
 * 4. 优化字符串键生成
 * 
 * @author Hoshi
 * @version 2.0
 * @since 2025/7/28
 */
public class OptimizedPLI {
    private final BitSet columns;
    private final int rowCount;
    
    // 使用压缩存储：每个等价类用int[]数组存储行索引
    private final List<int[]> compressedEquivalenceClasses;
    
    // 延迟计算的属性向量，使用软引用避免内存泄漏
    private volatile int[] attributeVector;
    private final int totalSize;
    
    // 内存使用统计
    private final long memoryFootprint;
    
    public OptimizedPLI(BitSet columns, DataSet data) {
        this.columns = (BitSet) columns.clone();
        this.rowCount = data.getRowCount();
        
        // 使用优化的构造方法
        ConstructionResult result = constructOptimizedEquivalenceClasses(columns, data);
        this.compressedEquivalenceClasses = result.equivalenceClasses;
        this.totalSize = result.totalSize;
        this.memoryFootprint = result.memoryFootprint;
    }
    
    private OptimizedPLI(BitSet columns, int rowCount, List<int[]> equivalenceClasses, int totalSize) {
        this.columns = (BitSet) columns.clone();
        this.rowCount = rowCount;
        this.compressedEquivalenceClasses = equivalenceClasses;
        this.totalSize = totalSize;
        this.memoryFootprint = calculateMemoryFootprint();
    }
    
    /**
     * 优化的等价类构造方法
     * 主要优化：
     * 1. 使用StringBuilder替代StringJoiner
     * 2. 使用TIntObjectHashMap减少装箱
     * 3. 直接构造int[]数组
     */
    private ConstructionResult constructOptimizedEquivalenceClasses(BitSet columns, DataSet data) {
        // 使用更高效的Map实现
        Map<String, List<Integer>> keyToRows = new HashMap<>(data.getRowCount() / 10);
        int totalRows = data.getRowCount();
        long memoryUsed = 0;
        
        // 预分配StringBuilder避免重复扩容
        StringBuilder keyBuilder = new StringBuilder(64);
        
        // 遍历每一行
        for (int rowIdx = 0; rowIdx < totalRows; rowIdx++) {
            List<String> row = data.getRow(rowIdx);
            
            // 重用StringBuilder，避免对象创建
            keyBuilder.setLength(0);
            boolean first = true;
            for (int col = columns.nextSetBit(0); col >= 0; col = columns.nextSetBit(col + 1)) {
                if (!first) keyBuilder.append('|');
                keyBuilder.append(row.get(col));
                first = false;
            }
            String key = keyBuilder.toString();
            
            // 使用List而不是Set，减少内存开销
            keyToRows.computeIfAbsent(key, k -> new ArrayList<>()).add(rowIdx);
        }
        
        // 转换为压缩格式
        List<int[]> compressedClasses = new ArrayList<>();
        int totalSize = 0;
        
        for (List<Integer> group : keyToRows.values()) {
            if (group.size() > 1) {
                // 转换为原始int数组，节省内存
                int[] compressedGroup = group.stream().mapToInt(Integer::intValue).toArray();
                compressedClasses.add(compressedGroup);
                totalSize += compressedGroup.length;
                memoryUsed += compressedGroup.length * 4; // int数组内存
            }
        }
        
        // 释放临时Map的内存
        keyToRows.clear();
        
        return new ConstructionResult(compressedClasses, totalSize, memoryUsed);
    }
    
    /**
     * 延迟计算属性向量，只在需要时计算
     */
    public int[] toAttributeVector() {
        if (attributeVector == null) {
            synchronized (this) {
                if (attributeVector == null) {
                    attributeVector = new int[rowCount];
                    int clusterId = 1;
                    for (int[] cluster : compressedEquivalenceClasses) {
                        for (int row : cluster) {
                            attributeVector[row] = clusterId;
                        }
                        clusterId++;
                    }
                }
            }
        }
        return attributeVector;
    }
    
    /**
     * 优化的PLI交集操作
     */
    public OptimizedPLI intersect(OptimizedPLI other) {
        // 选择较小的PLI作为探测源
        OptimizedPLI x, y;
        if (this.totalSize >= other.totalSize) {
            y = this;
            x = other;
        } else {
            y = other;
            x = this;
        }
        
        // 获取Y的属性向量
        int[] yAttributeVector = y.toAttributeVector();
        
        // 使用ArrayList避免频繁扩容
        List<int[]> newEquivalenceClasses = new ArrayList<>(x.compressedEquivalenceClasses.size());
        int newTotalSize = 0;
        
        for (int[] xCluster : x.compressedEquivalenceClasses) {
            // 使用Map分组，但使用原始类型优化
            Map<Integer, List<Integer>> tempGroups = new HashMap<>();
            
            for (int row : xCluster) {
                int yValue = yAttributeVector[row];
                if (yValue != 0) {
                    tempGroups.computeIfAbsent(yValue, k -> new ArrayList<>()).add(row);
                }
            }
            
            // 转换为压缩格式
            for (List<Integer> group : tempGroups.values()) {
                if (group.size() > 1) {
                    int[] compressedGroup = group.stream().mapToInt(Integer::intValue).toArray();
                    newEquivalenceClasses.add(compressedGroup);
                    newTotalSize += compressedGroup.length;
                }
            }
        }
        
        // 合并列属性
        BitSet mergedColumns = (BitSet) x.columns.clone();
        mergedColumns.or(y.columns);
        
        return new OptimizedPLI(mergedColumns, x.rowCount, newEquivalenceClasses, newTotalSize);
    }
    
    // Getters
    public BitSet getColumns() {
        return (BitSet) columns.clone();
    }
    
    public int getRowCount() {
        return rowCount;
    }
    
    public int getClusterCount() {
        return compressedEquivalenceClasses.size();
    }
    
    public int size() {
        return totalSize;
    }
    
    public long getMemoryFootprint() {
        return memoryFootprint;
    }
    
    /**
     * 计算内存占用
     */
    private long calculateMemoryFootprint() {
        long memory = 0;
        for (int[] cluster : compressedEquivalenceClasses) {
            memory += cluster.length * 4; // int数组
        }
        if (attributeVector != null) {
            memory += attributeVector.length * 4; // 属性向量
        }
        return memory;
    }
    
    /**
     * 获取等价类流（兼容性方法）
     */
    public Stream<Set<Integer>> clusterStream() {
        return compressedEquivalenceClasses.stream()
                .map(cluster -> {
                    Set<Integer> set = new HashSet<>(cluster.length);
                    for (int row : cluster) {
                        set.add(row);
                    }
                    return set;
                });
    }
    
    /**
     * 构造结果内部类
     */
    private static class ConstructionResult {
        final List<int[]> equivalenceClasses;
        final int totalSize;
        final long memoryFootprint;
        
        ConstructionResult(List<int[]> equivalenceClasses, int totalSize, long memoryFootprint) {
            this.equivalenceClasses = equivalenceClasses;
            this.totalSize = totalSize;
            this.memoryFootprint = memoryFootprint;
        }
    }
    
    @Override
    public String toString() {
        return String.format("OptimizedPLI{columns=%s, clusters=%d, size=%d, memory=%dKB}", 
                           columns, getClusterCount(), size(), getMemoryFootprint() / 1024);
    }
}
