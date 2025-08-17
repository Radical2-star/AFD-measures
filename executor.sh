#!/bin/bash

# AFD-measures 底层执行器

set -e

# ============================================================
#                    参数定义和解析
# ============================================================

# 显示帮助信息
show_help() {
    echo "AFD-measures 底层执行器"
    echo ""
    echo "用法: $0 [必需参数] [可选参数]"
    echo ""
    echo "必需参数:"
    echo "  --jar-file <PATH>              Java应用程序JAR文件路径"
    echo "  --main-class <CLASS>           Java主类名"
    echo "  --dataset-path <PATH>          数据集文件完整路径"
    echo "  --output-dir <PATH>            结果CSV文件输出目录路径"
    echo "  --other-files-dir <PATH>       其他文件（GC日志、火焰图）输出目录路径"
    echo "  --results-file <FILENAME>      结果CSV文件名（不含路径）"
    echo "  --run-mode <APPEND|OVERWRITE>  结果文件写入模式"
    echo "  --algorithm <PYRO|TANE>        算法类型"
    echo "  --sampling-mode <MODE>         采样模式"
    echo "  --timeout <MINUTES>            算法超时时间（分钟）"
    echo "  --enable-profiling <true|false> 是否启用性能分析"
    echo ""
    echo "可选参数:"
    echo "  --flamegraph-file <FILENAME>   火焰图文件名（不含路径）"
    echo "  --max-error <VALUE>            最大错误阈值（默认：0.05）"
    echo "  --sample-param <VALUE>         采样参数（默认：200）"
    echo "  --random-seed <VALUE>          随机种子（默认：114514）"
    echo "  --compile                      执行Maven编译"
    echo "  --heap-size <SIZE>             JVM堆内存大小（默认：64g）"
    echo "  --help, -h                     显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 --jar-file target/app.jar --main-class Main \\"
    echo "     --dataset-path data/test.csv --output-dir results \\"
    echo "     --other-files-dir results/dataset1 --results-file results.csv \\"
    echo "     --run-mode APPEND --algorithm PYRO --sampling-mode RANDOM \\"
    echo "     --timeout 120 --enable-profiling true"
    echo ""
}

# 初始化变量
JAR_FILE=""
MAIN_CLASS=""
DATASET_PATH=""
OUTPUT_DIR=""
OTHER_FILES_DIR=""
RESULTS_FILE=""
RUN_MODE=""
ALGORITHM=""
SAMPLING_MODE=""
TIMEOUT=""
ENABLE_PROFILING=""
FLAMEGRAPH_FILE=""
MAX_ERROR="0.05"
SAMPLE_PARAM="200"
RANDOM_SEED="114514"
COMPILE=false
HEAP_SIZE="64g"

# 必需参数标记
required_params=(
    "jar-file" "main-class" "dataset-path" "output-dir" "other-files-dir"
    "results-file" "run-mode" "algorithm" "sampling-mode" 
    "timeout" "enable-profiling"
)
declare -A provided_params

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --jar-file)
            JAR_FILE="$2"
            provided_params["jar-file"]=1
            shift 2
            ;;
        --main-class)
            MAIN_CLASS="$2"
            provided_params["main-class"]=1
            shift 2
            ;;
        --dataset-path)
            DATASET_PATH="$2"
            provided_params["dataset-path"]=1
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            provided_params["output-dir"]=1
            shift 2
            ;;
        --other-files-dir)
            OTHER_FILES_DIR="$2"
            provided_params["other-files-dir"]=1
            shift 2
            ;;
        --results-file)
            RESULTS_FILE="$2"
            provided_params["results-file"]=1
            shift 2
            ;;
        --run-mode)
            RUN_MODE="$2"
            provided_params["run-mode"]=1
            shift 2
            ;;
        --algorithm)
            ALGORITHM="$2"
            provided_params["algorithm"]=1
            shift 2
            ;;
        --sampling-mode)
            SAMPLING_MODE="$2"
            provided_params["sampling-mode"]=1
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            provided_params["timeout"]=1
            shift 2
            ;;
        --enable-profiling)
            ENABLE_PROFILING="$2"
            provided_params["enable-profiling"]=1
            shift 2
            ;;
        --flamegraph-file)
            FLAMEGRAPH_FILE="$2"
            shift 2
            ;;
        --max-error)
            MAX_ERROR="$2"
            shift 2
            ;;
        --sample-param)
            SAMPLE_PARAM="$2"
            shift 2
            ;;
        --random-seed)
            RANDOM_SEED="$2"
            shift 2
            ;;
        --compile)
            COMPILE=true
            shift
            ;;
        --heap-size)
            HEAP_SIZE="$2"
            shift 2
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            echo "错误: 未知参数 $1"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
done

# ============================================================
#                    参数验证
# ============================================================

# 检查必需参数
missing_params=()
for param in "${required_params[@]}"; do
    if [[ -z "${provided_params[$param]}" ]]; then
        missing_params+=("--$param")
    fi
done

