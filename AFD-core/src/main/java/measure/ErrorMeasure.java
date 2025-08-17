package measure;

import model.DataSet;
import pli.PLICache;
import sampling.SamplingStrategy;

import java.util.BitSet;

/**
 *  ErrorMeasure接口 - 支持BitSet和long两种位集合表示
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/2/26
 */
public interface ErrorMeasure {

    // ==================== 新的long版本方法（推荐使用） ====================

    /**
     * 计算函数依赖的错误率（long版本，性能更优）
     * @param lhs 左手边属性集（long表示，支持最多64列）
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @return 错误率
     */
    default double calculateError(long lhs, int rhs, DataSet data, PLICache cache) {
        // 默认实现：转换为BitSet调用原方法
        BitSet lhsBitSet = utils.LongBitSetUtils.longToBitSet(lhs, data.getColumnCount());
        return calculateError(lhsBitSet, rhs, data, cache);
    }

    /**
     * 估计函数依赖的错误率（long版本，性能更优）
     * @param lhs 左手边属性集（long表示，支持最多64列）
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @param strategy 采样策略
     * @return 估计的错误率
     */
    default double estimateError(long lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy) {
        // 默认实现：转换为BitSet调用原方法
        BitSet lhsBitSet = utils.LongBitSetUtils.longToBitSet(lhs, data.getColumnCount());
        return estimateError(lhsBitSet, rhs, data, cache, strategy);
    }

    // ==================== 原有BitSet版本方法（保持兼容性） ====================

    /**
     * 计算函数依赖的错误率（BitSet版本，兼容性方法）
     * @param lhs 左手边属性集
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @return 错误率
     */
    double calculateError(BitSet lhs, int rhs, DataSet data, PLICache cache);

    /**
     * 估计函数依赖的错误率（BitSet版本，兼容性方法）
     * @param lhs 左手边属性集
     * @param rhs 右手边属性
     * @param data 数据集
     * @param cache PLI缓存
     * @param strategy 采样策略
     * @return 估计的错误率
     */
    double estimateError(BitSet lhs, int rhs, DataSet data, PLICache cache, SamplingStrategy strategy);
}