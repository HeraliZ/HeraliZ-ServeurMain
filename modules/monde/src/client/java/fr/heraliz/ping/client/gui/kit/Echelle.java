package fr.heraliz.ping.client.gui.kit;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Echelle d'interface forcee pour nos ecrans plein panneau (cahier des charges §1.7) : a l'ouverture,
 * echelle = max(1, min(largeur px / 640, hauteur px / 360)) (3 en 1080p, 4 en 1440p, 2 en 720p) pour un canevas de
 * conception de 640 x 360 ; l'echelle du joueur est retablie a la fermeture. Un changement de taille de fenetre rappelle
 * init() de l'ecran, qui reapplique l'echelle. Les elements de HUD ne passent pas par ici.
 */
public final class Echelle {
    private Echelle() {
    }

    /** Largeur et hauteur du canevas de conception. */
    public static final int CANEVAS_L = 640, CANEVAS_H = 360;
    private static boolean forcee;

    /**
     * Echelle voulue pour la fenetre courante : la plus grande, par quarts, qui laisse au moins ~94 % du canevas
     * (600 x 340). La fenetre "1080p" ne fait que 1057 px de haut une fois la barre de titre retiree : une division stricte
     * donnerait moins que 3. 18/11 (retour du proprietaire : « en fenetre, c'est petit et difficile a lire ») : par crans
     * entiers, une fenetre a peine plus petite que l'ecran (1920 x 990) tombait de 3 a 2, un tiers plus petit ; par quarts,
     * elle prend 2,75 (1600 x 900 : 2,5 ; plein ecran 1080p : 3 et 1440p : 4, inchanges). Les lettres restent nettes a
     * ces echelles (heraliz_interface : PolicesDensite, finesse par quarts).
     */
    public static double voulue() {
        Window w = Minecraft.getInstance().getWindow();
        double s = Math.min(w.getWidth() / (double) (CANEVAS_L - 40), w.getHeight() / (double) (CANEVAS_H - 20));
        return Math.max(1.0, Math.min(8.0, Math.floor(s * 4.0) / 4.0));
    }

    /** A appeler au debut de init() : force l'echelle et recale width/height de l'ecran. */
    public static void forcer(Screen ecran) {
        Window w = Minecraft.getInstance().getWindow();
        double voulu = voulue();
        if (w.getGuiScale() != voulu) w.setGuiScale(voulu);
        forcee = true;
        ecran.width = w.getGuiScaledWidth();
        ecran.height = w.getGuiScaledHeight();
    }

    /** A appeler dans removed() : retablit l'echelle choisie par le joueur. */
    public static void restaurer() {
        if (!forcee) return;
        forcee = false;
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        w.setGuiScale(w.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode()));
    }
}
