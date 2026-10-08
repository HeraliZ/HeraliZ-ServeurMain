package fr.heraliz.airstrike.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Animated, self lit ball of fire. */
public class FireballParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseSize;

    protected FireballParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.9f;
        this.gravity = -0.01f;
        this.hasPhysics = false;
        this.lifetime = 14 + random.nextInt(10);
        this.baseSize = 1.3f + random.nextFloat() * 1.2f;
        this.quadSize = baseSize;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        setSpriteFromAge(sprites);
    }

    @Override
    public Particle scale(float f) {
        super.scale(f);
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) setSpriteFromAge(sprites);
        float t = age / (float) lifetime;
        alpha = Mth.clamp((1f - t) * 1.6f, 0f, 1f);
    }

    @Override
    public float getQuadSize(float pt) {
        float t = (age + pt) / lifetime;
        return quadSize * (0.6f + 0.6f * (1f - (1f - t) * (1f - t)));
    }

    @Override
    protected int getLightColor(float pt) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ModParticleRenderTypes.GLOW;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new FireballParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
