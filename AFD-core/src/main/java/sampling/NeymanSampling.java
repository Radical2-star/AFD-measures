package sampling;

import model.DataSet;
import pli.PLI;
import pli.PLICache;
import utils.LongBitSetUtils;

import java.util.*;
import java.util.function.Function;

/**
 * NeymanSampling - 基于Neyman最优分配的聚焦采样策略
 * 通过两阶段采样方法，先进行试点采样估计方差，再按Neyman公式分配样本
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/6/16
 */
public class NeymanSampling implements SamplingStrategy {
    private Set<Integer> sampleIndices;
    private String samplingInfo;
    private int sampleSize; // 理论样本总数
    private final Random random;

    // 缓存相关：用于存储预计算的方差信息
    private Function<Integer, List<Double>> varianceCacheAccessor;
    
    public NeymanSampling(long seed) {
        this.random = new Random(seed);
    }

    public NeymanSampling() {
        this.random = new Random();
    }
    
    /**
     * 设置方差缓存访问器（由SearchSpace调用）
     * @param accessor 缓存访问函数
     */
    public void setVarianceCacheAccessor(Function<Integer, List<Double>> accessor) {
        this.varianceCacheAccessor = accessor;
    }

    // 试点采样的最大样本数
    private static final int MAX_PILOT_SAMPLE_SIZE = 20;
    
    /**
     * 预计算单列PLI的方差信息（供SearchSpace调用）
     * @param pli 单列PLI
     * @param data 数据集
     * @param rhs 右手边属性索引
     * @return 每个簇的方差列表，按PLI中等价类的顺序
     */
    public List<Double> precomputeColumnVariances(PLI pli, DataSet data, int rhs) {
        List<Set<Integer>> clusters = pli.getEquivalenceClasses();
        List<Double> variances = new ArrayList<>();
        
        if (clusters.isEmpty()) {
            return variances;
        }
        
        for (Set<Integer> cluster : clusters) {
            // 执行试点采样
            Set<Integer> pilotSample = performPilotSampling(cluster);
            
            // 计算方差
            double variance = calculateClusterVariance(pilotSample, data, rhs);
            variances.add(variance);
        }
        
        return variances;
    }
    
    /**
     * 执行试点采样（内部方法）- 优化版本，减少集合转换和对象创建
     */
    private Set<Integer> performPilotSampling(Set<Integer> cluster) {
        // 计算试点样本数量：min(sqrt(簇大小), MAX_PILOT_SAMPLE_SIZE)
        int pilotSize = Math.min(MAX_PILOT_SAMPLE_SIZE, (int) Math.sqrt(cluster.size()));
        
        // 如果试点样本数量大于等于簇大小，全部采样
        if (pilotSize >= cluster.size()) {
            return new HashSet<>(cluster);
        }
        
        // 优化的随机采样：避免频繁的remove操作
        Set<Integer> pilotSample = new HashSet<>(pilotSize);
        Integer[] clusterArray = cluster.toArray(new Integer[0]);
        
        // 使用Fisher-Yates洗牌算法的简化版本
        for (int i = 0; i < pilotSize; i++) {
            int randomIndex = random.nextInt(clusterArray.length - i);
            pilotSample.add(clusterArray[randomIndex]);
            // 将选中的元素交换到末尾，避免重复选择
            Integer temp = clusterArray[randomIndex];
            clusterArray[randomIndex] = clusterArray[clusterArray.length - 1 - i];
            clusterArray[clusterArray.length - 1 - i] = temp;
        }
        
        return pilotSample;
    }
    
