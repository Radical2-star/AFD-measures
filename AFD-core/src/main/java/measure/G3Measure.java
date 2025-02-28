package measure;

import model.AutoTypeDataSet;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLI;
import pli.PLICache;

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
    public double calculateError(DataSet data,
                                 FunctionalDependency fd,
                                 PLICache cache) {
        Set<Integer> lhs = fd.getLhs();
        int rhs = fd.getRhs();

        // 获取左侧列的PLI
        PLI lhsPLI = cache.getPLI(lhs);

        // 计算最大满足子集的大小
        long maxConsistent = calculateMaxConsistentSubset(lhsPLI, data, rhs);
        long total = data.getRowCount();

        return 1.0 - (double) maxConsistent / total;
    }

    private long calculateMaxConsistentSubset(PLI lhsPLI, DataSet data, int rhs) {
        long total = 0L;
        Set<Integer> coveredRows = new HashSet<>();

        // 统计等价类中的最大一致数
        for (Set<Integer> cluster : lhsPLI.getEquivalenceClasses()) {
            Map<String, Integer> rhsCounts = new HashMap<>();
            for (int row : cluster) {
                coveredRows.add(row);
                String value = data.getValue(row, rhs);
                rhsCounts.put(value, rhsCounts.getOrDefault(value, 0) + 1);
            }
            total += rhsCounts.values().stream()
                    .mapToInt(i -> i)
                    .max()
                    .orElse(0);
        }

        // 添加未被PLI覆盖的行（单例行）
        int singletonCount = data.getRowCount() - coveredRows.size();
        total += singletonCount;

        return total;
    }

}