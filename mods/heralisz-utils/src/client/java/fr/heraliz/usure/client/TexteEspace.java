package fr.heraliz.usure.client;

import fr.heraliz.usure.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Texte en capitales espacees mis en cache (HUD) : les composants de chaque lettre et leurs avances ne sont recalcules
 * que si le texte change ; le dessin d'une image n'alloue rien de plus que le rendu vanilla des glyphes.
 */
public final class TexteEspace {
    private final ResourceLocation police;
    private final float espace;
    private String source;
    private MutableComponent[] lettres = new MutableComponent[0];
    private float[] avances = new float[0];
    private float largeur;

    public TexteEspace(ResourceLocation police, float espace) {
        this.police = police;
        this.espace = espace;
    }

    /** Fixe le texte (recalcul seulement s'il a change). */
    public TexteEspace texte(String s) {
        if (s == null) s = "";
        if (s.equals(source)) return this;
        source = s;
        String up = s.toUpperCase(Locale.ROOT);
        Font font = Minecraft.getInstance().font;
        int n = up.codePointCount(0, up.length());
        lettres = new MutableComponent[n];
        avances = new float[n];
        largeur = 0;
        int k = 0;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1), k++) {
            lettres[k] = Ui.lit(new String(Character.toChars(up.codePointAt(i))), police);
            avances[k] = font.width(lettres[k]) + espace;
            largeur += avances[k];
        }
        if (n > 0) largeur -= espace;
        return this;
    }

    public float largeur() {
        return largeur;
    }

    public String source() {
        return source == null ? "" : source;
    }

    /** Dessine a partir de x (bord gauche). */
    public void dessiner(GuiGraphics g, float x, float y, int argb) {
        Font font = Minecraft.getInstance().font;
        float cx = x;
        for (int i = 0; i < lettres.length; i++) {
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.drawString(font, lettres[i], 0, 0, argb, false);
            g.pose().popPose();
            cx += avances[i];
        }
    }

    public void centre(GuiGraphics g, float cx, float y, int argb) {
        dessiner(g, cx - largeur / 2f, y, argb);
    }

    public void droite(GuiGraphics g, float xDroite, float y, int argb) {
        dessiner(g, xDroite - largeur, y, argb);
    }

    /** Composant simple (non espace) dans une police, pour les textes qui ne changent pas a chaque image. */
    public static MutableComponent en(Component c, ResourceLocation police) {
        return Ui.in(c, police);
    }
}
