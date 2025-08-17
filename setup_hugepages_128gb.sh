#!/bin/bash

# 128GB服务器大页面配置脚本
# 此脚本配置系统大页面支持以优化JVM性能

set -e

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

# 检查是否为root用户
check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "此脚本需要root权限运行"
        echo "请使用: sudo $0"
        exit 1
    fi
}

# 显示当前内存和大页面状态
show_current_status() {
    echo
    echo "============================================================"
    echo "                   当前系统状态"
    echo "============================================================"
    
    # 显示总内存
    TOTAL_MEMORY_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    TOTAL_MEMORY_GB=$(echo "scale=1; $TOTAL_MEMORY_KB / 1024 / 1024" | bc)
    echo "总内存: ${TOTAL_MEMORY_GB}GB"
    
    # 显示当前大页面状态
    echo
    echo "当前大页面配置:"
    grep -E "HugePages|Hugepagesize" /proc/meminfo
    
    echo "============================================================"
    echo
}

# 配置大页面
configure_hugepages() {
    log_info "开始配置大页面..."
    
    # 计算需要的大页面数量
    # 为31GB堆内存 + 一些额外的大页面，总共分配20GB大页面
    # 2MB大页面：20GB / 2MB = 10240个
    HUGEPAGES_COUNT=10240
    HUGEPAGES_SIZE_GB=20
    
    log_info "配置 ${HUGEPAGES_COUNT} 个大页面 (总计 ${HUGEPAGES_SIZE_GB}GB)"
    
    # 设置大页面数量
    echo $HUGEPAGES_COUNT > /proc/sys/vm/nr_hugepages
    
    # 验证设置
    ACTUAL_HUGEPAGES=$(cat /proc/sys/vm/nr_hugepages)
    if [ "$ACTUAL_HUGEPAGES" -eq "$HUGEPAGES_COUNT" ]; then
        log_success "大页面配置成功: ${ACTUAL_HUGEPAGES} 个"
    else
        log_warn "大页面配置部分成功: 请求 ${HUGEPAGES_COUNT} 个，实际分配 ${ACTUAL_HUGEPAGES} 个"
        log_warn "这可能是由于内存碎片导致的，建议重启后重新配置"
    fi
}

# 永久配置大页面
make_permanent() {
    log_info "配置永久大页面设置..."
    
    # 备份原始配置
    if [ -f /etc/sysctl.conf ]; then
        cp /etc/sysctl.conf /etc/sysctl.conf.backup.$(date +%Y%m%d-%H%M%S)
        log_info "已备份原始 /etc/sysctl.conf"
    fi
    
    # 移除旧的大页面配置（如果存在）
    sed -i '/vm.nr_hugepages/d' /etc/sysctl.conf
    
    # 添加新的大页面配置
    echo "# 128GB服务器大页面配置 - $(date)" >> /etc/sysctl.conf
    echo "vm.nr_hugepages=10240" >> /etc/sysctl.conf
    echo "vm.hugetlb_shm_group=0" >> /etc/sysctl.conf
    
    log_success "永久配置已添加到 /etc/sysctl.conf"
    log_info "重启后配置将自动生效"
}

# 优化内存相关参数
optimize_memory_settings() {
    log_info "优化内存相关系统参数..."
    
    # 添加其他内存优化参数
    cat >> /etc/sysctl.conf << EOF

# 128GB服务器内存优化参数 - $(date)
# 减少swap使用
vm.swappiness=1

# 优化脏页回写
vm.dirty_ratio=15
vm.dirty_background_ratio=5

# 优化内存回收
vm.vfs_cache_pressure=50

# 优化网络缓冲区（如果需要）
net.core.rmem_max=134217728
net.core.wmem_max=134217728
EOF

    log_success "内存优化参数已添加"
}

# 验证配置
verify_configuration() {
    echo
    echo "============================================================"
    echo "                   配置验证"
    echo "============================================================"
    
    # 显示大页面状态
    echo "大页面配置:"
    grep -E "HugePages|Hugepagesize" /proc/meminfo
    
    echo
    echo "相关系统参数:"
    sysctl vm.nr_hugepages
    sysctl vm.swappiness
    
    echo
    echo "内存使用情况:"
    free -h
    
    echo "============================================================"
}

# 创建测试脚本
create_test_script() {
    log_info "创建大页面测试脚本..."
    
    cat > /usr/local/bin/test_hugepages.sh << 'EOF'
#!/bin/bash

echo "=== 大页面状态检查 ==="
echo "配置的大页面:"
grep -E "HugePages|Hugepagesize" /proc/meminfo

echo
echo "=== 内存使用情况 ==="
free -h

echo
echo "=== 系统参数 ==="
echo "vm.nr_hugepages = $(sysctl -n vm.nr_hugepages)"
echo "vm.swappiness = $(sysctl -n vm.swappiness)"

echo
echo "=== JVM大页面测试命令 ==="
echo "java -XX:+PrintFlagsFinal -version | grep -i hugepage"
EOF

    chmod +x /usr/local/bin/test_hugepages.sh
    log_success "测试脚本已创建: /usr/local/bin/test_hugepages.sh"
}

# 显示使用说明
show_usage_instructions() {
    echo
    echo "============================================================"
    echo "                   使用说明"
    echo "============================================================"
    echo
    echo "1. 大页面配置已完成，建议重启系统以确保配置生效"
    echo
    echo "2. 重启后，运行以下命令验证配置:"
    echo "   /usr/local/bin/test_hugepages.sh"
    echo
    echo "3. 运行优化的实验脚本:"
    echo "   ./run_experiments_optimized.sh"
    echo
    echo "4. 如果遇到问题，可以运行以下命令检查:"
    echo "   cat /proc/meminfo | grep -i huge"
    echo "   dmesg | grep -i hugepage"
    echo
    echo "5. 恢复原始配置（如果需要）:"
    echo "   sudo cp /etc/sysctl.conf.backup.* /etc/sysctl.conf"
    echo "   sudo sysctl -p"
    echo
    echo "============================================================"
}

# 主函数
main() {
    echo "128GB服务器大页面配置脚本"
    echo "此脚本将配置系统大页面支持以优化JVM性能"
    echo
    
    # 检查权限
    check_root
    
    # 显示当前状态
    show_current_status
    
    # 询问用户确认
    read -p "是否继续配置大页面? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "用户取消操作"
        exit 0
    fi
    
    # 执行配置
    configure_hugepages
    make_permanent
    optimize_memory_settings
    create_test_script
    
    # 验证配置
    verify_configuration
    
    # 显示使用说明
    show_usage_instructions
    
    log_success "大页面配置完成!"
    log_warn "建议重启系统以确保所有配置生效"
}

# 执行主函数
main "$@"
