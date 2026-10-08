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
 * Caisse Pendentifs : la machine a capsules, facon machine a GobbleGum. Un grand distributeur retro : estrade ronde
 * cerclee d'ampoules, pied en vase de laque rouge bague d'or, tete de mecanisme avec l'enseigne lumineuse "CHARMS", la
 * fente a piece, la manivelle chromee et le cadenas qui la bloque, bien au centre ; au-dessus, un grand globe de verre
 * rempli de capsules de toutes les couleurs, coiffe d'or et d'une etoile. Un toboggan de verre descend en spirale
 * autour du pied jusqu'a la coupelle de sortie.
 * <p>A l'ouverture : une piece vole dans la fente, les ampoules s'emballent, la manivelle fait deux tours, les capsules
 * tourbillonnent dans le globe ; une capsule d'or tombe dans le toboggan, devale la spirale en l'illuminant, saute
 * dans la coupelle, s'eleve devant la machine en tournant et s'ouvre dans une gerbe de confettis.
 */
public final class Gachapon extends Design {
    private static final Mat LACQUER = Mat.MAIN;
    private static final Mat GOLD = Mat.TRIM;
    private static final Mat CHROME = Mat.ACCENT;
    private static final Mat PANEL = Mat.EMBLEM;
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat GLASS = Mat.DECAL_A;
    private static final Mat COIN = Mat.DECAL_B;
    private static final Mat PLASTIC = Mat.INTERIOR;
    private static final Mat BULB = Mat.GEM;
    private static final Mat BLACK = Mat.DARK;
    private static final Mat GOLD_F = Mat.SPECIAL;

    private static final int[] CAPS = {0xFF4F7A, 0x3FA9FF, 0xFFD23F, 0x5CFF7A, 0xB46CFF, 0xFF8A3A, 0x3FF0E0, 0xFF6AD5};

    /** Estrade ; pied ; tete (tambour + facade) ; globe ; toboggan ; coupelle. */
    private static final float PLINTH_R = 11.8f, PLINTH_H = 1.4f;
    private static final float HEAD_Y0 = 12.4f, HEAD_Y1 = 17.4f, HEAD_R = 6.0f;
    private static final float PANEL_W = 9.2f, PANEL_H = 4.5f, PANEL_Z = 8 + HEAD_R + 0.55f, PANEL_Y = (HEAD_Y0 + HEAD_Y1) / 2;
    private static final float GLOBE_R = 7.2f, GLOBE_Y = HEAD_Y1 + 6.2f;
    private static final float STAR_Y = GLOBE_Y + GLOBE_R + 3.4f;
    private static final float CRANK_X = 8 + 2.9f, CRANK_Y = PANEL_Y - 0.7f, CRANK_R = 1.6f;
    private static final float SLOT_X = 8 - 2.9f;
    private static final float CHUTE_R = 8.3f, CHUTE_Y0 = HEAD_Y0 - 0.4f, CHUTE_Y1 = PLINTH_H + 1.5f, CHUTE_TURNS = 1.5f, TUBE_R = 0.95f;
    private static final float[] TRAY = {8, PLINTH_H + 0.1f, 8 + 10.6f};
    private static final int NCAPS = 34;

    private Mesh machine = Mesh.EMPTY, chute = Mesh.EMPTY, chuteRail = Mesh.EMPTY, globe = Mesh.EMPTY, globeRim = Mesh.EMPTY, crank = Mesh.EMPTY;
    private Mesh coin = Mesh.EMPTY, capBottom = Mesh.EMPTY, capTop = Mesh.EMPTY, capGlow = Mesh.EMPTY, star = Mesh.EMPTY, starGlow = Mesh.EMPTY;
    private Mesh bulbs = Mesh.EMPTY;
    private final float[][] capPos = new float[NCAPS][];
    private final float[][] capRot = new float[NCAPS][];
    private final float[][] bulbAt = new float[40][];

    public Gachapon() {
        super("pendentif", 0xFFB43A, 0xFF4F7A);
        keyRgb = 0xFFD23F;
        cavityRgb = 0xFFD86A;
        cavityAccent = 0xFF4F7A;
        ringY = 46;
        ringR = 23;
        revealY = 51;
        riseFromY = 10;
        lockFloorY = PLINTH_H;
    }

    // =================================================================== construction
    /** Point du toboggan (u = 0 en haut, derriere la tete ; 1 en bas, devant). */
    private static float[] chuteAt(float u) {
        float ang = 270 - 360 * CHUTE_TURNS * u;
        float y = Ease.lerp(CHUTE_Y0, CHUTE_Y1, u);
        return Geo.polar(8, 8, ang, CHUTE_R, y);
    }

