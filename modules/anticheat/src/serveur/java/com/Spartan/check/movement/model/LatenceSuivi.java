package com.Spartan.check.movement.model;

/**
 * Latence annoncée contre latence mesurée (audit du 02/11, point 14 : « faux lag »), Java pur, testé.
 *
 * <p>Le serveur connaît deux latences d'un joueur :
 * <ul>
 *   <li>celle de Minecraft (keepalive, une mesure toutes les 15 s, lissée : latence = (latence × 3 + mesure) / 4). Le
 *   jeu du joueur y répond dès réception, sans attendre son image suivante. TaCZ s'en sert pour « remonter le temps » des
 *   hitbox (jusqu'à 1 s), Spartan pour ses marges ;</li>
 *   <li>celle des transactions de Spartan (un ping par tick) : le jeu y répond APRÈS avoir traité tout ce qui précède,
 *   au début d'une image. Chez un joueur honnête, elle est donc au moins aussi grande que la mesure keepalive faite au
 *   même moment (un gel du jeu ou du réseau retarde les deux).</li>
 * </ul>
 * Un « faux lag » retient les réponses keepalive pour gonfler la première (remonter plus loin dans le temps avec TaCZ,
 * marges plus larges) tout en jouant normalement. Chaque nouvelle mesure keepalive est retrouvée à partir de la latence
 * lissée (mesure = 4 × nouvelle − 3 × ancienne) et comparée à la plus grande réponse aux transactions des 30 dernières
 * secondes : plusieurs mesures de suite bien au-dessus = latence trafiquée.
 *
 * <p>Tableau fixe (aucun objet créé par paquet).
 */
public final class LatenceSuivi {
    private static final int SECONDES = 30;
    private final int[] maxParSeconde = new int[SECONDES];
    private int pos;
    private int maxCourant;
    private int derniereLissee = -1;
    private int suspectes;
    private int derniereMesure;

    /** Réponse à une transaction : aller-retour en nanosecondes. */
    public void pong(long rttNanos) {
        int ms = (int) Math.min(60_000, Math.max(0, rttNanos / 1_000_000L));
        if (ms > maxCourant) {
            maxCourant = ms;
        }
    }

    /** Plus grande réponse aux transactions sur les 30 dernières secondes (ms), 0 si aucune. */
    public int reference() {
        int m = maxCourant;
        for (int v : maxParSeconde) {
            if (v > m) {
                m = v;
            }
        }
        return m;
    }

    /**
     * Une fois par seconde.
     *
     * @param lisseeMs  latence lissée de Minecraft (ServerPlayer.latency)
     * @param ecartMs   écart toléré au-dessus de la plus grande réponse aux transactions
     * @param minimumMs mesure keepalive en dessous de laquelle rien n'est suspect
     * @return nombre de mesures keepalive suspectes de suite (0 : rien, ou pas encore de mesure)
     */
    public int seconde(int lisseeMs, int ecartMs, int minimumMs) {
        int ref = reference();
        maxParSeconde[pos] = maxCourant;
        pos = (pos + 1) % SECONDES;
        maxCourant = 0;
        if (derniereLissee < 0 || lisseeMs == derniereLissee) {
            derniereLissee = lisseeMs;
            return suspectes; // pas de nouvelle mesure keepalive cette seconde
        }
        int mesure = Math.max(0, 4 * lisseeMs - 3 * derniereLissee);
        derniereLissee = lisseeMs;
        derniereMesure = mesure;
        if (ref > 0 && mesure >= minimumMs && mesure > ref + ecartMs) {
            suspectes++;
        } else {
            suspectes = 0;
        }
        return suspectes;
    }

    /** Dernière mesure keepalive retrouvée (ms). */
    public int derniereMesure() {
        return derniereMesure;
    }

    /**
     * Latence à utiliser pour les marges de Spartan : celle de Minecraft, jamais plus que la plus grande réponse aux
     * transactions + 100 ms. Pour un joueur honnête, la même (ou plus juste après un gel) ; un faux lag n'élargit plus
     * les marges.
     */
    public int effective(int lisseeMs) {
        int ref = reference();
        return ref <= 0 ? lisseeMs : Math.min(lisseeMs, ref + 100);
    }

    public void remettre() {
        suspectes = 0;
    }
}
