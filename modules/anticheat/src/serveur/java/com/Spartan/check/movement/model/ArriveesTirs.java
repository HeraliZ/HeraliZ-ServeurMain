package com.Spartan.check.movement.model;

/**
 * Arrivées des tirs TaCZ d'un joueur sur le réseau (19/11), Java pur, testé (ArriveesTirsTest).
 *
 * <p>Le fil réseau du joueur (observateur de Spartan, avant tout traitement) note chaque paquet de tir de TaCZ avec l'heure
 * annoncée par le jeu, son heure d'arrivée et le silence réseau qui venait de finir ; le fil du serveur, quand TaCZ traite
 * ce tir, reprend ces chiffres ({@link #prendre}). Le tir est retrouvé par son heure annoncée (exacte) ; à défaut (TaCZ
 * vient de recaler son horloge et l'heure a été recomptée), par ordre d'arrivée : les tirs sont traités dans l'ordre où
 * ils arrivent. Ce qui a plus de 3 s n'est plus repris (tir refusé avant d'être jugé, par exemple sans arme en main).
 *
 * <p>Écrit par le fil réseau, lu par le fil du serveur : méthodes synchronisées (au plus quelques dizaines par seconde).
 */
public final class ArriveesTirs {
    /** Au-delà, une arrivée notée n'est plus reprise. */
    static final long PERIME = 3_000_000_000L;
    private static final int TAILLE = 32;
    private final long[] heure = new long[TAILLE];
    private final long[] arrivee = new long[TAILLE];
    private final long[] silence = new long[TAILLE];
    private int debut;
    private int taille;

    /** Fil réseau : un tir annoncé à {@code heureMs} arrive à {@code nanos}, après un silence de {@code silenceNanos}. */
    public synchronized void noter(long heureMs, long nanos, long silenceNanos) {
        int i;
        if (taille == TAILLE) {
            i = debut; // le plus ancien est remplacé
            debut = (debut + 1) % TAILLE;
        } else {
            i = (debut + taille) % TAILLE;
            taille++;
        }
        heure[i] = heureMs;
        arrivee[i] = nanos;
        silence[i] = silenceNanos;
    }

    /**
     * Fil du serveur : arrivée du tir annoncé à {@code heureMs}, retirée de la liste avec celles qui la précèdent.
     *
     * @param sortie reçoit {arrivée (nanos), silence (nanos)}
     * @return false si aucune arrivée récente ne correspond
     */
    public synchronized boolean prendre(long heureMs, long maintenant, long[] sortie) {
        while (taille > 0 && maintenant - arrivee[debut] > PERIME) {
            debut = (debut + 1) % TAILLE;
            taille--;
        }
        if (taille == 0) {
            return false;
        }
        int trouve = 0; // à défaut d'heure exacte : la plus ancienne (ordre d'arrivée)
        for (int k = 0; k < taille; k++) {
            if (heure[(debut + k) % TAILLE] == heureMs) {
                trouve = k;
                break;
            }
        }
        int i = (debut + trouve) % TAILLE;
        sortie[0] = arrivee[i];
        sortie[1] = silence[i];
        debut = (i + 1) % TAILLE;
        taille -= trouve + 1;
        return true;
    }
}
