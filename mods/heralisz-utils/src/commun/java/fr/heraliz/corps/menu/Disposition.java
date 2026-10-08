package fr.heraliz.corps.menu;

/**
 * Plan de l'ecran de fouille (partage par le menu, qui place les cases, et l'ecran, qui dessine autour). Deux colonnes :
 * a gauche le corps (equipement + sac, accessoires, inventaire), a droite l'inventaire du joueur et « Tout prendre ».
 * Coordonnees en pixels d'interface depuis le coin du panneau ; une case fait 18 px.
 */
public final class Disposition {
    public static final int CASE = 18;
    public static final int MARGE = 10;
    public static final int COLONNE = 9 * CASE;
    public static final int ECART = 20;
    public static final int GAUCHE = MARGE;
    public static final int DROITE = MARGE + COLONNE + ECART;
    public static final int LARGEUR = DROITE + COLONNE + MARGE;
    /** Hauteur de l'en-tete (titre, cause, filet). */
    public static final int ENTETE = 36;
    /** Hauteur d'une etiquette de section au-dessus de ses cases. */
    public static final int ETIQUETTE = 11;
    /** Espace entre deux sections. */
    public static final int ENTRE = 7;

    public final int yEquipement, ySac, xSac, xMainGauche;
    public final int yCurios, rangsCurios;
    public final int yInventaire, yBarre, yAutres, rangsAutres;
    public final int yJoueur, yJoueurBarre, yBouton, yAide;
    public final int hauteur;

    public Disposition(int nbCurios, int nbAutres) {
        int y = ENTETE + 6;
        yEquipement = y + ETIQUETTE;
        xMainGauche = GAUCHE + 4 * CASE + 6;
        xSac = GAUCHE + 6 * CASE + 4;
        ySac = yEquipement;
        y = yEquipement + CASE + ENTRE;
        rangsCurios = (nbCurios + 8) / 9;
        if (rangsCurios > 0) {
            yCurios = y + ETIQUETTE;
            y = yCurios + rangsCurios * CASE + ENTRE;
        } else {
            yCurios = -1;
        }
        yInventaire = y + ETIQUETTE;
        yBarre = yInventaire + 3 * CASE + 4;
        rangsAutres = (nbAutres + 8) / 9;
        yAutres = yBarre + CASE + 4;
        int basGauche = yBarre + CASE + (rangsAutres > 0 ? 4 + rangsAutres * CASE : 0);

        yJoueur = ENTETE + 6 + ETIQUETTE;
        yJoueurBarre = yJoueur + 3 * CASE + 4;
        yBouton = yJoueurBarre + CASE + 12;
        yAide = yBouton + 22;
        int basDroite = yAide + 10;
        hauteur = Math.max(basGauche, basDroite) + MARGE;
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
