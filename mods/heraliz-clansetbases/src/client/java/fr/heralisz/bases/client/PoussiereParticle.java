package fr.heralisz.bases.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Poussiere sombre de la cloison de coque qui se desagrege (texture maison textures/particle/poussiere_coque.png,
 * tools/bases/gen_textures.py) : grains qui tombent lentement en s'eclaircissant puis s'effacent.
 */
public class PoussiereParticle extends TextureSheetParticle {
    protected PoussiereParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx + (random.nextDouble() - 0.5) * 0.04;
        this.yd = vy + random.nextDouble() * 0.03;
        this.zd = vz + (random.nextDouble() - 0.5) * 0.04;
        this.gravity = 0.35f;
        this.friction = 0.92f;
        this.lifetime = 30 + random.nextInt(30);
        this.quadSize = 0.06f + random.nextFloat() * 0.08f;
        float g = 0.10f + random.nextFloat() * 0.08f;
        this.rCol = g;
        this.gCol = g;
        this.bCol = g + 0.03f;
        this.alpha = 0.95f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        alpha = 0.95f * (1f - t * t);
        rCol = gCol = Math.min(0.35f, rCol + 0.002f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Fabrique enregistree par BasesClient. */
    public static final class Fabrique implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Fabrique(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new PoussiereParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
