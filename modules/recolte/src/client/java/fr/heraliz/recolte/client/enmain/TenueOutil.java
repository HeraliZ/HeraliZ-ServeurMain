package fr.heraliz.recolte.client.enmain;

/**
 * Comment un outil de récolte est tenu (A_FAIRE §7) : les deux prises sur le manche, et vu des autres, la place de
 * l'outil dans la main qui tient le bas du manche. Lu par la première personne ({@link PremierePersonne}), le rendu de
 * l'objet ({@link RenduOutilObjet}) et la pose des bras vue des autres ({@link VueDesAutres}).
 */
final class TenueOutil {
    /** Prises (m depuis le bas du manche, {@code PRISES} de recolte_modeles.py) : main droite, main gauche. */
    static final float PRISE_BAS = 0.06F, PRISE_HAUT = 0.34F;

    /**
     * Vu des autres, l'outil dans la main du bas, repère du bras droit (px du modèle, y vers le bas le long du bras, -z
     * devant) : prise (milieu du poing), sens du manche (devant le poing, vers la main du haut), côté qui frappe (le
     * tranchant de la hache dans le prolongement du bras : il mène le coup ; le pic pour la pioche).
     */
    static final float[] PRISE_MAIN = {-1.0F, 9.0F, 0.0F}, MANCHE = {0.7F, -0.6F, -1.0F}, TRANCHANT = {0.0F, 1.0F, -0.35F};
    /** px du modèle par mètre : le joueur est dessiné à 0,9375, l'outil garde sa vraie taille. */
    static final float PX = 16.0F / 0.9375F;

    private TenueOutil() {
    }
}
