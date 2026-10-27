package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.Sprite;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Distributeur de capsules (« ATM », 27/10, demande du proprietaire) : un vieux distributeur de banque recupere et
 * blinde au bunker. Caisson d'acier peint bleu-gris, ecaille ; ecran vert a tube cathodique sous une visiere, quatre
 * boutons de chaque cote ; console inclinee avec le clavier et, a droite, l'entonnoir de depot ; bouche de retrait et son
 * bac ; plaque soudee et rivetee en bas, chaine et cadenas ; caisson lumineux « ATM » sur le dessus ; pose sur une palette,
 * alimente par une batterie de voiture. Un bloc de large, deux de haut.
 * <p>Textures : moitie basse de l'atlas du guichet (outils/textures_atm.py), et la feuille commune /recup (palette,
 * batterie, cables). PAS ENCORE DANS LE JEU : modele dessine hors du jeu (outils/apercu_atm.py) pour validation.
 */
public final class AtmDesign extends MachineDesign<AtmView> {
    // ---- atlas du guichet, moitie basse (outils/textures_atm.py)
    static final int[] R_PAINT = {0, 512, 256, 256}, R_FASCIA = {256, 512, 256, 256}, R_SCREEN = {512, 512, 256, 192};
    static final int[] R_KEYPAD = {768, 512, 128, 128}, R_DARK = {896, 512, 128, 128}, R_IN = {768, 640, 128, 64}, R_OUT = {896, 640, 128, 64};
    static final int[] R_CAP10 = {512, 704, 64, 64}, R_CAP5 = {576, 704, 64, 64}, R_CAP1 = {640, 704, 64, 64}, R_CRIMP = {704, 704, 64, 64};
    static final int[] R_SIGN = {0, 768, 512, 128}, R_SIDE = {512, 768, 512, 128}, R_LED = {0, 896, 64, 64}, R_GLASS = {64, 896, 128, 128};
    static final int[] R_CHAIN = {192, 896, 64, 64};
    // ---- atlas du guichet, moitie haute (memes zones que CashierDesign)
    static final int[] R_RUST = {384, 256, 128, 128}, R_STEEL = {512, 256, 128, 128}, R_SAND = {640, 256, 128, 128};
    static final int[] R_HAZARD = {768, 256, 128, 128}, R_CABLE = {896, 448, 64, 64}, R_BRASS = {960, 448, 64, 64};
    // ---- feuille commune /recup (memes zones que Recup)
    static final String RECUP = "/recup";
    static final int[] REC_BATTERY = {768, 128, 256, 128}, REC_PALLET = {256, 256, 128, 128}, REC_DARK = {896, 256, 64, 64};
    static final int[] REC_RED = {896, 384, 64, 64}, REC_RUBBER = {512, 256, 128, 128};

