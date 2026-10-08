package fr.heralisz.matchs.zombies.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/**
 * Texte du HUD mis en cache : construit une seule fois (composants par lettre et positions) puis redessine a chaque
 * image sans allocation. {@link #definir} ne reconstruit que si le texte, la police ou l'espacement changent.
 * Espacement &gt; 0 = capitales espacees de la charte ; 0 = texte normal (un seul composant).
 */
public final class Libelle {
    private String source;
    private ResourceLocation police;
    private float espace = -1;
    private Component[] lettres = new Component[0];
    private float[] xs = new float[0];
    private float largeur;

    /** Met a jour le texte (rend vrai s'il a ete reconstruit). */
    public boolean definir(Font font, String s, ResourceLocation f, float esp) {
        if (Objects.equals(s, source) && Objects.equals(f, police) && esp == espace) return false;
        source = s;
        police = f;
        espace = esp;
        if (s == null || s.isEmpty()) {
            lettres = new Component[0];
            xs = new float[0];
            largeur = 0;
            return true;
        }
        if (esp <= 0) {
            lettres = new Component[]{Component.literal(s).withStyle(st -> st.withFont(f))};
            xs = new float[]{0};
            largeur = font.width(lettres[0]);
            return true;
        }
        String up = s.toUpperCase(Locale.ROOT);
        int n = up.codePointCount(0, up.length());
        lettres = new Component[n];
        xs = new float[n];
        float x = 0;
        int k = 0;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(up.codePointAt(i)));
            lettres[k] = Component.literal(ch).withStyle(st -> st.withFont(f));
            xs[k] = x;
            x += font.width(lettres[k]) + esp;
            k++;
        }
        largeur = Math.max(0, x - esp);
        return true;
    }

    /** Variante pour un composant deja traduit (texte normal, sans espacement). */
    public boolean definir(Font font, Component c, ResourceLocation f) {
        String s = c == null ? null : c.getString();
        if (Objects.equals(s, source) && Objects.equals(f, police) && espace == 0) return false;
        source = s;
        police = f;
        espace = 0;
        if (c == null) {
            lettres = new Component[0];
            xs = new float[0];
            largeur = 0;
            return true;
        }
        Component cc = Component.empty().withStyle(st -> st.withFont(f)).append(c);
        lettres = new Component[]{cc};
        xs = new float[]{0};
        largeur = font.width(cc);
        return true;
    }

    public float largeur() {
        return largeur;
    }

    public boolean vide() {
        return lettres.length == 0;
    }

    /** Dessine a partir du bord gauche {@code x}. */
    public void dessiner(GuiGraphics g, Font font, float x, float y, int argb) {
        if ((argb >>> 24) < 4) return;
        for (int i = 0; i < lettres.length; i++) {
            g.pose().pushPose();
            g.pose().translate(x + xs[i], y, 0);
            g.drawString(font, lettres[i], 0, 0, argb, false);
            g.pose().popPose();
        }
    }

    public void centre(GuiGraphics g, Font font, float cx, float y, int argb) {
        dessiner(g, font, cx - largeur / 2F, y, argb);
    }

    public void droite(GuiGraphics g, Font font, float xDroit, float y, int argb) {
        dessiner(g, font, xDroit - largeur, y, argb);
    }
}
