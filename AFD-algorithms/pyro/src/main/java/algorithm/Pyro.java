package algorithm;

import measure.*;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLICache;
import sampling.NeymanSampling;
import sampling.RandomSampling;
import sampling.SamplingStrategy;
import utils.DataLoader;
import utils.FunctionTimer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */

public class Pyro {
    private final DataSet dataset;
    private final PLICache pliCache;
    private final ErrorMeasure measure;
    private final SamplingStrategy samplingStrategy;
    private final double maxError;
    private long executionTimeMs;
    private final boolean verbose;
    private final double sampleParam;

    public Pyro(DataSet dataset, ErrorMeasure measure, SamplingStrategy samplingStrategy, double maxError, double sampleParam, boolean verbose) {
        this.dataset = dataset;
        this.measure = measure;
        this.samplingStrategy = samplingStrategy;
        this.maxError = maxError;
        this.sampleParam = sampleParam;
        this.verbose = verbose;
        this.pliCache = new PLICache(dataset);
    }

    public List<FunctionalDependency> discover() {
        List<FunctionalDependency> result = new ArrayList<>();
        SearchSpace.resetValidateCount();

        long startTime = System.currentTimeMillis();
        
        // 为每个属性创建搜索空间
        for (int rhs = 0; rhs < dataset.getColumnCount(); rhs++) {
            // if (rhs != 0) continue; // 仅测试一个searchSpace，真正运行时请注释掉！
            SearchSpace searchSpace = new SearchSpace(rhs, dataset, pliCache, measure, samplingStrategy, maxError, sampleParam, verbose);
            searchSpace.explore();
            result.addAll(searchSpace.getValidatedFDs());
        }

        this.executionTimeMs = System.currentTimeMillis() - startTime;
        return result;
    }

    public int getValidationCount() {
        return SearchSpace.getValidateCount();
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public static void main(String[] args) {
        DataLoader loader = DataLoader.fromFile(
                Path.of("data/0/classification.csv")
        ).withHeader(true).withDelimiter(';');
        DataSet dataset = loader.load();
        Pyro pyro = new Pyro(dataset,
                new G3Measure(),
                new NeymanSampling(),
                0.05,
                200,
                false
        );

        List<FunctionalDependency> fdResults = pyro.discover();

        // 输出FD结果
        System.out.println("\n===== Pyro算法结果（FD格式） =====");
        List<FunctionalDependency> sortedFDs = new ArrayList<>(fdResults);
        sortedFDs.sort(Comparator.comparingDouble(FunctionalDependency::getError));
        for (int i = 0; i < Math.min(10, sortedFDs.size()); i++) {
            System.out.println(sortedFDs.get(i));
        }
        System.out.println("总共找到 " + fdResults.size() + " 个函数依赖");

        FunctionTimer timer = FunctionTimer.getInstance();
        timer.printResults();
    }
} 