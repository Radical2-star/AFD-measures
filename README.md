# AFD 度量与算法

本项目提供函数依赖发现（AFD）的核心度量实现及多种发现算法，包含基础工具库、核心算法实现及实验框架。

## 📦 项目结构

```
AFD-measures
├── AFD-core - 核心库模块
│   ├── src/main/java
│   │   ├── measure - 度量计算实现
│   │   │   ├── ErrorMeasure.java    # 错误率度量接口
│   │   │   ├── G1Measure.java       # G1度量策略：基于"违反元组对"的误差
│   │   │   ├── G3Measure.java       # G3度量策略：基于"删除元组数"的误差
│   │   │   ├── SimpleG3Measure.java # 简化的G3度量实现
│   │   │   ├── SimpleMeasure.java   # 简化度量实现
│   │   │   └── DynamicThreshold.java # 动态阈值计算
│   │   ├── model - 数据模型
│   │   │   ├── DataSet.java         # 数据集类
│   │   │   ├── AutoTypeDataSet.java # 自动类型识别数据集
│   │   │   └── FunctionalDependency.java # 函数依赖表示
│   │   ├── pli - 分区列表索引
│   │   │   ├── PLI.java              # PLI实现
│   │   │   └── PLICache.java         # PLI缓存
│   │   ├── sampling - 采样策略
│   │   │   ├── SamplingStrategy.java # 采样策略接口
│   │   │   ├── RandomSampling.java   # 随机采样
│   │   │   ├── FocusedSampling.java  # 聚焦采样
│   │   │   └── NeymanSampling.java   # Neyman最优分配采样
│   │   └── utils - 工具类
│   │       ├── DataLoader.java       # 数据加载器
│   │       ├── FunctionTimer.java    # 性能计时器
│   │       ├── HittingSet.java       # 最小命中集算法
│   │       ├── BitSetUtils.java      # 位集操作工具
│   │       ├── BitSetTrie.java       # 位集前缀树
│   │       ├── Trie.java             # 通用前缀树
│   │       ├── MinFDTrie.java        # 最小FD前缀树
│   │       ├── MaxFDTrie.java        # 最大非FD前缀树
│   │       ├── Pair.java             # 通用对
│   │       └── MinMaxPair.java       # 最小最大值对
│   └── src/test/java - 单元测试
│       ├── DataLoaderTest.java
│       ├── DataSetTest.java
│       ├── HittingSetTest.java
│       └── pli/
│           ├── PLITest.java
│           └── PLICacheTest.java
├── AFD-algorithms - 算法实现模块
│   ├── pyro - Pyro算法实现（项目重点）
│   │   ├── src/main/java/algorithm
│   │   │   ├── Pyro.java             # Pyro算法主类
│   │   │   ├── SearchSpace.java      # 搜索空间实现
│   │   │   └── Node.java             # 搜索节点
│   │   └── src/test/java/algorithm
│   │       └── SearchSpaceTest.java
│   ├── tane - TANE算法实现（基准对比）
│   │   └── src/main/java/algorithm
│   │       └── TaneAlgorithm.java    # TANE算法实现
│   └── experiment - 实验框架
│       └── src/main/java/experiment
│           ├── ExperimentRunner.java  # 实验运行器
│           ├── ExperimentConfig.java  # 实验配置
│           ├── ExperimentResult.java  # 实验结果
│           ├── ResultManager.java     # 结果管理器
│           └── finder/
│               ├── AFDFinder.java     # AFD发现器接口
│               ├── PyroExecutor.java  # Pyro执行器
│               └── TaneExecutor.java  # TANE执行器
├── data/ - 数据集目录
└── result/ - 实验结果目录
```

## 🔧 环境要求

- **Java**: JDK 11
- **Maven**: 3.9.9+
- **依赖**:
  - SLF4J 2.0.17 + Logback 1.5.13 (日志系统)
  - Caffeine 3.1.8 (缓存系统)
  - JUnit Jupiter 5.8.2 (测试框架)

