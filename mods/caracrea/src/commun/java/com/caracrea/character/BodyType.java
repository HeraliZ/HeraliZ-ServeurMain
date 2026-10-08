package com.caracrea.character;

public enum BodyType {
    MALE("default"),
    FEMALE("slim");

    private final String modelName;

    BodyType(String modelName) {
        this.modelName = modelName;
    }

    /** Vanilla player model name: "default" (4px arms) or "slim" (3px arms). */
    public String modelName() {
        return modelName;
    }

    public boolean slim() {
        return this == FEMALE;
    }

    public static BodyType of(int index) {
        return index == 1 ? FEMALE : MALE;
    }
}
