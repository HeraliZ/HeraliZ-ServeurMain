package com.Spartan.check.movement.model;

/**
 * Silences du réseau d'un joueur (19/11), Java pur, testé (ArriveesTirsTest) : trous dans l'arrivée de ses paquets.
 *
 * <p>Avec les transactions de Spartan, le jeu du joueur répond à un ping par tick : il envoie au moins un paquet toutes les
 * 50 ms. Un trou plus long veut dire que le réseau a retenu ses paquets (Wi-Fi, 4G, coupure) ; quand il se vide, tout ce que
 * le joueur a fait pendant le trou arrive d'un coup. La cadence réelle des tirs ({@code CadenceTirs}) s'en sert pour ne pas
 * prendre ces tirs groupés par le réseau pour une rafale trafiquée.
 *
 * <p>Fil réseau du joueur seulement (Netty : un fil par connexion) ; tableaux fixes, aucun objet créé par paquet.
 */
public final class Silences {
    /** Le jeu répond au moins toutes les 50 ms (un ping par tick) : seul l'excès au-delà compte. */
    static final long NORMAL = 50_000_000L;
    /** Excès noté à partir de 30 ms (en dessous : à-coups ordinaires). */
    static final long SEUIL = 30_000_000L;
    private static final int TAILLE = 16;
    private final long[] fin = new long[TAILLE];
    private final long[] exces = new long[TAILLE];
    private int pos;
    private int taille;
    private boolean vu;
    private long dernier;

    /** Un paquet du joueur vient d'arriver ({@code System.nanoTime()}). */
    public void paquet(long nanos) {
        if (vu) {
            long ex = nanos - dernier - NORMAL;
            if (ex > SEUIL) {
                fin[pos] = nanos;
                exces[pos] = ex;
                pos = (pos + 1) % TAILLE;
                if (taille < TAILLE) {
                    taille++;
                }
            }
        }
        vu = true;
        dernier = nanos;
    }

    /** Plus long silence (excès sur 50 ms, en nanosecondes) fini dans les {@code fenetreNanos} avant {@code nanos}. */
    public long recent(long nanos, long fenetreNanos) {
        long m = 0;
        for (int i = 0; i < taille; i++) {
            if (nanos - fin[i] <= fenetreNanos && exces[i] > m) {
                m = exces[i];
            }
        }
        return m;
    }
}
