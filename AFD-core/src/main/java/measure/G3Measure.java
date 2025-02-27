package measure;

import model.DataSet;
import model.FunctionalDependency;

/**
 * @ClassName G3Measure
 * @Description
 * @Author Zuoxing Xie
 * @Time 2025/2/26
 * @Version 1.0
 */
public class G3Measure implements ErrorMeasure {
    @Override
    public double calculateError(DataSet dataset, FunctionalDependency fd) {
        return 0.0;
    }
}
