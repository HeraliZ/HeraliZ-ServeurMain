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
 * Caisse Medicale : Trauma. Un chariot d'urgence rouge sur un tapis marque d'une croix, une mallette de trauma blanche
 * posee dessus (le cadenas ferme son loquet), un moniteur cardiaque sur pied dont le trace ECG bat en direct, un
 * defibrillateur jaune et ses deux palettes sur une tablette, une potence et sa poche de sang.
 * <p>A l'ouverture : le moniteur passe en asystolie (bip continu), le defibrillateur se charge, les palettes viennent
 * encadrer la mallette. "CLEAR !" : premier choc (arcs electriques, la mallette sursaute), rien. Deuxieme charge,
 * deuxieme choc plus violent : le coeur repart, le couvercle saute dans une gerbe de lumiere verte et une croix
 * holographique s'eleve. Pendant la roue, le rythme cardiaque suit sa vitesse ; les palettes retournent a leur support.
 */
public final class Trauma extends Design {
    private static final Mat ENAMEL = Mat.MAIN;
    private static final Mat FLOOR = Mat.MAIN2;
    private static final Mat STEEL = Mat.TRIM;
    private static final Mat PLASTIC = Mat.ACCENT;
    private static final Mat LID = Mat.EMBLEM;
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat SCREEN = Mat.DECAL_A;
    private static final Mat MSG = Mat.DECAL_B;
    private static final Mat FOAM = Mat.INTERIOR;
    private static final Mat BLOOD = Mat.GEM;
    private static final Mat RUBBER = Mat.DARK;
    private static final Mat YELLOW = Mat.SPECIAL;

    private static final int GREEN = 0x3CFF8A, RED = 0xFF2A3A, ARC = 0x5AB8FF;

    /** Chariot ; mallette ; moniteur ; defibrillateur. */
    private static final float CART_X0 = 2.5f, CART_X1 = 12.5f, CART_Z0 = 4.5f, CART_Z1 = 11.5f, CART_TOP = 9.6f;
    private static final float CASE_X0 = 3.6f, CASE_X1 = 11.4f, CASE_Z0 = 6.1f, CASE_Z1 = 11.75f, CASE_MID = CART_TOP + 2.2f, LID_H = 1.1f;
    private static final float CASE_CX = (CASE_X0 + CASE_X1) / 2, CASE_CZ = (CASE_Z0 + CASE_Z1) / 2;
    private static final float MON_X = -1.4f, MON_Z = 7.8f, MON_Y = 18.2f, MON_YAW = 32f, SCR_W = 6f, SCR_H = 4.6f;
    private static final float[] DOCK_L = {13.9f, 11.05f, 8.0f}, DOCK_R = {15.5f, 11.05f, 8.0f};
    private static final float[] SHOCK_L = {CASE_X0 - 0.02f, CART_TOP + 1.5f, CASE_CZ}, SHOCK_R = {CASE_X1 + 0.02f, CART_TOP + 1.5f, CASE_CZ};

    private Mesh site = Mesh.EMPTY, caseBase = Mesh.EMPTY, lid = Mesh.EMPTY, monitor = Mesh.EMPTY, paddle = Mesh.EMPTY, defibLed = Mesh.EMPTY;
    private Mesh crossHolo = Mesh.EMPTY;
    private final Mesh[] msg = new Mesh[4];

    public Trauma() {
        super("trauma", 0x3CFF8A, 0xFF2A3A);
        keyRgb = 0xFF4050;
        cavityRgb = 0x7AFFB0;
        cavityAccent = 0xFFFFFF;
        size = 1.5f;
        ringY = 42;
        ringR = 24;
        revealY = 48;
        riseFromY = CASE_MID;
        lockFloorY = 0.8f;
    }

