package algorithm;

import measure.G3Measure;
import model.DataSet;
import model.FunctionalDependency;
import utils.DataLoader;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * TaneAlgorithmNew测试类
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/15
 */
public class TaneAlgorithmNewTest {
    public static void main(String[] args) {
        // 加载测试数据集
        DataLoader loader = DataLoader.fromFile(
                Path.of("data/airport_A.csv")
        ).withHeader(true).withDelimiter(',');
        DataSet dataset = loader.load();
        
        System.out.println("数据集加载完成，行数: " + dataset.getRowCount() + ", 列数: " + dataset.getColumnCount());
        
        // 运行原始Tane算法
        System.out.println("\n===== 运行原始Tane算法 =====");
        TaneAlgorithmOld originalTane = new TaneAlgorithmOld();
        originalTane.run(dataset, 0.05, new G3Measure(), true);
        Set<FunctionalDependency> originalFDs = originalTane.getFDSet();
        
        System.out.println("原始Tane算法找到 " + originalFDs.size() + " 个函数依赖");
        System.out.println("验证次数: " + originalTane.getValidationCount());
        System.out.println("执行时间: " + originalTane.getExecutionTimeMs() + " ms");
        
        // 运行重构版Tane算法
        System.out.println("\n===== 运行重构版Tane算法 =====");
        TaneAlgorithm newTane = new TaneAlgorithm(
                dataset,
                new G3Measure(),
                0.05, // 最大误差阈值
                true  // 输出详细信息
        );
        
        List<FunctionalDependency> newFDs = newTane.discover();
        
        System.out.println("重构版Tane算法找到 " + newFDs.size() + " 个函数依赖");
        System.out.println("验证次数: " + newTane.getValidationCount());
        System.out.println("执行时间: " + newTane.getExecutionTimeMs() + " ms");
        
        // 输出部分结果进行比较
        System.out.println("\n===== 原始Tane算法结果（前10个） =====");
        originalFDs.stream()
                .sorted(Comparator.comparingDouble(FunctionalDependency::getError))
                .limit(10)
                .forEach(System.out::println);
        
        System.out.println("\n===== 重构版Tane算法结果（前10个） =====");
        newFDs.stream()
                .sorted(Comparator.comparingDouble(FunctionalDependency::getError))
                .limit(10)
                .forEach(System.out::println);
        
        // 比较结果差异
        System.out.println("\n===== 结果比较 =====");
        System.out.println("原始Tane算法找到的FD数量: " + originalFDs.size());
        System.out.println("重构版Tane算法找到的FD数量: " + newFDs.size());
        
        // 计算两个结果集的交集大小
        long commonFDs = newFDs.stream().filter(originalFDs::contains).count();
        System.out.println("两个算法共同找到的FD数量: " + commonFDs);
        System.out.println("重构版算法找到但原始算法未找到的FD数量: " + (newFDs.size() - commonFDs));
        System.out.println("原始算法找到但重构版算法未找到的FD数量: " + (originalFDs.size() - commonFDs));
    }
} 