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

    private final List<Double> errorRates;


    public DataSet(List<String> columnHeaders) {
        this.columnHeaders = columnHeaders;
        this.columnCount = columnHeaders.size();
        this.rows = new ArrayList<>();
        this.errorRates = new ArrayList<>(Collections.nCopies(columnCount, 0.0)); // 默认错误率为0
    }

    // 获取错误率
    public double getErrorRate(int columnIndex) {
        return errorRates.get(columnIndex);
    }

    // 设置某列的错误率
    public void setErrorRate(int columnIndex, double errorRate) {
        if (errorRate < 0 || errorRate > 1) {
            throw new IllegalArgumentException("Error rate must be between 0 and 1");
        }
        errorRates.set(columnIndex, errorRate);
    }

    // 批量设置所有列的错误率
    public void setAllErrorRates(List<Double> rates) {
        if (rates.size() != columnCount) {
            throw new IllegalArgumentException("Error rate list size must match column count");
        }
        for (int i = 0; i < rates.size(); i++) {
            setErrorRate(i, rates.get(i));
        }
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