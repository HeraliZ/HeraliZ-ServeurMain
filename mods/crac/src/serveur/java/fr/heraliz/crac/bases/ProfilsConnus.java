package fr.heraliz.crac.bases;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import fr.heraliz.crac.Crac;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les joueurs déjà venus sur le serveur, lus dans {@code usercache.json} (relu au plus toutes les 60 s). Sans réseau :
 * {@code getProfileCache().get(nom)} de Minecraft interroge Mojang pour un pseudo inconnu, sur le fil du serveur, et
 * ajoute un faux profil au fichier (mode hors ligne) ; une recherche en boucle pouvait donc geler le serveur.
 */
final class ProfilsConnus {
    private static final long RELIRE_MS = 60_000L;
    private static Map<String, GameProfile> parNom = Map.of();
    private static long lu;

    private ProfilsConnus() {
    }

    static synchronized Optional<GameProfile> parNom(MinecraftServer server, String nom) {
        long now = System.currentTimeMillis();
        if (now - lu > RELIRE_MS) {
            lu = now;
            parNom = lire(server.getServerDirectory().toPath().resolve("usercache.json"));
        }
        return Optional.ofNullable(parNom.get(nom.toLowerCase(Locale.ROOT)));
    }

    private static Map<String, GameProfile> lire(Path fichier) {
        Map<String, GameProfile> m = new HashMap<>();
        try {
            if (!Files.isRegularFile(fichier) || Files.size(fichier) > 16L * 1024 * 1024) return m;
            for (JsonElement e : JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (!o.has("name") || !o.has("uuid")) continue;
                try {
                    String n = o.get("name").getAsString();
                    m.put(n.toLowerCase(Locale.ROOT), new GameProfile(UUID.fromString(o.get("uuid").getAsString()), n));
                } catch (RuntimeException ignore) {
                }
            }
        } catch (Exception ex) {
            Crac.LOGGER.warn("[CRac] usercache.json illisible ({}) : pas de recherche de joueur déconnecté", ex.toString());
        }
        return m;
    }
}
