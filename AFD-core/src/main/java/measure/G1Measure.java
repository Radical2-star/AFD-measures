package measure;

import model.DataSet;
import model.FunctionalDependency;
import pli.PLI;
import pli.PLICache;
import sampling.SamplingStrategy;
import static utils.BitSetUtils.*;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *  G1Measure
 *
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class G1Measure implements ErrorMeasure {
    @Override
    public double calculateError(BitSet lhsBitSet, int rhs, DataSet data,
                                 PLICache cache) {
        Set<Integer> lhs = bitSetToSet(lhsBitSet);

        // 获取左侧列的PLI
        PLI lhsPLI = cache.getOrCalculatePLI(lhsBitSet);

        // 计算总可能元组对数
        long totalPairs = totalPossiblePairs(data.getRowCount());

        // 计算违反的元组对数
        long violatingPairs = calculateViolatingPairs(lhsPLI, data, rhs);

        return (double) violatingPairs / totalPairs;
    }

    private long totalPossiblePairs(int n) {
        return n * (n - 1L) / 2L;
    }

    private long calculateViolatingPairs(PLI lhsPLI, DataSet data, int rhs) {
        long count = 0L;

        for (Set<Integer> cluster : lhsPLI.getEquivalenceClasses()) {
            // 统计该簇中不同RHS值的分布
            Map<String, Integer> rhsCounts = new HashMap<>();
            for (int row : cluster) {
                String value = data.getValue(row, rhs);
                rhsCounts.put(value, rhsCounts.getOrDefault(value, 0) + 1);
            }

            // 计算该簇内的冲突对数
            count += calculateClusterViolations(rhsCounts);
        }
        return count;
    }

    private long calculateClusterViolations(Map<String, Integer> counts) {
        long sum = counts.values().stream().mapToLong(i -> i).sum();
        long totalPairs = sum * (sum - 1) / 2;

        long samePairs = counts.values().stream()
                .mapToLong(n -> n * (n - 1) / 2)
                .sum();

        return totalPairs - samePairs;
    }

    @Override
    public double estimateError(BitSet lhsBitSet, int rhs, DataSet data,
                                PLICache cache, SamplingStrategy strategy) {
        // TODO: estimate方法待实现（需要结合samplingStrategy）
        Set<Integer> lhs = bitSetToSet(lhsBitSet);

        // 获取左侧列的PLI
        PLI lhsPLI = cache.getOrCalculatePLI(lhsBitSet);

        // 计算总可能元组对数
        long totalPairs = totalPossiblePairs(data.getRowCount());

        // 计算违反的元组对数
        long violatingPairs = calculateViolatingPairs(lhsPLI, data, rhs);

        return (double) violatingPairs / totalPairs;
    }

}