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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class ExperimentRunner {
    private static final Logger logger = LoggerFactory.getLogger(ExperimentRunner.class);

    // 采样方法选择枚举
    public enum SamplingMode {
        ALL,            // 运行所有采样方法
        NO_SAMPLING,    // 只运行不采样方法
        RANDOM,         // 只运行随机采样方法
        FOCUSED,        // 只运行聚焦采样方法
        NEYMAN          // 只运行Neyman采样方法
    }

    // --- 可配置的实验参数 (增加了默认值) ---
    public static double MAX_ERROR = 0.05;
    public static double SAMPLE_PARAM = 200;
    public static Long RANDOM_SEED = 114514L; // 可以设置为null表示纯随机
    public static boolean USE_DYNAMIC_THRESHOLD = false;
    public static boolean VERBOSE = false;
    public static final ErrorMeasure MEASURE = new G3Measure();
    
    // 数据集配置 (通过命令行参数 --dataset 指定)
    public static String DATASET_PATH = "data/0"; // 默认数据集路径，可以是文件或目录
    // public static boolean USE_DIRECTORY_MODE = true; // true: 使用目录模式, false: 使用硬编码列表模式
    
    // 硬编码列表模式：指定数据集列表
    // public static List<String> DATASET_PATHS = List.of(
    //     "data/0/bio_entry.csv",
    //     "data/0/classification.csv",
    //     "data/abalone.csv",
    //     "data/atom_new.csv",
    //     "data/test_new.csv",
    //     "data/airport.csv",
    //      "data/1_CLASSIFICATION_new.csv",
    //     "data/CENSUS_50000_new.csv"
    //     // "data/hepa.csv",
    //     // "data/ncvoter.csv"
    // );

    // 实验控制配置
    public static boolean RUN_TANE = true; // 是否运行TANE算法
    public static SamplingMode SAMPLING_MODE = SamplingMode.ALL; // 采样方法选择
    
    public static String RESULTS_FILE = "result/result0721.csv";
    public static ResultManager.RunMode RUN_MODE = ResultManager.RunMode.APPEND;
    // 可以设置为APPEND或OVERWRITE
    public static boolean USE_TIME_SUFFIX = false; // 是否在结果文件名后添加时间后缀
    public static long ALGORITHM_TIMEOUT_MINUTES = 120; // 每个算法执行的超时时间，单位：分钟

    public static void main(String[] args) {
        // --- 1. 解析命令行参数 ---
        parseArgs(args);

        // --- 2. 根据模式获取数据集列表 ---
        List<String> datasetPaths;
        File datasetFileOrDir = new File(DATASET_PATH);

        if (datasetFileOrDir.isDirectory()) {
            try {
                datasetPaths = getDatasetPaths(DATASET_PATH);
                System.out.println("目录模式：找到 " + datasetPaths.size() + " 个数据集文件（按CSV列数从少到多排序）:");
                // 打印文件名、列数和检测到的分隔符
                for (String path : datasetPaths) {
                    char delimiter = detectDelimiter(path);
                    int columns = getColumnCount(path);
                    int rows = getRowCount(path);
                    System.out.println(path + " (列数: " + columns + ", 行数: " + rows +
                            ", 分隔符: " + (delimiter == ',' ? "逗号" : delimiter == ';' ? "分号" : delimiter) + ")");
                }
            } catch (IOException e) {
                System.err.println("读取数据集目录失败: " + e.getMessage());
                return;
            }

            if (datasetPaths.isEmpty()) {
                System.err.println("警告: 在 " + DATASET_PATH + " 目录下没有找到任何文件，请检查路径是否正确。");
                return;
            }
        } else if (datasetFileOrDir.isFile()) {
            datasetPaths = new ArrayList<>(List.of(DATASET_PATH));
            System.out.println("文件模式：使用 " + datasetPaths.size() + " 个预定义数据集:");
            for (String path : datasetPaths) {
                try {
                    char delimiter = detectDelimiter(path);
                    int columns = getColumnCount(path);
                    int rows = getRowCount(path);
                    System.out.println(path + " (列数: " + columns + ", 行数: " + rows +
                            ", 分隔符: " + (delimiter == ',' ? "逗号" : delimiter == ';' ? "分号" : delimiter) + ")");
                } catch (IOException e) {
                    System.err.println("读取文件失败: " + path + " | " + e.getMessage());
                }
            }
        } else {
            System.err.println("错误: 数据集路径 " + DATASET_PATH + " 不是一个有效的文件或目录。");
            return;
        }
        
        // --- 3. 处理结果文件名 ---
        String resultFilePath = RESULTS_FILE;
        if (USE_TIME_SUFFIX) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmm"));
            int dotIndex = RESULTS_FILE.lastIndexOf('.');
            if (dotIndex > 0) {
                resultFilePath = RESULTS_FILE.substring(0, dotIndex) + "_" + timestamp + RESULTS_FILE.substring(dotIndex);
            } else {
                resultFilePath = RESULTS_FILE + "_" + timestamp;
            }
        }

        // --- 4. 根据采样模式选择实验配置 ---
        List<ExperimentConfig> configs = getConfigsBySamplingMode(SAMPLING_MODE);
        System.out.println("采样模式: " + SAMPLING_MODE + ", 将运行 " + configs.size() + " 种采样策略");

        // TANE配置 (TANE不支持采样)
        ExperimentConfig taneConfig = new ExperimentConfig("G3-No-Sampling", MEASURE, null, MAX_ERROR, 0, null, false, false);

        // --- 5. 内存预检查和配置优化 ---
        performMemoryPreCheck(datasetPaths);
        configureMemoryOptimization();

        // 启动全局内存监控
        MemoryMonitor memoryMonitor = MemoryMonitor.getInstance();
        memoryMonitor.startMonitoring(10000, alert -> {
            if (alert.getLevel() == MemoryMonitor.AlertLevel.CRITICAL) {
                logger.error("实验执行中检测到严重内存压力: {}", alert);
                System.err.println("警告: 检测到严重内存压力，建议增加堆内存或减少数据集规模");
            } else if (alert.getLevel() == MemoryMonitor.AlertLevel.WARNING) {
                logger.warn("实验执行中检测到内存压力: {}", alert);
            }
        });

        // --- 6. 初始化执行器和结果管理器 ---
        AFDFinder pyro = new PyroExecutor();
        AFDFinder tane = new TaneExecutor();
        ResultManager resultManager = new ResultManager(resultFilePath, RUN_MODE);

        // 创建线程池
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        // --- 7. 运行实验并收集结果 ---
        for (String path : datasetPaths) {
            System.out.println("============================================================");
            System.out.println("Processing Dataset: " + path);
            DataSet dataSet;
            int columns = -1;
            int rows = -1;

            try {
                // 获取数据集信息
                columns = getColumnCount(path);
                rows = getRowCount(path);

                // 根据数据集大小调整超时时间
                long datasetSize = (long) columns * rows;
                long adjustedTimeout = calculateTimeoutForDataset(datasetSize);

                System.out.println("数据集规模: " + columns + "列 × " + rows + "行 = " + datasetSize + " 个数据点");
                System.out.println("调整后的超时时间: " + adjustedTimeout + " 分钟");

                // 检测正确的分隔符并设置
                dataSet = DataLoader.fromFile(Path.of(path))
                        .withHeader(true)
                        .withDelimiter(detectDelimiter(path))  // 使用检测到的分隔符
                        .load();

                // 输出当前内存状态
                MemoryMonitor.MemorySnapshot snapshot = memoryMonitor.getCurrentMemorySnapshot();
                System.out.println("当前内存使用: " + snapshot.getUsedHeapMemory() / (1024 * 1024) + "MB / " +
                                 snapshot.getMaxHeapMemory() / (1024 * 1024) + "MB (" +
                                 String.format("%.1f%%", snapshot.getUsageRatio() * 100) + ")");
                // --- 运行Pyro (对选中的采样策略)---
                for (ExperimentConfig config : configs) {
                    System.out.println("  Running Config: " + config.getConfigName() + " with Pyro");

                    if (resultManager.isResultExists(path, config.getConfigName(), "Pyro")) {
                        System.out.println("    -> Pyro result already exists. Skipping.");
                    } else {
                        try {
                            // 使用Future和超时来执行Pyro算法
                            Future<ExperimentResult> future = executorService.submit(() -> pyro.discover(dataSet, config));

                            try {
                                ExperimentResult pyroResult = future.get(adjustedTimeout, TimeUnit.MINUTES);
                                resultManager.writeResult(path, config.getConfigName(), "Pyro", pyroResult, columns, rows);
                                System.out.println("    -> Pyro result saved.");

                                // 输出性能统计
                                if (pyroResult.getPeakMemoryUsageMB() > 0) {
                                    System.out.println("    -> 内存峰值: " + pyroResult.getPeakMemoryUsageMB() + "MB");
                                }
                                if (!pyroResult.getPliPerformanceStats().isEmpty()) {
                                    System.out.println("    -> PLI统计: " + pyroResult.getPliPerformanceStats());
                                }
                            } catch (TimeoutException e) {
                                future.cancel(true);
                                System.err.println("    -> Pyro timed out after " + adjustedTimeout + " minutes");
                                resultManager.writeError(path, config.getConfigName(), "Pyro",
                                        new Exception("Algorithm timeout after " + adjustedTimeout + " minutes"),
                                        columns, rows);
                            } catch (ExecutionException e) {
                                System.err.println("    -> Pyro failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                                resultManager.writeError(path, config.getConfigName(), "Pyro",
                                        new Exception(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()),
                                        columns, rows);
                            }
                        } catch (Exception e) {
                            System.err.println("    -> Pyro failed: " + e.getMessage());
                            resultManager.writeError(path, config.getConfigName(), "Pyro", e, columns, rows);
                        }
                    }
                }

                // --- 运行TANE (如果开启) ---
                if (RUN_TANE) {
                    System.out.println("  Running TANE (no sampling)");

                    if (resultManager.isResultExists(path, taneConfig.getConfigName(), "TANE")) {
                        System.out.println("    -> TANE result already exists. Skipping.");
                    } else {
                        try {
                            // 使用Future和超时来执行TANE算法
                            Future<ExperimentResult> future = executorService.submit(() -> tane.discover(dataSet, taneConfig));

                            try {
                                ExperimentResult taneResult = future.get(adjustedTimeout, TimeUnit.MINUTES);
                                resultManager.writeResult(path, taneConfig.getConfigName(), "TANE", taneResult, columns, rows);
                                System.out.println("    -> TANE result saved.");

                                // 输出性能统计
                                if (taneResult.getPeakMemoryUsageMB() > 0) {
                                    System.out.println("    -> 内存峰值: " + taneResult.getPeakMemoryUsageMB() + "MB");
                                }
                                if (!taneResult.getPliPerformanceStats().isEmpty()) {
                                    System.out.println("    -> PLI统计: " + taneResult.getPliPerformanceStats());
                                }
                            } catch (TimeoutException e) {
                                future.cancel(true);
                                System.err.println("    -> TANE timed out after " + adjustedTimeout + " minutes");
                                resultManager.writeError(path, taneConfig.getConfigName(), "TANE",
                                        new Exception("Algorithm timeout after " + adjustedTimeout + " minutes"),
                                        columns, rows);
                            } catch (ExecutionException e) {
                                System.err.println("    -> TANE failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                                resultManager.writeError(path, taneConfig.getConfigName(), "TANE",
                                        new Exception(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()),
                                        columns, rows);
                            }
                        } catch (Exception e) {
                            System.err.println("    -> TANE failed: " + e.getMessage());
                            resultManager.writeError(path, taneConfig.getConfigName(), "TANE", e, columns, rows);
                        }
                    }
                } else {
                    System.out.println("  TANE execution is disabled.");
                }
            } catch (Exception e) {
                System.err.println("Could not load dataset: " + path + " | " + e.getMessage());
                continue;
            }
        }
        
        // 关闭线程池
        executorService.shutdown();
        
        System.out.println("\n============================================================");
        System.out.println("All experiments finished. Results are in " + resultFilePath);
        System.out.println("============================================================");
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
                        DATASET_PATH = value;
                        break;
                    case "--results-file":
                        RESULTS_FILE = value;
                        break;
                    case "--max-error":
                        MAX_ERROR = Double.parseDouble(value);
                        break;
                    case "--sample-param":
                        SAMPLE_PARAM = Double.parseDouble(value);
                        break;
                    case "--sampling-mode":
                        SAMPLING_MODE = SamplingMode.valueOf(value.toUpperCase());
                        break;
                    case "--run-tane":
                        RUN_TANE = Boolean.parseBoolean(value);
                        break;
                    case "--run-mode":
                        RUN_MODE = ResultManager.RunMode.valueOf(value.toUpperCase());
                        break;
                    case "--timeout":
                        ALGORITHM_TIMEOUT_MINUTES = Long.parseLong(value);
                        break;
                    case "--seed":
                        RANDOM_SEED = Long.parseLong(value);
                        break;
                    case "--use-dynamic-threshold":
                        USE_DYNAMIC_THRESHOLD = Boolean.parseBoolean(value);
                        break;
                    case "--use-time-suffix":
                        USE_TIME_SUFFIX = Boolean.parseBoolean(value);
                        break;
                    case "--verbose":
                        VERBOSE = Boolean.parseBoolean(value);
                        break;
                    default:
                        System.err.println("警告: 未知参数 " + arg);
                        break;
                }
            }
        }
    }
    
    /**
     * 根据采样模式获取对应的实验配置列表
     * @param mode 采样模式
     * @return 实验配置列表
     */
    private static List<ExperimentConfig> getConfigsBySamplingMode(SamplingMode mode) {
        // 创建各种采样配置
        ExperimentConfig noSamplingConfig = new ExperimentConfig(
                "G3-No-Sampling", MEASURE, null, MAX_ERROR, 0, null, false, false);
        
        ExperimentConfig randomSamplingConfig = new ExperimentConfig(
                "G3-Random-Sampling", MEASURE, RandomSampling.class, MAX_ERROR, SAMPLE_PARAM, 
                RANDOM_SEED, USE_DYNAMIC_THRESHOLD, VERBOSE);
        
        ExperimentConfig focusedSamplingConfig = new ExperimentConfig(
                "G3-Focused-Sampling", MEASURE, FocusedSampling.class, MAX_ERROR, SAMPLE_PARAM, 
                RANDOM_SEED, USE_DYNAMIC_THRESHOLD, VERBOSE);
        
        ExperimentConfig neymanSamplingConfig = new ExperimentConfig(
                "G3-Neyman-Sampling", MEASURE, NeymanSampling.class, MAX_ERROR, SAMPLE_PARAM, 
                RANDOM_SEED, USE_DYNAMIC_THRESHOLD, VERBOSE);
        
        // 根据模式选择配置
        switch (mode) {
            case ALL:
                return List.of(noSamplingConfig, randomSamplingConfig, focusedSamplingConfig, neymanSamplingConfig);
            case NO_SAMPLING:
                return List.of(noSamplingConfig);
            case RANDOM:
                return List.of(randomSamplingConfig);
            case FOCUSED:
                return List.of(focusedSamplingConfig);
            case NEYMAN:
                return List.of(neymanSamplingConfig);
            default:
                // 默认运行所有采样方法
                return List.of(noSamplingConfig, randomSamplingConfig, focusedSamplingConfig, neymanSamplingConfig);
        }
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
    
    /**
     * 获取指定目录下所有文件的路径，按CSV文件的列数从少到多排序
     * @param directoryPath 目录路径
     * @return 文件路径列表（按列数排序）
     * @throws IOException 如果读取目录失败
     */
    private static List<String> getDatasetPaths(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IOException("路径不存在或不是一个目录: " + directoryPath);
        }
        
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath), 1)) {
            List<File> files = paths
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
            
            // 按CSV列数从少到多排序
            return files.stream()
                    .sorted((file1, file2) -> {
                        try {
                            int columns1 = getColumnCount(file1.getPath());
                            int columns2 = getColumnCount(file2.getPath());
                            return Integer.compare(columns1, columns2);
                        } catch (IOException e) {
                            System.err.println("排序文件时出错: " + e.getMessage());
                            return 0; // 出错时保持原有顺序
                        }
                    })
                    .map(File::getPath)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 执行内存预检查，评估数据集规模和可用内存
     */
    private static void performMemoryPreCheck(List<String> datasetPaths) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        long availableMemoryMB = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024);

        System.out.println("\n============================================================");
        System.out.println("内存预检查");
        System.out.println("============================================================");
        System.out.println("JVM最大堆内存: " + maxMemoryMB + "MB");
        System.out.println("当前可用内存: " + availableMemoryMB + "MB");

        // 分析数据集规模
        long totalDataPoints = 0;
        int largeDatasetCount = 0;
        int hugeDatasetCount = 0;

        for (String path : datasetPaths) {
            try {
                int columns = getColumnCount(path);
                int rows = getRowCount(path);
                long datasetSize = (long) columns * rows;
                totalDataPoints += datasetSize;

                if (datasetSize > 50_000_000) { // 5000万数据点
                    hugeDatasetCount++;
                    System.out.println("超大数据集: " + path + " (" + columns + "×" + rows + " = " + datasetSize + " 数据点)");
                } else if (datasetSize > 10_000_000) { // 1000万数据点
                    largeDatasetCount++;
                    System.out.println("大数据集: " + path + " (" + columns + "×" + rows + " = " + datasetSize + " 数据点)");
                }
            } catch (Exception e) {
                System.err.println("无法分析数据集: " + path + " - " + e.getMessage());
            }
        }

        System.out.println("总数据点: " + totalDataPoints);
        System.out.println("大数据集数量: " + largeDatasetCount);
        System.out.println("超大数据集数量: " + hugeDatasetCount);

        // 内存建议
        if (hugeDatasetCount > 0 && maxMemoryMB < 8192) {
            System.out.println("\n⚠️  警告: 检测到超大数据集，建议增加堆内存到至少8GB");
            System.out.println("   建议JVM参数: -Xmx8g 或更大");
        } else if (largeDatasetCount > 0 && maxMemoryMB < 4096) {
            System.out.println("\n⚠️  警告: 检测到大数据集，建议增加堆内存到至少4GB");
            System.out.println("   建议JVM参数: -Xmx4g");
        } else if (totalDataPoints > 100_000_000 && maxMemoryMB < 2048) {
            System.out.println("\n⚠️  警告: 数据集总规模较大，建议增加堆内存到至少2GB");
            System.out.println("   建议JVM参数: -Xmx2g");
        } else {
            System.out.println("\n✅ 内存配置看起来合适");
        }

        System.out.println("============================================================\n");
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
            System.setProperty("pli.force.streaming", "true"); // 强制使用流式处理
        }

        // 配置内存监控参数
        System.setProperty("memory.monitor.warning.threshold", "0.7");
        System.setProperty("memory.monitor.critical.threshold", "0.85");

        logger.info("内存优化配置完成，最大堆内存: {}MB", maxMemoryMB);
    }

    /**
     * 根据数据集大小计算合适的超时时间
     */
    private static long calculateTimeoutForDataset(long datasetSize) {
        if (datasetSize > 100_000_000) { // 1亿数据点
            return Math.max(ALGORITHM_TIMEOUT_MINUTES, 240); // 至少4小时
        } else if (datasetSize > 50_000_000) { // 5000万数据点
            return Math.max(ALGORITHM_TIMEOUT_MINUTES, 180); // 至少3小时
        } else if (datasetSize > 10_000_000) { // 1000万数据点
            return Math.max(ALGORITHM_TIMEOUT_MINUTES, 120); // 至少2小时
        } else if (datasetSize > 1_000_000) { // 100万数据点
            return Math.max(ALGORITHM_TIMEOUT_MINUTES, 60); // 至少1小时
        } else {
            return ALGORITHM_TIMEOUT_MINUTES; // 使用默认超时时间
        }
    }
}
