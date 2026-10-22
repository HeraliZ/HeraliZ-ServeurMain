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
import fr.heraliz.crates.client.render.scene.Sprite;
import fr.heraliz.crates.registry.Snd;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Caisse des emotes : la scene de spectacle (concept valide par le proprietaire). Une estrade ronde laquee noire, bordee
 * d'or et d'un bandeau lumineux, avec une piste de danse en dalles de verre qui s'allument ; au fond, une loge de cabaret
 * (colonnes dorees, ampoules, enseigne "EMOTES", etoile neon) fermee par un rideau de velours rouge, le cadenas sur la
 * jointure ; au-dessus, un portique en treillis d'aluminium qui porte deux lyres motorisees et une boule a facettes.
 * <p>A l'ouverture : roulement de tambour, le cadenas tombe, le rideau s'ouvre, les lyres s'allument et balaient la
 * scene, la boule tourne plus vite, deux canons a confettis tirent ; un danseur holographique sort de la loge et danse
 * pendant la roue. A la fin, le rideau se referme et les lumieres s'eteignent.
 */
public final class Spectacle extends Design {
    private static final Mat VELVET = Mat.MAIN;
    private static final Mat LACQUER = Mat.MAIN2;
    private static final Mat GOLD = Mat.TRIM;
    private static final Mat ALU = Mat.ACCENT;
    private static final Mat SIGN = Mat.EMBLEM;
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat TILE = Mat.DECAL_A;
    private static final Mat GRILLE = Mat.DECAL_B;
    private static final Mat BACKDROP = Mat.INTERIOR;
    private static final Mat MIRROR = Mat.GEM;
    private static final Mat RUBBER = Mat.DARK;
    private static final Mat NEON = Mat.SPECIAL;

    private static final int PINK = 0xFF4FD8, CYAN = 0x3FE0FF, GOLDC = 0xFFC845, HOLO = 0x6FF4FF;
    private static final int[] TILE_RGB = {PINK, CYAN, GOLDC};

    // ------------------------------------------------------------------ cotes (px du modele)
    private static final float STAGE_R = 12.5f, STAGE_Y = 2.2f;
    /** Loge : corps, ouverture du rideau (face avant), fronton. */
    private static final float BX0 = 1.6f, BX1 = 14.4f, BZ0 = 0.6f, BZ1 = 8.8f, BY1 = 16.6f;
    private static final float OX0 = 2.5f, OX1 = 13.5f, OY1 = BY1 - 0.4f, CURTAIN_Z = 9.25f;
    private static final float PED_Y0 = BY1 - 0.2f, PED_Y1 = BY1 + 3.6f, PED_Z1 = 10.5f;
    private static final float STAR_Y = PED_Y1 + 3.9f, STAR_Z = 9.4f;
    /** Portique : tours, poutre, lyres, boule. */
    private static final float TX0 = -3.2f, TX1 = 19.2f, TZ = 3.6f, TRUSS_Y = 32.6f, TH = 0.9f;
    private static final float[] HEAD_X = {1.2f, 14.8f};
    private static final float HEAD_Y = TRUSS_Y - 2.6f;
    private static final float BALL_Y = 29.6f, BALL_Z = 5.2f, BALL_R = 2.5f;
    /** Danseur holographique (debout devant la loge). */
    private static final float DANCER_Z = 13.6f;

    private Mesh site = Mesh.EMPTY, curtain = Mesh.EMPTY, ball = Mesh.EMPTY, head = Mesh.EMPTY, beamCone = Mesh.EMPTY;
    private Mesh star = Mesh.EMPTY, starGlow = Mesh.EMPTY, ledBand = Mesh.EMPTY, lens = Mesh.EMPTY;
    private final Mesh[] tiles = new Mesh[3];
    private final Mesh[] bulbs = new Mesh[2];
    /** Danseur : tete, buste, bras (haut et bas), jambes. Repere : bassin a l'origine. */
    private Mesh dHead = Mesh.EMPTY, dBody = Mesh.EMPTY, dArm = Mesh.EMPTY, dLeg = Mesh.EMPTY;
    private final Pose idlePose = new Pose();

    public Spectacle() {
        super("spectacle", PINK, CYAN);
        keyRgb = GOLDC;
        cavityRgb = PINK;
        cavityAccent = CYAN;
        size = 1.3f;
        ringY = 42;
        ringR = 25;
        revealY = 47;
        riseFromY = STAGE_Y + 7;
        lockFloorY = STAGE_Y;
        runeRadius = 22f;
    }

