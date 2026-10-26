package fr.heraliz.noyau.serveur.diag;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.diag.Profil;
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
 *     {@code diag/} à la racine du serveur (mod, catégorie, lignes, piles, GC, mémoire, entités), opérateurs prévenus.</li>
 *     <li><b>Enregistrement ({@code /diag start})</b> : relevé de la pile toutes les 10 ms pendant les ticks, résumé dans le
 *     chat toutes les 10 s à celui qui l'a lancé, rapport complet à la fin.</li>
 * </ul>
 * Le relevé d'une pile d'un autre fil arrête brièvement la machine Java : le veilleur ne le fait que pendant un blocage
 * ou un enregistrement demandé. Fichiers gardés : les 60 derniers.
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
    private static long dernierRapportBlocage, derniereAnnonce;

    // enregistrement demandé
    private static Profil session;
    private static long finSession, prochainResume;
    private static UUID demandeur;
    private static double sessionMsTotal, sessionMsMax;
    private static int sessionTicks;

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> demarrer(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> arreter());
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START) debut();
        });
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
        synchronized (VERROU) {
            session = null;
            blocage = null;
        }
        serveur = null;
    }

    private static void debut() {
        if (filServeur == null) filServeur = Thread.currentThread();
        gcDebutTick = tempsGc();
        debutTick = System.nanoTime();
        dansTick = true;
    }

    private static void fin(MinecraftServer srv) {
        dansTick = false;
        double ms = (System.nanoTime() - debutTick) / 1_000_000.0;
        TICKS[ticksPos] = ms;
        ticksPos = (ticksPos + 1) % TICKS_GARDES;
        if (ticksNb < TICKS_GARDES) ticksNb++;

        Profil b;
        synchronized (VERROU) {
            b = blocage;
            blocage = null;
            if (session != null) {
                sessionTicks++;
                sessionMsTotal += ms;
                sessionMsMax = Math.max(sessionMsMax, ms);
            }
        }
        if (b != null && b.releves > 0) rapportBlocage(srv, b, ms, tempsGc() - gcDebutTick);
        suivreSession(srv);
    }

    // ------------------------------------------------------------------ fil veilleur
    private static void veiller() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread t = filServeur;
                boolean enSession;
                synchronized (VERROU) {
                    enSession = session != null;
                }
                long seuil = NoyauConfig.DIAG_SEUIL_MS.get() * 1_000_000L;
                boolean bloque = t != null && dansTick && NoyauConfig.DIAG_AUTO.get() && System.nanoTime() - debutTick > seuil;
                if (t != null && dansTick && (bloque || enSession)) {
                    StackTraceElement[] pile = t.getStackTrace();
                    synchronized (VERROU) {
                        if (bloque) {
                            if (blocage == null) blocage = new Profil();
                            if (blocage.releves < 4000) blocage.ajouter(pile);
                        }
                        if (session != null) session.ajouter(pile);
                    }
                    Thread.sleep(bloque ? 5 : 10);
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
    private static void rapportBlocage(MinecraftServer srv, Profil b, double ms, long gcMs) {
        long maintenant = System.currentTimeMillis();
        String resume = b.resume();
        LOG.warn("[diag] blocage de {} ms : {}", Math.round(ms), resume);
        if (maintenant - dernierRapportBlocage < 10_000) return;   // au plus un fichier toutes les 10 s
        dernierRapportBlocage = maintenant;
        StringBuilder t = new StringBuilder();
        t.append("HeraliZ — rapport de blocage\n");
        t.append("Date : ").append(LocalDateTime.now()).append('\n');
        t.append(String.format(Locale.ROOT, "Durée du tick : %.0f ms (seuil %d ms)%n", ms, NoyauConfig.DIAG_SEUIL_MS.get()));
        t.append("Ramasse-miettes (GC) pendant le tick : ").append(gcMs).append(" ms").append(gcMs > ms / 2 ? "  <- la mémoire est en cause" : "").append('\n');
        etatServeur(srv, t::append);
        t.append('\n');
        b.ecrire(t);
        String nom = "blocage-" + LocalDateTime.now().format(NOM) + ".txt";
        ecrire(srv, nom, t.toString());
        if (NoyauConfig.DIAG_ANNONCER.get() && maintenant - derniereAnnonce >= 30_000) {
            derniereAnnonce = maintenant;
            Component c = Component.translatableWithFallback("noyau.diag.blocage", "[Diag] Blocage de %s ms (%s) — rapport diag/%s",
                    String.valueOf(Math.round(ms)), resume, nom).withStyle(s -> s.withColor(0xFFB060));
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) if (p.hasPermissions(4)) p.sendSystemMessage(c);
        }
    }

    // ------------------------------------------------------------------ enregistrement demandé
    public static boolean lancer(int secondes, UUID qui) {
        synchronized (VERROU) {
            if (session != null) return false;
            session = new Profil();
            finSession = System.currentTimeMillis() + secondes * 1000L;
            prochainResume = System.currentTimeMillis() + 10_000;
            demandeur = qui;
            sessionMsTotal = 0;
            sessionMsMax = 0;
            sessionTicks = 0;
        }
        return true;
    }

    /** Arrête l'enregistrement et écrit le rapport ; nom du fichier, ou null s'il n'y en avait pas. */
    public static String arreterSession(MinecraftServer srv) {
        Profil p;
        double moy, max;
        int ticks;
        synchronized (VERROU) {
            p = session;
            session = null;
            moy = sessionTicks == 0 ? 0 : sessionMsTotal / sessionTicks;
            max = sessionMsMax;
            ticks = sessionTicks;
        }
        if (p == null) return null;
        StringBuilder t = new StringBuilder();
        t.append("HeraliZ — enregistrement /diag\n");
        t.append("Date : ").append(LocalDateTime.now()).append('\n');
        t.append(String.format(Locale.ROOT, "Ticks : %d, %.1f ms en moyenne, %.0f ms au plus (50 ms = limite des 20 TPS)%n", ticks, moy, max));
        etatServeur(srv, t::append);
        t.append('\n');
        p.ecrire(t);
        String nom = "profil-" + LocalDateTime.now().format(NOM) + ".txt";
        ecrire(srv, nom, t.toString());
        return nom;
    }

    private static void suivreSession(MinecraftServer srv) {
        long maintenant = System.currentTimeMillis();
        String resume = null;
        boolean finie = false;
        UUID qui;
        double moy, max;
        synchronized (VERROU) {
            if (session == null) return;
            qui = demandeur;
            moy = sessionTicks == 0 ? 0 : sessionMsTotal / sessionTicks;
            max = sessionMsMax;
            if (maintenant >= finSession) finie = true;
            else if (maintenant >= prochainResume) {
                prochainResume = maintenant + 10_000;
                resume = session.resume();
            }
        }
        Consumer<Component> dire = c -> {
            if (qui == null) srv.sendSystemMessage(c);
            else {
                ServerPlayer p = srv.getPlayerList().getPlayer(qui);
                if (p != null) p.sendSystemMessage(c);
            }
        };
        if (finie) {
            String nom = arreterSession(srv);
            if (nom != null) dire.accept(Component.translatableWithFallback("noyau.diag.fin", "[Diag] Terminé : rapport diag/%s", nom)
                    .withStyle(s -> s.withColor(0x8FD3FF)));
        } else if (resume != null) {
            dire.accept(Component.translatableWithFallback("noyau.diag.direct", "[Diag] %s ms/tick (max %s), %s TPS — %s",
                    String.format(Locale.ROOT, "%.1f", moy), String.valueOf(Math.round(max)), tps(), resume)
                    .withStyle(s -> s.withColor(0x8FD3FF)));
        }
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
    }

    private static long tempsGc() {
        long t = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) t += Math.max(0, gc.getCollectionTime());
        return t;
    }

    // ------------------------------------------------------------------ fichiers
    public static Path dossier(MinecraftServer srv) {
        return srv.getServerDirectory().toPath().resolve("diag");
    }

    public static void ecrire(MinecraftServer srv, String nom, String texte) {
        Path d = dossier(srv);
        ECRIVAIN.execute(() -> {
            try {
                Files.createDirectories(d);
                Files.writeString(d.resolve(nom), texte, StandardCharsets.UTF_8);
                List<Path> tous;
                try (Stream<Path> s = Files.list(d)) {
                    tous = s.filter(f -> f.getFileName().toString().endsWith(".txt"))
                            .sorted(Comparator.comparing(f -> f.getFileName().toString())).toList();
                }
                for (int i = 0; i < tous.size() - GARDES; i++) Files.deleteIfExists(tous.get(i));
            } catch (Exception e) {
                LOG.warn("[diag] rapport {} non écrit : {}", nom, e.toString());
            }
        });
    }
}