    private static void bar(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 6, m, true, true, 0, false);
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // estrade a deux marches, liseres d'or
        b.push().translate(8, 0, 8);
        b.cylinder(PLINTH_R, 0, 0.75f, seg(44), BLACK, null, LACQUER);
        b.cylinder(PLINTH_R - 2.0f, 0.75f, PLINTH_H, seg(40), BLACK, null, LACQUER);
        b.push().translate(0, 0.75f, 0);
        b.torus(PLINTH_R, 0.28f, seg(44), 5, 0, 360, GOLD);
        b.pop();
        b.push().translate(0, PLINTH_H, 0);
        b.torus(PLINTH_R - 2.0f, 0.24f, seg(40), 5, 0, 360, GOLD);
        b.pop();
        // pied en vase de laque, bagues d'or
        b.lathe(new float[][]{{6.4f, PLINTH_H}, {6.2f, PLINTH_H + 0.8f}, {4.9f, PLINTH_H + 1.7f}, {3.8f, 4.4f}, {3.3f, 6.4f}, {3.4f, 8.4f},
                {4.1f, 9.9f}, {5.3f, 11.2f}, {HEAD_R, HEAD_Y0 - 0.3f}, {HEAD_R, HEAD_Y0}}, seg(28), 0, 360, LACQUER, 1, 1, 0, 0, 0, false);
        for (float[] ring : new float[][]{{6.25f, PLINTH_H + 0.8f, 0.3f}, {3.32f, 6.4f, 0.25f}, {HEAD_R + 0.05f, HEAD_Y0 - 0.15f, 0.3f}}) {
            b.push().translate(0, ring[1], 0);
            b.torus(ring[0], ring[2], seg(28), 5, 0, 360, GOLD);
            b.pop();
        }
        // tete : tambour laque, couronnes d'or, plateau du globe
        b.cylinder(HEAD_R, HEAD_Y0, HEAD_Y1, seg(28), LACQUER, null, GOLD);
        for (float y : new float[]{HEAD_Y0 + 0.2f, HEAD_Y1 - 0.2f}) {
            b.push().translate(0, y, 0);
            b.torus(HEAD_R + 0.05f, 0.3f, seg(28), 5, 0, 360, GOLD_F);
            b.pop();
        }
        b.cylinder(4.3f, HEAD_Y1, HEAD_Y1 + 0.7f, seg(24), GOLD, null, GOLD);
        b.pop();
        // facade : enseigne, cadre d'or
        b.push().translate(8, PANEL_Y, PANEL_Z - 0.4f);
        b.roundedSlab(PANEL_W, PANEL_H, 0.8f, 0.9f, 0.25f, seg(4), PANEL, GOLD);
        b.pop();
        b.box(8 - PANEL_W / 2 + 0.3f, HEAD_Y0 + 0.3f, 8 + 1, 8 + PANEL_W / 2 - 0.3f, HEAD_Y1 - 0.3f, PANEL_Z - 0.8f, LACQUER);
        // fente a piece (plaque chromee) et moraillon du cadenas
        b.box(SLOT_X - 1.0f, CRANK_Y - 1.1f, PANEL_Z, SLOT_X + 1.0f, CRANK_Y + 1.1f, PANEL_Z + 0.3f, CHROME);
        b.box(SLOT_X - 0.15f, CRANK_Y - 0.7f, PANEL_Z + 0.3f, SLOT_X + 0.15f, CRANK_Y + 0.7f, PANEL_Z + 0.33f, BLACK);
        b.box(8 - 0.9f, CRANK_Y - 0.9f, PANEL_Z, 8 + 0.9f, CRANK_Y + 0.2f, PANEL_Z + 0.35f, CHROME);
        b.push().translate(8, CRANK_Y - 0.55f, PANEL_Z + 0.4f).rotateX(90);
        b.torus(0.35f, 0.1f, 10, 4, 0, 360, CHROME);
        b.pop();
        // axe de la manivelle
        b.push().translate(CRANK_X, CRANK_Y, PANEL_Z).rotateX(90);
        b.cylinder(0.5f, 0, 0.5f, 10, CHROME, null, CHROME);
        b.pop();
        // coupelle de sortie
        b.push().translate(TRAY[0], TRAY[1], TRAY[2]);
        b.lathe(new float[][]{{0, 0.5f}, {2.0f, 0.5f}, {2.4f, 1.0f}, {2.6f, 1.05f}, {2.4f, 0.1f}, {1.0f, 0}}, seg(20), 0, 360, GOLD, 1, 1, 0, 0, 0, true);
        b.pop();
        // supports du toboggan (bras d'or depuis le pied)
        for (int i = 0; i < 5; i++) {
            float u = 0.08f + i * 0.2f;
            float[] q = chuteAt(u);
            float ang = 270 - 360 * CHUTE_TURNS * u;
            float[] root = Geo.polar(8, 8, ang, i < 2 ? 5.0f : 3.8f, q[1]);
            bar(b, root, v(q[0], q[1] - TUBE_R * 0.8f, q[2]), 0.14f, GOLD);
        }
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        machine = b.build();

