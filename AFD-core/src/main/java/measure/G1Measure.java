package measure;

import model.DataSet;
import pli.PLI;
import pli.PLICache;
import sampling.SamplingStrategy;
import utils.LongBitSetUtils;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * G1Measure - 基于"违反元组对"的误差度量实现
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/2/26
 */
public class G1Measure implements ErrorMeasure {

    // ==================== 新的long版本方法（性能优化） ====================

    /**
     * 计算函数依赖的错误率（long版本，性能更优）
     * 使用G1度量：基于违反元组对的误差计算
     *
     * @param lhs 左手边属性集（long表示，支持最多64列）
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @return G1错误率
     */
    @Override
    public double calculateError(long lhs, int rhs, DataSet data, PLICache cache) {
        // 获取rhs对应的PLI（单列）
        long rhsLong = LongBitSetUtils.setBit(0L, rhs);
        BitSet rhsBitSet = LongBitSetUtils.longToBitSet(rhsLong, data.getColumnCount());
        PLI rhsPLI = cache.getOrCalculatePLI(rhsBitSet);

        // 总误差元组对数
        long totalViolations = 0;
        int totalRows = data.getRowCount();
        long totalPossiblePairs = (long) totalRows * (totalRows - 1);

        // 处理根节点的情况：UCC
        if (LongBitSetUtils.isEmpty(lhs)) {
            // 遍历rhsPLI中的所有簇，分别计算n * (n-1)并求和
            for (Set<Integer> cluster : rhsPLI.getEquivalenceClasses()) {
                int clusterSize = cluster.size();
                totalViolations += (long) clusterSize * (clusterSize - 1);
            }
            return totalViolations == 0 ? 0 : (double) totalViolations / totalPossiblePairs;
        }

        int[] vY = rhsPLI.toAttributeVector();

        // 获取lhs对应的PLI
        BitSet lhsBitSet = LongBitSetUtils.longToBitSet(lhs, data.getColumnCount());
        PLI lhsPLI = cache.getOrCalculatePLI(lhsBitSet);

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

    /**
     * 估计函数依赖的错误率（long版本，性能更优）
     * 当前实现直接调用完整计算，未来可扩展采样策略
     *
     * @param lhs 左手边属性集（long表示，支持最多64列）
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @param strategy 采样策略
     * @return 估计的G1错误率
     */
    @Override
    public double estimateError(long lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        // 暂时直接返回完整计算结果
        return calculateError(lhs, rhs, data, cache);
    }

    // ==================== 原有BitSet版本方法（兼容性） ====================

    /**
     * 计算函数依赖的错误率（BitSet版本，兼容性方法）
     * 使用G1度量：基于违反元组对的误差计算
     *
     * @param lhs 左手边属性集
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @return G1错误率
     */
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

    /**
     * 估计函数依赖的错误率（BitSet版本，兼容性方法）
     * 当前实现直接调用完整计算，未来可扩展采样策略
     *
     * @param lhs 左手边属性集
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @param strategy 采样策略
     * @return 估计的G1错误率
     */
    @Override
    public double estimateError(BitSet lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        // 暂时直接返回完整计算结果
        return calculateError(lhs, rhs, data, cache);
    }
}
