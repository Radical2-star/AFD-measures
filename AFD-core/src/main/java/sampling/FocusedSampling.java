package sampling;

import model.AutoTypeDataSet;
import model.DataSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 *  FocusedSampling
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class FocusedSampling implements SamplingStrategy {
    private Set<Integer> sampleIndices;
    private String samplingInfo;

    @Override
    public void initialize(DataSet data, double sampleParam) {
        // TODO: 完善聚焦采样逻辑（当前为随机采样的复制）
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
        return "聚焦采样(" + samplingInfo + ")";
    }
}
