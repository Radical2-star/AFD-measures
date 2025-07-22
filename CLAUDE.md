# CLAUDE.md

此文件为Claude Code (claude.ai/code)在此代码仓库中工作时提供指导。

## 项目概述

这是一个基于Java的近似函数依赖（AFD）发现研究项目，实现并比较了多种AFD算法。项目专注于为数据库研究和数据质量应用提供高效、准确的函数依赖发现工具。

### 核心组件

- **AFD-core**: 核心库，包含数据模型、错误度量、PLI操作、采样策略和工具类
- **AFD-algorithms**: 算法实现，包括Pyro（主要算法）和TANE（基准算法）
- **AFD-algorithms/experiment**: 用于运行和比较算法的实验框架

## 构建和开发命令

### 前置条件
- Java 11
- Maven 3.9.9+

### 核心命令

```bash
# 清理并构建整个项目
mvn clean install

# 跳过测试进行构建
mvn clean install -DskipTests

# 运行特定模块的测试
mvn test -pl AFD-core

# 运行所有模块的测试
mvn test

# 仅编译不运行测试
mvn clean compile

# 运行实验框架
mvn exec:java -Dexec.mainClass="experiment.ExperimentRunner" -pl AFD-algorithms/experiment
```

## 架构设计

### 模块结构
- **AFD-core**: 包含基础组件（度量、模型、PLI、采样、工具类）
- **AFD-algorithms/pyro**: Pyro算法实现（主要研究重点）
- **AFD-algorithms/tane**: TANE算法实现（基准比较）
- **AFD-algorithms/experiment**: 用于运行比较的实验框架

### 核心类和接口

#### 核心组件
- `DataSet`: 表示带有列元数据的表格数据
- `FunctionalDependency`: 表示带有错误度量的函数依赖
- `PLI`和`PLICache`: 分区列表索引操作和缓存
- `ErrorMeasure`: 不同错误计算策略的接口（G1、G3、SimpleG3）

#### 算法
- `Pyro`: 支持采样的主要AFD发现算法
- `SearchSpace`: Pyro算法的核心搜索逻辑
- `TaneAlgorithm`: TANE基准算法实现

#### 采样策略
- `RandomSampling`: 随机采样策略
- `FocusedSampling`: 聚焦采样策略
- `NeymanSampling`: Neyman最优分配采样

### 数据流
1. 通过`DataLoader`将数据加载到`DataSet`对象中
2. 为属性组合计算并缓存PLI（分区列表索引）
3. 算法使用各种采样策略探索搜索空间
4. 错误度量（G1、G3、SimpleG3）计算函数依赖的有效性
5. 结果收集为`FunctionalDependency`对象

## 配置

### 实验配置
实验框架通过`ExperimentRunner`中的常量进行配置：
- `MAX_ERROR`: 最大错误阈值（默认：0.05）
- `SAMPLE_PARAM`: 样本大小参数（默认：200）
- `RANDOM_SEED`: 可重现性的随机种子（默认：12345L）
- `SAMPLING_MODE`: 控制运行哪些采样策略

### 数据集配置
- `USE_DIRECTORY_MODE`: 在目录扫描和预定义数据集列表之间切换
- `DATASET_DIRECTORY`: 包含CSV数据集的目录
- `DATASET_PATHS`: 硬编码的数据集路径列表

## 测试

### 测试结构
- 单元测试位于各模块的`src/test/java`中
- 测试数据在`src/test/resources/`中
- 关键测试类：`DataLoaderTest`、`PLITest`、`HittingSetTest`

### 运行测试
```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=DataLoaderTest

# 运行测试时输出详细信息
mvn test -Dtest=PLITest -Dverbose=true
```

## 数据处理

### 支持的格式
- 支持各种分隔符的CSV文件（逗号、分号、制表符）
- 自动分隔符检测
- 支持表头行

### 数据集位置
- 主要数据集：`data/`目录
- 测试数据集：`AFD-core/src/test/resources/`
- 结果：`result/`目录

