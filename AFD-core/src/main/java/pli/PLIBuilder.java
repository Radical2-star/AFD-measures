package pli;

import model.DataSet;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *  PLIBuilder
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */

public class PLIBuilder {
    private final Map<Integer, PLI> singleColumnCache = new ConcurrentHashMap<>();
    private final LoadingCache<List<Integer>, PLI> combinedCache;

    public PLIBuilder() {
        this.combinedCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .build(this::buildCombinedPLI);
    }

    private PLI buildCombinedPLI(List<Integer> columns) {
        List<PLI> plis = columns.stream()
                .map(this::getSingleColumnPLI)
                .collect(Collectors.toList());

        List<List<Integer>> merged = PLI.merge(plis);

        PLI combined = new PLI();
        combined.setCombinedPartitions(merged);
        return combined;
    }

    public PLI getSingleColumnPLI(int column) {
        return singleColumnCache.computeIfAbsent(column, c -> {
            PLI pli = new PLI();
            // 延迟加载数据集，实际项目需传入Dataset参数
            // pli.buildSingleColumn(dataset, c);
            return pli;
        });
    }

    public PLI getCombinedPLI(List<Integer> columns) {
        return combinedCache.get(columns);
    }

    // 带数据集初始化的版本
    public void initializeWithDataset(DataSet dataset) {
        singleColumnCache.clear();
        combinedCache.invalidateAll();

        IntStream.range(0, dataset.getAttributes().size())
                .forEach(col -> {
                    PLI pli = new PLI();
                    pli.buildSingleColumn(dataset, col);
                    singleColumnCache.put(col, pli);
                });
    }
}


/*
public class PLIBuilder {
    private final Map<Integer, PLI> singleColumnCache = new ConcurrentHashMap<>();
    private final Cache<List<Integer>, PLI> combinedCache;

    public PLIBuilder() {
        this.combinedCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .build();
    }

    // 获取单列PLI（带缓存）
    public PLI getSingleColumnPLI(Dataset dataset, int column) {
        return singleColumnCache.computeIfAbsent(column, c -> {
            PLI pli = new PLI();
            pli.buildForColumn(dataset, c);
            return pli;
        });
    }

    // 获取多列组合PLI（带缓存）
    public PLI getCombinedPLI(Dataset dataset, List<Integer> columns) {
        return combinedCache.get(columns, cols -> {
            List<PLI> plis = cols.stream()
                    .map(c -> getSingleColumnPLI(dataset, c))
                    .collect(Collectors.toList());

            PLI combined = new PLI();
            List<List<Integer>> merged = PLI.mergePLIs(plis);
            combined.setPartitions(merged);
            return combined;
        });
    }
}

*/