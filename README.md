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

## 🌟 项目目标与愿景

本项目的核心目标是提供一个全面、高效、易用的函数依赖发现（AFD）工具集。我们致力于：

- **精确性与效率**：实现和优化多种AFD算法，追求在不同类型数据集上的高精确度和高效率。
- **模块化与可扩展性**：通过清晰的模块划分（核心度量、算法实现、工具类），方便研究人员和开发者理解、使用和扩展新功能。
- **理论与实践结合**：不仅关注算法的理论基础，也重视其在实际数据场景中的应用和验证。
- **学术研究支持**：为数据库领域，特别是数据质量、数据集成、查询优化等方向的研究提供坚实的工具基础。

我们希望本项目能够成为AFD领域研究和应用的一个有价值的资源。

## 💡 潜在应用场景

函数依赖（FDs）及其近似形式（AFDs）在数据库和数据科学领域有广泛的应用价值：

- **数据清洗与预处理**：识别并纠正数据中的不一致性和错误。
- **数据规范化**：指导数据库模式设计，减少数据冗余。
- **查询优化**：利用FDs来简化查询、重写查询以提高效率。
- **数据集成**：在合并来自不同数据源的数据时，解决数据冲突和异构性问题。
- **特征工程**：在机器学习中，FDs可以帮助识别冗余特征或构建新的有意义特征。
- **知识发现**：从数据中自动发现潜在的业务规则和约束。


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

项目提供了用于比较和验证算法结果的工具：

   * **CompareAlgorithms**：比较Pyro和TANE算法的结果差异
   - 显示两个算法找到的函数依赖数量
   - 分析共同的、独有的函数依赖
   - 比较误差计算的差异
   - 运行方式：双击 `run_compare_algorithms.bat`

## 🔮 未来工作

我们计划在未来进一步增强本项目的功能和性能：

- **更多AFD算法**：集成更多先进的精确及近似函数依赖发现算法。
- **增量发现**：研究和实现针对动态变化数据集的增量AFD发现算法。
- **分布式计算支持**：探索将核心算法扩展到分布式计算框架（如Spark）上，以处理超大规模数据集。
- **用户界面**：开发一个简单的用户界面，方便非技术用户上传数据并进行AFD分析。
- **更丰富的度量指标**：引入更多评估AFD质量和算法性能的度量指标。
- **与其他数据工具集成**：提供接口或插件，方便与常见的数据处理和分析工具（如Pandas, Apache Spark等）集成。

## 🤝 如何贡献

我们欢迎任何形式的贡献，包括但不限于：

- **Bug修复**：发现并修复代码中的错误。
- **新功能**：实现新的AFD算法、度量方法或工具。
- **性能优化**：改进现有算法的效率。
- **文档完善**：改进README、代码注释或其他文档。
- **测试用例**：添加更多的单元测试和集成测试。

如果您有兴趣贡献，请：

1. Fork本仓库。
2. 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)。
3. 提交您的更改 (`git commit -m 'Add some AmazingFeature'`)。
4. 推送到分支 (`git push origin feature/AmazingFeature`)。
5. 打开一个Pull Request。

我们期待您的参与！

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