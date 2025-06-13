package measure;

import model.DataSet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DynamicThreshold {

    public static double computeAlpha(DataSet dataset,
                               List<Integer> leftIndices,
                               List<Integer> rightIndices) {
        //去重
        Set<Integer> combined = new HashSet<>();
        combined.addAll(leftIndices);
        combined.addAll(rightIndices);

        double alpha = 0.0;
        for (int index : combined) {
            if (index < 0 || index >= dataset.getColumnCount()) {
                throw new IllegalArgumentException("属性索引非法: " + index);
            }
            alpha += dataset.getErrorRate(index);
        }
        return alpha;
    }
}
