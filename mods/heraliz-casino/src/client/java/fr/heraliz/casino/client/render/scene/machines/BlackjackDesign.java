package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Geo;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.render.scene.Sprite;
import fr.heraliz.casino.game.cards.BlackjackTable;
import fr.heraliz.casino.game.cards.Cards;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;

/**
 * Table de blackjack, version bunker : un plateau en demi-lune decoupe dans du contreplaque, recouvert d'un vieux tapis
 * vert agrafe dont les reperes ont ete repeints a la main, borde d'un tuyau de caoutchouc. Il repose sur deux futs et
 * des caisses ; un tube d'echafaudage sert de repose-pieds. Cote croupier : une caisse de munitions en guise de sabot,
 * un bac de tole pour les jetons, une boite de fer pour la defausse, une plaque au pochoir et une baladeuse sur sa
 * potence. Les cartes 3D sortent du sabot en arc et se retournent ; deux gants de travail uses flottent au-dessus de
 * la table et accompagnent la distribution ; les jetons de la mise sont ramasses ou payes au reglement.
 */
public final class BlackjackDesign extends MachineDesign<BlackjackView> {
    public static final int[] R_FELT = {0, 0, 1024, 512};
    public static final int[] R_CARDS = {0, 512, 832, 384};
    public static final int[] R_CARD_BACK = {832, 512, 64, 96};
    public static final int[] R_SIGN = {896, 512, 128, 64};
    public static final int[] R_CHIP_EDGE = {896, 576, 64, 64};
    public static final int[] R_CHIP_WHITE = {960, 576, 64, 64};
    public static final int[] R_CHIP_TOP = {896, 640, 64, 64};
    public static final int[] R_DARK = {960, 640, 64, 64};
    public static final int[] R_SHOE = {896, 704, 128, 64};
    public static final int[] R_BULB = {832, 608, 64, 64};
    public static final int[] R_GLOVE = {832, 672, 64, 64};
    public static final int[] R_TIN = {832, 736, 64, 64};
    public static final int[] R_CABLE = {832, 800, 64, 64};
    public static final int[] R_CRATE = {896, 768, 128, 128};
    public static final int[] R_WOOD = {0, 896, 256, 128};
    public static final int[] R_RUBBER = {256, 896, 256, 128};
    public static final int[] R_RUST = {512, 896, 256, 128};
    public static final int[] R_DRUM = {768, 896, 256, 128};
    /** Futs du pietement : rayon, et hauteur couverte par leur tuile (le marquage tombe entre les cerclages). */
    public static final float DRUM_R = 4.3f, DRUM_WORLD = 12.1f;

    private static final Mat FELT = FruitsDesign.fit(R_FELT), WOOD = FruitsDesign.tri(R_WOOD, 24), RUBBER = FruitsDesign.tri(R_RUBBER, 16);
    private static final Mat RUST = FruitsDesign.tri(R_RUST, 12), DRUM = FruitsDesign.tri(R_DRUM, DRUM_WORLD), GLOVE = FruitsDesign.tri(R_GLOVE, 8);
    private static final Mat SHOE = FruitsDesign.tri(R_SHOE, 8), DARK = FruitsDesign.tri(R_DARK, 4), SIGN = FruitsDesign.fit(R_SIGN);
    private static final Mat WHITE = FruitsDesign.fit(R_CHIP_WHITE), BULB = FruitsDesign.fit(R_BULB), TIN = FruitsDesign.tri(R_TIN, 6);
    private static final Mat CABLE = FruitsDesign.tri(R_CABLE, 6), CRATE = FruitsDesign.tri(R_CRATE, 12);

    static final float TOP = 13.4f, BACK = -12.5f, TCX = 8f, RX = 22f, RZ = 26f;
    static final float CARD_W = 2.5f, CARD_H = 3.5f;
    static final float[] SHOE_MOUTH = {20.6f, TOP + 1.3f, -6.4f};
    /** Baladeuse pendue a sa potence, au-dessus du coin gauche de la table. */
    static final float[] LAMP = {-5.2f, TOP + 11.4f, -7.4f};
    static final int GOLD_RGB = 0xFFD36A, GREEN = 0x3DF5A0, CHALK = 0xE8E0C8;

