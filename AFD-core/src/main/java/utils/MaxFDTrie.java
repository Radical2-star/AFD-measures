package utils;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static utils.BitSetUtils.bitSetToList;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/5/6
 */
public class MaxFDTrie extends Trie<Boolean>{
    public void add(BitSet bitSetKey) {
        List<Integer> key = bitSetToList(bitSetKey);
        set(key, true);
    }

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
