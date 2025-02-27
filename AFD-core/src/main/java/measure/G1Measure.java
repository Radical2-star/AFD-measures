package measure;

import model.DataSet;
import model.FunctionalDependency;

/**
 *  G1Measure
 *
 * @author Hoshi
 * @Time 2025/2/26
 * @version 1.0
 */
public class G1Measure implements ErrorMeasure {
    @Override
    public double calculateError(DataSet dataset, FunctionalDependency fd) {
        return 0.0;
    }
}
