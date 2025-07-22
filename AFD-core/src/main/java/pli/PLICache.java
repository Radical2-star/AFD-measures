package pli;

import model.DataSet;
import utils.BitSetUtils;
import utils.Trie;

import java.util.*;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/2/28
 */
public class PLICache extends Trie<PLI> {
    // 随机缓存概率
    public static final double CACHE_PROBABILITY = 0.5;
    private final DataSet dataset;

    public PLICache(DataSet dataset) {
        this.dataset = dataset;
        initializeSingleColumnPLIs();
    }

    // 初始化单列PLI
    private void initializeSingleColumnPLIs() {
        int columnCount = dataset.getColumnCount();
        for (int col = 0; col < columnCount; col++) {
            BitSet columns = new BitSet();
            columns.set(col);
            PLI pli = new PLI(columns, dataset);
            List<Integer> key = Collections.singletonList(col);
            set(key, pli);
        }
    }

    /**
     * 获取或计算指定属性集的PLI
     * @param targetColumns 需要计算的属性集
     * @return 对应的PLI
     */
    public PLI getOrCalculatePLI(BitSet targetColumns) {
        List<Integer> sortedCols = BitSetUtils.bitSetToList(targetColumns);
        // 1. 优先从缓存获取
        PLI cached = get(sortedCols);
        if (cached != null) return cached;

        // 2. 获取所有可能子集PLI
        List<PLI> candidatePLIs = findAllSubsetPLIs(targetColumns);

        // 3. 贪心选择覆盖最多新属性的PLI（动态计算覆盖增益）
        List<PLI> selectedPLIs = new ArrayList<>();
        BitSet covered = new BitSet();
        while (!covered.equals(targetColumns)) {
            PLI bestPLI = null;
            int maxNewCover = -1;
            int minSize = Integer.MAX_VALUE;

            // 遍历候选集寻找最佳PLI
            for (PLI pli : candidatePLIs) {
                BitSet pliCols = pli.getColumns();
                // 计算新增覆盖列数
                BitSet newCover = (BitSet) pliCols.clone();
                newCover.andNot(covered);
                int newCoverCount = newCover.cardinality();

                // 选择条件：最多新覆盖列 → 若相同则选size更小的
                if (newCoverCount > maxNewCover ||
                        (newCoverCount == maxNewCover && pli.size() < minSize)) {
                    bestPLI = pli;
                    maxNewCover = newCoverCount;
                    minSize = pli.size();
                }
            }

            if (bestPLI == null) throw new RuntimeException("No PLI found.");
            selectedPLIs.add(bestPLI);
            covered.or(bestPLI.getColumns());
            candidatePLIs.remove(bestPLI);
        }

        // 4. 按size升序排序（确保先处理较小PLI）
        selectedPLIs.sort(Comparator.comparingInt(PLI::size));

        // 5. 逐步合并PLI
        PLI result = selectedPLIs.get(0);
        for (int i = 1; i < selectedPLIs.size(); i++) {
            result = result.intersect(selectedPLIs.get(i));
        }

        // 6. 概率性缓存（使用合并后的最终列）
        if (Math.random() < CACHE_PROBABILITY) {
            List<Integer> finalKey = BitSetUtils.bitSetToList(targetColumns);
            set(finalKey, result);
        }

        return result;
    }

    /**
     * 辅助方法，通过BitSet获取PLI
     * @param columns 属性集
     * @return 对应的PLI，如果不存在则返回null
     */
    public PLI getPLI(BitSet columns) {
        return get(BitSetUtils.bitSetToList(columns));
    }

    /**
     * 辅助方法，通过BitSet检查是否存在PLI
     * @param columns 属性集
     * @return 是否存在
     */
    public boolean containsKey(BitSet columns) {
        return get(BitSetUtils.bitSetToList(columns)) != null;
    }

    /**
     * 高效查找LHS的最佳已缓存子集PLI
     * 通过遍历Trie树，寻找路径上存在的尽可能深的、已被缓存的PLI，避免任何重计算
     * 注意，这里查找到的并不一定是最深的，而是尽可能深的，是为了降低复杂度，避免多次遍历
     * 只按顺序查找子集，例如[1,2,3]，则只查找[1],[1,2],[1,2,3]，不查找[1,3]
     * @param lhs 左手边属性集
     * @return 找到的最佳子集PLI
     */
    public PLI findBestCachedSubsetPli(BitSet lhs) {
        List<Integer> columns = BitSetUtils.bitSetToList(lhs);
        if (columns.isEmpty()) {
            return null;
        }

        Node<PLI> currentNode = getRoot();
        PLI bestPli = null;

        // 沿着LHS的路径遍历Trie
        for (int col : columns) {
            Node<PLI> child = currentNode.getChild(col);
            if (child == null) {
                // 路径中断，无法继续深入
                break;
            }
            if (child.getValue() != null) {
                // 找到了一个被缓存的PLI，记录下来
                bestPli = child.getValue();
            }
            currentNode = child;
        }

        // 如果遍历完整个LHS路径都没有找到任何缓存的PLI（除了根节点）
        // 则退一步，返回单列中第一个属性的PLI（这个是保证一定存在的）
        // 理论上这部分应该不可能被执行，因为单列PLI一定被缓存
        if (bestPli == null) {
            BitSet firstCol = new BitSet();
            firstCol.set(columns.get(0));
            return getOrCalculatePLI(firstCol);
        }

        return bestPli;
    }

    private List<PLI> findAllSubsetPLIs(BitSet target) {
        List<PLI> result = new ArrayList<>();
        // 递归遍历Trie树
        traverseSubsets(
                this.getRoot(),
                new BitSet(), // 初始空路径
                BitSetUtils.bitSetToList(target),
                target,
                result
        );
        return result;
    }

    /**
     * 递归辅助函数：遍历所有可能的子集路径
     * @param node       当前Trie节点
     * @param currentPath 当前路径的列集合（BitSet）
     * @param targetOrder 目标列的有序列表（如[1,2,3]）
     * @param target     目标列集合
     * @param result     结果收集列表
     */
    private void traverseSubsets(
            Node<PLI> node,
            BitSet currentPath,
            List<Integer> targetOrder, // 传入list方便遍历，可能可以合并
            BitSet target, // 传入BitSet方便判断子集
            List<PLI> result
    ) {
        // 如果当前节点有值（非根节点）且是目标子集（这个判断在for循环里），则加入结果
        if (node != this.getRoot() && node.getValue() != null) {
            result.add(node.getValue());
        }

        // 遍历所有可能的后续列（保证递增顺序）
        for (int col : targetOrder) {
            // 剪枝1：当前路径已经包含该列则跳过
            if (currentPath.get(col)) continue;
            // 剪枝2：该列不在目标中则跳过
            if (!target.get(col)) continue;
            // 剪枝3：检查当前路径添加col后是否仍是目标子集
            BitSet newPath = (BitSet) currentPath.clone();
            newPath.set(col);
            if (!BitSetUtils.isSubSet(newPath, target)) continue;

            // 查找Trie中是否存在该列的子节点
            Node<PLI> child = node.getChildren().get(col);
            if (child == null) continue;

            // 递归深入
            traverseSubsets(child, newPath, targetOrder, target, result);
        }
    }
}