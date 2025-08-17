#!/bin/bash

# AFD-measures 顶层协调器
# 职责：定义和管理整个实验流程，协调多个实验任务的执行
# 设计原则：实验策略定义、编排逻辑、调用底层执行器

# 如果任何命令执行失败，立即退出脚本
set -e

# ============================================================
#                    配置和参数定义
# ============================================================

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[协调器-INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[协调器-SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[协调器-WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[协调器-ERROR]${NC} $1"
}

log_progress() {
    echo -e "${CYAN}[协调器-PROGRESS]${NC} $1"
}

# ============================================================
#                    CSV文件处理辅助函数
# ============================================================

# 检测CSV文件的分隔符
detect_csv_delimiter() {
    local file_path="$1"
    if [[ ! -f "$file_path" ]]; then
        echo ","
        return
    fi
    
    # 读取第一行
    local first_line
    first_line=$(head -n 1 "$file_path" 2>/dev/null)
    if [[ -z "$first_line" ]]; then
        echo ","
        return
    fi
    
    # 计算各种分隔符的出现次数
    local comma_count semicolon_count tab_count
    comma_count=$(echo "$first_line" | tr -cd ',' | wc -c)
    semicolon_count=$(echo "$first_line" | tr -cd ';' | wc -c)
    tab_count=$(echo "$first_line" | tr -cd '\t' | wc -c)
    
    # 返回出现次数最多的分隔符
    if [[ $semicolon_count -gt $comma_count && $semicolon_count -gt $tab_count ]]; then
        echo ";"
    elif [[ $tab_count -gt $comma_count && $tab_count -gt $semicolon_count ]]; then
        echo -e "\t"
    else
        echo ","
    fi
}

# 获取CSV文件的列数
get_csv_column_count() {
    local file_path="$1"
    if [[ ! -f "$file_path" ]]; then
        echo "0"
        return
    fi
    
    local delimiter
    delimiter=$(detect_csv_delimiter "$file_path")
    
    local first_line
    first_line=$(head -n 1 "$file_path" 2>/dev/null)
    if [[ -z "$first_line" ]]; then
        echo "0"
        return
    fi
    
    # 根据分隔符计算列数
    if [[ "$delimiter" == $'\t' ]]; then
        echo "$first_line" | awk -F'\t' '{print NF}'
    elif [[ "$delimiter" == ";" ]]; then
        echo "$first_line" | awk -F';' '{print NF}'
    else
        echo "$first_line" | awk -F',' '{print NF}'
    fi
}

# 获取CSV文件的行数（不包括表头）
get_csv_row_count() {
    local file_path="$1"
    if [[ ! -f "$file_path" ]]; then
        echo "0"
        return
    fi
    
    # 总行数减去表头行
    local total_lines
    total_lines=$(wc -l < "$file_path" 2>/dev/null || echo "0")
    echo $((total_lines - 1))
}

# 按列数排序CSV文件数组
sort_csv_files_by_columns() {
    local files=("$@")
    local file_info=()
    
    # 收集文件信息：列数:文件路径
    for file in "${files[@]}"; do
        local columns
        columns=$(get_csv_column_count "$file")
        file_info+=("$columns:$file")
    done
    
    # 按列数排序并输出文件路径
    printf '%s\n' "${file_info[@]}" | sort -t: -k1,1n | cut -d: -f2-
}

# 显示帮助信息
show_help() {
    echo "AFD-measures 顶层协调器"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --mode <MODE>                实验模式:"
    echo "                                 single-dataset: 单数据集多采样模式"
    echo "                                 multi-dataset: 多数据集多采样模式"
    echo "                                 custom: 自定义实验列表"
    echo "  --dataset-path <PATH>        单数据集模式：指定数据集文件路径"
    echo "  --datasets-dir <DIR>         多数据集模式：数据集目录路径"
    echo "  --output-base-dir <DIR>      基础输出目录（默认：results）"
    echo "  --shared-results             所有实验共享一个CSV结果文件"
    echo "  --enable-profiling           启用性能分析（生成火焰图）"
    echo "  --timeout <MINUTES>          算法超时时间（默认：120分钟）"
    echo "  --run-tane <true|false>      是否运行TANE算法（默认：true）"
    echo "  --max-error <VALUE>          最大错误阈值（默认：0.05）"
    echo "  --heap-size <SIZE>           JVM堆内存大小（默认：64g）"
    echo "  --dry-run                    干运行模式（显示将要执行的命令但不执行）"
    echo "  --help, -h                   显示此帮助信息"
    echo ""
    echo "实验模式说明:"
    echo "  single-dataset: 对单个数据集运行所有采样模式"
    echo "  multi-dataset:  对目录下所有数据集运行所有采样模式"
    echo "  custom:         根据脚本内定义的自定义实验列表运行"
    echo ""
    echo "示例:"
    echo "  # 单数据集模式"
    echo "  $0 --mode single-dataset --dataset-path data/test.csv"
    echo ""
    echo "  # 多数据集模式"
    echo "  $0 --mode multi-dataset --datasets-dir data/int/"
    echo ""
    echo "  # 启用性能分析的自定义模式"
    echo "  $0 --mode custom --enable-profiling"
    echo ""
}

# 默认配置
MODE=""
DATASET_PATH=""
DATASETS_DIR=""
OUTPUT_BASE_DIR="results"
SHARED_RESULTS=false
ENABLE_PROFILING=false
TIMEOUT="120"
RUN_TANE="true"
MAX_ERROR="0.05"
HEAP_SIZE="64g"
DRY_RUN=false

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --mode)
            MODE="$2"
            shift 2
            ;;
        --dataset-path)
            DATASET_PATH="$2"
            shift 2
            ;;
        --datasets-dir)
            DATASETS_DIR="$2"
            shift 2
            ;;
        --output-base-dir)
            OUTPUT_BASE_DIR="$2"
            shift 2
            ;;
        --shared-results)
            SHARED_RESULTS=true
            shift
            ;;
        --enable-profiling)
            ENABLE_PROFILING=true
            shift
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --run-tane)
            RUN_TANE="$2"
            shift 2
            ;;
        --max-error)
            MAX_ERROR="$2"
            shift 2
            ;;
        --heap-size)
            HEAP_SIZE="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
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
#                    实验策略定义
# ============================================================

