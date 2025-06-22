package measure;

import model.DataSet;
import pli.PLI;
import pli.PLICache;
import sampling.SamplingStrategy;
import utils.BitSetUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        // 1. 使用采样策略获取样本行索引集合。
        if (strategy == null) {
            return calculateError(lhs, rhs, data, cache);
        }

        // The strategy is initialized by the caller
        
        Set<Integer> sampleRows = strategy.getSampleIndices();

        // 如果样本为空，则无法进行估计，返回0.0。
        if (sampleRows == null || sampleRows.isEmpty()) {
            return 0.0;
        }

        // LHS为空的特殊情况不需要处理，算法保证传入的LHS不为空。
        List<Integer> lhsColumnIndices = BitSetUtils.bitSetToList(lhs);

        // 2. 从PLICache中获取LHS各列基于完整数据集的属性向量。
        List<int[]> lhsAttributeVectors = new ArrayList<>(lhsColumnIndices.size());
        for (int colIndex : lhsColumnIndices) {
            BitSet singleColBitSet = new BitSet();
            singleColBitSet.set(colIndex);
            PLI singleColPli = cache.getOrCalculatePLI(singleColBitSet); // 获取单列PLI
            if (singleColPli == null) {
                // 理论上，在系统正常运行时，所有单列PLI都应该能获取到。
                throw new IllegalStateException("无法为LHS列获取PLI: " + colIndex +
                                                ". 请确保PLICache已正确初始化且数据一致.");
            }
            lhsAttributeVectors.add(singleColPli.toAttributeVector()); // 添加该列的属性向量
        }

        // 3. 从PLICache中获取RHS列基于完整数据集的属性向量。
        BitSet rhsBitSet = new BitSet();
        rhsBitSet.set(rhs);
        PLI rhsPli = cache.getOrCalculatePLI(rhsBitSet);
        if (rhsPli == null) {
            throw new IllegalStateException("无法为RHS列获取PLI: " + rhs +
                                            ". 请确保PLICache已正确初始化且数据一致.");
        }
        int[] rhsVector = rhsPli.toAttributeVector(); // RHS的属性向量

        // 4. 在样本行上构建LHS的等价类。
        // 键是LHS各列属性值（来自属性向量的簇ID）的列表，值是具有这些LHS值的行索引集合。
        Map<List<Integer>, Set<Integer>> lhsSampleEquivalenceClasses = new HashMap<>();
        for (int rowIndex : sampleRows) {
            List<Integer> currentLhsValues = new ArrayList<>(lhsColumnIndices.size());
            boolean skipRowDueToLhsSingleton = false; // 标记当前行是否因为LHS中某列为全局单例而应跳过

            // 遍历LHS的每一列，获取当前行在这些列上的属性向量值（即簇ID）
            for (int[] attrVec : lhsAttributeVectors) {
                int val = attrVec[rowIndex];
                if (val == 0) { // 如果该行在LHS的任何一列上是全局单例 (属性向量值为0)
                    skipRowDueToLhsSingleton = true; // 标记此行应跳过
                    break; // 无需检查此行LHS的其他列了，它在LHS组合上必然是全局单例
                }
                currentLhsValues.add(val); // 将非0的簇ID加入当前行的LHS值列表
            }

            if (skipRowDueToLhsSingleton) {
                continue; // 跳过此行，因为它在LHS上是全局单例，不会形成有效等价类
            }
            
            // 如果行未被跳过，则将其加入对应的LHS采样等价类中
            lhsSampleEquivalenceClasses.computeIfAbsent(currentLhsValues, k -> new HashSet<>()).add(rowIndex);
        }

        // 5. 基于样本LHS等价类计算冲突数，模拟G3度量的逻辑。
        long sampleViolations = 0; // 样本中的冲突总数
        for (Set<Integer> sampledClusterRows : lhsSampleEquivalenceClasses.values()) {
            int currentSampledLhsClusterSize = sampledClusterRows.size();

            // 如果采样LHS等价类的大小 <= 1，则它内部不可能有冲突。
            if (currentSampledLhsClusterSize <= 1) {
                continue;
            }

            // 统计当前采样LHS等价类中，RHS各属性值的频率。
            Map<Integer, Integer> freqCounter = new HashMap<>();
            for (int rowInCluster : sampledClusterRows) {
                int yVal = rhsVector[rowInCluster]; // 获取当前行RHS的整数编码值 (簇ID)
                if (yVal == 0) { // 如果RHS值是全局单例 (属性向量值为0)，则不参与频率计数
                    continue;
                }
                freqCounter.put(yVal, freqCounter.getOrDefault(yVal, 0) + 1);
            }

            int maxFreqInSampledCluster = 0; // 当前采样LHS等价类中，RHS出现次数最多的频率
            if (!freqCounter.isEmpty()) {
                maxFreqInSampledCluster = freqCounter.values().stream().max(Integer::compare).orElse(0);
            }

            // 应用G3度量的移除逻辑：
            // 如果maxFreq为0 (意味着此采样LHS等价类中所有行的RHS都是全局单例)，则移除数为 clusterSize - 1。
            // 否则，移除数为 clusterSize - maxFreq。
            if (maxFreqInSampledCluster == 0) {
                sampleViolations += (currentSampledLhsClusterSize - 1);
            } else {
                sampleViolations += (currentSampledLhsClusterSize - maxFreqInSampledCluster);
            }
        }

        // 6. 根据采样率将样本冲突数放大，以估算完整数据集上的总冲突数，并进行归一化。
        // 如果完整数据集的行数 <= 1，则误差为0，避免除以0的错误。
        if (data.getRowCount() <= 1) {
            return 0.0;
        }

        double sampleRate = (double) strategy.getSampleSize() / data.getRowCount(); // 计算采样率，使用理论样本总数
        // 再次检查sampleRate，以防万一。
        if (sampleRate == 0) { 
            return 0.0; 
        }

        double estimatedTotalViolations = sampleViolations / sampleRate; // 估算的总冲突数
        
        // 根据G3定义，用 (总行数 - 1) 进行归一化。
        return estimatedTotalViolations / (data.getRowCount() - 1);
    }
}
