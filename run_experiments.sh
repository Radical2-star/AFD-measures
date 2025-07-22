#!/bin/bash

# 如果任何命令执行失败，立即退出脚本
set -e

# --- 1. 项目编译 ---
echo ">>> 正在使用Maven编译项目..."
mvn clean package -DskipTests
echo ">>> 项目编译完成!"

# --- 2. 实验配置 ---
# 定义JAR文件的路径 (根据你的项目结构，这个路径通常是固定的)
# 'target'目录是Maven编译后存放结果的地方
JAR_FILE="AFD-algorithms/experiment/target/experiment-1.0-SNAPSHOT-jar-with-dependencies.jar"

# 定义主类的完整名称
MAIN_CLASS="experiment.ExperimentRunner"

# --- 3. 执行实验 ---
echo "-------------------------------------------------"
echo ">>> 开始执行批量实验..."
echo ">>  数据集目录: data/0"
echo ">>  采样模式: ALL"
echo ">>  运行TANE: true"
echo ">>  结果文件: result/result0721.csv (追加模式)"
echo ">>  超时时间: 60分钟"
echo "-------------------------------------------------"

# 使用 java 命令运行你的程序，并通过命令行参数传入本次实验的所有配置
# 我们将Java可以使用的最大内存设置为8GB (-Xmx8g)，这对于处理大数据集很有帮助
java -Xmx8g -cp "$JAR_FILE" "$MAIN_CLASS" \
    --dataset "data/0" \
    --results-file "result/result0721.csv" \
    --sampling-mode "ALL" \
    --run-mode "APPEND" \
    --run-tane "true" \
    --timeout "60" \
    --max-error "0.05" \
    --sample-param "200" \
    --seed "114514"

echo ">>> 所有实验已提交执行!"
echo ">>> 你可以在 result/result0721.csv 文件中查看进度。"