package experiment;

import measure.ErrorMeasure;
import sampling.SamplingStrategy;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class ExperimentConfig {
    private final String configName;
    private final ErrorMeasure measure;
    private final Class<? extends SamplingStrategy> samplingStrategyClass; // Can be null if not applicable
    private final double maxError;
    private final double sampleParam; // Can be ratio or fixed number
    private final Long randomSeed;
    private final boolean useDynamicThreshold;
    private final boolean verbose;

    public ExperimentConfig(String configName, ErrorMeasure measure, Class<? extends SamplingStrategy> samplingStrategyClass, double maxError, double sampleParam, Long randomSeed, boolean useDynamicThreshold, boolean verbose) {
        this.configName = configName;
        this.measure = measure;
        this.samplingStrategyClass = samplingStrategyClass;
        this.maxError = maxError;
        this.sampleParam = sampleParam;
        this.randomSeed = randomSeed;
        this.useDynamicThreshold = useDynamicThreshold;
        this.verbose = verbose;
    }

    public String getConfigName() {
        return configName;
    }

    public ErrorMeasure getMeasure() {
        return measure;
    }

    public Class<? extends SamplingStrategy> getSamplingStrategyClass() {
        return samplingStrategyClass;
    }

    public double getMaxError() {
        return maxError;
    }

    public double getSampleParam() {
        return sampleParam;
    }

    public Long getRandomSeed() {
        return randomSeed;
    }

    public boolean isUseDynamicThreshold() {
        return useDynamicThreshold;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public String toString() {
        return "ExperimentConfig{" +
                "configName='" + configName + '\'' +
                ", measure=" + measure.getClass().getSimpleName() +
                ", samplingStrategyClass=" + (samplingStrategyClass != null ? samplingStrategyClass.getSimpleName() : "N/A") +
                ", maxError=" + maxError +
                ", sampleParam=" + sampleParam +
                ", randomSeed=" + randomSeed +
                ", useDynamicThreshold=" + useDynamicThreshold +
                ", verbose=" + verbose +
                '}';
    }
}
