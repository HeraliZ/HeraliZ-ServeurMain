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
 * Caisse Camos : le mur de graffiti. Une ruelle la nuit : mur de briques couronne de barbele, vieux tags delaves et
 * affiches dechirees, un grand aplat gris "buffe" par la voirie, un reverbere au sodium qui gresille ; sur le trottoir,
 * la cage grillagee ou l'on enferme les bombes de peinture (le cadenas ferme sa porte) et une caisse a lait.
 * <p>A l'ouverture : la porte de la cage s'ouvre, six bombes s'envolent en cliquetant. Pendant toute la roue elles
 * taguent le mur : quatre bombes posent le fond et remplissent chacune une lettre de "CAMO" avec un camo different, la
 * noire trace les contours et l'extrusion 3D, la blanche pose les brillances, les etoiles et les coulures. A l'arret de
 * la roue la fresque s'allume comme un neon (scratch, gerbe de peinture). A la fermeture, un rouleau de la voirie
 * repasse le mur en gris et les bombes rentrent dans la cage.
 */
public final class Graffiti extends Design {
    private static final Mat BRICK = Mat.MAIN;
    private static final Mat CONCRETE = Mat.TRIM;
    private static final Mat MESH = Mat.ACCENT;
    private static final Mat SIGN = Mat.EMBLEM;
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat PAVING = Mat.INTERIOR;
    private static final Mat CAN = Mat.GEM;
    private static final Mat STEEL = Mat.DARK;
    private static final Mat PLASTIC = Mat.SPECIAL;
    /** Couches de la fresque (alpha) : remplissages, contours (sur deux tuiles contigues), reflets. */
    private static final Mat FILL = Mat.fit(512, 0, 512, 256), OUTLINE = Mat.fit(0, 768, 512, 256), SHINE = Mat.fit(512, 256, 512, 256);

    /** Couleurs des bombes : quatre remplissages (une par lettre), contour noir, reflets blancs. */
    private static final int[] CAN_RGB = {0xFF7A1A, 0x20E8FF, 0x9A5CFF, 0xFF2BD6, 0x202024, 0xF4F4F4};
    private static final int NCAN = 6;

    /** Trottoir ; mur (face avant en WALL_Z) ; fresque ; cage. */
    private static final float SW_T = 0.9f;
    private static final float WALL_X0 = -5f, WALL_X1 = 21f, WALL_Z0 = -2f, WALL_Z = 1.5f, WALL_TOP = 19f;
    private static final float PX0 = -3f, PX1 = 19f, PY0 = 5f, PY1 = 16f;
    private static final int NS = 32;
    private static final float CG_X0 = 4.5f, CG_X1 = 11.5f, CG_Z0 = 9.5f, CG_Z1 = 14.5f, CG_H = 5.6f;
    private static final float[] LAMP = {1.2f, 23.6f, 12.2f};
    /** Distance des bombes au mur quand elles peignent. */
    private static final float SPRAY_Z = WALL_Z + 3.4f;

    private Mesh site = Mesh.EMPTY, buff = Mesh.EMPTY, cage = Mesh.EMPTY, cageMesh = Mesh.EMPTY, door = Mesh.EMPTY, doorMesh = Mesh.EMPTY;
    private Mesh canBody = Mesh.EMPTY, canCap = Mesh.EMPTY, cone = Mesh.EMPTY, roller = Mesh.EMPTY, lampHead = Mesh.EMPTY, lampCone = Mesh.EMPTY;
    private final Mesh[] fillSl = new Mesh[NS], lineSl = new Mesh[NS], shineSl = new Mesh[NS], buffSl = new Mesh[NS];

    public Graffiti() {
        super("camo", 0xFF3FA4, 0x2FE0FF);
        keyRgb = 0xFF4FD8;
        cavityRgb = 0xFF7AE0;
        cavityAccent = 0x20E8FF;
        size = 1.45f;
        ringY = 44;
        ringR = 24;
        revealY = 50;
        riseFromY = 10;
        lockFloorY = SW_T;
    }

