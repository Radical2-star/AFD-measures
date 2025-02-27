package sampling;

import model.DataSet;

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
    public List<Integer> createSamples(DataSet dataset, int sampleSize) {
        List<Integer> samples = new ArrayList<>();
        // TODO: implement random sampling logic
        return samples;
    }
}
