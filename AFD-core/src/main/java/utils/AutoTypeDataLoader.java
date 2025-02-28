package utils;

import model.AutoTypeDataSet;
import model.AutoTypeDataSet.DataType;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 *  DataLoader
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */

public class AutoTypeDataLoader {
    private final Path filePath;
    private boolean hasHeader = true;
    private char delimiter = ',';
    private int sampleSize = 100;
    private List<DataType> specifiedTypes;

    private AutoTypeDataLoader(Path filePath) {
        this.filePath = filePath;
    }

    public static AutoTypeDataLoader fromFile(Path filePath) {
        return new AutoTypeDataLoader(filePath);
    }

    public AutoTypeDataLoader withHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
        return this;
    }

    public AutoTypeDataLoader withDelimiter(char delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public AutoTypeDataLoader withTypeSpecification(List<DataType> types) {
        this.specifiedTypes = new ArrayList<>(types);
        return this;
    }

    public AutoTypeDataLoader withSamplingSize(int sampleSize) {
        this.sampleSize = Math.max(10, sampleSize);
        return this;
    }

    public AutoTypeDataSet load() throws DataLoadingException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            // 读取首行处理列头
            List<String> headers = readHeaders(reader);

            // 类型推断
            List<DataType> types = determineDataTypes(reader, headers.size());

            // 创建数据集
            AutoTypeDataSet dataset = new AutoTypeDataSet(headers, types);

            // 读取剩余数据
            loadDataRows(reader, dataset);

            return dataset;
        } catch (IOException e) {
            throw new DataLoadingException("Failed to read file: " + filePath, e);
        }
    }

    private List<String> readHeaders(BufferedReader reader) throws IOException {
        if (hasHeader) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new DataLoadingException("File is empty");
            }
            return parseLine(headerLine);
        }
        // 生成默认列名
        int columnCount = detectColumnCount(reader);
        return Collections.nCopies(columnCount, "col_%d");
    }

    private int detectColumnCount(BufferedReader reader) throws IOException {
        reader.mark(4096);
        String firstLine = reader.readLine();
        reader.reset();
        return firstLine != null ? parseLine(firstLine).size() : 0;
    }

    private List<DataType> determineDataTypes(BufferedReader reader, int columnCount)
            throws IOException {
        if (specifiedTypes != null) {
            validateTypeSpecification(columnCount);
            return new ArrayList<>(specifiedTypes);
        }

        // 采样数据行进行类型推断
        reader.mark(4096 * 16);
        List<List<String>> sampleRows = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null && sampleRows.size() < sampleSize) {
            sampleRows.add(parseLine(line));
        }
        reader.reset();

        return inferTypesFromSample(sampleRows, columnCount);
    }

    private List<DataType> inferTypesFromSample(List<List<String>> sampleRows, int columnCount) {
        List<DataType> types = new ArrayList<>(Collections.nCopies(columnCount, DataType.STRING));

        for (int col = 0; col < columnCount; col++) {
            DataType detectedType = DataType.STRING;
            for (List<String> row : sampleRows) {
                if (row.size() <= col) continue;

                DataType currentType = detectCellType(row.get(col));
                if (currentType.ordinal() < detectedType.ordinal()) {
                    detectedType = currentType;
                }
            }
            types.set(col, detectedType);
        }
        return types;
    }

    private DataType detectCellType(String value) {
        if (value == null || value.isEmpty()) return DataType.STRING;

        // 检测布尔类型
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return DataType.BOOLEAN;
        }

        // 检测整数
        try {
            Integer.parseInt(value);
            return DataType.INTEGER;
        } catch (NumberFormatException e) { /* 继续检测 */ }

        // 检测浮点数
        try {
            Double.parseDouble(value);
            return DataType.DOUBLE;
        } catch (NumberFormatException e) { /* 默认字符串 */ }

        return DataType.STRING;
    }

    private void loadDataRows(BufferedReader reader, AutoTypeDataSet dataset) throws IOException {
        String line;
        int lineNum = hasHeader ? 1 : 0;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            try {
                List<String> rawValues = parseLine(line);
                List<Object> converted = convertValues(rawValues, dataset.getColumnTypes());
                dataset.addRow(converted);
            } catch (Exception e) {
                throw new DataLoadingException("Error processing line " + lineNum + ": " + e.getMessage(), e);
            }
        }
    }

    private List<Object> convertValues(List<String> rawValues, List<DataType> types) {
        List<Object> converted = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            String value = i < rawValues.size() ? rawValues.get(i) : "";
            converted.add(convertSingleValue(value, types.get(i)));
        }
        return converted;
    }

    private Object convertSingleValue(String value, DataType type) {
        if (value == null || value.isEmpty()) return null;

        switch (type) {
            case INTEGER:
                return Integer.parseInt(value);
            case DOUBLE:
                return Double.parseDouble(value);
            case BOOLEAN:
                return Boolean.parseBoolean(value);
            case STRING:
                return value.trim();
            default:
                throw new IllegalArgumentException("Unexpected type: " + type);
        }
    }

    private List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                values.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().trim());
        return values;
    }

    private void validateTypeSpecification(int columnCount) {
        if (specifiedTypes.size() != columnCount) {
            throw new DataLoadingException(String.format(
                    "Specified types count (%d) doesn't match column count (%d)",
                    specifiedTypes.size(), columnCount
            ));
        }
    }

    public static class DataLoadingException extends RuntimeException {
        public DataLoadingException(String message) {
            super(message);
        }

        public DataLoadingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
