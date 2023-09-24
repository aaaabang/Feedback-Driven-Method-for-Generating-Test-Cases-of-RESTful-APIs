package org.restct.dto.keywords;

public enum ValueType {
    Enum("enum"),
    Default("default"),
    Example("example"),
    Random("random"),
    Dynamic("dynamic"),
    NULL("Null");

    private final String value;

    ValueType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ValueType fromValue(String value) {
        for (ValueType valueType : ValueType.values()) {
            if (valueType.getValue().equalsIgnoreCase(value)) {
                return valueType;
            }
        }
        throw new IllegalArgumentException("Invalid valueType value: " + value);
    }

}
