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
 *  G3Measure
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class G3Measure implements ErrorMeasure {
    @Override
    public double calculateError(BitSet lhs, int rhs, DataSet data, PLICache cache) {
        // 获取rhs对应的PLI（单列）及其属性向量
        BitSet rhsBitSet = new BitSet();
        rhsBitSet.set(rhs);
        PLI rhsPLI = cache.getOrCalculatePLI(rhsBitSet);

        // 处理根节点的情况：UCC
        if (lhs.isEmpty()) {
            // 遍历rhsPLI中的所有簇，分别计算最大有效行数并求和
            // TODO: 补充根节点的特殊情况
        }

        int[] vY = rhsPLI.toAttributeVector(); // 属性向量

        // 获取lhs对应的PLI
        PLI lhsPLI = cache.getOrCalculatePLI(lhs);

        int totalRows = data.getRowCount();
        if (totalRows <= 1) return 0.0;

        int maxValidRows = 0;

        // 遍历lhs的每个等价类
        // TODO: 计算逻辑有问题，应该对每个簇统计要删除的元组数，否则单例直接没了
        for (Set<Integer> cluster : lhsPLI.getEquivalenceClasses()) {
            Map<Integer, Integer> yClusterCounter = new HashMap<>();
            for (int row : cluster) {
                int yClusterId = vY[row];
                if (yClusterId != 0) { // 忽略单例
                    yClusterCounter.merge(yClusterId, 1, Integer::sum);
                }
            }

            // 取当前簇中最大的Y簇计数
            maxValidRows += yClusterCounter.values().stream()
                    .mapToInt(v -> v)
                    .max()
                    .orElse(0); // 若全为单例则加0
        }

        // 处理全表单例的情况（至少保留1行）
        if (maxValidRows == 0) maxValidRows = 1;

        // 计算公式: 1 - (maxValidRows - 1)/(totalRows - 1)
        double ratio = (double) (maxValidRows - 1) / (totalRows - 1);
        return 1.0 - ratio;
    }

    @Override
    public double estimateError(BitSet lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        return calculateError(lhs, rhs, data, cache);
    }
}