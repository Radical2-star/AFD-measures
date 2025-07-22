package utils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static utils.BitSetUtils.bitSetToList;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/5/6
 */
public class MinFDTrie extends Trie<Boolean>{
    public void add(BitSet bitSetKey) {
        // TODO: 优化逻辑
        List<Integer> key = bitSetToList(bitSetKey);
        set(key, true);
    }


    public boolean containsSubSetOf(List<Integer> key) {
        Node<Boolean> root = getRoot();
        return containsSubSetOfHelper(root, key, 0);
    }

    private boolean containsSubSetOfHelper(Node<Boolean> node, List<Integer> key, int startIndex) {
        if (node == null) return false;
        // 如果当前节点是非空叶子节点，说明找到了一个完整的 storedKey
        if (node.getValue() != null && node.getValue()) {
            return true;
        }
        for (int i = startIndex; i < key.size(); i++) {
            int current = key.get(i);
            Node<Boolean> child = node.getChild(current);

            if (child != null) {
                // 继续深入查找，并确保下一层从当前元素之后开始
                if (containsSubSetOfHelper(child, key, i + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<BitSet> toList(int nbits) {
        List<BitSet> result = new ArrayList<>();
        collectBitSets(getRoot(), new BitSet(nbits), result);
        return result;
    }

    private void collectBitSets(Node<Boolean> node, BitSet currentPath, List<BitSet> result) {
        if (node == null) return;
        if (node.getValue() != null && node.getValue()) {
            result.add((BitSet) currentPath.clone());
            return;
        }

        for (int key : node.getChildren().keySet()) {
            Node<Boolean> child = node.getChild(key);

            currentPath.set(key);
            collectBitSets(child, currentPath, result);
            currentPath.clear(key); // 回溯
        }
    }

    /*
    public BitSet getAttributeBitSet(int nbits) {
        BitSet attributeSet = new BitSet(nbits);
        traverseAndCollectAttributes(getRoot(), attributeSet);
        return attributeSet;
    }

    private void traverseAndCollectAttributes(Node<Boolean> node, BitSet attributeSet) {
        if (node == null) return;

        // 遍历当前节点的所有子节点
        for (Integer key : node.getChildren().keySet()) {
            attributeSet.set(key);  // 标记该属性已出现
            traverseAndCollectAttributes(node.getChild(key), attributeSet);
        }
    }
    */

}
