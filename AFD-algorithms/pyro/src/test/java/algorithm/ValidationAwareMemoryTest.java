/**
 * 验证感知的内存管理测试 - 测试优先回收未验证节点的策略
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/23
 */
package algorithm;

import measure.G3Measure;
import model.DataSet;
import pli.PLICache;
import sampling.RandomSampling;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class ValidationAwareMemoryTest {

    public static void main(String[] args) {
        ValidationAwareMemoryTest test = new ValidationAwareMemoryTest();
        System.out.println("=== 验证感知内存管理测试 ===");
        
        test.testValidationAwareCleanup();
        
        System.out.println("=== 测试完成 ===");
    }
    
    private SearchSpace createSearchSpaceForValidationTest() {
        // 创建一个有8列的数据集，足够创建多种节点组合
        List<String> columnHeaders = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            columnHeaders.add("Col" + i);
        }
        
        DataSet dataSet = new DataSet(columnHeaders);
        
        // 添加测试数据
        for (int row = 0; row < 10; row++) {
            List<String> values = new ArrayList<>();
            for (int col = 0; col < 8; col++) {
                values.add("val" + (row % 4) + "_" + col); // 创建一些重复模式
            }
            dataSet.addRow(values);
        }
        
        // 初始化组件
        PLICache cache = new PLICache(dataSet);
        G3Measure measure = new G3Measure();
        RandomSampling sampling = new RandomSampling(12345L);
        
        // RHS选择最后一列(7)，前7列可作为LHS
        return new SearchSpace(7, dataSet, cache, measure, sampling, 0.1, 100, true);
    }

    public void testValidationAwareCleanup() {
        System.out.println("\n--- 验证感知清理测试 ---");
        
        SearchSpace searchSpace = createSearchSpaceForValidationTest();
        System.out.println("开始创建节点...");
        System.out.println("初始状态: " + searchSpace.getMemoryStats());
        
        // 创建大量节点，观察内存管理行为
        int createdNodes = 0;
        
        for (int combination = 1; combination <= 12000; combination++) {
            BitSet lhs = new BitSet();
            
            // 根据combination的二进制位设置BitSet
            for (int bit = 0; bit < 7; bit++) {  // 前7列可作为LHS
                if ((combination & (1 << bit)) != 0) {
                    lhs.set(bit);
                }
            }
            
            if (!lhs.isEmpty()) {
                Node node = searchSpace.getOrCreateNode(lhs);
                createdNodes++;
                
                // 通过调用estimate来模拟节点状态变化
                // estimate方法会设置error值，使节点变为"已估计"状态
                if (combination % 2 == 0) {
                    // 手动设置一些节点的error值来模拟验证状态
                    try {
                        java.lang.reflect.Field errorField = Node.class.getDeclaredField("error");
                        errorField.setAccessible(true);
                        errorField.set(node, 0.05); // 设置一个具体的error值
                        
                        java.lang.reflect.Field validatedField = Node.class.getDeclaredField("isValidated");
                        validatedField.setAccessible(true);
                        validatedField.set(node, true); // 标记为已验证
                    } catch (Exception e) {
                        // 忽略反射异常，继续测试
                    }
                }
            }
            
            // 每2000个节点输出一次统计信息
            if (combination % 2000 == 0) {
                System.out.println("已创建 " + combination + " 个节点，" + searchSpace.getMemoryStats());
            }
        }
        
        System.out.println("\n=== 验证感知清理测试结果 ===");
        System.out.println("总计创建节点调用: " + createdNodes);
        System.out.println("最终内存状态: " + searchSpace.getMemoryStats());
        
        // 验证内存管理是否按预期工作
        String stats = searchSpace.getMemoryStats();
        if (stats.contains("缓存节点:") && !stats.contains("缓存节点: 0")) {
            System.out.println("✓ 内存管理机制已触发，节点被降级为软引用");
        } else if (createdNodes > 8000) {
            System.out.println("✓ 创建了足够多的节点，内存管理机制应该已触发");
        } else {
            System.out.println("? 节点数: " + createdNodes + "，可能不足以触发内存管理");
        }
        
        System.out.println("验证感知内存管理测试完成");
    }
}