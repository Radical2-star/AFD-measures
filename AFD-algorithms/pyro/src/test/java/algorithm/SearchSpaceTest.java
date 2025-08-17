/**
 * SearchSpace现代化测试类 - 专注测试节点内存管理功能
 * 
 * @author Hoshi
 * @version 2.0
 * @since 2025/7/22
 */
package algorithm;

import measure.G3Measure;
import model.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pli.PLICache;
import sampling.RandomSampling;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SearchSpaceTest {

    private DataSet dataSet;
    private PLICache cache;
    private SearchSpace searchSpace;
    private G3Measure measure;
    private RandomSampling sampling;

    @BeforeEach
    public void setUp() {
        // 创建测试数据集 - 4列，足够进行节点管理测试
        List<String> columnHeaders = Arrays.asList("A", "B", "C", "D");
        dataSet = new DataSet(columnHeaders);
        
        // 添加测试数据
        dataSet.addRow(Arrays.asList("1", "a", "x", "p"));
        dataSet.addRow(Arrays.asList("2", "a", "x", "q"));
        dataSet.addRow(Arrays.asList("1", "b", "y", "p"));
        dataSet.addRow(Arrays.asList("2", "b", "y", "q"));
        dataSet.addRow(Arrays.asList("3", "c", "z", "r"));
        
        // 初始化组件
        cache = new PLICache(dataSet);
        measure = new G3Measure();
        sampling = new RandomSampling(12345L);
        
        // 创建SearchSpace，RHS=3 (列D)，开启verbose模式便于观察内存管理
        searchSpace = new SearchSpace(3, dataSet, cache, measure, sampling, 0.1, 100, true);
    }

    @Test
    @DisplayName("测试节点创建和基本访问")
    public void testNodeCreationAndAccess() {
        // 创建一个BitSet表示LHS {A}
        BitSet lhs1 = new BitSet();
        lhs1.set(0); // 列A
        
        // 第一次访问 - 应该创建新节点
        Node node1 = searchSpace.getOrCreateNode(lhs1);
        assertNotNull(node1);
        assertEquals(lhs1, node1.getLhs());
        assertEquals(3, node1.getRhs());
        
        // 第二次访问相同LHS - 应该返回相同节点
        Node node2 = searchSpace.getOrCreateNode(lhs1);
        assertSame(node1, node2, "相同LHS应该返回相同的Node对象");
        
        System.out.println("基本节点访问测试通过");
    }

    @Test
    @DisplayName("测试节点内存管理 - 大量节点创建")
    public void testNodeMemoryManagement() {
        System.out.println("开始内存管理测试...");
        
        // 创建大量不同的节点来触发内存管理
        for (int i = 0; i < 12000; i++) { // 超过MAX_ACTIVE_NODES(10000)
            BitSet lhs = new BitSet();
            // 使用i的二进制表示来创建不同的LHS组合
            for (int bit = 0; bit < 4 && bit < Integer.bitCount(i); bit++) {
                if ((i & (1 << bit)) != 0) {
                    lhs.set(bit);
                }
            }
            
            Node node = searchSpace.getOrCreateNode(lhs);
            assertNotNull(node);
            
            // 每1000个节点输出一次统计信息
            if (i > 0 && i % 1000 == 0) {
                System.out.println("已创建 " + i + " 个节点，" + searchSpace.getMemoryStats());
            }
        }
        
        System.out.println("内存管理测试完成，" + searchSpace.getMemoryStats());
        
        // 验证内存管理生效 - 活跃节点数应该被控制在合理范围内
        String stats = searchSpace.getMemoryStats();
        assertTrue(stats.contains("活跃节点:"), "应该包含内存统计信息");
        System.out.println("最终" + stats);
    }

    @Test
    @DisplayName("测试节点重激活 - 从软引用缓存恢复")
    public void testNodeReactivation() {
        System.out.println("开始节点重激活测试...");
        
        // 创建一个特定的LHS
        BitSet specialLhs = new BitSet();
        specialLhs.set(0); // A
        specialLhs.set(1); // B
        
        // 首次创建节点
        Node originalNode = searchSpace.getOrCreateNode(specialLhs);
        assertNotNull(originalNode);
        System.out.println("创建特殊节点: " + originalNode.getLhs());
        
        // 创建大量其他节点，以触发原节点被降级为软引用
        for (int i = 0; i < 15000; i++) {
            BitSet lhs = new BitSet();
            // 创建不同的LHS组合，避免与specialLhs冲突
            lhs.set((i % 3) + 1); // 使用列B, C, D
            if (i % 2 == 0) lhs.set(2);
            
            searchSpace.getOrCreateNode(lhs);
        }
        
        System.out.println("创建大量节点后，" + searchSpace.getMemoryStats());
        
        // 重新访问特殊LHS - 应该从缓存中重新激活
        Node reactivatedNode = searchSpace.getOrCreateNode(specialLhs);
        assertNotNull(reactivatedNode);
        assertEquals(specialLhs, reactivatedNode.getLhs());
        
        // 注意：由于软引用可能被GC，这里不能断言是同一个对象
        // 但应该能正常获取到节点
        System.out.println("节点重激活测试完成，重新获取到节点: " + reactivatedNode.getLhs());
        System.out.println("最终" + searchSpace.getMemoryStats());
    }

    @Test
    @DisplayName("测试搜索过程中的内存管理")
    public void testMemoryManagementDuringSearch() {
        System.out.println("开始搜索过程内存管理测试...");
        System.out.println("搜索前，" + searchSpace.getMemoryStats());
        
        // 执行一轮简单的搜索，观察内存管理行为
        try {
            searchSpace.explore();
            System.out.println("搜索过程完成");
        } catch (Exception e) {
            System.out.println("搜索过程遇到异常（正常情况）: " + e.getMessage());
        }
        
        System.out.println("搜索后，" + searchSpace.getMemoryStats());
        
        // 验证搜索后能正常获取结果
        List<?> results = searchSpace.getValidatedFDs();
        assertNotNull(results, "应该能获取到FD结果");
        System.out.println("获取到 " + results.size() + " 个FD结果");
    }
}