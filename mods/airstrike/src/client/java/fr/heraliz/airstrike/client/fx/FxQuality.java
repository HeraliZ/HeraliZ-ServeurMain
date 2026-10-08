package fr.heraliz.airstrike.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;

/**
 * How many particles an effect may spawn: follows the player's particle setting and the distance to the camera
 * (far effects use fewer, bigger particles), plus a per tick budget so that many impacts landing together (cluster
 * bomb, carpet bombing) never spawn thousands of particles in one frame.
 */
public final class FxQuality {
    /** Particles our effects may spawn in one client tick before they get thinned out. */
    private static final int TICK_BUDGET = 700;
    private static int spawnedThisTick;

    private FxQuality() {
    }

    /** 1 = all, 0.5 = decreased, 0.2 = minimal. */
    public static float setting() {
        ParticleStatus s = Minecraft.getInstance().options.particles().get();
        return s == ParticleStatus.ALL ? 1f : s == ParticleStatus.DECREASED ? 0.5f : 0.2f;
    }

    /** 1 up to 64 blocks, then down to 0.2 at 320 blocks and beyond. */
    public static float distance(double dist) {
        if (dist <= 64) return 1f;
        return Mth.clamp(1f - 0.8f * (float) ((dist - 64) / 256), 0.2f, 1f);
    }

    /** Combined multiplier for an effect at this distance, including what is left of the tick budget. */
    public static float at(double dist) {
        float budget = Mth.clamp(1f - (spawnedThisTick - TICK_BUDGET * 0.5f) / (TICK_BUDGET * 0.5f), 0.1f, 1f);
        return setting() * distance(dist) * budget;
    }

    /** {@link #at(double)} for an effect at this position (distance to the camera). */
    public static float at(double x, double y, double z) {
        return at(cameraDistance(x, y, z));
    }

    /**
     * Distance only, for effects that matter to gameplay (smoke screens, gas): they must hide the same whatever the
     * particle setting, so only far away clouds use fewer, bigger puffs (same coverage).
     */
    public static float gameplay(double x, double y, double z) {
        return distance(cameraDistance(x, y, z));
    }

    private static double cameraDistance(double x, double y, double z) {
        return Math.sqrt(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z));
    }

    /** n scaled by q, at least min; the result is counted against the tick budget. */
    public static int count(int n, float q, int min) {
        int c = Math.max(min, Math.round(n * q));
        spawnedThisTick += c;
        return c;
    }

    /** Size multiplier keeping the look of an effect drawn with fewer particles. */
    public static float grow(float q) {
        return 1f + 0.6f * (1f - q);
    }

    public static void endTick() {
        spawnedThisTick = 0;
    }
}
