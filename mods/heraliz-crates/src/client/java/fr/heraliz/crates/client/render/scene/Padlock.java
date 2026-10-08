package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
import fr.heraliz.crates.client.render.mesh.Occlusion;

import java.util.function.BiConsumer;

/**
 * Cadenas 3D (corps, anse, entree de serrure) et son animation : la cle arrive, s'enfonce, tourne,
 * l'anse saute, le cadenas pivote puis tombe au sol avec la cle, disparait et revient a la fermeture.
 * Repere local : origine au sommet de l'anse, le corps pend vers -Y, face avant vers +Z.
 */
public final class Padlock {
    public enum Shape { ROUND_RECT, DISC, SHIELD, HEX }

    public static final class Style {
        public Shape shape = Shape.ROUND_RECT;
        public float w = 7f, h = 6.4f, d = 3.2f, corner = 1.7f, bevel = 0.55f;
        /** Anse : rayon du fil, demi-ecartement des branches, hauteur au-dessus du corps. */
        public float wire = 0.72f, halfSpan = 2.1f, height = 4.6f;
        public Mat body, face, shackle, trim, hole;
        /** Decorations supplementaires (repere local du corps : centre du corps a l'origine). */
        public BiConsumer<MeshBuilder, Style> decorate;
    }

    public final Style st;
    public Mesh body = Mesh.EMPTY, shackle = Mesh.EMPTY, keyholeGlow = Mesh.EMPTY;
    /** Centre du corps (repere local) et entree de la serrure. */
    public float bodyY, keyY, frontZ;

    public Padlock(Style st) {
        this.st = st;
    }

    public void build(float detail) {
        Style s = st;
        int cs = Math.max(2, Math.round(5 * detail));
        bodyY = -s.height - s.h / 2;
        frontZ = s.d / 2;
        keyY = bodyY - s.h * 0.12f;
        MeshBuilder b = new MeshBuilder();
        b.push().translate(0, bodyY, 0);
        float[][] outline = switch (s.shape) {
            case DISC -> MeshBuilder.ngon(Math.min(s.w, s.h) / 2, Math.max(12, Math.round(28 * detail)), 90);
            case SHIELD -> MeshBuilder.shield(s.w, s.h, Math.max(3, Math.round(7 * detail)));
            case HEX -> MeshBuilder.ngon(Math.min(s.w, s.h) / 2 * 1.08f, 6, 90);
            default -> MeshBuilder.roundRect(s.w, s.h, s.corner, cs);
        };
        b.slab(outline, s.d, s.bevel, s.shape != Shape.HEX, s.face, s.body, s.body);
        // entree de serrure : plaque ronde en relief + anneau + trou
        float ky = keyY - bodyY;
        b.push().translate(0, ky, s.d / 2).rotateX(90);
        int rs = Math.max(8, Math.round(20 * detail));
        b.cylinder(1.45f, 0f, 0.38f, rs, s.trim, null, s.trim);
        b.push().translate(0, 0.38f, 0);
        b.torus(1.45f, 0.2f, rs, Math.max(4, Math.round(6 * detail)), 0, 360, s.trim);
        b.pop();
        b.pop();
        // trou de serrure (sombre) : disque + fente
        b.color(0.35f);
        b.push().translate(0, ky, s.d / 2 + 0.39f);
        b.slab(MeshBuilder.ngon(0.55f, 12, 0), 0.04f, 0.0f, true, s.hole, null, s.hole);
        b.push().translate(0, -0.85f, 0);
        b.slab(new float[][]{{-0.22f, 0.55f}, {-0.42f, -0.65f}, {0.42f, -0.65f}, {0.22f, 0.55f}}, 0.04f, 0f, false, s.hole, null, s.hole);
        b.pop();
        b.pop();
        b.color(1f);
        if (s.decorate != null) s.decorate.accept(b, s);
        b.pop();
        Occlusion.bake(b, 0.25f, 2.2f, 0.45f, Float.NaN);
        body = b.build();

        // anse
        MeshBuilder sh = new MeshBuilder();
        float legBottom = -s.height - 0.9f;
        float yc = -(s.halfSpan + s.wire);
        java.util.List<float[]> path = new java.util.ArrayList<>();
        for (int i = 0; i <= 4; i++) path.add(new float[]{-s.halfSpan, legBottom + (yc - legBottom) * i / 4f, 0});
        int arc = Math.max(6, Math.round(14 * detail));
        for (int i = 1; i < arc; i++) {
            double a = Math.PI - Math.PI * i / (double) arc;
            path.add(new float[]{(float) Math.cos(a) * s.halfSpan, yc + (float) Math.sin(a) * s.halfSpan, 0});
        }
        for (int i = 0; i <= 4; i++) path.add(new float[]{s.halfSpan, yc + (legBottom - yc) * i / 4f, 0});
        float[][] pa = path.toArray(new float[0][]);
        float[] radii = new float[pa.length];
        java.util.Arrays.fill(radii, s.wire);
        sh.tube(pa, radii, Math.max(6, Math.round(10 * detail)), s.shackle, true, true, 0, false);
        shackle = sh.build();

        MeshBuilder g = new MeshBuilder();
        g.push().translate(0, keyY, s.d / 2 + 0.42f);
        g.slab(MeshBuilder.ngon(1.2f, 16, 0), 0.05f, 0f, true, Mat.WHITE, null, Mat.WHITE);
        g.pop();
        keyholeGlow = g.build();
    }

