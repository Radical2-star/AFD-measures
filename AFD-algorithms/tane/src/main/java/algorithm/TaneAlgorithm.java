package algorithm;

import measure.ErrorMeasure;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLICache;
import utils.BitSetUtils;

import java.util.*;

/**
 * Tane算法实现
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/7/15
 */
public class TaneAlgorithm {
    private final DataSet dataSet;
    private final PLICache pliCache;
    private final ErrorMeasure measure;
    private final double maxError;
    private final boolean verbose;
    private final List<FunctionalDependency> result;
    private final Map<BitSet, BitSet> cPlusMap;
    private int validationCount;
    private long executionTimeMs;

    /**
     * 构造函数
     * 
     * @param dataSet 数据集
     * @param measure 误差度量
     * @param maxError 最大误差阈值
     * @param verbose 是否输出详细信息
     */
    public TaneAlgorithm(DataSet dataSet, ErrorMeasure measure, double maxError, boolean verbose) {
        this.dataSet = dataSet;
        this.measure = measure;
        this.maxError = maxError;
        this.verbose = verbose;
        this.pliCache = new PLICache(dataSet);
        this.result = new ArrayList<>();
        this.cPlusMap = new HashMap<>();
        this.validationCount = 0;
    }

    /**
     * 发现函数依赖
     * 
     * @return 发现的函数依赖列表
     */
    public List<FunctionalDependency> discover() {
        long startTime = System.currentTimeMillis();
        result.clear();
        validationCount = 0;
        cPlusMap.clear();

        // 初始化空集的C+为所有属性
        BitSet emptySet = new BitSet();
        BitSet allAttributes = new BitSet();
        allAttributes.set(0, dataSet.getColumnCount());
        cPlusMap.put(emptySet, allAttributes);

        // 初始化第一层（单个属性）
        List<BitSet> level1 = new ArrayList<>();
        for (int i = 0; i < dataSet.getColumnCount(); i++) {
            BitSet singleton = new BitSet();
            singleton.set(i);
            level1.add(singleton);
            cPlusMap.put(singleton, (BitSet) allAttributes.clone());
        }

        // 逐层遍历
        List<BitSet> currentLevel = level1;
        int levelNum = 1;

        while (!currentLevel.isEmpty()) {
            if (verbose) {
                System.out.println("处理第 " + levelNum + " 层，候选集大小: " + currentLevel.size());
            }

            computeDependencies(currentLevel);
            // prune(currentLevel); 
            
            if (currentLevel.isEmpty()) {
                break;
            }
            
            List<BitSet> nextLevel = generateNextLevel(currentLevel);
            
            for (BitSet x : nextLevel) {
                computeCPlus(x);
            }
            
            currentLevel = nextLevel;
            levelNum++;
        }

        this.executionTimeMs = System.currentTimeMillis() - startTime;
        return result;
    }

    /**
     * 计算属性集X的C+ 
     *
     * @param x 属性集
     */
    private void computeCPlus(BitSet x) {
        // 此方法目前未被直接调用，但保留其基础实现以备将来可能的扩展
        if (cPlusMap.containsKey(x)) return;

        List<BitSet> subsets = new ArrayList<>();
        for (int i = x.nextSetBit(0); i >= 0; i = x.nextSetBit(i + 1)) {
            BitSet subset = (BitSet) x.clone();
            subset.clear(i);
            subsets.add(subset);
        }

        if (subsets.isEmpty()) {
            cPlusMap.put(x, new BitSet());
            return;
        }
        
        BitSet cPlus = (BitSet) cPlusMap.get(subsets.get(0)).clone();
        for (int i = 1; i < subsets.size(); i++) {
            BitSet subsetCPlus = cPlusMap.get(subsets.get(i));
            if (subsetCPlus != null) {
                cPlus.and(subsetCPlus);
            }
        }
        cPlusMap.put(x, cPlus);
    }


    /**
     * 计算当前层的函数依赖
     * 
     * @param level 当前层的属性集列表
     */
    private void computeDependencies(List<BitSet> level) {
        for (BitSet x : level) {
            BitSet cPlusX = cPlusMap.get(x);
            if (cPlusX == null) {
                // 如果C+(X)不存在，则计算它
                List<BitSet> subsets = new ArrayList<>();
                for (int i = x.nextSetBit(0); i >= 0; i = x.nextSetBit(i + 1)) {
                    BitSet subset = (BitSet) x.clone();
                    subset.clear(i);
                    subsets.add(subset);
                }

                if (subsets.isEmpty()) {
                    cPlusX = new BitSet();
                } else {
                    cPlusX = (BitSet) cPlusMap.get(subsets.get(0)).clone();
                    for (int i = 1; i < subsets.size(); i++) {
                        cPlusX.and(cPlusMap.get(subsets.get(i)));
                    }
                }
                cPlusMap.put(x, cPlusX);
            }

            BitSet cPlusXandX = (BitSet) cPlusX.clone();
            cPlusXandX.and(x);

            for (int a = cPlusXandX.nextSetBit(0); a >= 0; a = cPlusXandX.nextSetBit(a + 1)) {
                BitSet xWithoutA = (BitSet) x.clone();
                xWithoutA.clear(a);

                // 防止空集作为函数依赖的LHS
                if (xWithoutA.isEmpty()) {
                    continue;
                }

                double error = calculateError(xWithoutA, a);
                if (error <= this.maxError) {
                    FunctionalDependency fd = new FunctionalDependency(
                            BitSetUtils.bitSetToSet(xWithoutA), a, error
                    );
                    result.add(fd);

                    cPlusMap.get(x).clear(a);

                    if (error == 0) {
                        BitSet rMinusX = new BitSet();
                        rMinusX.set(0, dataSet.getColumnCount());
                        rMinusX.andNot(x);
                        cPlusMap.get(x).andNot(rMinusX);
                    }
                }
            }
        }
    }

