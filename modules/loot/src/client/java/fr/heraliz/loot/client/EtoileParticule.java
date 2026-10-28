package fr.heraliz.loot.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Étoile d'un conteneur de loot rempli (28/10, {@code heraliz_loot:etoile} blanche, {@code etoile_or} dorée en T7) : petit
 * scintillement (images « glitter » de Minecraft, comme l'END_ROD d'avant) qui apparaît après une courte attente tirée au
 * hasard (les étoiles d'une seconde arrivent du serveur en une fois : sans ça, elles clignoteraient toutes ensemble), grandit,
 * puis s'éteint en moins d'une seconde. Conteneur vidé : le serveur n'en envoie plus, les dernières s'éteignent aussitôt.
 * Toujours en pleine lumière (visibles dans une cave), sans mouvement (pas de dérive comme l'END_ROD).
 */
public final class EtoileParticule extends TextureSheetParticle {
    private final SpriteSet sprites;
    /** Ticks d'attente avant d'apparaître (invisible pendant ce temps). */
    private final int attente;
    private final float taille;

    private EtoileParticule(ClientLevel level, double x, double y, double z, SpriteSet sprites, float r, float g, float b) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.attente = random.nextInt(14);
        this.lifetime = attente + 12 + random.nextInt(6);
        this.taille = 0.055F + random.nextFloat() * 0.03F;
        this.quadSize = 0;
        this.hasPhysics = false;
        this.gravity = 0;
        this.xd = this.yd = this.zd = 0;
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.alpha = 0;
        setSprite(sprites.get(0, 1));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        int vie = lifetime - attente;
        int t = age - attente;
        if (t < 0) {
            alpha = 0;
            quadSize = 0;
            return;
        }
        float f = (float) t / Math.max(1, vie);                  // 0 -> 1 sur la vie visible
        float eclat = f < 0.25F ? f / 0.25F : 1F - (f - 0.25F) / 0.75F;
        alpha = Math.max(0F, Math.min(1F, eclat));
        quadSize = taille * (0.4F + 0.6F * alpha);
        setSprite(sprites.get(t, vie));                           // glitter_7 -> glitter_0, comme l'END_ROD
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    /** Étoile blanche (T1 à T6). */
    public record Blanche(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new EtoileParticule(level, x, y, z, sprites, 1F, 1F, 1F);
        }
    }

    /** Étoile dorée (T7). */
    public record Doree(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new EtoileParticule(level, x, y, z, sprites, 1F, 0.80F, 0.32F);
        }
    }
}