        // toboggan : tube de verre en spirale, rail d'or dessous
        int n = seg(64);
        float[][] path = new float[n + 3][];
        float[] radii = new float[n + 3];
        float[][] rail = new float[n + 3][];
        float[] rr = new float[n + 3];
        path[0] = Geo.polar(8, 8, 270, 4.8f, CHUTE_Y0 + 0.8f);
        for (int i = 0; i <= n; i++) path[i + 1] = chuteAt(i / (float) n);
        path[n + 2] = v(TRAY[0], TRAY[1] + 1.5f, TRAY[2] - 1.4f);
        for (int i = 0; i < path.length; i++) {
            radii[i] = TUBE_R;
            rail[i] = v(path[i][0], path[i][1] - TUBE_R - 0.12f, path[i][2]);
            rr[i] = 0.16f;
        }
        MeshBuilder ch = new MeshBuilder();
        ch.tube(path, radii, 10, GLASS.asFit(), true, true, 0, false);
        chute = ch.build();
        MeshBuilder rl = new MeshBuilder();
        rl.tube(rail, rr, 5, GOLD, true, true, 0, false);
        chuteRail = rl.build();

        // globe de verre et coiffe
        MeshBuilder d = new MeshBuilder();
        d.sphereSection(GLOBE_R, seg(32), seg(16), 0, 152, GLASS.asFit());
        globe = d.build();
        MeshBuilder dr = new MeshBuilder();
        float cut = (float) Math.sin(Math.toRadians(152)) * GLOBE_R;
        dr.push().translate(0, (float) Math.cos(Math.toRadians(152)) * GLOBE_R, 0);
        dr.torus(cut + 0.1f, 0.4f, seg(24), 5, 0, 360, GOLD);
        dr.pop();
        dr.push().translate(0, GLOBE_R - 0.5f, 0);
        dr.sphereSection(2.2f, seg(16), 6, 0, 90, GOLD_F);
        dr.torus(2.2f, 0.22f, seg(16), 4, 0, 360, GOLD);
        dr.cylinder(0.35f, 1.8f, 2.8f, 8, GOLD, null, GOLD);
        dr.pop();
        globeRim = dr.build();

        // manivelle : roue chromee a trois rayons, poignee rouge (tourne autour de Z)
        MeshBuilder c = new MeshBuilder();
        c.push().translate(0, 0, 0.9f);
        c.torus(CRANK_R, 0.2f, 20, 6, 0, 360, CHROME);
        c.pop();
        c.push().rotateX(90);
        c.cylinder(0.55f, 0.5f, 1.1f, 12, CHROME, null, GOLD);
        c.pop();
        for (int i = 0; i < 3; i++) {
            c.push().translate(0, 0, 0.9f).rotateZ(i * 120);
            bar(c, v(0, 0.4f, 0), v(0, CRANK_R, 0), 0.14f, CHROME);
            c.pop();
        }
        c.push().translate(CRANK_R, 0, 0.9f).rotateX(90);
        c.cylinder(0.24f, 0, 1.3f, 8, CHROME, null, CHROME);
        c.pop();
        c.push().translate(CRANK_R, 0, 2.5f);
        c.sphere(0.6f, 12, 8, LACQUER);
        c.pop();
        Occlusion.bake(c, 0.25f, 2f, 0.3f, Float.NaN);
        crank = c.build();

        // piece
        MeshBuilder cn = new MeshBuilder();
        cn.push().rotateX(90);
        cn.cylinder(1.0f, -0.12f, 0.12f, 18, GOLD, COIN.asFit(), COIN.asFit());
        cn.pop();
        coin = cn.build();

