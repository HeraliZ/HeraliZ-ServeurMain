package fr.heralisz.matchs.moteur.client;

import fr.heralisz.matchs.moteur.api.Kit;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Silhouettes d'armes dessinees en aplats (cartes de l'ecran de choix quand l'apercu TaCZ n'est pas disponible) :
 * fusil d'assaut, pistolet-mitrailleur, fusil a pompe, fusil de precision, arme tactique. Chaque motif est une grille
 * de 40 x 12 caracteres decoupee une fois en segments horizontaux ; le dessin n'alloue rien.
 */
public final class Silhouettes {
    private Silhouettes() {
    }

    private static final String[] ASSAUT = {
            "                    ##                  ",
            "          ####     ####                 ",
            "  ########################################",
            "##############################          ",
            "##############################          ",
            " ######     ####  ###                   ",
            "  #####     ####   ###                  ",
            "   ###       ###    ###                 ",
            "             ###     #                  ",
            "            ###                         ",
            "                                        ",
            "                                        "};
    private static final String[] SMG = {
            "                                        ",
            "            #####                       ",
            "     ###########################        ",
            "  ##############################        ",
            "  ######################                ",
            "  ###   ####   ####                     ",
            "  ###   ####   ####                     ",
            "        ####   ####                     ",
            "        ####   ####                     ",
            "                ###                     ",
            "                                        ",
            "                                        "};
    private static final String[] POMPE = {
            "                                        ",
            "                                        ",
            "    ##########################################",
            "##################################      ",
            "##########    ##########                ",
            " #####          ######                  ",
            "  ####                                  ",
            "   ###                                  ",
            "                                        ",
            "                                        ",
            "                                        ",
            "                                        "};
    private static final String[] SNIPER = {
            "          ##########                    ",
            "         ############                   ",
            "            ####                        ",
            "  ##############################################",
            "###################################     ",
            "#######   ####  ###                     ",
            " #####    ####   ###                    ",
            "  ###            ##                     ",
            "                  #                     ",
            "                                        ",
            "                                        ",
            "                                        "};
    private static final String[] TACTIQUE = {
            "                                        ",
            "              ######                    ",
            "      ##################################",
            "  ###############################       ",
            "  ###############################       ",
            "  ######  #####    ####  ##             ",
            "   ####   #####     ###  ##             ",
            "          #####          ##             ",
            "           ###                          ",
            "                                        ",
            "                                        ",
            "                                        "};

    private static final int[][] SEG_ASSAUT = segments(ASSAUT), SEG_SMG = segments(SMG), SEG_POMPE = segments(POMPE),
            SEG_SNIPER = segments(SNIPER), SEG_TACTIQUE = segments(TACTIQUE);

    /** Segments {ligne, debut, fin} de chaque ligne du motif. */
    private static int[][] segments(String[] motif) {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        for (int y = 0; y < motif.length; y++) {
            String s = motif[y];
            int x = 0;
            while (x < s.length()) {
                if (s.charAt(x) != '#') {
                    x++;
                    continue;
                }
                int d = x;
                while (x < s.length() && s.charAt(x) == '#') x++;
                l.add(new int[]{y, d, x});
            }
        }
        return l.toArray(new int[0][]);
    }

    /** Dessine la silhouette de la classe dans le rectangle (x, y, largeur ~ 48 x pixel, hauteur 12 x pixel). */
    public static void dessiner(GuiGraphics g, String classe, int x, int y, int pixel, int argb) {
        int[][] seg = switch (classe) {
            case Kit.CLASSE_SMG -> SEG_SMG;
            case Kit.CLASSE_POMPE -> SEG_POMPE;
            case Kit.CLASSE_SNIPER -> SEG_SNIPER;
            case Kit.CLASSE_TACTIQUE -> SEG_TACTIQUE;
            default -> SEG_ASSAUT;
        };
        for (int[] s : seg) g.fill(x + s[1] * pixel, y + s[0] * pixel, x + s[2] * pixel, y + (s[0] + 1) * pixel, argb);
    }

    /** Largeur maximale d'un motif en cases. */
    public static final int LARGEUR = 48, HAUTEUR = 12;
}
