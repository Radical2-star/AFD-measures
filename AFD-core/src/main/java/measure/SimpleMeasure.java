package measure;

import model.DataSet;
import pli.PLICache;
import sampling.SamplingStrategy;
import utils.LongBitSetUtils;

import java.util.BitSet;

/**
 * SimpleMeasure - 简单的错误度量实现
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/4/29
 */
public class SimpleMeasure implements ErrorMeasure{

    // ==================== 新的long版本方法（性能优化） ====================

    @Override
    public double calculateError(long lhs, int rhs, DataSet data, PLICache cache) {
        return 1d - (double) LongBitSetUtils.cardinality(lhs) / data.getColumnCount();
    }

    @Override
    public double estimateError(long lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        return 1d - (double) LongBitSetUtils.cardinality(lhs) / data.getColumnCount();
    }

    // ==================== 原有BitSet版本方法（兼容性） ====================

    @Override
    public double calculateError(BitSet lhs, int rhs, DataSet data, PLICache cache) {
        return 1d - (double) lhs.cardinality() / data.getColumnCount();
    }

    @Override
    public double estimateError(BitSet lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        return 1d - (double) lhs.cardinality() / data.getColumnCount();
    }
}
