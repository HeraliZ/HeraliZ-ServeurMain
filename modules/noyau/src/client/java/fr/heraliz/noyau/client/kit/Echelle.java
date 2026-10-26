package fr.heraliz.noyau.client.kit;

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
     * Echelle voulue pour la fenetre courante : la plus grande qui laisse au moins ~94 % du canevas (600 x 340). La fenetre
     * "1080p" ne fait que 1057 px de haut une fois la barre de titre retiree : une division entiere stricte donnerait 2
     * au lieu de 3.
     */
    public static int voulue() {
        Window w = Minecraft.getInstance().getWindow();
        for (int s = 8; s >= 2; s--) {
            if (w.getWidth() / s >= CANEVAS_L - 40 && w.getHeight() / s >= CANEVAS_H - 20) return s;
        }
        return 1;
    }

    /** A appeler au debut de init() : force l'echelle et recale width/height de l'ecran. */
    public static void forcer(Screen ecran) {
        Window w = Minecraft.getInstance().getWindow();
        int voulu = voulue();
        if ((int) w.getGuiScale() != voulu) w.setGuiScale(voulu);
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
