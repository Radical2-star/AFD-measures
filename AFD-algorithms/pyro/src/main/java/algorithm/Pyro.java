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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */

public class Pyro {
    private final DataSet dataset;
    private final PyroConfig config;
    private final PLICache pliCache;
    private static final String dataPath = "data/atom_new.csv";
    private static final double maxError = 0.05;

    public Pyro(DataSet dataset, PyroConfig config) {
        this.dataset = dataset;
        this.config = config;
        this.pliCache = PLICache.getInstance(dataset);
    }

    public List<Node> discover() {
        List<Node> result = new ArrayList<>();

        // 为每个属性创建搜索空间
        for (int rhs = 0; rhs < dataset.getColumnCount(); rhs++) {
            // if (rhs != 0) continue; // 仅测试一个searchSpace，真正运行时请注释掉！
            SearchSpace searchSpace = new SearchSpace(rhs, dataset, pliCache, config);
            searchSpace.explore();
            result.addAll(searchSpace.getValidatedNodes());
        }

        System.out.println("验证次数：" + SearchSpace.getValidateCount());

        return result;
    }
    
    /**
     * 将{@code List<Node>}转换为{@code Set<FunctionalDependency>}
     * @param nodes 待转换的节点列表
     * @return 函数依赖集合
     */
    public Set<FunctionalDependency> convertToFDSet(List<Node> nodes) {
        Set<FunctionalDependency> fdSet = new HashSet<>();
        for (Node node : nodes) {
            if (node.isValid(config.getMaxError())) {
                fdSet.add(new FunctionalDependency(
                    utils.BitSetUtils.bitSetToSet(node.getLhs()),
                    node.getRhs(),
                    node.getError()
                ));
            }
        }
        return fdSet;
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
        
        List<Node> nodeResults = pyro.discover();
        Set<FunctionalDependency> fdResults = pyro.convertToFDSet(nodeResults);
        
        // 输出节点结果（原来的输出方式）
        nodeResults.sort(Comparator.comparingDouble(Node::getError));
        System.out.println("===== Pyro算法结果（Node格式） =====");
        for (int i = 0; i < Math.min(10, nodeResults.size()); i++) {
            System.out.println(nodeResults.get(i));
        }
        System.out.println("总共找到 " + nodeResults.size() + " 个近似函数依赖");
        
        // 输出FD结果
        System.out.println("\n===== Pyro算法结果（FD格式） =====");
        List<FunctionalDependency> sortedFDs = new ArrayList<>(fdResults);
        sortedFDs.sort(new Comparator<FunctionalDependency>() {
            @Override
            public int compare(FunctionalDependency fd1, FunctionalDependency fd2) {
                return Double.compare(fd1.getError(), fd2.getError());
            }
        });
        for (int i = 0; i < Math.min(10, sortedFDs.size()); i++) {
            System.out.println(sortedFDs.get(i));
        }
        System.out.println("总共找到 " + fdResults.size() + " 个函数依赖");
    }
}