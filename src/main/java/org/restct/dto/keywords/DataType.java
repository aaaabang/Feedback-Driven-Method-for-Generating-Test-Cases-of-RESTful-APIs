package org.restct.dto.keywords;

public enum DataType {
    // 数字
    Integer("integer"),
    Number("number"),
    Int32("int32"),
    Int64("int64"),
    Float("float"),
    Double("double"),
    Long("long"),
    // 字符串
    String("string"),
    Byte("byte"),
    Binary("binary"),
    Date("date"),
    DateTime("datetime"),
    Password("password"),
    // 布尔
    Bool("boolean"),
    // 文件
    File("file"),
    UUID("uuid"),
    // 复杂类型
    Array("array"),
    Object("object"),
    NULL("none");

    private final String value;

    DataType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DataType fromValue(String value) {
        for (DataType dataType : DataType.values()) {
            if (dataType.getValue().equalsIgnoreCase(value)) {
                return dataType;
            }
        }
        throw new IllegalArgumentException("Invalid DataType value: " + value);
    }

}
