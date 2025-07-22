package experiment.finder;

import experiment.ExperimentConfig;
import experiment.ExperimentResult;
import model.DataSet;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public interface AFDFinder {
    /**
     * Run the AFD discovery algorithm.
     *
     * @param dataset The dataset to run on.
     * @param config The configuration for the experiment.
     * @return An ExperimentResult object containing the findings.
     */
    ExperimentResult discover(DataSet dataset, ExperimentConfig config);
}
