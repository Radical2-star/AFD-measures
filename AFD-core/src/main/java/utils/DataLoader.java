package utils;

import model.DataSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @ClassName DataLoader
 * @Description
 * @Author Zuoxing Xie
 * @Time 2025/2/26
 * @Version 1.0
 */
public class DataLoader {
    public static DataSet loadCSV(Path filePath) throws IOException {
        try (Stream<String> lines = Files.lines(filePath)) {
            List<List<Object>> data = new ArrayList<>();
            List<String> attributes = new ArrayList<>();

            lines.forEach(line -> {
                String[] parts = line.split(",");
                if (attributes.isEmpty()) {
                    attributes.addAll(Arrays.asList(parts));
                } else {
                    data.add(Arrays.stream(parts)
                            .map(String::trim)
                            .collect(Collectors.toList()));
                }
            });

            return new DataSet(attributes, data);
        }
    }
}