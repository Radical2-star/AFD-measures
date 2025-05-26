package algorithm;

import model.DataSet;
import model.FunctionalDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pli.PLICache;
import utils.BitSetUtils;
import utils.HittingSet;
import utils.MaxFDTrie;
import utils.MinFDTrie;
import utils.MinMaxPair;

import java.util.*;

import static utils.BitSetUtils.*;

/**
 * @author Hoshi
 * @version 1.0
 * @since 2025/4/15
 */
public class SearchSpace {
    private final int rhs;
    private final DataSet dataSet;
    private final PyroConfig config;
    private final PLICache cache;
    private final Node root;
    private final Map<BitSet, Node> nodeMap;
    private final MinFDTrie minValidFD;
    private final MaxFDTrie maxNonFD;
    private final Set<BitSet> peaks; // TODO: 优化为Trie
    private static final Logger logger = LoggerFactory.getLogger(SearchSpace.class);

    public SearchSpace(int rhs, DataSet dataSet, PLICache cache, PyroConfig config) {
        this.rhs = rhs;
        this.dataSet = dataSet;
        this.cache = cache;
        this.config = config;
        this.nodeMap = new HashMap<>();
        this.root = getOrCreateNode(new BitSet(dataSet.getColumnCount()));
        this.minValidFD = new MinFDTrie();
        this.maxNonFD = new MaxFDTrie();
        this.peaks = new HashSet<>();
    }

    public void explore() {
        logger.info("开始搜索, rhs = {}", rhs);
        // 判断是否为近似UCC，如果是，则整个搜索空间被剪枝
        validate(root);
        logger.info("root: {}", root);
        if (root.isValid(config.getMaxError())) {
            minValidFD.add(root.getLhs());
            return;
        }

        // 初始化launchpads
        PriorityQueue<Node> launchpads = new PriorityQueue<>();
        for (int i = 0; i < dataSet.getColumnCount(); i++) {
            if (i == rhs) continue;
            BitSet newLhs = new BitSet(dataSet.getColumnCount());
            newLhs.set(i);
            Node child = getOrCreateNode(newLhs);
            estimate(child);
            launchpads.add(child);
        }

        // 遍历过程
        while (!launchpads.isEmpty()) {
            Node launchpad = launchpads.poll();
            logger.info("launchpad: {}", launchpad);
            boolean isValidPrune = checkValidPrune(launchpad.getLhs());
            boolean isInvalidPrune = checkInvalidPrune(launchpad.getLhs());
            Node peak;
            // 先检查launchpad是否被maxNonFD剪枝，如果已经被剪枝，则直接进入逃逸阶段
            if (!isInvalidPrune) {
                // 如果被validPrune，跳到trickleDown阶段
                if (!isValidPrune) {
                    // launchpad需要直接验，如果有效，直接添加到minValidFD和peaks
                    validate(launchpad);
                    if (launchpad.isValid(config.getMaxError())) {
                        peak = launchpad;
                    } else {
                        MinMaxPair<Node> minMaxPair = new MinMaxPair<>(null, launchpad);
                        // 上升，找到peak，并更新maxNonFD
                        minMaxPair = ascend(minMaxPair);

                        logger.info("上升到节点: {}", minMaxPair.getLeft());

                        maxNonFD.add(minMaxPair.getRight().getLhs());
                        logger.info("添加最大nonFD: {}", minMaxPair.getRight());
                        peak = minMaxPair.getLeft();
                    }
                } else {
                    peak = launchpad;
                }
                if (peak != null) {
                    peaks.add(peak.getLhs());
                    // 下降，遍历peak覆盖的所有未剪枝节点，寻找最小FD
                    if (isValidPrune || peak.isValid(config.getMaxError())) {
                        trickleDown(peak);
                    }
                }

            }
            // 逃逸
            BitSet launchpadLhs = launchpad.getLhs();
            List<BitSet> escapeList = escape(launchpadLhs);
            for (BitSet escapeLhs : escapeList) {
                Node escapeNode = getOrCreateNode(escapeLhs);
                estimate(escapeNode);
                launchpads.add(escapeNode);
            }
        }
    }

