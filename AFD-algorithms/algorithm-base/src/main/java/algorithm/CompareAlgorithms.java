package algorithm;

import measure.G3Measure;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLICache;
import utils.BitSetUtils;
import utils.DataLoader;

import java.nio.file.Path;
import java.util.*;

/**
 * 比较Pyro和TaneAlgorithm算法的结果差异
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/5/10
 */
public class CompareAlgorithms {
    private static final String DATA_PATH = "data/test_new.csv";
    private static final double ERROR_THRESHOLD = 0.05;
    
    public static void main(String[] args) {
        // 加载数据集
        DataLoader loader = DataLoader.fromFile(
                Path.of(DATA_PATH)
        ).withHeader(true).withDelimiter(',');
        DataSet dataset = loader.load();
        
        System.out.println("===== 数据集信息 =====");
        System.out.println("数据集路径: " + DATA_PATH);
        System.out.println("行数: " + dataset.getRowCount());
        System.out.println("列数: " + dataset.getColumnCount());
        
        // 获取列名列表
        List<String> columnNames = new ArrayList<>();
        for (int i = 0; i < dataset.getColumnCount(); i++) {
            columnNames.add(dataset.getColumnName(i));
        }
        System.out.println("列名: " + String.join(", ", columnNames));
        System.out.println();
        
        // 运行Pyro算法
        System.out.println("===== 运行Pyro算法 =====");
        long pyroStartTime = System.currentTimeMillis();
        Set<FunctionalDependency> pyroResults = runPyro(dataset);
        long pyroEndTime = System.currentTimeMillis();
        System.out.println("Pyro运行时间: " + (pyroEndTime - pyroStartTime) + "ms");
        System.out.println("Pyro找到的函数依赖数量: " + pyroResults.size());
        printTopFDs(pyroResults, 10);
        System.out.println();
        
        // 运行TANE算法
        System.out.println("===== 运行TANE算法 =====");
        long taneStartTime = System.currentTimeMillis();
        Set<FunctionalDependency> taneResults = runTane();
        long taneEndTime = System.currentTimeMillis();
        System.out.println("TANE运行时间: " + (taneEndTime - taneStartTime) + "ms");
        System.out.println("TANE找到的函数依赖数量: " + taneResults.size());
        printTopFDs(taneResults, 10);
        System.out.println();
        
        // 比较结果
        compareResults(pyroResults, taneResults);

        // 找出Pyro独有的函数依赖
        Set<FunctionalDependency> pyroOnlyFDs = new HashSet<>(pyroResults);
        pyroOnlyFDs.removeAll(taneResults);

        System.out.println("Pyro独有的函数依赖数量: " + pyroOnlyFDs.size());

        // 找出TANE独有的函数依赖
        Set<FunctionalDependency> taneOnlyFDs = new HashSet<>(taneResults);
        taneOnlyFDs.removeAll(pyroResults);

        System.out.println("TANE独有的函数依赖数量: " + taneOnlyFDs.size());

        // 验证Pyro独有的函数依赖是否为最小依赖
        System.out.println("\n===== 验证Pyro独有的函数依赖是否为最小依赖 =====");
        verifyMinimalFDs(pyroOnlyFDs, pyroResults);

        // 验证TANE独有的函数依赖是否为最小依赖
        System.out.println("\n===== 验证TANE独有的函数依赖是否为最小依赖 =====");
        verifyMinimalFDs(taneOnlyFDs, taneResults);

        // 使用G3Measure验证Pyro独有的函数依赖的误差
        System.out.println("\n===== 使用G3Measure验证Pyro独有的函数依赖的误差 =====");
        verifyErrorWithG3(pyroOnlyFDs, dataset);

        // 使用G3Measure验证TANE独有的函数依赖的误差
        System.out.println("\n===== 使用G3Measure验证TANE独有的函数依赖的误差 =====");
        verifyErrorWithG3(taneOnlyFDs, dataset);
    }
    
    /**
     * 运行Pyro算法
     * @param dataset 数据集
     * @return 函数依赖列表
     */
    private static Set<FunctionalDependency> runPyro(DataSet dataset) {
        // 创建Pyro算法实例并运行
        Pyro pyro = new Pyro(dataset, 
                new PyroConfig(
                        new measure.G3Measure(),
                        new sampling.RandomSampling(),
                        ERROR_THRESHOLD
                ));
        return new HashSet<>(pyro.discover());
    }
    
    /**
     * 运行TANE算法
     * @return 函数依赖集合
     */
    private static Set<FunctionalDependency> runTane() {
        // 创建TANE算法实例并运行
        TaneAlgorithm tane = new TaneAlgorithm();
        try {
            tane.loadData(DATA_PATH);
            tane.run(ERROR_THRESHOLD);
            return tane.getFDSet();
        } catch (Exception e) {
            System.err.println("TANE算法运行错误: " + e.getMessage());
            e.printStackTrace();
            return new HashSet<>();
        }
    }
    
    /**
     * 打印前N个函数依赖
     * @param fdSet 函数依赖集合
     * @param n 打印数量
     */
    private static void printTopFDs(Set<FunctionalDependency> fdSet, int n) {
        List<FunctionalDependency> sortedFDs = new ArrayList<>(fdSet);
        sortedFDs.sort(Comparator.comparingDouble(FunctionalDependency::getError));
        
        System.out.println("===== 前" + Math.min(n, sortedFDs.size()) + "个函数依赖 =====");
        for (int i = 0; i < Math.min(n, sortedFDs.size()); i++) {
            System.out.println(sortedFDs.get(i));
        }
    }
    
