package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class FunctionTimer {
    private final Map<String, Long> startTimes = new HashMap<>(); // 存储函数开始时间
    private final Map<String, Long> totalTimings = new HashMap<>(); // 存储函数总耗时
    private static final Logger logger = LoggerFactory.getLogger(FunctionTimer.class);

    // 单例实现
    private static final FunctionTimer INSTANCE = new FunctionTimer();

    public static FunctionTimer getInstance() {
        return INSTANCE;
    }

    /**
     * 开始记录某个函数的执行时间
     * @param functionName 函数名称（需唯一）
     */
    public void start(String functionName) {
        startTimes.put(functionName, System.nanoTime());
    }

    /**
     * 结束记录并计算耗时
     * @param functionName 必须与 start() 调用的名称一致
     */
    public void end(String functionName) {
        Long startTime = startTimes.get(functionName);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
            totalTimings.merge(functionName, duration, Long::sum);
            startTimes.remove(functionName); // 移除已结束的计时
        }
    }

    /**
     * 获取指定函数总耗时（毫秒）
     */
    public double getDuration(String functionName) {
        return totalTimings.getOrDefault(functionName, 0L) / 1_000_000.0;
    }

    /**
     * 重置所有统计
     */
    public void reset() {
        startTimes.clear();
        totalTimings.clear();
    }

    /**
     * 打印统计结果
     */
    public void printResults() {
        totalTimings.forEach((name, nanos) -> {
            logger.info("[{}] {} ms", name, String.format("%.2f", nanos / 1_000_000.0));
        });
    }
}
