package utils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * HittingSet - 最小命中集的Trie存储结构
 * 支持BitSet和long两种位集合表示，long版本性能更优
 *
 * @author Hoshi
 * @version 2.0
 * @since 2025/5/12
 */
public class HittingSet extends Trie<Boolean>{
    public HittingSet() {
        super();
    }

    public boolean isEmpty() {
        return getRoot().getChildren().isEmpty();
    }

    // ==================== 新的long版本方法（性能优化） ====================

    /**
     * 添加long表示的位集合到HittingSet中（long版本，性能更优）
     * @param bits long表示的位集合（支持最多64列）
     */
    public void add(long bits) {
        List<Integer> key = LongBitSetUtils.longToList(bits);
        setIfAbsent(key, true);
    }

    /**
     * 从HittingSet中删除long表示的位集合（long版本，性能更优）
     * @param bits long表示的位集合（支持最多64列）
     */
    public void delete(long bits) {
        List<Integer> key = LongBitSetUtils.longToList(bits);
        delete(key);
    }

    /**
     * 如果不存在子集则添加long表示的位集合（long版本，性能更优）
     * @param bits long表示的位集合（支持最多64列）
     */
    public void addIfNoSubset(long bits) {
        List<Integer> key = LongBitSetUtils.longToList(bits);

        // 先检查是否存在子集
        if (containsSubset(key)) {
            return; // 存在子集，不执行添加
        }

        // 不存在子集则添加
        this.add(bits);
    }

    /**
     * 移除指定位集合的所有子集（long版本，性能更优）
     * @param bits long表示的位集合（支持最多64列）
     * @return 被移除的子集列表（long格式）
     */
    public List<Long> removeSubsets(long bits) {
        List<Integer> key = LongBitSetUtils.longToList(bits);
        List<Long> result = new ArrayList<>();
        // 直接使用long版本的辅助方法，避免BitSet转换
        removeSubsetsHelperLong(null, -1, getRoot(), 0L, key, result);
        return result;
    }

    /**
     * 移除子集的辅助方法（long版本，性能更优）
     * @param parentNode 当前节点的父节点
     * @param parentKey 从父节点到当前节点的键
     * @param currentNode 当前正在检查的节点
     * @param currentPath 当前路径（long表示）
     * @param key 输入的位集合转换成的List
     * @param result 存储被移除子集的结果列表
     */
    private void removeSubsetsHelperLong(
            Node<Boolean> parentNode,        // 当前节点的父节点
            int parentKey,                   // 从父节点到当前节点的键
            Node<Boolean> currentNode,       // 当前正在检查的节点
            long currentPath,                // 当前路径（long表示）
            List<Integer> key,               // 输入的位集合转换成的List
            List<Long> result                // 存储被移除子集的结果列表
    ) {
        // 如果当前节点包含有效值，说明找到了一个存储的集合
        if (currentNode.getValue() != null && currentNode.getValue()) {
            // 检查当前路径是否是输入key的子集
            boolean isSubset = true;
            for (int bit : LongBitSetUtils.longToList(currentPath)) {
                if (!key.contains(bit)) {
                    isSubset = false;
                    break;
                }
            }

            if (isSubset) {
                // 当前路径是key的子集，需要移除
                result.add(currentPath);
                // 从父节点中移除当前节点
                if (parentNode != null) {
                    parentNode.getChildren().remove(parentKey);
                }
                return; // 移除后不需要继续遍历子节点
            }
        }

        // 遍历当前节点的所有子节点
        // 需要复制键集合以避免并发修改异常
        List<Integer> childKeys = new ArrayList<>(currentNode.getChildren().keySet());

        for (int childKey : childKeys) {
            Node<Boolean> childNode = currentNode.getChild(childKey);
            if (childNode != null) {
                // 检查子节点的键是否在输入key中
                if (key.contains(childKey)) {
                    // 将子节点键加入当前路径
                    long newPath = LongBitSetUtils.setBit(currentPath, childKey);

                    // 递归处理子节点
                    removeSubsetsHelperLong(
                            currentNode,
                            childKey,
                            childNode,
                            newPath,
                            key,
                            result
                    );
                }
            }
        }
    }

    /**
     * 获取所有最小命中集（long版本，性能更优）
     * @return 包含所有最小命中集的long列表
     */
    public List<Long> getAllMinimalHittingSetsLong() {
        List<Long> result = new ArrayList<>();
        traverseTrieLong(getRoot(), 0L, result);
        return result;
    }

    /**
     * 遍历Trie并收集long格式的结果（内部方法）
     * @param currentNode 当前节点
     * @param currentPath 当前路径的long表示
     * @param result 结果列表
     */
    private void traverseTrieLong(Node<Boolean> currentNode, long currentPath, List<Long> result) {
        // 如果当前节点是终止节点（包含有效值）
        if (currentNode.getValue() != null) {
            result.add(currentPath);
        }

        // 遍历所有子节点（需要复制key集合避免并发修改问题）
        List<Integer> childKeys = new ArrayList<>(currentNode.getChildren().keySet());
        childKeys.sort(Integer::compareTo); // 保证输出顺序稳定

        for (Integer key : childKeys) {
            // 添加当前键到路径
            long newPath = LongBitSetUtils.setBit(currentPath, key);
            // 递归处理子节点
            traverseTrieLong(currentNode.getChild(key), newPath, result);
        }
    }

    // ==================== 原有BitSet版本方法（兼容性） ====================

    /**
     * 添加BitSet表示的位集合到HittingSet中（BitSet版本，兼容性方法）
     * @param bitSetKey BitSet表示的位集合
     */
    public void add(BitSet bitSetKey) {
        List<Integer> key = BitSetUtils.bitSetToList(bitSetKey);
        setIfAbsent(key, true);
    }

