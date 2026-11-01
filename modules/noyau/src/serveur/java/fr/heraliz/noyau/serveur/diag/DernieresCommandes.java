package fr.heraliz.noyau.serveur.diag;

import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Les dernières commandes jouées, pour les rapports de blocage (01/11, « grosse montée de ms quand je faisais des
 * commandes ») : heure et mots de la commande seulement (« /clan info »), jamais les arguments ni qui l'a tapée (ni mot de
 * passe, ni message privé, ni pseudo dans les rapports). 16 au plus, en mémoire, fil serveur seulement (l'événement de
 * Forge passe sur le fil serveur) ; ne coûte qu'une ligne par commande.
 * <p>
 * Durée de chaque commande (01/11, profileur) : {@code CommandesDureeMixin} et {@code CommandeJoueurDureeMixin} appellent
 * {@link #debut} et {@link #fin} autour de l'exécution (commandes des joueurs, plugins compris, et de la console) ; la
 * durée va dans la liste ci-dessus, dans les totaux depuis le démarrage ({@code /diag commandes}) et dans le profil en
 * cours. Deux lectures d'horloge par commande.
 */
public final class DernieresCommandes {
    private DernieresCommandes() {
    }

    private static final int GARDEES = 16, NOMS_MAX = 300;
    private static final long[] QUAND = new long[GARDEES];
    private static final String[] QUOI = new String[GARDEES];
    /** durée de la commande (ns), -1 si inconnue */
    private static final long[] DUREE = new long[GARDEES];
    private static int pos, nb;

    // mesure en cours (fil serveur) : la plus extérieure seulement (une commande peut en jouer d'autres)
    private static int profondeur;
    private static long debutMesure;
    private static int entreeMesure = -1;
    private static String secours;
    /** nom -> [fois, ns en tout, ns au pire], depuis le démarrage */
    private static final Map<String, long[]> TOTAUX = new HashMap<>();

    static void enregistrer() {
        // en dernier, refusées comprises : on note ce qui est vraiment parti (ou refusé par une règle)
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, DernieresCommandes::noter);
    }

    private static void noter(CommandEvent e) {
        CommandContextBuilder<CommandSourceStack> b = e.getParseResults().getContext();
        StringBuilder s = new StringBuilder("/");
        List<ParsedCommandNode<CommandSourceStack>> noeuds = b.getNodes();
        // les mots fixes du début (racine et sous-commandes), jusqu'au premier argument
        for (int i = 0; i < noeuds.size() && i < 3 && noeuds.get(i).getNode() instanceof LiteralCommandNode<?>; i++) {
            if (i > 0) s.append(' ');
            s.append(noeuds.get(i).getNode().getName());
        }
        if (s.length() == 1) {
            // commande inconnue de Brigadier : seulement son premier mot
            s.append(premierMot(e.getParseResults().getReader().getString())).append(" (inconnue de Minecraft)");
        }
        if (b.getSource().getEntity() == null) s.append(" (console)");
        if (e.isCanceled()) s.append(" (refusée)");
        synchronized (QUAND) {
            int i = ajouter(System.nanoTime(), s.toString(), -1);
            if (profondeur > 0 && entreeMesure < 0 && Thread.currentThread() == Diag.filServeur()) entreeMesure = i;
        }
    }

    /** sous le verrou ; rend la place prise */
    private static int ajouter(long quand, String quoi, long duree) {
        int i = pos;
        QUAND[i] = quand;
        QUOI[i] = quoi;
        DUREE[i] = duree;
        pos = (pos + 1) % GARDEES;
        if (nb < GARDEES) nb++;
        return i;
    }

    private static String premierMot(String brut) {
        String t = brut.trim();
        if (t.startsWith("/")) t = t.substring(1);
        int sp = t.indexOf(' ');
        String racine = sp < 0 ? t : t.substring(0, sp);
        return racine.length() > 32 ? racine.substring(0, 32) : racine;
    }

    // ------------------------------------------------------------------ durées (mixins)
    /**
     * Début d'une commande. {@code brut} : le texte tapé par un joueur (seul son premier mot est gardé, pour une commande
     * qu'aucun événement de Forge ne nomme, celles des plugins), ou null.
     */
    public static void debut(String brut) {
        if (Thread.currentThread() != Diag.filServeur()) return;
        if (profondeur++ > 0) return;
        debutMesure = System.nanoTime();
        entreeMesure = -1;
        secours = brut == null ? null : "/" + premierMot(brut);
    }

    public static void fin() {
        if (Thread.currentThread() != Diag.filServeur() || profondeur <= 0) return;
        if (--profondeur > 0) return;
        long d = System.nanoTime() - debutMesure;
        String nom;
        synchronized (QUAND) {
            if (entreeMesure >= 0 && QUAND[entreeMesure] - debutMesure >= 0) {
                DUREE[entreeMesure] = d;
                nom = QUOI[entreeMesure];
            } else {
                nom = secours != null ? secours + " (plugin ou commande sans événement)" : "/? (commande sans nom)";
                ajouter(debutMesure, nom, d);
            }
        }
        entreeMesure = -1;
        long[] t = TOTAUX.get(nom);
        if (t == null && TOTAUX.size() < NOMS_MAX) TOTAUX.put(nom, t = new long[3]);
        if (t != null) {
            t[0]++;
            t[1] += d;
            t[2] = Math.max(t[2], d);
        }
        Profileur.commande(nom, d);
    }

    /** Début de chaque tick : une commande sortie par une exception ne laisse pas la mesure ouverte. */
    static void nouveauTick() {
        profondeur = 0;
    }

    /** La plus récente commande jouée depuis {@code depuisNanos} (horloge System.nanoTime), ou null. */
    static String depuis(long depuisNanos) {
        synchronized (QUAND) {
            if (nb == 0) return null;
            int i = (pos - 1 + GARDEES) % GARDEES;
            if (QUAND[i] - depuisNanos < 0) return null;
            return DUREE[i] >= 0 ? QUOI[i] + String.format(Locale.ROOT, " (%.0f ms)", DUREE[i] / 1e6) : QUOI[i];
        }
    }

    /** Section du rapport : les commandes des 60 dernières secondes, de la plus récente à la plus ancienne. */
    static void ecrire(StringBuilder t, long maintenant) {
        t.append("== Dernières commandes (60 s ; mots de la commande seulement ; durée sur le fil serveur) ==\n");
        int ecrites = 0;
        synchronized (QUAND) {
            for (int k = 1; k <= nb; k++) {
                int i = (pos - k + GARDEES) % GARDEES;
                long ms = (maintenant - QUAND[i]) / 1_000_000L;
                if (ms > 60_000) break;
                t.append(String.format(Locale.ROOT, "  il y a %6d ms : %s%s%n", ms, QUOI[i],
                        DUREE[i] >= 0 ? String.format(Locale.ROOT, "  (%.1f ms)", DUREE[i] / 1e6) : ""));
                ecrites++;
            }
        }
        if (ecrites == 0) t.append("  aucune\n");
        t.append('\n');
    }

    /** /diag commandes : les plus lentes depuis le démarrage (pire durée), une ligne chacune. */
    static void plusLentes(int n, Consumer<String> ligne) {
        List<Map.Entry<String, long[]>> l = new ArrayList<>(TOTAUX.entrySet());
        l.sort((x, y) -> Long.compare(y.getValue()[2], x.getValue()[2]));
        for (int i = 0; i < Math.min(n, l.size()); i++) {
            long[] a = l.get(i).getValue();
            ligne.accept(String.format(Locale.ROOT, "%s : %d fois, %.1f ms en moyenne, pire %.0f ms", l.get(i).getKey(), a[0],
                    a[1] / 1e6 / Math.max(1, a[0]), a[2] / 1e6));
        }
    }

    static boolean rienMesure() {
        return TOTAUX.isEmpty();
    }
}
