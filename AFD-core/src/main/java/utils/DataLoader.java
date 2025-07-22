package utils;
import model.DataSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
public class DataLoader {
    private final Path filePath;
    private boolean hasHeader = true;
    private char delimiter = ',';

    private DataLoader(Path filePath) {
        this.filePath = filePath;
    }

    public static DataLoader fromFile(Path filePath) {
        return new DataLoader(filePath);
    }

    public DataLoader withHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
        return this;
    }

    public DataLoader withDelimiter(char delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public DataSet load() throws DataLoadingException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            List<String> headers = readHeaders(reader);
            DataSet dataset = new DataSet(headers);
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
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            headers.add("col_" + (i + 1));
        }
        return headers;
    }

    private int detectColumnCount(BufferedReader reader) throws IOException {
        reader.mark(4096);
        String firstLine = reader.readLine();
        reader.reset();
        return firstLine != null ? parseLine(firstLine).size() : 0;
    }

    private void loadDataRows(BufferedReader reader, DataSet dataset) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            List<String> values = parseLine(line);
            dataset.addRow(values);
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

    public static class DataLoadingException extends RuntimeException {
        public DataLoadingException(String message) {
            super(message);
        }

        public DataLoadingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}