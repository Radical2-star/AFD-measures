import model.DataSet;
import utils.DataLoader;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
class DataLoaderTest {
    private static final Path TEST_CSV = Paths.get("src/test/resources/test.csv");
    private static final Path INVALID_CSV = Paths.get("nonexistent.csv");
    @Test
    void testLoadNormalCSV() {
        DataSet data = DataLoader.fromFile(TEST_CSV)
                .withHeader(true)
                .load();

        // 验证列头
        assertEquals("ID", data.getColumnName(0));
        assertEquals("Name", data.getColumnName(1));

        // 验证数据
        assertEquals("1", data.getValue(0, 0));
        assertEquals("Alice", data.getValue(0, 1));
        assertEquals("3", data.getValue(2, 0));
    }

    @Test
    void testLoadWithoutHeader() {
        DataSet data = DataLoader.fromFile(TEST_CSV)
                .withHeader(false)
                .load();

        assertEquals("col_1", data.getColumnName(0));
        assertEquals(4, data.getRowCount());
        assertEquals("ID", data.getValue(0, 0));
        assertEquals("Name", data.getValue(0, 1));
    }

    @Test
    void testFileNotFound() {
        assertThrows(DataLoader.DataLoadingException.class,
                () -> DataLoader.fromFile(INVALID_CSV).load());
    }

}