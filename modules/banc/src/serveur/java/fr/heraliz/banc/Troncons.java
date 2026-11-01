package fr.heraliz.banc;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tronçons du banc (01/11).
 * <ul>
 *     <li><b>Terrain créé d'avance</b> ({@code /bots pregenerer <rayon>}, à la position de la commande ; {@code stop}) :
 *     au premier banc, 445 tronçons ont été créés PENDANT la mesure (les bots marchaient au bord du terrain déjà fait).
 *     Ici, chaque tronçon des zones des bots reçoit un ticket de niveau « plein » (chargé et créé jusqu'au bout, sans
 *     tourner : ni créatures ni ticks), 768 à la fois, comme un pré-générateur ; Minecraft les crée sur ses fils de tâche
 *     de fond, puis le ticket est retiré et le tronçon enregistré. Ligne {@code [banc] PREGEN {json}} à la fin ;</li>
 *     <li><b>Ce qui tient les tronçons chargés</b> ({@code /bots troncons}) : par monde, les tronçons chargés et les
 *     tickets par sorte (joueurs, spawn, nos tickets…), avec les tronçons que chaque sorte tient chargés (un ticket de
 *     niveau N garde chargés les tronçons jusqu'à 44 - N tronçons autour de lui). Ligne {@code [banc] TRONCONS {json}}.
 *     Lu une seule fois, après la mesure (un peu lourd).</li>
 * </ul>
 * Fil serveur seulement. Rien ne tourne sans pré-génération en cours.
 */
final class Troncons {
    private Troncons() {
    }

    static final TicketType<ChunkPos> PREGEN = TicketType.create("heraliz_banc_pregen", Comparator.comparingLong(ChunkPos::toLong));
    /** Arrivée d'un bot (départ, voyage) : 30 s, comme les tickets d'arrivée de la borne. */
    static final TicketType<ChunkPos> DEPART = TicketType.create("heraliz_banc_depart", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    /** Dalle du camp, gardée chargée comme le vrai spawn. */
    static final TicketType<ChunkPos> CAMP = TicketType.create("heraliz_banc_camp", Comparator.comparingLong(ChunkPos::toLong));

    /** Niveau au-delà duquel un tronçon n'est plus gardé (33 « plein » + 11 étapes de création). */
    private static final int NIVEAU_MAX = 44;
    private static final int EN_COURS_MAX = 768;
    /** Un tronçon pas prêt au bout de ce délai (ticks) est laissé de côté. */
    private static final int ABANDON = 20 * 120;

    private record Tache(ServerLevel niveau, long pos) {
    }

    private static final ArrayDeque<Tache> FILE = new ArrayDeque<>();
    private static final Map<Tache, Integer> EN_COURS = new LinkedHashMap<>();
    private static final Map<ServerLevel, LongOpenHashSet> VUS = new HashMap<>();
    private static int total, faits, abandonnes, ticks;
    private static long debutMs;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Troncons::tick);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> arreter());
    }

    /** Ajoute les tronçons d'un carré de {@code rayonBlocs} autour de (x, z) ; nombre de tronçons ajoutés. */
    static int pregenerer(ServerLevel l, int x, int z, int rayonBlocs) {
        int r = Math.max(0, (rayonBlocs + 15) >> 4), cx = x >> 4, cz = z >> 4, n = 0;
        LongOpenHashSet vus = VUS.computeIfAbsent(l, k -> new LongOpenHashSet());
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long p = ChunkPos.asLong(cx + dx, cz + dz);
                if (vus.add(p)) {
                    FILE.add(new Tache(l, p));
                    n++;
                }
            }
        }
        if (total == faits + abandonnes && EN_COURS.isEmpty()) {
            debutMs = System.currentTimeMillis();
            total = faits = abandonnes = 0;
        }
        total += n;
        return n;
    }

    static int enAttente() {
        return FILE.size() + EN_COURS.size();
    }

    /** Arrête tout (tickets retirés) ; nombre de tronçons laissés. */
    static int arreter() {
        int n = enAttente();
        for (Tache t : EN_COURS.keySet()) retirer(t);
        EN_COURS.clear();
        FILE.clear();
        VUS.clear();
        return n;
    }

    private static void retirer(Tache t) {
        ChunkPos cp = new ChunkPos(t.pos());
        t.niveau().getChunkSource().removeRegionTicket(PREGEN, cp, 0, cp);
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || (FILE.isEmpty() && EN_COURS.isEmpty())) return;
        long t0 = System.nanoTime();
        ticks++;
        if (ticks % 5 == 0) {
            for (Iterator<Map.Entry<Tache, Integer>> it = EN_COURS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Tache, Integer> en = it.next();
                Tache t = en.getKey();
                boolean pret = t.niveau().getChunkSource().getChunkNow(ChunkPos.getX(t.pos()), ChunkPos.getZ(t.pos())) != null;
                if (pret || ticks - en.getValue() > ABANDON) {
                    retirer(t);
                    it.remove();
                    if (pret) faits++;
                    else abandonnes++;
                }
            }
            while (EN_COURS.size() < EN_COURS_MAX && !FILE.isEmpty()) {
                Tache t = FILE.poll();
                ChunkPos cp = new ChunkPos(t.pos());
                t.niveau().getChunkSource().addRegionTicket(PREGEN, cp, 0, cp);
                EN_COURS.put(t, ticks);
            }
            double sec = Math.max(0.001, (System.currentTimeMillis() - debutMs) / 1000.0);
            if (ticks % 600 == 0) {
                HeralizBanc.LOG.info("[banc] terrain créé d'avance : {} sur {} tronçons ({} par seconde)", faits, total,
                        String.format(Locale.ROOT, "%.0f", faits / sec));
            }
            if (FILE.isEmpty() && EN_COURS.isEmpty()) {
                JsonObject j = new JsonObject();
                j.addProperty("troncons", total);
                j.addProperty("prets", faits);
                j.addProperty("abandonnes", abandonnes);
                j.addProperty("secondes", Math.round(sec));
                j.addProperty("par_seconde", Math.round(faits / sec));
                HeralizBanc.LOG.info("[banc] PREGEN {}", j);
                VUS.clear();
            }
        }
        Couts.banc += System.nanoTime() - t0;
    }

    // ------------------------------------------------------------------ tickets par sorte (/bots troncons)

    @Nullable
    private static Field distance, tickets;
    private static boolean cherche;

    @Nullable
    private static DistanceManager distanceManager(ChunkMap m) throws IllegalAccessException {
        if (!cherche) {
            cherche = true;
            for (Field f : ChunkMap.class.getDeclaredFields()) {
                if (DistanceManager.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    distance = f;
                    break;
                }
            }
        }
        return distance == null ? null : (DistanceManager) distance.get(m);
    }

    /**
     * La table des tickets du gestionnaire, trouvée par son type (pas par son nom) : la Long2ObjectOpenHashMap la plus
     * remplie dont les valeurs sont des ensembles (celle que Forge ajoute ne garde que les tickets forcés). Vide si le
     * monde n'a aucun ticket ; null si rien ne ressemble à cette table.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static Long2ObjectOpenHashMap<? extends Iterable<Ticket<?>>> tickets(DistanceManager dm) throws IllegalAccessException {
        Long2ObjectOpenHashMap<? extends Iterable<Ticket<?>>> mieux = null;
        boolean vide = false;
        List<Field> champs = new ArrayList<>();
        if (tickets != null) {
            champs.add(tickets);
        } else {
            for (Class<?> c = dm.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) if (f.getType() == Long2ObjectOpenHashMap.class) champs.add(f);
            }
        }
        for (Field f : champs) {
            f.setAccessible(true);
            Object v = f.get(dm);
            if (!(v instanceof Long2ObjectOpenHashMap<?> m)) continue;
            if (m.isEmpty()) {
                vide = true;
                continue;
            }
            if (!(m.values().iterator().next() instanceof Iterable<?>)) continue;
            if (mieux == null || m.size() > mieux.size()) {
                mieux = (Long2ObjectOpenHashMap<? extends Iterable<Ticket<?>>>) m;
                if (champs.size() > 1) tickets = f;
            }
        }
        if (mieux != null) return mieux;
        return vide ? new Long2ObjectOpenHashMap<Iterable<Ticket<?>>>() : null;
    }

    /** Par monde : tronçons chargés, tickets par sorte (nombre, tronçons portant le ticket, niveau le plus fort, tronçons tenus). */
    static JsonObject releve(MinecraftServer srv) {
        JsonObject tout = new JsonObject();
        for (ServerLevel l : srv.getAllLevels()) {
            JsonObject o = new JsonObject();
            int charges = l.getChunkSource().getLoadedChunksCount();
            o.addProperty("charges", charges);
            o.addProperty("joueurs", l.players().size());
            try {
                DistanceManager dm = distanceManager(l.getChunkSource().chunkMap);
                Long2ObjectOpenHashMap<? extends Iterable<Ticket<?>>> tk = dm == null ? null : tickets(dm);
                if (tk == null) {
                    o.addProperty("erreur", "tickets illisibles");
                } else {
                    // sorte -> {tickets, niveau le plus fort} ; sorte -> position -> niveau le plus fort
                    Map<String, int[]> parSorte = new TreeMap<>();
                    Map<String, Long2ObjectOpenHashMap<int[]>> positions = new HashMap<>();
                    for (Long2ObjectMap.Entry<? extends Iterable<Ticket<?>>> en : tk.long2ObjectEntrySet()) {
                        for (Ticket<?> t : en.getValue()) {
                            String sorte = String.valueOf(t.getType());
                            int niv = t.getTicketLevel();
                            int[] s = parSorte.computeIfAbsent(sorte, k -> new int[]{0, Integer.MAX_VALUE});
                            s[0]++;
                            s[1] = Math.min(s[1], niv);
                            Long2ObjectOpenHashMap<int[]> pm = positions.computeIfAbsent(sorte, k -> new Long2ObjectOpenHashMap<>());
                            int[] p = pm.get(en.getLongKey());
                            if (p == null) pm.put(en.getLongKey(), new int[]{niv});
                            else p[0] = Math.min(p[0], niv);
                        }
                    }
                    LongOpenHashSet tenusTous = new LongOpenHashSet();
                    JsonObject sortes = new JsonObject();
                    for (Map.Entry<String, int[]> s : parSorte.entrySet()) {
                        Long2ObjectOpenHashMap<int[]> pos = positions.get(s.getKey());
                        LongOpenHashSet tenus = new LongOpenHashSet();
                        for (Long2ObjectMap.Entry<int[]> p : pos.long2ObjectEntrySet()) {
                            int r = NIVEAU_MAX - p.getValue()[0];
                            if (r < 0) continue;
                            int x = ChunkPos.getX(p.getLongKey()), z = ChunkPos.getZ(p.getLongKey());
                            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) tenus.add(ChunkPos.asLong(x + dx, z + dz));
                        }
                        tenusTous.addAll(tenus);
                        JsonObject so = new JsonObject();
                        so.addProperty("tickets", s.getValue()[0]);
                        so.addProperty("positions", pos.size());
                        so.addProperty("niveau", s.getValue()[1]);
                        so.addProperty("tenus", tenus.size());
                        sortes.add(s.getKey(), so);
                    }
                    o.add("sortes", sortes);
                    o.addProperty("tenus", tenusTous.size());
                    // chargés mais tenus par aucun ticket : en train de se décharger (enregistrement en cours)
                    o.addProperty("sans_ticket", Math.max(0, charges - tenusTous.size()));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                o.addProperty("erreur", ex.toString());
            }
            tout.add(l.dimension().location().toString(), o);
        }
        return tout;
    }
}
