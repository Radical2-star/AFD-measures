package utils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static utils.BitSetUtils.bitSetToList;

/**
 * MinFDTrie - 最小有效函数依赖的Trie存储结构
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/5/6
 */
public class MinFDTrie extends Trie<Boolean>{

    // ==================== 新的long版本方法（性能优化） ====================

    /**
     * 添加long表示的位集合到Trie中（long版本，性能更优）
     * @param bits long表示的位集合（支持最多64列）
     */
    public void add(long bits) {
        List<Integer> key = LongBitSetUtils.longToList(bits);
        set(key, true);
    }

    /**
     * 检查Trie中是否包含指定位集合的子集（long版本，性能更优）
     * @param bits long表示的位集合（支持最多64列）
     * @return 如果Trie中存在bits的子集则返回true，否则返回false
     */
    public boolean containsSubSetOf(long bits) {
        List<Integer> key = LongBitSetUtils.longToList(bits);
        return containsSubSetOf(key);
    }

    /**
     * 将Trie中的所有位集合转换为long列表（long版本，性能更优）
     * @param columnCount 列数，用于验证
     * @return 包含所有存储位集合的long列表
     */
    public List<Long> toLongList(int columnCount) {
        LongBitSetUtils.validateColumnCount(columnCount);
        List<Long> result = new ArrayList<>();
        collectLongs(getRoot(), 0L, result);
        return result;
    }

    /**
     * 递归收集所有long表示的位集合（内部方法）
     * @param node 当前节点
     * @param currentPath 当前路径的long表示
     * @param result 结果列表
     */
    private void collectLongs(Node<Boolean> node, long currentPath, List<Long> result) {
        if (node == null) return;
        if (node.getValue() != null && node.getValue()) {
            result.add(currentPath);
            return;
        }

        for (int key : node.getChildren().keySet()) {
            Node<Boolean> child = node.getChild(key);
            long newPath = LongBitSetUtils.setBit(currentPath, key);
            collectLongs(child, newPath, result);
        }
    }

    // ==================== 原有BitSet版本方法（兼容性） ====================

    /**
     * 添加BitSet表示的位集合到Trie中（BitSet版本，兼容性方法）
     * @param bitSetKey BitSet表示的位集合
     */
    public void add(BitSet bitSetKey) {
        // TODO: 优化逻辑
        List<Integer> key = bitSetToList(bitSetKey);
        set(key, true);
    }

    /**
     * 检查Trie中是否包含指定列表的子集（List版本，兼容性方法）
     * @param key 整数列表表示的位集合
     * @return 如果Trie中存在key的子集则返回true，否则返回false
     */
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

    /**
     * 将Trie中的所有位集合转换为BitSet列表（BitSet版本，兼容性方法）
     * @param nbits 位数
     * @return 包含所有存储位集合的BitSet列表
     */
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
