package fr.heraliz.crac.client.render;

import javax.annotation.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.entity.StickyExplosiveEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Client-side visuals shared by the raiding explosives: the C4 timer unit (countdown digits and an LED that blinks
 * faster as the fuse runs out), the fuse length and the "stuck on a face" pose.
 * Bone names come from tools/art/gen_explosives.py.
 */
public final class C4Display {
    /** Seconds between arming and detonation when the server config is not available yet. */
    private static final double DEFAULT_C4_FUSE = 7.5;
    private static final double DEFAULT_CHARGE_FUSE = 3.0;
    private static final int DASH = 10;

    private C4Display() {
    }

    // ------------------------------------------------------------------------------------------------ timer unit

    /** Bone visibility for the timer unit: one glyph per digit, LED on/off. */
    public static void applyTimerBone(GeoBone bone, int tens, int ones, boolean ledOn) {
        String n = bone.getName();
        if (n.length() > 5 && n.charAt(4) == '_') {
            if (n.startsWith("tens")) {
                bone.setHidden(Integer.parseInt(n, 5, n.length(), 10) != tens);
            } else if (n.startsWith("ones")) {
                bone.setHidden(Integer.parseInt(n, 5, n.length(), 10) != ones);
            }
        } else if (n.equals("led")) {
            bone.setHidden(!ledOn);
        }
    }

    public static int tensOf(int seconds) {
        return seconds < 0 ? DASH : Math.min(9, seconds / 10);
    }

    public static int onesOf(int seconds) {
        return seconds < 0 ? DASH : (seconds >= 100 ? 9 : seconds % 10);
    }

    /**
     * LED cadence: ~1.2 Hz when armed, accelerating to ~9 Hz, solid in the last half second.
     * Integrates the frequency so the blink never jumps backwards while it speeds up.
     */
    public static boolean ledOn(double elapsed, double fuse) {
        if (fuse - elapsed < 0.5) {
            return true;
        }
        double f0 = 1.2;
        double f1 = 9.0;
        double phase = f0 * elapsed + (f1 - f0) * elapsed * elapsed * elapsed / (3.0 * fuse * fuse);
        return phase - Math.floor(phase) < 0.35;
    }

    public static double c4FuseSeconds() {
        try {
            return Config.c4FuseSeconds.get();
        } catch (RuntimeException e) {
            return DEFAULT_C4_FUSE;
        }
    }

    public static double chargeFuseSeconds() {
        try {
            return Config.explosiveChargeFuseSeconds.get();
        } catch (RuntimeException e) {
            return DEFAULT_CHARGE_FUSE;
        }
    }

    // ----------------------------------------------------------------------------------------------- fuse clock

    /**
     * Fuse length in seconds of an explosive: the one synced by the server once it is armed (fixed when it stuck), else
     * the configured one. How long it has been burning comes from {@link StickyExplosiveEntity#fuseElapsedSeconds}.
     */
    public static double fuseSeconds(StickyExplosiveEntity e, double configured) {
        int ticks = e.isStuck() ? e.getFuseLength() : 0;
        return ticks > 0 ? ticks / 20.0 : configured;
    }

    // ------------------------------------------------------------------------------------------------------ pose

    /**
     * Orients a prop model (Y = out of the surface, Z = up, origin on the surface) for an explosive entity.
     * Replaces GeckoLib's default yaw handling: stuck on a block face the back sits flush on that face, stuck on an
     * entity it hangs upright, in flight it tumbles end over end around {@code centerPx}.
     */
    public static void orient(Entity e, boolean stuck, @Nullable Direction face, PoseStack poseStack, float ageInTicks,
                              float partialTick, float centerPx) {
        if (stuck && face != null) {
            switch (face) {
                case UP -> poseStack.mulPose(Axis.YP.rotationDegrees(restingYaw(e)));
                case DOWN -> {
                    poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(restingYaw(e)));
                }
                default -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(wallYaw(face)));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                }
            }
            // GeckoLib lifts every entity model by 0.01 after applyRotations: keep only a hair of it (no z-fight)
            poseStack.translate(0.0F, -0.008F, 0.0F);
        } else if (stuck) {
            poseStack.mulPose(Axis.YP.rotationDegrees(restingYaw(e)));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        } else {
            float yaw = Mth.lerp(partialTick, e.yRotO, e.getYRot());
            float c = centerPx / 16.0F;
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.translate(0.0F, c, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(ageInTicks * 32.0F));
            poseStack.translate(0.0F, -c, 0.0F);
        }
    }

    /** Model +Y is rotated onto the face normal, model +Z onto world up. */
    private static float wallYaw(Direction face) {
        return switch (face) {
            case SOUTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    /** Stable pseudo-random yaw for props lying on a floor/ceiling (their own yaw drifts to 0 once stopped). */
    private static float restingYaw(Entity e) {
        return (float)((e.getId() * 2654435761L >>> 8) % 360L);
    }
}
