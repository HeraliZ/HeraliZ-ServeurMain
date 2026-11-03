package com.Spartan.check.combat;

/**
 * Statistiques de tir TaCZ d'un joueur (audit du 02/11, point 14), Java pur, testé. Seuls les tirs qui touchent un
 * JOUEUR comptent (pas les zombies ni les PNJ) ; les N dernières touches sont gardées dans des tableaux fixes.
 *
 * <ul>
 *   <li>tirs à la tête à distance : un aimbot vise la tête à chaque fois, de loin comme de près ; un très bon joueur
 *   aussi parfois, d'où un minimum de touches et un taux élevé ;</li>
 *   <li>« coups secs » : la visée a tourné d'un grand angle juste avant le tir (moins de 100 ms) et le tir a touché la
 *   tête. Un humain le fait parfois ; un aimbot le fait sans cesse ;</li>
 *   <li>tirs refusés par TaCZ lui-même (cadence trop rapide, horloge du tir trafiquée) : un jeu honnête respecte la
 *   cadence de l'arme, un « rapid fire » se fait refuser sans cesse. Les deux sortes sont comptées à part (03/11 : une
 *   coupure réseau, qui fait refuser des tirs pour l'horloge, ne compte plus dans la cadence), et les tirs acceptés de la
 *   même fenêtre sont comptés pour connaître la PART des tirs refusés pour cadence (minigun : 20 tirs par seconde).</li>
 * </ul>
 */
public final class StatsTirs {
    private final boolean[] tete;
    private final boolean[] sec;
    private final float[] distance;
    private int debut;
    private int taille;
    private long tirs;
    private long touchesTotal;
    private long tetesTotal;
    private long refusTotal;
    private final Fenetre refusCadence = new Fenetre();
    private final Fenetre refusHorloge = new Fenetre();
    /** Tirs acceptés par seconde, sur les 64 dernières secondes (case = seconde % 64). */
    private final long[] seconde = new long[64];
    private final int[] tirsSeconde = new int[64];

    /** Heures (ms) des 64 derniers refus d'une sorte. */
    private static final class Fenetre {
        final long[] heures = new long[64];
        int pos;
        int taille;

        int ajouter(long maintenantMs, long fenetreMs) {
            heures[pos] = maintenantMs;
            pos = (pos + 1) % heures.length;
            if (taille < heures.length) {
                taille++;
            }
            int n = 0;
            for (int k = 0; k < taille; k++) {
                if (maintenantMs - heures[k] <= fenetreMs) {
                    n++;
                }
            }
            return n;
        }

        void oublier() {
            taille = 0;
            pos = 0;
        }
    }

    public StatsTirs(int fenetre) {
        int n = Math.max(4, Math.min(512, fenetre));
        tete = new boolean[n];
        sec = new boolean[n];
        distance = new float[n];
    }

    public void tir() {
        tirs++;
    }

    /** Un tir accepté par TaCZ à cette heure (compté aussi pour la part des refus de cadence). */
    public void tir(long maintenantMs) {
        tirs++;
        long s = Math.floorDiv(maintenantMs, 1000L);
        int i = (int) Math.floorMod(s, (long) seconde.length);
        if (seconde[i] != s) {
            seconde[i] = s;
            tirsSeconde[i] = 0;
        }
        tirsSeconde[i]++;
    }

    /** Tirs acceptés pendant les {@code fenetreMs} dernières millisecondes (à la seconde près, 64 s au plus). */
    public int tirsRecents(long maintenantMs, long fenetreMs) {
        long s = Math.floorDiv(maintenantMs, 1000L);
        long secondes = Math.min(seconde.length, Math.max(1L, (fenetreMs + 999L) / 1000L));
        int n = 0;
        for (int i = 0; i < seconde.length; i++) {
            if (s - seconde[i] < secondes && s >= seconde[i]) {
                n += tirsSeconde[i];
            }
        }
        return n;
    }

    /** Un tir a touché un joueur. */
    public void touche(boolean aLaTete, double dist, boolean coupSec) {
        int i;
        if (taille == tete.length) {
            i = debut;
            debut = (debut + 1) % tete.length;
        } else {
            i = (debut + taille) % tete.length;
            taille++;
        }
        tete[i] = aLaTete;
        sec[i] = coupSec && aLaTete;
        distance[i] = (float) dist;
        touchesTotal++;
        if (aLaTete) {
            tetesTotal++;
        }
    }

    /** Touches gardées (au plus la fenêtre). */
    public int touches() {
        return taille;
    }

    /** Touches à au moins {@code distMin} blocs. */
    public int touchesLoin(double distMin) {
        int n = 0;
        for (int k = 0; k < taille; k++) {
            if (distance[(debut + k) % tete.length] >= distMin) {
                n++;
            }
        }
        return n;
    }

    /** Touches à la tête à au moins {@code distMin} blocs. */
    public int tetesLoin(double distMin) {
        int n = 0;
        for (int k = 0; k < taille; k++) {
            int i = (debut + k) % tete.length;
            if (tete[i] && distance[i] >= distMin) {
                n++;
            }
        }
        return n;
    }

    /** Touches à la tête juste après un grand mouvement de visée. */
    public int coupsSecs() {
        int n = 0;
        for (int k = 0; k < taille; k++) {
            if (sec[(debut + k) % tete.length]) {
                n++;
            }
        }
        return n;
    }

    /** Oublie les touches gardées (après une alerte : la suivante repart de zéro). */
    public void oublierTouches() {
        debut = 0;
        taille = 0;
    }

    /**
     * Un tir refusé par TaCZ, pour cadence ({@code cadence}) ou pour l'horloge ; rend le nombre de refus de la MÊME sorte
     * pendant les {@code fenetreMs} dernières millisecondes.
     */
    public int refus(boolean cadence, long maintenantMs, long fenetreMs) {
        refusTotal++;
        return (cadence ? refusCadence : refusHorloge).ajouter(maintenantMs, fenetreMs);
    }

    /** Oublie les refus d'une sorte (après une alerte : la suivante repart de zéro). */
    public void oublierRefus(boolean cadence) {
        (cadence ? refusCadence : refusHorloge).oublier();
    }

    /** « 120 tirs, 34 touches de joueurs dont 12 à la tête, 0 refus » (journal). */
    public String resume() {
        return tirs + " tir(s), " + touchesTotal + " touche(s) de joueurs dont " + tetesTotal + " à la tête, " + refusTotal
                + " tir(s) refusé(s) par TaCZ";
    }

    public long tirs() {
        return tirs;
    }

    public long touchesTotal() {
        return touchesTotal;
    }
}
