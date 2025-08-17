package algorithm;

import measure.G3Measure;
import model.DataSet;
import model.FunctionalDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import sampling.FocusedSampling;
import utils.DataLoader;
import utils.LongBitSetUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Long优化效果专项测试
 * 验证基于long的重构是否带来显著性能提升
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/30
 */
public class LongOptimizationTest {
    
    private DataSet smallDataSet;
    
    @BeforeEach
    void setUp() {
        // 创建一个小的测试数据集（确保列数≤64）
        try {
            DataLoader loader = DataLoader.fromFile(
                    Path.of("D:/Study/tan/AFD-measures/data/0/classification.csv")
            ).withHeader(true).withDelimiter(';');
            smallDataSet = loader.load();
            
            // 验证列数限制
            if (smallDataSet.getColumnCount() > 64) {
                System.err.println("警告：数据集列数超过64，Long优化可能不适用");
            }
        } catch (Exception e) {
            System.err.println("警告：无法加载测试数据集，将跳过Long优化测试");
        }
    }
    
    @Test
    @DisplayName("Long优化基准测试")
    void testLongOptimizationBenchmark() {
        if (smallDataSet == null) {
            System.out.println("跳过Long优化测试 - 数据集未找到");
            return;
        }
        
        System.out.println("=== Long优化基准测试 ===");
        System.out.printf("数据集信息: %d行, %d列%n", 
                         smallDataSet.getRowCount(), smallDataSet.getColumnCount());
        
        // 清理缓存确保公平测试
        LongBitSetUtils.clearAllCaches();
        
        // 执行优化后的Pyro算法
        long startTime = System.currentTimeMillis();
        
        Pyro pyro = new Pyro(smallDataSet,
                new G3Measure(),
                new FocusedSampling(),
                0.05,
                200,
                true // verbose模式查看详细信息
        );
        
        List<FunctionalDependency> results = pyro.discover();
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        // 输出详细的性能统计
        System.out.printf("执行时间: %d ms%n", executionTime);
        System.out.printf("发现FD数量: %d%n", results.size());
        System.out.printf("验证次数: %d%n", pyro.getValidationCount());
        //System.out.printf("内存峰值: %d MB%n", pyro.getPeakMemoryUsage());
        System.out.printf("Long优化统计: %s%n", LongBitSetUtils.getPerformanceStats());
        
        // 性能断言
        assertTrue(executionTime > 0, "执行时间应该大于0");
        assertTrue(results.size() >= 0, "FD数量应该非负");
        
        // 基于预期的Long优化效果，设置更严格的性能目标
        if (smallDataSet.getColumnCount() <= 10) {
            assertTrue(executionTime < 2000, 
                      String.format("小数据集执行时间 %d ms 应该小于2000ms", executionTime));
        }
        
        System.out.println("Long优化基准测试完成\n");
    }
    
