#!/bin/bash

# AFD-measures PLI优化实验启动脚本
# 此脚本自动检测系统配置并设置最优的JVM参数

set -e

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
EXPERIMENT_JAR="$SCRIPT_DIR/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar"
CONFIG_FILE="$SCRIPT_DIR/pli-optimization.properties"

# 兼容原始脚本的JAR路径
if [ ! -f "$EXPERIMENT_JAR" ]; then
    EXPERIMENT_JAR="$PROJECT_ROOT/AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar"
fi

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# 检测系统内存
detect_system_memory() {
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        TOTAL_MEMORY_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
        TOTAL_MEMORY_MB=$((TOTAL_MEMORY_KB / 1024))
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        TOTAL_MEMORY_BYTES=$(sysctl -n hw.memsize)
        TOTAL_MEMORY_MB=$((TOTAL_MEMORY_BYTES / 1024 / 1024))
    else
        # Windows (Git Bash) 或其他系统
        log_warn "无法自动检测系统内存，使用默认配置"
        TOTAL_MEMORY_MB=8192
    fi
    
    log_info "检测到系统内存: ${TOTAL_MEMORY_MB}MB"
}

# 根据系统内存计算最优JVM参数
calculate_jvm_params() {
    # 为系统保留适当的内存，大内存服务器可以更激进
    if [ $TOTAL_MEMORY_MB -ge 65536 ]; then
        # 64GB+ 系统，保留15%给系统
        AVAILABLE_MEMORY_MB=$((TOTAL_MEMORY_MB * 85 / 100))
    elif [ $TOTAL_MEMORY_MB -ge 32768 ]; then
        # 32GB+ 系统，保留20%给系统
        AVAILABLE_MEMORY_MB=$((TOTAL_MEMORY_MB * 80 / 100))
    else
        # 小于32GB系统，保留25%给系统
        AVAILABLE_MEMORY_MB=$((TOTAL_MEMORY_MB * 3 / 4))
    fi

    if [ $AVAILABLE_MEMORY_MB -ge 81920 ]; then
        # 80GB+ 系统 - 超大规模内存模式
        HEAP_SIZE="72g"
        NEW_SIZE="18g"
        REGION_SIZE="64m"
        PAUSE_TARGET="100"
        METASPACE_SIZE="2g"
        log_info "配置为超大规模内存模式 (80GB+) - 针对大数据集优化"
    elif [ $AVAILABLE_MEMORY_MB -ge 49152 ]; then
        # 48GB+ 系统 - 大规模内存模式
        HEAP_SIZE="40g"
        NEW_SIZE="10g"
        REGION_SIZE="32m"
        PAUSE_TARGET="150"
        METASPACE_SIZE="1g"
        log_info "配置为大规模内存模式 (48GB+)"
    elif [ $AVAILABLE_MEMORY_MB -ge 24576 ]; then
        # 24GB+ 系统 - 高内存模式
        HEAP_SIZE="20g"
        NEW_SIZE="5g"
        REGION_SIZE="32m"
        PAUSE_TARGET="200"
        METASPACE_SIZE="512m"
        log_info "配置为高内存模式 (24GB+)"
    elif [ $AVAILABLE_MEMORY_MB -ge 16384 ]; then
        # 16GB+ 系统
        HEAP_SIZE="12g"
        NEW_SIZE="3g"
        REGION_SIZE="32m"
        PAUSE_TARGET="200"
        METASPACE_SIZE="512m"
        log_info "配置为超大内存模式 (16GB+)"
    elif [ $AVAILABLE_MEMORY_MB -ge 8192 ]; then
        # 8GB+ 系统
        HEAP_SIZE="6g"
        NEW_SIZE="1536m"
        REGION_SIZE="16m"
        PAUSE_TARGET="200"
        METASPACE_SIZE="256m"
        log_info "配置为大内存模式 (8GB+)"
    elif [ $AVAILABLE_MEMORY_MB -ge 4096 ]; then
        # 4GB+ 系统
        HEAP_SIZE="3g"
        NEW_SIZE="768m"
        REGION_SIZE="8m"
        PAUSE_TARGET="300"
        METASPACE_SIZE="256m"
        log_info "配置为中等内存模式 (4GB+)"
    elif [ $AVAILABLE_MEMORY_MB -ge 2048 ]; then
        # 2GB+ 系统
        HEAP_SIZE="1536m"
        NEW_SIZE="384m"
        REGION_SIZE="4m"
        PAUSE_TARGET="500"
        METASPACE_SIZE="128m"
        log_info "配置为小内存模式 (2GB+)"
    else
        # <2GB 系统
        HEAP_SIZE="1g"
        NEW_SIZE="256m"
        REGION_SIZE="2m"
        PAUSE_TARGET="1000"
        METASPACE_SIZE="128m"
        log_warn "系统内存较小，可能影响大数据集处理性能"
    fi
}