    private MinMaxPair<Node> ascend(MinMaxPair<Node> currentPair) {
        // 进入迭代之前要先保证max没有被剪枝
        if (currentPair.getLeft() == null) { // 此时还没有找到peak, 按照min向上走
            Node currentNode = currentPair.getRight();
            // 创建并估计所有未被minValidFD剪枝的子节点，获得估计最大和最小的子节点
            MinMaxPair<Node> minMaxPair = getMinMaxChildren(currentNode);
            if (minMaxPair.isEmpty()) { // 说明全部被剪枝或到顶
                return currentPair;
            }
            // 验证最小节点
            Node minNode = minMaxPair.getLeft();
            validate(minNode);
            if (minNode.isValid(config.getMaxError())) {
                // 最小节点有效，标记为peak，然后继续向上找maxNonFD
                // 这里是转折点，需要额外操作一次，节省一次计算maxNode. 逻辑和else的情况一致
                currentPair.setLeft(minNode);
                Node maxNode = minMaxPair.getRight();
                validate(maxNode);
                if (maxNode.isInvalid(config.getMaxError())) {
                    currentPair.setRight(maxNode);
                } else {
                    return currentPair;
                }
            } else {
                // 最小节点无效，说明爬的不够高，继续向上找
                currentPair.setRight(minNode);
            }
            return ascend(currentPair);
        } else { // 已经找到peak, 继续向上找maxNonFD
            // 这个maxNonFD是估计的，子节点还是有可能是nonFD
            // 因为精确计算开销大，没有必要，但可以保证这是"尽可能高的"精确的NonFD
            Node maxNode = getMaxChildren(currentPair.getRight());
            if (maxNode == null) {
                return currentPair;
            }
            validate(maxNode);
            if (maxNode.isInvalid(config.getMaxError())) {
                currentPair.setRight(maxNode);
                return ascend(currentPair);
            } else {
                return currentPair;
            }
        }
    }

    /*
    private algorithm.Node ascendSimple(algorithm.Node currentNode) {
        MinMaxPair<algorithm.Node> currentPair = new MinMaxPair<>(null, currentNode);
        // 创建并估计所有未被minValidFD剪枝的子节点，获得估计最大和最小的子节点
        MinMaxPair<algorithm.Node> minMaxPair = getMinMaxChildren(currentNode);
        if (minMaxPair.isEmpty()) { // 说明全部被剪枝或到顶
            return currentPair;
        }
        // 验证最小节点
        algorithm.Node minNode = minMaxPair.getLeft();
        validate(minNode);
        if (minNode.isValid(config.getMaxError())) {
            // 最小节点有效，标记为peak，然后继续向上找maxNonFD
            // 这里是转折点，需要额外操作一次，节省一次计算maxNode. 逻辑和else的情况一致
            currentPair.setLeft(minNode);
            algorithm.Node maxNode = minMaxPair.getRight();
            validate(maxNode);
            if (maxNode.isInvalid(config.getMaxError())) {
                currentPair.setRight(maxNode);
            } else {
                return currentPair;
            }
        } else {
            // 最小节点无效，说明爬的不够高，继续向上找
            currentPair.setRight(minNode);
        }
        return ascendSimple(currentNode);
    }
    */

    private void trickleDown(Node peak) {
        // 需要覆盖到peak下方的所有节点，用优先队列存储所有祖先节点
        // 1. 获取所有未被剪枝的父节点，加入优先队列
        // 2. 取出error最小的节点递归trickledown，直到找到最小FD（所有父节点都是剪枝或验证无效的）
        // 3. 将最小FD加入结果集
        // 4. 继续遍历优先队列中的其他节点
        PriorityQueue<Node> queue = new PriorityQueue<>(
                // 先按node.getLevel()排序,再按node.getError()排序
                // 这样，优先队列会先访问更深的节点中error更小的节点，从而优先剪枝
                Comparator.comparingInt(Node::getLevel)
                        .thenComparingDouble(Node::getError)
        );
        queue.add(peak);
        Set<BitSet> visited = new HashSet<>(); // 用于跟踪已处理过的节点 (LHS)

        while (!queue.isEmpty()) {
            Node currentNode = queue.peek(); // 查看队列顶部的节点，但不移除

            if (currentNode == root) { // 根节点是特殊情况，不参与FD发现
                queue.poll();
                continue;
            }

            boolean isCurrentlyPruned = checkValidPrune(currentNode.getLhs());

            if (visited.contains(currentNode.getLhs())) {
                // 如果节点之前被处理过 (例如，被验证为valid并保留在队列中，或被剪枝并处理了父节点)
                queue.poll(); // 从队列中移除
                if (!isCurrentlyPruned) {
                    // 如果它之前是valid的（意味着它在被标记visited时没有被poll），并且现在没有被一个更小的、新发现的FD剪枝，
                    // 那么它就是一个最小FD。
                    logger.info("找到最小FD: {}", currentNode);
                    minValidFD.add(currentNode.getLhs());
                }
                // 父节点在它首次被标记为visited时（如果是valid）或被剪枝时已经加入队列探索，
                // 所以这里可以直接continue。
                continue;
            }

            // 节点是首次被处理 (即 !visited.contains(currentNode.getLhs()) )
            // 首先标记为已访问，以避免在当前trickleDown调用中重复验证或基本处理。
            visited.add(currentNode.getLhs());

            if (isCurrentlyPruned) {
                // 节点被minValidFD剪枝。
                queue.poll(); // 从队列中移除，因为它不能成为最小FD。
                // 虽然此节点被剪枝，但其父节点仍需探索。
                // 将在下面的公共代码块中添加父节点。
            } else {
                // 节点未被剪枝，进行验证。
                validate(currentNode);
                if (currentNode.isValid(config.getMaxError())) {
                    // 节点有效。
                    // 不从队列中 poll() 它。它将被保留，
                    // 当它再次成为队列头部时，会进入上面的 visited.contains() 分支进行最小性判断。
                    // 其父节点也需要被探索。
                } else {
                    // 节点无效。
                    queue.poll(); // 从队列中移除。
                    // 无效节点的父节点不应通过此特定无效路径进行探索。
                    // 如果其父节点本身是有效的，它们将通过从 peak 开始的其他路径被处理。
                    continue; // 跳过添加父节点的步骤。
                }
            }

            // 对于未因"无效"或"已充分处理的visited"而continue的节点（即有效的、或被剪枝的节点），
            // 将其所有父节点加入队列进行探索。
            List<BitSet> parentsLhs = getAllParents(currentNode.getLhs());
            for (BitSet parentLhs : parentsLhs) {
                Node parentNode = getOrCreateNode(parentLhs);
                if (!visited.contains(parentLhs)) {
                    estimate(parentNode);
                    queue.add(parentNode);
                }
            }
        }
    }

