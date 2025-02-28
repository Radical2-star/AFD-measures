package measure;

import model.DataSet;
import model.FunctionalDependency;
import pli.PLICache;

/**
 *  AbstractMeasure
 *
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public interface ErrorMeasure {
    double calculateError(DataSet data,
                          FunctionalDependency fd,
                          PLICache cache);
}