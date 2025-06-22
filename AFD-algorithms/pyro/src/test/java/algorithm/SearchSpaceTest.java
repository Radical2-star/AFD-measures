/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/15
 */
package algorithm;

import model.DataSet;
import org.junit.jupiter.api.BeforeEach;
import pli.PLICache;
import java.util.Arrays;
import java.util.List;

public class SearchSpaceTest {

    private DataSet dataSet;
    private PLICache cache;
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
        cache = new PLICache(dataSet);

        // 创建PyroConfig
        // config = new PyroConfig(null, null, 0.1);

        // 创建SearchSpace
        // searchSpace = new SearchSpace(2, dataSet, cache, config);
    }

}
