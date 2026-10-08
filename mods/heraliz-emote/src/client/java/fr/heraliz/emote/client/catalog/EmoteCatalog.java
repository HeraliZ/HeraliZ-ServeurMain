package fr.heraliz.emote.client.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.emote.HeralizEmote;
import fr.heraliz.emote.client.EmoteInstaller;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the mod knows about its own emotes beyond the animation: category, props shown in the
 * hands or around the player, particles, and whether held items are hidden meanwhile.
 * Read from {@code assets/heraliz_emote/emotes/catalog.json} (written by tools/build_emotes.py)
 * and looked up by emote UUID, so it also works for an emote another player streams to us.
 */
public final class EmoteCatalog {
    public static final List<String> CATEGORIES = List.of("dance", "music", "party", "social", "sport", "survival");

    private static final Map<UUID, EmoteInfo> BY_UUID = new HashMap<>();
    private static final Map<String, EmoteInfo> BY_ID = new LinkedHashMap<>();
    private static final Map<String, PropModel> MODELS = new HashMap<>();
    private static boolean loaded;

    private EmoteCatalog() {
    }

    public record EmoteInfo(String id, UUID uuid, String category, int icon, boolean hideHeldItems, List<PropSpec> props,
                            List<ParticleSpec> particles, List<SoundSpec> sounds, @Nullable MoveSpec move) {
        public boolean hasProps() {
            return !props.isEmpty() || !particles.isEmpty();
        }

        /**
         * Props stand on the ground around the player (a hoop, DJ decks...): the body must not turn
         * with the camera while it plays, or the whole scene would turn with it.
         */
        public boolean fixedScene() {
            return move == null && props.stream().anyMatch(p -> p.attach.equals("world"));
        }
    }

    @Nullable
    public static EmoteInfo get(@Nullable UUID uuid) {
        load();
        return uuid == null ? null : BY_UUID.get(uuid);
    }

    public static Map<String, EmoteInfo> all() {
        load();
        return Collections.unmodifiableMap(BY_ID);
    }

    @Nullable
    public static PropModel model(String name) {
        load();
        return MODELS.get(name);
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            byte[] data = EmoteInstaller.resource("catalog.json");
            if (data == null) {
                HeralizEmote.LOGGER.warn("No emote catalog in the jar");
                return;
            }
            JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            Gson gson = new Gson();
            if (root.has("models")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("models").entrySet()) {
                    MODELS.put(e.getKey(), PropModel.parse(e.getValue().getAsJsonObject()));
                }
            }
            for (JsonElement el : root.getAsJsonArray("emotes")) {
                JsonObject o = el.getAsJsonObject();
                List<PropSpec> props = new ArrayList<>();
                if (o.has("props")) {
                    for (JsonElement p : o.getAsJsonArray("props")) props.add(PropSpec.parse(p.getAsJsonObject()));
                }
                List<ParticleSpec> particles = new ArrayList<>();
                if (o.has("particles")) {
                    for (JsonElement p : o.getAsJsonArray("particles")) particles.add(gson.fromJson(p, ParticleSpec.class));
                }
                List<SoundSpec> sounds = new ArrayList<>();
                if (o.has("sounds")) {
                    for (JsonElement p : o.getAsJsonArray("sounds")) sounds.add(gson.fromJson(p, SoundSpec.class));
                }
                MoveSpec move = o.has("move") ? gson.fromJson(o.get("move"), MoveSpec.class) : null;
                EmoteInfo info = new EmoteInfo(o.get("id").getAsString(), UUID.fromString(o.get("uuid").getAsString()),
                        o.has("category") ? o.get("category").getAsString() : "dance",
                        o.has("icon") ? o.get("icon").getAsInt() : 0,
                        o.has("hideHeldItems") && o.get("hideHeldItems").getAsBoolean(),
                        List.copyOf(props), List.copyOf(particles), List.copyOf(sounds), move);
                BY_UUID.put(info.uuid(), info);
                BY_ID.put(info.id(), info);
            }
            HeralizEmote.LOGGER.info("Heraliz catalog: {} emotes, {} prop models", BY_ID.size(), MODELS.size());
        } catch (Exception e) {
            HeralizEmote.LOGGER.error("Could not read the emote catalog", e);
        }
    }

    static float[] floats(JsonArray a, int n, float fallback) {
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = a != null && i < a.size() ? a.get(i).getAsFloat() : fallback;
        return out;
    }
}
