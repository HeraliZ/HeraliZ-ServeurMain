package fr.heraliz.noyau.serveur.diag;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.timings.TimeTracker;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Temps par système pendant un profil (01/11), mesuré exactement (pas par relevés) et seulement pendant un profil :
 * <ul>
 *     <li><b>entités et entités de bloc par type</b> : Forge entoure déjà le tick de chaque entité et de chaque entité de
 *     bloc de son chronomètre ({@link TimeTracker}) ; pendant un profil on l'allume, et {@code ChronoForgeMixin} range
 *     chaque durée par type et par monde, sur tout le profil (Forge, lui, ne garde que les 100 derniers ticks de chaque
 *     objet). Éteint, le chronomètre de Forge sort tout de suite : rien n'est appelé hors profil ;</li>
 *     <li><b>monde par monde</b> : durée du tick de chaque monde, que Forge mesure toujours ({@code getTickTime}) ;</li>
 *     <li><b>écouteurs de tous les mods, par événement chaud</b> (tick du serveur, des mondes, des joueurs, des créatures,
 *     dégâts, arrivées d'entités) : deux écouteurs à nous, le premier et le dernier, ajoutés le temps du profil ;</li>
 *     <li><b>tronçons</b> chargés, lus et écrits sur le disque, déchargés ; <b>entités arrivées</b> par type ;</li>
 *     <li><b>paquets</b> reçus et envoyés par type ({@link PaquetsDiag}).</li>
 * </ul>
 * Fil serveur seulement (sauf les compteurs des tronçons, qui peuvent venir d'un autre fil).
 */
public final class Systemes {
    private Systemes() {
    }

    private static final Logger LOG = LogUtils.getLogger();

    /** profil en cours (fil serveur ; lu par ChronoForgeMixin sur le fil serveur) */
    private static boolean actif;
    private static Thread fil;
    private static long appelsChrono;

    // ---- chronomètre de Forge : objet en cours et début, par chronomètre
    private static Object entite, bloc;
    private static long debutEntite, debutBloc;
    private static final IdentityHashMap<EntityType<?>, long[]> ENTITES = new IdentityHashMap<>();
    private static final IdentityHashMap<BlockEntityType<?>, long[]> BLOCS = new IdentityHashMap<>();
    /** monde -> [ns entités, ns entités de bloc] */
    private static final IdentityHashMap<Level, long[]> PAR_MONDE = new IdentityHashMap<>();
    private static Level dernierMonde;
    private static long[] dernierCompte;

    // ---- durée du tick de chaque monde (Forge) : [ns, ticks]
    private static final Map<ResourceKey<Level>, long[]> MONDES = new LinkedHashMap<>();
    private static boolean mondesIllisibles;

    // ---- écouteurs : chronos par événement, écouteurs à retirer
    private static final List<Chrono> CHRONOS = new ArrayList<>();
    private static final List<Consumer<?>> ECOUTEURS = new ArrayList<>();

    // ---- tronçons (dimension -> [chargés, lus, écrits, déchargés]) et arrivées d'entités
    private static final ConcurrentHashMap<String, AtomicLongArray> TRONCONS = new ConcurrentHashMap<>();
    private static final IdentityHashMap<EntityType<?>, long[]> ARRIVEES = new IdentityHashMap<>();

    /** Durée cumulée d'un événement, du premier au dernier écouteur (imbrication comprise). */
    private static final class Chrono {
        final String nom;
        long ns, nb;
        final long[] debuts = new long[16];
        int profondeur;

        Chrono(String nom) {
            this.nom = nom;
        }

        void debut() {
            if (profondeur < debuts.length) debuts[profondeur] = System.nanoTime();
            profondeur++;
        }

        void fin() {
            if (profondeur <= 0) return;
            profondeur--;
            if (profondeur < debuts.length) {
                ns += System.nanoTime() - debuts[profondeur];
                nb++;
            }
        }
    }

    // ------------------------------------------------------------------ début et fin d'un profil (fil serveur)
    static void demarrer(MinecraftServer srv) {
        vider();
        fil = Thread.currentThread();
        actif = true;
        // chronomètre de Forge : allumé pour toute la durée (ChronoForgeMixin le garde allumé et range les durées)
        TimeTracker.ENTITY_UPDATE.enable(24 * 3600);
        TimeTracker.BLOCK_ENTITY_UPDATE.enable(24 * 3600);
        entourer(TickEvent.ServerTickEvent.class, "tick du serveur", e -> e.phase == TickEvent.Phase.START ? 0 : 1);
        entourer(TickEvent.LevelTickEvent.class, "tick d'un monde", e -> e.phase == TickEvent.Phase.START ? 0 : 1);
        entourer(TickEvent.PlayerTickEvent.class, "tick d'un joueur", e -> e.phase == TickEvent.Phase.START ? 0 : 1);
        entourer(LivingEvent.LivingTickEvent.class, "tick d'une créature (LivingTickEvent)", null);
        entourer(LivingHurtEvent.class, "dégâts (LivingHurtEvent)", null);
        entourer(EntityJoinLevelEvent.class, "arrivée d'une entité (EntityJoinLevelEvent)", null);
        ecouter(EntityJoinLevelEvent.class, e -> {
            if (Thread.currentThread() == fil && e.getEntity() != null) ARRIVEES.computeIfAbsent(e.getEntity().getType(), k -> new long[1])[0]++;
        });
        ecouter(ChunkEvent.Load.class, e -> troncon(e.getLevel(), 0));
        ecouter(ChunkDataEvent.Load.class, e -> troncon(e.getLevel(), 1));
        ecouter(ChunkDataEvent.Save.class, e -> troncon(e.getLevel(), 2));
        ecouter(ChunkEvent.Unload.class, e -> troncon(e.getLevel(), 3));
        ecouter(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) PaquetsDiag.brancher(p);
        });
        PaquetsDiag.demarrer(srv);
    }

    /** Fin du profil : tout est retiré, puis le texte des mesures. */
    static String arreter(MinecraftServer srv, int ticks, double secondes) {
        actif = false;
        for (Consumer<?> c : ECOUTEURS) {
            try {
                MinecraftForge.EVENT_BUS.unregister(c);
            } catch (RuntimeException ex) {
                LOG.debug("[diag] écouteur du profil non retiré", ex);
            }
        }
        ECOUTEURS.clear();
        PaquetsDiag.arreter(srv);
        StringBuilder b = new StringBuilder();
        try {
            // avant d'éteindre le chronomètre de Forge : s'il a fallu le prendre seul, ses relevés sont encore là
            ecrire(srv, b, Math.max(1, ticks), Math.max(0.001, secondes));
        } catch (RuntimeException ex) {
            b.append("  mesures illisibles : ").append(ex).append('\n');
        }
        eteindreChrono();
        vider();
        return b.toString();
    }

    static void arretUrgent() {
        if (!actif) return;
        actif = false;
        for (Consumer<?> c : ECOUTEURS) {
            try {
                MinecraftForge.EVENT_BUS.unregister(c);
            } catch (RuntimeException ignore) {
                // bus déjà arrêté
            }
        }
        ECOUTEURS.clear();
        PaquetsDiag.arreter(null);
        eteindreChrono();
        vider();
    }

    /**
     * Chronomètre de Forge rendu à son état : éteint, ou, si /diag mesure tourne encore, avec SA durée (Forge l'éteint
     * seul à la fin de la mesure ; sans ça, il resterait allumé pour toujours).
     */
    private static void eteindreChrono() {
        if (Moniteur.mesureEnCours()) {
            TimeTracker.ENTITY_UPDATE.enable(Moniteur.dureeMesure());
            TimeTracker.BLOCK_ENTITY_UPDATE.enable(Moniteur.dureeMesure());
        } else {
            TimeTracker.ENTITY_UPDATE.reset();
            TimeTracker.BLOCK_ENTITY_UPDATE.reset();
        }
    }

    private static void vider() {
        entite = bloc = null;
        ENTITES.clear();
        BLOCS.clear();
        PAR_MONDE.clear();
        dernierMonde = null;
        dernierCompte = null;
        MONDES.clear();
        mondesIllisibles = false;
        CHRONOS.clear();
        TRONCONS.clear();
        ARRIVEES.clear();
        appelsChrono = 0;
    }

    /** Fin de chaque tick pendant un profil : durée du tick de chaque monde (mesurée par Forge). */
    static void tick(MinecraftServer srv) {
        if (!actif) return;
        if (srv.getTickCount() % 20 == 0) {
            // /diag mesure lancé pendant le profil remet le chronomètre de Forge à SA durée : on le garde allumé
            TimeTracker.ENTITY_UPDATE.enable(24 * 3600);
            TimeTracker.BLOCK_ENTITY_UPDATE.enable(24 * 3600);
        }
        if (mondesIllisibles) return;
        try {
            int i = srv.getTickCount() % 100;
            for (ServerLevel l : srv.getAllLevels()) {
                long[] t = srv.getTickTime(l.dimension());
                if (t == null || t.length != 100) continue;
                long[] a = MONDES.computeIfAbsent(l.dimension(), k -> new long[2]);
                a[0] += t[i];
                a[1]++;
            }
        } catch (Throwable t) {
            mondesIllisibles = true;
            LOG.debug("[diag] durée des mondes illisible", t);
        }
    }

    private static <E extends Event> void ecouter(Class<E> type, Consumer<E> c) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, type, c);
        ECOUTEURS.add(c);
    }

    /** Premier et dernier écouteur d'un événement ; {@code phase} : 0 début, 1 fin (null : une seule phase). */
    private static <E extends Event> void entourer(Class<E> type, String nom, ToIntFunction<E> phase) {
        Chrono[] c = phase == null ? new Chrono[]{new Chrono(nom)} : new Chrono[]{new Chrono(nom + " (début)"), new Chrono(nom + " (fin)")};
        for (Chrono x : c) CHRONOS.add(x);
        Consumer<E> haut = e -> {
            if (Thread.currentThread() == fil) c[phase == null ? 0 : phase.applyAsInt(e)].debut();
        };
        Consumer<E> bas = e -> {
            if (Thread.currentThread() == fil) c[phase == null ? 0 : phase.applyAsInt(e)].fin();
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, type, haut);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, type, bas);
        ECOUTEURS.add(haut);
        ECOUTEURS.add(bas);
    }

    private static void troncon(LevelAccessor l, int quoi) {
        if (!(l instanceof ServerLevel sl)) return;
        TRONCONS.computeIfAbsent(sl.dimension().location().toString(), k -> new AtomicLongArray(4)).incrementAndGet(quoi);
    }

    // ------------------------------------------------------------------ chronomètre de Forge (ChronoForgeMixin)
    /**
     * Début du tick d'une entité ou d'une entité de bloc ({@code nanos} : relevé par Forge). Vrai : Forge ne fait pas son
     * propre relevé (il n'est utile qu'à /diag mesure, qui garde le sien s'il tourne).
     */
    public static boolean debutChrono(Object chrono, Object objet, long nanos) {
        if (!actif || Thread.currentThread() != fil) return false;
        if (chrono == TimeTracker.ENTITY_UPDATE) {
            entite = objet;
            debutEntite = nanos;
        } else {
            bloc = objet;
            debutBloc = nanos;
        }
        return !Moniteur.mesureEnCours();
    }

    public static boolean finChrono(Object chrono, Object objet, long nanos) {
        if (!actif || Thread.currentThread() != fil) return false;
        appelsChrono++;
        if (chrono == TimeTracker.ENTITY_UPDATE) {
            if (objet == entite && objet instanceof Entity e) {
                long d = nanos - debutEntite;
                long[] a = ENTITES.get(e.getType());
                if (a == null) ENTITES.put(e.getType(), a = new long[2]);
                a[0] += d;
                a[1]++;
                monde(e.level())[0] += d;
            }
            entite = null;
        } else {
            if (objet == bloc && objet instanceof BlockEntity be) {
                long d = nanos - debutBloc;
                long[] a = BLOCS.get(be.getType());
                if (a == null) BLOCS.put(be.getType(), a = new long[2]);
                a[0] += d;
                a[1]++;
                if (be.getLevel() != null) monde(be.getLevel())[1] += d;
            }
            bloc = null;
        }
        return !Moniteur.mesureEnCours();
    }

    private static long[] monde(Level l) {
        if (l == dernierMonde) return dernierCompte;
        long[] a = PAR_MONDE.get(l);
        if (a == null) PAR_MONDE.put(l, a = new long[2]);
        dernierMonde = l;
        dernierCompte = a;
        return a;
    }

    // ------------------------------------------------------------------ texte
    private static String nomEntite(EntityType<?> t) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(t);
        return id == null ? String.valueOf(t) : id.toString();
    }

    private static String nomBloc(BlockEntityType<?> t) {
        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(t);
        return id == null ? String.valueOf(t) : id.toString();
    }

    private static void ecrire(MinecraftServer srv, StringBuilder b, int ticks, double secondes) {
        // ---- mondes
        b.append("-- Par monde (ms par tick ; tick du monde mesuré par Forge, entités et entités de bloc par le chronomètre) --\n");
        b.append(String.format(Locale.ROOT, "  %-28s %8s %9s %9s %9s %9s %8s %9s%n", "monde", "tick", "entités", "de bloc", "reste",
                "entités", "joueurs", "tronçons"));
        Map<ResourceKey<Level>, long[]> parDim = new HashMap<>();
        for (Map.Entry<Level, long[]> x : PAR_MONDE.entrySet()) parDim.put(x.getKey().dimension(), x.getValue());
        for (ServerLevel l : srv.getAllLevels()) {
            long[] t = MONDES.get(l.dimension());
            long[] c = parDim.getOrDefault(l.dimension(), new long[2]);
            double tick = t == null || t[1] == 0 ? -1 : t[0] / 1e6 / t[1];
            double ent = c[0] / 1e6 / ticks, blocs = c[1] / 1e6 / ticks;
            int n = 0;
            for (Entity e : l.getAllEntities()) if (e != null) n++;
            b.append(String.format(Locale.ROOT, "  %-28s %8s %9.2f %9.2f %9s %9d %8d %9d%n", l.dimension().location(),
                    tick < 0 ? "?" : String.format(Locale.ROOT, "%.2f", tick), ent, blocs,
                    tick < 0 ? "?" : String.format(Locale.ROOT, "%.2f", Math.max(0, tick - ent - blocs)), n, l.players().size(),
                    l.getChunkSource().getLoadedChunksCount()));
        }
        b.append("  reste = tronçons, blocs (ticks aléatoires et programmés), météo, apparitions, écouteurs de LevelTickEvent…\n");
        if (mondesIllisibles) b.append("  (durée des mondes illisible sur ce serveur)\n");
        b.append('\n');

        // ---- entités et entités de bloc
        if (ENTITES.isEmpty() && BLOCS.isEmpty()) {
            b.append("-- Entités et entités de bloc : rien mesuré (le chronomètre de Forge n'a pas été appelé : mixin non posé ?) ;\n");
            b.append("   repli : chronomètre de Forge seul (100 derniers ticks de chaque objet) --\n");
            Moniteur.ecrireMesure(b);
        } else {
            Map<String, long[]> ent = new HashMap<>();
            for (Map.Entry<EntityType<?>, long[]> x : ENTITES.entrySet()) ent.put(nomEntite(x.getKey()), x.getValue());
            Map<String, long[]> blocs = new HashMap<>();
            for (Map.Entry<BlockEntityType<?>, long[]> x : BLOCS.entrySet()) blocs.put(nomBloc(x.getKey()), x.getValue());
            table(b, "Entités par type (tout le profil)", ent, ticks, 30, "entités");
            table(b, "Entités par mod", parMod(ent), ticks, 15, "entités");
            table(b, "Entités de bloc par type", blocs, ticks, 25, "objets");
            table(b, "Entités de bloc par mod", parMod(blocs), ticks, 15, "objets");
            b.append(String.format(Locale.ROOT, "Coût de ce chronomètre : de l'ordre de 0,1 µs par entité et par tick (%d durées rangées)%n%n", appelsChrono));
        }

        // ---- écouteurs par événement
        b.append("-- Écouteurs de tous les mods, par événement (du premier au dernier écouteur ; imbrications comprises) --\n");
        b.append(String.format(Locale.ROOT, "  %9s %12s %10s  %s%n", "ms/tick", "fois/tick", "µs chaque", "événement"));
        for (Chrono c : CHRONOS) {
            if (c.nb == 0) continue;
            b.append(String.format(Locale.ROOT, "  %9.3f %12.1f %10.2f  %s%n", c.ns / 1e6 / ticks, (double) c.nb / ticks, c.ns / 1e3 / c.nb, c.nom));
        }
        b.append("  (le détail par écouteur est dans la section 4, d'après les relevés)\n\n");

        // ---- tronçons
        b.append("-- Tronçons pendant le profil (nombre ; par seconde) --\n");
        b.append(String.format(Locale.ROOT, "  %-28s %16s %16s %16s %16s%n", "monde", "chargés", "lus (disque)", "écrits (disque)", "déchargés"));
        if (TRONCONS.isEmpty()) b.append("  aucun\n");
        for (Map.Entry<String, AtomicLongArray> x : TRONCONS.entrySet()) {
            AtomicLongArray a = x.getValue();
            b.append(String.format(Locale.ROOT, "  %-28s %16s %16s %16s %16s%n", x.getKey(), parSeconde(a.get(0), secondes), parSeconde(a.get(1), secondes),
                    parSeconde(a.get(2), secondes), parSeconde(a.get(3), secondes)));
        }
        b.append('\n');

        // ---- arrivées
        b.append("-- Entités arrivées dans un monde (apparitions, chargements, tirs…), par type --\n");
        List<Map.Entry<EntityType<?>, long[]>> arr = new ArrayList<>(ARRIVEES.entrySet());
        arr.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
        for (int i = 0; i < Math.min(20, arr.size()); i++) {
            b.append(String.format(Locale.ROOT, "  %8d (%6.1f /s)  %s%n", arr.get(i).getValue()[0], arr.get(i).getValue()[0] / secondes, nomEntite(arr.get(i).getKey())));
        }
        if (arr.isEmpty()) b.append("  aucune\n");
        b.append('\n');

        // ---- paquets
        PaquetsDiag.ecrire(b, secondes);
    }

    private static String parSeconde(long n, double s) {
        return String.format(Locale.ROOT, "%d (%.1f/s)", n, n / s);
    }

    private static Map<String, long[]> parMod(Map<String, long[]> parType) {
        Map<String, long[]> m = new HashMap<>();
        for (Map.Entry<String, long[]> e : parType.entrySet()) {
            int p = e.getKey().indexOf(':');
            long[] a = m.computeIfAbsent(p > 0 ? e.getKey().substring(0, p) : "?", k -> new long[2]);
            a[0] += e.getValue()[0];
            a[1] += e.getValue()[1];
        }
        return m;
    }

    /** {@code [0]} ns cumulées, {@code [1]} ticks d'objets ; en ms par tick, objets par tick et µs par objet. */
    private static void table(StringBuilder b, String titre, Map<String, long[]> m, int ticks, int n, String quoi) {
        List<Map.Entry<String, long[]>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
        long total = 0;
        for (Map.Entry<String, long[]> e : l) total += e.getValue()[0];
        b.append("-- ").append(titre).append(String.format(Locale.ROOT, " : %.2f ms par tick en tout --%n", total / 1e6 / ticks));
        b.append(String.format(Locale.ROOT, "  %9s %12s %10s  %s%n", "ms/tick", quoi + "/tick", "µs chaque", "type"));
        for (int i = 0; i < Math.min(n, l.size()); i++) {
            long[] v = l.get(i).getValue();
            b.append(String.format(Locale.ROOT, "  %9.3f %12.1f %10.2f  %s%n", v[0] / 1e6 / ticks, (double) v[1] / ticks, v[1] == 0 ? 0 : v[0] / 1e3 / v[1], l.get(i).getKey()));
        }
        b.append('\n');
    }
}
