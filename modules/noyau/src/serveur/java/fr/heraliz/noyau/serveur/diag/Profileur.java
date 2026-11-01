package fr.heraliz.noyau.serveur.diag;

import fr.heraliz.noyau.serveur.diag.profil.NomsSrg;
import fr.heraliz.noyau.serveur.diag.profil.RapportProfil;
import fr.heraliz.noyau.serveur.diag.profil.Releves;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profileur du serveur façon spark (01/11, demande du propriétaire : « un truc qui nous permette de voir ce qui fait lag,
 * quelle partie du code, quel fichier, un truc ultra précis »). {@code /diag profil [secondes] [intervalle]} (op 4 ou
 * console) :
 * <ul>
 *     <li>le fil veilleur de {@link Diag} relève la pile du fil serveur toutes les {@code intervalle} ms (4 à 10),
 *     PENDANT les ticks et ENTRE les ticks (commandes, paquets des joueurs, tâches) ; entre deux ticks, une pile qui
 *     attend juste le tick suivant est comptée à part ;</li>
 *     <li>la durée de chaque tick est gardée (moyenne, 95 %, pire, et ce qui tournait pendant les pires) ;</li>
 *     <li>{@link Systemes} mesure exactement le temps par type d'entité, d'entité de bloc, par monde, par événement, les
 *     tronçons et les paquets ;</li>
 *     <li>à la fin, le rapport ({@link RapportProfil}) est fait sur le fil des rapports, jamais sur le fil serveur, puis
 *     écrit dans {@code diag/profil-<date>.txt} ; résumé dans le chat de celui qui l'a lancé (ou la console).</li>
 * </ul>
 * Coût : rien hors profil (une lecture d'un booléen par tick) ; pendant un profil, l'arrêt du fil serveur le temps de lire
 * sa pile (mesuré et écrit dans le rapport, de l'ordre de 20 à 100 µs par relevé, soit 0,5 à 2 % à 5 ms) plus les mesures
 * de {@link Systemes}.
 */
public final class Profileur {
    private Profileur() {
    }

    private static final DateTimeFormatter NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** nœuds au plus par arbre (pendant les ticks, entre les ticks) : ~25 Mo chacun au pire */
    private static final int MAX_NOEUDS = 300_000;

    /** profil en cours : lu par le veilleur et le fil serveur */
    static volatile boolean actif;
    static volatile int intervalleMs = 5;
    /** numéro du tick en cours (dans le tick) ou du dernier fini (entre deux ticks), -1 avant le premier */
    static volatile int tickNo = -1;
    private static volatile Releves releves;

    // fil serveur seulement
    private static long debutNanos, debutMs, finPrevueMs, prochainResume, gcDebut, cpuDebut;
    private static int dureeDemandee, resumeDepuis;
    private static UUID demandeur;
    private static boolean console;
    private static double[] ticksMs = new double[0];
    private static long[] ticksDebutMs = new long[0];
    private static int nbTicks;
    private static final Map<String, long[]> COMMANDES = new HashMap<>();

    /** Lance un profil ; faux si un profil tourne déjà. {@code qui} : joueur à prévenir (null : la console). */
    public static boolean lancer(MinecraftServer srv, int secondes, int intervalle, UUID qui) {
        if (actif) return false;
        intervalleMs = Math.max(1, Math.min(50, intervalle));
        demandeur = qui;
        console = qui == null;
        dureeDemandee = secondes;
        int capacite = secondes * 20 + 200;
        ticksMs = new double[capacite];
        ticksDebutMs = new long[capacite];
        nbTicks = 0;
        resumeDepuis = 0;
        tickNo = -1;
        COMMANDES.clear();
        debutNanos = System.nanoTime();
        debutMs = System.currentTimeMillis();
        finPrevueMs = debutMs + secondes * 1000L;
        prochainResume = debutMs + 10_000;
        gcDebut = Diag.tempsGc();
        cpuDebut = cpuFil();
        Systemes.demarrer(srv);
        releves = new Releves(MAX_NOEUDS);
        actif = true;
        return true;
    }

    public static boolean enCours() {
        return actif;
    }

    // ------------------------------------------------------------------ fil veilleur
    /** Un relevé (fil veilleur) ; {@code memeEtat} faux si un tick a commencé ou fini pendant la lecture de la pile. */
    static void ranger(StackTraceElement[] pile, boolean dans, int tick, boolean memeEtat, long coutNanos) {
        Releves r = releves;
        if (r == null) return;
        if (memeEtat) r.ajouter(pile, dans, tick, coutNanos);
        else r.ecarter(coutNanos);
    }

    // ------------------------------------------------------------------ fil serveur
    /** Début d'un tick (après la mesure de Diag). */
    static void debutTick() {
        if (!actif) return;
        if (nbTicks == ticksMs.length) {
            ticksMs = Arrays.copyOf(ticksMs, nbTicks * 2);
            ticksDebutMs = Arrays.copyOf(ticksDebutMs, nbTicks * 2);
        }
        ticksDebutMs[nbTicks] = System.currentTimeMillis();
        tickNo = nbTicks;
    }

    /** Fin d'un tick : sa durée, le résumé toutes les 10 s, la fin du profil. */
    static void finTick(MinecraftServer srv, double ms) {
        if (!actif || tickNo != nbTicks) return;
        ticksMs[nbTicks++] = ms;
        Systemes.tick(srv);
        long maintenant = System.currentTimeMillis();
        if (maintenant >= finPrevueMs) {
            arreter(srv);
        } else if (maintenant >= prochainResume) {
            prochainResume = maintenant + 10_000;
            resumeEnDirect(srv);
        }
    }

    /** Commande mesurée pendant le profil (nom seulement). */
    static void commande(String nom, long nanos) {
        if (!actif) return;
        long[] a = COMMANDES.computeIfAbsent(nom, k -> new long[3]);
        a[0]++;
        a[1] += nanos;
        a[2] = Math.max(a[2], nanos);
    }

    private static void resumeEnDirect(MinecraftServer srv) {
        Releves r = releves;
        if (r == null) return;
        double somme = 0, max = 0;
        for (int i = resumeDepuis; i < nbTicks; i++) {
            somme += ticksMs[i];
            max = Math.max(max, ticksMs[i]);
        }
        int n = nbTicks - resumeDepuis;
        resumeDepuis = nbTicks;
        String moy = String.format(Locale.ROOT, "%.1f", n == 0 ? 0 : somme / n), pire = String.valueOf(Math.round(max)), tps = Diag.tps();
        UUID qui = demandeur;
        boolean cons = console;
        Diag.surLeFilDesRapports(() -> {
            String mods;
            synchronized (r) {
                mods = RapportProfil.resumeDirect(r.ticks, Profileur::proprietaireMixin, 3);
            }
            String m = NomsSrg.traduire(mods);
            srv.execute(() -> dire(srv, qui, cons, Component.translatableWithFallback("noyau.diag.direct", "[Diag] %s ms/tick (max %s), %s TPS — %s",
                    moy, pire, tps, m).withStyle(s -> s.withColor(0x8FD3FF))));
        });
    }

    /** Arrête le profil et lance l'écriture du rapport ; nom du fichier, ou null s'il n'y avait pas de profil. */
    public static String arreter(MinecraftServer srv) {
        Releves r = releves;
        if (!actif || r == null) return null;
        actif = false;
        releves = null;
        long dureeNanos = System.nanoTime() - debutNanos;
        double secondes = dureeNanos / 1e9;
        RapportProfil.Entrees e = new RapportProfil.Entrees();
        e.releves = r;
        e.nbTicks = nbTicks;
        e.ticksMs = Arrays.copyOf(ticksMs, nbTicks);
        e.ticksDebutMs = Arrays.copyOf(ticksDebutMs, nbTicks);
        e.debutMs = debutMs;
        e.dureeNanos = dureeNanos;
        e.intervalleMs = intervalleMs;
        e.gcMs = Diag.tempsGc() - gcDebut;
        long cpu = cpuFil();
        e.cpuFil = cpu < 0 || cpuDebut < 0 ? -1 : 100.0 * (cpu - cpuDebut) / Math.max(1, dureeNanos);
        e.lanceur = console ? "la console" : "un opérateur";
        e.systemes = Systemes.arreter(srv, nbTicks, secondes);
        e.commandes = texteCommandes(secondes);
        StringBuilder etat = new StringBuilder();
        Diag.etatServeur(srv, etat::append);
        e.etat = etat.toString();
        e.proprietaireMixin = Profileur::proprietaireMixin;
        e.nomMethode = NomsSrg::nom;
        String nom = "profil-" + LocalDateTime.now().format(NOM) + ".txt";
        UUID qui = demandeur;
        boolean cons = console;
        java.nio.file.Path racine = srv.getServerDirectory().toPath();
        Diag.surLeFilDesRapports(() -> {
            synchronized (r) {
                r.fermer();
            }
            boolean traduits = NomsSrg.preparer(racine);
            e.noms = traduits ? "traduits (" + NomsSrg.origine() + ")"
                    : "noms SRG (m_12345_), " + NomsSrg.origine() + " ; pour les lire : python outils/diag_noms.py diag/" + nom
                    + " (le workflow « Lenteurs du serveur » le fait seul)";
            RapportProfil rp = RapportProfil.ecrire(e);
            Diag.ecrireMaintenant(racine, nom, rp.texte);
            String resume = NomsSrg.traduire(rp.resume);
            srv.execute(() -> dire(srv, qui, cons, Component.translatableWithFallback("noyau.diag.profil_fin", "[Diag] Profil terminé : %s — rapport diag/%s",
                    resume, nom).withStyle(s -> s.withColor(0x8FD3FF))));
        });
        return nom;
    }

    /** Arrêt du serveur pendant un profil : tout est retiré, pas de rapport. */
    static void arretUrgent() {
        actif = false;
        releves = null;
        Systemes.arretUrgent();
    }

    private static void dire(MinecraftServer srv, UUID qui, boolean cons, Component c) {
        if (cons || qui == null) {
            srv.sendSystemMessage(c);
            return;
        }
        ServerPlayer p = srv.getPlayerList().getPlayer(qui);
        if (p != null) p.sendSystemMessage(c);
        else srv.sendSystemMessage(c);
    }

    private static String texteCommandes(double secondes) {
        if (COMMANDES.isEmpty()) return "";
        StringBuilder b = new StringBuilder("-- Commandes jouées pendant le profil (mots de la commande seulement ; durée sur le fil serveur) --\n");
        b.append(String.format(Locale.ROOT, "  %7s %11s %10s %10s  %s%n", "fois", "ms moyenne", "ms pire", "ms total", "commande"));
        COMMANDES.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1])).limit(30).forEach(x -> {
            long[] a = x.getValue();
            b.append(String.format(Locale.ROOT, "  %7d %11.2f %10.1f %10.1f  %s%n", a[0], a[1] / 1e6 / Math.max(1, a[0]), a[2] / 1e6, a[1] / 1e6, x.getKey()));
        });
        COMMANDES.clear();
        return b.toString();
    }

    private static long cpuFil() {
        try {
            ThreadMXBean t = ManagementFactory.getThreadMXBean();
            Thread f = Diag.filServeur();
            if (f == null || !t.isThreadCpuTimeSupported() || !t.isThreadCpuTimeEnabled()) return -1;
            return t.getThreadCpuTime(f.getId());
        } catch (RuntimeException | LinkageError e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ mixins
    private static final Map<String, Map<String, String>> MIXINS = new ConcurrentHashMap<>();

    /**
     * Classe du mixin qui a ajouté {@code methode} à {@code classe} : Mixin pose sur chaque méthode fusionnée une annotation
     * {@code @MixinMerged(mixin = "...")} gardée à l'exécution. Lu une fois par classe, sur le fil des rapports ; null si
     * inconnu.
     */
    static String proprietaireMixin(String classe, String methode) {
        return MIXINS.computeIfAbsent(classe, Profileur::lireMixins).get(methode);
    }

    private static Map<String, String> lireMixins(String classe) {
        Map<String, String> r = new HashMap<>();
        try {
            Class<?> c = Class.forName(classe, false, MinecraftServer.class.getClassLoader());
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().indexOf('$') < 0) continue;
                for (Annotation a : m.getDeclaredAnnotations()) {
                    if (!a.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")) continue;
                    Object v = a.annotationType().getMethod("mixin").invoke(a);
                    if (v instanceof String s) r.put(m.getName(), s);
                }
            }
        } catch (Throwable t) {
            // classe illisible depuis ici : mixin sans propriétaire connu
        }
        return r;
    }

    /** Pour /diag etat. */
    static long secondesFaites() {
        return Math.max(0, (System.currentTimeMillis() - debutMs) / 1000);
    }

    static int secondesDemandees() {
        return dureeDemandee;
    }
}
