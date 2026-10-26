package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.client.fx.Deafen;
import fr.heraliz.airstrike.client.fx.FxQuality;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import fr.heraliz.airstrike.registry.ModParticles;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Client side explosion / impact effects: fire balls, debris, smoke columns, shock waves, shake, distant booms. */
public final class ImpactFx {
    private static final RandomSource R = RandomSource.create();

    private ImpactFx() {
    }

    private static Particle spawn(ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        return Minecraft.getInstance().particleEngine.createParticle(o, x, y, z, vx, vy, vz);
    }

    private static void sound(SoundEvent e, Vec3 p, float volume, float pitch, int delay) {
        SimpleSoundInstance s = new SimpleSoundInstance(e.getLocation(), SoundSource.BLOCKS, volume, pitch, R, false, 0,
                SoundInstance.Attenuation.LINEAR, p.x, p.y, p.z, false);
        if (delay > 0) Minecraft.getInstance().getSoundManager().playDelayed(s, delay);
        else Minecraft.getInstance().getSoundManager().play(s);
    }

    public static void play(ImpactPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 p = new Vec3(pkt.x, pkt.y, pkt.z);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(p);
        int soundDelay = (int) Math.min(60, dist / 17.0);
        float q = FxQuality.at(dist);
        switch (pkt.kind) {
            case ImpactPacket.BOMB -> explosion(level, p, 1.0f, dist, soundDelay, q);
            case ImpactPacket.HEAVY -> explosion(level, p, 2.1f, dist, soundDelay, q);
            case ImpactPacket.SMALL -> explosion(level, p, 0.5f, dist, soundDelay, q);
            case ImpactPacket.NAPALM -> napalm(level, p, dist, soundDelay, q);
            case ImpactPacket.POP -> {
                spawn(ParticleTypes.FLASH, p.x, p.y, p.z, 0, 0, 0);
                for (int i = 0, n = FxQuality.count(10, q, 3); i < n; i++) {
                    spawn(SmokeParticleOptions.of(0x8A8A8A, 1.5f + R.nextFloat(), 50, 0.02f, 0.8f), p.x, p.y, p.z,
                            R.nextGaussian() * 0.25, R.nextGaussian() * 0.1, R.nextGaussian() * 0.25);
                }
                for (int i = 0, n = dist > 140 ? 0 : FxQuality.count(12, q, 2); i < n; i++) {
                    spawn(ModParticles.SPARK.get(), p.x, p.y, p.z, R.nextGaussian() * 0.5, R.nextGaussian() * 0.3, R.nextGaussian() * 0.5);
                }
            }
            case ImpactPacket.CANISTER -> {
                if (dist < 120) debris(level, p, FxQuality.count(12, q, 3), 0.35);
                for (int i = 0, n = FxQuality.count(6, q, 2); i < n; i++) {
                    spawn(SmokeParticleOptions.of(0x9B8F7A, 1.4f + R.nextFloat(), 40, 0.03f, 0.7f), p.x, p.y + 0.3, p.z,
                            R.nextGaussian() * 0.12, 0.05, R.nextGaussian() * 0.12);
                }
                sound(ModSounds.CANISTER_POP.get(), p, 2.0f, 0.7f, soundDelay);
            }
            case ImpactPacket.BULLET -> {
                // a strafing run lands ~200 rounds: far away only the dust puff is worth drawing
                if (dist < 100) {
                    debris(level, p, FxQuality.count(5, q, 1), 0.45);
                    spawn(ParticleTypes.CRIT, p.x, p.y + 0.1, p.z, 0, 0.3, 0);
                }
                spawn(SmokeParticleOptions.of(0x7A6E5E, 1.0f + R.nextFloat() * 0.8f, 30, 0.04f, 0.8f), p.x, p.y + 0.3, p.z,
                        R.nextGaussian() * 0.05, 0.08, R.nextGaussian() * 0.05);
                for (int i = 0, n = dist > 100 ? 0 : FxQuality.count(3, q, 1); i < n; i++) {
                    spawn(ModParticles.SPARK.get(), p.x, p.y + 0.1, p.z, R.nextGaussian() * 0.3, 0.3 + R.nextFloat() * 0.3, R.nextGaussian() * 0.3);
                }
                if (R.nextInt(2) == 0) sound(ModSounds.BULLET_IMPACT.get(), p, 1.5f, 0.8f + R.nextFloat() * 0.4f, 0);
                if (dist < 40) CameraShake.add(0.05f);
            }
            case ImpactPacket.CRATE_LAND -> {
                debris(level, p, FxQuality.count(50, q, 10), 0.35);
                for (int i = 0, n = FxQuality.count(32, q, 10); i < n; i++) {
                    double a = i / (double) n * Math.PI * 2;
                    spawn(SmokeParticleOptions.of(0xA89B80, 2.0f + R.nextFloat() * 1.0f, 45, 0.0f, 0.6f),
                            p.x + Math.cos(a) * 1.8, p.y + 0.2, p.z + Math.sin(a) * 1.8, Math.cos(a) * 0.35, 0.02, Math.sin(a) * 0.35);
                }
                if (dist < 50) CameraShake.add(0.22f);
            }
            default -> {
            }
        }
    }

