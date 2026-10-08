package fr.heraliz.crac.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SparkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    SparkParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.friction = 0.74F;
        this.gravity = 1.3F;
        this.hasPhysics = true;
        this.xd = (this.random.nextDouble() - 0.5) * 0.34;
        this.yd = this.random.nextDouble() * 0.4 + 0.12;
        this.zd = (this.random.nextDouble() - 0.5) * 0.34;
        this.quadSize = 0.05F + this.random.nextFloat() * 0.05F;
        this.lifetime = 20 + this.random.nextInt(24);
        this.setColor(1.0F, 0.78F + this.random.nextFloat() * 0.16F, 0.1F + this.random.nextFloat() * 0.14F);
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float)this.age / this.lifetime;
        if (t > 0.6F) {
            this.alpha = Math.max(0.0F, 1.0F - (t - 0.6F) / 0.4F);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SparkParticle(level, x, y, z, this.sprites);
        }
    }
}
