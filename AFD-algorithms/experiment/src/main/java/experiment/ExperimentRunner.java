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

import java.nio.file.Path;
import java.util.List;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class ExperimentRunner {
    // public configs
    public static final double MAX_ERROR = 0.05;
    public static final double SAMPLE_PARAM = 0.1;
    public static final Long RANDOM_SEED = 12345L; // 可以设置为null表示纯随机
    public static final boolean USE_DYNAMIC_THRESHOLD = true;
    public static final boolean VERBOSE = false;
    public static final ErrorMeasure MEASURE = new G3Measure();
    public static final List<String> DATASET_PATHS = List.of(
            "data/abalone.csv",
            "data/atom_new.csv",
            "data/test_new.csv",
            "data/airport.csv",
            "data/1_CLASSIFICATION_new.csv",
            "data/CENSUS_50000_new.csv",
            "data/hepa.csv",
            "data/ncvoter.csv"
    );
    public static final String RESULTS_FILE = "result/result0623.csv";
    public static final ResultManager.RunMode RUN_MODE = ResultManager.RunMode.APPEND;
    // 可以设置为APPEND或OVERWRITE

    public static void main(String[] args) {
        // 1. 定义实验配置列表
        List<ExperimentConfig> configs = List.of(
                new ExperimentConfig("G3-Random-Sampling", MEASURE, RandomSampling.class, MAX_ERROR, SAMPLE_PARAM, RANDOM_SEED, USE_DYNAMIC_THRESHOLD, VERBOSE),
                new ExperimentConfig("G3-Focused-Sampling", MEASURE, FocusedSampling.class, MAX_ERROR, SAMPLE_PARAM, RANDOM_SEED, USE_DYNAMIC_THRESHOLD, VERBOSE),
                new ExperimentConfig("G3-Neyman-Sampling", MEASURE, NeymanSampling.class, MAX_ERROR, SAMPLE_PARAM, RANDOM_SEED, USE_DYNAMIC_THRESHOLD, VERBOSE),
                new ExperimentConfig("G3-No-Sampling", MEASURE, null, MAX_ERROR, 0, null, false, false)
        );

        // 2. 初始化执行器和结果管理器
        AFDFinder pyro = new PyroExecutor();
        AFDFinder tane = new TaneExecutor();
        ResultManager resultManager = new ResultManager(RESULTS_FILE, RUN_MODE);


        // 3. 运行实验并收集结果
        for (String path : DATASET_PATHS) {
            System.out.println("============================================================");
            System.out.println("Processing Dataset: " + path);
            DataSet dataSet;
            try {
                dataSet = DataLoader.fromFile(Path.of(path)).withHeader(true).load();
            } catch (Exception e) {
                System.err.println("Could not load dataset: " + path + " | " + e.getMessage());
                continue;
            }

            for (ExperimentConfig config : configs) {
                System.out.println("  Running Config: " + config.getConfigName());

                // --- Run Pyro ---
                if (resultManager.isResultExists(path, config.getConfigName(), "Pyro")) {
                    System.out.println("    -> Pyro result already exists. Skipping.");
                } else {
                    try {
                        ExperimentResult pyroResult = pyro.discover(dataSet, config);
                        resultManager.writeResult(path, config.getConfigName(), "Pyro", pyroResult);
                        System.out.println("    -> Pyro result saved.");
                    } catch (Exception e) {
                        System.err.println("    -> Pyro failed: " + e.getMessage());
                        resultManager.writeError(path, config.getConfigName(), "Pyro", e);
                    }
                }

                // --- Run TANE ---
                // TANE 不支持采样, 所以为它使用一个无采样的基准配置
                ExperimentConfig taneConfig = (config.getSamplingStrategyClass() != null)
                        ? new ExperimentConfig("G3-No-Sampling", MEASURE, null, MAX_ERROR, 0, null, false, false)
                        : config;

                if (resultManager.isResultExists(path, taneConfig.getConfigName(), "TANE")) {
                    System.out.println("    -> TANE result already exists. Skipping.");
                } else {
                    try {
                        ExperimentResult taneResult = tane.discover(dataSet, taneConfig);
                        resultManager.writeResult(path, taneConfig.getConfigName(), "TANE", taneResult);
                        System.out.println("    -> TANE result saved.");
                    } catch (Exception e) {
                        System.err.println("    -> TANE failed: " + e.getMessage());
                        resultManager.writeError(path, taneConfig.getConfigName(), "TANE", e);
                    }
                }
            }
        }
        System.out.println("\n============================================================");
        System.out.println("All experiments finished. Results are in " + RESULTS_FILE);
        System.out.println("============================================================");
    }
}
