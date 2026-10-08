package fr.heralisz.tags.client.gui.kit;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Echelle forcee des ecrans plein panneau (§1.7) : canevas de conception 640×360, echelle
 * max(1, min(⌊largeur px / 640⌋, ⌊hauteur px / 360⌋)) imposee a l'ouverture et a chaque redimensionnement, echelle
 * d'origine (celle des options) retablie a la fermeture. Les options du joueur ne sont jamais modifiees.
 */
public final class Echelle {
    private Echelle() {
    }

    public static final int CANEVAS_L = 640, CANEVAS_H = 360;

    /**
     * Echelle cible pour la fenetre courante : la plus grande qui laisse au moins ~94 % du canevas (600 x 340). La fenetre
     * "1080p" ne fait que 1057 px de haut une fois la barre de titre retiree : une division entiere stricte donnerait 2
     * au lieu de 3.
     */
    public static int cible(Window w) {
        for (int s = 8; s >= 2; s--) {
            if (w.getWidth() / s >= CANEVAS_L - 40 && w.getHeight() / s >= CANEVAS_H - 20) return s;
        }
        return 1;
    }

    /**
     * A appeler au debut de init() : impose l'echelle cible et met a jour la taille de l'ecran. Retourne vrai si
     * l'echelle a change.
     */
    public static boolean appliquer(Screen ecran) {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        int cible = cible(w);
        if ((int) w.getGuiScale() == cible) return false;
        w.setGuiScale(cible);
        ecran.width = w.getGuiScaledWidth();
        ecran.height = w.getGuiScaledHeight();
        return true;
    }

    /** A appeler dans removed() : retablit l'echelle des options (le prochain ecran s'ouvre a la bonne taille). */
    public static void retablir() {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        int origine = w.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
        if ((int) w.getGuiScale() != origine) w.setGuiScale(origine);
    }
}
