package fr.heraliz.casino.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Comptabilite du casino : mises et gains par machine, par jour et par joueur (commande /casino stats et onglet
 * Statistiques de la Tablette du Directeur). Permet de verifier que la maison gagne et de regler les taux.
 */
public class CasinoStats extends SavedData {
    private static final String NAME = "heralizcasino_stats";
    /** Jours conserves dans l'historique. */
    public static final int KEEP_DAYS = 120;
    /** Index des compteurs : mises placees, total mise, total verse, plus gros gain. */
    public static final int BETS = 0, WAGERED = 1, PAID = 2, MAX_WIN = 3, SIZE = 4;

    /** Totaux par machine depuis le debut. */
    private final Map<MachineType, long[]> machines = new EnumMap<>(MachineType.class);
    /** Joueurs distincts par machine depuis le debut. */
    private final Map<MachineType, Set<UUID>> machinePlayers = new EnumMap<>(MachineType.class);
    /** Par jour (jour epoch) puis par machine. */
    private final TreeMap<Integer, Map<MachineType, long[]>> days = new TreeMap<>();
    private final TreeMap<Integer, Map<MachineType, Set<UUID>>> dayPlayers = new TreeMap<>();
    /** Par joueur : total mise, total verse, plus gros gain. */
    private final Map<UUID, long[]> players = new HashMap<>();
    /** Record du casino : plus gros gain en une partie (joueur, montant, machine). */
    private UUID recordPlayer;
    private long recordAmount;
    private String recordMachine = "";

