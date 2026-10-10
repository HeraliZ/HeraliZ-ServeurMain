package fr.heraliz.crac.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Dessin des maquettes validées (roues, cartes du HUD, écrans de groupe) : rectangles, dégradés, anneaux, secteurs et
 * traits à des coordonnées libres (les filets font un pixel d'écran, comme sur les maquettes), petites icônes vectorielles,
 * textes posés entre deux pixels. Complète {@link Ui} (charte des écrans de clan).
 */
public final class Dessin {
    private Dessin() {
    }

    /** DM Sans : titres (gras 13), gras 9, textes courants (7,5), petits textes (6,5). */
    public static final ResourceLocation TITRE = police("ui_titre"), GRAS_9 = police("ui_small_bold"), MOYEN = police("ui_moyen"),
            MOYEN_GRAS = police("ui_moyen_bold"), MINI = police("ui_mini"), MINI_GRAS = police("ui_mini_bold");

    /** Couleurs des maquettes (accent = rouge du logo). */
    public static final int ACCENT = 0xE3201B, BLANC = 0xFFFFFF, TEXTE = 0xECEEF2, BOUTON = 0xC9CDD4, GRIS = 0x9AA0AA, ATTENUE = 0x8F95A0,
            PALE = 0x5A5F69, VERT = 0x5FD08A, VERT_PALE = 0x9BE3B3, ROUGE = 0xFF6B6B, ROUGE_PALE = 0xFF8A80, ROSE = 0xFFB3AC,
            OR = 0xE8C36A, NUIT = 0x050608, VERRE = 0x0D0F14;

    private static ResourceLocation police(String nom) {
        return new ResourceLocation("crac", nom);
    }

    /** Couleur RVB avec une opacité de 0 à 255 (bornée). */
    public static int a(int rgb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha))) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        return Ui.mix(a, b, Math.max(0, Math.min(1, t)));
    }

    // ------------------------------------------------------------------ formes

    private static void sommet(VertexConsumer vc, Matrix4f m, float x, float y, int argb) {
        vc.vertex(m, x, y, 0).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).endVertex();
    }

    /** Un quadrilatère, coins haut-gauche, bas-gauche, bas-droit, haut-droit (le sens que garde Minecraft). */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int hg, int bg, int bd, int hd) {
        sommet(vc, m, x0, y0, hg);
        sommet(vc, m, x0, y1, bg);
        sommet(vc, m, x1, y1, bd);
        sommet(vc, m, x1, y0, hd);
    }

    private static VertexConsumer tampon(GuiGraphics g) {
        return g.bufferSource().getBuffer(RenderType.gui());
    }

    public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || argb >>> 24 == 0) {
            return;
        }
        quad(tampon(g), g.pose().last().pose(), x0, y0, x1, y1, argb, argb, argb, argb);
        g.flush();
    }

    /** Dégradé horizontal, de gauche à droite. */
    public static void degradeH(GuiGraphics g, float x0, float y0, float x1, float y1, int gauche, int droite) {
        if (x1 <= x0 || y1 <= y0 || (gauche >>> 24 == 0 && droite >>> 24 == 0)) {
            return;
        }
        quad(tampon(g), g.pose().last().pose(), x0, y0, x1, y1, gauche, gauche, droite, droite);
        g.flush();
    }

    /** Dégradé vertical, de haut en bas. */
    public static void degradeV(GuiGraphics g, float x0, float y0, float x1, float y1, int haut, int bas) {
        if (x1 <= x0 || y1 <= y0 || (haut >>> 24 == 0 && bas >>> 24 == 0)) {
            return;
        }
        quad(tampon(g), g.pose().last().pose(), x0, y0, x1, y1, haut, bas, bas, haut);
        g.flush();
    }

    /** Contour intérieur d'épaisseur e (le « inset 0 0 0 1px » des maquettes avec e = un pixel d'écran). */
    public static void contour(GuiGraphics g, float x, float y, float w, float h, float e, int argb) {
        if (argb >>> 24 == 0 || w <= 0 || h <= 0) {
            return;
        }
        VertexConsumer vc = tampon(g);
        Matrix4f m = g.pose().last().pose();
        quad(vc, m, x, y, x + w, y + e, argb, argb, argb, argb);
        quad(vc, m, x, y + h - e, x + w, y + h, argb, argb, argb, argb);
        quad(vc, m, x, y + e, x + e, y + h - e, argb, argb, argb, argb);
        quad(vc, m, x + w - e, y + e, x + w, y + h - e, argb, argb, argb, argb);
        g.flush();
    }

    /**
     * Secteur d'anneau entre les angles {@code a0} et {@code a1} (radians depuis midi, sens horaire), entre les rayons
     * {@code rInt} et {@code rExt}.
     */
    public static void secteur(GuiGraphics g, float cx, float cy, float rInt, float rExt, double a0, double a1, int argb) {
        if (a1 <= a0 || argb >>> 24 == 0) {
            return;
        }
        int n = Math.max(2, (int) Math.ceil((a1 - a0) / (2 * Math.PI) * 64 * Math.max(1, rExt / 8)));
        VertexConsumer vc = tampon(g);
        Matrix4f m = g.pose().last().pose();
        float s0 = (float) Math.sin(a0), c0 = (float) Math.cos(a0);
        for (int i = 1; i <= n; i++) {
            double t = a0 + (a1 - a0) * i / n;
            float s1 = (float) Math.sin(t), c1 = (float) Math.cos(t);
            sommet(vc, m, cx + rExt * s0, cy - rExt * c0, argb);
            sommet(vc, m, cx + rInt * s0, cy - rInt * c0, argb);
            sommet(vc, m, cx + rInt * s1, cy - rInt * c1, argb);
            sommet(vc, m, cx + rExt * s1, cy - rExt * c1, argb);
            s0 = s1;
            c0 = c1;
        }
        g.flush();
    }

    /** Contour d'un secteur (arcs intérieur et extérieur, bords droits), d'épaisseur e. */
    public static void contourSecteur(GuiGraphics g, float cx, float cy, float rInt, float rExt, double a0, double a1, float e, int argb) {
        secteur(g, cx, cy, rExt - e, rExt, a0, a1, argb);
        secteur(g, cx, cy, rInt, rInt + e, a0, a1, argb);
        for (double t : new double[]{a0, a1}) {
            float s = (float) Math.sin(t), c = (float) Math.cos(t);
            trait(g, cx + rInt * s, cy - rInt * c, cx + rExt * s, cy - rExt * c, e, argb);
        }
    }

    public static void anneau(GuiGraphics g, float cx, float cy, float rInt, float rExt, int argb) {
        secteur(g, cx, cy, rInt, rExt, 0, 2 * Math.PI, argb);
    }

    /** Arc d'anneau de la fraction {@code de} à la fraction {@code a} d'un tour (0 = midi, sens horaire). */
    public static void arc(GuiGraphics g, float cx, float cy, float rInt, float rExt, float de, float a, int argb) {
        secteur(g, cx, cy, rInt, rExt, de * 2 * Math.PI, a * 2 * Math.PI, argb);
    }

    public static void disque(GuiGraphics g, float cx, float cy, float r, int argb) {
        secteur(g, cx, cy, 0, r, 0, 2 * Math.PI, argb);
    }

    /** Halo elliptique : la couleur au centre, transparent au bord. */
    public static void halo(GuiGraphics g, float cx, float cy, float rx, float ry, int argb) {
        if (argb >>> 24 == 0 || rx <= 0 || ry <= 0) {
            return;
        }
        int bord = argb & 0xFFFFFF;
        int n = 40;
        VertexConsumer vc = tampon(g);
        Matrix4f m = g.pose().last().pose();
        for (int i = 0; i < n; i++) {
            double t0 = i * 2 * Math.PI / n, t1 = (i + 1) * 2 * Math.PI / n;
            sommet(vc, m, cx + rx * (float) Math.sin(t0), cy - ry * (float) Math.cos(t0), bord);
            sommet(vc, m, cx, cy, argb);
            sommet(vc, m, cx, cy, argb);
            sommet(vc, m, cx + rx * (float) Math.sin(t1), cy - ry * (float) Math.cos(t1), bord);
        }
        g.flush();
    }

    /** Trait droit d'épaisseur e. */
    public static void trait(GuiGraphics g, float x0, float y0, float x1, float y1, float e, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4F || argb >>> 24 == 0) {
            return;
        }
        float nx = -dy / l * e / 2, ny = dx / l * e / 2;
        VertexConsumer vc = tampon(g);
        Matrix4f m = g.pose().last().pose();
        sommet(vc, m, x0 - nx, y0 - ny, argb);
        sommet(vc, m, x0 + nx, y0 + ny, argb);
        sommet(vc, m, x1 + nx, y1 + ny, argb);
        sommet(vc, m, x1 - nx, y1 - ny, argb);
        g.flush();
    }

    // ------------------------------------------------------------------ petites icônes

    /** Coche dans un carré de côté s (coin haut-gauche x, y). */
    public static void coche(GuiGraphics g, float x, float y, float s, float e, int argb) {
        float bx = x + s * 0.38F, by = y + s * 0.9F;
        trait(g, x, y + s * 0.55F, bx, by, e, argb);
        trait(g, bx, by, x + s, y + s * 0.12F, e, argb);
        disque(g, bx, by, e / 2, argb);
    }

    /** Croix centrée, de demi-côté r. */
    public static void croix(GuiGraphics g, float cx, float cy, float r, float e, int argb) {
        trait(g, cx - r, cy - r, cx + r, cy + r, e, argb);
        trait(g, cx - r, cy + r, cx + r, cy - r, e, argb);
    }

    /** Deux marteaux croisés (réparer), centrés, dans un carré de demi-côté r. */
    public static void marteaux(GuiGraphics g, float cx, float cy, float r, int argb) {
        float e = r * 0.2F;
        for (int s = -1; s <= 1; s += 2) {
            // manche en diagonale, tête perpendiculaire au bout du haut
            float hx = cx + s * r * 0.62F, hy = cy - r * 0.62F;
            trait(g, cx - s * r * 0.85F, cy + r * 0.85F, hx, hy, e, argb);
            float tx = r * 0.42F, ty = r * 0.42F;
            trait(g, hx - s * tx, hy - ty, hx + s * tx, hy + ty, e * 2.1F, argb);
        }
    }

    /** Étoile à cinq branches (nommer chef). */
    public static void etoile(GuiGraphics g, float cx, float cy, float r, int argb) {
        if (argb >>> 24 == 0) {
            return;
        }
        float ri = r * 0.45F;
        VertexConsumer vc = tampon(g);
        Matrix4f m = g.pose().last().pose();
        for (int i = 0; i < 10; i++) {
            double t0 = i * Math.PI / 5, t1 = (i + 1) * Math.PI / 5;
            float r0 = i % 2 == 0 ? r : ri, r1 = i % 2 == 0 ? ri : r;
            sommet(vc, m, cx + r0 * (float) Math.sin(t0), cy - r0 * (float) Math.cos(t0), argb);
            sommet(vc, m, cx, cy, argb);
            sommet(vc, m, cx, cy, argb);
            sommet(vc, m, cx + r1 * (float) Math.sin(t1), cy - r1 * (float) Math.cos(t1), argb);
        }
        g.flush();
    }

    /** Cadenas (raid block) dans un carré de côté s (coin haut-gauche x, y). */
    public static void cadenas(GuiGraphics g, float x, float y, float s, int argb) {
        float e = s * 0.1F;
        float cx = x + s / 2, r = s * 0.27F, top = y + s * 0.3F;
        // anse : demi-anneau sur deux montants
        secteur(g, cx, top, r - e, r, -Math.PI / 2, Math.PI / 2, argb);
        rect(g, cx - r, top, cx - r + e, y + s * 0.45F, argb);
        rect(g, cx + r - e, top, cx + r, y + s * 0.45F, argb);
        // corps
        rect(g, x + s * 0.1F, y + s * 0.4F, x + s * 0.9F, y + s * 0.97F, argb);
    }

    // ------------------------------------------------------------------ panneaux et boutons

    /** Panneau de verre des maquettes : fond sombre, contour d'un pixel, liseré clair en haut. */
    public static void verre(GuiGraphics g, float x, float y, float w, float h, float px, int alpha) {
        rect(g, x, y, x + w, y + h, a(VERRE, alpha));
        contour(g, x, y, w, h, px, a(BLANC, 26));
        rect(g, x + px, y + px, x + w - px, y + 2 * px, a(BLANC, 36));
    }

    /** Fond d'un bouton (secondaire, ou rouge pour le principal) ; survol de 0 à 1. */
    public static void bouton(GuiGraphics g, float x, float y, float w, float h, boolean principal, float survol, float px) {
        if (principal) {
            degradeH(g, x, y, x + w, y + h, a(ACCENT, 77 + 34 * survol), a(ACCENT, 31 + 26 * survol));
            contour(g, x, y, w, h, px, a(ACCENT, 140 + 60 * survol));
        } else {
            rect(g, x, y, x + w, y + h, a(BLANC, 13 + 9 * survol));
            contour(g, x, y, w, h, px, a(BLANC, 36 + 30 * survol));
        }
    }

    /** Bouton teinté d'une couleur (Exclure, Quitter : rouge pâle ; Ajouter : vert) ; survol de 0 à 1. */
    public static void boutonTeinte(GuiGraphics g, float x, float y, float w, float h, int rgb, float survol, float px) {
        rect(g, x, y, x + w, y + h, a(survol > 0 ? mix(BLANC, rgb, 0.5F) : BLANC, 13 + 12 * survol));
        contour(g, x, y, w, h, px, a(rgb, 102 + 70 * survol));
    }

    /** Touche du clavier (pastille claire) centrée sur cy ; rend sa largeur. */
    public static float touche(GuiGraphics g, Font font, String texte, float x, float cy, float h, float espace, float minW, float px) {
        String up = texte.toUpperCase(Locale.ROOT);
        float tw = Ui.spacedWidth(font, up, MINI_GRAS, espace);
        float w = Math.max(minW, tw + 4.67F);
        float y = cy - h / 2;
        rect(g, x, y, x + w, y + h, a(BLANC, 31));
        contour(g, x, y, w, h, px, a(BLANC, 61));
        Ui.spaced(g, font, up, MINI_GRAS, x + (w - tw) / 2, cy - 4.21F, a(BLANC, 255), espace);
        return w;
    }

    public static float largeurTouche(Font font, String texte, float espace, float minW) {
        return Math.max(minW, Ui.spacedWidth(font, texte.toUpperCase(Locale.ROOT), MINI_GRAS, espace) + 4.67F);
    }

    /** Pastille teintée (Chef) en petites capitales ; rend sa largeur. */
    public static float pastille(GuiGraphics g, Font font, String texte, float x, float cy, int rgb, float px) {
        String up = texte.toUpperCase(Locale.ROOT);
        float tw = Ui.spacedWidth(font, up, MINI, 0.56F);
        float w = tw + 6, h = 8;
        float y = cy - h / 2;
        rect(g, x, y, x + w, y + h, a(rgb, 34));
        contour(g, x, y, w, h, px, a(rgb, 90));
        Ui.spaced(g, font, up, MINI, x + 3, cy - 4.21F, a(mix(rgb, BLANC, 0.3F), 255), 0.56F);
        return w;
    }

    // ------------------------------------------------------------------ textes

    /** Texte à une position libre (rien sous une opacité de 4 : Minecraft le dessinerait opaque). */
    public static void texte(GuiGraphics g, Font font, Component c, float x, float y, int argb) {
        if (argb >>> 24 < 4) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    public static void texte(GuiGraphics g, Font font, Component c, float x, float y, float echelle, int argb) {
        if (argb >>> 24 < 4) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(echelle, echelle, 1);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Texte centré sur cx. */
    public static void centre(GuiGraphics g, Font font, Component c, float cx, float y, int argb) {
        texte(g, font, c, cx - font.width(c) / 2F, y, argb);
    }

    /** Capitales espacées, bord gauche en x ; rend leur largeur. */
    public static float caps(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, float espace, int argb) {
        String up = s.toUpperCase(Locale.ROOT);
        if (argb >>> 24 >= 4) {
            Ui.spaced(g, font, up, f, x, y, argb, espace);
        }
        return Ui.spacedWidth(font, up, f, espace);
    }

    /** Capitales espacées dessinées à l'échelle {@code echelle} (l'espacement reste en unités d'écran) ; rend leur largeur. */
    public static float caps(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, float echelle, float espace, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(echelle, echelle, 1);
        float w = caps(g, font, s, f, 0, 0, espace / echelle, argb);
        g.pose().popPose();
        return w * echelle;
    }

    /** Capitales espacées centrées sur cx ; rend leur largeur. */
    public static float capsCentre(GuiGraphics g, Font font, String s, ResourceLocation f, float cx, float y, float espace, int argb) {
        float w = largeurCaps(font, s, f, espace);
        caps(g, font, s, f, cx - w / 2, y, espace, argb);
        return w;
    }

    public static float largeurCaps(Font font, String s, ResourceLocation f, float espace) {
        return Ui.spacedWidth(font, s.toUpperCase(Locale.ROOT), f, espace);
    }

    public static MutableComponent lit(String s, ResourceLocation f) {
        return Ui.lit(s, f);
    }

    public static float largeur(Font font, String s, ResourceLocation f) {
        return font.width(Ui.lit(s, f));
    }
}
