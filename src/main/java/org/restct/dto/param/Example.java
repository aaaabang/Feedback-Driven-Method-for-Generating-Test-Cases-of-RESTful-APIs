package org.restct.dto.param;

import org.restct.dto.Operation;

import java.util.*;

public class Example {
    public static Set<Example> members;

    static {
        members = new HashSet<>();
    }

    private String parameterStr;
    private Object value;

    public Example(String parameterStr, Object value) {
        this.parameterStr = parameterStr;
        this.value = value;

        if (this.value != null && !this.value.toString().isEmpty()) {
            Example.members.add(this);
        }
    }

    public static List<Object> findExample(String parameterStr) {
        List<Object> allValues = new ArrayList<>();
        for (Example example : Example.members) {
            if (example.parameterStr.equals(parameterStr)) {
                allValues.add(example.value);
            }
        }
        return allValues;

    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterStr, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Example) {
            Example other = (Example) obj;
            return parameterStr.equals(other.parameterStr) && Objects.equals(value, other.value);
        }
        return false;
    }

    public void setOperation(Operation operation) {
    }
}
