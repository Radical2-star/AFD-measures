package utils;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/28
 */
public class MinMaxPair<T extends Comparable<T>> extends Pair<T, T>{
    public MinMaxPair() {
        super();
    }

    public MinMaxPair(T min, T max) {
        super(min, max);
    }

    public void update(T newValue) {
        if (getLeft() == null && getRight() == null) {
            setLeft(newValue);
            setRight(newValue);
        }
        updateMin(newValue);
        updateMax(newValue);
    }

    public void updateMin(T newValue) {
        if (newValue.compareTo(getLeft()) < 0) {
            setLeft(newValue);
        }
    }

    public void updateMax(T newValue) {
        if (newValue.compareTo(getRight()) > 0) {
            setRight(newValue);
        }
    }

    public boolean isEmpty() {
        return getLeft() == null && getRight() == null;
    }
}
