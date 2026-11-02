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
 *   cadence de l'arme, un « rapid fire » se fait refuser sans cesse.</li>
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
    private final long[] refus = new long[64];
    private int refusPos;
    private int refusTaille;

    public StatsTirs(int fenetre) {
        int n = Math.max(4, Math.min(512, fenetre));
        tete = new boolean[n];
        sec = new boolean[n];
        distance = new float[n];
    }

    public void tir() {
        tirs++;
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

    /** Un tir refusé par TaCZ ; rend le nombre de refus des {@code fenetreMs} dernières millisecondes. */
    public int refus(long maintenantMs, long fenetreMs) {
        refusTotal++;
        refus[refusPos] = maintenantMs;
        refusPos = (refusPos + 1) % refus.length;
        if (refusTaille < refus.length) {
            refusTaille++;
        }
        int n = 0;
        for (int k = 0; k < refusTaille; k++) {
            if (maintenantMs - refus[k] <= fenetreMs) {
                n++;
            }
        }
        return n;
    }

    public void oublierRefus() {
        refusTaille = 0;
        refusPos = 0;
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
