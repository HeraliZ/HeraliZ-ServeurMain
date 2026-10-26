package fr.heraliz.noyau.client.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Texte de HUD en cache (refonte du 25/10, docs/HUD_REFONTE.md) : une police HUD à sa vraie taille ({@link PoliceHud},
 * jamais de {@code pose().scale}), posé sur un pixel entier de l'écran (net), placé sur le milieu de ses capitales ou sur
 * sa ligne de base, aligné à gauche, au centre ou à droite, avec une ombre douce d'un pixel d'écran (lisible sur le
 * monde). Le texte n'est recalculé que s'il change ({@link #texte} compare la chaîne ; {@link #composant} n'est rappelé
 * par l'appelant que si ce qu'il montre a changé) : une image n'alloue rien de plus que le rendu des lettres du jeu. Les
 * lettres sont ENVOYÉES au tampon sans le vider : le HUD vide une fois ({@code g.flush()}) après tous ses textes.
 * <p>Capitales espacées : {@code espace} unités entre deux lettres, texte mis en capitales, chaque lettre posée sur un
 * pixel entier.
 */
public final class TexteHud {
    /** Alignements. */
    public static final int GAUCHE = -1, CENTRE = 0, DROITE = 1;
    /** Opacité de l'ombre, relative au texte. */
    private static final float OMBRE = 0.55f;
    private static final int LUMIERE = 15728880;

    private final PoliceHud police;
    private final float espace;
    private final boolean capitales;
    private Object source;
    private FormattedCharSequence sequence = FormattedCharSequence.EMPTY;
    private FormattedCharSequence[] lettres = new FormattedCharSequence[0];
    private float[] avances = new float[0];
    private float largeur;
    private boolean vide = true;

    /** Texte simple, tel quel. */
    public TexteHud(PoliceHud police) {
        this(police, 0f, false);
    }

    /** Texte en capitales espacées de {@code espace} unités. */
    public TexteHud(PoliceHud police, float espace) {
        this(police, espace, true);
    }

    private TexteHud(PoliceHud police, float espace, boolean capitales) {
        this.police = police;
        this.espace = espace;
        this.capitales = capitales;
    }

    public PoliceHud police() {
        return police;
    }

    /** Fixe le texte (recalcul seulement s'il a changé). */
    public TexteHud texte(String s) {
        if (s == null) s = "";
        if (s.equals(source)) return this;
        source = s;
        String t = capitales ? s.toUpperCase(Locale.ROOT) : s;
        Font font = Minecraft.getInstance().font;
        vide = t.isEmpty();
        if (espace == 0f) {
            MutableComponent c = Component.literal(t).withStyle(st -> st.withFont(police.id));
            sequence = c.getVisualOrderText();
            lettres = new FormattedCharSequence[0];
            avances = new float[0];
            largeur = font.getSplitter().stringWidth(sequence);
            return this;
        }
        int n = t.codePointCount(0, t.length());
        lettres = new FormattedCharSequence[n];
        avances = new float[n];
        largeur = 0f;
        int k = 0;
        for (int i = 0; i < t.length(); i = t.offsetByCodePoints(i, 1), k++) {
            MutableComponent c = Component.literal(new String(Character.toChars(t.codePointAt(i)))).withStyle(st -> st.withFont(police.id));
            lettres[k] = c.getVisualOrderText();
            avances[k] = font.getSplitter().stringWidth(lettres[k]) + espace;
            largeur += avances[k];
        }
        if (n > 0) largeur -= espace;
        return this;
    }

    /**
     * Fixe un composant déjà mis en forme (morceaux dans d'autres polices HUD, par exemple un pseudo en gras), dans la
     * police de ce texte pour tout ce qui n'a pas la sienne. Toujours recalculé : l'appelant ne le rappelle que si ce
     * dont dépend le composant a changé (langue, pseudo...). Pas de capitales espacées pour un composant.
     */
    public TexteHud composant(Component c) {
        source = c;
        MutableComponent m = Component.empty().withStyle(st -> st.withFont(police.id)).append(c);
        sequence = m.getVisualOrderText();
        lettres = new FormattedCharSequence[0];
        avances = new float[0];
        largeur = Minecraft.getInstance().font.getSplitter().stringWidth(sequence);
        vide = largeur <= 0f;
        return this;
    }

    /** Oublie le texte (il sera recalculé au prochain {@link #texte}). */
    public void oublier() {
        source = null;
    }

    public float largeur() {
        return largeur;
    }

    public boolean vide() {
        return vide;
    }

    /** Ajoute le texte au tampon : milieu des capitales sur {@code cy}, aligné sur {@code x} ({@link #GAUCHE}...). */
    public void ajouter(GuiGraphics g, float x, float cy, int argb, int align, boolean ombre) {
        poser(g, x, cy - police.milieu, argb, align, ombre);
    }

    /** Ajoute le texte au tampon, ligne de base sur {@code yBase}. */
    public void ajouterSurBase(GuiGraphics g, float x, float yBase, int argb, int align, boolean ombre) {
        poser(g, x, yBase - police.base, argb, align, ombre);
    }

    private void poser(GuiGraphics g, float x, float y, int argb, int align, boolean ombre) {
        if (vide || (argb >>> 24) < 4) return;
        float x0 = align < 0 ? x : align == 0 ? x - largeur / 2f : x - largeur;
        Font font = Minecraft.getInstance().font;
        Matrix4f m = g.pose().last().pose();
        MultiBufferSource.BufferSource tampon = g.bufferSource();
        if (ombre) {
            int a = Math.round((argb >>> 24) * OMBRE);
            if (a >= 4) {
                float px = Formes.pixel();
                lettres(font, m, tampon, x0 + px, y + px, a << 24);
            }
        }
        lettres(font, m, tampon, x0, y, argb);
    }

    private void lettres(Font font, Matrix4f m, MultiBufferSource.BufferSource tampon, float x, float y, int argb) {
        float yy = calerY(m, y);
        if (lettres.length == 0) {
            font.drawInBatch(sequence, calerX(m, x), yy, argb, false, m, tampon, Font.DisplayMode.NORMAL, 0, LUMIERE);
            return;
        }
        float cx = x;
        for (int i = 0; i < lettres.length; i++) {
            font.drawInBatch(lettres[i], calerX(m, cx), yy, argb, false, m, tampon, Font.DisplayMode.NORMAL, 0, LUMIERE);
            cx += avances[i];
        }
    }

    /** x posé sur un pixel entier de l'écran, compte tenu de la matrice courante (comme Ui.placer de la boutique). */
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
