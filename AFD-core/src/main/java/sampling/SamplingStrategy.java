package sampling;

import model.AutoTypeDataSet;
import model.DataSet;

import java.util.List;
import java.util.Set;

/**
 *  SamplingStrategy
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public interface SamplingStrategy {
    /**
     * 初始化采样策略
     * @param data         原始数据集
     * @param sampleParam  采样参数（可以是比例或固定数量）
     */
    void initialize(DataSet data, double sampleParam);

    /**
     * 获取采样行索引集合
     */
    Set<Integer> getSampleIndices();

    /**
     * 获取采样参数说明（如比例0.2或数量100）
     */
    String getSamplingInfo();
}
