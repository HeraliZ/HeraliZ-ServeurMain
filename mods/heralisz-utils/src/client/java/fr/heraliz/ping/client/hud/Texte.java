package fr.heraliz.ping.client.hud;

/**
 * Texte du HUD dessine avec les glyphes DM Sans Bold de l'atlas (meme police que le kit des clans), dans le lot
 * courant : aucune allocation (les chiffres de la distance s'ecrivent dans un {@link StringBuilder} reutilise).
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

    /** Distance lisible dans {@code sb} : « 42 m », « 1,2 km » (virgule en francais). */
    public static StringBuilder distance(StringBuilder sb, float metres, boolean francais) {
        sb.setLength(0);
        if (metres < 1000f) {
            sb.append((int) (metres + 0.5f)).append(" m");
        } else {
            int dixiemes = (int) (metres / 100f + 0.5f);
            sb.append(dixiemes / 10).append(francais ? ',' : '.').append(dixiemes % 10).append(" km");
        }
        return sb;
    }
}
