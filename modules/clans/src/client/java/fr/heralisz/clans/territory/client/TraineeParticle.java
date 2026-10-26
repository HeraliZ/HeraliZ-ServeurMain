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
 * Trainee de condensation derriere les moteurs de l'avion : blanc-gris, grossit (x 3,5), se dissipe en 3 s, sans
 * gravite. Emise cote client par AvionEntity (deux par moteur et par tick).
 */
@OnlyIn(Dist.CLIENT)
public class TraineeParticle extends TextureSheetParticle {
    private final float taille0;

    protected TraineeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 50 + random.nextInt(25);
        this.gravity = 0f;
        this.friction = 0.97f;
        this.hasPhysics = false;
        float n = 0.88f + random.nextFloat() * 0.1f;
        this.rCol = n;
        this.gCol = n;
        this.bCol = n;
        this.taille0 = 0.5f + random.nextFloat() * 0.3f;
        this.quadSize = taille0;
        this.roll = random.nextFloat() * 6.2831f;
        this.oRoll = roll;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float v = (float) age / lifetime;
        alpha = (v < 0.1f ? v / 0.1f : 1 - (v - 0.1f) / 0.9f) * 0.5f;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float v = (age + partialTick) / lifetime;
        return taille0 * (1f + 2.5f * v);
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
            return new TraineeParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
