package fr.heralisz.matchs.duels.client.bombe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.DuelsConfig;
import fr.heralisz.matchs.duels.bombe.OrdinateurDuel;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ce que l'écran de l'ordinateur affiche en direct, par-dessus sa texture (outils/bombe/ecrans.py laisse ces parties
 * vides) : amorçage (pourcentage, chiffres tapés, case active, coches, barre), amorcée (compte à rebours, barre du
 * temps restant), piratage (secondes restantes, anneau, barre). Coordonnées en pixels de l'image d'écran (536 x 360),
 * les mêmes que dans ecrans.py ; texte en DM Sans (police des interfaces), plein éclat. Léger : quelques quads et au
 * plus 13 textes, seulement à moins de {@link #DISTANCE} blocs.
 */
final class EcranVif {
    private EcranVif() {
    }

    /** Au-delà, l'écran est trop petit pour être lu : rien n'est dessiné en direct. */
    static final double DISTANCE = 20.0;

    // ------------------------------------------------------------------ place de l'image sur le modèle (pixels de modèle)
    /** Os « ecran » : pivot et ouverture (outils/bombe/modeles.py, ordi_noir). */
    private static final float PIVOT_Y = 1.0f, PIVOT_Z = 3.6f, OUVERTURE = 12f;
    /** Coin haut gauche de l'image sur la face avant de l'écran, taille de l'image, décalage vers l'avant. */
    private static final float COIN_X = 4.75f, COIN_Y = 7.75f, FACE_Z = 3.6f, LARGEUR = 9.5f, HAUTEUR = 5.9f, AVANT = 0.04f;
    private static final float IMG_L = 536f, IMG_H = 360f;

    // ------------------------------------------------------------------ couleurs du thème « noir » (ecrans.py)
    private static final int ACCENT = 0xFF7A1A, TEXTE = 0xFFECDC, OK = 0x50FFAA, DANGER = 0xFF3428, PIRATE = 0x3CDCFF, ACTIF = 0x4D2508,
            COMPTE = 0xFFEBE1;
    /** Codes tapés pendant l'amorçage (ecrans.CODES). */
    private static final String CODES = "739140821657";
    /** Hauteur des chiffres de DM Sans (police à 11) en unités de police. */
    private static final float HAUTEUR_CHIFFRE = 7.9f;

    private static float largeurReference = -1f;
    private static String dernierTexte = "";
    private static Component dernierComposant = Component.empty();

    /**
     * Dessine l'écran en direct. {@code ps} est dans le repère du modèle, après l'orientation et le décalage de 0,01 bloc
     * de GeckoLib.
     */
    static void dessiner(OrdinateurDuel o, PoseStack ps, MultiBufferSource tampons, float partial) {
        int etat = o.etat();
        if (etat != OrdinateurDuel.AMORCAGE && etat != OrdinateurDuel.AMORCEE && etat != OrdinateurDuel.PIRATAGE) return;
        ps.pushPose();
        // os de l'écran ouvert, puis coin de l'image, face avant tournée vers nous : x vers la droite, y vers le bas
        ps.translate(0, PIVOT_Y / 16f, PIVOT_Z / 16f);
        ps.mulPose(Axis.XP.rotationDegrees(OUVERTURE));
        ps.translate(0, -PIVOT_Y / 16f, -PIVOT_Z / 16f);
        ps.translate(COIN_X / 16f, COIN_Y / 16f, (FACE_Z - AVANT) / 16f);
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.scale(LARGEUR / IMG_L / 16f, -HAUTEUR / IMG_H / 16f, 1f / 16f);
        Matrix4f m = ps.last().pose();
        VertexConsumer q = tampons.getBuffer(TypesRendu.COULEUR_ECRAN);
        Font police = Minecraft.getInstance().font;
        TEXTES.clear();
        try {
            switch (etat) {
                case OrdinateurDuel.AMORCAGE -> amorcage(o.progression(partial), m, q, police, ps, tampons);
                case OrdinateurDuel.AMORCEE -> amorcee(o.resteTicks(partial), m, q, police, ps, tampons);
                default -> piratage(o.progression(partial), m, q, police, ps, tampons);
            }
            // les textes APRES tous les quads : demander le tampon de la police termine celui des quads (MultiBufferSource
            // immediat) ; un quad ecrit ensuite dans le tampon termine faisait planter le jeu (« Not building! ») des le
            // premier chiffre tape (Matchs 0.7.0 a 0.9.0 : plantage a l'amorcage de la bombe)
            for (Texte t : TEXTES) dessinerTexte(police, ps, tampons, t);
        } finally {
            TEXTES.clear();
            ps.popPose();
        }
    }

    private static void amorcage(float r, Matrix4f m, VertexConsumer q, Font police, PoseStack ps, MultiBufferSource tampons) {
        int tapes = Math.min(12, (int) (r * 12f + 1e-4f));
        boolean curseur = (Util.getMillis() / 250L) % 2L == 0L;
        for (int i = 0; i < 3; i++) {
            float y = 74 + 60 * i;
            for (int k = 0; k < 4; k++) {
                int n = i * 4 + k;
                float x = 70 + 62 * k;
                if (n == tapes) {
                    // case active : fond orange sombre, cadre orange, curseur qui clignote
                    rect(m, q, x + 3, y + 3, x + 49, y + 43, ACTIF);
                    rect(m, q, x, y, x + 52, y + 4, ACCENT);
                    rect(m, q, x, y + 42, x + 52, y + 46, ACCENT);
                    rect(m, q, x, y, x + 4, y + 46, ACCENT);
                    rect(m, q, x + 48, y, x + 52, y + 46, ACCENT);
                    if (curseur) rect(m, q, x + 16, y + 34, x + 36, y + 39, ACCENT);
                } else if (n < tapes) {
                    texte(police, ps, tampons, CODES.substring(n, n + 1), x + 26, y + 23, 30, TEXTE, 1);
                }
            }
            if (tapes >= 4 * (i + 1)) {
                trait(m, q, 340, y + 24, 352, y + 36, 7, OK);
                trait(m, q, 352, y + 36, 374, y + 10, 7, OK);
            }
        }
        barre(m, q, r, ACCENT);
        texte(police, ps, tampons, Math.round(r * 100) + "%", 520, 23, 25, ACCENT, 2);
    }

    private static void amorcee(float resteTicks, Matrix4f m, VertexConsumer q, Font police, PoseStack ps, MultiBufferSource tampons) {
        int s = (int) Math.ceil(resteTicks / 20f);
        String t = String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        // taille fixe (d'après « 0:00 ») : le texte ne change pas de taille quand les chiffres changent
        if (largeurReference <= 0) largeurReference = police.width(Ui.lit("0:00", Ui.BOLD));
        float hauteur = Math.min(92f, 250f / Math.max(1f, largeurReference) * HAUTEUR_CHIFFRE);
        texte(police, ps, tampons, t, 396, 173, hauteur, COMPTE, 1);
        float part = Math.max(0f, Math.min(1f, resteTicks / DuelsConfig.RD_MINUTERIE_TICKS));
        if (part > 0) rect(m, q, 260, 326, 260 + 260 * part, 344, DANGER);
    }

    private static void piratage(float r, Matrix4f m, VertexConsumer q, Font police, PoseStack ps, MultiBufferSource tampons) {
        float reste = (1f - r) * DuelsConfig.RD_DESAMORCAGE_TICKS / 20f;
        texte(police, ps, tampons, String.format(Locale.ROOT, "%.1f s", reste), 520, 23, 25, PIRATE, 2);
        if (r > 0) anneau(m, q, 418, 172, 72, 88, -90f, -90f + 360f * r, PIRATE);
        barre(m, q, r, PIRATE);
    }

    // ------------------------------------------------------------------ primitives (pixels de l'image d'écran)
    /** Barre de 20 cases (cadre peint dans la texture) : cases pleines jusqu'à la progression. */
    private static void barre(Matrix4f m, VertexConsumer q, float r, int couleur) {
        float w = (520f - 16f - 12f) / 20f;
        for (int i = 0; i < 20; i++) {
            if ((i + 1) / 20f > r + 1e-6f) break;
            float xa = 16 + 6 + i * w;
            rect(m, q, xa + 2, 306, xa + w - 3, 340, couleur);
        }
    }

    private static void rect(Matrix4f m, VertexConsumer q, float x0, float y0, float x1, float y1, int rgb) {
        sommet(q, m, x0, y0, rgb);
        sommet(q, m, x0, y1, rgb);
        sommet(q, m, x1, y1, rgb);
        sommet(q, m, x1, y0, rgb);
    }

    /** Trait épais de (x0, y0) à (x1, y1). */
    private static void trait(Matrix4f m, VertexConsumer q, float x0, float y0, float x1, float y1, float epaisseur, int rgb) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-3f) return;
        float nx = -dy / l * epaisseur / 2, ny = dx / l * epaisseur / 2;
        // prolongé d'une demi-épaisseur aux deux bouts : les deux traits de la coche se rejoignent sans trou
        float ex = dx / l * epaisseur / 2, ey = dy / l * epaisseur / 2;
        sommet(q, m, x0 - ex + nx, y0 - ey + ny, rgb);
        sommet(q, m, x0 - ex - nx, y0 - ey - ny, rgb);
        sommet(q, m, x1 + ex - nx, y1 + ey - ny, rgb);
        sommet(q, m, x1 + ex + nx, y1 + ey + ny, rgb);
    }

    /** Arc d'anneau (angles en degrés, 0 = à droite, sens horaire à l'écran). */
    private static void anneau(Matrix4f m, VertexConsumer q, float cx, float cy, float r0, float r1, float a0, float a1, int rgb) {
        int n = Math.max(1, (int) Math.ceil(Math.abs(a1 - a0) / 6f));
        for (int i = 0; i < n; i++) {
            double b0 = Math.toRadians(a0 + (a1 - a0) * i / n), b1 = Math.toRadians(a0 + (a1 - a0) * (i + 1) / n);
            float c0 = (float) Math.cos(b0), s0 = (float) Math.sin(b0), c1 = (float) Math.cos(b1), s1 = (float) Math.sin(b1);
            sommet(q, m, cx + r0 * c0, cy + r0 * s0, rgb);
            sommet(q, m, cx + r1 * c0, cy + r1 * s0, rgb);
            sommet(q, m, cx + r1 * c1, cy + r1 * s1, rgb);
            sommet(q, m, cx + r0 * c1, cy + r0 * s1, rgb);
        }
    }

    private static void sommet(VertexConsumer q, Matrix4f m, float x, float y, int rgb) {
        q.vertex(m, x, y, 0f).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private record Texte(String s, float x, float yc, float hauteur, int rgb, int align) {
    }

    /** Textes de l'image en cours, dessinés après les quads (voir {@link #dessiner}). */
    private static final List<Texte> TEXTES = new ArrayList<>();

    /**
     * Texte en DM Sans gras, centré verticalement sur {@code yc}, de {@code hauteur} pixels d'image (hauteur des
     * chiffres) ; align : 0 = à gauche de x, 1 = centré, 2 = à droite. Un peu devant les quads. Mis en attente : dessiné
     * après tous les quads de l'écran.
     */
    private static void texte(Font police, PoseStack ps, MultiBufferSource tampons, String s, float x, float yc, float hauteur, int rgb, int align) {
        TEXTES.add(new Texte(s, x, yc, hauteur, rgb, align));
    }

    private static void dessinerTexte(Font police, PoseStack ps, MultiBufferSource tampons, Texte t) {
        String s = t.s();
        float x = t.x(), yc = t.yc(), hauteur = t.hauteur();
        int rgb = t.rgb(), align = t.align();
        Component c = composant(s);
        float sc = hauteur / HAUTEUR_CHIFFRE;
        float w = police.width(c) * sc;
        float x0 = align == 1 ? x - w / 2 : align == 2 ? x - w : x;
        ps.pushPose();
        ps.translate(x0, yc - 4.6f * sc, 0.03f);
        ps.scale(sc, sc, 1f);
        police.drawInBatch(c, 0, 0, 0xFF000000 | rgb, false, ps.last().pose(), tampons, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    /** Composant du texte (le dernier est gardé : le compte à rebours ne change qu'une fois par seconde). */
    private static Component composant(String s) {
        if (s.equals(dernierTexte)) return dernierComposant;
        Component c = Ui.lit(s, Ui.BOLD);
        if (s.length() > 1) {
            dernierTexte = s;
            dernierComposant = c;
        }
        return c;
    }
}
