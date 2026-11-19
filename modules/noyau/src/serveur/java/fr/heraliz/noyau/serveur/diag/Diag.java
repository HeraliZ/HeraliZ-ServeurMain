package fr.heraliz.noyau.serveur.diag;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.diag.Profil;
import fr.heraliz.noyau.serveur.diag.profil.NomsSrg;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Diagnostic du serveur façon spark (choix du propriétaire, 24/10 : « il dit quel mod et quelle ligne de code font
 * ramer, en direct, avec un rapport automatique à chaque blocage »). Serveur seulement.
 *
 * <ul>
 *     <li><b>Blocages (toujours actif, {@code [diag] auto})</b> : la durée de chaque tick est mesurée (deux lectures
 *     d'horloge, rien d'autre). Un fil « veilleur » regarde toutes les 20 ms si le tick en cours dure plus que le seuil ;
 *     seulement alors, il relève la pile du fil serveur toutes les 5 ms jusqu'à la fin du tick. Rapport écrit dans
 *     {@code diag/} à la racine du serveur (mod, catégorie, lignes, piles, GC, mémoire, entités, dernières commandes),
 *     opérateurs prévenus.</li>
 *     <li><b>Blocages entre deux ticks (01/11)</b> : les commandes et les paquets des joueurs ne passent pas DANS le tick,
 *     mais juste après, dans le temps laissé libre jusqu'au tick suivant : une commande qui gelait le serveur 2 s n'était
 *     vue ni ici, ni par le temps par tick de spark. Le veilleur regarde aussi ce temps-là : plus de 50 ms + le seuil
 *     après la fin d'un tick sans que le suivant ait commencé, il relève la pile de la même façon, et le rapport part au
 *     début du tick suivant (même dossier, même alerte, « entre deux ticks »).</li>
 *     <li><b>Profil ({@code /diag profil})</b> : relevé de la pile toutes les 4 à 10 ms, pendant
 *     ET entre les ticks, rapport façon spark (mod, fichier:ligne, arbre des appels, temps par système) : voir
 *     {@link Profileur}.</li>
 * </ul>
 * Le relevé d'une pile d'un autre fil arrête brièvement ce fil : le veilleur ne le fait que pendant un blocage ou un profil
 * demandé. Les noms SRG de Minecraft y sont traduits quand la table est trouvée ({@link NomsSrg}).
 * <p>19/11 (audit des lenteurs, point C15 ; C7, F5) :
 * <ul>
 *     <li>relevés dès {@code [diag] releves_des_ms} (50 ms) : le rapport d'un tick de 114 ms a une douzaine de relevés depuis
 *     le début du blocage, au lieu de 2 ou 3 pris à sa fin ; seul un tick plus long que le seuil fait un rapport ;</li>
 *     <li>pendant les {@code [diag] calme_demarrage_s} (3 min) qui suivent « Done » : pas d'alerte (ni message aux
 *     opérateurs, ni ligne « blocage ») ; journal « [diag] démarrage » et fichier {@code demarrage-<date>.txt} ;</li>
 *     <li>un blocage de 300 ms et plus a toujours son fichier, même dans les 10 s d'un autre ;</li>
 *     <li>fichiers gardés par sorte : 60 rapports de blocage (les 20 plus récents, puis les plus longs : un gros blocage
 *     n'est plus effacé par une série de petits), 20 de chaque autre sorte (profil, charge, fuite, démarrage…) ;</li>
 *     <li>chaque rapport et {@code /diag etat} disent la vraie machine Java : version, arguments réels, ramasse-miettes,
 *     tas, cœurs vus.</li>
 * </ul>
 */
public final class Diag {
    private Diag() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int TICKS_GARDES = 1200;
    /** Rapports de blocage gardés, dont les plus récents toujours ; rapports gardés de chaque autre sorte. */
    private static final int GARDES_BLOCAGES = 60, GARDES_RECENTS = 20, GARDES_AUTRES = 20;
    /** Un blocage au moins aussi long a toujours son fichier (ms). */
    private static final double TOUJOURS_ECRIT_MS = 300;
    /** Démarrage du serveur (System.nanoTime de « Done »), 0 avant. */
    private static volatile long demarrage;

    private static volatile MinecraftServer serveur;
    private static volatile Thread filServeur;
    private static volatile long debutTick;
    private static volatile boolean dansTick;
    private static volatile long gcDebutTick;
    /** Fin du dernier tick (System.nanoTime), 0 avant le premier ; temps du GC à ce moment. */
    private static volatile long finTick;
    private static volatile long gcFinTick;
    /** Après la fin d'un tick, Minecraft peut faire ses tâches 50 ms (commandes, paquets) sans retarder le jeu. */
    private static final long LIBRE_APRES_TICK = 50_000_000L;
    private static Thread veilleur;
    private static final ExecutorService ECRIVAIN = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeraliZ diag (rapports)");
        t.setDaemon(true);
        return t;
    });

    /** Durées des derniers ticks (ms), fil serveur seulement. */
    private static final double[] TICKS = new double[TICKS_GARDES];
    private static int ticksNb, ticksPos;

    // blocage en cours (rempli par le veilleur, lu par le fil serveur à la fin du tick)
    private static final Object VERROU = new Object();
    private static Profil blocage;
    /** blocage entre deux ticks en cours (rempli par le veilleur, lu au début du tick suivant) */
    private static Profil blocageEntre;
    private static long dernierRapportBlocage, derniereAnnonce;
    /** Nom (sans .txt) du dernier rapport de blocage, et rapports déjà écrits dans cette seconde (fil serveur). */
    private static String dernierNom;
    private static int memeSeconde;

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> demarrer(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> arreter());
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START) debut(e.getServer());
        });
        DernieresCommandes.enregistrer();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) fin(e.getServer());
        });
    }

    private static void demarrer(MinecraftServer srv) {
        serveur = srv;
        filServeur = Thread.currentThread();
        demarrage = System.nanoTime();
        LOG.info("[diag] {}", jvmResume());
        veilleur = new Thread(Diag::veiller, "HeraliZ diag (veilleur)");
        veilleur.setDaemon(true);
        veilleur.setPriority(Thread.MAX_PRIORITY);
        veilleur.start();
    }

    private static void arreter() {
        Thread v = veilleur;
        veilleur = null;
        if (v != null) v.interrupt();
        Profileur.arretUrgent();
        synchronized (VERROU) {
            blocage = null;
            blocageEntre = null;
        }
        serveur = null;
        finTick = 0;
    }

    private static void debut(MinecraftServer srv) {
        if (filServeur == null) filServeur = Thread.currentThread();
        long maintenant = System.nanoTime(), gc = tempsGc();
        long fin = finTick;
        Profil entre;
        synchronized (VERROU) {
            entre = blocageEntre;
            blocageEntre = null;
        }
        gcDebutTick = gc;
        debutTick = maintenant;
        Profileur.debutTick();
        DernieresCommandes.nouveauTick();
        dansTick = true;
        // travail fait depuis la fin du tick précédent, au-delà du temps libre normal (au moins autant) ; relevé dès
        // releves_des_ms (19/11), rapport seulement au-delà du seuil
        if (entre != null && entre.releves > 0 && fin != 0) {
            double ms = (maintenant - fin - LIBRE_APRES_TICK) / 1_000_000.0;
            if (ms > NoyauConfig.DIAG_SEUIL_MS.get()) rapportBlocage(srv, entre, ms, gc - gcFinTick, true, fin);
        }
    }

    private static void fin(MinecraftServer srv) {
        long maintenant = System.nanoTime(), gc = tempsGc();
        // fin du tick notée AVANT dansTick = false : le veilleur ne prend jamais la fin d'un long tick (rapport compris)
        // pour du travail « entre deux ticks »
        finTick = maintenant;
        gcFinTick = gc;
        dansTick = false;
        double ms = (maintenant - debutTick) / 1_000_000.0;
        TICKS[ticksPos] = ms;
        ticksPos = (ticksPos + 1) % TICKS_GARDES;
        if (ticksNb < TICKS_GARDES) ticksNb++;

        Profil b;
        synchronized (VERROU) {
            b = blocage;
            blocage = null;
        }
        // relevé dès releves_des_ms (19/11) : rapport seulement au-delà du seuil, sinon les relevés sont jetés
        if (b != null && b.releves > 0 && ms > NoyauConfig.DIAG_SEUIL_MS.get()) rapportBlocage(srv, b, ms, gc - gcDebutTick, false, debutTick);
        Profileur.finTick(srv, ms);
    }

    // ------------------------------------------------------------------ fil veilleur
    private static void veiller() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread t = filServeur;
                boolean profil = Profileur.actif;
                // 19/11 (audit, point C15) : relevés dès releves_des_ms (50 ms), pas seulement après le seuil ; le rapport,
                // lui, n'est écrit que pour un tick plus long que le seuil (fin du tick)
                long seuil = Math.min(NoyauConfig.DIAG_SEUIL_MS.get(), NoyauConfig.diagRelevesDesMs) * 1_000_000L;
                boolean auto = NoyauConfig.DIAG_AUTO.get(), dans = dansTick;
                long maintenant = System.nanoTime(), fin = finTick, debut = debutTick;
                boolean bloque = t != null && dans && auto && maintenant - debut > seuil;
                // entre deux ticks : commandes, paquets des joueurs, tâches (01/11)
                boolean bloqueEntre = t != null && !dans && auto && fin != 0 && maintenant - fin > LIBRE_APRES_TICK + seuil;
                if (t != null && (bloque || bloqueEntre || profil)) {
                    int tick = Profileur.tickNo;
                    long avant = System.nanoTime();
                    StackTraceElement[] pile = t.getStackTrace();
                    long cout = System.nanoTime() - avant;
                    // profil : pendant ET entre les ticks ; un relevé à cheval sur le début ou la fin d'un tick est écarté
                    if (profil) Profileur.ranger(pile, dans, tick, dans == dansTick && tick == Profileur.tickNo, cout);
                    synchronized (VERROU) {
                        // 01/11 : pile relevée pendant le MÊME tick (sinon un tick fini entre-temps laissait un relevé au
                        // tick suivant : faux rapports « blocage de 2 ms » des 29 et 31/10)
                        if (bloque && dansTick && debutTick == debut) {
                            if (blocage == null) blocage = new Profil();
                            if (blocage.releves < 4000) blocage.ajouter(pile);
                        }
                        // pile relevée pendant le même temps entre deux ticks (pas déjà dans le tick suivant)
                        if (bloqueEntre && !dansTick && finTick == fin) {
                            if (blocageEntre == null) blocageEntre = new Profil();
                            if (blocageEntre.releves < 4000) blocageEntre.ajouter(pile);
                        }
                    }
                    Thread.sleep(profil ? Profileur.intervalleMs : 5);
                } else {
                    Thread.sleep(10);   // 19/11 : 10 ms (avant 20) : le 1er relevé tombe au plus 10 ms après releves_des_ms
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                LOG.warn("[diag] veilleur : {}", t.toString());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ blocages
    /** Vrai pendant le calme du démarrage ({@code [diag] calme_demarrage_s} après « Done ») : pas d'alerte. */
    public static boolean auDemarrage() {
        long d = demarrage;
        return d != 0 && System.nanoTime() - d < NoyauConfig.diagCalmeDemarrageS * 1_000_000_000L;
    }

    /**
     * {@code entreTicks} : travail fait après la fin d'un tick (commandes, paquets), {@code ms} au-delà du temps libre
     * normal. {@code debutNanos} : début du tick, ou fin du tick précédent (la commande jouée jusqu'à 2 s avant est nommée).
     */
    private static void rapportBlocage(MinecraftServer srv, Profil b, double ms, long gcMs, boolean entreTicks, long debutNanos) {
        long maintenant = System.currentTimeMillis();
        String resume = b.resume();
        String commande = DernieresCommandes.depuis(debutNanos - 2_000_000_000L);
        boolean auDemarrage = auDemarrage();
        String sauvegarde = entreTicks ? null : fr.heraliz.noyau.serveur.perf.SauvegardesEtalees.dansCeTick(srv.getTickCount());
        if (auDemarrage) {
            // 19/11 (audit, point C7) : premières lectures après le démarrage, personne n'est encore là : noté, pas une alerte
            LOG.info("[diag] démarrage : tick de {} ms{} : {}{}", Math.round(ms), entreTicks ? " entre deux ticks" : "", resume,
                    commande == null ? "" : " — dernière commande : " + commande);
        } else {
            LOG.warn("[diag] blocage de {} ms{} : {}{}{}", Math.round(ms), entreTicks ? " entre deux ticks" : "", resume,
                    commande == null ? "" : " — dernière commande : " + commande, sauvegarde == null ? "" : " — " + sauvegarde);
        }
        // au plus un fichier toutes les 10 s, sauf un blocage de 300 ms et plus (19/11 : 2 463 ms perdu à 23:07:29 le 18/11)
        if (maintenant - dernierRapportBlocage < 10_000 && ms < TOUJOURS_ECRIT_MS) return;
        dernierRapportBlocage = maintenant;
        StringBuilder t = new StringBuilder();
        t.append("HeraliZ — rapport de blocage").append(entreTicks ? " entre deux ticks (commandes, paquets des joueurs, tâches)" : "")
                .append(auDemarrage ? " — pendant le démarrage (pas d'alerte)" : "").append('\n');
        t.append("Date : ").append(LocalDateTime.now()).append('\n');
        if (sauvegarde != null) t.append(sauvegarde).append('\n');
        t.append(String.format(Locale.ROOT, "Relevés de la pile dès %d ms du tick (seuil du rapport %d ms)%n",
                Math.min(NoyauConfig.DIAG_SEUIL_MS.get(), NoyauConfig.diagRelevesDesMs), NoyauConfig.DIAG_SEUIL_MS.get()));
        if (entreTicks) {
            t.append(String.format(Locale.ROOT, "Travail entre deux ticks : %.0f ms au moins, au-delà des 50 ms laissés après chaque tick (seuil %d ms)%n",
                    ms, NoyauConfig.DIAG_SEUIL_MS.get()));
        } else {
            t.append(String.format(Locale.ROOT, "Durée du tick : %.0f ms (seuil %d ms)%n", ms, NoyauConfig.DIAG_SEUIL_MS.get()));
        }
        t.append("Ramasse-miettes (GC) pendant ce temps : ").append(gcMs).append(" ms").append(gcMs > ms / 2 ? "  <- la mémoire est en cause" : "").append('\n');
        etatServeur(srv, t::append);
        t.append('\n');
        DernieresCommandes.ecrire(t, System.nanoTime());
        b.ecrire(t);
        String nom = (auDemarrage ? "demarrage-" : "blocage-") + LocalDateTime.now().format(NOM);
        if (nom.equals(dernierNom)) {
            nom = nom + "-" + (++memeSeconde);   // deux rapports dans la même seconde (300 ms et plus) : les deux gardés
        } else {
            dernierNom = nom;
            memeSeconde = 1;
        }
        nom = nom + ".txt";
        ecrire(srv, nom, t.toString());
        if (!auDemarrage && NoyauConfig.DIAG_ANNONCER.get() && maintenant - derniereAnnonce >= 30_000) {
            derniereAnnonce = maintenant;
            Component c = (entreTicks
                    ? Component.translatableWithFallback("noyau.diag.blocage_entre", "[Diag] Blocage de %s ms entre deux ticks (%s) — rapport diag/%s",
                    String.valueOf(Math.round(ms)), resume, nom)
                    : Component.translatableWithFallback("noyau.diag.blocage", "[Diag] Blocage de %s ms (%s) — rapport diag/%s",
                    String.valueOf(Math.round(ms)), resume, nom)).withStyle(s -> s.withColor(0xFFB060));
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) if (p.hasPermissions(4)) p.sendSystemMessage(c);
        }
    }

    // ------------------------------------------------------------------ profil (voir Profileur)
    /** Profil de {@code secondes} s, relevé toutes les 5 ms (banc de charge) ; faux si un profil tourne déjà. */
    public static boolean lancer(int secondes, UUID qui) {
        MinecraftServer srv = serveur;
        return srv != null && Profileur.lancer(srv, secondes, 5, qui);
    }

    /** Arrête le profil ; nom du rapport (écrit juste après, sur le fil des rapports), ou null s'il n'y en avait pas. */
    public static String arreterSession(MinecraftServer srv) {
        return Profileur.arreter(srv);
    }

    static Thread filServeur() {
        return filServeur;
    }

    // ------------------------------------------------------------------ état du serveur
    public static String tps() {
        double moy = msMoyen();
        return String.format(Locale.ROOT, "%.1f", moy <= 0 ? 20.0 : Math.min(20.0, 1000.0 / Math.max(50.0, moy)));
    }

    public static double msMoyen() {
        if (ticksNb == 0) return 0;
        double s = 0;
        for (int i = 0; i < ticksNb; i++) s += TICKS[i];
        return s / ticksNb;
    }

    public static double msMax() {
        double m = 0;
        for (int i = 0; i < ticksNb; i++) m = Math.max(m, TICKS[i]);
        return m;
    }

    /** Durée de tick (ms) sous laquelle sont {@code p} % des derniers ticks (50 = médiane, 95, 99). */
    public static double msCentile(double p) {
        if (ticksNb == 0) return 0;
        double[] c = java.util.Arrays.copyOf(TICKS, ticksNb);
        java.util.Arrays.sort(c);
        return c[Math.min(ticksNb - 1, (int) Math.ceil(p / 100.0 * ticksNb) - 1 < 0 ? 0 : (int) Math.ceil(p / 100.0 * ticksNb) - 1)];
    }

    static int ticksMesures() {
        return ticksNb;
    }

    /** Mémoire, mondes, entités les plus nombreuses (fil serveur). */
    public static void etatServeur(MinecraftServer srv, Consumer<String> ligne) {
        Runtime r = Runtime.getRuntime();
        long utilisee = (r.totalMemory() - r.freeMemory()) >> 20;
        ligne.accept(String.format(Locale.ROOT, "Mémoire : %d / %d Mo%n", utilisee, r.maxMemory() >> 20));
        jvm(ligne);
        ligne.accept(String.format(Locale.ROOT, "Ticks (derniers %d) : %.1f ms en moyenne, médiane %.1f, 95 %% %.1f, 99 %% %.1f, max %.0f ms, %s TPS%n",
                ticksNb, msMoyen(), msCentile(50), msCentile(95), msCentile(99), msMax(), tps()));
        ligne.accept("  objectifs : moyenne <= 30 ms, 95 % <= 40 ms, 99 % < 50 ms\n");
        ligne.accept("Joueurs connectés : " + srv.getPlayerCount() + "\n");
        Map<EntityType<?>, Integer> types = new HashMap<>();
        for (ServerLevel lvl : srv.getAllLevels()) {
            int n = 0;
            for (Entity e : lvl.getAllEntities()) {
                if (e == null) continue;
                n++;
                types.merge(e.getType(), 1, Integer::sum);
            }
            ligne.accept(String.format(Locale.ROOT, "  %s : %d entités, %d tronçons chargés, %d joueurs%n", lvl.dimension().location(), n,
                    lvl.getChunkSource().getLoadedChunksCount(), lvl.players().size()));
        }
        List<Map.Entry<EntityType<?>, Integer>> l = new ArrayList<>(types.entrySet());
        l.sort(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed());
        StringBuilder b = new StringBuilder("Entités les plus nombreuses : ");
        for (int i = 0; i < Math.min(10, l.size()); i++) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(l.get(i).getKey());
            b.append(i > 0 ? ", " : "").append(id == null ? "?" : id.toString()).append(' ').append(l.get(i).getValue());
        }
        ligne.accept(b.append('\n').toString());
        fr.heraliz.noyau.serveur.perf.Allege.etat(ligne);
    }

    static long tempsGc() {
        long t = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) t += Math.max(0, gc.getCollectionTime());
        return t;
    }

    // ------------------------------------------------------------------ machine Java (19/11, audit des réglages F5)
    /** Lignes « machine Java », faites une fois (elles ne changent pas pendant que le serveur tourne). */
    private static volatile String jvm;
    /** Noms d'arguments dont la valeur n'est jamais écrite (mot de passe, clé...). */
    private static final java.util.regex.Pattern SECRET = java.util.regex.Pattern.compile("(?i)(pass|secret|token|key|cle|credential|auth)");

    /**
     * La machine Java qui fait VRAIMENT tourner le serveur (F5 : Mohist peut se relancer sans les réglages du panel ; rien ne
     * montrait la ligne de lancement) : version, ramasse-miettes, tas (maximum et réservé au départ), cœurs vus, mémoire de
     * la machine (ou du conteneur), arguments réels de Java ({@code RuntimeMXBean.getInputArguments}). La valeur d'un
     * argument {@code -Dnom=valeur} dont le nom ressemble à un secret est masquée.
     */
    public static void jvm(Consumer<String> ligne) {
        String j = jvm;
        if (j == null) jvm = j = jvmTexte();
        ligne.accept(j);
    }

    /** Une ligne courte pour le journal du démarrage. */
    private static String jvmResume() {
        String j = jvm;
        if (j == null) jvm = j = jvmTexte();
        int fin = j.indexOf('\n');
        return fin < 0 ? j : j.substring(0, fin);
    }

    private static String jvmTexte() {
        StringBuilder s = new StringBuilder("Java : ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vm.name")).append(")");
        s.append(", ramasse-miettes :");
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) s.append(' ').append(g.getName()).append(',');
        if (s.charAt(s.length() - 1) == ',') s.setLength(s.length() - 1);
        Runtime r = Runtime.getRuntime();
        long init = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getInit();
        s.append(String.format(Locale.ROOT, " ; tas maximum %d Mo, réservé au départ %d Mo ; %d cœurs vus", r.maxMemory() >> 20,
                init < 0 ? -1 : init >> 20, r.availableProcessors()));
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                s.append(String.format(Locale.ROOT, " ; mémoire de la machine %.1f Go", os.getTotalMemorySize() / 1073741824.0));
            }
        } catch (Throwable ignore) {
            // machine Java sans ces mesures : rien de plus
        }
        s.append('\n');
        StringBuilder a = new StringBuilder("Arguments de Java :");
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            int egal = arg.indexOf('=');
            if (arg.startsWith("-D") && egal > 0 && SECRET.matcher(arg.substring(2, egal)).find()) arg = arg.substring(0, egal + 1) + "***";
            a.append(' ').append(arg);
        }
        if (System.getenv("JDK_JAVA_OPTIONS") != null) a.append(" (JDK_JAVA_OPTIONS donné aussi)");
        return s.append(a).append('\n').toString();
    }

    // ------------------------------------------------------------------ fichiers
    public static Path dossier(MinecraftServer srv) {
        return srv.getServerDirectory().toPath().resolve("diag");
    }

    public static void ecrire(MinecraftServer srv, String nom, String texte) {
        Path racine = srv.getServerDirectory().toPath();
        ECRIVAIN.execute(() -> ecrireMaintenant(racine, nom, texte));
    }

    /** Travail lourd d'un rapport (analyse d'un profil…), sur le fil des rapports, jamais sur le fil serveur. */
    static void surLeFilDesRapports(Runnable r) {
        ECRIVAIN.execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                LOG.warn("[diag] rapport non fait : {}", t.toString(), t);
            }
        });
    }

    /**
     * Écrit un rapport dans {@code diag/} (fil des rapports seulement) : noms SRG de Minecraft traduits si la table est
     * trouvée sur le serveur, puis la rotation par sorte ({@link #ranger}).
     */
    static void ecrireMaintenant(Path racine, String nom, String texte) {
        Path d = racine.resolve("diag");
        try {
            String t = NomsSrg.preparer(racine) ? NomsSrg.traduire(texte) : texte;
            Files.createDirectories(d);
            Files.writeString(d.resolve(nom), t, StandardCharsets.UTF_8);
            ranger(d);
        } catch (Exception e) {
            LOG.warn("[diag] rapport {} non écrit : {}", nom, e.toString());
        }
    }

    /** Durée lue en tête de chaque rapport de blocage (nom du fichier → ms), pour ne pas relire les mêmes fichiers. */
    private static final Map<String, Double> DUREES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.regex.Pattern DUREE = java.util.regex.Pattern.compile("(?:Durée du tick|Travail entre deux ticks) : (\\d+) ms");

    /**
     * Rotation par sorte (19/11, audit des lenteurs, point C15 : 22 vieux rapports de charge, de fuite et de profil
     * prenaient des places, et les blocages de 2 463 et 1 563 ms du 18/11 avaient été effacés) : la sorte est le début du
     * nom (« blocage », « profil », « charge », « fuite », « demarrage »…). Blocages : {@value #GARDES_BLOCAGES} gardés, les
     * {@value #GARDES_RECENTS} plus récents toujours, puis les plus longs (durée lue en tête du fichier). Autres sortes :
     * les {@value #GARDES_AUTRES} plus récents de chacune.
     */
    private static void ranger(Path d) throws java.io.IOException {
        Map<String, List<Path>> parSorte = new HashMap<>();
        try (Stream<Path> s = Files.list(d)) {
            s.filter(f -> f.getFileName().toString().endsWith(".txt")).forEach(f -> {
                String n = f.getFileName().toString();
                int tiret = n.indexOf('-');
                parSorte.computeIfAbsent(tiret > 0 ? n.substring(0, tiret) : n, k -> new ArrayList<>()).add(f);
            });
        }
        for (Map.Entry<String, List<Path>> e : parSorte.entrySet()) {
            List<Path> l = e.getValue();
            l.sort(Comparator.comparing(f -> f.getFileName().toString()));   // la date est dans le nom : du plus ancien au plus récent
            boolean blocages = e.getKey().equals("blocage");
            int gardes = blocages ? GARDES_BLOCAGES : GARDES_AUTRES;
            if (l.size() <= gardes) continue;
            if (!blocages) {
                for (int i = 0; i < l.size() - gardes; i++) Files.deleteIfExists(l.get(i));
                continue;
            }
            // les plus récents gardés ; parmi les plus anciens, les plus courts partent d'abord
            List<Path> anciens = new ArrayList<>(l.subList(0, l.size() - GARDES_RECENTS));
            anciens.sort(Comparator.comparingDouble(Diag::duree));
            int enTrop = l.size() - gardes;
            for (int i = 0; i < enTrop && i < anciens.size(); i++) {
                Files.deleteIfExists(anciens.get(i));
                DUREES.remove(anciens.get(i).getFileName().toString());
            }
        }
    }

    /** Durée d'un rapport de blocage (ms), lue dans ses premières lignes (0 si illisible). */
    private static double duree(Path f) {
        return DUREES.computeIfAbsent(f.getFileName().toString(), n -> {
            try (java.io.BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                for (int i = 0; i < 8; i++) {
                    String l = r.readLine();
                    if (l == null) break;
                    java.util.regex.Matcher m = DUREE.matcher(l);
                    if (m.find()) return Double.parseDouble(m.group(1));
                }
            } catch (Exception ignore) {
                // illisible : compté comme le plus court
            }
            return 0.0;
        });
    }
}
