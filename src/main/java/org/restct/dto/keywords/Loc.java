package org.restct.dto.keywords;

public enum Loc {
    FormData("formData"),
    Body("body"),
    Query("query"),
    Path("path"),
    Header("header"),
    NULL("NONE");

    private final String value;

    Loc(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Loc fromValue(String value) {
        for (Loc loc : Loc.values()) {
            if (loc.getValue().equalsIgnoreCase(value)) {
                return loc;
            }
        }
        throw new IllegalArgumentException("Invalid Loc value: " + value);
    }

}
