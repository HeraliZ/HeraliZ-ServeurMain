package fr.heraliz.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Zones de loot : lieux de la map dont les conteneurs se partagent le loot (rotation, {@link RotationLoot}). Une zone
 * par fichier, dans un pack de données du monde : {@code data/heraliz_loot/zones/<zone>.json} (sous-dossiers permis,
 * l'identifiant de la zone est alors « dossier/nom »). Format, écrit par le générateur de la map :
 * <pre>{ "nom": "Tcherno — quartier 3", "actifs": 0.2, "conteneurs": [[x, y, z, "t2_frigo"], ...] }</pre>
 * « actifs » (part des conteneurs qui ont du loot) et « dimension » (minecraft:overworld par défaut) sont facultatifs.
 * x, y, z : bloc principal du conteneur ; le nom est celui du bloc sans « heraliz_loot: ». Les véhicules sont ignorés.
 */
public final class ZonesLoot extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    /** Change à chaque rechargement (ou changement de configuration) : les mondes recalculent alors leurs actifs. */
    private static volatile int version;
    private static volatile Map<ResourceKey<Level>, Map<String, Zone>> ZONES = Map.of();
    private static volatile Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Zone>> INDEX = Map.of();

    /** Une zone : ses conteneurs (position du bloc principal + nom), dans l'ordre du fichier. */
    public static final class Zone {
        public final String id, nom;
        @Nullable
        public final Double actifs;
        public final ResourceKey<Level> dimension;
        private final long[] positions;
        private final String[] noms;
        private final Long2IntOpenHashMap indice = new Long2IntOpenHashMap();

        Zone(String id, String nom, @Nullable Double actifs, ResourceKey<Level> dimension, LongArrayList positions, List<String> noms) {
            this.id = id;
            this.nom = nom;
            this.actifs = actifs;
            this.dimension = dimension;
            this.positions = positions.toLongArray();
            this.noms = noms.toArray(new String[0]);
            indice.defaultReturnValue(-1);
            for (int i = 0; i < this.positions.length; i++) indice.put(this.positions[i], i);
        }

        public int taille() {
            return positions.length;
        }

        public long position(int i) {
            return positions[i];
        }

        public String conteneur(int i) {
            return noms[i];
        }

        /** Nom du conteneur attendu en pos (null si pos n'est pas dans la zone). */
        @Nullable
        public String conteneur(long pos) {
            int i = indice.get(pos);
            return i < 0 ? null : noms[i];
        }

        /** Part d'actifs : celle de la zone, sinon celle de la configuration serveur. */
        public double proportion() {
            return actifs != null ? actifs : ConfigLoot.proportion();
        }

        /**
         * Nombre de conteneurs actifs voulu : arrondi(proportion x taille), au moins 1 si la proportion n'est pas nulle,
         * au plus le nombre de conteneurs disponibles (ceux qui ne sont pas reconnus invalides).
         */
        public int cible(int disponibles) {
            if (disponibles <= 0) return 0;
            double p = proportion();
            if (p <= 0) return 0;
            int n = Math.max(1, (int) Math.round(p * taille()));
            return Math.min(n, disponibles);
        }
    }

    public ZonesLoot() {
        super(GSON, "zones");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> fichiers, ResourceManager rm, ProfilerFiller pf) {
        Map<ResourceKey<Level>, Map<String, Zone>> zones = new HashMap<>();
        Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Zone>> index = new HashMap<>();
        int total = 0, vehicules = 0, inconnus = 0, doublons = 0;
        // ordre stable : en cas de doublon, la zone dont l'identifiant vient en premier garde le conteneur
        for (Map.Entry<ResourceLocation, JsonElement> f : new TreeMap<>(fichiers).entrySet()) {
            ResourceLocation id = f.getKey();
            if (!id.getNamespace().equals(HeralizLoot.MODID)) continue;
            try {
                JsonObject o = f.getValue().getAsJsonObject();
                String zid = id.getPath();
                String nom = o.has("nom") && !o.get("nom").isJsonNull() ? o.get("nom").getAsString() : zid;
                Double actifs = o.has("actifs") && !o.get("actifs").isJsonNull()
                        ? Math.max(0.0, Math.min(1.0, o.get("actifs").getAsDouble())) : null;
                ResourceKey<Level> dim = Level.OVERWORLD;
                if (o.has("dimension")) {
                    ResourceLocation d = ResourceLocation.tryParse(o.get("dimension").getAsString());
                    if (d == null) throw new IllegalArgumentException("dimension illisible : " + o.get("dimension"));
                    dim = ResourceKey.create(Registries.DIMENSION, d);
                }
                Long2ObjectOpenHashMap<Zone> idx = index.computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>());
                LongArrayList positions = new LongArrayList();
                List<String> noms = new ArrayList<>();
                it.unimi.dsi.fastutil.longs.LongOpenHashSet vus = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
                for (JsonElement e : o.getAsJsonArray("conteneurs")) {
                    JsonArray a = e.getAsJsonArray();
                    String n = a.get(3).getAsString();
                    if (n.startsWith(HeralizLoot.MODID + ":")) n = n.substring(HeralizLoot.MODID.length() + 1);
                    if (n.startsWith("vehicules_")) {
                        vehicules++;
                        continue;
                    }
                    ConteneurBlock cb = ConteneurBlock.parNom(n);
                    if (cb == null || cb.vehicule) {
                        inconnus++;
                        HeralizLoot.LOG.debug("Zone {} : conteneur inconnu {}", zid, n);
                        continue;
                    }
                    long k = BlockPos.asLong(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
                    if (idx.containsKey(k) || !vus.add(k)) {
                        doublons++;
                        continue;
                    }
                    positions.add(k);
                    noms.add(cb.nom);
                }
                Zone z = new Zone(zid, nom, actifs, dim, positions, noms);
                for (long k : z.positions) idx.put(k, z);
                zones.computeIfAbsent(dim, k -> new TreeMap<>()).put(zid, z);
                total += z.taille();
            } catch (Exception ex) {
                HeralizLoot.LOG.error("Zone de loot {} illisible", id, ex);
            }
        }
        ZONES = zones;
        INDEX = index;
        version++;
        int nz = zones.values().stream().mapToInt(Map::size).sum();
        HeralizLoot.LOG.info("{} zones de loot, {} conteneurs{}{}{}", nz, total,
                vehicules > 0 ? ", " + vehicules + " véhicules ignorés" : "",
                inconnus > 0 ? ", " + inconnus + " noms inconnus ignorés" : "",
                doublons > 0 ? ", " + doublons + " positions en double ignorées" : "");
        if (inconnus > 0) HeralizLoot.LOG.warn("Zones de loot : {} conteneurs au nom inconnu (détail en DEBUG)", inconnus);
    }

    static int version() {
        return version;
    }

    /** La configuration a changé (proportion) : les mondes recalculent leurs actifs. */
    static void invalider() {
        version++;
    }

    /** Zones d'une dimension, par identifiant (ordre alphabétique). */
    static Map<String, Zone> zones(ResourceKey<Level> dim) {
        return ZONES.getOrDefault(dim, Collections.emptyMap());
    }

    /** Toutes les zones, toutes dimensions. */
    static Map<ResourceKey<Level>, Map<String, Zone>> toutes() {
        return ZONES;
    }

    @Nullable
    static Zone zone(ResourceKey<Level> dim, String id) {
        return zones(dim).get(id);
    }

    /** Zone qui contient le conteneur en pos (null : hors zone). */
    @Nullable
    static Zone zoneDe(ResourceKey<Level> dim, long pos) {
        Long2ObjectOpenHashMap<Zone> idx = INDEX.get(dim);
        return idx == null ? null : idx.get(pos);
    }

    /** Index position -> zone d'une dimension (lecture seule). */
    static Long2ObjectOpenHashMap<Zone> index(ResourceKey<Level> dim) {
        Long2ObjectOpenHashMap<Zone> idx = INDEX.get(dim);
        return idx == null ? new Long2ObjectOpenHashMap<>() : idx;
    }
}
