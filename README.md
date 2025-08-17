# AFD 度量与算法

本项目提供函数依赖发现（AFD）的核心度量实现及多种发现算法，包含基础工具库、核心算法实现及实验框架。

## 📦 项目结构

```
AFD-measures
├── executor.sh                     # 🔧 底层执行器（单一实验任务精确执行）
├── run.sh                          # 🚀 顶层协调器（实验策略定义和编排）
├── orchestrate_experiments.sh      # 🚀 自动化实验编排脚本（旧版）
├── run_experiments.sh              # 🔧 128GB优化实验脚本（旧版，支持命令行参数）
├── setup_hugepages_128gb.sh        # 大页面配置脚本
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

#### 🚀 新架构：高度解耦的实验系统（推荐）

**顶层协调器（run.sh）- 实验策略定义**
```bash
# 单数据集多采样模式
./run.sh --mode single-dataset --dataset-path data/int/EQ-500K-12.csv

# 多数据集批量处理
./run.sh --mode multi-dataset --datasets-dir data/int/

# 自定义实验模式（脚本内预定义）
./run.sh --mode custom --enable-profiling

# 干运行模式（查看将要执行的命令）
./run.sh --mode single-dataset --dataset-path data/test.csv --dry-run

# 查看协调器帮助
./run.sh --help
```

**底层执行器（executor.sh）- 单一任务精确执行**
```bash
# 执行单个Pyro实验（通常由run.sh调用）
./executor.sh \
    --jar-file AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar \
    --main-class experiment.ExperimentRunner \
    --dataset-path data/int/EQ-500K-12.csv \
    --output-dir results/EQ-500K-12 \
    --results-file results.csv \
    --run-mode APPEND \
    --algorithm PYRO \
    --sampling-mode RANDOM \
    --timeout 120 \
    --enable-profiling true \
    --compile

# 执行单个TANE实验
./executor.sh \
    --jar-file AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar \
    --main-class experiment.ExperimentRunner \
    --dataset-path data/int/EQ-500K-12.csv \
    --output-dir results/EQ-500K-12 \
    --results-file results.csv \
    --run-mode APPEND \
    --algorithm TANE \
    --sampling-mode NO_SAMPLING \
    --timeout 120 \
    --enable-profiling true

# 查看执行器帮助
./executor.sh --help
```

#### 🎯 旧版自动化实验编排（向后兼容）
```bash
# 一键执行所有采样模式的自动化实验
./orchestrate_experiments.sh
```

#### 📊 旧版高级实验编排（向后兼容）
```bash
# 指定数据集和输出目录
./orchestrate_experiments.sh \
    --dataset-name EQ-500K-12 \
    --output-dir results \
    --run-tane true

# 查看编排脚本帮助
./orchestrate_experiments.sh --help
```

#### 🔧 旧版单独实验运行（向后兼容）
```bash
# 使用优化的实验脚本（自动配置内存参数）
./run_experiments.sh

# 命令行参数化运行
./run_experiments.sh \
    --sampling-mode RANDOM \
    --dataset-name EQ-500K-12 \
    --output-dir results \
    --run-tane true

