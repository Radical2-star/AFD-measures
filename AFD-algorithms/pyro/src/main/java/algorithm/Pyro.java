package algorithm;

import measure.*;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLICache;
import sampling.RandomSampling;
import utils.DataLoader;

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
    private final PyroConfig config;
    private final PLICache pliCache;
    private static final String dataPath = "data/test_new.csv";
    private static final double maxError = 0.05;

    public Pyro(DataSet dataset, PyroConfig config) {
        this.dataset = dataset;
        this.config = config;
        this.pliCache = PLICache.getInstance(dataset);
    }

    public List<FunctionalDependency> discover() {
        List<FunctionalDependency> result = new ArrayList<>();

        // 为每个属性创建搜索空间
        for (int rhs = 0; rhs < dataset.getColumnCount(); rhs++) {
            // if (rhs != 0) continue; // 仅测试一个searchSpace，真正运行时请注释掉！
            SearchSpace searchSpace = new SearchSpace(rhs, dataset, pliCache, config);
            searchSpace.explore();
            result.addAll(searchSpace.getValidatedFDs());
        }

        System.out.println("验证次数：" + SearchSpace.getValidateCount());

        return result;
    }

    public static void main(String[] args) {
        DataLoader loader = DataLoader.fromFile(
                Path.of(dataPath)
        ).withHeader(true).withDelimiter(',');
        DataSet dataset = loader.load();
        Pyro pyro = new Pyro(dataset,
                new PyroConfig(
                new G3Measure(),
                new RandomSampling(),
                maxError
        ));

        List<FunctionalDependency> fdResults = pyro.discover();

        // 输出FD结果
        System.out.println("\n===== Pyro算法结果（FD格式） =====");
        List<FunctionalDependency> sortedFDs = new ArrayList<>(fdResults);
        sortedFDs.sort(Comparator.comparingDouble(FunctionalDependency::getError));
        for (int i = 0; i < Math.min(10, sortedFDs.size()); i++) {
            System.out.println(sortedFDs.get(i));
        }
        System.out.println("总共找到 " + fdResults.size() + " 个函数依赖");
    }
}