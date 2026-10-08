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
 * Caisse Gemme : le Reliquaire des Gemmes. Sur un dais octogonal de marbre noir veine d'or, quatre pylones d'or portent
 * les quatre gemmes des couteaux (rubis, saphir, emeraude, perle noire), reliees au centre par des rigoles de lumiere.
 * Au centre, une colonne de cristal taille, cerclee d'or et tenue par quatre griffes fines, renferme un couteau qui
 * se balance doucement et passe, dans un eclair, par les douze couteaux de la caisse (3 modeles x 4 gemmes).
 * <p>Le cadenas verrouille la griffe avant. Au declic les griffes s'ouvrent, les pylones tirent l'un apres l'autre leur
 * rayon dans le cristal qui se charge de lumiere, puis il vole en eclats : les eclats tournent autour du reliquaire et
 * trois couteaux s'envolent pour leur numero (flips du butterfly, karambit qui tourne sur son anneau, M9 qui pivote).
 * A la fermeture, ils se fondent en un seul qui rentre, et les eclats se ressoudent autour de lui.
 */
public final class Gemme extends Design {
    private static final Mat MARBLE = Mat.MAIN;
    private static final Mat LACQUER = Mat.MAIN2;
    private static final Mat GOLD = Mat.TRIM;
    private static final Mat GOLD_F = Mat.ACCENT;
    private static final Mat PLAQUE = Mat.EMBLEM;
    private static final Mat PLATE = Mat.LOCK;
    private static final Mat ICONS = Mat.DECAL_A;
    private static final Mat FACET = Mat.DECAL_B;
    private static final Mat VELVET = Mat.INTERIOR.withWorld(12);
    private static final Mat CRYSTAL = Mat.GEM;
    private static final Mat DARK = Mat.DARK;
    private static final Mat POLISH = Mat.SPECIAL;

    /** Rubis, saphir, emeraude, perle noire : lueurs (meme ordre que les lames de l'atlas et que les finitions des couteaux). */
    public static final int[] GEM_RGB = {0xFF2050, 0x3070FF, 0x25E080, 0x9A6CFF};
    /** Teinte des gemmes taillees (plus sombre pour la perle noire). */
    private static final int[] GEM_TINT = {0xFF3A60, 0x3A7AFF, 0x34EA8C, 0x6A48D8};

    /** Dais : dessus des deux gradins, rayons (aux sommets) des octogones. */
    private static final float T1 = 1.5f, T2 = 3.2f, R1 = 14.6f, R2 = 11.2f;
    /** Pylones (aux diagonales) : rayon, sommet de la coupe, hauteur des gemmes flottantes. */
    private static final float PYL_R = 12.9f, PYL_TOP = 12.4f, GEM_Y = 16.0f;
    /** Colonne de cristal : 8 pans, pointe basse, bas et haut du fut, pointe haute, rayon. */
    private static final int N = 8;
    private static final float CB = 4.6f, CB1 = 7.2f, CT0 = 20.4f, CT = 24.6f, CR = 5.0f, CMID = (CB1 + CT0) / 2;
    /** Griffes : pied (rayon), pointe (rayon, hauteur) au bas du fut. */
    private static final float CLAW_R0 = 6.1f, CLAW_TIP_R = CR + 0.3f, CLAW_TIP_Y = 8.4f;
    /**
     * Couteaux : les vrais modeles TaCZ des skins heralisz_skins (le shader de heralisz_camo dessine la finition).
     * Taille d'affichage (px) dans le cristal et en l'air, roulis du modele ; hauteur dans le cristal ; orbite en l'air.
     */
    private static final String[] KNIFE_ID = {"butterfly", "karambit", "m9"};
    private static final String[] GEM_ID = {"doppler_ruby", "doppler_sapphire", "gamma_emerald", "doppler_black_pearl"};
    private static final float KNIFE_IN = 8f, KNIFE_SHOW = 9f;
    private static final float K_Y = 13.8f, SHOW_R = 10f, SHOW_Y = 25.5f;
    /** Duree d'un couteau dans le cristal avant de passer au suivant (ticks). */
    private static final float GEM_CYCLE = 70f;

    private Mesh base = Mesh.EMPTY, channel = Mesh.EMPTY, collar = Mesh.EMPTY, gemGlow = Mesh.EMPTY;
    private final Mesh[] gems = new Mesh[4];
    private Mesh claw = Mesh.EMPTY, diamond = Mesh.EMPTY, diamondGlow = Mesh.EMPTY;
    /** Diamant qui flotte au-dessus de la pointe du cristal. */
    private static final float DIAMOND_Y = CT + 2.6f;
    private final Mesh[] shellOut = new Mesh[N], shellIn = new Mesh[N], shellEdge = new Mesh[N], shellBand = new Mesh[N];
    /** Fissures lumineuses de chaque quartier, par etape (1..CRACK_STEPS segments). */
    private static final int CRACK_STEPS = 6;
    private final Mesh[][] cracks = new Mesh[N][CRACK_STEPS + 1];
    private final float[][] shellPivot = new float[N][];
    /** Rendus reels des 12 couteaux (apercu hors jeu et rendu lointain), index modele * 4 + gemme. */
    private final Mesh[] icons = new Mesh[12];