        // capsule : moitie basse coloree (a teinter), moitie haute transparente, lueur
        MeshBuilder cb = new MeshBuilder();
        cb.sphereSection(1.3f, 14, 6, 90, 180, PLASTIC);
        cb.push().translate(0, -0.04f, 0);
        cb.torus(1.3f, 0.09f, 14, 4, 0, 360, PLASTIC);
        cb.pop();
        capBottom = cb.build();
        MeshBuilder ct = new MeshBuilder();
        ct.sphereSection(1.32f, 14, 6, 0, 90, GLASS.asFit());
        capTop = ct.build();
        MeshBuilder cg = new MeshBuilder();
        cg.scale(1.18f);
        cg.sphere(1.3f, 12, 8, Mat.WHITE);
        capGlow = cg.build();
        Random rng = new Random(77);
        int placed = 0;
        float inner = GLOBE_R - 1.6f;
        for (int tries = 0; placed < NCAPS && tries < 8000; tries++) {
            float x = (rng.nextFloat() - 0.5f) * 2 * inner, z = (rng.nextFloat() - 0.5f) * 2 * inner;
            float y = -GLOBE_R * 0.62f + rng.nextFloat() * GLOBE_R * 1.0f;
            if (x * x + y * y + z * z > inner * inner) continue;
            boolean ok = true;
            for (int i = 0; i < placed; i++) {
                float dx = capPos[i][0] - x, dy = capPos[i][1] - y, dz = capPos[i][2] - z;
                if (dx * dx + dy * dy + dz * dz < 2.45f * 2.45f) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            capPos[placed] = v(x, y, z);
            capRot[placed] = v(rng.nextFloat() * 360, rng.nextFloat() * 360, rng.nextFloat() * 360);
            placed++;
        }
        for (int i = placed; i < NCAPS; i++) {
            capPos[i] = v(0, -GLOBE_R * 0.5f, 0);
            capRot[i] = v(0, 0, 0);
        }

        // etoile au sommet
        MeshBuilder st = new MeshBuilder();
        st.push().translate(0, 0, -0.4f);
        st.prism(starPts(2.3f, 1.0f), 0.8f, GOLD_F, GOLD);
        st.pop();
        star = st.build();
        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(0, 0, -0.5f);
        sg.prism(starPts(2.7f, 1.2f), 1.0f, Mat.WHITE, Mat.WHITE);
        sg.pop();
        starGlow = sg.build();

        // ampoules : pourtour de l'estrade (20), couronne de la tete (12), coiffe du globe (8)
        MeshBuilder bl = new MeshBuilder();
        int k = 0;
        for (int i = 0; i < 20; i++) bulbAt[k++] = Geo.polar(8, 8, i * 18, PLINTH_R - 0.7f, 0.85f);
        for (int i = 0; i < 12; i++) bulbAt[k++] = Geo.polar(8, 8, i * 30 + 15, HEAD_R + 0.35f, HEAD_Y1 - 0.2f);
        for (int i = 0; i < 8; i++) bulbAt[k++] = Geo.polar(8, 8, i * 45, 2.4f, GLOBE_Y + GLOBE_R - 0.4f);
        for (float[] q : bulbAt) {
            bl.push().translate(q[0], q[1] + 0.1f, q[2]);
            bl.sphere(0.3f, 8, 5, BULB);
            bl.pop();
        }
        bulbs = bl.build();

        // cadenas : laque rouge, face a la capsule d'or, anse doree (il bloque la manivelle, au centre de la facade)
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.DISC;
        ps.w = 3.6f;
        ps.h = 3.6f;
        ps.d = 1.5f;
        ps.height = 1.5f;
        ps.halfSpan = 0.95f;
        ps.wire = 0.36f;
        ps.body = LACQUER;
        ps.face = LOCKFACE;
        ps.shackle = GOLD;
        ps.trim = GOLD;
        ps.hole = BLACK;
        lock = new Padlock(ps);
        lock.build(detail);

        // cle : capsule miniature (rouge et verre) dans un anneau d'or
        Mesh[] kk = KeySmith.build(GOLD, GOLD_F, 0.5f, (kb, kg) -> {
            kb.push().translate(0, 2.9f, 0);
            kb.colorRgb(0xFF4F7A);
            kb.sphereSection(2.0f, 16, 6, 90, 180, PLASTIC);
            kb.color(1f);
            kb.sphereSection(2.02f, 16, 6, 0, 90, GLASS.asFit());
            kb.torus(2.02f, 0.18f, 16, 4, 0, 360, GOLD);
            kb.pop();
            kg.push().translate(0, 2.9f, 0);
            kg.torus(2.05f, 0.3f, 16, 4, 0, 360, Mat.WHITE);
            kg.pop();
        });
        key = kk[0];
        keyGlow = kk[1];
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
    private static final float COIN0 = CrateAnim.UNLOCK + 2, COIN1 = COIN0 + 9;
    private static final float CRANK0 = COIN1 + 1, CRANK1 = CrateAnim.OPEN + 12;
    private static final float ROLL0 = CRANK1 + 1, ROLL1 = ROLL0 + 20, OUT1 = ROLL1 + 4;
    private static final float LEV0 = OUT1 + 3, LEV1 = LEV0 + 10, POP = LEV1 + 2;
    private static final float[] HOVER = {8, 10.5f, 8 + 12.2f};

    /** Angle de la manivelle : deux tours par a-coups (crans du cliquet). */
    private static float crankAngle(CrateState s) {
        if (!s.active) return 0;
        float u = Ease.range(s.t, CRANK0, CRANK1);
        float steps = 8;
        float k = u * steps;
        float fi = (float) Math.floor(k), fr = k - fi;
        float a = (fi + Ease.inOutCubic(Math.min(1, fr * 1.6f))) / steps;
        return -360f * 2 * Math.min(1, a);
    }

    /** Brassage des capsules (0..1). */
    private static float churn(CrateState s) {
        if (!s.active) return 0;
        return Ease.smooth(Ease.range(s.t, CRANK0, CRANK0 + 5)) * (1 - Ease.smooth(Ease.range(s.t, CRANK1 + 2, CRANK1 + 18)));
    }

    /** Ampoules : vitesse de la chenille (0 repos .. 1 ouverture). */
    private static float frenzy(CrateState s) {
        if (!s.active) return 0;
        return Ease.smooth(Ease.range(s.t, COIN1, COIN1 + 4)) * (1 - Ease.smooth(Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)));
    }