# 查看实验脚本帮助
./run_experiments.sh --help
```

#### ⚙️ 手动运行方式
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

### 🔄 新架构：高度解耦的实验系统

#### 设计原则
- **单一职责分离**：底层执行器(`executor.sh`)专注单一实验精确执行，顶层协调器(`run.sh`)负责策略定义和编排
- **完全参数化**：`executor.sh`移除所有硬编码配置，通过命令行参数接收所有配置
- **无状态执行**：`executor.sh`无逻辑分支，接收指令→执行任务→退出
- **灵活编排**：`run.sh`实现所有循环和策略逻辑，支持多种实验模式

#### 🔧 executor.sh（底层执行器）特性
- **单一实验执行**：仅执行一次具体实验，专注单一任务
- **算法类型分离**：通过`--algorithm`参数独立选择PYRO或TANE算法
- **完全参数化**：所有配置通过命令行参数传入，无硬编码配置
- **编译控制**：`--compile`标志控制是否执行Maven编译
- **输出管理**：根据参数精确放置火焰图、GC日志、结果CSV
- **JVM优化**：自动配置128GB服务器优化参数

#### 🚀 run.sh（顶层协调器）特性
- **按数据集分组执行**：对每个数据集先执行所有Pyro采样策略，再执行TANE算法
- **算法执行顺序控制**：严格按照 Pyro(所有采样) → TANE(NO_SAMPLING) 的顺序串行执行
- **智能数据集排序**：多数据集模式自动按CSV列数从少到多排序，实现渐进式处理
- **多种运行模式**：
  - `single-dataset`: 单数据集多采样模式
  - `multi-dataset`: 多数据集批量处理（智能排序）
  - `custom`: 脚本内定义的自定义实验列表
- **智能编译**：开始前仅编译一次，后续实验跳过编译
- **动态参数生成**：自动生成时间戳文件名、输出路径
- **进度追踪**：详细的实验进度报告和结果统计

#### 📋 协调器运行模式详解

**single-dataset模式**
```bash
./run.sh --mode single-dataset --dataset-path data/int/EQ-500K-12.csv
# 对指定数据集运行所有采样模式：NO_SAMPLING, RANDOM, FOCUSED, NEYMAN
```

**multi-dataset模式**
```bash
./run.sh --mode multi-dataset --datasets-dir data/int/
# 对目录下所有CSV文件运行所有采样模式
# 自动按列数从少到多排序，便于渐进式处理
```

**custom模式**
```bash
./run.sh --mode custom --enable-profiling
# 使用脚本内CUSTOM_EXPERIMENTS数组定义的实验列表
```

#### 🎯 输出结构控制
```bash
# 共享结果文件（所有实验追加到一个CSV）
./run.sh --mode single-dataset --dataset-path data/test.csv --shared-results

# 分离结果文件（每个数据集一个目录和CSV）
./run.sh --mode multi-dataset --datasets-dir data/int/
```

#### 💡 新架构使用示例

**典型工作流**
```bash
# 1. 快速单数据集实验（最常用）
./run.sh --mode single-dataset --dataset-path data/int/EQ-500K-12.csv --enable-profiling

# 2. 批量处理多个数据集
./run.sh --mode multi-dataset --datasets-dir data/int/ --timeout 180

# 3. 高级定制：性能分析 + 共享结果
./run.sh --mode single-dataset \
    --dataset-path data/int/large-dataset.csv \
    --enable-profiling \
    --shared-results \
    --timeout 240 \
    --heap-size 96g

# 4. 干运行模式：查看将要执行的命令（调试用）
./run.sh --mode multi-dataset --datasets-dir data/test/ --dry-run
```

**高级用法：直接调用执行器**
```bash
# 编译项目（仅需一次）
./executor.sh --compile \
    --jar-file AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar \
    --main-class experiment.ExperimentRunner \
    --dataset-path /tmp/dummy.csv --output-dir /tmp \
    --results-file dummy.csv --run-mode OVERWRITE \
    --algorithm PYRO --sampling-mode NO_SAMPLING \
    --timeout 1 --enable-profiling false

# 执行Pyro实验
./executor.sh \
    --jar-file AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar \
    --main-class experiment.ExperimentRunner \
    --dataset-path data/int/EQ-500K-12.csv \
    --output-dir results/manual-test \
    --results-file manual_results.csv \
    --run-mode OVERWRITE \
    --algorithm PYRO \
    --sampling-mode FOCUSED \
    --timeout 120 \
    --enable-profiling true \
    --flamegraph-file manual_pyro_flamegraph.html

# 执行TANE实验
./executor.sh \
    --jar-file AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar \
    --main-class experiment.ExperimentRunner \
    --dataset-path data/int/EQ-500K-12.csv \
    --output-dir results/manual-test \
    --results-file manual_results.csv \
    --run-mode APPEND \
    --algorithm TANE \
    --sampling-mode NO_SAMPLING \
    --timeout 120 \
    --enable-profiling true \
    --flamegraph-file manual_tane_flamegraph.html
```

#### 🔄 新架构执行流程

**实验执行顺序（重要特性）**：

新架构实现了严格的按数据集分组的串行执行模式：

```
对于每个数据集：
  1. 执行 Pyro + NO_SAMPLING
  2. 执行 Pyro + RANDOM
  3. 执行 Pyro + FOCUSED  
  4. 执行 Pyro + NEYMAN
  5. 执行 TANE + NO_SAMPLING（如果启用）
