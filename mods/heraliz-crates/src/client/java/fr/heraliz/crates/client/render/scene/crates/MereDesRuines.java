package fr.heraliz.crates.client.render.scene.crates;

import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
import fr.heraliz.crates.client.render.mesh.Noise;
import fr.heraliz.crates.client.render.mesh.Occlusion;
import fr.heraliz.crates.client.render.scene.CommonFx;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.DrawList;
import fr.heraliz.crates.client.render.scene.Ease;
import fr.heraliz.crates.client.render.scene.FxSink;
import fr.heraliz.crates.client.render.scene.Geo;
import fr.heraliz.crates.client.render.scene.KeySmith;
import fr.heraliz.crates.client.render.scene.Padlock;
import fr.heraliz.crates.client.render.scene.Pose;
import fr.heraliz.crates.client.render.scene.Seq;
import fr.heraliz.crates.registry.Snd;
import fr.heraliz.crates.client.render.scene.Sprite;

import java.util.Random;

import static fr.heraliz.crates.client.render.scene.Geo.add;
import static fr.heraliz.crates.client.render.scene.Geo.mul;
import static fr.heraliz.crates.client.render.scene.Geo.norm;
import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Mere des Ruines : sur des dalles en ruine couvertes de toiles, un cocon de soie enchaine ; derriere lui,
 * l'araignee geante l'enlace de ses pattes, yeux rouges et crocs. A l'ouverture le cocon eclot en quatre
 * petales, l'araignee se cabre en sifflant, les oeufs rougeoient.
 */
public final class MereDesRuines extends Design {
    private static final Mat STONE = Mat.MAIN;
    private static final Mat CHITIN = Mat.MAIN2;
    private static final Mat LEG = Mat.TRIM;
    private static final Mat IRON = Mat.ACCENT;
    private static final Mat MARK = Mat.EMBLEM;
    private static final Mat PLATE = Mat.LOCK;
    private static final Mat SILK = Mat.DECAL_A.withWorld(16);
    private static final Mat WEB = Mat.DECAL_B;
    private static final Mat FLESH = Mat.INTERIOR;
    private static final Mat EYE = Mat.GEM;
    private static final Mat FANG = Mat.DARK;
    private static final Mat BONE = Mat.SPECIAL;

    /** Cocon. */
    private static final float KX = 8, KZ = 11, KY0 = 2.2f, KH = 17.5f, KR = 7.4f, T_CUT = 0.52f;
    /** Chaine et cadenas. */
    private static final float CHAIN_Y = 14.0f;
    private static final float HANG_Y = 14.6f;
    /** Cephalothorax (repere du corps, avant la pose de cabrage). */
    private static final float[] CEPH = {8, 19.8f, 2.2f};
    private static final float[] ABD = {8, 18.8f, -5.6f};

    private final Parts.RockSlab ground = new Parts.RockSlab();
    private Mesh base = Mesh.EMPTY, lowerIn = Mesh.EMPTY, body = Mesh.EMPTY, eyes = Mesh.EMPTY, eyesGlow = Mesh.EMPTY;
    private Mesh fang = Mesh.EMPTY, chain = Mesh.EMPTY, spiderling = Mesh.EMPTY, eggsGlow = Mesh.EMPTY, strands = Mesh.EMPTY;
    private final Mesh[] petal = new Mesh[4], petalIn = new Mesh[4];
    private final Mesh[] stones = new Mesh[5];

    public MereDesRuines() {
        super("mere_des_ruines", 0xFF3048, 0xFF9AA8);
        ringY = 38;
        ringR = 22;
        revealY = 46;
        riseFromY = 12;
        lockFloorY = 2.6f;
        ground.radius = 15.6f;
        ground.hCenter = 2.6f;
        ground.hEdge = 2.1f;
        ground.bump = 0.25f;
        ground.freq = 0.2f;
        ground.edgeNoise = 1.4f;
        ground.seed = 9;
        ground.planarTop = true;
    }

    // =================================================================== cocon
    static float cocR(float t) {
        if (t <= 0 || t >= 1) return 0;
        return KR * (float) Math.pow(Math.sin(Math.PI * t), 0.75) * (1 + 0.25f * (0.45f - t));
    }

    static float cut(float phi) {
        float u = phi / 360f * 12f;
        float tri = Math.abs((u - (float) Math.floor(u)) - 0.5f) * 2f;
        return T_CUT + 0.028f * (tri - 0.5f) * 2f;
    }

    static float[] coc(float phi, float t, float inset) {
        double a = Math.toRadians(phi);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float bump = 1 + 0.04f * Noise.fbm(c * 2.6f + 1, t * 5f, s * 2.6f, 3);
        float r = Math.max(0.01f, cocR(t) * bump - inset);
        return v(KX + c * r, KY0 + KH * t, KZ + s * r);
    }