    @Test
    @DisplayName("Long位操作性能测试")
    void testLongBitOperationsPerformance() {
        System.out.println("=== Long位操作性能测试 ===");
        
        // 清理缓存
        LongBitSetUtils.clearAllCaches();
        
        int iterations = 100000;
        long startTime = System.currentTimeMillis();
        
        // 测试各种位操作
        for (int i = 0; i < iterations; i++) {
            long bits1 = (1L << (i % 32));
            long bits2 = (1L << ((i + 1) % 32));
            
            // 测试基本位操作
            LongBitSetUtils.cardinality(bits1);
            LongBitSetUtils.isSubset(bits1, bits2);
            LongBitSetUtils.union(bits1, bits2);
            LongBitSetUtils.intersection(bits1, bits2);
            LongBitSetUtils.setBit(bits1, (i + 2) % 32);
            LongBitSetUtils.clearBit(bits1, i % 32);
            
            // 测试转换操作
            if (i % 1000 == 0) { // 减少转换操作频率
                LongBitSetUtils.longToList(bits1);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long operationTime = endTime - startTime;
        
        System.out.printf("执行 %d 次位操作耗时: %d ms%n", iterations * 6, operationTime);
        System.out.printf("平均每次操作耗时: %.3f μs%n", 
                         (double) operationTime * 1000 / (iterations * 6));
        System.out.printf("操作统计: %s%n", LongBitSetUtils.getPerformanceStats());
        
        // 性能断言
        assertTrue(operationTime < 5000, 
                  String.format("位操作耗时 %d ms 应该小于5000ms", operationTime));
        
        System.out.println("Long位操作性能测试完成\n");
    }
    
    @Test
    @DisplayName("缓存效果验证测试")
    void testCacheEffectiveness() {
        System.out.println("=== 缓存效果验证测试 ===");
        
        // 清理缓存
        LongBitSetUtils.clearAllCaches();
        
        // 创建一些测试数据
        long[] testBits = new long[1000];
        for (int i = 0; i < testBits.length; i++) {
            testBits[i] = (1L << (i % 32)) | (1L << ((i + 1) % 32));
        }
        
        // 第一轮：填充缓存
        long startTime1 = System.currentTimeMillis();
        for (long bits : testBits) {
            LongBitSetUtils.cardinality(bits);
            LongBitSetUtils.longToList(bits);
            LongBitSetUtils.getAllParents(bits);
        }
        long time1 = System.currentTimeMillis() - startTime1;
        
        System.out.printf("第一轮（填充缓存）耗时: %d ms%n", time1);
        System.out.printf("缓存统计: %s%n", LongBitSetUtils.getPerformanceStats());
        
        // 第二轮：利用缓存
        long startTime2 = System.currentTimeMillis();
        for (long bits : testBits) {
            LongBitSetUtils.cardinality(bits);
            LongBitSetUtils.longToList(bits);
            LongBitSetUtils.getAllParents(bits);
        }
        long time2 = System.currentTimeMillis() - startTime2;
        
        System.out.printf("第二轮（利用缓存）耗时: %d ms%n", time2);
        System.out.printf("缓存统计: %s%n", LongBitSetUtils.getPerformanceStats());
        
        // 验证缓存效果
        double speedup = (double) time1 / time2;
        System.out.printf("缓存加速比: %.2fx%n", speedup);
        
        // 缓存应该带来显著的性能提升
        assertTrue(time2 == 0 || speedup > 1.5,
                  String.format("缓存加速比 %.2f 应该大于1.5", speedup));
        
        System.out.println("缓存效果验证测试完成\n");
    }
    
    @Test
    @DisplayName("内存使用优化测试")
    void testMemoryOptimization() {
        if (smallDataSet == null) {
            System.out.println("跳过内存优化测试 - 数据集未找到");
            return;
        }
        
        System.out.println("=== 内存使用优化测试 ===");
        
        Runtime runtime = Runtime.getRuntime();
        
        // 强制垃圾回收
        runtime.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 执行算法
        Pyro pyro = new Pyro(smallDataSet,
                new G3Measure(),
                new FocusedSampling(),
                0.05,
                200,
                false // 关闭verbose减少内存开销
        );
        
        List<FunctionalDependency> results = pyro.discover();
        
        // 再次检查内存
        runtime.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;
        
        System.out.printf("初始内存: %d MB%n", initialMemory / (1024 * 1024));
        System.out.printf("最终内存: %d MB%n", finalMemory / (1024 * 1024));
        System.out.printf("内存增长: %d MB%n", memoryUsed / (1024 * 1024));
        //System.out.printf("算法报告的峰值内存: %d MB%n", pyro.getPeakMemoryUsage());
        System.out.printf("发现FD数量: %d%n", results.size());
        
        // 内存使用应该相对较低
        long memoryMB = memoryUsed / (1024 * 1024);
        assertTrue(memoryMB < 200, 
                  String.format("内存使用 %d MB 应该小于200MB", memoryMB));
        
        System.out.println("内存使用优化测试完成\n");
    }
}