    private void trickleDownSimple(Node peak) {
        // 简单版：全部遍历全部验证
        List<BitSet> parentsLhs = getAllParents(peak.getLhs());
        boolean hasValidParent = false;
        for (BitSet parentLhs : parentsLhs) {
            Node parentNode = getOrCreateNode(parentLhs);
            validate(parentNode);
            if (parentNode.isValid(config.getMaxError())) {
                hasValidParent = true;
                trickleDownSimple(parentNode);
            } else {
                maxNonFD.add(parentNode.getLhs());
            }
        }
        if (!hasValidParent) {
            minValidFD.add(peak.getLhs());
        }
    }

    private List<BitSet> escape(BitSet launchpadLhs) {
        List<BitSet> result = new ArrayList<>();
        for (BitSet peak : peaks) {
            if (BitSetUtils.isSubSet(launchpadLhs, peak)) {
                BitSet newLhs = (BitSet) peak.clone();
                newLhs.set(rhs);
                newLhs.flip(0, dataSet.getColumnCount());
                result.add(newLhs);
            }
        }
        HittingSet hittingSet = calculateHittingSet(result, dataSet.getColumnCount());
        result = hittingSet.getAllMinimalHittingSets();
        // 把result中所有BitSet与launchpad union后返回
        for (BitSet set : result) {
            set.or(launchpadLhs);
        }
        return result;
    }

    private boolean checkPruned(BitSet lhs) {
        return checkValidPrune(lhs) || checkInvalidPrune(lhs);
    }

    private boolean checkValidPrune(BitSet lhs) {
        return minValidFD.containsSubSetOf(bitSetToList(lhs));
    }

    private boolean checkInvalidPrune(BitSet lhs) {
        return maxNonFD.containsSuperSetOf(bitSetToList(lhs));
    }

    private Node getOrCreateNode(BitSet lhs) {
        return nodeMap.computeIfAbsent(lhs, k -> new Node(lhs, rhs));
    }

    private MinMaxPair<Node> getMinMaxChildren(Node node) {
        MinMaxPair<Node> minMaxPair = new MinMaxPair<>(null, null);
        for (int i = 0; i < dataSet.getColumnCount(); i++) {
            if (!node.getLhs().get(i) && i != rhs) {
                BitSet newLhs = (BitSet) node.getLhs().clone();
                newLhs.set(i);
                if (!checkValidPrune(newLhs)) {
                    // 假设node已经经过剪枝检验，如果有剪枝一定是被minValidFD剪，因为如果被maxNonFD剪，node本身就被剪了
                    Node child = getOrCreateNode(newLhs);
                    estimate(child);
                    minMaxPair.update(child);
                }
            }
        }
        return minMaxPair;
    }

    private Node getMaxChildren(Node node) {
        Node maxNode = null;
        for (int i = 0; i < dataSet.getColumnCount(); i++) {
            if (!node.getLhs().get(i) && i != rhs) {
                BitSet newLhs = (BitSet) node.getLhs().clone();
                newLhs.set(i);
                if (!checkValidPrune(newLhs)) {
                    Node child = getOrCreateNode(newLhs);
                    estimate(child);
                    if (maxNode == null) {
                        maxNode = child;
                    } else if (child.getError() > maxNode.getError()) {
                        maxNode = child;
                    }
                }
            }
        }
        return maxNode;
    }

