package measure;

import model.DataSet;
import pli.PLICache;
import sampling.SamplingStrategy;

import java.util.BitSet;

/**
 *  AbstractMeasure
 *
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public interface ErrorMeasure {
    double calculateError(BitSet lhs,
                          int rhs,
                          DataSet data,
                          PLICache cache);
    double estimateError(BitSet lhs,
                         int rhs,
                         DataSet data,
                         PLICache cache,
                         SamplingStrategy strategy
    );
}