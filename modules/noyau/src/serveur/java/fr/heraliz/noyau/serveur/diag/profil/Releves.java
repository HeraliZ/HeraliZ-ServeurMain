package fr.heraliz.noyau.serveur.diag.profil;

import java.util.Arrays;

/**
 * Relevés d'un profil (01/11) : deux arbres des appels (pendant les ticks, entre les ticks), le journal des relevés (pour
 * retrouver ce qui tournait pendant les pires ticks) et le coût mesuré des relevés. Rempli par le fil veilleur, lu par le
 * fil des rapports une fois le profil fini ; toujours sous le verrou de l'objet (jamais pris par le fil serveur).
 */
public final class Releves {
    /** Assez pour 30 min à 4 ms ; au-delà, les pires ticks ne sont plus détaillés (les arbres continuent). */
    private static final int JOURNAL_MAX = 500_000;

    public final Arbre ticks, entre;
    /** relevés entre deux ticks où le fil serveur attendait le tick suivant (rien à faire) */
    public int attente;
    /** relevés écartés : un tick a commencé ou fini pendant le relevé */
    public int ecartes;
    /** coût des relevés pour le fil serveur (durée de getStackTrace, qui l'arrête le temps de lire sa pile) */
    public long coutTotalNanos, coutMaxNanos;
    public int coutNb;

    private Arbre.Noeud[] feuilles = new Arbre.Noeud[4096];
    private int[] tickDe = new int[4096];
    private boolean[] dansTick = new boolean[4096];
    private int nb;
    private boolean ferme;

    public Releves(int maxNoeudsParArbre) {
        ticks = new Arbre(maxNoeudsParArbre);
        entre = new Arbre(maxNoeudsParArbre);
    }

    /**
     * Range un relevé. {@code tick} : numéro du tick en cours (dans le tick) ou du dernier tick fini (entre deux ticks).
     * Entre deux ticks, une pile où le fil serveur attend le tick suivant compte seulement dans {@link #attente}.
     */
    public synchronized void ajouter(StackTraceElement[] pile, boolean dans, int tick, long coutNanos) {
        if (ferme) return;
        coutTotalNanos += coutNanos;
        coutMaxNanos = Math.max(coutMaxNanos, coutNanos);
        coutNb++;
        if (pile.length == 0) return;
        if (!dans && attenteDuTickSuivant(pile)) {
            attente++;
            return;
        }
        Arbre.Noeud f = (dans ? ticks : entre).ajouter(pile);
        if (nb < JOURNAL_MAX) {
            if (nb == feuilles.length) {
                int n = Math.min(JOURNAL_MAX, nb * 2);
                feuilles = Arrays.copyOf(feuilles, n);
                tickDe = Arrays.copyOf(tickDe, n);
                dansTick = Arrays.copyOf(dansTick, n);
            }
            feuilles[nb] = f;
            tickDe[nb] = tick;
            dansTick[nb] = dans;
            nb++;
        }
    }

    public synchronized void ecarter(long coutNanos) {
        if (ferme) return;
        coutTotalNanos += coutNanos;
        coutMaxNanos = Math.max(coutMaxNanos, coutNanos);
        coutNb++;
        ecartes++;
    }

    /** Plus aucun relevé accepté (le veilleur peut finir le sien pendant que le rapport commence). */
    public synchronized void fermer() {
        ferme = true;
    }

    public int journal() {
        return nb;
    }

    public Arbre.Noeud feuille(int i) {
        return feuilles[i];
    }

    public int tick(int i) {
        return tickDe[i];
    }

    public boolean dans(int i) {
        return dansTick[i];
    }

    /** Relevés rangés dans un arbre (hors attente et écartés). */
    public int travail() {
        return ticks.racine.total + entre.racine.total;
    }

    /** Tous les relevés pris (travail, attente, écartés). */
    public int tous() {
        return travail() + attente + ecartes;
    }

    /**
     * Le fil serveur attend le tick suivant : en haut de la pile, une attente de Java (park, sleep, yield), puis seulement
     * la boucle de tâches de Minecraft (BlockableEventLoop) jusqu'à la boucle du serveur. Une attente demandée par autre
     * chose (un tronçon attendu par ServerChunkCache, une tâche qui attend un autre fil) reste du travail.
     */
    static boolean attenteDuTickSuivant(StackTraceElement[] p) {
        if (!Origines.attente(p[0].getClassName(), p[0].getMethodName())) return false;
        for (int i = 1; i < p.length; i++) {
            String c = p[i].getClassName();
            if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("sun.")) continue;
            if (c.equals("net.minecraft.util.thread.BlockableEventLoop") || c.equals("net.minecraft.util.thread.ReentrantBlockableEventLoop")) continue;
            // la boucle du serveur (Mohist peut la mettre dans sa propre classe)
            return c.startsWith("net.minecraft.server.MinecraftServer") || c.startsWith("net.minecraft.server.dedicated.")
                    || c.startsWith("com.mohistmc.");
        }
        return false;
    }
}
