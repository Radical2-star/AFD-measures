#!/bin/bash

# AFD-measures 128GB服务器优化实验启动脚本
# 此脚本已废弃，优化为executor.sh与run.sh

# 如果任何命令执行失败，立即退出脚本
set -e

# ============================================================
#                    命令行参数解析区域 
# ============================================================

# 显示帮助信息
show_help() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --sampling-mode <MODE>    设置采样模式 (NO_SAMPLING, RANDOM, FOCUSED, NEYMAN)"
    echo "  --run-tane <true|false>   是否运行TANE算法 (默认: true)"
    echo "  --dataset-name <NAME>     数据集名称，用于生成输出路径"
    echo "  --output-dir <PATH>       基础输出目录路径"
    echo "  --flamegraph-file <FILE>  火焰图输出文件路径"
    echo "  --skip-compile           跳过Maven编译步骤"
    echo "  --help, -h               显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 --sampling-mode RANDOM --dataset-name EQ-500K-12 --output-dir /tmp/results"
    echo ""
}

# 默认值设置
SKIP_COMPILE=false
FLAMEGRAPH_FILE=""
DATASET_NAME=""
OUTPUT_DIR=""

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --sampling-mode)
            SAMPLING_MODE_OVERRIDE="$2"
            shift 2
            ;;
        --run-tane)
            RUN_TANE_OVERRIDE="$2"
            shift 2
            ;;
        --dataset-name)
            DATASET_NAME="$2"
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --flamegraph-file)
            FLAMEGRAPH_FILE="$2"
            shift 2
            ;;
        --skip-compile)
            SKIP_COMPILE=true
            shift
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            echo "未知参数: $1"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
done

# ============================================================
#                    配置区域 
# ============================================================

# --- 实验基础配置 (默认值，可被命令行参数覆盖) ---
DATASET_PATH='data/int/EQ-500K-12.csv'     # 数据集路径 (默认)
RESULTS_FILE='result/result0807_test.csv'  # 结果文件路径 (默认)

# --- Java应用程序配置 ---
JAR_FILE="AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar"
MAIN_CLASS="experiment.ExperimentRunner"

# --- 实验参数配置 (默认值，可被命令行参数覆盖) ---
PLI_MODE='original'                        # PLI模式: original 或 dynamic
SAMPLING_MODE="ALL"                        # 采样模式: ALL, NO_SAMPLING, RANDOM, FOCUSED, NEYMAN
RUN_MODE="APPEND"                          # 运行模式: APPEND 或 OVERWRITE
RUN_TANE="true"                           # 是否运行TANE算法
ALGORITHM_TIMEOUT="120"                    # 算法超时时间（分钟）
MAX_ERROR="0.05"                          # 最大错误率
SAMPLE_PARAM="200"                        # 采样参数
RANDOM_SEED="114514"                      # 随机种子

# --- 应用命令行参数覆盖 ---
if [ -n "$SAMPLING_MODE_OVERRIDE" ]; then
    SAMPLING_MODE="$SAMPLING_MODE_OVERRIDE"
fi

if [ -n "$RUN_TANE_OVERRIDE" ]; then
    RUN_TANE="$RUN_TANE_OVERRIDE"
fi

# --- 动态输出路径配置 ---
if [ -n "$OUTPUT_DIR" ] && [ -n "$DATASET_NAME" ]; then
    # 创建结构化输出目录
    STRUCTURED_OUTPUT_DIR="${OUTPUT_DIR}/${DATASET_NAME}"
    mkdir -p "$STRUCTURED_OUTPUT_DIR"
    
    # 更新数据集路径（如果传递了dataset-name，假设数据集在data目录下）
    DATASET_PATH="data/int/${DATASET_NAME}.csv"
    
    # 更新结果文件路径
    RESULTS_FILE="${STRUCTURED_OUTPUT_DIR}/result_${DATASET_NAME}.csv"
    
    echo "[INFO] 使用结构化输出目录: $STRUCTURED_OUTPUT_DIR"
    echo "[INFO] 数据集路径: $DATASET_PATH"
    echo "[INFO] 结果文件: $RESULTS_FILE"
elif [ -n "$OUTPUT_DIR" ]; then
    # 仅指定了输出目录，使用默认数据集名称
    mkdir -p "$OUTPUT_DIR"
    RESULTS_FILE="${OUTPUT_DIR}/result_default.csv"