# 构建JVM参数
build_jvm_args() {
    # 计算PLI缓存大小 - 大内存服务器可以分配更多
    if [ $AVAILABLE_MEMORY_MB -ge 81920 ]; then
        # 80GB+ 系统，分配30%给PLI缓存
        PLI_CACHE_MB=$((AVAILABLE_MEMORY_MB * 30 / 100))
        PLI_CLEANUP_MB=$((PLI_CACHE_MB * 80 / 100))
    elif [ $AVAILABLE_MEMORY_MB -ge 24576 ]; then
        # 24GB+ 系统，分配25%给PLI缓存
        PLI_CACHE_MB=$((AVAILABLE_MEMORY_MB * 25 / 100))
        PLI_CLEANUP_MB=$((PLI_CACHE_MB * 80 / 100))
    else
        # 小内存系统，保守分配
        PLI_CACHE_MB=$((AVAILABLE_MEMORY_MB / 4))
        PLI_CLEANUP_MB=$((AVAILABLE_MEMORY_MB / 5))
    fi

    JVM_ARGS=(
        # 内存配置
        "-Xms${HEAP_SIZE}"
        "-Xmx${HEAP_SIZE}"
        "-XX:NewSize=${NEW_SIZE}"
        "-XX:MaxNewSize=${NEW_SIZE}"
        "-XX:MetaspaceSize=${METASPACE_SIZE}"
        "-XX:MaxMetaspaceSize=${METASPACE_SIZE}"

        # G1GC配置 - 针对大内存优化
        "-XX:+UseG1GC"
        "-XX:MaxGCPauseMillis=${PAUSE_TARGET}"
        "-XX:G1HeapRegionSize=${REGION_SIZE}"
        "-XX:G1NewSizePercent=25"
        "-XX:G1MaxNewSizePercent=35"
        "-XX:G1MixedGCCountTarget=8"
        "-XX:G1MixedGCLiveThresholdPercent=85"
        "-XX:G1OldCSetRegionThresholdPercent=10"
        "-XX:G1ReservePercent=15"

        # 大内存系统的额外G1优化
        "-XX:+G1UseAdaptiveIHOP"
        "-XX:G1MixedGCLiveThresholdPercent=85"
        "-XX:G1HeapWastePercent=5"

        # 性能优化
        "-XX:+UnlockExperimentalVMOptions"
        "-XX:+UseStringDeduplication"
        "-XX:+OptimizeStringConcat"
        "-XX:+UseCompressedOops"
        "-XX:+UseCompressedClassPointers"

        # 大数据集优化
        "-XX:+UseLargePages"
        "-XX:LargePageSizeInBytes=2m"
        "-XX:+AlwaysPreTouch"

        # 监控和调试
        "-Xlog:gc*=info:file=gc-%t.log:time,uptime,level,tags:filecount=10,filesize=50M"

        # PLI优化配置 - 大内存服务器激进配置
        "-Dpli.cache.max.memory.mb=${PLI_CACHE_MB}"
        "-Dpli.cache.cleanup.threshold.mb=${PLI_CLEANUP_MB}"
        "-Dmemory.monitor.enabled=true"
        "-Dmemory.monitor.warning.threshold=0.8"
        "-Dmemory.monitor.critical.threshold=0.9"
        "-Dpli.performance.stats=true"
        "-Dpli.memory.stats=true"
    )
    
    # 根据内存大小调整策略
    if [ $AVAILABLE_MEMORY_MB -ge 81920 ]; then
        # 超大内存服务器，启用所有优化特性
        JVM_ARGS+=("-Dpli.enable.all.optimizations=true")
        JVM_ARGS+=("-Dstreaming.pli.chunk.size=200000")
        JVM_ARGS+=("-Dpli.cache.aggressive.mode=true")
        log_info "启用超大内存服务器全优化模式"
    elif [ $AVAILABLE_MEMORY_MB -ge 24576 ]; then
        # 大内存服务器，启用高级优化
        JVM_ARGS+=("-Dstreaming.pli.chunk.size=100000")
        JVM_ARGS+=("-Dpli.cache.enhanced.mode=true")
        log_info "启用大内存服务器高级优化模式"
    elif [ $AVAILABLE_MEMORY_MB -lt 2048 ]; then
        JVM_ARGS+=("-Dpli.force.streaming=true")
        log_info "启用强制流式处理模式"
    fi
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    
    # 检查Java
    if ! command -v java &> /dev/null; then
        log_error "未找到Java，请安装Java 8或更高版本"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    log_info "Java版本: $JAVA_VERSION"
    
    # 检查实验JAR文件
    if [ ! -f "$EXPERIMENT_JAR" ]; then
        log_warn "未找到实验JAR文件: $EXPERIMENT_JAR"
        log_info "尝试构建项目..."
        
        cd "$PROJECT_ROOT"
        if command -v mvn &> /dev/null; then
            mvn clean package -DskipTests
        else
            log_error "未找到Maven，请手动构建项目或安装Maven"
            exit 1
        fi
    fi
    
    # 检查配置文件
    if [ ! -f "$CONFIG_FILE" ]; then
        log_warn "未找到配置文件: $CONFIG_FILE"
        log_info "使用默认配置"
    else
        log_info "使用配置文件: $CONFIG_FILE"
    fi
}