    // ======================================================================= animation
    /** Le cadenas est-il visible (sequence) ? */
    public static float scale(CrateState s) {
        if (!s.active) return 1f;
        float t = s.t;
        if (t < CrateAnim.UNLOCK + 14) return 1f;
        if (t < CrateAnim.UNLOCK + 20) return 1f - Ease.inBack(Ease.range(t, CrateAnim.UNLOCK + 14, CrateAnim.UNLOCK + 20));
        if (t < CrateAnim.SLAM + 2) return 0f;
        return Ease.outBack(Ease.range(t, CrateAnim.SLAM + 2, CrateAnim.SLAM + 9), 2.2f);
    }

    /**
     * Dessine le cadenas (et la cle) ; la pose courante doit etre le point d'accroche (repere du modele).
     * fallTo : hauteur (px, repere du modele) du sol sur lequel le cadenas tombe.
     */
    public void draw(CrateState s, Pose p, DrawList out, Mesh key, Mesh keyGlow, int rgb, float glow, float fallTo, float hangY) {
        draw(s, p, out, key, keyGlow, rgb, rgb, glow, fallTo, hangY);
    }

    /** Variante ou la cle brille d'une autre couleur (keyRgb) que le cadenas (rgb). */
    public void draw(CrateState s, Pose p, DrawList out, Mesh key, Mesh keyGlow, int rgb, int keyRgb, float glow, float fallTo, float hangY) {
        float sc = scale(s);
        if (sc <= 0.001f) return;
        float t = s.active ? s.t : -1;
        float lift = 0, swing = 0, fall = 0, tumble = 0, jiggle = 0;
        boolean keyIn = false;
        if (s.active && t < CrateAnim.SLAM) {
            float u = CrateAnim.UNLOCK;
            lift = 2.0f * Ease.outBack(Ease.range(t, u, u + 3), 2.5f);
            swing = -75f * Ease.outBack(Ease.range(t, u + 2, u + 6), 1.6f);
            if (t > u + 5) {
                float dt = t - (u + 5);
                float drop = 0.5f * 1.3f * dt * dt;
                float maxDrop = Math.max(0, hangY - fallTo - (st.height + st.h) + 1.2f);
                fall = Math.min(drop, maxDrop);
                if (drop > maxDrop) {
                    float after = (float) Math.sqrt(2 * maxDrop / 1.3f);
                    float bt = dt - after;
                    fall -= 1.2f * Math.abs((float) Math.sin(bt * 0.45f)) * (float) Math.exp(-bt * 0.35f);
                }
                tumble = Math.min(1f, dt / 6f);
            }
            if (t >= CrateAnim.KEY_IN && t < u) jiggle = (float) Math.sin(t * 2.3f) * 1.6f * Ease.range(t, CrateAnim.KEY_IN, u);
            keyIn = t >= CrateAnim.KEY_IN - 2;
        } else {
            jiggle = (float) Math.sin(s.time * 0.05f + s.seed * 9f) * 1.5f;
            if (s.shake >= 0 && s.shake < 10) jiggle += (float) Math.sin(s.shake * 2.6f) * 9f * (1 - s.shake / 10f);
        }
        p.push();
        // chute dans le repere du modele (vers le bas), avec culbute
        if (fall != 0) p.m().translateLocal(0, -fall, 0);
        p.rotZ(jiggle);
        if (tumble > 0) p.rotX(tumble * 70f).rotZ(tumble * 25f);
        p.scale(sc);
        // anse
        p.push().translate(0, lift, 0);
        out.solid(p, shackle, 0.25f * glow);
        p.pop();
        // corps : pivote autour de la branche droite une fois l'anse sortie
        p.push();
        if (swing != 0) p.translate(st.halfSpan, 0, 0).rotY(swing).translate(-st.halfSpan, 0, 0);
        out.solid(p, body, glow);
        float kg = keyGlowAmount(s);
        if (kg > 0.01f) out.additive(p, keyholeGlow, rgb, kg);
        if (keyIn || (s.active && t < CrateAnim.KEY_IN)) drawKey(s, p, out, key, keyGlow, keyRgb);
        p.pop();
        p.pop();
    }