    // =================================================================== construction
    private static void bar(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 6, m, true, true, 0, false);
    }

    /** Tranche i (0..NS-1) d'une couche de la fresque, UV explicites sur la tuile m. */
    private static Mesh slice(Mat m, int i, float z) {
        MeshBuilder b = new MeshBuilder();
        float u0 = i / (float) NS, u1 = (i + 1) / (float) NS;
        float x0 = Ease.lerp(PX0, PX1, u0), x1 = Ease.lerp(PX0, PX1, u1);
        b.poly(new float[][]{v(x0, PY0, z), v(x1, PY0, z), v(x1, PY1, z), v(x0, PY1, z)},
                new float[][]{{m.u(u0), m.v(1)}, {m.u(u1), m.v(1)}, {m.u(u1), m.v(0)}, {m.u(u0), m.v(0)}}, null, v(0, 0, 1));
        return b.build();
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // trottoir, bordure, caniveau
        b.box(-6f, 0, WALL_Z, 22f, SW_T, 17.5f, PAVING);
        b.box(-6f, 0, 17.5f, 22f, SW_T + 0.15f, 19.2f, CONCRETE);
        b.box(-6f, -0.4f, 19.2f, 22f, 0.1f, 21f, STEEL);
        // mur de briques, chaperon de beton, barbele
        b.box(WALL_X0, 0, WALL_Z0, WALL_X1, WALL_TOP, WALL_Z, BRICK);
        b.box(WALL_X0 - 0.3f, WALL_TOP, WALL_Z0 - 0.3f, WALL_X1 + 0.3f, WALL_TOP + 0.8f, WALL_Z + 0.3f, CONCRETE);
        b.box(WALL_X0 - 0.2f, 0, WALL_Z - 0.05f, WALL_X1 + 0.2f, 1.6f, WALL_Z + 0.25f, CONCRETE);
        for (float x = WALL_X0 + 1; x < WALL_X1; x += 5) bar(b, v(x, WALL_TOP + 0.8f, (WALL_Z0 + WALL_Z) / 2), v(x, WALL_TOP + 2.8f, (WALL_Z0 + WALL_Z) / 2), 0.1f, STEEL);
        int nh = 120;
        float[][] coil = new float[nh][];
        float[] cr = new float[nh];
        for (int i = 0; i < nh; i++) {
            float u = i / (float) (nh - 1), th = u * 26 * (float) Math.PI;
            coil[i] = v(Ease.lerp(WALL_X0 + 0.3f, WALL_X1 - 0.3f, u), WALL_TOP + 2.0f + 0.9f * (float) Math.sin(th), (WALL_Z0 + WALL_Z) / 2 + 0.9f * (float) Math.cos(th));
            cr[i] = 0.07f;
        }
        b.tube(coil, cr, 4, STEEL, false, false, 0, false);
        // cage a bombes : cadre, tablette
        float y0 = SW_T, y1 = SW_T + CG_H;
        float[][] cn = {{CG_X0, CG_Z0}, {CG_X1, CG_Z0}, {CG_X1, CG_Z1}, {CG_X0, CG_Z1}};
        for (int i = 0; i < 4; i++) {
            bar(b, v(cn[i][0], y0, cn[i][1]), v(cn[i][0], y1, cn[i][1]), 0.16f, STEEL);
            float[] a = cn[i], c = cn[(i + 1) % 4];
            bar(b, v(a[0], y1, a[1]), v(c[0], y1, c[1]), 0.14f, STEEL);
            bar(b, v(a[0], y0 + 0.1f, a[1]), v(c[0], y0 + 0.1f, c[1]), 0.14f, STEEL);
        }
        b.box(CG_X0, y0, CG_Z0, CG_X1, y0 + 0.2f, CG_Z1, STEEL);
        // reverbere
        bar(b, v(-3.8f, SW_T, 16.5f), v(-3.8f, 22.4f, 16.5f), 0.3f, STEEL);
        b.push().translate(-3.8f, SW_T, 16.5f);
        b.cylinder(0.7f, 0, 1.2f, 10, STEEL, null, STEEL);
        b.pop();
        b.tube(new float[][]{v(-3.8f, 22.2f, 16.5f), v(-3.6f, 23.6f, 16.0f), v(-2.4f, 24.3f, 14.6f), v(-0.4f, 24.2f, 13.0f), v(LAMP[0], 24.0f, LAMP[2])},
                new float[]{0.24f, 0.22f, 0.2f, 0.2f, 0.2f}, 6, STEEL, false, true, 0, false);
        // caisse a lait (bleue) et deux bombes renversees
        b.push().translate(17.2f, SW_T, 14.2f).rotateY(18);
        b.box(-1.8f, 0, -1.8f, 1.8f, 0.2f, 1.8f, PLASTIC);
        b.box(-1.8f, 0, -1.8f, 1.8f, 2.6f, -1.55f, PLASTIC);
        b.box(-1.8f, 0, 1.55f, 1.8f, 2.6f, 1.8f, PLASTIC);
        b.box(-1.8f, 0, -1.8f, -1.55f, 2.6f, 1.8f, PLASTIC);
        b.box(1.55f, 0, -1.8f, 1.8f, 2.6f, 1.8f, PLASTIC);
        b.pop();
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        site = b.build();
        // aplat "buffe" (gris de la voirie) sous la fresque
        MeshBuilder bf = new MeshBuilder();
        bf.face(CONCRETE, v(0, 0, 1), v(PX0 - 0.6f, PY0 - 0.6f, WALL_Z + 0.01f), v(PX1 + 0.6f, PY0 - 0.6f, WALL_Z + 0.01f),
                v(PX1 + 0.6f, PY1 + 0.6f, WALL_Z + 0.01f), v(PX0 - 0.6f, PY1 + 0.6f, WALL_Z + 0.01f));
        buff = bf.build();
        for (int i = 0; i < NS; i++) {
            fillSl[i] = slice(FILL, i, WALL_Z + 0.03f);
            lineSl[i] = slice(OUTLINE, i, WALL_Z + 0.045f);
            shineSl[i] = slice(SHINE, i, WALL_Z + 0.06f);
            MeshBuilder gb = new MeshBuilder();
            float x0 = Ease.lerp(PX0, PX1, i / (float) NS), x1 = Ease.lerp(PX0, PX1, (i + 1) / (float) NS);
            gb.face(CONCRETE, v(0, 0, 1), v(x0, PY0 - 0.6f, WALL_Z + 0.075f), v(x1, PY0 - 0.6f, WALL_Z + 0.075f), v(x1, PY1 + 0.6f, WALL_Z + 0.075f), v(x0, PY1 + 0.6f, WALL_Z + 0.075f));
            buffSl[i] = gb.build();
        }
        // grillage de la cage (translucide) et porte (repere : charniere gauche, en bas)
        MeshBuilder cm = new MeshBuilder();
        cm.face(MESH, v(0, 0, -1), v(CG_X0, y0, CG_Z0), v(CG_X1, y0, CG_Z0), v(CG_X1, y1, CG_Z0), v(CG_X0, y1, CG_Z0));
        cm.face(MESH, v(-1, 0, 0), v(CG_X0, y0, CG_Z0), v(CG_X0, y0, CG_Z1), v(CG_X0, y1, CG_Z1), v(CG_X0, y1, CG_Z0));
        cm.face(MESH, v(1, 0, 0), v(CG_X1, y0, CG_Z0), v(CG_X1, y0, CG_Z1), v(CG_X1, y1, CG_Z1), v(CG_X1, y1, CG_Z0));
        cm.face(MESH, v(0, 1, 0), v(CG_X0, y1, CG_Z0), v(CG_X1, y1, CG_Z0), v(CG_X1, y1, CG_Z1), v(CG_X0, y1, CG_Z1));
        cageMesh = cm.build();
        float dw = CG_X1 - CG_X0;
        MeshBuilder dr = new MeshBuilder();
        bar(dr, v(0.2f, 0.3f, 0), v(dw - 0.2f, 0.3f, 0), 0.12f, STEEL);
        bar(dr, v(0.2f, CG_H - 0.3f, 0), v(dw - 0.2f, CG_H - 0.3f, 0), 0.12f, STEEL);
        bar(dr, v(dw - 0.2f, 0.3f, 0), v(dw - 0.2f, CG_H - 0.3f, 0), 0.12f, STEEL);
        dr.face(SIGN, v(0, 0, 1), v(dw / 2 - 1.8f, CG_H / 2 - 0.2f, 0.12f), v(dw / 2 + 1.8f, CG_H / 2 - 0.2f, 0.12f),
                v(dw / 2 + 1.8f, CG_H / 2 + 1.9f, 0.12f), v(dw / 2 - 1.8f, CG_H / 2 + 1.9f, 0.12f));
        dr.box(dw - 0.5f, CG_H * 0.42f, -0.1f, dw + 0.15f, CG_H * 0.42f + 0.6f, 0.3f, STEEL);
        door = dr.build();
        MeshBuilder dm = new MeshBuilder();
        dm.face(MESH, v(0, 0, 1), v(0.2f, 0.3f, 0), v(dw - 0.2f, 0.3f, 0), v(dw - 0.2f, CG_H - 0.3f, 0), v(0.2f, CG_H - 0.3f, 0));
        doorMesh = dm.build();
        // bombe de peinture (repere : bas au centre, buse vers +Y) : corps a teinter, capuchon
        MeshBuilder cb = new MeshBuilder();
        cb.cylinder(0.6f, 0, 2.0f, 14, CAN, CAN, CAN);
        cb.lathe(new float[][]{{0.6f, 2.0f}, {0.5f, 2.2f}, {0.32f, 2.36f}}, 14, 0, 360, CAN, 1, 1, 0, 0, 0, false);
        canBody = cb.build();
        MeshBuilder cc = new MeshBuilder();
        cc.cylinder(0.42f, 2.34f, 2.95f, 12, CAN, null, CAN);
        cc.box(-0.12f, 2.65f, 0.3f, 0.12f, 2.85f, 0.5f, STEEL);
        canCap = cc.build();
        // cone de pulverisation (large cote mur)
        MeshBuilder co = new MeshBuilder();
        Geo.cone(co, v(0, 0, 0), v(0, 0, 1), 1f, 1f, 14, Mat.WHITE);
        cone = co.build();
        // rouleau de la voirie (repere : axe du rouleau vertical, manche vers le trottoir)
        MeshBuilder rl = new MeshBuilder();
        rl.cylinder(0.9f, PY0 - 0.8f, PY1 + 0.8f, 14, CONCRETE, CONCRETE, CONCRETE);
        bar(rl, v(0, PY0 - 1.2f, 0.9f), v(0, PY1 + 1.2f, 0.9f), 0.12f, STEEL);
        bar(rl, v(0, (PY0 + PY1) / 2, 0.9f), v(1.5f, SW_T + 1f, 9f), 0.18f, STEEL);
        roller = rl.build();
        MeshBuilder lh = new MeshBuilder();
        lh.bevelBox(-1.2f, -0.5f, -0.9f, 1.2f, 0.4f, 0.9f, 0.2f, STEEL);
        lh.face(Mat.WHITE, v(0, -1, 0), v(-1.0f, -0.52f, -0.7f), v(1.0f, -0.52f, -0.7f), v(1.0f, -0.52f, 0.7f), v(-1.0f, -0.52f, 0.7f));
        lampHead = lh.build();
        MeshBuilder lc = new MeshBuilder();
        Geo.cone(lc, v(0, 0, 0), v(0, 0, -1), 1f, 1f, 16, Mat.WHITE);
        lampCone = lc.build();

        // cadenas et cle
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.ROUND_RECT;
        ps.w = 3.0f;
        ps.h = 3.2f;
        ps.d = 1.2f;
        ps.height = 1.5f;
        ps.halfSpan = 0.85f;
        ps.wire = 0.32f;
        ps.body = STEEL;
        ps.face = LOCKFACE;
        ps.shackle = CAN;
        ps.trim = PLASTIC;
        ps.hole = STEEL;
        lock = new Padlock(ps);
        lock.build(detail);
        Mesh[] k = KeySmith.build(CAN, PLASTIC, 0.5f, (kb, kg) -> {
            // tete de cle : bombe de peinture magenta
            kb.push().translate(0, 1.4f, 0);
            kb.colorRgb(0xFF2BD6);
            kb.cylinder(1.1f, 0, 3.2f, 14, CAN, CAN, CAN);
            kb.color(1f);
            kb.cylinder(0.7f, 3.2f, 4.2f, 12, STEEL, null, STEEL);
            kb.pop();
            kg.push().translate(0, 5.8f, 0);
            kg.sphere(0.55f, 8, 6, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== chronologie
    private static final float DOOR0 = CrateAnim.UNLOCK + 1, DOOR1 = DOOR0 + 6;
    private static final float OUT0 = CrateAnim.UNLOCK + 3;
    private static final float FILL0 = CrateAnim.OPEN + 8, FILL1 = CrateAnim.SPIN + 52;
    private static final float LINE0 = FILL1 + 4, LINE1 = CrateAnim.STOP - 24;
    private static final float SHINE0 = LINE1 + 2, SHINE1 = CrateAnim.STOP - 3;
    private static final float POP = CrateAnim.STOP;
    private static final float BUFF0 = CrateAnim.CLOSE, BUFF1 = CrateAnim.CLOSE + 13;
    private static final float HOME0 = CrateAnim.CLOSE + 2;

    /** Fenetre de travail de la bombe i. */
    private static float[] job(int i) {
        if (i < 4) return new float[]{FILL0 + i * 3, FILL1 - (3 - i) * 2};
        if (i == 4) return new float[]{LINE0, LINE1};
        return new float[]{SHINE0, SHINE1};
    }

    /** Tranches couvertes par la bombe i : [debut, fin[. */
    private static int[] span(int i) {
        return i < 4 ? new int[]{i * NS / 4, (i + 1) * NS / 4} : new int[]{0, NS};
    }

    /** Avancement du travail de la bombe i (0..1). */
    private static float work(CrateState s, int i) {
        if (!s.active) return 0;
        float[] w = job(i);
        return Ease.range(s.t, w[0], w[1]);
    }

    /** Pulverisation en cours (0..1) de la bombe i. */
    private static float spraying(CrateState s, int i) {
        if (!s.active) return 0;
        float[] w = job(i);
        return Ease.smooth(Ease.range(s.t, w[0], w[0] + 2)) * (1 - Ease.smooth(Ease.range(s.t, w[1] - 1, w[1] + 1)));
    }

    /** Point vise sur le mur par la bombe i (zigzag vertical en balayant ses tranches). */
    private static float[] aim(CrateState s, int i) {
        int[] sp = span(i);
        float u = work(s, i);
        float x0 = Ease.lerp(PX0, PX1, sp[0] / (float) NS), x1 = Ease.lerp(PX0, PX1, sp[1] / (float) NS);
        float zig = i < 4 ? 5 : 9;
        float y = (PY0 + PY1) / 2 + (PY1 - PY0) * 0.42f * (float) Math.sin(u * Math.PI * 2 * zig);
        return v(Ease.lerp(x0 - 0.3f, x1 + 0.3f, u), y, WALL_Z);
    }

    /** Couverture (0..1) de la tranche j d'une couche peinte par la bombe i. */
    private static float cover(CrateState s, int i, int j) {
        int[] sp = span(i);
        if (j < sp[0] || j >= sp[1]) return 0;
        float u = work(s, i) * (sp[1] - sp[0]);
        return Ease.clamp01((u - (j - sp[0])) * 1.1f + 0.15f);
    }

    /** Retrait par le rouleau (1 = la tranche j est deja repassee en gris). */
    private static float buffed(CrateState s, int j) {
        if (!s.active) return 0;
        float r = Ease.range(s.t, BUFF0, BUFF1) * (NS + 2) - 1;
        return Ease.clamp01(r - j + 0.5f);
    }

    /** Pose de la bombe i : dans la cage, en vol, au mur, en attente, retour. */
    private static void canPose(CrateState s, int i, Pose p) {
        float[] home = v(CG_X0 + 1.5f + (i % 3) * 2.0f, SW_T + 0.2f + 1.4f, CG_Z0 + 1.4f + (i / 3) * 2.2f);
        if (!s.active) {
            p.translate(home[0], home[1] - 1.4f, home[2]);
            return;
        }
        float t = s.t;
        float[] w = job(i);
        float leave0 = OUT0 + i * 1.5f;
        // attente : alignees au-dessus du trottoir, balancement
        float[] wait = v(-1.5f + i * 3.4f, 21.5f + 0.4f * (float) Math.sin(s.time * 0.1f + i), 7.5f);
        float[] at = aim(s, i);
        float[] paint = v(at[0], at[1], SPRAY_Z);
        float[] pos;
        float tilt = 0, shake = 0;
        float go = Ease.inOutCubic(Ease.range(t, w[0] - 6, w[0]));
        float done = Ease.inOutCubic(Ease.range(t, w[1], w[1] + 6));
        float out = Ease.inOutCubic(Ease.range(t, leave0, leave0 + 9));
        float back = Ease.inOutCubic(Ease.range(t, HOME0 + i, HOME0 + i + 10));
        pos = Geo.lerp(home, wait, out);
        pos[1] += 3f * (float) Math.sin(out * Math.PI) * (1 - back);
        pos = Geo.lerp(pos, paint, go * (1 - done));
        tilt = 70f * go * (1 - done);
        pos = Geo.lerp(pos, home, back);
        tilt *= 1 - back;
        // secousse au decollage et avant de peindre (bille qui claque)
        shake = 12f * (Ease.bell(t, leave0, leave0 + 10) + Ease.bell(t, w[0] - 8, w[0])) * (float) Math.sin(t * 3.1f + i);
        float party = t > POP && t < CrateAnim.CLOSE ? 360f * Ease.inOutCubic(Ease.range(t, POP + i * 2, POP + i * 2 + 14)) : 0;
        p.translate(pos[0], pos[1], pos[2]).rotY(party).rotZ(shake).rotX(-tilt).translate(0, -1.4f, 0);
    }

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float pop = s.t >= POP ? 0.4f * (float) Math.exp(-(s.t - POP) * 0.3f) : 0;
        return Math.max(pop, 0.2f * Seq.stopFlash(s));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        out.solid(p, site, gl);
        out.solidTinted(p, buff, gl, 0.5f, 0.5f, 0.54f);
        // la fresque, tranche par tranche (chaque bombe decouvre ses tranches)
        float neon = s.active ? Ease.smooth(Ease.range(s.t, POP, POP + 4)) * (1 - Ease.smooth(Ease.range(s.t, BUFF0, BUFF1))) : 0;
        float pulse = 0.6f + 0.4f * (float) Math.sin(s.time * 0.25f);
        for (int j = 0; j < NS; j++) {
            float gone = buffed(s, j);
            float f = 0;
            for (int i = 0; i < 4; i++) f = Math.max(f, cover(s, i, j));
            f *= 1 - gone;
            if (f > 0.003f) {
                out.translucent(p, fillSl[j], 0xFFFFFF, f);
                if (neon > 0.01f) out.additive(p, fillSl[j], 0xFFFFFF, 0.35f * neon * pulse * f);
            }
            float l = cover(s, 4, j) * (1 - gone);
            if (l > 0.003f) out.translucent(p, lineSl[j], 0xFFFFFF, l);
            float h = cover(s, 5, j) * (1 - gone);
            if (h > 0.003f) {
                out.translucent(p, shineSl[j], 0xFFFFFF, h);
                if (neon > 0.01f) out.additive(p, shineSl[j], 0xFFFFFF, 0.6f * neon * h);
            }
            if (gone > 0.003f && s.t < CrateAnim.END - 1) out.solidTinted(p, buffSl[j], gl, 0.46f, 0.46f, 0.5f);
        }
        // cage : porte (charniere a gauche), grillage
        float d = s.active ? Ease.outBack(Ease.range(s.t, DOOR0, DOOR1), 1.3f) * (1 - Ease.inOutCubic(Ease.range(s.t, CrateAnim.SLAM - 8, CrateAnim.SLAM))) : 0;
        p.push().translate(CG_X0, SW_T, CG_Z1).rotY(-105f * d);
        out.solid(p, door, gl);
        out.translucent(p, doorMesh, 0xFFFFFF, 1f);
        p.pop();
        out.translucent(p, cageMesh, 0xFFFFFF, 1f);
        // bombes
        for (int i = 0; i < NCAN; i++) {
            int c = CAN_RGB[i];
            p.push();
            canPose(s, i, p);
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, bl = (c & 255) / 255f;
            out.solidTinted(p, canBody, 0.3f, r, g, bl);
            out.solidTinted(p, canCap, 0.3f, Math.min(1, r * 0.7f + 0.3f), Math.min(1, g * 0.7f + 0.3f), Math.min(1, bl * 0.7f + 0.3f));
            p.pop();
        }
        // rouleau de la voirie
        if (s.active && s.t > BUFF0 - 3 && s.t < BUFF1 + 4) {
            float u = Ease.range(s.t, BUFF0, BUFF1);
            float in = Ease.smooth(Ease.range(s.t, BUFF0 - 3, BUFF0)) * (1 - Ease.smooth(Ease.range(s.t, BUFF1, BUFF1 + 4)));
            float x = Ease.lerp(PX0 - 1.5f, PX1 + 1.5f, u);
            p.push().translate(x, 0, WALL_Z + 0.95f + 3f * (1 - in));
            out.solid(p, roller, gl);
            p.pop();
        }
        // reverbere : tete
        p.push().translate(LAMP[0], LAMP[1], LAMP[2]).rotX(20);
        out.solid(p, lampHead, 0.3f + 0.9f * flicker(s));
        p.pop();
    }

    private static float flicker(CrateState s) {
        float f = 0.85f + 0.15f * (float) Math.sin(s.time * 2.7f);
        float blink = (s.time % 97f) < 3 ? 0.3f : 1f;
        return f * blink;
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(CG_X1 - 0.1f, SW_T + CG_H * 0.42f + 0.1f, CG_Z1 + 0.45f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float aw = Seq.awaken(s);
        float fl = flicker(s);
        // lumiere du reverbere sur le mur
        float[] tgt = v(8, 9, WALL_Z);
        float[] d = Geo.sub(tgt, LAMP);
        float len = Geo.len(d);
        float yaw = (float) Math.toDegrees(Math.atan2(d[0], d[2]));
        float pitch = (float) Math.toDegrees(Math.atan2(d[1], Math.hypot(d[0], d[2])));
        p.push().translate(tgt[0], tgt[1], tgt[2]).rotY(yaw).rotX(-pitch).scale(7f, 7f, len);
        out.additive(p, lampCone, 0xFFB050, 0.028f * fl);
        p.pop();
        out.billboard(p, Sprite.FLARE, LAMP[0], LAMP[1] - 0.6f, LAMP[2], 3f * fl, s.time, 0xFFC070, 0.8f * fl);
        out.ground(p, Sprite.HALO, 4, SW_T + 0.05f, 10, 13f, 0, 0xFFA040, 0.18f * fl);
        out.ground(p, Sprite.HALO, 8, SW_T + 0.05f, 10, 18f, 0, glow, 0.06f + 0.14f * aw);
        if (!s.active) return;
        float t = s.t;
        // pulverisation : cone colore de la buse au mur, brume au point d'impact
        for (int i = 0; i < NCAN; i++) {
            float sp = spraying(s, i);
            if (sp < 0.01f) continue;
            float[] at = aim(s, i);
            float[] noz = v(at[0], at[1] + 0.35f, SPRAY_Z - 1.4f);
            float[] dd = Geo.sub(noz, at);
            float ln = Geo.len(dd);
            float yw = (float) Math.toDegrees(Math.atan2(dd[0], dd[2]));
            float pt = (float) Math.toDegrees(Math.atan2(dd[1], Math.hypot(dd[0], dd[2])));
            int c = i == 4 ? 0x6A6A78 : CAN_RGB[i];
            p.push().translate(at[0], at[1], at[2] + 0.1f).rotY(yw).rotX(-pt).scale(1.6f, 1.6f, ln);
            out.additive(p, cone, c, 0.18f * sp);
            p.pop();
            out.billboard(p, Sprite.SMOKE, at[0], at[1], at[2] + 0.4f, 1.8f, s.time * 7 + i * 50, c, 0.3f * sp);
            out.billboard(p, Sprite.HALO, at[0], at[1], at[2] + 0.3f, 1.4f, 0, c, 0.22f * sp);
        }
        // la fresque s'allume : eclair, rayons, halo de neon
        if (t >= POP && t < BUFF1) {
            float e = (float) Math.exp(-(t - POP) * 0.25f);
            float neon = 1 - Ease.smooth(Ease.range(t, BUFF0, BUFF1));
            out.billboard(p, Sprite.FLARE, 8, (PY0 + PY1) / 2, WALL_Z + 1, 6f + 22f * e, s.time, 0xFFFFFF, e);
            out.rays(p, 8, (PY0 + PY1) / 2, WALL_Z + 1.5f, 14, 30f * (0.4f + 0.6f * e), 2.2f, s.time * 1.2f, glow, 0.5f * e);
            for (int k = 0; k < 4; k++) {
                float x = Ease.lerp(PX0, PX1, (k + 0.5f) / 4);
                out.billboard(p, Sprite.HALO, x, (PY0 + PY1) / 2, WALL_Z + 0.6f, 9f, 0, CAN_RGB[k], (0.18f + 0.3f * e) * neon);
            }
        }
        CommonFx.cavity(s, p, out, this, 8, WALL_TOP + 1, (WALL_Z0 + WALL_Z) / 2, 6f);
    }

    @Override
    public float[] cavityPos() {
        // derriere le mur : les particules generiques montent comme la lueur de la ville au-dessus du chaperon
        return new float[]{8, WALL_TOP - 2, WALL_Z0 + 0.8f};
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.06f) {
            fx.particle(FxSink.DOT, LAMP[0] + rnd(r, 3f), LAMP[1] - 1 - r.nextFloat() * 3, LAMP[2] + rnd(r, 3f), rnd(r, 0.03f), -0.01f, rnd(r, 0.03f), 0xFFC070, 0.8f, 40);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == (int) DOOR0) fx.sound(Snd.RATCHET, CG_X1, SW_T + 3, CG_Z1, 0.8f, 0.7f);
        for (int i = 0; i < NCAN; i++) {
            float[] w = job(i);
            if (k == (int) (OUT0 + i * 1.5f)) fx.sound(Snd.CAN_RATTLE, CG_X0 + 2 + i, SW_T + 3, CG_Z0 + 2, 0.5f, 0.9f + 0.05f * i);
            if (k == (int) w[0] - 7) fx.sound(Snd.CAN_RATTLE, 8, 14, SPRAY_Z, 0.6f, 1.0f + 0.04f * i);
            if (k >= w[0] && k < w[1] && (k - (int) w[0]) % 18 == 0) {
                float[] at = aim(s, i);
                fx.sound(Snd.SPRAY, at[0], at[1], SPRAY_Z, 0.55f, 0.95f + 0.05f * i);
            }
            if (k >= w[0] && k < w[1]) {
                float[] at = aim(s, i);
                int c = i == 4 ? 0x303038 : CAN_RGB[i];
                for (int n = 0; n < 2; n++) {
                    fx.particle(FxSink.DOT, at[0] + rnd(r, 0.8f), at[1] + rnd(r, 0.8f), WALL_Z + 0.4f + r.nextFloat() * 1.2f, rnd(r, 0.05f), rnd(r, 0.05f), 0.02f,
                            c, 0.7f + r.nextFloat() * 0.6f, 14);
                }
            }
        }
        if (k == (int) POP) {
            fx.sound(Snd.SCRATCH, 8, 10, WALL_Z + 2, 1.2f, 1.0f);
            fx.sound(Snd.SPLAT, 8, 10, WALL_Z + 2, 1.0f, 0.9f);
            for (int n = 0; n < 90; n++) {
                double th = r.nextDouble() * Math.PI * 2;
                float sp = 0.5f + r.nextFloat() * 1.3f;
                fx.particle(n % 3 == 0 ? FxSink.CONFETTI : FxSink.DROP, Ease.lerp(PX0, PX1, r.nextFloat()), PY0 + r.nextFloat() * (PY1 - PY0), WALL_Z + 1,
                        (float) Math.cos(th) * sp * 0.6f, 0.4f + r.nextFloat() * 1.2f, 0.4f + r.nextFloat() * 0.8f, CAN_RGB[r.nextInt(4)], 1.2f + r.nextFloat(), 40 + r.nextInt(20));
            }
        }
        if (k == (int) BUFF0) fx.sound(Snd.ROLLER, PX0, 10, WALL_Z + 1, 1.0f, 1.0f);
        if (k == (int) BUFF0 + 7) fx.sound(Snd.ROLLER, 8, 10, WALL_Z + 1, 0.9f, 1.1f);
        if (k == CrateAnim.SLAM) fx.sound(Snd.RATCHET, CG_X1, SW_T + 3, CG_Z1, 0.8f, 0.8f);
    }
}
