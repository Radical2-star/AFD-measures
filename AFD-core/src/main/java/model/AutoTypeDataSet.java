package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 *  DataSet
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */

public class AutoTypeDataSet {
    private final List<String> columnHeaders;
    private final List<DataType> columnTypes;
    private final List<List<Object>> rows;
    private final int columnCount;

    public AutoTypeDataSet(List<String> columnHeaders, List<DataType> columnTypes) {
        this.columnHeaders = new ArrayList<>(Objects.requireNonNull(columnHeaders));
        this.columnTypes = new ArrayList<>(Objects.requireNonNull(columnTypes));
        this.columnCount = columnHeaders.size();
        this.rows = new ArrayList<>();

        validateSchema();
    }

    private void validateSchema() {
        if (columnHeaders.size() != columnTypes.size()) {
            throw new IllegalArgumentException("Column headers and types size mismatch");
        }
    }

    public void addRow(List<Object> row) {
        validateRow(row);
        rows.add(new ArrayList<>(row));
    }

    private void validateRow(List<Object> row) {
        if (row.size() != columnCount) {
            throw new IllegalArgumentException(
                    String.format("Expected %d columns, got %d", columnCount, row.size())
            );
        }

        for (int i = 0; i < row.size(); i++) {
            Object value = row.get(i);
            DataType expectedType = columnTypes.get(i);

            if (!isValidType(value, expectedType)) {
                throw new IllegalArgumentException(
                        String.format("Column %d type mismatch: expected %s, got %s",
                                i, expectedType, value.getClass().getSimpleName())
                );
            }
        }
    }

    private boolean isValidType(Object value, DataType type) {
        if (type == null) throw new NullPointerException("DataType cannot be null");

        switch (type) {
            case INTEGER:
                return value instanceof Integer;
            case STRING:
                return value instanceof String;
            case DOUBLE:
                return value instanceof Double;
            case BOOLEAN:
                return value instanceof Boolean;
            default:
                throw new IllegalArgumentException("Unsupported data type: " + type);
        }
    }

    // Getters
    public List<Object> getRow(int index) {
        return Collections.unmodifiableList(rows.get(index));
    }

    public Object getValue(int rowIndex, int columnIndex) {
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

    public DataType getColumnType(int index) {
        return columnTypes.get(index);
    }

    public List<DataType> getColumnTypes() {
        return columnTypes;
    }

    public enum DataType {
        INTEGER, STRING, DOUBLE, BOOLEAN
    }
}