    public Gemme() {
        super("gemme", 0x9FE8FF, 0xFFD36A);
        keyRgb = 0xBFEFFF;
        cavityRgb = 0xDFF6FF;
        cavityAccent = 0xFFD36A;
        // la plus prestigieuse du jeu : la plus grande
        size = 1.6f;
        ringY = 54;
        ringR = 25;
        revealY = 60;
        riseFromY = CMID;
        lockFloorY = T2;
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- dais : deux gradins octogonaux de marbre noir, flancs laques et plaques emaillees, liseres d'or
        octa(b, R1, 0, T1, MARBLE, LACQUER);
        octa(b, R2, T1, T2, MARBLE, PLAQUE);
        edge(b, R1, T1, 0.28f);
        edge(b, R1, 0.2f, 0.2f);
        edge(b, R2, T2, 0.26f);
        edge(b, R2, T1 + 0.12f, 0.16f);
        // coupe d'or et coussin de velours sous le cristal
        b.push().translate(8, T2, 8);
        b.lathe(new float[][]{{3.9f, 0}, {4.1f, 0.35f}, {3.6f, 0.8f}, {2.9f, 1.0f}}, seg(24), 0, 360, GOLD, 1, 1, 0, 0, 0, false);
        b.disc(2.9f, 1.0f, seg(24), VELVET, true);
        b.pop();
        // rigoles des quatre gemmes (fond sombre ; la lumiere est dessinee a part)
        for (int k = 0; k < 4; k++) {
            b.push().translate(8, T2, 8).rotateY(-(45 + 90 * k));
            b.box(4.1f, -0.01f, -0.34f, R2 * 0.924f - 0.45f, 0.03f, 0.34f, DARK);
            b.pop();
        }
        for (int k = 0; k < 4; k++) pylon(b, k);
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        base = b.build();

        MeshBuilder ch = new MeshBuilder();
        ch.box(4.1f, 0.03f, -0.2f, R2 * 0.924f - 0.45f, 0.09f, 0.2f, Mat.WHITE);
        channel = ch.build();
        MeshBuilder co = new MeshBuilder();
        co.torus(1.02f, 0.13f, seg(14), 4, 0, 360, Mat.WHITE);
        collar = co.build();

        // ---- gemmes taillees des pylones
        for (int k = 0; k < 4; k++) {
            MeshBuilder g = new MeshBuilder();
            g.colorRgb(GEM_TINT[k]);
            g.gem(1.75f, 0.95f, 2.1f, 8, CRYSTAL);
            gems[k] = g.build();
        }
        MeshBuilder gg = new MeshBuilder();
        gg.scale(1.22f);
        gg.gem(1.75f, 0.95f, 2.1f, 8, Mat.WHITE);
        gemGlow = gg.build();

        // ---- griffe d'or (repere : pied a l'origine, vers +X = vers l'exterieur, pointe courbee vers l'axe)
        MeshBuilder cl = new MeshBuilder();
        float dx = CLAW_TIP_R - CLAW_R0, dy = CLAW_TIP_Y - T2;
        float[][] path = {v(0, 0, 0), v(0.55f, dy * 0.35f, 0), v(0.35f, dy * 0.7f, 0), v(dx, dy, 0)};
        int ps = seg(4);
        cl.tube(Geo.spline(path, ps), Geo.taper(3 * ps + 1, 0.34f, 0.14f, 0.8f), seg(8), GOLD, true, true, 0, false);
        cl.push().translate(0, 0.1f, 0);
        cl.sphereSection(0.75f, seg(10), 4, 0, 90, GOLD_F);
        cl.pop();
        cl.push().translate(dx, dy, 0);
        cl.colorRgb(0xE8F6FF);
        cl.sphere(0.3f, 8, 5, CRYSTAL);
        cl.color(1f);
        cl.pop();
        Occlusion.bake(cl, 0.3f, 2f, 0.35f, Float.NaN);
        claw = cl.build();

        buildShell();

        // ---- diamant flottant au sommet (table vers le haut)
        MeshBuilder dm = new MeshBuilder();
        dm.colorRgb(0xF2FAFF);
        dm.gem(1.35f, 0.7f, 1.6f, 8, CRYSTAL);
        diamond = dm.build();
        MeshBuilder dg = new MeshBuilder();
        dg.scale(1.3f);
        dg.gem(1.35f, 0.7f, 1.6f, 8, Mat.WHITE);
        diamondGlow = dg.build();

        // ---- rendus reels des couteaux (cases 4 x 3 de la tuile) : carres recto verso, 1 px de large
        for (int c = 0; c < 12; c++) {
            Mat m = ICONS.sub((c % 4) / 4f, (c / 4) / 3f, (c % 4 + 1) / 4f, (c / 4 + 1) / 3f);
            MeshBuilder ib = new MeshBuilder();
            float hw = 0.5f, hh = 0.5f * (4f / 3f);
            float[][] uv = {{m.u(0), m.v(1)}, {m.u(1), m.v(1)}, {m.u(1), m.v(0)}, {m.u(0), m.v(0)}};
            ib.poly(new float[][]{v(-hw, -hh, 0.01f), v(hw, -hh, 0.01f), v(hw, hh, 0.01f), v(-hw, hh, 0.01f)}, uv, null, v(0, 0, 1));
            ib.poly(new float[][]{v(-hw, -hh, -0.01f), v(hw, -hh, -0.01f), v(hw, hh, -0.01f), v(-hw, hh, -0.01f)}, uv, null, v(0, 0, -1));
            icons[c] = ib.build();
        }

        // ---- cadenas : hexagone de laque noire, face emaillee au diamant, anse d'or
        Padlock.Style st = new Padlock.Style();
        st.shape = Padlock.Shape.HEX;
        st.w = 3.6f;
        st.h = 3.6f;
        st.d = 1.8f;
        st.height = 1.8f;
        st.halfSpan = 1.1f;
        st.wire = 0.42f;
        st.body = LACQUER;
        st.face = PLATE;
        st.shackle = GOLD;
        st.trim = GOLD;
        st.hole = DARK;
        lock = new Padlock(st);
        lock.build(detail);

        // ---- cle : anneau d'or serti d'un diamant et des quatre gemmes
        Mesh[] k = KeySmith.build(GOLD, GOLD_F, 0.55f, (kb, kg) -> {
            kb.push().translate(0, 3.1f, 0);
            kb.torusXY(2.55f, 0.42f, 20, 6, GOLD_F);
            kb.push().rotateX(90);
            kb.colorRgb(0xE8F6FF);
            kb.gem(1.5f, 0.7f, 1.1f, 8, CRYSTAL);
            kb.color(1f);
            kb.pop();
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(45 + 90 * i);
                kb.push().translate((float) Math.cos(a) * 2.55f, (float) Math.sin(a) * 2.55f, 0.35f).rotateX(90);
                kb.colorRgb(GEM_TINT[i]);
                kb.gem(0.6f, 0.3f, 0.45f, 6, CRYSTAL);
                kb.color(1f);
                kb.pop();
            }
            kb.pop();
            kg.push().translate(0, 3.1f, 0).rotateX(90).scale(1.3f);
            kg.gem(1.5f, 0.7f, 1.1f, 8, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    /** Prisme octogonal centre en (8, 8) : sommets aux angles 22,5 + 45 k (une face plate regarde +Z). */
    private static void octa(MeshBuilder b, float r, float y0, float y1, Mat top, Mat side) {
        float[] c = {8, y1, 8};
        for (int i = 0; i < 8; i++) {
            float a0 = 22.5f + 45 * i, a1 = a0 + 45;
            float[] p0 = Geo.polar(8, 8, a0, r, y0), p1 = Geo.polar(8, 8, a1, r, y0);
            float[] q0 = Geo.polar(8, 8, a0, r, y1), q1 = Geo.polar(8, 8, a1, r, y1);
            double am = Math.toRadians(a0 + 22.5f);
            b.face(side, v((float) Math.cos(am), 0, (float) Math.sin(am)), p0, p1, q1, q0);
            b.face(top, v(0, 1, 0), c, q1, q0);
        }
    }

    /** Liseret d'or sur les aretes d'un octogone (tore a 8 pans passant par les sommets). */
    private static void edge(MeshBuilder b, float r, float y, float wire) {
        b.push().translate(8, y, 8);
        b.torus(r + 0.04f, wire, 8, 5, 22.5f, 382.5f, GOLD);
        b.pop();
    }

    private static float[] pylonPos(int k) {
        return Geo.polar(8, 8, 45 + 90 * k, PYL_R, T1);
    }

    /** Pylone : pied d'or, fut de laque noire bague d'or, coupe ciselee et quatre griffes qui bercent la gemme. */
    private void pylon(MeshBuilder b, int k) {
        float[] c = pylonPos(k);
        int n = seg(14);
        float hy = PYL_TOP - T1;
        b.push().translate(c[0], T1, c[2]);
        b.lathe(new float[][]{{0, 0}, {1.9f, 0}, {1.9f, 0.45f}, {1.45f, 0.8f}, {1.1f, 1.3f}}, n, 0, 360, GOLD, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.1f, 1.3f}, {0.86f, 4.5f}, {0.74f, hy - 1.8f}}, n, 0, 360, LACQUER, 1, 1, 0, 0, 0, false);
        for (float y : new float[]{4.6f, hy - 3.6f}) {
            b.push().translate(0, y, 0);
            b.torus(0.92f, 0.2f, n, 5, 0, 360, GOLD);
            b.pop();
        }
        b.lathe(new float[][]{{0.74f, hy - 1.8f}, {1.25f, hy - 0.9f}, {1.5f, hy - 0.2f}, {1.25f, hy}, {0.2f, hy}}, n, 0, 360, GOLD_F, 1, 1, 0, 0, 0, false);
        for (int i = 0; i < 4; i++) {
            float a = 45 + 90 * i;
            float[][] path = {Geo.polar(0, 0, a, 1.2f, hy - 0.1f), Geo.polar(0, 0, a, 1.6f, hy + 1.2f), Geo.polar(0, 0, a, 1.15f, hy + 2.4f)};
            int ps = seg(3);
            b.tube(Geo.spline(path, ps), Geo.taper(2 * ps + 1, 0.2f, 0.1f, 1), 5, GOLD, false, true, 0, false);
        }
        b.pop();
    }

    /** Colonne de cristal en 8 quartiers (chacun dans le repere de son pivot) : facettes, faces internes, aretes, cerclages d'or. */
    private void buildShell() {
        for (int k = 0; k < N; k++) {
            float a0 = 22.5f + 45 * k, a1 = a0 + 45, am = a0 + 22.5f;
            float[] piv = Geo.polar(8, 8, am, CR * 0.45f, CMID);
            shellPivot[k] = piv;
            float[] T = rel(v(8, CT, 8), piv), B = rel(v(8, CB, 8), piv);
            float[] t0 = rel(Geo.polar(8, 8, a0, CR, CT0), piv), t1 = rel(Geo.polar(8, 8, a1, CR, CT0), piv);
            float[] b0 = rel(Geo.polar(8, 8, a0, CR, CB1), piv), b1 = rel(Geo.polar(8, 8, a1, CR, CB1), piv);
            float[] axis = rel(v(8, CMID, 8), piv);
            MeshBuilder o = new MeshBuilder();
            o.flatOut(FACET, axis, T, t0, t1);
            o.flatOut(FACET, axis, t0, b0, b1, t1);
            o.flatOut(FACET, axis, B, b1, b0);
            shellOut[k] = o.build();
            MeshBuilder in = new MeshBuilder();
            float[] mid = {0, 0, 0};
            in.flatOut(FACET, mid, T, t0, b0, B);
            in.flatOut(FACET, mid, T, t1, b1, B);
            shellIn[k] = in.build();
            MeshBuilder e = new MeshBuilder();
            float r = 0.07f;
            for (float[][] sg : new float[][][]{{T, t0}, {t0, b0}, {b0, B}}) {
                e.tube(new float[][]{sg[0], sg[1]}, new float[]{r, r}, 4, Mat.WHITE, false, false, 0, false);
            }
            shellEdge[k] = e.build();
            // cerclages d'or au bas et au haut du fut, gemme sertie a chaque angle du cerclage bas
            MeshBuilder band = new MeshBuilder();
            band.push().translate(8 - piv[0], -piv[1], 8 - piv[2]);
            for (float y : new float[]{CB1 + 0.25f, CT0 - 0.25f}) {
                band.push().translate(0, y, 0);
                band.torus(CR + 0.05f, 0.34f, 1, 6, a0, a1, GOLD);
                band.pop();
            }
            float[] sv = Geo.polar(0, 0, a0, CR + 0.3f, CB1 + 0.25f);
            band.push().translate(sv[0], sv[1], sv[2]);
            band.colorRgb(GEM_TINT[k % 4]);
            band.sphere(0.4f, 8, 5, CRYSTAL);
            band.color(1f);
            band.pop();
            band.pop();
            shellBand[k] = band.build();
            // fissures : deux marches aleatoires sur la face du fut, legerement decollees, construites segment par segment
            float[] fn = Geo.norm(Geo.cross(Geo.sub(b1, b0), Geo.sub(t0, b0)));
            if (Geo.dot(fn, Geo.sub(axis, b0)) > 0) fn = Geo.mul(fn, -1);
            Random rr = new Random(31L * k + 7);
            float[][][] walk = new float[2][CRACK_STEPS + 1][];
            for (int c = 0; c < 2; c++) {
                float u = 0.25f + rr.nextFloat() * 0.5f, w = 0.3f + rr.nextFloat() * 0.4f;
                float dir = rr.nextBoolean() ? 1 : -1;
                for (int i = 0; i <= CRACK_STEPS; i++) {
                    float[] bot = Geo.lerp(b0, b1, u), top = Geo.lerp(t0, t1, u);
                    walk[c][i] = Geo.add(Geo.lerp(bot, top, w), Geo.mul(fn, 0.05f));
                    u = Ease.clamp(u + (rr.nextFloat() - 0.5f) * 0.24f, 0.06f, 0.94f);
                    w = Ease.clamp(w + dir * (0.07f + rr.nextFloat() * 0.06f), 0.04f, 0.96f);
                }
            }
            for (int st = 1; st <= CRACK_STEPS; st++) {
                MeshBuilder cb = new MeshBuilder();
                for (int c = 0; c < 2; c++) {
                    for (int i = 0; i < st; i++) {
                        float r0 = 0.07f * (1 - i / (float) (CRACK_STEPS + 2));
                        cb.tube(new float[][]{walk[c][i], walk[c][i + 1]}, new float[]{r0, r0 * 0.8f}, 4, Mat.WHITE, false, false, 0, false);
                    }
                }
                cracks[k][st] = cb.build();
            }
        }
    }

    private static float[] rel(float[] p, float[] o) {
        return v(p[0] - o[0], p[1] - o[1], p[2] - o[2]);
    }

    // =================================================================== chronologie
    /** Allumage des pylones (un toutes les 4 ticks apres le declic) ; eclatement du cristal (1 s de charge). */
    private static final int FIRE0 = CrateAnim.UNLOCK + 1, BURST = CrateAnim.OPEN + 10;
    /** Moment "heros" : les couteaux alignes face au joueur, un eclat de lumiere court sur chaque lame. */
    private static final float HERO0 = BURST + 6, HERO1 = CrateAnim.RISE + 10;
    private static final float[] HERO_X = {0, -7.5f, 7.5f}, HERO_YAW = {0, 14, -14}, HERO_TILT = {0, 12, -12};
    /** Debut des figures (flips, rotations) une fois les couteaux repartis en orbite. */
    private static final float TRICK0 = HERO1 + 6;

    /** Temps local des mouvements de la caisse : arret sur image de 4 ticks a l'eclatement (la roue garde le temps reel). */
    private static CrateState warp(CrateState s) {
        if (!s.active) return s;
        CrateState w = s.copy();
        w.t = Seq.hitStop(s.t, BURST, 4, 12);
        return w;
    }

    /** Ouverture des griffes (0 serrees, 1 ouvertes). */
    private static float clawOpen(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, CrateAnim.UNLOCK + 1, CrateAnim.UNLOCK + 7)) * (1 - Ease.smooth(Ease.range(t, CrateAnim.SLAM - 12, CrateAnim.SLAM)));
    }

    /** Charge du cristal avant l'eclatement (0..1). */
    private static float charge(CrateState s) {
        if (!s.active) return 0;
        return Ease.inQuad(Ease.range(s.t, FIRE0, BURST));
    }

    /** Lueur du cristal : se charge avant l'eclatement, se rallume quand les eclats se ressoudent. */
    private static float crystalGlow(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return charge(s) * (1 - Ease.smooth(Ease.range(t, BURST, BURST + 8)))
                + 0.6f * Ease.bell(t, CrateAnim.CLOSE - 2, CrateAnim.SLAM + 14);
    }

    /** Eclatement du cristal : 0 entier, 1 en eclats (revient a 0 a la fermeture). */
    private static float burst(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.outCubic(Ease.range(t, BURST, BURST + 10)) * (1 - Ease.inOutCubic(Ease.range(t, CrateAnim.CLOSE - 2, CrateAnim.SLAM)));
    }

    /** Rotation des eclats autour du reliquaire (degres) ; se pose sur un multiple de 45 (quartiers identiques) a la fermeture. */
    private static float shardSpin(CrateState s) {
        if (!s.active) return 0;
        float t = s.t, t0 = BURST + 2, t1 = CrateAnim.CLOSE - 2;
        if (t <= t0) return 0;
        float w = 2.1f;
        float a = w * (Math.min(t, t1) - t0) + 60f * Ease.outCubic(Ease.range(t, t0, t0 + 20));
        if (t > t1) {
            float end = w * (t1 - t0) + 60f;
            float target = (float) Math.ceil(end / 45f) * 45f;
            a = Ease.lerp(end, target, Ease.inOutCubic(Ease.range(t, t1, CrateAnim.SLAM)));
        }
        return a;
    }

    /** Couteaux hors du cristal (0 dedans, 1 en l'air). */
    private static float release(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, BURST + 2, BURST + 14)) * (1 - Ease.smooth(Ease.range(t, CrateAnim.CLOSE - 8, CrateAnim.SLAM - 4)));
    }

    /** Alignement "heros" face au joueur (0..1). */
    private static float hero(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, HERO0, HERO0 + 10)) * (1 - Ease.smooth(Ease.range(t, HERO1 - 8, HERO1 + 4)));
    }

    /** Avancement des figures des couteaux (0..1), decale de lead ticks. */
    private static float show(CrateState s, float lead) {
        if (!s.active) return 0;
        return Ease.inOutSine(Ease.range(s.t, TRICK0 + lead, CrateAnim.CLOSE - 14 + lead * 0.4f));
    }

    /** Allumage du pylone k (rayon tire dans le cristal), 0..1. */
    private static float pylonFire(CrateState s, int k) {
        if (!s.active) return 0;
        float t = s.t, t0 = FIRE0 + 4 * k;
        float on = Ease.smooth(Ease.range(t, t0, t0 + 2));
        float hold = 1 - 0.7f * Ease.smooth(Ease.range(t, BURST + 2, BURST + 14));
        float off = 1 - Ease.smooth(Ease.range(t, CrateAnim.CLOSE - 6, CrateAnim.CLOSE + 6));
        return on * hold * off + 0.8f * Seq.stopFlash(s);
    }

    /** Couleur qui passe d'une gemme a l'autre (lueurs du cristal). */
    private static int gemCycle(float time) {
        float u = (time * 0.02f) % 4f;
        int i = (int) u;
        return Ease.mix(GEM_RGB[i], GEM_RGB[(i + 1) % 4], Ease.smooth(u - i));
    }

    /** Couteau montre dans le cristal (0..11 : modele c % 3, gemme c % 4) ; fige a l'ouverture (varie d'une ouverture a l'autre). */
    private static int shown(CrateState s) {
        return s.active ? (s.winIndex * 5 + 1) % 12 : (int) Math.floor(s.time / GEM_CYCLE) % 12;
    }

    /** Nombre de couteaux dessines : un au repos, trois pendant l'ouverture. */
    private static int knifeCount(CrateState s) {
        return release(s) > 0.001f ? 3 : 1;
    }

    private static float wrap(float a) {
        return ((a + 180f) % 360f + 360f) % 360f - 180f;
    }

    /**
     * Pose du couteau j (centre du couteau, lame a la verticale) ; renvoie sa taille d'affichage (plus grande dimension,
     * px). Au repos il tourne lentement sur lui-meme dans le cristal ; en l'air les trois couteaux flottent en orbite et
     * tournent comme sur un presentoir, puis s'alignent face au joueur pour le moment "heros".
     */
    private float knifePose(CrateState s, int j, Pose p) {
        float e = release(s), h = hero(s);
        float t = s.active ? s.t : 0;
        float phi = 90 + j * 120 + (s.active ? 160f * Ease.smooth(Ease.range(t, BURST, BURST + 36)) + 0.35f * Math.max(0, t - HERO1) * e : 0);
        float y = Ease.lerp(K_Y + 0.25f * (float) Math.sin(s.time * 0.05f), SHOW_Y + 0.8f * (float) Math.sin(s.time * 0.06f + j * 2.1f), e);
        float[] c = Geo.polar(8, 8, phi, SHOW_R * e, y);
        if (h > 0) {
            float[] lineup = {8 + HERO_X[j], SHOW_Y + (j == 0 ? 1.2f : 0) + 0.3f * (float) Math.sin(s.time * 0.08f + j), 8 + 6.5f};
            c = Geo.lerp(c, lineup, h);
        }
        float size = j == 0 ? Ease.lerp(KNIFE_IN, KNIFE_SHOW, e) : KNIFE_SHOW * Ease.smooth(Ease.clamp01(e * 1.2f));
        size *= 1 - 0.08f * swapFlash(s);
        float spin = s.time * 1.1f + j * 120;
        float yaw = Ease.lerp(spin, HERO_YAW[j], h);
        p.translate(c[0], c[1], c[2]).rotY(yaw).rotZ(0.5f * HERO_TILT[j] * h);
        return size;
    }

    /** Pointe de la lame (repere du couteau : lame verticale centree). */
    private static float[] tip(float size) {
        return v(0, 0.45f * size, 0);
    }

    /** Couteau j : modele (0 butterfly, 1 karambit, 2 m9) et index 0..11 (modele * 4 + gemme). */
    private static int knifeModel(CrateState s, int j) {
        return (shown(s) + j) % 3;
    }

    private static int knifeIndex(CrateState s, int j) {
        return knifeModel(s, j) * 4 + gemOf(s, j);
    }

    private static int gemOf(CrateState s, int j) {
        return (shown(s) + j) % 4;
    }

    /** NBT du couteau TaCZ (meme objet que celui donne par la caisse). */
    private static String knifeNbt(int c) {
        return "{GunId:\"heralisz_skins:" + KNIFE_ID[c / 4] + "__" + GEM_ID[c % 4] + "\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:1,HasBulletInBarrel:1b}";
    }

    /** Dessine le couteau (vrai modele en jeu, rendu reel en apercu / de loin). */
    private void drawKnife(CrateState s, Pose p, DrawList out, int c, float size, int gc, float glowA) {
        if (s.realItems) {
            out.item(p, "tacz:modern_kinetic_gun", knifeNbt(c), size, true);
        } else {
            p.push().scale(size * 0.72f);
            out.translucent(p, icons[c], 0xFFFFFF, 1f);
            out.additive(p, icons[c], gc, glowA * 0.2f);
            p.pop();
        }
    }

    /** Eclair du changement de gemme (1 -> 0 juste apres le changement). */
    private static float swapFlash(CrateState s) {
        if (s.active || s.time < GEM_CYCLE) return 0;
        float u = (s.time % GEM_CYCLE) / GEM_CYCLE;
        return (float) Math.exp(-u * 30f);
    }

    private static float gemY(CrateState s, int k) {
        return GEM_Y + 0.35f * (float) Math.sin(s.time * 0.06f + k * 1.7f);
    }

    /** Pose d'un quartier de cristal (respiration a la charge, eclatement, orbite, retour). */
    private void shardPose(CrateState s, int k, Pose p) {
        float E = burst(s), ch = charge(s) * (1 - E), spin = shardSpin(s);
        double ar = Math.toRadians(45 + 45 * k);
        float dx = (float) Math.cos(ar), dz = (float) Math.sin(ar);
        float[] pv = shellPivot[k];
        float breathe = 1 + ch * (0.025f + 0.014f * (float) Math.sin(s.t * 1.7f));
        float jit = ch * 0.22f * (float) Math.sin(s.t * 2.3f + k * 1.9f);
        float outR = E * (9.5f + 1.3f * (float) Math.sin(s.t * 0.1f + k)) + jit;
        float up = E * (4.5f + 2.2f * (float) Math.sin(s.t * 0.07f + k * 1.3f));
        p.translate(8, CMID, 8).scale(breathe).translate(-8, -CMID, -8);
        p.translate(8, 0, 8).rotY(-spin).translate(-8, 0, -8);
        p.translate(pv[0] + dx * outR, pv[1] + up, pv[2] + dz * outR);
        if (E > 0.001f) {
            p.rot(E * 38f * (float) Math.sin(s.t * 0.09f + k), -dz, 0, dx);
            p.rotY(E * s.t * 1.4f * (k % 2 == 0 ? 1 : -1));
            p.scale(1 - 0.42f * E);
        }
    }

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        float rumble = t < BURST ? 0.2f * charge(s) : 0;
        float impact = t >= BURST ? (float) Math.exp(-(t - BURST) * 0.2f) : 0;
        return Math.max(Math.max(rumble, impact), Math.max(0.35f * Seq.stopFlash(s), 0.45f * Seq.slam(s)));
    }

    @Override
    protected void model(CrateState s0, Pose p, DrawList out) {
        CrateState s = warp(s0);
        float gl = glowLevel(s0);
        float aw = Seq.awaken(s0);
        float ch = crystalGlow(s);
        float E = burst(s);
        float stop = Seq.stopFlash(s0);
        out.solid(p, base, gl);

        // rigoles et bagues lumineuses des pylones, gemmes flottantes
        for (int k = 0; k < 4; k++) {
            float fire = pylonFire(s0, k);
            float pulse = 0.5f + 0.5f * (float) Math.sin(s0.time * 0.12f - k * 1.6f);
            p.push().translate(8, T2, 8).rotY(-(45 + 90 * k));
            out.additive(p, channel, GEM_RGB[k], 0.35f + 0.25f * pulse + 0.6f * fire);
            p.pop();
            float[] c = pylonPos(k);
            for (float y : new float[]{4.6f, PYL_TOP - T1 - 3.6f}) {
                p.push().translate(c[0], T1 + y, c[2]);
                out.additive(p, collar, GEM_RGB[k], 0.3f + 0.2f * pulse + 0.7f * fire);
                p.pop();
            }
            p.push().translate(c[0], gemY(s0, k), c[2]).rotY(s0.time * 2.2f + k * 40 + 500f * fire).scale(1 + 0.25f * fire);
            out.solid(p, gems[k], 0.9f);
            out.additive(p, gemGlow, GEM_RGB[k], 0.22f + 0.12f * pulse + 0.6f * fire);
            p.pop();
        }

        // anneau de huit gemmes en orbite pendant la roue (vitesse de la roue), s'eleve puis redescend dans les pylones
        float orb = s0.active ? Ease.smooth(Ease.range(s0.t, BURST + 6, BURST + 20)) * (1 - Ease.smooth(Ease.range(s0.t, CrateAnim.CLOSE - 6, CrateAnim.CLOSE + 6))) : 0;
        if (orb > 0.01f) {
            float spinA = s0.t * 1.6f + 900f * Ease.smooth(Ease.range(s0.t, CrateAnim.SPIN, CrateAnim.STOP));
            for (int i = 0; i < 8; i++) {
                float[] q = Geo.polar(8, 8, spinA + i * 45, 15.5f * orb, T2 + 3f + 6f * orb + 0.8f * (float) Math.sin(s0.time * 0.07f + i));
                p.push().translate(q[0], q[1], q[2]).rotY(s0.time * 4 + i * 30).scale(0.55f * orb);
                out.solid(p, gems[i % 4], 0.9f);
                out.additive(p, gemGlow, GEM_RGB[i % 4], 0.45f + 0.5f * Seq.stopFlash(s0));
                p.pop();
            }
        }

        // diamant flottant : monte et tourne plus vite a l'ouverture
        float dy = DIAMOND_Y + 0.4f * (float) Math.sin(s0.time * 0.05f) + 3f * aw;
        p.push().translate(8, dy, 8).rotY(s0.time * 2.5f + 400f * aw);
        out.solid(p, diamond, 1f);
        out.additive(p, diamondGlow, 0xDFF6FF, 0.3f + 0.25f * (float) Math.sin(s0.time * 0.08f) + 0.4f * aw);
        p.pop();

        // griffes d'or (la griffe avant porte le cadenas)
        float co = clawOpen(s);
        for (int k = 0; k < 4; k++) {
            float az = 90 * k;
            float[] c = Geo.polar(8, 8, az, CLAW_R0, T2);
            p.push().translate(c[0], c[1], c[2]).rotY(-az).rotZ(-38f * Ease.outBack(co, 1.4f));
            out.solid(p, claw, gl);
            p.pop();
        }

        // couteaux (derriere le verre : dessines avant le cristal translucide)
        float sf = swapFlash(s);
        for (int j = 0; j < knifeCount(s); j++) {
            int gc = GEM_RGB[gemOf(s, j)];
            float ka = 0.55f + 0.4f * sf + 0.25f * aw + 0.6f * stop;
            p.push();
            float size = knifePose(s, j, p);
            drawKnife(s0, p, out, knifeIndex(s, j), size, gc, ka);
            p.pop();
        }

        // cristal : quartiers (respirent et se fissurent a la charge, eclatent, tournent, se ressoudent)
        int tint = Ease.mix(0xFFFFFF, gemCycle(s.time), 0.04f + 0.3f * ch);
        int edgeCol = Ease.mix(0xFFFFFF, gemCycle(s.time + 20), 0.12f + 0.65f * ch);
        float edgeA = (0.28f + 0.08f * (float) Math.sin(s.time * 0.1f)) * (1 + 2.2f * ch) * (1 - 0.3f * E) + 0.9f * Seq.slam(s);
        int crackStep = Math.min(CRACK_STEPS, (int) Math.ceil(ch * CRACK_STEPS * 1.15f));
        int crackCol = Ease.mix(0xFFFFFF, gemCycle(s.time + 40), 0.45f);
        for (int k = 0; k < N; k++) {
            p.push();
            shardPose(s, k, p);
            out.solid(p, shellBand[k], gl);
            out.translucent(p, shellOut[k], tint, 0.95f);
            if (E > 0.01f) out.translucent(p, shellIn[k], tint, 0.8f * E);
            out.additive(p, shellOut[k], edgeCol, 0.22f + 0.9f * ch + 0.4f * E);
            out.additive(p, shellEdge[k], edgeCol, edgeA);
            if (crackStep > 0 && E < 0.6f) out.additive(p, cracks[k][crackStep], crackCol, (0.55f + 0.6f * ch) * (1 - E));
            p.pop();
        }

        // trainees : eclats qui partent et reviennent, pointes des lames en l'air
        if (s.active && !s.item) {
            boolean shardsMoving = (s.t > BURST && s.t < BURST + 18) || (s.t > CrateAnim.CLOSE - 4 && s.t < CrateAnim.SLAM + 2);
            if (shardsMoving) {
                for (int k = 0; k < N; k++) {
                    float[][] pts = new float[6][];
                    for (int i = 0; i < pts.length; i++) {
                        CrateState c = s.copy();
                        c.t = s.t - i * 1.1f;
                        Pose q = new Pose();
                        shardPose(c, k, q);
                        pts[i] = q.apply(0, 0, 0);
                    }
                    out.additive(p, fr.heraliz.crates.client.render.scene.Trail.ribbon(pts, 0.55f, 0.05f), edgeCol, 0.8f);
                }
            }
            float e = release(s);
            if (e > 0.05f) {
                for (int j = 0; j < knifeCount(s); j++) {
                    float[][] pts = new float[9][];
                    for (int i = 0; i < pts.length; i++) {
                        CrateState c = s.copy();
                        c.t = s.t - i * 0.7f;
                        c.time = s.time - i * 0.7f;
                        Pose q = new Pose();
                        float[] tp = tip(knifePose(c, j, q));
                        pts[i] = q.apply(tp[0], tp[1], tp[2]);
                    }
                    out.additive(p, fr.heraliz.crates.client.render.scene.Trail.ribbon(pts, 0.42f, 0.03f), GEM_RGB[gemOf(s, j)], 0.85f * e);
                }
            }
        }
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        // pendu a la pointe de la griffe avant (position a l'echelle de la caisse, cadenas a taille normale)
        p.translate(8, CLAW_TIP_Y + 0.35f, 8 + CLAW_TIP_R + 0.45f);
    }

    @Override
    protected void effects(CrateState s0, Pose p, DrawList out) {
        if (s0.item) return;
        CrateState s = warp(s0);
        float aw = Seq.awaken(s0);
        float ch = crystalGlow(s);
        float E = burst(s);
        // halo au sol et coeur du cristal
        out.ground(p, Sprite.HALO, 8, 0.06f, 8, 21f, 0, glow, 0.12f + 0.25f * aw);
        out.billboard(p, Sprite.HALO, 8, CMID, 8, 8f + 12f * ch, 0, Ease.mix(GEM_RGB[gemOf(s, 0)], gemCycle(s.time), ch), (0.4f + 0.6f * ch) * (1 - E));
        out.billboard(p, Sprite.HALO, 8, DIAMOND_Y + 3f * aw, 8, 4.5f + 3f * aw, 0, 0xE8F8FF, 0.35f + 0.3f * aw);
        out.billboard(p, Sprite.GLINT, 8, DIAMOND_Y + 0.9f + 3f * aw, 8.6f, 2.2f, s0.time * 2f, 0xFFFFFF, 0.5f + 0.4f * (float) Math.sin(s0.time * 0.11f));
        // eclats de lumiere sur les facettes
        for (int i = 0; i < 5; i++) {
            float ph = (s0.time * 0.035f + Ease.hash(i * 17 + 3)) % 1f;
            float tw = (float) Math.pow(Math.sin(Math.PI * ph), 8);
            float[] q = Geo.polar(8, 8, 90 + (Ease.hash(i * 5 + 1) - 0.5f) * 150, CR * (0.8f + 0.2f * Ease.hash(i * 7)), CB1 + (CT0 - CB1) * Ease.hash(i * 3 + 2));
            out.billboard(p, Sprite.GLINT, q[0], q[1], q[2] + 0.6f, 1.6f, s0.time * 3 + i * 40, 0xFFFFFF, 0.9f * tw * (1 - E));
        }
        // rayons divins qui descendent sur le cristal pendant la charge
        float chg = charge(s);
        if (chg > 0.01f && s0.t < BURST + 6) {
            float fade = 1 - Ease.range(s0.t, BURST, BURST + 6);
            out.shaft(p, 8, CB, 8, 3.5f + 3f * chg, 42f, s0.time * 0.6f, 3, 0xE8F8FF, 0.35f * chg * fade);
            out.shaft(p, 8, CB, 8, 1.2f + 1.5f * chg, 46f, -s0.time * 0.9f, 2, 0xFFFFFF, 0.5f * chg * fade);
        }
        // gemmes des pylones : halos ; rayons vers le cristal (fil discret au repos, tir a la charge)
        float[] core = {8, CMID, 8};
        for (int k = 0; k < 4; k++) {
            float[] c = pylonPos(k);
            float gy = gemY(s0, k);
            float fire = pylonFire(s0, k);
            out.billboard(p, Sprite.HALO, c[0], gy, c[2], 4.2f + 4f * fire, 0, GEM_RGB[k], 0.3f + 0.5f * fire);
            beamBetween(p, out, new float[]{c[0], gy, c[2]}, core, 0.35f + 1.3f * fire, GEM_RGB[k], 0.1f + 0.85f * fire);
            if (fire > 0.5f && s0.active && s0.t < BURST + 4) {
                beamBetween(p, out, new float[]{c[0], gy, c[2]}, core, 0.18f, 0xFFFFFF, 0.9f * fire);
            }
        }
        // couteaux : aura a la couleur de leur gemme ; moment "heros" : projecteur et eclat qui court sur la lame
        float sf = swapFlash(s);
        float e = release(s), h = hero(s);
        for (int j = 0; j < knifeCount(s); j++) {
            Pose q = new Pose();
            float ksz = knifePose(s, j, q);
            float[] at = q.apply(0, 0, -1.2f);
            int gc = GEM_RGB[gemOf(s, j)];
            out.billboard(p, Sprite.HALO, at[0], at[1], at[2], 0.35f * ksz + 3f * e, 0, gc, 0.35f + 0.3f * e + 0.7f * sf + 0.8f * Seq.stopFlash(s0));
            if (h > 0.01f) {
                out.beam(p, at[0], at[1] - 7f, at[2], 1.9f, 16f, Ease.mix(0xFFFFFF, gc, 0.35f), 0.28f * h);
                float u = Ease.range(s.t, BURST + 18 + 6 * j, BURST + 27 + 6 * j);
                if (u > 0 && u < 1) {
                    float gu = Ease.inOutSine(u);
                    float[] g = q.apply(Ease.lerp(-0.3f, 0.36f, gu) * ksz, Ease.lerp(-0.35f, 0.42f, gu) * ksz, 0.8f);
                    float a = (float) Math.sin(Math.PI * u);
                    out.billboard(p, Sprite.GLINT, g[0], g[1], g[2], 2.6f, s0.time * 6, 0xFFFFFF, a);
                    out.billboard(p, Sprite.HALO, g[0], g[1], g[2], 2.4f, 0, gc, 0.6f * a);
                }
            }
        }
        if (!s0.active) return;
        // eclatement : eclair blanc, halo qui s'expand, rayons aux quatre couleurs, onde au sol
        float flash = s0.t >= BURST ? (float) Math.exp(-(s0.t - BURST) * 0.32f) : 0;
        if (flash > 0.01f) {
            out.billboard(p, Sprite.FLARE, 8, CMID, 8, 8f + 20f * flash, s0.time * 2, 0xFFFFFF, 0.85f * flash);
            out.billboard(p, Sprite.HALO, 8, CMID, 8, 10f + 60f * (1 - flash), 0, 0xDFF6FF, 0.35f * flash);
            for (int k = 0; k < 4; k++) {
                out.rays(p, 8, CMID, 8, 5, 40f * (1.25f - flash * 0.4f), 3.4f, s0.time * 1.2f + k * 18f, GEM_RGB[k], 0.75f * flash);
            }
            out.ground(p, Sprite.SHOCK, 8, T2 + 0.15f, 8, 6f + 50f * (1 - flash), 0, glow, 0.9f * flash);
        }
        // lueur du cristal qui se ressoude
        float reform = Seq.slam(s0);
        if (reform > 0.01f) {
            out.billboard(p, Sprite.FLARE, 8, CMID, 8, 8f + 10f * reform, 0, 0xFFFFFF, 0.8f * reform);
            out.ground(p, Sprite.SHOCK, 8, T2 + 0.15f, 8, 4f + 22f * (1 - reform), 0, glow, 0.7f * reform);
        }
        CommonFx.cavity(s0, p, out, this, 8, T2 + 1.1f, 8, 5.5f);
        if (aw > 0.01f) out.ground(p, Sprite.SWIRL, 8, T2 + 0.2f, 8, 12f, -s0.time * 3f, gemCycle(s0.time), 0.3f * aw);
    }

    /** Faisceau entre deux points : deux plans croises portant le sprite BEAM. */
    private static void beamBetween(Pose p, DrawList out, float[] a, float[] b, float halfW, int rgb, float alpha) {
        if (alpha <= 0.003f) return;
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(Math.hypot(dx, dz), dy));
        for (int i = 0; i < 2; i++) {
            p.push().translate(a[0], a[1], a[2]).rotY(yaw).rotX(pitch).rotY(i * 90);
            out.quad(p, Sprite.BEAM, -halfW, 0, halfW, len, rgb, alpha);
            p.pop();
        }
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, CMID, 8};
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.3f) {
            int k = r.nextInt(4);
            float[] q = Geo.polar(8, 8, 45 + 90 * k, 4.5f + r.nextFloat() * 5.5f, T2 + 0.2f);
            fx.particle(FxSink.SPARK, q[0], q[1], q[2], 0, 0.12f + r.nextFloat() * 0.1f, 0, GEM_RGB[k], 1.0f + r.nextFloat() * 0.6f, 30);
        }
        if (r.nextFloat() < 0.12f) {
            float[] q = Geo.polar(8, 8, r.nextFloat() * 360, CR * (0.5f + r.nextFloat() * 0.6f), 7f + r.nextFloat() * 16f);
            fx.particle(FxSink.DOT, q[0], q[1], q[2], 0, 0.04f, 0, 0xFFFFFF, 1.1f, 24);
        }
        if (r.nextFloat() < 0.08f) {
            int k = r.nextInt(4);
            float[] c = pylonPos(k);
            fx.particle(FxSink.SPARK, c[0] + rnd(r, 1.2f), GEM_Y + rnd(r, 1.2f), c[2] + rnd(r, 1.2f), 0, 0.08f, 0, GEM_RGB[k], 1.2f, 26);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        CrateState w = warp(s);
        // charge : le cristal monte en puissance, les pylones l'alimentent un par un
        if (k == FIRE0) fx.sound(Snd.GEM_CHARGE, 8, CMID, 8, 1.0f, 1.0f);
        if (k == CrateAnim.UNLOCK + 1) fx.sound(Snd.KEY_TURN, 8, CLAW_TIP_Y, 8 + CLAW_TIP_R, 0.8f, 0.7f);
        for (int i = 0; i < 4; i++) {
            int t0 = FIRE0 + 4 * i;
            float[] c = pylonPos(i);
            if (k == t0) fx.sound(Snd.GEM_CHIME, c[0], GEM_Y, c[2], 0.35f, 1.3f + 0.15f * i);
            if (k >= t0 && k < BURST) {
                for (int n = 0; n < 2; n++) {
                    float u = r.nextFloat();
                    fx.particle(FxSink.SPARK, Ease.lerp(c[0], 8, u), Ease.lerp(GEM_Y, CMID, u), Ease.lerp(c[2], 8, u),
                            (8 - c[0]) * 0.04f, 0, (8 - c[2]) * 0.04f, GEM_RGB[i], 1.3f, 10);
                }
            }
        }
        if (k > FIRE0 + 4 && k < BURST && r.nextFloat() < charge(w)) {
            float[] q = Geo.polar(8, 8, r.nextFloat() * 360, CR, CB1 + r.nextFloat() * (CT0 - CB1));
            fx.particle(FxSink.SHARD, q[0], q[1], q[2], rnd(r, 0.1f), -0.05f, rnd(r, 0.1f), 0xFFFFFF, 0.9f, 20);
        }
        // eclatement
        if (k == BURST) {
            fx.sound(Snd.GEM_SHATTER, 8, CMID, 8, 1.5f, 1.0f);
            for (int i = 0; i < 80; i++) {
                double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(2 * r.nextDouble() - 1);
                double sp = 1.2 + r.nextDouble() * 2.2;
                fx.particle(i % 3 == 0 ? FxSink.SPARK : FxSink.SHARD, 8, CMID + rnd(r, 5f), 8,
                        (float) (Math.sin(ph) * Math.cos(th) * sp), (float) (Math.cos(ph) * sp * 0.7 + 0.3), (float) (Math.sin(ph) * Math.sin(th) * sp),
                        i % 5 == 0 ? 0xFFFFFF : GEM_RGB[i % 4], 1.4f + r.nextFloat() * 1.8f, 26 + r.nextInt(20));
            }
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                fx.particle(FxSink.DOT, 8, T2 + 0.5f, 8, (float) Math.cos(a) * 1.6f, 0.05f, (float) Math.sin(a) * 1.6f, glow, 1.6f, 18);
            }
        }
        // moment heros : un eclat court sur chaque lame
        for (int j = 0; j < 3; j++) {
            if (k == BURST + 18 + 6 * j) fx.sound(Snd.GEM_CHIME, 8 + HERO_X[j], SHOW_Y, 14, 0.7f, 1.0f + 0.12f * j);
        }
        if (k > BURST + 14 && k < CrateAnim.CLOSE - 8) {
            int j = r.nextInt(3);
            Pose q = new Pose();
            float[] tp = tip(knifePose(w, j, q));
            float[] at = q.apply(tp[0], tp[1], tp[2]);
            fx.particle(FxSink.SPARK, at[0], at[1], at[2], rnd(r, 0.08f), 0.03f, rnd(r, 0.08f), GEM_RGB[gemOf(w, j)], 1.1f, 14);
        }
        if (k == CrateAnim.STOP + 1) fx.sound(Snd.GEM_CHIME, 8, SHOW_Y, 8, 0.8f, 1.35f);
        // les eclats se ressoudent : scintillement aspire, 'tink' pile au choc
        if (k == CrateAnim.SLAM - 21) fx.sound(Snd.GEM_REFORM, 8, CMID, 8, 1.0f, 1.0f);
        if (k == CrateAnim.SLAM) burst(fx, r, 8, CMID, 8, 0xFFFFFF, glow, 30, 1.4f, 1.4f);
    }
}
