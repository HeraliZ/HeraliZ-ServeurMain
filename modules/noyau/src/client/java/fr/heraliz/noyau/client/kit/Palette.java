package fr.heraliz.noyau.client.kit;

/**
 * Palette des HUD en jeu, une seule valeur par rôle (refonte du 25/10, docs/HUD_REFONTE.md) : danger (rouge du logo),
 * alerte, ok, info, texte, atténué, très atténué, verre. Tous les HUD de la refonte (à terre, réanimation, soins, usure)
 * prennent leurs couleurs ici ; les teintes plus claires se font par {@link #clair}. Valeurs RGB (sans opacité) ; les
 * opacités sont des entiers 0..255. Lu aussi par {@code modules/noyau/outils/apercu_hud.py} (mêmes noms).
 */
public final class Palette {
    private Palette() {
    }

    /** Rouge du logo : danger, à terre, interruption, arme ruinée. */
    public static final int DANGER = 0xE3201B;
    /** Ambre : alerte (fouille, arme usée). */
    public static final int ALERTE = 0xE8B34A;
    /** Vert : soin, réanimation, réussite, arme en bon état. */
    public static final int OK = 0x5FD08A;
    /** Bleu : information. */
    public static final int INFO = 0x6FA8FF;
    /** Texte principal, texte atténué, texte très atténué. */
    public static final int TEXTE = 0xECEEF2, ATTENUE = 0x8F95A0, TRES_ATTENUE = 0x5A5F69;
    /** Fond des panneaux « verre » et son opacité (80 %). */
    public static final int VERRE = 0x0D0F14, VERRE_ALPHA = 204;
    /** Bord d'un pixel (blanc à 10 %) et liseré clair du haut (blanc à 16 %) du verre. */
    public static final int BORD_ALPHA = 26, LISERE_ALPHA = 41;

    /** Couleur ARGB : RGB + opacité 0..255 (bornée). */
    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Couleur ARGB : RGB + opacité 0..1. */
    public static int argb(int rgb, float opacite) {
        return argb(rgb, Math.round(255f * opacite));
    }

    /** Opacité d'une couleur ARGB multipliée par {@code k} (0..1). */
    public static int fondu(int argb, float k) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, k)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Mélange de deux couleurs RGB ({@code t} = 0 : a, 1 : b). */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Teinte éclaircie vers le blanc ({@code t} = 0 : la couleur, 1 : blanc). */
    public static int clair(int rgb, float t) {
        return mix(rgb, 0xFFFFFF, t);
    }
}