    /** Avancement de la capsule dans le toboggan (acceleration, puis vitesse constante). */
    private static float roll(CrateState s) {
        float u = Ease.range(s.t, ROLL0, ROLL1);
        return 0.35f * u * u + 0.65f * u;
    }

    private static float capOpen(CrateState s) {
        if (!s.active) return 0;
        return Ease.outBack(Ease.range(s.t, POP, POP + 6), 1.8f) * (1 - Ease.smooth(Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.CLOSE + 8)));
    }

    /** Pose de la capsule d'or (renvoie son echelle ; 0 = cachee). */
    private static float goldPose(CrateState s, Pose p) {
        if (!s.active || s.t < ROLL0) return 0;
        float t = s.t;
        float fade = 1 - Ease.smooth(Ease.range(t, CrateAnim.CLOSE + 6, CrateAnim.SLAM));
        if (t < ROLL1) {
            float u = roll(s);
            float[] q = chuteAt(u);
            p.translate(q[0], q[1], q[2]).rotY(-540f * CHUTE_TURNS * u).rotX(-1400f * u);
            return 1.1f;
        }
        if (t < OUT1) {
            float u = Ease.range(t, ROLL1, OUT1);
            float[] a = chuteAt(1), c = v(TRAY[0], TRAY[1] + 1.8f, TRAY[2]);
            float[] q = Geo.lerp(a, c, Ease.outCubic(u));
            q[1] += 1.2f * (float) Math.sin(u * Math.PI);
            p.translate(q[0], q[1], q[2]).rotX(-1400f - 360f * u);
            return 1.1f;
        }
        float lev = Ease.inOutCubic(Ease.range(t, LEV0, LEV1));
        float[] rest = v(TRAY[0], TRAY[1] + 1.8f, TRAY[2]);
        float wob = t < LEV0 ? 14f * (float) Math.sin((t - OUT1) * 1.4f) * (float) Math.exp(-(t - OUT1) * 0.3f) : 0;
        float[] q = Geo.lerp(rest, HOVER, lev);
        q[1] += lev * 0.35f * (float) Math.sin(t * 0.12f);
        float sc = 1.1f + 0.7f * lev;
        p.translate(q[0], q[1], q[2]).rotY(lev * (t - LEV0) * 9f).rotZ(wob).rotX(-20f * lev);
        return sc * fade;
    }

    /** Position d'une capsule du globe (repere du globe), brassee par la manivelle. */
    private float[] capAt(CrateState s, int i) {
        float c = churn(s);
        float[] q = capPos[i];
        if (c <= 0.001f) {
            float j = 0.06f * (float) Math.sin(s.time * 0.2f + i * 1.7f);
            return v(q[0], q[1] + j, q[2]);
        }
        float ang = (float) Math.toRadians((s.t - CRANK0) * (10f + (i % 5)) * c);
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        float x = q[0] * cs - q[2] * sn, z = q[0] * sn + q[2] * cs;
        float bounce = c * 2.2f * (float) Math.abs(Math.sin(s.t * 0.5f + i * 1.3f));
        return v(x, Math.min(q[1] + bounce, GLOBE_R - 1.8f), z);
    }

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float crank = 0.1f * churn(s);
        float pop = s.t >= POP ? 0.45f * (float) Math.exp(-(s.t - POP) * 0.3f) : 0;
        float land = s.t >= ROLL1 ? 0.15f * (float) Math.exp(-(s.t - ROLL1) * 0.5f) : 0;
        return Math.max(Math.max(crank, Math.max(pop, land)), 0.3f * Seq.stopFlash(s));
    }

    private static int bulbColor(int i) {
        return i % 3 == 0 ? 0xFFD86A : (i % 3 == 1 ? 0xFF6A9A : 0xFFF4E0);
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        float fz = frenzy(s);
        out.solid(p, machine, gl);
        out.solid(p, chuteRail, gl);
        // ampoules : chenille (lente au repos, folle a l'ouverture)
        out.solid(p, bulbs, 1.2f);
        for (int i = 0; i < bulbAt.length; i++) {
            float speed = 0.12f + 0.9f * fz;
            float ph = (s.time * speed - i * 0.55f) % 3f;
            if (ph < 0) ph += 3;
            float on = ph < 1.2f ? 1f : 0.2f;
            float[] q = bulbAt[i];
            out.billboard(p, Sprite.HALO, q[0], q[1] + 0.15f, q[2], 0.95f, 0, bulbColor(i), 0.55f * on);
        }
        // manivelle
        p.push().translate(CRANK_X, CRANK_Y, PANEL_Z).rotZ(crankAngle(s));
        out.solid(p, crank, gl);
        p.pop();
        // piece qui vole vers la fente
        if (s.active && s.t >= COIN0 && s.t < COIN1 + 1) {
            float u = Ease.range(s.t, COIN0, COIN1);
            float[] a = v(8 - 9f, 18f, PANEL_Z + 10f), b = v(SLOT_X, CRANK_Y, PANEL_Z + 0.4f);
            float[] c = Geo.lerp(a, b, Ease.inOutSine(u));
            c[1] += (float) Math.sin(u * Math.PI) * 4f;
            p.push().translate(c[0], c[1], c[2]).rotY(u * 900).scale(u > 0.9f ? (1 - u) * 10 : 1);
            out.solid(p, coin, 1.2f);
            p.pop();
        }
        // capsules du globe (moities basses opaques ici, le verre plus bas)
        p.push().translate(8, GLOBE_Y, 8);
        float c = churn(s);
        for (int i = 0; i < NCAPS; i++) {
            float[] q = capAt(s, i);
            float[] r = capRot[i];
            int col = CAPS[i % CAPS.length];
            p.push().translate(q[0], q[1], q[2]).rotY(r[0] + c * s.t * 7 * (i % 2 == 0 ? 1 : -1)).rotX(r[1] + c * s.t * 5).rotZ(r[2]);
            out.solidTinted(p, capBottom, gl, ((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f);
            p.pop();
        }
        p.pop();
        // capsule d'or : toboggan, coupelle, levitation, ouverture
        p.push();
        float gs = goldPose(s, p);
        if (gs > 0.01f) {
            float o = capOpen(s);
            p.scale(gs);
            out.solidTinted(p, capBottom, 1.2f, 1.0f, 0.82f, 0.3f);
            p.push().translate(0, 0, -1.3f).rotX(-115f * o).translate(0, 0, 1.3f);
            out.translucent(p, capTop, 0xFFE9A0, 0.9f);
            p.pop();
            out.additive(p, capGlow, 0xFFD23F, 0.3f + 0.6f * o);
        }
        p.pop();
        // verre : toboggan, capsules (hautes), globe
        out.translucent(p, chute, 0xFFF4E4, 1f);
        out.additive(p, chute, 0xFFE8C0, 0.35f + 0.3f * fz);
        p.push().translate(8, GLOBE_Y, 8);
        for (int i = 0; i < NCAPS; i++) {
            float[] q = capAt(s, i);
            float[] r = capRot[i];
            p.push().translate(q[0], q[1], q[2]).rotY(r[0] + c * s.t * 7 * (i % 2 == 0 ? 1 : -1)).rotX(r[1] + c * s.t * 5).rotZ(r[2]);
            out.translucent(p, capTop, Ease.mix(CAPS[i % CAPS.length], 0xFFFFFF, 0.55f), 0.9f);
            p.pop();
        }
        out.solid(p, globeRim, gl);
        out.translucent(p, globe, 0xFFFFFF, 0.95f);
        p.pop();
        // etoile au sommet
        p.push().translate(8, STAR_Y + 0.3f * (float) Math.sin(s.time * 0.07f) + 1.5f * aw, 8).rotY(s.time * 1.5f + 720f * aw);
        out.solid(p, star, 1.1f);
        out.additive(p, starGlow, 0xFFD23F, 0.3f + 0.2f * (float) Math.sin(s.time * 0.1f) + 0.5f * aw);
        p.pop();
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        // a l'anneau du moraillon, au centre de la facade, sous l'enseigne
        p.translate(8, CRANK_Y - 0.55f, PANEL_Z + 0.45f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float aw = Seq.awaken(s);
        float fz = frenzy(s);
        out.ground(p, Sprite.HALO, 8, 0.06f, 8, 19f, 0, glow, 0.12f + 0.2f * aw);
        out.billboard(p, Sprite.HALO, 8, STAR_Y + 1.5f * aw, 8, 5f + 3f * aw, 0, 0xFFD23F, 0.3f + 0.3f * aw);
        out.billboard(p, Sprite.GLINT, 8, STAR_Y + 1.5f * aw, 8.8f, 3f, s.time * 2, 0xFFFFFF, 0.5f + 0.3f * (float) Math.sin(s.time * 0.13f));
        // reflets du globe et lueur interieure quand il s'emballe
        for (int i = 0; i < 3; i++) {
            float ph = (s.time * 0.03f + i * 0.33f) % 1f;
            float tw = (float) Math.pow(Math.sin(Math.PI * ph), 6);
            float[] q = Geo.polar(8, 8, 70 + i * 25, GLOBE_R * 0.82f, GLOBE_Y + 2 + i * 1.5f);
            out.billboard(p, Sprite.GLINT, q[0], q[1], q[2] + 0.5f, 2.0f, i * 40, 0xFFFFFF, 0.8f * tw);
        }
        out.billboard(p, Sprite.HALO, 8, GLOBE_Y, 8, 11f, 0, 0xFFF0D0, 0.08f + 0.3f * churn(s) + 0.1f * fz);
        // enseigne : halo qui pulse
        out.billboard(p, Sprite.HALO, 8, PANEL_Y + 1.2f, PANEL_Z + 0.8f, 6f, 0, 0xFFB43A, 0.15f + 0.25f * fz * (0.6f + 0.4f * (float) Math.sin(s.time * 0.8f)));
        if (!s.active) return;
        float c = churn(s);
        if (c > 0.01f) out.ground(p, Sprite.SWIRL, 8, GLOBE_Y - 3.5f, 8, GLOBE_R - 0.8f, -s.t * 12f, 0xFFD86A, 0.45f * c);
        // capsule d'or dans le toboggan : le verre s'illumine, trainee
        if (s.t > ROLL0 && s.t < OUT1 + 2) {
            float[][] pts = new float[8][];
            for (int i = 0; i < pts.length; i++) {
                CrateState q = s.copy();
                q.t = Math.max(ROLL0, s.t - i * 0.5f);
                Pose pp = new Pose();
                goldPose(q, pp);
                pts[i] = pp.apply(0, 0, 0);
            }
            out.additive(p, Trail.ribbon(pts, 1.0f, 0.1f), 0xFFD23F, 0.8f);
            out.billboard(p, Sprite.HALO, pts[0][0], pts[0][1], pts[0][2], 3.2f, 0, 0xFFD86A, 0.8f);
        }
        Pose q = new Pose();
        float gs = goldPose(s, q);
        if (gs > 0.01f && s.t >= LEV0) {
            float[] at = q.apply(0, 0, 0);
            float lev = Ease.range(s.t, LEV0, LEV1);
            out.beam(p, at[0], TRAY[1], at[2], 1.8f, at[1] - TRAY[1] + 2, 0xFFE9A0, 0.35f * lev * (1 - capOpen(s) * 0.5f));
            out.ground(p, Sprite.RUNE, at[0], TRAY[1] + 0.15f, at[2], 3.5f, s.time * 2, 0xFFD23F, 0.5f * lev);
        }
        float o = capOpen(s);
        if (o > 0.01f) {
            float[] at = q.apply(0, 0.3f, 0);
            float flash = s.t >= POP ? (float) Math.exp(-(s.t - POP) * 0.25f) : 0;
            out.billboard(p, Sprite.FLARE, at[0], at[1], at[2], 4f + 18f * flash, s.time * 2, 0xFFFFFF, flash);
            out.rays(p, at[0], at[1], at[2], 12, 26f * (0.4f + 0.6f * o), 2.6f, s.time * 1.5f, 0xFFD23F, 0.5f * o);
            out.ground(p, Sprite.SHOCK, at[0], PLINTH_H + 0.1f, at[2], 3f + 30f * (1 - flash), 0, 0xFFD23F, 0.8f * flash);
        }
        CommonFx.cavity(s, p, out, this, 8, HEAD_Y1 + 0.8f, 8, 5f);
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, HEAD_Y1 + 1, 8};
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.12f) {
            fx.particle(FxSink.SPARK, 8 + rnd(r, 2f), STAR_Y + rnd(r, 1.5f), 8 + rnd(r, 2f), 0, 0.05f, 0, 0xFFD23F, 1.1f, 24);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == (int) COIN0 + 2) fx.sound(Snd.WHOOSH, SLOT_X - 5, CRANK_Y + 3, PANEL_Z + 6, 0.5f, 1.8f);
        if (k == (int) COIN1) {
            fx.sound(Snd.COIN, SLOT_X, CRANK_Y, PANEL_Z, 1.0f, 1.0f);
            burst(fx, r, SLOT_X, CRANK_Y, PANEL_Z + 0.5f, 0xFFD23F, 0xFFFFFF, 12, 0.8f, 1.1f);
        }
        if (k >= CRANK0 && k <= CRANK1) {
            float u0 = Ease.range(k - 1, CRANK0, CRANK1) * 8, u1 = Ease.range(k, CRANK0, CRANK1) * 8;
            if ((int) u1 != (int) u0) fx.sound(Snd.RATCHET, CRANK_X, CRANK_Y, PANEL_Z, 0.9f, 0.9f + 0.05f * ((int) u1 % 3));
        }
        if (k == (int) CRANK0 + 2) fx.sound(Snd.CAPSULES, 8, GLOBE_Y, 8, 1.0f, 1.0f);
        if (k > CRANK0 && k < CRANK1 && (k & 1) == 0) {
            float[] q = Geo.polar(8, 8, r.nextFloat() * 360, GLOBE_R * 0.7f, GLOBE_Y - 2 + r.nextFloat() * 4);
            fx.particle(FxSink.SPARK, q[0], q[1], q[2], 0, 0.1f, 0, CAPS[r.nextInt(CAPS.length)], 1.1f, 16);
        }
        if (k == (int) ROLL0) fx.sound(Snd.CAPSULE_ROLL, 8, CHUTE_Y0, 8 - CHUTE_R, 1.0f, 0.9f);
        if (k > ROLL0 && k < ROLL1 && k % 6 == 0) {
            CrateState q = s.copy();
            q.t = k;
            float[] at = chuteAt(roll(q));
            fx.sound(Snd.CAPSULE_ROLL, at[0], at[1], at[2], 0.6f, 1.0f + 0.1f * ((k / 6) % 3));
            fx.particle(FxSink.SPARK, at[0], at[1] + 1, at[2], 0, 0.2f, 0, 0xFFD23F, 1.2f, 12);
        }
        if (k == (int) OUT1) fx.sound(Snd.CAPSULE_ROLL, TRAY[0], TRAY[1] + 1, TRAY[2], 0.8f, 1.3f);
        if (k == (int) LEV0) fx.sound(Snd.HOLO, HOVER[0], HOVER[1], HOVER[2], 0.6f, 1.4f);
        if (k == (int) POP) {
            fx.sound(Snd.CAPSULE_POP, HOVER[0], HOVER[1], HOVER[2], 1.2f, 1.0f);
            fx.sound(Snd.GEM_CHIME, HOVER[0], HOVER[1], HOVER[2], 0.8f, 1.2f);
            for (int i = 0; i < 70; i++) {
                double th = r.nextDouble() * Math.PI * 2;
                double sp = 0.6 + r.nextDouble() * 1.4;
                fx.particle(FxSink.CONFETTI, HOVER[0] + rnd(r, 0.8f), HOVER[1] + 1f, HOVER[2] + rnd(r, 0.8f), (float) (Math.cos(th) * sp * 0.6),
                        (float) (1.0 + r.nextDouble() * 1.6), (float) (Math.sin(th) * sp * 0.6), CAPS[r.nextInt(CAPS.length)], 1.3f + r.nextFloat() * 0.8f, 40 + r.nextInt(30));
            }
            burst(fx, r, HOVER[0], HOVER[1], HOVER[2], 0xFFD23F, 0xFFFFFF, 30, 1.6f, 1.5f);
        }
        if (k == CrateAnim.CLOSE + 6) fx.sound(Snd.WHOOSH, HOVER[0], HOVER[1], HOVER[2], 0.5f, 1.5f);
        if (k == CrateAnim.SLAM) fx.sound(Snd.RATCHET, CRANK_X, CRANK_Y, PANEL_Z, 0.8f, 0.8f);
    }
}