# 显示配置信息
show_configuration() {
    echo
    echo "============================================================"
    echo "                   PLI优化实验配置"
    echo "============================================================"
    echo "系统内存:     ${TOTAL_MEMORY_MB}MB ($(echo "scale=1; ${TOTAL_MEMORY_MB}/1024" | bc)GB)"
    echo "可用内存:     ${AVAILABLE_MEMORY_MB}MB ($(echo "scale=1; ${AVAILABLE_MEMORY_MB}/1024" | bc)GB)"
    echo "堆内存大小:   ${HEAP_SIZE}"
    echo "新生代大小:   ${NEW_SIZE}"
    echo "元空间大小:   ${METASPACE_SIZE}"
    echo "GC暂停目标:   ${PAUSE_TARGET}ms"
    echo "PLI缓存限制:  ${PLI_CACHE_MB}MB ($(echo "scale=1; ${PLI_CACHE_MB}/1024" | bc)GB)"
    echo "PLI清理阈值:  ${PLI_CLEANUP_MB}MB"
    echo "============================================================"
    echo
}

# 运行实验
run_experiment() {
    log_info "启动PLI优化实验..."

    # 检查是否使用-cp模式（兼容原始脚本）
    if [ "$USE_CP_MODE" = "true" ]; then
        # 使用-cp和主类模式（兼容原始run_experiments.sh）
        JAVA_CMD=(
            "java"
            "${JVM_ARGS[@]}"
            "-cp" "$EXPERIMENT_JAR"
            "experiment.ExperimentRunner"
            "$@"
        )
    else
        # 使用-jar模式
        JAVA_CMD=(
            "java"
            "${JVM_ARGS[@]}"
            "-jar" "$EXPERIMENT_JAR"
            "$@"
        )
    fi

    # 输出完整命令（调试用）
    if [ "${DEBUG:-false}" = "true" ]; then
        log_info "执行命令:"
        printf '%s ' "${JAVA_CMD[@]}"
        echo
        echo
    fi

    # 执行实验
    exec "${JAVA_CMD[@]}"
}

# 显示帮助信息
show_help() {
    cat << EOF
PLI优化实验启动脚本

用法: $0 [选项] [实验参数]

选项:
  -h, --help          显示此帮助信息
  -d, --debug         启用调试模式
  -c, --config FILE   指定配置文件路径
  --dry-run          仅显示配置，不运行实验
  --force-memory MB   强制设置堆内存大小
  --cp-mode          使用-cp模式运行（兼容原始脚本）
  --preset-94gb      使用94GB服务器预设配置

示例:
  $0                                    # 使用自动配置运行实验
  $0 --debug                           # 启用调试模式
  $0 --force-memory 72000              # 强制使用72GB堆内存
  $0 --config custom.properties        # 使用自定义配置文件
  $0 --dry-run                         # 仅显示配置信息
  $0 --preset-94gb                     # 使用94GB服务器预设
  $0 --cp-mode --dataset "data/0" --results-file "result/result0728.csv"  # 兼容原始脚本

实验参数会直接传递给ExperimentRunner，支持所有原始参数:
  --dataset DIR              数据集目录
  --results-file FILE        结果文件路径
  --sampling-mode MODE       采样模式 (ALL/NO_SAMPLING/RANDOM/FOCUSED/NEYMAN)
  --run-mode MODE           运行模式 (APPEND/OVERWRITE)
  --run-tane BOOL           是否运行TANE算法
  --timeout MINUTES         超时时间（分钟）
  --max-error VALUE         最大错误率
  --sample-param VALUE      采样参数
  --seed VALUE              随机种子

EOF
}

# 主函数
main() {
    # 解析命令行参数
    DRY_RUN=false
    DEBUG=false
    FORCE_MEMORY=""
    USE_CP_MODE=false
    PRESET_94GB=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -d|--debug)
                DEBUG=true
                shift
                ;;
            -c|--config)
                CONFIG_FILE="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --force-memory)
                FORCE_MEMORY="$2"
                shift 2
                ;;
            --cp-mode)
                USE_CP_MODE=true
                shift
                ;;
            --preset-94gb)
                PRESET_94GB=true
                FORCE_MEMORY="81920"  # 强制使用80GB堆内存
                shift
                ;;
            *)
                # 其他参数传递给实验程序
                break
                ;;
        esac
    done
    
    # 执行初始化步骤
    log_info "初始化PLI优化实验环境..."
    
    check_dependencies
    detect_system_memory
    
    # 如果指定了强制内存大小
    if [ -n "$FORCE_MEMORY" ]; then
        AVAILABLE_MEMORY_MB=$FORCE_MEMORY
        if [ "$PRESET_94GB" = "true" ]; then
            log_info "使用94GB服务器预设配置: ${FORCE_MEMORY}MB堆内存"
        else
            log_info "使用强制指定的内存大小: ${FORCE_MEMORY}MB"
        fi
    fi
    
    calculate_jvm_params
    build_jvm_args
    show_configuration
    
    if [ "$DRY_RUN" = "true" ]; then
        log_info "干运行模式，不执行实验"
        log_info "JVM参数: ${JVM_ARGS[*]}"
        exit 0
    fi
    
    run_experiment "$@"
}

# 执行主函数
main "$@"
