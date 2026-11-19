package com.Spartan.check.combat;

/**
 * Cadence réelle des tirs TaCZ (19/11, demande du propriétaire : « plus on peut mettre côté serveur, mieux c'est »), Java
 * pur, testé (CadenceTirsTest).
 *
 * <p>Le trou : TaCZ juge la cadence d'une arme sur l'heure du tir ÉCRITE PAR LE JEU DU JOUEUR (écart avec le tir
 * précédent), et ne vérifie cette heure contre la sienne qu'à 300 ms près en avance et ~400 ms en retard. Un jeu modifié
 * peut donc envoyer d'un coup, dans le même paquet réseau, une rafale de tirs datés à la bonne cadence : jusqu'à 700 ms de
 * tirs partent dans le même tick (8 balles d'un fusil à 600 coups/min, 15 d'un minigun), sans un seul refus de TaCZ.
 *
 * <p>Ici, chaque tir est daté par son ARRIVÉE sur le réseau du serveur (que le jeu ne choisit pas). Un joueur honnête envoie
 * ses tirs au rythme où il les tire : l'heure annoncée et l'heure d'arrivée avancent ensemble, au retard du réseau près.
 * Pour chaque tir, la « dette » est l'avance prise par les heures annoncées sur les heures d'arrivée depuis les tirs des
 * dernières {@code fenetreMs} : {@code max(arrivée − heure annoncée des tirs récents) − (arrivée − heure annoncée de ce
 * tir)}. Sont permis sans rien faire :
 * <ul>
 *   <li>{@code toleranceMs} (150 ms par défaut) : petits à-coups du réseau ;</li>
 *   <li>le silence réseau qui vient de finir ({@code silenceMs}, rien reçu du jeu du joueur pendant ce temps) : après une
 *   coupure, les tirs faits pendant la coupure arrivent tous ensemble, c'est normal.</li>
 * </ul>
 * Au-delà, le tir n'est PAS refusé : il est ÉTALÉ (joué plus tard, à la cadence de l'arme). Le tricheur perd sa rafale ; un
 * joueur honnête dont le réseau s'est vidé d'un coup (rare) voit au pire quelques tirs partir 50 à 200 ms plus tard. Seul
 * un tir qui devrait attendre plus de {@code attenteMaxMs} (cadence soutenue plus rapide que l'arme) est refusé.
 *
 * <p>Tableaux fixes : aucun objet créé par tir. Fil du serveur seulement.
 */
public final class CadenceTirs {
    /** Réponse de {@link #tir} : tir refusé (il devrait attendre trop longtemps). */
    public static final double REFUSE = -1.0;
    private static final int TAILLE = 48;
    /** Arrivée effective de chaque tir gardé (ms, après étalement). */
    private final double[] arrivee = new double[TAILLE];
    /** Arrivée effective moins heure annoncée (ms) : le retard apparent de chaque tir gardé. */
    private final double[] retard = new double[TAILLE];
    private int pos;
    private int taille;
    private double derniereDette;

    /** Oublie les tirs gardés (horloge des tirs de TaCZ recalée, changement de monde). */
    public void oublier() {
        pos = 0;
        taille = 0;
        derniereDette = 0;
    }

    /**
     * Un tir arrivé sur le réseau à {@code arriveeMs} (horloge du serveur), daté {@code heureMs} par le jeu du joueur
     * (horloge des tirs de TaCZ), juste après un silence réseau de {@code silenceMs} (0 sinon).
     *
     * @return 0 : le tir part tout de suite ; plus de 0 : il part autant de millisecondes après son arrivée ;
     *         {@link #REFUSE} : il devrait attendre plus de {@code attenteMaxMs}, il ne part pas
     */
    public double tir(double arriveeMs, long heureMs, double silenceMs, double toleranceMs, double fenetreMs,
                      double attenteMaxMs) {
        double e = arriveeMs - heureMs;
        double max = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < taille; k++) {
            if (arriveeMs - arrivee[k] <= fenetreMs && retard[k] > max) {
                max = retard[k]; // un tir étalé (arrivée effective plus tard que celle-ci) compte aussi
            }
        }
        double dette = max == Double.NEGATIVE_INFINITY ? 0.0 : Math.max(0.0, max - e);
        derniereDette = dette;
        double etaler = Math.max(0.0, dette - toleranceMs - Math.max(0.0, silenceMs));
        if (etaler > attenteMaxMs) {
            return REFUSE;
        }
        arrivee[pos] = arriveeMs + etaler;
        retard[pos] = e + etaler;
        pos = (pos + 1) % TAILLE;
        if (taille < TAILLE) {
            taille++;
        }
        return etaler;
    }

    /** Avance prise par l'heure annoncée du dernier tir jugé (ms) : pour les chiffres de l'alerte. */
    public double derniereDette() {
        return derniereDette;
    }
}
