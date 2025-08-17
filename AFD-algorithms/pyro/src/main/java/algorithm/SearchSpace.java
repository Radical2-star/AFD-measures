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
import utils.LongBitSetUtils;
import utils.FunctionTimer;
import utils.HittingSet;
import utils.MaxFDTrie;
import utils.MinFDTrie;
import utils.MinMaxPair;

import java.lang.ref.SoftReference;
import java.util.*;

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

    // 智能节点内存管理
    private final Map<Long, Node> activeNodes;           // 强引用保持活跃节点
    private final Map<Long, SoftReference<Node>> cachedNodes;  // 软引用管理可能重用的节点
    private final LinkedHashMap<Long, Long> accessOrder;      // LRU维护最近访问顺序
    
    // 内存管理配置
    private static final int MAX_ACTIVE_NODES = 10000;     // 活跃节点上限
    private static final int CLEANUP_BATCH_SIZE = 1000;    // 批量清理大小
    private static final int CLEANUP_THRESHOLD = 8000;     // 开始清理的阈值

    private final MinFDTrie minValidFD;
    private final MaxFDTrie maxNonFD;
    private final Set<Long> peaks; // 优化为long类型

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

        // 初始化智能节点管理
        this.activeNodes = new HashMap<>();
        this.cachedNodes = new HashMap<>(); 
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true); // LRU访问顺序
        
        this.minValidFD = new MinFDTrie();
        this.maxNonFD = new MaxFDTrie();
        this.peaks = new HashSet<>();
        this.root = getOrCreateNode(0L); // 空集用0L表示
        
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
            NeymanSampling neymanSampling = (NeymanSampling) samplingStrategy;
            
            int successCount = 0;
            int errorCount = 0;
            
            for (int col = 0; col < dataSet.getColumnCount(); col++) {
                if (col == rhs) continue; // 跳过RHS列
                
                try {
                    // 获取单列PLI
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
            long newLhs = 1L << i;
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

                        maxNonFD.add(minMaxPair.getRight().getLhsBitSet());
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
            long launchpadLhs = launchpad.getLhs();
            List<Long> escapeList = escape(launchpadLhs);
            for (long escapeLhs : escapeList) {
                Node escapeNode = getOrCreateNode(escapeLhs);
                estimate(escapeNode);
                launchpads.add(escapeNode);
            }
        }
        
        // 搜索完成，输出内存使用统计
        if (verbose) {
            logger.info("搜索完成，{}", getMemoryStats());
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
        // 2. 取出error最小的节点递归trickleDown，直到找到最小FD（所有父节点都是剪枝或验证无效的）
        // 3. 将最小FD加入结果集
        // 4. 继续遍历优先队列中的其他节点
        PriorityQueue<Node> queue = new PriorityQueue<>(
                // 先按node.getLevel()排序,再按node.getError()排序
                // 这样，优先队列会先访问更深的节点中error更小的节点，从而优先剪枝
                Comparator.comparingInt(Node::getLevel)
                        .thenComparingDouble(Node::getError)
        );
        queue.add(peak);
        Set<Long> visited = new HashSet<>(); // 用于跟踪已处理过的节点 (LHS)

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
                    minValidFD.add(currentNode.getLhsBitSet());
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
            List<Long> parentsLhs = getAllParents(currentNode.getLhs());
            for (long parentLhs : parentsLhs) {
                Node parentNode = getOrCreateNode(parentLhs);
                if (!visited.contains(parentLhs)) { // 直接使用long进行visited检查
                    estimate(parentNode);
                    queue.add(parentNode);
                }
            }
        }
    }

    private List<Long> escape(long launchpadLhs) {
        List<Long> result = new ArrayList<>();

        for (long peak : peaks) {
            if (LongBitSetUtils.isSubset(launchpadLhs, peak)) {
                long newLhs = LongBitSetUtils.setBit(peak, rhs);
                newLhs = LongBitSetUtils.complement(newLhs, dataSet.getColumnCount());
                result.add(newLhs);
            }
        }

        // 优化：如果result为空，直接返回
        if (result.isEmpty()) {
            return result;
        }

        // 使用long版本的HittingSet计算（性能优化）
        HittingSet hittingSet = BitSetUtils.calculateHittingSet(result, dataSet.getColumnCount());
        List<Long> minimalSets = hittingSet.getAllMinimalHittingSetsLong();

        // 直接与launchpad合并，无需转换
        List<Long> finalResult = new ArrayList<>();
        for (long set : minimalSets) {
            finalResult.add(LongBitSetUtils.union(set, launchpadLhs));
        }

        return finalResult;
    }

    // 兼容性方法：从BitSet调用
    @Deprecated
    private List<BitSet> escape(BitSet launchpadLhs) {
        long launchpadLong = BitSetUtils.bitSetToLong(launchpadLhs, dataSet.getColumnCount());
        List<Long> longResult = escape(launchpadLong);

        List<BitSet> result = new ArrayList<>();
        for (long bits : longResult) {
            BitSet bitSet = new BitSet();
            for (int i = 0; i < dataSet.getColumnCount(); i++) {
                if (LongBitSetUtils.testBit(bits, i)) {
                    bitSet.set(i);
                }
            }
            result.add(bitSet);
        }
        return result;
    }

    private boolean checkValidPrune(long lhs) {
        List<Integer> lhsList = LongBitSetUtils.longToList(lhs);
        return minValidFD.containsSubSetOf(lhsList);
    }

    private boolean checkInvalidPrune(long lhs) {
        List<Integer> lhsList = LongBitSetUtils.longToList(lhs);
        return maxNonFD.containsSuperSetOf(lhsList);
    }

    @Deprecated
    private boolean checkValidPrune(BitSet lhs) {
        return checkValidPrune(BitSetUtils.bitSetToLong(lhs, dataSet.getColumnCount()));
    }

    @Deprecated
    private boolean checkInvalidPrune(BitSet lhs) {
        return checkInvalidPrune(BitSetUtils.bitSetToLong(lhs, dataSet.getColumnCount()));
    }

    Node getOrCreateNode(long lhsLong) {
        // 1. 先从活跃节点中查找
        Node node = activeNodes.get(lhsLong);
        if (node != null) {
            updateAccessTime(lhsLong);
            return node;
        }

        // 2. 从软引用缓存中查找
        SoftReference<Node> ref = cachedNodes.get(lhsLong);
        if (ref != null) {
            node = ref.get();
            if (node != null) {
                // 重新激活节点
                activeNodes.put(lhsLong, node);
                cachedNodes.remove(lhsLong);
                updateAccessTime(lhsLong);
                if (verbose) logger.debug("节点从缓存中重新激活: key={}", lhsLong);
                return node;
            } else {
                // 软引用已被GC，清理
                cachedNodes.remove(lhsLong);
            }
        }

        // 3. 创建新节点
        node = new Node(lhsLong, rhs);
        activeNodes.put(lhsLong, node);
        updateAccessTime(lhsLong);

        // 4. 检查是否需要内存管理
        manageMemoryIfNeeded();

        return node;
    }

    @Deprecated
    Node getOrCreateNode(BitSet lhsBitSet) {
        long longKey = BitSetUtils.bitSetToLong(lhsBitSet, dataSet.getColumnCount());
        return getOrCreateNode(longKey);
    }
    
    /**
     * 更新节点的访问时间（用于LRU管理）
     */
    private void updateAccessTime(long nodeKey) {
        accessOrder.put(nodeKey, System.currentTimeMillis());
    }
    
    /**
     * 根据需要管理内存，将老节点降级为软引用
     * 优先回收未验证的节点，保护已验证节点的状态
     */
    private void manageMemoryIfNeeded() {
        if (activeNodes.size() <= CLEANUP_THRESHOLD) return;
        
        if (verbose) {
            logger.info("开始内存管理，当前活跃节点数: {}, 缓存节点数: {}", 
                       activeNodes.size(), cachedNodes.size());
        }
        
        // 分两阶段回收：先回收未验证节点，再回收已验证节点
        int cleaned = 0;
        
        // 阶段1：优先回收未验证的节点
        cleaned += cleanupNodesByValidationStatus(false);
        
        // 阶段2：如果还需要回收，再回收已验证节点
        if (cleaned < CLEANUP_BATCH_SIZE && activeNodes.size() > MAX_ACTIVE_NODES - CLEANUP_BATCH_SIZE) {
            cleaned += cleanupNodesByValidationStatus(true);
        }
        
        if (verbose && cleaned > 0) {
            logger.info("内存管理完成，降级节点数: {}, 当前活跃节点数: {}, 缓存节点数: {}", 
                       cleaned, activeNodes.size(), cachedNodes.size());
        }
    }
    
    /**
     * 按验证状态清理节点
     * @param includeValidated 是否包含已验证的节点
     * @return 清理的节点数量
     */
    private int cleanupNodesByValidationStatus(boolean includeValidated) {
        Iterator<Map.Entry<Long, Long>> it = accessOrder.entrySet().iterator();
        int cleaned = 0;
        
        while (it.hasNext() && cleaned < CLEANUP_BATCH_SIZE && activeNodes.size() > MAX_ACTIVE_NODES - CLEANUP_BATCH_SIZE) {
            Map.Entry<Long, Long> entry = it.next();
            Long key = entry.getKey();
            
            Node node = activeNodes.get(key);
            if (node != null) {
                // 检查节点的验证状态
                boolean shouldCleanup = includeValidated || !node.isValidated();
                
                if (shouldCleanup) {
                    activeNodes.remove(key);
                    cachedNodes.put(key, new SoftReference<>(node));
                    cleaned++;
                    it.remove();
                } else {
                    // 跳过已验证节点，但更新其访问时间以避免被过早回收
                    updateAccessTime(key);
                }
            } else {
                it.remove();
            }
        }
        
        return cleaned;
    }
    
    /**
     * 获取内存使用统计信息（用于调试）
     */
    public String getMemoryStats() {
        // 清理已被GC的软引用
        cachedNodes.entrySet().removeIf(entry -> entry.getValue().get() == null);
        
        return String.format("内存统计 - 活跃节点: %d, 缓存节点: %d, 访问记录: %d", 
                           activeNodes.size(), cachedNodes.size(), accessOrder.size());
    }

    private MinMaxPair<Node> getMinMaxChildren(Node node) {
        MinMaxPair<Node> minMaxPair = new MinMaxPair<>(null, null);
        long parentLhs = node.getLhs(); // 直接使用long

        for (int i = 0; i < dataSet.getColumnCount(); i++) {
            if (!LongBitSetUtils.testBit(parentLhs, i) && i != rhs) {
                // 使用高效的位操作创建子节点
                long childLhs = LongBitSetUtils.setBit(parentLhs, i);

                // 高效的剪枝检查
                if (!checkValidPrune(childLhs)) {
                    Node child = getOrCreateNode(childLhs);
                    estimate(child);
                    minMaxPair.update(child);
                }
            }
        }
        return minMaxPair;
    }

    private Node getMaxChildren(Node node) {
        Node maxNode = null;
        long parentLhs = node.getLhs(); // 直接使用long

        for (int i = 0; i < dataSet.getColumnCount(); i++) {
            if (!LongBitSetUtils.testBit(parentLhs, i) && i != rhs) {
                // 使用高效的位操作创建子节点
                long childLhs = LongBitSetUtils.setBit(parentLhs, i);

                // 高效的剪枝检查
                if (!checkValidPrune(childLhs)) {
                    Node child = getOrCreateNode(childLhs);
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

    private List<Long> getAllParents(long lhs) {
        // 使用LongBitSetUtils的高效缓存
        Set<Long> parentsSet = LongBitSetUtils.getAllParents(lhs);
        return new ArrayList<>(parentsSet);
    }

    @Deprecated
    private List<BitSet> getAllParents(BitSet lhs) {
        long lhsLong = BitSetUtils.bitSetToLong(lhs, dataSet.getColumnCount());
        List<Long> longParents = getAllParents(lhsLong);

        List<BitSet> result = new ArrayList<>(longParents.size());
        for (long parent : longParents) {
            BitSet parentBitSet = new BitSet();
            for (int i = 0; i < dataSet.getColumnCount(); i++) {
                if ((parent & (1L << i)) != 0) {
                    parentBitSet.set(i);
                }
            }
            result.add(parentBitSet);
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
        // 直接使用long版本，避免BitSet转换开销
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

