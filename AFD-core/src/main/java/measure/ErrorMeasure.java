package measure;

import model.DataSet;
import model.FunctionalDependency;

/**
 * @ClassName AbstractMeasure
 * @Description
 * @Author Zuoxing Xie
 * @Time 2025/2/26 
 * @Version 1.0
 */
public interface ErrorMeasure {
    /**
     * 计算函数依赖的误差
     * @param dataset 数据集
     * @param fd 待评估的函数依赖
     * @return 误差值（0.0表示完全满足，1.0表示完全不满足）
     */
    double calculateError(DataSet dataset, FunctionalDependency fd);
}
