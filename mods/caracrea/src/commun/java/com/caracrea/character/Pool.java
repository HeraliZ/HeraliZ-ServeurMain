package com.caracrea.character;

/** Which body types a preset is offered to by default (any preset stays selectable by everyone). */
public enum Pool {
    MALE, FEMALE, BOTH;

    public boolean suits(BodyType type) {
        return this == BOTH || (this == MALE) == (type == BodyType.MALE);
    }
}