fi

# --- 内存配置 ---
HEAP_SIZE="64g"                           # 64GB堆内存（保守配置，确保可靠性）
NEW_SIZE="16g"                            # 16GB新生代（约25%）
METASPACE_SIZE="3g"                       # 3GB元空间
REGION_SIZE="32m"                         # 32MB G1区域大小（适合64GB堆）
PAUSE_TARGET="150"                        # 150ms GC暂停目标

# --- PLI缓存配置 ---
PLI_CACHE_MB=24576                        # 24GB PLI缓存（利用堆外内存）
PLI_CLEANUP_MB=19660                      # 20GB 清理阈值（80%）



# --- G1GC详细配置 - 针对64GB堆优化 ---
G1_NEW_SIZE_PERCENT=25                    # 新生代占比
G1_MAX_NEW_SIZE_PERCENT=35                # 最大新生代占比
G1_MIXED_GC_COUNT_TARGET=8                # 混合GC目标次数
G1_MIXED_GC_LIVE_THRESHOLD=85             # 混合GC存活阈值
G1_OLD_CSET_REGION_THRESHOLD=10           # 老年代收集集合区域阈值
G1_RESERVE_PERCENT=10                     # G1保留百分比（减少以节省内存）
G1_HEAP_WASTE_PERCENT=3                   # 堆浪费百分比（减少以提高效率）

# --- 大页配置 ---
ENABLE_LARGE_PAGES=true                   # 是否启用大页（可配置）
LARGE_PAGE_SIZE="2m"                      # 大页大小
DISABLE_LARGE_PAGES_ON_FAILURE=true       # 大页失败时自动禁用

# --- 性能分析配置 ---
ENABLE_PROFILING=true                     # 是否启用性能分析
ASYNC_PROFILER_PATH="/opt/async-profiler" # async-profiler安装路径
ASYNC_PROFILER_LIB="${ASYNC_PROFILER_PATH}/lib/libasyncProfiler.so" # Agent库路径

# --- 性能分析详细配置 (针对编排脚本优化) ---
ENABLE_CPU_PROFILING=true                 # 启用CPU分析 (生成HTML火焰图)
ENABLE_ALLOC_PROFILING=false              # 禁用内存分配分析 (不生成JFR文件)
CPU_PROFILING_EVENT="cpu"                 # CPU分析事件类型
ALLOC_PROFILING_EVENT="alloc"             # 内存分配分析事件类型
PROFILING_INTERVAL="10ms"                 # 采样间隔
PROFILING_FORMAT="html"                   # 默认输出格式

# --- 日志和调试配置 ---
GC_LOG_PREFIX="gc-128gb"                  # GC日志文件前缀
GC_LOG_FILE_COUNT=10                      # GC日志文件数量
GC_LOG_FILE_SIZE="100M"                   # GC日志文件大小
DEBUG_MODE="${DEBUG:-false}"              # 调试模式

# ============================================================
#                    脚本功能区域 
# ============================================================

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}



# 检查async profiler agent是否可用
check_async_profiler() {
    if [ "$ENABLE_PROFILING" = "true" ]; then
        if [ ! -d "$ASYNC_PROFILER_PATH" ]; then
            log_warn "Async Profiler路径不存在: $ASYNC_PROFILER_PATH"
            log_info "请下载并安装async-profiler v4.1"
            log_warn "禁用性能分析功能"
            ENABLE_PROFILING=false
            return 1
        fi

        # 检查agent库文件
        if [ ! -f "$ASYNC_PROFILER_LIB" ]; then
            log_error "Async Profiler Agent库不存在: $ASYNC_PROFILER_LIB"
            log_info "请检查async-profiler v4.1是否正确安装"
            log_info "预期文件位置: $ASYNC_PROFILER_LIB"
            log_warn "禁用性能分析功能"
            ENABLE_PROFILING=false
            return 1
        fi

        # 检查库文件权限
        if [ ! -r "$ASYNC_PROFILER_LIB" ]; then
            log_error "Async Profiler Agent库无读取权限: $ASYNC_PROFILER_LIB"
            log_info "修复权限命令: sudo chmod 644 $ASYNC_PROFILER_LIB"
            log_warn "禁用性能分析功能"
            ENABLE_PROFILING=false
            return 1
        fi

        # 检查可执行文件
        PROFILER_EXECUTABLE="$ASYNC_PROFILER_PATH/bin/asprof"
        if [ ! -f "$PROFILER_EXECUTABLE" ]; then
            log_warn "Async Profiler可执行文件不存在: $PROFILER_EXECUTABLE"
            log_warn "这可能影响某些功能，但Agent模式仍可工作"
        fi

        log_success "Async Profiler v4.1 Agent模式检查通过"
        log_info "Agent库路径: $ASYNC_PROFILER_LIB"
        return 0
    fi
    return 0
}

