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
 * Chevalier Sans Visage : du champ de ruines surgit la main colossale du chevalier, en fer vieilli comme de la
 * pierre, la paume tournee vers le ciel. Ses doigts de pierre nue sont refermes sur une ame de feu captive (gemme
 * rouge). A ses pieds gisent son heaume sans visage et son epee plantee entre deux colonnes brisees. A l'ouverture la
 * main se deplie phalange par phalange, l'ame de feu s'eleve, l'epee s'arrache du sol et des epees de flammes
 * orange tournoient autour de la caisse.
 */
public final class ChevalierSansVisage extends Design {
    private static final Mat STONE = Mat.MAIN;
    private static final Mat PLATE = Mat.MAIN2;
    /** Doigts : granit taille sans joints. */
    private static final Mat FINGER_STONE = Mat.SPECIAL;
    private static final Mat GOLD = Mat.ACCENT.sub(0.5f, 0, 1, 1);
    private static final Mat LOCKF = Mat.LOCK;
    private static final Mat LEATHER = Mat.DECAL_B.withWorld(8);
    private static final Mat SOUL = Mat.INTERIOR;
    private static final Mat CRYSTAL = Mat.GEM;
    private static final Mat STEEL = Mat.DARK;
    /** Feu de l'ame captive, flammes vives, epees de feu en orbite. */
    private static final int FIRE = 0xFF2A0C, FIRE_HOT = 0xFF9A3A, SWORD_FIRE = 0xFF8A1E;

    /** Avant-bras, poignet, paume. */
    private static final float WRIST_Y = 10.2f, PALM_Y = 14.3f;
    private static final float HANG_Y = 12.4f, HANG_Z = 8 + 5.7f;
    /** Ame captive au creux de la main. */
    private static final float ORB_Y = 16.0f, ORB_Z = 8.2f, ORB_R = 1.6f;
    /** La main entiere est agrandie autour du poignet : un gantelet de geant. */
    private static final float HS = 1.4f;

    /** Doigts : position x de la base, echelle ; phalanges (longueurs de base) ; angles fermes / ouverts. */
    private static final float[] FX = {-2.85f, -0.95f, 0.95f, 2.85f};
    private static final float[] FS = {0.92f, 1.05f, 1.0f, 0.86f};
    private static final float[] PH = {4.8f, 3.8f, 3.0f};
    private static final float[] CLOSED = {10f, 75f, 70f};
    private static final float[] OPENED = {-14f, 6f, 4f};
    private static final float FINGER_Z = 4.6f, FINGER_Y = 14.0f;
    /** Pouce : base, phalanges, angles (autour de Z). */
    private static final float[] THUMB = {12.3f, 13.6f, 10.9f};
    private static final float[] TPH = {3.2f, 2.6f};
    private static final float[] T_CLOSED = {50f, 45f};
    private static final float[] T_OPENED = {-15f, -5f};
    /** Epee plantee. */
    private static final float SWORD_X = 20.5f, SWORD_Z = 12.5f, SWORD_Y = 12.0f;

    private final Parts.RockSlab floor = new Parts.RockSlab();
    private Mesh base = Mesh.EMPTY, orb = Mesh.EMPTY, orbGlow = Mesh.EMPTY, sword = Mesh.EMPTY, swordGlow = Mesh.EMPTY, ghost = Mesh.EMPTY;
    private final Mesh[][] fingers = new Mesh[4][3];
    private final Mesh[] thumb = new Mesh[2];

    public ChevalierSansVisage() {
        super("chevalier_sans_visage", 0x9FD0FF, 0xE0F0FF);
        ringY = 40;
        ringR = 22;
        revealY = 48;
        riseFromY = 18f;
        lockFloorY = 2.6f;
        floor.radius = 15.6f;
        floor.hCenter = 2.8f;
        floor.hEdge = 1.6f;
        floor.bump = 0.6f;
        floor.seed = 88;
        keyRgb = FIRE_HOT;
        cavityRgb = FIRE;
        cavityAccent = FIRE_HOT;
    }

