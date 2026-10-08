package com.caracrea.character;

import java.util.Arrays;

/**
 * A complete character: one byte per {@link Trait}. Immutable; values are
 * always within the ranges of the {@link Catalog}, so an appearance received
 * from the network or read from disk can be painted without further checks.
 *
 * <p>This class has no Minecraft dependency so the painter can run outside
 * the game (see the preview tool).
 */
public final class Appearance {
    /** Bumped when the meaning of the bytes changes. */
    public static final int FORMAT = 1;

    private final byte[] values;
    private final int hash;

    private Appearance(byte[] values) {
        this.values = values;
        this.hash = Arrays.hashCode(values);
    }

    /**
     * Builds an appearance, bringing every value back to what can be chosen ({@link Catalog#sanitize}): whatever a
     * client sends or an old save holds, the result can be painted as is.
     */
    public static Appearance of(int[] values) {
        byte[] bytes = new byte[Trait.VALUES.length];
        // A woman saved before 1.3.0 comes back as a man (body type clamped), without the lipstick.
        boolean female = Trait.BODY_TYPE.ordinal() < values.length && values[Trait.BODY_TYPE.ordinal()] == 1;
        for (Trait trait : Trait.VALUES) {
            int value = trait.ordinal() < values.length ? values[trait.ordinal()] : 0;
            if (female && trait == Trait.LIP_COLOR) {
                value = 0;
            }
            bytes[trait.ordinal()] = (byte) Catalog.sanitize(trait, value);
        }
        return new Appearance(bytes);
    }

    /** Reads the bytes written by {@link #toBytes()}; missing or extra bytes are tolerated. */
    public static Appearance fromBytes(byte[] bytes) {
        int[] values = new int[Trait.VALUES.length];
        for (int i = 0; i < values.length && i < bytes.length; i++) {
            values[i] = bytes[i] & 0xFF;
        }
        return of(values);
    }

    public byte[] toBytes() {
        return values.clone();
    }

    public int get(Trait trait) {
        return values[trait.ordinal()] & 0xFF;
    }

    public BodyType bodyType() {
        return BodyType.of(get(Trait.BODY_TYPE));
    }

    public boolean hasMark(int markBit) {
        return (get(Trait.MARKS) & markBit) != 0;
    }

    public Appearance with(Trait trait, int value) {
        byte[] copy = values.clone();
        copy[trait.ordinal()] = (byte) Catalog.sanitize(trait, value);
        return new Appearance(copy);
    }

    public Appearance withMark(int markBit, boolean set) {
        int marks = get(Trait.MARKS);
        return with(Trait.MARKS, set ? marks | markBit : marks & ~markBit);
    }

    /**
     * Seed for the procedural details of one part of the character (hair
     * strands, fabric folds, freckles...), derived only from the traits of
     * that part: changing the eyes never reshuffles the hair. Every client
     * gets the same seed, so they all paint the same skin.
     */
    public long seed(Trait... traits) {
        long seed = 0x9E3779B97F4A7C15L;
        for (Trait trait : traits) {
            seed = (seed ^ (trait.ordinal() * 131L + get(trait))) * 0xBF58476D1CE4E5B9L;
            seed ^= seed >>> 31;
        }
        return seed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Appearance that && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("Appearance{");
        for (Trait trait : Trait.VALUES) {
            if (trait.ordinal() > 0) {
                text.append(", ");
            }
            text.append(trait.key()).append('=').append(get(trait));
        }
        return text.append('}').toString();
    }
}
