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
 * demandé. Fichiers gardés : les 60 derniers. Les noms SRG de Minecraft y sont traduits quand la table est trouvée
 * ({@link NomsSrg}).
 */
public final class Diag {
    private Diag() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int GARDES = 60, TICKS_GARDES = 1200;

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
        // travail fait depuis la fin du tick précédent, au-delà du temps libre normal (au moins autant)
        if (entre != null && entre.releves > 0 && fin != 0) {
            rapportBlocage(srv, entre, (maintenant - fin - LIBRE_APRES_TICK) / 1_000_000.0, gc - gcFinTick, true, fin);
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
        if (b != null && b.releves > 0) rapportBlocage(srv, b, ms, gc - gcDebutTick, false, debutTick);
        Profileur.finTick(srv, ms);
    }

    // ------------------------------------------------------------------ fil veilleur
    private static void veiller() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread t = filServeur;
                boolean profil = Profileur.actif;
                long seuil = NoyauConfig.DIAG_SEUIL_MS.get() * 1_000_000L;
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
                    Thread.sleep(20);
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
    /**
     * {@code entreTicks} : travail fait après la fin d'un tick (commandes, paquets), {@code ms} au-delà du temps libre
     * normal. {@code debutNanos} : début du tick, ou fin du tick précédent (la commande jouée jusqu'à 2 s avant est nommée).
     */
    private static void rapportBlocage(MinecraftServer srv, Profil b, double ms, long gcMs, boolean entreTicks, long debutNanos) {
        long maintenant = System.currentTimeMillis();
        String resume = b.resume();
        String commande = DernieresCommandes.depuis(debutNanos - 2_000_000_000L);
        LOG.warn("[diag] blocage de {} ms{} : {}{}", Math.round(ms), entreTicks ? " entre deux ticks" : "", resume,
                commande == null ? "" : " — dernière commande : " + commande);
        if (maintenant - dernierRapportBlocage < 10_000) return;   // au plus un fichier toutes les 10 s
        dernierRapportBlocage = maintenant;
        StringBuilder t = new StringBuilder();
        t.append("HeraliZ — rapport de blocage").append(entreTicks ? " entre deux ticks (commandes, paquets des joueurs, tâches)" : "").append('\n');
        t.append("Date : ").append(LocalDateTime.now()).append('\n');
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
        String nom = "blocage-" + LocalDateTime.now().format(NOM) + ".txt";
        ecrire(srv, nom, t.toString());
        if (NoyauConfig.DIAG_ANNONCER.get() && maintenant - derniereAnnonce >= 30_000) {
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
     * trouvée sur le serveur, puis seuls les 60 derniers rapports sont gardés.
     */
    static void ecrireMaintenant(Path racine, String nom, String texte) {
        Path d = racine.resolve("diag");
        try {
            String t = NomsSrg.preparer(racine) ? NomsSrg.traduire(texte) : texte;
            Files.createDirectories(d);
            Files.writeString(d.resolve(nom), t, StandardCharsets.UTF_8);
            List<Path> tous;
            try (Stream<Path> s = Files.list(d)) {
                tous = s.filter(f -> f.getFileName().toString().endsWith(".txt"))
                        .sorted(Comparator.comparing(f -> f.getFileName().toString())).toList();
            }
            for (int i = 0; i < tous.size() - GARDES; i++) Files.deleteIfExists(tous.get(i));
        } catch (Exception e) {
            LOG.warn("[diag] rapport {} non écrit : {}", nom, e.toString());
        }
    }
}
