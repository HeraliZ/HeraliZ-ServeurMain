package fr.heraliz.airstrike.client.fx;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Ears ringing after a close explosion: a tinnitus tone and every other sound muffled for a few seconds. The duck is
 * the listener gain (the master volume the game applies), so nothing is changed in the player's options; it is
 * restored as soon as the effect is over or the player leaves.
 */
public final class Deafen {
    /** Strongest duck: the world keeps this share of its volume. */
    private static final float FLOOR = 0.28f;
    /** Rise per tick: the bang itself is heard before the world goes muffled. */
    private static final float ATTACK = 0.08f;
    private static float level, target;
    private static int hold;
    private static boolean ducked;

    private Deafen() {
    }

    private static boolean enabled() {
        try {
            return AirstrikeConfig.DEAFENING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** An explosion went off; intensity 0..1 from the distance and the size of the blast. */
    public static void hit(float intensity) {
        if (intensity < 0.15f || !enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return;
        if (intensity > target + 0.1f) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TINNITUS.get(), 0.97f + intensity * 0.06f,
                    0.35f + 0.65f * intensity));
        }
        target = Math.max(target, intensity);
        hold = Math.max(hold, (int) (18 + 30 * intensity));
    }

    public static void tick() {
        if (target <= 0f && !ducked) return;
        if (hold > 0) hold--;
        else target = Math.max(0f, target - 0.011f);
        level = level < target ? Math.min(target, level + ATTACK) : target;
        apply();
    }

    private static void apply() {
        Minecraft mc = Minecraft.getInstance();
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        if (level <= 0f) {
            mc.getSoundManager().updateSourceVolume(SoundSource.MASTER, master);
            ducked = false;
            return;
        }
        float k = level * level * (3f - 2f * level);
        mc.getSoundManager().updateSourceVolume(SoundSource.MASTER, master * (1f - (1f - FLOOR) * k));
        ducked = true;
    }

    /** Current strength 0..1 (development checks). */
    public static float level() {
        return level;
    }

    /** Leaving the world: give the volume back right away. */
    public static void reset() {
        level = target = 0f;
        hold = 0;
        if (ducked) apply();
    }
}
