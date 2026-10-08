package com.caracrea.character;

/** A named preset: {@code id} is used for lang keys ({@code caracrea.<trait>.<id>}). */
public record Option(String id, Pool pool) {
    public static Option both(String id) {
        return new Option(id, Pool.BOTH);
    }

    public static Option male(String id) {
        return new Option(id, Pool.MALE);
    }

    public static Option female(String id) {
        return new Option(id, Pool.FEMALE);
    }
}
