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
 * <p>Les zones changent en jeu : un conteneur posé ou remplacé entre tout seul dans la zone de son tiers la plus proche
 * ({@link #rattacher}, {@link #placer}) ; loin de toute zone de son tiers, il en commence une nouvelle ({@link #creer}).
 * Ces changements, et la part d'actifs réglée par commande, sont gardés par le monde ({@link RotationLoot}) et remis à
 * chaque rechargement des fichiers.</p>
 */
public final class ZonesLoot extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    /** Change à chaque rechargement (ou changement de configuration) : les mondes recalculent alors leurs actifs. */
    private static volatile int version;
    private static volatile Map<ResourceKey<Level>, Map<String, Zone>> ZONES = Map.of();
    private static volatile Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Zone>> INDEX = Map.of();
    /** Index des fichiers (position -> zone), tel que lu : sans les changements faits en jeu. */
    private static volatile Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Zone>> ORIGINE = Map.of();
    /** Un conteneur posé en jeu rejoint la zone de son niveau dont un conteneur est à moins de RAYON blocs. */
    static final int RAYON = 64;

    /** Une zone : ses conteneurs (position du bloc principal + nom), ceux du fichier puis ceux posés en jeu. */
    public static final class Zone {
        public final String id, nom;
        @Nullable
        public final Double actifs;
        public final ResourceKey<Level> dimension;
        /** Part d'actifs réglée en jeu (/heralizloot zone <id> part), prioritaire ; null : celle du fichier. */
        @Nullable
        volatile Double partForcee;
        /** Niveau de la zone : celui de la plupart de ses conteneurs (t5_... -> 5), 0 si aucun ; donné à la création
         *  pour une zone créée en jeu. */
        public int niveau;
        private final LongArrayList positions;
        private final List<String> noms;
        private final Long2IntOpenHashMap indice = new Long2IntOpenHashMap();
        /** Conteneurs du fichier (position -> nom), même si la position a changé de modèle ou de zone en jeu. */
        private final Long2ObjectOpenHashMap<String> fichier = new Long2ObjectOpenHashMap<>();

        Zone(String id, String nom, @Nullable Double actifs, ResourceKey<Level> dimension, LongArrayList positions, List<String> noms) {
            this.id = id;
            this.nom = nom;
            this.actifs = actifs;
            this.dimension = dimension;
            this.positions = positions;
            this.noms = new ArrayList<>(noms);
            indice.defaultReturnValue(-1);
            int[] parNiveau = new int[10];
            for (int i = 0; i < positions.size(); i++) {
                indice.put(positions.getLong(i), i);
                fichier.put(positions.getLong(i), noms.get(i));
                int n = ZonesLoot.niveau(noms.get(i));
                if (n > 0 && n < parNiveau.length) parNiveau[n]++;
            }
            int m = 0;
            for (int n = 1; n < parNiveau.length; n++) {
                if (parNiveau[n] > parNiveau[m]) m = n;
            }
            this.niveau = m;
        }

        public int taille() {
            return positions.size();
        }

        public long position(int i) {
            return positions.getLong(i);
        }

        public String conteneur(int i) {
            return noms.get(i);
        }

        /** Nom du conteneur attendu en pos (null si pos n'est pas dans la zone). */
        @Nullable
        public String conteneur(long pos) {
            int i = indice.get(pos);
            return i < 0 ? null : noms.get(i);
        }

        /** Nom que le fichier de la zone met en pos (null : pos n'est pas dans le fichier). */
        @Nullable
        public String duFichier(long pos) {
            return fichier.get(pos);
        }

        /** Met le conteneur nom en pos (ajouté, ou nouveau modèle à une place déjà dans la zone). */
        void poser(long pos, String nom) {
            int i = indice.get(pos);
            if (i >= 0) {
                noms.set(i, nom);
                return;
            }
            indice.put(pos, positions.size());
            positions.add(pos);
            noms.add(nom);
        }

        /** Retire pos de la zone (le dernier conteneur prend sa place dans la liste). */
        void enlever(long pos) {
            int i = indice.remove(pos);
            if (i < 0) return;
            int der = positions.size() - 1;
            if (i != der) {
                long k = positions.getLong(der);
                positions.set(i, k);
                noms.set(i, noms.get(der));
                indice.put(k, i);
            }
            positions.removeLong(der);
            noms.remove(der);
        }

        /** Carré de la distance de (x, y, z) au plus proche conteneur de la zone (MAX_VALUE si la zone est vide). */
        double distance2(int x, int y, int z) {
            double d = Double.MAX_VALUE;
            for (int i = 0; i < positions.size(); i++) {
                long k = positions.getLong(i);
                double dx = BlockPos.getX(k) - x, dy = BlockPos.getY(k) - y, dz = BlockPos.getZ(k) - z;
                d = Math.min(d, dx * dx + dy * dy + dz * dz);
            }
            return d;
        }

        /**
         * Part d'actifs : celle réglée en jeu, sinon celle de la zone (ou de la configuration serveur) multipliée par le
         * facteur de son niveau.
         */
        public double proportion() {
            Double p = partForcee;
            if (p != null) return p;
            // plusieurs objets par conteneur (23/10) : moins de conteneurs remplis à la fois, facteur par niveau
            return (actifs != null ? actifs : ConfigLoot.proportion()) * ConfigLoot.facteur(niveau);
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
        Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Zone>> origine = new HashMap<>();
        index.forEach((d, idx) -> origine.put(d, new Long2ObjectOpenHashMap<>(idx)));
        ZONES = zones;
        INDEX = index;
        ORIGINE = origine;
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

    /** Zone dont le fichier contient pos, avant tout changement fait en jeu (null : pos n'est dans aucun fichier). */
    @Nullable
    static Zone origine(ResourceKey<Level> dim, long pos) {
        Long2ObjectOpenHashMap<Zone> idx = ORIGINE.get(dim);
        return idx == null ? null : idx.get(pos);
    }

    /** Niveau d'un conteneur d'après son préfixe : t5_caisse_mortier -> 5, soins2_... -> 2 (0 sans chiffre). */
    static int niveau(String nom) {
        int i = nom.indexOf('_');
        String p = i > 0 ? nom.substring(0, i) : nom;
        int j = p.length();
        while (j > 0 && Character.isDigit(p.charAt(j - 1))) j--;
        try {
            return j < p.length() ? Integer.parseInt(p.substring(j)) : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Zone où ranger un conteneur posé en jeu en pos : la zone de son niveau dont un conteneur est le plus près, à moins
     * de {@link #RAYON} blocs (null : aucune, il faut en commencer une, {@link #creer}).
     */
    @Nullable
    static Zone rattacher(ResourceKey<Level> dim, long pos, String nom) {
        int niv = niveau(nom), x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
        Zone mieux = null;
        double dm = (double) RAYON * RAYON;
        for (Zone zo : zones(dim).values()) {
            if (zo.niveau != niv) continue;
            double d = zo.distance2(x, y, z);
            if (d <= dm) {
                dm = d;
                mieux = zo;
            }
        }
        return mieux;
    }

    /** Part d'actifs des zones de ce niveau dans les fichiers (celle de la plus grande), null si aucune n'en donne. */
    @Nullable
    static Double partNiveau(ResourceKey<Level> dim, int niv) {
        Zone mieux = null;
        for (Zone zo : zones(dim).values()) {
            if (zo.niveau == niv && zo.actifs != null && (mieux == null || zo.taille() > mieux.taille())) mieux = zo;
        }
        return mieux == null ? null : mieux.actifs;
    }

    /** Nouvelle zone (vide) créée en jeu ; si l'identifiant existe déjà, la zone existante. */
    static Zone creer(ResourceKey<Level> dim, String id, String nom, @Nullable Double actifs, int niveau) {
        Zone z = zone(dim, id);
        if (z != null) return z;
        if (!(ZONES instanceof HashMap)) ZONES = new HashMap<>(ZONES);
        if (!(INDEX instanceof HashMap)) INDEX = new HashMap<>(INDEX);
        z = new Zone(id, nom, actifs, dim, new LongArrayList(), new ArrayList<>());
        z.niveau = niveau;
        ZONES.computeIfAbsent(dim, k -> new TreeMap<>()).put(id, z);
        INDEX.computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>());
        return z;
    }

    /** Retire une zone créée en jeu (vide). */
    static void supprimer(ResourceKey<Level> dim, String id) {
        Map<String, Zone> m = ZONES.get(dim);
        Zone z = m == null ? null : m.remove(id);
        if (z == null) return;
        Long2ObjectOpenHashMap<Zone> idx = INDEX.get(dim);
        for (int i = 0; i < z.taille() && idx != null; i++) idx.remove(z.position(i), z);
    }

    /** Met le conteneur nom en pos dans la zone z (il quitte son ancienne zone s'il en change). */
    static void placer(Zone z, long pos, String nom) {
        Long2ObjectOpenHashMap<Zone> idx = INDEX.get(z.dimension);
        if (idx == null) return;
        Zone avant = idx.get(pos);
        if (avant != null && avant != z) avant.enlever(pos);
        z.poser(pos, nom);
        idx.put(pos, z);
    }

    /** Retire pos de sa zone. */
    static void enlever(ResourceKey<Level> dim, long pos) {
        Long2ObjectOpenHashMap<Zone> idx = INDEX.get(dim);
        Zone z = idx == null ? null : idx.remove(pos);
        if (z != null) z.enlever(pos);
    }

    /** Index position -> zone d'une dimension (lecture seule). */
    static Long2ObjectOpenHashMap<Zone> index(ResourceKey<Level> dim) {
        Long2ObjectOpenHashMap<Zone> idx = INDEX.get(dim);
        return idx == null ? new Long2ObjectOpenHashMap<>() : idx;
    }
}
