package sampling;

import model.DataSet;
import pli.PLICache;

import java.util.BitSet;
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
     * @param cache        PLI缓存
     * @param lhs          左手边属性集
     * @param rhs          右手边属性
     * @param sampleParam  采样参数（可以是比例或固定数量）
     */
    void initialize(DataSet data, PLICache cache, BitSet lhs, int rhs, double sampleParam);

    /**
     * 获取采样行索引集合
     */
    Set<Integer> getSampleIndices();

    /**
     * 获取理论样本总数（包括单例）
     * @return 理论上应该采样的总行数
     */
    int getSampleSize();

    /**
     * 获取采样参数说明（如比例0.2或数量100）
     */
    String getSamplingInfo();
}
