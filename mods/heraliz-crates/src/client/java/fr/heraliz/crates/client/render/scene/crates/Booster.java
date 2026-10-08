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
import fr.heraliz.crates.client.render.scene.Trail;

import java.util.Random;

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Caisse Stickers : le Booster Holo. Un paquet booster en aluminium holographique (face imprimee "STICKERS", bouchon
 * serti, bande de dechirure rouge) plante dans un presentoir en acrylique, sur un podium hexagonal en carbone cercle de
 * LED arc-en-ciel et eclaire par deux spots. Trois stickers tournent autour du paquet. Le cadenas est un sceau holo
 * accroche au trou de suspension.
 * <p>A l'ouverture : le paquet sort du presentoir en avalant les stickers, tremble (la lumiere fuit par la ligne de
 * dechirure), la bande se dechire de droite a gauche, le bouchon s'envole ; explosion holographique (arret sur image),
 * les douze stickers jaillissent en gerbe, tournoient autour de la roue en montrant leurs reflets, puis viennent se
 * plaquer au sol un par un quand la roue s'arrete. A la fermeture, ils se decollent et rentrent dans le paquet, le
 * bouchon revient se sceller et le paquet redescend dans son presentoir.
 */
public final class Booster extends Design {
    private static final Mat FOIL = Mat.MAIN;
    private static final Mat SHEET = Mat.MAIN2;
    private static final Mat CRIMP = Mat.TRIM;
    private static final Mat CARBON = Mat.ACCENT;
    private static final Mat FRONT = Mat.EMBLEM;
    private static final Mat SEAL = Mat.LOCK;
    private static final Mat ART_B = Mat.DECAL_A;
    private static final Mat BACK_B = Mat.DECAL_B;
    private static final Mat INSIDE = Mat.INTERIOR;
    private static final Mat CHROME = Mat.GEM;
    private static final Mat BLACK = Mat.DARK;
    private static final Mat PINK = Mat.SPECIAL;

    private static final int[] HOLO = {0xFF4FD8, 0x4FE8FF, 0xFFE24F, 0x7AFF8A, 0xA07CFF, 0xFF8A4F};

    /** Podium hexagonal ; paquet (largeur, hauteur, epaisseur, sertissage, ligne de dechirure), pose au repos. */
    private static final float POD_R = 12.3f, POD_H = 1.8f;
    private static final float W = 11f, H = 16f, D = 2.4f, CRIMP_H = 1.4f, TEAR_Y = 12.6f;
    private static final float PACK_Y = 3.0f, TILT = -7f, LIFT_H = 7f;
    private static final int NST = 12;
    private static final float ST = 6.0f;
    /** Bouche du paquet quand il flotte (depart et retour des stickers). */
    private static final float[] MOUTH = {8, PACK_Y + LIFT_H + TEAR_Y - 0.6f, 8};

    private Mesh podium = Mesh.EMPTY, podFoil = Mesh.EMPTY, stand = Mesh.EMPTY, standTrim = Mesh.EMPTY, spots = Mesh.EMPTY, spotCone = Mesh.EMPTY;
    private final Mesh[] led = new Mesh[6];
    private Mesh body = Mesh.EMPTY, bodyFront = Mesh.EMPTY, bodyBack = Mesh.EMPTY, mouthFloor = Mesh.EMPTY, mouthGlow = Mesh.EMPTY, tearGlow = Mesh.EMPTY;
    private Mesh cap = Mesh.EMPTY, capFront = Mesh.EMPTY;
    private final Mesh[] stFront = new Mesh[NST], stBack = new Mesh[NST];
    private final float[][] spotAt = new float[2][];

    public Booster() {
        super("sticker", 0x7AF0FF, 0xFF4FD8);
        keyRgb = 0xFF7AE0;
        cavityRgb = 0x9FF4FF;
        cavityAccent = 0xFF6AD8;
        size = 1.55f;
        ringY = 40;
        ringR = 24;
        revealY = 46;
        riseFromY = MOUTH[1];
        lockFloorY = POD_H;
    }

    // =================================================================== construction
    private static float thick(float x, float y) {
        float ex = 1 - (float) Math.pow(Math.min(1, Math.abs(x) / (W / 2)), 4);
        ex = (float) Math.sqrt(Math.max(0, ex));
        float ey = Math.min(Ease.smooth(Ease.clamp01((y - CRIMP_H) / 2.4f)), Ease.smooth(Ease.clamp01((H - CRIMP_H - y) / 2.4f)));
        return 0.14f + (D - 0.14f) * ex * ey;
    }

    /**
     * Nappe du paquet entre y0 et y1 : side = +1 (avant) ou -1 (arriere), zk = fraction de l'epaisseur (1 = peau, <1 =
     * paroi interieure), out = normale vers l'exterieur. fit : UV de la face imprimee (sinon UV en tuiles). (dx, dy) :
     * decalage (bouchon exprime depuis sa charniere).
     */
    private static void sheet(MeshBuilder b, float y0, float y1, int ny, int nx, int side, float zk, boolean out, Mat m, boolean fit, float dx, float dy) {
        float[][][] P = new float[ny][nx][];
        float[][] S = new float[ny][nx], T = new float[ny][nx];
        for (int i = 0; i < ny; i++) {
            float y = y0 + (y1 - y0) * i / (ny - 1);
            for (int j = 0; j < nx; j++) {
                float x = -W / 2 + W * j / (nx - 1);
                P[i][j] = v(x + dx, y + dy, side * zk * thick(x, y) / 2);
                if (fit) {
                    S[i][j] = (float) j / (nx - 1);
                    T[i][j] = 1 - y / H;
                } else {
                    S[i][j] = (x + W / 2) / 14f;
                    T[i][j] = y / 14f;
                }
            }
        }
        // normale calculee de la grille : -Z ; on la retourne pour une face qui doit regarder vers +Z
        boolean wantPlusZ = (side > 0) == out;
        b.grid(P, null, S, T, false, m, wantPlusZ ? MeshBuilder.Orient.FLIP : MeshBuilder.Orient.KEEP, !fit);
    }

