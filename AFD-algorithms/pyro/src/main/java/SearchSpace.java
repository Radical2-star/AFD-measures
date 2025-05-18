import model.DataSet;
import model.FunctionalDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pli.PLICache;
import utils.BitSetUtils;
import utils.FunctionTimer;
import utils.HittingSet;
import utils.MaxFDTrie;
import utils.MinFDTrie;
import utils.MinMaxPair;
import utils.Trie;

import java.util.*;
import java.util.stream.Collectors;

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
    private final FunctionTimer timer;
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
        this.timer = FunctionTimer.getInstance();
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
            if (checkValidPrune(launchpad.getLhs())) {
                continue;
            }
            // 先检查launchpad是否被maxNonFD剪枝，如果已经被剪枝，则直接进入逃逸阶段
            if (!checkInvalidPrune(launchpad.getLhs())) {
                // launchpad需要直接验，如果有效，直接添加到minValidFD和peaks
                validate(launchpad);
                Node peak = null;
                if (launchpad.isValid(config.getMaxError())) {
                    peak = launchpad;
                } else {
                    MinMaxPair<Node> minMaxPair = new MinMaxPair<>(null, launchpad);
                    // 上升，找到peak，并更新maxNonFD
                    timer.start("ascend");
                    minMaxPair = ascend(minMaxPair);
                    timer.end("ascend");

                    logger.info("上升到节点: {}", minMaxPair.getLeft());

                    maxNonFD.add(minMaxPair.getRight().getLhs());
                    logger.info("添加最大nonFD: {}", minMaxPair.getRight());
                    peak = minMaxPair.getLeft();
                }
                if (peak != null) peaks.add(peak.getLhs());
                // 下降，遍历peak覆盖的所有未剪枝节点，寻找最小FD
                if (peak != null && peak.isValid(config.getMaxError())) {
                    timer.start("trickleDown");
                    trickleDown(peak);
                    timer.end("trickleDown");
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
        timer.printResults();
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
            // 因为精确计算开销大，没有必要，但可以保证这是“尽可能高的”精确的NonFD
            Node maxNode = getMaxChildren(currentPair.getRight());
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
    private Node ascendSimple(Node currentNode) {
        MinMaxPair<Node> currentPair = new MinMaxPair<>(null, currentNode);
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
        Set<BitSet> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Node minNode = queue.peek();
            if (minNode == root) {
                queue.poll();
                continue;
            }
            // 先检查minNode本身是否被minValidFD剪枝
            boolean isMinPruned = checkValidPrune(minNode.getLhs());
            // 最小性判断：如果子节点都验证无效，则为最小
            // 为此，验证通过时，不从队列中移除，并标记为visited
            // 如果已经visited，说明子节点都被验证过一遍了，则为最小有效FD
            if (isMinPruned) {
                visited.add(minNode.getLhs());
            }
            if (visited.contains(minNode.getLhs())) {
                queue.poll();
                if (!isMinPruned) {
                    logger.info("找到最小FD: {}", minNode);
                    minValidFD.add(minNode.getLhs());
                }
                continue;
            }
            if (!isMinPruned) {
                validate(minNode);
                if (minNode.isValid(config.getMaxError())) {
                    visited.add(minNode.getLhs());
                } else {
                    queue.poll();
                    continue;
                }
            }
            // 即使被minValidFD剪枝，也需要把父节点加入队列，防止遗漏
            // 如果验证无效则不需要加入队列
            List<BitSet> parentsLhs = getAllParents(minNode.getLhs());
            for (BitSet parentLhs : parentsLhs) {
                Node parentNode = getOrCreateNode(parentLhs);
                estimate(parentNode);
                queue.add(parentNode);
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
            result.add(node);
        }
        result.sort(Comparator.comparingDouble(Node::getError));
        return result;
    }

    public List<FunctionalDependency> getValidatedFDs() {
        List<FunctionalDependency> result = new ArrayList<>();
        List<BitSet> minLhs = minValidFD.toList(dataSet.getColumnCount());
        for (BitSet lhs : minLhs) {
            result.add(new FunctionalDependency(bitSetToSet(lhs), rhs));
        }
        return result;
    }

    // 测试用方法
    Node getRoot(){
        return root;
    }

    Map<BitSet, Node> getNodeMap(){
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

    private void buildTreeRecursive(Node parent, Set<Integer> attributes) {
        for (Integer attr : attributes) {
            if (parent.getLhs().get(attr)) {
                continue;
            }
            BitSet newLhs = (BitSet) parent.getLhs().clone();
            newLhs.set(attr);
            Node child = nodeMap.computeIfAbsent(newLhs, l -> new Node(l, rhs));
            parent.addChild(child);
            Set<Integer> remainingAttributes = new HashSet<>(attributes);
            remainingAttributes.remove(attr);
            buildTreeRecursive(child, remainingAttributes);
        }
    }
    */

}
