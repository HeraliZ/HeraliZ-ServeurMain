package fr.heralisz.matchs.zombies.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Particules maison du mode zombies (textures generees par {@code tools/matchs/gen_zombies.py}) :
 * <ul>
 *     <li>{@link Copeau} : echardes de bois qui tombent en tournoyant ;</li>
 *     <li>{@link Cendre} : cendres sombres qui montent puis retombent doucement ;</li>
 *     <li>{@link Etincelle} : etincelles brillantes qui jaillissent et tombent ;</li>
 *     <li>{@link Lueur} : petite lueur additive qui monte (couleur passee dans la vitesse : x = rouge, y = vert, z = bleu) ;</li>
 *     <li>{@link Brume} : volute sombre qui gonfle et s'efface ;</li>
 *     <li>{@link Ame} (v3, The Giant) : ame bleu-vert qui vole en courbe d'un zombie tue jusqu'au cornet d'une cuve (la
 *     vitesse recue est le vecteur jusqu'a la cible) ;</li>
 *     <li>{@link Eclair} (v3) : eclair brise du ciel au sol a l'apparition d'un chien (6 ticks, dessine en ruban face a la
 *     camera, sans allocation par image).</li>
 * </ul>
 */
public final class ZbParticules {
    private ZbParticules() {
    }

    /** Base : une image tiree au hasard dans le jeu de sprites. */
    private abstract static class Base extends TextureSheetParticle {
        Base(ClientLevel l, double x, double y, double z, SpriteSet s) {
            super(l, x, y, z);
            this.pickSprite(s);
        }
    }

    public static final class Copeau extends Base {
        private final float vrot;

        Copeau(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z, s);
            this.xd = vx + (random.nextDouble() - 0.5) * 0.18;
            this.yd = vy + random.nextDouble() * 0.18;
            this.zd = vz + (random.nextDouble() - 0.5) * 0.18;
            this.gravity = 1.0F;
            this.friction = 0.96F;
            this.lifetime = 25 + random.nextInt(20);
            this.quadSize = 0.07F + random.nextFloat() * 0.05F;
            this.vrot = (random.nextFloat() - 0.5F) * 0.6F;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.hasPhysics = true;
        }

