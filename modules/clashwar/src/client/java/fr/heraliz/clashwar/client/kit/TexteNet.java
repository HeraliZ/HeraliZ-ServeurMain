package fr.heraliz.clashwar.client.kit;

import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.PoliceHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Texte net d'une police du jeu, toujours à sa vraie taille (jamais de {@code pose().scale}), posé sur un pixel entier de
 * l'écran et sur le milieu de ses capitales : la méthode de {@code TexteHud} du noyau, pour n'importe quelle police
 * (polices {@code ui_bold_N} du ClashWar, {@code Ui.FONT} / {@code BOLD} / {@code SMALL}, polices HUD du noyau).
 * <p>Préparé une fois ({@link #texte} ne recalcule que si le texte change), dessiné sans allocation : les lettres sont
 * envoyées au tampon sans le vider, l'appelant vide une fois ({@code g.flush()}) après tous ses textes. Ombre en option :
 * un pixel d'écran, noir à 55 % (comme {@code TexteHud}).
 */
public final class TexteNet {
    /** Alignements. */
    public static final int GAUCHE = -1, CENTRE = 0, DROITE = 1;
    private static final float OMBRE = 0.55f;
    private static final int LUMIERE = 15728880;

    private final ResourceLocation police;
    /** Milieu des capitales sous le point de dessin (unités). */
    private final float milieu;
    private String source;
    private FormattedCharSequence sequence = FormattedCharSequence.EMPTY;
    private float largeur;
    private boolean vide = true;

    private TexteNet(ResourceLocation police, float milieu) {
        this.police = police;
        this.milieu = milieu;
    }

    /**
     * Police au format des polices d'interface (DM Sans, décalage vertical 1) : {@code Ui.FONT} et {@code Ui.BOLD}
     * (taille 11), {@code Ui.SMALL} (9), {@code heraliz_clashwar:ui_bold_N} et {@code ui_N} (N). Minecraft 1.20.1 pose une
     * lettre TTF à y + ascendante + 2 x décalage - 3 ; DM Sans : milieu des capitales = taille x 0,49309 - 1.
     */
    public static TexteNet ui(ResourceLocation police, float tailleJson) {
        return new TexteNet(police, tailleJson * 0.49309f - 1f);
    }

    /** Police de HUD du noyau ({@link PoliceHud}, sans décalage). */
    public static TexteNet hud(PoliceHud p) {
        return new TexteNet(p.id, p.milieu);
    }

    private Style style() {
        return Style.EMPTY.withFont(police);
    }

    /** Fixe le texte (recalcul seulement s'il a changé). */
    public TexteNet texte(String s) {
        if (s == null) s = "";
        if (s.equals(source)) return this;
        source = s;
        vide = s.isEmpty();
        sequence = Component.literal(s).withStyle(style()).getVisualOrderText();
        largeur = Minecraft.getInstance().font.getSplitter().stringWidth(sequence);
        return this;
    }

    /** Fixe le texte, coupé à {@code max} unités (son début suivi de « … ») s'il ne tient pas. Préparation seulement. */
    public TexteNet texte(String s, float max) {
        return texte(couper(s, max));
    }

    /** Le texte s'il tient dans {@code max} unités dans cette police, sinon son début suivi de « … ». */
    public String couper(String s, float max) {
        if (s == null) return "";
        StringSplitter sp = Minecraft.getInstance().font.getSplitter();
        Style st = style();
        if (sp.stringWidth(FormattedText.of(s, st)) <= max) return s;
        float points = sp.stringWidth(FormattedText.of("…", st));
        return sp.plainHeadByWidth(s, (int) Math.max(0, max - points), st).stripTrailing() + "…";
    }

    /** Lignes d'au plus {@code max} unités (mots entiers), {@code lignesMax} au plus (la dernière finit par « … »). */
    public List<String> lignes(String s, float max, int lignesMax) {
        List<String> out = new ArrayList<>(lignesMax);
        if (s == null || s.isBlank()) return out;
        List<FormattedText> l = Minecraft.getInstance().font.getSplitter().splitLines(s.trim(), (int) max, style());
        for (int i = 0; i < l.size() && out.size() < lignesMax; i++) {
            String ligne = l.get(i).getString().trim();
            if (out.size() == lignesMax - 1 && i < l.size() - 1) ligne = couper(ligne + " …", max);
            out.add(ligne);
        }
        return out;
    }

    /** Largeur d'un texte dans cette police (préparation seulement : alloue). */
    public float mesurer(String s) {
        return s == null ? 0f : Minecraft.getInstance().font.getSplitter().stringWidth(FormattedText.of(s, style()));
    }

    public String source() {
        return source == null ? "" : source;
    }

    public float largeur() {
        return largeur;
    }

    public boolean vide() {
        return vide;
    }

    /** Ajoute le texte au tampon : milieu des capitales sur {@code cy}, aligné sur {@code x} ({@link #GAUCHE}...). */
    public void ajouter(GuiGraphics g, float x, float cy, int argb, int align, boolean ombre) {
        if (vide || (argb >>> 24) < 4) return;
        float x0 = align < 0 ? x : align == 0 ? x - largeur / 2f : x - largeur;
        float y = cy - milieu;
        Font font = Minecraft.getInstance().font;
        Matrix4f m = g.pose().last().pose();
        MultiBufferSource.BufferSource tampon = g.bufferSource();
        if (ombre) {
            int a = Math.round((argb >>> 24) * OMBRE);
            if (a >= 4) {
                float px = Formes.pixel();
                font.drawInBatch(sequence, calerX(m, x0 + px), calerY(m, y + px), a << 24, false, m, tampon,
                        Font.DisplayMode.NORMAL, 0, LUMIERE);
            }
        }
        font.drawInBatch(sequence, calerX(m, x0), calerY(m, y), argb, false, m, tampon, Font.DisplayMode.NORMAL, 0, LUMIERE);
    }

    /** x posé sur un pixel entier de l'écran, compte tenu de la matrice courante (comme TexteHud). */
    private static float calerX(Matrix4f m, float x) {
        double s = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        float e = m.m00() * x + m.m30();
        float r = (float) (Math.round(e * s) / s);
        return m.m00() == 0f ? x : x + (r - e) / m.m00();
    }

    private static float calerY(Matrix4f m, float y) {
        double s = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        float e = m.m11() * y + m.m31();
        float r = (float) (Math.round(e * s) / s);
        return m.m11() == 0f ? y : y + (r - e) / m.m11();
    }
}
