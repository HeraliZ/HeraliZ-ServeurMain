package fr.heraliz.noyau.serveur.diag;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.api.Fuites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.timings.ForgeTimings;
import net.minecraftforge.server.timings.TimeTracker;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Moniteur du noyau (24/10, « temps de chaque système par mod, alerte quand une liste grossit sans jamais
 * redescendre »). Serveur seulement. Deux outils :
 * <ul>
 *     <li><b>Temps par entité et entité de bloc</b> ({@code /diag mesure}) : le chronomètre de Forge ({@link TimeTracker},
 *     branché par Forge autour du tick de chaque entité et de chaque entité de bloc) est allumé quelques secondes ; on
 *     regroupe ensuite par type (zombie, conteneur de loot, porte…) et par mod le temps moyen pris à chaque tick. Le
 *     reste (événements, paquets, IA appelée ailleurs) est vu par le relevé des piles de {@link Diag}.</li>
 *     <li><b>Fuites</b> (toujours actif) : une fois par minute, tailles relevées (entités et objets au sol par monde,
 *     tronçons chargés, mémoire gardée après le ramasse-miettes, listes déclarées par les mods via {@link Fuites}).
 *     Une taille qui grossit pendant 20 minutes sans jamais redescendre (et d'au moins la moitié) : rapport
 *     {@code diag/fuite-<date>.txt} et opérateurs prévenus (une fois par heure et par liste).</li>
 * </ul>
 */
public final class Moniteur {
    private Moniteur() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int FENETRE = 20;

    private static long finMesure;
    /** Mesure demandée par /diag mesure : rapport écrit à la fin, prévenir qui l'a lancée (null : la console). */
    private static boolean rapportAttendu;
    private static java.util.UUID demandeur;
    private static final Map<String, Deque<Long>> SERIES = new LinkedHashMap<>();
    private static final Map<String, Long> DERNIERE_ALERTE = new HashMap<>();

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) return;
            if (e.getServer().getTickCount() % 1200 == 600) releverFuites(e.getServer());
            if (rapportAttendu && !mesureEnCours()) rapportMesure(e.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            TimeTracker.ENTITY_UPDATE.reset();
            TimeTracker.BLOCK_ENTITY_UPDATE.reset();
            SERIES.clear();
        });
    }

    // ------------------------------------------------------------------ temps par type
    /** Allume le chronomètre de Forge pour {@code secondes} (10 au moins conseillées : 100 ticks par objet). */
    public static void mesurer(int secondes) {
        TimeTracker.ENTITY_UPDATE.reset();
        TimeTracker.BLOCK_ENTITY_UPDATE.reset();
        TimeTracker.ENTITY_UPDATE.enable(secondes);
        TimeTracker.BLOCK_ENTITY_UPDATE.enable(secondes);
        finMesure = System.currentTimeMillis() + secondes * 1000L;
    }

    /** /diag mesure : mesure puis rapport {@code diag/mesure-<date>.txt}, résumé à {@code qui}. */
    static void mesurerPuisRapport(int secondes, java.util.UUID qui) {
        mesurer(secondes);
        rapportAttendu = true;
        demandeur = qui;
    }

    private static void rapportMesure(MinecraftServer srv) {
        rapportAttendu = false;
        StringBuilder t = new StringBuilder("HeraliZ — temps par entité et entité de bloc\nDate : ").append(LocalDateTime.now()).append("\n");
        Diag.etatServeur(srv, t::append);
        t.append('\n');
        ecrireMesure(t);
        String fichier = "mesure-" + LocalDateTime.now().format(NOM) + ".txt";
        Diag.ecrire(srv, fichier, t.toString());
        Component c = Component.translatableWithFallback("noyau.diag.mesure_fin", "[Diag] Plus lourds : %s — rapport diag/%s",
                resumeMesure(), fichier).withStyle(st -> st.withColor(0x8FD3FF));
        ServerPlayer p = demandeur == null ? null : srv.getPlayerList().getPlayer(demandeur);
        if (p != null) p.sendSystemMessage(c);
        else srv.sendSystemMessage(c);
    }

    public static boolean mesureEnCours() {
        return System.currentTimeMillis() < finMesure;
    }

    public static boolean mesureFaite() {
        return finMesure > 0;
    }

    /** Somme des moyennes par tick (ms) par clé ; {@code [0]} temps, {@code [1]} nombre d'objets. */
    private static <T> Map<String, double[]> regrouper(List<ForgeTimings<T>> donnees, java.util.function.Function<T, String> cle) {
        Map<String, double[]> m = new HashMap<>();
        for (ForgeTimings<T> t : donnees) {
            T o = t.getObject().get();
            if (o == null) continue;
            double[] a = m.computeIfAbsent(cle.apply(o), k -> new double[2]);
            a[0] += t.getAverageTimings() / 1_000_000.0;
            a[1]++;
        }
        return m;
    }

    private static String typeEntite(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null ? e.getClass().getName() : id.toString();
    }

    private static String typeBloc(BlockEntity b) {
        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(b.getType());
        return id == null ? b.getClass().getName() : id.toString();
    }

    private static Map<String, double[]> parMod(Map<String, double[]> parType) {
        Map<String, double[]> m = new HashMap<>();
        for (Map.Entry<String, double[]> e : parType.entrySet()) {
            int p = e.getKey().indexOf(':');
            String mod = p > 0 ? e.getKey().substring(0, p) : "?";
            double[] a = m.computeIfAbsent(mod, k -> new double[2]);
            a[0] += e.getValue()[0];
            a[1] += e.getValue()[1];
        }
        return m;
    }

    private static void table(StringBuilder b, String titre, Map<String, double[]> m, int n) {
        List<Map.Entry<String, double[]>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0]));
        double total = 0;
        for (Map.Entry<String, double[]> e : l) total += e.getValue()[0];
        b.append("== ").append(titre).append(String.format(Locale.ROOT, " (%.2f ms par tick en tout) ==%n", total));
        for (int i = 0; i < Math.min(n, l.size()); i++) {
            double[] v = l.get(i).getValue();
            b.append(String.format(Locale.ROOT, "%8.3f ms  %6d objets  %7.1f µs chacun  %s%n", v[0], (long) v[1],
                    v[1] > 0 ? v[0] * 1000 / v[1] : 0, l.get(i).getKey()));
        }
        b.append('\n');
    }

    /** Résultat de la dernière mesure (entités et entités de bloc, par type et par mod). */
    public static void ecrireMesure(StringBuilder b) {
        Map<String, double[]> ent = regrouper(TimeTracker.ENTITY_UPDATE.getTimingData(), Moniteur::typeEntite);
        Map<String, double[]> blocs = regrouper(TimeTracker.BLOCK_ENTITY_UPDATE.getTimingData(), Moniteur::typeBloc);
        b.append("Temps moyen pris à chaque tick (chronomètre de Forge, 100 derniers ticks de chaque objet).\n\n");
        table(b, "Entités par mod", parMod(ent), 15);
        table(b, "Entités par type", ent, 25);
        table(b, "Entités de bloc par mod", parMod(blocs), 15);
        table(b, "Entités de bloc par type", blocs, 25);
    }

    /** Une ligne : les 3 types qui pèsent le plus (entités et entités de bloc confondues). */
    public static String resumeMesure() {
        Map<String, double[]> tout = new HashMap<>(regrouper(TimeTracker.ENTITY_UPDATE.getTimingData(), Moniteur::typeEntite));
        tout.putAll(regrouper(TimeTracker.BLOCK_ENTITY_UPDATE.getTimingData(), Moniteur::typeBloc));
        List<Map.Entry<String, double[]>> l = new ArrayList<>(tout.entrySet());
        l.sort((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0]));
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(3, l.size()); i++) {
            if (i > 0) s.append(", ");
            s.append(l.get(i).getKey()).append(String.format(Locale.ROOT, " %.2f ms", l.get(i).getValue()[0]));
        }
        return s.length() == 0 ? "rien mesuré" : s.toString();
    }

    // ------------------------------------------------------------------ fuites
    private static void releverFuites(MinecraftServer srv) {
        Map<String, Long> v = new LinkedHashMap<>();
        for (ServerLevel lvl : srv.getAllLevels()) {
            long ent = 0, objets = 0, monstres = 0;
            for (Entity e : lvl.getAllEntities()) {
                if (e == null) continue;
                ent++;
                if (e instanceof ItemEntity) objets++;
                else if (e instanceof Enemy) monstres++;
            }
            String d = lvl.dimension().location().toString();
            v.put("entites " + d, ent);
            v.put("objets au sol " + d, objets);
            v.put("monstres " + d, monstres);
            v.put("tronçons chargés " + d, (long) lvl.getChunkSource().getLoadedChunksCount());
        }
        long vieux = memoireGardee();
        if (vieux >= 0) v.put("mémoire gardée après GC (Mo)", vieux);
        for (Map.Entry<String, IntSupplier> e : Fuites.series().entrySet()) {
            try {
                v.put(e.getKey(), (long) e.getValue().getAsInt());
            } catch (RuntimeException ex) {
                LOG.debug("[moniteur] liste {} illisible", e.getKey(), ex);
            }
        }
        long maintenant = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : v.entrySet()) {
            Deque<Long> s = SERIES.computeIfAbsent(e.getKey(), k -> new ArrayDeque<>());
            s.addLast(e.getValue());
            while (s.size() > 40) s.removeFirst();
            if (fuit(e.getKey(), s) && maintenant - DERNIERE_ALERTE.getOrDefault(e.getKey(), 0L) > 3_600_000L) {
                DERNIERE_ALERTE.put(e.getKey(), maintenant);
                alerter(srv, e.getKey(), s);
            }
        }
    }

    /** 20 relevés (minutes) qui ne redescendent jamais de plus de 2 %, et au moins +50 % (et un minimum selon la liste). */
    private static boolean fuit(String nom, Deque<Long> s) {
        if (s.size() < FENETRE + 1) return false;
        List<Long> l = new ArrayList<>(s);
        List<Long> f = l.subList(l.size() - FENETRE - 1, l.size());
        long debut = f.get(0), fin = f.get(f.size() - 1);
        long minimum = nom.startsWith("mémoire") ? 400 : nom.startsWith("tronçons") ? 500 : 200;
        if (fin < debut * 3 / 2 || fin - debut < minimum) return false;
        for (int i = 1; i < f.size(); i++) if (f.get(i) < f.get(i - 1) * 0.98) return false;
        return true;
    }

    private static void alerter(MinecraftServer srv, String nom, Deque<Long> s) {
        List<Long> l = new ArrayList<>(s);
        long debut = l.get(Math.max(0, l.size() - FENETRE - 1)), fin = l.get(l.size() - 1);
        StringBuilder t = new StringBuilder("HeraliZ — fuite possible\nDate : ").append(LocalDateTime.now()).append('\n');
        t.append("Liste : ").append(nom).append('\n');
        t.append("Relevés (une fois par minute, du plus ancien au plus récent) : ").append(l).append('\n');
        t.append("Elle grossit depuis ").append(FENETRE).append(" minutes sans jamais redescendre. Si c'est une liste d'un mod :")
                .append(" une entrée ajoutée et jamais retirée (joueur déconnecté gardé, cible morte gardée…).\n\n");
        Diag.etatServeur(srv, t::append);
        String fichier = "fuite-" + LocalDateTime.now().format(NOM) + ".txt";
        Diag.ecrire(srv, fichier, t.toString());
        LOG.warn("[moniteur] fuite possible : {} passe de {} à {} en {} min sans redescendre (diag/{})", nom, debut, fin, FENETRE, fichier);
        Component c = Component.translatableWithFallback("noyau.diag.fuite", "[Diag] Fuite possible : %s passe de %s à %s en %s min sans redescendre — diag/%s",
                nom, String.valueOf(debut), String.valueOf(fin), String.valueOf(FENETRE), fichier).withStyle(st -> st.withColor(0xFFB060));
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) if (p.hasPermissions(4)) p.sendSystemMessage(c);
    }

    /** Mémoire gardée par l'ancienne génération après le dernier ramasse-miettes (Mo), -1 si inconnue. */
    private static long memoireGardee() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String n = pool.getName().toLowerCase(Locale.ROOT);
            if (n.contains("old") || n.contains("tenured")) {
                MemoryUsage u = pool.getCollectionUsage();
                if (u != null) return u.getUsed() >> 20;
            }
        }
        return -1;
    }

    /** État des listes surveillées : dernière valeur et variation sur 20 minutes. */
    public static void ecrireFuites(java.util.function.Consumer<String> ligne) {
        if (SERIES.isEmpty()) {
            ligne.accept("Aucun relevé encore (le premier arrive une minute après le démarrage).");
            return;
        }
        for (Map.Entry<String, Deque<Long>> e : SERIES.entrySet()) {
            List<Long> l = new ArrayList<>(e.getValue());
            long fin = l.get(l.size() - 1), debut = l.get(Math.max(0, l.size() - FENETRE - 1));
            ligne.accept(String.format(Locale.ROOT, "%s : %d (%+d en %d min)%s", e.getKey(), fin, fin - debut,
                    Math.min(FENETRE, l.size() - 1), fuit(e.getKey(), e.getValue()) ? "  <- grossit sans redescendre" : ""));
        }
    }
}
