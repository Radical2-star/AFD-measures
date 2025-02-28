package sampling;

import model.AutoTypeDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 *  RandomSampling
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class RandomSampling implements SamplingStrategy {
    @Override
    public List<Integer> createSamples(AutoTypeDataSet dataset, int sampleSize) {
        List<Integer> samples = new ArrayList<>();
        // TODO: implement random sampling logic
        return samples;
    }
}
