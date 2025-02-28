package measure;

import model.AutoTypeDataSet;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLI;
import pli.PLICache;

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
    public double calculateError(DataSet data,
                                 FunctionalDependency fd,
                                 PLICache cache) {
        Set<Integer> lhs = fd.getLhs();
        int rhs = fd.getRhs();

        // 获取左侧列的PLI
        PLI lhsPLI = cache.getPLI(lhs);

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
}