## 性能考虑

### 缓存
- PLI操作使用Caffeine缓存（在AFD-core中配置）
- 缓存提高了重复属性组合查询的性能

### 采样
- 采样策略减少了大数据集上的计算开销
- 不同的采样方法（随机、聚焦、Neyman）在精度和速度之间提供权衡

### 算法超时
- 实验框架包含超时机制（默认：每个算法120分钟）
- 防止在复杂数据集上出现无限循环

## 依赖项

### 核心依赖
- SLF4J 2.0.17 + Logback 1.5.13 用于日志记录
- Caffeine 3.1.8 用于缓存
- JUnit Jupiter 5.8.2 用于测试

### 模块依赖
- AFD-algorithms依赖于AFD-core
- Experiment模块依赖于Pyro和TANE模块

## 常见开发模式

### 添加新的错误度量
1. 实现`ErrorMeasure`接口
2. 添加到AFD-core的measure包中
3. 更新实验配置以包含新度量

### 添加新的采样策略
1. 实现`SamplingStrategy`接口
2. 添加到AFD-core的sampling包中
3. 更新`ExperimentRunner`配置选项

### 添加新算法
1. 在AFD-algorithms下创建新模块
2. 实现`AFDFinder`接口
3. 在experiment模块中添加执行器类
4. 更新实验配置

## 研究待办事项

### 高优先级任务（当前重点）
1. **✅ 优化Neyman采样策略性能**
   - ✅ 将二阶段采样的第一阶段提前到算法初始化阶段
   - ✅ 限制采样的子集PLI查找范围，仅在单列PLI上进行采样
   - ✅ 目标：显著提升当前低效的二阶段采样性能
   - **实现详情**：
     - 在SearchSpace中添加`columnVarianceCache`缓存单列PLI方差信息
     - 预计算阶段只在使用NeymanSampling时触发
     - 采样时选择LHS中size最小的单列PLI进行采样
     - 预期性能提升：单次采样时间减少70-80%

2. **🚨 SearchSpace性能优化（紧急）**
   - 实施节点回收机制：清理已访问的节点，避免内存无限增长问题
   - 优化PLI缓存策略：为PLICache实现LRU淘汰机制，限制缓存大小
   - 减少对象创建：避免频繁的BitSet克隆，复用对象减少GC压力
   - 简化搜索算法：优化ascend/trickleDown的递归逻辑，减少栈开销
   - **问题背景**：
     - 当前实验显示pyro算法比TANE慢9-26倍
     - 大数据集上出现Java heap space内存溢出错误
     - lhsToIdMap和nodeList等数据结构无限增长导致内存泄漏
     - PLI操作和搜索递归的性能瓶颈严重

### 中优先级任务（核心功能）
3. **实现动态阈值策略**
   - 根据给定的每列阈值动态计算列组合阈值
   - 设计策略模式支持动态阈值和静态阈值两种策略
   - 创建专用的动态阈值实验类和框架

4. **开发批量实验工具**
   - 编写bash脚本用于在服务器上批量运行实验
   - 支持不同数据集和参数配置的自动化测试

### 低优先级任务（后期完善）
5. **算法和度量优化**
   - 完善G1度量的实现（当前存在不完善之处）
   - 对比分析G1和G3两种度量方法的差异和适用场景
   - 重命名当前的"Pyro"算法（实际为自定义算法）

6. **理论研究**
   - 数学证明Neyman采样方法相比随机采样的优越性
   - 完善算法的理论基础

### 项目背景说明
- 本项目为研究生论文项目，主要算法位于pyro模块
- 当前算法仿照pyro遍历流程但与实际pyro算法不同
- 研究重点：遍历逻辑（pyro模块）、采样策略（Neyman采样）、动态阈值、错误度量

## 已知问题

- README中引用的一些批处理文件在仓库中不存在
- TANE算法的prune()方法在当前实现中被注释掉了