package utils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/28
 */
public class Trie<T> {
    private final Node<T> root;

    protected static class Node<T> {
        private T value;
        private Map<Integer, Node<T>> children;

        public Node() {
            this.value = null;
            this.children = new HashMap<>();
        }

        public T getValue() {
            return value;
        }

        public Map<Integer, Node<T>> getChildren() {
            return children;
        }

        public Node<T> getChild(int key) {
            return children.get(key);
        }
    }

    {
        root = new Node<>();
        root.children = new HashMap<>();
    }

    public Trie() {
    }

    public T get(List<Integer> key) {
        Node<T> currentNode = root;
        for (int i : key) {
            if (!currentNode.children.containsKey(i)) {
                return null;
            }
            currentNode = currentNode.children.get(i);
        }
        return currentNode.value;
    }

    public void set(List<Integer> key, T value) {
        getOrCreateNode(key).value = value;
    }

    public T setIfAbsent(List<Integer> key, T value) {
        Node<T> node = getOrCreateNode(key);
        if (node.value == null) {
            node.value = value;
            return value;
        }
        return null;
    }

    Node<T> getOrCreateNode(List<Integer> key) {
        Node<T> currentNode = root;
        for (int i : key) {
            currentNode.children.putIfAbsent(i, new Node<>());
            currentNode = currentNode.children.get(i);
        }
        return currentNode;
    }

    public void delete(List<Integer> key) {
        // 简单地删除最后一个节点，如果这个节点不存在，则无事发生
        Node<T> currentNode = root;
        Node<T> prevNode = root;
        for (int i : key) {
            if (!currentNode.children.containsKey(i)) {
                return;
            }
            prevNode = currentNode;
            currentNode = currentNode.children.get(i);
        }
        prevNode.children.remove(key.get(key.size() - 1));
    }

    protected Node<T> getRoot() {
        return root;
    }

}
