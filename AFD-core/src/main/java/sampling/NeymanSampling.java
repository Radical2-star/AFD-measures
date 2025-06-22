package sampling;

import model.DataSet;
import pli.PLI;
import pli.PLICache;

import java.util.*;
import java.util.stream.Collectors;

/**
 * NeymanSampling - 基于Neyman最优分配的聚焦采样策略
 * 通过两阶段采样方法，先进行试点采样估计方差，再按Neyman公式分配样本
 *
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/16
 */
public class NeymanSampling implements SamplingStrategy {
    private Set<Integer> sampleIndices;
    private String samplingInfo;
    private int sampleSize; // 理论样本总数
    private final Random random;
    
    public NeymanSampling(long seed) {
        this.random = new Random(seed);
    }

    public NeymanSampling() {
        this.random = new Random();
    }

    // 试点采样的最大样本数
    private static final int MAX_PILOT_SAMPLE_SIZE = 20;

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
        
        // 步骤2：执行两阶段采样
        sampleIndices = twoPhaseNeymanSampling(subsetPli, data, rhs, sampleSize);
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
        
        // 步骤1：进行试点采样，估计各簇的方差
        Map<Integer, Double> clusterVariances = new HashMap<>();
        Map<Integer, Set<Integer>> pilotSamples = pilotSample(clusters);
        
        // 步骤2：估计各簇的方差
        for (int i = 0; i < clusters.size(); i++) {
            double variance = estimateVariance(pilotSamples.get(i), data, rhs);
            clusterVariances.put(i, variance);
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
            
            // 随机采样
            List<Integer> clusterRows = new ArrayList<>(cluster);
            for (int j = 0; j < allocation; j++) {
                int randomIndex = random.nextInt(clusterRows.size());
                result.add(clusterRows.remove(randomIndex));
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
                // 随机采样
                List<Integer> clusterRows = new ArrayList<>(cluster);
                for (int j = 0; j < pilotSize; j++) {
                    int randomIndex = random.nextInt(clusterRows.size());
                    pilotSample.add(clusterRows.remove(randomIndex));
                }
            }
            
            pilotSamples.put(i, pilotSample);
        }
        
        return pilotSamples;
    }
    
    /**
     * 估计簇内方差 (使用指标：冲突指示变量的样本方差)
     * @param pilotSample 试点样本
     * @param data 数据集对象
     * @param rhs 右手边属性索引
     * @return 估计的方差
     */
    private double estimateVariance(Set<Integer> pilotSample, DataSet data, int rhs) {
        if (pilotSample.size() <= 1) {
            return 0.0; // 单个或没有样本，方差为0
        }
        
        // 1. 找到试点样本中的主流RHS值
        String majorityValue = pilotSample.stream()
                .map(row -> data.getValue(row, rhs))
                .collect(Collectors.groupingBy(val -> val, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        // 2. 计算冲突指示变量 (1表示冲突, 0表示不冲突)
        List<Integer> indicators = new ArrayList<>();
        for (int row : pilotSample) {
            indicators.add(data.getValue(row, rhs).equals(majorityValue) ? 0 : 1);
        }

        // 3. 计算这些指示变量的样本方差 s^2 = (1/(n-1)) * sum((x_i - mean)^2)
        double mean = indicators.stream().mapToInt(Integer::intValue).average().orElse(0.0);

        return indicators.stream()
                .mapToDouble(i -> (i - mean) * (i - mean))
                .sum() / (indicators.size() - 1);
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
