package experiment;

import model.FunctionalDependency;
import java.util.Set;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/6/22
 */
public class ExperimentResult {
    private final Set<FunctionalDependency> fds;
    private final long executionTimeMs;
    private final long validationCount;

    public ExperimentResult(Set<FunctionalDependency> fds, long executionTimeMs, long validationCount) {
        this.fds = fds;
        this.executionTimeMs = executionTimeMs;
        this.validationCount = validationCount;
    }

    public Set<FunctionalDependency> getFds() {
        return fds;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long getValidationCount() {
        return validationCount;
    }
}
