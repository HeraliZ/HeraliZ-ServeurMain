package fr.heraliz.crac.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Burning fuse of the Explosive Charge: the "fuse" bone shrinks toward the can, the glowing "ember" bone rides the
 * burning end and the renderer throws sparks from its world position. Bone names come from tools/art/gen_explosives.py.
 */
public final class ExplosiveChargeFuse {
    /** Fuse cord length in model px (must match gen_explosives.py). */
    public static final float FUSE_PX = 3.4F;
    /** Shortest stub left when the charge goes off. */
    private static final float MIN_LEFT = 0.08F;

    private ExplosiveChargeFuse() {
    }

    /** @param left remaining fuse fraction 1..0 */
    public static void applyBone(GeoBone bone, boolean lit, float left) {
        float l = Math.max(MIN_LEFT, Math.min(1.0F, left));
        switch (bone.getName()) {
            case "fuse" -> bone.setScaleZ(l);
            case "ember" -> {
                bone.setHidden(!lit);
                bone.setPosZ(-(1.0F - l) * FUSE_PX);
                bone.setTrackingMatrices(lit);
            }
            default -> {
            }
        }
    }

    public static float left(double elapsed, double fuse) {
        return elapsed < 0.0 ? 1.0F : (float)Math.max(0.0, 1.0 - elapsed / Math.max(0.05, fuse));
    }

    /** Sparks, a lick of flame and wisps of smoke at the burning end (called once per client tick). */
    public static void spawnSparks(GeoBone ember, RandomSource r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        Vector3d p = ember.getWorldPosition();
        ParticleEngine pe = mc.particleEngine;
        int sparks = 2 + r.nextInt(2);
        for (int k = 0; k < sparks; k++) {
            Particle s = pe.createParticle(ParticleTypes.FIREWORK, p.x, p.y, p.z,
                (r.nextDouble() - 0.5) * 0.11, r.nextDouble() * 0.09 + 0.02, (r.nextDouble() - 0.5) * 0.11);
            if (s != null) {
                s.setColor(1.0F, 0.6F + r.nextFloat() * 0.32F, 0.16F + r.nextFloat() * 0.22F);
                s.setLifetime(5 + r.nextInt(8));
                s.scale(0.38F + r.nextFloat() * 0.22F);
            }
        }
        if (r.nextInt(2) == 0) {
            Particle f = pe.createParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 0.0, 0.004, 0.0);
            if (f != null) {
                f.setLifetime(3 + r.nextInt(3));
            }
        }
        if (r.nextInt(4) == 0) {
            Particle smoke = pe.createParticle(ParticleTypes.SMOKE, p.x, p.y + 0.03, p.z, 0.0, 0.012, 0.0);
            if (smoke != null) {
                float g = 0.42F + r.nextFloat() * 0.15F;   // vanilla smoke is near-black: keep it a light fuse wisp
                smoke.setColor(g, g, g);
            }
        }
    }
}
