package fr.heralisz.clans.territory.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Fumee rouge du signal : volute qui monte lentement, grossit (x 2,4), tourne un peu, rouge sombre nuance, fondu
 * d'entree et de sortie. Eclairee par le monde (melange alpha).
 */
@OnlyIn(Dist.CLIENT)
public class FumeeParticle extends TextureSheetParticle {
    private final float taille0;
    private final float spin;

    protected FumeeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 90 + random.nextInt(50);
        this.gravity = -0.012f;
        this.friction = 0.975f;
        this.hasPhysics = false;
        float n = random.nextFloat();
        this.rCol = 0.62f + 0.2f * n;
        this.gCol = 0.07f + 0.05f * n;
        this.bCol = 0.07f + 0.04f * n;
        this.taille0 = 0.7f + random.nextFloat() * 0.4f;
        this.quadSize = taille0;
        this.roll = random.nextFloat() * 6.2831f;
        this.oRoll = roll;
        this.spin = (random.nextFloat() - 0.5f) * 0.04f;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        // le vent la pousse un peu de cote
        xd += (random.nextFloat() - 0.5f) * 0.0015;
        zd += (random.nextFloat() - 0.5f) * 0.0015;
        float v = (float) age / lifetime;
        alpha = (v < 0.12f ? v / 0.12f : v > 0.55f ? 1 - (v - 0.55f) / 0.45f : 1f) * 0.62f;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float v = (age + partialTick) / lifetime;
        return taille0 * (1f + 1.4f * v);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new FumeeParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
