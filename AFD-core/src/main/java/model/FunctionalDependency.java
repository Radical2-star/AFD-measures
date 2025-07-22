package model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 *  FD
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/26
 */
public class FunctionalDependency {
    private final Set<Integer> lhs;  // 左侧属性集合
    private final int rhs;           // 右侧单个属性
    private final double error;

    public FunctionalDependency(Set<Integer> determinantIndices,
                                int dependentIndex) {
        validateIndices(determinantIndices, dependentIndex);
        this.lhs = Collections.unmodifiableSet(new HashSet<>(determinantIndices));
        this.rhs = dependentIndex;
        this.error = 0;
    }

    public FunctionalDependency(Set<Integer> determinantIndices,
                                int dependentIndex,
                                double error) {
        validateIndices(determinantIndices, dependentIndex);
        this.lhs = Collections.unmodifiableSet(new HashSet<>(determinantIndices));
        this.rhs = dependentIndex;
        this.error = error;
    }

    private void validateIndices(Set<Integer> left, int right) {
        if (left.contains(right)) {
            throw new IllegalArgumentException(
                    String.format("RHS attribute %d cannot be in LHS", right)
            );
        }
    }

    // Getters
    public Set<Integer> getLhs() {
        return lhs;
    }

    public int getRhs() {
        return rhs;
    }

    public double getError() {
        return error;
    }

    // Equality checks
    @Override
    public boolean equals(Object o) {
        // 仅判断LHS和RHS，不判断Error
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionalDependency that = (FunctionalDependency) o;
        return rhs == that.rhs && lhs.equals(that.lhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        // 输出格式为：{1,2,3} → 4 (0.05)
        return String.format("%s → %d (%f)",
                formatIndices(lhs),
                rhs,
                error);
    }

    private String formatIndices(Set<Integer> indices) {
        return indices.stream()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}