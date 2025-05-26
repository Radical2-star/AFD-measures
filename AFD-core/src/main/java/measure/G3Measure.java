package measure;

import model.DataSet;
import pli.PLI;
import pli.PLICache;
import sampling.SamplingStrategy;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *  G3Measure
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class G3Measure implements ErrorMeasure {
    @Override
    public double calculateError(BitSet lhs, int rhs, DataSet data, PLICache cache) {
        // 1. 获取rhs对应的PLI（单列）及属性向量
        BitSet rhsBitSet = new BitSet();
        rhsBitSet.set(rhs);
        PLI rhsPLI = cache.getOrCalculatePLI(rhsBitSet);
        int[] vY = rhsPLI.toAttributeVector();

        // 2. 初始化总删除数和总行数
        int totalRows = data.getRowCount();
        if (totalRows <= 1) return 0.0; // 空表或单行表无需计算
        int totalRemovals = 0;

        // 处理根节点特殊情况
        if (lhs.isEmpty()) {
            for (Set<Integer> cluster : rhsPLI.getEquivalenceClasses()) {
                int clusterSize = cluster.size();
                totalRemovals += clusterSize - 1;
            }
            return totalRemovals == 0 ? 0 : (double) totalRemovals / (totalRows - 1);
        }

        // 3. 获取lhs对应的PLI
        PLI lhsPLI = cache.getOrCalculatePLI(lhs);

        // 4. 遍历lhs的每个等价类簇
        for (Set<Integer> cluster : lhsPLI.getEquivalenceClasses()) {
            int clusterSize = cluster.size();
            Map<Integer, Integer> freqCounter = new HashMap<>();

            // 4.1 统计当前簇中rhs属性值的分布
            for (int row : cluster) {
                int yVal = vY[row];
                if (yVal == 0) continue; // 去除单例
                freqCounter.put(yVal, freqCounter.getOrDefault(yVal, 0) + 1);
            }

            // 4.2 找出出现次数最多的属性值
            int maxCount = freqCounter.values().stream()
                    .max(Integer::compare)
                    .orElse(0); // 当簇为空时返回0（理论上不会发生）

            // 4.3 计算需要删除的元组数
            if (maxCount == 0) {
                // 情况1：所有元组在rhs都是单例 → 保留1个，删除(clusterSize-1)
                totalRemovals += (clusterSize - 1);
            } else {
                // 情况2：保留出现最多的值对应的元组 → 删除(clusterSize - maxCount)
                totalRemovals += (clusterSize - maxCount);
            }
        }

        // 5. 计算最终误差率
        return (double) totalRemovals / (totalRows - 1);
    }

    @Override
    public double estimateError(BitSet lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        // TODO: 未测试estimate逻辑
        // 应用采样策略
        strategy.initialize(data, 0.05); // 示例使用20%比例采样
        Set<Integer> sampleRows = strategy.getSampleIndices();

        // 获取原始PLI信息
        PLI lhsPLI = cache.getOrCalculatePLI(lhs);
        int[] rhsVector = cache.getOrCalculatePLI(BitSet.valueOf(new long[]{1L << rhs}))
                .toAttributeVector();

        // 统计样本中的误差
        long sampleViolations = 0;
        for (Set<Integer> cluster : lhsPLI.getEquivalenceClasses()) {
            // 筛选出在样本中的行
            Set<Integer> sampledCluster = new HashSet<>();
            for (int row : cluster) {
                if (sampleRows.contains(row)) {
                    sampledCluster.add(row);
                }
            }

            // 计算该子簇的删除数
            Map<Integer, Integer> counter = new HashMap<>();
            for (int row : sampledCluster) {
                int yVal = rhsVector[row];
                if (yVal == 0) continue; // 去除单例
                counter.put(yVal, counter.getOrDefault(yVal, 0) + 1);
            }

            int maxCount = counter.values().stream()
                    .max(Integer::compare).orElse(0);
            if (maxCount == 0) {
                // sampledCluster是有可能等于0的，因为采样可能都没采到
                if (!sampledCluster.isEmpty()) {
                    sampleViolations += (sampledCluster.size() - 1);
                }
            } else {
                sampleViolations += (sampledCluster.size() - maxCount);
            }
        }

        // 计算采样比例并估计总误差
        double sampleRate = (double)sampleRows.size() / data.getRowCount();
        double estimatedTotal = sampleViolations / sampleRate;
        return estimatedTotal / (data.getRowCount() - 1);
    }
}