    /**
     * Phalange de pierre nue le long de +Y (origine a l'articulation) : tronc taille, plus epais vers l'exterieur du
     * poing (z), noeud de pierre a l'articulation ; tip : bout du doigt arrondi.
     */
    /**
     * Phalange de gantelet : lame d'acier ovale evasee a la base (elle recouvre l'articulation precedente), arete sur le
     * dos (-Z), liseré d'or a la jointure ; la derniere phalange se termine en pointe griffue.
     */
    private void phalanx(MeshBuilder b, float L, float r, boolean tip, int seed) {
        float[][] prof = tip
                ? new float[][]{{0.05f, -0.3f}, {r * 0.95f, -0.25f}, {r * 1.14f, 0.12f}, {r * 1.02f, 0.55f}, {r * 0.92f, L * 0.55f},
                {r * 0.72f, L - 0.35f}, {r * 0.36f, L + 0.35f}, {0.04f, L + 0.95f}}
                : new float[][]{{0.05f, -0.3f}, {r * 0.95f, -0.25f}, {r * 1.16f, 0.12f}, {r * 1.03f, 0.55f}, {r * 0.95f, L - 0.55f},
                {r * 1.02f, L - 0.2f}, {r * 0.8f, L + 0.12f}, {0.05f, L + 0.18f}};
        b.lathe(prof, seg(14), 0, 360, PLATE, 1.12f, 0.86f, 0, 0, 0, false);
        // arete du dos
        float top = tip ? L * 0.75f : L - 0.35f;
        b.tube(new float[][]{v(0, 0.2f, -r * 0.93f), v(0, top * 0.5f, -r * 0.9f), v(0, top, -r * (tip ? 0.6f : 0.86f))},
                new float[]{0.2f * r, 0.17f * r, 0.08f * r}, 5, PLATE, true, true, 0, false);
        // liseré d'or a la jointure
        b.push().translate(0, 0.12f, 0).scale(1.12f, 1, 0.86f);
        b.torus(r * 1.13f, 0.1f, seg(14), 4, 0, 360, GOLD);
        b.pop();
        if (!tip) {
            // rivets de la jointure
            for (int sd = -1; sd <= 1; sd += 2) {
                b.push().translate(sd * r * 1.08f, L * 0.5f, 0).rotateZ(sd * -90);
                b.rivet(0.16f, 0.12f, GOLD);
                b.pop();
            }
        }
    }

    private static void handFrame(MeshBuilder b) {
        b.translate(8, WRIST_Y, 8).scale(HS).translate(-8, -WRIST_Y, -8);
    }

