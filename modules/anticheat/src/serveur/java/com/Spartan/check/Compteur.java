package com.Spartan.check;

/**
 * Compte des événements sur une fenêtre glissante (19/11), Java pur, testé (CompteurTest) : refus d'interaction, tirs
 * étalés… L'alerte ne tombe qu'au N-ième dans la fenêtre : un cas isolé (décalage du réseau) ne dérange personne.
 * Tableau fixe des {@code taille} derniers instants, aucun objet créé par événement. Un fil à la fois (fil du serveur).
 */
public final class Compteur {
    private final long[] heures;
    private int pos;
    private int taille;

    public Compteur(int taille) {
        this.heures = new long[Math.max(2, taille)];
    }

    /** Un événement à {@code maintenantMs} ; rend le nombre d'événements des {@code fenetreMs} dernières millisecondes. */
    public int ajouter(long maintenantMs, long fenetreMs) {
        heures[pos] = maintenantMs;
        pos = (pos + 1) % heures.length;
        if (taille < heures.length) {
            taille++;
        }
        return compter(maintenantMs, fenetreMs);
    }

    /** Événements des {@code fenetreMs} dernières millisecondes (au plus la taille du tableau). */
    public int compter(long maintenantMs, long fenetreMs) {
        int n = 0;
        for (int k = 0; k < taille; k++) {
            if (maintenantMs - heures[k] <= fenetreMs) {
                n++;
            }
        }
        return n;
    }

    /** Repart de zéro (après une alerte : la suivante attend N nouveaux événements). */
    public void vider() {
        pos = 0;
        taille = 0;
    }
}
