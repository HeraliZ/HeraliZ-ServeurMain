package fr.heralisz.tags.client.particle;

import fr.heralisz.tags.client.render.AtlasCouleurs;
import fr.heralisz.tags.particle.BrouillardOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * Brouillard de peinture a la pose d'un tag : petite volute douce (sprite maison textures/particle/brouillard.png),
 * couleur dominante du tag (atlas) ou couleur de repli envoyee par le serveur, vie courte (8 a 14 ticks), grossit
 * et s'estompe, derive lente. Eclairee par le monde (feuille translucide).
 */
public class BrouillardParticle extends TextureSheetParticle {
    private final float tailleDepart;

    protected BrouillardParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                 BrouillardOptions o, SpriteSet sprites) {
        super(level, x, y, z);
        int rgb = AtlasCouleurs.couleur(o.cas());
        if (rgb < 0) rgb = o.repli();
        // nuance aleatoire legere pour que les volutes ne soient pas toutes identiques
        float var = 0.9f + random.nextFloat() * 0.2f;
        rCol = Math.min(1f, ((rgb >> 16) & 255) / 255f * var);
        gCol = Math.min(1f, ((rgb >> 8) & 255) / 255f * var);
        bCol = Math.min(1f, (rgb & 255) / 255f * var);
        xd = vx + (random.nextFloat() - 0.5f) * 0.02;
        yd = vy + (random.nextFloat() - 0.3f) * 0.015;
        zd = vz + (random.nextFloat() - 0.5f) * 0.02;
        lifetime = 8 + random.nextInt(7);
        tailleDepart = 0.10f + random.nextFloat() * 0.08f;
        quadSize = tailleDepart;
        alpha = 0.75f;
        gravity = 0f;
        friction = 0.92f;
        hasPhysics = false;
        roll = random.nextFloat() * 6.2831f;
        oRoll = roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float t = (float) age / lifetime;
        quadSize = tailleDepart * (1f + 1.6f * t);
        alpha = 0.75f * (1f - t * t);
        oRoll = roll;
        roll += 0.03f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Fournisseur enregistre dans ClientSetup.registerParticles. */
    public static class Provider implements ParticleProvider<BrouillardOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(BrouillardOptions o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new BrouillardParticle(level, x, y, z, vx, vy, vz, o, sprites);
        }
    }
}
