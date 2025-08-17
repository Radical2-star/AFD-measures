/**
 * 改进的PLICache LRU淘汰机制测试 - 触发清理阈值
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/23
 */
package pli;

import model.DataSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class PLICacheLRUTestV2 {

    public static void main(String[] args) {
        PLICacheLRUTestV2 test = new PLICacheLRUTestV2();
        System.out.println("=== PLICache LRU清理机制测试V2 ===");
        
        test.testLRUCleanupTrigger();
        
        System.out.println("=== 测试完成 ===");
    }
    
    private DataSet createLargeTestDataSet() {
        // 创建12列的数据集，能产生更多PLI组合
        List<String> columnHeaders = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            columnHeaders.add("Col" + i);
        }
        
        DataSet dataSet = new DataSet(columnHeaders);
        
        // 添加足够的测试数据
        for (int row = 0; row < 50; row++) {
            List<String> values = new ArrayList<>();
            for (int col = 0; col < 12; col++) {
                values.add("val" + (row % 5) + "_" + col); // 创建重复模式
            }
            dataSet.addRow(values);
        }
        
        return dataSet;
    }

    public void testLRUCleanupTrigger() {
        System.out.println("\n--- 测试: 触发LRU清理机制 ---");
        
        DataSet dataSet = createLargeTestDataSet();
        PLICache cache = new PLICache(dataSet);
        
        System.out.println("开始大量创建PLI...");
        System.out.println("初始状态: " + cache.getCacheStats());
        
        int createdCount = 0;
        
        // 策略1：创建大量2列组合
        for (int i = 0; i < 12; i++) {
            for (int j = i + 1; j < 12; j++) {
                BitSet columns = new BitSet();
                columns.set(i);
                columns.set(j);
                
                cache.getOrCalculatePLI(columns);
                createdCount++;
                
                if (createdCount % 10 == 0) {
                    System.out.println("创建了 " + createdCount + " 个2列PLI，" + cache.getCacheStats());
                }
            }
        }
        
        // 策略2：创建3列组合
        for (int i = 0; i < 10 && createdCount < 100; i++) {
            for (int j = i + 1; j < 11 && createdCount < 100; j++) {
                for (int k = j + 1; k < 12 && createdCount < 100; k++) {
                    BitSet columns = new BitSet();
                    columns.set(i);
                    columns.set(j);
                    columns.set(k);
                    
                    cache.getOrCalculatePLI(columns);
                    createdCount++;
                    
                    if (createdCount % 10 == 0) {
                        System.out.println("创建了 " + createdCount + " 个PLI（含3列），" + cache.getCacheStats());
                    }
                    
                    // 检查是否触发了清理
                    String stats = cache.getCacheStats();
                    if (stats.contains("大小:") && extractCacheSize(stats) < createdCount + 12) { // 12是初始单列PLI数量
                        System.out.println("⭐ LRU清理机制已触发！缓存大小被控制在合理范围内");
                        System.out.println("最终统计: " + cache.getCacheStats());
                        return;
                    }
                }
            }
        }
        
        System.out.println("\n=== 最终结果 ===");
        System.out.println("总计创建PLI: " + createdCount);
        System.out.println("最终缓存状态: " + cache.getCacheStats());
        
        String finalStats = cache.getCacheStats();
        int finalSize = extractCacheSize(finalStats);
        
        if (finalSize < createdCount + 12) { // 12是单列PLI
            System.out.println("✅ LRU清理机制正常工作，缓存大小得到有效控制");
            System.out.printf("   缓存效率: %.1f%% (保留了 %d/%d 个PLI)\n", 
                             (double)finalSize / (createdCount + 12) * 100, finalSize, createdCount + 12);
        } else {
            System.out.println("⚠️  LRU清理机制可能需要进一步调整");
        }
    }
    
    /**
     * 从统计字符串中提取缓存大小
     */
    private int extractCacheSize(String stats) {
        try {
            String[] parts = stats.split("大小: ");
            if (parts.length > 1) {
                String sizeStr = parts[1].split(",")[0].trim();
                return Integer.parseInt(sizeStr);
            }
        } catch (Exception e) {
            System.out.println("解析缓存大小失败: " + e.getMessage());
        }
        return 0;
    }
}