package org.restct.dto.keywords;

import java.util.ArrayList;
import java.util.List;

public enum Method {
    POST("post"),
    GET("get"),
    DELETE("delete"),
    PUT("put");

    private final String value;

    Method(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static String[] getAllValues(){
        Method[] methods = Method.values();
        List<String> values = new ArrayList<>();
        for (Method method : methods) {
            values.add(method.getValue());
        }
        return values.toArray(new String[0]);
    }

    public static Method fromValue(String value) {
        for (Method method : Method.values()) {
            if (method.getValue().equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Invalid Method value: " + value);
    }

}