# Pyro采样模式策略
SAMPLING_MODES=("NO_SAMPLING" "RANDOM" "FOCUSED" "NEYMAN")

# 自定义实验列表（用于custom模式）
# 格式：数据集路径:采样模式:特殊配置
CUSTOM_EXPERIMENTS=(
    "data/int/Fuel-22K-6.csv:NO_SAMPLING:default"
    "data/int/Fuel-22K-6.csv:RANDOM:default"
    "data/int/Fuel-22K-6.csv:FOCUSED:default"
    "data/int/Fuel-22K-6.csv:NEYMAN:default"
)

# 固定配置
JAR_FILE="AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar"
MAIN_CLASS="experiment.ExperimentRunner"
EXECUTOR_SCRIPT="./executor.sh"

# ============================================================
#                    参数验证和初始化
# ============================================================

# 验证模式参数
if [[ -z "$MODE" ]]; then
    echo "错误: 必须指定 --mode 参数"
    echo "可用模式: single-dataset, multi-dataset, custom"
    echo "使用 --help 查看详细说明"
    exit 1
fi

if [[ "$MODE" != "single-dataset" && "$MODE" != "multi-dataset" && "$MODE" != "custom" ]]; then
    echo "错误: 无效的模式: $MODE"
    echo "可用模式: single-dataset, multi-dataset, custom"
    exit 1
fi