    /** Tranches gauche et droite du paquet (soudure). */
    private static void sides(MeshBuilder b, float y0, float y1, int ny, float dx, float dy) {
        for (int sd = -1; sd <= 1; sd += 2) {
            float x = sd * W / 2;
            float[][][] P = new float[ny][2][];
            float[][] S = new float[ny][2], T = new float[ny][2];
            for (int i = 0; i < ny; i++) {
                float y = y0 + (y1 - y0) * i / (ny - 1);
                float d = thick(x, y) / 2;
                P[i][0] = v(x + dx, y + dy, d);
                P[i][1] = v(x + dx, y + dy, -d);
                S[i][0] = 0;
                S[i][1] = 0.02f;
                T[i][0] = T[i][1] = y / 16f;
            }
            b.grid(P, null, S, T, false, CRIMP, sd < 0 ? MeshBuilder.Orient.KEEP : MeshBuilder.Orient.FLIP, true);
        }
    }

    /** Dents de sertissage (dir = +1 vers le haut, -1 vers le bas), sur les deux faces. */
    private static void teeth(MeshBuilder b, float y, float dir, float len, float zk, Mat m, float dx, float dy) {
        int n = 22;
        float tw = W / n;
        for (int k = 0; k < n; k++) {
            float x0 = -W / 2 + k * tw, x1 = x0 + tw, xm = x0 + tw / 2;
            float d0 = thick(x0, y) / 2 * zk, d1 = thick(x1, y) / 2 * zk, dm = thick(xm, y) / 2 * zk;
            float l = len * (0.7f + 0.3f * Ease.hash(k * 7 + (int) (y * 3)));
            for (int f = -1; f <= 1; f += 2) {
                b.face(m, v(0, 0, f), v(x0 + dx, y + dy, f * d0), v(x1 + dx, y + dy, f * d1), v(xm + dx, y + dy + dir * l, f * dm));
            }
        }
    }

    /** Fond en lentille (entre les deux peaux) a la hauteur y ; up = normale vers le haut. */
    private static void lens(MeshBuilder b, float y, float zk, boolean up, Mat m, float dx, float dy) {
        int nx = 15;
        float[][][] P = new float[2][nx][];
        float[][] S = new float[2][nx], T = new float[2][nx];
        for (int j = 0; j < nx; j++) {
            float x = -W / 2 + W * j / (nx - 1);
            float d = thick(x, y) / 2 * zk;
            P[0][j] = v(x + dx, y + dy, d);
            P[1][j] = v(x + dx, y + dy, -d);
            S[0][j] = S[1][j] = (x + W / 2) / 14f;
            T[0][j] = 0;
            T[1][j] = 0.15f;
        }
        // normale calculee : -Y
        b.grid(P, null, S, T, false, m, up ? MeshBuilder.Orient.FLIP : MeshBuilder.Orient.KEEP, true);
    }

