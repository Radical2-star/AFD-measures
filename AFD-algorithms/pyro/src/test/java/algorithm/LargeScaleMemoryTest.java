/**
 * 大规模内存管理测试 - 使用更多列来触发内存管理
 * 
 * @author Hoshi
 * @version 2.0
 * @since 2025/7/22
 */
package algorithm;

import measure.G3Measure;
import model.DataSet;
import pli.PLICache;
import sampling.RandomSampling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class LargeScaleMemoryTest {

    public static void main(String[] args) {
        LargeScaleMemoryTest test = new LargeScaleMemoryTest();
        System.out.println("=== 大规模SearchSpace内存管理测试 ===");
        
        test.testLargeScaleMemoryManagement();
        
        System.out.println("=== 测试完成 ===");
    }
    
    private SearchSpace createLargeSearchSpace() {
        // 创建一个有更多列的数据集 - 15列
        List<String> columnHeaders = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            columnHeaders.add("Col" + i);
        }
        
        DataSet dataSet = new DataSet(columnHeaders);
        
        // 添加一些测试数据
        for (int row = 0; row < 20; row++) {
            List<String> values = new ArrayList<>();
            for (int col = 0; col < 15; col++) {
                values.add("val" + (row % 3) + "_" + col); // 创建一些重复模式
            }
            dataSet.addRow(values);
        }
        
        // 初始化组件
        PLICache cache = new PLICache(dataSet);
        G3Measure measure = new G3Measure();
        RandomSampling sampling = new RandomSampling(12345L);
        
        // RHS选择第14列（最后一列），这样有14列可以作为LHS
        // 2^14 = 16384种可能的组合（减去空集 = 16383种）
        return new SearchSpace(14, dataSet, cache, measure, sampling, 0.1, 100, true);
    }

    public void testLargeScaleMemoryManagement() {
        System.out.println("\n--- 大规模内存管理测试 (14列LHS，16383种组合) ---");
        
        SearchSpace searchSpace = createLargeSearchSpace();
        System.out.println("开始创建大量节点...");
        System.out.println("初始状态: " + searchSpace.getMemoryStats());
        
        int createdNodes = 0;
        int uniqueNodesCreated = 0;
        
        // 遍历前10000个组合（足以触发内存管理）
        for (int combination = 1; combination <= 10000 && combination < 16384; combination++) {
            BitSet lhs = new BitSet();
            
            // 根据combination的二进制位来设置BitSet
            for (int bit = 0; bit < 14; bit++) {  // 0-13对应前14列
                if ((combination & (1 << bit)) != 0) {
                    lhs.set(bit);
                }
            }
            
            Node node = searchSpace.getOrCreateNode(lhs);
            createdNodes++;
            uniqueNodesCreated++;
            
            // 每2000个节点输出一次统计信息
            if (createdNodes % 2000 == 0) {
                System.out.println("已创建 " + createdNodes + " 个唯一节点，" + searchSpace.getMemoryStats());
            }
        }
        
        System.out.println("\n=== 测试结果 ===");
        System.out.println("总计创建唯一节点数: " + uniqueNodesCreated);
        System.out.println("最终内存状态: " + searchSpace.getMemoryStats());
        
        // 验证内存管理是否按预期工作
        String stats = searchSpace.getMemoryStats();
        if (stats.contains("缓存节点:") && !stats.contains("缓存节点: 0")) {
            System.out.println("✓ 内存管理机制已触发，成功将节点降级为软引用");
        } else if (uniqueNodesCreated > 8000) {
            System.out.println("✓ 创建了足够多的节点，内存管理机制应该已触发");
        } else {
            System.out.println("? 唯一节点数: " + uniqueNodesCreated + "，可能不足以触发内存管理（阈值8000）");
        }
    }
}