    private static BlockState groundState(ClientLevel level, Vec3 p) {
        BlockPos bp = BlockPos.containing(p.x, p.y - 0.5, p.z);
        BlockState s = level.getBlockState(bp);
        if (s.isAir()) s = level.getBlockState(bp.below());
        return s;
    }

    private static void debris(ClientLevel level, Vec3 p, int count, double speed) {
        BlockState state = groundState(level, p);
        if (state.isAir()) return;
        BlockParticleOption o = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < count; i++) {
            spawn(o, p.x, p.y + 0.2, p.z, R.nextGaussian() * speed, speed * (1.2 + R.nextFloat() * 2.5), R.nextGaussian() * speed);
        }
    }

    private static void explosion(ClientLevel level, Vec3 p, float scale, double dist, int soundDelay, float q) {
        // fewer particles far away or with a lower particle setting, a bit bigger to keep the same mass
        float g = FxQuality.grow(q);
        // flash + fire balls
        spawn(ParticleTypes.FLASH, p.x, p.y + 1, p.z, 0, 0, 0);
        int fire = FxQuality.count((int) (14 * scale) + 4, q, 3);
        for (int i = 0; i < fire; i++) {
            Particle f = spawn(ModParticles.FIREBALL.get(), p.x + R.nextGaussian() * 1.2 * scale, p.y + 0.5 + R.nextFloat() * 2 * scale,
                    p.z + R.nextGaussian() * 1.2 * scale, R.nextGaussian() * 0.15 * scale, 0.05 + R.nextFloat() * 0.25 * scale,
                    R.nextGaussian() * 0.15 * scale);
            if (f != null) f.scale((1.2f + scale * 1.6f) * g);
        }
        // mushroom: dark column + cap
        // big dark puffs also follow the global smoke budget (stacked clouds)
        float budget = Math.max(0.35f, fr.heraliz.airstrike.particle.SmokeBudget.factor());
        int smoke = FxQuality.count((int) (((int) (22 * scale) + 6) * budget), q, 4);
        for (int i = 0; i < smoke; i++) {
            float grey = 0.12f + R.nextFloat() * 0.18f;
            int c = ((int) (grey * 255) << 16) | ((int) (grey * 240) << 8) | (int) (grey * 225);
            double h = R.nextFloat();
            spawn(SmokeParticleOptions.of(c, (3.0f + R.nextFloat() * 3.0f) * (0.6f + scale * 0.5f) * g, 140 + R.nextInt(120), 0.05f + (float) h * 0.06f, 0.95f),
                    p.x + R.nextGaussian() * 1.5 * scale, p.y + 1 + h * 3 * scale, p.z + R.nextGaussian() * 1.5 * scale,
                    R.nextGaussian() * 0.06, 0.1 + h * 0.5 * scale, R.nextGaussian() * 0.06);
        }
        // dust ring
        int ring = dist > 220 ? 0 : FxQuality.count((int) (18 * scale) + 8, q, 6);
        for (int i = 0; i < ring; i++) {
            double a = i / (double) ring * Math.PI * 2 + R.nextFloat() * 0.3;
            double sp = (0.5 + R.nextFloat() * 0.4) * (0.6 + scale * 0.6);
            spawn(SmokeParticleOptions.of(0x9C8E74, (2.2f + R.nextFloat() * 2.0f * scale) * g, 70 + R.nextInt(50), 0.005f, 0.75f),
                    p.x, p.y + 0.4, p.z, Math.cos(a) * sp, 0.02, Math.sin(a) * sp);
        }
        Particle ring0 = spawn(ModParticles.SHOCKWAVE.get(), p.x, p.y + 0.15, p.z, 0, 0, 0);
        if (ring0 != null) ring0.scale(scale);
        // debris and sparks are a few pixels wide beyond ~130 blocks
        if (dist < 130) debris(level, p, FxQuality.count((int) (40 * scale) + 6, q, 4), 0.35 * (0.6 + scale * 0.5));
        for (int i = 0, n = dist > 150 ? 0 : FxQuality.count((int) (26 * scale) + 4, q, 3); i < n; i++) {
            spawn(ModParticles.SPARK.get(), p.x, p.y + 0.5, p.z, R.nextGaussian() * 0.6 * scale, 0.4 + R.nextFloat() * 0.9 * scale,
                    R.nextGaussian() * 0.6 * scale);
        }

        // sound: close crack + distant rumble (delayed by the speed of sound)
        if (dist < 90) sound(ModSounds.EXPLOSION_NEAR.get(), p, 3.0f * scale + 1.0f, 0.9f + R.nextFloat() * 0.2f, soundDelay);
        if (dist > 25) sound(ModSounds.EXPLOSION_FAR.get(), p, 6.0f, 0.85f + R.nextFloat() * 0.3f - (scale > 1.2f ? 0.15f : 0f), soundDelay);
        float shake = (float) Math.max(0.0, 1.0 - dist / (70.0 * scale + 20.0)) * 0.9f * scale;
        if (shake > 0) CameraShake.add(shake);
        Deafen.hit((float) (1.0 - dist / (10.0 + 14.0 * scale)));
    }

    private static void napalm(ClientLevel level, Vec3 p, double dist, int soundDelay, float q) {
        float g = FxQuality.grow(q);
        spawn(ParticleTypes.FLASH, p.x, p.y + 1, p.z, 0, 0, 0);
        for (int i = 0, n = FxQuality.count(40, q, 6); i < n; i++) {
            double a = R.nextFloat() * Math.PI * 2;
            double sp = 0.25 + R.nextFloat() * 0.45;
            Particle f = spawn(ModParticles.FIREBALL.get(), p.x, p.y + 0.6 + R.nextFloat() * 1.5, p.z,
                    Math.cos(a) * sp, 0.08 + R.nextFloat() * 0.25, Math.sin(a) * sp);
            if (f != null) f.scale((1.8f + R.nextFloat() * 1.5f) * g);
        }
        for (int i = 0, n = FxQuality.count(26, q, 4); i < n; i++) {
            spawn(SmokeParticleOptions.of(0x1A1715, (3.5f + R.nextFloat() * 3f) * g, 160 + R.nextInt(80), 0.08f, 0.9f),
                    p.x + R.nextGaussian() * 2.5, p.y + 1.5 + R.nextFloat() * 3, p.z + R.nextGaussian() * 2.5,
                    R.nextGaussian() * 0.05, 0.2 + R.nextFloat() * 0.3, R.nextGaussian() * 0.05);
        }
        for (int i = 0, n = dist > 150 ? 0 : FxQuality.count(30, q, 4); i < n; i++) {
            spawn(ParticleTypes.LAVA, p.x, p.y + 0.5, p.z, 0, 0, 0);
        }
        if (dist < 90) sound(ModSounds.EXPLOSION_NEAR.get(), p, 2.5f, 1.2f, soundDelay);
        if (dist > 25) sound(ModSounds.EXPLOSION_FAR.get(), p, 4.0f, 1.1f, soundDelay);
        float shake = (float) Math.max(0.0, 1.0 - dist / 60.0) * 0.5f;
        if (shake > 0) CameraShake.add(shake);
        Deafen.hit((float) (0.7 * (1.0 - dist / 16.0)));
    }
}
