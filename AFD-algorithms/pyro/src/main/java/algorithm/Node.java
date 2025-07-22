package algorithm;

import model.FunctionalDependency;

import java.util.BitSet;
import java.util.Objects;

import static utils.BitSetUtils.*;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/15
 */
public class Node implements Comparable<Node>{
    private final BitSet lhs;
    private final int rhs;
    private final int level;
    private boolean isValidated;
    private double error;

    {
        this.isValidated = false;
        this.error = Double.MAX_VALUE; // 注意度量不一定保证error <= 1.0
    }

    public Node(BitSet lhs, int rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.level = lhs.cardinality(); // TODO: 这里影响性能
    }

    // public Node(BitSet lhs, int rhs, int level) {
    //     this.lhs = lhs;
    //     this.rhs = rhs;
    //     this.level = level;
    // }
    //
    // public Node(Set<Integer> lhs, int rhs) {
    //     this.lhs = setToBitSet(lhs);
    //     this.rhs = rhs;
    //     this.level = lhs.size();
    // }

    public BitSet getLhs() {
        return lhs;
    }

    public int getRhs() {
        return rhs;
    }

    public int getLevel() {
        return level;
    }

    public double getError() {
        return error;
    }

    public boolean isEstimated() {
        return error != Double.MAX_VALUE;
    }

    public boolean isValidated() {
        return isValidated;
    }

    public boolean isValid(double maxError) {
        return isValidated && error <= maxError;
    }

    public boolean isInvalid(double maxError) {
        return isValidated && error > maxError;
    }

    void setError(double error) {
        this.error = error;
    }

    void setValidated() {
        isValidated = true;
    }

    public FunctionalDependency toFD() {
        return new FunctionalDependency(bitSetToSet(lhs), rhs, error);
    }

    @Override
    public String toString() {
        return "Node{" + lhs +
                "→" + rhs +
                ", error=" + error +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return rhs == node.rhs && lhs.equals(node.lhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public int compareTo(Node o) {
        return Double.compare(this.error, o.error);
    }
}