    /** Frontiere dechiree entre deux petales. */
    static float seam(int j, float t) {
        return 45 + 90 * j + 7f * Noise.noise(t * 6f, j * 3.3f, 0.5f);
    }

    private void lowerShell(MeshBuilder b, float inset, Mat m, boolean inward) {
        int nu = seg(40), nv = seg(9);
        float[][][] P = new float[nv + 1][nu + 1][];
        float[][] S = new float[nv + 1][nu + 1];
        float[][] T = new float[nv + 1][nu + 1];
        for (int j = 0; j <= nu; j++) {
            float phi = 360f * (j % nu) / nu;
            float tc = cut(phi);
            for (int i = 0; i <= nv; i++) {
                float t = 0.03f + (tc - 0.03f) * i / nv;
                P[i][j] = coc(phi, t, inset);
                S[i][j] = (float) j / nu * 3f;
                T[i][j] = (KY0 + KH - P[i][j][1]) / 16f;
            }
        }
        b.grid(P, null, S, T, true, m, inward ? MeshBuilder.Orient.IN : MeshBuilder.Orient.OUT, true);
    }

    private void petalMesh(MeshBuilder b, int k, float inset, Mat m, boolean inward) {
        int nu = seg(10), nv = seg(8);
        float[][][] P = new float[nv + 1][nu + 1][];
        float[][] S = new float[nv + 1][nu + 1];
        float[][] T = new float[nv + 1][nu + 1];
        for (int i = 0; i <= nv; i++) {
            float vv = (float) i / nv;
            for (int j = 0; j <= nu; j++) {
                float u = (float) j / nu;
                float tBase = cut(90 * k + 90 + (u - 0.5f) * 90);
                float t = tBase + (0.9995f - tBase) * (float) Math.sin(vv * Math.PI / 2);
                float a0 = seam(k - 1 + 4, t) - 360, a1 = seam(k, t);
                if (a1 < a0) a1 += 360;
                // l'angle du petale k est centre sur 90 + 90 k
                float lo = seam((k + 3) % 4, t) + (k == 0 ? -360 : 0);
                float hi = seam(k, t);
                while (hi < lo) hi += 360;
                float phi = lo + (hi - lo) * u;
                P[i][j] = coc(phi, t, inset);
                S[i][j] = u * 1.1f;
                T[i][j] = (KY0 + KH - P[i][j][1]) / 16f;
            }
        }
        b.grid(P, null, S, T, false, m, inward ? MeshBuilder.Orient.IN : MeshBuilder.Orient.OUT, true);
        if (!inward) {
            // tranches dechirees : bas et cotes
            for (int j = 0; j < nu; j++) {
                edge(b, P[0][j], P[0][j + 1], k, j / (float) nu, (j + 1) / (float) nu, 0, 0);
            }
            for (int i = 0; i < nv; i++) {
                edge(b, P[i][0], P[i + 1][0], k, 0, 0, i / (float) nv, (i + 1) / (float) nv);
                edge(b, P[i][nu], P[i + 1][nu], k, 1, 1, i / (float) nv, (i + 1) / (float) nv);
            }
        }
    }

    private void edge(MeshBuilder b, float[] o0, float[] o1, int k, float u0, float u1, float v0, float v1) {
        float[] c = v(KX, KY0 + KH * 0.6f, KZ);
        float[] i0 = Geo.add(o0, mul(norm(Geo.sub(c, o0)), 0.5f));
        float[] i1 = Geo.add(o1, mul(norm(Geo.sub(c, o1)), 0.5f));
        b.flatOut(SILK, c, o0, o1, i1, i0);
    }