    private static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world);
    }

    private static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]);
    }

    private static final Mat PAINT = tri(R_PAINT, 16), FASCIA = tri(R_FASCIA, 10), SCREEN = fit(R_SCREEN), KEYPAD = fit(R_KEYPAD);
    private static final Mat DARK = tri(R_DARK, 6), IN = fit(R_IN), OUT = fit(R_OUT), CRIMP = tri(R_CRIMP, 2), SIGN = fit(R_SIGN);
    private static final Mat SIDE = fit(R_SIDE), LED = fit(R_LED), GLASS = fit(R_GLASS), CHAIN = tri(R_CHAIN, 3);
    private static final Mat RUST = tri(R_RUST, 10), STEEL = tri(R_STEEL, 10), SAND = tri(R_SAND, 8), HAZARD = fit(R_HAZARD);
    private static final Mat CABLE = tri(R_CABLE, 6), BRASS = tri(R_BRASS, 6);
    private static final Mat BATTERY = fit(REC_BATTERY), PALLET = tri(REC_PALLET, 10), BDARK = tri(REC_DARK, 4), RED = tri(REC_RED, 4);
    private static final Mat[] CAPS = {fit(R_CAP1), fit(R_CAP5), fit(R_CAP10)};

    /** Caisson : flancs, arriere, facade du bas, haut du bas de caisse (console), facade du haut, haut. */
    static final float X0 = 1.4f, X1 = 12.2f, CX = (X0 + X1) / 2, Z0 = 3.0f, ZF = 12.6f, CON_Y0 = 15.0f, CON_Y1 = 16.6f, CON_Z1 = ZF + 2.8f;
    static final float UP_Y1 = 27.4f, UP_ZF = 11.4f;
    /** Ecran : centre, inclinaison vers le haut (degres), demi-largeur et demi-hauteur de l'image (4:3). */
    static final float SCR_Y = 22.0f, SCR_TILT = -8f, SCR_W = 3.6f, SCR_H = 2.7f;
    /** Bouche de retrait et son bac ; entonnoir de depot. */
    static final float MOUTH_X0 = CX - 3.0f, MOUTH_X1 = CX + 3.0f, MOUTH_Y0 = 9.0f, MOUTH_Y1 = 11.0f, TRAY_Y = 8.3f;
    static final float[] FUNNEL = {10.5f, CON_Y1 + 0.4f, 13.6f};
    /** Clavier (centre x, z), sur la console, incline vers le joueur. */
    static final float[] KEY = {4.6f, 13.4f};
    static final float[] SIGN_C = {CX, 29.8f, 9.45f};
    static final float[] LED_C = {11.9f, 15.8f, CON_Z1 + 0.03f};
    static final int SCREEN_RGB = 0x6CFF9A, SIGN_RGB = 0x5A9BFF, GOLD_RGB = 0xFFD36A;

    private Mesh body = Mesh.EMPTY, recup = Mesh.EMPTY, lit = Mesh.EMPTY, glass = Mesh.EMPTY;
    private final Mesh[] caps = new Mesh[3];

    public AtmDesign() {
        super("atm", "caisse", SCREEN_RGB, GOLD_RGB);
        labelY = 36;
        itemScale = 0.4f;
    }

    @Override
    protected void build() {
        MeshBuilder r = new MeshBuilder();
        recupParts(r);
        Mesh recupBrut = r.build();

        MeshBuilder b = new MeshBuilder();
        // ---- caisson du bas : facade, dos, flancs (tole peinte), plaque soudee et rivetee devant
        b.bevelBox(X0, 1.5f, Z0, X1, CON_Y0, ZF, 0.35f, PAINT);
        b.bevelBox(X0 - 0.15f, 1.5f, ZF - 0.05f, X1 + 0.15f, 6.6f, ZF + 0.45f, 0.12f, STEEL);
        BunkerKit.rivetsX(b, X0 + 0.2f, X1 - 0.2f, 6.0f, ZF + 0.45f, 7, 0.2f, RUST);
        BunkerKit.rivetsX(b, X0 + 0.2f, X1 - 0.2f, 2.1f, ZF + 0.45f, 7, 0.2f, RUST);
        b.push().translate(CX + 1.4f, 4.4f, ZF + 0.47f).rotateZ(-9);
        b.decalZ(HAZARD, 0, 0, 0, 9.6f, 1.15f);
        b.pop();
        // piece de tole rapportee sur le flanc gauche
        b.bevelBox(X0 - 0.3f, 5.0f, 4.2f, X0, 11.2f, 10.6f, 0.08f, RUST);
        for (float y : new float[]{5.6f, 10.6f}) {
            for (float z : new float[]{4.8f, 10.0f}) {
                b.push().translate(X0 - 0.3f, y, z).rotateZ(90);
                b.rivet(0.18f, 0.1f, STEEL);
                b.pop();
            }
        }
        // ---- facade de la bouche de retrait (inox), bouche noire, levre d'acier, bac
        b.bevelBox(X0 + 0.7f, 7.0f, ZF - 0.05f, X1 - 0.7f, CON_Y0 - 0.2f, ZF + 0.3f, 0.1f, FASCIA);
        b.bevelBox(MOUTH_X0, MOUTH_Y0, ZF + 0.18f, MOUTH_X1, MOUTH_Y1, ZF + 0.33f, 0.04f, DARK);
        b.bevelBox(MOUTH_X0 - 0.35f, MOUTH_Y1, ZF + 0.25f, MOUTH_X1 + 0.35f, MOUTH_Y1 + 0.4f, ZF + 0.75f, 0.06f, STEEL);
        b.bevelBox(MOUTH_X0 - 0.35f, MOUTH_Y0 - 0.4f, ZF + 0.25f, MOUTH_X1 + 0.35f, MOUTH_Y0, ZF + 0.6f, 0.06f, STEEL);
        tray(b);
        b.push().translate(CX, 12.8f, ZF + 0.32f);
        b.decalZ(OUT, 0, 0, 0, 4.0f, 2.0f);
        b.pop();
        // ---- console inclinee : clavier a gauche, entonnoir de depot a droite
        b.bevelBox(X0 - 0.2f, CON_Y0, Z0 + 0.5f, X1 + 0.2f, CON_Y1, CON_Z1, 0.3f, FASCIA);
        b.push().translate(KEY[0], CON_Y1, KEY[1]).rotateX(18);
        b.bevelBox(-2.1f, -0.1f, -2.1f, 2.1f, 0.3f, 2.1f, 0.1f, FASCIA);
        b.bevelBox(-1.7f, 0.3f, -1.7f, 1.7f, 0.42f, 1.7f, 0.03f, DARK);
        b.pop();
        b.push().translate(FUNNEL[0], CON_Y1, FUNNEL[2]);
        b.cylinder(0.75f, 0, 0.4f, 12, FASCIA, null, FASCIA);
        b.pop();
        b.push().translate(FUNNEL[0], FUNNEL[1], FUNNEL[2]);
        b.lathe(new float[][]{{0.5f, 0}, {0.58f, 0.35f}, {1.45f, 1.35f}, {1.58f, 1.5f}}, 16, 0, 360, FASCIA, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.5f, 1.45f}, {1.38f, 1.32f}, {0.5f, 0.42f}, {0.42f, 0.05f}}, 16, 0, 360, DARK, 1, 1, 0, 0, 0, true);
        b.pop();
        b.push().translate(10.1f, 15.8f, CON_Z1 + 0.02f);
        b.decalZ(IN, 0, 0, 0, 3.0f, 1.5f);
        b.pop();
        b.bevelBox(LED_C[0] - 0.42f, LED_C[1] - 0.42f, CON_Z1 - 0.1f, LED_C[0] + 0.42f, LED_C[1] + 0.42f, CON_Z1, 0.08f, DARK);
        // ---- caisson du haut : ecran sous sa visiere, quatre boutons de chaque cote
        b.bevelBox(X0, CON_Y1, Z0, X1, UP_Y1, UP_ZF, 0.35f, PAINT);
        b.push().translate(CX, SCR_Y, UP_ZF).rotateX(SCR_TILT);
        b.bevelBox(-4.9f, -4.0f, -0.2f, 4.9f, 4.0f, 0.6f, 0.15f, FASCIA);
        b.bevelBox(-SCR_W - 0.35f, -SCR_H - 0.35f, 0.5f, SCR_W + 0.35f, SCR_H + 0.35f, 0.68f, 0.06f, DARK);
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 4; k++) {
                float y = -2.3f + k * 1.5f, x = s * 4.25f;
                b.bevelBox(x - 0.32f, y - 0.42f, 0.55f, x + 0.32f, y + 0.42f, 0.85f, 0.08f, STEEL);
            }
            // joues de la visiere
            b.bevelBox(s > 0 ? 4.7f : -5.1f, -3.4f, 0.6f, s > 0 ? 5.1f : -4.7f, 4.3f, 2.6f, 0.08f, PAINT);
        }
        b.bevelBox(-5.1f, 4.0f, 0.6f, 5.1f, 4.4f, 2.8f, 0.1f, PAINT);
        b.pop();
        // pochoir « CAPSULES » sur une plaque du flanc droit
        b.push().translate(X1 + 0.06f, 24.2f, 7.2f).rotateY(90);
        b.decalZ(SIDE, 0, 0, 0, 8.0f, 2.0f);
        b.pop();
        // passage des cables de la batterie (plaque et passe-fil)
        b.bevelBox(X1, 12.4f, 5.4f, X1 + 0.28f, 14.6f, 10.0f, 0.06f, RUST);
        // ---- caisson lumineux sur le dessus, sur deux equerres
        for (float x : new float[]{2.4f, 11.2f}) b.bevelBox(x - 0.3f, UP_Y1, 6.4f, x + 0.3f, 28.1f, 8.6f, 0.06f, STEEL);
        b.bevelBox(0.7f, 28.0f, 5.4f, 12.9f, 31.6f, 9.4f, 0.25f, PAINT, new Mat[]{null, null, null, null, null, DARK}, 0);
        // ---- chaine autour du bas de caisse, cadenas devant ; sac de sable au pied
        b.chain(new float[][]{p(CX, 3.7f, ZF + 0.75f), p(X1 + 0.55f, 4.3f, ZF + 0.7f), p(X1 + 0.55f, 4.3f, Z0 - 0.5f), p(X0 - 0.55f, 4.3f, Z0 - 0.5f),
                p(X0 - 0.55f, 4.3f, ZF + 0.7f), p(CX - 0.2f, 3.75f, ZF + 0.75f)}, 0.9f, 0.45f, 0.1f, CHAIN);
        b.push().translate(CX, 3.0f, ZF + 0.95f);
        b.roundedSlab(1.25f, 1.35f, 0.5f, 0.3f, 0.08f, 3, BRASS, BRASS);
        b.push().translate(0, 0.75f, 0);
        b.torusXY(0.42f, 0.1f, 12, 5, STEEL);
        b.pop();
        b.pop();
        BunkerKit.sandbag(b, 1.9f, 1.5f, 14.0f, 3.6f, 1.6f, 2.4f, 12, 7, SAND);
        Occlusion.bake(b, 0.5f, 3.5f, 0.45f, 0f, recupBrut);
        body = b.build();
        Occlusion.bake(r, 0.5f, 3.5f, 0.45f, 0f, body);
        recup = r.build();

        // ---- ce qui brille : ecran, caisson, clavier retroeclaire, voyant du depot
        MeshBuilder l = new MeshBuilder();
        l.push().translate(CX, SCR_Y, UP_ZF).rotateX(SCR_TILT);
        l.face(SCREEN, v(0, 0, 1), p(-SCR_W, -SCR_H, 0.69f), p(SCR_W, -SCR_H, 0.69f), p(SCR_W, SCR_H, 0.69f), p(-SCR_W, SCR_H, 0.69f));
        l.pop();
        l.push().translate(SIGN_C[0], SIGN_C[1], SIGN_C[2]);
        l.decalZ(SIGN, 0, 0, 0, 12.0f, 3.0f);
        l.pop();
        l.push().translate(KEY[0], CON_Y1, KEY[1]).rotateX(18);
        l.face(KEYPAD, v(0, 1, 0), p(-1.7f, 0.43f, 1.7f), p(1.7f, 0.43f, 1.7f), p(1.7f, 0.43f, -1.7f), p(-1.7f, 0.43f, -1.7f));
        l.pop();
        l.push().translate(LED_C[0], LED_C[1], LED_C[2]);
        l.decalZ(LED, 0, 0, 0, 0.6f, 0.6f);
        l.pop();
        lit = l.build();
        MeshBuilder g = new MeshBuilder();
        g.push().translate(CX, SCR_Y, UP_ZF).rotateX(SCR_TILT);
        g.face(GLASS, v(0, 0, 1), p(-SCR_W - 0.1f, -SCR_H - 0.1f, 0.78f), p(SCR_W + 0.1f, -SCR_H - 0.1f, 0.78f), p(SCR_W + 0.1f, SCR_H + 0.1f, 0.78f),
                p(-SCR_W - 0.1f, SCR_H + 0.1f, 0.78f));
        g.pop();
        glass = g.build();
        // une capsule de chaque valeur (1, 5, 10), posee sur y = 0
        for (int k = 0; k < 3; k++) {
            MeshBuilder c = new MeshBuilder();
            c.cylinder(0.55f, 0, 0.26f, 14, CRIMP, CRIMP, CAPS[k]);
            caps[k] = c.build();
        }
    }

    /** Palette, batterie de voiture et cables (feuille commune /recup). */
    private static void recupParts(MeshBuilder r) {
        BunkerKit.pallet(r, 0.2f, 0, 0.4f, 15.8f, 1.5f, 15.6f, 5, PALLET);
        float bx0 = 12.8f, bx1 = 15.4f, bz0 = 5.4f, bz1 = 10.6f, by1 = 4.6f;
        r.bevelBox(bx0, 1.5f, bz0, bx1, by1, bz1, 0.18f, BDARK, new Mat[]{null, BATTERY, null, null, null, null}, 0);
        float[][] bornes = {{14.0f, 9.6f}, {14.0f, 6.4f}};
        for (int k = 0; k < 2; k++) {
            r.push().translate(bornes[k][0], by1, bornes[k][1]);
            r.cylinder(0.3f, 0, 0.45f, 8, k == 0 ? RED : BDARK, null, k == 0 ? RED : BDARK);
            r.pop();
        }
        r.tube(new float[][]{p(14.0f, by1 + 0.4f, 9.6f), p(14.0f, by1 + 2.2f, 9.8f), p(13.4f, 9.0f, 9.4f), p(12.9f, 12.6f, 8.8f), p(12.3f, 13.4f, 8.6f)},
                new float[]{0.16f, 0.16f, 0.16f, 0.16f, 0.16f}, 6, RED, true, true, 0, false);
        r.tube(new float[][]{p(14.0f, by1 + 0.4f, 6.4f), p(14.1f, by1 + 1.8f, 6.2f), p(13.6f, 8.2f, 6.0f), p(12.9f, 12.4f, 6.4f), p(12.3f, 13.0f, 6.6f)},
                new float[]{0.16f, 0.16f, 0.16f, 0.16f, 0.16f}, 6, BDARK, true, true, 0, false);
    }

    /** Bac de retrait (fond, rebords), devant la bouche. */
    private static void tray(MeshBuilder b) {
        float x0 = MOUTH_X0 + 0.2f, x1 = MOUTH_X1 - 0.2f, z0 = ZF + 0.2f, z1 = ZF + 2.7f, y = TRAY_Y;
        b.bevelBox(x0, y, z0, x1, y + 0.18f, z1, 0.04f, STEEL);
        b.bevelBox(x0 - 0.15f, y, z0, x0, y + 0.85f, z1, 0.04f, STEEL);
        b.bevelBox(x1, y, z0, x1 + 0.15f, y + 0.85f, z1, 0.04f, STEEL);
        b.bevelBox(x0 - 0.15f, y, z1 - 0.15f, x1 + 0.15f, y + 0.6f, z1, 0.04f, STEEL);
        b.bevelBox(x0 - 0.15f, y - 0.9f, z0, x1 + 0.15f, y, z0 + 0.5f, 0.04f, RUST);
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
        out.sheet(RECUP);
        out.solid(p, recup, 0);
        out.sheet(null);
    }

    @Override
    protected void animate(Scene s, AtmView v, Pose p, DrawList out) {
        // ecran et caisson : une legere respiration, un grésillement de temps en temps
        float k = s.item ? 1 : Ease.hash((int) (s.time / 4) + (int) (s.seed * 100)) > 0.96f ? 0.55f : 0.92f + 0.08f * (float) Math.sin(s.time * 0.9f);
        out.solid(p, lit, k);
        out.translucent(p, glass, 0xFFFFFF, 0.9f);
        float t = s.item ? -1 : s.since(v.lastTx);
        if (t >= 0 && t < 80) {
            if (v.lastTxType == AtmView.DEPOT) {
                // capsules qui tombent dans l'entonnoir, en tournoyant
                for (int i = 0; i < 6; i++) {
                    float u = (t - i * 3) / 14f;
                    if (u < 0 || u > 1) continue;
                    float y = FUNNEL[1] + 5.0f - 4.4f * u * u, dx = (float) Math.sin(i * 2.1f) * 0.9f * (1 - u), dz = (float) Math.cos(i * 1.7f) * 0.7f * (1 - u);
                    capsule(p, out, i % 3, FUNNEL[0] + dx, y, FUNNEL[2] + dz, t * 40 + i * 50, 70 + i * 13);
                }
            } else {
                // capsules qui sortent de la bouche et s'empilent dans le bac
                int n = (int) Math.min(12, Math.max(1, v.lastTxAmount / 50));
                for (int i = 0; i < n; i++) {
                    float u = Math.min(1, Math.max(0, (t - 4 - i * 2.5f) / 8f));
                    if (u <= 0) continue;
                    float tx = MOUTH_X0 + 1.0f + (i % 5) * 1.0f, tz = ZF + 0.9f + (i / 5) * 0.75f, ty = TRAY_Y + 0.18f + (i / 10) * 0.27f;
                    float x = CX + (tx - CX) * u, y = MOUTH_Y0 + 0.6f + (ty - MOUTH_Y0 - 0.6f) * Ease.inOutSine(u), z = ZF + 0.3f + (tz - ZF - 0.3f) * u;
                    capsule(p, out, (2 - i % 3), x, y, z, u < 1 ? t * 30 + i * 40 : (i * 37) % 360, u < 1 ? 50 : (i % 2) * 8);
                }
            }
        }
        if (s.item) return;
        out.billboard(p, Sprite.HALO, SIGN_C[0], SIGN_C[1], SIGN_C[2] + 1.2f, 7.5f, 0, SIGN_RGB, 0.22f * k);
        out.billboard(p, Sprite.HALO, CX, SCR_Y, UP_ZF + 2.0f, 5.0f, 0, SCREEN_RGB, 0.14f * k);
        out.billboard(p, Sprite.HALO, LED_C[0], LED_C[1], LED_C[2] + 0.3f, 0.9f, 0, 0x60FF90, 0.5f);
    }

    /** Une capsule (k : 0 = 1, 1 = 5, 2 = 10), tournee de yaw puis penchee de tilt. */
    private void capsule(Pose p, DrawList out, int k, float x, float y, float z, float yaw, float tilt) {
        p.push().translate(x, y, z).rotY(yaw).rotX(tilt);
        out.solid(p, caps[k], 0);
        p.pop();
    }

    // =================================================================== camera et vues
    @Override
    public void camera(AtmView v, Scene s, int mode, Shot out) {
        out.set(CX, 24, 34, CX, 20, 8, 50);
    }

    @Override
    public AtmView newView() {
        return new AtmView();
    }

    /** Demonstration : un depot au tick 10, un retrait de 600 capsules au tick 90. */
    @Override
    public AtmView demoView(float t) {
        AtmView v = newView();
        boolean retrait = t >= 80;
        v.lastTx = DEMO_START + (retrait ? 90 : 10);
        v.lastTxType = retrait ? AtmView.RETRAIT : AtmView.DEPOT;
        v.lastTxAmount = 600;
        return v;
    }

    @Override
    public int demoLength() {
        return 170;
    }
}