    private static void handFrame(Pose p) {
        p.translate(8, WRIST_Y, 8).scale(HS).translate(-8, -WRIST_Y, -8);
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        floor.build(b, STONE, seg(40), seg(8), 4f);
        // avant-bras : canon d'avant-bras en fer-pierre, bandes rivetees
        b.push().translate(8, 0, 8);
        float[][] arm = {{4.6f, 0.5f}, {4.4f, 3.0f}, {4.0f, 6.5f}, {3.8f, WRIST_Y - 0.6f}, {3.9f, WRIST_Y + 0.8f}};
        b.lathe(arm, seg(28), 0, 360, PLATE, 1, 0.92f, 0.04f, 0.4f, 6, false);
        for (float y : new float[]{3.2f, 6.4f}) {
            b.push().translate(0, y, 0).scale(1, 1, 0.92f);
            b.torus(y < 4 ? 4.5f : 4.15f, 0.38f, seg(28), 4, 0, 360, STEEL);
            b.pop();
        }
        for (int k = 0; k < 8; k++) {
            b.push().rotateY(k * 45f + 22.5f).translate(4.65f, 3.2f, 0).rotateZ(-90);
            b.rivet(0.36f, 0.3f, GOLD);
            b.pop();
        }
        // manchette evasee du gantelet
        float[][] cuff = {{4.4f, WRIST_Y - 1.2f}, {5.1f, WRIST_Y - 0.9f}, {5.3f, WRIST_Y + 1.2f}, {4.7f, WRIST_Y + 1.8f}};
        b.lathe(cuff, seg(28), 0, 360, PLATE, 1, 0.92f, 0.03f, 0.5f, 8, false);
        b.push().translate(0, WRIST_Y + 1.35f, 0).scale(1, 1, 0.92f);
        b.torus(5.2f, 0.32f, seg(28), 4, 0, 360, GOLD);
        b.pop();
        b.push().translate(0, WRIST_Y - 0.95f, 0).scale(1, 1, 0.92f);
        b.torus(5.05f, 0.3f, seg(28), 4, 0, 360, GOLD);
        b.pop();
        b.pop();
        // paume (coupe de fer), sceau d'ame au centre
        b.push();
        handFrame(b);
        b.push().translate(8, 0, 8);
        float[][] palm = {{3.2f, WRIST_Y + 1.2f}, {4.5f, 12.6f}, {4.8f, 13.4f}, {4.6f, PALM_Y - 0.2f}, {4.2f, PALM_Y}, {0.05f, PALM_Y - 0.5f}};
        b.lathe(palm, seg(24), 0, 360, PLATE, 1.05f, 0.95f, 0.02f, 0.5f, 9, false);
        b.push().translate(0, 13.0f, 0).scale(1.05f, 1, 0.95f);
        b.torus(4.75f, 0.3f, seg(24), 4, 0, 360, GOLD);
        b.pop();
        b.pop();
        b.push().translate(8, PALM_Y + 0.02f, 8.2f);
        b.disc(2.4f, 0, seg(20), SOUL, true);
        b.pop();
        b.pop();
        // anneau du cadenas a l'avant de la manchette
        b.push().translate(8, HANG_Y + 0.5f, 8 + 5.0f);
        b.slab(MeshBuilder.roundRect(2.8f, 2.2f, 0.7f, 3), 0.8f, 0.18f, true, GOLD, GOLD, GOLD);
        b.pop();
        b.push().translate(8, HANG_Y, HANG_Z).rotateZ(90);
        b.torus(0.95f, 0.28f, seg(12), 5, 0, 360, STEEL);
        b.pop();

        // gravats autour du bras
        Random rr = new Random(7);
        for (int k = 0; k < 13; k++) {
            float az = k * (360f / 13) + rr.nextFloat() * 14;
            if (Math.abs(((az - 90 + 540) % 360) - 180) < 14) continue;
            float[] q = Geo.polar(8, 8, az, 5.6f + rr.nextFloat() * 2.4f, 0);
            q[1] = floor.top(q[0], q[2]) + 0.2f;
            b.push().translate(q[0], q[1], q[2]).rotateY(rr.nextFloat() * 360).rotateZ((rr.nextFloat() - 0.5f) * 50);
            b.rock(1.1f + rr.nextFloat() * 1.2f, 0.7f + rr.nextFloat() * 0.9f, 1.0f + rr.nextFloat(), 0.25f, 1.2f, k + 3, seg(8), STONE);
            b.pop();
        }
        // colonnes brisees
        float[][] pillars = {{-3.5f, -2.0f, 10.5f}, {19.5f, -3.0f, 6.5f}};
        for (int i = 0; i < pillars.length; i++) {
            float[] pl = pillars[i];
            float y0 = floor.top(pl[0], pl[1]) - 0.4f;
            b.push().translate(pl[0], y0, pl[1]);
            b.cylinder(3.0f, 0, 1.3f, seg(12), STONE, null, STONE);
            float[][] shaft = {{2.3f, 1.2f}, {2.2f, pl[2] * 0.5f}, {2.1f, pl[2]}, {0.05f, pl[2] + 0.3f}};
            b.lathe(shaft, seg(12), 0, 360, STONE, 1, 1, 0.12f, 0.9f, 20 + i, false);
            for (int k = 0; k < 8; k++) {
                b.push().rotateY(k * 45f).translate(2.2f, 1.3f, 0);
                b.cylinder(0.35f, 0, pl[2] - 1.4f, 5, STONE, null, null);
                b.pop();
            }
            b.translate(0, pl[2] + 0.2f, 0).rotateZ(i == 0 ? 12 : -9);
            b.rock(2.4f, 1.0f, 2.3f, 0.35f, 1.4f, 30 + i, seg(8), STONE);
            b.pop();
        }
        // troncon de colonne tombe
        b.push().translate(-4.5f, floor.top(-4.5f, 13) + 1.3f, 13).rotateY(-30).rotateZ(90);
        b.cylinder(1.9f, -3.2f, 3.2f, seg(12), STONE, STONE, STONE);
        b.pop();
        // grand heaume sans visage pose dans les gravats : une fente d'ou ne sort que le vide
        b.push().translate(0.5f, floor.top(0.5f, 15.5f) - 0.5f, 15.5f).rotateY(-35).rotateZ(10);
        float[][] helm = {{0.05f, 0}, {3.0f, 0}, {3.15f, 2.5f}, {3.1f, 4.8f}, {2.7f, 6.3f}, {1.5f, 7.1f}, {0.05f, 7.3f}};
        b.lathe(helm, seg(20), 0, 360, PLATE, 1, 1.12f, 0.02f, 0.5f, 12, false);
        b.push().translate(0, 0.4f, 0).scale(1, 1, 1.12f);
        b.torus(3.05f, 0.28f, seg(20), 4, 0, 360, GOLD);
        b.pop();
        // renfort en croix sur la face
        b.push().translate(0, 3.2f, 3.35f);
        b.slab(MeshBuilder.roundRect(0.9f, 6.0f, 0.3f, 2), 0.35f, 0.1f, false, GOLD, GOLD, GOLD);
        b.pop();
        b.color(0.05f);
        for (int sd = -1; sd <= 1; sd += 2) {
            b.push().translate(sd * 1.45f, 4.5f, 3.3f).rotateY(sd * 22);
            b.slab(MeshBuilder.roundRect(2.0f, 0.5f, 0.22f, 2), 0.3f, 0.05f, false, STEEL, null, STEEL);
            b.pop();
            for (int k = 0; k < 3; k++) {
                b.push().translate(sd * (0.9f + k * 0.55f), 2.2f - k * 0.1f, 3.35f).rotateY(sd * (15 + k * 12)).rotateX(90);
                b.cylinder(0.16f, -0.2f, 0.2f, 6, STEEL, null, STEEL);
                b.pop();
            }
        }
        b.color(1f);
        b.pop();
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        base = b.build();

        // doigts et pouce de pierre : une maille par phalange
        for (int f = 0; f < 4; f++) {
            for (int j = 0; j < 3; j++) {
                MeshBuilder fb = new MeshBuilder();
                phalanx(fb, PH[j] * FS[f], 0.95f - j * 0.08f, j == 2, f * 3 + j);
                Occlusion.bake(fb, 0.3f, 2f, 0.4f, Float.NaN);
                fingers[f][j] = fb.build();
            }
        }
        for (int j = 0; j < 2; j++) {
            MeshBuilder tb = new MeshBuilder();
            phalanx(tb, TPH[j], 1.0f - j * 0.1f, j == 1, 40 + j);
            Occlusion.bake(tb, 0.3f, 2f, 0.4f, Float.NaN);
            thumb[j] = tb.build();
        }

        // ame captive : cristal a facettes + halo
        MeshBuilder ob = new MeshBuilder();
        ob.push().scale(1, 1.3f, 1);
        ob.gem(ORB_R, ORB_R * 0.7f, ORB_R * 1.2f, 8, CRYSTAL);
        ob.pop();
        orb = ob.build();
        MeshBuilder og = new MeshBuilder();
        og.sphere(ORB_R * 1.35f, seg(12), seg(8), Mat.WHITE);
        orbGlow = og.build();

        // epee lourde de pierre : large lame de fer-pierre ecornee, nervure de granit, garde massive,
        // fusee de cuir cerclee d'acier, pommeau de pierre serti d'un cristal ; origine a la garde, lame vers le bas
        MeshBuilder sw = new MeshBuilder();
        sw.push().translate(0, -16, 0);
        sw.slab(new float[][]{{0, -2.4f}, {1.5f, -0.6f}, {1.75f, 2}, {1.85f, 14.2f}, {1.5f, 16}, {-1.5f, 16}, {-1.85f, 14.2f}, {-1.75f, 2}, {-1.5f, -0.6f}},
                1.1f, 0.35f, false, PLATE, PLATE, STONE);
        sw.slab(new float[][]{{0, -0.6f}, {0.5f, 0.6f}, {0.55f, 14.5f}, {-0.55f, 14.5f}, {-0.5f, 0.6f}}, 1.5f, 0.2f, false, STONE, STONE, STONE);
        Random rs = new Random(4);
        for (int i = 0; i < 7; i++) {
            float y = 1.5f + i * 2.0f + rs.nextFloat();
            float sd = (i % 2 == 0) ? 1 : -1;
            sw.push().translate(sd * 1.75f, y, 0).rotateZ(rs.nextFloat() * 60);
            sw.color(0.55f);
            sw.rock(0.45f + rs.nextFloat() * 0.25f, 0.35f, 0.6f, 0.3f, 1.5f, 50 + i, 6, STONE);
            sw.color(1f);
            sw.pop();
        }
        sw.pop();
        // garde : bloc de pierre taillee
        sw.push().translate(0, 0.2f, 0);
        sw.slab(new float[][]{{-4.6f, -0.9f}, {4.6f, -0.9f}, {5.2f, 0}, {4.6f, 0.9f}, {-4.6f, 0.9f}, {-5.2f, 0}}, 2.0f, 0.4f, false, STONE, STONE, STONE);
        for (int sd = -1; sd <= 1; sd += 2) {
            sw.push().translate(sd * 5.0f, 0, 0);
            sw.rock(1.0f, 1.2f, 1.1f, 0.3f, 1.3f, 60 + sd, 7, STONE);
            sw.pop();
        }
        sw.push().translate(0, 0, 1.05f);
        sw.slab(MeshBuilder.roundRect(1.8f, 1.2f, 0.4f, 2), 0.2f, 0.05f, false, GOLD, null, GOLD);
        sw.pop();
        sw.pop();
        // fusee et pommeau
        sw.push().translate(0, 1.1f, 0);
        sw.cylinder(0.7f, 0, 4.6f, seg(8), LEATHER, null, null);
        for (int i = 0; i < 3; i++) {
            sw.push().translate(0, 0.8f + i * 1.5f, 0);
            sw.torus(0.75f, 0.14f, seg(8), 3, 0, 360, STEEL);
            sw.pop();
        }
        sw.translate(0, 5.6f, 0);
        sw.rock(1.4f, 1.2f, 1.2f, 0.2f, 1.2f, 70, 8, STONE);
        sw.translate(0, 0, 1.0f);
        sw.gem(0.55f, 0.35f, 0.5f, 6, CRYSTAL);
        sw.pop();
        sword = sw.build();
        // lueur : fissures froides le long de la nervure
        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(0, -16, 0);
        sg.slab(new float[][]{{0, 0.5f}, {0.18f, 1.0f}, {0.18f, 14}, {-0.18f, 14}, {-0.18f, 1.0f}}, 1.7f, 0f, false, Mat.WHITE, Mat.WHITE, Mat.WHITE);
        sg.pop();
        swordGlow = sg.build();
        MeshBuilder gh = new MeshBuilder();
        gh.push().scale(0.45f);
        gh.push().translate(0, -15, 0);
        gh.slab(new float[][]{{0, -1.4f}, {0.75f, 0}, {0.75f, 15}, {-0.75f, 15}, {-0.75f, 0}}, 0.35f, 0.1f, false, Mat.WHITE, Mat.WHITE, Mat.WHITE);
        gh.pop();
        gh.push().rotateX(-90);
        gh.slab(new float[][]{{-4.2f, 0.5f}, {-4.2f, -0.5f}, {4.2f, -0.5f}, {4.2f, 0.5f}}, 1.0f, 0.1f, false, Mat.WHITE, Mat.WHITE, Mat.WHITE);
        gh.pop();
        gh.cylinder(0.45f, 0, 3.4f, 6, Mat.WHITE, null, Mat.WHITE);
        gh.pop();
        ghost = gh.build();

        // cadenas ecusson
        Padlock.Style st = new Padlock.Style();
        st.shape = Padlock.Shape.SHIELD;
        st.w = 6.0f;
        st.h = 6.6f;
        st.d = 2.6f;
        st.height = 3.4f;
        st.halfSpan = 1.8f;
        st.body = PLATE;
        st.face = LOCKF;
        st.shackle = STEEL;
        st.trim = GOLD;
        st.hole = STEEL;
        st.decorate = (mb, s) -> {
            for (int sd = -1; sd <= 1; sd += 2) {
                Geo.cone(mb, v(sd * 2.6f, 2.7f, 0), v(sd * 0.5f, 1, 0), 1.5f, 0.42f, 6, GOLD);
            }
            Geo.cone(mb, v(0, -3.4f, 0), v(0, -1, 0), 1.3f, 0.42f, 6, GOLD);
        };
        lock = new Padlock(st);
        lock.build(detail);

        // cle : poignee en gantelet ferme sur un cristal
        Mesh[] k = KeySmith.build(STEEL, GOLD, 0.55f, (kb, kg) -> {
            float[][] g = {{1.5f, 0.6f}, {1.9f, 1.4f}, {2.1f, 3.4f}, {1.6f, 4.4f}, {0.05f, 4.6f}};
            kb.lathe(g, 14, 0, 360, PLATE, 1, 0.85f, 0, 0, 0, false);
            for (int i = 0; i < 4; i++) {
                kb.push().translate(-1.5f + i, 4.2f, -0.6f).rotateX(70);
                kb.cylinder(0.45f, 0, 2.2f, 6, PLATE, null, STEEL);
                kb.pop();
            }
            kb.push().translate(0, 5.2f, 0.4f);
            kb.gem(0.9f, 0.6f, 1.0f, 6, CRYSTAL);
            kb.pop();
            kg.push().translate(0, 5.2f, 0.4f);
            kg.sphere(1.2f, 8, 5, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== animation
    /** Ouverture de la main (0 = poing ferme, 1 = main ouverte). */
    private static float unfold(CrateState s, float delay) {
        if (!s.active) return 0;
        float t = s.t;
        float o = Ease.outBack(Ease.range(t, CrateAnim.OPEN + delay, CrateAnim.OPEN_END - 4 + delay), 1.4f);
        float c = Ease.inCubic(Ease.range(t, CrateAnim.CLOSE + delay * 0.5f, CrateAnim.SLAM - 2));
        // la main se crispe un instant quand la cle tourne
        float grip = -0.06f * Ease.bell(t, CrateAnim.UNLOCK, CrateAnim.OPEN);
        return o * (1 - c) + grip;
    }

    private static float orbRise(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, CrateAnim.OPEN + 8, CrateAnim.OPEN_END + 6)) * (1 - Ease.smooth(Ease.range(t, CrateAnim.CLOSE - 12, CrateAnim.CLOSE + 8)));
    }

    private static float swordLift(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.outBack(Ease.range(t, CrateAnim.OPEN, CrateAnim.OPEN_END), 1.3f) * (1 - Ease.inOutCubic(Ease.range(t, CrateAnim.CLOSE, CrateAnim.SLAM - 2)));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        float cav = Seq.cavity(s);
        out.solid(p, base, gl);
        // doigts : chaine d'articulations autour de X (le doigt se replie vers +Z)
        p.push();
        handFrame(p);
        for (int f = 0; f < 4; f++) {
            float o = unfold(s, f * 1.5f);
            p.push().translate(8 + FX[f], FINGER_Y, FINGER_Z).rotZ(-FX[f] * 2.2f * Math.max(0, o));
            for (int j = 0; j < 3; j++) {
                p.rotX(Ease.lerp(CLOSED[j], OPENED[j], o));
                out.solid(p, fingers[f][j], gl);
                p.translate(0, PH[j] * FS[f], 0);
            }
            p.pop();
        }
        float ot = unfold(s, 4);
        p.push().translate(THUMB[0], THUMB[1], THUMB[2]);
        for (int j = 0; j < 2; j++) {
            p.rotZ(Ease.lerp(T_CLOSED[j], T_OPENED[j], ot));
            out.solid(p, thumb[j], gl);
            p.translate(0, TPH[j], 0);
        }
        p.pop();
        p.pop();
        // ame de feu captive
        float r = orbRise(s);
        float[] c = cavityPos();
        p.push().translate(c[0], c[1] + 9f * r, c[2] - 0.2f * r).rotY(s.time * 3f * r + 20).scale(HS * (1 + 0.15f * r));
        out.solid(p, orb, 1f);
        out.additive(p, orbGlow, FIRE, 0.16f + 0.06f * s.pulse(0.3f) + 0.12f * cav + 0.14f * aw);
        p.pop();
        // epee plantee : s'arrache du sol et flotte, pointe en bas ; ses fissures rougeoient
        float sl = swordLift(s);
        p.push().translate(SWORD_X, SWORD_Y + 8f * sl, SWORD_Z).rotY(-20 + s.time * 2f * sl).rotZ(-5 * (1 - sl)).rotX(4 * (1 - sl));
        out.solid(p, sword, 0.85f + 0.15f * aw);
        out.additive(p, swordGlow, SWORD_FIRE, 0.15f + 0.5f * sl);
        p.pop();
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(8, HANG_Y + 0.67f, HANG_Z);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        float aw = Seq.awaken(s);
        float ex = Seq.excite(s);
        // epees de feu en orbite (ouverture uniquement) : lames orange, flamme le long de chaque lame
        float orbS = orbitScale(s);
        for (int i = 0; i < 6; i++) {
            if (orbS <= 0.001f) break;
            float ang = i * 60f + s.time * (0.9f + 0.1f * (i % 2)) + (s.active ? s.t * s.t * 0.02f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)) : 0);
            float rad = 20f + 4f * ex;
            float y = 12f + 2.5f * (float) Math.sin(s.time * 0.05f + i * 1.1f) + 10f * aw;
            p.push().translate(8, 0, 8).rotY(ang).translate(rad, y, 0).rotZ(-90).rotX((float) Math.sin(s.time * 0.1f + i) * 20 + 90).rotZ(-12).scale(orbS);
            out.additive(p, ghost, SWORD_FIRE, 0.4f + 0.25f * s.pulse(0.2f + i * 0.05f) + 0.3f * ex);
            out.billboard(p, Sprite.FLAME, 0, -3.6f, 0, 2.6f * orbS, 0, 0xFFFFFF, 0.28f + 0.12f * s.pulse(0.35f + i * 0.07f));
            p.pop();
        }
        if (s.item) return;
        float[] c = cavityPos();
        CommonFx.cavity(s, p, out, this, c[0], c[1], c[2], 5f, FIRE);
        // flamme qui danse au-dessus de l'ame quand elle s'eleve
        float r = orbRise(s);
        if (r > 0.01f) {
            float fy = c[1] + 9f * r;
            out.billboard(p, Sprite.FLAME, c[0], fy + 3.2f, c[2], 3.4f * r * (0.9f + 0.1f * s.pulse(0.5f)), 0, 0xFFFFFF, 0.6f * r);
            out.billboard(p, Sprite.HALO, c[0], fy, c[2], 8f * r, 0, FIRE, 0.3f * r);
        }
        if (aw > 0.01f) {
            out.ground(p, Sprite.SHOCK, 8, floor.hCenter + 0.3f, 8, 12f + 12f * aw, 0, 0xFFD8B0, 0.3f * aw);
            out.rays(p, c[0], c[1] + 9f * r, c[2], 10, 10f * aw, 0.9f, s.time * 1.5f, FIRE, 0.35f * aw);
        }
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, WRIST_Y + (ORB_Y - WRIST_Y) * HS, 8 + (ORB_Z - 8) * HS};
    }

    // =================================================================== particules
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        // braises qui s'echappent d'entre les doigts de pierre
        if (r.nextFloat() < 0.07f) {
            float[] c = cavityPos();
            fx.particle(FxSink.EMBER, 8 + (r.nextFloat() - 0.5f) * 3, c[1] + 1, c[2] + (r.nextFloat() - 0.5f) * 3, 0, 0.06f, 0,
                    r.nextBoolean() ? FIRE : FIRE_HOT, 0.8f, 45);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == CrateAnim.UNLOCK + 4) fx.sound(Snd.KNIGHT, 8, PALM_Y, 8, 1.0f, 0.9f);
        if (k == CrateAnim.OPEN + 8) fx.sound(Snd.FIRE, 8, PALM_Y, 8, 0.7f, 1.1f);
        if (k == CrateAnim.OPEN) {
            fx.sound(Snd.KNIGHT, 8, PALM_Y, 8, 1.2f, 0.7f);
            fx.sound(Snd.WHOOSH, 8, PALM_Y, 8, 0.9f, 0.9f);
            for (int i = 0; i < 26; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.SMOKE, 8 + (float) Math.cos(a) * 6, 3, 8 + (float) Math.sin(a) * 6,
                        (float) Math.cos(a) * 0.4f, 0.25f, (float) Math.sin(a) * 0.4f, 0x8a8a90, 3.2f, 40);
                fx.particle(i % 3 == 0 ? FxSink.FLAME : FxSink.EMBER, 8, cavityPos()[1], cavityPos()[2], (float) Math.cos(a) * 0.5f,
                        0.4f + r.nextFloat() * 0.6f, (float) Math.sin(a) * 0.5f, i % 2 == 0 ? FIRE : FIRE_HOT, 1.2f, 40);
            }
            // l'epee s'arrache : eclats de pierre
            for (int i = 0; i < 14; i++) {
                fx.particle(FxSink.SHARD, SWORD_X, 3, SWORD_Z, (r.nextFloat() - 0.5f) * 0.8f, 0.8f + r.nextFloat(), (r.nextFloat() - 0.5f) * 0.8f,
                        0x8a8680, 1.0f + r.nextFloat() * 0.5f, 30);
            }
        }
        if (k > CrateAnim.OPEN && k < CrateAnim.CLOSE && r.nextFloat() < 0.6f) {
            float y = cavityPos()[1] + 9f * orbRise(s);
            fx.particle(r.nextInt(3) == 0 ? FxSink.FLAME : FxSink.EMBER, 8 + (r.nextFloat() - 0.5f) * 4, y, cavityPos()[2] + (r.nextFloat() - 0.5f) * 4,
                    0, 0.15f, 0, r.nextBoolean() ? FIRE : FIRE_HOT, 1.0f, 40);
        }
        if (k == CrateAnim.SLAM) {
            fx.sound(Snd.KNIGHT, 8, PALM_Y, 8, 0.7f, 1.3f);
            for (int i = 0; i < 18; i++) {
                fx.particle(FxSink.SPARK, 8 + (r.nextFloat() - 0.5f) * 8, PALM_Y + 2, 8 + (r.nextFloat() - 0.5f) * 8,
                        (r.nextFloat() - 0.5f) * 0.8f, 0.6f + r.nextFloat() * 0.6f, (r.nextFloat() - 0.5f) * 0.8f, FIRE_HOT, 0.8f, 20);
            }
        }
    }
}
