package algorithm;

import measure.ErrorMeasure;
import model.DataSet;
import model.FunctionalDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pli.PLI;
import pli.PLICache;
import sampling.NeymanSampling;
import sampling.SamplingStrategy;
import utils.BitSetUtils;
import utils.FunctionTimer;
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
    private final ErrorMeasure measure;
    private final SamplingStrategy samplingStrategy;
    private final double maxError;
    private final double sampleParam;
    private final boolean verbose;
    private final PLICache cache;
    private final Node root;

    // 存储节点的辅助结构
    private final Map<Long, Integer> lhsToIdMap;
    private final List<Node> nodeList;
    private int nextNodeId;

    private final MinFDTrie minValidFD;
    private final MaxFDTrie maxNonFD;
    private final Set<BitSet> peaks; // TODO: 优化为Trie

    private final FunctionTimer timer = FunctionTimer.getInstance();

    // Neyman采样优化：缓存单列PLI的方差信息
    // 外层List长度为列数，RHS位置为null
    // 内层List长度为对应单列PLI的簇数，存储每个簇的方差
    private List<List<Double>> columnVarianceCache;

    private static final Logger logger = LoggerFactory.getLogger(SearchSpace.class);

    public static int getValidateCount() { // 测试用，统计验证次数
        return validateCount;
    }

    public static void resetValidateCount() {
        validateCount = 0;
    }

    private static int validateCount = 0;

    public SearchSpace(int rhs, DataSet dataSet, PLICache cache, ErrorMeasure measure, SamplingStrategy sampling, double maxError, double sampleParam, boolean verbose) {
        this.rhs = rhs;
        this.dataSet = dataSet;
        this.cache = cache;
        this.measure = measure;
        this.samplingStrategy = sampling;
        this.maxError = maxError;
        this.sampleParam = sampleParam;
        this.verbose = verbose;

        if (dataSet.getColumnCount() > 63) {
            logger.warn("数据集的列数 ({}) 大于63，使用Long作为键的优化不适用。", dataSet.getColumnCount());
        }

        this.lhsToIdMap = new HashMap<>();
        this.nodeList = new ArrayList<>();
        this.nextNodeId = 0;

        this.minValidFD = new MinFDTrie();
        this.maxNonFD = new MaxFDTrie();
        this.peaks = new HashSet<>();
        this.root = getOrCreateNode(new BitSet(dataSet.getColumnCount()));
        
        // 初始化Neyman采样缓存
        this.columnVarianceCache = new ArrayList<>(Collections.nCopies(dataSet.getColumnCount(), null));
        
        // 如果使用NeymanSampling，进行预计算
        if (samplingStrategy instanceof NeymanSampling) {
            timer.start("一阶段预计算方差");
            precomputeVariances();
            // 设置缓存访问器
            ((NeymanSampling) samplingStrategy).setVarianceCacheAccessor(this::getColumnVarianceCache);
            timer.end("一阶段预计算方差");
        }
    }

    /**
     * 预计算所有单列PLI的方差信息（仅在使用NeymanSampling时调用）
     */
    private void precomputeVariances() {
        if (verbose) logger.info("开始预计算单列PLI方差信息，rhs = {}", rhs);
        
        try {
            // 强制转型为NeymanSampling
            NeymanSampling neymanSampling = (NeymanSampling) samplingStrategy;
            
            int successCount = 0;
            int errorCount = 0;
            
            for (int col = 0; col < dataSet.getColumnCount(); col++) {
                if (col == rhs) continue; // 跳过RHS列
                
                try {
                    // 获取单列PLI
                    BitSet singleColumn = new BitSet();
                    singleColumn.set(col);
                    List<Integer> key = new ArrayList<>();
                    key.add(col);
                    PLI pli = cache.get(key);
                    
                    if (pli == null) {
                        if (verbose) logger.warn("列 {} 的PLI不存在，跳过", col);
                        continue;
                    }
                    
                    // 调用NeymanSampling的预计算方法
                    List<Double> clusterVariances = neymanSampling.precomputeColumnVariances(pli, dataSet, rhs);
                    columnVarianceCache.set(col, clusterVariances);
                    
                    successCount++;
                    if (verbose) logger.debug("列 {} 预计算完成，共 {} 个簇", col, clusterVariances.size());
                    
                } catch (Exception e) {
                    errorCount++;
                    logger.error("列 {} 预计算失败: {}", col, e.getMessage(), e);
                    // 设置为空列表，表示该列预计算失败
                    columnVarianceCache.set(col, new ArrayList<>());
                }
            }
            
            if (verbose) logger.info("预计算完成，成功: {}, 失败: {}", successCount, errorCount);
            
        } catch (Exception e) {
            logger.error("预计算过程中发生严重错误: {}", e.getMessage(), e);
            // 清空缓存，回退到原始方法
            columnVarianceCache = new ArrayList<>(Collections.nCopies(dataSet.getColumnCount(), null));
        }
    }
    
    /**
     * 获取列的缓存方差信息（供NeymanSampling调用）
     * @param col 列索引
     * @return 该列的方差信息，如果不存在则返回null
     */
    public List<Double> getColumnVarianceCache(int col) {
        if (col < 0 || col >= columnVarianceCache.size()) {
            return null;
        }
        return columnVarianceCache.get(col);
    }

    public void explore() {
        if (verbose) logger.info("开始搜索, rhs = {}", rhs);
        if (verbose) logger.info("root: {}", root);

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
            if (verbose) logger.info("launchpad: {}", launchpad);
            boolean isValidPrune = checkValidPrune(launchpad.getLhs());
            boolean isInvalidPrune = checkInvalidPrune(launchpad.getLhs());
            Node peak;
            // 先检查launchpad是否被maxNonFD剪枝，如果已经被剪枝，则直接进入逃逸阶段
            if (!isInvalidPrune) {
                // 如果被validPrune，跳到trickleDown阶段
                if (!isValidPrune) {
                    // launchpad需要直接验，如果有效，直接添加到minValidFD和peaks
                    validate(launchpad);
                    if (launchpad.isValid(maxError)) {
                        peak = launchpad;
                    } else {
                        MinMaxPair<Node> minMaxPair = new MinMaxPair<>(null, launchpad);
                        // 上升，找到peak，并更新maxNonFD
                        minMaxPair = ascend(minMaxPair);

                        if (verbose) logger.info("上升到节点: {}", minMaxPair.getLeft());

                        maxNonFD.add(minMaxPair.getRight().getLhs());
                        if (verbose) logger.info("添加最大nonFD: {}", minMaxPair.getRight());
                        peak = minMaxPair.getLeft();
                    }
                } else {
                    peak = launchpad;
                }
                if (peak != null) {
                    peaks.add(peak.getLhs());
                    // 下降，遍历peak覆盖的所有未剪枝节点，寻找最小FD
                    if (isValidPrune || peak.isValid(maxError)) {
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
            if (minNode.isValid(maxError)) {
                // 最小节点有效，标记为peak，然后继续向上找maxNonFD
                // 这里是转折点，需要额外操作一次，节省一次计算maxNode. 逻辑和else的情况一致
                currentPair.setLeft(minNode);
                Node maxNode = minMaxPair.getRight();
                validate(maxNode);
                if (maxNode.isInvalid(maxError)) {
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
            if (maxNode.isInvalid(maxError)) {
                currentPair.setRight(maxNode);
                return ascend(currentPair);
            } else {
                return currentPair;
            }
        }
    }

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

            boolean isValidPruned = checkValidPrune(currentNode.getLhs());
            boolean isInvalidPruned = checkInvalidPrune(currentNode.getLhs());

            if (visited.contains(currentNode.getLhs())) {
                // 如果节点之前被处理过 (例如，被验证为valid并保留在队列中，或被剪枝并处理了父节点)
                queue.poll(); // 从队列中移除
                if (!isValidPruned) {
                    // 如果它之前是valid的（意味着它在被标记visited时没有被poll），并且现在没有被一个更小的、新发现的FD剪枝，
                    // 那么它就是一个最小FD。
                    if (verbose) logger.info("找到最小FD: {}", currentNode);
                    minValidFD.add(currentNode.getLhs());
                }
                // 父节点在它首次被标记为visited时（如果是valid）或被剪枝时已经加入队列探索，
                // 所以这里可以直接continue。
                continue;
            }

            // 节点是首次被处理 (即 !visited.contains(currentNode.getLhs()) )
            // 首先标记为已访问，以避免在当前trickleDown调用中重复验证或基本处理。
            visited.add(currentNode.getLhs());

            if (isValidPruned) {
                // 节点被minValidFD剪枝。
                queue.poll(); // 从队列中移除，因为它不能成为最小FD。
                // 虽然此节点被剪枝，但其父节点仍需探索。
                // 将在下面的公共代码块中添加父节点。
            } else if (isInvalidPruned) {
                // 节点被maxNonFD剪枝。
                queue.poll(); // 从队列中移除，因为它不能成为FD。
                continue; // 无需探索其父节点。
            } else {
                // 节点未被剪枝，进行验证。
                validate(currentNode);
                // 如果节点有效，不从队列中 poll() 它。它将被保留，
                // 当它再次成为队列头部时，会进入上面的 visited.contains() 分支进行最小性判断。
                // 其父节点也需要被探索。
                // 所以只需要处理节点无效的情况。
                if (!currentNode.isValid(maxError)) {
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

    private boolean checkValidPrune(BitSet lhs) {
        return minValidFD.containsSubSetOf(bitSetToList(lhs));
    }

    private boolean checkInvalidPrune(BitSet lhs) {
        return maxNonFD.containsSuperSetOf(bitSetToList(lhs));
    }

    private Node getOrCreateNode(BitSet lhsBitSet) {
        long longKey = BitSetUtils.bitSetToLong(lhsBitSet, dataSet.getColumnCount());
        
        // 使用 computeIfAbsent 来获取或创建ID
        int nodeId = lhsToIdMap.computeIfAbsent(longKey, k -> {
            // 注意：这里不能直接在 lambda 表达式中修改 nodeList，
            // 因为 computeIfAbsent 的 lambda 是为了生成 Map 的 value (即 Integer)。
            // 我们需要先获得 ID，然后再处理 nodeList。
            return nextNodeId++; // 返回新的ID
        });

        // 此时 nodeId 肯定存在，可能是旧的，也可能是刚通过 lambda 创建的新的
        if (nodeId >= nodeList.size()) { 
            // 这是一个新节点，nodeId 是刚分配的，nodeList中还没有这个索引
            // 需要确保 nodeList 扩展到能容纳这个新 ID
            // (理论上，如果 nextNodeId 和 nodeList.size() 同步增长，这里add的应该是正确的)
            Node newNode = new Node(lhsBitSet, rhs);
            nodeList.add(newNode); // 假设 add 后 newNode 的索引就是 nodeId
            return newNode;
        } else {
            // 节点已存在
            return nodeList.get(nodeId);
        }
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
        timer.start("validate");
        if (node.isValidated()) return;
        node.setError(measure.calculateError(node.getLhs(), rhs, dataSet, cache));
        node.setValidated();
        validateCount++;
        timer.end("validate");
    }

    private void estimate(Node node) {
        if (node.isEstimated()) return;
        if (samplingStrategy != null) {
            samplingStrategy.initialize(dataSet, cache, node.getLhs(), rhs, sampleParam);
        }
        node.setError(measure.estimateError(node.getLhs(), rhs, dataSet, cache, samplingStrategy));
    }

    public List<FunctionalDependency> getValidatedFDs() {
        List<FunctionalDependency> result = new ArrayList<>();
        List<BitSet> minLhs = minValidFD.toList(dataSet.getColumnCount());
        for (BitSet lhs : minLhs) {
            Node node = getOrCreateNode(lhs);
            result.add(node.toFD());
        }
        return result;
    }

    // 测试用方法
    // protected Node getRoot(){
    //     return root;
    // }
    //
    // protected List<Node> getNodeList() {
    //     return nodeList;
    // }

}