# --- 1. 检查和显示内存配置 ---
log_info "当前内存配置:"
log_info "  堆内存: ${HEAP_SIZE}"
log_info "  PLI缓存: $(echo "scale=1; ${PLI_CACHE_MB}/1024" | bc)GB"

# --- 2. 检查async profiler ---
check_async_profiler

# --- 3. 项目编译和实验配置 ---
if [ "$SKIP_COMPILE" = "true" ]; then
    log_info "跳过Maven编译步骤 (--skip-compile)"
else
    log_info "正在使用Maven编译项目..."
    mvn clean package -DskipTests
    log_success "项目编译完成!"
fi

# 检查JAR文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    if [ "$SKIP_COMPILE" = "true" ]; then
        log_error "JAR文件不存在，但跳过了编译: $JAR_FILE"
        log_error "请先运行不带 --skip-compile 参数的编译，或手动编译项目"
        exit 1
    else
        log_warn "未找到JAR文件: $JAR_FILE"
        log_info "尝试使用优化启动脚本构建..."
        cd AFD-algorithms/experiment
        if [ -f "run-optimized-experiment.sh" ]; then
            chmod +x run-optimized-experiment.sh
            ./run-optimized-experiment.sh --dry-run
            cd ../..
        else
            echo "错误: 无法找到实验JAR文件"
            exit 1
        fi
    fi
fi

# --- 4. 准备输出目录 ---
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PROFILING_OUTPUT_DIR="$(pwd)/profiling_output"

# 创建profiling输出目录（包括GC日志）
log_info "创建输出目录: $PROFILING_OUTPUT_DIR"
mkdir -p "$PROFILING_OUTPUT_DIR"

if [ ! -w "$PROFILING_OUTPUT_DIR" ]; then
    log_error "输出目录无写入权限: $PROFILING_OUTPUT_DIR"
    exit 1
fi

# --- 5. 128GB服务器内存配置 ---
log_info "配置128GB服务器专用JVM参数..."

# 检测系统内存
TOTAL_MEMORY_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
TOTAL_MEMORY_MB=$((TOTAL_MEMORY_KB / 1024))
log_info "检测到系统内存: ${TOTAL_MEMORY_MB}MB ($(echo "scale=1; ${TOTAL_MEMORY_MB}/1024" | bc)GB)"

# 检测大页面支持
HUGEPAGES_TOTAL=$(cat /proc/meminfo | grep HugePages_Total | awk '{print $2}')
HUGEPAGES_SIZE_KB=$(cat /proc/meminfo | grep Hugepagesize | awk '{print $2}')
if [ "$HUGEPAGES_TOTAL" -gt 0 ]; then
    HUGEPAGES_AVAILABLE_GB=$(echo "scale=1; $HUGEPAGES_TOTAL * $HUGEPAGES_SIZE_KB / 1024 / 1024" | bc)
    log_info "检测到大页面支持: ${HUGEPAGES_TOTAL}个 × ${HUGEPAGES_SIZE_KB}KB = ${HUGEPAGES_AVAILABLE_GB}GB"
    USE_LARGE_PAGES=true
else
    log_warn "未检测到大页面支持，建议配置大页面以提升性能"
    USE_LARGE_PAGES=false
fi

# 构建JVM参数 - 基于动态调整后的配置
JVM_ARGS=(
    # 内存配置 - 动态调整的堆内存配置
    "-Xms${HEAP_SIZE}"
    "-Xmx${HEAP_SIZE}"
    "-XX:NewSize=${NEW_SIZE}"
    "-XX:MaxNewSize=${NEW_SIZE}"
    "-XX:MetaspaceSize=${METASPACE_SIZE}"
    "-XX:MaxMetaspaceSize=${METASPACE_SIZE}"

    # 性能优化
    "-XX:+UnlockExperimentalVMOptions"
    "-XX:+UseStringDeduplication"
    "-XX:+OptimizeStringConcat"
)