    // =================================================================== construction
    private static void bar(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 6, m, true, true, 0, false);
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // tapis
        b.bevelBox(-4.5f, 0, -2.5f, 20.5f, 0.8f, 18.5f, 0.25f, FLOOR);
        // chariot : caisson, tiroirs, plateau inox, roulettes, poignee
        b.bevelBox(CART_X0, 1.9f, CART_Z0, CART_X1, 9.2f, CART_Z1, 0.2f, ENAMEL);
        float[] dy = {2.2f, 4.0f, 5.6f, 7.1f, 8.6f};
        for (int i = 0; i < dy.length - 1; i++) {
            float ya = dy[i] + 0.1f, yb = dy[i + 1] - 0.1f;
            b.box(CART_X0 + 0.35f, yb, CART_Z1, CART_X1 - 0.35f, yb + 0.12f, CART_Z1 + 0.05f, RUBBER);
            bar(b, v(CART_X0 + 3.6f, (ya + yb) / 2 + 0.2f, CART_Z1 + 0.35f), v(CART_X1 - 3.6f, (ya + yb) / 2 + 0.2f, CART_Z1 + 0.35f), 0.12f, STEEL);
            b.box(CART_X0 + 3.5f, (ya + yb) / 2 + 0.05f, CART_Z1, CART_X0 + 3.7f, (ya + yb) / 2 + 0.35f, CART_Z1 + 0.35f, STEEL);
            b.box(CART_X1 - 3.7f, (ya + yb) / 2 + 0.05f, CART_Z1, CART_X1 - 3.5f, (ya + yb) / 2 + 0.35f, CART_Z1 + 0.35f, STEEL);
        }
        b.bevelBox(CART_X0 - 0.3f, 9.2f, CART_Z0 - 0.3f, CART_X1 + 0.3f, CART_TOP, CART_Z1 + 0.3f, 0.1f, STEEL);
        b.box(CART_X0 - 0.3f, CART_TOP, CART_Z0 - 0.3f, CART_X1 + 0.3f, CART_TOP + 0.35f, CART_Z0, STEEL);
        b.box(CART_X0 - 0.3f, CART_TOP, CART_Z0, CART_X0, CART_TOP + 0.35f, CART_Z1 + 0.3f, STEEL);
        for (int i = 0; i < 4; i++) {
            float wx = i % 2 == 0 ? CART_X0 + 0.9f : CART_X1 - 0.9f, wz = i / 2 == 0 ? CART_Z0 + 0.9f : CART_Z1 - 0.9f;
            b.box(wx - 0.25f, 1.3f, wz - 0.4f, wx + 0.25f, 1.9f, wz + 0.4f, STEEL);
            b.push().translate(wx, 1.35f, wz).rotateZ(90);
            b.cylinder(0.55f, -0.22f, 0.22f, 12, RUBBER, RUBBER, RUBBER);
            b.pop();
        }
        bar(b, v(CART_X0 + 0.8f, 9.4f, CART_Z0 - 0.5f), v(CART_X0 + 0.8f, 11.2f, CART_Z0 - 1.3f), 0.18f, STEEL);
        bar(b, v(CART_X1 - 0.8f, 9.4f, CART_Z0 - 0.5f), v(CART_X1 - 0.8f, 11.2f, CART_Z0 - 1.3f), 0.18f, STEEL);
        bar(b, v(CART_X0 + 0.8f, 11.2f, CART_Z0 - 1.3f), v(CART_X1 - 0.8f, 11.2f, CART_Z0 - 1.3f), 0.2f, STEEL);
        // boite a aiguilles jaune sur le flanc gauche
        b.bevelBox(CART_X0 - 1.8f, 6.0f, 6.0f, CART_X0, 8.4f, 8.6f, 0.15f, YELLOW);
        b.box(CART_X0 - 1.85f, 8.0f, 6.3f, CART_X0 - 1.75f, 8.25f, 8.3f, ENAMEL);
        // tablette et defibrillateur
        b.box(CART_X1, 8.3f, 5.2f, CART_X1 + 4.4f, 8.8f, 10.8f, STEEL);
        bar(b, v(CART_X1, 6.6f, 8f), v(CART_X1 + 3.8f, 8.3f, 8f), 0.15f, STEEL);
        b.bevelBox(13.0f, 8.8f, 5.8f, 16.4f, 11.0f, 10.2f, 0.25f, YELLOW);
        b.face(RUBBER, v(0, 0, 1), v(13.3f, 9.2f, 10.21f), v(15.2f, 9.2f, 10.21f), v(15.2f, 10.6f, 10.21f), v(13.3f, 10.6f, 10.21f));
        b.push().translate(15.8f, 9.9f, 10.2f).rotateX(90);
        b.cylinder(0.4f, 0, 0.25f, 12, STEEL, null, RUBBER);
        b.pop();
        b.box(13.4f, 11.0f, 6.6f, 14.4f, 11.05f, 9.4f, RUBBER);
        b.box(15.0f, 11.0f, 6.6f, 16.0f, 11.05f, 9.4f, RUBBER);
        // potence et poche de sang
        float ivx = 17.8f, ivz = 1.2f;
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(i * 72 + 10);
            bar(b, v(ivx, 1.2f, ivz), v(ivx + (float) Math.cos(a) * 2.0f, 0.95f, ivz + (float) Math.sin(a) * 2.0f), 0.14f, RUBBER);
        }
        bar(b, v(ivx, 0.9f, ivz), v(ivx, 21.5f, ivz), 0.16f, STEEL);
        bar(b, v(ivx - 1.4f, 21.2f, ivz), v(ivx + 1.4f, 21.2f, ivz), 0.1f, STEEL);
        b.push().translate(ivx - 1.1f, 17.4f, ivz);
        b.roundedSlab(1.9f, 3.0f, 0.7f, 0.6f, 0.3f, 4, BLOOD, BLOOD);
        b.pop();
        bar(b, v(ivx - 1.1f, 18.9f, ivz), v(ivx - 1.1f, 21.2f, ivz), 0.05f, PLASTIC);
        b.tube(new float[][]{v(ivx - 1.1f, 15.8f, ivz), v(ivx - 1.1f, 14.8f, ivz), v(ivx - 1.8f, 13.6f, ivz + 1.2f), v(ivx - 3.5f, 12.2f, ivz + 3.2f),
                v(CASE_X1 - 0.6f, CASE_MID + 0.3f, CASE_Z0 + 0.6f)}, new float[]{0.08f, 0.08f, 0.08f, 0.08f, 0.08f}, 5, BLOOD, true, true, 0, false);
        b.push().translate(ivx - 1.1f, 15.5f, ivz);
        b.cylinder(0.25f, -0.6f, 0.4f, 8, PLASTIC, PLASTIC, PLASTIC);
        b.pop();
        // pied du moniteur (5 branches a roulettes)
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(i * 72 + 30);
            float ex = MON_X + (float) Math.cos(a) * 2.4f, ez = MON_Z + (float) Math.sin(a) * 2.4f;
            bar(b, v(MON_X, 1.5f, MON_Z), v(ex, 1.1f, ez), 0.18f, RUBBER);
            b.push().translate(ex, 1.1f, ez);
            b.sphere(0.35f, 8, 6, RUBBER);
            b.pop();
        }
        bar(b, v(MON_X, 1.0f, MON_Z), v(MON_X, MON_Y - 2.4f, MON_Z), 0.22f, STEEL);
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        site = b.build();

        // mallette : bac ouvert, mousse et materiel
        MeshBuilder c = new MeshBuilder();
        float t = 0.3f;
        c.box(CASE_X0, CART_TOP, CASE_Z0, CASE_X1, CART_TOP + t, CASE_Z1, PLASTIC);
        c.box(CASE_X0, CART_TOP, CASE_Z0, CASE_X1, CASE_MID, CASE_Z0 + t, PLASTIC);
        c.box(CASE_X0, CART_TOP, CASE_Z1 - t, CASE_X1, CASE_MID, CASE_Z1, PLASTIC);
        c.box(CASE_X0, CART_TOP, CASE_Z0, CASE_X0 + t, CASE_MID, CASE_Z1, PLASTIC);
        c.box(CASE_X1 - t, CART_TOP, CASE_Z0, CASE_X1, CASE_MID, CASE_Z1, PLASTIC);
        c.box(CASE_X0 + t, CART_TOP + t, CASE_Z0 + t, CASE_X1 - t, CASE_MID - 0.6f, CASE_Z1 - t, FOAM);
        float fy = CASE_MID - 0.6f;
        for (int i = 0; i < 3; i++) {
            float z = CASE_Z0 + 1.2f + i * 0.7f;
            c.push().translate(CASE_X0 + 1.0f, fy + 0.2f, z).rotateZ(-90);
            c.cylinder(0.18f, 0, 2.6f, 8, PLASTIC, null, PLASTIC);
            c.cylinder(0.05f, 2.6f, 3.4f, 5, STEEL, null, STEEL);
            c.cylinder(0.22f, -0.5f, 0, 8, BLOOD, BLOOD, BLOOD);
            c.pop();
        }
        for (int i = 0; i < 2; i++) {
            c.push().translate(CASE_X1 - 1.2f - i * 1.3f, fy, CASE_Z0 + 1.4f);
            c.cylinder(0.5f, 0, 1.1f, 10, YELLOW, null, YELLOW);
            c.cylinder(0.52f, 1.1f, 1.4f, 10, PLASTIC, null, PLASTIC);
            c.pop();
        }
        c.push().translate(CASE_X1 - 1.8f, fy + 0.5f, CASE_Z1 - 1.6f).rotateX(90);
        c.cylinder(0.75f, -0.6f, 0.6f, 12, PLASTIC, PLASTIC, PLASTIC);
        c.pop();
        c.push().translate(CASE_CX - 0.5f, fy + 0.25f, CASE_Z1 - 1.3f).rotateZ(90);
        c.cylinder(0.25f, -1.6f, 1.6f, 8, YELLOW, BLOOD, RUBBER);
        c.pop();
        // loquets
        for (int sd = -1; sd <= 1; sd += 2) {
            c.box(CASE_CX + sd * 2.6f - 0.35f, CASE_MID - 0.8f, CASE_Z1, CASE_CX + sd * 2.6f + 0.35f, CASE_MID + 0.3f, CASE_Z1 + 0.25f, STEEL);
        }
        c.box(CASE_CX - 0.4f, CASE_MID - 0.6f, CASE_Z1, CASE_CX + 0.4f, CASE_MID - 0.1f, CASE_Z1 + 0.3f, STEEL);
        Occlusion.bake(c, 0.3f, 3f, 0.4f, Float.NaN);
        caseBase = c.build();
        // couvercle (repere : charniere arriere, en haut du bac)
        MeshBuilder l = new MeshBuilder();
        float hw = (CASE_X1 - CASE_X0) / 2, d = CASE_Z1 - CASE_Z0;
        l.box6(-hw, 0, 0, hw, LID_H, d, PLASTIC, PLASTIC, FOAM, LID, PLASTIC, PLASTIC);
        bar(l, v(-1.4f, LID_H * 0.5f, d + 0.5f), v(1.4f, LID_H * 0.5f, d + 0.5f), 0.14f, RUBBER);
        l.box(-1.5f, LID_H * 0.3f, d, -1.3f, LID_H * 0.7f, d + 0.5f, RUBBER);
        l.box(1.3f, LID_H * 0.3f, d, 1.5f, LID_H * 0.7f, d + 0.5f, RUBBER);
        lid = l.build();

        // moniteur (repere : centre de l'ecran, face +Z)
        MeshBuilder m = new MeshBuilder();
        m.bevelBox(-SCR_W / 2 - 0.5f, -SCR_H / 2 - 0.5f, -0.7f, SCR_W / 2 + 0.5f, SCR_H / 2 + 0.5f, 0.7f, 0.25f, PLASTIC);
        m.face(SCREEN, v(0, 0, 1), v(-SCR_W / 2, -SCR_H / 2, 0.71f), v(SCR_W / 2, -SCR_H / 2, 0.71f), v(SCR_W / 2, SCR_H / 2, 0.71f), v(-SCR_W / 2, SCR_H / 2, 0.71f));
        bar(m, v(-1.5f, SCR_H / 2 + 0.5f, 0), v(-1.2f, SCR_H / 2 + 1.1f, 0), 0.12f, RUBBER);
        bar(m, v(1.5f, SCR_H / 2 + 0.5f, 0), v(1.2f, SCR_H / 2 + 1.1f, 0), 0.12f, RUBBER);
        bar(m, v(-1.2f, SCR_H / 2 + 1.1f, 0), v(1.2f, SCR_H / 2 + 1.1f, 0), 0.12f, RUBBER);
        m.box(-0.5f, -SCR_H / 2 - 1.9f, -0.4f, 0.5f, -SCR_H / 2 - 0.5f, 0.4f, STEEL);
        m.box(SCR_W / 2 + 0.52f, -1.5f, -0.4f, SCR_W / 2 + 0.6f, 1.5f, 0.4f, RUBBER);
        monitor = m.build();
        for (int i = 0; i < 4; i++) {
            MeshBuilder q = new MeshBuilder();
            Mat band = MSG.sub(0, i / 4f, 1, (i + 1) / 4f);
            float mw = 3.6f, mh = 0.9f, mx = -0.7f, my = 1.25f;
            q.poly(new float[][]{v(mx - mw / 2, my - mh / 2, 0.74f), v(mx + mw / 2, my - mh / 2, 0.74f), v(mx + mw / 2, my + mh / 2, 0.74f), v(mx - mw / 2, my + mh / 2, 0.74f)},
                    new float[][]{{band.u(0), band.v(1)}, {band.u(1), band.v(1)}, {band.u(1), band.v(0)}, {band.u(0), band.v(0)}}, null, v(0, 0, 1));
            msg[i] = q.build();
        }

        // palette de defibrillateur (repere : centre de l'electrode, electrode vers -Y)
        MeshBuilder pd = new MeshBuilder();
        pd.box(-0.75f, 0, -1.1f, 0.75f, 0.2f, 1.1f, STEEL);
        pd.bevelBox(-0.85f, 0.2f, -1.2f, 0.85f, 0.9f, 1.2f, 0.2f, PLASTIC);
        pd.push().translate(0, 0.9f, 0);
        pd.cylinder(0.36f, 0, 1.6f, 10, RUBBER, null, RUBBER);
        pd.pop();
        pd.push().translate(0, 2.5f, 0);
        pd.cylinder(0.3f, 0, 0.2f, 10, ENAMEL, null, ENAMEL);
        pd.pop();
        paddle = pd.build();
        MeshBuilder dl = new MeshBuilder();
        dl.push().translate(15.8f, 9.9f, 10.45f);
        dl.sphere(0.3f, 8, 6, Mat.WHITE);
        dl.pop();
        defibLed = dl.build();

        // croix holographique
        MeshBuilder ch = new MeshBuilder();
        ch.box(-2.4f, -0.8f, -0.25f, 2.4f, 0.8f, 0.25f, Mat.WHITE);
        ch.box(-0.8f, -2.4f, -0.25f, 0.8f, 2.4f, 0.25f, Mat.WHITE);
        crossHolo = ch.build();

        // cadenas et cle
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.SHIELD;
        ps.w = 2.8f;
        ps.h = 3.0f;
        ps.d = 1.2f;
        ps.height = 1.5f;
        ps.halfSpan = 0.9f;
        ps.wire = 0.34f;
        ps.body = ENAMEL;
        ps.face = LOCKFACE;
        ps.shackle = STEEL;
        ps.trim = STEEL;
        ps.hole = RUBBER;
        lock = new Padlock(ps);
        lock.build(detail);
        Mesh[] k = KeySmith.build(STEEL, ENAMEL, 0.5f, (kb, kg) -> {
            kb.push().translate(0, 2.9f, -0.35f);
            kb.box(-2.2f, -0.75f, 0, 2.2f, 0.75f, 0.7f, ENAMEL);
            kb.box(-0.75f, -2.2f, 0, 0.75f, 2.2f, 0.7f, ENAMEL);
            kb.pop();
            kg.push().translate(0, 2.9f, -0.45f);
            kg.box(-2.5f, -1f, 0, 2.5f, 1f, 0.9f, Mat.WHITE);
            kg.box(-1f, -2.5f, 0, 1f, 2.5f, 0.9f, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== chronologie
    private static final float FLAT0 = CrateAnim.UNLOCK + 2;
    private static final float CH1 = CrateAnim.UNLOCK + 4, LIFT1 = CH1 + 4, MOVE1 = LIFT1 + 4, SHOCK1 = CrateAnim.OPEN + 5;
    private static final float CH2 = SHOCK1 + 3, SHOCK2 = SHOCK1 + 10, BEAT0 = SHOCK2 + 2;
    private static final float LID0 = SHOCK2 + 1, LID1 = LID0 + 7;
    private static final float BACK0 = SHOCK2 + 8, BACK1 = BACK0 + 12;
    private static final float IDLE_RATE = 0.06f, SWEEP = 34f;

    /** Avance cumulee de la phase cardiaque par rapport au rythme du repos (integrale exacte, par morceaux). */
    private static float acc(float tq) {
        if (tq <= FLAT0) return 0;
        float a = -IDLE_RATE * (Math.min(tq, BEAT0) - FLAT0);
        if (tq <= BEAT0) return a;
        a += (0.1f - IDLE_RATE) * (Math.min(tq, CrateAnim.SPIN) - BEAT0);
        if (tq <= CrateAnim.SPIN) return a;
        // rythme qui suit la roue : 0,1 + 0,06 (1 - x)^2, x = avancement de la rotation (s'emballe puis s'apaise)
        float len = CrateAnim.STOP - CrateAnim.SPIN;
        float te = Math.min(tq, CrateAnim.STOP) - CrateAnim.SPIN;
        double x = te / len;
        double integ = (1 - Math.pow(1 - x, 3)) / 3;
        a += (0.1f - IDLE_RATE) * te + 0.06f * (float) (integ * len);
        if (tq <= CrateAnim.STOP) return a;
        return a + (0.078f - IDLE_RATE) * (tq - CrateAnim.STOP);
    }

    /** Phase cardiaque (battements) au temps de sequence tq (continue avec le repos). */
    private static float phase(CrateState s, float tq) {
        float base = ((s.active ? s.time - s.t : s.time) + tq) * IDLE_RATE;
        return s.active ? base + acc(tq) : base;
    }

    /** Trace ECG normalise (-0.3..1) d'un battement (x = 0..1). */
    private static float ecg(float x) {
        return 0.12f * g(x, 0.1f, 0.04f) - 0.14f * g(x, 0.235f, 0.02f) + 1.0f * g(x, 0.27f, 0.024f) - 0.28f * g(x, 0.305f, 0.022f)
                + 0.26f * g(x, 0.5f, 0.07f);
    }

    private static float g(float x, float c, float w) {
        float d = (x - c) / w;
        return (float) Math.exp(-d * d);
    }

    /** Valeur affichee par le moniteur au temps tq. */
    private static float trace(CrateState s, float tq) {
        boolean flat = s.active && tq >= FLAT0 && tq < BEAT0;
        float v;
        if (flat) {
            v = 0.03f * (float) Math.sin(tq * 2.3f) + 0.02f * (float) Math.sin(tq * 5.1f);
        } else {
            float ph = phase(s, tq);
            v = ecg(ph - (float) Math.floor(ph));
        }
        if (s.active) {
            for (float sh : new float[]{SHOCK1, SHOCK2}) {
                float d = tq - sh;
                if (d >= 0 && d < 3) v += (sh == SHOCK2 ? 1.4f : 1.1f) * (float) Math.exp(-d * 1.6f) * (float) Math.cos(d * 9);
            }
        }
        return v;
    }

    /** Message du moniteur (0 asystolie, 1 clear, 2 sinusal, 3 charge ; -1 aucun). */
    private static int message(CrateState s) {
        if (!s.active) return -1;
        float t = s.t;
        if (t < FLAT0) return -1;
        if (t < CH1) return 0;
        if (t < SHOCK1 - 4) return 3;
        if (t < SHOCK1) return 1;
        if (t < CH2) return 0;
        if (t < SHOCK2 - 3) return 3;
        if (t < SHOCK2) return 1;
        if (t < BEAT0) return -1;
        if (t < CrateAnim.STOP + 20) return 2;
        return -1;
    }

    private static int charging(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        if (t >= CH1 && t < SHOCK1) return 1;
        if (t >= CH2 && t < SHOCK2) return 2;
        return 0;
    }

    /** Pose d'une palette (0 gauche, 1 droite). */
    private static void paddlePose(CrateState s, int side, Pose p) {
        float[] dock = side == 0 ? DOCK_L : DOCK_R, shock = side == 0 ? SHOCK_L : SHOCK_R;
        float t = s.active ? s.t : 0;
        float lift = s.active ? Ease.smooth(Ease.range(t, CH1, LIFT1)) * (1 - Ease.smooth(Ease.range(t, BACK1 - 3, BACK1))) : 0;
        float move = s.active ? Ease.inOutCubic(Ease.range(t, LIFT1, MOVE1)) * (1 - Ease.inOutCubic(Ease.range(t, BACK0, BACK1 - 3))) : 0;
        float[] pos = {Ease.lerp(dock[0], shock[0], move), Ease.lerp(dock[1], shock[1], move) + 3.0f * (float) Math.sin(Math.PI * move) + 1.8f * lift * (1 - move),
                Ease.lerp(dock[2], shock[2], move)};
        // recul et appui au moment des chocs
        for (float sh : new float[]{SHOCK1, SHOCK2}) {
            float d = t - sh;
            if (s.active && d > -2 && d < 4) {
                float press = d < 0 ? (d + 2) / 2 : (float) Math.exp(-d * 1.2f);
                pos[0] += (side == 0 ? 1 : -1) * 0.15f * press;
            }
        }
        float hover = 0.15f * (float) Math.sin(s.time * 0.2f + side) * move;
        p.translate(pos[0], pos[1] + hover, pos[2]).rotZ((side == 0 ? 90f : -90f) * move);
    }

    /** Saut de la mallette (px) au choc. */
    private static float jump(CrateState s) {
        if (!s.active) return 0;
        float t = s.t, j = 0;
        for (int i = 0; i < 2; i++) {
            float sh = i == 0 ? SHOCK1 : SHOCK2, d = t - sh, amp = i == 0 ? 1.3f : 2.6f, dur = i == 0 ? 5 : 7;
            if (d > 0 && d < dur) j = Math.max(j, amp * (float) Math.sin(Math.PI * d / dur));
        }
        return j;
    }

    private static float lidOpen(CrateState s) {
        if (!s.active) return 0;
        return Ease.outBack(Ease.range(s.t, LID0, LID1), 2.2f) * (1 - Ease.inOutCubic(Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.CLOSE + 10)));
    }

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float t = s.t, v = 0;
        if (t >= SHOCK1) v = Math.max(v, 0.35f * (float) Math.exp(-(t - SHOCK1) * 0.4f));
        if (t >= SHOCK2) v = Math.max(v, 0.6f * (float) Math.exp(-(t - SHOCK2) * 0.3f));
        return Math.max(v, 0.25f * Seq.stopFlash(s));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        out.solid(p, site, gl);
        float j = jump(s);
        float tilt = s.active ? 4f * (float) Math.sin(Math.PI * Math.min(1, Math.max(0, (s.t - SHOCK2) / 7f))) : 0;
        p.push().translate(0, j, 0).translate(CASE_CX, CART_TOP, CASE_CZ).rotZ(tilt).translate(-CASE_CX, -CART_TOP, -CASE_CZ);
        out.solid(p, caseBase, gl);
        float lo = lidOpen(s);
        p.push().translate(CASE_CX, CASE_MID, CASE_Z0).rotX(-112f * lo);
        out.solid(p, lid, gl);
        p.pop();
        p.pop();
        // palettes et leurs cables
        Pose[] pp = {new Pose(), new Pose()};
        MeshBuilder cb = new MeshBuilder();
        for (int i = 0; i < 2; i++) {
            paddlePose(s, i, pp[i]);
            p.push().mul(pp[i].m());
            out.solid(p, paddle, gl);
            p.pop();
            float[] tail = pp[i].apply(0, 2.4f, 0);
            float[] from = v(14.2f + i * 1.4f, 9.4f, 10.25f);
            float[][] path = new float[8][];
            for (int k = 0; k < path.length; k++) {
                float u = k / (float) (path.length - 1);
                float sag = 2.2f * (float) Math.sin(Math.PI * u);
                path[k] = v(Ease.lerp(from[0], tail[0], u), Ease.lerp(from[1], tail[1], u) - sag, Ease.lerp(from[2], tail[2], u) + 0.8f * (float) Math.sin(Math.PI * u));
            }
            float[] radii = new float[path.length];
            java.util.Arrays.fill(radii, 0.1f);
            cb.tube(path, radii, 5, RUBBER, false, false, 0, false);
        }
        out.solid(p, cb.buildDynamic(), gl);
        // defibrillateur : voyant de charge
        int ch = charging(s);
        float led = ch > 0 ? 0.5f + 0.5f * (float) Math.sin(s.t * (ch == 1 ? 1.2f : 2.2f)) : 0.25f;
        out.additive(p, defibLed, ch > 0 ? 0xFF6A1A : GREEN, 0.4f + 0.6f * led);
        // moniteur : boitier, ecran, trace
        p.push().translate(MON_X, MON_Y, MON_Z).rotY(MON_YAW);
        out.solid(p, monitor, 1f);
        boolean flat = s.active && s.t >= FLAT0 && s.t < BEAT0;
        int col = flat ? RED : GREEN;
        out.additive(p, traceMesh(s, 0.2f), col, 0.3f);
        out.additive(p, traceMesh(s, 0.075f), col, 1f);
        int mi = message(s);
        if (mi >= 0) {
            float bl = mi == 1 ? (float) (0.6f + 0.4f * Math.signum(Math.sin(s.t * 1.6f))) : 1f;
            out.additive(p, msg[mi], 0xFFFFFF, bl);
        }
        p.pop();
    }

    /** Trace ECG (repere du moniteur) : balayage de gauche a droite sur SWEEP ticks. */
    private Mesh traceMesh(CrateState s, float th) {
        MeshBuilder b = new MeshBuilder();
        int n = 120;
        float x0 = -SCR_W / 2 + 0.2f, x1 = SCR_W / 2 - 1.75f, base = -0.9f, amp = 1.45f, z = 0.76f;
        float now = s.active ? s.t : 0;
        float[][] pts = new float[n][];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            float tq = now - (1 - u) * SWEEP;
            pts[i] = v(x0 + (x1 - x0) * u, base + amp * trace(s, tq), z);
        }
        float wu = Mat.WHITE.u(0.5f), wv = Mat.WHITE.v(0.5f);
        float[][] uv = {{wu, wv}, {wu, wv}, {wu, wv}, {wu, wv}};
        for (int i = 0; i < n - 1; i++) {
            float[] a = pts[i], c = pts[i + 1];
            float dx = c[0] - a[0], dy = c[1] - a[1];
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            float nx = -dy / l * th, ny = dx / l * th;
            b.poly(new float[][]{v(a[0] - nx, a[1] - ny, z), v(c[0] - nx, c[1] - ny, z), v(c[0] + nx, c[1] + ny, z), v(a[0] + nx, a[1] + ny, z)}, uv, null, v(0, 0, 1));
        }
        return b.buildDynamic();
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(CASE_CX, CASE_MID - 0.35f + jump(s), CASE_Z1 + 0.32f);
    }

    /** Arc electrique entre deux points (zigzag qui change a chaque demi-tick). */
    private static Mesh arc(float[] a, float[] c, float lift, int seed, float jag, float width) {
        Random r = new Random(seed);
        int n = 12;
        float[][] pts = new float[n][];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            float e = (float) Math.sin(Math.PI * u);
            pts[i] = v(Ease.lerp(a[0], c[0], u) + (r.nextFloat() - 0.5f) * jag * e, Ease.lerp(a[1], c[1], u) + lift * e + (r.nextFloat() - 0.5f) * jag * e,
                    Ease.lerp(a[2], c[2], u) + (r.nextFloat() - 0.5f) * jag * e * 1.5f);
        }
        return Trail.ribbon(pts, width, width * 0.6f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float aw = Seq.awaken(s);
        // lueur de l'ecran
        Pose mp = new Pose();
        mp.translate(MON_X, MON_Y, MON_Z).rotY(MON_YAW);
        float[] scr = mp.apply(0, 0, 1.2f);
        boolean flat = s.active && s.t >= FLAT0 && s.t < BEAT0;
        out.billboard(p, Sprite.HALO, scr[0], scr[1], scr[2], 7f, 0, flat ? RED : GREEN, 0.12f + 0.1f * aw);
        // tete du balayage
        float now = s.active ? s.t : 0;
        float[] head = mp.apply(SCR_W / 2 - 1.75f, -0.9f + 1.45f * trace(s, now), 0.9f);
        out.billboard(p, Sprite.HALO, head[0], head[1], head[2], 0.9f, 0, flat ? RED : GREEN, 0.9f);
        out.ground(p, Sprite.HALO, 8, 0.85f, 8, 15f, 0, GREEN, 0.08f + 0.12f * aw);
        if (!s.active) return;
        float t = s.t;
        // chocs : arcs entre les electrodes, eclair
        for (int i = 0; i < 2; i++) {
            float sh = i == 0 ? SHOCK1 : SHOCK2;
            float d = t - sh;
            if (d < 0 || d > (i == 0 ? 4 : 6)) continue;
            float e = (float) Math.exp(-d * 0.6f);
            Pose l = new Pose(), r = new Pose();
            paddlePose(s, 0, l);
            paddlePose(s, 1, r);
            float[] a = l.apply(0, 0.1f, 0), c = r.apply(0, 0.1f, 0);
            int seed = (int) (t * 3);
            int arcs = i == 0 ? 3 : 5;
            for (int k = 0; k < arcs; k++) {
                float lift = 1.2f + k * 1.0f + (i == 1 ? 1.2f : 0);
                float jag = 1.8f + 0.7f * i;
                out.additive(p, arc(a, c, lift, seed * 31 + k, jag, 1.3f), ARC, 0.45f * e);
                out.additive(p, arc(a, c, lift, seed * 31 + k, jag, 0.3f), 0xFFFFFF, e);
            }
            float mx = (a[0] + c[0]) / 2, my = CASE_MID + 1.5f + jump(s);
            out.billboard(p, Sprite.FLARE, mx, my, CASE_CZ + 1, (8f + 10f * i) * e, s.time, 0xDFF4FF, e);
            out.billboard(p, Sprite.FLARE, a[0], a[1], a[2], 3f * e, 0, ARC, e);
            out.billboard(p, Sprite.FLARE, c[0], c[1], c[2], 3f * e, 0, ARC, e);
            out.ground(p, Sprite.SHOCK, CASE_CX, CART_TOP + 0.4f, CASE_CZ, 3f + 14f * (1 - e), 0, ARC, 0.8f * e);
        }
        // charge : etincelles qui courent sur les palettes
        int ch = charging(s);
        if (ch > 0) {
            for (int i = 0; i < 2; i++) {
                Pose q = new Pose();
                paddlePose(s, i, q);
                float[] at = q.apply(0, 0.5f, 0);
                out.billboard(p, Sprite.GLINT, at[0], at[1], at[2], 1.2f + 0.6f * ch, s.time * 6, 0xFF9A3A, 0.5f + 0.5f * (float) Math.sin(t * 3 + i));
            }
        }
        // reanimation : colonne de lumiere verte, croix holographique, anneau
        float lo = lidOpen(s);
        if (lo > 0.01f) {
            float e = t >= LID0 ? (float) Math.exp(-(t - LID0) * 0.2f) : 0;
            out.billboard(p, Sprite.FLARE, CASE_CX, CASE_MID + 1, CASE_CZ, 4f + 18f * e, s.time, 0xDFFFE8, Math.min(1, lo) * (0.4f + 0.6f * e));
            out.beam(p, CASE_CX, CASE_MID, CASE_CZ, 3f, 22f, GREEN, 0.3f * Math.min(1, lo));
            float cy = CASE_MID + 5.5f + 1.5f * Ease.smooth(Ease.range(t, LID0, LID1 + 6));
            p.push().translate(CASE_CX, cy + 0.3f * (float) Math.sin(s.time * 0.08f), CASE_CZ).rotY(s.time * 2.5f).scale(Math.min(1, lo) * 0.9f);
            out.additive(p, crossHolo, GREEN, 0.55f + 0.2f * (float) Math.sin(s.time * 0.3f));
            p.pop();
            out.ground(p, Sprite.RING, CASE_CX, CART_TOP + 0.4f, CASE_CZ, 6f + 3f * (float) Math.sin(s.time * 0.1f), s.time, GREEN, 0.4f * Math.min(1, lo));
        }
        if (lo > 0.01f) CommonFx.cavity(s, p, out, this, CASE_CX, CASE_MID - 0.3f, CASE_CZ, 6f, GREEN);
    }

    @Override
    public float[] cavityPos() {
        return new float[]{CASE_CX, CASE_MID, CASE_CZ};
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.04f) {
            fx.particle(FxSink.DOT, CASE_CX + rnd(r, 4f), CASE_MID + 1.5f, CASE_CZ + rnd(r, 3f), 0, 0.05f, 0, GREEN, 0.9f, 30);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        float[] scr = {MON_X, MON_Y, MON_Z};
        // bips cardiaques (hors asystolie)
        if (k > 0 && (k < FLAT0 || k >= BEAT0) && k < CrateAnim.CLOSE) {
            float a = phase(s, k - 1), b = phase(s, k);
            if (Math.floor(a + 0.72f) != Math.floor(b + 0.72f)) fx.sound(Snd.BEEP, scr[0], scr[1], scr[2], 0.55f, k >= BEAT0 && k < CrateAnim.STOP ? 1.05f : 1f);
        }
        if (k == (int) FLAT0) fx.sound(Snd.FLATLINE, scr[0], scr[1], scr[2], 0.8f, 1f);
        if (k == (int) CH1) fx.sound(Snd.DEFIB_CHARGE, 14.7f, 10, 8, 0.9f, 1f);
        if (k == (int) CH2) fx.sound(Snd.DEFIB_CHARGE, 14.7f, 10, 8, 1.0f, 1.15f);
        if (k == (int) SHOCK1 || k == (int) SHOCK2) {
            boolean big = k == (int) SHOCK2;
            fx.sound(Snd.ZAP, CASE_CX, CASE_MID, CASE_CZ, big ? 1.2f : 0.9f, big ? 0.9f : 1.05f);
            for (int i = 0; i < (big ? 50 : 24); i++) {
                double th = r.nextDouble() * Math.PI * 2;
                float sp = 0.4f + r.nextFloat() * (big ? 1.2f : 0.7f);
                fx.particle(FxSink.SPARK, CASE_CX + rnd(r, 4f), CASE_MID + 1, CASE_CZ + rnd(r, 2f), (float) Math.cos(th) * sp, 0.3f + r.nextFloat() * 0.8f,
                        (float) Math.sin(th) * sp, r.nextBoolean() ? ARC : 0xFFFFFF, 1.2f, 12 + r.nextInt(8));
            }
            if (!big) fx.sound(Snd.FLATLINE, scr[0], scr[1], scr[2], 0.6f, 1f);
        }
        if (k == (int) LID0) {
            fx.sound(Snd.HOLO, CASE_CX, CASE_MID + 2, CASE_CZ, 0.8f, 1.2f);
            fx.sound(Snd.CAPSULE_POP, CASE_CX, CASE_MID + 1, CASE_CZ, 0.9f, 0.8f);
            for (int i = 0; i < 40; i++) {
                double th = r.nextDouble() * Math.PI * 2;
                float sp = 0.3f + r.nextFloat() * 0.8f;
                fx.particle(i % 3 == 0 ? FxSink.CONFETTI : FxSink.DOT, CASE_CX + rnd(r, 2f), CASE_MID + 1, CASE_CZ + rnd(r, 2f), (float) Math.cos(th) * sp,
                        1.0f + r.nextFloat() * 1.4f, (float) Math.sin(th) * sp, i % 4 == 0 ? RED : (i % 2 == 0 ? 0xFFFFFF : GREEN), 1.3f, 40 + r.nextInt(20));
            }
        }
        if (k == (int) BACK0) fx.sound(Snd.WHOOSH, 10, 12, 8, 0.4f, 1.3f);
        if (k == CrateAnim.CLOSE + 9) fx.sound(Snd.CLOSE, CASE_CX, CASE_MID, CASE_CZ, 0.7f, 1.3f);
    }
}
