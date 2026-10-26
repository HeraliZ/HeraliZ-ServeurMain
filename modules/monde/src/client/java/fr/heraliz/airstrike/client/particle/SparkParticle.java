package fr.heraliz.airstrike.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Hot ember thrown by explosions. */
public class SparkParticle extends TextureSheetParticle {
    protected SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0.9f;
        this.friction = 0.97f;
        this.hasPhysics = true;
        this.lifetime = 18 + random.nextInt(24);
        this.quadSize = 0.08f + random.nextFloat() * 0.08f;
        this.rCol = 1.0f;
        this.gCol = 0.55f + random.nextFloat() * 0.3f;
        this.bCol = 0.2f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        alpha = 1f - t * t;
        gCol = Math.max(0.2f, gCol - 0.01f);
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
            return new SparkParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
