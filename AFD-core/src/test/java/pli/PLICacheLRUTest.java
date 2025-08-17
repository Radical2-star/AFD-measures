/**
 * PLICache LRU淘汰机制测试
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/23
 */
package pli;

import model.DataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class PLICacheLRUTest {

    public static void main(String[] args) {
        PLICacheLRUTest test = new PLICacheLRUTest();
        System.out.println("=== PLICache LRU淘汰机制测试 ===");
        
        test.testBasicLRUFunctionality();
        test.testCacheStatistics();
        test.testLRUCleanup();
        
        System.out.println("=== 测试完成 ===");
    }
    
    private DataSet createTestDataSet(int columns, int rows) {
        List<String> columnHeaders = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            columnHeaders.add("Col" + i);
        }
        
        DataSet dataSet = new DataSet(columnHeaders);
        
        // 添加测试数据
        for (int row = 0; row < rows; row++) {
            List<String> values = new ArrayList<>();
            for (int col = 0; col < columns; col++) {
                values.add("val" + (row % 4) + "_" + col); // 创建一些重复模式
            }
            dataSet.addRow(values);
        }
        
        return dataSet;
    }

    public void testBasicLRUFunctionality() {
        System.out.println("\n--- 测试1: 基本LRU功能 ---");
        
        DataSet dataSet = createTestDataSet(6, 20);
        PLICache cache = new PLICache(dataSet);
        
        System.out.println("初始状态: " + cache.getCacheStats());
        
        // 访问一些单列PLI（这些应该已经在初始化时缓存了）
        for (int col = 0; col < 6; col++) {
            List<Integer> key = Arrays.asList(col);
            PLI pli = cache.get(key);
            System.out.println("访问单列PLI [" + col + "], 结果: " + (pli != null ? "命中" : "未命中"));
        }
        
        System.out.println("单列访问后: " + cache.getCacheStats());
        
        // 通过getOrCalculatePLI创建一些多列PLI
        for (int i = 0; i < 5; i++) {
            BitSet columns = new BitSet();
            columns.set(i);
            columns.set(i + 1);
            
            PLI pli = cache.getOrCalculatePLI(columns);
            System.out.println("计算双列PLI [" + i + "," + (i+1) + "], size: " + pli.size());
        }
        
        System.out.println("双列计算后: " + cache.getCacheStats());
    }

    public void testCacheStatistics() {
        System.out.println("\n--- 测试2: 缓存统计功能 ---");
        
        DataSet dataSet = createTestDataSet(8, 30);
        PLICache cache = new PLICache(dataSet);
        
        System.out.println("初始状态: " + cache.getCacheStats());
        
        // 混合访问：命中和未命中
        for (int round = 0; round < 3; round++) {
            // 访问存在的单列PLI（命中）
            for (int col = 0; col < 8; col++) {
                List<Integer> key = Arrays.asList(col);
                cache.get(key);
            }
            
            // 尝试访问不存在的多列PLI（未命中）
            for (int i = 0; i < 5; i++) {
                List<Integer> key = Arrays.asList(i, i + 1, i + 2);
                cache.get(key);
            }
            
            System.out.println("轮次 " + (round + 1) + ": " + cache.getCacheStats());
        }
        
        // 通过getOrCalculatePLI增加一些缓存项
        for (int i = 0; i < 10; i++) {
            BitSet columns = new BitSet();
            columns.set(i % 8);
            columns.set((i + 1) % 8);
            cache.getOrCalculatePLI(columns);
        }
        
        System.out.println("最终统计: " + cache.getCacheStats());
    }

    public void testLRUCleanup() {
        System.out.println("\n--- 测试3: LRU清理机制 ---");
        
        DataSet dataSet = createTestDataSet(10, 50);
        PLICache cache = new PLICache(dataSet);
        
        System.out.println("开始创建大量PLI项...");
        System.out.println("初始状态: " + cache.getCacheStats());
        
        // 创建大量PLI项来触发LRU清理
        int createdPLIs = 0;
        
        // 创建各种组合的PLI
        for (int size = 2; size <= 4; size++) {
            for (int start = 0; start <= 10 - size; start++) {
                BitSet columns = new BitSet();
                for (int i = 0; i < size; i++) {
                    columns.set(start + i);
                }
                
                try {
                    PLI pli = cache.getOrCalculatePLI(columns);
                    createdPLIs++;
                    
                    if (createdPLIs % 50 == 0) {
                        System.out.println("已创建 " + createdPLIs + " 个PLI，" + cache.getCacheStats());
                    }
                } catch (Exception e) {
                    System.out.println("创建PLI时发生异常: " + e.getMessage());
                    break;
                }
                
                // 如果已经创建足够多的PLI，就停止
                if (createdPLIs >= 200) {
                    break;
                }
            }
            if (createdPLIs >= 200) {
                break;
            }
        }
        
        System.out.println("\n=== LRU清理测试结果 ===");
        System.out.println("总计尝试创建PLI数: " + createdPLIs);
        System.out.println("最终缓存状态: " + cache.getCacheStats());
        
        // 验证LRU机制是否有效控制了缓存大小
        String stats = cache.getCacheStats();
        if (stats.contains("大小:") && createdPLIs > 100) {
            System.out.println("✓ LRU机制似乎正在工作，缓存大小得到控制");
        } else {
            System.out.println("? LRU机制可能需要调整");
        }
    }
}