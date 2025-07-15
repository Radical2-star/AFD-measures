package experiment.finder;

import algorithm.TaneAlgorithm;
import experiment.ExperimentConfig;
import experiment.ExperimentResult;
import model.DataSet;
import model.FunctionalDependency;

import java.util.HashSet;
import java.util.List;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class TaneExecutor implements AFDFinder {
    @Override
    public ExperimentResult discover(DataSet dataset, ExperimentConfig config) {
        TaneAlgorithm tane = new TaneAlgorithm(dataset, config.getMeasure(), config.getMaxError(), config.isVerbose());
        List<FunctionalDependency> fds = tane.discover();

        // Note: TANE does not support sampling strategies from the config in this setup.
        // It also does not use a random seed directly.

        return new ExperimentResult(
                new HashSet<>(fds),
                tane.getExecutionTimeMs(),
                tane.getValidationCount()
        );
    }
}