    /**
     * 检查属性集是否为超键
     * 
     * @param x 属性集
     * @return 是否为超键
     */
    private boolean isSuperkey(BitSet x) {
        // 使用PLI来检查是否为超键
        // 如果x的PLI中没有等价类，则x是超键
        // 注意：PLI只保存大小>1的等价类，所以如果等价类为空，说明所有行都是唯一的，即为超键
        return pliCache.getOrCalculatePLI(x).getEquivalenceClasses().isEmpty();
    }

    /**
     * 剪枝操作
     * 
     * @param level 当前层的属性集列表
     */
    private void prune(List<BitSet> level) {
        Iterator<BitSet> iterator = level.iterator();
        while (iterator.hasNext()) {
            BitSet x = iterator.next();
            BitSet cPlusX = cPlusMap.get(x);

            if (cPlusX == null) {
                iterator.remove();
                continue;
            }

            // 剪枝策略1: 如果C+(X)与X相等，则剪枝
            if (cPlusX.equals(x)) {
                iterator.remove();
                continue;
            }

            // 剪枝策略2: 如果X是超键，执行特殊的依赖发现逻辑
            if (isSuperkey(x)) {
                BitSet cPlusXMinusX = (BitSet) cPlusX.clone();
                cPlusXMinusX.andNot(x);

                for (int a = cPlusXMinusX.nextSetBit(0); a >= 0; a = cPlusXMinusX.nextSetBit(a + 1)) {
                    BitSet tempSet = (BitSet) x.clone();
                    tempSet.set(a);
                    
                    List<BitSet> subsetsToIntersect = new ArrayList<>();
                    for (int b = x.nextSetBit(0); b >= 0; b = x.nextSetBit(b + 1)) {
                        BitSet subset = (BitSet) tempSet.clone();
                        subset.clear(b);
                        subsetsToIntersect.add(subset);
                    }

                    if (subsetsToIntersect.isEmpty()) continue;

                    // 计算这组临时子集的C+的交集
                    BitSet intersection = null;
                    boolean first = true;
                    boolean canIntersect = true;
                    for(BitSet sub : subsetsToIntersect) {
                        // 修复：不再越级调用computeCPlus，而是检查C+是否存在
                        if (!cPlusMap.containsKey(sub)) {
                           canIntersect = false;
                           break;
                        }
                        BitSet subCPlus = cPlusMap.get(sub);

                        if (first) {
                            intersection = (BitSet) subCPlus.clone();
                            first = false;
                        } else {
                            intersection.and(subCPlus);
                        }
                    }

                    if (canIntersect && intersection != null && intersection.get(a)) {
                        double error = calculateError(x, a);
                        if (error <= this.maxError) {
                            FunctionalDependency fd = new FunctionalDependency(
                                    BitSetUtils.bitSetToSet(x), a, error
                            );
                            result.add(fd);
                        }
                    }
                }
                iterator.remove();
            }
        }
    }

    /**
     * 生成下一层的候选集
     * 
     * @param level 当前层的属性集列表
     * @return 下一层的候选集
     */
    private List<BitSet> generateNextLevel(List<BitSet> level) {
        if (level.isEmpty()) {
            return new ArrayList<>();
        }
        
        Set<BitSet> nextLevelSet = new HashSet<>();
        
        for (int i = 0; i < level.size(); i++) {
            for (int j = i + 1; j < level.size(); j++) {
                BitSet x = level.get(i);
                BitSet y = level.get(j);
                
                BitSet xPrefix = (BitSet) x.clone();
                xPrefix.clear(x.length() - 1);
                BitSet yPrefix = (BitSet) y.clone();
                yPrefix.clear(y.length() - 1);
                
                if (xPrefix.equals(yPrefix)) {
                    BitSet candidate = (BitSet) x.clone();
                    candidate.or(y);
                    
                    boolean allSubsetsExist = true;
                    for (int bit = candidate.nextSetBit(0); bit >= 0; bit = candidate.nextSetBit(bit + 1)) {
                        BitSet subset = (BitSet) candidate.clone();
                        subset.clear(bit);
                        
                        if (!containsBitSet(level, subset)) {
                            allSubsetsExist = false;
                            break;
                        }
                    }
                    
                    if (allSubsetsExist) {
                        nextLevelSet.add(candidate);
                    }
                }
            }
        }
        
        return new ArrayList<>(nextLevelSet);
    }
    
    /**
     * 检查列表中是否包含指定的BitSet
     * 
     * @param list BitSet列表
     * @param target 目标BitSet
     * @return 是否包含
     */
    private boolean containsBitSet(List<BitSet> list, BitSet target) {
        // 对于小的列表，线性搜索足够快
        for (BitSet set : list) {
            if (set.equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算函数依赖的误差
     * 
     * @param lhs 左手边属性集
     * @param rhs 右手边属性
     * @return 误差值
     */
    private double calculateError(BitSet lhs, int rhs) {
        validationCount++; // 将计数器移到这里，与旧代码(g3方法)保持一致
        return measure.calculateError(lhs, rhs, dataSet, pliCache);
    }

    /**
     * 获取验证次数
     * 
     * @return 验证次数
     */
    public int getValidationCount() {
        return validationCount;
    }

    /**
     * 获取执行时间（毫秒）
     * 
     * @return 执行时间
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
}
