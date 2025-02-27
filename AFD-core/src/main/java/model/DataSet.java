package model;

import java.util.Collections;
import java.util.List;

/**
 * @ClassName DataSet
 * @Description
 * @Author Zuoxing Xie
 * @Time 2025/2/26
 * @Version 1.0
 */
public class DataSet {
    private final List<String> attributes;
    private final List<List<Object>> data;

    public DataSet(List<String> attributes, List<List<Object>> data) {
        this.attributes = Collections.unmodifiableList(attributes);
        this.data = Collections.unmodifiableList(data);
    }

    public int getAttributeIndex(String name) {
        return attributes.indexOf(name);
    }

    public Object getValue(int row, int column) {
        return data.get(row).get(column);
    }

    public int size() {
        return data.size();
    }

    public List<String> getAttributes() {
        return attributes;
    }
}
