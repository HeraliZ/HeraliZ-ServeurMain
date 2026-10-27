package com.Spartan.ban;

import com.Spartan.Spartan;
import com.Spartan.util.AsyncIO;
import com.Spartan.util.JsonFiles;
import com.Spartan.util.HardwareId;
import com.Spartan.util.NetUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.UUIDUtil;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Registre des joueurs connus : pseudo, IP et identifiants matériels vus.
 * Sert à bannir un joueur hors ligne (on connaît ses IP et ses PC) et à
 * repérer les comptes secondaires (même PC ou même IP qu'un banni).
 *
 * - Seules les IP publiques sont enregistrées (derrière Docker/Pterodactyl, une
 *   IP interne serait commune à tous les joueurs).
 * - Fichier illisible → mis de côté, jamais écrasé.
 * - Toutes les modifications se font sur le thread serveur ; l'écriture disque
 *   part en asynchrone.
 *
 * Fichier : config/spartan/players.json.
 */
public final class PlayerRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_IPS = 10;
    private static final int MAX_SERIALS = 10;
    private static final long DAY_MS = 24L * 3600 * 1000;

    private static final Map<String, Record> BY_UUID = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static boolean dirty = false;

    private PlayerRegistry() {
    }

    public static final class Record {
        public String uuid;
        public String name;
        public LinkedHashSet<String> ips = new LinkedHashSet<>();
        public LinkedHashSet<String> serials = new LinkedHashSet<>();
        public long firstSeen;
        public long lastSeen;

        /** IP la plus récente (les ensembles sont tenus dans l'ordre d'utilisation). */
        public String lastIp() {
            String last = null;
            for (String ip : ips) {
                last = ip;
            }
            return last;
        }

        /** Copie pour l'écriture en fond (tous les champs : un champ ajouté ici doit l'être aussi dans la copie). */
        Record copie() {
            Record c = new Record();
            c.uuid = uuid;
            c.name = name;
            c.ips = ips == null ? null : new LinkedHashSet<>(ips);
            c.serials = serials == null ? null : new LinkedHashSet<>(serials);
            c.firstSeen = firstSeen;
            c.lastSeen = lastSeen;
            return c;
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("spartan").resolve("players.json");
    }

    /** Chargement + migration de l'ancien format + purge RGPD. */
    public static synchronized void load(int retentionDays, Predicate<String> isBannedUuid) {
        BY_UUID.clear();
        Path f = file();
        boolean corrupted = false;
        String json = null;
        try {
            json = JsonFiles.readOrNull(f);
        } catch (IOException e) {
            JsonFiles.quarantine(f, e);
            corrupted = true;
        }
        if (json != null) {
            try {
                List<Record> list = GSON.fromJson(json, new TypeToken<List<Record>>() {}.getType());
                if (list != null) {
                    for (Record r : list) {
                        if (r != null && r.uuid != null) {
                            if (r.ips == null) {
                                r.ips = new LinkedHashSet<>();
                            }
                            if (r.serials == null) {
                                r.serials = new LinkedHashSet<>();
                            }
                            // Nettoyage des données écrites par d'anciennes versions :
                            // IP internes (Docker) et identifiants génériques inexploitables.
                            r.ips.removeIf(ip -> !NetUtil.isPublicIp(ip));
                            r.serials.removeIf(HardwareId::isPlaceholder);
                            BY_UUID.put(r.uuid, r);
                        }
                    }
                }
            } catch (JsonParseException | IllegalStateException e) {
                JsonFiles.quarantine(f, e);
                BY_UUID.clear();
                corrupted = true;
            }
        } else if (!corrupted) {
            migrateLegacy();
        }

        loaded = true;
        int purged = purge(retentionDays, isBannedUuid);
        Spartan.LOGGER.info("[Spartan] Registre joueurs : {} joueurs ({} purgés après {} jours).",
                BY_UUID.size(), purged, retentionDays);
        saveNow();
    }

    /** Import de l'ancien config/bancomposant/list.json de la 1.0.0. */
    private static void migrateLegacy() {
        Path legacy = FMLPaths.CONFIGDIR.get().resolve("bancomposant").resolve("list.json");
        String json;
        try {
            json = JsonFiles.readOrNull(legacy);
        } catch (IOException e) {
            Spartan.LOGGER.error("[Spartan] Migration : list.json illisible, ignoré", e);
            return;
        }
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonArray arr = GSON.fromJson(json, JsonArray.class);
            if (arr == null) {
                return;
            }
            long now = System.currentTimeMillis();
            int n = 0;
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                if (!o.has("player") || !o.has("cpuSerial")
                        || !o.get("player").isJsonPrimitive() || !o.get("cpuSerial").isJsonPrimitive()) {
                    continue;
                }
                String name = o.get("player").getAsString();
                // Serveur crack : l'UUID d'un compte se déduit de son pseudo (casse comprise).
                UUID uuid = UUIDUtil.createOfflinePlayerUUID(name);
                Record r = getOrCreate(uuid, name, now);
                addBounded(r.serials, o.get("cpuSerial").getAsString(), MAX_SERIALS);
                n++;
            }
            Spartan.LOGGER.info("[Spartan] Migration : {} entrées importées depuis bancomposant/list.json", n);
            JsonFiles.markMigrated(legacy);
        } catch (JsonParseException | IllegalStateException ex) {
            Spartan.LOGGER.error("[Spartan] Migration de list.json impossible (fichier conservé)", ex);
        }
    }

    /** RGPD : retire les joueurs non bannis inactifs depuis trop longtemps. Renvoie le nombre retiré. */
    public static synchronized int purge(int retentionDays, Predicate<String> isBannedUuid) {
        long limit = System.currentTimeMillis() - retentionDays * DAY_MS;
        int purged = 0;
        for (Iterator<Record> it = BY_UUID.values().iterator(); it.hasNext(); ) {
            Record r = it.next();
            if (r.lastSeen < limit && !isBannedUuid.test(r.uuid)) {
                it.remove();
                purged++;
            }
        }
        if (purged > 0) {
            saveNow();
        }
        return purged;
    }

    /** Écrit le registre s'il a changé (appelé périodiquement et à l'arrêt). */
    public static synchronized void flushIfDirty() {
        if (loaded && dirty) {
            saveNow();
        }
    }

    public static synchronized void recordLogin(UUID uuid, String name, String ip) {
        Record r = getOrCreate(uuid, name, System.currentTimeMillis());
        r.name = name;
        if (NetUtil.isPublicIp(ip)) {
            addBounded(r.ips, ip, MAX_IPS);
        }
        dirty = true;
    }

    public static synchronized void recordSerial(UUID uuid, String name, String serial) {
        Record r = getOrCreate(uuid, name, System.currentTimeMillis());
        if (addBounded(r.serials, serial, MAX_SERIALS)) {
            dirty = true;
        }
    }

    /** Tous les comptes dont le pseudo correspond, sans tenir compte de la casse. */
    public static synchronized List<Record> findAllByName(String name) {
        List<Record> out = new ArrayList<>();
        for (Record r : BY_UUID.values()) {
            if (r.name != null && r.name.equalsIgnoreCase(name)) {
                out.add(r);
            }
        }
        return out;
    }

    public static synchronized Record findByUuid(UUID uuid) {
        return BY_UUID.get(uuid.toString());
    }

    /** Autres comptes ayant utilisé un même PC ou une même IP (détection de multi-comptes). */
    public static synchronized List<Record> linkedAccounts(Record r) {
        List<Record> out = new ArrayList<>();
        for (Record o : BY_UUID.values()) {
            if (o == r) {
                continue;
            }
            boolean shared = o.serials.stream().anyMatch(r.serials::contains)
                    || o.ips.stream().anyMatch(r.ips::contains);
            if (shared) {
                out.add(o);
            }
        }
        return out;
    }

    private static Record getOrCreate(UUID uuid, String name, long now) {
        Record r = BY_UUID.computeIfAbsent(uuid.toString(), k -> {
            Record n = new Record();
            n.uuid = k;
            n.name = name;
            n.firstSeen = now;
            return n;
        });
        r.lastSeen = now;
        return r;
    }

    /** Ajoute en gardant les N plus récents (on retire puis remet pour mettre à jour l'ordre). */
    private static boolean addBounded(LinkedHashSet<String> set, String value, int max) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        boolean isNew = !set.remove(v);
        set.add(v);
        while (set.size() > max) {
            set.remove(set.iterator().next());
        }
        return isNew;
    }

    /**
     * Seule une copie des fiches est faite ici (sous le verrou, sur le thread serveur) ; la mise en JSON de tout le
     * registre (des milliers de fiches, écriture « jolie ») et l'écriture partent sur le fil d'écriture de Spartan, dans
     * l'ordre (27/10, perf : c'était fait sur le thread serveur). Même fichier, même format ; vidé à l'arrêt
     * ({@link AsyncIO#flush}).
     */
    private static void saveNow() {
        if (!loaded) {
            return;
        }
        dirty = false;
        List<Record> copie = new ArrayList<>(BY_UUID.size());
        for (Record r : BY_UUID.values()) {
            copie.add(r.copie());
        }
        Path f = file();
        AsyncIO.submit(() -> JsonFiles.write(f, GSON.toJson(copie)));
    }
}
