package algorithm;

import model.FunctionalDependency;
import utils.LongBitSetUtils;

import java.util.BitSet;
import java.util.Objects;

/**
 * 基于long优化的Node类
 * 专门为≤64列的数据集优化，使用long替代BitSet提升性能
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/7/30
 */
public class Node implements Comparable<Node>{
    private final long lhs;  // 使用long替代BitSet
    private final int rhs;
    private int level;       // 延迟计算，不再是final
    private boolean isValidated;
    private double error;

    {
        this.isValidated = false;
        this.error = Double.MAX_VALUE; // 注意度量不一定保证error <= 1.0
    }

    public Node(long lhs, int rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
        // 优化：延迟计算level，只在需要时计算
        this.level = -1; // 标记为未计算
    }

    // 兼容性构造函数，从BitSet转换
    @Deprecated
    public Node(java.util.BitSet lhsBitSet, int rhs) {
        this.lhs = utils.BitSetUtils.bitSetToLong(lhsBitSet, 64); // 假设最大64列
        this.rhs = rhs;
        this.level = -1;
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

    public long getLhs() {
        return lhs;
    }

    // 兼容性方法，返回BitSet表示
    @Deprecated
    public java.util.BitSet getLhsBitSet() {
        java.util.BitSet bitSet = new java.util.BitSet();
        for (int i = 0; i < 64; i++) {
            if ((lhs & (1L << i)) != 0) {
                bitSet.set(i);
            }
        }
        return bitSet;
    }

    public int getRhs() {
        return rhs;
    }

    public int getLevel() {
        // 延迟计算level，使用高效的Long.bitCount
        if (level == -1) {
            level = LongBitSetUtils.cardinality(lhs);
        }
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
        // 将long转换为Set<Integer>
        return new FunctionalDependency(
            new java.util.HashSet<>(LongBitSetUtils.longToList(lhs)),
            rhs,
            error
        );
    }

    @Override
    public String toString() {
        return "Node{" + LongBitSetUtils.toString(lhs, 64) +
                "→" + rhs +
                ", error=" + error +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return rhs == node.rhs && lhs == node.lhs;
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