    @Override
    protected void build() {
        // ---------------------------------------------------------------- podium hexagonal (un plat face au joueur)
        MeshBuilder b = new MeshBuilder();
        float[] ctr = v(8, POD_H / 2, 8);
        float bev = 0.35f;
        for (int k = 0; k < 6; k++) {
            float a0 = k * 60, a1 = (k + 1) * 60;
            float[] b0 = Geo.polar(8, 8, a0, POD_R, 0), b1 = Geo.polar(8, 8, a1, POD_R, 0);
            float[] t0 = Geo.polar(8, 8, a0, POD_R, POD_H - bev), t1 = Geo.polar(8, 8, a1, POD_R, POD_H - bev);
            b.flatOut(CARBON, ctr, b0, b1, t1, t0);
            float[] c0 = Geo.polar(8, 8, a0, POD_R - bev, POD_H), c1 = Geo.polar(8, 8, a1, POD_R - bev, POD_H);
            b.flatOut(CHROME, ctr, t0, t1, c1, c0);
            float[] r0 = Geo.polar(8, 8, a0, 10.7f, POD_H), r1 = Geo.polar(8, 8, a1, 10.7f, POD_H);
            b.face(BLACK, v(0, 1, 0), c0, c1, r1, r0);
            float[] i0 = Geo.polar(8, 8, a0, 8.3f, POD_H), i1 = Geo.polar(8, 8, a1, 8.3f, POD_H);
            b.face(BLACK, v(0, 1, 0), i0, i1, v(8, POD_H, 8));
            // pieds chromes aux sommets
            float[] q = Geo.polar(8, 8, a0, POD_R - 0.6f, 0);
            b.push().translate(q[0], -0.4f, q[2]);
            b.cylinder(0.7f, 0, 0.4f, 10, CHROME, null, CHROME);
            b.pop();
        }
        // presentoir : socle de fente (le bloc d'acrylique est dessine en translucide)
        b.box(8 - 6.4f, POD_H, 8 - 2.4f, 8 + 6.4f, POD_H + 0.35f, 8 + 2.4f, BLACK);
        // spots : deux projecteurs chromes braques sur le paquet
        for (int i = 0; i < 2; i++) {
            float[] q = Geo.polar(8, 8, 60 + i * 60, 9.6f, POD_H);
            float[] tgt = v(8, PACK_Y + 8, 8);
            float yaw = (float) Math.toDegrees(Math.atan2(tgt[0] - q[0], tgt[2] - q[2]));
            float dist = (float) Math.hypot(tgt[0] - q[0], tgt[2] - q[2]);
            float pitch = (float) Math.toDegrees(Math.atan2(tgt[1] - q[1] - 1.4f, dist));
            b.push().translate(q[0], q[1], q[2]);
            b.cylinder(0.3f, 0, 1.0f, 8, CHROME, null, CHROME);
            b.push().translate(0, 1.4f, 0).rotateY(yaw).rotateX(-pitch);
            b.push().rotateX(90);
            b.cylinder(0.62f, -0.9f, 0.5f, 12, BLACK, BLACK, Mat.WHITE);
            b.push().translate(0, 0.5f, 0);
            b.torus(0.62f, 0.12f, 12, 4, 0, 360, CHROME);
            b.pop();
            b.pop();
            b.pop();
            b.pop();
            spotAt[i] = v(q[0], q[1] + 1.4f, q[2]);
        }
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        podium = b.build();

        MeshBuilder f = new MeshBuilder();
        for (int k = 0; k < 6; k++) {
            float a0 = k * 60, a1 = (k + 1) * 60;
            f.face(FOIL, v(0, 1, 0), Geo.polar(8, 8, a0, 10.7f, POD_H + 0.02f), Geo.polar(8, 8, a1, 10.7f, POD_H + 0.02f),
                    Geo.polar(8, 8, a1, 8.3f, POD_H + 0.02f), Geo.polar(8, 8, a0, 8.3f, POD_H + 0.02f));
        }
        podFoil = f.build();
        for (int k = 0; k < 6; k++) {
            MeshBuilder l = new MeshBuilder();
            float a0 = k * 60 + 3, a1 = (k + 1) * 60 - 3;
            float r = POD_R + 0.04f;
            l.flatOut(Mat.WHITE, ctr, Geo.polar(8, 8, a0, r, 0.55f), Geo.polar(8, 8, a1, r, 0.55f), Geo.polar(8, 8, a1, r, 1.1f), Geo.polar(8, 8, a0, r, 1.1f));
            led[k] = l.build();
        }
        // bloc d'acrylique et aretes chromees
        MeshBuilder sa = new MeshBuilder();
        sa.box(8 - 6.2f, POD_H + 0.35f, 8 - 2.2f, 8 + 6.2f, POD_H + 2.8f, 8 + 2.2f, Mat.WHITE);
        stand = sa.build();
        MeshBuilder st = new MeshBuilder();
        float sy = POD_H + 2.8f;
        st.box(8 - 6.3f, sy - 0.12f, 8 + 2.1f, 8 + 6.3f, sy + 0.05f, 8 + 2.3f, CHROME);
        st.box(8 - 6.3f, sy - 0.12f, 8 - 2.3f, 8 + 6.3f, sy + 0.05f, 8 - 2.1f, CHROME);
        st.box(8 - 6.3f, sy - 0.12f, 8 - 2.3f, 8 - 6.1f, sy + 0.05f, 8 + 2.3f, CHROME);
        st.box(8 + 6.1f, sy - 0.12f, 8 - 2.3f, 8 + 6.3f, sy + 0.05f, 8 + 2.3f, CHROME);
        st.box(8 - 5.9f, sy - 0.02f, 8 - 1.5f, 8 + 5.9f, sy + 0.03f, 8 + 1.5f, BLACK);
        standTrim = st.build();
        // cone de lumiere (large cote paquet, pointe sur la lampe) : oriente a l'image
        MeshBuilder sc = new MeshBuilder();
        Geo.cone(sc, v(0, 0, 0), v(0, 0, -1), 1f, 1f, 16, Mat.WHITE);
        spotCone = sc.build();

        // ---------------------------------------------------------------- paquet (repere : bas au centre, avant en +Z)
        MeshBuilder pb = new MeshBuilder();
        MeshBuilder pf = new MeshBuilder();
        MeshBuilder pk = new MeshBuilder();
        int nx = seg(17);
        sheet(pb, 0, TEAR_Y, seg(26), nx, 1, 1, true, FRONT, true, 0, 0);
        sheet(pf, 0, TEAR_Y, seg(26), nx, 1, 1, true, FRONT, true, 0, 0);
        sheet(pb, 0, TEAR_Y, seg(26), nx, -1, 1, true, FOIL, false, 0, 0);
        sheet(pk, 0, TEAR_Y, seg(26), nx, -1, 1, true, FOIL, false, 0, 0);
        sides(pb, 0, TEAR_Y, seg(20), 0, 0);
        teeth(pb, 0, -1, 0.42f, 1, CRIMP, 0, 0);
        // bouche : parois interieures, dents de dechirure (cachees tant que le bouchon est la)
        sheet(pb, TEAR_Y - 2.8f, TEAR_Y, 6, nx, 1, 0.96f, false, INSIDE, false, 0, 0);
        sheet(pb, TEAR_Y - 2.8f, TEAR_Y, 6, nx, -1, 0.96f, false, INSIDE, false, 0, 0);
        teeth(pb, TEAR_Y, 1, 0.28f, 0.97f, INSIDE, 0, 0);
        body = pb.build();
        bodyFront = pf.build();
        bodyBack = pk.build();
        MeshBuilder mf = new MeshBuilder();
        lens(mf, TEAR_Y - 2.8f, 0.96f, true, INSIDE, 0, 0);
        mouthFloor = mf.build();
        MeshBuilder mg = new MeshBuilder();
        lens(mg, TEAR_Y - 0.25f, 0.95f, true, Mat.WHITE, 0, 0);
        mouthGlow = mg.build();
        MeshBuilder tg = new MeshBuilder();
        sheet(tg, TEAR_Y - 0.22f, TEAR_Y + 0.22f, 2, nx, 1, 1.03f, true, Mat.WHITE, false, 0, 0);
        sheet(tg, TEAR_Y - 0.22f, TEAR_Y + 0.22f, 2, nx, -1, 1.03f, true, Mat.WHITE, false, 0, 0);
        tearGlow = tg.build();

        // bouchon, exprime depuis sa charniere (coin gauche de la ligne de dechirure)
        float dx = W / 2, dy = -TEAR_Y;
        MeshBuilder cb = new MeshBuilder();
        MeshBuilder cf = new MeshBuilder();
        sheet(cb, TEAR_Y, H, seg(10), nx, 1, 1, true, FRONT, true, dx, dy);
        sheet(cf, TEAR_Y, H, seg(10), nx, 1, 1, true, FRONT, true, dx, dy);
        sheet(cb, TEAR_Y, H, seg(10), nx, -1, 1, true, FOIL, false, dx, dy);
        sides(cb, TEAR_Y, H, seg(8), dx, dy);
        teeth(cb, H, 1, 0.42f, 1, CRIMP, dx, dy);
        sheet(cb, TEAR_Y, TEAR_Y + 1.2f, 3, nx, 1, 0.96f, false, INSIDE, false, dx, dy);
        sheet(cb, TEAR_Y, TEAR_Y + 1.2f, 3, nx, -1, 0.96f, false, INSIDE, false, dx, dy);
        lens(cb, TEAR_Y + 1.2f, 0.96f, false, INSIDE, dx, dy);
        teeth(cb, TEAR_Y, -1, 0.28f, 0.97f, INSIDE, dx, dy);
        cap = cb.build();
        capFront = cf.build();

        // ---------------------------------------------------------------- stickers (recto : art decoupe, verso : papier)
        for (int i = 0; i < NST; i++) {
            Mat art, back;
            if (i < 8) {
                float s0 = (i % 4) / 4f, t0 = (i / 4) / 4f;
                art = SHEET.sub(s0, t0, s0 + 0.25f, t0 + 0.25f);
                back = SHEET.sub(s0, t0 + 0.5f, s0 + 0.25f, t0 + 0.75f);
            } else {
                int k = i - 8;
                float s0 = (k % 2) / 2f, t0 = (k / 2) / 2f;
                art = ART_B.sub(s0, t0, s0 + 0.5f, t0 + 0.5f);
                back = BACK_B.sub(s0, t0, s0 + 0.5f, t0 + 0.5f);
            }
            stFront[i] = quad(art, 0.012f, 1);
            stBack[i] = quad(back, -0.012f, -1);
        }

        // ---------------------------------------------------------------- cadenas : sceau holographique d'authenticite
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.DISC;
        ps.w = 4.6f;
        ps.h = 4.6f;
        ps.d = 1.3f;
        ps.height = 1.5f;
        ps.halfSpan = 0.9f;
        ps.wire = 0.34f;
        ps.body = PINK;
        ps.face = SEAL;
        ps.shackle = CHROME;
        ps.trim = CHROME;
        ps.hole = BLACK;
        lock = new Padlock(ps);
        lock.build(detail);

        // cle : etoile holographique
        Mesh[] k = KeySmith.build(CHROME, PINK, 0.5f, (kb, kg) -> {
            kb.push().translate(0, 2.9f, -0.4f);
            kb.prism(starPts(2.4f, 1.05f), 0.8f, FOIL, CHROME);
            kb.pop();
            kg.push().translate(0, 2.9f, -0.5f);
            kg.prism(starPts(2.8f, 1.25f), 1.0f, Mat.WHITE, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    /** Carre de sticker (ST x ST) dans le plan XY, face +Z (sign = +1) ou -Z (sign = -1). */
    private static Mesh quad(Mat m, float z, int sign) {
        MeshBuilder q = new MeshBuilder();
        float h = ST / 2;
        float[][] pts = {v(-h, -h, z), v(h, -h, z), v(h, h, z), v(-h, h, z)};
        float[][] uv = {{m.u(0), m.v(1)}, {m.u(1), m.v(1)}, {m.u(1), m.v(0)}, {m.u(0), m.v(0)}};
        q.poly(pts, uv, null, v(0, 0, sign));
        return q.build();
    }

    private static float[][] starPts(float r1, float r2) {
        float[][] p = new float[10][];
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(90 + i * 36);
            float r = i % 2 == 0 ? r1 : r2;
            p[i] = new float[]{(float) Math.cos(a) * r, (float) Math.sin(a) * r};
        }
        return p;
    }

    // =================================================================== chronologie
    private static final float LIFT0 = CrateAnim.UNLOCK + 1, LIFT1 = CrateAnim.OPEN - 2;
    private static final float SHAKE0 = CrateAnim.OPEN - 4, TEAR0 = CrateAnim.OPEN + 2, TEAR1 = CrateAnim.OPEN + 8;
    private static final float BURST = TEAR1;
    private static final float CAP_BACK0 = CrateAnim.CLOSE + 4, CAP_BACK1 = CrateAnim.CLOSE + 14, DESC1 = CrateAnim.SLAM;

    /** Temps de la choregraphie (arret sur image a l'explosion). */
    private static float tt(CrateState s) {
        return Seq.hitStop(s.t, BURST, 3, 10);
    }

    private static float launchT(int i) {
        return BURST + i * 0.8f;
    }

    /** Debut de la descente vers le sol (le sticker touche le sol 5 ticks plus tard). */
    private static float slapT(int i) {
        return CrateAnim.STOP - 9 + i;
    }

    private static float backT(int i) {
        return CrateAnim.CLOSE + i * 0.6f;
    }

    /** Couleur irisee (teinte 0..1). */
    private static int hue(float h) {
        h = h - (float) Math.floor(h);
        float r = Ease.clamp01(Math.abs(h * 6 - 3) - 1), g = Ease.clamp01(2 - Math.abs(h * 6 - 2)), b = Ease.clamp01(2 - Math.abs(h * 6 - 4));
        r = 0.35f + 0.65f * r;
        g = 0.35f + 0.65f * g;
        b = 0.35f + 0.65f * b;
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    /** Hauteur du paquet au-dessus de sa pose (sortie du presentoir, recul a l'explosion, retour). */
    private static float lift(CrateState s, float tt) {
        if (!s.active) return 0;
        float up = Ease.outBack(Ease.range(tt, LIFT0, LIFT1), 1.4f) * LIFT_H;
        float recoil = tt > BURST ? -1.6f * (float) Math.exp(-(tt - BURST) * 0.3f) * Ease.clamp01((tt - BURST) * 2) : 0;
        float down = Ease.inCubic(Ease.range(tt, CAP_BACK1, DESC1));
        float bounce = tt > DESC1 ? 0.5f * Math.abs((float) Math.sin((tt - DESC1) * 0.8f)) * (float) Math.exp(-(tt - DESC1) * 0.4f) : 0;
        return (up + recoil) * (1 - down) + bounce;
    }

    /** Pose du paquet (origine au bas du paquet, au centre). */
    private void packPose(CrateState s, float tt, Pose p) {
        float a = s.active ? Ease.smooth(Ease.range(tt, LIFT0, LIFT1)) * (1 - Ease.smooth(Ease.range(tt, CAP_BACK1, DESC1))) : 0;
        float idleYaw = 10f * (float) Math.sin(s.time * 0.02f);
        float spin = s.active ? 720f * Ease.inOutSine(Ease.range(tt, BURST + 10, CAP_BACK1)) : 0;
        float bob = 0.3f * (float) Math.sin(s.time * 0.06f) * (0.3f + 0.7f * a);
        float tr = s.active ? Ease.range(tt, SHAKE0, TEAR1) : 0;
        float trem = tr > 0 && tt < TEAR1 + 1 ? tr * tr * 0.35f : 0;
        p.translate(8 + trem * (float) Math.sin(s.t * 7.3f), PACK_Y + lift(s, tt) + bob, 8);
        p.rotY(idleYaw * (1 - a) + spin).rotX(TILT * (1 - a)).rotZ(trem * 12f * (float) Math.sin(s.t * 9.1f));
        if (s.active && tt > BURST) {
            float e = (float) Math.exp(-(tt - BURST) * 0.35f);
            p.scale(1 + 0.07f * e, 1 - 0.1f * e, 1 + 0.07f * e);
        }
    }

    /** Dechirure (0..1) : 0 scelle, 1 bouchon arrache ; repasse a 0 quand il se rescelle. */
    private static float tearU(CrateState s, float tt) {
        if (!s.active) return 0;
        if (tt < TEAR1) return Ease.inQuad(Ease.range(tt, TEAR0, TEAR1));
        return 1 - Ease.smooth(Ease.range(tt, CAP_BACK1, CAP_BACK1 + 3));
    }

    /** Age de vol du bouchon (0 = accroche, jusqu'a 26). */
    private static float capAge(CrateState s, float tt) {
        if (!s.active || tt < TEAR1) return 0;
        if (tt < CAP_BACK0) return Math.min(tt - TEAR1, 26);
        return 26 * (1 - Ease.inOutCubic(Ease.range(tt, CAP_BACK0, CAP_BACK1)));
    }

    /** Pose du bouchon dans le repere du paquet ; renvoie son echelle (0 = disparu). */
    private static float capPose(CrateState s, float tt, Pose p) {
        float u = tearU(s, tt);
        float a = capAge(s, tt);
        p.translate(-W / 2, TEAR_Y, 0);
        if (a > 0) {
            p.translate(-0.35f * a, 1.7f * a - 0.035f * a * a, -0.5f * a).rotY(a * 16f).rotX(-a * 9f);
        }
        p.rotZ(28f * u).rotX(-18f * u);
        float sc = 1 - Ease.smooth(Ease.range(a, 12, 22));
        if (sc < 1) p.scale(Math.max(sc, 0.001f));
        return sc;
    }

    private static float orbitRot(CrateState s, float tt) {
        return (tt - BURST) * 1.3f + 320f * Ease.smooth(Ease.range(tt, CrateAnim.RISE, CrateAnim.STOP));
    }

    private static float[] orbitPos(CrateState s, int i, float tt) {
        float th = i * 137.5f + orbitRot(s, tt);
        float r = 12.5f + 1.8f * (i % 3);
        float y = POD_H + 8f + (i % 4) * 4.6f + 0.8f * (float) Math.sin(tt * 0.08f + i);
        return Geo.polar(8, 8, th, r, y);
    }

    private static float[] floorPos(CrateState s, int i) {
        float th = i * 137.5f + orbitRot(s, slapT(i));
        return Geo.polar(8, 8, th, 15.4f + 2.2f * Ease.hash(i * 5 + 3), 0.07f + 0.004f * i);
    }

    /**
     * Etat d'un sticker : position (out[0..2]), rotations Y, X, Z (out[3..5]), echelle (out[6]), reflet holo (out[7]).
     * Renvoie false s'il est cache.
     */
    private boolean sticker(CrateState s, float tt, int i, float[] out) {
        if (!s.active || tt < launchT(i)) return false;
        float h1 = Ease.hash(i * 3 + 1), h2 = Ease.hash(i * 3 + 2);
        float a0 = i * 137.5f;
        float age = tt - launchT(i);
        float ar = (float) Math.toRadians(a0);
        float hs = 0.5f + 0.3f * h1, vs = 1.5f + 0.5f * h2, g = 0.12f;
        float fa = Math.min(age, 22);
        float[] fl = v(MOUTH[0] + (float) Math.cos(ar) * hs * fa, MOUTH[1] + vs * fa - g / 2 * fa * fa, MOUTH[2] + (float) Math.sin(ar) * hs * fa);
        float fry = a0 + age * 28f * (i % 2 == 0 ? 1 : -1), frx = 35f * (float) Math.sin(age * 0.45f + i);
        float w = Ease.smooth(Ease.range(age, 8, 24));
        float[] op = orbitPos(s, i, tt);
        float th = a0 + orbitRot(s, tt);
        float f = (tt * 0.018f + h1) % 1f;
        float flip = 360f * Ease.smooth(Ease.range(f, 0.42f, 0.58f));
        float ory = 90 - th + flip, orx = -12f;
        float[] pos = Geo.lerp(fl, op, w);
        float ry = Ease.lerp(fry, ory, w), rx = Ease.lerp(frx, orx, w), rz = 0;
        float sc = Ease.outBack(Ease.clamp01(age / 5f), 1.6f) * (0.35f + 0.65f * Ease.clamp01(age / 5f));
        float holo = 0.25f + 0.6f * Ease.bell(f, 0.4f, 0.6f) + 0.6f * (float) Math.exp(-age * 0.25f);
        // plaque au sol
        float st = slapT(i);
        if (tt >= st) {
            float u = Ease.range(tt, st, st + 5);
            float[] o = orbitPos(s, i, st);
            float[] fp = floorPos(s, i);
            float thS = a0 + orbitRot(s, st);
            float ry0 = 90 - thS;
            float ryF = ry0 + 40f * (h2 - 0.5f);
            float k = Ease.inQuad(u);
            pos = Geo.lerp(o, fp, k);
            pos[1] += 3f * (float) Math.sin(u * Math.PI) * (1 - u);
            ry = Ease.lerp(ry0, ryF, k);
            rx = Ease.lerp(-12f, -90f, Ease.smooth(u));
            float land = tt - (st + 5);
            sc = 1 + (land > 0 ? 0.28f * (float) Math.exp(-land * 0.5f) * (float) Math.cos(land * 1.2f) : 0);
            holo = 0.2f + (land > 0 ? 1.2f * (float) Math.exp(-land * 0.3f) : 0.3f);
            // retour dans le paquet
            float bt = backT(i);
            if (tt >= bt) {
                float r = Ease.range(tt, bt, bt + 9);
                if (r >= 1) return false;
                float kk = Ease.inCubic(r);
                pos = Geo.lerp(fp, MOUTH, kk);
                pos[1] += 6f * (float) Math.sin(r * Math.PI);
                ry = ryF + 540f * r;
                rx = Ease.lerp(-90f, 0f, Ease.smooth(Math.min(1, r * 2)));
                sc = 1 - 0.75f * kk;
                holo = 0.5f;
            }
        }
        out[0] = pos[0];
        out[1] = pos[1];
        out[2] = pos[2];
        out[3] = ry;
        out[4] = rx;
        out[5] = rz;
        out[6] = sc;
        out[7] = holo;
        return true;
    }

    /** Stickers satellites du repos (0 = absorbes par le paquet). */
    private static float satVis(CrateState s) {
        if (!s.active) return 1;
        if (s.t < LIFT0 + 9) return 1 - Ease.inCubic(Ease.range(s.t, LIFT0, LIFT0 + 9));
        return Ease.outBack(Ease.range(s.t, DESC1 + 2, CrateAnim.END), 1.5f);
    }

    private static final int[] SAT = {0, 4, 5};

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float b = s.t >= BURST ? 0.55f * (float) Math.exp(-(s.t - BURST) * 0.3f) : 0;
        float tr = s.t > SHAKE0 && s.t < TEAR1 ? 0.12f * Ease.range(s.t, SHAKE0, TEAR1) : 0;
        float sl = 0;
        for (int i = 0; i < NST; i++) {
            float l = s.t - (slapT(i) + 5);
            if (l >= 0 && l < 3) sl = Math.max(sl, 0.12f * (1 - l / 3));
        }
        return Math.max(Math.max(b, tr), Math.max(sl, 0.3f * Seq.stopFlash(s)));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        float tt = s.active ? tt(s) : 0;
        float hb = s.time * 0.006f;
        // podium, anneau holo (reflet qui tourne), LED arc-en-ciel
        out.solid(p, podium, gl);
        out.solid(p, podFoil, gl);
        out.additive(p, podFoil, hue(hb + 0.3f), 0.1f + 0.2f * aw);
        for (int k = 0; k < 6; k++) {
            out.additive(p, led[k], hue(k / 6f - s.time * (0.004f + 0.02f * aw)), 0.8f);
        }
        out.solid(p, standTrim, gl);
        // paquet
        p.push();
        packPose(s, tt, p);
        out.solid(p, body, gl);
        out.additive(p, bodyFront, hue(hb), 0.08f + 0.14f * aw);
        out.additive(p, bodyBack, hue(hb + 0.5f), 0.05f + 0.08f * aw);
        boolean open = s.active && tt > TEAR0;
        if (open) out.solidTinted(p, mouthFloor, 0, 0.35f, 0.35f, 0.4f);
        float leak = s.active ? Ease.smooth(Ease.range(tt, SHAKE0, TEAR0)) * (tt < TEAR1 ? 1 : 0) : 0;
        if (leak > 0.01f) out.additive(p, tearGlow, hue(s.t * 0.05f), 0.9f * leak * (0.7f + 0.3f * (float) Math.sin(s.t * 3)));
        if (open) {
            float m = Ease.range(tt, TEAR0, TEAR1) * (1 - Ease.smooth(Ease.range(tt, CAP_BACK0, CAP_BACK1)));
            out.additive(p, mouthGlow, 0xFFFFFF, 0.9f * m);
        }
        // bouchon
        p.push();
        float csc = capPose(s, tt, p);
        if (csc > 0.01f) {
            out.solid(p, cap, gl);
            out.additive(p, capFront, hue(hb + 0.1f), 0.08f + 0.14f * aw);
        }
        p.pop();
        p.pop();
        // stickers satellites (repos)
        float sv = satVis(s);
        if (sv > 0.01f) {
            Pose q = new Pose();
            packPose(s, tt, q);
            float[] c = q.apply(0, 8, 0);
            for (int k = 0; k < SAT.length; k++) {
                float th = s.time * 1.1f + k * 120;
                float[] o = Geo.polar(8, 8, th, 10.5f, PACK_Y + 4.5f + k * 4.2f + 0.5f * (float) Math.sin(s.time * 0.05f + k));
                float[] at = s.active && s.t < LIFT0 + 9 ? Geo.lerp(c, o, sv) : o;
                p.push().translate(at[0], at[1], at[2]).rotY(90 - th).rotX(-10).scale(0.62f * sv);
                out.translucent(p, stFront[SAT[k]], 0xFFFFFF, 1f);
                out.translucent(p, stBack[SAT[k]], 0xFFFFFF, 1f);
                out.additive(p, stFront[SAT[k]], hue(hb * 3 + k * 0.33f), 0.3f);
                p.pop();
            }
        }
        // les douze stickers
        float[] st = new float[8];
        for (int i = 0; i < NST; i++) {
            if (!sticker(s, tt, i, st)) continue;
            p.push().translate(st[0], st[1], st[2]).rotY(st[3]).rotX(st[4]).rotZ(st[5]).scale(st[6]);
            out.translucent(p, stFront[i], 0xFFFFFF, 1f);
            out.translucent(p, stBack[i], 0xFFFFFF, 1f);
            out.additive(p, stFront[i], hue(hb * 4 + i * 0.083f), Math.min(1f, st[7]) * 0.6f);
            p.pop();
        }
        // presentoir en acrylique (verre depoli)
        out.translucent(p, stand, 0xCFF4FF, 0.2f);
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        // accroche au trou de suspension, en haut du paquet
        packPose(s, 0, p);
        p.translate(0, H - 0.6f, 0.25f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float aw = Seq.awaken(s);
        float tt = s.active ? tt(s) : 0;
        float hb = s.time * 0.006f;
        out.ground(p, Sprite.HALO, 8, 0.06f, 8, 18f, 0, hue(hb), 0.12f + 0.2f * aw);
        out.ground(p, Sprite.RING, 8, POD_H + 0.06f, 8, 10.4f, s.time * 0.4f, hue(hb + 0.5f), 0.18f + 0.3f * aw);
        // LED : halos
        for (int k = 0; k < 6; k++) {
            float[] q = Geo.polar(8, 8, k * 60 + 30, POD_R * 0.87f + 0.4f, 0.85f);
            out.billboard(p, Sprite.HALO, q[0], q[1], q[2], 2.2f, 0, hue(k / 6f - s.time * (0.004f + 0.02f * aw)), 0.35f);
        }
        // spots : cone de lumiere et eclat de la lampe
        Pose pk = new Pose();
        packPose(s, tt, pk);
        float[] tgt = pk.apply(0, 8, 0);
        for (int i = 0; i < 2; i++) {
            float[] l = spotAt[i];
            float[] d = Geo.sub(tgt, l);
            float len = Geo.len(d);
            float yaw = (float) Math.toDegrees(Math.atan2(d[0], d[2]));
            float pitch = (float) Math.toDegrees(Math.atan2(d[1], Math.hypot(d[0], d[2])));
            p.push().translate(tgt[0], tgt[1], tgt[2]).rotY(yaw).rotX(-pitch).scale(3.2f, 3.2f, len);
            out.additive(p, spotCone, i == 0 ? 0x9FF4FF : 0xFF9FE8, 0.035f + 0.03f * aw);
            p.pop();
            out.billboard(p, Sprite.FLARE, l[0], l[1], l[2], 1.8f, s.time, i == 0 ? 0x9FF4FF : 0xFF9FE8, 0.6f);
        }
        // reflets qui balaient la face du paquet
        float ph = (s.time * 0.012f) % 1f;
        if (ph < 0.3f) {
            float u = ph / 0.3f;
            float x = -W / 2 + W * u, y = H - 1.5f - (H - 3) * u;
            float[] g = pk.apply(x, y, thick(x, y) / 2 + 0.3f);
            out.billboard(p, Sprite.GLINT, g[0], g[1], g[2], 2.4f, s.time * 3, 0xFFFFFF, 0.9f * Ease.bell(u, 0, 1));
        }
        if (!s.active) return;
        // dechirure : etincelle qui court le long de la ligne
        if (tt > TEAR0 && tt < TEAR1 + 1) {
            float u = Ease.clamp01(tearU(s, tt));
            float x = W / 2 - W * u;
            float[] g = pk.apply(x, TEAR_Y, thick(x, TEAR_Y) / 2 + 0.2f);
            out.billboard(p, Sprite.FLARE, g[0], g[1], g[2], 3.5f, s.time * 4, 0xFFFFFF, 0.9f);
            out.billboard(p, Sprite.GLINT, g[0], g[1], g[2], 5f, -s.time * 5, hue(s.t * 0.1f), 0.8f);
        }
        // explosion holographique
        float[] mw = pk.apply(0, TEAR_Y, 0);
        if (tt >= BURST) {
            float e = (float) Math.exp(-(tt - BURST) * 0.22f);
            out.billboard(p, Sprite.FLARE, mw[0], mw[1] + 1, mw[2], 4f + 18f * e, s.time, 0xFFFFFF, e);
            out.rays(p, mw[0], mw[1] + 1, mw[2], 16, 34f * (0.3f + 0.7f * e), 2.8f, s.time * 1.5f, hue(hb * 5), 0.6f * e);
            out.ground(p, Sprite.SHOCK, 8, POD_H + 0.1f, 8, 4f + 34f * (1 - e), 0, hue(hb + 0.2f), 0.9f * e);
            float m = 1 - Ease.smooth(Ease.range(tt, CAP_BACK0, CAP_BACK1));
            out.beam(p, mw[0], mw[1], mw[2], 2.4f, 26f, hue(hb * 2), (0.12f + 0.3f * e) * m);
        }
        // trainees des stickers en vol, chocs au sol
        float[] st = new float[8];
        for (int i = 0; i < NST; i++) {
            float age = tt - launchT(i);
            if (age > 0 && age < 16 && sticker(s, tt, i, st)) {
                float[][] pts = new float[6][];
                float[] tmp = new float[8];
                for (int k = 0; k < pts.length; k++) {
                    CrateState q = s.copy();
                    q.t = s.t - k * 0.7f;
                    if (!sticker(q, tt(q), i, tmp)) {
                        tmp[0] = MOUTH[0];
                        tmp[1] = MOUTH[1];
                        tmp[2] = MOUTH[2];
                    }
                    pts[k] = v(tmp[0], tmp[1], tmp[2]);
                }
                out.additive(p, Trail.ribbon(pts, 0.9f, 0.05f), HOLO[i % HOLO.length], 0.7f * (1 - age / 16));
            }
            float land = tt - (slapT(i) + 5);
            if (land > 0 && land < 10 && tt < backT(i)) {
                float[] fp = floorPos(s, i);
                float k = land / 10;
                out.ground(p, Sprite.SHOCK, fp[0], 0.1f, fp[2], 2f + 7f * k, 0, HOLO[i % HOLO.length], 0.8f * (1 - k));
            }
            if (land > 0 && tt < backT(i) + 4) {
                float[] fp = floorPos(s, i);
                out.ground(p, Sprite.HALO, fp[0], 0.08f, fp[2], 4f, 0, HOLO[i % HOLO.length], 0.25f + 0.1f * (float) Math.sin(s.time * 0.1f + i));
            }
        }
        // rescellement du bouchon
        if (tt > CAP_BACK1 - 1 && tt < CAP_BACK1 + 6) {
            float u = Ease.range(tt, CAP_BACK1 - 1, CAP_BACK1 + 6);
            float x = -W / 2 + W * u;
            float[] g = pk.apply(x, TEAR_Y, thick(x, TEAR_Y) / 2 + 0.2f);
            out.billboard(p, Sprite.GLINT, g[0], g[1], g[2], 3.5f, s.time * 3, 0xFFFFFF, Ease.bell(u, 0, 1));
        }
        CommonFx.cavity(s, p, out, this, mw[0], mw[1], mw[2], 4f, hue(hb));
    }

    @Override
    public float[] cavityPos() {
        return MOUTH.clone();
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.1f) {
            fx.particle(FxSink.SPARK, 8 + rnd(r, 5f), PACK_Y + 2 + r.nextFloat() * 14, 8 + rnd(r, 2f), 0, 0.04f, 0, HOLO[r.nextInt(HOLO.length)], 1.0f, 22);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == (int) LIFT0) {
            fx.sound(Snd.FOIL, 8, PACK_Y + 8, 8, 0.8f, 1.1f);
            fx.sound(Snd.WHOOSH, 8, PACK_Y + 8, 8, 0.5f, 0.8f);
        }
        if (k > LIFT0 && k < LIFT0 + 9) {
            for (int i = 0; i < 3; i++) {
                float[] q = Geo.polar(8, 8, r.nextFloat() * 360, 4 + r.nextFloat() * 5, PACK_Y + 6 + r.nextFloat() * 10);
                fx.particle(FxSink.SPARK, q[0], q[1], q[2], (8 - q[0]) * 0.08f, 0.05f, (8 - q[2]) * 0.08f, HOLO[r.nextInt(HOLO.length)], 1.2f, 10);
            }
        }
        if (k == (int) SHAKE0 + 1) fx.sound(Snd.FOIL, 8, MOUTH[1], 8, 1.0f, 0.9f);
        if (k == (int) TEAR0) fx.sound(Snd.RIP, 8, MOUTH[1], 8, 1.1f, 1.0f);
        if (k > TEAR0 && k <= TEAR1) {
            float u = Ease.range(k, TEAR0, TEAR1);
            float x = 8 + W / 2 - W * u;
            for (int i = 0; i < 4; i++) {
                fx.particle(FxSink.SPARK, x, MOUTH[1] + 0.6f, 8 + 1.2f, rnd(r, 0.3f), 0.3f + r.nextFloat() * 0.4f, 0.2f + r.nextFloat() * 0.3f, HOLO[r.nextInt(HOLO.length)], 1.2f, 14);
            }
        }
        if (k == (int) BURST) {
            fx.sound(Snd.HOLO, 8, MOUTH[1], 8, 1.2f, 1.0f);
            fx.sound(Snd.CAPSULE_POP, 8, MOUTH[1], 8, 0.8f, 0.7f);
            for (int i = 0; i < 36; i++) {
                double th = r.nextDouble() * Math.PI * 2;
                double sp = 0.4 + r.nextDouble() * 1.2;
                fx.particle(FxSink.CONFETTI, 8 + rnd(r, 1.5f), MOUTH[1] + 0.5f, 8 + rnd(r, 0.8f), (float) (Math.cos(th) * sp * 0.7), (float) (1.4 + r.nextDouble() * 1.8),
                        (float) (Math.sin(th) * sp * 0.7), HOLO[r.nextInt(HOLO.length)], 1.2f + r.nextFloat() * 0.8f, 40 + r.nextInt(30));
            }
            burst(fx, r, 8, MOUTH[1], 8, 0x9FF4FF, 0xFF6AD8, 40, 2.0f, 1.8f);
        }
        if (k == (int) BURST + 12) fx.sound(Snd.WHOOSH, 8, 24, 8, 0.6f, 1.3f);
        // decollage du bouchon : poussiere d'etoiles quand il disparait
        if (k == (int) TEAR1 + 20) {
            burst(fx, r, 8 - W / 2 - 7, MOUTH[1] + 20, 8 - 10, 0xFFFFFF, 0xFF6AD8, 16, 0.8f, 1.2f);
        }
        for (int i = 0; i < NST; i++) {
            if (k == (int) (slapT(i) + 5)) {
                float[] fp = floorPos(s, i);
                fx.sound(Snd.SLAP, fp[0], 0.5f, fp[2], 0.8f, 0.9f + 0.03f * (i % 5));
                for (int j = 0; j < 10; j++) {
                    double th = r.nextDouble() * Math.PI * 2;
                    fx.particle(FxSink.SPARK, fp[0], 0.4f, fp[2], (float) Math.cos(th) * 0.5f, 0.3f + r.nextFloat() * 0.4f, (float) Math.sin(th) * 0.5f,
                            HOLO[i % HOLO.length], 1.1f, 12);
                }
            }
            if (k == (int) backT(i) && i % 3 == 0) {
                float[] fp = floorPos(s, i);
                fx.sound(Snd.WHOOSH, fp[0], 2, fp[2], 0.4f, 1.6f);
            }
        }
        if (k == (int) CAP_BACK0) fx.sound(Snd.WHOOSH, 8, MOUTH[1] + 10, 8, 0.6f, 0.7f);
        if (k == (int) CAP_BACK1) {
            fx.sound(Snd.FOIL, 8, MOUTH[1], 8, 0.8f, 1.2f);
            fx.sound(Snd.GEM_CHIME, 8, MOUTH[1], 8, 0.6f, 1.3f);
        }
    }
}
