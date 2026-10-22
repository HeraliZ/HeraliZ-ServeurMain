package fr.heraliz.emote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les emotes HeraliZ connues des deux côtés : {@code data/heraliz_emote/liste.json} des ressources communes, écrit par
 * {@code outils/liste_emotes.py} d'après le catalogue du jar joueur (le serveur n'a pas les fichiers des emotes). Seules
 * ces emotes peuvent être débloquées (caisse des emotes, staff) ; toute autre emote reste verrouillée en survie.
 */
public final class ListeEmotes {
    /** accessoires : l'emote a des objets 3D ou des particules (plus rare dans la caisse des emotes). */
    public record Emote(String id, UUID uuid, String nom, boolean accessoires) {
    }

    private static final Map<UUID, Emote> PAR_UUID = new LinkedHashMap<>();
    private static final Map<String, Emote> PAR_ID = new LinkedHashMap<>();
    private static boolean lue;

    private ListeEmotes() {
    }

    public static synchronized List<Emote> toutes() {
        lire();
        return List.copyOf(PAR_UUID.values());
    }

    @Nullable
    public static synchronized Emote de(@Nullable UUID uuid) {
        lire();
        return uuid == null ? null : PAR_UUID.get(uuid);
    }

    @Nullable
    public static synchronized Emote parId(String id) {
        lire();
        return PAR_ID.get(id);
    }

    public static synchronized Map<String, Emote> parId() {
        lire();
        return Collections.unmodifiableMap(PAR_ID);
    }

    private static void lire() {
        if (lue) return;
        lue = true;
        try (InputStream in = ListeEmotes.class.getResourceAsStream("/data/heraliz_emote/liste.json")) {
            if (in == null) {
                HeralizEmote.LOGGER.warn("[emote] liste des emotes absente du jar");
                return;
            }
            JsonObject racine = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement el : racine.getAsJsonArray("emotes")) {
                JsonObject o = el.getAsJsonObject();
                Emote e = new Emote(o.get("id").getAsString(), UUID.fromString(o.get("uuid").getAsString()), o.get("nom").getAsString(),
                        o.has("accessoires") && o.get("accessoires").getAsBoolean());
                PAR_UUID.put(e.uuid(), e);
                PAR_ID.put(e.id(), e);
            }
        } catch (Exception ex) {
            HeralizEmote.LOGGER.error("[emote] liste des emotes illisible", ex);
        }
    }
}