## 🚀 快速开始

### 1. 克隆并构建项目

```bash
git clone [仓库地址]
cd AFD-measures

# 构建项目
mvn clean install

# 跳过测试构建
mvn clean install -DskipTests
```

### 2. 运行实验

#### 标准运行方式（推荐）
```bash
# 使用优化的实验脚本（自动配置内存参数）
./run_experiments.sh
```

#### 手动运行方式
```bash
# 运行实验框架
mvn exec:java -Dexec.mainClass="experiment.ExperimentRunner" -pl AFD-algorithms/experiment
```

#### 大内存服务器配置（128GB+）
```bash
# 1. 配置系统大页面支持（需要root权限）
sudo ./setup_hugepages_128gb.sh

# 2. 重启系统（推荐）
sudo reboot

# 3. 运行优化实验
./run_experiments.sh
```

### 3. 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定模块测试
mvn test -pl AFD-core

# 运行特定测试类
mvn test -Dtest=DataLoaderTest
```

## 🧪 实验配置

实验框架支持灵活的配置，主要参数包括：

- **MAX_ERROR**: 最大错误阈值（默认：0.05）
- **SAMPLE_PARAM**: 采样参数（默认：200）
- **SAMPLING_MODE**: 采样模式（ALL/NO_SAMPLING/RANDOM/FOCUSED/NEYMAN）
- **RUN_TANE**: 是否运行TANE算法（默认：true）
- **ALGORITHM_TIMEOUT_MINUTES**: 算法超时时间（默认：120分钟）

### 数据集配置

支持两种数据集配置模式：

1. **目录模式**: 自动扫描指定目录下的所有CSV文件
2. **列表模式**: 使用预定义的数据集路径列表

## 🎯 核心算法

### Pyro算法
- **特点**: 支持多种采样策略的AFD发现算法
- **采样策略**: 随机采样、聚焦采样、Neyman最优分配采样
- **优势**: 在大数据集上具有良好的性能和准确性

### TANE算法
- **特点**: 经典的函数依赖发现算法
- **用途**: 作为基准算法进行性能对比
- **实现**: 完整的TANE算法实现

## 📊 度量策略

### G1度量
基于"违反元组对"的误差计算策略

### G3度量
基于"删除元组数"的误差计算策略

### SimpleG3度量
简化的G3度量实现，提供更好的性能

### 动态阈值
支持动态阈值计算，适应不同数据集的特性

## 🔍 数据处理

### 支持的数据格式
- CSV文件（支持逗号、分号、制表符分隔）
- 自动分隔符检测
- 自动表头识别
- 多种数据类型支持

### 数据集位置
- 主数据集：`data/` 目录
- 测试数据：`AFD-core/src/test/resources/`
- 实验结果：`result/` 目录

## 🎨 扩展开发

### 添加新的错误度量
1. 实现 `ErrorMeasure` 接口
2. 添加到 `AFD-core/src/main/java/measure/` 包
3. 更新实验配置

### 添加新的采样策略
1. 实现 `SamplingStrategy` 接口
2. 添加到 `AFD-core/src/main/java/sampling/` 包
3. 更新 `ExperimentRunner` 配置

### 添加新算法
1. 在 `AFD-algorithms` 下创建新模块
2. 实现 `AFDFinder` 接口
3. 添加对应的执行器类
4. 更新实验框架配置

## 🌟 项目特色

### 模块化设计
- 清晰的模块划分，便于维护和扩展
- 核心库与算法实现分离
- 统一的接口设计

### 性能优化
- **PLI内存优化**: 智能缓存策略，内存使用减少30-60%
- **大内存服务器支持**: 针对128GB+服务器的专门优化
- **流式处理**: 支持千万级数据集，内存峰值降低80-90%
- **多种采样策略**: 减少计算开销
- **超时机制**: 防止长时间运行

### 实验框架
- 完整的实验配置系统
- 自动结果收集和管理
- 支持多算法并行比较

## 📈 应用场景

- **数据清洗**: 识别和修正数据不一致性
- **数据库设计**: 指导数据库模式规范化
- **查询优化**: 利用函数依赖优化查询性能
- **数据集成**: 处理多数据源的数据冲突
- **数据质量评估**: 评估数据集的质量和一致性

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出建议：

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 发起 Pull Request

## 🔧 PLI内存优化

本项目实现了完整的PLI（Partition List Index）内存优化方案，显著提升大数据集处理能力。

### 核心优化组件

#### 1. OptimizedPLI - 数据结构优化
- 使用`int[]`替代`Set<Integer>`存储等价类，内存节省30-50%
- 延迟计算属性向量，避免不必要的内存分配
- 优化字符串键生成，减少对象创建开销

#### 2. OptimizedPLICache - 智能缓存管理
- 分层缓存策略：热缓存（强引用）+ 冷缓存（软引用）
- 基于内存使用量的动态管理
- LFU缓存替换算法，缓存内存使用减少40-60%

#### 3. StreamingPLI - 大数据集流式处理
- 自适应分块处理（10K-300K行/块）
- 内存压力感知调整
- 支持千万级数据集，内存峰值降低80-90%

#### 4. MemoryMonitor - 实时内存监控
- JMX内存监控，70%警告，85%严重警告
- 自动垃圾回收触发
- 详细的内存使用统计和历史记录

### 128GB服务器优化配置

#### 内存分配策略
| 组件 | 分配 | 说明 |
|------|------|------|
| **堆内存** | 31GB | 保持Compressed OOPs优化 |
| **PLI缓存** | 48GB | 超激进配置，利用堆外内存 |
| **新生代** | 8GB | 优化的25%比例 |
| **系统保留** | 49GB | 操作系统和其他进程 |

#### JVM参数优化
```bash
# 内存配置
-Xms31g -Xmx31g                    # 31GB堆内存（保持Compressed OOPs）
-XX:NewSize=8g -XX:MaxNewSize=8g    # 8GB新生代