然后继续下一个数据集...
```

**关键改进**：
- ✅ **算法彻底分离**：Pyro和TANE通过`--algorithm`参数独立执行，不再混合
- ✅ **数据集分组执行**：确保同一数据集的所有实验连续完成，避免资源竞争
- ✅ **可预测的执行顺序**：先完成所有Pyro实验，再执行TANE，便于结果分析
- ✅ **独立控制**：可以单独运行某种算法，无需绑定执行

#### 🔄 架构对比

| 特性 | 新架构（executor.sh + run.sh） | 旧版（orchestrate_experiments.sh） |
|------|-------------------------------|----------------------------------|
| **算法分离** | ✅ Pyro和TANE完全独立执行 | ❌ 通过布尔参数混合控制 |
| **执行顺序** | ✅ 按数据集分组，算法类型串行 | ❌ 混合执行，顺序不可控 |
| **数据集排序** | ✅ 自动按列数排序，渐进式处理 | ❌ 无排序功能 |
| **职责分离** | ✅ 高度解耦，单一职责 | ❌ 单体脚本，职责混合 |
| **可重用性** | ✅ executor.sh可独立使用 | ❌ 整体调用，难以复用 |
| **灵活性** | ✅ 支持多种实验模式 | ❌ 固定的4种采样模式 |
| **参数化** | ✅ 完全参数化，无硬编码 | ❌ 部分硬编码配置 |
| **扩展性** | ✅ 易于添加新实验策略 | ❌ 需修改脚本逻辑 |
| **调试能力** | ✅ 支持干运行模式 | ❌ 无干运行支持 |
| **向后兼容** | ✅ 与旧版并存 | ✅ 原有功能保持 |

### 🎯 旧版自动化实验编排（向后兼容）

#### 🚀 orchestrate_experiments.sh 特性
- **自动化执行**: 一键运行4种采样模式实验（NO_SAMPLING, RANDOM, FOCUSED, NEYMAN）
- **智能编译**: 首次运行编译项目，后续实验跳过编译提高效率
- **结构化输出**: 自动创建 `<output-dir>/<dataset-name>/` 目录结构
- **文件验收**: 自动验证输出文件（1个CSV + 4个HTML火焰图）
- **内存管理**: 每次实验独立进程，自动内存回收

#### 📋 编排脚本参数
```bash
--dataset-name <NAME>     # 数据集名称（默认：EQ-500K-12）
--output-dir <PATH>       # 基础输出目录（默认：result）
--run-tane <true|false>   # 是否运行TANE算法（默认：true）
--help, -h               # 显示帮助信息
```

#### 🎯 预期输出结构
```
result/
└── EQ-500K-12/
    ├── result_EQ-500K-12.csv              # 所有实验结果的汇总CSV
    ├── flamegraph_NO_SAMPLING_<时间戳>.html  # 无采样模式火焰图
    ├── flamegraph_RANDOM_<时间戳>.html       # 随机采样模式火焰图
    ├── flamegraph_FOCUSED_<时间戳>.html      # 聚焦采样模式火焰图
    └── flamegraph_NEYMAN_<时间戳>.html       # Neyman采样模式火焰图
```

### 精细化实验控制

#### 🔧 run_experiments.sh 增强特性
- **命令行参数化**: 支持所有核心参数的命令行覆盖
- **跳过编译选项**: `--skip-compile` 提高多次实验效率
- **动态输出路径**: 基于参数自动生成结构化目录
- **火焰图定制**: 支持自定义火焰图文件名和路径
- **性能分析优化**: 默认禁用JFR内存分析，专注CPU火焰图

#### 📋 实验脚本参数
```bash
--sampling-mode <MODE>    # 采样模式（NO_SAMPLING/RANDOM/FOCUSED/NEYMAN）
--run-tane <true|false>   # 是否运行TANE算法
--dataset-name <NAME>     # 数据集名称，用于生成输出路径
--output-dir <PATH>       # 基础输出目录路径
--flamegraph-file <FILE>  # 火焰图输出文件路径
--skip-compile           # 跳过Maven编译步骤
--help, -h               # 显示帮助信息
```

### 传统实验参数

实验框架支持的核心配置参数：

- **MAX_ERROR**: 最大错误阈值（默认：0.05）
- **SAMPLE_PARAM**: 采样参数（默认：200）
- **SAMPLING_MODE**: 采样模式（ALL/NO_SAMPLING/RANDOM/FOCUSED/NEYMAN）
- **RUN_TANE**: 是否运行TANE算法（默认：true）
- **ALGORITHM_TIMEOUT_MINUTES**: 算法超时时间（默认：120分钟）

### 数据集配置

支持两种数据集配置模式：

1. **目录模式**: 自动扫描指定目录下的所有CSV文件
2. **列表模式**: 使用预定义的数据集路径列表
3. **参数化模式**: 通过命令行参数动态指定数据集名称

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