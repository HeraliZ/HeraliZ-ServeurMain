package com.heraliz.extraction.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Étincelle brûlante : jaillit, retombe, passe du blanc au rouge. */
public class ExtractionSparkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;

    protected ExtractionSparkParticle(ClientLevel level, double x, double y, double z,
                                      double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.94F;
        this.gravity = 0.6F;
        this.hasPhysics = true;
        this.lifetime = 14 + this.random.nextInt(16);
        this.startSize = 0.06F + this.random.nextFloat() * 0.05F;
        this.quadSize = this.startSize;
        updateLook();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            updateLook();
        }
    }

    private void updateLook() {
        setSpriteFromAge(this.sprites);
        float t = (float) this.age / this.lifetime;
        this.quadSize = this.startSize * (1.0F - t * 0.6F);
        this.rCol = 1.0F;
        this.gCol = Mth.lerp(t, 0.95F, 0.25F);
        this.bCol = Mth.lerp(t, 0.7F, 0.05F);
        this.alpha = Mth.clamp((1.0F - t) * 2.0F, 0.0F, 1.0F);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ExtractionSparkParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
