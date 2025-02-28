package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
public class DataSet {
    private final List<String> columnHeaders;
    private final List<List<String>> rows;
    private final int columnCount;

    public DataSet(List<String> columnHeaders) {
        this.columnHeaders = new ArrayList<>(Objects.requireNonNull(columnHeaders));
        this.columnCount = columnHeaders.size();
        this.rows = new ArrayList<>();
    }

    public void addRow(List<String> row) {
        validateRow(row);
        rows.add(new ArrayList<>(row));
    }

    private void validateRow(List<String> row) {
        if (row.size() != columnCount) {
            throw new IllegalArgumentException(
                    String.format("Expected %d columns, got %d", columnCount, row.size())
            );
        }
    }

    // Getters
    public List<String> getRow(int index) {
        return Collections.unmodifiableList(rows.get(index));
    }

    public String getValue(int rowIndex, int columnIndex) {
        return rows.get(rowIndex).get(columnIndex);
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return columnCount;
    }

    public String getColumnName(int index) {
        return columnHeaders.get(index);
    }
}