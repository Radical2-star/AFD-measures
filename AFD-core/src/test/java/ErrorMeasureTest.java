/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
import pli.PLICache;
import measure.G1Measure;
import measure.G3Measure;
import model.DataSet;
import model.FunctionalDependency;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class ErrorMeasureTest {
    // 测试数据集1：有冲突的FD案例
    private DataSet createConflictDataSet() {
        DataSet data = new DataSet(Arrays.asList("A", "B"));
        data.addRow(Arrays.asList("1", "x"));
        data.addRow(Arrays.asList("1", "y"));
        data.addRow(Arrays.asList("2", "z"));
        return data;
    }

    // 测试数据集2：完美满足FD的案例
    private DataSet createPerfectDataSet() {
        DataSet data = new DataSet(Arrays.asList("A", "B"));
        data.addRow(Arrays.asList("1", "x"));
        data.addRow(Arrays.asList("2", "y"));
        data.addRow(Arrays.asList("3", "z"));
        return data;
    }

    // 测试数据集3：边界案例（单行数据）
    private DataSet createSingleRowDataSet() {
        DataSet data = new DataSet(Arrays.asList("A", "B"));
        data.addRow(Arrays.asList("1", "x"));
        return data;
    }

    @Test
    void testG1MeasureWithConflict() {
        DataSet data = createConflictDataSet();
        PLICache cache = new PLICache(data);
        FunctionalDependency fd = new FunctionalDependency(Collections.singleton(0), 1);

        G1Measure g1 = new G1Measure();
        double error = g1.calculateError(data, fd, cache);

        // 预期结果：3行数据总对数C(3,2)=3，冲突1对
        assertEquals(1.0/3, error, 0.001, "G1计算不符合预期");
    }

    @Test
    void testG3MeasureWithConflict() {
        DataSet data = createConflictDataSet();
        PLICache cache = new PLICache(data);
        FunctionalDependency fd = new FunctionalDependency(Collections.singleton(0), 1);

        G3Measure g3 = new G3Measure();
        double error = g3.calculateError(data, fd, cache);

        // 预期结果：最大满足子集为2行（选1-x或1-y中的一行+2-z）
        assertEquals(1.0 - 2.0/3, error, 0.001, "G3计算不符合预期");
    }

    @Test
    void testPerfectFD() {
        DataSet data = createPerfectDataSet();
        PLICache cache = new PLICache(data);
        FunctionalDependency fd = new FunctionalDependency(Collections.singleton(0), 1);

        G1Measure g1 = new G1Measure();
        G3Measure g3 = new G3Measure();

        assertEquals(0.0, g1.calculateError(data, fd, cache), 0.001, "G1完美案例错误");
        assertEquals(0.0, g3.calculateError(data, fd, cache), 0.001, "G3完美案例错误");
    }

    @Test
    void testEdgeCaseWithSingleRow() {
        DataSet data = createSingleRowDataSet();
        PLICache cache = new PLICache(data);
        FunctionalDependency fd = new FunctionalDependency(Collections.singleton(0), 1);

        G1Measure g1 = new G1Measure();
        G3Measure g3 = new G3Measure();

        // 单行数据没有可比对的对
        assertEquals(0.0, g1.calculateError(data, fd, cache), 0.001, "G1单行案例错误");
        assertEquals(0.0, g3.calculateError(data, fd, cache), 0.001, "G3单行案例错误");
    }

    @Test
    void testMultiColumnLHS() {
        DataSet data = new DataSet(Arrays.asList("A", "B", "C"));
        data.addRow(Arrays.asList("1", "a", "x"));
        data.addRow(Arrays.asList("1", "a", "y")); // 违反A,B→C
        data.addRow(Arrays.asList("2", "b", "z"));

        PLICache cache = new PLICache(data);
        FunctionalDependency fd = new FunctionalDependency(new HashSet<>(Arrays.asList(0, 1)), 2);

        G1Measure g1 = new G1Measure();
        double error = g1.calculateError(data, fd, cache);

        // 总对数C(3,2)=3，冲突1对（行0和行1）
        assertEquals(1.0/3, error, 0.001, "多列LHS测试失败");
    }
}