# 验证模式特定参数
case "$MODE" in
    "single-dataset")
        if [[ -z "$DATASET_PATH" ]]; then
            echo "错误: single-dataset模式需要 --dataset-path 参数"
            exit 1
        fi
        if [[ ! -f "$DATASET_PATH" ]]; then
            echo "错误: 数据集文件不存在: $DATASET_PATH"
            exit 1
        fi
        ;;
    "multi-dataset")
        if [[ -z "$DATASETS_DIR" ]]; then
            echo "错误: multi-dataset模式需要 --datasets-dir 参数"
            exit 1
        fi
        if [[ ! -d "$DATASETS_DIR" ]]; then
            echo "错误: 数据集目录不存在: $DATASETS_DIR"
            exit 1
        fi
        ;;
    "custom")
        # custom模式使用脚本内定义的实验列表
        ;;
esac

# 检查executor.sh是否存在
if [[ ! -f "$EXECUTOR_SCRIPT" ]]; then
    log_error "未找到executor.sh: $EXECUTOR_SCRIPT"
    log_error "请确保executor.sh在当前目录下"
    exit 1
fi

# 确保executor.sh可执行
chmod +x "$EXECUTOR_SCRIPT"

# 创建基础输出目录
mkdir -p "$OUTPUT_BASE_DIR"

# ============================================================
#                    实验列表生成
# ============================================================

# 根据模式生成实验列表
EXPERIMENTS=()

