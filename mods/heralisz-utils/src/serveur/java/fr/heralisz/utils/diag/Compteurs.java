package fr.heralisz.utils.diag;

import java.util.Arrays;
import java.util.Locale;

/**
 * Compteurs permanents des ticks : durée de chaque tick (du début à la fin de l'évènement de tick du serveur, sauvegarde
 * automatique comprise) et écart depuis le début du tick précédent (50 ms quand tout va bien ; plus : le serveur a pris
 * du retard, travail entre deux ticks compris), sur les 18 000 derniers ticks (15 min à 20 TPS).
 * <p>Écrits par le fil du serveur (trois cases de tableau et un compteur par tick), lus par les autres fils : une case
 * en cours de réécriture peut être lue avec sa valeur d'avant, sans conséquence pour des moyennes.
 */
final class Compteurs {
    private Compteurs() {
    }

    static final int N = 18_000;
    private static final float[] DUREE = new float[N];
    private static final float[] ECART = new float[N];
    private static final long[] QUAND = new long[N];
    private static final int[] NUMERO = new int[N];
    /** Nombre de ticks enregistrés depuis le démarrage. */
    static volatile long total;

    /** Fil du serveur, fin d'un tick. */
    static void ajouter(int numero, long dureeNs, long ecartNs, long maintenantMs) {
        long t = total;
        int i = (int) (t % N);
        DUREE[i] = dureeNs / 1_000_000f;
        ECART[i] = ecartNs / 1_000_000f;
        QUAND[i] = maintenantMs;
        NUMERO[i] = numero;
        total = t + 1;
    }

    /** Statistiques d'une fenêtre. */
    record Stats(int ticks, double secondes, double moyenne, double p95, double pire, int pireNumero, double ecartPire,
                 int auDela50, int auDelaSeuil, double tps, double sommeMs) {
        String court() {
            if (ticks == 0) return "aucun tick";
            return String.format(Locale.ROOT, "%d ticks, moyenne %.1f ms, 95 %% sous %.1f ms, pire %.0f ms (tick %d), %.1f TPS, %d > 50 ms, %d > seuil",
                    ticks, moyenne, p95, pire, pireNumero, tps, auDela50, auDelaSeuil);
        }
    }

    /** Ticks finis depuis {@code depuisMs} millisecondes (horloge murale). */
    static Stats fenetre(long depuisMs) {
        long fin = System.currentTimeMillis();
        long debut = fin - depuisMs;
        long t = total;
        int n = (int) Math.min(t, N);
        float[] d = new float[n];
        int k = 0, pireNum = 0, a50 = 0, aSeuil = 0;
        double somme = 0, pire = 0, ecartPire = 0;
        long plusAncien = fin;
        int seuil = DiagConfig.seuilMs;
        for (int j = 0; j < n; j++) {
            int i = (int) ((t - 1 - j) % N);
            long q = QUAND[i];
            if (q < debut) break;
            float v = DUREE[i];
            d[k++] = v;
            somme += v;
            plusAncien = q;
            if (v > pire) {
                pire = v;
                pireNum = NUMERO[i];
            }
            if (ECART[i] > ecartPire) ecartPire = ECART[i];
            if (v > 50) a50++;
            if (v > seuil) aSeuil++;
        }
        return stats(d, k, somme, pire, pireNum, ecartPire, a50, aSeuil, Math.max(0.001, (fin - Math.min(plusAncien, fin)) / 1000.0));
    }

    /** Ticks dont le numéro est dans [premier, dernier] (profil). */
    static Stats numeros(int premier, int dernier, double secondes) {
        long t = total;
        int n = (int) Math.min(t, N);
        float[] d = new float[n];
        int k = 0, pireNum = 0, a50 = 0, aSeuil = 0;
        double somme = 0, pire = 0, ecartPire = 0;
        int seuil = DiagConfig.seuilMs;
        for (int j = 0; j < n; j++) {
            int i = (int) ((t - 1 - j) % N);
            int num = NUMERO[i];
            if (num < premier) break;
            if (num > dernier) continue;
            float v = DUREE[i];
            d[k++] = v;
            somme += v;
            if (v > pire) {
                pire = v;
                pireNum = num;
            }
            if (ECART[i] > ecartPire) ecartPire = ECART[i];
            if (v > 50) a50++;
            if (v > seuil) aSeuil++;
        }
        return stats(d, k, somme, pire, pireNum, ecartPire, a50, aSeuil, Math.max(0.001, secondes));
    }

    private static Stats stats(float[] d, int k, double somme, double pire, int pireNum, double ecartPire, int a50, int aSeuil,
                               double secondes) {
        if (k == 0) return new Stats(0, secondes, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        float[] tri = Arrays.copyOf(d, k);
        Arrays.sort(tri);
        double p95 = tri[Math.min(k - 1, (int) Math.ceil(k * 0.95) - 1)];
        double tps = Math.min(20.0, k / secondes);
        return new Stats(k, secondes, somme / k, p95, pire, pireNum, ecartPire, a50, aSeuil, tps, somme);
    }
}