    /**
     * 计算簇内方差（内部方法）- 优化版本，避免Stream API开销
     */
    private double calculateClusterVariance(Set<Integer> pilotSample, DataSet data, int rhs) {
        if (pilotSample.size() <= 1) {
            return 0.0;
        }
        
        // 1. 高效查找主流RHS值 - 用手动实现替代Stream API
        Map<String, Integer> valueCount = new HashMap<>();
        String majorityValue = "";
        int maxCount = 0;
        
        for (int row : pilotSample) {
            String value = data.getValue(row, rhs);
            int count = valueCount.getOrDefault(value, 0) + 1;
            valueCount.put(value, count);
            
            if (count > maxCount) {
                maxCount = count;
                majorityValue = value;
            }
        }

        // 2. 直接计算方差，避免创建中间列表
        double sum = 0.0;
        double sumSquares = 0.0;
        int sampleSize = pilotSample.size();
        
        for (int row : pilotSample) {
            int indicator = data.getValue(row, rhs).equals(majorityValue) ? 0 : 1;
            sum += indicator;
            sumSquares += indicator * indicator;
        }
        
        // 3. 使用数学公式直接计算样本方差: s^2 = (sumSquares - n*mean^2) / (n-1)
        double mean = sum / sampleSize;
        return (sumSquares - sampleSize * mean * mean) / (sampleSize - 1);
    }

    // ==================== 新的long版本方法（性能优化） ====================

    /**
     * 初始化Neyman最优采样策略（long版本，性能更优）
     * 通过两阶段采样方法，先进行试点采样估计方差，再按Neyman公式分配样本
     *
     * @param data 原始数据集
     * @param cache PLI缓存
     * @param lhs 左手边属性集（long表示，支持最多64列）
     * @param rhs 右手边属性
     * @param sampleParam 采样参数（可以是比例或固定数量）
     */
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

        // 优化：选择LHS中size最小的单列PLI进行采样
        PLI selectedPli = selectMinimalSizePliLong(lhs, cache, rhs);

