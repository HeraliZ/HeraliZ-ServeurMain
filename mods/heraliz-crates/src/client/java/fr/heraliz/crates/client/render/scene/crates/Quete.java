package fr.heraliz.crates.client.render.scene.crates;

import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
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

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Caisse de quetes : un grimoire geant relie de cuir repousse et de laiton, pose sur un lutrin de gres sculpte
 * et ferme par une sangle a cadenas. Au declic, la boussole du medaillon s'envole au centre de la roue ;
 * la couverture bascule, les pages tournent a toute vitesse et l'aiguille finit par designer l'objet gagne.
 */
public final class Quete extends Design {
    private static final Mat STONE = Mat.MAIN;
    private static final Mat LEATHER = Mat.MAIN2;
    private static final Mat LEATHER_PANEL = Mat.MAIN2.sub(0, 0, 0.5f, 0.5f).asFit();
    private static final Mat BRASS = Mat.TRIM;
    private static final Mat BRASS_F = Mat.ACCENT.sub(0, 0, 0.5f, 1);
    private static final Mat COMPASS = Mat.EMBLEM;
    private static final Mat PLATE = Mat.LOCK;
    private static final Mat EDGES = Mat.DECAL_A.withWorld(16);
    private static final Mat MAP = Mat.DECAL_B;
    private static final Mat PAGE = Mat.INTERIOR.sub(0, 0.12f, 1, 0.88f).asFit();
    private static final Mat AMBER = Mat.GEM;
    private static final Mat STRAP = Mat.DARK;
    private static final Mat RIBBON = Mat.SPECIAL;

    /** Livre (repere local : centre du dessous de la couverture arriere, tranche vers +Z). */
    private static final float W = 22f, D = 16f, COVER = 0.9f, PAGES = 5.0f;
    private static final float TOP = COVER + PAGES;
    private static final float SPINE_Z = -D / 2;
    private static final float HINGE_Y = TOP + COVER / 2, HINGE_Z = SPINE_Z + 0.5f;
    /** Bloc de pages. */
    private static final float PX0 = -W / 2 + 0.8f, PX1 = W / 2 - 0.8f, PZ0 = SPINE_Z + 0.6f, PZ1 = D / 2 - 0.9f;
    /** Lutrin : pivot du plateau incline. */
    private static final float PIV_Y = 12.2f, TILT = 12f, FLOOR = 1.6f;
    /** Agrafe du fermoir (repere du livre) : centre de l'anneau. */
    private static final float RING_Y = TOP - 1.6f, RING_Z = D / 2 + 1.9f;
    /** Boussole volante (repere du modele). */
    private static final float AIR_Y = 65f;

    private Mesh base = Mesh.EMPTY, book = Mesh.EMPTY, cover = Mesh.EMPTY, topPage = Mesh.EMPTY;
    private Mesh compass = Mesh.EMPTY, compassGlow = Mesh.EMPTY, needle = Mesh.EMPTY, gimbal = Mesh.EMPTY, gimbalGlow = Mesh.EMPTY;
    private Mesh scroll = Mesh.EMPTY, amber = Mesh.EMPTY, amberGlow = Mesh.EMPTY;
    private final MeshBuilder dyn = new MeshBuilder();

    public Quete() {
        super("quete", 0xFFC43A, 0xFFE9A8);
        ringY = 40;
        ringR = 22;
        revealY = 50;
        riseFromY = 18;
        lockFloorY = FLOOR;
    }

    /** Repere du livre pose sur le plateau incline. */
    private static void bookFrame(Pose p) {
        p.translate(8, PIV_Y, 8).rotX(TILT);
    }

    private static void bookFrame(MeshBuilder b) {
        b.translate(8, PIV_Y, 8).rotateX(TILT);
    }

    /** Triangle (coin de laiton) oriente dans le sens trigonometrique ; angle droit en (sx*s, sy*s). */
    private static float[][] corner(float sx, float sy, float s) {
        float[][] t = {{sx * s, sy * s}, {-sx * s, sy * s}, {sx * s, -sy * s}};
        float cr = (t[1][0] - t[0][0]) * (t[2][1] - t[0][1]) - (t[1][1] - t[0][1]) * (t[2][0] - t[0][0]);
        if (cr < 0) {
            float[] q = t[1];
            t[1] = t[2];
            t[2] = q;
        }
        return t;
    }

