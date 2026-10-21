package fr.heralisz.matchs.client.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Texte d'interface centre au dixieme de pixel (21/10, demande du proprietaire : « le texte ultra bien centre dans son
 * conteneur ») :
 * <ul>
 *     <li>largeur exacte, en flottant ({@code StringSplitter}), et non celle arrondie au pixel superieur de
 *     {@link Font#width} : le texte n'est plus decale d'un demi-pixel a droite ;</li>
 *     <li>milieu vertical = milieu des capitales et des chiffres ({@link #MILIEU} sous le point de dessin), mesure sur la
 *     police DM Sans du kit : {@code cy} est le milieu visuel du texte, pas le haut de la ligne.</li>
 * </ul>
 * Prepare une fois ({@link #texte}), dessine sans allocation. Capitales espacees possibles ({@code espace} en pixels de
 * police entre deux lettres, texte mis en capitales).
 */
public final class TexteCentre {
    /**
     * Milieu des capitales et des chiffres sous le point de dessin, en pixels de police, pour {@link Ui#FONT},
     * {@link Ui#BOLD} (DM Sans 11, decalage 1 : ligne de base a 7,38, capitales de 5,91) et {@link Ui#SMALL} (DM Sans 9 :
     * ligne de base a 6,86, capitales de 4,84) : 4,42 et 4,44.
     */
    public static final float MILIEU = 4.43f;

    private final ResourceLocation police;
    private final float espace;
    private final boolean capitales;
    private String source = null;
    private MutableComponent entier;
    private MutableComponent[] lettres = new MutableComponent[0];
    private float[] avances = new float[0];
    private float largeur;

    /** Texte simple (sans espacement), tel quel. */
    public TexteCentre(ResourceLocation police) {
        this(police, 0f, false);
    }

    /** Texte en capitales espacees de {@code espace} pixels de police. */
    public TexteCentre(ResourceLocation police, float espace) {
        this(police, espace, true);
    }

    private TexteCentre(ResourceLocation police, float espace, boolean capitales) {
        this.police = police;
        this.espace = espace;
        this.capitales = capitales;
    }

    /** Fixe le texte (recalcul seulement s'il change). */
    public TexteCentre texte(String s) {
        if (s == null) s = "";
        if (s.equals(source)) return this;
        source = s;
        String t = capitales ? s.toUpperCase(Locale.ROOT) : s;
        Font font = Minecraft.getInstance().font;
        entier = Ui.lit(t, police);
        if (espace == 0f) {
            lettres = new MutableComponent[0];
            avances = new float[0];
            largeur = font.getSplitter().stringWidth(entier);
            return this;
        }
        int n = t.codePointCount(0, t.length());
        lettres = new MutableComponent[n];
        avances = new float[n];
        largeur = 0;
        int k = 0;
        for (int i = 0; i < t.length(); i = t.offsetByCodePoints(i, 1), k++) {
            lettres[k] = Ui.lit(new String(Character.toChars(t.codePointAt(i))), police);
            avances[k] = font.getSplitter().stringWidth(lettres[k]) + espace;
            largeur += avances[k];
        }
        if (n > 0) largeur -= espace;
        return this;
    }

    public String source() {
        return source == null ? "" : source;
    }

    /** Largeur a l'echelle 1 (pixels de police). */
    public float largeur() {
        return largeur;
    }

    /** Largeur a l'echelle {@code k}. */
    public float largeur(float k) {
        return largeur * k;
    }

    /** Centre en x sur {@code cx}, milieu des capitales sur {@code cy}. */
    public void centre(GuiGraphics g, float cx, float cy, float k, int argb) {
        dessiner(g, cx - largeur * k / 2f, cy, k, argb);
    }

    /** Bord gauche en {@code x}, milieu des capitales sur {@code cy}. */
    public void gauche(GuiGraphics g, float x, float cy, float k, int argb) {
        dessiner(g, x, cy, k, argb);
    }

    /** Bord droit en {@code xDroite}, milieu des capitales sur {@code cy}. */
    public void droite(GuiGraphics g, float xDroite, float cy, float k, int argb) {
        dessiner(g, xDroite - largeur * k, cy, k, argb);
    }

    // ------------------------------------------------------------------ sans preparation (peintres de textures)
    /**
     * Dessine un composant aligne ({@code align} : -1 bord gauche en x, 0 centre sur x, 1 bord droit en x), milieu des
     * capitales sur {@code cy}, a l'echelle k. Alloue : pour les dessins faits une fois (textures des murs).
     */
    public static void ecrire(GuiGraphics g, net.minecraft.network.chat.Component c, float x, float cy, float k, int argb, int align) {
        Font font = Minecraft.getInstance().font;
        float w = font.getSplitter().stringWidth(c) * k;
        float x0 = align < 0 ? x : align == 0 ? x - w / 2f : x - w;
        g.pose().pushPose();
        g.pose().translate(x0, cy - MILIEU * k, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Largeur exacte d'un composant a l'echelle k. */
    public static float largeur(net.minecraft.network.chat.Component c, float k) {
        return Minecraft.getInstance().font.getSplitter().stringWidth(c) * k;
    }

    private void dessiner(GuiGraphics g, float x, float cy, float k, int argb) {
        if (entier == null || (argb >>> 24) < 4) return;
        Font font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(x, cy - MILIEU * k, 0);
        g.pose().scale(k, k, 1);
        if (lettres.length == 0) {
            g.drawString(font, entier, 0, 0, argb, false);
        } else {
            float cx = 0;
            for (int i = 0; i < lettres.length; i++) {
                g.pose().pushPose();
                g.pose().translate(cx, 0, 0);
                g.drawString(font, lettres[i], 0, 0, argb, false);
                g.pose().popPose();
                cx += avances[i];
            }
        }
        g.pose().popPose();
    }
}
