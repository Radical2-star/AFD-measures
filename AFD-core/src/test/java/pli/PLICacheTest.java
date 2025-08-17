package pli;

import model.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.BitSet;
import java.util.List;
/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
public class PLICacheTest {

    private DataSet testDataSet;

    // 新设计的数据集（保证能产生非空交集）
    private DataSet createTestDataSet() {
        DataSet data = new DataSet(List.of("姓名", "年龄", "城市"));
        // 行0和1在"年龄"和"城市"列都相同
        data.addRow(List.of("张三", "25", "北京"));  // 行0
        data.addRow(List.of("李四", "25", "北京"));  // 行1
        // 行2和3在"年龄"列相同
        data.addRow(List.of("王五", "30", "上海"));  // 行2
        data.addRow(List.of("赵六", "25", "广州"));  // 行3
        return data;
    }

    @BeforeEach
    void setup() {
        testDataSet = createTestDataSet();
    }

    @Test
    void testSingleColumnPLI() {
        PLICache cache = new PLICache(testDataSet);

        // 验证年龄列的PLI（应有3行25岁）
        BitSet ageCol = new BitSet();
        ageCol.set(1);
        PLI agePLI = cache.getOrCalculatePLI(ageCol);
        assertEquals(1, agePLI.getClusterCount()); // 预期一个等价类{0,1,3}
        assertTrue(agePLI.getEquivalenceClasses().get(0).containsAll(List.of(0, 1, 3)));
    }

    @Test
    void testLRUCacheStatistics() {
        PLICache cache = new PLICache(testDataSet);
        
        // 测试缓存统计初始状态
        String initialStats = cache.getCacheStats();
        assertNotNull(initialStats);
        assertTrue(initialStats.contains("PLI缓存统计"));
        System.out.println("初始缓存统计: " + initialStats);
        
        // 访问一些已存在的单列PLI（应该增加命中计数）
        BitSet col0 = new BitSet();
        col0.set(0);
        cache.getOrCalculatePLI(col0);
        
        BitSet col1 = new BitSet();
        col1.set(1);
        cache.getOrCalculatePLI(col1);
        
        // 访问一个不存在的多列组合（会触发计算和可能的缓存）
        BitSet cols01 = new BitSet();
        cols01.set(0);
        cols01.set(1);
        cache.getOrCalculatePLI(cols01);
        
        String finalStats = cache.getCacheStats();
        System.out.println("最终缓存统计: " + finalStats);
        
        // 验证统计信息包含预期内容
        assertTrue(finalStats.contains("访问:"));
        assertTrue(finalStats.contains("命中:"));
        assertTrue(finalStats.contains("命中率:"));
    }
    
    @Test
    void testIntersectionWithResult() {
        PLICache cache = new PLICache(testDataSet);

        // 构建两个PLI
        BitSet ageCol = new BitSet();
        ageCol.set(1);
        PLI agePLI = cache.getOrCalculatePLI(ageCol); // 年龄PLI: {0,1,3}

        BitSet cityCol = new BitSet();
        cityCol.set(2);
        PLI cityPLI = cache.getOrCalculatePLI(cityCol); // 城市PLI: {0,1}（北京）

        // 计算交集
        PLI intersectPLI = agePLI.intersect(cityPLI);

        // 验证结果（年龄25 + 北京 → 行0,1）
        assertEquals(1, intersectPLI.getClusterCount());
        assertTrue(intersectPLI.getEquivalenceClasses().get(0).containsAll(List.of(0, 1)));
    }
}