        // 执行优化后的两阶段采样
        sampleIndices = optimizedTwoPhaseNeymanSampling(selectedPli, data, rhs, sampleSize);
    }

    /**
     * 选择LHS中size最小的单列PLI（long版本）
     * @param lhs 左手边属性集合（long表示）
     * @param cache PLI缓存
     * @param rhs 右手边属性索引
     * @return size最小的单列PLI
     */
    private PLI selectMinimalSizePliLong(long lhs, PLICache cache, int rhs) {
        PLI minPli = null;
        int minSize = Integer.MAX_VALUE;

        try {
            // 使用传统for循环遍历lhs中的每一位
            for (int col = 0; col < 64; col++) {
                // 检查该位是否被设置
                if (!LongBitSetUtils.testBit(lhs, col)) continue;
                // 跳过RHS列
                if (col == rhs) continue;

                try {
                    // 获取单列PLI
                    List<Integer> key = new ArrayList<>();
                    key.add(col);
                    PLI pli = cache.get(key);

                    // 找到size更小的PLI
                    if (pli != null && pli.size() < minSize) {
                        minSize = pli.size();
                        minPli = pli;
                    }
                } catch (Exception e) {
                    // 单列PLI获取失败，跳过这一列
                    continue;
                }
            }
        } catch (Exception e) {
            // 整个过程失败，回退到原始方法
            BitSet lhsBitSet = LongBitSetUtils.longToBitSet(lhs, 64);
            return cache.findBestCachedSubsetPli(lhsBitSet);
        }

        // 如果没有找到合适的单列PLI，回退到原始方法
        if (minPli == null) {
            BitSet lhsBitSet = LongBitSetUtils.longToBitSet(lhs, 64);
            return cache.findBestCachedSubsetPli(lhsBitSet);
        }

        return minPli;
    }

    // ==================== 原有BitSet版本方法（兼容性） ====================

    /**
     * 初始化Neyman最优采样策略（BitSet版本，兼容性方法）
     * 通过两阶段采样方法，先进行试点采样估计方差，再按Neyman公式分配样本
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
        
        // 优化：选择LHS中size最小的单列PLI进行采样
        PLI selectedPli = selectMinimalSizePli(lhs, cache, rhs);
        
        // 执行优化后的两阶段采样
        sampleIndices = optimizedTwoPhaseNeymanSampling(selectedPli, data, rhs, sampleSize);
    }
    
    /**
     * 选择LHS中size最小的单列PLI
     * @param lhs 左手边属性集合
     * @param cache PLI缓存
     * @param rhs 右手边属性索引
     * @return size最小的单列PLI
     */
    private PLI selectMinimalSizePli(BitSet lhs, PLICache cache, int rhs) {
        PLI minPli = null;
        int minSize = Integer.MAX_VALUE;
        
        try {
            for (int col = lhs.nextSetBit(0); col >= 0; col = lhs.nextSetBit(col + 1)) {
                if (col == rhs) continue; // 跳过RHS列
                
                try {
                    // 获取单列PLI
                    BitSet singleColumn = new BitSet();
                    singleColumn.set(col);
                    List<Integer> key = new ArrayList<>();
                    key.add(col);
                    PLI pli = cache.get(key);
                    
                    if (pli != null && pli.size() < minSize) {
                        minSize = pli.size();
                        minPli = pli;
                    }
                } catch (Exception e) {
                    // 单列PLI获取失败，跳过这一列
                    continue;
                }
            }
        } catch (Exception e) {
            // 整个过程失败，回退到原始方法
            return cache.findBestCachedSubsetPli(lhs);
        }
        
        // 如果没有找到合适的单列PLI，回退到原始方法
        if (minPli == null) {
            return cache.findBestCachedSubsetPli(lhs);
        }
        
        return minPli;
    }
    
    /**
     * 优化后的两阶段Neyman采样（尝试使用缓存的方差信息）
     * @param pli 选择的PLI对象
     * @param data 数据集对象
     * @param rhs 右手边属性索引
     * @param targetSampleSize 目标样本总数
     * @return 采样得到的行索引集合
     */
    private Set<Integer> optimizedTwoPhaseNeymanSampling(PLI pli, DataSet data, int rhs, int targetSampleSize) {
        List<Set<Integer>> clusters = pli.getEquivalenceClasses();
        Set<Integer> result = new HashSet<>();
        
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
        
        // 尝试使用缓存的方差信息，如果没有则回退到原始方法
        Map<Integer, Double> clusterVariances = getCachedVariances(pli, data, rhs);
        if (clusterVariances == null) {
            // 回退到原始的两阶段采样
            return twoPhaseNeymanSampling(pli, data, rhs, targetSampleSize);
        }
        
        // 使用缓存的方差信息进行Neyman最优分配
        Map<Integer, Integer> allocations = calculateAllocation(
                clusters, clusterVariances, targetSampleSize);
        
        // 执行主采样
        for (int i = 0; i < clusters.size(); i++) {
            Set<Integer> cluster = clusters.get(i);
            int allocation = allocations.get(i);
            
            // 如果分配数量大于等于簇大小，全部采样
            if (allocation >= cluster.size()) {
                result.addAll(cluster);
                continue;
            }
            
            // 优化的随机采样：避免频繁的remove操作
            Integer[] clusterArray = cluster.toArray(new Integer[0]);
            
            // 使用Fisher-Yates洗牌算法的简化版本
            for (int j = 0; j < allocation; j++) {
                int randomIndex = random.nextInt(clusterArray.length - j);
                result.add(clusterArray[randomIndex]);
                // 将选中的元素交换到末尾，避免重复选择
                Integer temp = clusterArray[randomIndex];
                clusterArray[randomIndex] = clusterArray[clusterArray.length - 1 - j];
                clusterArray[clusterArray.length - 1 - j] = temp;
            }
        }
        
        return result;
    }
    
    /**
     * 尝试获取缓存的方差信息
     * @param pli PLI对象
     * @param data 数据集
     * @param rhs 右手边属性索引
     * @return 缓存的方差信息，如果没有则返回null
     */
    private Map<Integer, Double> getCachedVariances(PLI pli, DataSet data, int rhs) {
        // 如果没有缓存访问器，返回null
        if (varianceCacheAccessor == null) {
            return null;
        }
        
        // 获取PLI对应的列索引
        BitSet columns = pli.getColumns();
        if (columns.cardinality() != 1) {
            return null; // 只支持单列PLI
        }
        
        int columnIndex = columns.nextSetBit(0);
        List<Double> cachedVariances = varianceCacheAccessor.apply(columnIndex);
        
        if (cachedVariances == null || cachedVariances.isEmpty()) {
            return null;
        }
        
        // 将List转换为Map（索引作为键）
        Map<Integer, Double> result = new HashMap<>();
        for (int i = 0; i < cachedVariances.size(); i++) {
            result.put(i, cachedVariances.get(i));
        }
        
        return result;
    }
    
    /**
     * 执行两阶段Neyman最优分配采样
     * @param pli PLI对象，包含等价类簇
     * @param data 数据集对象
     * @param rhs 右手边属性索引
     * @param targetSampleSize 目标样本总数
     * @return 采样得到的行索引集合
     */
    private Set<Integer> twoPhaseNeymanSampling(PLI pli, DataSet data, int rhs, int targetSampleSize) {
        List<Set<Integer>> clusters = pli.getEquivalenceClasses();
        Set<Integer> result = new HashSet<>();
        
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
        
        // 步骤1：尝试使用缓存的方差信息，如果没有则进行试点采样
        Map<Integer, Double> clusterVariances = getCachedVariances(pli, data, rhs);
        
        if (clusterVariances == null) {
            // 缓存未命中，进行试点采样，估计各簇的方差
            clusterVariances = new HashMap<>();
            Map<Integer, Set<Integer>> pilotSamples = pilotSample(clusters);
            
            // 步骤2：估计各簇的方差
            for (int i = 0; i < clusters.size(); i++) {
                double variance = estimateVariance(pilotSamples.get(i), data, rhs);
                clusterVariances.put(i, variance);
            }
        }
        
        // 步骤3：计算Neyman最优分配
        Map<Integer, Integer> allocations = calculateAllocation(
                clusters, clusterVariances, targetSampleSize);
        
        // 步骤4：执行主采样
        for (int i = 0; i < clusters.size(); i++) {
            Set<Integer> cluster = clusters.get(i);
            int allocation = allocations.get(i);
            
            // 如果分配数量大于等于簇大小，全部采样
            if (allocation >= cluster.size()) {
                result.addAll(cluster);
                continue;
            }
            
            // 优化的随机采样：避免频繁的remove操作
            Integer[] clusterArray = cluster.toArray(new Integer[0]);
            
            // 使用Fisher-Yates洗牌算法的简化版本
            for (int j = 0; j < allocation; j++) {
                int randomIndex = random.nextInt(clusterArray.length - j);
                result.add(clusterArray[randomIndex]);
                // 将选中的元素交换到末尾，避免重复选择
                Integer temp = clusterArray[randomIndex];
                clusterArray[randomIndex] = clusterArray[clusterArray.length - 1 - j];
                clusterArray[clusterArray.length - 1 - j] = temp;
            }
        }
        
        return result;
    }
    
    /**
     * 执行试点采样
     * @param clusters 等价类簇列表
     * @return 每个簇的试点样本
     */
    private Map<Integer, Set<Integer>> pilotSample(List<Set<Integer>> clusters) {
        Map<Integer, Set<Integer>> pilotSamples = new HashMap<>();
        
        for (int i = 0; i < clusters.size(); i++) {
            Set<Integer> cluster = clusters.get(i);
            Set<Integer> pilotSample = new HashSet<>();
            
            // 计算试点样本数量：min(sqrt(簇大小), MAX_PILOT_SAMPLE_SIZE)
            int pilotSize = Math.min(
                    MAX_PILOT_SAMPLE_SIZE, 
                    (int) Math.sqrt(cluster.size())
            );
            
            // 如果试点样本数量大于等于簇大小，全部采样
            if (pilotSize >= cluster.size()) {
                pilotSample.addAll(cluster);
            } else {
                // 优化的随机采样：避免频繁的remove操作
                Integer[] clusterArray = cluster.toArray(new Integer[0]);
                
                // 使用Fisher-Yates洗牌算法的简化版本
                for (int j = 0; j < pilotSize; j++) {
                    int randomIndex = random.nextInt(clusterArray.length - j);
                    pilotSample.add(clusterArray[randomIndex]);
                    // 将选中的元素交换到末尾，避免重复选择
                    Integer temp = clusterArray[randomIndex];
                    clusterArray[randomIndex] = clusterArray[clusterArray.length - 1 - j];
                    clusterArray[clusterArray.length - 1 - j] = temp;
                }
            }
            
            pilotSamples.put(i, pilotSample);
        }
        
        return pilotSamples;
    }
    
    /**
     * 估计簇内方差 (使用指标：冲突指示变量的样本方差) - 优化版本
     * @param pilotSample 试点样本
     * @param data 数据集对象
     * @param rhs 右手边属性索引
     * @return 估计的方差
     */
    private double estimateVariance(Set<Integer> pilotSample, DataSet data, int rhs) {
        if (pilotSample.size() <= 1) {
            return 0.0; // 单个或没有样本，方差为0
        }
        
        // 1. 高效查找主流RHS值 - 用手动实现替代Stream API
        Map<String, Integer> valueCount = new HashMap<>();
        String majorityValue = "";
        int maxCount = 0;
        
        for (int row : pilotSample) {
            String value = data.getValue(row, rhs);
            int count = valueCount.getOrDefault(value, 0) + 1;
            valueCount.put(value, count);
            
            if (count > maxCount) {
                maxCount = count;
                majorityValue = value;
            }
        }

        // 2. 直接计算方差，避免创建中间列表
        double sum = 0.0;
        double sumSquares = 0.0;
        int sampleSize = pilotSample.size();
        
        for (int row : pilotSample) {
            int indicator = data.getValue(row, rhs).equals(majorityValue) ? 0 : 1;
            sum += indicator;
            sumSquares += indicator * indicator;
        }
        
        // 3. 使用数学公式直接计算样本方差: s^2 = (sumSquares - n*mean^2) / (n-1)
        double mean = sum / sampleSize;
        return (sumSquares - sampleSize * mean * mean) / (sampleSize - 1);
    }
    
    /**
     * 计算Neyman最优分配 (使用最大余数法进行公平舍入)
     * @param clusters 等价类簇列表
     * @param variances 各簇的方差估计
     * @param targetSampleSize 目标样本总数
     * @return 各簇的样本分配数量
     */
    private Map<Integer, Integer> calculateAllocation(
            List<Set<Integer>> clusters,
            Map<Integer, Double> variances,
            int targetSampleSize) {
        
        Map<Integer, Integer> allocations = new HashMap<>();
        Map<Integer, Double> idealAllocations = new HashMap<>();
        double totalWeight = 0.0;
        
        // 计算总权重：sum(N_i * S_i)
        for (int i = 0; i < clusters.size(); i++) {
            double weight = clusters.get(i).size() * Math.sqrt(variances.get(i));
            totalWeight += weight;
        }

        if (totalWeight == 0) { // 所有方差都为0，均匀分配
             int remainingSamples = targetSampleSize;
             for (int i = 0; i < clusters.size(); i++) {
                 int alloc = Math.min(clusters.get(i).size(), remainingSamples / (clusters.size() - i));
                 allocations.put(i, alloc);
                 remainingSamples -= alloc;
             }
             return allocations;
        }

        // 1. 计算理想的、带小数的分配数
        for (int i = 0; i < clusters.size(); i++) {
            double weight = clusters.get(i).size() * Math.sqrt(variances.get(i));
            idealAllocations.put(i, targetSampleSize * weight / totalWeight);
        }

        // 2. 分配整数部分，并记录小数部分
        int allocatedSamples = 0;
        List<Map.Entry<Integer, Double>> fractionalParts = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            double ideal = idealAllocations.get(i);
            int alloc = (int) ideal;
            allocations.put(i, alloc);
            allocatedSamples += alloc;
            fractionalParts.add(new AbstractMap.SimpleEntry<>(i, ideal - alloc));
        }

        // 3. 将剩余样本按小数部分大小依次分配
        int remaining = targetSampleSize - allocatedSamples;
        fractionalParts.sort(Map.Entry.<Integer, Double>comparingByValue().reversed());
        
        for (int i = 0; i < remaining; i++) {
            int clusterIndex = fractionalParts.get(i).getKey();
            allocations.put(clusterIndex, allocations.get(clusterIndex) + 1);
        }

        // 确保分配不超过簇大小
        for(int i = 0; i < clusters.size(); i++) {
            allocations.put(i, Math.min(clusters.get(i).size(), allocations.get(i)));
        }
        
        return allocations;
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
        return "Neyman最优采样(" + samplingInfo + ")";
    }
}