# 根据堆大小决定是否使用压缩指针
HEAP_SIZE_GB=${HEAP_SIZE%g}
if [ "$HEAP_SIZE_GB" -le 32 ]; then
    JVM_ARGS+=(
        "-XX:+UseCompressedOops"
        "-XX:+UseCompressedClassPointers"
    )
    log_info "启用压缩指针 (堆内存 <= 32GB)"
else
    JVM_ARGS+=(
        "-XX:-UseCompressedOops"
        "-XX:-UseCompressedClassPointers"
    )
    log_info "禁用压缩指针 (堆内存 > 32GB)"
fi

# G1GC配置
JVM_ARGS+=(
    "-XX:+UseG1GC"
    "-XX:MaxGCPauseMillis=${PAUSE_TARGET}"
    "-XX:G1HeapRegionSize=${REGION_SIZE}"
    "-XX:G1NewSizePercent=${G1_NEW_SIZE_PERCENT}"
    "-XX:G1MaxNewSizePercent=${G1_MAX_NEW_SIZE_PERCENT}"
    "-XX:G1MixedGCCountTarget=${G1_MIXED_GC_COUNT_TARGET}"
    "-XX:G1MixedGCLiveThresholdPercent=${G1_MIXED_GC_LIVE_THRESHOLD}"
    "-XX:G1OldCSetRegionThresholdPercent=${G1_OLD_CSET_REGION_THRESHOLD}"
    "-XX:G1ReservePercent=${G1_RESERVE_PERCENT}"

    # G1GC额外优化
    "-XX:+G1UseAdaptiveIHOP"
    "-XX:G1HeapWastePercent=${G1_HEAP_WASTE_PERCENT}"
    "-XX:G1MixedGCLiveThresholdPercent=${G1_MIXED_GC_LIVE_THRESHOLD}"

    # 监控和调试
    "-Xlog:gc*=info:file=${PROFILING_OUTPUT_DIR}/${GC_LOG_PREFIX}-$(date +%Y%m%d-%H%M%S).log:time,uptime,level,tags:filecount=${GC_LOG_FILE_COUNT},filesize=${GC_LOG_FILE_SIZE}"
)

# 大页配置
if [ "$ENABLE_LARGE_PAGES" = "true" ]; then
    JVM_ARGS+=(
        "-XX:+UseLargePages"
        "-XX:LargePageSizeInBytes=${LARGE_PAGE_SIZE/m/M}"
    )
    log_info "启用大页支持 (${LARGE_PAGE_SIZE})"
else
    JVM_ARGS+=(
        "-XX:-UseLargePages"
    )
    log_info "禁用大页支持"
fi

# PLI相关的JVM参数
JVM_ARGS+=(
    # PLI缓存配置
    "-Dpli.cache.max.size.mb=${PLI_CACHE_MB}"
    "-Dpli.cache.cleanup.threshold.mb=${PLI_CLEANUP_MB}"

    # 内存管理优化
    "-XX:+UseG1GC"
    "-XX:+UnlockExperimentalVMOptions"
    "-XX:+UseTransparentHugePages"

    # PLI优化配置
    "-Dpli.cache.max.memory.mb=${PLI_CACHE_MB}"
    "-Dpli.cache.cleanup.threshold.mb=${PLI_CLEANUP_MB}"
    "-Dmemory.monitor.enabled=true"
    "-Dmemory.monitor.warning.threshold=0.8"
    "-Dmemory.monitor.critical.threshold=0.9"
    "-Dpli.performance.stats=true"
    "-Dpli.memory.stats=true"
    "-Dpli.enable.all.optimizations=true"
    "-Dstreaming.pli.chunk.size=300000"
    "-Dpli.cache.aggressive.mode=true"
    "-Dpli.cache.use.offheap=true"
)

# 根据大页面支持情况添加相应参数
if [ "$USE_LARGE_PAGES" = "true" ]; then
    JVM_ARGS+=(
        "-XX:+UseLargePages"
        "-XX:LargePageSizeInBytes=2m"
        "-XX:+AlwaysPreTouch"
    )
    log_info "启用大页面支持"
