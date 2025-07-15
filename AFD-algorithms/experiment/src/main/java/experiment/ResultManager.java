package experiment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages experiment results, including saving them to a file and checking for duplicates.
 * Supports different run modes for appending to or overwriting existing results.
 *
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/23
 */
public class ResultManager {

    public enum RunMode {
        APPEND,
        OVERWRITE
    }

    private static final String HEADER = "Timestamp,Dataset,Columns,Rows,ConfigName,Algorithm,FD_Count,Validation_Count,ExecutionTime_ms,Status,ErrorMessage";
    private final String filePath;
    private final Set<String> existingResults = new HashSet<>();

    /**
     * Initializes the ResultManager, handling the results file based on the specified run mode.
     *
     * @param filePath The path to the CSV file for storing results.
     * @param mode     The run mode (APPEND or OVERWRITE).
     */
    public ResultManager(String filePath, RunMode mode) {
        this.filePath = filePath;
        File file = new File(filePath);

        try {
            if (mode == RunMode.OVERWRITE && file.exists()) {
                String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                String backupPath = filePath.replace(".csv", "_" + timestamp + ".csv");
                Files.move(Paths.get(filePath), Paths.get(backupPath), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Backed up existing results to: " + backupPath);
            }
            
            boolean fileExists = file.exists();
            if (!fileExists) {
                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath, true)))) {
                    writer.println(HEADER);
                }
            } else {
                loadExistingResults();
            }

        } catch (IOException e) {
            throw new RuntimeException("Error managing result file: " + filePath, e);
        }
    }

    private void loadExistingResults() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 6); // We only need parts for the key
                if (parts.length >= 5) {
                    String key = createKey(parts[1], parts[4], parts[5].split(",")[0]);
                    existingResults.add(key);
                }
            }
        }
    }

    /**
     * Checks if the result for a specific experiment already exists.
     *
     * @param datasetName   The name of the dataset.
     * @param configName    The name of the configuration.
     * @param algorithmName The name of the algorithm.
     * @return true if the result exists, false otherwise.
     */
    public boolean isResultExists(String datasetName, String configName, String algorithmName) {
        return existingResults.contains(createKey(datasetName, configName, algorithmName));
    }

    /**
     * Writes a new experiment result to the CSV file.
     *
     * @param datasetName    The name of the dataset.
     * @param configName     The name of the configuration.
     * @param algorithmName  The name of the algorithm.
     * @param result         The ExperimentResult object.
     * @param columns        The number of columns in the dataset.
     * @param rows           The number of rows in the dataset.
     */
    public void writeResult(String datasetName, String configName, String algorithmName, 
                            ExperimentResult result, int columns, int rows) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String resultLine = String.join(",",
                timestamp,
                datasetName,
                String.valueOf(columns),
                String.valueOf(rows),
                configName,
                algorithmName,
                String.valueOf(result.getFds().size()),
                String.valueOf(result.getValidationCount()),
                String.valueOf(result.getExecutionTimeMs()),
                "SUCCESS",
                "" // No error message
        );

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath, true)))) {
            writer.println(resultLine);
            existingResults.add(createKey(datasetName, configName, algorithmName));
        } catch (IOException e) {
            System.err.println("Failed to write result to file: " + e.getMessage());
        }
    }

    /**
     * Writes an error to the CSV file.
     *
     * @param datasetName    The name of the dataset.
     * @param configName     The name of the configuration.
     * @param algorithmName  The name of the algorithm.
     * @param e              The exception that occurred.
     * @param columns        The number of columns in the dataset.
     * @param rows           The number of rows in the dataset.
     */
    public void writeError(String datasetName, String configName, String algorithmName, 
                          Exception e, int columns, int rows) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage().replace(",", ";"); // Avoid breaking CSV format

        String resultLine = String.join(",",
                timestamp,
                datasetName,
                String.valueOf(columns),
                String.valueOf(rows),
                configName,
                algorithmName,
                "N/A", "N/A", "N/A", // No results
                "FAILURE",
                errorMessage
        );

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath, true)))) {
            writer.println(resultLine);
            existingResults.add(createKey(datasetName, configName, algorithmName));
        } catch (IOException ex) {
            System.err.println("Failed to write error to file: " + ex.getMessage());
        }
    }
    
    /**
     * Writes an error to the CSV file (原有方法的重载，兼容旧版本调用)
     */
    public void writeError(String datasetName, String configName, String algorithmName, Exception e) {
        writeError(datasetName, configName, algorithmName, e, -1, -1); // 使用-1表示未知列数和行数
    }

    /**
     * Writes a new experiment result to the CSV file (原有方法的重载，兼容旧版本调用)
     */
    public void writeResult(String datasetName, String configName, String algorithmName, ExperimentResult result) {
        writeResult(datasetName, configName, algorithmName, result, -1, -1); // 使用-1表示未知列数和行数
    }

    private String createKey(String datasetName, String configName, String algorithmName) {
        return datasetName + "|" + configName + "|" + algorithmName;
    }
}
