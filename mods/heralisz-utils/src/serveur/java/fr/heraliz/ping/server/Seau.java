package fr.heraliz.ping.server;

/**
 * Anti-spam d'un joueur : seau a jetons. Plein au depart ({@code rafale} pings d'affilee), il regagne un jeton toutes
 * les {@code rechargeMs} millisecondes, sans depasser {@code rafale}. Pur calcul (teste hors du jeu : SeauTest).
 */
public final class Seau {
    double jetons;
    long derniere;
    /** Dernier message « trop de pings » envoye au joueur (ms), pour ne pas l'inonder. */
    long dernierMessage;

    public Seau(int rafale, long maintenant) {
        jetons = rafale;
        derniere = maintenant;
        dernierMessage = Long.MIN_VALUE / 2;
    }

    /** Prend un jeton s'il y en a un (vrai = le ping passe). */
    public boolean prendre(long maintenant, int rafale, int rechargeMs) {
        jetons = Math.min(rafale, jetons + Math.max(0, maintenant - derniere) / (double) rechargeMs);
        derniere = maintenant;
        if (jetons >= 1.0) {
            jetons -= 1.0;
            return true;
        }
        return false;
    }
}
