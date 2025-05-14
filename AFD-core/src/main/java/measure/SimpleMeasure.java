package measure;

import model.DataSet;
import pli.PLICache;
import sampling.SamplingStrategy;

import java.util.BitSet;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/29
 */
public class SimpleMeasure implements ErrorMeasure{
    @Override
    public double calculateError(BitSet lhs, int rhs, DataSet data, PLICache cache) {
        return 1d - (double) lhs.cardinality() / data.getColumnCount();
    }
    @Override
    public double estimateError(BitSet lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        return 1d - (double) lhs.cardinality() / data.getColumnCount();
    }
}
