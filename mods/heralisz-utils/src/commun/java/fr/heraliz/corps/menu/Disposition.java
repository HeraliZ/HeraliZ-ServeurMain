package fr.heraliz.corps.menu;

/**
 * Plan de l'ecran de fouille (partage par le menu, qui place les cases, et l'ecran, qui dessine autour), comme un
 * coffre de Minecraft : EN HAUT le corps (titre « Corps de X », equipement et sac, accessoires, inventaire du mort,
 * bouton « Tout prendre »), EN DESSOUS l'inventaire du joueur, exactement a la place qu'il a dans un coffre (le bas de
 * la texture du coffre est repris tel quel). Largeur d'un coffre (176) ; la hauteur suit ce que porte le corps.
 * <p>Coordonnees en pixels d'interface depuis le coin du panneau : (x, y) = coin du cadre de 18 px d'une case (l'objet
 * est dessine 1 px plus loin).
 */
public final class Disposition {
    public static final int CASE = 18;
    /** Largeur d'un coffre. */
    public static final int LARGEUR = 176;
    /** Bord gauche des cases (comme un coffre). */
    public static final int GAUCHE = 7;
    public static final int COLONNE = 9 * CASE;
    /** Texte du titre (y = 6, comme un coffre) puis cause de la mort (y = 16). */
    public static final int Y_TITRE = 6, Y_CAUSE = 16;
    /** Hauteur de l'en-tete (titre, cause, filet). */
    public static final int ENTETE = 28;
    /** Hauteur d'une etiquette de section au-dessus de ses cases. */
    public static final int ETIQUETTE = 10;
    /** Espace entre deux sections. */
    public static final int ENTRE = 4;
    /** Hauteur du bouton « Tout prendre ». */
    public static final int BOUTON = 14;
    /** Bas de la texture du coffre (etiquette, inventaire et barre rapide du joueur), repris tel quel sous le corps. */
    public static final int BAS_COFFRE = 96;

    public final int yEquipement, xMainGauche, xSac;
    public final int yCurios, rangsCurios;
    public final int yInventaire, yBarre, yAutres, rangsAutres;
    public final int yBouton;
    /** Hauteur de la partie du corps (le bas du coffre commence la). */
    public final int hauteurCorps;
    /** Etiquette « Ton inventaire », inventaire du joueur et sa barre rapide (comme dans un coffre). */
    public final int yEtiquetteJoueur, yJoueur, yJoueurBarre;
    public final int hauteur;

    public Disposition(int nbCurios, int nbAutres) {
        int y = ENTETE;
        // equipement : tete, torse, jambes, pieds, puis main gauche et sac un peu a part
        yEquipement = y + ETIQUETTE;
        xMainGauche = GAUCHE + 4 * CASE + 4;
        xSac = xMainGauche + CASE + 4;
        y = yEquipement + CASE + ENTRE;
        // accessoires Curios portes (seulement s'il y en a)
        rangsCurios = (nbCurios + 8) / 9;
        if (rangsCurios > 0) {
            yCurios = y + ETIQUETTE;
            y = yCurios + rangsCurios * CASE + ENTRE;
        } else {
            yCurios = -1;
        }
        // inventaire du mort : 3 rangees, puis sa barre rapide, puis les autres objets
        yInventaire = y + ETIQUETTE;
        yBarre = yInventaire + 3 * CASE + 4;
        rangsAutres = (nbAutres + 8) / 9;
        yAutres = yBarre + CASE + 4;
        int bas = yBarre + CASE + (rangsAutres > 0 ? 4 + rangsAutres * CASE : 0);
        yBouton = bas + 5;
        // etiquette « Ton inventaire » a cheval sur le haut du bas du coffre (dans le pack, son rebord commence 9 px plus
        // bas : le texte ne doit pas y descendre), puis inventaire a 13 px et barre rapide a 71 px (positions vanilla)
        yEtiquetteJoueur = yBouton + BOUTON + 5;
        hauteurCorps = yEtiquetteJoueur + 1;
        yJoueur = hauteurCorps + 13;
        yJoueurBarre = hauteurCorps + 71;
        hauteur = hauteurCorps + BAS_COFFRE;
    }

    /** Position (x, y) de la case du corps d'indice {@code index} (voir CorpsContenu). */
    public int x(int index, int nbCurios) {
        if (index < 9) return GAUCHE + index * CASE;
        if (index < 36) return GAUCHE + ((index - 9) % 9) * CASE;
        if (index < 40) return GAUCHE + (3 - (index - 36)) * CASE; // tete, torse, jambes, pieds de gauche a droite
        if (index == 40) return xMainGauche;
        if (index == 41) return xSac;
        int k = index - 42;
        if (k < nbCurios) return GAUCHE + (k % 9) * CASE;
        return GAUCHE + ((k - nbCurios) % 9) * CASE;
    }

    public int y(int index, int nbCurios) {
        if (index < 9) return yBarre;
        if (index < 36) return yInventaire + ((index - 9) / 9) * CASE;
        if (index < 42) return yEquipement;
        int k = index - 42;
        if (k < nbCurios) return yCurios + (k / 9) * CASE;
        return yAutres + ((k - nbCurios) / 9) * CASE;
    }
}