    private Mesh body = Mesh.EMPTY, signGlow = Mesh.EMPTY, glove = Mesh.EMPTY, gloveL = Mesh.EMPTY, bulb = Mesh.EMPTY;
    private CardMeshes cards;
    private ChipStacks chips;

    public BlackjackDesign() {
        super("blackjack", GREEN, GOLD_RGB);
        labelY = 25;
        itemScale = 0.26f;
    }

    /** Point du bord arrondi de la table (angle 0 = droite, pi/2 = face au joueur). */
    static float[] edge(double th, float grow) {
        return new float[]{TCX + (RX + grow) * (float) Math.cos(th), BACK + (RZ + grow) * (float) Math.sin(th)};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        int n = seg(40);
        // ---- plateau en demi-lune (tapis sur le dessus, chant en bois)
        float[][] outline = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            double th = Math.PI - Math.PI * i / n;
            float[] e = edge(th, 0);
            outline[i] = new float[]{e[0], -e[1]};
        }
        b.push().translate(0, TOP - 0.8f, 0).rotateX(-90);
        b.slab(outline, 1.6f, 0.2f, true, FELT, WOOD, WOOD);
        b.pop();
        // ---- bourrelet : tuyau de caoutchouc cloue le long de l'arrondi, feuillard d'acier
        float[][] rail = new float[n + 1][];
        float[][] lip = new float[n + 1][];
        float[][] foot = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            double th = Math.PI * i / n;
            float[] e = edge(th, 0.7f);
            rail[i] = p(e[0], TOP + 0.75f, e[1]);
            float[] f = edge(th, -0.35f);
            lip[i] = p(f[0], TOP + 0.8f, f[1]);
            float[] g = edge(th, -3.4f);
            foot[i] = p(g[0], 2.6f, g[1]);
        }
        b.tube(rail, fill(n + 1, 1.05f), seg(12), RUBBER, true, true, 0, false);
        b.tube(lip, fill(n + 1, 0.16f), seg(6), RUST, true, true, 0, false);
        // colliers de fer qui serrent le tuyau
        for (int k = 1; k < 10; k++) {
            double th = Math.PI * k / 10;
            float[] e = edge(th, 0.7f);
            double tang = Math.atan2(RZ * Math.cos(th), -RX * Math.sin(th));
            b.push().translate(e[0], TOP + 0.75f, e[1]).rotateY((float) -Math.toDegrees(tang)).rotateZ(90);
            b.cylinder(1.12f, -0.2f, 0.2f, seg(12), RUST, null, null);
            b.pop();
        }
        // ---- repose-pieds : tube d'echafaudage sur des fers a beton
        b.tube(foot, fill(n + 1, 0.4f), seg(8), RUST, true, true, 0, false);
        for (int k = 1; k < 6; k++) {
            float[] g = edge(Math.PI * k / 6, -3.4f);
            b.push().translate(g[0], 0, g[1]);
            b.cylinder(0.2f, 0, 2.6f, 6, RUST, null, null);
            b.pop();
        }
        // ---- pietement : deux futs, deux caisses empilees, palette
        BunkerKit.pallet(b, -8f, 0, BACK + 1.0f, 24f, 1.2f, 7.6f, 8, WOOD);
        BunkerKit.drum(b, -1.4f, 1.2f, -5.2f, DRUM_R, TOP - 2.0f, seg(22), DRUM, RUST);
        BunkerKit.drum(b, 17.4f, 1.2f, -5.2f, DRUM_R, TOP - 2.0f, seg(22), DRUM, RUST);
        BunkerKit.crate(b, 3.8f, 1.2f, -2.4f, 12.2f, 7.4f, 5.4f, CRATE, WOOD);
        BunkerKit.crate(b, 4.6f, 7.4f, -1.6f, 11.4f, TOP - 0.8f, 4.6f, CRATE, WOOD);
        // ---- cote croupier : bac de tole pour les jetons, caisse de munitions (sabot), boite de fer (defausse), plaque
        b.bevelBox(1.5f, TOP, BACK + 0.4f, 14.5f, TOP + 0.5f, BACK + 3.2f, 0.1f, RUST);
        for (int k = 0; k < 8; k++) {
            float x = 2.3f + k * 1.6f;
            b.bevelBox(x - 0.08f, TOP + 0.5f, BACK + 0.5f, x + 0.08f, TOP + 0.9f, BACK + 3.1f, 0.02f, RUST);
        }
        b.push().translate(21.2f, TOP, -8.6f).rotateY(-20);
        b.bevelBox(-2f, 0, -2.4f, 2f, 1.6f, 2.4f, 0.2f, SHOE);
        b.bevelBox(-1.7f, 1.2f, 2.0f, 1.7f, 2.1f, 2.9f, 0.15f, SHOE);
        b.bevelBox(-1.3f, 0.2f, 2.3f, 1.3f, 1.3f, 2.5f, 0.05f, WHITE);
        // couvercle releve, poignee et fermoir
        b.push().translate(0, 1.6f, -2.4f).rotateX(-62);
        b.bevelBox(-2.05f, 0, 0, 2.05f, 0.3f, 3.6f, 0.1f, SHOE);
        b.pop();
        b.bevelBox(-0.9f, 0.5f, -2.62f, 0.9f, 0.8f, -2.4f, 0.05f, RUST);
        b.pop();
        b.bevelBox(-7.4f, TOP, -10.4f, -3.2f, TOP + 1.8f, -7.2f, 0.15f, TIN);
        b.bevelBox(-7.0f, TOP + 1.8f, -10.0f, -3.6f, TOP + 1.84f, -7.6f, 0, DARK);
        b.push().translate(8, TOP + 1.1f, BACK + 0.25f);
        b.bevelBox(-4.2f, -1.1f, -0.25f, 4.2f, 1.1f, 0.3f, 0.1f, RUST, new Mat[]{null, null, null, null, null, SIGN}, 0);
        b.pop();
        // ---- potence de la baladeuse : tube plante dans un pneu de brouette, bras, cable enroule
        float px = -10.4f, pz = -10.6f;
        b.push().translate(px, TOP, pz);
        b.cylinder(1.2f, 0, 0.7f, seg(12), RUBBER, null, RUBBER);
        b.cylinder(0.3f, 0, 13.4f, seg(8), RUST, null, RUST);
        b.pop();
        b.tube(new float[][]{p(px, TOP + 13.2f, pz), p(px + 2.6f, TOP + 13.7f, pz + 1.6f), p(LAMP[0], TOP + 12.6f, LAMP[2])}, new float[]{0.22f, 0.2f, 0.18f}, seg(6), RUST, true, true, 0,
                false);
        b.bevelBox(LAMP[0] - 0.08f, LAMP[1] + 0.9f, LAMP[2] - 0.08f, LAMP[0] + 0.08f, TOP + 12.6f, LAMP[2] + 0.08f, 0.02f, RUST);
        BunkerKit.cageLamp(b, LAMP[0], LAMP[1], LAMP[2], RUST);
        BunkerKit.cable(b, p(px + 0.3f, TOP + 12.8f, pz), p(px + 0.9f, TOP + 0.4f, pz - 0.9f), 0.5f, 0.12f, CABLE);
        Occlusion.bake(b, 0.5f, 3.5f, 0.4f, 0f);
        body = b.build();

        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(8, TOP + 1.1f, BACK + 0.57f);
        sg.decalZ(SIGN, 0, 0, 0, 8.0f, 1.9f);
        sg.pop();
        signGlow = sg.build();
        MeshBuilder bu = new MeshBuilder();
        bu.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        bu.sphere(0.42f, 8, 5, BULB);
        bu.pop();
        bulb = bu.build();

        cards = new CardMeshes(R_CARDS, FruitsDesign.fit(R_CARD_BACK), WHITE, CARD_W, CARD_H);
        chips = new ChipStacks(0.95f, 0.26f, seg(14));
        glove = glove(false);
        gloveL = glove(true);
    }

    /** Gant de travail en cuir (paume vers le bas, doigts vers -Z). */
    private Mesh glove(boolean left) {
        MeshBuilder b = new MeshBuilder();
        float sx = left ? -1 : 1;
        b.push().scale(1.1f, 0.5f, 1.25f);
        b.sphere(1f, seg(12), seg(8), GLOVE);
        b.pop();
        for (int k = 0; k < 4; k++) {
            float x = (-0.75f + k * 0.5f) * sx;
            float len = k == 0 || k == 3 ? 1.3f : 1.6f;
            b.tube(new float[][]{p(x, 0.05f, -0.8f), p(x * 1.05f, -0.05f, -0.8f - len * 0.6f), p(x * 1.08f, -0.3f, -0.8f - len)},
                    new float[]{0.24f, 0.22f, 0.2f}, seg(8), GLOVE, false, true, 0, false);
        }
        b.tube(new float[][]{p(0.9f * sx, 0, 0.2f), p(1.4f * sx, -0.1f, -0.4f), p(1.6f * sx, -0.3f, -1.1f)}, new float[]{0.28f, 0.24f, 0.21f},
                seg(8), GLOVE, false, true, 0, false);
        b.push().translate(0, 0.05f, 1.3f).rotateX(90);
        b.cylinder(0.85f, -0.2f, 0.7f, seg(12), GLOVE, GLOVE, GLOVE);
        b.pop();
        return b.build();
    }

    private static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    // =================================================================== positions
    /** Position (x, z) de la carte i de la main h du joueur. */
    static float[] playerCard(BlackjackTable t, int h, int i) {
        float base = t.handCount == 1 ? 6.4f : (h == 0 ? 1.8f : 12.2f);
        return new float[]{base + i * 1.15f, 4.8f - i * 0.75f};
    }

    static float[] dealerCard(int i) {
        return new float[]{4.6f + i * 2.05f, -4.6f};
    }

    static float[] betPos(BlackjackTable t, int h) {
        float x = t.handCount == 1 ? 8f : (h == 0 ? 3.6f : 12.4f);
        return new float[]{x, 9.4f};
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, BlackjackView v, Pose p, DrawList out) {
        // baladeuse : elle vacille de temps en temps ; la plaque du croupier luit faiblement dessous
        float lampK = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 100)) > 0.95f ? 0.35f : 0.92f + 0.08f * (float) Math.sin(s.time * 1.1f);
        out.solid(p, bulb, lampK);
        out.solid(p, signGlow, 0.3f * lampK);
        if (!s.item) out.billboard(p, Sprite.HALO, LAMP[0], LAMP[1], LAMP[2], 3.2f, 0, 0xFFC880, 0.32f * lampK);
        BlackjackTable t = v.table;
        long now = s.tick;
        float fnow = now + s.partial;
        // ---- jetons du rack (decor)
        for (int k = 0; k < 8; k++) {
            chips.stackOf(p, out, 3 + (k * 7) % 6, k % 5, 2.3f + k * 1.6f + 0.8f, TOP + 0.5f, BACK + 1.8f);
        }
        float[] glovePos = null;
        if (!s.item) {
            double bp = t.rules().blackjackPays;
            String pays = Math.abs(bp - 1.5) < 0.01 ? "3 CONTRE 2" : Math.abs(bp - 1.2) < 0.01 ? "6 CONTRE 5" : Math.abs(bp - 1.0) < 0.01 ? "1 CONTRE 1" : "x" + bp;
            flatText(p, out, "BLACKJACK PAIE " + pays, 8f, -1.3f, 0.95f, CHALK, 22f, false);
            flatText(p, out, t.rules().dealerHitsSoft17 ? "LE CROUPIER TIRE SUR 17 SOUPLE" : "LE CROUPIER RESTE SUR 17", 8f, 0.3f, 0.62f, 0xC8C0A8, 22f, false);
        }
        if (!s.item && t.phase != BlackjackTable.IDLE) {
            // ---- cartes du joueur
            for (int h = 0; h < t.handCount; h++) {
                for (int i = 0; i < t.handSize[h]; i++) {
                    float[] c = playerCard(t, h, i);
                    float[] gp = card(s, p, out, t.hands[h][i], t.handTicks[h][i], c[0], TOP + 0.09f + i * 0.06f, c[1], 8, false, Long.MIN_VALUE);
                    if (gp != null) glovePos = gp;
                }
            }
            // ---- cartes du croupier (carte cachee retournee au moment voulu)
            for (int i = 0; i < t.dealerSize; i++) {
                float[] c = dealerCard(i);
                int card = t.dealer[i];
                float[] gp = card(s, p, out, card, t.dealerTicks[i], c[0], TOP + 0.09f, c[1], 8, i == 1, t.revealTick);
                if (gp != null) glovePos = gp;
            }
            // ---- mises (jetons) : ramassees ou payees au reglement
            boolean settled = now >= t.settleTick;
            for (int h = 0; h < t.handCount; h++) {
                float[] bp = betPos(t, h);
                long bet = t.bets[h];
                if (!settled) {
                    chips.amount(p, out, bet, bp[0], TOP + 0.1f, bp[1], 2.1f, 3);
                } else {
                    float k = Ease.inOutSine(Ease.range(fnow, t.settleTick, t.settleTick + 14));
                    int res = t.results[h];
                    if (res == BlackjackTable.R_LOSE || res == BlackjackTable.R_BUST) {
                        // la banque ramasse la mise
                        float x = Ease.lerp(bp[0], 8f, k), z = Ease.lerp(bp[1], BACK + 1.8f, k), y = TOP + 0.1f + 3f * Ease.bell(k, 0, 1);
                        if (k < 1) chips.amount(p, out, bet, x, y, z, 2.1f, 3);
                    } else {
                        chips.amount(p, out, bet, bp[0], TOP + 0.1f, bp[1], 2.1f, 3);
                        long gain = t.payouts[h] - bet;
                        if (gain > 0) {
                            float x = Ease.lerp(8f, bp[0] + 0.4f, k), z = Ease.lerp(BACK + 1.8f, bp[1] - 2.4f, k), y = TOP + 0.1f + 3f * Ease.bell(k, 0, 1);
                            chips.amount(p, out, gain, x, y, z, 2.1f, 3);
                        }
                    }
                }
            }
            // ---- totaux et resultats (a plat sur le tapis, face au joueur)
            for (int h = 0; h < t.handCount; h++) {
                int landed = landedCount(t.hands[h], t.handTicks[h], t.handSize[h], now);
                if (landed == 0) continue;
                int total = Cards.total(t.hands[h], landed);
                float[] c = playerCard(t, h, 0);
                String txt = total > 21 ? "SAUTE" : (landed == 2 && total == 21 && t.handCount == 1 ? "BLACKJACK" : String.valueOf(total));
                int col = total > 21 ? 0xFF5A5A : (t.active == h && t.phase == BlackjackTable.PLAYER && t.handCount > 1 ? GOLD_RGB : 0xFFFFFF);
                flatText(p, out, txt, c[0] - 2.6f, c[1] + 0.2f, 1.1f, col, 3.6f, true);
                if (settled) {
                    String res = switch (t.results[h]) {
                        case BlackjackTable.R_WIN -> "GAGNE";
                        case BlackjackTable.R_BLACKJACK -> "BLACKJACK !";
                        case BlackjackTable.R_PUSH -> "EGALITE";
                        default -> "PERDU";
                    };
                    int rc = t.results[h] == BlackjackTable.R_WIN || t.results[h] == BlackjackTable.R_BLACKJACK ? GOLD_RGB
                            : t.results[h] == BlackjackTable.R_PUSH ? 0xCFE8FF : 0xFF6A6A;
                    float[] bp = betPos(t, h);
                    flatText(p, out, res, bp[0], bp[1] - 2.5f, 1.2f, rc);
                }
            }
            int dl = 0;
            for (int i = 0; i < t.dealerSize; i++) if (t.dealerTicks[i] <= now && (i != 1 || now >= t.revealTick + 3)) dl++;
            if (dl > 0) {
                int[] shown = new int[dl];
                int k = 0;
                for (int i = 0; i < t.dealerSize && k < dl; i++) if (t.dealerTicks[i] <= now && (i != 1 || now >= t.revealTick + 3)) shown[k++] = t.dealer[i];
                int total = Cards.total(shown, dl);
                flatText(p, out, total > 21 ? "SAUTE" : String.valueOf(total), 1.6f, -4.6f, 1.1f, total > 21 ? 0xFF5A5A : 0xCFE8FF);
            }
            if (settled && t.totalPayout() > t.totalBet()) {
                out.billboard(p, Sprite.FLARE, 8, TOP + 2, 7, 6f * (1 - Ease.range(fnow, t.settleTick + 20, t.settleTick + 60)), s.time * 2, GOLD_RGB, 0.3f);
            }
        }
        // ---- gants du croupier : modeles doigts vers -Z, retournes pour que les doigts pointent vers le joueur (et
        // un peu vers le milieu de la table) ; celui de droite suit la carte distribuee
        float bob = (float) Math.sin(s.time * 0.08f) * 0.3f;
        float[] rest = {16.5f, TOP + 3.2f + bob, -7.5f};
        float[] g = glovePos != null ? glovePos : rest;
        p.push().translate(g[0], g[1], g[2]).rotY(glovePos != null ? 155 : 165).rotX(glovePos != null ? -8 : 0);
        out.solid(p, glove, 0);
        p.pop();
        p.push().translate(-0.5f, TOP + 3.0f - bob, -7.8f).rotY(195);
        out.solid(p, gloveL, 0);
        p.pop();
    }

    /** Nombre de cartes deja posees. */
    static int landedCount(int[] hand, long[] ticks, int n, long now) {
        int k = 0;
        for (int i = 0; i < n; i++) if (ticks[i] <= now) k++;
        return k;
    }

    /** Texte a plat sur le tapis, lisible depuis la place du joueur. */
    private static void flatText(Pose p, DrawList out, String s, float x, float z, float size, int rgb) {
        flatText(p, out, s, x, z, size, rgb, 7.5f, true);
    }

    private static void flatText(Pose p, DrawList out, String s, float x, float z, float size, int rgb, float maxW, boolean outline) {
        p.push().translate(x, TOP + 0.12f, z).rotX(-90);
        out.text(p, s, 0, 0, 0, fit(s, size, maxW, true), rgb, 1f, DrawList.T_CENTER | DrawList.T_BOLD | (outline ? DrawList.T_OUTLINE : 0), 0x05140C);
        p.pop();
    }

    /**
     * Dessine une carte distribuee : vol depuis le sabot pendant "flight" ticks avant "land", retournement a
     * "reveal" pour la carte cachee.
     * @return position du gant qui l'accompagne si la carte est en vol, sinon null
     */
    private float[] card(Scene s, Pose p, DrawList out, int card, long land, float x, float y, float z, int flight, boolean hole, long reveal) {
        float t = s.since(land);
        if (t < -flight) return null;
        float[] glovePos = null;
        float px = x, py = y, pz = z, flip = 0, yaw = 0;
        if (t < 0) {
            float e = Ease.outCubic((t + flight) / flight);
            px = Ease.lerp(SHOE_MOUTH[0], x, e);
            pz = Ease.lerp(SHOE_MOUTH[2], z, e);
            py = Ease.lerp(SHOE_MOUTH[1], y, e) + 2.2f * (float) Math.sin(Math.PI * e);
            flip = hole ? 180 : 180 * (1 - e);
            yaw = 25 * (1 - e);
            glovePos = new float[]{px + 0.8f, py + 1.4f, pz + 1.2f};
        } else if (hole) {
            float r = s.since(reveal);
            flip = r < 0 ? 180 : 180 * (1 - Ease.inOutSine(Math.min(1, r / 6f)));
            if (r >= 0 && r < 6) py += 1.2f * Ease.bell(r, 0, 6);
        }
        p.push().translate(px, py, pz).rotY(yaw).rotZ(flip);
        out.solid(p, cards.get(card < 0 ? -1 : card), 0);
        p.pop();
        return glovePos;
    }

    // =================================================================== camera
    @Override
    public void camera(BlackjackView v, Scene s, int mode, Shot out) {
        // place du joueur : on voit ses cartes, celles du croupier et les totaux poses sur le tapis
        out.set(TCX, TOP + 22.5f, 30, TCX, TOP, -1, 52);
    }
    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, BlackjackView v, FxSink fx, Random r) {
        BlackjackTable t = v.table;
        if (t.phase == BlackjackTable.IDLE) return;
        long now = s.tick;
        for (int h = 0; h < t.handCount; h++) {
            for (int i = 0; i < t.handSize[h]; i++) {
                if (t.handTicks[h][i] - 8 == now) fx.sound(Snd.CARD_DEAL, SHOE_MOUTH[0], SHOE_MOUTH[1], SHOE_MOUTH[2], 0.8f, 0.9f + r.nextFloat() * 0.2f);
                if (t.handTicks[h][i] == now) {
                    float[] c = playerCard(t, h, i);
                    fx.sound(Snd.CARD_FLIP, c[0], TOP, c[1], 0.6f, 1.1f);
                }
            }
        }
        for (int i = 0; i < t.dealerSize; i++) {
            if (t.dealerTicks[i] - 8 == now) fx.sound(Snd.CARD_DEAL, SHOE_MOUTH[0], SHOE_MOUTH[1], SHOE_MOUTH[2], 0.8f, 0.9f + r.nextFloat() * 0.2f);
        }
        if (t.revealTick == now) {
            float[] c = dealerCard(1);
            fx.sound(Snd.CARD_FLIP, c[0], TOP, c[1], 0.9f, 0.9f);
        }
        if (t.settleTick == now) {
            long pay = t.totalPayout(), bet = t.totalBet();
            if (pay > bet) {
                fx.sound(t.results[0] == BlackjackTable.R_BLACKJACK ? Snd.WIN_BIG : Snd.WIN_SMALL, 8, TOP + 2, 8, 1f, 1f);
                fx.sound(Snd.COINS, 8, TOP, 8, 0.8f, 1f);
                burst(fx, r, 8, TOP + 1.5f, 8, GOLD_RGB, GREEN, 22, 0.9f, 1.1f);
            } else if (pay == bet) {
                fx.sound(Snd.CHIP, 8, TOP, 8, 0.8f, 1f);
            } else {
                fx.sound(Snd.LOSE, 8, TOP, 8, 0.7f, 1f);
            }
        }
        if (t.settleTick + 4 == now) fx.sound(Snd.CHIP, 8, TOP, 4, 0.6f, 1.2f);
    }

    // =================================================================== vues
    @Override
    public BlackjackView newView() {
        return new BlackjackView();
    }

    @Override
    public BlackjackView demoView(float t) {
        BlackjackView v = new BlackjackView();
        BlackjackTable tb = v.table;
        // joueur 10 + 6, tire un 4 (20) et reste ; le croupier (6 + Dame) tire un 9 et saute
        int[] deck = {9, 18, 31, 50, 3, 21};
        final int[] k = {0};
        tb.start(100, DEMO_START, () -> deck[k[0]++ % deck.length]);
        tb.hit(DEMO_START + 45, () -> deck[k[0]++ % deck.length]);
        tb.stand(DEMO_START + 60, () -> deck[k[0]++ % deck.length]);
        return v;
    }

    @Override
    public int demoLength() {
        return 150;
    }
}