else
    # 不使用大页面时的优化
    JVM_ARGS+=(
        "-XX:+AlwaysPreTouch"
    )
    log_warn "未启用大页面，建议配置大页面以获得更好性能"
fi

# 预先准备async-profiler agent配置
AGENT_PARAMS=""

if [ "$ENABLE_PROFILING" = "true" ]; then
    log_info "准备性能分析 (async-profiler v4.1 Agent模式)..."
    
    # 输出目录已在前面创建并验证
    if [ ! -w "$PROFILING_OUTPUT_DIR" ]; then
        log_error "输出目录无写入权限: $PROFILING_OUTPUT_DIR"
        log_warn "禁用性能分析功能"
        ENABLE_PROFILING=false
    else
        log_info "输出目录: $PROFILING_OUTPUT_DIR"
        
        # 构建CPU分析参数
        if [ "$ENABLE_CPU_PROFILING" = "true" ]; then
            # 使用传递的火焰图文件名，或生成默认文件名
            if [ -n "$FLAMEGRAPH_FILE" ]; then
                CPU_FLAME_GRAPH_FILE="$FLAMEGRAPH_FILE"
            elif [ -n "$STRUCTURED_OUTPUT_DIR" ]; then
                # 如果有结构化输出目录，使用它
                CPU_FLAME_GRAPH_FILE="${STRUCTURED_OUTPUT_DIR}/flamegraph_${SAMPLING_MODE}_${TIMESTAMP}.html"
            else
                # 使用默认输出目录
                CPU_FLAME_GRAPH_FILE="${PROFILING_OUTPUT_DIR}/cpu_flamegraph_${PLI_MODE}_${TIMESTAMP}.html"
            fi
            
            # 验证async-profiler v4.1的agent参数格式
            # 格式: start,event=cpu,interval=10ms,file=/absolute/path/to/output.html
            AGENT_PARAMS="start,event=${CPU_PROFILING_EVENT},interval=${PROFILING_INTERVAL},file=${CPU_FLAME_GRAPH_FILE}"
            
            log_info "CPU火焰图将生成于: $CPU_FLAME_GRAPH_FILE"
            log_info "Agent参数: $AGENT_PARAMS"
            
            # 验证agent库文件
            if [ ! -f "$ASYNC_PROFILER_LIB" ]; then
                log_error "Agent库文件不存在: $ASYNC_PROFILER_LIB"
                log_warn "禁用性能分析功能"
                ENABLE_PROFILING=false
                AGENT_PARAMS=""
            else
                # 测试agent参数格式
                log_info "测试Agent库兼容性..."
                if ! java -agentpath:"${ASYNC_PROFILER_LIB}=help" -version >/dev/null 2>&1; then
                    log_warn "Agent库可能有问题或与Java版本不兼容，但仍将继续尝试"
                else
                    log_success "Agent库测试通过"
                fi
            fi
        fi
        
        # 内存分析配置（将在Java进程启动后通过attach模式进行）
        if [ "$ENABLE_ALLOC_PROFILING" = "true" ]; then
            ALLOC_JFR_FILE="${PROFILING_OUTPUT_DIR}/alloc_profiling_${PLI_MODE}_${TIMESTAMP}.jfr"
            log_info "内存分配JFR文件将生成于: $ALLOC_JFR_FILE"
            log_info "(内存分析将在应用启动后通过attach模式启动)"
        fi
    fi
fi

# 添加async-profiler agent配置到JVM参数
# DEBUG: 验证逻辑修复 - 检查AGENT_PARAMS是否已正确设置
if [ "$DEBUG_MODE" = "true" ] || [ "$ENABLE_PROFILING" = "true" ]; then
    log_info "调试信息: AGENT_PARAMS='${AGENT_PARAMS}'"
    log_info "调试信息: ENABLE_PROFILING=${ENABLE_PROFILING}, ENABLE_CPU_PROFILING=${ENABLE_CPU_PROFILING}"
fi

