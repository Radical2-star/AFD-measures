package utils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/28
 */
public class BitSetTrie<T> {
    private final Node<T> root;

    private static class Node<T> {
        private T value;
        private Map<Integer, Node<T>> children;
    }

    {
        root = new Node<>();
        root.children = new HashMap<>();
    }

    public BitSetTrie() {
    }

    public T get(BitSet key) {
        Node<T> currentNode = root;
        for (int i = key.nextSetBit(0); i >= 0; i = key.nextSetBit(i + 1)) {
            if (!currentNode.children.containsKey(i)) {
                return null;
            }
            currentNode = currentNode.children.get(i);
        }
        return currentNode.value;
    }

    public void set(BitSet key, T value) {
        getOrCreateNode(key).value = value;
    }

    public T setIfAbsent(BitSet key, T value) {
        Node<T> node = getOrCreateNode(key);
        if (node.value == null) {
            node.value = value;
            return value;
        }
        return null;
    }

    private Node<T> getOrCreateNode(BitSet key) {
        Node<T> currentNode = root;
        for (int i = key.nextSetBit(0); i >= 0; i = key.nextSetBit(i + 1)) {
            currentNode.children.putIfAbsent(i, new Node<>());
            currentNode = currentNode.children.get(i);
        }
        return currentNode;
    }

    public void delete(BitSet key) {
        // TODO: 实现节点的删除
    }
}
