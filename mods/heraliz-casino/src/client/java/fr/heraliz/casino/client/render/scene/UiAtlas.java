package fr.heraliz.casino.client.render.scene;

/**
 * Plan de l'atlas de l'interface (textures/gui/casino_ui.png), partage par le generateur de textures et les
 * ecrans : chaque element est dessine a 4 texels par pixel d'interface (net a toutes les echelles d'interface).
 * Rectangles : {u, v, largeur, hauteur, bord 9-slice} en texels.
 */
public final class UiAtlas {
    private UiAtlas() {
    }

    public static final int SIZE = 512;
    /** Texels par pixel d'interface. */
    public static final int SCALE = 4;

    /** Panneau de verre fume (avec 8 texels d'ombre autour). */
    public static final int[] PANEL = {0, 0, 112, 112, 32};
    public static final int PANEL_SHADOW = 8;
    /** Pastille (infobulles, solde, etat). */
    public static final int[] PILL = {128, 0, 96, 48, 24};
    /** Champ de saisie creuse. */
    public static final int[] FIELD = {256, 0, 64, 64, 16};
    /** Anneau de selection (rond). */
    public static final int[] RING = {336, 0, 64, 64, 0};
    /** Halo doux (rond). */
    public static final int[] HALO = {416, 0, 64, 64, 0};

    /** Styles de bouton. */
    public static final int GOLD = 0, GREEN = 1, RED = 2, BLUE = 3, DARK = 4, PURPLE = 5, GREY = 6;
    public static final int STYLES = 7;
    /** Couleurs de base des styles (le generateur en tire les degrades). */
    public static final int[] STYLE_RGB = {0xE8A93A, 0x2FB55A, 0xD8303C, 0x2F78E0, 0x3A3444, 0x8A4AE0, 0x4A4652};
    /** Couleur du texte des boutons de chaque style. */
    public static final int[] STYLE_TEXT = {0xFFE39A, 0xD2FFDC, 0xFFD6D6, 0xD8ECFF, 0xF2EADC, 0xEEDDFF, 0x8A8490};

    /** Bouton arrondi (9-slice, bord 5 px d'interface). */
    public static int[] button(int style, boolean hover) {
        int k = style * 2 + (hover ? 1 : 0);
        return new int[]{(k % 8) * 64, 128 + (k / 8) * 64, 64, 64, 20};
    }

    /** Bouton rond (pas de 9-slice). */
    public static int[] round(int style, boolean hover) {
        int k = style * 2 + (hover ? 1 : 0);
        return new int[]{(k % 8) * 64, 256 + (k / 8) * 64, 64, 64, 0};
    }
}