if [ "$ENABLE_PROFILING" = "true" ] && [ "$ENABLE_CPU_PROFILING" = "true" ] && [ -n "$AGENT_PARAMS" ]; then
    AGENT_PATH_PARAM="-agentpath:${ASYNC_PROFILER_LIB}=${AGENT_PARAMS}"
    JVM_ARGS+=(
        "$AGENT_PATH_PARAM"
    )
    log_success "已启用async-profiler Agent模式"
    log_info "Agent库路径: $ASYNC_PROFILER_LIB"
    log_info "完整Agent配置: $AGENT_PATH_PARAM"

    # 验证agent配置
    if [ "$DEBUG_MODE" = "true" ]; then
        log_info "调试模式: 显示完整JVM参数"
        log_info "JVM_ARGS包含的Agent配置:"
        for arg in "${JVM_ARGS[@]}"; do
            if [[ "$arg" == *"agentpath"* ]]; then
                log_info "  $arg"
            fi
        done
    fi
fi

# --- 6. 显示配置信息 ---
echo
echo "============================================================"
echo "        128GB服务器优化实验配置         "
echo "============================================================"
echo "系统内存:     ${TOTAL_MEMORY_MB}MB ($(echo "scale=1; ${TOTAL_MEMORY_MB}/1024" | bc)GB)"
echo "堆内存大小:   ${HEAP_SIZE}"
echo "新生代大小:   ${NEW_SIZE}"
echo "元空间大小:   ${METASPACE_SIZE}"
echo "G1区域大小:   ${REGION_SIZE}"
echo "GC暂停目标:   ${PAUSE_TARGET}ms"
echo "PLI缓存限制:  ${PLI_CACHE_MB}MB ($(echo "scale=1; ${PLI_CACHE_MB}/1024" | bc)GB)"
echo "PLI清理阈值:  ${PLI_CLEANUP_MB}MB ($(echo "scale=1; ${PLI_CLEANUP_MB}/1024" | bc)GB)"
echo "PLI实现模式:  ${PLI_MODE}"
echo "数据集路径:   ${DATASET_PATH}"
echo "结果文件:     ${RESULTS_FILE}"
echo "采样模式:     ${SAMPLING_MODE}"
echo "算法超时:     ${ALGORITHM_TIMEOUT}分钟"
if [ "$ENABLE_PROFILING" = "true" ]; then
    echo "性能分析:     已启用"
else
    echo "性能分析:     已禁用"
fi
if [ "$ENABLE_LARGE_PAGES" = "true" ]; then
    echo "大页面支持:   已启用"
else
    echo "大页面支持:   已禁用"
fi

# 显示压缩指针状态
HEAP_SIZE_GB=${HEAP_SIZE%g}
if [ "$HEAP_SIZE_GB" -le 32 ]; then
    echo "压缩指针:     已启用 (堆内存 <= 32GB)"
else
    echo "压缩指针:     已禁用 (堆内存 > 32GB)"
fi


echo "============================================================"

# --- 7. 执行实验 ---
echo
log_info "开始执行128GB服务器优化实验..."
echo ">>  数据集路径: ${DATASET_PATH}"
echo ">>  PLI模式: ${PLI_MODE}"
echo ">>  采样模式: ${SAMPLING_MODE}"
echo ">>  运行TANE: ${RUN_TANE}"
echo ">>  结果文件: ${RESULTS_FILE} (${RUN_MODE}模式)"
echo ">>  超时时间: ${ALGORITHM_TIMEOUT}分钟"
echo ">>  内存配置: ${HEAP_SIZE}堆内存 + $(echo "scale=1; ${PLI_CACHE_MB}/1024" | bc)GB PLI缓存"
echo ">>  GC配置: G1GC, ${PAUSE_TARGET}ms暂停目标, ${REGION_SIZE}区域大小"
if [ "$ENABLE_PROFILING" = "true" ]; then
    echo ">>  性能分析: 已启用"
fi
echo "-------------------------------------------------"

# 构建完整的Java命令 - 使用配置区域的参数
JAVA_CMD=(
    "java"
    "${JVM_ARGS[@]}"
    "-cp" "$JAR_FILE"
    "$MAIN_CLASS"
    "--dataset" "${DATASET_PATH}"
    "--results-file" "${RESULTS_FILE}"
    "--pli-mode" "${PLI_MODE}"
    "--sampling-mode" "${SAMPLING_MODE}"
    "--run-mode" "${RUN_MODE}"
    "--run-tane" "${RUN_TANE}"
    "--timeout" "${ALGORITHM_TIMEOUT}"
    "--max-error" "${MAX_ERROR}"
    "--sample-param" "${SAMPLE_PARAM}"
    "--seed" "${RANDOM_SEED}"
)

