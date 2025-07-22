package measure;

import model.DataSet;
import pli.PLI;
import pli.PLICache;
import sampling.SamplingStrategy;
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
    public double calculateError(BitSet lhs, int rhs, DataSet data, PLICache cache) {
        // 获取rhs对应的PLI（单列）
        BitSet rhsBitSet = new BitSet();
        rhsBitSet.set(rhs);
        PLI rhsPLI = cache.getOrCalculatePLI(rhsBitSet);

        // 总误差元组对数
        long totalViolations = 0;
        int totalRows = data.getRowCount();
        long totalPossiblePairs = (long) totalRows * (totalRows - 1);

        // 处理根节点的情况：UCC
        if (lhs.isEmpty()) {
            // 遍历rhsPLI中的所有簇，分别计算n * (n-1)并求和
            for (Set<Integer> cluster : rhsPLI.getEquivalenceClasses()) {
                int clusterSize = cluster.size();
                totalViolations += (long) clusterSize * (clusterSize - 1);
            }
            return totalViolations == 0 ? 0 : (double) totalViolations / totalPossiblePairs;
        }

        int[] vY = rhsPLI.toAttributeVector();

        // 获取lhs对应的PLI
        PLI lhsPLI = cache.getOrCalculatePLI(lhs);

        // 遍历lhs的每个等价类
        for (Set<Integer> cluster : lhsPLI.getEquivalenceClasses()) {
            int clusterSize = cluster.size();
            if (clusterSize < 2) continue;

            // 统计rhs属性向量中各clusterId的出现次数
            Map<Integer, Integer> counter = new HashMap<>();
            for (int row : cluster) {
                int yClusterId = vY[row];
                if (yClusterId != 0) { // 忽略单例
                    counter.put(yClusterId, counter.getOrDefault(yClusterId, 0) + 1);
                }
            }

            // 计算该簇的总元组对数
            long totalPairsInCluster = (long) clusterSize * (clusterSize - 1);

            // 计算有效匹配的元组对数
            long validPairs = 0;
            for (int count : counter.values()) {
                validPairs += (long) count * (count - 1);
            }

            // 累计违规对：总对数 - 有效对数
            totalViolations += (totalPairsInCluster - validPairs);
        }

        // 计算最终误差率
        return totalPossiblePairs == 0 ? 0 : (double) totalViolations / totalPossiblePairs;
    }

    @Override
    public double estimateError(BitSet lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        // 暂时直接返回完整计算结果
        return calculateError(lhs, rhs, data, cache);
    }
}
