package fr.heraliz.casino.decor;

/**
 * Mesures partagees par les modeles 3D et les blocs (Java pur : le jeu, le serveur et les outils hors-jeu).
 */
public final class DecorGeom {
    private DecorGeom() {
    }

    // =================================================================== porte du coffre-fort (3 x 3 blocs)
    /** Hauteur du dormant (px) : les trois blocs entiers, sans fente en haut. */
    public static final float VAULT_H = 48;
    /** Centre du rond (px), rayon du passage, rayon du vantail, epaisseur du dormant (z). */
    public static final float VAULT_CX = 8, VAULT_CY = VAULT_H / 2, VAULT_HOLE = 21.4f, VAULT_R = 22.6f, VAULT_FRAME_Z = 2.4f;
    /** Axe vertical du gond (x, z) et angle d'ouverture du vantail (degres, vers l'avant). */
    public static final float VAULT_HINGE_X = -15.6f, VAULT_HINGE_Z = 6.4f, VAULT_OPEN_DEG = 96;
    /** Duree des animations (ticks) : volant, pennes, puis le vantail pivote. */
    public static final float VAULT_UNLOCK = 22, VAULT_SWING = 46;

    /** Fermee : dormant et vantail bouchent les trois blocs (x0, y0, z0, x1, y1, z1). */
    public static final float[][] VAULT_CLOSED = {{-16, 0, 0, 32, VAULT_H, 7.6f}};
    /**
     * Ouverte : dormant autour du passage (seuil, montants, linteau, coins arrondis en escalier), gond et vantail
     * rabattu sur la gauche (presque perpendiculaire au mur, bouchon compris). On passe par le milieu du rond.
     */
    public static final float[][] VAULT_OPEN = {
            {-16, 0, 0, 32, 2.6f, 2.4f},
            {-16, 0, 0, -4, VAULT_H, 2.4f},
            {20, 0, 0, 32, VAULT_H, 2.4f},
            {-16, 41, 0, 32, VAULT_H, 2.4f},
            {-4, 0, 0, 2, 6.5f, 2.4f},
            {14, 0, 0, 20, 6.5f, 2.4f},
            {-4, 38, 0, 2, VAULT_H, 2.4f},
            {14, 38, 0, 20, VAULT_H, 2.4f},
            {-18, 2, 2.4f, -13, 46, 9},
            {-21, 1.4f, 7, -8, 46.6f, 52.5f}};
    /** Zone balayee par le vantail ouvert : deux blocs a gauche du rond, quatre devant (reservee a la pose). */
    public static final float[] VAULT_SWING_ZONE = {-32, 0, 0, 0, VAULT_H, 53};
}