    /**
     * 从HittingSet中删除BitSet表示的位集合（BitSet版本，兼容性方法）
     * @param bitSetKey BitSet表示的位集合
     */
    public void delete(BitSet bitSetKey) {
        List<Integer> key = BitSetUtils.bitSetToList(bitSetKey);
        delete(key);
    }

    /**
     * 如果不存在子集则添加BitSet表示的位集合（BitSet版本，兼容性方法）
     * @param bitSetKey BitSet表示的位集合
     */
    public void addIfNoSubset(BitSet bitSetKey) {
        List<Integer> key = BitSetUtils.bitSetToList(bitSetKey);

        // 先检查是否存在子集
        if (containsSubset(key)) {
            return; // 存在子集，不执行添加
        }

        // 不存在子集则添加
        this.add(bitSetKey);
    }

    // 检查是否存在子集
    public boolean containsSubset(List<Integer> key) {
        return containsSubsetHelper(getRoot(), new ArrayList<>(), key);
    }

    // 递归检查的核心逻辑
    private boolean containsSubsetHelper(
            Node<Boolean> currentNode,
            List<Integer> currentPath,
            List<Integer> targetKey
    ) {
        // 终止条件1：当前路径是已存储的键，且是targetKey的子集
        if (currentNode.getValue() != null && isSubset(currentPath, targetKey)) {
            return true;
        }

        // 遍历所有子节点（需要排序以保证确定性）
        List<Integer> sortedKeys = new ArrayList<>(currentNode.getChildren().keySet());
        sortedKeys.sort(Integer::compareTo); // 避免遍历顺序影响结果

        for (int childKey : sortedKeys) {
            // 剪枝：只有子节点键在目标集合中才有意义继续
            if (targetKey.contains(childKey)) {
                List<Integer> newPath = new ArrayList<>(currentPath);
                newPath.add(childKey);

                // 递归检查子节点
                if (containsSubsetHelper(
                        currentNode.getChild(childKey),
                        newPath,
                        targetKey
                )) {
                    return true; // 发现子集立即返回
                }
            }
        }

        return false;
    }

    public List<BitSet> removeSubsets(BitSet bitSetKey) {
        List<BitSet> result = new ArrayList<>();
        List<Integer> key = BitSetUtils.bitSetToList(bitSetKey);
        // 传入父节点为null，currentKey为无效值（根节点没有父节点）
        removeSubsetsHelper(null, -1, getRoot(), new ArrayList<>(), key, result);
        return result;
    }

    private void removeSubsetsHelper(
            Node<Boolean> parentNode,        // 当前节点的父节点
            int parentKey,                   // 从父节点到当前节点的键
            Node<Boolean> currentNode,       // 当前正在检查的节点
            List<Integer> currentPath,       // 当前路径（用于构建BitSet）
            List<Integer> key,               // 输入的BitSet转换成的List
            List<BitSet> result              // 需要返回的结果列表
    ) {
        // 先递归处理所有子节点（深度优先遍历）
        List<Integer> childKeys = new ArrayList<>(currentNode.getChildren().keySet());
        for (int childKey : childKeys) {
            Node<Boolean> childNode = currentNode.getChild(childKey);

            // 只有子节点的键在目标集合中才继续探索
            if (key.contains(childKey)) {
                List<Integer> newPath = new ArrayList<>(currentPath);
                newPath.add(childKey); // 将子节点键加入当前路径

                // 递归处理子节点（当前节点成为父节点，childKey是连接键）
                removeSubsetsHelper(
                        currentNode,
                        childKey,
                        childNode,
                        newPath,
                        key,
                        result
                );
            }
        }

        // 后序处理当前节点（确保子节点已处理完）
        if (currentNode.getValue() != null) { // 当前路径是Trie中的一个完整键
            BitSet currentBitSet = BitSetUtils.listToBitSet(currentPath);
            // 检查当前路径是否是输入key的子集
            if (isSubset(currentPath, key)) {
                // 并加入结果
                result.add(currentBitSet);
                // 直接从父节点删除当前节点（如果有父节点）
                if (parentNode != null) {
                    parentNode.getChildren().remove(parentKey);
                }
            }
        }
    }

    public List<BitSet> getAllMinimalHittingSets() {
        List<BitSet> result = new ArrayList<>();
        traverseTrie(getRoot(), new ArrayList<>(), result);
        return result;
    }

    private void traverseTrie(Node<Boolean> currentNode,
                              List<Integer> currentPath,
                              List<BitSet> result) {
        // 如果当前节点是终止节点（包含有效值）
        if (currentNode.getValue() != null) {
            // 将当前路径转换为BitSet
            BitSet bitSet = BitSetUtils.listToBitSet(currentPath);
            result.add(bitSet);
        }

        // 遍历所有子节点（需要复制key集合避免并发修改问题）
        List<Integer> childKeys = new ArrayList<>(currentNode.getChildren().keySet());
        childKeys.sort(Integer::compareTo); // 保证输出顺序稳定

        for (Integer key : childKeys) {
            // 添加当前键到路径
            currentPath.add(key);
            // 递归处理子节点
            traverseTrie(currentNode.getChild(key), currentPath, result);
            // 回溯：移除最后添加的键
            currentPath.remove(currentPath.size() - 1);
        }
    }

    // 辅助方法：判断subset是否是superset的子集
    private boolean isSubset(List<Integer> subset, List<Integer> superset) {
        // TODO: 改为用BitSet判断子集
        return superset.containsAll(subset);
    }

}
