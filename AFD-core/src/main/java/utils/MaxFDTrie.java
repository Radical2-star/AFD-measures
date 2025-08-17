package utils;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static utils.BitSetUtils.bitSetToList;

/**
 * MaxFDTrie - 最大无效函数依赖的Trie存储结构
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/5/6
 */
public class MaxFDTrie extends Trie<Boolean>{

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
     * 检查Trie中是否包含指定位集合的超集（long版本，性能更优）
     * @param bits long表示的位集合（支持最多64列）
     * @return 如果Trie中存在bits的超集则返回true，否则返回false
     */
    public boolean containsSuperSetOf(long bits) {
        List<Integer> key = LongBitSetUtils.longToList(bits);
        return containsSuperSetOf(key);
    }

    // ==================== 原有BitSet版本方法（兼容性） ====================

    /**
     * 添加BitSet表示的位集合到Trie中（BitSet版本，兼容性方法）
     * @param bitSetKey BitSet表示的位集合
     */
    public void add(BitSet bitSetKey) {
        List<Integer> key = bitSetToList(bitSetKey);
        set(key, true);
    }

    /**
     * 检查Trie中是否包含指定列表的超集（List版本，兼容性方法）
     * @param key 整数列表表示的位集合
     * @return 如果Trie中存在key的超集则返回true，否则返回false
     */
    public boolean containsSuperSetOf(List<Integer> key) {
        return containsSuperSetOfHelper(getRoot(), key, 0);
    }

    private boolean containsSuperSetOfHelper(Node<Boolean> currentNode, List<Integer> key, int index) {
        // 如果已经检查完所有的元素，说明找到了一个超集
        if (index >= key.size()) {
            return true;
        }

        // 获取当前要检查的元素
        int currentElement = key.get(index);

        // 遍历当前节点的所有子节点
        for (Map.Entry<Integer, Node<Boolean>> entry : currentNode.getChildren().entrySet()) {
            int childKey = entry.getKey();
            Node<Boolean> childNode = entry.getValue();

            // 如果子节点的键大于当前元素，说明后面的子节点也不可能包含当前元素，直接跳过
            if (childKey > currentElement) {
                continue;
            }

            // 如果子节点的键等于当前元素，继续检查下一个元素
            if (childKey == currentElement) {
                if (containsSuperSetOfHelper(childNode, key, index + 1)) {
                    return true;
                }
            }

            // 如果子节点的键小于当前元素，继续检查当前元素
            if (childKey < currentElement) {
                if (containsSuperSetOfHelper(childNode, key, index)) {
                    return true;
                }
            }
        }

        // 如果没有找到匹配的路径，返回false
        return false;
    }

}
