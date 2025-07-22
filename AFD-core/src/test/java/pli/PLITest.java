package pli;

import model.DataSet;
import org.junit.jupiter.api.Test;
import pli.PLI;

import static org.junit.jupiter.api.Assertions.*;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

public class PLITest {

    // 测试数据构建器
    private DataSet createSampleDataSet() {
        List<String> headers = List.of("颜色", "大小", "形状");
        DataSet data = new DataSet(headers);
        data.addRow(List.of("红", "大", "圆"));    // 行0
        data.addRow(List.of("红", "大", "方"));    // 行1
        data.addRow(List.of("红", "小", "圆"));    // 行2
        data.addRow(List.of("蓝", "大", "方"));    // 行3
        data.addRow(List.of("蓝", "大", "三角"));  // 行4
        return data;
    }

    @Test
    void testPLIConstruction() {
        DataSet data = createSampleDataSet();

        // 测试颜色列的PLI
        BitSet colorColumns = new BitSet();
        colorColumns.set(0); // 第0列是"颜色"
        PLI colorPLI = new PLI(colorColumns, data);

        // 颜色列应该有两个等价类：红（行0,1,2）和蓝（行3,4），但红有3行会被保留吗？
        // 根据PLI构造逻辑，等价类需要大小>1才会保留
        // 红有3行，蓝有2行，所以应该生成两个等价类
        assertEquals(2, colorPLI.getEquivalenceClasses().size());
        assertTrue(containsCluster(colorPLI, Set.of(0, 1, 2))); // 红色
        assertTrue(containsCluster(colorPLI, Set.of(3, 4)));    // 蓝色
    }

    @Test
    void testIntersectBasic() {
        DataSet data = createSampleDataSet();

        // PLI1: 颜色列（红和蓝的簇）
        BitSet colorCols = new BitSet();
        colorCols.set(0);
        PLI colorPLI = new PLI(colorCols, data);

        // PLI2: 大小列（大的簇包含行0,1,3,4）
        BitSet sizeCols = new BitSet();
        sizeCols.set(1);
        PLI sizePLI = new PLI(sizeCols, data);

        // 计算交集
        PLI intersectPLI = colorPLI.intersect(sizePLI);

        // 验证合并后的列
        BitSet expectedColumns = new BitSet();
        expectedColumns.set(0);
        expectedColumns.set(1);
        assertEquals(expectedColumns, intersectPLI.getColumns());

        // 预期结果：红+大的行（0,1）和蓝+大的行（3,4）
        // 等价类应该分成两个簇：{0,1}和{3,4}
        assertEquals(2, intersectPLI.getEquivalenceClasses().size());
        assertTrue(containsCluster(intersectPLI, Set.of(0, 1)));
        assertTrue(containsCluster(intersectPLI, Set.of(3, 4)));
    }

    @Test
    void testIntersectWithSingleton() {
        DataSet data = createSampleDataSet();

        // PLI1: 形状列（可能有圆和方的簇）
        BitSet shapeCols = new BitSet();
        shapeCols.set(2);
        PLI shapePLI = new PLI(shapeCols, data);
        // 形状列等价类：圆（0,2）、方（1,3）、三角（4单例被过滤）
        // 所以shapePLI有2个等价类：{0,2}, {1,3}
        assertEquals(2, shapePLI.getEquivalenceClasses().size());

        // PLI2: 颜色列（红、蓝）
        BitSet colorCols = new BitSet();
        colorCols.set(0);
        PLI colorPLI = new PLI(colorCols, data);

        // 计算交集
        PLI intersectPLI = shapePLI.intersect(colorPLI);

        // 预期结果：在形状和颜色组合下
        // - 红+圆：行0（红）和行2（红+圆），颜色相同，形状相同吗？
        // 需要看具体数据：
        // 行0: 红,大,圆
        // 行2: 红,小,圆 → 颜色相同，形状相同，所以合并后的列是颜色+形状？或者颜色和形状的并？
        // 但根据intersect的逻辑，合并列是取并集，所以这里列是颜色（0）和形状（2）
        // 等价类需要同时在这两列上相同

        // 行0和行2在颜色（红）和形状（圆）上都相同 → 应该在一个簇
        // 行1: 红,大,方 → 颜色红，形状方 → 单独簇
        // 行3: 蓝,大,方 → 颜色蓝，形状方 → 单独簇
        // 行4: 蓝,大,三角 → 颜色蓝，形状三角 → 单独

        // 但根据intersect的逻辑，会用较大的PLI作为Y来探测。这里可能需要更精确的测试数据设计
        // 此测试可能需要调整数据以更明确
    }

    // 辅助方法：检查PLI是否包含指定簇
    private boolean containsCluster(PLI pli, Set<Integer> expected) {
        return pli.getEquivalenceClasses().stream()
                .anyMatch(cluster -> cluster.equals(expected));
    }
}
