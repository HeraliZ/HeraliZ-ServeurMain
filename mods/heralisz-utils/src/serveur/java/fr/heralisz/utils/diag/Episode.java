package fr.heralisz.utils.diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un tick long (ou un long travail entre deux ticks) suivi par le chien de garde : ses échantillons, sa durée et son
 * contexte. Rempli par le fil du chien de garde, puis remis au fil d'écriture qui seul le lit ensuite.
 */
final class Episode {
    /** Un échantillon gardé en entier (liste d'un blocage). */
    record Ech(int ms, Thread.State etat, String verrou, int[] pile, String entite) {
    }

    static final int MAX_LISTE = 2000;

    /** true : pendant un tick ; false : entre deux ticks (tâches du serveur : paquets des joueurs, tronçons…). */
    final boolean dansTick;
    final int numero;
    /** Début (horloge murale, ms) : début du tick, ou heure à laquelle le tick suivant aurait dû commencer. */
    final long debutMs;
    final Agregat agregat = new Agregat();
    final List<Ech> liste = new ArrayList<>();
    final Instantane contexte;
    final List<String> commandes;
    final int seuilMs, blocageMs, intervalleMs;

    double dureeMs;
    long finMs;
    boolean fini;
    int premierMs = -1;
    long trouMs;
    String tousLesFils;
    int tousLesFilsMs;
    String filsEnAttente;
    int filsEnAttenteMs;

    Episode(boolean dansTick, int numero, long debutMs, Instantane contexte, List<String> commandes) {
        this.dansTick = dansTick;
        this.numero = numero;
        this.debutMs = debutMs;
        this.contexte = contexte;
        this.commandes = commandes;
        this.seuilMs = DiagConfig.seuilMs;
        this.blocageMs = DiagConfig.blocageMs;
        this.intervalleMs = DiagConfig.intervalleMs;
    }

    boolean blocage() {
        return dureeMs >= blocageMs;
    }

    String phase() {
        return dansTick ? "pendant le tick n° " + numero : "entre le tick n° " + numero + " et le suivant (tâches du serveur)";
    }

    /** Temps représenté par un échantillon (partie échantillonnée de l'épisode / nombre d'échantillons). */
    double msParEchantillon() {
        int n = agregat.echantillons;
        if (n == 0) return intervalleMs;
        double echantillonne = Math.max(intervalleMs, dureeMs - Math.max(0, premierMs) - trouMs);
        return echantillonne / n;
    }

    /** Une ligne pour la liste d'un regroupement. */
    String ligne() {
        return String.format(Locale.ROOT, "%s  %7.0f ms  %s  (%d éch.)  ->  %s", Rapports.heure(debutMs), dureeMs,
                dansTick ? "tick " + numero : "entre " + numero + "/" + (numero + 1), agregat.echantillons,
                agregat.echantillons == 0 ? "fini avant le premier échantillon" : agregat.tete(3));
    }

    /** Texte complet (rapport « blocage », ou provisoire pendant un blocage qui dure). */
    String texte(boolean provisoire) {
        StringBuilder b = new StringBuilder(64 * 1024);
        b.append("HeraliZ Utils · diagnostic du serveur\n");
        b.append(provisoire ? "Rapport : BLOCAGE EN COURS (provisoire : le serveur est encore bloqué)\n" : "Rapport : BLOCAGE du serveur\n");
        b.append("Quand : ").append(Rapports.date(debutMs)).append(" (heure du serveur)\n");
        b.append(String.format(Locale.ROOT, "Durée : %.0f ms %s%s%n", dureeMs, provisoire ? "jusqu'ici, " : "", phase()));
        entete(b);
        agregat.ecrire(b, msParEchantillon(), 15);
        if (tousLesFils != null) {
            b.append("== Piles de tous les fils, à ").append(tousLesFilsMs).append(" ms ==\n").append(tousLesFils).append('\n');
        }
        if (filsEnAttente != null) {
            b.append("== Piles de tous les fils quand le fil du serveur attendait, à ").append(filsEnAttenteMs).append(" ms ==\n")
                    .append(filsEnAttente).append('\n');
        }
        b.append("== Chaque échantillon (pile entière ; « = » : même pile que l'échantillon d'avant) ==\n");
        int[] avant = null;
        for (Ech e : liste) {
            b.append(String.format(Locale.ROOT, "-- %6d ms  %s", e.ms, e.etat));
            if (e.verrou != null) b.append("  ").append(e.verrou);
            if (e.entite != null) b.append("  entité : ").append(e.entite);
            if (avant != null && java.util.Arrays.equals(avant, e.pile)) {
                b.append("  =\n");
            } else {
                b.append('\n');
                agregat.pile(b, e.pile);
            }
            avant = e.pile;
        }
        if (agregat.echantillons > liste.size()) {
            b.append("(").append(agregat.echantillons - liste.size()).append(" échantillon(s) de plus, comptés plus haut mais non listés)\n");
        }
        return b.toString();
    }

    /** En-tête commun : échantillonnage, ramasse-miettes, contexte, commandes. */
    void entete(StringBuilder b) {
        b.append(String.format(Locale.ROOT, "Échantillonné de %s ms à la fin, un échantillon toutes les %d ms : %d échantillon(s)%n",
                premierMs < 0 ? "?" : String.valueOf(premierMs), intervalleMs, agregat.echantillons));
        b.append(String.format(Locale.ROOT, "Seuils : tick long %d ms, blocage %d ms%n", seuilMs, blocageMs));
        if (trouMs > 0) {
            b.append(String.format(Locale.ROOT, "Trous entre les échantillons : %d ms (la JVM entière était arrêtée : ramasse-miettes, "
                    + "machine saturée ou mise en pause)%n", trouMs));
        }
        List<PausesGc.Pause> gc = PausesGc.entre(debutMs, finMs > 0 ? finMs : System.currentTimeMillis());
        b.append("Ramasse-miettes pendant ce temps : ").append(PausesGc.resume(gc)).append('\n');
        Runtime rt = Runtime.getRuntime();
        b.append(String.format(Locale.ROOT, "Mémoire : %d / %d Mo%n", (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20));
        if (!commandes.isEmpty()) {
            b.append("Dernières commandes (nom seul, sans arguments) : ").append(String.join(", ", commandes)).append('\n');
        }
        b.append('\n');
        if (contexte != null) contexte.decrire(b, "Dernier relevé du serveur (avant l'épisode)");
    }
}