case "$MODE" in
    "single-dataset")
        log_info "单数据集模式: $DATASET_PATH"
        for sampling_mode in "${SAMPLING_MODES[@]}"; do
            EXPERIMENTS+=("${DATASET_PATH}:${sampling_mode}:default")
        done
        ;;
    "multi-dataset")
        log_info "多数据集模式: $DATASETS_DIR"
        
        # 查找所有CSV文件并按列数排序
        log_info "正在扫描CSV文件并按列数排序..."
        
        # 首先收集所有CSV文件
        CSV_FILES=()
        while IFS= read -r -d '' file; do
            CSV_FILES+=("$file")
        done < <(find "$DATASETS_DIR" -name "*.csv" -type f -print0)
        
        if [[ ${#CSV_FILES[@]} -eq 0 ]]; then
            log_error "在目录 $DATASETS_DIR 中未找到CSV文件"
            exit 1
        fi
        
        # 按列数排序CSV文件
        SORTED_CSV_FILES=($(sort_csv_files_by_columns "${CSV_FILES[@]}"))
        
        # 显示排序后的文件信息
        log_info "发现 ${#SORTED_CSV_FILES[@]} 个CSV文件（按列数从少到多排序）:"
        for file in "${SORTED_CSV_FILES[@]}"; do
            columns=$(get_csv_column_count "$file")
            rows=$(get_csv_row_count "$file")
            delimiter=$(detect_csv_delimiter "$file")
            delimiter_name="逗号"
            if [[ "$delimiter" == ";" ]]; then
                delimiter_name="分号"
            elif [[ "$delimiter" == $'\t' ]]; then
                delimiter_name="制表符"
            fi
            log_info "  $(basename "$file") (列数: $columns, 行数: $rows, 分隔符: $delimiter_name)"
        done
        
        # 生成实验列表
        for file in "${SORTED_CSV_FILES[@]}"; do
            for sampling_mode in "${SAMPLING_MODES[@]}"; do
                EXPERIMENTS+=("${file}:${sampling_mode}:default")
            done
        done
        ;;
    "custom")
        log_info "自定义实验模式"
        EXPERIMENTS=("${CUSTOM_EXPERIMENTS[@]}")
        ;;
esac

# ============================================================
#                    实验总数计算
# ============================================================

# 计算实际的实验总数（Pyro + TANE）
PYRO_EXPERIMENT_COUNT=${#EXPERIMENTS[@]}
TANE_EXPERIMENT_COUNT=0

# 计算TANE实验数量
if [[ "$RUN_TANE" == "true" ]]; then
    # 构建数据集列表（去重）
    UNIQUE_DATASETS_FOR_COUNT=()
    for experiment in "${EXPERIMENTS[@]}"; do
        IFS=':' read -r dataset_path sampling_mode config <<< "$experiment"
        # 检查是否已存在
        found=false
        for existing_dataset in "${UNIQUE_DATASETS_FOR_COUNT[@]}"; do
            if [[ "$existing_dataset" == "$dataset_path" ]]; then
                found=true
                break
            fi
        done
        if [[ "$found" == "false" ]]; then
            UNIQUE_DATASETS_FOR_COUNT+=("$dataset_path")
        fi
    done
    TANE_EXPERIMENT_COUNT=${#UNIQUE_DATASETS_FOR_COUNT[@]}
fi

TOTAL_EXPERIMENT_COUNT=$((PYRO_EXPERIMENT_COUNT + TANE_EXPERIMENT_COUNT))

# ============================================================
#                    编译阶段
# ============================================================

TIMESTAMP=$(date +%Y%m%d_%H%M%S)

log_info "开始实验编排..."
log_info "实验模式: $MODE"
log_info "Pyro实验数量: $PYRO_EXPERIMENT_COUNT"
log_info "TANE实验数量: $TANE_EXPERIMENT_COUNT"
log_info "总实验数量: $TOTAL_EXPERIMENT_COUNT"
log_info "输出目录: $OUTPUT_BASE_DIR"
log_info "共享结果: $SHARED_RESULTS"
log_info "性能分析: $ENABLE_PROFILING"
log_info "干运行模式: $DRY_RUN"

echo
echo "============================================================"
echo "           AFD-measures 实验协调器开始"
echo "============================================================"
echo "模式: $MODE"
echo "Pyro实验: $PYRO_EXPERIMENT_COUNT"
echo "TANE实验: $TANE_EXPERIMENT_COUNT"
echo "总实验数量: $TOTAL_EXPERIMENT_COUNT"
echo "输出目录: $OUTPUT_BASE_DIR"
echo "时间戳: $TIMESTAMP"
echo "============================================================"
echo

# 编译项目（仅一次）
log_progress "步骤 1/3: 编译项目"
COMPILE_CMD=(
    "$EXECUTOR_SCRIPT"
    "--compile"
    "--jar-file" "$JAR_FILE"
    "--main-class" "$MAIN_CLASS"
    "--dataset-path" "/tmp/dummy.csv"  # 编译不需要真实数据集
    "--output-dir" "/tmp"
    "--other-files-dir" "/tmp"
    "--results-file" "dummy.csv"
    "--run-mode" "OVERWRITE"
    "--algorithm" "PYRO"
    "--sampling-mode" "NO_SAMPLING"
    "--timeout" "1"
    "--enable-profiling" "false"
)

if [[ "$DRY_RUN" == "true" ]]; then
    log_info "干运行 - 编译命令:"
    echo "  ${COMPILE_CMD[*]}"
else
    log_info "执行编译..."
    # 创建临时文件用于编译测试
    touch /tmp/dummy.csv
    if "${COMPILE_CMD[@]}" >/dev/null 2>&1 || true; then
        log_success "编译完成"
    else
        log_warn "编译可能有问题，但继续执行..."
    fi
    rm -f /tmp/dummy.csv
fi

# ============================================================
#                    实验执行阶段 - 按数据集分组
# ============================================================

log_progress "步骤 2/3: 执行实验"

# 实验计数器
CURRENT_EXPERIMENT=0
SUCCESSFUL_EXPERIMENTS=0
FAILED_EXPERIMENTS=0

# 构建数据集列表（去重）
UNIQUE_DATASETS=()
for experiment in "${EXPERIMENTS[@]}"; do
    IFS=':' read -r dataset_path sampling_mode config <<< "$experiment"
    # 检查是否已存在
    found=false
    for existing_dataset in "${UNIQUE_DATASETS[@]}"; do
        if [[ "$existing_dataset" == "$dataset_path" ]]; then
            found=true
            break
        fi
    done
    if [[ "$found" == "false" ]]; then
        UNIQUE_DATASETS+=("$dataset_path")
    fi
done

log_info "发现 ${#UNIQUE_DATASETS[@]} 个不重复数据集，将按数据集分组执行实验"

# 全局实验计数器（用于共享结果模式的写入控制）
GLOBAL_EXPERIMENT_COUNT=0

# 按数据集分组执行实验
for dataset_path in "${UNIQUE_DATASETS[@]}"; do
    dataset_basename=$(basename "$dataset_path" .csv)
    
    echo
    echo "============================================================"
    log_progress "开始处理数据集: $dataset_basename"
    echo "数据集路径: $dataset_path"
    echo "============================================================"
    
    # 生成输出路径
    if [[ "$SHARED_RESULTS" == "true" ]]; then
        # 共享结果模式：CSV文件存储在基础目录，其他文件按数据集分文件夹
        results_output_dir="$OUTPUT_BASE_DIR"
        other_files_output_dir="${OUTPUT_BASE_DIR}/${dataset_basename}"
        results_filename="shared_results_${TIMESTAMP}.csv"
    else
        # 非共享结果模式：所有文件都按数据集分文件夹
        results_output_dir="${OUTPUT_BASE_DIR}/${dataset_basename}"
        other_files_output_dir="${OUTPUT_BASE_DIR}/${dataset_basename}"
        results_filename="results_${dataset_basename}_${TIMESTAMP}.csv"
    fi
    
    # 第一步：执行所有Pyro采样策略实验
    log_info "第一阶段: 执行Pyro算法的所有采样策略"
    
    pyro_experiment_count=0
    for sampling_mode in "${SAMPLING_MODES[@]}"; do
        CURRENT_EXPERIMENT=$((CURRENT_EXPERIMENT + 1))
        GLOBAL_EXPERIMENT_COUNT=$((GLOBAL_EXPERIMENT_COUNT + 1))
        pyro_experiment_count=$((pyro_experiment_count + 1))
        
        # 确定run_mode
        if [[ "$SHARED_RESULTS" == "true" ]]; then
            # 共享结果模式：全局第一个实验OVERWRITE，后续APPEND
            if [[ $GLOBAL_EXPERIMENT_COUNT -eq 1 ]]; then
                run_mode="OVERWRITE"
            else
                run_mode="APPEND"
            fi
        else
            # 非共享结果模式：每个数据集的第一个实验OVERWRITE，后续APPEND
            if [[ $pyro_experiment_count -eq 1 ]]; then
                run_mode="OVERWRITE"
            else
                run_mode="APPEND"
            fi
        fi
        
        # 生成火焰图文件名
        flamegraph_filename=""
        if [[ "$ENABLE_PROFILING" == "true" ]]; then
            flamegraph_filename="flamegraph_${dataset_basename}_Pyro_${sampling_mode}_${TIMESTAMP}.html"
        fi
        
        # 构建Pyro实验执行命令
        EXECUTOR_CMD=(
            "$EXECUTOR_SCRIPT"
            "--jar-file" "$JAR_FILE"
            "--main-class" "$MAIN_CLASS"
            "--dataset-path" "$dataset_path"
            "--output-dir" "$results_output_dir"
            "--other-files-dir" "$other_files_output_dir"
            "--results-file" "$results_filename"
            "--run-mode" "$run_mode"
            "--algorithm" "PYRO"
            "--sampling-mode" "$sampling_mode"
            "--timeout" "$TIMEOUT"
            "--enable-profiling" "$ENABLE_PROFILING"
            "--max-error" "$MAX_ERROR"
            "--heap-size" "$HEAP_SIZE"
        )
        
        # 添加火焰图参数
        if [[ -n "$flamegraph_filename" ]]; then
            EXECUTOR_CMD+=("--flamegraph-file" "$flamegraph_filename")
        fi
        
        echo
        echo "------------------------------------------------------------"
        log_progress "实验 $CURRENT_EXPERIMENT: Pyro - $sampling_mode ($dataset_basename)"
        echo "算法: Pyro"
        echo "采样模式: $sampling_mode"
        echo "结果文件目录: $results_output_dir"
        echo "其他文件目录: $other_files_output_dir"
        echo "结果文件: $results_filename"
        if [[ -n "$flamegraph_filename" ]]; then
            echo "火焰图: $flamegraph_filename"
        fi
        echo "------------------------------------------------------------"
        
        if [[ "$DRY_RUN" == "true" ]]; then
            log_info "干运行 - 执行命令:"
            echo "  ${EXECUTOR_CMD[*]}"
            SUCCESSFUL_EXPERIMENTS=$((SUCCESSFUL_EXPERIMENTS + 1))
        else
            # 执行Pyro实验
            log_info "开始执行Pyro实验..."
            start_time=$(date)
            
            if "${EXECUTOR_CMD[@]}"; then
                end_time=$(date)
                log_success "Pyro实验完成: $dataset_basename - $sampling_mode"
                log_info "开始时间: $start_time"
                log_info "结束时间: $end_time"
                SUCCESSFUL_EXPERIMENTS=$((SUCCESSFUL_EXPERIMENTS + 1))
            else
                end_time=$(date)
                log_error "Pyro实验失败: $dataset_basename - $sampling_mode"
                log_error "开始时间: $start_time"
                log_error "结束时间: $end_time"
                FAILED_EXPERIMENTS=$((FAILED_EXPERIMENTS + 1))
                log_warn "继续执行剩余实验..."
            fi
        fi
        
        # 实验间延迟
        if [[ "$DRY_RUN" != "true" ]]; then
            sleep 2
        fi
    done
    
    # 第二步：执行TANE实验（如果开启）
    if [[ "$RUN_TANE" == "true" ]]; then
        log_info "第二阶段: 执行TANE算法"
        
        CURRENT_EXPERIMENT=$((CURRENT_EXPERIMENT + 1))
        GLOBAL_EXPERIMENT_COUNT=$((GLOBAL_EXPERIMENT_COUNT + 1))
        
        # 确定run_mode
        if [[ "$SHARED_RESULTS" == "true" ]]; then
            # 共享结果模式：全局第一个实验OVERWRITE，后续APPEND
            if [[ $GLOBAL_EXPERIMENT_COUNT -eq 1 ]]; then
                run_mode="OVERWRITE"
            else
                run_mode="APPEND"
            fi
        else
            # 非共享结果模式：TANE总是APPEND（因为Pyro实验已经先执行）
            run_mode="APPEND"
        fi
        
        # 生成火焰图文件名
        flamegraph_filename=""
        if [[ "$ENABLE_PROFILING" == "true" ]]; then
            flamegraph_filename="flamegraph_${dataset_basename}_TANE_NO_SAMPLING_${TIMESTAMP}.html"
        fi
        
        # 构建TANE实验执行命令
        EXECUTOR_CMD=(
            "$EXECUTOR_SCRIPT"
            "--jar-file" "$JAR_FILE"
            "--main-class" "$MAIN_CLASS"
            "--dataset-path" "$dataset_path"
            "--output-dir" "$results_output_dir"
            "--other-files-dir" "$other_files_output_dir"
            "--results-file" "$results_filename"
            "--run-mode" "$run_mode"
            "--algorithm" "TANE"
            "--sampling-mode" "NO_SAMPLING"
            "--timeout" "$TIMEOUT"
            "--enable-profiling" "$ENABLE_PROFILING"
            "--max-error" "$MAX_ERROR"
            "--heap-size" "$HEAP_SIZE"
        )
        
        # 添加火焰图参数
        if [[ -n "$flamegraph_filename" ]]; then
            EXECUTOR_CMD+=("--flamegraph-file" "$flamegraph_filename")
        fi
        
        echo
        echo "------------------------------------------------------------"
        log_progress "实验 $CURRENT_EXPERIMENT: TANE - NO_SAMPLING ($dataset_basename)"
        echo "算法: TANE"
        echo "采样模式: NO_SAMPLING"
        echo "结果文件目录: $results_output_dir"
        echo "其他文件目录: $other_files_output_dir"
        echo "结果文件: $results_filename"
        if [[ -n "$flamegraph_filename" ]]; then
            echo "火焰图: $flamegraph_filename"
        fi
        echo "------------------------------------------------------------"
        
        if [[ "$DRY_RUN" == "true" ]]; then
            log_info "干运行 - 执行命令:"
            echo "  ${EXECUTOR_CMD[*]}"
            SUCCESSFUL_EXPERIMENTS=$((SUCCESSFUL_EXPERIMENTS + 1))
        else
            # 执行TANE实验
            log_info "开始执行TANE实验..."
            start_time=$(date)
            
            if "${EXECUTOR_CMD[@]}"; then
                end_time=$(date)
                log_success "TANE实验完成: $dataset_basename"
                log_info "开始时间: $start_time"
                log_info "结束时间: $end_time"
                SUCCESSFUL_EXPERIMENTS=$((SUCCESSFUL_EXPERIMENTS + 1))
            else
                end_time=$(date)
                log_error "TANE实验失败: $dataset_basename"
                log_error "开始时间: $start_time"
                log_error "结束时间: $end_time"
                FAILED_EXPERIMENTS=$((FAILED_EXPERIMENTS + 1))
                log_warn "继续执行剩余实验..."
            fi
        fi
        
        # 实验间延迟
        if [[ "$DRY_RUN" != "true" ]]; then
            sleep 2
        fi
    else
        log_info "TANE实验已禁用，跳过"
    fi
    
    log_success "数据集 $dataset_basename 的所有实验已完成"
done

# ============================================================
#                    结果统计和验证
# ============================================================

log_progress "步骤 3/3: 结果验证"

echo
echo "============================================================"
echo "           实验协调器完成 - 结果统计"
echo "============================================================"

echo "总实验数量: $TOTAL_EXPERIMENT_COUNT"
echo "成功实验: $SUCCESSFUL_EXPERIMENTS"
echo "失败实验: $FAILED_EXPERIMENTS"
echo "成功率: $(echo "scale=2; $SUCCESSFUL_EXPERIMENTS * 100 / $TOTAL_EXPERIMENT_COUNT" | bc)%"

if [[ "$DRY_RUN" == "true" ]]; then
    log_info "干运行完成 - 未实际执行实验"
    exit 0
fi

# 验证输出文件
log_info "验证输出文件..."

if [[ "$SHARED_RESULTS" == "true" ]]; then
    # 验证共享结果文件
    shared_result_file="${OUTPUT_BASE_DIR}/shared_results_${TIMESTAMP}.csv"
    if [[ -f "$shared_result_file" ]]; then
        file_size=$(du -h "$shared_result_file" | cut -f1)
        line_count=$(wc -l < "$shared_result_file")
        log_success "共享结果文件: $shared_result_file ($file_size, $line_count 行)"
    else
        log_warn "共享结果文件未找到: $shared_result_file"
    fi
else
    # 验证分散的结果文件
    result_files_count=0
    while IFS= read -r -d '' file; do
        result_files_count=$((result_files_count + 1))
        file_size=$(du -h "$file" | cut -f1)
        log_success "结果文件: $file ($file_size)"
    done < <(find "$OUTPUT_BASE_DIR" -name "results_*_${TIMESTAMP}.csv" -type f -print0)
    
    if [[ $result_files_count -eq 0 ]]; then
        log_warn "未找到结果文件"
    fi
fi

# 验证火焰图文件
if [[ "$ENABLE_PROFILING" == "true" ]]; then
    flamegraph_count=0
    while IFS= read -r -d '' file; do
        flamegraph_count=$((flamegraph_count + 1))
        file_size=$(du -h "$file" | cut -f1)
        log_success "火焰图文件: $file ($file_size)"
    done < <(find "$OUTPUT_BASE_DIR" -name "flamegraph_*_${TIMESTAMP}.html" -type f -print0)
    
    log_info "火焰图文件总数: $flamegraph_count"
fi

# 最终状态
echo
if [[ $FAILED_EXPERIMENTS -eq 0 ]]; then
    log_success "所有实验成功完成! 🎉"
elif [[ $SUCCESSFUL_EXPERIMENTS -gt 0 ]]; then
    log_warn "部分实验完成 ($SUCCESSFUL_EXPERIMENTS 成功, $FAILED_EXPERIMENTS 失败)"
else
    log_error "所有实验都失败了 ❌"
    exit 1
fi

echo
echo "============================================================"
echo "协调器执行完成"
echo "============================================================"