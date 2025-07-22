import measure.G3Measure;
import model.DataSet;
import pli.PLICache;
import utils.BitSetUtils;
import utils.DataLoader;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/5/21
 */
public class Test {
    public static void main(String[] args) {
        DataLoader loader = DataLoader.fromFile(
                Path.of("data/abalone.csv")
        ).withHeader(true).withDelimiter(',');
        DataSet dataset = loader.load();

        // 初始化 PLI 缓存
        PLICache cache = new PLICache(dataset);

        // 构造目标列的 BitSet，比如第0列和第1列
        BitSet targetColumns = BitSetUtils.listToBitSet(Arrays.asList(0,2,3,6,7));

        double error = new G3Measure().calculateError(targetColumns, 4, dataset, cache);

        System.out.println(error);
    }
}
