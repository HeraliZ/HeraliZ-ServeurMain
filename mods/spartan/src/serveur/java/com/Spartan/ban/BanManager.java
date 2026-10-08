package com.Spartan.ban;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import com.Spartan.util.HardwareId;
import com.Spartan.util.JsonFiles;
import com.Spartan.util.NetUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Système de ban à trois couches :
 * 1. COMPTE : liste de bans vanilla → refusé dès l'écran de connexion, même hors ligne ;
 * 2. IP     : la dernière IP publique du joueur, durée plafonnée (ipBanHours) ;
 *             liste vanilla pour l'IPv4, vérification Spartan à la connexion pour tout ;
 * 3. PC     : identifiants matériels, vérifiés quand le client les envoie.
 *
 * Spartan garde un historique complet (qui, quand, pourquoi, levé par qui) dans
 * config/spartan/bans.json. Un fichier illisible est mis de côté, jamais écrasé.
 */
public final class BanManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SOURCE = "Spartan";
    private static final long DAY_MS = 24L * 3600 * 1000;

    private static final List<BanRecord> BANS = new ArrayList<>();
    private static boolean loaded = false;

    private BanManager() {
    }

    public static final class BanRecord {
        public String uuid;
        public String name;
        public LinkedHashSet<String> ips = new LinkedHashSet<>();     // IP bannie (publique, la plus récente)
        public LinkedHashSet<String> serials = new LinkedHashSet<>();
        public String reason;
        public String by;
        public long created;
        public long expires;   // 0 = définitif
        public long ipExpires; // fin du ban d'IP (0 = pas de ban d'IP)
        public long liftedAt;  // 0 = toujours actif
        public String liftedBy;

        public boolean isActive(long now) {
            return liftedAt == 0 && (expires == 0 || now < expires);
        }

        public boolean isIpBanActive(long now) {
            return isActive(now) && ipExpires > now;
        }

        public String describeExpiry() {
            return expires == 0 ? "définitif"
                    : "jusqu'au " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(expires));
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("spartan").resolve("bans.json");
    }

    public static synchronized void load(int retentionDays) {
        BANS.clear();
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
                List<BanRecord> list = GSON.fromJson(json, new TypeToken<List<BanRecord>>() {}.getType());
                if (list != null) {
                    for (BanRecord r : list) {
                        if (r == null) {
                            continue;
                        }
                        if (r.ips == null) {
                            r.ips = new LinkedHashSet<>();
                        }
                        if (r.serials == null) {
                            r.serials = new LinkedHashSet<>();
                        }
                        // Nettoyage des données d'anciennes versions : IP internes / identifiants génériques.
                        r.ips.removeIf(ip -> !NetUtil.isPublicIp(ip));
                        r.serials.removeIf(HardwareId::isPlaceholder);
                        BANS.add(r);
                    }
                }
            } catch (JsonParseException | IllegalStateException e) {
                JsonFiles.quarantine(f, e);
                BANS.clear();
                corrupted = true;
            }
        } else if (!corrupted) {
            migrateLegacy();
        }

        loaded = true;
        long now = System.currentTimeMillis();
        int stripped = anonymize(retentionDays);
        Spartan.LOGGER.info("[Spartan] Bans : {} actifs / {} au total ({} anciens bans anonymisés).",
                BANS.stream().filter(b -> b.isActive(now)).count(), BANS.size(), stripped);
        save();
    }

    /**
     * RGPD : efface IP et identifiants matériels des bans terminés depuis plus de
     * retentionDays (l'historique — qui, quand, pourquoi — reste). Renvoie le nombre traité.
     */
    public static synchronized int anonymize(int retentionDays) {
        long now = System.currentTimeMillis();
        long limit = now - retentionDays * DAY_MS;
        int stripped = 0;
        for (BanRecord r : BANS) {
            if (r.isActive(now) || (r.ips.isEmpty() && r.serials.isEmpty())) {
                continue;
            }
            long end = r.liftedAt != 0 ? r.liftedAt : r.expires;
            if (end < limit) {
                r.ips.clear();
                r.serials.clear();
                stripped++;
            }
        }
        if (stripped > 0) {
            save();
        }
        return stripped;
    }

    /** Import des serials bannis par la 1.0.0 (config/bancomposant/banlist.json). */
    private static void migrateLegacy() {
        Path legacy = FMLPaths.CONFIGDIR.get().resolve("bancomposant").resolve("banlist.json");
        String json;
        try {
            json = JsonFiles.readOrNull(legacy);
        } catch (IOException e) {
            Spartan.LOGGER.error("[Spartan] Migration : banlist.json illisible, ignoré", e);
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
                if (!e.isJsonPrimitive()) {
                    continue;
                }
                BanRecord r = new BanRecord();
                r.name = "(ban Spartan 1.0)";
                r.serials.add(norm(e.getAsString()));
                r.reason = "Ban matériel importé de Spartan 1.0";
                r.by = SOURCE;
                r.created = now;
                BANS.add(r);
                n++;
            }
            Spartan.LOGGER.info("[Spartan] Migration : {} serial(s) banni(s) importé(s) depuis bancomposant/banlist.json", n);
            JsonFiles.markMigrated(legacy);
        } catch (JsonParseException | IllegalStateException ex) {
            Spartan.LOGGER.error("[Spartan] Migration de banlist.json impossible (fichier conservé)", ex);
        }
    }

    /**
     * Bannit un joueur (en ligne ou non) : compte + dernière IP publique + PC connus.
     * Un ban actif existant pour ce compte est remplacé (le dernier ban fait foi).
     * @param durationMs 0 = définitif
     */
    public static synchronized BanRecord ban(MinecraftServer server, PlayerRegistry.Record target,
                                             long durationMs, String reason, String by) {
        long now = System.currentTimeMillis();
        BanRecord r = new BanRecord();
        r.uuid = target.uuid;
        r.name = target.name;
        r.serials.addAll(target.serials);
        r.reason = reason;
        r.by = by;
        r.created = now;
        r.expires = durationMs == 0 ? 0 : now + durationMs;

        // Remplacement d'un ban actif du même compte (sinon deux bans incohérents).
        for (BanRecord o : BANS) {
            if (o.isActive(now) && r.uuid.equals(o.uuid)) {
                o.liftedAt = now;
                o.liftedBy = by + " (remplacé par un nouveau ban)";
                r.serials.addAll(o.serials);
            }
        }

        // IP : uniquement la plus récente, et seulement si elle est publique.
        String ip = target.lastIp();
        int ipHours = SpartanConfig.IP_BAN_HOURS.get();
        if (ipHours > 0 && NetUtil.isPublicIp(ip)) {
            long cap = now + ipHours * 3_600_000L;
            r.ips.add(ip);
            r.ipExpires = r.expires == 0 ? cap : Math.min(r.expires, cap);
        }
        BANS.add(r);
        save();

        // 1. Compte : liste vanilla, refus dès la connexion.
        GameProfile profile = new GameProfile(UUID.fromString(r.uuid), r.name);
        Date expires = r.expires == 0 ? null : new Date(r.expires);
        server.getPlayerList().getBans().add(
                new UserBanListEntry(profile, new Date(now), SOURCE + " (" + by + ")", expires, reason));

        // 2. IP : liste vanilla pour l'IPv4 (la liste vanilla ne reconnaît pas l'IPv6 :
        //    Spartan vérifie lui-même à la connexion, voir findIpBan).
        for (String bannedIp : r.ips) {
            if (NetUtil.isIpv4(bannedIp)) {
                server.getPlayerList().getIpBans().add(new IpBanListEntry(bannedIp, new Date(now),
                        SOURCE + " (" + by + ")", new Date(r.ipExpires), reason));
            }
        }

        // Expulsion immédiate s'il est en ligne.
        ServerPlayer online = server.getPlayerList().getPlayer(profile.getId());
        if (online != null) {
            online.connection.disconnect(kickMessage(r));
        }
        return r;
    }

    /**
     * Lève tous les bans actifs correspondant au pseudo, à l'UUID ou à un serial
     * (pour les bans importés de la 1.0, sans compte). Renvoie le nombre levé.
     */
    public static synchronized int unban(MinecraftServer server, String nameUuidOrSerial, String by) {
        long now = System.currentTimeMillis();
        List<BanRecord> lifted = new ArrayList<>();
        for (BanRecord r : BANS) {
            if (r.isActive(now) && matches(r, nameUuidOrSerial)) {
                r.liftedAt = now;
                r.liftedBy = by;
                lifted.add(r);
                if (r.uuid != null) {
                    server.getPlayerList().getBans().remove(new GameProfile(UUID.fromString(r.uuid), r.name));
                }
            }
        }
        // Une IP n'est retirée de la liste vanilla que si c'est Spartan qui l'a bannie
        // et qu'aucun autre ban actif ne la couvre encore.
        for (BanRecord r : lifted) {
            for (String ip : r.ips) {
                boolean stillNeeded = BANS.stream().anyMatch(o -> o.isIpBanActive(now) && o.ips.contains(ip));
                IpBanListEntry entry = server.getPlayerList().getIpBans().get(ip);
                if (!stillNeeded && entry != null && entry.getSource() != null && entry.getSource().startsWith(SOURCE)) {
                    server.getPlayerList().getIpBans().remove(ip);
                }
            }
        }
        if (!lifted.isEmpty()) {
            save();
        }
        return lifted.size();
    }

    /** Ban actif portant sur ce PC, ou null. */
    public static synchronized BanRecord findActiveBySerial(String serial) {
        long now = System.currentTimeMillis();
        String s = norm(serial);
        for (BanRecord r : BANS) {
            if (r.isActive(now) && r.serials.contains(s)) {
                return r;
            }
        }
        return null;
    }

    /** Ban d'IP en cours sur cette adresse (→ expulsion), ou null. */
    public static synchronized BanRecord findIpBan(String ip) {
        if (!NetUtil.isPublicIp(ip)) {
            return null;
        }
        long now = System.currentTimeMillis();
        String s = norm(ip);
        for (BanRecord r : BANS) {
            if (r.isIpBanActive(now) && r.ips.contains(s)) {
                return r;
            }
        }
        return null;
    }

    /** Ban actif (même si le ban d'IP est terminé) portant sur cette IP (→ simple alerte), ou null. */
    public static synchronized BanRecord findActiveByIp(String ip) {
        if (!NetUtil.isPublicIp(ip)) {
            return null;
        }
        long now = System.currentTimeMillis();
        String s = norm(ip);
        for (BanRecord r : BANS) {
            if (r.isActive(now) && r.ips.contains(s)) {
                return r;
            }
        }
        return null;
    }

    public static synchronized boolean isBannedUuid(String uuid) {
        long now = System.currentTimeMillis();
        return BANS.stream().anyMatch(r -> r.isActive(now) && uuid.equals(r.uuid));
    }

    /** Historique des bans (actifs et levés) par pseudo, UUID ou serial. */
    public static synchronized List<BanRecord> history(String nameUuidOrSerial) {
        List<BanRecord> out = new ArrayList<>();
        for (BanRecord r : BANS) {
            if (matches(r, nameUuidOrSerial)) {
                out.add(r);
            }
        }
        return out;
    }

    public static Component kickMessage(BanRecord r) {
        return Component.literal("Vous êtes banni de ce serveur.\n\nRaison : " + r.reason
                + "\nDurée : " + r.describeExpiry());
    }

    private static boolean matches(BanRecord r, String key) {
        String k = norm(key);
        // Nom sensible à la casse : « Steve » et « steve » sont deux comptes distincts en crack.
        return (r.name != null && r.name.equals(key))
                || (r.uuid != null && r.uuid.equalsIgnoreCase(key))
                || r.serials.contains(k);
    }

    private static String norm(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static void save() {
        if (!loaded) {
            return;
        }
        JsonFiles.writeAsync(file(), GSON.toJson(BANS));
    }
}
