package pli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
class PLITest {
    // 测试数据：3行2列
    // 列0: [A, A, B]
    // 列1: [1, 1, 2]
    private final List<Set<Integer>> testClusters = Arrays.asList(
            new HashSet<>(Arrays.asList(0, 1)),  // 列0的等价类（A）
            new HashSet<>(Collections.singletonList(2))      // 列0的等价类（B）单例将被过滤
    );

    private final PLI singleColumnPLI = new PLI(new HashSet<>(Collections.singletonList(0)), testClusters);

    @Test
    void testConstructor() {
        assertEquals(1, singleColumnPLI.getColumns().size());
        assertTrue(singleColumnPLI.getColumns().contains(0));
        assertEquals(1, singleColumnPLI.getClusterCount());
    }

    @Test
    void testIntersectSameColumn() {
        PLI result = singleColumnPLI.intersect(singleColumnPLI);
        assertEquals(1, result.getColumns().size());
        assertEquals(1, result.getClusterCount());
        assertEquals(new HashSet<>(Arrays.asList(0,1)), result.getEquivalenceClasses().get(0));
    }

    @Test
    void testIntersectDifferentColumns() {
        // 第二个PLI（列1）
        List<Set<Integer>> clusters1 = Collections.singletonList(new HashSet<>(Arrays.asList(0, 1)));
        PLI pli1 = new PLI(new HashSet<>(Collections.singletonList(1)), clusters1);

        PLI result = singleColumnPLI.intersect(pli1);
        assertEquals(2, result.getColumns().size());
        assertEquals(1, result.getClusterCount());
        assertEquals(new HashSet<>(Arrays.asList(0,1)), result.getEquivalenceClasses().get(0));
    }

    @Test
    void testIntersectNoOverlap() {
        List<Set<Integer>> clusters1 = Collections.singletonList(new HashSet<>(Collections.singletonList(2)));
        PLI pli1 = new PLI(new HashSet<>(Collections.singletonList(1)), clusters1);

        PLI result = singleColumnPLI.intersect(pli1);
        assertEquals(0, result.getClusterCount());
    }
}