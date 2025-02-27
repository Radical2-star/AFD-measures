package utils;

import measure.ErrorMeasure;
import model.DataSet;
import model.FunctionalDependency;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *  Validator
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class Validator {
    /**
     * 通用验证方法
     * @param measure 使用的误差度量标准
     * @param dataset 数据集
     * @param fd 待验证的函数依赖
     * @return 根据指定度量标准计算的误差值
     */
    public static double validate(ErrorMeasure measure, DataSet dataset, FunctionalDependency fd) {
        if (!fd.isValid()) {
            throw new IllegalArgumentException("Invalid FD: RHS contained in LHS");
        }
        return measure.calculateError(dataset, fd);
    }

    /**
     * 批量验证方法（带并行优化）
     * @param measure 误差度量标准
     * @param dataset 数据集
     * @param candidates 候选FD集合
     * @return 包含有效FD及其误差的映射
     */
    public static Map<FunctionalDependency, Double> batchValidate(ErrorMeasure measure,
                                                                  DataSet dataset,
                                                                  Collection<FunctionalDependency> candidates) {
        return candidates.parallelStream()
                .filter(FunctionalDependency::isValid)
                .collect(Collectors.toConcurrentMap(
                        fd -> fd,
                        fd -> measure.calculateError(dataset, fd)
                ));
    }
}