    // =================================================================== construction
    private static void bar(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 6, m, true, true, 0, false);
    }

    /** Tour en treillis carre (4 membrures, croisillons en zigzag sur chaque face). */
    private static void tower(MeshBuilder b, float x, float z, float y0, float y1) {
        float[][] c = {{x - TH, z - TH}, {x + TH, z - TH}, {x + TH, z + TH}, {x - TH, z + TH}};
        for (float[] q : c) bar(b, v(q[0], y0, q[1]), v(q[0], y1, q[1]), 0.2f, ALU);
        float step = 2.1f;
        int n = (int) ((y1 - y0) / step);
        for (int f = 0; f < 4; f++) {
            float[] a = c[f], d = c[(f + 1) % 4];
            for (int i = 0; i < n; i++) {
                float ya = y0 + i * step, yb = ya + step;
                boolean up = i % 2 == 0;
                bar(b, v(up ? a[0] : d[0], ya, up ? a[1] : d[1]), v(up ? d[0] : a[0], yb, up ? d[1] : a[1]), 0.09f, ALU);
            }
            bar(b, v(a[0], y0 + 0.4f, a[1]), v(d[0], y0 + 0.4f, d[1]), 0.12f, ALU);
        }
        b.bevelBox(x - 1.6f, y0, z - 1.6f, x + 1.6f, y0 + 0.4f, z + 1.6f, 0.12f, RUBBER);
        b.bevelBox(x - 1.2f, y1 - 0.2f, z - 1.2f, x + 1.2f, y1 + 1.9f, z + 1.2f, 0.12f, ALU);
    }

    /** Poutre en treillis horizontale (le long de X), section carree. */
    private static void beamTruss(MeshBuilder b, float x0, float x1, float y, float z) {
        float h = 0.8f;
        float[][] c = {{y - h, z - h}, {y + h, z - h}, {y + h, z + h}, {y - h, z + h}};
        for (float[] q : c) bar(b, v(x0, q[0], q[1]), v(x1, q[0], q[1]), 0.2f, ALU);
        float step = 1.8f;
        int n = (int) ((x1 - x0) / step);
        for (int f = 0; f < 4; f++) {
            float[] a = c[f], d = c[(f + 1) % 4];
            for (int i = 0; i < n; i++) {
                float xa = x0 + i * step, xb = xa + step;
                boolean up = i % 2 == 0;
                bar(b, v(xa, up ? a[0] : d[0], up ? a[1] : d[1]), v(xb, up ? d[0] : a[0], up ? d[1] : a[1]), 0.09f, ALU);
            }
        }
    }

    /** Rideau plisse (une moitie) : de x = 0 (bord exterieur) a x = w, plis le long de Z, du sol (0) a h. */
    private static void pleats(MeshBuilder b, float w, float h, int folds, Mat m) {
        int n = folds * 6;
        float[][] pts = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            pts[i] = v(u * w, 0, 0.32f * (float) Math.sin(u * folds * Math.PI * 2));
        }
        for (int i = 0; i < n; i++) {
            float[] a = pts[i], c = pts[i + 1];
            float dx = c[0] - a[0], dz = c[2] - a[2];
            float l = (float) Math.sqrt(dx * dx + dz * dz);
            float[] fn = v(-dz / l, 0, dx / l);
            // bas legerement ondule (le velours tombe sur la scene)
            float ha = h, hc = h;
            b.face(m, fn, v(a[0], 0, a[2]), v(c[0], 0, c[2]), v(c[0], hc, c[2]), v(a[0], ha, a[2]));
            b.face(m, v(-fn[0], 0, -fn[2]), v(c[0], 0, c[2] - 0.02f), v(a[0], 0, a[2] - 0.02f), v(a[0], ha, a[2] - 0.02f), v(c[0], hc, c[2] - 0.02f));
        }
    }

    private static float[][] starOutline(float r1, float r2) {
        float[][] s = new float[10][];
        for (int k = 0; k < 10; k++) {
            double a = Math.PI / 2 + k * Math.PI / 5;
            float r = k % 2 == 0 ? r1 : r2;
            s[k] = new float[]{(float) Math.cos(a) * r, (float) Math.sin(a) * r};
        }
        return s;
    }

    /** Points des ampoules du cadre de la loge (deux groupes en alternance pour le chenillard). */
    private static List<float[]> bulbPoints() {
        List<float[]> l = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            float y = STAGE_Y + 1.2f + i * 1.3f;
            l.add(v(OX0 - 0.45f, y, PED_Z1 - 0.25f));
            l.add(v(OX1 + 0.45f, y, PED_Z1 - 0.25f));
        }
        return l;
    }

    @Override
    protected void build() {
        int seg = seg(48);
        MeshBuilder b = new MeshBuilder();
        // estrade : socle, corps laque, levre doree, plateau
        b.push().translate(8, 0, 8);
        b.cylinder(STAGE_R + 0.5f, 0, 0.5f, seg, RUBBER, null, RUBBER);
        b.cylinder(STAGE_R, 0.5f, STAGE_Y, seg, LACQUER, null, LACQUER);
        b.push().translate(0, STAGE_Y, 0);
        b.torus(STAGE_R, 0.28f, seg, 6, 0, 360, GOLD);
        b.pop();
        b.push().translate(0, 0.5f, 0);
        b.torus(STAGE_R + 0.05f, 0.18f, seg, 5, 0, 360, GOLD);
        b.pop();
        b.pop();
        // marches devant
        b.bevelBox(3.6f, 0, 19.2f, 12.4f, 1.1f, 22.8f, 0.12f, LACQUER);
        b.box(3.6f, 1.1f, 22.5f, 12.4f, 1.18f, 22.8f, GOLD);
        // dalles de la piste (cadre noir) : la couleur est dans le calque des dalles, anime
        float step = 3.1f, half = 1.42f;
        MeshBuilder[] tb = {new MeshBuilder(), new MeshBuilder(), new MeshBuilder()};
        for (int i = -4; i <= 4; i++) {
            for (int j = -4; j <= 4; j++) {
                float cx = 8 + i * step, cz = 8 + j * step;
                if (Math.hypot(i * step, j * step) > STAGE_R - 2.1f) continue;
                if (cz - half < BZ1 + 0.4f && cx + half > BX0 - 0.6f && cx - half < BX1 + 0.6f) continue;
                b.box(cx - half, STAGE_Y, cz - half, cx + half, STAGE_Y + 0.12f, cz + half, TILE);
                MeshBuilder t = tb[Math.floorMod(i + j * 2, 3)];
                t.face(Mat.WHITE, v(0, 1, 0), v(cx - half + 0.2f, STAGE_Y + 0.14f, cz + half - 0.2f), v(cx + half - 0.2f, STAGE_Y + 0.14f, cz + half - 0.2f),
                        v(cx + half - 0.2f, STAGE_Y + 0.14f, cz - half + 0.2f), v(cx - half + 0.2f, STAGE_Y + 0.14f, cz - half + 0.2f));
            }
        }
        for (int k = 0; k < 3; k++) tiles[k] = tb[k].build();

        // loge : fond, parois, toit (creuse : on voit dedans quand le rideau s'ouvre)
        b.box6(BX0, STAGE_Y, BZ0, BX1, BY1, BZ0 + 0.7f, LACQUER, LACQUER, null, LACQUER, LACQUER, BACKDROP);
        b.box6(BX0, STAGE_Y, BZ0, OX0, BY1, BZ1, LACQUER, BACKDROP, null, LACQUER, LACQUER, LACQUER);
        b.box6(OX1, STAGE_Y, BZ0, BX1, BY1, BZ1, BACKDROP, LACQUER, null, LACQUER, LACQUER, LACQUER);
        b.box6(OX0, OY1, BZ0, OX1, BY1, BZ1, LACQUER, LACQUER, BACKDROP, LACQUER, LACQUER, LACQUER);
        b.box(OX0, STAGE_Y, BZ0 + 0.7f, OX1, STAGE_Y + 0.15f, BZ1, LACQUER);
        // colonnes dorees cannelees de part et d'autre de l'ouverture
        for (float cx : new float[]{OX0 - 0.45f, OX1 + 0.45f}) {
            b.push().translate(cx, 0, PED_Z1 - 0.85f);
            b.cylinder(0.95f, STAGE_Y, STAGE_Y + 0.8f, 16, GOLD, null, GOLD);
            b.cylinder(0.62f, STAGE_Y + 0.8f, PED_Y0, 12, GOLD, null, null);
            b.cylinder(0.9f, PED_Y0 - 0.7f, PED_Y0, 16, GOLD, GOLD, GOLD);
            b.pop();
        }
        // fronton : plaque laquee, cadre dore, enseigne
        b.bevelBox(0.5f, PED_Y0, BZ1 - 0.2f, 15.5f, PED_Y1, PED_Z1, 0.2f, LACQUER);
        b.box(0.3f, PED_Y1 - 0.1f, BZ1 - 0.3f, 15.7f, PED_Y1 + 0.35f, PED_Z1 + 0.2f, GOLD);
        b.box(0.3f, PED_Y0 - 0.3f, BZ1 - 0.3f, 15.7f, PED_Y0 + 0.05f, PED_Z1 + 0.2f, GOLD);
        b.decalZ(SIGN, 8, (PED_Y0 + PED_Y1) / 2 + 0.05f, PED_Z1 + 0.01f, 14.4f, 3.3f);
        // lambrequin : bandeau de velours festonne, frange doree
        b.box(OX0, OY1 - 2.0f, CURTAIN_Z + 0.15f, OX1, OY1, CURTAIN_Z + 0.55f, VELVET);
        for (int i = 0; i < 6; i++) {
            float cx = OX0 + (OX1 - OX0) * (i + 0.5f) / 6;
            float[][] arc = new float[9][];
            for (int k = 0; k <= 8; k++) {
                double a = Math.PI + k * Math.PI / 8;
                arc[k] = new float[]{cx + (float) Math.cos(a) * 0.93f, OY1 - 2.0f + (float) Math.sin(a) * 0.75f};
            }
            b.push().translate(0, 0, CURTAIN_Z + 0.15f);
            b.prism(arc, 0.4f, VELVET, VELVET);
            b.pop();
        }
        b.box(OX0, OY1 - 2.05f, CURTAIN_Z + 0.56f, OX1, OY1 - 1.85f, CURTAIN_Z + 0.62f, GOLD);
        // socles des ampoules
        for (float[] q : bulbPoints()) b.box(q[0] - 0.3f, q[1] - 0.3f, q[2] - 0.15f, q[0] + 0.3f, q[1] + 0.3f, q[2] + 0.05f, GOLD);
        // pied de l'etoile
        b.box(7.6f, PED_Y1 + 0.3f, STAR_Z - 0.3f, 8.4f, STAR_Y - 2.6f, STAR_Z + 0.3f, GOLD);
        // retours de scene (enceintes en biseau aux coins avant)
        for (int sd = -1; sd <= 1; sd += 2) {
            b.push().translate(8 + sd * 7.6f, STAGE_Y, 16.2f).rotateY(sd * 28f);
            b.bevelBox(-1.7f, 0, -1.1f, 1.7f, 1.0f, 1.1f, 0.12f, RUBBER);
            b.push().translate(0, 1.0f, 1.1f).rotateX(-32f);
            b.box(-1.7f, -0.0f, -2.3f, 1.7f, 0.55f, 0f, RUBBER);
            b.face(GRILLE, v(0, 0.53f, 0.85f), v(-1.5f, 0.56f, -2.1f), v(1.5f, 0.56f, -2.1f), v(1.5f, 0.56f, -0.2f), v(-1.5f, 0.56f, -0.2f));
            b.pop();
            b.pop();
        }
        // canons a confettis (tubes dores sur pied, aux coins arriere)
        for (int sd = -1; sd <= 1; sd += 2) {
            b.push().translate(8 + sd * 9.6f, STAGE_Y, 11.5f).rotateZ(-sd * 22f).rotateX(-12f);
            b.cylinder(0.7f, 0, 0.4f, 10, RUBBER, RUBBER, RUBBER);
            b.cylinder(0.55f, 0.4f, 3.6f, 12, GOLD, null, RUBBER);
            b.pop();
        }
        // portique : tours, poutre, boitier de la boule
        tower(b, TX0, TZ, STAGE_Y, TRUSS_Y - 0.8f);
        tower(b, TX1, TZ, STAGE_Y, TRUSS_Y - 0.8f);
        beamTruss(b, TX0, TX1, TRUSS_Y, TZ);
        bar(b, v(8, TRUSS_Y - 0.8f, TZ), v(8, BALL_Y + BALL_R + 0.2f, BALL_Z), 0.07f, ALU);
        b.box(7.4f, TRUSS_Y - 1.2f, TZ - 0.4f, 8.6f, TRUSS_Y - 0.8f, TZ + 0.4f, RUBBER);
        // lyres : crochets et etriers
        for (float hx : HEAD_X) {
            b.box(hx - 0.5f, TRUSS_Y - 1.4f, TZ - 0.5f, hx + 0.5f, TRUSS_Y - 0.8f, TZ + 0.5f, RUBBER);
            b.box(hx - 1.4f, HEAD_Y + 0.9f, TZ - 0.35f, hx + 1.4f, TRUSS_Y - 1.4f, TZ + 0.35f, RUBBER);
            b.box(hx - 1.6f, HEAD_Y - 1.3f, TZ - 0.3f, hx - 1.25f, HEAD_Y + 1.2f, TZ + 0.3f, RUBBER);
            b.box(hx + 1.25f, HEAD_Y - 1.3f, TZ - 0.3f, hx + 1.6f, HEAD_Y + 1.2f, TZ + 0.3f, RUBBER);
        }
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        site = b.build();

        // bandeau lumineux de l'estrade (anime)
        MeshBuilder lb = new MeshBuilder();
        lb.push().translate(8, 0, 8);
        lb.cylinder(STAGE_R + 0.04f, 1.05f, 1.55f, seg, Mat.WHITE, null, null);
        lb.pop();
        ledBand = lb.build();

        // rideau (une moitie, repere : bord exterieur en x = 0, au sol de la scene)
        MeshBuilder cb = new MeshBuilder();
        float cw = (OX1 - OX0) / 2 + 0.25f;
        pleats(cb, cw, OY1 - STAGE_Y - 0.1f, 4, VELVET);
        cb.box(cw - 0.25f, 0, -0.45f, cw, OY1 - STAGE_Y - 0.1f, 0.45f, VELVET);
        cb.box(0, 0, -0.5f, cw, 0.25f, 0.5f, GOLD);
        curtain = cb.build();

        // ampoules (deux groupes)
        MeshBuilder[] bb = {new MeshBuilder(), new MeshBuilder()};
        List<float[]> bp = bulbPoints();
        for (int i = 0; i < bp.size(); i++) {
            float[] q = bp.get(i);
            MeshBuilder t = bb[(i / 2) % 2];
            t.push().translate(q[0], q[1], q[2] + 0.2f);
            t.sphere(0.27f, 8, 6, NEON);
            t.pop();
        }
        for (int k = 0; k < 2; k++) bulbs[k] = bb[k].build();

        // etoile neon
        MeshBuilder sb = new MeshBuilder();
        sb.push().translate(8, STAR_Y, STAR_Z - 0.35f);
        sb.prism(starOutline(3.4f, 1.45f), 0.7f, NEON, GOLD);
        sb.pop();
        star = sb.build();
        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(8, STAR_Y, STAR_Z + 0.4f);
        sg.prism(starOutline(4.0f, 1.8f), 0.05f, Mat.WHITE, Mat.WHITE);
        sg.pop();
        starGlow = sg.build();

        // boule a facettes (repere : centre)
        MeshBuilder mb = new MeshBuilder();
        mb.sphere(BALL_R, seg(18), seg(12), MIRROR.withWorld(4));
        ball = mb.build();

        // tete de lyre (repere : pivot, faisceau vers -Y)
        MeshBuilder hb = new MeshBuilder();
        hb.bevelBox(-1.2f, -1.0f, -1.0f, 1.2f, 0.9f, 1.0f, 0.3f, RUBBER);
        hb.cylinder(0.95f, -2.1f, -1.0f, 14, RUBBER, null, null);
        hb.push().translate(0, -2.1f, 0);
        hb.torus(0.95f, 0.14f, 14, 4, 0, 360, ALU);
        hb.pop();
        head = hb.build();
        MeshBuilder lnb = new MeshBuilder();
        lnb.disc(0.82f, -2.12f, 14, NEON, false);
        lens = lnb.build();
        MeshBuilder bc = new MeshBuilder();
        bc.tube(new float[][]{v(0, -2.2f, 0), v(0, -31f, 0)}, new float[]{0.8f, 5.5f}, 18, Mat.WHITE, false, false, 0, false);
        beamCone = bc.build();

        // danseur holographique (bassin a l'origine, 0,9 bloc de haut environ)
        MeshBuilder d = new MeshBuilder();
        d.bevelBox(-0.9f, 4.6f, -0.55f, 0.9f, 6.4f, 0.55f, 0.25f, Mat.WHITE);
        dHead = d.build();
        d = new MeshBuilder();
        d.bevelBox(-1.15f, 0, -0.6f, 1.15f, 4.3f, 0.6f, 0.2f, Mat.WHITE);
        dBody = d.build();
        d = new MeshBuilder();
        d.bevelBox(-0.45f, -3.9f, -0.45f, 0.45f, 0.2f, 0.45f, 0.15f, Mat.WHITE);
        dArm = d.build();
        d = new MeshBuilder();
        d.bevelBox(-0.52f, -4.4f, -0.52f, 0.52f, 0, 0.52f, 0.15f, Mat.WHITE);
        dLeg = d.build();

        // cadenas (etoile doree sur laque framboise) et cle (une etoile)
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.ROUND_RECT;
        ps.w = 3.2f;
        ps.h = 2.9f;
        ps.d = 1.2f;
        ps.corner = 0.8f;
        ps.bevel = 0.25f;
        ps.height = 1.6f;
        ps.halfSpan = 1.0f;
        ps.wire = 0.34f;
        ps.body = GOLD;
        ps.face = LOCKFACE;
        ps.shackle = ALU;
        ps.trim = GOLD;
        ps.hole = RUBBER;
        lock = new Padlock(ps);
        lock.build(detail);
        Mesh[] k = KeySmith.build(GOLD, NEON, 0.5f, (kb, kg) -> {
            kb.push().translate(0, 2.9f, -0.35f);
            kb.prism(starOutline(2.4f, 1.0f), 0.7f, GOLD, GOLD);
            kb.pop();
            kg.push().translate(0, 2.9f, -0.45f);
            kg.prism(starOutline(2.8f, 1.2f), 0.9f, Mat.WHITE, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== chronologie
    private static final float CURTAIN0 = CrateAnim.OPEN, CURTAIN1 = CrateAnim.OPEN + 14;
    private static final float LIGHTS0 = CrateAnim.OPEN - 2, CANNON = CrateAnim.OPEN + 5;
    private static final float DANCE0 = CrateAnim.OPEN + 10, DANCE1 = CrateAnim.CLOSE;

    /** Ouverture du rideau (0 ferme, 1 ouvert), refermeture a la fin. */
    private static float curtainOpen(CrateState s) {
        if (!s.active) return 0;
        return Ease.inOutCubic(Ease.range(s.t, CURTAIN0, CURTAIN1)) * (1 - Ease.inOutCubic(Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.CLOSE + 14)));
    }

    /** Lumieres du spectacle (lyres, chenillard rapide). */
    private static float show(CrateState s) {
        if (!s.active) return 0;
        return Ease.smooth(Ease.range(s.t, LIGHTS0, LIGHTS0 + 6)) * (1 - Ease.smooth(Ease.range(s.t, CrateAnim.CLOSE + 4, CrateAnim.END)));
    }

    /** Presence du danseur (0..1). */
    private static float dancer(CrateState s) {
        if (!s.active) return 0;
        return Ease.smooth(Ease.range(s.t, DANCE0, DANCE0 + 8)) * (1 - Ease.smooth(Ease.range(s.t, DANCE1 - 6, DANCE1 + 4)));
    }

    // =================================================================== image
    @Override
    public boolean animatedAtRest() {
        return true;
    }

    @Override
    public void idle(CrateState s, DrawList out) {
        ensureBuilt();
        Pose p = idlePose.reset();
        scaleFrame(p);
        lights(s, p, out, glowLevel(s));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        out.solid(p, site, gl);
        float o = curtainOpen(s);
        float gather = 1 - 0.78f * o;
        float lift = 0.6f * (float) Math.sin(Math.PI * o);
        // moitie gauche (bord exterieur en OX0), moitie droite en miroir
        p.push().translate(OX0, STAGE_Y + 0.05f, CURTAIN_Z).scale(gather, 1, 1).translate(0, lift * 0.1f, 0);
        out.solid(p, curtain, gl);
        p.pop();
        p.push().translate(OX1, STAGE_Y + 0.05f, CURTAIN_Z).scale(-gather, 1, 1).translate(0, lift * 0.1f, 0);
        out.solid(p, curtain, gl);
        p.pop();
        // tetes de lyres : au repos tournees vers la scene, elles balaient pendant le spectacle
        float sh = show(s);
        for (int i = 0; i < 2; i++) {
            p.push();
            headPose(s, i, p);
            out.solid(p, head, gl);
            p.pop();
        }
        if (!s.frozen) lights(s, p, out, gl);
    }

    /** Pose d'une tete de lyre (pivot, faisceau vers -Y) : elle vise un point de la scene qui se promene pendant le spectacle. */
    private static void headPose(CrateState s, int i, Pose p) {
        float sh = show(s);
        float side = i == 0 ? 1 : -1;
        float tt = s.time * 0.07f + i * 2.1f;
        float gx = 8 - side * 2.5f + sh * 6.5f * (float) Math.sin(tt), gz = 14.5f + sh * 4.5f * (float) Math.cos(tt * 1.3f);
        float dx = gx - HEAD_X[i], dy = STAGE_Y - HEAD_Y, dz = gz - TZ;
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float tilt = (float) Math.toDegrees(Math.acos(-dy / Math.sqrt(dx * dx + dy * dy + dz * dz)));
        p.translate(HEAD_X[i], HEAD_Y, TZ).rotY(yaw).rotX(-tilt);
    }

    /** Tout ce qui s'anime meme au repos : dalles, bandeau, ampoules, etoile, boule, lentilles. */
    private void lights(CrateState s, Pose p, DrawList out, float gl) {
        float sh = show(s);
        float aw = Seq.awaken(s);
        float rate = 0.05f + 0.12f * sh;
        // dalles : chaque groupe s'allume a son tour, plus vite pendant le spectacle
        for (int k = 0; k < 3; k++) {
            float ph = s.time * rate * 2 - k * 2.094f;
            float on = 0.5f + 0.5f * (float) Math.sin(ph);
            out.additive(p, tiles[k], TILE_RGB[k], (0.22f + 0.7f * on * on) * (0.8f + 0.4f * sh));
        }
        // bandeau de l'estrade : couleur qui glisse du rose au bleu
        int band = Ease.mix(PINK, CYAN, 0.5f + 0.5f * (float) Math.sin(s.time * 0.04f));
        out.additive(p, ledBand, band, 0.75f + 0.25f * sh);
        // ampoules en chenillard
        int beat = (int) Math.floor(s.time * (0.12f + 0.25f * sh));
        out.additive(p, bulbs[beat % 2], 0xFFE6A8, 1f);
        out.additive(p, bulbs[(beat + 1) % 2], 0xFFE6A8, 0.25f);
        // etoile : neon qui respire
        out.solid(p, star, gl);
        out.additive(p, starGlow, PINK, 0.35f + 0.15f * (float) Math.sin(s.time * 0.1f) + 0.35f * sh);
        // boule a facettes
        p.push().translate(8, BALL_Y, BALL_Z).rotY(s.time * (1.2f + 4f * sh));
        out.solid(p, ball, gl * (0.9f + 0.3f * sh));
        p.pop();
        // lentilles des lyres
        for (int i = 0; i < 2; i++) {
            p.push();
            headPose(s, i, p);
            out.additive(p, lens, i == 0 ? PINK : CYAN, 0.25f + 0.75f * sh + 0.15f * aw);
            p.pop();
        }
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(8, STAGE_Y + 7.2f, CURTAIN_Z + 0.75f);
    }

    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float v = s.t >= CANNON ? 0.3f * (float) Math.exp(-(s.t - CANNON) * 0.35f) : 0;
        return Math.max(v, 0.25f * Seq.stopFlash(s));
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float sh = show(s);
        float aw = Seq.awaken(s);
        // lueurs douces au repos : etoile, piste, boule
        out.billboard(p, Sprite.HALO, 8, STAR_Y, STAR_Z + 0.6f, 6.5f, 0, PINK, 0.18f + 0.12f * aw + 0.2f * sh);
        out.ground(p, Sprite.HALO, 8, STAGE_Y + 0.2f, 13f, 12f, 0, Ease.mix(PINK, CYAN, 0.5f + 0.5f * (float) Math.sin(s.time * 0.04f)), 0.1f + 0.1f * aw);
        out.billboard(p, Sprite.HALO, 8, BALL_Y, BALL_Z, 4.5f, 0, 0xFFFFFF, 0.1f + 0.25f * sh);
        // eclats de la boule
        for (int k = 0; k < 7; k++) {
            double a = s.time * (0.02f + 0.07f * sh) + k * 0.9;
            float y = BALL_Y + (float) Math.sin(k * 2.1) * 1.8f;
            float r = (float) Math.sqrt(Math.max(0, BALL_R * BALL_R - (y - BALL_Y) * (y - BALL_Y))) + 0.05f;
            float x = 8 + (float) Math.cos(a) * r, z = BALL_Z + (float) Math.sin(a) * r;
            float front = Math.max(0, (float) Math.sin(a));
            out.billboard(p, Sprite.GLINT, x, y, z, 0.9f + 0.8f * sh, s.time * 4 + k * 40, 0xFFFFFF, front * (0.35f + 0.6f * sh));
        }
        if (sh > 0.01f) {
            // faisceaux des lyres et taches au sol
            for (int i = 0; i < 2; i++) {
                Pose hp = new Pose();
                headPose(s, i, hp);
                p.push().mul(hp.m());
                out.additive(p, beamCone, i == 0 ? PINK : CYAN, 0.12f * sh);
                p.pop();
                float[] a = hp.apply(0, -2.2f, 0), b = hp.apply(0, -31f, 0);
                float k = (STAGE_Y + 0.3f - a[1]) / (b[1] - a[1]);
                if (k > 0 && k < 1) {
                    float gx = a[0] + (b[0] - a[0]) * k, gz = a[2] + (b[2] - a[2]) * k;
                    out.ground(p, Sprite.HALO, gx, STAGE_Y + 0.25f, gz, 4.5f, 0, i == 0 ? PINK : CYAN, 0.45f * sh);
                }
                out.billboard(p, Sprite.FLARE, a[0], a[1], a[2], 2.6f, s.time * 2, i == 0 ? PINK : CYAN, 0.8f * sh);
            }
            // reflets de la boule balayes sur la scene
            for (int k = 0; k < 10; k++) {
                double a = s.time * 0.05f + k * 0.63;
                float rr = 4f + (k % 3) * 3f;
                out.ground(p, Sprite.HALO, 8 + (float) Math.cos(a) * rr, STAGE_Y + 0.3f, 10 + (float) Math.sin(a) * rr * 0.8f, 0.9f, 0, 0xFFFFFF, 0.5f * sh);
            }
        }
        // danseur holographique
        float dn = dancer(s);
        if (dn > 0.01f) {
            drawDancer(s, p, out, dn);
            out.ground(p, Sprite.RING, 8, STAGE_Y + 0.3f, DANCER_Z, 4.2f, s.time * 3, HOLO, 0.7f * dn);
            out.ground(p, Sprite.HALO, 8, STAGE_Y + 0.25f, DANCER_Z, 6f, 0, HOLO, 0.45f * dn);
            out.shaft(p, 8, STAGE_Y, DANCER_Z, 2.6f, 12f, s.time * 1.5f, 2, HOLO, 0.12f * dn);
        }
        float o = curtainOpen(s);
        if (o > 0.01f) CommonFx.cavity(s, p, out, this, 8, STAGE_Y + 1, 5, 5.5f, PINK);
    }

    /** Danseur fait de lumiere : il entre en scene par le bas, puis enchaine des pas de danse sur le rythme. */
    private void drawDancer(CrateState s, Pose p, DrawList out, float dn) {
        float t = s.t - DANCE0;
        float beat = t * 0.32f;
        float bob = 0.35f * Math.abs((float) Math.sin(beat));
        float sway = 14f * (float) Math.sin(beat * 0.5f);
        float h = 4.4f;
        // sort du sol en se reconstituant
        float rise = Ease.outCubic(Ease.range(t, 0, 8));
        float flick = 0.85f + 0.15f * (float) Math.sin(s.time * 2.7f);
        float a = 0.55f * dn * flick;
        p.push().translate(8, STAGE_Y + h * rise + bob - h, DANCER_Z).scale(0.95f).rotY(sway);
        p.push().translate(0, h, 0).rotZ(4f * (float) Math.sin(beat));
        out.additive(p, dBody, HOLO, a);
        p.push().rotY(10f * (float) Math.sin(beat * 0.5f + 1));
        out.additive(p, dHead, HOLO, a * 1.1f);
        p.pop();
        // bras : vagues en alternance (haut au-dessus de la tete, bas sur la hanche)
        for (int sd = -1; sd <= 1; sd += 2) {
            float sw = (float) Math.sin(beat + (sd > 0 ? 0 : Math.PI));
            p.push().translate(sd * 1.6f, 4.0f, 0).rotZ(sd * (35f + 95f * (0.5f + 0.5f * sw))).rotX(-20f * sw);
            out.additive(p, dArm, HOLO, a);
            p.pop();
        }
        p.pop();
        // jambes : pas chasses
        for (int sd = -1; sd <= 1; sd += 2) {
            float st = (float) Math.sin(beat + (sd > 0 ? 0 : Math.PI));
            p.push().translate(sd * 0.62f, h, 0).rotX(28f * st).rotZ(sd * 6f);
            out.additive(p, dLeg, HOLO, a);
            p.pop();
        }
        p.pop();
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, STAGE_Y + 3, 5};
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.03f) {
            fx.particle(FxSink.DOT, 8 + rnd(r, 9f), STAGE_Y + 0.5f, 12 + rnd(r, 5f), 0, 0.04f, 0, r.nextBoolean() ? PINK : CYAN, 0.7f, 30);
        }
        if (r.nextFloat() < 0.015f) {
            fx.particle(FxSink.DOT, 8 + rnd(r, 1.5f), STAR_Y + rnd(r, 1.5f), STAR_Z + 0.6f, 0, 0.02f, 0, GOLDC, 0.6f, 24);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == CrateAnim.KEY_TURN) fx.sound(Snd.DRUMROLL, 8, 8, 12, 0.8f, 1f);
        if (k == (int) CURTAIN0) fx.sound(Snd.CURTAIN, 8, 9, CURTAIN_Z, 0.9f, 1f);
        if (k == (int) CURTAIN0 + 2) fx.sound(Snd.FANFARE, 8, 14, 10, 0.8f, 1f);
        if (k == (int) CANNON) {
            for (int sd = -1; sd <= 1; sd += 2) {
                float cx = 8 + sd * 9.6f, cy = STAGE_Y + 3.8f, cz = 11.5f;
                fx.sound(Snd.CANNON, cx, cy, cz, 0.7f, 1.1f);
                for (int i = 0; i < 45; i++) {
                    float sp = 0.5f + r.nextFloat() * 0.6f;
                    fx.particle(FxSink.CONFETTI, cx, cy, cz, -sd * (0.25f + r.nextFloat() * 0.45f) * sp, (1.0f + r.nextFloat() * 0.9f) * sp,
                            (0.15f + r.nextFloat() * 0.35f) * sp, new int[]{PINK, CYAN, GOLDC, 0xFFFFFF}[i % 4], 1.2f, 50 + r.nextInt(30));
                }
            }
        }
        if (k == (int) DANCE0) fx.sound(Snd.HOLO, 8, STAGE_Y + 3, DANCER_Z, 0.7f, 1.2f);
        if (k == CrateAnim.REVEAL) fx.sound(Snd.CHEER, 8, 10, 14, 0.8f, 1f);
        if (k > CrateAnim.SPIN && k < CrateAnim.STOP && k % 9 == 0) {
            fx.particle(FxSink.CONFETTI, 8 + rnd(r, 8f), TRUSS_Y - 2, 8 + rnd(r, 6f), 0, -0.05f, 0, new int[]{PINK, CYAN, GOLDC}[r.nextInt(3)], 1.0f, 60);
        }
        if (k == CrateAnim.CLOSE) fx.sound(Snd.CURTAIN, 8, 9, CURTAIN_Z, 0.7f, 0.9f);
        if (k == CrateAnim.CLOSE + 12) fx.sound(Snd.CLOSE, 8, 9, CURTAIN_Z, 0.6f, 1.2f);
    }
}
