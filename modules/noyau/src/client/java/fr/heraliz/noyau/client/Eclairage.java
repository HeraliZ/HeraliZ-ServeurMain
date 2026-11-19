package fr.heraliz.noyau.client;

/**
 * Lumière de bloc coupée près du joueur, sans changer la lumière du monde (audit des images par seconde du 19/11, point 2).
 * <p>
 * Un bloc qui fait varier sa propre lumière (réglette qui grésille du casino) fait refaire l'éclairage et reconstruire les
 * morceaux de carte autour, chez chaque joueur proche, à chaque saute. À la place, il garde une lumière fixe et demande
 * ici, à chaque tick, quelle part de la lumière de bloc retirer autour du joueur (0 : rien, 1 : toute) ; la teinte de la
 * lumière de HeraliZ Utils ({@code fr.heraliz.decors.client.AlarmeRouge}, la seule à retoucher la lightmap) l'applique,
 * comme la seconde noire de ses lampes. Aucun coût sans demande.
 */
public final class Eclairage {
    private Eclairage() {}

    private static float demande;

    /** Pendant le tick du client : la plus forte demande du tick l'emporte. */
    public static void demander(float part) {
        if (part > demande) demande = Math.min(1.0F, part);
    }

    /** Fin du tick du client : la demande du tick, remise à zéro pour le suivant. */
    public static float lire() {
        float d = demande;
        demande = 0.0F;
        return d;
    }
}