    /** Charniere d'un petale : pivot et axe (ouverture vers l'exterieur). */
    private static float[][] hinge(int k) {
        float phi = 90 + 90 * k;
        float[] p0 = coc(phi, cut(phi), 0.25f);
        double a = Math.toRadians(phi);
        return new float[][]{p0, v((float) Math.sin(a), 0, -(float) Math.cos(a))};
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        ground.build(b, STONE, seg(40), seg(8), 4f);
        lowerShell(b, 0, SILK, false);
        // tranche de la coquille de soie au niveau de la coupe
        int nu = seg(40);
        for (int j = 0; j < nu; j++) {
            float p0 = 360f * j / nu, p1 = 360f * (j + 1) / nu;
            float[] o0 = coc(p0, cut(p0), 0), o1 = coc(p1, cut(p1), 0), i0 = coc(p0, cut(p0), 0.55f), i1 = coc(p1, cut(p1), 0.55f);
            b.face(SILK, v(0, 1, 0), o0, o1, i1, i0);
        }
        ruins(b);
        Occlusion.bake(b, 0.5f, 5f, 0.6f, 0f);
        base = b.build();
        // fils de soie vers le sol (maillage a part : trop fins pour l'occlusion)
        MeshBuilder fs = new MeshBuilder();
        Random r = new Random(4);
        for (int i = 0; i < 7; i++) {
            float phi = 20 + i * 50 + r.nextFloat() * 20;
            float[] a = coc(phi, 0.3f + r.nextFloat() * 0.35f, -0.1f);
            float[] g = Geo.polar(8, 8, phi, 13.5f + r.nextFloat() * 2.5f, 0);
            g[1] = ground.top(g[0], g[2]);
            float[] mid = Geo.lerp(a, g, 0.5f);
            mid[1] -= 1.2f;
            fs.tube(Geo.spline(new float[][]{a, mid, g}, seg(3)), Geo.taper(2 * seg(3) + 1, 0.16f, 0.1f, 1), 4, SILK, false, false, 0, false);
        }
        strands = fs.build();

        MeshBuilder li = new MeshBuilder();
        lowerShell(li, 0.55f, FLESH, true);
        // oeufs luisants, os et pieces
        MeshBuilder eg = new MeshBuilder();
        Random re = new Random(21);
        float floor = KY0 + KH * 0.22f;
        for (int i = 0; i < 11; i++) {
            double a = re.nextDouble() * Math.PI * 2;
            float rr = re.nextFloat() * 4.2f;
            float er = 1.1f + re.nextFloat() * 0.8f;
            float x = KX + (float) Math.cos(a) * rr, z = KZ + (float) Math.sin(a) * rr;
            float y = floor + er * 0.7f + (4.2f - rr) * 0.4f;
            li.push().translate(x, y, z).scale(1, 1.15f, 1);
            li.sphere(er, seg(10), seg(6), EYE);
            li.pop();
            eg.push().translate(x, y, z).scale(1.1f, 1.25f, 1.1f);
            eg.sphere(er, seg(8), seg(5), Mat.WHITE);
            eg.pop();
        }
        for (int i = 0; i < 3; i++) {
            float x = KX - 3 + i * 3, z = KZ + 3.5f - i;
            bone(li, v(x - 1.8f, floor + 2.2f, z), v(x + 1.8f, floor + 2.6f, z - 1f), 0.3f);
        }
        Occlusion.bake(li, 0.4f, 4f, 0.45f, Float.NaN, base);
        lowerIn = li.build();
        eggsGlow = eg.build();

        for (int k = 0; k < 4; k++) {
            MeshBuilder pb = new MeshBuilder();
            petalMesh(pb, k, 0, SILK, false);
            Occlusion.bake(pb, 0.4f, 3f, 0.35f, Float.NaN);
            petal[k] = pb.build();
            MeshBuilder pi = new MeshBuilder();
            petalMesh(pi, k, 0.5f, FLESH, true);
            petalIn[k] = pi.build();
        }

        // chaine autour des petales
        MeshBuilder ch = new MeshBuilder();
        int cn = 40;
        float[][] path = new float[cn + 1][];
        for (int i = 0; i <= cn; i++) {
            float phi = 90 + 360f * i / cn;
            float t = (CHAIN_Y - KY0) / KH;
            float[] q = coc(phi, t, -0.55f);
            float front = (float) Math.pow(Math.max(0, Math.cos(Math.toRadians(phi - 90))), 8);
            q[2] += front * 0.9f;
            q[1] += front * (HANG_Y - 0.7f - CHAIN_Y);
            path[i] = q;
        }
        ch.chain(path, 2.4f, 1.5f, 0.42f, IRON, seg(8), 4);
        chain = ch.build();

        // corps de l'araignee (statique, anime par sa pose)
        MeshBuilder sb = new MeshBuilder();
        sb.push().translate(ABD[0], ABD[1], ABD[2]).rotateX(-12);
        sb.blob(6.4f, 5.6f, 7.2f, 1f, 0.05f, 1.6f, 3, seg(22), CHITIN);
        // filieres
        sb.push().translate(0, -1.0f, -7.0f).rotateX(-100);
        sb.cylinder(1.0f, 0, 1.6f, seg(8), CHITIN, null, CHITIN);
        sb.pop();
        sb.pop();
        // marque en crane (decal courbe sur le dessus de l'abdomen)
        sb.surface((u, vv) -> {
            double ph = Math.toRadians(236 + u * 68);
            double th = Math.toRadians(12 + vv * 58);
            float x = (float) (Math.sin(th) * Math.cos(ph)), y = (float) Math.cos(th), z = (float) (Math.sin(th) * Math.sin(ph));
            float[] q = v(x * 6.4f * 1.02f, y * 5.6f * 1.02f, z * 7.2f * 1.02f);
            double rx = Math.toRadians(-12);
            float qy = (float) (q[1] * Math.cos(rx) - q[2] * Math.sin(rx)), qz = (float) (q[1] * Math.sin(rx) + q[2] * Math.cos(rx));
            return v(ABD[0] + q[0], ABD[1] + qy, ABD[2] + qz);
        }, seg(8), seg(8), MARK, 1, 1, false, MeshBuilder.Orient.OUT);
        // taille et cephalothorax
        sb.push().translate(8, 19.2f, -1.2f);
        sb.sphere(1.5f, seg(10), seg(6), CHITIN);
        sb.pop();
        sb.push().translate(CEPH[0], CEPH[1], CEPH[2]).rotateX(10);
        sb.blob(4.3f, 3.0f, 4.8f, 0.9f, 0.04f, 2f, 7, seg(16), CHITIN);
        // cheliceres
        for (int s = -1; s <= 1; s += 2) {
            sb.push().translate(s * 1.0f, -1.6f, 4.0f);
            sb.blob(1.0f, 1.5f, 1.0f, 1f, 0.05f, 2f, 11 + s, seg(9), CHITIN);
            sb.pop();
            // pedipalpes
            float[][] pp = {v(s * 2.0f, -1.2f, 3.6f), v(s * 3.0f, -0.6f, 5.6f), v(s * 2.6f, -2.6f, 7.0f)};
            Geo.horn(sb, pp, 0.5f, 0.2f, 1f, seg(3), seg(6), LEG);
        }
        sb.pop();
        Occlusion.bake(sb, 0.45f, 4f, 0.5f, Float.NaN);
        body = sb.build();

        MeshBuilder ey = new MeshBuilder();
        MeshBuilder eyg = new MeshBuilder();
        float[][] E = {{0.9f, 1.3f, 4.2f, 0.72f}, {2.0f, 1.5f, 3.7f, 0.5f}, {1.35f, 2.3f, 3.4f, 0.36f}, {2.6f, 0.6f, 3.5f, 0.4f}};
        for (float[] e : E) {
            for (int s = -1; s <= 1; s += 2) {
                ey.push().translate(CEPH[0] + s * e[0], CEPH[1] + e[1], CEPH[2] + e[2]);
                ey.sphere(e[3], seg(8), seg(5), EYE);
                ey.pop();
                eyg.push().translate(CEPH[0] + s * e[0], CEPH[1] + e[1], CEPH[2] + e[2]);
                eyg.sphere(e[3] * 1.6f, seg(6), seg(4), Mat.WHITE);
                eyg.pop();
            }
        }
        eyes = ey.build();
        eyesGlow = eyg.build();

        MeshBuilder fb = new MeshBuilder();
        Geo.horn(fb, new float[][]{v(0, 0, 0), v(0, -1.4f, 0.6f), v(-0.3f, -2.6f, 0.2f), v(-0.9f, -3.1f, -0.6f)}, 0.5f, 0.04f, 1.2f, seg(3), seg(7), FANG);
        fang = fb.build();

        // petite araignee (araignees qui rodent)
        MeshBuilder sp = new MeshBuilder();
        sp.push().translate(0, 0.9f, -0.9f);
        sp.blob(1.0f, 0.8f, 1.2f, 1f, 0, 1, 1, 8, CHITIN);
        sp.pop();
        sp.push().translate(0, 0.8f, 0.6f);
        sp.sphere(0.6f, 7, 4, CHITIN);
        sp.pop();
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 4; i++) {
                float z = 0.9f - i * 0.5f;
                float[][] lp = {v(s * 0.4f, 0.8f, z), v(s * 1.4f, 1.5f, z + (1.5f - i) * 0.5f), v(s * 2.2f, 0.05f, z + (1.5f - i) * 0.9f)};
                sp.tube(lp, new float[]{0.14f, 0.1f, 0.05f}, 4, LEG, false, false, 0, false);
            }
        }
        spiderling = sp.build();

        for (int i = 0; i < stones.length; i++) {
            MeshBuilder rb = new MeshBuilder();
            float s = 1.2f + (i * 0.41f) % 0.9f;
            rb.push().scale(1.2f, 0.9f, 1);
            rb.rock(s, s * 0.8f, s * 0.9f, 0.25f, 1.1f, i + 20, seg(9), STONE);
            rb.pop();
            stones[i] = rb.build();
        }

        // cadenas gothique (ecusson), araignee gravee
        Padlock.Style st = new Padlock.Style();
        st.shape = Padlock.Shape.SHIELD;
        st.w = 6.4f;
        st.h = 7.2f;
        st.d = 2.8f;
        st.height = 4.2f;
        st.halfSpan = 1.9f;
        st.body = IRON;
        st.face = PLATE;
        st.shackle = IRON;
        st.trim = IRON;
        st.hole = FANG;
        st.decorate = (mb, s) -> {
            for (int side = -1; side <= 1; side += 2) {
                Geo.horn(mb, new float[][]{v(side * 2.8f, 2.6f, 0), v(side * 3.9f, 3.6f, 0.2f), v(side * 3.6f, 5.0f, 0.6f)}, 0.5f, 0.05f, 1.2f, 3, 6, FANG);
            }
        };
        lock = new Padlock(st);
        lock.build(detail);

        // cle : araignee (corps + 8 pattes) en guise d'anneau
        Mesh[] k = KeySmith.build(IRON, BONE, 0.55f, (kb, kg) -> {
            kb.push().translate(0, 2.6f, 0);
            kb.blob(1.3f, 1.1f, 0.9f, 1f, 0, 1, 2, 12, FANG);
            kb.pop();
            kb.push().translate(0, 5.2f, 0);
            kb.blob(2.2f, 2.3f, 1.5f, 1f, 0.03f, 2, 3, 14, FANG);
            kb.pop();
            for (int s = -1; s <= 1; s += 2) {
                for (int i = 0; i < 4; i++) {
                    float y = 3.2f - i * 0.5f;
                    float a = 30 - i * 25;
                    double ar = Math.toRadians(a);
                    float[][] lp = {v(s * 0.8f, y, 0), v(s * 2.6f, y + 1.4f + (float) Math.sin(ar) * 1.5f, 0.3f),
                            v(s * 4.2f, y - 0.2f + (float) Math.sin(ar) * 2.6f, 0.2f)};
                    kb.tube(Geo.spline(lp, 3), Geo.taper(7, 0.3f, 0.08f, 1), 6, LEG, true, true, 0, false);
                }
                kb.push().translate(s * 0.4f, 3.0f, 0.75f);
                kb.sphere(0.24f, 6, 4, EYE);
                kb.pop();
                kg.push().translate(s * 0.4f, 3.0f, 0.75f);
                kg.sphere(0.45f, 6, 4, Mat.WHITE);
                kg.pop();
            }
            // marque rouge sur l'abdomen
            kb.push().translate(0, 5.3f, 1.35f);
            kb.color(1, 0.3f, 0.3f);
            kb.slab(MeshBuilder.ngon(0.7f, 6, 90), 0.2f, 0.05f, false, EYE, EYE, EYE);
            kb.color(1);
            kb.pop();
            kg.push().translate(0, 5.3f, 1.35f);
            kg.slab(MeshBuilder.ngon(0.9f, 6, 90), 0.2f, 0.05f, false, Mat.WHITE, Mat.WHITE, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    private void bone(MeshBuilder b, float[] a, float[] c, float r) {
        b.tube(new float[][]{a, Geo.lerp(a, c, 0.5f), c}, new float[]{r, r * 0.8f, r}, seg(6), BONE, false, false, 0, false);
        for (float[] e : new float[][]{a, c}) {
            b.push().translate(e[0], e[1], e[2]);
            b.sphere(r * 1.6f, seg(6), seg(4), BONE);
            b.pop();
        }
    }

    private void skull(MeshBuilder b, float x, float z, float yaw, float sc) {
        float y = ground.top(x, z);
        b.push().translate(x, y + 1.1f * sc, z).rotateY(yaw).scale(sc);
        b.blob(1.3f, 1.15f, 1.5f, 1f, 0.02f, 2, 5, seg(10), BONE);
        b.push().translate(0, -0.8f, 0.7f);
        b.blob(0.9f, 0.55f, 0.8f, 1f, 0, 1, 6, seg(8), BONE);
        b.pop();
        b.color(0.08f);
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * 0.5f, 0.05f, 1.28f);
            b.sphere(0.36f, 6, 4, BONE);
            b.pop();
        }
        b.color(1);
        b.pop();
    }

    private void ruins(MeshBuilder b) {
        // colonne renversee (avant gauche)
        b.push().translate(-3.5f, ground.top(-3.5f, 19.5f) + 1.7f, 19.5f).rotateY(-18).rotateZ(90);
        b.cylinder(2.0f, -5.5f, 5.5f, seg(12), STONE, STONE, STONE);
        b.pop();
        // troncon de colonne debout (arriere droit), sommet brise
        float bx = 21.5f, bz = -4.5f;
        b.push().translate(bx, ground.top(bx, bz) - 0.5f, bz);
        b.cylinder(2.5f, 0, 8.5f, seg(12), STONE, null, STONE);
        b.translate(0, 8.5f, 0);
        b.rock(2.3f, 1.2f, 2.2f, 0.35f, 1.4f, 3, seg(10), STONE);
        b.pop();
        // blocs de pierre
        float[][] blocks = {{19.5f, 15.5f, 25, 3.2f, 2.4f, 2.6f}, {-5.5f, 3.5f, -10, 2.8f, 3.2f, 2.4f}, {1.5f, -7.5f, 35, 3.6f, 2.2f, 2.8f}};
        for (float[] bl : blocks) {
            float y = ground.top(bl[0], bl[1]);
            b.push().translate(bl[0], y - 0.3f, bl[1]).rotateY(bl[2]).rotateZ(6);
            b.bevelBox(-bl[3] / 2, 0, -bl[5] / 2, bl[3] / 2, bl[4], bl[5] / 2, 0.45f, STONE);
            b.pop();
        }
        skull(b, 17.5f, 20.0f, -30, 1.0f);
        skull(b, -2.0f, 12.0f, 40, 0.8f);
        bone(b, v(13.5f, ground.top(14, 21) + 0.4f, 22), v(18.5f, ground.top(18, 23) + 0.4f, 23.5f), 0.32f);
        bone(b, v(-6, ground.top(-6, 9) + 0.4f, 9), v(-4, ground.top(-4, 13) + 0.4f, 13.5f), 0.28f);
        // toiles
        web(b, v(19.8f, 5.0f, 14.6f), v(21.5f, 9.8f, -3.5f), v(22.4f, 2.4f, 4.5f), v(19.0f, 2.4f, 13.5f));
        web(b, v(-4.2f, 5.6f, 3.0f), v(1.0f, 9.5f, -5.0f), v(0.5f, 2.4f, -8.2f), v(-6.5f, 2.4f, 1.5f));
    }

    private void web(MeshBuilder b, float[]... q) {
        float[] n = norm(Geo.cross(Geo.sub(q[1], q[0]), Geo.sub(q[3], q[0])));
        float[][] uv = {{WEB.u(0), WEB.v(1)}, {WEB.u(1), WEB.v(1)}, {WEB.u(1), WEB.v(0)}, {WEB.u(0), WEB.v(0)}};
        b.poly(q, uv, null, n);
    }

    // =================================================================== animation
    private float rear(CrateState s) {
        float twitch = 0.08f * Ease.bell((s.time + s.seed * 170) % 170, 0, 10);
        if (!s.active) return twitch;
        float t = s.t;
        return Math.max(twitch, Ease.smooth(Ease.range(t, CrateAnim.UNLOCK, CrateAnim.OPEN + 12)) * (1 - Ease.smooth(Ease.range(t, CrateAnim.CLOSE, CrateAnim.SLAM + 6))));
    }

    private void bodyPose(CrateState s, Pose p) {
        float rr = rear(s);
        float breath = (float) Math.sin(s.time * 0.07f) * 0.3f;
        p.translate(8, 12, -9).rotX(-26f * rr).translate(-8, -12, 9);
        p.translate(0, 1.2f * rr + breath * 0.3f, -0.6f * rr);
    }

    private Mesh legs(CrateState s, Pose bp) {
        MeshBuilder b = new MeshBuilder();
        float rr = rear(s);
        float spin = Seq.spin(s);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 4; i++) {
                float[] rootL = v(CEPH[0] + side * 3.3f, CEPH[1] - 0.2f, CEPH[2] + 2.2f - i * 1.7f);
                float[] root = bp.apply(rootL[0], rootL[1], rootL[2]);
                float[] foot;
                float wig = (float) Math.sin(s.time * 0.09f + i * 1.7f + side) * 0.35f;
                switch (i) {
                    case 0 -> {
                        float[] rest = coc(90 - side * 48, 0.8f, -0.2f);
                        float wave = (float) Math.sin(s.time * 0.25f + side * 1.5f);
                        float[] up = v(8 + side * (10 + 2 * wave * spin), 30f + 3 * wave, 9.5f + 2 * wave);
                        foot = Geo.lerp(rest, up, rr);
                    }
                    case 1 -> {
                        float[] rest = coc(90 - side * 92, 0.5f, -0.2f);
                        float[] up = v(8 + side * 15, 22f + 2 * (float) Math.sin(s.time * 0.2f + side), 8f);
                        foot = Geo.lerp(rest, up, rr * 0.8f);
                    }
                    case 2 -> {
                        float fx = 8 + side * 18f, fz = 2f;
                        foot = v(fx, ground.top(Math.max(-7, Math.min(23, fx)), fz), fz + wig);
                    }
                    default -> {
                        float fx = 8 + side * 15.5f, fz = -11f;
                        foot = v(fx, ground.top(Math.max(-7, Math.min(23, fx)), Math.max(-7, fz)), fz + wig);
                    }
                }
                float[] d = Geo.sub(foot, root);
                float[] out = norm(v(d[0], 0, d[2]));
                float kneeH = i < 2 ? 6.5f : 8.5f;
                float[] knee = add(add(Geo.lerp(root, foot, 0.3f), v(0, kneeH, 0)), mul(out, 1.2f));
                float[] ankle = add(Geo.lerp(root, foot, 0.76f), v(0, 2.2f, 0));
                float[][] path = Geo.spline(new float[][]{root, knee, ankle, foot}, seg(4));
                int n = path.length;
                float[] radii = new float[n];
                for (int q = 0; q < n; q++) {
                    float u = (float) q / (n - 1);
                    radii[q] = u < 0.33f ? Ease.lerp(0.85f, 0.6f, u / 0.33f) : (u < 0.75f ? Ease.lerp(0.6f, 0.42f, (u - 0.33f) / 0.42f) : Ease.lerp(0.42f, 0.1f, (u - 0.75f) / 0.25f));
                }
                b.tube(path, radii, seg(6), LEG, true, true, 0, false);
                for (float[] j : new float[][]{knee, ankle}) {
                    b.push().translate(j[0], j[1], j[2]);
                    b.sphere(j == knee ? 0.8f : 0.55f, seg(6), seg(4), LEG);
                    b.pop();
                }
            }
        }
        return b.buildDynamic();
    }

    private float petalOpen(CrateState s) {
        if (!s.active) return 0.012f * Ease.bell((s.time + s.seed * 200) % 200, 0, 30);
        return Seq.open(s);
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float cav = Seq.cavity(s);
        out.solid(p, base, gl);
        out.solid(p, strands, 0.2f);
        float op = petalOpen(s);
        boolean inside = op > 0.02f;
        if (inside) {
            out.solid(p, lowerIn, 0.3f + 0.4f * cav);
            out.additive(p, eggsGlow, glow, 0.08f + 0.2f * cav + 0.06f * s.pulse(0.15f));
        }
        for (int k = 0; k < 4; k++) {
            float[][] h = hinge(k);
            float ang = 118f * op + (k % 2) * 6f * op;
            p.push().translate(h[0][0], h[0][1], h[0][2]).rot(ang, h[1][0], h[1][1], h[1][2]).translate(-h[0][0], -h[0][1], -h[0][2]);
            out.solid(p, petal[k], gl);
            if (inside) out.solid(p, petalIn[k], 0.3f + 0.4f * cav);
            p.pop();
        }
        // chaine : tombe avec le cadenas
        float sc = Padlock.scale(s);
        if (sc > 0.01f) {
            float drop = 0;
            if (s.active && s.t > CrateAnim.UNLOCK + 3 && s.t < CrateAnim.SLAM) {
                float dt = s.t - (CrateAnim.UNLOCK + 3);
                drop = Math.min(CHAIN_Y - 3.2f, 0.5f * 1.3f * dt * dt);
            }
            p.push().translate(KX, CHAIN_Y - drop, KZ).scale(1 + 0.25f * Math.min(1f, drop / 6f), 1, 1 + 0.25f * Math.min(1f, drop / 6f)).scale(sc).translate(-KX, -CHAIN_Y, -KZ);
            out.solid(p, chain, 0.2f);
            p.pop();
        }
        // araignee
        Pose bp = new Pose();
        bodyPose(s, bp);
        p.push();
        bodyPose(s, p);
        float breath = 1 + 0.025f * (float) Math.sin(s.time * 0.07f);
        p.push().translate(ABD[0], ABD[1], ABD[2]).scale(breath).translate(-ABD[0], -ABD[1], -ABD[2]);
        out.solid(p, body, gl);
        p.pop();
        float blink = eyeBlink(s);
        p.push().translate(CEPH[0], CEPH[1] + 1.3f, CEPH[2] + 4f).scale(1, blink, 1).translate(-CEPH[0], -CEPH[1] - 1.3f, -CEPH[2] - 4f);
        out.solid(p, eyes, 0.7f);
        float eg = (0.14f + 0.1f * s.pulse(0.2f) + 0.3f * rear(s)) * blink;
        out.additive(p, eyesGlow, glow, eg);
        p.pop();
        float fo = 0.15f + 0.1f * (float) Math.sin(s.time * 0.3f) + 0.75f * rear(s);
        for (int side = -1; side <= 1; side += 2) {
            p.push().translate(CEPH[0] + side * 1.0f, CEPH[1] - 2.8f, CEPH[2] + 4.4f).rotZ(side * (8 + 32 * fo)).rotX(-10 * fo);
            p.scale(side, 1, 1);
            out.solid(p, fang, gl);
            p.pop();
        }
        p.pop();
        out.solid(p, legs(s, bp), gl);
        // araignees qui rodent
        for (int i = 0; i < 2; i++) {
            float ph = s.time * 0.8f + i * 180;
            float burst = (float) Math.floor(ph / 30f) * 30f + Ease.smooth((ph % 30f) / 12f) * 30f;
            float rad = 16.5f + i * 1.5f;
            float a = burst * (i == 0 ? 1 : -1);
            double ar = Math.toRadians(a);
            float x = 8 + (float) Math.cos(ar) * rad, z = 8 + (float) Math.sin(ar) * rad;
            p.push().translate(x, ground.top(Math.max(-7, Math.min(23, x)), Math.max(-7, Math.min(23, z))) - 0.3f, z)
                    .rotY(-a + (i == 0 ? 180 : 0)).scale(0.9f);
            out.solid(p, spiderling, gl);
            p.pop();
        }
    }

    private float eyeBlink(CrateState s) {
        float c = (s.time + s.seed * 90) % 90;
        return c < 4 ? 0.25f + 0.75f * Math.abs(c - 2) / 2f : 1f;
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        float t = (CHAIN_Y - KY0) / KH;
        float[] q = coc(90, t, -0.55f);
        p.translate(8, HANG_Y, q[2] + 1.35f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        float aw = Seq.awaken(s);
        float ex = Seq.excite(s);
        float orb = orbitScale(s);
        for (int i = 0; i < stones.length; i++) {
            if (orb <= 0.001f) break;
            float ang = i * 72f + s.time * (0.7f + 0.12f * i) + (s.active ? s.t * s.t * 0.015f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)) : 0);
            float rad = 19.5f + (i % 2) * 1.5f + 4f * ex;
            float y = 9f + 3f * (float) Math.sin(s.time * 0.04f + i * 1.7f) + 9f * aw;
            p.push().translate(8, 0, 8).rotZ(9f * (float) Math.sin(i * 2.3f)).rotY(ang).translate(rad, y, 0).rotX(s.time * 1.5f + i * 40).rotY(s.time * i).scale(orb);
            out.solid(p, stones[i], 0.7f);
            p.pop();
        }
        if (s.item) return;
        // brume rouge au sol
        for (int i = 0; i < 3; i++) {
            float a = s.time * (0.4f + i * 0.2f) + i * 120;
            float[] q = Geo.polar(8, 8, a, 9 + i * 3, 0);
            out.billboard(p, Sprite.SMOKE, q[0], 3.5f, q[2], 9f + 4f * aw, a, glow, 0.07f + 0.1f * aw);
        }
        out.ground(p, Sprite.HALO, 8, 0.6f, 9, 20f, 0, glow, 0.12f + 0.08f * s.pulse(0.08f) + 0.25f * aw);
        CommonFx.cavity(s, p, out, this, KX, KY0 + KH * 0.25f, KZ, 6.5f);
    }

    @Override
    public float[] cavityPos() {
        return new float[]{KX, KY0 + KH * 0.3f, KZ};
    }

    // =================================================================== particules
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.12f) {
            double a = r.nextDouble() * Math.PI * 2;
            float rr = 4 + r.nextFloat() * 12;
            fx.particle(FxSink.SMOKE, 8 + (float) Math.cos(a) * rr, 2.8f, 8 + (float) Math.sin(a) * rr, 0, 0.06f, 0, 0x2a0a10, 3.2f, 70);
        }
        if (r.nextFloat() < 0.15f) {
            fx.particle(FxSink.DOT, 8 + (r.nextFloat() - 0.5f) * 26, 3 + r.nextFloat() * 14, 8 + (r.nextFloat() - 0.5f) * 26,
                    0, -0.02f, 0, 0xB0A090, 0.5f, 60);
        }
        if (r.nextFloat() < 0.05f) {
            fx.particle(FxSink.SPARK, CEPH[0] + (r.nextFloat() - 0.5f) * 4, CEPH[1] + 1.5f, CEPH[2] + 4.5f, 0, 0.05f, 0.05f, glow, 1.0f, 20);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == CrateAnim.UNLOCK + 4) fx.sound(Snd.SPIDER, CEPH[0], CEPH[1], CEPH[2], 1.0f, 0.9f);
        if (k == CrateAnim.OPEN) {
            fx.sound(Snd.SPIDER, CEPH[0], CEPH[1], CEPH[2], 1.2f, 0.75f);
            fx.sound(Snd.WHOOSH, KX, 14, KZ, 0.8f, 0.9f);
            for (int i = 0; i < 36; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                float sp = 0.3f + r.nextFloat() * 0.6f;
                fx.particle(FxSink.SMOKE, KX + (float) Math.cos(a) * 5, 12, KZ + (float) Math.sin(a) * 5,
                        (float) Math.cos(a) * sp, 0.3f + r.nextFloat() * 0.4f, (float) Math.sin(a) * sp, r.nextBoolean() ? 0x5a0812 : 0x2a0a10, 4f, 50);
            }
        }
        if (k > CrateAnim.OPEN && k < CrateAnim.CLOSE && r.nextFloat() < 0.5f) {
            double a = Math.toRadians(k * 21f);
            fx.particle(FxSink.EMBER, KX + (float) Math.cos(a) * 3, 9, KZ + (float) Math.sin(a) * 3, 0, 0.6f, 0, glow, 1.1f, 30);
        }
        if (k == CrateAnim.SLAM) {
            for (int i = 0; i < 20; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.SMOKE, KX + (float) Math.cos(a) * 7, 10, KZ + (float) Math.sin(a) * 7,
                        (float) Math.cos(a) * 0.5f, 0.15f, (float) Math.sin(a) * 0.5f, 0x3a3030, 3.5f, 40);
            }
        }
    }
}