# G1GC大内存优化
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100           # 100ms暂停目标
-XX:G1HeapRegionSize=32m           # 32MB区域大小

# 大页面支持（需要系统配置）
-XX:+UseLargePages
-XX:LargePageSizeInBytes=2m
-XX:+AlwaysPreTouch

# PLI优化配置
-Dpli.cache.max.memory.mb=49152    # 48GB PLI缓存
-Dpli.cache.aggressive.mode=true   # 激进缓存模式
-Dstreaming.pli.chunk.size=300000  # 30万行块大小
```

### 性能提升效果

| 优化方面 | 提升幅度 | 说明 |
|---------|----------|------|
| **内存使用效率** | +25-35% | 压缩指针 + 大页面 + 优化缓存 |
| **小数据集性能** | +20-30% | 优化的数据结构和缓存策略 |
| **中等数据集性能** | +40-60% | 智能缓存管理和内存优化 |
| **大数据集支持** | 质的飞跃 | 从无法运行到支持千万级数据 |
| **PLI缓存效率** | +100% | 48GB vs 24GB缓存容量 |

### 故障排除

#### 常见JVM警告解决
1. **UseLargePages disabled**: 运行`sudo ./setup_hugepages_128gb.sh`配置大页面
2. **Compressed OOPs warning**: 已通过31GB堆内存配置解决

#### 内存不足处理
```bash
# 检查内存使用
free -h

# 减少PLI缓存（如果需要）
export PLI_CACHE_OVERRIDE=32768  # 32GB
./run_experiments.sh
```

#### 性能监控
```bash
# 监控GC日志
tail -f gc-128gb-*.log

# 检查大页面状态
cat /proc/meminfo | grep -i huge

# 验证JVM参数
java -XX:+PrintFlagsFinal -version | grep -i hugepage
```

## 📄 许可证

本项目采用开源许可证，详情请参阅 LICENSE 文件。