package fr.heraliz.crac.client.render;

/**
 * Places du cœur de clan (modèle tourné vers le nord, px). ÉCRIT par mods/crac/outils/coeur_modele.py --installer :
 * jamais à la main.
 */
final class CoeurClanDonnees {
    /** Plaque des points, sur l'avant du socle. */
    static final float PLAQUE_X0 = 3.6F, PLAQUE_X1 = 12.4F, PLAQUE_Y0 = 1.7F, PLAQUE_Y1 = 3.05F, PLAQUE_Z = 0.9F;
    /** Hologramme des rangs : centre (hauteur) et taille. */
    static final float HOLO_Y = 21.8F, HOLO_LARGEUR = 12.0F, HOLO_HAUTEUR = 5.7F;
    /** Centre du cristal (il tourne autour de l'axe vertical qui passe par le milieu du bloc). */
    static final float CRISTAL_Y = 10.0F;

    private CoeurClanDonnees() {
    }
}
