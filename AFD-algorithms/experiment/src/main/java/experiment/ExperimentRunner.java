package experiment;

import experiment.finder.AFDFinder;
import experiment.finder.PyroExecutor;
import experiment.finder.TaneExecutor;
import measure.ErrorMeasure;
import measure.G3Measure;
import model.DataSet;
import sampling.FocusedSampling;
import sampling.NeymanSampling;
import sampling.RandomSampling;
import utils.DataLoader;
import utils.MemoryMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import sampling.SamplingStrategy;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class ExperimentRunner {
    private static final Logger logger = LoggerFactory.getLogger(ExperimentRunner.class);

    // 算法类型枚举
    public enum AlgorithmType {
        PYRO,       // 执行Pyro算法
        TANE        // 执行TANE算法
    }

    // 采样方法选择枚举
    public enum SamplingMode {
        NO_SAMPLING,    // 不采样
        RANDOM,         // 随机采样
        FOCUSED,        // 聚焦采样
        NEYMAN          // Neyman采样
    }

    // === 单一实验执行参数 ===
    // 这些参数将通过命令行参数传入，而非硬编码
    private static String datasetPath;
    private static String resultsFile;
    private static AlgorithmType algorithmType;
    private static SamplingMode samplingMode;
    private static ResultManager.RunMode runMode;
    private static double maxError = 0.05;
    private static double sampleParam = 200;
    private static Long randomSeed = 114514L;
    private static boolean useDynamicThreshold = false;
    private static boolean verbose = false;
    private static long timeoutMinutes = 120;
    private static final ErrorMeasure MEASURE = new G3Measure();

    public static void main(String[] args) {
        try {
            // 1. 解析命令行参数
        parseArgs(args);

            // 2. 验证必需参数
            validateRequiredParameters();
            
            // 3. 配置内存优化
        configureMemoryOptimization();

            // 4. 执行单个实验
            ExperimentResult result = executeSingleExperiment();
            
            // 5. 保存结果
            saveResult(result);
            
            System.out.println("\n============================================================");
            System.out.println("单一实验执行完成");
            System.out.println("数据集: " + datasetPath);
            System.out.println("算法: " + algorithmType);
            System.out.println("采样模式: " + samplingMode);
            System.out.println("结果文件: " + resultsFile);
            System.out.println("============================================================");
            
                        } catch (Exception e) {
            logger.error("实验执行失败", e);
            System.err.println("实验执行失败: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * 解析命令行参数并更新配置
     * @param args 命令行参数
     */
    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--") && i + 1 < args.length) {
                String value = args[i + 1];
                i++; // Consume value
                switch (arg) {
                    case "--dataset":
                        datasetPath = value;
                        break;
                    case "--results-file":
                        resultsFile = value;
                        break;
                    case "--algorithm":
                        algorithmType = AlgorithmType.valueOf(value.toUpperCase());
                        break;
                    case "--sampling-mode":
                        samplingMode = SamplingMode.valueOf(value.toUpperCase());
                        break;
                    case "--run-mode":
                        runMode = ResultManager.RunMode.valueOf(value.toUpperCase());
                        break;
                    case "--max-error":
                        maxError = Double.parseDouble(value);
                        break;
                    case "--sample-param":
                        sampleParam = Double.parseDouble(value);
                        break;
                    case "--timeout":
                        timeoutMinutes = Long.parseLong(value);
                        break;
                    case "--seed":
                        randomSeed = Long.parseLong(value);
                        break;
                    case "--use-dynamic-threshold":
                        useDynamicThreshold = Boolean.parseBoolean(value);
                        break;
                    case "--verbose":
                        verbose = Boolean.parseBoolean(value);
                        break;
                    default:
                        System.err.println("警告: 未知参数 " + arg);
                        break;
                }
            }
        }
    }
    
    /**
     * 验证必需参数
     */
    private static void validateRequiredParameters() {
        if (datasetPath == null || datasetPath.isEmpty()) {
            throw new IllegalArgumentException("必需参数: --dataset");
        }
        if (resultsFile == null || resultsFile.isEmpty()) {
            throw new IllegalArgumentException("必需参数: --results-file");
        }
        if (algorithmType == null) {
            throw new IllegalArgumentException("必需参数: --algorithm (PYRO|TANE)");
        }
        if (samplingMode == null) {
            throw new IllegalArgumentException("必需参数: --sampling-mode (NO_SAMPLING|RANDOM|FOCUSED|NEYMAN)");
        }
        if (runMode == null) {
            throw new IllegalArgumentException("必需参数: --run-mode (APPEND|OVERWRITE)");
        }
        
        // 验证文件存在性
        File datasetFile = new File(datasetPath);
        if (!datasetFile.exists() || !datasetFile.isFile()) {
            throw new IllegalArgumentException("数据集文件不存在: " + datasetPath);
        }
        
        // TANE算法不支持采样，验证参数组合
        if (algorithmType == AlgorithmType.TANE && samplingMode != SamplingMode.NO_SAMPLING) {
            System.out.println("警告: TANE算法不支持采样，将强制使用NO_SAMPLING模式");
            samplingMode = SamplingMode.NO_SAMPLING;
        }
    }
    
    /**
     * 执行单个实验
     */
    private static ExperimentResult executeSingleExperiment() throws Exception {
        System.out.println("============================================================");
        System.out.println("开始执行单一实验");
        System.out.println("数据集: " + datasetPath);
        System.out.println("算法: " + algorithmType);
        System.out.println("采样模式: " + samplingMode);
        System.out.println("最大错误率: " + maxError);
        System.out.println("采样参数: " + sampleParam);
        System.out.println("随机种子: " + randomSeed);
        System.out.println("超时时间: " + timeoutMinutes + " 分钟");
        System.out.println("============================================================");
        
        // 1. 加载数据集
        DataSet dataSet = loadDataset();
        
        // 2. 创建实验配置
        ExperimentConfig config = createExperimentConfig();
        
        // 3. 选择并执行算法
        AFDFinder algorithm = createAlgorithm();
        
        // 4. 启动内存监控
        MemoryMonitor memoryMonitor = startMemoryMonitoring();
        
        try {
            // 5. 执行算法（带超时）
            ExperimentResult result = executeWithTimeout(algorithm, dataSet, config);
            
            // 6. 输出执行统计
            printExecutionStats(result);
            
            return result;
            
        } finally {
            // 7. 停止内存监控
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
        }
    }
    
    /**
     * 加载数据集
     */
    private static DataSet loadDataset() throws Exception {
        System.out.println("正在加载数据集: " + datasetPath);
        
        // 检测分隔符
        char delimiter = detectDelimiter(datasetPath);
        System.out.println("检测到分隔符: " + (delimiter == ',' ? "逗号" : delimiter == ';' ? "分号" : delimiter == '\t' ? "制表符" : delimiter));
        
        // 获取数据集信息
        int columns = getColumnCount(datasetPath);
        int rows = getRowCount(datasetPath);
        System.out.println("数据集规模: " + columns + "列 × " + rows + "行 = " + ((long) columns * rows) + " 个数据点");
        
        // 加载数据集
        DataSet dataSet = DataLoader.fromFile(Path.of(datasetPath))
                .withHeader(true)
                .withDelimiter(delimiter)
                .load();
        
        System.out.println("数据集加载完成");
        return dataSet;
    }
    
    /**
     * 创建实验配置
     */
    private static ExperimentConfig createExperimentConfig() {
        Class<? extends SamplingStrategy> samplingClass = null;
        String configName = "G3-No-Sampling";
        
        // 根据采样模式选择采样策略类
        switch (samplingMode) {
            case RANDOM:
                samplingClass = RandomSampling.class;
                configName = "G3-Random-Sampling";
                break;
            case FOCUSED:
                samplingClass = FocusedSampling.class;
                configName = "G3-Focused-Sampling";
                break;
            case NEYMAN:
                samplingClass = NeymanSampling.class;
                configName = "G3-Neyman-Sampling";
                break;
            case NO_SAMPLING:
            default:
                // 保持默认值
                break;
        }
        
        return new ExperimentConfig(
                configName,
                MEASURE,
                samplingClass,
                maxError,
                sampleParam,
                randomSeed,
                useDynamicThreshold,
                verbose
        );
    }
    
    /**
     * 创建算法实例
     */
    private static AFDFinder createAlgorithm() {
        switch (algorithmType) {
            case PYRO:
                return new PyroExecutor();
            case TANE:
                return new TaneExecutor();
            default:
                throw new IllegalArgumentException("未知的算法类型: " + algorithmType);
        }
    }
    
    /**
     * 启动内存监控
     */
    private static MemoryMonitor startMemoryMonitoring() {
        MemoryMonitor memoryMonitor = MemoryMonitor.getInstance();
        memoryMonitor.startMonitoring(10000, alert -> {
            if (alert.getLevel() == MemoryMonitor.AlertLevel.CRITICAL) {
                logger.error("检测到严重内存压力: {}", alert);
                System.err.println("⚠️ 严重内存压力: " + alert);
            } else if (alert.getLevel() == MemoryMonitor.AlertLevel.WARNING) {
                logger.warn("检测到内存压力: {}", alert);
                System.out.println("⚠️ 内存压力警告: " + alert);
            }
        });
        
        // 显示当前内存状态
        MemoryMonitor.MemorySnapshot snapshot = memoryMonitor.getCurrentMemorySnapshot();
        System.out.println("当前内存使用: " + snapshot.getUsedHeapMemory() / (1024 * 1024) + "MB / " +
                         snapshot.getMaxHeapMemory() / (1024 * 1024) + "MB (" +
                         String.format("%.1f%%", snapshot.getUsageRatio() * 100) + ")");
        
        return memoryMonitor;
    }
    
    /**
     * 带超时执行算法
     */
    private static ExperimentResult executeWithTimeout(AFDFinder algorithm, DataSet dataSet, ExperimentConfig config) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        try {
            Future<ExperimentResult> future = executor.submit(() -> algorithm.discover(dataSet, config));
            return future.get(timeoutMinutes, TimeUnit.MINUTES);
            
        } catch (TimeoutException e) {
            throw new Exception("算法执行超时 (" + timeoutMinutes + " 分钟)");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            } else {
                throw new Exception("算法执行失败", cause);
            }
        } finally {
            executor.shutdownNow();
        }
    }
    
    /**
     * 输出执行统计信息
     */
    private static void printExecutionStats(ExperimentResult result) {
        System.out.println("\n实验执行完成:");
        System.out.println("  发现的函数依赖数量: " + result.getFds().size());
        System.out.println("  执行时间: " + result.getExecutionTimeMs() + " 毫秒");
        System.out.println("  验证次数: " + result.getValidationCount());
        
        if (result.getPeakMemoryUsageMB() > 0) {
            System.out.println("  内存峰值: " + result.getPeakMemoryUsageMB() + "MB");
        }
        if (!result.getPliPerformanceStats().isEmpty()) {
            System.out.println("  PLI统计: " + result.getPliPerformanceStats());
        }
        if (!result.getMemoryStats().isEmpty()) {
            System.out.println("  内存统计: " + result.getMemoryStats());
        }
    }
    
    /**
     * 保存实验结果
     */
    private static void saveResult(ExperimentResult result) throws Exception {
        System.out.println("\n正在保存实验结果到: " + resultsFile);
        
        // 获取数据集信息用于结果记录
        int columns = getColumnCount(datasetPath);
        int rows = getRowCount(datasetPath);
        
        // 创建结果管理器并保存结果
        ResultManager resultManager = new ResultManager(resultsFile, runMode);
        
        ExperimentConfig config = createExperimentConfig();
        resultManager.writeResult(
                datasetPath,
                config.getConfigName(),
                algorithmType.toString(),
                result,
                columns,
                rows
        );
        
        System.out.println("实验结果保存完成");
    }
    
    /**
     * 配置内存优化参数
     */
    private static void configureMemoryOptimization() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);

        // 根据可用内存动态配置PLI缓存限制
        if (maxMemoryMB >= 8192) { // 8GB+
            System.setProperty("pli.cache.max.memory.mb", "2048");
            System.setProperty("pli.cache.cleanup.threshold.mb", "1600");
        } else if (maxMemoryMB >= 4096) { // 4GB+
            System.setProperty("pli.cache.max.memory.mb", "1024");
            System.setProperty("pli.cache.cleanup.threshold.mb", "800");
        } else if (maxMemoryMB >= 2048) { // 2GB+
            System.setProperty("pli.cache.max.memory.mb", "512");
            System.setProperty("pli.cache.cleanup.threshold.mb", "400");
        } else { // <2GB
            System.setProperty("pli.cache.max.memory.mb", "256");
            System.setProperty("pli.cache.cleanup.threshold.mb", "200");
        }

        // 配置内存监控参数
        System.setProperty("memory.monitor.warning.threshold", "0.7");
        System.setProperty("memory.monitor.critical.threshold", "0.85");

        logger.info("内存优化配置完成，最大堆内存: {}MB", maxMemoryMB);
    }
    
    /**
     * 获取CSV文件的行数
     * @param filePath CSV文件路径
     * @return 行数（不包括表头）
     * @throws IOException 如果读取文件失败
     */
    private static int getRowCount(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            // 跳过表头
            reader.readLine();
            
            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        }
    }
    
    /**
     * 检测CSV文件使用的分隔符
     * @param filePath CSV文件路径
     * @return 检测到的分隔符（','或';'或'\t'）
     * @throws IOException 如果读取文件失败
     */
    private static char detectDelimiter(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return ','; // 默认使用逗号
            }
            
            int commas = countOccurrences(firstLine, ',');
            int semicolons = countOccurrences(firstLine, ';');
            int tabs = countOccurrences(firstLine, '\t');
            
            // 使用出现次数最多的分隔符
            if (semicolons > commas && semicolons > tabs) {
                return ';';
            } else if (tabs > commas && tabs > semicolons) {
                return '\t';
            } else {
                return ','; // 默认使用逗号或当逗号数量最多时
            }
        }
    }
    
    /**
     * 计算字符在字符串中出现的次数
     * @param str 要检查的字符串
     * @param ch 要计数的字符
     * @return 出现次数
     */
    private static int countOccurrences(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取CSV文件的列数（通过读取第一行并使用检测到的分隔符）
     * @param filePath CSV文件路径
     * @return 列数
     * @throws IOException 如果读取文件失败
     */
    private static int getColumnCount(String filePath) throws IOException {
        char delimiter = detectDelimiter(filePath);
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return 0;
            }
            // 使用检测到的分隔符拆分
            // 注意：当分隔符是正则表达式中的特殊字符时需要转义
            String regex = String.valueOf(delimiter);
            if (delimiter == '|' || delimiter == '.' || delimiter == '\\' || 
                delimiter == '[' || delimiter == ']' || delimiter == '^' || 
                delimiter == '$' || delimiter == '?' || delimiter == '*' || 
                delimiter == '+' || delimiter == '(' || delimiter == ')') {
                regex = "\\\\" + delimiter;
            }
            return firstLine.split(regex, -1).length;
        }
    }
    

}
