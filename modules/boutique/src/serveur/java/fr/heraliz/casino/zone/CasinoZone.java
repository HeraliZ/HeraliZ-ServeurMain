package fr.heraliz.casino.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Une zone du casino (sauvegardee avec le monde) : une salle delimitee par deux coins, ses entrees (la ou se tiennent
 * les videurs, et par ou l'on sort les fauches) et les tickets d'entree des joueurs (zone payante seulement).
 */
public class CasinoZone extends SavedData {
    /**
     * Les zones : le casino (entree payante) et la zone VIP (reservee aux grades de la boutique, Rescape au moins).
     * Chacune a sa salle, ses entrees et ses videurs ; la zone VIP peut etre dans le casino ou a part.
     */
    public enum Kind {
        CASINO("heralizcasino_zone", "casino"), VIP("heralizcasino_zone_vip", "vip");

        final String file;
        /** Identifiant (sauvegarde des videurs, commandes). */
        public final String id;

        Kind(String file, String id) {
            this.file = file;
            this.id = id;
        }

        public static Kind byId(String id) {
            return VIP.id.equals(id) ? VIP : CASINO;
        }
    }

    private final Kind kind;

    /** Une entree : point de passage (au sol, sur le bord de la zone) et direction de l'exterieur (horizontale). */
    public record Entrance(Vec3 door, double nx, double nz) {
        public Vec3 normal() {
            return new Vec3(nx, 0, nz);
        }

        /** Point a "d" blocs de la porte (positif : dehors, negatif : dedans). */
        public Vec3 at(double d) {
            return new Vec3(door.x + nx * d, door.y, door.z + nz * d);
        }

        /** Direction tangente a la facade (vers la droite quand on sort). */
        public Vec3 tangent() {
            return new Vec3(-nz, 0, nx);
        }
    }

    @Nullable
    private ResourceKey<Level> dimension;
    private int x0, y0, z0, x1, y1, z1;
    public final List<Entrance> entrances = new ArrayList<>();
    /** Tickets : joueur -> fin de validite (millisecondes, horloge du serveur). */
    private final Map<UUID, Long> passes = new HashMap<>();
    /** Jetons encaisses aux entrees depuis la creation de la zone. */
    private long revenue;

    public CasinoZone(Kind kind) {
        this.kind = kind;
    }

    /** La zone du casino (entree payante). */
    public static CasinoZone get(MinecraftServer server) {
        return get(server, Kind.CASINO);
    }

    public static CasinoZone get(MinecraftServer server, Kind kind) {
        return server.overworld().getDataStorage().computeIfAbsent(tag -> load(kind, tag), () -> new CasinoZone(kind), kind.file);
    }

    public Kind kind() {
        return kind;
    }

    public boolean vip() {
        return kind == Kind.VIP;
    }

    // =================================================================== la salle
    public boolean exists() {
        return dimension != null;
    }

    @Nullable
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public boolean in(Level level) {
        return dimension != null && dimension.equals(level.dimension());
    }

    /** Le point (pieds d'un joueur) est dans la salle. */
    public boolean contains(double x, double y, double z) {
        return dimension != null && x >= x0 && x < x1 + 1 && y >= y0 && y < y1 + 1 && z >= z0 && z < z1 + 1;
    }

    public boolean contains(Vec3 p) {
        return contains(p.x, p.y, p.z);
    }

    public AABB box() {
        return new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
    }

    public BlockPos min() {
        return new BlockPos(x0, y0, z0);
    }

    public BlockPos max() {
        return new BlockPos(x1, y1, z1);
    }

    /**
     * Delimite la salle entre deux coins. Une selection plate (deux coins poses au sol) est etendue en hauteur : un bloc
     * sous le plus bas, cinq au-dessus du plus haut.
     */
    public void define(ResourceKey<Level> dim, BlockPos a, BlockPos b) {
        dimension = dim;
        x0 = Math.min(a.getX(), b.getX());
        x1 = Math.max(a.getX(), b.getX());
        z0 = Math.min(a.getZ(), b.getZ());
        z1 = Math.max(a.getZ(), b.getZ());
        y0 = Math.min(a.getY(), b.getY());
        y1 = Math.max(a.getY(), b.getY());
        if (y1 - y0 < 3) {
            y0 -= 1;
            y1 += 5;
        }
        entrances.clear();
        revenue = 0;
        setDirty();
    }

    public void clear() {
        dimension = null;
        entrances.clear();
        passes.clear();
        setDirty();
    }

    // =================================================================== entrees
    /**
     * Entree au point "at" : la porte est ramenee sur la facade la plus proche, la direction de l'exterieur est celle
     * de cette facade.
     */
    public Entrance addEntrance(Vec3 at) {
        Entrance e = facade(at);
        entrances.add(e);
        setDirty();
        return e;
    }

    /** Point de la facade (mur vertical de la zone) le plus proche de "at", et sa direction vers l'exterieur. */
    public Entrance facade(Vec3 at) {
        double cx = Math.max(x0, Math.min(x1 + 1, at.x)), cz = Math.max(z0, Math.min(z1 + 1, at.z));
        double[] d = {Math.abs(at.x - x0), Math.abs(at.x - (x1 + 1)), Math.abs(at.z - z0), Math.abs(at.z - (z1 + 1))};
        int k = 0;
        for (int i = 1; i < 4; i++) if (d[i] < d[k]) k = i;
        return switch (k) {
            case 0 -> new Entrance(new Vec3(x0, at.y, cz), -1, 0);
            case 1 -> new Entrance(new Vec3(x1 + 1, at.y, cz), 1, 0);
            case 2 -> new Entrance(new Vec3(cx, at.y, z0), 0, -1);
            default -> new Entrance(new Vec3(cx, at.y, z1 + 1), 0, 1);
        };
    }

    @Nullable
    public Entrance nearest(Vec3 at) {
        Entrance best = null;
        double bd = Double.MAX_VALUE;
        for (Entrance e : entrances) {
            double d = e.door.distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // =================================================================== tickets
    public boolean hasPass(UUID player) {
        Long until = passes.get(player);
        return until != null && until > System.currentTimeMillis();
    }

    /** Minutes restantes du ticket (0 : aucun). */
    public long passMinutes(UUID player) {
        Long until = passes.get(player);
        long left = until == null ? 0 : until - System.currentTimeMillis();
        return left <= 0 ? 0 : Math.max(1, left / 60_000);
    }

    /** Ticket de "minutes" minutes ; 0 : juste le temps d'entrer (il faudra repayer a la prochaine entree). */
    public void grant(UUID player, int minutes) {
        long ms = minutes <= 0 ? 30_000L : minutes * 60_000L;
        passes.put(player, System.currentTimeMillis() + ms);
        passes.values().removeIf(until -> until <= System.currentTimeMillis());
        setDirty();
    }

    public void revoke(UUID player) {
        if (passes.remove(player) != null) setDirty();
    }

    public long revenue() {
        return revenue;
    }

    public void earn(long chips) {
        revenue += chips;
        setDirty();
    }

    // =================================================================== sauvegarde
    public static CasinoZone load(Kind kind, CompoundTag tag) {
        CasinoZone z = new CasinoZone(kind);
        if (tag.contains("Dim")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dim"));
            if (id != null) z.dimension = ResourceKey.create(Registries.DIMENSION, id);
            int[] b = tag.getIntArray("Box");
            if (b.length == 6) {
                z.x0 = b[0];
                z.y0 = b[1];
                z.z0 = b[2];
                z.x1 = b[3];
                z.y1 = b[4];
                z.z1 = b[5];
            } else {
                z.dimension = null;
            }
        }
        ListTag list = tag.getList("Entrances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            z.entrances.add(new Entrance(new Vec3(e.getDouble("X"), e.getDouble("Y"), e.getDouble("Z")), e.getDouble("NX"), e.getDouble("NZ")));
        }
        CompoundTag passes = tag.getCompound("Passes");
        for (String k : passes.getAllKeys()) {
            try {
                z.passes.put(UUID.fromString(k), passes.getLong(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        z.revenue = tag.getLong("Revenue");
        return z;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (dimension != null) {
            tag.putString("Dim", dimension.location().toString());
            tag.putIntArray("Box", new int[]{x0, y0, z0, x1, y1, z1});
        }
        ListTag list = new ListTag();
        for (Entrance e : entrances) {
            CompoundTag t = new CompoundTag();
            t.putDouble("X", e.door.x);
            t.putDouble("Y", e.door.y);
            t.putDouble("Z", e.door.z);
            t.putDouble("NX", e.nx);
            t.putDouble("NZ", e.nz);
            list.add(t);
        }
        tag.put("Entrances", list);
        CompoundTag p = new CompoundTag();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : passes.entrySet()) if (e.getValue() > now) p.putLong(e.getKey().toString(), e.getValue());
        tag.put("Passes", p);
        tag.putLong("Revenue", revenue);
        return tag;
    }
}
