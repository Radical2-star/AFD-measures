package measure;

import model.DataSet;
import model.FunctionalDependency;

/**
 *  G3Measure
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class G3Measure implements ErrorMeasure {
    @Override
    public double calculateError(DataSet dataset, FunctionalDependency fd) {
        return 0.0;
    }
}
