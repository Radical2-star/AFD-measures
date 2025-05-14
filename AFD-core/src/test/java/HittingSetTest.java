import org.junit.jupiter.api.Test;
import utils.BitSetUtils;
import utils.HittingSet;

import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class HittingSetTest {
    // 辅助方法：将BitSet转换为字符串（例如 {1,2} -> "1,2"）
    private String bitSetToString(BitSet bs) {
        return BitSetUtils.bitSetToList(bs).stream()
                .map(String::valueOf)
                .sorted()
                .collect(Collectors.joining(","));
    }

    @Test
    void testCalculateHittingSet() {
        // 构造输入集合
        List<BitSet> inputSets = new ArrayList<>();
        inputSets.add(BitSetUtils.listToBitSet(Arrays.asList(1, 2, 3)));
        inputSets.add(BitSetUtils.listToBitSet(Arrays.asList(1, 2, 4)));
        inputSets.add(BitSetUtils.listToBitSet(Arrays.asList(1, 2, 5)));

        // 计算HittingSet
        HittingSet result = BitSetUtils.calculateHittingSet(inputSets, 6);

        // 转换为字符串集合方便比较
        Set<String> expected = new HashSet<>(Arrays.asList(
                "1",
                "2",
                "3,4,5"
        ));

        Set<String> actual = result.getAllMinimalHittingSets().stream()
                .map(this::bitSetToString)
                .collect(Collectors.toSet());

        // 验证结果
        assertEquals(expected, actual);

        // 附加验证：每个结果确实是hitting set
        for (BitSet hs : result.getAllMinimalHittingSets()) {
            assertTrue(isValidHittingSet(hs, inputSets),
                    "Generated set " + bitSetToString(hs) + " is not valid");
        }
    }

    @Test
    void testNestedSets() {
        List<BitSet> inputSets = Arrays.asList(
                BitSetUtils.listToBitSet(Arrays.asList(1, 2)),
                BitSetUtils.listToBitSet(Arrays.asList(1, 2, 3)),
                BitSetUtils.listToBitSet(Arrays.asList(1, 2, 3, 4))
        );

        HittingSet result = BitSetUtils.calculateHittingSet(inputSets, 5);

        // 预期结果应为 {1} {2}
        // 转换为字符串集合方便比较
        Set<String> expected = new HashSet<>(Arrays.asList(
                "1",
                "2"
        ));
        Set<String> actual = result.getAllMinimalHittingSets().stream()
                .map(this::bitSetToString)
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
    }

    @Test
    void testCombinationRequired() {
        List<BitSet> inputSets = Arrays.asList(
                BitSetUtils.listToBitSet(Arrays.asList(1, 2)),
                BitSetUtils.listToBitSet(Arrays.asList(3, 4)),
                BitSetUtils.listToBitSet(Arrays.asList(1, 3, 5))
        );

        HittingSet result = BitSetUtils.calculateHittingSet(inputSets, 6);
        Set<String> actual = result.getAllMinimalHittingSets().stream()
                .map(this::bitSetToString)
                .collect(Collectors.toSet());

        // 验证所有结果是否有效
        for (String candidate : actual) {
            BitSet bs = BitSetUtils.listToBitSet(
                    Arrays.stream(candidate.split(","))
                            .map(Integer::parseInt)
                            .collect(Collectors.toList())
            );
            assertTrue(isValidHittingSet(bs, inputSets));
        }

        Set<String> expected = new HashSet<>(Arrays.asList(
                "1,3",
                "1,4",
                "2,3",
                "2,4,5"
        ));
        assertEquals(expected, actual);
    }

    @Test
    void testDisjointSets() {
        List<BitSet> inputSets = Arrays.asList(
                BitSetUtils.listToBitSet(Collections.singletonList(1)),
                BitSetUtils.listToBitSet(Collections.singletonList(2)),
                BitSetUtils.listToBitSet(Collections.singletonList(3))
        );

        HittingSet result = BitSetUtils.calculateHittingSet(inputSets, 4);
        Set<String> actual = result.getAllMinimalHittingSets().stream()
                .map(this::bitSetToString)
                .collect(Collectors.toSet());

        // 预期结果必须包含所有元素
        assertEquals(1, actual.size());
        assertTrue(actual.contains("1,2,3"));
    }


    // 验证单个HittingSet是否有效
    private boolean isValidHittingSet(BitSet candidate, List<BitSet> sets) {
        return sets.stream().allMatch(set -> set.intersects(candidate));
    }
}
