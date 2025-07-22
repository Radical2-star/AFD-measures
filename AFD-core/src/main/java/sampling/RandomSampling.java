package sampling;

import model.DataSet;
import pli.PLICache;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 *  RandomSampling
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class RandomSampling implements SamplingStrategy {
    private Set<Integer> sampleIndices;
    private String samplingInfo;
    private int sampleSize;
    private final Random rand;

    public RandomSampling(long seed) {
        if (seed != 0) {
            this.rand = new Random(seed);
        } else {
            this.rand = new Random();
        }
    }

    public RandomSampling() {
        this.rand = new Random();
    }

    @Override
    public void initialize(DataSet data, PLICache cache, BitSet lhs, int rhs, double sampleParam) {
        int totalRows = data.getRowCount();

        // 参数解释逻辑
        if (sampleParam < 1) {
            // 按比例采样
            sampleSize = (int) (totalRows * sampleParam);
            samplingInfo = "比例: " + sampleParam;
        } else {
            // 按固定数量采样
            sampleSize = (int) Math.min(sampleParam, totalRows);
            samplingInfo = "数量: " + sampleSize;
        }

        sampleIndices = new HashSet<>();
        while (sampleIndices.size() < sampleSize) {
            int row = rand.nextInt(totalRows);
            sampleIndices.add(row);
        }
    }

    @Override
    public Set<Integer> getSampleIndices() {
        return sampleIndices;
    }

    @Override
    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    public String getSamplingInfo() {
        return "随机采样(" + samplingInfo + ")";
    }
}
