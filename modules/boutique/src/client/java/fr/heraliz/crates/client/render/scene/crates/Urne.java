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
 * Caisse de vote : le bureau de vote. Sur un sol de marbre, une table nappee de bleu nuit porte l'urne transparente a
 * cadre bleu et or (fente, compteur mecanique, levier ; le cadenas ferme son couvercle), une pile d'enveloppes et le
 * tampon "A VOTE" ; derriere, l'isoloir a rideau bleu sous l'enseigne BUREAU DE VOTE. Au repos le bureau vit : une
 * enveloppe vole regulierement jusqu'a la fente, le levier claque et le compteur tourne.
 * <p>A l'ouverture : le rideau de l'isoloir s'ouvre sur une lumiere d'or, l'enveloppe doree du joueur en sort et vole
 * jusqu'a l'urne ; levier, le compteur tourne, le tampon geant "A VOTE !" s'abat en hologramme. Les enveloppes
 * tourbillonnent dans l'urne, le couvercle s'ouvre et elles jaillissent en spirale (la roue sort de l'urne) ; pendant
 * la roue elles tournent en anneau et le compteur depouille les votes a toute vitesse, puis ralentit avec la roue.
 */
public final class Urne extends Design {
    private static final Mat MARBLE = Mat.MAIN;
    private static final Mat CLOTH = Mat.MAIN2;
    private static final Mat GOLD = Mat.TRIM;
    private static final Mat NAVY = Mat.ACCENT;
    private static final Mat SIGN = Mat.EMBLEM;
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat GLASS = Mat.DECAL_A;
    private static final Mat OAK = Mat.INTERIOR;
    private static final Mat PAPER = Mat.GEM.asFit();
    private static final Mat BLACK = Mat.DARK;
    private static final Mat CURTAIN = Mat.SPECIAL;
    /** Rouleau de chiffres (colonne gauche de DECAL_B) et tampon "A VOTE !" (le reste de la tuile). */
    private static final Mat DIGITS = Mat.fit(256, 768, 48, 256), STAMP = Mat.fit(256 + 56, 768, 200, 256);

    private static final int BLUE = 0x3FA9FF, GOLD_RGB = 0xFFD86A, RED = 0xFF2A3A;

    /** Table ; urne (verre) ; couvercle ; compteur ; isoloir. */
    private static final float TABLE_Y = 7.0f;
    private static final float UX0 = 3.9f, UX1 = 12.1f, UZ0 = 6.6f, UZ1 = 13.4f, UTOP = 18.2f;
    private static final float UCX = (UX0 + UX1) / 2, UCZ = (UZ0 + UZ1) / 2;
    private static final float[] SLOT = {UCX, UTOP + 1.0f, UCZ - 0.8f};
    private static final float BOOTH_Z = 0.2f, BOOTH_X0 = 1.5f, BOOTH_X1 = 14.5f, ROD_Y = 22.5f;
    private static final int STRIPS = 6, NENV = 22;
    private static final float IDLE_PERIOD = 90f, BASE_COUNT = 1247f;

    private Mesh site = Mesh.EMPTY, glass = Mesh.EMPTY, frame = Mesh.EMPTY, lid = Mesh.EMPTY, lever = Mesh.EMPTY, pile = Mesh.EMPTY;
    private Mesh env = Mesh.EMPTY, envGlow = Mesh.EMPTY, stampHolo = Mesh.EMPTY, inner = Mesh.EMPTY;
    private final Mesh[] strip = new Mesh[STRIPS];

    public Urne() {
        super("vote", 0x3FA9FF, 0xFFD86A);
        keyRgb = 0x8FCBFF;
        cavityRgb = 0xBFE4FF;
        cavityAccent = GOLD_RGB;
        size = 1.5f;
        ringY = 46;
        ringR = 24;
        revealY = 52;
        riseFromY = UTOP + 1;
        lockFloorY = TABLE_Y;
    }

    // =================================================================== construction
    private static void bar(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 6, m, true, true, 0, false);
    }

    /** Enveloppe (1,5 x 1,05 px, papier des deux cotes) centree. */
    private static void envelope(MeshBuilder b) {
        b.box6(-0.75f, -0.04f, -0.52f, 0.75f, 0.04f, 0.52f, PAPER, PAPER, PAPER, PAPER, PAPER, PAPER);
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // sol de marbre
        b.bevelBox(-5f, 0, -5f, 21f, 0.8f, 21f, 0.2f, MARBLE);
        // table nappee : jupe de tissu, plateau, frange d'or
        b.box(-1f, 0.8f, 6f, 17f, TABLE_Y, 14f, CLOTH);
        b.box(-1.2f, TABLE_Y - 0.05f, 5.8f, 17.2f, TABLE_Y + 0.05f, 14.2f, CLOTH);
        b.box(-1.25f, 1.2f, 14.18f, 17.25f, 1.5f, 14.28f, GOLD);
        b.box(-1.25f, TABLE_Y - 0.4f, 14.18f, 17.25f, TABLE_Y - 0.1f, 14.28f, GOLD);
        // pile d'enveloppes et tampon sur son encreur
        Random r = new Random(9);
        for (int i = 0; i < 9; i++) {
            b.push().translate(1.4f + (r.nextFloat() - 0.5f) * 0.3f, TABLE_Y + 0.1f + i * 0.09f, 10f + (r.nextFloat() - 0.5f) * 0.3f).rotateY((r.nextFloat() - 0.5f) * 20).scale(1.3f);
            envelope(b);
            b.pop();
        }
        b.box(13.2f, TABLE_Y, 9.2f, 15.6f, TABLE_Y + 0.4f, 11.0f, BLACK);
        b.box(13.4f, TABLE_Y + 0.4f, 9.4f, 15.4f, TABLE_Y + 0.45f, 10.8f, NAVY);
        b.push().translate(15.8f, TABLE_Y, 12.2f);
        b.box(-0.9f, 0, -0.6f, 0.9f, 0.5f, 0.6f, BLACK);
        b.cylinder(0.3f, 0.5f, 1.8f, 8, OAK, null, OAK);
        b.push().translate(0, 2.2f, 0);
        b.sphere(0.55f, 10, 6, OAK);
        b.pop();
        b.pop();
        // isoloir : cadre de chene, flancs et fond de rideau, tringle, enseigne
        float bx0 = BOOTH_X0 - 0.5f, bx1 = BOOTH_X1 + 0.5f, bz0 = -4.5f;
        for (float[] c : new float[][]{{bx0, BOOTH_Z}, {bx1, BOOTH_Z}, {bx0, bz0}, {bx1, bz0}}) {
            b.box(c[0] - 0.3f, 0.8f, c[1] - 0.3f, c[0] + 0.3f, ROD_Y + 0.6f, c[1] + 0.3f, OAK);
        }
        b.box(bx0 - 0.3f, ROD_Y + 0.6f, bz0 - 0.3f, bx1 + 0.3f, ROD_Y + 1.1f, BOOTH_Z + 0.3f, OAK);
        b.face(CURTAIN, v(-1, 0, 0), v(bx0, 0.9f, bz0), v(bx0, 0.9f, BOOTH_Z), v(bx0, ROD_Y, BOOTH_Z), v(bx0, ROD_Y, bz0));
        b.face(CURTAIN, v(1, 0, 0), v(bx1, 0.9f, bz0), v(bx1, 0.9f, BOOTH_Z), v(bx1, ROD_Y, BOOTH_Z), v(bx1, ROD_Y, bz0));
        b.face(CURTAIN, v(0, 0, 1), v(bx0, 0.9f, bz0 + 0.1f), v(bx1, 0.9f, bz0 + 0.1f), v(bx1, ROD_Y, bz0 + 0.1f), v(bx0, ROD_Y, bz0 + 0.1f));
        b.box(3f, 8f, bz0 + 0.1f, 13f, 8.4f, bz0 + 1.6f, OAK);
        bar(b, v(bx0, ROD_Y - 0.2f, BOOTH_Z + 0.35f), v(bx1, ROD_Y - 0.2f, BOOTH_Z + 0.35f), 0.12f, GOLD);
        b.face(SIGN, v(0, 0, 1), v(2f, ROD_Y + 1.1f, BOOTH_Z + 0.32f), v(14f, ROD_Y + 1.1f, BOOTH_Z + 0.32f), v(14f, ROD_Y + 4.1f, BOOTH_Z + 0.32f), v(2f, ROD_Y + 4.1f, BOOTH_Z + 0.32f));
        b.box(1.8f, ROD_Y + 0.9f, BOOTH_Z, 14.2f, ROD_Y + 4.3f, BOOTH_Z + 0.3f, NAVY);
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        site = b.build();
        // rideau : bandes verticales (glissent sur la tringle)
        float sw = (BOOTH_X1 - BOOTH_X0) / STRIPS;
        for (int i = 0; i < STRIPS; i++) {
            MeshBuilder c = new MeshBuilder();
            float wave = (i % 2 == 0 ? 0.25f : -0.25f);
            c.poly(new float[][]{v(-sw / 2, 1.0f, wave), v(sw / 2, 1.0f, -wave), v(sw / 2, ROD_Y - 0.3f, -wave), v(-sw / 2, ROD_Y - 0.3f, wave)},
                    new float[][]{{CURTAIN.u(i / (float) STRIPS), CURTAIN.v(1)}, {CURTAIN.u((i + 1) / (float) STRIPS), CURTAIN.v(1)},
                            {CURTAIN.u((i + 1) / (float) STRIPS), CURTAIN.v(0)}, {CURTAIN.u(i / (float) STRIPS), CURTAIN.v(0)}}, null, v(0, 0, 1));
            c.poly(new float[][]{v(-sw / 2, 1.0f, wave - 0.02f), v(sw / 2, 1.0f, -wave - 0.02f), v(sw / 2, ROD_Y - 0.3f, -wave - 0.02f), v(-sw / 2, ROD_Y - 0.3f, wave - 0.02f)},
                    new float[][]{{CURTAIN.u(0), CURTAIN.v(1)}, {CURTAIN.u(0.2f), CURTAIN.v(1)}, {CURTAIN.u(0.2f), CURTAIN.v(0)}, {CURTAIN.u(0), CURTAIN.v(0)}}, null, v(0, 0, -1));
            for (int k = 0; k < 3; k++) {
                c.push().translate(-sw / 2 + sw * (k + 0.5f) / 3, ROD_Y - 0.2f, 0.35f).rotateX(90);
                c.torus(0.2f, 0.05f, 8, 3, 0, 360, GOLD);
                c.pop();
            }
            strip[i] = c.build();
        }
        // urne : verre, cadre bleu et or, pile d'enveloppes au fond
        MeshBuilder g = new MeshBuilder();
        g.box6(UX0, TABLE_Y, UZ0, UX1, UTOP, UZ1, GLASS.asFit(), GLASS.asFit(), null, null, GLASS.asFit(), GLASS.asFit());
        glass = g.build();
        MeshBuilder f = new MeshBuilder();
        float[][] cn = {{UX0, UZ0}, {UX1, UZ0}, {UX1, UZ1}, {UX0, UZ1}};
        for (int i = 0; i < 4; i++) {
            f.box(cn[i][0] - 0.22f, TABLE_Y, cn[i][1] - 0.22f, cn[i][0] + 0.22f, UTOP, cn[i][1] + 0.22f, NAVY);
            float[] a = cn[i], c = cn[(i + 1) % 4];
            f.box(Math.min(a[0], c[0]) - 0.25f, TABLE_Y, Math.min(a[1], c[1]) - 0.25f, Math.max(a[0], c[0]) + 0.25f, TABLE_Y + 0.6f, Math.max(a[1], c[1]) + 0.25f, NAVY);
            f.push().translate(cn[i][0], TABLE_Y + 0.6f, cn[i][1]);
            f.sphere(0.35f, 8, 5, GOLD);
            f.pop();
        }
        f.box(UX0 - 0.26f, TABLE_Y + 0.55f, UZ1 + 0.2f, UX1 + 0.26f, TABLE_Y + 0.7f, UZ1 + 0.28f, GOLD);
        f.box(UCX - 0.6f, UTOP - 1.2f, UZ1 - 0.05f, UCX + 0.6f, UTOP - 0.1f, UZ1 + 0.25f, GOLD);
        frame = f.build();
        MeshBuilder pl = new MeshBuilder();
        Random rp = new Random(3);
        for (int i = 0; i < 26; i++) {
            float x = UX0 + 0.9f + rp.nextFloat() * (UX1 - UX0 - 1.8f), z = UZ0 + 0.8f + rp.nextFloat() * (UZ1 - UZ0 - 1.6f);
            pl.push().translate(x, TABLE_Y + 0.15f + (i / 7) * 0.35f + rp.nextFloat() * 0.3f, z).rotateY(rp.nextFloat() * 360).rotateX((rp.nextFloat() - 0.5f) * 40).scale(1.2f);
            envelope(pl);
            pl.pop();
        }
        pile = pl.build();
        // couvercle (repere : charniere arriere) : plaque, fente, compteur, charniere d'or
        MeshBuilder ld = new MeshBuilder();
        float lw = (UX1 - UX0) / 2 + 0.3f, ld0 = UZ1 - UZ0 + 0.4f;
        ld.bevelBox(-lw, 0, -0.2f, lw, 0.8f, ld0 - 0.2f, 0.15f, NAVY);
        ld.box(-1.2f, 0.8f, (SLOT[2] - UZ0) - 0.3f, 1.2f, 0.9f, (SLOT[2] - UZ0) + 0.3f, GOLD);
        ld.box(-0.9f, 0.9f, (SLOT[2] - UZ0) - 0.08f, 0.9f, 0.92f, (SLOT[2] - UZ0) + 0.08f, BLACK);
        ld.bevelBox(-1.7f, 0.8f, ld0 - 1.6f, 1.7f, 2.2f, ld0 - 0.3f, 0.12f, NAVY);
        ld.box(-1.3f, 1.05f, ld0 - 0.31f, 1.3f, 1.95f, ld0 - 0.26f, BLACK);
        ld.box(-lw - 0.1f, 0.2f, -0.35f, lw + 0.1f, 0.55f, 0.05f, GOLD);
        lid = ld.build();
        // levier (repere : pivot sur le flanc droit du couvercle)
        MeshBuilder lv = new MeshBuilder();
        lv.push().rotateZ(90);
        lv.cylinder(0.35f, -0.4f, 0.1f, 10, GOLD, GOLD, GOLD);
        lv.pop();
        bar(lv, v(0.2f, 0, 0), v(0.3f, 2.4f, 0.3f), 0.14f, GOLD);
        lv.push().translate(0.3f, 2.6f, 0.3f);
        lv.sphere(0.4f, 10, 6, BLACK);
        lv.pop();
        lever = lv.build();
        // enveloppe volante et sa lueur ; hologramme du tampon ; lueur interieure de l'urne
        MeshBuilder ev = new MeshBuilder();
        envelope(ev);
        env = ev.build();
        MeshBuilder eg = new MeshBuilder();
        eg.box(-0.95f, -0.12f, -0.7f, 0.95f, 0.12f, 0.7f, Mat.WHITE);
        envGlow = eg.build();
        MeshBuilder sh = new MeshBuilder();
        float hw = 3.4f, hh = 3.4f * 256f / 200f;
        sh.poly(new float[][]{v(-hw, -hh, 0), v(hw, -hh, 0), v(hw, hh, 0), v(-hw, hh, 0)},
                new float[][]{{STAMP.u(0), STAMP.v(1)}, {STAMP.u(1), STAMP.v(1)}, {STAMP.u(1), STAMP.v(0)}, {STAMP.u(0), STAMP.v(0)}}, null, v(0, 0, 1));
        sh.poly(new float[][]{v(-hw, -hh, -0.01f), v(hw, -hh, -0.01f), v(hw, hh, -0.01f), v(-hw, hh, -0.01f)},
                new float[][]{{STAMP.u(1), STAMP.v(1)}, {STAMP.u(0), STAMP.v(1)}, {STAMP.u(0), STAMP.v(0)}, {STAMP.u(1), STAMP.v(0)}}, null, v(0, 0, -1));
        stampHolo = sh.build();
        MeshBuilder in = new MeshBuilder();
        in.box(UX0 + 0.3f, TABLE_Y + 0.2f, UZ0 + 0.3f, UX1 - 0.3f, UTOP - 0.3f, UZ1 - 0.3f, Mat.WHITE);
        inner = in.build();

        // cadenas et cle
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.SHIELD;
        ps.w = 2.5f;
        ps.h = 2.8f;
        ps.d = 1.2f;
        ps.height = 1.4f;
        ps.halfSpan = 0.8f;
        ps.wire = 0.3f;
        ps.body = NAVY;
        ps.face = LOCKFACE;
        ps.shackle = GOLD;
        ps.trim = GOLD;
        ps.hole = BLACK;
        lock = new Padlock(ps);
        lock.build(detail);
        Mesh[] k = KeySmith.build(GOLD, NAVY, 0.5f, (kb, kg) -> {
            // tete de cle : enveloppe de vote scellee
            kb.push().translate(0, 3.0f, 0).rotateX(90).scale(2.6f);
            envelope(kb);
            kb.pop();
            kb.push().translate(0, 3.0f, 0.3f).rotateX(90);
            kb.cylinder(0.5f, 0, 0.15f, 12, GOLD, null, GOLD);
            kb.pop();
            kg.push().translate(0, 3.0f, 0.35f).rotateX(90);
            kg.cylinder(0.65f, -0.05f, 0.25f, 12, Mat.WHITE, Mat.WHITE, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== chronologie
    private static final float CURT0 = CrateAnim.UNLOCK + 2, CURT1 = CURT0 + 8;
    private static final float GOLD_IN = CURT0 + 3, FLY0 = CURT1 + 1, FLY1 = FLY0 + 12;
    private static final float LEVER_T = FLY1, COUNT0 = FLY1 + 2, COUNT1 = COUNT0 + 6;
    private static final float STAMP0 = COUNT0 + 3, STAMP1 = STAMP0 + 3;
    private static final float SWIRL0 = STAMP1 + 1, LID0 = CrateAnim.RISE - 3, LID1 = CrateAnim.RISE + 3, BURST = CrateAnim.RISE;
    private static final float C_BACK0 = CrateAnim.CLOSE, C_BACK1 = CrateAnim.CLOSE + 10, C_LID1 = CrateAnim.CLOSE + 12, C_CURT0 = CrateAnim.CLOSE + 8, C_CURT1 = CrateAnim.SLAM;

    /** Ouverture du rideau (0 ferme, 1 ouvert). */
    private static float curtain(CrateState s) {
        if (!s.active) return 0;
        return Ease.inOutCubic(Ease.range(s.t, CURT0, CURT1)) * (1 - Ease.inOutCubic(Ease.range(s.t, C_CURT0, C_CURT1)));
    }

    private static float lidOpen(CrateState s) {
        if (!s.active) return 0;
        return Ease.outBack(Ease.range(s.t, LID0, LID1), 1.4f) * (1 - Ease.inOutCubic(Ease.range(s.t, C_BACK0 + 4, C_LID1)));
    }

    /** Envoi du repos : phase (0..1) dans la periode, index de l'envoi, arrivee a 0,28. */
    private static float idlePhase(CrateState s) {
        return (s.time / IDLE_PERIOD) % 1f;
    }

    /** Compteur (valeur flottante, les rouleaux tournent sur la partie decimale). */
    private static float counter(CrateState s) {
        float n = (float) Math.floor(s.time / IDLE_PERIOD);
        float ph = idlePhase(s);
        float idle = BASE_COUNT + n + Ease.smooth(Ease.range(ph, 0.3f, 0.36f));
        if (!s.active) return idle;
        float t = s.t;
        float base = BASE_COUNT + (float) Math.floor((s.time - s.t) / IDLE_PERIOD) + 1;
        float vote = Ease.inOutCubic(Ease.range(t, COUNT0, COUNT1));
        float count = 0;
        if (t > CrateAnim.SPIN) {
            float u = Ease.range(t, CrateAnim.SPIN, CrateAnim.STOP);
            count = 180f * (1 - (float) Math.pow(1 - u, 4));
        }
        return base + vote + count;
    }

    /** Pose de l'enveloppe du repos (vol jusqu'a la fente) ; renvoie son echelle. */
    private static float idleEnvelope(CrateState s, Pose p) {
        float ph = idlePhase(s);
        if (ph > 0.3f) return 0;
        float u = ph / 0.3f;
        int n = (int) Math.floor(s.time / IDLE_PERIOD);
        float ang = 40 + Ease.hash(n * 7 + 1) * 280;
        float[] from = Geo.polar(UCX, UCZ, ang, 20f, 14f + 6f * Ease.hash(n * 3));
        float[] to = SLOT;
        float k = Ease.inOutSine(u);
        float[] q = Geo.lerp(from, to, k);
        q[1] += 5f * (float) Math.sin(u * Math.PI);
        float sc = u > 0.85f ? (1 - u) / 0.15f : 1;
        p.translate(q[0], q[1], q[2]).rotY(u * 540f).rotZ(90f * Ease.smooth(Ease.range(u, 0.7f, 0.95f))).rotX(20f * (float) Math.sin(u * 9));
        return 1.2f * sc;
    }

    /** Enveloppe doree du joueur : sort de l'isoloir, vole en spirale, entre dans la fente. */
    private static float goldEnvelope(CrateState s, Pose p) {
        if (!s.active || s.t < GOLD_IN || s.t > COUNT0) return 0;
        float t = s.t;
        float[] booth = v(8, 11f, -1.8f);
        float app = Ease.outBack(Ease.range(t, GOLD_IN, GOLD_IN + 5), 1.6f);
        float u = Ease.range(t, FLY0, FLY1);
        float k = Ease.inOutCubic(u);
        float[] q = Geo.lerp(booth, SLOT, k);
        q[0] += 5f * (float) Math.sin(k * Math.PI) * (float) Math.cos(k * Math.PI * 2);
        q[1] += 6f * (float) Math.sin(k * Math.PI) + 0.3f * (float) Math.sin(t * 0.3f) * (1 - k);
        q[2] += 3f * (float) Math.sin(k * Math.PI * 2);
        float in = 1 - Ease.inCubic(Ease.range(t, FLY1 - 1, COUNT0));
        p.translate(q[0], q[1], q[2]).rotY(t * 8f).rotZ(90f * Ease.smooth(Ease.range(u, 0.7f, 1f))).rotX(15f * (1 - u));
        return 2.0f * app * in;
    }

    /** Enveloppes dans l'urne (tourbillon), puis en anneau pendant la roue ; renvoie l'echelle (0 = cachee). */
    private static float swirlEnvelope(CrateState s, int i, Pose p) {
        if (!s.active || s.t < SWIRL0 || s.t > C_BACK1) return 0;
        float t = s.t;
        float h1 = Ease.hash(i * 5 + 2), h2 = Ease.hash(i * 7 + 3);
        float sw = Ease.smooth(Ease.range(t, SWIRL0, SWIRL0 + 8));
        float ang = i * 360f / NENV + t * (6f + 4f * h1) * sw;
        float r = 1.2f + 1.5f * h2;
        float y = TABLE_Y + 1.2f + (UTOP - TABLE_Y - 2.5f) * (0.5f + 0.5f * (float) Math.sin(t * 0.15f + i)) * sw;
        float[] inside = Geo.polar(UCX, UCZ, ang, r * sw, y);
        float out = Ease.inOutCubic(Ease.range(t, BURST + i * 0.4f, BURST + i * 0.4f + 12));
        float ringA = i * 360f / NENV + 60f * (t - BURST) * 0.05f + 700f * Ease.smooth(Ease.range(t, CrateAnim.SPIN, CrateAnim.STOP)) * 0.5f;
        float[] ring = Geo.polar(UCX, UCZ, ringA, 13f + 1.5f * (i % 3), 21f + 2.5f * (float) Math.sin(t * 0.08f + i) + (i % 2) * 2);
        float[] pos = Geo.lerp(inside, ring, out);
        pos[1] += 6f * (float) Math.sin(out * Math.PI);
        float back = Ease.inCubic(Ease.range(t, C_BACK0 + i * 0.2f, C_BACK1));
        pos = Geo.lerp(pos, v(UCX, TABLE_Y + 1, UCZ), back);
        float flip = t * (12f + 10f * h1);
        p.translate(pos[0], pos[1], pos[2]).rotY(ang * (1 - out) + (90 - ringA) * out).rotX(flip * (1 - out * 0.6f)).rotZ(40f * (float) Math.sin(t * 0.1f + i) * out);
        return 1.3f * (1 - back * 0.7f);
    }

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float st = s.t >= STAMP1 ? 0.5f * (float) Math.exp(-(s.t - STAMP1) * 0.35f) : 0;
        float bu = s.t >= BURST ? 0.3f * (float) Math.exp(-(s.t - BURST) * 0.3f) : 0;
        return Math.max(Math.max(st, bu), 0.25f * Seq.stopFlash(s));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        out.solid(p, site, gl);
        // rideau : les bandes glissent vers les montants
        float c = curtain(s);
        float sw = (BOOTH_X1 - BOOTH_X0) / STRIPS;
        for (int i = 0; i < STRIPS; i++) {
            float x = BOOTH_X0 + sw * (i + 0.5f);
            boolean left = i < STRIPS / 2;
            float edge = left ? BOOTH_X0 + sw * 0.25f * (i + 0.5f) : BOOTH_X1 - sw * 0.25f * (STRIPS - i - 0.5f);
            float xx = Ease.lerp(x, edge, c);
            float sway = 3f * (float) Math.sin(s.time * 0.03f + i) * (1 - c) + 10f * (float) Math.sin(s.t * 0.4f + i) * Ease.bell(s.active ? s.t : 0, CURT0, CURT1 + 6);
            p.push().translate(xx, 0, BOOTH_Z + 0.3f).translate(0, ROD_Y, 0).rotX(sway * 0.2f).translate(0, -ROD_Y, 0).scale(1f - 0.7f * c, 1, 1);
            out.solid(p, strip[i], gl);
            p.pop();
        }
        // urne : pile, lueur interieure, cadre
        out.solid(p, pile, gl);
        float glowIn = s.active ? Ease.smooth(Ease.range(s.t, SWIRL0, SWIRL0 + 8)) * (1 - Ease.smooth(Ease.range(s.t, C_BACK0, C_LID1))) : 0;
        out.solid(p, frame, gl);
        // couvercle (charniere arriere), compteur, levier
        float lo = lidOpen(s);
        p.push().translate(UCX, UTOP, UZ0 - 0.2f).rotX(-105f * lo);
        out.solid(p, lid, gl);
        // compteur : 5 rouleaux (repere du couvercle)
        out.solid(p, counterMesh(counter(s), (UZ1 - UZ0 + 0.4f) - 0.25f), 1.1f);
        // levier : tire a l'envoi du repos, et pour l'enveloppe du joueur
        float ph = idlePhase(s);
        float pull = Ease.bell(ph, 0.26f, 0.36f);
        if (s.active) pull = Math.max(pull * (s.t < CURT0 ? 1 : 0), Ease.bell(s.t, LEVER_T - 2, LEVER_T + 5));
        p.push().translate((UX1 - UX0) / 2 + 0.3f, 0.45f, (SLOT[2] - UZ0) + 0.2f).rotX(-55f * pull);
        out.solid(p, lever, gl);
        p.pop();
        p.pop();
        // enveloppes : du repos, doree, tourbillon/anneau
        Pose q = new Pose();
        if (!s.active || s.t < CURT0) {
            float sc = idleEnvelope(s, q);
            if (sc > 0.01f) {
                p.push().mul(q.m()).scale(sc);
                out.solid(p, env, gl);
                p.pop();
            }
        }
        q = new Pose();
        float gs = goldEnvelope(s, q);
        if (gs > 0.01f) {
            p.push().mul(q.m()).scale(gs);
            out.solidTinted(p, env, 1.2f, 1.0f, 0.85f, 0.45f);
            out.additive(p, envGlow, GOLD_RGB, 0.6f + 0.2f * (float) Math.sin(s.t * 0.8f));
            p.pop();
        }
        for (int i = 0; i < NENV; i++) {
            q = new Pose();
            float sc = swirlEnvelope(s, i, q);
            if (sc > 0.01f) {
                p.push().mul(q.m()).scale(sc);
                out.solid(p, env, gl);
                if (s.t > BURST) out.additive(p, envGlow, i % 3 == 0 ? GOLD_RGB : BLUE, 0.25f + 0.4f * Seq.stopFlash(s));
                p.pop();
            }
        }
        // verre (translucide) et lueur interieure
        float gi = glowIn * (0.35f + 0.65f * (1 - Ease.smooth(Ease.range(s.t, BURST, BURST + 16))));
        if (gi > 0.01f) out.additive(p, inner, Ease.mix(BLUE, GOLD_RGB, Ease.range(s.t, SWIRL0, BURST)), 0.09f * gi);
        out.translucent(p, glass, 0xE8F6FF, 0.9f);
    }

    /** Rouleaux du compteur (repere du couvercle, fenetre en z = zf) : odometre, les chiffres roulent. */
    private Mesh counterMesh(float value, float zf) {
        MeshBuilder b = new MeshBuilder();
        float dw = 0.48f, dh = 0.8f, y0 = 1.1f;
        float v = Math.max(0, value);
        float carry = v - (float) Math.floor(v);
        for (int k = 0; k < 5; k++) {
            float pow = (float) Math.pow(10, k);
            float digit = (float) Math.floor(v / pow) % 10;
            // le rouleau k tourne si tous les rouleaux de droite affichent 9
            boolean allNine = true;
            for (int j = 0; j < k; j++) {
                if ((float) Math.floor(v / Math.pow(10, j)) % 10 != 9) allNine = false;
            }
            float pos = digit + (k == 0 || allNine ? carry : 0);
            float t0 = pos / 11f, t1 = (pos + 1) / 11f;
            float x1 = 1.2f - k * (dw + 0.04f), x0 = x1 - dw;
            b.poly(new float[][]{v(x0, y0, zf), v(x1, y0, zf), v(x1, y0 + dh, zf), v(x0, y0 + dh, zf)},
                    new float[][]{{DIGITS.u(0), DIGITS.v(t1)}, {DIGITS.u(1), DIGITS.v(t1)}, {DIGITS.u(1), DIGITS.v(t0)}, {DIGITS.u(0), DIGITS.v(t0)}}, null, v(0, 0, 1));
        }
        return b.buildDynamic();
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(UCX, UTOP - 0.9f, UZ1 + 0.35f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float aw = Seq.awaken(s);
        out.ground(p, Sprite.HALO, 8, 0.85f, 9, 17f, 0, BLUE, 0.08f + 0.15f * aw);
        out.billboard(p, Sprite.HALO, 8, ROD_Y + 2.6f, BOOTH_Z + 1, 7f, 0, GOLD_RGB, 0.12f + 0.1f * aw);
        // reflet qui balaie la vitre de l'urne
        float ph = (s.time * 0.018f) % 1f;
        if (ph < 0.3f) {
            float u = ph / 0.3f;
            out.billboard(p, Sprite.GLINT, UX0 + (UX1 - UX0) * u, UTOP - 1 - 6 * u, UZ1 + 0.3f, 2f, s.time * 3, 0xFFFFFF, 0.8f * Ease.bell(u, 0, 1));
        }
        // lueur de la fente a chaque envoi
        float pulse = Ease.bell(idlePhase(s), 0.25f, 0.4f);
        out.billboard(p, Sprite.HALO, SLOT[0], SLOT[1] + 0.3f, SLOT[2], 2.5f, 0, BLUE, 0.5f * pulse);
        if (!s.active) return;
        float t = s.t;
        // lumiere d'or dans l'isoloir ouvert
        float c = curtain(s);
        if (c > 0.01f) {
            out.billboard(p, Sprite.HALO, 8, 10, -1.5f, 9f, 0, GOLD_RGB, 0.45f * c);
            out.shaft(p, 8, 1, -1.8f, 3f, 17f, s.time * 0.5f, 3, GOLD_RGB, 0.18f * c);
        }
        // trainee de l'enveloppe doree
        if (t > FLY0 && t < COUNT0) {
            float[][] pts = new float[8][];
            for (int i = 0; i < pts.length; i++) {
                CrateState qs = s.copy();
                qs.t = Math.max(FLY0, t - i * 0.6f);
                Pose qp = new Pose();
                goldEnvelope(qs, qp);
                pts[i] = qp.apply(0, 0, 0);
            }
            out.additive(p, Trail.ribbon(pts, 1.1f, 0.1f), GOLD_RGB, 0.8f);
        }
        // tampon holographique "A VOTE !" : s'abat, onde, puis s'eleve et s'efface
        if (t > STAMP0 && t < SWIRL0 + 22) {
            float slam = Ease.inCubic(Ease.range(t, STAMP0, STAMP1));
            float sc = Ease.lerp(3.2f, 1f, slam);
            float fade = 1 - Ease.smooth(Ease.range(t, SWIRL0 + 8, SWIRL0 + 22));
            float rise = 6f * Ease.inCubic(Ease.range(t, SWIRL0 + 8, SWIRL0 + 22));
            p.push().translate(UCX, UTOP - 3.5f + rise, UZ1 + 2.2f).rotZ(-8f * slam).scale(sc);
            out.translucent(p, stampHolo, 0xFFFFFF, 0.85f * slam * fade);
            out.additive(p, stampHolo, 0xFF6A7A, (0.25f + 0.35f * slam) * fade);
            p.pop();
            if (t >= STAMP1) {
                float e = (float) Math.exp(-(t - STAMP1) * 0.4f);
                out.billboard(p, Sprite.FLARE, UCX + 3.2f, UTOP - 1.2f, UZ1 + 2.0f, 2f + 5f * e, s.time, 0xFFFFFF, 0.6f * e);
                out.ground(p, Sprite.SHOCK, UCX, TABLE_Y + 0.1f, UCZ + 3, 3f + 16f * (1 - e), 0, RED, 0.7f * e);
            }
        }
        // jaillissement : colonne et rayons au-dessus de l'urne
        float lo = lidOpen(s);
        if (lo > 0.01f) {
            float e = t >= BURST ? (float) Math.exp(-(t - BURST) * 0.2f) : 0;
            out.billboard(p, Sprite.FLARE, UCX, UTOP + 1, UCZ, 4f + 16f * e, s.time, 0xFFFFFF, 0.9f * e);
            out.beam(p, UCX, UTOP, UCZ, 3f, 24f, Ease.mix(BLUE, GOLD_RGB, 0.4f), 0.3f * lo);
            out.rays(p, UCX, UTOP + 1, UCZ, 12, 24f * (0.4f + 0.6f * e), 2.2f, s.time * 1.3f, BLUE, 0.45f * e);
            CommonFx.cavity(s, p, out, this, UCX, UTOP - 0.5f, UCZ, 4.5f, BLUE);
        }
    }

    @Override
    public float[] cavityPos() {
        return new float[]{UCX, UTOP + 1, UCZ};
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        float ph = idlePhase(s), prev = ((s.time - 1) / IDLE_PERIOD) % 1f;
        if (ph >= 0.28f && prev < 0.28f) {
            fx.sound(Snd.LEVER, SLOT[0] + 3, SLOT[1], SLOT[2], 0.35f, 1.1f);
            fx.sound(Snd.COUNTER, SLOT[0], SLOT[1] + 1, SLOT[2] + 5, 0.3f, 1.1f);
            for (int i = 0; i < 6; i++) fx.particle(FxSink.SPARK, SLOT[0] + rnd(r, 0.6f), SLOT[1] + 0.3f, SLOT[2], rnd(r, 0.05f), 0.12f, rnd(r, 0.05f), BLUE, 1.0f, 14);
        }
        if (r.nextFloat() < 0.04f) fx.particle(FxSink.DOT, 8 + rnd(r, 8f), 2 + r.nextFloat() * 14, 8 + rnd(r, 8f), 0, 0.02f, 0, 0xDFEFFF, 0.8f, 40);
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == (int) CURT0) fx.sound(Snd.CURTAIN, 8, 12, BOOTH_Z, 1.0f, 1.0f);
        if (k == (int) GOLD_IN) fx.sound(Snd.GEM_CHIME, 8, 11, -1.5f, 0.8f, 1.2f);
        if (k == (int) FLY0) fx.sound(Snd.WHOOSH, 8, 14, 4, 0.6f, 1.2f);
        if (k == (int) LEVER_T) fx.sound(Snd.LEVER, SLOT[0] + 3, SLOT[1], SLOT[2], 1.0f, 1.0f);
        if (k == (int) COUNT0) {
            fx.sound(Snd.COUNTER, SLOT[0], SLOT[1] + 1, SLOT[2] + 5, 0.9f, 1.0f);
            burst(fx, r, SLOT[0], SLOT[1] + 0.5f, SLOT[2], GOLD_RGB, 0xFFFFFF, 20, 1.0f, 1.2f);
        }
        if (k == (int) STAMP1) {
            fx.sound(Snd.STAMP, UCX, UTOP - 3, UZ1 + 2, 1.3f, 1.0f);
            burst(fx, r, UCX, UTOP - 3.5f, UZ1 + 2.4f, RED, 0xFFFFFF, 30, 1.4f, 1.4f);
        }
        if (k == (int) SWIRL0 + 1) fx.sound(Snd.PAPER, UCX, UTOP - 3, UCZ, 1.0f, 1.0f);
        if (k == (int) BURST) {
            fx.sound(Snd.HOLO, UCX, UTOP + 2, UCZ, 1.0f, 0.9f);
            fx.sound(Snd.WHOOSH, UCX, UTOP + 2, UCZ, 0.8f, 0.8f);
        }
        if (k > CrateAnim.SPIN && k < CrateAnim.STOP && (k - CrateAnim.SPIN) % 24 == 0) {
            fx.sound(Snd.COUNTER, SLOT[0], SLOT[1] + 1, SLOT[2] + 5, 0.4f, 1.2f);
        }
        if (k == CrateAnim.STOP) {
            for (int i = 0; i < 60; i++) {
                double th = r.nextDouble() * Math.PI * 2;
                float sp = 0.5f + r.nextFloat() * 1.2f;
                fx.particle(i % 2 == 0 ? FxSink.PAPER : FxSink.CONFETTI, UCX, 24, UCZ, (float) Math.cos(th) * sp, 0.4f + r.nextFloat(), (float) Math.sin(th) * sp,
                        i % 3 == 0 ? GOLD_RGB : (i % 3 == 1 ? BLUE : 0xFFFFFF), 1.3f + r.nextFloat(), 40 + r.nextInt(20));
            }
        }
        if (k == (int) C_BACK0) fx.sound(Snd.PAPER, UCX, 20, UCZ, 0.8f, 1.2f);
        if (k == (int) C_LID1) fx.sound(Snd.CLOSE, UCX, UTOP, UCZ, 0.8f, 1.2f);
        if (k == (int) C_CURT0) fx.sound(Snd.CURTAIN, 8, 12, BOOTH_Z, 0.8f, 1.1f);
    }
}
