#!/bin/bash

# AFD-measures 128GB服务器优化实验启动脚本
# 此脚本是原始run_experiments.sh的优化版本，专门针对128GB大内存服务器

# 如果任何命令执行失败，立即退出脚本
set -e

# 文件名日期
DATE='0729'

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

# --- 1. 项目编译 ---
log_info "正在使用Maven编译项目..."
mvn clean package -DskipTests
log_success "项目编译完成!"

# --- 2. 实验配置 ---
# 定义JAR文件的路径
JAR_FILE="AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar"

# 检查JAR文件是否存在
if [ ! -f "$JAR_FILE" ]; then
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

# 定义主类的完整名称
MAIN_CLASS="experiment.ExperimentRunner"

# --- 3. 128GB服务器内存配置 ---
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

# 128GB服务器优化配置 - 解决Compressed OOPs警告
# 使用31GB堆内存以保持Compressed OOPs优势，配合更大的堆外缓存
HEAP_SIZE="31g"              # 31GB堆内存（保持Compressed OOPs）
NEW_SIZE="8g"                # 8GB新生代（约25%）
METASPACE_SIZE="2g"          # 2GB元空间
REGION_SIZE="32m"            # 32MB G1区域大小（适合31GB堆）
PAUSE_TARGET="100"           # 100ms GC暂停目标

# PLI缓存配置 - 超激进配置，利用剩余内存
PLI_CACHE_MB=49152           # 48GB PLI缓存（利用堆外内存）
PLI_CLEANUP_MB=40960         # 40GB 清理阈值

# 构建JVM参数
JVM_ARGS=(
    # 内存配置 - 优化的31GB堆内存配置
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
    "-XX:+UseCompressedOops"
    "-XX:+UseCompressedClassPointers"

    # G1GC配置 - 针对31GB堆优化
    "-XX:+UseG1GC"
    "-XX:MaxGCPauseMillis=${PAUSE_TARGET}"
    "-XX:G1HeapRegionSize=${REGION_SIZE}"
    "-XX:G1NewSizePercent=25"
    "-XX:G1MaxNewSizePercent=35"
    "-XX:G1MixedGCCountTarget=8"
    "-XX:G1MixedGCLiveThresholdPercent=85"
    "-XX:G1OldCSetRegionThresholdPercent=10"
    "-XX:G1ReservePercent=15"
    
    # G1GC额外优化
    "-XX:+G1UseAdaptiveIHOP"
    "-XX:G1HeapWastePercent=5"
    "-XX:G1MixedGCLiveThresholdPercent=85"

    # 监控和调试
    "-Xlog:gc*=info:file=gc-128gb-$(date +%Y%m%d-%H%M%S).log:time,uptime,level,tags:filecount=10,filesize=100M"
    
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

# --- 4. 显示配置信息 ---
echo
echo "============================================================"
echo "                128GB服务器优化实验配置"
echo "============================================================"
echo "系统内存:     ${TOTAL_MEMORY_MB}MB ($(echo "scale=1; ${TOTAL_MEMORY_MB}/1024" | bc)GB)"
echo "堆内存大小:   ${HEAP_SIZE}"
echo "新生代大小:   ${NEW_SIZE}"
echo "元空间大小:   ${METASPACE_SIZE}"
echo "GC暂停目标:   ${PAUSE_TARGET}ms"
echo "PLI缓存限制:  ${PLI_CACHE_MB}MB ($(echo "scale=1; ${PLI_CACHE_MB}/1024" | bc)GB)"
echo "PLI清理阈值:  ${PLI_CLEANUP_MB}MB"
if [ "$USE_LARGE_PAGES" = "true" ]; then
    echo "大页面支持:   已启用 (${HUGEPAGES_AVAILABLE_GB}GB可用)"
else
    echo "大页面支持:   未启用 (建议配置以提升性能)"
fi
echo "============================================================"

# --- 5. 执行实验 ---
echo
log_info "开始执行128GB服务器优化批量实验..."
echo ">>  数据集目录: data/0"
echo ">>  采样模式: ALL"
echo ">>  运行TANE: true"
echo ">>  结果文件: result/result${DATE}.csv (追加模式)"
echo ">>  超时时间: 120分钟"
echo ">>  内存配置: 31GB堆内存 + 48GB PLI缓存 (优化Compressed OOPs)"
echo "-------------------------------------------------"

# 构建完整的Java命令
JAVA_CMD=(
    "java"
    "${JVM_ARGS[@]}"
    "-cp" "$JAR_FILE"
    "$MAIN_CLASS"
    "--dataset" "data/0"
    "--results-file" "result/result${DATE}.csv"
    "--sampling-mode" "ALL"
    "--run-mode" "APPEND"
    "--run-tane" "true"
    "--timeout" "120"
    "--max-error" "0.05"
    "--sample-param" "200"
    "--seed" "114514"
)

# 显示完整命令（可选）
if [ "${DEBUG:-false}" = "true" ]; then
    log_info "执行命令:"
    printf '%s ' "${JAVA_CMD[@]}"
    echo
    echo
fi

# 执行实验
log_info "启动实验..."
"${JAVA_CMD[@]}"

# 检查执行结果
if [ $? -eq 0 ]; then
    echo
    log_success "所有实验已成功完成!"
    log_success "你可以在 result/result${DATE}.csv 文件中查看结果。"
    
    # 显示GC日志文件位置
    GC_LOG=$(ls -t gc-128gb-*.log 2>/dev/null | head -n1)
    if [ -n "$GC_LOG" ]; then
        log_info "GC日志文件: $GC_LOG"
    fi
else
    echo
    log_warn "实验执行过程中出现问题，请检查日志。"
    exit 1
fi
