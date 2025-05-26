# AFD 度量与算法

本项目提供函数依赖发现（AFD）的核心度量实现及多种发现算法，包含基础工具库、核心算法实现及测试套件。

## 📦 项目结构

```
AFD-measures
├── AFD-core - 核心库模块
│   ├── src/main/java
│   │   ├── measure - 度量计算实现
│   │   │   ├── ErrorMeasure.java  # 错误率度量
│   │   │   ├── G1Measure.java    # G1度量策略：基于"违反元组对"的误差
│   │   │   └── G3Measure.java    # G3度量策略：基于"删除元组数"的误差
│   │   ├── model - 数据模型
│   │   │   ├── DataSet.java
│   │   │   └── FunctionalDependency.java
│   │   ├── pli - 分区列表交运算
│   │   │   ├── PLI.java          # PLI实现
│   │   │   └── PLICache.java     # 缓存实现
│   │   ├── sampling - 采样策略
│   │   │   ├── SamplingStrategy.java # 采样策略接口
│   │   │   ├── RandomSampling.java # 随机采样
│   │   │   └── FocusedSampling.java # 聚焦采样
│   │   └── utils - 工具类
│   │       ├── DataLoader.java   # 读取csv文件
│   │       ├── FunctionTimer.java # 时间统计
│   │       ├── HittingSet.java  # 实现最小命中集计算的算法
│   │       ├── BitSetUtils.java  # 位集操作
│   │       ├── Trie.java         # 前缀树实现
│   │       ├── MinFDTrie.java    # 存储最小有效FD
│   │       ├── MaxFDTrie.java    # 存储最大nonFD
│   │       ├── Pair.java         # 存储一个对
│   │       └── MinMaxPair.java   # 维护最小值和最大值
│   └── test - 单元测试

├── AFD-algorithms - 算法实现模块
│   ├── algorithm-base - 算法基础模块
│   │   ├── CompareAlgorithms.java # 比较Pyro和TANE算法结果
│   ├── pyro - Pyro算法实现      # 项目重点实现的算法
│   │   ├── algorithm - 算法实现
│   │   │   ├── Pyro.java            # 算法启动类
│   │   │   └── SearchSpace.java     # 搜索空间，包含核心的算法逻辑
│   └── tane - TANE算法实现      # 对比用的baseline算法

├── run_compare_algorithms.bat    # 运行算法比较工具的批处理文件
├── run_verify_tane_fds.bat       # 运行TANE函数依赖验证工具的批处理文件
└── run_verify_specific_fds.bat   # 运行特定函数依赖验证工具的批处理文件
```

## 🔧 项目依赖

- **核心依赖**：
  - Java 21
  - SLF4J 2.0 + Logback 1.5.13 (日志系统)

- **测试框架**：
  - JUnit Jupiter 5.8.2

## 🚀 快速开始

1. 克隆仓库：
```bash
git clone [仓库地址]
```
2. 构建项目：
```bash
mvn clean install -DskipTests
```
3. 运行算法比较工具：
   - 双击 `run_compare_algorithms.bat` 文件
   - 或者使用命令：`mvn exec:java -Dexec.mainClass="algorithm.CompareAlgorithms" -pl AFD-algorithms/algorithm-base`

## 算法比较工具

项目提供了几个用于比较和验证算法结果的工具：

1. **CompareAlgorithms**：比较Pyro和TANE算法的结果差异
   - 显示两个算法找到的函数依赖数量
   - 分析共同的、独有的函数依赖
   - 比较误差计算的差异
   - 运行方式：双击 `run_compare_algorithms.bat`

2. **VerifyTaneFDs**：验证TANE算法独有的函数依赖
   - 检查这些函数依赖是否为最小依赖
   - 使用G3Measure验证这些函数依赖的误差
   - 运行方式：双击 `run_verify_tane_fds.bat`

3. **VerifySpecificFDs**：验证特定的TANE独有函数依赖
   - 专门验证指定的几个函数依赖
   - 检查它们是否为最小依赖
   - 使用G3Measure验证它们的误差
   - 运行方式：双击 `run_verify_specific_fds.bat`

## ⚠️ 注意事项

1. **JDK版本**：
   - 根项目要求JDK 21

2. **依赖管理**：
   ```xml
   <!-- 添加核心库依赖 -->
   <dependency>
     <groupId>com.example</groupId>
     <artifactId>AFD-core</artifactId>
     <version>1.0-SNAPSHOT</version>
   </dependency>
   ```

3. **日志配置**：
   各算法模块的`src/main/resources/logback.xml`可自定义日志级别

4. **测试数据**：
   数据集的csv文件放在`/data`文件夹下