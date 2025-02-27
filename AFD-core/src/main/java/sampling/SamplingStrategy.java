package sampling;

import model.DataSet;

import java.util.List;

/**
 * @ClassName SamplingStrategy
 * @Description
 * @Author Zuoxing Xie
 * @Time 2025/2/26
 * @Version 1.0
 */
public interface SamplingStrategy {
    /**
     * 创建样本集合
     * @param dataset 完整数据集
     * @param sampleSize 期望样本大小
     * @return 样本行索引列表
     */
    List<Integer> createSamples(DataSet dataset, int sampleSize);
}