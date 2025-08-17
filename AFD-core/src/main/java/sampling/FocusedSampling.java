package sampling;

import model.DataSet;
import pli.PLI;
import pli.PLICache;
import utils.LongBitSetUtils;

import java.util.*;

/**
 * FocusedSampling - 聚焦采样策略
 * 通过PLI中的等价类簇进行分层采样，忽略单例元组
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/2/26
 */
public class FocusedSampling implements SamplingStrategy {
    private Set<Integer> sampleIndices;
    private String samplingInfo;
    private int sampleSize; // 理论样本总数
    private final Random random;

    public FocusedSampling(long seed) {
        this.random = new Random(seed);
    }

    public FocusedSampling() {
        this.random = new Random();
    }

    // ==================== 新的long版本方法（性能优化） ====================

    /**
     * 初始化聚焦采样策略（long版本，性能更优）
     * 通过PLI中的等价类簇进行分层采样，忽略单例元组
     *
     * @param data 原始数据集
     * @param cache PLI缓存
     * @param lhs 左手边属性集（long表示，支持最多64列）
     * @param rhs 右手边属性
     * @param sampleParam 采样参数（可以是比例或固定数量）
     */
    @Override
    public void initialize(DataSet data, PLICache cache, long lhs, int rhs, double sampleParam) {
        int totalRows = data.getRowCount();

        // 计算理论样本总数
        if (sampleParam < 1) {
            // 按比例采样
            sampleSize = (int) (totalRows * sampleParam);
            samplingInfo = "比例: " + sampleParam;
        } else {
            // 按固定数量采样
            sampleSize = (int) Math.min(sampleParam, totalRows);
            samplingInfo = "数量: " + sampleSize;
        }

        // 如果LHS为空或样本数为0，返回空集
        if (LongBitSetUtils.isEmpty(lhs) || sampleSize == 0) {
            sampleIndices = Collections.emptySet();
            return;
        }

        // 步骤1：查找LHS的最佳子集PLI
        BitSet lhsBitSet = LongBitSetUtils.longToBitSet(lhs, data.getColumnCount());
        PLI subsetPli = cache.findBestCachedSubsetPli(lhsBitSet);

        // 步骤2：按簇分组采样
        sampleIndices = sampleByCluster(subsetPli, sampleSize);
    }

    // ==================== 原有BitSet版本方法（兼容性） ====================

    /**
     * 初始化聚焦采样策略（BitSet版本，兼容性方法）
     * 通过PLI中的等价类簇进行分层采样，忽略单例元组
     *
     * @param data 原始数据集
     * @param cache PLI缓存
     * @param lhs 左手边属性集
     * @param rhs 右手边属性
     * @param sampleParam 采样参数（可以是比例或固定数量）
     */
    @Override
    public void initialize(DataSet data, PLICache cache, BitSet lhs, int rhs, double sampleParam) {
        int totalRows = data.getRowCount();
        
        // 计算理论样本总数
        if (sampleParam < 1) {
            // 按比例采样
            sampleSize = (int) (totalRows * sampleParam);
            samplingInfo = "比例: " + sampleParam;
        } else {
            // 按固定数量采样
            sampleSize = (int) Math.min(sampleParam, totalRows);
            samplingInfo = "数量: " + sampleSize;
        }
        
        // 如果LHS为空或样本数为0，返回空集
        if (lhs.isEmpty() || sampleSize == 0) {
            sampleIndices = Collections.emptySet();
            return;
        }
        
        // 步骤1：查找LHS的最佳子集PLI
        PLI subsetPli = cache.findBestCachedSubsetPli(lhs);
        
        // 步骤2：按簇分组采样
        sampleIndices = sampleByCluster(subsetPli, sampleSize);
    }
    
    /**
     * 按簇比例采样
     * @param pli PLI对象，包含等价类簇
     * @param targetSampleSize 目标样本总数
     * @return 采样得到的行索引集合
     */
    private Set<Integer> sampleByCluster(PLI pli, int targetSampleSize) {
        Set<Integer> result = new HashSet<>();
        List<Set<Integer>> clusters = pli.getEquivalenceClasses();
        
        // 如果没有非单例簇，返回空集
        if (clusters.isEmpty()) {
            return result;
        }
        
        // 计算所有非单例元组的总数
        int totalNonSingletons = clusters.stream()
                .mapToInt(Set::size)
                .sum();
        
        // 如果非单例元组数量小于等于目标样本数，全部采样
        if (totalNonSingletons <= targetSampleSize) {
            for (Set<Integer> cluster : clusters) {
                result.addAll(cluster);
            }
            return result;
        }
        
        // 按比例分配样本到各个簇
        for (Set<Integer> cluster : clusters) {
            int clusterSize = cluster.size();
            // 计算应分配给该簇的样本数
            int clusterSampleSize = Math.max(1, (int) Math.round((double) clusterSize / totalNonSingletons * targetSampleSize));
            
            // 从该簇中随机抽取样本
            List<Integer> clusterRows = new ArrayList<>(cluster);
            if (clusterSampleSize >= clusterSize) {
                // 如果分配的样本数大于等于簇大小，全部采样
                result.addAll(cluster);
            } else {
                // 随机采样
                for (int i = 0; i < clusterSampleSize; i++) {
                    int randomIndex = random.nextInt(clusterRows.size());
                    result.add(clusterRows.remove(randomIndex));
                }
            }
        }
        
        return result;
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
        return "聚焦采样(" + samplingInfo + ")";
    }
}
