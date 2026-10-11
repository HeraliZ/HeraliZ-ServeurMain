package com.caracrea.server;

import com.caracrea.CaraCrea;
import com.caracrea.api.voice.VoiceEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Operators' voice mutes, with an optional end, kept in {@code <world>/caracrea/voix_sourdines.json} so they last
 * through deaths, reconnections and restarts, and work for offline players. A muted player still hears; their voice is
 * dropped by the server. The 1.0.1 flag ({@code caracrea:voice_muted} in the player's data) becomes a mute without end
 * at the next connection.
 */
public final class VoiceMutes {
    /** One mute: {@code until} in epoch milliseconds, 0 for no end. */
    public record Mute(UUID player, String name, long until, String reason, String by, long since) {
        public boolean expired(long now) {
            return until > 0 && now >= until;
        }
    }

    private static final String OLD_FLAG = "caracrea:voice_muted";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Mute> MUTES = new ConcurrentHashMap<>();
    private static volatile MinecraftServer server;
    private static long nextCheck;

    private VoiceMutes() {
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("caracrea").resolve("voix_sourdines.json");
    }

    /** Server start: reads the file. */
    static void load(MinecraftServer started) {
        server = started;
        MUTES.clear();
        Path file = file(started);
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            JsonArray list = root == null ? null : root.getAsJsonArray("sourdines");
            if (list != null) {
                for (JsonElement element : list) {
                    JsonObject o = element.getAsJsonObject();
                    UUID id = UUID.fromString(o.get("uuid").getAsString());
                    MUTES.put(id, new Mute(id, string(o, "nom"), o.has("fin") ? o.get("fin").getAsLong() : 0, string(o, "raison"),
                            string(o, "par"), o.has("depuis") ? o.get("depuis").getAsLong() : 0));
                }
            }
            CaraCrea.LOGGER.info("Voice chat: {} mute(s) loaded", MUTES.size());
        } catch (IOException | RuntimeException e) {
            CaraCrea.LOGGER.warn("Voice chat: could not read {}", file, e);
        }
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    static void unload() {
        server = null;
        MUTES.clear();
    }

    private static void save() {
        MinecraftServer current = server;
        if (current == null) {
            return;
        }
        JsonArray list = new JsonArray();
        for (Mute mute : sorted()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", mute.player().toString());
            o.addProperty("nom", mute.name());
            o.addProperty("fin", mute.until());
            o.addProperty("raison", mute.reason());
            o.addProperty("par", mute.by());
            o.addProperty("depuis", mute.since());
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("sourdines", list);
        Path file = file(current);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("voix_sourdines.json.tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            CaraCrea.LOGGER.warn("Voice chat: could not save {}", file, e);
        }
    }

    /** Mutes a player (online or not); {@code duration} null for no end. Server thread. */
    public static void mute(UUID player, @Nullable String name, @Nullable Duration duration, @Nullable String reason, @Nullable String by) {
        long now = System.currentTimeMillis();
        long until = duration == null || duration.isZero() || duration.isNegative() ? 0 : now + duration.toMillis();
        ServerPlayer online = online(player);
        String known = name != null ? name : online != null ? online.getGameProfile().getName() : MUTES.containsKey(player)
                ? MUTES.get(player).name() : player.toString();
        MUTES.put(player, new Mute(player, known, until, reason == null ? "" : reason, by == null ? "" : by, now));
        save();
        if (online != null) {
            online.sendSystemMessage(until == 0
                    ? Component.translatable("caracrea.voice.admin_muted").withStyle(ChatFormatting.RED)
                    : Component.translatable("caracrea.voice.admin_muted_for", remainingText(until - now)).withStyle(ChatFormatting.RED));
            if (reason != null && !reason.isBlank()) {
                online.sendSystemMessage(Component.translatable("caracrea.voice.reason", reason).withStyle(ChatFormatting.GRAY));
            }
        }
        VoiceServer.rosterChanged();
        MinecraftForge.EVENT_BUS.post(new VoiceEvent.MuteChanged(player, true, until, reason));
        CaraCrea.LOGGER.info("Voice chat: {} muted{} by {}{}", known, until == 0 ? "" : " for " + remaining(until - now), by,
                reason == null || reason.isBlank() ? "" : " (" + reason + ")");
    }

    /** Lifts a mute. Returns false if the player was not muted. Server thread. */
    public static boolean unmute(UUID player) {
        Mute removed = MUTES.remove(player);
        if (removed == null) {
            return false;
        }
        save();
        ServerPlayer online = online(player);
        if (online != null) {
            online.sendSystemMessage(Component.translatable("caracrea.voice.admin_unmuted").withStyle(ChatFormatting.GREEN));
        }
        VoiceServer.rosterChanged();
        MinecraftForge.EVENT_BUS.post(new VoiceEvent.MuteChanged(player, false, 0, null));
        return true;
    }

    /** Whether the player is muted now (any thread). */
    public static boolean isMuted(UUID player) {
        Mute mute = MUTES.get(player);
        return mute != null && !mute.expired(System.currentTimeMillis());
    }

    @Nullable
    public static Mute get(UUID player) {
        return MUTES.get(player);
    }

    /** Current mutes, soonest end first, those without end last. */
    public static List<Mute> sorted() {
        List<Mute> list = new ArrayList<>(MUTES.values());
        list.sort(Comparator.comparingLong((Mute m) -> m.until() == 0 ? Long.MAX_VALUE : m.until()).thenComparing(Mute::name));
        return list;
    }

    /** Server thread, every tick: lifts the mutes that ended (once a second). */
    static void tick() {
        long now = System.currentTimeMillis();
        if (now < nextCheck) {
            return;
        }
        nextCheck = now + 1000;
        for (Mute mute : List.copyOf(MUTES.values())) {
            if (mute.expired(now)) {
                unmute(mute.player());
                CaraCrea.LOGGER.info("Voice chat: mute of {} ended", mute.name());
            }
        }
    }

    /** At connection: the 1.0.1 flag becomes a mute without end. */
    static void migrate(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(OLD_FLAG)) {
            persisted.remove(OLD_FLAG);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            if (!MUTES.containsKey(player.getUUID())) {
                mute(player.getUUID(), player.getGameProfile().getName(), null, "1.0.1", "CaraCrea");
            }
        }
    }

    @Nullable
    private static ServerPlayer online(UUID player) {
        MinecraftServer current = server;
        return current == null ? null : current.getPlayerList().getPlayer(player);
    }

    /** "2 h 05", "12 min", "40 s". */
    public static String remaining(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long days = s / 86400;
        long hours = s / 3600 % 24;
        long minutes = s / 60 % 60;
        if (days > 0) {
            return days + " j " + hours + " h";
        }
        if (hours > 0) {
            return hours + " h " + String.format("%02d", minutes);
        }
        if (minutes > 0) {
            return minutes + " min";
        }
        return s + " s";
    }

    /** {@link #remaining} in the player's language (French for the console and players without the client jar). */
    public static Component remainingText(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long days = s / 86400;
        long hours = s / 3600 % 24;
        long minutes = s / 60 % 60;
        if (days > 0) {
            return Component.translatableWithFallback("caracrea.voice.duration.days", "%s j %s h", String.valueOf(days), String.valueOf(hours));
        }
        if (hours > 0) {
            return Component.translatableWithFallback("caracrea.voice.duration.hours", "%s h %s", String.valueOf(hours),
                    String.format(java.util.Locale.ROOT, "%02d", minutes));
        }
        if (minutes > 0) {
            return Component.translatableWithFallback("caracrea.voice.duration.minutes", "%s min", String.valueOf(minutes));
        }
        return Component.translatableWithFallback("caracrea.voice.duration.seconds", "%s s", String.valueOf(s));
    }

    /** "30s", "10m", "2h", "3j" / "3d", "1sem" / "1w"; null when it is not a duration. */
    @Nullable
    public static Duration parse(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(s|sec|m|min|h|j|d|sem|w)").matcher(t);
        if (!m.matches()) {
            return null;
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "s", "sec" -> Duration.ofSeconds(n);
            case "m", "min" -> Duration.ofMinutes(n);
            case "h" -> Duration.ofHours(n);
            case "j", "d" -> Duration.ofDays(n);
            default -> Duration.ofDays(7 * n);
        };
    }
}
