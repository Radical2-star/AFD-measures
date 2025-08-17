/**
 * SearchSpace内存管理简单测试 - 兼容老版本JUnit
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

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class MemoryManagementTest {

    public static void main(String[] args) {
        MemoryManagementTest test = new MemoryManagementTest();
        
        System.out.println("=== SearchSpace内存管理测试 ===");
        
        test.testBasicNodeManagement();
        test.testMemoryManagement();
        test.testNodeReactivation();
        
        System.out.println("=== 测试完成 ===");
    }
    
    private SearchSpace createSearchSpace() {
        // 创建测试数据集
        List<String> columnHeaders = Arrays.asList("A", "B", "C", "D");
        DataSet dataSet = new DataSet(columnHeaders);
        
        // 添加测试数据
        dataSet.addRow(Arrays.asList("1", "a", "x", "p"));
        dataSet.addRow(Arrays.asList("2", "a", "x", "q"));
        dataSet.addRow(Arrays.asList("1", "b", "y", "p"));
        dataSet.addRow(Arrays.asList("2", "b", "y", "q"));
        dataSet.addRow(Arrays.asList("3", "c", "z", "r"));
        
        // 初始化组件
        PLICache cache = new PLICache(dataSet);
        G3Measure measure = new G3Measure();
        RandomSampling sampling = new RandomSampling(12345L);
        
        // 创建SearchSpace，开启verbose模式
        return new SearchSpace(3, dataSet, cache, measure, sampling, 0.1, 100, true);
    }

    public void testBasicNodeManagement() {
        System.out.println("\n--- 测试1: 基本节点管理 ---");
        
        SearchSpace searchSpace = createSearchSpace();
        
        // 创建一个BitSet表示LHS {A}
        BitSet lhs1 = new BitSet();
        lhs1.set(0); // 列A
        
        // 第一次访问 - 应该创建新节点
        Node node1 = searchSpace.getOrCreateNode(lhs1);
        System.out.println("创建节点1: " + node1.getLhs() + " -> " + node1.getRhs());
        
        // 第二次访问相同LHS - 应该返回相同节点
        Node node2 = searchSpace.getOrCreateNode(lhs1);
        System.out.println("再次访问相同LHS，节点相同: " + (node1 == node2));
        
        System.out.println("当前" + searchSpace.getMemoryStats());
        System.out.println("基本节点管理测试完成");
    }

    public void testMemoryManagement() {
        System.out.println("\n--- 测试2: 内存管理机制 ---");
        
        SearchSpace searchSpace = createSearchSpace();
        System.out.println("开始创建大量节点...");
        
        // 创建大量不同的节点来触发内存管理 - 使用更直接的方法
        int createdNodes = 0;
        // 为了创建15个不同节点（4列有15种非空组合），我们重复创建
        for (int round = 0; round < 1000; round++) {  // 多轮创建
            for (int combination = 1; combination < 16; combination++) { // 1-15的组合 (避免0即空集)
                BitSet lhs = new BitSet();
                
                // 根据combination的二进制位来设置BitSet
                if ((combination & 1) != 0) lhs.set(0);   // 第1位对应列A
                if ((combination & 2) != 0) lhs.set(1);   // 第2位对应列B
                if ((combination & 4) != 0) lhs.set(2);   // 第3位对应列C
                if ((combination & 8) != 0) lhs.set(3);   // 第4位对应列D（但这是RHS，应该跳过）
                
                // 如果包含RHS列(3)，就移除它
                if (lhs.get(3)) {
                    lhs.clear(3);
                }
                
                if (!lhs.isEmpty()) { // 确保不是空集
                    Node node = searchSpace.getOrCreateNode(lhs);
                    createdNodes++;
                    
                    // 每1000个节点输出一次统计信息
                    if (createdNodes % 1000 == 0) {
                        System.out.println("已创建 " + createdNodes + " 个节点调用，" + searchSpace.getMemoryStats());
                    }
                    
                    // 如果已经触发了内存管理，就结束测试
                    if (createdNodes > 12000) break;
                }
            }
            if (createdNodes > 12000) break;
        }
        
        System.out.println("内存管理测试完成，总计进行了 " + createdNodes + " 次节点创建调用");
        System.out.println("(注意：由于只有7种可能的LHS组合{A},{B},{C},{A,B},{A,C},{B,C},{A,B,C}，所以实际节点数会少很多)");
        System.out.println(searchSpace.getMemoryStats());
    }

    public void testNodeReactivation() {
        System.out.println("\n--- 测试3: 节点重激活机制 ---");
        
        SearchSpace searchSpace = createSearchSpace();
        
        // 创建一个特定的LHS
        BitSet specialLhs = new BitSet();
        specialLhs.set(0); // A
        specialLhs.set(1); // B
        
        // 首次创建节点
        Node originalNode = searchSpace.getOrCreateNode(specialLhs);
        System.out.println("创建特殊节点: " + originalNode.getLhs());
        System.out.println("创建前，" + searchSpace.getMemoryStats());
        
        // 创建大量其他节点，以触发原节点被降级为软引用
        for (int i = 1; i <= 15000; i++) {
            BitSet lhs = new BitSet();
            // 创建不同的LHS组合，避免与specialLhs冲突
            int colIndex = (i % 3) + 1; // 使用列B(1), C(2), D(3)  
            lhs.set(colIndex);
            if (i % 2 == 0) {
                lhs.set((colIndex + 1) % 4);
            }
            
            searchSpace.getOrCreateNode(lhs);
        }
        
        System.out.println("创建大量节点后，" + searchSpace.getMemoryStats());
        
        // 重新访问特殊LHS - 应该从缓存中重新激活或重新创建
        Node reactivatedNode = searchSpace.getOrCreateNode(specialLhs);
        System.out.println("重新获取到节点: " + reactivatedNode.getLhs());
        System.out.println("节点重激活测试完成，" + searchSpace.getMemoryStats());
    }
}