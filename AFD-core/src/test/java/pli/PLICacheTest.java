package pli;

import model.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
class PLICacheTest {
    private DataSet testDataSet;
    private PLICache pliCache;

    @BeforeEach
    void setUp() {
        // 创建测试数据集（3行2列）
        // 列0: ["A", "A", "B"]
        // 列1: ["1", "1", "2"]
        List<String> headers = Arrays.asList("col0", "col1");
        testDataSet = new DataSet(headers);
        testDataSet.addRow(Arrays.asList("A", "1"));
        testDataSet.addRow(Arrays.asList("A", "1"));
        testDataSet.addRow(Arrays.asList("B", "2"));

        pliCache = new PLICache(testDataSet);
    }

    @Test
    void testInitialization() {
        // 验证单列PLI已缓存
        assertTrue(pliCache.getPLI(Collections.singleton(0)).getClusterCount() > 0);
        assertTrue(pliCache.getPLI(Collections.singleton(1)).getClusterCount() > 0);
    }

    @Test
    void testSingleColumnPLI() {
        PLI col0PLI = pliCache.getPLI(Collections.singleton(0));
        assertEquals(1, col0PLI.getClusterCount());
        assertEquals(new HashSet<>(Arrays.asList(0, 1)), col0PLI.getEquivalenceClasses().get(0));

        PLI col1PLI = pliCache.getPLI(Collections.singleton(1));
        assertEquals(1, col1PLI.getClusterCount());
        assertEquals(new HashSet<>(Arrays.asList(0, 1)), col1PLI.getEquivalenceClasses().get(0));
    }

    @Test
    void testMultiColumnPLI() {
        // 列0和列1的组合
        PLI combinedPLI = pliCache.getPLI(new HashSet<>(Arrays.asList(0, 1)));

        assertEquals(2, combinedPLI.getColumns().size());
        assertEquals(1, combinedPLI.getClusterCount());
        assertEquals(new HashSet<>(Arrays.asList(0, 1)), combinedPLI.getEquivalenceClasses().get(0));
    }

    @Test
    void testCacheReuse() {
        PLI firstCall = pliCache.getPLI(new HashSet<>(Arrays.asList(0, 1)));
        PLI secondCall = pliCache.getPLI(new HashSet<>(Arrays.asList(1, 0))); // 不同顺序

        assertSame(firstCall, secondCall, "应返回缓存中的同一实例");
    }

    @Test
    void testInvalidColumn() {
        assertThrows(IllegalArgumentException.class, () -> {
            pliCache.getPLI(Collections.singleton(3)); // 不存在的列
        });
    }
}