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
        appliquer(ecran, voulue());
    }

    /**
     * Ecran « panneau » (quetes, hotel des ventes, kits... : un panneau au milieu, pas tout le canevas), 03/11, retour du
     * proprietaire (« on doit plisser les yeux ») : meme regle que {@link #forcer(Screen)}, puis jusqu'a deux demi-crans de
     * plus (+0,5 ou +1) tant que le panneau de {@code largeur} x {@code hauteur} unites tient dans 90 % de la fenetre.
     * Exemples pour un panneau de 560 x 320 : fenetre 1080p agrandie (1920 x 1009, l'ecran tombait a 2) -> 2,5 ;
     * 1600 x 900 -> 2,5 ; plein ecran 1080p -> 3 (inchange) ; 1366 x 768 -> 2 (inchange). Jamais plus petit qu'avant.
     * Une echelle non entiere est sans defaut pour le texte (lettres tracees a la finesse de l'ecran, heraliz_interface :
     * PolicesDensite) et pour les formes posees sur les points de l'ecran (Net).
     */
    public static void forcer(Screen ecran, int largeur, int hauteur) {
        appliquer(ecran, pourPanneau(largeur, hauteur));
    }

    /** Echelle d'un ecran panneau (voir {@link #forcer(Screen, int, int)}), par quarts (18/11 ; avant : par demis). */
    public static double pourPanneau(int largeur, int hauteur) {
        Window w = Minecraft.getInstance().getWindow();
        double base = voulue(), s = base;
        for (double e = base + 0.25; e <= base + 1.0; e += 0.25) {
            if (largeur * e > w.getWidth() * 0.9 || hauteur * e > w.getHeight() * 0.9) break;
            s = e;
        }
        return s;
    }

    private static void appliquer(Screen ecran, double voulu) {
        Window w = Minecraft.getInstance().getWindow();
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
