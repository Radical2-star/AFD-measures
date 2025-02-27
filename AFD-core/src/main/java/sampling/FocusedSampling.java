package sampling;

import model.DataSet;

import java.util.ArrayList;
import java.util.List;

/**
 *  FocusedSampling
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class FocusedSampling implements SamplingStrategy {
    @Override
    public List<Integer> createSamples(DataSet dataset, int sampleSize) {
        List<Integer> samples = new ArrayList<>();
        // TODO: implement focused sampling logic
        return samples;
    }
}