    public static CasinoStats get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CasinoStats::load, CasinoStats::new, NAME);
    }

    public static int today() {
        return (int) LocalDate.now().toEpochDay();
    }

    private long[] m(MachineType t) {
        return machines.computeIfAbsent(t, k -> new long[SIZE]);
    }

    private long[] d(MachineType t) {
        int day = today();
        if (!days.containsKey(day)) prune(day);
        return days.computeIfAbsent(day, k -> new EnumMap<>(MachineType.class)).computeIfAbsent(t, k -> new long[SIZE]);
    }

    private long[] p(UUID id) {
        return players.computeIfAbsent(id, k -> new long[4]);
    }

    private void prune(int today) {
        while (!days.isEmpty() && days.firstKey() < today - KEEP_DAYS) days.pollFirstEntry();
        while (!dayPlayers.isEmpty() && dayPlayers.firstKey() < today - KEEP_DAYS) dayPlayers.pollFirstEntry();
    }

    public void wager(MachineType t, UUID player, long amount) {
        long[] a = m(t), b = d(t);
        a[BETS]++;
        a[WAGERED] += amount;
        b[BETS]++;
        b[WAGERED] += amount;
        if (player != null) {
            p(player)[0] += amount;
            machinePlayers.computeIfAbsent(t, k -> new HashSet<>()).add(player);
            dayPlayers.computeIfAbsent(today(), k -> new EnumMap<>(MachineType.class)).computeIfAbsent(t, k -> new HashSet<>()).add(player);
        }
        // les videurs de la zone du casino ne sortent que ceux qui ont joue
        fr.heraliz.casino.zone.ZoneManager.onWager(player);
        setDirty();
    }

    public void paid(MachineType t, UUID player, long amount) {
        if (amount <= 0) return;
        long[] a = m(t), b = d(t);
        a[PAID] += amount;
        a[MAX_WIN] = Math.max(a[MAX_WIN], amount);
        b[PAID] += amount;
        b[MAX_WIN] = Math.max(b[MAX_WIN], amount);
        if (player != null) {
            long[] pl = p(player);
            pl[1] += amount;
            if (amount > pl[2]) {
                pl[2] = amount;
                pl[3] = t.ordinal() + 1;
            }
            if (amount > recordAmount) {
                recordAmount = amount;
                recordPlayer = player;
                recordMachine = t.id;
            }
        }
        setDirty();
    }

    /** Mise remboursee (partie annulee). */
    public void cancel(MachineType t, UUID player, long amount) {
        long[] a = m(t), b = d(t);
        a[BETS] = Math.max(0, a[BETS] - 1);
        a[WAGERED] -= amount;
        b[BETS] = Math.max(0, b[BETS] - 1);
        b[WAGERED] -= amount;
        if (player != null) p(player)[0] -= amount;
        setDirty();
    }

    /** mises placees, mise, verse (depuis le debut) */
    public long[] machine(MachineType t) {
        long[] a = machines.get(t);
        return a == null ? new long[3] : new long[]{a[BETS], a[WAGERED], a[PAID]};
    }

    public Map<UUID, long[]> players() {
        return players;
    }

    public void reset() {
        machines.clear();
        machinePlayers.clear();
        days.clear();
        dayPlayers.clear();
        players.clear();
        recordPlayer = null;
        recordAmount = 0;
        recordMachine = "";
        setDirty();
    }

    // =================================================================== tableau des gagnants
    /** Une ligne d'un classement : joueur, valeur classee, machine (identifiant, peut etre vide). */
    public record Row(String name, long value, String machine) {
    }

    /** Classements des ecrans : plus gros gains en une partie, plus grosses pertes nettes, plus gros parieurs. */
    /** Classements : valeurs declarees dans le code commun (LeaderboardBlockEntity, lues par l'affichage). */
    public static final int BOARD_WINNERS = fr.heraliz.casino.block.entity.LeaderboardBlockEntity.BOARD_WINNERS,
            BOARD_LOSERS = fr.heraliz.casino.block.entity.LeaderboardBlockEntity.BOARD_LOSERS,
            BOARD_BETTORS = fr.heraliz.casino.block.entity.LeaderboardBlockEntity.BOARD_BETTORS;

    public List<Row> board(MinecraftServer server, int kind, int n) {
        List<Map.Entry<UUID, long[]>> list = new ArrayList<>(players.entrySet());
        java.util.function.ToLongFunction<long[]> key = switch (kind) {
            case BOARD_LOSERS -> v -> v[0] - v[1];
            case BOARD_BETTORS -> v -> v[0];
            default -> v -> v.length > 2 ? v[2] : 0;
        };
        list.removeIf(e -> key.applyAsLong(e.getValue()) <= 0);
        list.sort((x, y) -> Long.compare(key.applyAsLong(y.getValue()), key.applyAsLong(x.getValue())));
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Map.Entry<UUID, long[]> e = list.get(i);
            long[] v = e.getValue();
            String machine = "";
            if (kind == BOARD_WINNERS && v.length > 3 && v[3] > 0 && v[3] <= MachineType.values().length) {
                machine = MachineType.values()[(int) v[3] - 1].id;
            }
            out.add(new Row(name(server, e.getKey()), key.applyAsLong(v), machine));
        }
        return out;
    }

    /** Record du casino : {nom, montant, identifiant de machine}, ou null. */
    public Object[] record(MinecraftServer server) {
        if (recordPlayer == null || recordAmount <= 0) return null;
        return new Object[]{name(server, recordPlayer), recordAmount, recordMachine};
    }

    static String name(MinecraftServer server, UUID id) {
        return server.getProfileCache() == null ? id.toString().substring(0, 8)
                : server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString().substring(0, 8));
    }

    // =================================================================== export (Tablette du Directeur)
    /**
     * Statistiques pour la tablette : par machine, totaux et 30 derniers jours ; joueurs les plus gagnants et
     * perdants. Chaque serie : [mises, mise, verse, plus gros gain, joueurs distincts].
     */
    public JsonObject export(MinecraftServer server) {
        int today = today();
        JsonObject root = new JsonObject();
        root.addProperty("today", today);
        JsonObject ms = new JsonObject();
        Set<UUID> all = new HashSet<>();
        for (MachineType t : MachineType.values()) {
            JsonObject o = new JsonObject();
            long[] a = machines.getOrDefault(t, new long[SIZE]);
            Set<UUID> pl = machinePlayers.getOrDefault(t, Set.of());
            all.addAll(pl);
            o.add("total", arr(a, pl.size()));
            JsonObject ds = new JsonObject();
            for (int day = today - 29; day <= today; day++) {
                Map<MachineType, long[]> dm = days.get(day);
                long[] v = dm == null ? null : dm.get(t);
                if (v == null) continue;
                Map<MachineType, Set<UUID>> dp = dayPlayers.get(day);
                int np = dp == null || dp.get(t) == null ? 0 : dp.get(t).size();
                ds.add(Integer.toString(day), arr(v, np));
            }
            o.add("days", ds);
            o.add("p", distinct(today, t));
            ms.add(t.id, o);
        }
        root.add("machines", ms);
        root.add("p", distinct(today, null));
        // joueurs distincts du casino par jour (toutes machines)
        JsonObject dayAll = new JsonObject();
        for (int day = today - 29; day <= today; day++) {
            Map<MachineType, Set<UUID>> dp = dayPlayers.get(day);
            if (dp == null) continue;
            Set<UUID> u = new HashSet<>();
            for (Set<UUID> s : dp.values()) u.addAll(s);
            dayAll.addProperty(Integer.toString(day), u.size());
        }
        root.add("dayPlayers", dayAll);
        root.addProperty("players", all.size());
        // joueurs : gain net (verse - mise) du point de vue du joueur
        List<Map.Entry<UUID, long[]>> list = new ArrayList<>(players.entrySet());
        list.sort((x, y) -> Long.compare(y.getValue()[1] - y.getValue()[0], x.getValue()[1] - x.getValue()[0]));
        JsonArray top = new JsonArray();
        for (int i = 0; i < list.size(); i++) {
            if (i >= 6 && i < list.size() - 6) continue;
            Map.Entry<UUID, long[]> e = list.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("name", server.getProfileCache() == null ? e.getKey().toString()
                    : server.getProfileCache().get(e.getKey()).map(com.mojang.authlib.GameProfile::getName).orElse(e.getKey().toString().substring(0, 8)));
            o.addProperty("wagered", e.getValue()[0]);
            o.addProperty("paid", e.getValue()[1]);
            top.add(o);
        }
        root.add("top", top);
        return root;
    }

    /** Joueurs distincts sur 1, 7 et 30 jours puis depuis le debut (une machine, ou tout le casino si t == null). */
    private JsonArray distinct(int today, MachineType t) {
        JsonArray a = new JsonArray();
        for (int n : new int[]{1, 7, 30}) {
            Set<UUID> u = new HashSet<>();
            for (int d = today - n + 1; d <= today; d++) {
                Map<MachineType, Set<UUID>> dp = dayPlayers.get(d);
                if (dp == null) continue;
                if (t == null) dp.values().forEach(u::addAll);
                else if (dp.get(t) != null) u.addAll(dp.get(t));
            }
            a.add(u.size());
        }
        if (t == null) {
            Set<UUID> u = new HashSet<>();
            machinePlayers.values().forEach(u::addAll);
            a.add(u.size());
        } else {
            a.add(machinePlayers.getOrDefault(t, Set.of()).size());
        }
        return a;
    }

    private static JsonArray arr(long[] v, int players) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < SIZE; i++) a.add(i < v.length ? v[i] : 0);
        a.add(players);
        return a;
    }

    // =================================================================== NBT
    public static CasinoStats load(CompoundTag tag) {
        CasinoStats s = new CasinoStats();
        CompoundTag m = tag.getCompound("Machines");
        for (String k : m.getAllKeys()) {
            MachineType t = MachineType.byId(k);
            if (t == null) continue;
            long[] a = m.getLongArray(k);
            long[] v = new long[SIZE];
            System.arraycopy(a, 0, v, 0, Math.min(a.length, SIZE));
            s.machines.put(t, v);
        }
        CompoundTag mp = tag.getCompound("MachinePlayers");
        for (String k : mp.getAllKeys()) {
            MachineType t = MachineType.byId(k);
            if (t != null) s.machinePlayers.put(t, uuids(mp.getList(k, Tag.TAG_STRING)));
        }
        CompoundTag ds = tag.getCompound("Days");
        for (String dk : ds.getAllKeys()) {
            try {
                int day = Integer.parseInt(dk);
                CompoundTag dm = ds.getCompound(dk);
                Map<MachineType, long[]> map = new EnumMap<>(MachineType.class);
                Map<MachineType, Set<UUID>> pmap = new EnumMap<>(MachineType.class);
                for (String k : dm.getAllKeys()) {
                    MachineType t = MachineType.byId(k);
                    if (t == null) continue;
                    CompoundTag e = dm.getCompound(k);
                    long[] a = e.getLongArray("V");
                    long[] v = new long[SIZE];
                    System.arraycopy(a, 0, v, 0, Math.min(a.length, SIZE));
                    map.put(t, v);
                    pmap.put(t, uuids(e.getList("P", Tag.TAG_STRING)));
                }
                s.days.put(day, map);
                s.dayPlayers.put(day, pmap);
            } catch (NumberFormatException ignored) {
            }
        }
        if (tag.hasUUID("RecordPlayer")) {
            s.recordPlayer = tag.getUUID("RecordPlayer");
            s.recordAmount = tag.getLong("RecordAmount");
            s.recordMachine = tag.getString("RecordMachine");
        }
        CompoundTag p = tag.getCompound("Players");
        for (String k : p.getAllKeys()) {
            try {
                long[] a = p.getLongArray(k);
                if (a.length >= 2) s.players.put(UUID.fromString(k), java.util.Arrays.copyOf(a, Math.max(4, a.length)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    private static Set<UUID> uuids(ListTag l) {
        Set<UUID> out = new HashSet<>();
        for (int i = 0; i < l.size(); i++) {
            try {
                out.add(UUID.fromString(l.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    private static ListTag list(Set<UUID> s) {
        ListTag l = new ListTag();
        for (UUID u : s) l.add(StringTag.valueOf(u.toString()));
        return l;
    }

    /** Écriture en fond ratée : les statistiques redeviennent « à sauver » à la sauvegarde suivante. */
    private final java.util.concurrent.atomic.AtomicBoolean echecFond = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Comme Minecraft (relevé {@link #save(CompoundTag)} sur le fil serveur, à la sauvegarde du monde), mais la compression
     * et l'écriture partent sur le fil d'écriture du casino ({@link EcrituresFond}, 27/10, perf). Même fichier, même
     * format ; écrit dans un fichier à côté puis mis à sa place.
     */
    @Override
    public void save(java.io.File fichier) {
        if (echecFond.getAndSet(false)) setDirty(true);
        if (!isDirty()) return;
        CompoundTag racine = new CompoundTag();
        racine.put("data", save(new CompoundTag()));
        net.minecraft.nbt.NbtUtils.addCurrentDataVersion(racine);
        setDirty(false);
        java.nio.file.Path cible = fichier.toPath();
        EcrituresFond.ecrire(cible, () -> {
            java.nio.file.Path tmp = cible.resolveSibling(cible.getFileName() + ".tmp");
            net.minecraft.nbt.NbtIo.writeCompressed(racine, tmp.toFile());
            try {
                java.nio.file.Files.move(tmp, cible, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(tmp, cible, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }, () -> echecFond.set(true));
    }

    /** Relevé : les tableaux de compteurs sont copiés (le NBT part sur le fil d'écriture, le jeu continue de compter). */
    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag m = new CompoundTag();
        for (Map.Entry<MachineType, long[]> e : machines.entrySet()) m.putLongArray(e.getKey().id, e.getValue().clone());
        tag.put("Machines", m);
        CompoundTag mp = new CompoundTag();
        for (Map.Entry<MachineType, Set<UUID>> e : machinePlayers.entrySet()) mp.put(e.getKey().id, list(e.getValue()));
        tag.put("MachinePlayers", mp);
        CompoundTag ds = new CompoundTag();
        for (Map.Entry<Integer, Map<MachineType, long[]>> de : days.entrySet()) {
            CompoundTag dm = new CompoundTag();
            Map<MachineType, Set<UUID>> pmap = dayPlayers.getOrDefault(de.getKey(), Map.of());
            for (Map.Entry<MachineType, long[]> e : de.getValue().entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putLongArray("V", e.getValue().clone());
                c.put("P", list(pmap.getOrDefault(e.getKey(), Set.of())));
                dm.put(e.getKey().id, c);
            }
            ds.put(Integer.toString(de.getKey()), dm);
        }
        tag.put("Days", ds);
        CompoundTag p = new CompoundTag();
        for (Map.Entry<UUID, long[]> e : players.entrySet()) p.putLongArray(e.getKey().toString(), e.getValue().clone());
        tag.put("Players", p);
        if (recordPlayer != null) {
            tag.putUUID("RecordPlayer", recordPlayer);
            tag.putLong("RecordAmount", recordAmount);
            tag.putString("RecordMachine", recordMachine);
        }
        return tag;
    }
}
