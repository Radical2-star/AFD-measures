package experiment.finder;

import algorithm.TaneAlgorithm;
import experiment.ExperimentConfig;
import experiment.ExperimentResult;
import model.DataSet;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class TaneExecutor implements AFDFinder {
    @Override
    public ExperimentResult discover(DataSet dataset, ExperimentConfig config) {
        TaneAlgorithm tane = new TaneAlgorithm();

        // Note: TANE does not support sampling strategies from the config in this setup.
        // It also does not use a random seed directly.
        tane.run(dataset, config.getMaxError(), config.getMeasure(), config.isVerbose());

        return new ExperimentResult(
                tane.getFDSet(),
                tane.getExecutionTimeMs(),
                tane.getValidationCount()
        );
    }
}
