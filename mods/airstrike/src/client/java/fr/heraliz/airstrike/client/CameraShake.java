package fr.heraliz.airstrike.client;

import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

/** Trauma based camera shake (explosions, low pass of the jet). */
public final class CameraShake {
    private static float trauma;
    private static float time;

    private CameraShake() {
    }

    public static void add(float amount) {
        trauma = Math.min(1.2f, trauma + amount);
    }

    public static void tick() {
        trauma = Math.max(0f, trauma * 0.9f - 0.005f);
        time += 1f;
    }

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0.001f) return;
        float s = trauma * trauma;
        float t = (float) (time + event.getPartialTick());
        float yaw = (Mth.sin(t * 1.9f) + Mth.sin(t * 3.7f + 1.3f) * 0.5f) * 2.2f * s;
        float pitch = (Mth.sin(t * 2.3f + 0.7f) + Mth.sin(t * 4.1f + 2.1f) * 0.5f) * 2.2f * s;
        float roll = (Mth.sin(t * 1.6f + 2.9f)) * 3.0f * s;
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }
}