    private static float[] fill(int n, float w) {
        float[] a = new float[n];
        java.util.Arrays.fill(a, w);
        return a;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- socle et colonne de gres (verticaux)
        float[][] prof = {{13.0f, 0}, {13.3f, 0.6f}, {12.9f, 1.2f}, {12.3f, FLOOR}, {7.4f, FLOOR}, {6.8f, 2.1f}, {5.6f, 2.7f},
                {4.7f, 3.3f}, {4.3f, 4.1f}, {4.0f, 5.0f}, {3.9f, 7.6f}, {4.4f, 8.0f}, {4.4f, 8.6f}, {3.8f, 8.9f}, {0.05f, 8.9f}};
        b.push().translate(8, 0, 8);
        b.lathe(prof, seg(32), 0, 360, STONE, 1, 1, 0.015f, 0.5f, 3, false);
        b.pop();
        // ---- chapiteau et plateau inclines
        b.push();
        bookFrame(b);
        float[][] cap = {{3.7f, -6.0f}, {4.3f, -5.5f}, {4.1f, -5.0f}, {4.6f, -4.4f}, {6.0f, -3.4f}, {7.2f, -2.5f}, {7.7f, -1.9f}, {7.5f, -1.5f}, {0.05f, -1.5f}};
        b.lathe(cap, seg(28), 0, 360, STONE, 1, 1, 0.015f, 0.5f, 5, false);
        b.push().translate(0, -0.8f, 0.3f).rotateX(-90);
        b.roundedSlab(20.6f, 15.4f, 1.5f, 1.4f, 0.45f, 3, STONE, STONE);
        b.pop();
        b.pop();
        // ---- accessoires sur le socle : rouleau, encrier et plume, pile de livres
        b.push().translate(-0.5f, FLOOR + 1.0f, 15.2f).rotateY(-28).rotateZ(90);
        b.cylinder(1.0f, -3.4f, 3.4f, seg(12), MAP.asFit(), null, null);
        for (int sd = -1; sd <= 1; sd += 2) {
            b.push().translate(0, sd * 3.7f, 0);
            b.cylinder(0.42f, -0.35f, 0.35f, seg(8), BRASS, BRASS, BRASS);
            b.pop();
        }
        b.torus(1.05f, 0.16f, seg(12), 4, 0, 360, RIBBON);
        b.pop();
        b.push().translate(17.2f, FLOOR, 15.4f);
        float[][] ink = {{0.05f, 0}, {1.5f, 0}, {1.7f, 0.3f}, {1.6f, 1.6f}, {0.8f, 2.1f}, {0.7f, 2.6f}, {0.95f, 2.8f}, {0.6f, 2.9f}};
        b.lathe(ink, seg(14), 0, 360, BRASS, 1, 1, 0, 0, 0, false);
        b.color(0.12f, 0.1f, 0.14f);
        b.disc(0.62f, 2.86f, seg(10), STRAP, true);
        b.color(1f);
        b.pop();
        {
            float[][] shaft = Geo.spline(new float[][]{v(17.2f, FLOOR + 2.4f, 15.4f), v(16.4f, FLOOR + 5.5f, 14.4f), v(15.0f, FLOOR + 9.5f, 12.6f)}, seg(3));
            b.tube(shaft, Geo.taper(shaft.length, 0.16f, 0.07f, 1), 5, BRASS, false, true, 0, false);
            int m0 = b.mark();
            float[][] vane = Geo.spline(new float[][]{v(16.9f, FLOOR + 4.2f, 15.0f), v(16.3f, FLOOR + 6.5f, 14.2f), v(15.1f, FLOOR + 9.4f, 12.7f)}, seg(3));
            float[] wv = new float[vane.length];
            for (int i = 0; i < wv.length; i++) wv[i] = 1.8f * (float) Math.sin(Math.PI * Math.min(1, 0.15 + 0.85 * i / (wv.length - 1.0)));
            b.ribbon(vane, wv, v(1, 0, -1), EDGES);
            b.ribbon(vane, wv, v(-1, 0, 1), EDGES);
            b.shade(m0, (x, y, z, nx, ny, nz, rgb) -> {
                rgb[0] *= 1.05f;
                rgb[1] *= 1.02f;
                rgb[2] *= 0.98f;
            });
        }
        for (int k = 0; k < 2; k++) {
            b.push().translate(-0.5f + k * 0.4f, FLOOR + 0.9f + k * 1.8f, 1.2f).rotateY(-60 + k * 25);
            b.push().rotateX(-90);
            b.roundedSlab(7.0f - k, 5.2f - k * 0.6f, 1.8f, 0.6f, 0.25f, 2, LEATHER, LEATHER);
            b.pop();
            b.box6(-3.1f + k * 0.5f, -0.7f, -2.2f + k * 0.3f, 3.1f - k * 0.5f, 0.7f, 2.4f - k * 0.3f, null, EDGES, null, null, null, EDGES);
            b.pop();
        }
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        base = b.build();

        // ---- livre : couverture arriere, bloc de pages, dos a nervures, sangle basse
        MeshBuilder bk = new MeshBuilder();
        bk.push().translate(0, COVER / 2, 0).rotateX(-90);
        bk.roundedSlab(W, D, COVER, 1.2f, 0.3f, 3, LEATHER, LEATHER);
        bk.pop();
        bk.box6(PX0, COVER, PZ0, PX1, TOP - 0.03f, PZ1, EDGES, EDGES, null, null, null, null);
        int nv = 5;
        for (int i = 0; i < nv; i++) {
            float y0 = COVER + PAGES * i / nv, y1 = COVER + PAGES * (i + 1) / nv - (i == nv - 1 ? 0.03f : 0);
            float c0 = -0.55f * (float) Math.sin(Math.PI * i / nv), c1 = -0.55f * (float) Math.sin(Math.PI * (i + 1) / nv);
            bk.face(EDGES, v(0, 0, 1), v(PX0, y0, PZ1 + c0), v(PX1, y0, PZ1 + c0), v(PX1, y1, PZ1 + c1), v(PX0, y1, PZ1 + c1));
        }
        bk.push().translate(0, (TOP + COVER) / 2, SPINE_Z + 0.9f).rotateZ(90);
        bk.push().scale(1, 1, 0.62f);
        bk.cylinder((TOP + COVER) / 2 + 0.25f, -W / 2, W / 2, seg(20), LEATHER, LEATHER, LEATHER);
        bk.pop();
        for (int k = 0; k < 4; k++) {
            bk.push().translate(0, -W / 2 + 3.5f + k * 5f, 0).scale(1, 1, 0.62f);
            bk.torus((TOP + COVER) / 2 + 0.3f, 0.34f, seg(16), 4, 0, 360, LEATHER);
            bk.pop();
        }
        bk.pop();
        for (int sx = -1; sx <= 1; sx += 2) {
            bk.push().translate(sx * (W / 2 - 1.6f), COVER / 2, D / 2 - 1.6f).rotateX(-90);
            bk.slab(corner(sx, -1, 1.8f), COVER + 0.3f, 0.12f, false, BRASS, BRASS, BRASS);
            bk.pop();
        }
        // signets de velours qui sortent de la tranche
        for (int k = 0; k < 2; k++) {
            float x = -3.5f + k * 5.5f;
            float[][] path = Geo.spline(new float[][]{v(x, TOP - 0.2f, PZ1 - 2.5f), v(x, TOP - 0.25f, PZ1 + 0.25f), v(x + 0.3f, TOP - 2.6f, PZ1 + 0.55f),
                    v(x - 0.2f, -1.4f - k * 1.2f, PZ1 + 0.95f)}, 3);
            bk.ribbon(path, fill(path.length, 1.1f), v(1, 0, 0), RIBBON);
        }
        // sangle basse : sous le livre, remonte sur la tranche jusqu'a l'agrafe
        float[][] strapLo = Geo.spline(new float[][]{v(0, -0.12f, PZ1 - 4f), v(0, -0.12f, D / 2 + 0.2f), v(0, 1.0f, D / 2 + 0.75f), v(0, RING_Y - 0.6f, D / 2 + 0.8f)}, 3);
        bk.ribbon(strapLo, fill(strapLo.length, 2.6f), v(1, 0, 0), STRAP);
        Occlusion.bake(bk, 0.4f, 4f, 0.45f, Float.NaN);
        book = bk.build();

        // ---- couverture avant (charniere au dos) : panneau repousse, coins, bossettes d'ambre, sangle haute, agrafe
        MeshBuilder cv = new MeshBuilder();
        cv.push().translate(0, TOP + COVER / 2, 0).rotateX(-90);
        cv.roundedSlab(W, D, COVER, 1.2f, 0.3f, 3, LEATHER_PANEL, LEATHER);
        cv.pop();
        // garde : carte au tresor collee a l'interieur de la couverture
        cv.push().translate(0, TOP - 0.03f, 0.3f).rotateX(90);
        cv.decalZ(MAP, 0, 0, 0, W - 3f, D - 3f);
        cv.pop();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                cv.push().translate(sx * (W / 2 - 1.6f), TOP + COVER / 2, sz * (D / 2 - 1.6f)).rotateX(-90);
                cv.slab(corner(sx, -sz, 1.8f), COVER + 0.36f, 0.12f, false, BRASS_F, BRASS_F, BRASS_F);
                cv.push().translate(sx * 0.65f, -sz * 0.65f, (COVER + 0.36f) / 2).rotateX(90);
                cv.rivet(0.42f, 0.32f, AMBER);
                cv.pop();
                cv.pop();
            }
        }
        cv.push().translate(0, TOP + COVER, 0);
        cv.cylinder(4.9f, -0.05f, 0.22f, seg(28), BRASS, null, BRASS);
        cv.push().translate(0, 0.22f, 0);
        cv.torus(4.9f, 0.3f, seg(28), 4, 0, 360, BRASS_F);
        cv.pop();
        cv.pop();
        float[][] strapHi = Geo.spline(new float[][]{v(0, TOP + COVER + 0.1f, D / 2 - 3.8f), v(0, TOP + COVER + 0.12f, D / 2 + 0.2f), v(0, TOP + 0.25f, D / 2 + 0.78f),
                v(0, RING_Y + 0.4f, D / 2 + 0.86f)}, 3);
        cv.ribbon(strapHi, fill(strapHi.length, 2.6f), v(1, 0, 0), STRAP);
        cv.push().translate(0, RING_Y + 0.1f, D / 2 + 1.0f);
        cv.slab(MeshBuilder.roundRect(3.4f, 3.0f, 0.8f, 3), 0.5f, 0.15f, true, BRASS_F, BRASS, BRASS);
        cv.pop();
        cv.push().translate(0, RING_Y, RING_Z).rotateZ(90);
        cv.torus(0.95f, 0.28f, seg(12), 5, 0, 360, BRASS);
        cv.pop();
        Occlusion.bake(cv, 0.4f, 3f, 0.4f, Float.NaN);
        cover = cv.build();
        MeshBuilder tp = new MeshBuilder();
        tp.push().translate(0, TOP + 0.01f, (PZ0 + PZ1) / 2).rotateX(-90);
        tp.decalZ(PAGE, 0, 0, 0, PX1 - PX0 - 0.4f, PZ1 - PZ0 - 0.4f);
        tp.pop();
        topPage = tp.build();

        // ---- boussole (medaillon amovible) : boitier de laiton, rose des vents, aiguille, cardan
        MeshBuilder cp = new MeshBuilder();
        cp.cylinder(4.3f, 0, 0.7f, seg(28), BRASS, BRASS, COMPASS);
        cp.push().translate(0, 0.7f, 0);
        cp.torus(4.3f, 0.32f, seg(28), 4, 0, 360, BRASS_F);
        cp.pop();
        cp.push().translate(0, 0.35f, 4.55f).rotateX(90);
        cp.cylinder(0.5f, 0, 0.6f, 8, BRASS, null, BRASS);
        cp.pop();
        cp.push().translate(0, 0.35f, 5.3f).rotateZ(90);
        cp.torus(0.7f, 0.2f, 10, 4, 0, 360, BRASS);
        cp.pop();
        compass = cp.build();
        MeshBuilder cg = new MeshBuilder();
        cg.disc(4.35f, 0.72f, seg(28), COMPASS, true);
        compassGlow = cg.build();
        MeshBuilder nd = new MeshBuilder();
        nd.push().translate(0, 0.95f, 0).rotateX(-90);
        nd.colorRgb(0xFF5040);
        nd.slab(new float[][]{{0, 3.6f}, {-0.55f, 0}, {0.55f, 0}}, 0.22f, 0.05f, false, Mat.WHITE, Mat.WHITE, Mat.WHITE);
        nd.color(1f);
        nd.slab(new float[][]{{0.55f, 0}, {-0.55f, 0}, {0, -3.0f}}, 0.22f, 0.05f, false, BRASS, BRASS, BRASS);
        nd.pop();
        nd.push().translate(0, 0.95f, 0);
        nd.rivet(0.45f, 0.35f, AMBER);
        nd.pop();
        needle = nd.build();
        MeshBuilder gb = new MeshBuilder();
        gb.torus(5.9f, 0.3f, seg(32), 5, 0, 360, BRASS_F);
        for (int k = 0; k < 8; k++) {
            gb.push().rotateY(k * 45f).translate(5.9f, 0, 0);
            gb.sphere(0.5f, 6, 4, BRASS);
            gb.pop();
        }
        gimbal = gb.build();
        MeshBuilder gg = new MeshBuilder();
        gg.torus(5.9f, 0.46f, seg(32), 4, 0, 360, Mat.WHITE);
        gimbalGlow = gg.build();

        // ---- parchemins et gemmes d'ambre en orbite
        MeshBuilder sc = new MeshBuilder();
        sc.push().rotateZ(90);
        sc.cylinder(1.1f, -3.0f, 3.0f, seg(12), MAP.asFit(), null, null);
        for (int sd = -1; sd <= 1; sd += 2) {
            sc.push().translate(0, sd * 3.35f, 0);
            sc.cylinder(0.42f, -0.35f, 0.35f, seg(8), BRASS, BRASS, BRASS);
            sc.sphere(0.62f, seg(8), 4, BRASS_F);
            sc.pop();
        }
        sc.torus(1.14f, 0.17f, seg(12), 4, 0, 360, RIBBON);
        sc.pop();
        scroll = sc.build();
        MeshBuilder am = new MeshBuilder();
        am.gem(1.3f, 0.6f, 1.4f, 8, AMBER);
        amber = am.build();
        MeshBuilder ag = new MeshBuilder();
        ag.scale(1.3f);
        ag.gem(1.3f, 0.6f, 1.4f, 8, Mat.WHITE);
        amberGlow = ag.build();

        // ---- cadenas de laiton : ecu au point d'exclamation lumineux
        Padlock.Style st = new Padlock.Style();
        st.shape = Padlock.Shape.ROUND_RECT;
        st.w = 6.0f;
        st.h = 6.2f;
        st.d = 2.7f;
        st.corner = 2.0f;
        st.height = 3.2f;
        st.halfSpan = 1.8f;
        st.body = BRASS;
        st.face = PLATE;
        st.shackle = BRASS;
        st.trim = BRASS_F;
        st.hole = STRAP;
        lock = new Padlock(st);
        lock.build(detail);

        // ---- cle : grimoire miniature frappe d'une boussole
        Mesh[] k = KeySmith.build(BRASS, BRASS_F, 0.55f, (kb, kg) -> {
            kb.push().translate(0, 3.4f, 0);
            kb.roundedSlab(5.2f, 6.4f, 1.8f, 0.5f, 0.2f, 2, LEATHER_PANEL, LEATHER);
            kb.box6(-2.35f, -3.0f, -0.62f, 2.45f, 3.0f, 0.62f, null, EDGES, EDGES, EDGES, null, null);
            kb.push().translate(0, 0, 0.9f).rotateX(90);
            kb.cylinder(1.7f, 0, 0.3f, 16, BRASS, null, COMPASS);
            kb.pop();
            kb.pop();
            kg.push().translate(0, 3.4f, 1.22f).rotateX(90);
            kg.disc(1.8f, 0, 16, COMPASS, true);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== animation
    /** Ouverture de la couverture (0..1+) ; au repos elle se souleve a peine de temps en temps. */
    private float coverOpen(CrateState s) {
        if (!s.active) return 0.02f * Ease.bell((s.time + s.seed * 170) % 170f, 0, 24);
        return Seq.open(s);
    }

    /** Envol de la boussole : 0 sur la couverture, 1 au centre de la roue. */
    private static float fly(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        float up = Ease.outCubic(Ease.range(t, CrateAnim.UNLOCK + 1, CrateAnim.OPEN + 4));
        float down = Ease.inOutCubic(Ease.range(t, CrateAnim.SLAM - 2, CrateAnim.END - 2));
        return up * (1 - down);
    }

    /** Angle de l'aiguille : oscille au repos, s'affole pendant la roue, designe l'objet gagne a l'arret. */
    private static float needleAngle(CrateState s) {
        float wob = 14f * (float) Math.sin(s.time * 0.05f + s.seed * 9) + 4f * (float) Math.sin(s.time * 0.23f);
        if (!s.active) return wob;
        float t = s.t;
        int n = Math.max(1, s.ringSize);
        float settle = CrateAnim.ringAngle(CrateAnim.STOP, n, s.winIndex);
        float spin = 3f * (CrateAnim.ringAngle(t, n, s.winIndex) - settle);
        float pre = t < CrateAnim.SPIN ? 900f * Ease.inQuad(Ease.range(t, CrateAnim.UNLOCK, CrateAnim.SPIN)) - 900f : 0;
        float calm = Ease.range(t, CrateAnim.STOP + 20, CrateAnim.CLOSE);
        return Ease.lerp(spin + pre, wob, Ease.smooth(calm)) + (t < CrateAnim.UNLOCK ? wob : 0);
    }

    /** Pages qui tournent (dynamique, double face) : de la tranche vers la couverture ouverte. */
    private Mesh flippingPages(CrateState s) {
        if (!s.active || s.t < CrateAnim.OPEN + 6 || s.t > CrateAnim.CLOSE + 4) return Mesh.EMPTY;
        float t = s.t;
        float energy = Ease.range(t, CrateAnim.OPEN + 6, CrateAnim.OPEN + 14) * (1 - Ease.range(t, CrateAnim.STOP, CrateAnim.CLOSE));
        MeshBuilder b = dyn.reset();
        float Dp = PZ1 - PZ0 - 0.3f;
        int nu = 2, nv = seg(7);
        int pages = 4;
        float period = 7f + 10f * (1 - Seq.spinSpeed(s));
        for (int k = 0; k < pages; k++) {
            float ph = ((t - CrateAnim.OPEN) / period + k / (float) pages) % 1f;
            float th0 = 100f * Ease.inOutSine(ph) * energy;
            if (th0 < 1f) continue;
            float lift = (float) Math.sin(Math.toRadians(th0 * 1.8f));
            float[][][] P = new float[nv + 1][nu + 1][];
            float[][] S = new float[nv + 1][nu + 1];
            float[][] T = new float[nv + 1][nu + 1];
            for (int i = 0; i <= nv; i++) {
                float u = (float) i / nv;
                float dd = Dp * u;
                double a = Math.toRadians(th0 - 38f * lift * u * u);
                float y = TOP + 0.05f + 0.02f * k + (float) Math.sin(a) * dd;
                float z = PZ0 + 0.3f + (float) Math.cos(a) * dd;
                for (int j = 0; j <= nu; j++) {
                    float fx = (float) j / nu;
                    float x = PX0 + 0.3f + (PX1 - PX0 - 0.6f) * fx;
                    P[i][j] = v(x, y + 0.6f * lift * (float) Math.sin(Math.PI * fx) * u, z);
                    S[i][j] = fx;
                    T[i][j] = 1 - u;
                }
            }
            b.grid(P, null, S, T, false, PAGE, MeshBuilder.Orient.KEEP, false);
        }
        return b.buildDynamic();
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float cav = Seq.cavity(s);
        out.solid(p, base, gl);
        p.push();
        bookFrame(p);
        float pre = anticipation(s);
        float trem = pre * pre;
        p.translate(0.3f * trem * (float) Math.sin(s.t * 7.1f), 1.3f * Ease.smooth(pre), 0.2f * trem * (float) Math.sin(s.t * 5.3f))
                .rotZ(1.8f * trem * (float) Math.sin(s.t * 8.3f)).rotX(1.2f * trem * (float) Math.sin(s.t * 6.1f));
        out.solid(p, book, gl);
        float op = coverOpen(s);
        if (op > 0.001f) {
            out.solid(p, topPage, 0.35f + 0.65f * cav);
            out.solid(p, flippingPages(s), 0.5f + 0.5f * cav);
        }
        float f = fly(s);
        p.push().translate(0, HINGE_Y, HINGE_Z).rotX(-106f * op).translate(0, -HINGE_Y, -HINGE_Z);
        out.solid(p, cover, gl);
        if (f < 0.001f) {
            p.translate(0, TOP + COVER + 0.2f, 0);
            drawCompass(s, p, out, gl, 0);
        }
        p.pop();
        p.pop();
        if (f >= 0.001f) {
            // depart : sur la couverture fermee ; arrivee : au centre de la roue
            Pose q = new Pose();
            bookFrame(q);
            float[] seat = q.apply(0, TOP + COVER + 0.2f, 0);
            float hover = (float) Math.sin(s.time * 0.09f) * 0.8f * f;
            float lower = airLower(s);
            float x = Ease.lerp(seat[0], 8, f), y = Ease.lerp(seat[1], AIR_Y - lower, f) + hover, z = Ease.lerp(seat[2], 8, f);
            float arc = 6f * (float) Math.sin(Math.PI * f);
            p.push().translate(x, y + arc, z).rotX(Ease.lerp(TILT, 52f, f)).rotZ(8f * (float) Math.sin(s.time * 0.07f) * f).scale(1f + 0.25f * f);
            drawCompass(s, p, out, 1f, f);
            p.pop();
        }
    }

    /** Tension avant l'ouverture (0..1, retombe a l'ouverture). */
    private static float anticipation(CrateState s) {
        if (!s.active) return 0;
        return Ease.range(s.t, CrateAnim.UNLOCK, CrateAnim.OPEN) * (1 - Ease.smooth(Ease.range(s.t, CrateAnim.OPEN, CrateAnim.OPEN + 6)));
    }

    /** La boussole redescend un peu pendant la revelation (laisse la place a l'objet gagne). */
    private static float airLower(CrateState s) {
        return s.active ? Ease.smooth(Ease.range(s.t, CrateAnim.STOP + 6, CrateAnim.REVEAL + 14)) * 4f : 0;
    }

    private void drawCompass(CrateState s, Pose p, DrawList out, float gl, float f) {
        float ex = Seq.excite(s);
        out.solid(p, compass, gl);
        out.additive(p, compassGlow, glow, 0.1f + 0.08f * s.pulse(0.1f) + 0.5f * f);
        p.push().rotY(needleAngle(s));
        out.solid(p, needle, 0.8f + 0.2f * ex);
        p.pop();
        if (f > 0.02f) {
            float sp = Seq.spinSpeed(s);
            float g = Ease.outBack(Ease.clamp01(f * 1.2f - 0.2f));
            float a1 = s.time * 2.5f + s.t * s.t * 0.02f * (1 - Ease.range(s.t, CrateAnim.STOP, CrateAnim.STOP + 30)) + sp * 40;
            p.push().translate(0, 0.35f, 0).scale(Math.max(0.01f, g));
            p.push().rotX(90).rotY(a1);
            out.solid(p, gimbal, 0.8f);
            out.additive(p, gimbalGlow, glow, 0.12f + 0.3f * ex);
            p.pop();
            p.push().rotZ(90).rotY(-a1 * 1.3f + 45).scale(1.18f);
            out.solid(p, gimbal, 0.8f);
            out.additive(p, gimbalGlow, glow, 0.12f + 0.3f * ex);
            p.pop();
            p.pop();
        }
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        bookFrame(p);
        p.translate(0, RING_Y + 0.67f, RING_Z).rotX(-TILT);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        float aw = Seq.awaken(s);
        float ex = Seq.excite(s);
        if (!s.item && s.active) {
            // la lumiere fuit par la tranche avant l'ouverture
            float pre = anticipation(s);
            if (pre > 0.01f) {
                Pose q = new Pose();
                bookFrame(q);
                for (int i = 0; i < 6; i++) {
                    float[] at = q.apply(PX0 + (PX1 - PX0) * (i + 0.5f) / 6, (COVER + TOP) / 2, D / 2 + 0.4f);
                    out.billboard(p, Sprite.HALO, at[0], at[1], at[2], 2.5f + 2f * pre, 0, 0xFFD27A, 0.7f * pre * (0.7f + 0.3f * (float) Math.sin(s.t * 3 + i)));
                }
            }
        }
        // parchemins et ambres en orbite
        float orb = orbitScale(s);
        for (int i = 0; i < 6; i++) {
            if (orb <= 0.001f) break;
            float ang = i * 60f + s.time * (0.8f + 0.12f * (i % 3)) + (s.active ? s.t * s.t * 0.018f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)) : 0);
            float rad = 20f + (i % 2) * 1.5f + 4f * ex;
            float y = 13f + 3f * (float) Math.sin(s.time * 0.05f + i * 1.2f) + 8f * aw;
            p.push().translate(8, 0, 8).rotY(ang).translate(rad, y, 0).scale(orb);
            if (i % 2 == 0) {
                p.push().rotZ((float) Math.sin(s.time * 0.06f + i) * 18).rotY(12);
                out.solid(p, scroll, 0.5f);
                p.pop();
            } else {
                p.push().rotY(s.time * 3);
                out.solid(p, amber, 0.9f);
                out.additive(p, amberGlow, glow, 0.2f + 0.2f * ex);
                p.pop();
            }
            p.pop();
        }
        if (s.item) return;
        float[] c = cavityPos();
        CommonFx.cavity(s, p, out, this, c[0], c[1], c[2], 8f);
        out.ground(p, Sprite.HALO, 8, FLOOR + 0.1f, 8, 16f, 0, glow, 0.1f + 0.06f * s.pulse(0.08f) + 0.25f * aw);
        float f = fly(s);
        if (f > 0.02f) {
            // disque runique derriere la boussole volante
            float lower = airLower(s);
            p.push().translate(8, AIR_Y - lower, 8).rotX(52f - 90f).rotZ(-s.time * 1.5f);
            out.quad(p, Sprite.RUNE, -12, -12, 12, 12, glow, 0.35f * f);
            p.pop();
            out.billboard(p, Sprite.HALO, 8, AIR_Y - lower, 8, 16f, 0, glow, 0.3f * f);
        }
        // fuite de lumiere quand la couverture se souleve au repos
        float op = coverOpen(s);
        if (!s.active && op > 0.002f) {
            out.billboard(p, Sprite.HALO, c[0], c[1] + 2, c[2] + 4, 14f, 0, glow, 12f * op);
        }
    }

    @Override
    public float[] cavityPos() {
        Pose q = new Pose();
        bookFrame(q);
        return q.apply(0, TOP + 0.2f, 0);
    }

    // =================================================================== particules
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.1f) {
            fx.particle(FxSink.DOT, 8 + (r.nextFloat() - 0.5f) * 22, 14 + r.nextFloat() * 10, 8 + (r.nextFloat() - 0.5f) * 18, 0, 0.05f, 0,
                    r.nextBoolean() ? glow : 0xFFF2C0, 0.9f, 50);
        }
        float op = coverOpen(s);
        if (!s.active && op > 0.012f && r.nextFloat() < 0.6f) {
            float[] c = cavityPos();
            fx.particle(FxSink.SPARK, c[0] + (r.nextFloat() - 0.5f) * 18, c[1] + 1f, c[2] + 6f + r.nextFloat() * 2f, 0, 0.25f, 0.1f, glow, 1.1f, 20);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        float[] c = cavityPos();
        if (k == CrateAnim.UNLOCK + 2) fx.sound(Snd.BOOK, 8, 10, 8, 0.6f, 0.7f);
        if (k > CrateAnim.UNLOCK && k < CrateAnim.OPEN && k % 3 == 0) {
            fx.particle(FxSink.DOT, 8 + rnd(r, 9f), 8 + r.nextFloat() * 3, 8 + rnd(r, 6f), rnd(r, 0.05f), 0.08f, rnd(r, 0.05f), 0xFFD27A, 1.0f, 20);
        }
        if (k == CrateAnim.UNLOCK + 1) {
            fx.sound(Snd.WHOOSH, 8, c[1] + 4, 8, 0.9f, 1.3f);
            for (int i = 0; i < 20; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.SPARK, c[0] + (float) Math.cos(a) * 4, c[1] + 1.5f, c[2] + (float) Math.sin(a) * 4,
                        (float) Math.cos(a) * 0.6f, 0.9f, (float) Math.sin(a) * 0.6f, glow, 1.4f, 20);
            }
        }
        if (k == CrateAnim.OPEN) {
            fx.sound(Snd.BOOK, c[0], c[1], c[2], 1.2f, 1.0f);
        }
        // pages qui defilent
        if (k > CrateAnim.OPEN + 6 && k < CrateAnim.STOP && (k - CrateAnim.OPEN) % 4 == 0) {
            fx.sound(Snd.PAPER, c[0], c[1], c[2], 0.35f, 1.0f + r.nextFloat() * 0.4f);
        }
        // glyphes dores et feuillets qui s'elevent des pages
        if (k > CrateAnim.OPEN + 4 && k < CrateAnim.CLOSE && r.nextFloat() < 0.9f) {
            fx.particle(FxSink.WISP, c[0] + (r.nextFloat() - 0.5f) * 16, c[1] + 0.5f, c[2] + (r.nextFloat() - 0.5f) * 11,
                    0, 0.3f + r.nextFloat() * 0.3f, 0, r.nextBoolean() ? glow : 0xFFF2C0, 1.3f, 40);
            fx.particle(FxSink.PAPER, c[0] + (r.nextFloat() - 0.5f) * 14, c[1] + 1f, c[2] + (r.nextFloat() - 0.5f) * 8,
                    (r.nextFloat() - 0.5f) * 0.6f, 0.7f + r.nextFloat() * 0.5f, (r.nextFloat() - 0.5f) * 0.6f, 0xF4E8C8, 1.0f, 40);
        }
        // spirale d'etincelles autour de la boussole pendant la roue
        if (k >= CrateAnim.SPIN && k < CrateAnim.STOP) {
            for (int arm = 0; arm < 2; arm++) {
                double a = Math.toRadians(k * 25f + arm * 180);
                fx.particle(FxSink.SPARK, 8 + (float) Math.cos(a) * 9, AIR_Y + (float) Math.sin(k * 0.3f) * 2, 8 + (float) Math.sin(a) * 9,
                        0, 0.1f, 0, arm == 0 ? glow : 0xFFFFFF, 1.2f, 14);
            }
        }
        if (k == CrateAnim.SLAM) {
            fx.sound(Snd.BOOK, c[0], c[1], c[2], 0.9f, 1.3f);
            for (int i = 0; i < 18; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.SMOKE, c[0] + (float) Math.cos(a) * 11, c[1] - 2, c[2] + (float) Math.sin(a) * 8,
                        (float) Math.cos(a) * 0.5f, 0.12f, (float) Math.sin(a) * 0.5f, 0xC8B090, 2.6f, 35);
            }
        }
    }
}
