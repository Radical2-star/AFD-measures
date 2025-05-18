package sampling;

import model.AutoTypeDataSet;
import model.DataSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Override
    public void initialize(DataSet data, double sampleParam) {
        int totalRows = data.getRowCount();
        int sampleSize;

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

        Random rand = new Random();
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
    public String getSamplingInfo() {
        return "随机采样(" + samplingInfo + ")";
    }
}