    /**
     * 比较两个算法的结果
     * @param pyroResults Pyro算法结果
     * @param taneResults TANE算法结果
     */
    private static void compareResults(Set<FunctionalDependency> pyroResults, Set<FunctionalDependency> taneResults) {
        System.out.println("===== 结果比较 =====");
        
        // 找出共同的函数依赖
        Set<FunctionalDependency> commonFDs = new HashSet<>(pyroResults);
        commonFDs.retainAll(taneResults);
        
        // 找出Pyro独有的函数依赖
        Set<FunctionalDependency> pyroOnlyFDs = new HashSet<>(pyroResults);
        pyroOnlyFDs.removeAll(taneResults);
        
        // 找出TANE独有的函数依赖
        Set<FunctionalDependency> taneOnlyFDs = new HashSet<>(taneResults);
        taneOnlyFDs.removeAll(pyroResults);
        
        System.out.println("共同的函数依赖数量: " + commonFDs.size());
        System.out.println("Pyro独有的函数依赖数量: " + pyroOnlyFDs.size());
        System.out.println("TANE独有的函数依赖数量: " + taneOnlyFDs.size());
        
        // 分析误差差异
        if (!commonFDs.isEmpty()) {
            System.out.println("\n===== 共同函数依赖的误差分析 =====");
            Map<String, Double> pyroErrorMap = createErrorMap(pyroResults);
            Map<String, Double> taneErrorMap = createErrorMap(taneResults);
            
            double totalErrorDiff = 0.0;
            double maxErrorDiff = 0.0;
            FunctionalDependency maxDiffFD = null;
            
            for (FunctionalDependency fd : commonFDs) {
                String fdKey = fdToString(fd);
                double pyroError = pyroErrorMap.get(fdKey);
                double taneError = taneErrorMap.get(fdKey);
                double errorDiff = Math.abs(pyroError - taneError);
                
                totalErrorDiff += errorDiff;
                if (errorDiff > maxErrorDiff) {
                    maxErrorDiff = errorDiff;
                    maxDiffFD = fd;
                }
            }
            
            System.out.println("共同函数依赖的平均误差差异: " + (totalErrorDiff / commonFDs.size()));
            if (maxDiffFD != null) {
                System.out.println("最大误差差异的函数依赖: " + maxDiffFD);
                System.out.println("Pyro误差: " + pyroErrorMap.get(fdToString(maxDiffFD)));
                System.out.println("TANE误差: " + taneErrorMap.get(fdToString(maxDiffFD)));
            }
        }
        
        // 输出一些独有的函数依赖示例
        if (!pyroOnlyFDs.isEmpty()) {
            System.out.println("\n===== Pyro独有的函数依赖示例 =====");
            printTopFDs(pyroOnlyFDs, 5);
        }
        
        if (!taneOnlyFDs.isEmpty()) {
            System.out.println("\n===== TANE独有的函数依赖示例 =====");
            printTopFDs(taneOnlyFDs, 5);
        }
    }

    /**
     * 验证函数依赖是否为最小依赖
     * @param fdsToVerify 需要验证的函数依赖集合
     * @param allFDs 所有函数依赖集合
     */
    private static void verifyMinimalFDs(Set<FunctionalDependency> fdsToVerify, Set<FunctionalDependency> allFDs) {
        // 对每个待验证的函数依赖
        for (FunctionalDependency fd : fdsToVerify) {
            boolean isMinimal = true;
            int rhs = fd.getRhs();
            Set<Integer> lhs = fd.getLhs();

            // 检查是否存在更小的函数依赖（相同的rhs，但lhs是当前lhs的子集）
            for (FunctionalDependency otherFd : allFDs) {
                if (otherFd.getRhs() == rhs &&
                        otherFd.getLhs().size() < lhs.size() &&
                        lhs.containsAll(otherFd.getLhs())) {
                    isMinimal = false;
                    System.out.println("函数依赖 " + fd + " 不是最小的，被 " + otherFd + " 包含");
                    break;
                }
            }

            if (isMinimal) {
                System.out.println("函数依赖 " + fd + " 是最小的");
            }
        }
    }

    /**
     * 使用G3Measure验证函数依赖的误差
     * @param fdsToVerify 需要验证的函数依赖集合
     * @param dataset 数据集
     */
    private static void verifyErrorWithG3(Set<FunctionalDependency> fdsToVerify, DataSet dataset) {
        G3Measure g3Measure = new G3Measure();
        PLICache pliCache = PLICache.getInstance(dataset);

        // 对每个待验证的函数依赖
        for (FunctionalDependency fd : fdsToVerify) {
            BitSet lhsBitSet = BitSetUtils.setToBitSet(fd.getLhs());
            int rhs = fd.getRhs();

            // 使用G3Measure计算误差
            double error = g3Measure.calculateError(lhsBitSet, rhs, dataset, pliCache);

            System.out.println("函数依赖 " + fd + " 使用G3Measure计算的误差为: " + error);

            // 判断误差是否为0
            if (Math.abs(error) < 1e-10) {
                System.out.println("  - 误差为0，Pyro应该能够发现这个依赖");
            } else {
                System.out.println("  - 误差不为0，可能是G3Measure计算结果与TANE不一致");
            }
        }
    }

    /**
     * 创建函数依赖与误差的映射表
     * @param fdSet 函数依赖集合
     * @return 函数依赖字符串与误差的映射
     */
    private static Map<String, Double> createErrorMap(Set<FunctionalDependency> fdSet) {
        Map<String, Double> errorMap = new HashMap<>();
        for (FunctionalDependency fd : fdSet) {
            errorMap.put(fdToString(fd), fd.getError());
        }
        return errorMap;
    }
    
    /**
     * 将函数依赖转换为字符串表示（用于比较）
     * @param fd 函数依赖
     * @return 字符串表示
     */
    private static String fdToString(FunctionalDependency fd) {
        return fd.getLhs() + "->" + fd.getRhs();
    }
} 