# 显示完整命令（可选）
if [ "$DEBUG_MODE" = "true" ]; then
    log_info "执行命令:"
    printf '%s ' "${JAVA_CMD[@]}"
    echo
    echo
fi

# 启动实验程序（Agent模式性能分析）
if [ "$ENABLE_PROFILING" = "true" ]; then
    log_info "启动实验程序..."

    if [ "$ENABLE_CPU_PROFILING" = "true" ]; then
        log_info "CPU分析已通过Agent模式启用，将跟随应用程序完整生命周期"
        log_info "预期输出文件: $CPU_FLAME_GRAPH_FILE"
    fi

    # 显示关键的JVM参数用于调试
    if [ "$DEBUG_MODE" = "true" ]; then
        log_info "关键JVM参数调试信息:"
        for arg in "${JAVA_CMD[@]}"; do
            if [[ "$arg" == *"agentpath"* ]] || [[ "$arg" == *"pli"* ]] || [[ "$arg" == *"PLI"* ]]; then
                log_info "  $arg"
            fi
        done
    fi

    # 启动Java程序（Agent会自动开始profiling）
    log_info "启动实验程序..."
    "${JAVA_CMD[@]}" &
    JAVA_PID=$!

    # 等待Java程序启动
    sleep 8  # 增加等待时间确保Agent完全加载

    # 检查Java程序是否还在运行
    if ! kill -0 $JAVA_PID 2>/dev/null; then
        log_error "Java程序启动失败"
        log_error "可能的原因:"
        log_error "1. Agent库加载失败"
        log_error "2. Agent参数格式错误"
        log_error "3. 内存分配失败"
        log_error "4. 输出文件路径权限问题"
        exit 1
    fi

    log_info "Java程序PID: $JAVA_PID"

    # 验证Agent是否成功加载
    if [ "$ENABLE_CPU_PROFILING" = "true" ]; then
        log_info "验证Agent加载状态..."
        # 检查进程的加载库
        if command -v lsof >/dev/null 2>&1; then
            if lsof -p $JAVA_PID 2>/dev/null | grep -q "libasyncProfiler.so"; then
                log_success "Agent库已成功加载到Java进程"
            else
                log_warn "无法确认Agent库是否加载，但进程正在运行"
            fi
        fi
    fi

    # 如果启用了内存分析，通过attach模式启动
    ALLOC_PROFILER_PID=""

    # 启动内存分配分析 (通过attach模式)
    if [ "$ENABLE_ALLOC_PROFILING" = "true" ]; then
        log_info "开始内存分配分析 (attach模式生成JFR文件)..."

        # 检查asprof可执行文件是否存在
        PROFILER_EXECUTABLE="$ASYNC_PROFILER_PATH/bin/asprof"
        if [ ! -f "$PROFILER_EXECUTABLE" ] || [ ! -x "$PROFILER_EXECUTABLE" ]; then
            log_warn "asprof可执行文件不可用，跳过内存分析"
            log_warn "路径: $PROFILER_EXECUTABLE"
        else

            # 构建内存分配分析命令
            ALLOC_ASPROF_CMD=(
                "$PROFILER_EXECUTABLE"
                "-e" "$ALLOC_PROFILING_EVENT"
                "-i" "$PROFILING_INTERVAL"
                "-o" "jfr"
                "-f" "$ALLOC_JFR_FILE"
                "$JAVA_PID"
            )

            # 启动内存分配profiling (attach模式)
            "${ALLOC_ASPROF_CMD[@]}" &
            ALLOC_PROFILER_PID=$!

            log_info "内存分配Profiler命令: ${ALLOC_ASPROF_CMD[*]}"
            log_info "内存分配Profiler PID: $ALLOC_PROFILER_PID"
        fi
    fi

    # 等待Java程序完成
    log_info "等待实验完成..."
    log_info "CPU分析将自动跟随应用程序完成并生成火焰图"
    wait $JAVA_PID
    JAVA_EXIT_CODE=$?

    # 停止内存分析profiler进程（如果有）
    if [ -n "$ALLOC_PROFILER_PID" ] && kill -0 $ALLOC_PROFILER_PID 2>/dev/null; then
        log_info "停止内存分析进程..."
        log_info "停止profiler进程: $ALLOC_PROFILER_PID"

        # 对于async-profiler v4.1，发送SIGTERM信号来优雅停止
        kill -TERM $ALLOC_PROFILER_PID 2>/dev/null || true

        # 等待一段时间让profiler完成文件写入
        sleep 3

        # 检查并强制终止仍在运行的profiler
        if kill -0 $ALLOC_PROFILER_PID 2>/dev/null; then
            log_warn "强制终止内存分析进程: $ALLOC_PROFILER_PID"
            kill -KILL $ALLOC_PROFILER_PID 2>/dev/null || true
        fi
        wait $ALLOC_PROFILER_PID 2>/dev/null || true

        log_success "内存分析进程已停止"
    fi

