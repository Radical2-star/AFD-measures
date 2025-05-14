package utils;

import java.util.Objects;

public class Pair<L, R> {
    private L left;
    private R right;
    public Pair() {
    }
    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }
    public L getLeft() {
        return left;
    }
    public R getRight() {
        return right;
    }
    public void setLeft(L left) {
        this.left = left;
    }
    public void setRight(R right) {
        this.right = right;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "left=" + left +
                ", right=" + right +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return left.equals(pair.left) && right.equals(pair.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }
}
