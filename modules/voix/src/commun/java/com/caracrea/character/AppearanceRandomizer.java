package com.caracrea.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Random but plausible characters: presets are drawn from the pool of the
 * body type, natural hair colours are far more common than dyed ones, faces
 * and clothes are clean, and clothes are mostly muted colours.
 */
public final class AppearanceRandomizer {
    private AppearanceRandomizer() {
    }

    /** Deterministic character for a player who never chose one (seeded by their UUID). */
    /**
     * The appearance a player has before choosing one: a random but stable character derived from the UUID. The
     * server (AppearanceStore) and the clients compute the same one.
     */
    public static Appearance defaultFor(java.util.UUID uuid) {
        return forSeed(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
    }

    public static Appearance forSeed(long seed) {
        return random(new Random(seed));
    }

    public static Appearance random(Random random) {
        // Draw kept from the time of the female body, so the default look of every player stays the same.
        random.nextInt(2);
        return random(random, BodyType.MALE);
    }

    public static Appearance random(Random random, BodyType type) {
        int[] values = new int[Trait.VALUES.length];
        values[Trait.BODY_TYPE.ordinal()] = type.ordinal();
        values[Trait.SKIN_TONE.ordinal()] = random.nextInt(Catalog.SKIN_TONES.size());
        values[Trait.EYE_SHAPE.ordinal()] = random.nextInt(Catalog.EYE_SHAPES.size());
        values[Trait.EYE_COLOR.ordinal()] = weighted(random, 3, 3, 1, 2, 2, 2, 2, 1, 1, 1);
        values[Trait.EYE_SIZE.ordinal()] = slider(random);
        values[Trait.EYE_SPACING.ordinal()] = slider(random);
        values[Trait.EYE_HEIGHT.ordinal()] = slider(random);
        values[Trait.IRIS_SIZE.ordinal()] = slider(random);
        values[Trait.BROW_STYLE.ordinal()] = random.nextInt(Catalog.BROW_STYLES.size());
        values[Trait.NOSE_STYLE.ordinal()] = random.nextInt(Catalog.NOSE_STYLES.size());
        values[Trait.MOUTH_STYLE.ordinal()] = weighted(random, 4, 2, 2, 1, 1);
        values[Trait.MOUTH_WIDTH.ordinal()] = slider(random);
        values[Trait.LIP_COLOR.ordinal()] = type == BodyType.FEMALE ? weighted(random, 5, 2, 1, 1) : 0;
        values[Trait.HAIR_STYLE.ordinal()] = pooled(random, Trait.HAIR_STYLE, type);
        // Natural colours (indices 0-14) most of the time, dyed ones rarely.
        values[Trait.HAIR_COLOR.ordinal()] = random.nextInt(10) == 0
                ? 15 + random.nextInt(Catalog.HAIR_COLORS.size() - 15)
                : weighted(random, 3, 3, 4, 4, 3, 3, 2, 2, 1, 1, 1, 1, 1, 1, 1);
        values[Trait.FACIAL_HAIR.ordinal()] = type == BodyType.MALE ? weighted(random, 4, 3, 1, 1, 1, 2, 1) : 0;
        values[Trait.SHIRT_STYLE.ordinal()] = pooled(random, Trait.SHIRT_STYLE, type);
        values[Trait.SHIRT_COLOR.ordinal()] = random.nextInt(Catalog.CLOTH_COLORS.size());
        values[Trait.SHIRT_COLOR2.ordinal()] = random.nextInt(Catalog.CLOTH_COLORS.size());
        values[Trait.PANTS_STYLE.ordinal()] = pooled(random, Trait.PANTS_STYLE, type);
        // Trousers: mostly dark and denim shades.
        values[Trait.PANTS_COLOR.ordinal()] = weighted(random, 4, 4, 2, 1, 0, 1, 3, 5, 1, 3, 2, 3, 2, 1, 0, 0, 0, 0, 0);
        values[Trait.SHOES_STYLE.ordinal()] = weighted(random, 4, 3, 3, 2, 1, 1, 1, 0);
        values[Trait.SHOES_COLOR.ordinal()] = weighted(random, 5, 3, 2, 1, 2, 0, 1, 1, 0, 2, 1, 1, 5, 1, 1, 0, 0, 0, 0);
        // Only harmless marks at random (freckles, mole): scars, dirt, blood and worn clothes
        // are left for players to pick themselves, so default characters never look gory.
        int marks = 0;
        if (random.nextInt(8) == 0) {
            marks |= Catalog.MARK_FRECKLES;
        }
        if (random.nextInt(10) == 0) {
            marks |= Catalog.MARK_MOLE;
        }
        values[Trait.MARKS.ordinal()] = marks;
        values[Trait.WEAR.ordinal()] = 0;
        return Appearance.of(values);
    }

    /** Slider value biased towards the middle. */
    private static int slider(Random random) {
        return weighted(random, 1, 2, 4, 2, 1);
    }

    private static int pooled(Random random, Trait trait, BodyType type) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < Catalog.count(trait); i++) {
            if (Catalog.suits(trait, i, type)) {
                candidates.add(i);
            }
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static int weighted(Random random, int... weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int pick = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
