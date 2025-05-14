
import measure.ErrorMeasure;
import sampling.SamplingStrategy;

public class PyroConfig {
    private final ErrorMeasure measure;
    private final SamplingStrategy samplingStrategy;
    private final double maxError;

    public PyroConfig(ErrorMeasure measure, SamplingStrategy samplingStrategy, double maxError) {
        this.measure = measure;
        this.samplingStrategy = samplingStrategy;
        this.maxError = maxError;
    }

    public ErrorMeasure getMeasure() {
        return measure;
    }

    public SamplingStrategy getSamplingStrategy() {
        return samplingStrategy;
    }

    public double getMaxError() {
        return maxError;
    }
}
