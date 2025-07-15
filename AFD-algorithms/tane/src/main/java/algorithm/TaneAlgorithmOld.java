package algorithm;

import measure.ErrorMeasure;
import measure.G3Measure;
import model.DataSet;
import model.FunctionalDependency;
import pli.PLICache;
import utils.BitSetUtils;
import utils.DataLoader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TaneAlgorithmOld {
    private List<String> listOfColumns;
    private List<Map<String, Object>> data;
    private Map<String, List<List<Integer>>> dictPartitions;
    private Map<String, List<String>> dictCplus;
    private List<List<String>> finalListOfFDs;
    private String[] tableT; // 改为String数组，与Python一致
    private int totalTuples;
    private PLICache cache;
    private DataSet dataSet;
    // private String infile;
    private Double threshold;
    private int valid_count;
    private ErrorMeasure measure;
    private long executionTimeMs;


    public TaneAlgorithmOld() {
        dictPartitions = new HashMap<>();
        dictCplus = new HashMap<>();
        finalListOfFDs = new ArrayList<>();
    }

    public void loadData(String filename) throws IOException {
        // this.infile = filename;
        data = new ArrayList<>();
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            line = br.readLine();
            if (line != null) {
                int columnCount = line.split(",").length;
                listOfColumns = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    listOfColumns.add(String.valueOf((char) ('A' + i)));
                }
                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",");
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 0; i < Math.min(listOfColumns.size(), values.length); i++) {
                        String value = values[i].trim();
                        row.put(listOfColumns.get(i), value);
                    }
                    data.add(row);
                }
            }
        }
        totalTuples = data.size();
        tableT = new String[totalTuples];
        Arrays.fill(tableT, "NULL"); // 初始化为"NULL"字符串
    }

    public int getValidationCount() {
        return valid_count;
    }

    public Map<String, List<Integer>> listDuplicates(List<String> seq) {
        Map<String, List<Integer>> tally = new HashMap<>();
        for (int i = 0; i < seq.size(); i++) {
            String item = seq.get(i);
            tally.computeIfAbsent(item, k -> new ArrayList<>()).add(i);
        }

        return tally.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .filter(entry -> !entry.getValue().isEmpty()) // 与Python保持一致：>0
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
    }

    // 修复findCplus - 按照Python版本的逻辑
    public List<String> findCplus(String x) {
        List<Set<String>> theSets = new ArrayList<>();
        for (char a : x.toCharArray()) {
            String subX = x.replace(String.valueOf(a), "");
            List<String> temp;
            if (dictCplus.containsKey(subX)) {
                temp = dictCplus.get(subX);
            } else {
                temp = findCplus(subX);
            }
            theSets.add(0, new HashSet<>(temp)); // 使用insert(0, ...)模拟Python的行为
        }

        if (theSets.isEmpty()) {
            return new ArrayList<>();
        }

        // 计算交集
        Set<String> intersection = new HashSet<>(theSets.get(0));
        for (int i = 1; i < theSets.size(); i++) {
            intersection.retainAll(theSets.get(i));
        }

        return new ArrayList<>(intersection);
    }


    public void computeDependencies(List<String> level, List<String> listOfCols) {
        for (String x : new ArrayList<>(level)) {
            x=sortString(x);
            List<Set<String>> theSets = new ArrayList<>();

            for (int i = 0; i < x.length(); i++) {
                char a = x.charAt(i);
                String subX = x.replace(String.valueOf(a), "");
                subX=sortString(subX);
                List<String> temp;
                if (dictCplus.containsKey(subX)) {
                    temp = dictCplus.get(subX);
                } else {
                    temp = computeCplus(subX); // 为每个A计算C+(X\\{A})
                    dictCplus.put(subX, temp);
                }
                theSets.add(0, new HashSet<>(temp));
            }


            if (theSets.isEmpty()) {
                dictCplus.put(x, new ArrayList<>());
            } else {
                Set<String> intersection = new HashSet<>(theSets.get(0));
                for (int i = 1; i < theSets.size(); i++) {
                    intersection.retainAll(theSets.get(i));
                }
                dictCplus.put(x, new ArrayList<>(intersection));
            }

        }

        // 第二阶段：检查函数依赖
        for (String x : level) {
            for (char a : x.toCharArray()) {
                String aStr = String.valueOf(a);
                if (dictCplus.get(x).contains(aStr)) {
                    String xWithoutA = x.replace(aStr, "");
                    if (validFD(xWithoutA, aStr)) {
                        List<String> fd = new ArrayList<>();
                        fd.add(xWithoutA);
                        fd.add(aStr);
                        Double e=g3(xWithoutA,aStr);

                        fd.add(String.valueOf(e));
                        finalListOfFDs.add(fd);
                        dictCplus.get(x).remove(aStr);

                        // 计算R\X（listofcols - x中的字符）
                        List<String> tempCols = new ArrayList<>(listOfCols);
                        for (char j : x.toCharArray()) {
                            tempCols.remove(String.valueOf(j));
                        }

                        // 从C+(X)中移除R\X中的每个b
                        if(g3(xWithoutA,aStr)==0) {
                            List<String> cplusX = dictCplus.get(x);
                            for (String b : tempCols) {
                                cplusX.remove(b);
                            }
                        }
                    }
                }
            }
        }
    }

    public List<String> computeCplus(String x) {
        List<String> listOfCols = new ArrayList<>(listOfColumns);
        if (x.isEmpty()) {
            return listOfCols; // C+{phi} = R
        }

        List<String> cplus = new ArrayList<>();
        for (String a : listOfCols) {
            // 检查Python代码逻辑：对于x中的每个b，检查是否validfd(temp, b)
            // 如果存在某个b使得!validfd(temp, b)为true，则将a加入cplus
            for (char b : x.toCharArray()) {
                String temp = x.replace(a, "");
                temp = temp.replace(String.valueOf(b), "");
                if (!validFD(temp, String.valueOf(b))) {
                    cplus.add(a);
                    break; // 找到一个就跳出
                }
            }
        }

        return cplus;
    }

    public List<Integer> getIndices(String colString, List<String> listOfColumns) {
        List<Integer> indices = new ArrayList<>();
        for (char ch : colString.toCharArray()) {
            String colName = String.valueOf(ch);
            int idx = listOfColumns.indexOf(colName);
            if (idx != -1) {
                indices.add(idx);
            } else {
                System.out.println("列名 " + colName + " 不存在");
            }
        }
        return indices;
    }
    public Double g3(String y,String z){
        valid_count+=1;
        //计算它们的g3误差，首先将y表示为0-n的数字，表示第i列
        List<Integer> indices = getIndices(y, listOfColumns);
        BitSet targetColumns = BitSetUtils.listToBitSet(indices);
        List<Integer> indicesZ = getIndices(z, listOfColumns);
        Integer r=indicesZ.get(0);

        return this.measure.calculateError(targetColumns, r, dataSet, cache);
    }
    public boolean validFD(String y, String z) {
        if (y.isEmpty() || z.isEmpty()) {
            return false;
        }
        double error = g3(y,z);
        return error <= threshold;

//        // === 动态阈值 ===
//        List<Integer> lhsIndices = getIndices(y, listOfColumns);
//        List<Integer> rhsIndices = getIndices(z, listOfColumns);
//        double alpha = measure.DynamicThreshold.computeAlpha(dataSet,lhsIndices, rhsIndices);
//
//        return error <= alpha;

    }

    public boolean checkSuperkey(String x) {
        List<List<Integer>> partitions = dictPartitions.get(x);
        if (partitions == null) return false;

        // 检查是否为空列表或包含一个空列表
        return partitions.isEmpty() ||
                (partitions.size() == 1 && partitions.get(0).isEmpty());
    }

    public void prune(List<String> level) {
        List<String> stuffToBeDeletedFromLevel = new ArrayList<>();

        for (String x : level) {
            if (dictCplus.get(x).isEmpty()) {
                // 标记为待删除，而不是直接删除
                stuffToBeDeletedFromLevel.add(x);
            }

            if (checkSuperkey(x)) {
                List<String> temp = new ArrayList<>(dictCplus.get(x));
                // 计算C+(X) \ X
                for (char i : x.toCharArray()) {
                    temp.remove(String.valueOf(i));
                }

                for (String a : temp) {
                    List<Set<String>> theSets = new ArrayList<>();
                    for (char b : x.toCharArray()) {
                        String combinedStr = x + a;
                        String withoutB = combinedStr.replace(String.valueOf(b), "");
                        String sortedWithoutB = sortString(withoutB);

                        if (!dictCplus.containsKey(sortedWithoutB)) {
                            dictCplus.put(sortedWithoutB, findCplus(sortedWithoutB));
                        }
                        theSets.add(0, new HashSet<>(dictCplus.get(sortedWithoutB))); // insert(0, ...)
                    }

                    if (!theSets.isEmpty()) {
                        Set<String> intersection = new HashSet<>(theSets.get(0));
                        for (int i = 1; i < theSets.size(); i++) {
                            intersection.retainAll(theSets.get(i));
                        }

                        if (intersection.contains(a)) {
                            List<String> fd = new ArrayList<>();
                            fd.add(x);
                            fd.add(a);
                            Double e=g3(x,a);
                            fd.add(String.valueOf(e));
                            finalListOfFDs.add(fd);
                        }
                    }
                }

                stuffToBeDeletedFromLevel.add(x);
            }
        }

        // 删除标记的元素
        for (String item : stuffToBeDeletedFromLevel) {
            level.remove(item);
        }
    }

    public List<String> generateNextLevel(List<String> level) {
        List<String> nextLevel = new ArrayList<>();

        for (int i = 0; i < level.size(); i++) {
            for (int j = i + 1; j < level.size(); j++) {
                String levelI = level.get(i);
                String levelJ = level.get(j);

                // 检查条件：不相等且前n-1个字符相同
                if (!levelI.equals(levelJ) &&
                        levelI.length() > 0 && levelJ.length() > 0 &&
                        levelI.substring(0, levelI.length() - 1).equals(levelJ.substring(0, levelJ.length() - 1))) {

                    String x = levelI + levelJ.charAt(levelJ.length() - 1);
                    boolean flag = true;

                    // 检查所有子集是否都在level中
                    for (char a : x.toCharArray()) {
                        String xWithoutA = x.replace(String.valueOf(a), "");
                        if (!level.contains(xWithoutA)) {
                            flag = false;
                            break;
                        }
                    }

                    if (flag) {
                        nextLevel.add(x);
                        strippedProduct(x, levelI, levelJ);
                    }
                }
            }
        }

        return nextLevel;
    }

    public void strippedProduct(String x, String y, String z) {
        String sortedY = sortString(y);
        String sortedZ = sortString(z);
        String sortedX = sortString(x);

        List<String> tableS = new ArrayList<>(Collections.nCopies(totalTuples, ""));
        List<List<Integer>> partitionY = dictPartitions.get(sortedY);
        List<List<Integer>> partitionZ = dictPartitions.get(sortedZ);
        List<List<Integer>> partitionOfX = new ArrayList<>();

        if (partitionY == null || partitionZ == null) {
            dictPartitions.put(sortedX, partitionOfX);
            return;
        }

        // 第一个循环：初始化tableT和tableS
        for (int i = 0; i < partitionY.size(); i++) {
            for (int t : partitionY.get(i)) {
                tableT[t] = String.valueOf(i);
            }
            if (i < tableS.size()) {
                tableS.set(i, "");
            }
        }

        // 第二个循环：处理partitionZ
        for (int i = 0; i < partitionZ.size(); i++) {
            // 第一个内循环
            for (int t : partitionZ.get(i)) {
                if (!tableT[t].equals("NULL")) {
                    int index = Integer.parseInt(tableT[t]);
                    if (index < tableS.size()) {
                        // 模拟Python的set union操作
                        Set<Integer> currentSet = new HashSet<>();
                        if (!tableS.get(index).isEmpty()) {
                            String[] parts = tableS.get(index).split(",");
                            for (String part : parts) {
                                if (!part.trim().isEmpty()) {
                                    currentSet.add(Integer.parseInt(part.trim()));
                                }
                            }
                        }
                        currentSet.add(t);

                        // 排序并转换为字符串
                        List<Integer> sortedList = new ArrayList<>(currentSet);
                        Collections.sort(sortedList);
                        tableS.set(index, sortedList.stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(",")));
                    }
                }
            }

            // 第二个内循环
            for (int t : partitionZ.get(i)) {
                if (!tableT[t].equals("NULL")) {
                    int index = Integer.parseInt(tableT[t]);
                    if (index < tableS.size()) {
                        String sValue = tableS.get(index);
                        if (!sValue.isEmpty()) {
                            String[] parts = sValue.split(",");
                            if (parts.length >= 2) {
                                List<Integer> partition = Arrays.stream(parts)
                                        .filter(s -> !s.trim().isEmpty())
                                        .map(s -> Integer.parseInt(s.trim()))
                                        .collect(Collectors.toList());
                                if (partition.size() >= 2) {
                                    partitionOfX.add(partition);
                                }
                            }
                        }
                        tableS.set(index, "");
                    }
                }
            }
        }

        // 清理tableT
        for (int i = 0; i < partitionY.size(); i++) {
            for (int t : partitionY.get(i)) {
                tableT[t] = "NULL";
            }
        }

        dictPartitions.put(sortedX, partitionOfX);
    }

    public void computeSingletonPartitions() {
        for (int j = 0; j < listOfColumns.size(); j++) {
            dictPartitions.put(listOfColumns.get(j), new ArrayList<>());
            int colIndex = j;

            List<String> columnData = new ArrayList<>(dataSet.getRowCount());
            for (int i = 0; i < dataSet.getRowCount(); i++) {
                columnData.add(dataSet.getValue(i, colIndex));
            }

            Map<String, List<Integer>> duplicates = listDuplicates(columnData);
            for (Map.Entry<String, List<Integer>> entry : duplicates.entrySet()) {
                if (entry.getValue().size() > 1) { // 只保留大于1的等价类
                    dictPartitions.get(listOfColumns.get(j)).add(entry.getValue());
                }
            }
        }
    }

    public String sortString(String str) {
        char[] chars = str.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    public void run(DataSet dataSet, Double threshold, ErrorMeasure measure, boolean verbose) {
        valid_count=0;
        this.dataSet = dataSet;
        this.threshold = threshold;
        this.measure = measure;
        this.totalTuples = dataSet.getRowCount();
        this.cache = new PLICache(dataSet);
        this.tableT = new String[totalTuples];
        Arrays.fill(tableT, "NULL");

        this.listOfColumns = new ArrayList<>();
        for (int i = 0; i < dataSet.getColumnCount(); i++) {
            this.listOfColumns.add(String.valueOf((char) ('A' + i)));
        }

        long startTime = System.currentTimeMillis();

        if (verbose) {
            System.out.println("开始运行TANE算法...");
            System.out.println("数据大小: " + totalTuples + " 行, " + listOfColumns.size() + " 列");
            System.out.println("列名: " + listOfColumns);
        }

        // DataLoader loader = DataLoader.fromFile(
        //         Path.of(infile)
        // ).withHeader(true).withDelimiter(',');
        // dataSet = loader.load();
        // cache = PLICache.getInstance(dataSet);

        List<String> L0 = new ArrayList<>();
        dictCplus.put("NULL", new ArrayList<>(listOfColumns)); // 保持与Python一致：使用"NULL"
        computeSingletonPartitions();


        List<String> L1 = new ArrayList<>(listOfColumns);
        int l = 1;

        List<List<String>> L = new ArrayList<>();
        L.add(L0);
        L.add(L1);

        while (!L.get(l).isEmpty()) {
            computeDependencies(L.get(l), new ArrayList<>(listOfColumns));

//            prune(L.get(l));

            List<String> temp = generateNextLevel(L.get(l));

            L.add(temp);
            l++;
        }


        finalListOfFDs.sort((fd1, fd2) -> {
            double error1 = Double.parseDouble(fd1.get(2));
            double error2 = Double.parseDouble(fd2.get(2));
            return Double.compare(error1, error2);
        });

        this.executionTimeMs = System.currentTimeMillis() - startTime;

        if (verbose) {
            System.out.println("找到的函数依赖:");
            for (List<String> fd : finalListOfFDs) {
                double error = Double.parseDouble(fd.get(2));
                System.out.println("[" + fd.get(0) + " -> " + fd.get(1) + "] error: " + error);
            }
            System.out.println("总共找到 " + finalListOfFDs.size() + " 个函数依赖");
        }
    }

    /**
     * 将TANE算法的结果转换为FunctionalDependency集合
     * @return 函数依赖集合
     */
    public Set<FunctionalDependency> getFDSet() {
        Set<FunctionalDependency> fdSet = new HashSet<>();
        
        for (List<String> fd : finalListOfFDs) {
            String lhsStr = fd.get(0);
            String rhsStr = fd.get(1);
            double error = Double.parseDouble(fd.get(2));
            
            // 将lhs字符串转换为列索引集合
            Set<Integer> lhsIndices = new HashSet<>();
            for (char c : lhsStr.toCharArray()) {
                String colName = String.valueOf(c);
                int colIndex = listOfColumns.indexOf(colName);
                if (colIndex != -1) {
                    lhsIndices.add(colIndex);
                }
            }
            
            // 将rhs字符串转换为列索引
            int rhsIndex = listOfColumns.indexOf(rhsStr);
            
            // 创建FunctionalDependency对象并添加到结果集
            if (rhsIndex != -1) {
                fdSet.add(new FunctionalDependency(lhsIndices, rhsIndex, error));
            }
        }
        
        return fdSet;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public static void main(String[] args) {
        String infile = "data/atom_new.csv";
        if (args.length > 0) {
            infile = args[0];
        }

        DataLoader loader = DataLoader.fromFile(
                Path.of(infile)
        ).withHeader(true).withDelimiter(',');
        DataSet dataSet = loader.load();

        Double threshold = 0.05;
        TaneAlgorithmOld tane = new TaneAlgorithmOld();
        try {
            //统计时间
            tane.run(dataSet, threshold, new G3Measure(), true);
            
            // 获取FD集合并输出
            Set<FunctionalDependency> fdSet = tane.getFDSet();
            System.out.println("\n===== TANE算法结果（FD格式） =====");
            List<FunctionalDependency> sortedFDs = new ArrayList<>(fdSet);
            sortedFDs.sort(new Comparator<FunctionalDependency>() {
                @Override
                public int compare(FunctionalDependency fd1, FunctionalDependency fd2) {
                    return Double.compare(fd1.getError(), fd2.getError());
                }
            });
            for (int i = 0; i < Math.min(10, sortedFDs.size()); i++) {
                System.out.println(sortedFDs.get(i));
            }
            System.out.println("总共找到 " + fdSet.size() + " 个函数依赖");
            
            System.out.println("运行时间: " + tane.getExecutionTimeMs() + "ms");
            System.out.println("valid_count: "+tane.getValidationCount());
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}