if [[ ${#missing_params[@]} -gt 0 ]]; then
    echo "错误: 缺少必需参数: ${missing_params[*]}"
    echo ""
    echo "使用 --help 查看所有必需参数"
    exit 1
fi

# 验证文件和目录
if [[ ! -f "$DATASET_PATH" ]]; then
    echo "错误: 数据集文件不存在: $DATASET_PATH"
    exit 1
fi

# 创建输出目录
mkdir -p "$OUTPUT_DIR"
if [[ ! -w "$OUTPUT_DIR" ]]; then
    echo "错误: 输出目录无写入权限: $OUTPUT_DIR"
    exit 1
fi

# 创建其他文件输出目录
mkdir -p "$OTHER_FILES_DIR"
if [[ ! -w "$OTHER_FILES_DIR" ]]; then
    echo "错误: 其他文件输出目录无写入权限: $OTHER_FILES_DIR"
    exit 1
fi

# 验证run-mode值
if [[ "$RUN_MODE" != "APPEND" && "$RUN_MODE" != "OVERWRITE" ]]; then
    echo "错误: run-mode必须是APPEND或OVERWRITE，当前值: $RUN_MODE"
    exit 1
fi

# 验证算法类型参数
if [[ "$ALGORITHM" != "PYRO" && "$ALGORITHM" != "TANE" ]]; then
    echo "错误: algorithm必须是PYRO或TANE，当前值: $ALGORITHM"
    exit 1
fi

if [[ "$ENABLE_PROFILING" != "true" && "$ENABLE_PROFILING" != "false" ]]; then
    echo "错误: enable-profiling必须是true或false，当前值: $ENABLE_PROFILING"
    exit 1
fi

# ============================================================
#                    编译处理
# ============================================================

if [[ "$COMPILE" == "true" ]]; then
    echo "[执行器] 开始Maven编译..."
    mvn clean package -DskipTests
    echo "[执行器] 编译完成"
fi

# 验证JAR文件
if [[ ! -f "$JAR_FILE" ]]; then
    echo "错误: JAR文件不存在: $JAR_FILE"
    if [[ "$COMPILE" != "true" ]]; then
        echo "提示: 可能需要添加 --compile 参数来编译项目"
    fi
    exit 1
fi

# ============================================================
#                    运行时配置
# ============================================================

echo "[执行器] 运行配置:"
echo "  JAR文件: $JAR_FILE"
echo "  主类: $MAIN_CLASS"
echo "  数据集: $DATASET_PATH"
echo "  结果文件输出目录: $OUTPUT_DIR"
echo "  其他文件输出目录: $OTHER_FILES_DIR"
echo "  结果文件: $RESULTS_FILE"
echo "  运行模式: $RUN_MODE"
echo "  算法类型: $ALGORITHM"
echo "  采样模式: $SAMPLING_MODE"
echo "  超时时间: ${TIMEOUT}分钟"
echo "  性能分析: $ENABLE_PROFILING"

# 构建完整的结果文件路径
FULL_RESULTS_PATH="${OUTPUT_DIR}/${RESULTS_FILE}"

# ============================================================
#                    JVM参数配置
# ============================================================

# JVM基础参数
JVM_ARGS=(
    "-Xms${HEAP_SIZE}"
    "-Xmx${HEAP_SIZE}"
    "-XX:NewSize=16g"
    "-XX:MaxNewSize=16g"
    "-XX:MetaspaceSize=3g"
    "-XX:MaxMetaspaceSize=3g"
)

# 性能优化
JVM_ARGS+=(
    "-XX:+UnlockExperimentalVMOptions"
    "-XX:+UseStringDeduplication"
    "-XX:+OptimizeStringConcat"
)

# G1GC配置
JVM_ARGS+=(
    "-XX:+UseG1GC"
    "-XX:MaxGCPauseMillis=150"
    "-XX:G1HeapRegionSize=32m"
    "-XX:G1NewSizePercent=25"
    "-XX:G1MaxNewSizePercent=35"
    "-XX:G1MixedGCCountTarget=8"
    "-XX:G1MixedGCLiveThresholdPercent=85"
    "-XX:G1OldCSetRegionThresholdPercent=10"
    "-XX:G1ReservePercent=10"
    "-XX:G1HeapWastePercent=3"
)

# 根据堆大小决定压缩指针
HEAP_SIZE_GB=${HEAP_SIZE%g}
if [[ "$HEAP_SIZE_GB" -le 32 ]]; then
    JVM_ARGS+=(
        "-XX:+UseCompressedOops"
        "-XX:+UseCompressedClassPointers"
    )
    echo "[执行器] 启用压缩指针 (堆内存 <= 32GB)"
else
    JVM_ARGS+=(
        "-XX:-UseCompressedOops"
        "-XX:-UseCompressedClassPointers"
    )
    echo "[执行器] 禁用压缩指针 (堆内存 > 32GB)"
fi

# PLI优化配置
PLI_CACHE_MB=24576
PLI_CLEANUP_MB=19660
JVM_ARGS+=(
    "-Dpli.cache.max.size.mb=${PLI_CACHE_MB}"
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

# GC日志配置
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
GC_LOG_FILE="${OTHER_FILES_DIR}/gc-executor-${TIMESTAMP}.log"
JVM_ARGS+=(
    "-Xlog:gc*=info:file=${GC_LOG_FILE}:time,uptime,level,tags:filecount=5,filesize=50M"
)

# ============================================================
#                    性能分析配置
# ============================================================

if [[ "$ENABLE_PROFILING" == "true" ]]; then
    echo "[执行器] 启用性能分析"
    
    # 如果没有指定火焰图文件名，生成默认名称
    if [[ -z "$FLAMEGRAPH_FILE" ]]; then
        DATASET_BASENAME=$(basename "$DATASET_PATH" .csv)
        FLAMEGRAPH_FILE="flamegraph-${DATASET_BASENAME}-${SAMPLING_MODE}-${TIMESTAMP}.html"
    fi
    
    FULL_FLAMEGRAPH_PATH="${OTHER_FILES_DIR}/${FLAMEGRAPH_FILE}"
    echo "[执行器] 火焰图输出: $FULL_FLAMEGRAPH_PATH"
    
    # async-profiler配置
    ASYNC_PROFILER_PATH="/opt/async-profiler"
    ASYNC_PROFILER_LIB="${ASYNC_PROFILER_PATH}/lib/libasyncProfiler.so"
    
    if [[ -f "$ASYNC_PROFILER_LIB" ]]; then
        AGENT_PARAMS="start,event=cpu,interval=10ms,file=${FULL_FLAMEGRAPH_PATH}"
        JVM_ARGS+=(
            "-agentpath:${ASYNC_PROFILER_LIB}=${AGENT_PARAMS}"
        )
        echo "[执行器] async-profiler已配置"
    else
        echo "[执行器] 警告: async-profiler未找到，跳过性能分析"
        echo "[执行器] 预期位置: $ASYNC_PROFILER_LIB"
    fi
else
    echo "[执行器] 性能分析已禁用"
fi

# ============================================================
#                    Java应用程序参数
# ============================================================

JAVA_APP_ARGS=(
    "--dataset" "$DATASET_PATH"
    "--results-file" "$FULL_RESULTS_PATH"
    "--algorithm" "$ALGORITHM"
    "--sampling-mode" "$SAMPLING_MODE"
    "--run-mode" "$RUN_MODE"
    "--timeout" "$TIMEOUT"
    "--max-error" "$MAX_ERROR"
    "--sample-param" "$SAMPLE_PARAM"
    "--seed" "$RANDOM_SEED"
)

# ============================================================
#                    执行实验
# ============================================================

echo "[执行器] 开始执行实验..."
echo "[执行器] 数据集: $(basename "$DATASET_PATH")"
echo "[执行器] 算法类型: $ALGORITHM"
echo "[执行器] 采样模式: $SAMPLING_MODE"
echo "[执行器] 超时: ${TIMEOUT}分钟"

# 构建完整的Java命令
JAVA_CMD=(
    "java"
    "${JVM_ARGS[@]}"
    "-cp" "$JAR_FILE"
    "$MAIN_CLASS"
    "${JAVA_APP_ARGS[@]}"
)

# 记录开始时间
START_TIME=$(date)
echo "[执行器] 开始时间: $START_TIME"

# 执行Java程序
"${JAVA_CMD[@]}"
EXIT_CODE=$?

# 记录结束时间
END_TIME=$(date)
echo "[执行器] 结束时间: $END_TIME"

# ============================================================
#                    结果验证
# ============================================================

if [[ $EXIT_CODE -eq 0 ]]; then
    echo "[执行器] 实验执行成功"
    
    # 验证结果文件
    if [[ -f "$FULL_RESULTS_PATH" ]]; then
        RESULTS_SIZE=$(du -h "$FULL_RESULTS_PATH" | cut -f1)
        echo "[执行器] 结果文件: $FULL_RESULTS_PATH ($RESULTS_SIZE)"
    else
        echo "[执行器] 警告: 结果文件未生成: $FULL_RESULTS_PATH"
    fi
    
    # 验证火焰图（如果启用了性能分析）
    if [[ "$ENABLE_PROFILING" == "true" && -n "$FLAMEGRAPH_FILE" ]]; then
        if [[ -f "$FULL_FLAMEGRAPH_PATH" ]]; then
            FLAMEGRAPH_SIZE=$(du -h "$FULL_FLAMEGRAPH_PATH" | cut -f1)
            echo "[执行器] 火焰图文件: $FULL_FLAMEGRAPH_PATH ($FLAMEGRAPH_SIZE)"
        else
            echo "[执行器] 警告: 火焰图文件未生成: $FULL_FLAMEGRAPH_PATH"
        fi
    fi
    
    # 显示GC日志
    if [[ -f "$GC_LOG_FILE" ]]; then
        GC_LOG_SIZE=$(du -h "$GC_LOG_FILE" | cut -f1)
        echo "[执行器] GC日志: $GC_LOG_FILE ($GC_LOG_SIZE)"
    fi
    
    echo "[执行器] 执行器任务完成"
else
    echo "[执行器] 实验执行失败，退出码: $EXIT_CODE"
    exit $EXIT_CODE
fi