else
    # 不使用性能分析，直接执行
    log_info "启动实验..."
    "${JAVA_CMD[@]}"
    JAVA_EXIT_CODE=$?
fi

# 检查执行结果
if [ $JAVA_EXIT_CODE -eq 0 ]; then
    echo
    log_success "所有实验已成功完成!"
    log_success "你可以在 ${RESULTS_FILE} 文件中查看结果。"

    # 显示GC日志文件位置
    GC_LOG=$(ls -t "${PROFILING_OUTPUT_DIR}/gc-128gb-*.log" 2>/dev/null | head -n1)
    if [ -n "$GC_LOG" ]; then
        log_info "GC日志文件: $GC_LOG"
    fi

    # 显示性能分析结果
    if [ "$ENABLE_PROFILING" = "true" ]; then
        echo
        log_success "性能分析完成!"

        # 显示CPU分析结果
        if [ "$ENABLE_CPU_PROFILING" = "true" ]; then
            if [ -f "$CPU_FLAME_GRAPH_FILE" ]; then
                log_success "CPU火焰图文件生成成功: $CPU_FLAME_GRAPH_FILE"
                log_info "分析模式: Agent模式"

                # 显示文件信息
                file_size=$(du -h "$CPU_FLAME_GRAPH_FILE" | cut -f1)
                log_info "文件大小: $file_size"
            else
                log_error "CPU火焰图文件未生成: $CPU_FLAME_GRAPH_FILE"
                log_error "问题诊断:"

                # 检查输出目录
                output_dir=$(dirname "$CPU_FLAME_GRAPH_FILE")
                if [ ! -d "$output_dir" ]; then
                    log_error "- 输出目录不存在: $output_dir"
                elif [ ! -w "$output_dir" ]; then
                    log_error "- 输出目录无写入权限: $output_dir"
                else
                    log_info "- 输出目录权限正常: $output_dir"
                fi

                # 检查Agent配置
                log_error "- 请检查Agent参数: $AGENT_PARAMS"
                log_error "- 请检查Agent库: $ASYNC_PROFILER_LIB"

                # 检查是否有其他async-profiler输出文件
                profiling_files=$(find . -name "*.html" -o -name "*profile*" -o -name "*flamegraph*" 2>/dev/null | head -5)
                if [ -n "$profiling_files" ]; then
                    log_info "发现的其他profiling文件:"
                    echo "$profiling_files" | while read -r file; do
                        log_info "  $file"
                    done
                fi
            fi
        fi

        # 显示内存分配分析结果
        if [ "$ENABLE_ALLOC_PROFILING" = "true" ]; then
            if [ -f "$ALLOC_JFR_FILE" ]; then
                log_info "内存分配JFR文件: $ALLOC_JFR_FILE"
                log_info "分析模式: Attach模式 (独立进程分析)"
                log_info "可以使用以下工具分析JFR文件:"
                log_info "- JProfiler: 专业的Java性能分析工具"
                log_info "- VisualVM: 免费的Java性能监控工具"
                log_info "- JDK Mission Control: Oracle官方JFR分析工具"
            else
                log_warn "内存分配JFR文件未生成: $ALLOC_JFR_FILE"
                log_warn "可能原因: asprof可执行文件不可用或attach失败"
            fi
        fi
    fi

else
    echo
    log_error "实验执行失败，退出码: $JAVA_EXIT_CODE"
    log_warn "请检查日志以获取更多信息。"
    exit $JAVA_EXIT_CODE
fi