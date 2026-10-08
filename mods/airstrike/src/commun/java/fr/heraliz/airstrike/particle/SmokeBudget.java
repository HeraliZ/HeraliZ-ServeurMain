package fr.heraliz.airstrike.particle;

/**
 * Client side count of the big smoke puffs alive (updated every tick by the particles themselves). Lingering emitters
 * throttle themselves with {@link #factor()} so that stacked clouds never flood the GPU with overlapping puffs.
 */
public final class SmokeBudget {
    /** Soft limit of big puffs alive at once. */
    public static final int LIMIT = 1600;

    private static int counting;
    private static int live;

    private SmokeBudget() {
    }

    /** Called by every big puff each tick. */
    public static void count() {
        counting++;
    }

    /** Called once per client tick after the particles ticked. */
    public static void endTick() {
        live = counting;
        counting = 0;
    }

    public static int live() {
        return live;
    }

    /** 1 while well under the limit, falls to 0 when reaching it. */
    public static float factor() {
        float f = (LIMIT - live) / (LIMIT * 0.35f);
        return f < 0f ? 0f : Math.min(1f, f);
    }
}
