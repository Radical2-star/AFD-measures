import model.DataSet;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */

class DataSetTest {

    @Test
    void testAddAndRetrieveData() {
        DataSet dataset = new DataSet(Arrays.asList("ID", "Name"));


        dataset.addRow(Arrays.asList("1", "Alice"));
        dataset.addRow(Arrays.asList("2", "Bob"));

        assertEquals(2, dataset.getRowCount());
        assertEquals("Alice", dataset.getValue(0, 1));
        assertEquals("Bob", dataset.getValue(1, 1));
    }

    @Test
    void testInvalidRowSize() {
        DataSet dataset = new DataSet(Arrays.asList("A", "B"));
        assertThrows(IllegalArgumentException.class,
                () -> dataset.addRow(Arrays.asList("1")));
    }
}