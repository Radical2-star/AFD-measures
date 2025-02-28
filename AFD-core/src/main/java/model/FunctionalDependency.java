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

    public FunctionalDependency(Set<Integer> determinantIndices,
                                int dependentIndex) {
        validateIndices(determinantIndices, dependentIndex);
        this.lhs = Collections.unmodifiableSet(new HashSet<>(determinantIndices));
        this.rhs = dependentIndex;
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

    // Equality checks
    @Override
    public boolean equals(Object o) {
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
        return String.format("%s → %d",
                formatIndices(lhs),
                rhs);
    }

    private String formatIndices(Set<Integer> indices) {
        return indices.stream()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}