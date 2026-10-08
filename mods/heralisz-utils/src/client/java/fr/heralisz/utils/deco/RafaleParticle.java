package fr.heralisz.utils.deco;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Particules du vent de la faille (côté client). Nappe : grand voile blanc translucide qui naît, file avec la rafale en
 * s'étirant puis s'efface. Trait : strie fine et longue, couchée dans le sens de sa vitesse (deux faces : visible de
 * partout). Ni collision ni gravité (rien ne reste coincé dans une paroi), durée de vie courte et bornée ; un peu de
 * lumière même dans le noir de la faille (sinon on ne voit rien passer).
 */
public class RafaleParticle extends TextureSheetParticle {
    private final float alphaMax;
    private final boolean trait;
    private final float longueur;

    RafaleParticle(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean trait) {
        super(l, x, y, z, 0, 0, 0);
        this.trait = trait;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = trait ? 0.99f : 0.965f;
        float blanc = 0.9f + random.nextFloat() * 0.1f;
        this.rCol = blanc;
        this.gCol = blanc;
        this.bCol = Math.min(1f, blanc + 0.03f);
        this.alpha = 0f;
        if (trait) {
            this.lifetime = 12 + random.nextInt(9);
            this.quadSize = 0.05f + random.nextFloat() * 0.04f;            // demi-largeur de la strie
            this.longueur = 3.0f + random.nextFloat() * 4.0f;
            this.alphaMax = 0.65f + random.nextFloat() * 0.2f;
        } else {
            this.lifetime = 28 + random.nextInt(17);
            this.quadSize = 1.4f + random.nextFloat() * 1.6f;
            this.longueur = 0f;
            this.alphaMax = 0.32f + random.nextFloat() * 0.18f;
            this.roll = (random.nextFloat() - 0.5f) * 0.3f;              // couché à l'horizontale : se lit comme du vent
            this.oRoll = this.roll;
        }
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float f = (age + 0.5f) / lifetime;
        float entree = Mth.clamp(f / 0.2f, 0f, 1f);
        float sortie = Mth.clamp((1f - f) / 0.45f, 0f, 1f);
        this.alpha = alphaMax * entree * sortie;
    }

    @Override
    public float getQuadSize(float pt) {
        if (trait) return quadSize;
        return quadSize * (0.75f + 0.55f * Mth.clamp((age + pt) / lifetime, 0f, 1f));
    }

    @Override
    protected int getLightColor(float pt) {
        int c = super.getLightColor(pt);
        int bloc = Math.max(LightTexture.block(c), 12);
        int ciel = Math.max(LightTexture.sky(c), 12);
        return LightTexture.pack(bloc, ciel);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer buf, Camera cam, float pt) {
        Vec3 c = cam.getPosition();
        float px = (float) (Mth.lerp(pt, xo, x) - c.x);
        float py = (float) (Mth.lerp(pt, yo, y) - c.y);
        float pz = (float) (Mth.lerp(pt, zo, z) - c.z);
        // estompé tout près de la caméra : un voile collé à l'écran ne serait qu'un grand carré gris
        float d = Mth.sqrt(px * px + py * py + pz * pz);
        float pres = trait ? Mth.clamp((d - 0.5f) / 2f, 0f, 1f) : Mth.clamp((d - 1.5f) / 4f, 0f, 1f);
        if (pres <= 0f) {
            return;
        }
        float a0 = alpha;
        alpha = a0 * pres;
        try {
            if (trait) {
                strie(buf, px, py, pz, pt);
            } else {
                super.render(buf, cam, pt);
            }
        } finally {
            alpha = a0;
        }
    }

    private void strie(VertexConsumer buf, float px, float py, float pz, float pt) {
        Vector3f axe = new Vector3f((float) xd, (float) yd, (float) zd);
        if (axe.lengthSquared() < 1e-8f) axe.set(0, 0, 1);
        axe.normalize();
        Vector3f cote = new Vector3f(axe).cross(px, py, pz);
        if (cote.lengthSquared() < 1e-8f) cote.set(0, 1, 0);
        cote.normalize().mul(quadSize);
        Vector3f demi = new Vector3f(axe).mul(longueur * 0.5f);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int lum = getLightColor(pt);
        float[][] p = {
                {px - demi.x - cote.x, py - demi.y - cote.y, pz - demi.z - cote.z, u0, v1},
                {px - demi.x + cote.x, py - demi.y + cote.y, pz - demi.z + cote.z, u0, v0},
                {px + demi.x + cote.x, py + demi.y + cote.y, pz + demi.z + cote.z, u1, v0},
                {px + demi.x - cote.x, py + demi.y - cote.y, pz + demi.z - cote.z, u1, v1}};
        for (int k = 0; k < 4; k++) sommet(buf, p[k], lum);
        for (int k = 3; k >= 0; k--) sommet(buf, p[k], lum);                // deux faces
    }

    private void sommet(VertexConsumer buf, float[] s, int lum) {
        buf.vertex(s[0], s[1], s[2]).uv(s[3], s[4]).color(rCol, gCol, bCol, alpha).uv2(lum).endVertex();
    }

    /** Nappe de rafale. */
    public static final class Nappe implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Nappe(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new RafaleParticle(l, x, y, z, vx, vy, vz, sprites, false);
        }
    }

    /** Trait de vent. */
    public static final class Trait implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Trait(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new RafaleParticle(l, x, y, z, vx, vy, vz, sprites, true);
        }
    }
}
