import utils.HittingSet;
import utils.LongBitSetUtils;

import java.util.List;

/**
 * 简单的编译验证测试
 * 确保HittingSet的long版本方法能够正常编译和基本运行
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/8/4
 */
public class HittingSetCompilationTest {
    
    public static void main(String[] args) {
        System.out.println("开始HittingSet编译验证测试...");
        
        try {
            // 基本编译验证
            HittingSet hittingSet = new HittingSet();
            
            // 测试add(long bits)方法编译
            long bits1 = LongBitSetUtils.createFromBits(0, 1, 2);
            hittingSet.add(bits1);
            
            // 测试removeSubsets(long bits)方法编译
            List<Long> removed = hittingSet.removeSubsets(bits1);
            
            // 测试其他long版本方法编译
            hittingSet.delete(bits1);
            hittingSet.addIfNoSubset(bits1);
            List<Long> allSets = hittingSet.getAllMinimalHittingSetsLong();
            
            System.out.println("✅ 编译验证通过！所有方法都能正常编译。");
            System.out.println("移除的集合数量: " + removed.size());
            System.out.println("所有集合数量: " + allSets.size());

            // 额外测试：验证removeSubsets方法的基本功能
            HittingSet testSet = new HittingSet();
            long testBits1 = LongBitSetUtils.createFromBits(0, 1);
            long testBits2 = LongBitSetUtils.createFromBits(0);  // testBits1的子集

            testSet.add(testBits1);
            testSet.add(testBits2);

            List<Long> removedSubsets = testSet.removeSubsets(testBits1);
            System.out.println("removeSubsets测试 - 移除的子集数量: " + removedSubsets.size());

            if (removedSubsets.size() > 0) {
                System.out.println("✅ removeSubsets方法工作正常！");
            } else {
                System.out.println("⚠️ removeSubsets方法可能需要进一步检查");
            }
            
        } catch (Exception e) {
            System.err.println("❌ 编译验证失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
