package experiment.finder;

import algorithm.Pyro;
import experiment.ExperimentConfig;
import experiment.ExperimentResult;
import model.DataSet;
import model.FunctionalDependency;
import sampling.SamplingStrategy;

import java.util.HashSet;
import java.util.List;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class PyroExecutor implements AFDFinder {
    @Override
    public ExperimentResult discover(DataSet dataset, ExperimentConfig config) {
        // Instantiate sampling strategy with the given seed
        SamplingStrategy samplingStrategy = null;
        if (config.getSamplingStrategyClass() != null) {
            try {
                if (config.getRandomSeed() != null) {
                    // Use constructor with seed
                    samplingStrategy = config.getSamplingStrategyClass()
                            .getConstructor(long.class).newInstance(config.getRandomSeed());
                } else {
                    // Use default (no-arg) constructor
                    samplingStrategy = config.getSamplingStrategyClass().getConstructor().newInstance();
                }
            } catch (Exception e) {
                throw new RuntimeException("Could not instantiate sampling strategy: " + config.getSamplingStrategyClass().getSimpleName(), e);
            }
        }


        Pyro pyro = new Pyro(
                dataset,
                config.getMeasure(),
                samplingStrategy,
                config.getMaxError(),
                config.getSampleParam(),
                config.isVerbose()
        );

        List<FunctionalDependency> fds = pyro.discover();

        return new ExperimentResult(
                new HashSet<>(fds),
                pyro.getExecutionTimeMs(),
                pyro.getValidationCount()
        );
    }
}
