/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/15
 */
import model.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pli.PLICache;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SearchSpaceTest {

    private DataSet dataSet;
    private PLICache cache;
    private PyroConfig config;
    private SearchSpace searchSpace;

    @BeforeEach
    public void setUp() {
        // 创建一个DataSet，假设有10个列
        List<String> columnHeaders;
        // 10个列，列名从1到10
        columnHeaders = Arrays.asList(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"
        );
        dataSet = new DataSet(columnHeaders);
        // 添加两行元组
        dataSet.addRow(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        dataSet.addRow(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));

        // 创建PLICache
        cache = PLICache.getInstance(dataSet);

        // 创建PyroConfig
        config = new PyroConfig(null, null, 0.1);

        // 创建SearchSpace
        searchSpace = new SearchSpace(2, dataSet, cache, config);
    }

    @Test
    public void testBuildTree() {
        // 构建树（已经在初始化过程中完成构建）
        // searchSpace.buildTree();

        int columnCount = dataSet.getColumnCount();

        // 根节点应该有n-1个子节点（每个子节点代表一个属性）
        assertEquals(columnCount-1, searchSpace.getRoot().getChildren().size());

        // 打印NodeMap并检查节点数
        for (Node node: searchSpace.getNodeMap().values()) {
            System.out.println(node);
        }
        assertEquals((int) Math.pow(2, columnCount - 1) - 1, searchSpace.getNodeMap().size());

        // 检查每个子节点
        for (Node child : searchSpace.getRoot().getChildren()) {
            BitSet lhs = child.getLhs();
            assertEquals(1, lhs.cardinality()); // 每个子节点的lhs应该只有一个属性

            // 检查子节点的子节点
            int rhs = child.getRhs();
            assertEquals(2, rhs); // 所有子节点的rhs应该是2

            // 检查子节点的子节点数量
            int expectedChildrenCount = columnCount - 2; // 除去当前属性和rhs属性
            assertEquals(expectedChildrenCount, child.getChildren().size());

            // 检查子节点的父节点数量
            assertEquals(1, child.getParents().size());


        }
    }
}
