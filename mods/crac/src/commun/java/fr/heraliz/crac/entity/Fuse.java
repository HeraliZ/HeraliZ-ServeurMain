package fr.heraliz.crac.entity;

/** Fuse arithmetic shared by the sticky explosives and their renderers. */
public final class Fuse {
    /** Shortest fuse, whatever the config says. */
    public static final int MIN_TICKS = 10;

    private Fuse() {
    }

    /** Fuse length in ticks for a configured length in seconds. */
    public static int ticks(double seconds) {
        return Math.max(MIN_TICKS, (int) Math.round(seconds * 20.0));
    }

    /**
     * Seconds the fuse has been burning, between 0 and its length, from the game time it was armed at. Client and
     * server share the game time, so a player who arrives mid-fuse sees the same countdown as the others.
     */
    public static double elapsedSeconds(long gameTime, long armedAt, float partialTick, int lengthTicks) {
        double ticks = gameTime - armedAt + (double) partialTick;
        return Math.max(0.0, Math.min(lengthTicks, ticks)) / 20.0;
    }
}
