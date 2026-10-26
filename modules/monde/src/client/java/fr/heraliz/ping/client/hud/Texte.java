package fr.heraliz.ping.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/**
 * Texte du HUD dessine avec les glyphes DM Sans Bold de l'atlas (meme police que le kit des clans ; russe en Onest
 * Bold), dans le lot courant : aucune allocation (les chiffres de la distance s'ecrivent dans un {@link StringBuilder}
 * reutilise).
 * {@code taille} = hauteur des capitales en pixels d'interface ; {@code y} = haut des capitales.
 */
public final class Texte {
    private Texte() {
    }

    /** Largeur du texte (pixels d'interface). */
    public static float largeur(CharSequence s, float taille, float espacement) {
        Atlas.Police p = Atlas.GRAS;
        float k = taille / p.cap, w = 0;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            int g = Atlas.Police.index(s.charAt(i));
            if (g < 0 || !p.present[g] && g != ' ') g = '?';
            w += p.avance[g] * k + espacement;
        }
        return n == 0 ? 0 : w - espacement;
    }

    /** Dessine le texte, bord gauche en x. */
    public static void dessiner(CharSequence s, float x, float y, float taille, int argb, float espacement) {
        Atlas.Police p = Atlas.GRAS;
        float k = taille / p.cap;
        float base = y + taille;
        float plume = x;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            int g = Atlas.Police.index(s.charAt(i));
            if (g < 0 || !p.present[g] && g != ' ') g = '?';
            if (p.present[g] && p.l[g] > 0) {
                float x0 = plume + p.ox[g] * k, y0 = base + p.oy[g] * k;
                Lot.quad(x0, y0, x0 + p.l[g] * k, y0 + p.h[g] * k, p.u0[g], p.v0[g], p.u1[g], p.v1[g], argb);
            }
            plume += p.avance[g] * k + espacement;
        }
    }

    /** Dessine le texte centre sur cx. */
    public static void centre(CharSequence s, float cx, float y, float taille, int argb, float espacement) {
        dessiner(s, cx - largeur(s, taille, espacement) * 0.5f, y, taille, argb, espacement);
    }

    /** Unites de distance et separateur decimal de la langue choisie (lus a chaque changement de langue). */
    private static String langue = "", metre = "m", kilometre = "km";
    private static char virgule = '.';

    /** Relit les unites si la langue a change (une comparaison par image, aucune allocation sinon). */
    public static void unites() {
        String l = Minecraft.getInstance().getLanguageManager().getSelected();
        if (l == null || l.equals(langue)) return;
        langue = l;
        metre = I18n.get("heraliz_ping.unite.m");
        kilometre = I18n.get("heraliz_ping.unite.km");
        String v = I18n.get("heraliz_ping.unite.virgule");
        virgule = v.length() == 1 ? v.charAt(0) : '.';
    }

    /** Distance lisible dans {@code sb} : « 42 m », « 1,2 km » (unites et virgule de la langue, {@link #unites}). */
    public static StringBuilder distance(StringBuilder sb, float metres) {
        sb.setLength(0);
        if (metres < 1000f) {
            sb.append((int) (metres + 0.5f)).append(' ').append(metre);
        } else {
            int dixiemes = (int) (metres / 100f + 0.5f);
            sb.append(dixiemes / 10).append(virgule).append(dixiemes % 10).append(' ').append(kilometre);
        }
        return sb;
    }
}
