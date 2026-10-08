package com.caracrea.client.voice;

import com.caracrea.CaraCrea;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The volume of each player's voice for this listener (0 to 2, 0 = muted),
 * kept in {@code config/caracrea-voice-volumes.json} so it lasts across
 * sessions and servers. Read by the playback thread, changed by the screen.
 */
public final class VoiceVolumes {
    private static final Map<UUID, Float> VOLUMES = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    private static boolean loaded;

    private VoiceVolumes() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("caracrea-voice-volumes.json");
    }

    public static float get(UUID player) {
        load();
        return VOLUMES.getOrDefault(player, 1F);
    }

    public static void set(UUID player, float volume) {
        load();
        volume = Math.max(0, Math.min(2, volume));
        if (Math.abs(volume - 1) < 0.001F) {
            VOLUMES.remove(player);
        } else {
            VOLUMES.put(player, volume);
        }
    }

    /** Writes the volumes (after the screen closes). */
    public static synchronized void save() {
        JsonObject json = new JsonObject();
        VOLUMES.forEach((player, volume) -> json.addProperty(player.toString(), volume));
        try (Writer writer = Files.newBufferedWriter(file())) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            CaraCrea.LOGGER.warn("Could not save the voice volumes", e);
        }
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                json.entrySet().forEach(e -> {
                    try {
                        VOLUMES.put(UUID.fromString(e.getKey()), e.getValue().getAsFloat());
                    } catch (RuntimeException ignored) {
                        // A bad line: skip it.
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            CaraCrea.LOGGER.warn("Could not read the voice volumes", e);
        }
    }
}