        @Override
        public void tick() {
            this.oRoll = this.roll;
            if (!this.onGround) this.roll += vrot;
            super.tick();
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    public static final class Cendre extends Base {
        Cendre(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z, s);
            this.xd = vx + (random.nextDouble() - 0.5) * 0.04;
            this.yd = vy + 0.02 + random.nextDouble() * 0.04;
            this.zd = vz + (random.nextDouble() - 0.5) * 0.04;
            this.gravity = 0.06F;
            this.friction = 0.94F;
            this.lifetime = 30 + random.nextInt(30);
            this.quadSize = 0.08F + random.nextFloat() * 0.07F;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = 1 - Mth.clamp((float) (age - lifetime * 0.6F) / (lifetime * 0.4F), 0, 1);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static final class Etincelle extends Base {
        Etincelle(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z, s);
            this.xd = vx + (random.nextDouble() - 0.5) * 0.25;
            this.yd = vy + random.nextDouble() * 0.25;
            this.zd = vz + (random.nextDouble() - 0.5) * 0.25;
            this.gravity = 0.9F;
            this.friction = 0.95F;
            this.lifetime = 10 + random.nextInt(14);
            this.quadSize = 0.04F + random.nextFloat() * 0.04F;
            this.hasPhysics = true;
        }

        @Override
        public void tick() {
            super.tick();
            this.quadSize *= 0.96F;
        }

        @Override
        public int getLightColor(float partiel) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static final class Lueur extends Base {
        Lueur(ClientLevel l, double x, double y, double z, double r, double g, double b, SpriteSet s) {
            super(l, x, y, z, s);
            this.rCol = (float) Mth.clamp(r, 0, 1);
            this.gCol = (float) Mth.clamp(g, 0, 1);
            this.bCol = (float) Mth.clamp(b, 0, 1);
            if (r == 0 && g == 0 && b == 0) this.rCol = this.gCol = this.bCol = 1;
            this.xd = (random.nextDouble() - 0.5) * 0.01;
            this.yd = 0.015 + random.nextDouble() * 0.02;
            this.zd = (random.nextDouble() - 0.5) * 0.01;
            this.gravity = 0;
            this.friction = 0.98F;
            this.lifetime = 30 + random.nextInt(25);
            this.quadSize = 0.06F + random.nextFloat() * 0.06F;
            this.alpha = 0;
        }

        @Override
        public void tick() {
            super.tick();
            float k = (float) age / lifetime;
            this.alpha = k < 0.2F ? k / 0.2F : 1 - (k - 0.2F) / 0.8F;
        }

        @Override
        public int getLightColor(float partiel) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static final class Brume extends Base {
        private final float tailleMax;

        Brume(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z, s);
            this.xd = vx + (random.nextDouble() - 0.5) * 0.03;
            this.yd = vy + random.nextDouble() * 0.02;
            this.zd = vz + (random.nextDouble() - 0.5) * 0.03;
            this.gravity = -0.01F;
            this.friction = 0.92F;
            this.lifetime = 40 + random.nextInt(30);
            this.tailleMax = 0.35F + random.nextFloat() * 0.35F;
            this.quadSize = tailleMax * 0.4F;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
        }

        @Override
        public void tick() {
            super.tick();
            float k = (float) age / lifetime;
            this.quadSize = tailleMax * (0.4F + 0.6F * k);
            this.alpha = 0.8F * (1 - k);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** Ame : trajet en courbe (montee puis plongee dans le cornet) en ~18 ticks, lueur bleu-vert qui s'affine. */
    public static final class Ame extends Base {
        private final double x0, y0, z0, dx, dy, dz;
        private final float taille;

        Ame(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z, s);
            this.x0 = x;
            this.y0 = y;
            this.z0 = z;
            this.dx = vx;
            this.dy = vy;
            this.dz = vz;
            this.lifetime = 16 + random.nextInt(6);
            this.taille = 0.16F + random.nextFloat() * 0.06F;
            this.quadSize = taille;
            this.rCol = 0.55F;
            this.gCol = 1.0F;
            this.bCol = 0.85F;
            this.gravity = 0;
            this.hasPhysics = false;
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            float p = (float) age / lifetime;
            float e = p * p * (3 - 2 * p);
            double arc = Math.sin(p * Math.PI) * 1.4;
            this.x = x0 + dx * e;
            this.y = y0 + dy * e + arc;
            this.z = z0 + dz * e;
            this.quadSize = taille * (1 - 0.6F * p);
            this.alpha = p < 0.15F ? p / 0.15F : 1;
        }

        @Override
        public int getLightColor(float partiel) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /**
     * Eclair d'apparition d'un chien : ligne brisee de {@link #N} points du ciel ({@code hauteur} blocs) jusqu'au sol,
     * dessinee en ruban tourne vers la camera, blanc-bleu, 6 ticks, deux branches secondaires.
     */
    public static final class Eclair extends Base {
        private static final int N = 12;
        private final float[] px = new float[N], pz = new float[N];
        private final float hauteur;

        Eclair(ClientLevel l, double x, double y, double z, double h, SpriteSet s) {
            super(l, x, y, z, s);
            this.hauteur = h > 0 ? (float) h : 18F;
            for (int i = 1; i < N - 1; i++) {
                px[i] = px[i - 1] * 0.5F + (random.nextFloat() - 0.5F) * 1.3F;
                pz[i] = pz[i - 1] * 0.5F + (random.nextFloat() - 0.5F) * 1.3F;
            }
            this.lifetime = 6;
            this.gravity = 0;
            this.hasPhysics = false;
            this.rCol = 0.85F;
            this.gCol = 0.93F;
            this.bCol = 1.0F;
        }

        @Override
        public void tick() {
            if (this.age++ >= this.lifetime) this.remove();
        }

        @Override
        public void render(VertexConsumer vc, Camera cam, float partiel) {
            double cx = cam.getPosition().x, cy = cam.getPosition().y, cz = cam.getPosition().z;
            float bx = (float) (x - cx), by = (float) (y - cy), bz = (float) (z - cz);
            float k = 1 - (age + partiel) / lifetime;
            if (k <= 0) return;
            // eclat qui clignote : fort, faible, fort
            float a = (age % 2 == 0 ? 1 : 0.45F) * k;
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            float um = (u0 + u1) / 2;
            float pas = hauteur / (N - 1);
            for (int i = 0; i < N - 1; i++) {
                float ax = bx + px[i], ay = by + hauteur - i * pas, az = bz + pz[i];
                float qx = bx + px[i + 1], qy = by + hauteur - (i + 1) * pas, qz = bz + pz[i + 1];
                segment(vc, ax, ay, az, qx, qy, qz, 0.09F + 0.05F * k, um, u1, v0, v1, a);
            }
        }

        /** Segment de ruban de a a q, largeur w, perpendiculaire au regard (camera a l'origine). */
        private void segment(VertexConsumer vc, float ax, float ay, float az, float qx, float qy, float qz, float w, float u0, float u1, float v0,
                             float v1, float a) {
            float mx = (ax + qx) / 2, my = (ay + qy) / 2, mz = (az + qz) / 2;
            float dx = qx - ax, dy = qy - ay, dz = qz - az;
            // perpendiculaire : d x (milieu - camera)
            float nx = dy * mz - dz * my, ny = dz * mx - dx * mz, nz = dx * my - dy * mx;
            float n = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (n < 1e-5F) return;
            nx = nx / n * w;
            ny = ny / n * w;
            nz = nz / n * w;
            int lum = 0xF000F0;
            vc.vertex(ax - nx, ay - ny, az - nz).uv(u0, v0).color(rCol, gCol, bCol, a).uv2(lum).endVertex();
            vc.vertex(ax + nx, ay + ny, az + nz).uv(u1, v0).color(rCol, gCol, bCol, a).uv2(lum).endVertex();
            vc.vertex(qx + nx, qy + ny, qz + nz).uv(u1, v1).color(rCol, gCol, bCol, a).uv2(lum).endVertex();
            vc.vertex(qx - nx, qy - ny, qz - nz).uv(u0, v1).color(rCol, gCol, bCol, a).uv2(lum).endVertex();
        }

        @Override
        public int getLightColor(float partiel) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------ fabriques
    public record FabAme(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Ame(l, x, y, z, vx, vy, vz, s);
        }
    }

    public record FabEclair(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Eclair(l, x, y, z, vx, s);
        }
    }

    public record FabCopeau(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Copeau(l, x, y, z, vx, vy, vz, s);
        }
    }

    public record FabCendre(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Cendre(l, x, y, z, vx, vy, vz, s);
        }
    }

    public record FabEtincelle(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Etincelle(l, x, y, z, vx, vy, vz, s);
        }
    }

    public record FabLueur(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Lueur(l, x, y, z, vx, vy, vz, s);
        }
    }

    public record FabBrume(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Brume(l, x, y, z, vx, vy, vz, s);
        }
    }
}