    private float keyGlowAmount(CrateState s) {
        if (!s.active) return 0.15f + 0.1f * s.pulse(0.1f);
        float t = s.t;
        return 0.2f + 0.8f * Ease.range(t, CrateAnim.KEY_IN, CrateAnim.UNLOCK) * (1 - Ease.range(t, CrateAnim.UNLOCK, CrateAnim.UNLOCK + 10));
    }

    /** Position de la cle pendant son vol (repere local du corps), t < KEY_IN. */
    private float[] keyFlight(float t) {
        float kz = frontZ + 0.45f;
        float u = Ease.clamp01(t / CrateAnim.KEY_IN);
        float e = Ease.outCubic(u);
        return new float[]{(1 - e) * 4f * (float) Math.sin(u * Math.PI * 2), Ease.lerp(keyY + 14f, keyY, e), Ease.lerp(kz + 20f, kz + 1.4f, e)};
    }

    /** Cle (repere local du corps du cadenas) : vol en spirale (trainee lumineuse), insertion, rotation. */
    private void drawKey(CrateState s, Pose p, DrawList out, Mesh key, Mesh keyGlow, int rgb) {
        if (key == null) return;
        float t = s.t;
        float kz = frontZ + 0.45f;
        float x = 0, y = keyY, z, spin = 0, roll = -90, sc = 0.58f, bright = 0.35f;
        // trainee de la cle en vol
        if (t > 1 && t < CrateAnim.KEY_IN + 2) {
            float[][] pts = new float[8][];
            for (int i = 0; i < pts.length; i++) pts[i] = keyFlight(Math.max(0, Math.min(t, CrateAnim.KEY_IN) - i * 0.9f));
            float fade = 1 - Ease.range(t, CrateAnim.KEY_IN - 2, CrateAnim.KEY_IN + 2);
            out.additive(p, Trail.ribbon(pts, 1.1f, 0.05f), rgb, 0.75f * fade);
        }
        // eclat quand la cle s'enfonce, eclair au declic
        if (t >= CrateAnim.KEY_IN && t < CrateAnim.KEY_IN + 6) {
            float e = (float) Math.exp(-(t - CrateAnim.KEY_IN) * 0.6f);
            out.billboard(p, Sprite.GLINT, 0, keyY, kz + 0.6f, 2f + 3f * e, t * 20, 0xFFFFFF, e);
        }
        if (t >= CrateAnim.UNLOCK && t < CrateAnim.UNLOCK + 10) {
            float e = (float) Math.exp(-(t - CrateAnim.UNLOCK) * 0.45f);
            out.billboard(p, Sprite.FLARE, 0, keyY, kz + 0.8f, 2.5f + 8f * e, t * 6, rgb, e);
            out.billboard(p, Sprite.HALO, 0, keyY, kz + 0.5f, 4f + 10f * (1 - e), 0, 0xFFFFFF, 0.6f * e);
        }
        if (t < CrateAnim.KEY_IN) {
            float u = t / CrateAnim.KEY_IN;
            float e = Ease.outCubic(u);
            float[] f = keyFlight(t);
            x = f[0];
            y = f[1];
            z = f[2];
            spin = (1 - e) * 720f;
            sc *= Ease.outBack(Math.min(1f, u * 1.6f));
            bright = 0.35f + 0.65f * (1 - u);
        } else if (t < CrateAnim.KEY_TURN) {
            float u = Ease.range(t, CrateAnim.KEY_IN, CrateAnim.KEY_TURN);
            z = Ease.lerp(kz + 1.4f, kz - 2.2f, Ease.inOutCubic(u));
        } else if (t < CrateAnim.UNLOCK) {
            float u = Ease.range(t, CrateAnim.KEY_TURN, CrateAnim.UNLOCK);
            z = kz - 2.2f;
            roll = -90 + 90 * Ease.inOutBack(u);
            bright = 0.35f + 0.5f * u;
        } else {
            z = kz - 2.2f;
            roll = 0;
            bright = Math.max(0.3f, 1f - (t - CrateAnim.UNLOCK) / 10f);
        }
        p.push().translate(x, y, z).rotY(spin).rotZ(roll).rotX(90).scale(sc);
        out.solid(p, key, 0.8f);
        out.additive(p, keyGlow, rgb, 0.2f + 0.6f * bright);
        p.pop();
    }

    /** Position (repere local) de l'entree de serrure : pour les sons et particules. */
    public float[] keyhole() {
        return new float[]{0, keyY, frontZ + 0.4f};
    }
}
