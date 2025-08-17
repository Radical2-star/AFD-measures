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
 * SimpleG3Measure - G3度量的简化实现
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/5/20
 */
public class SimpleG3Measure implements ErrorMeasure{

    // ==================== 新的long版本方法（性能优化） ====================

    /**
     * 计算函数依赖的错误率（long版本，性能更优）
     * 使用简化的G3度量：基于删除元组数的误差计算
     *
     * @param lhs 左手边属性集（long表示，支持最多64列）
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @return 简化G3错误率
     */
    @Override
    public double calculateError(long lhs, int rhs, DataSet data, PLICache cache) {
        // 1. 获取rhs对应的PLI（单列）及属性向量
        long rhsLong = LongBitSetUtils.setBit(0L, rhs);
        BitSet rhsBitSet = LongBitSetUtils.longToBitSet(rhsLong, data.getColumnCount());
        PLI rhsPLI = cache.getOrCalculatePLI(rhsBitSet);
        int[] vY = rhsPLI.toAttributeVector();

        // 2. 初始化总删除数和总行数
        int totalRows = data.getRowCount();
        if (totalRows <= 1) return 0.0; // 空表或单行表无需计算
        int totalRemovals = 0;

        // 处理根节点特殊情况
        if (LongBitSetUtils.isEmpty(lhs)) {
            for (Set<Integer> cluster : rhsPLI.getEquivalenceClasses()) {
                int clusterSize = cluster.size();
                totalRemovals += clusterSize - 1;
            }
            return totalRemovals == 0 ? 0 : (double) totalRemovals / (totalRows - 1);
        }

        // 3. 获取lhs对应的PLI
        BitSet lhsBitSet = LongBitSetUtils.longToBitSet(lhs, data.getColumnCount());
        PLI lhsPLI = cache.getOrCalculatePLI(lhsBitSet);

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

    /**
     * 估计函数依赖的错误率（long版本，性能更优）
     * 简化实现：直接调用完整计算
     *
     * @param lhs 左手边属性集（long表示，支持最多64列）
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @param strategy 采样策略
     * @return 估计的简化G3错误率
     */
    @Override
    public double estimateError(long lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        return calculateError(lhs, rhs, data, cache);
    }

    // ==================== 原有BitSet版本方法（兼容性） ====================

    /**
     * 计算函数依赖的错误率（BitSet版本，兼容性方法）
     * 使用简化的G3度量：基于删除元组数的误差计算
     *
     * @param lhs 左手边属性集
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @return 简化G3错误率
     */
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

    /**
     * 估计函数依赖的错误率（BitSet版本，兼容性方法）
     * 简化实现：直接调用完整计算
     *
     * @param lhs 左手边属性集
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @param strategy 采样策略
     * @return 估计的简化G3错误率
     */
    @Override
    public double estimateError(BitSet lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        return calculateError(lhs, rhs, data, cache);
    }
}