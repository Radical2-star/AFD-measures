package sampling;

import model.DataSet;
import pli.PLICache;
import utils.LongBitSetUtils;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * RandomSampling - 随机采样策略
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
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

    // ==================== 新的long版本方法（性能优化） ====================

    /**
     * 初始化随机采样策略（long版本，性能更优）
     * 注意：lhs参数在随机采样中未实际使用，但保持接口一致性
     *
     * @param data 原始数据集
     * @param cache PLI缓存
     * @param lhs 左手边属性集（long表示，支持最多64列）- 未使用
     * @param rhs 右手边属性 - 未使用
     * @param sampleParam 采样参数（可以是比例或固定数量）
     */
    @Override
    public void initialize(DataSet data, PLICache cache, long lhs, int rhs, double sampleParam) {
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

    // ==================== 原有BitSet版本方法（兼容性） ====================

    /**
     * 初始化随机采样策略（BitSet版本，兼容性方法）
     * 注意：lhs参数在随机采样中未实际使用，但保持接口一致性
     *
     * @param data 原始数据集
     * @param cache PLI缓存
     * @param lhs 左手边属性集 - 未使用
     * @param rhs 右手边属性 - 未使用
     * @param sampleParam 采样参数（可以是比例或固定数量）
     */
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
