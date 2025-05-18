import measure.G3Measure;
import measure.SimpleMeasure;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLICache;
import sampling.RandomSampling;
import utils.DataLoader;
import utils.FunctionTimer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */

public class Pyro {
    private final DataSet dataset;
    private final PyroConfig config;
    private final PLICache pliCache;
    private final FunctionTimer timer = FunctionTimer.getInstance();

    public Pyro(DataSet dataset, PyroConfig config) {
        this.dataset = dataset;
        this.config = config;
        timer.start("initializePLI");
        this.pliCache = PLICache.getInstance(dataset);
        timer.end("initializePLI");
    }

    public List<Node> discover() {
        List<Node> result = new ArrayList<>();

        // 为每个属性创建搜索空间
        for (int rhs = 0; rhs < dataset.getColumnCount(); rhs++) {
            if (rhs != 0) continue; // 仅测试一个searchspace，真正运行时请注释掉！
            SearchSpace searchSpace = new SearchSpace(rhs, dataset, pliCache, config);
            searchSpace.explore();
            result.addAll(searchSpace.getValidatedNodes());
        }

        return result;
    }

    public static void main(String[] args) {
        DataLoader loader = DataLoader.fromFile(
                Path.of("data/test_new.csv")
        ).withHeader(true).withDelimiter(',');
        DataSet dataset = loader.load();
        List<Node> res = new Pyro(dataset,
                new PyroConfig(
                new G3Measure(),
                new RandomSampling(),
                0.05
        )).discover();
        for (int i = 0; i < 10; i++) { // 仅输出前10个结果
            System.out.println(res.get(i));
        }
        System.out.println(res.size());
    }
}