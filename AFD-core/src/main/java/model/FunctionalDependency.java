package model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @ClassName FD
 * @Description
 * @Author Zuoxing Xie
 * @Time 2025/2/26
 * @Version 1.0
 */
public class FunctionalDependency {
    private final Set<Integer> lhs;
    private final int rhs;

    public FunctionalDependency(Set<Integer> lhs, int rhs) {
        this.lhs = Collections.unmodifiableSet(new HashSet<>(lhs));
        this.rhs = rhs;
    }

    public boolean isValid() {
        return !lhs.contains(rhs);
    }

    public Set<Integer> getLHS() {
        return lhs;
    }

    public int getRHS() {
        return rhs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FunctionalDependency)) return false;
        FunctionalDependency other = (FunctionalDependency) o;
        return rhs == other.rhs && lhs.equals(other.lhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }
}