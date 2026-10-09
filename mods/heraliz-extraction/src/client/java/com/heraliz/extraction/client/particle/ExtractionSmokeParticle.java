package com.heraliz.extraction.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Grosse fumée de fumigène, rouge sur toute la hauteur de la colonne. */
public class ExtractionSmokeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;

    protected ExtractionSmokeParticle(ClientLevel level, double x, double y, double z,
                                      double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        // ~0.24 b/t avec friction 0.996 sur ~230 ticks : la colonne monte à ~35 blocs
        this.friction = 0.996F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = 200 + this.random.nextInt(60);
        this.startSize = 0.45F + this.random.nextFloat() * 0.25F;
        this.quadSize = this.startSize;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        updateLook();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        // léger vent + tourbillon
        this.xd += (this.random.nextFloat() - 0.5F) * 0.004F + 0.0008F;
        this.zd += (this.random.nextFloat() - 0.5F) * 0.004F;
        this.oRoll = this.roll;
        this.roll += 0.01F;
        updateLook();
    }

    private void updateLook() {
        setSpriteFromAge(this.sprites);
        float t = (float) this.age / this.lifetime;
        this.quadSize = this.startSize * (1.0F + t * 6.0F);

        // fumigène rouge du début à la fin : rouge vif à la base, rouge un peu plus profond en haut
        this.rCol = Mth.lerp(t, 1.0F, 0.85F);
        this.gCol = Mth.lerp(t, 0.22F, 0.12F);
        this.bCol = Mth.lerp(t, 0.14F, 0.1F);

        float fadeIn = Mth.clamp(this.age / 6.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - t) * 3.0F, 0.0F, 1.0F);
        this.alpha = 0.85F * fadeIn * fadeOut;
    }

    @Override
    protected int getLightColor(float partialTick) {
        // toujours pleine luminosité : la colonne reste bien rouge, même la nuit et tout en haut
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
            return new ExtractionSmokeParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
