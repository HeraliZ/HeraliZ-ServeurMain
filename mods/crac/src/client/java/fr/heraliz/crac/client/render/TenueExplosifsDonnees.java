package fr.heraliz.crac.client.render;

/**
 * Nombres de la tenue à deux mains de la C4 et de la charge explosive en première personne. ÉCRIT par
 * mods/crac/outils/tenue_explosifs.py : ne pas modifier à la main.
 */
final class TenueExplosifsDonnees {
    /** Bras vus des autres joueurs (radians). */
    static final float BRAS_X = -0.78F, BRAS_Y = 0.22F;
    /** Centre de l'objet devant la caméra (blocs), inclinaison vers le joueur et lacet (degrés), échelle. */
    static final float FP_X = 0.0F, FP_Y = -0.4F, FP_Z = -0.92F;
    static final float FP_INCLINE = 38.0F, FP_LACET = 0.0F, FP_TAILLE = 0.5F;
    /** Balancement du bras au repos laissé par Minecraft (radians), annulé devant chaque main. */
    static final float BOB_Z = 0.1F;
    /** c4, main droite : position (blocs), angles XYZ (degrés), décalage du bout du bras (blocs). */
    static final float[] C4_DROITE = {0.26500F, -0.30875F, -0.96395F, 147.99500F, 0.00000F, 161.26300F, 0.37500F, -0.75000F, -0.00000F};
    /** c4, main gauche : position (blocs), angles XYZ (degrés), décalage du bout du bras (blocs). */
    static final float[] C4_GAUCHE = {-0.26500F, -0.30875F, -0.96395F, 147.99500F, -0.00000F, -161.26300F, -0.37500F, -0.75000F, -0.00000F};
    /** explosive_charge, main droite : position (blocs), angles XYZ (degrés), décalage du bout du bras (blocs). */
    static final float[] EXPLOSIVE_CHARGE_DROITE = {0.24844F, -0.29339F, -0.98057F, 147.99500F, 0.00000F, 161.26300F, 0.37500F, -0.75000F, -0.00000F};
    /** explosive_charge, main gauche : position (blocs), angles XYZ (degrés), décalage du bout du bras (blocs). */
    static final float[] EXPLOSIVE_CHARGE_GAUCHE = {-0.24844F, -0.29339F, -0.98057F, 147.99500F, -0.00000F, -161.26300F, -0.37500F, -0.75000F, -0.00000F};

    private TenueExplosifsDonnees() {
    }
}