    private List<BitSet> getAllParents(BitSet lhs) {
        List<BitSet> result = new ArrayList<>();
        if (lhs.isEmpty()) { // 空集的父节点就是它自己（或没有严格意义的父节点）
            return result; // 返回空列表，避免进一步处理
        }
        for (int i = lhs.nextSetBit(0); i >= 0; i = lhs.nextSetBit(i + 1)) {
            BitSet newLhs = (BitSet) lhs.clone();
            newLhs.clear(i);
            result.add(newLhs);
        }
        if (result.get(0).isEmpty()) {
            return new ArrayList<>();
        }
        return result;
    }

    /*
    private List<BitSet> getNotPrunedParents(BitSet lhs) {
        List<BitSet> result = new ArrayList<>();
        // 遍历minValidFD，找出lhs的所有子集并相交
        BitSet overlap = (BitSet) lhs.clone();
        for (BitSet subLhs : minValidFD) {
            if (isSubSet(subLhs, lhs)) {
                overlap.and(subLhs);
            }
        }
        // 从lhs中依次clear掉overlap中设置为1的bit
        for (int i = overlap.nextSetBit(0); i >= 0; i = overlap.nextSetBit(i + 1)) {
            BitSet newLhs = (BitSet) lhs.clone();
            newLhs.clear(i);
            result.add(newLhs);
        }
        return result;
    }
    */

    /*
    private void updateMaxNonFD(BitSet lhs) {
        for (BitSet nonFDLhs : maxNonFD) {
            if (isSubSet(lhs, nonFDLhs)) {
                return;
            }
            if (isSubSet(nonFDLhs, lhs)) {
                maxNonFD.remove(nonFDLhs);
            }
        }
        maxNonFD.add(lhs);
    }
    */

    private void validate(Node node) {
        if (node.isValidated()) return;
        node.setError(config.getMeasure().calculateError(node.getLhs(), rhs, dataSet, cache));
        node.setValidated();
    }

    private void estimate(Node node) {
        if (node.isEstimated()) return;
        node.setError(config.getMeasure().estimateError(node.getLhs(), rhs, dataSet, cache, config.getSamplingStrategy()));
    }

    public List<Node> getValidatedNodes() {
        List<Node> result = new ArrayList<>();
        List<BitSet> minLhs = minValidFD.toList(dataSet.getColumnCount());
        for (BitSet lhs: minLhs) {
            Node node = getOrCreateNode(lhs);
            if (!node.isValidated()) {
                validate(node); // 确保error是最新的精确值
                throw new RuntimeException(node + " should be validated");
            }
            result.add(node);
        }
        result.sort(Comparator.comparingDouble(Node::getError));
        return result;
    }

    public List<FunctionalDependency> getValidatedFDs() {
        List<FunctionalDependency> result = new ArrayList<>();
        List<BitSet> minLhs = minValidFD.toList(dataSet.getColumnCount());
        for (BitSet lhs : minLhs) {
            Node node = getOrCreateNode(lhs);
            if (!node.isValidated()) {
                validate(node); // 确保 FD 的 error 是准确的
                throw new RuntimeException(node + " should be validated");
            }
            result.add(new FunctionalDependency(bitSetToSet(lhs), rhs, node.getError()));
        }
        // 可以根据error排序
        result.sort(Comparator.comparingDouble(FunctionalDependency::getError));
        return result;
    }

    // 测试用方法
    protected Node getRoot(){
        return root;
    }

    protected Map<BitSet, Node> getNodeMap(){
        return nodeMap;
    }

    /* 复杂度太高 已废弃
    private void buildTree() {
        Set<Integer> attributes = new HashSet<>();
        for (int i = 0; i < dataSet.getColumnCount(); i++) {
            if (i != rhs) {
                attributes.add(i);
            }
        }
        buildTreeRecursive(root, attributes);
    }

    private void buildTreeRecursive(algorithm.Node parent, Set<Integer> attributes) {
        for (Integer attr : attributes) {
            if (parent.getLhs().get(attr)) {
                continue;
            }
            BitSet newLhs = (BitSet) parent.getLhs().clone();
            newLhs.set(attr);
            algorithm.Node child = nodeMap.computeIfAbsent(newLhs, l -> new algorithm.Node(l, rhs));
            parent.addChild(child);
            Set<Integer> remainingAttributes = new HashSet<>(attributes);
            remainingAttributes.remove(attr);
            buildTreeRecursive(child, remainingAttributes);
        }
    }
    */

}
