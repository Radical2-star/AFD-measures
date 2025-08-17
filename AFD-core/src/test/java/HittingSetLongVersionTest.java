import utils.HittingSet;
import utils.LongBitSetUtils;
import utils.BitSetUtils;

import java.util.*;

/**
 * 测试HittingSet的long版本方法修复
 * 验证removeSubsets(long bits)方法的正确性
 * 
 * @author Hoshi
 * @version 1.0
 * @since 2025/8/4
 */
public class HittingSetLongVersionTest {
    
    public static void main(String[] args) {
        System.out.println("开始测试HittingSet long版本方法修复...");
        
        try {
            testRemoveSubsetsLong();
            testHittingSetLongMethods();
            testConsistencyWithBitSetVersion();
            System.out.println("✅ 所有测试通过！HittingSet long版本修复成功。");
            
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试removeSubsets(long bits)方法的正确性
     */
    private static void testRemoveSubsetsLong() {
        System.out.println("测试removeSubsets(long bits)方法...");
        
        HittingSet hittingSet = new HittingSet();
        
        // 添加一些测试数据
        long subset1 = LongBitSetUtils.createFromBits(0, 1);      // {0, 1}
        long subset2 = LongBitSetUtils.createFromBits(0);         // {0} - subset1的子集
        long subset3 = LongBitSetUtils.createFromBits(2, 3);      // {2, 3}
        long subset4 = LongBitSetUtils.createFromBits(0, 1, 2);   // {0, 1, 2}
        
        hittingSet.add(subset1);
        hittingSet.add(subset2);
        hittingSet.add(subset3);
        hittingSet.add(subset4);
        
        // 测试移除subset4的所有子集
        // subset4 = {0, 1, 2}，它的子集包括：{0}, {0, 1}
        List<Long> removedSubsets = hittingSet.removeSubsets(subset4);
        
        // 验证结果
        assert removedSubsets != null : "移除结果不应为null";
        System.out.println("移除的子集数量: " + removedSubsets.size());
        
        for (long removed : removedSubsets) {
            System.out.println("移除的子集: " + LongBitSetUtils.longToList(removed));
            // 验证移除的确实是subset4的子集
            assert LongBitSetUtils.isSubset(removed, subset4) : 
                "移除的集合应该是输入集合的子集";
        }
        
        // 验证剩余的集合
        List<Long> remaining = hittingSet.getAllMinimalHittingSetsLong();
        System.out.println("剩余的集合数量: " + remaining.size());
        
        for (long remainingSet : remaining) {
            System.out.println("剩余的集合: " + LongBitSetUtils.longToList(remainingSet));
            // 验证剩余的集合不是subset4的子集
            assert !LongBitSetUtils.isSubset(remainingSet, subset4) || remainingSet == subset4 : 
                "剩余的集合不应该是输入集合的真子集";
        }
        
        System.out.println("✅ removeSubsets(long bits)方法测试通过");
    }
    
    /**
     * 测试HittingSet的其他long版本方法
     */
    private static void testHittingSetLongMethods() {
        System.out.println("测试HittingSet其他long版本方法...");
        
        HittingSet hittingSet = new HittingSet();
        
        // 测试add(long bits)
        long bits1 = LongBitSetUtils.createFromBits(0, 1, 2);
        long bits2 = LongBitSetUtils.createFromBits(3, 4);
        long bits3 = LongBitSetUtils.createFromBits(0, 1); // bits1的子集
        
        hittingSet.add(bits1);
        hittingSet.add(bits2);
        
        // 测试addIfNoSubset(long bits)
        hittingSet.addIfNoSubset(bits3); // 应该不会添加，因为bits1包含bits3
        
        List<Long> allSets = hittingSet.getAllMinimalHittingSetsLong();
        assert allSets.size() == 2 : "应该只有2个集合（bits3不应被添加）";
        
        // 测试delete(long bits)
        hittingSet.delete(bits1);
        allSets = hittingSet.getAllMinimalHittingSetsLong();
        assert allSets.size() == 1 : "删除后应该只有1个集合";
        assert allSets.contains(bits2) : "应该包含bits2";
        
        System.out.println("✅ HittingSet其他long版本方法测试通过");
    }
    
    /**
     * 测试long版本与BitSet版本的一致性
     */
    private static void testConsistencyWithBitSetVersion() {
        System.out.println("测试long版本与BitSet版本的一致性...");
        
        // 创建两个HittingSet实例
        HittingSet hittingSetLong = new HittingSet();
        HittingSet hittingSetBitSet = new HittingSet();
        
        // 准备测试数据
        long[] longBits = {
            LongBitSetUtils.createFromBits(0, 1),
            LongBitSetUtils.createFromBits(0),
            LongBitSetUtils.createFromBits(2, 3),
            LongBitSetUtils.createFromBits(0, 1, 2)
        };
        
        // 使用long版本添加数据
        for (long bits : longBits) {
            hittingSetLong.add(bits);
        }
        
        // 使用BitSet版本添加相同的数据
        for (long bits : longBits) {
            BitSet bitSet = LongBitSetUtils.longToBitSet(bits, 6);
            hittingSetBitSet.add(bitSet);
        }
        
        // 测试removeSubsets的一致性
        long targetBits = LongBitSetUtils.createFromBits(0, 1, 2);
        BitSet targetBitSet = LongBitSetUtils.longToBitSet(targetBits, 6);
        
        List<Long> removedLong = hittingSetLong.removeSubsets(targetBits);
        List<BitSet> removedBitSet = hittingSetBitSet.removeSubsets(targetBitSet);
        
        // 转换BitSet结果为long进行比较
        List<Long> removedBitSetAsLong = new ArrayList<>();
        for (BitSet bitSet : removedBitSet) {
            removedBitSetAsLong.add(LongBitSetUtils.bitSetToLong(bitSet, 6));
        }
        
        // 验证结果一致性
        assert removedLong.size() == removedBitSetAsLong.size() : 
            "long版本和BitSet版本移除的集合数量应该相同";
        
        Collections.sort(removedLong);
        Collections.sort(removedBitSetAsLong);
        
        for (int i = 0; i < removedLong.size(); i++) {
            assert removedLong.get(i).equals(removedBitSetAsLong.get(i)) : 
                "long版本和BitSet版本移除的集合应该相同";
        }
        
        System.out.println("移除的集合数量 (long版本): " + removedLong.size());
        System.out.println("移除的集合数量 (BitSet版本): " + removedBitSetAsLong.size());
        
        System.out.println("✅ long版本与BitSet版本一致性测试通过");
    }
}
