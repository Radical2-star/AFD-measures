package sampling;

import model.AutoTypeDataSet;

import java.util.List;

/**
 *  SamplingStrategy
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public interface SamplingStrategy {
    /**
     * 创建样本集合
     * @param dataset 完整数据集
     * @param sampleSize 期望样本大小
     * @return 样本行索引列表
     */
    List<Integer> createSamples(AutoTypeDataSet dataset, int sampleSize);
}