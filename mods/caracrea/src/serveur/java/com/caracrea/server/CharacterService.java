package com.caracrea.server;

import com.caracrea.CaraCrea;
import com.caracrea.CaraCreaConfig;
import com.caracrea.character.Appearance;
import com.caracrea.character.AppearanceRandomizer;
import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.network.CaraCreaNetwork.CloseCreator;
import com.caracrea.network.CaraCreaNetwork.Entry;
import com.caracrea.network.CaraCreaNetwork.OpenCreator;
import com.caracrea.network.CaraCreaNetwork.SyncAppearances;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Server-side logic: hands out appearances on login, decides who may edit,
 * validates and broadcasts saves. Everything here is O(players) with tiny
 * payloads, so it costs the server nothing noticeable.
 *
 * <p>The creator never gets in the way of a player who is busy (in a match, downed, dead or spectator,
 * see {@link Busy}): it waits until the player is free, and an open creator is closed if the player
 * becomes busy (the first-time creator then opens again later). A player who has not confirmed a look
 * yet gets the creator again at the next login.
 */
public final class CharacterService {
    /** Hard floor between two free saves of one player, whatever the config: each save repaints on every client. */
    private static final long MIN_SAVE_INTERVAL_MS = 2000;
    private static final long MIN_REQUEST_INTERVAL_MS = 1000;
    private static final int CHECK_EVERY_TICKS = 10;

    /**
     * Players allowed to save once because the creator was opened for them (value: exempt from the edit cooldown,
     * when the creator was imposed: first time, or opened by an operator).
     */
    private static final Map<UUID, Boolean> PENDING_EDITS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SAVE = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    /** Creators sent and not confirmed yet (value: first time), watched in case the player becomes busy. */
    private static final Map<UUID, Boolean> OPEN = new HashMap<>();
    /** Creators to open as soon as the player is free (value: first time). */
    private static final Map<UUID, Boolean> WAITING = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static int timer;

    private CharacterService() {
    }

    /** Called when a player joins: gives first-timers a character and syncs everyone. */
    public static void onLogin(ServerPlayer player) {
        if (AppearanceStore.get(player) == null) {
            AppearanceStore.set(player, AppearanceStore.defaultFor(player.getUUID()));
            AppearanceStore.setChosen(player, false);
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // The newcomer learns everyone's look; everyone learns the newcomer's.
        List<Entry> all = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            all.add(new Entry(other.getUUID(), current(other)));
        }
        CaraCreaNetwork.sendTo(player, new SyncAppearances(all));
        CaraCreaNetwork.sendToAll(SyncAppearances.of(player.getUUID(), current(player)));
        if (!AppearanceStore.hasChosen(player) && CaraCreaConfig.SERVER.openOnFirstJoin.get()) {
            openWhenFree(player, true);
        }
    }

    public static void onLogout(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PENDING_EDITS.remove(uuid);
        LAST_SAVE.remove(uuid);
        LAST_REQUEST.remove(uuid);
        OPEN.remove(uuid);
        WAITING.remove(uuid);
    }

    /** Every tick: opens the creators that were waiting, closes those of players who became busy. */
    public static void tick(MinecraftServer server) {
        if (++timer < CHECK_EVERY_TICKS || OPEN.isEmpty() && WAITING.isEmpty()) {
            return;
        }
        timer = 0;
        for (Iterator<Map.Entry<UUID, Boolean>> it = OPEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Boolean> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
            } else if (Busy.busy(player)) {
                it.remove();
                PENDING_EDITS.remove(e.getKey());
                CaraCreaNetwork.sendTo(player, new CloseCreator());
                player.displayClientMessage(Component.translatable("caracrea.message.closed_busy"), true);
                if (e.getValue() && !AppearanceStore.hasChosen(player)) {
                    WAITING.put(e.getKey(), true);
                }
            }
        }
        for (Iterator<Map.Entry<UUID, Boolean>> it = WAITING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Boolean> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
            } else if (!Busy.busy(player)) {
                it.remove();
                open(player, e.getValue());
            }
        }
    }

    /** Whether the creator would be in the way right now (match, downed, dead, spectator). */
    public static boolean busy(ServerPlayer player) {
        return Busy.busy(player);
    }

    /** The appearance of an online player (never null). */
    public static Appearance current(ServerPlayer player) {
        Appearance appearance = AppearanceStore.get(player);
        return appearance != null ? appearance : AppearanceStore.defaultFor(player.getUUID());
    }

    /** Opens the creator now if the player is free, otherwise as soon as they are. Returns true if opened now. */
    public static boolean openWhenFree(ServerPlayer player, boolean firstTime) {
        if (Busy.busy(player)) {
            WAITING.merge(player.getUUID(), firstTime, Boolean::logicalOr);
            return false;
        }
        open(player, firstTime);
        return true;
    }

    /** Opens the creator on a player's screen and allows the save that follows (operators' and first-time opens). */
    public static void open(ServerPlayer player, boolean firstTime) {
        open(player, firstTime, true);
    }

    /** The player opens their own creator (/caracrea): the edit cooldown applies to the save. */
    public static void openSelf(ServerPlayer player) {
        open(player, false, false);
    }

    private static void open(ServerPlayer player, boolean firstTime, boolean imposed) {
        PENDING_EDITS.merge(player.getUUID(), imposed || firstTime, Boolean::logicalOr);
        OPEN.put(player.getUUID(), firstTime);
        CaraCreaNetwork.sendTo(player, new OpenCreator(firstTime));
    }

    /** The player asked to open the creator (key or /caracrea). Returns false (with a message) when refused. */
    public static boolean requestOpen(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.put(player.getUUID(), now);
        if (last != null && now - last < MIN_REQUEST_INTERVAL_MS) {
            return false;
        }
        if (!CaraCreaConfig.SERVER.freeEdit.get() && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("caracrea.message.not_allowed"), true);
            return false;
        }
        if (Busy.busy(player)) {
            player.displayClientMessage(Component.translatable("caracrea.message.busy"), true);
            return false;
        }
        open(player, false, false);
        return true;
    }

    /** A player confirmed a look in the creator. */
    public static void save(ServerPlayer player, Appearance appearance) {
        UUID uuid = player.getUUID();
        Boolean exempt = PENDING_EDITS.remove(uuid);
        boolean pending = exempt != null;
        Boolean wasOpen = OPEN.remove(uuid);
        WAITING.remove(uuid);
        if (!pending && !CaraCreaConfig.SERVER.freeEdit.get() && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("caracrea.message.not_allowed"), true);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_SAVE.get(uuid);
        if (!pending || !exempt) {
            // 1.0.0 skipped the cooldown whenever the creator was open, so it never applied to normal edits. It now
            // does, except for imposed creators (first time, operators); and in any case a hard floor, so a modified
            // client cannot make every other client repaint its skin many times a second.
            long cooldown = Math.max(MIN_SAVE_INTERVAL_MS, CaraCreaConfig.SERVER.editCooldownSeconds.get() * 1000L);
            if (last != null && now - last < cooldown) {
                long wait = (cooldown - (now - last) + 999) / 1000;
                player.displayClientMessage(Component.translatable("caracrea.message.cooldown", wait), true);
                return;
            }
        }
        LAST_SAVE.put(uuid, now);
        AppearanceStore.setChosen(player, true);
        if (appearance.equals(AppearanceStore.get(player))) {
            // Same look: nothing to repaint anywhere.
            player.displayClientMessage(Component.translatable("caracrea.message.saved"), true);
            return;
        }
        apply(player, appearance);
        player.displayClientMessage(Component.translatable("caracrea.message.saved"), true);
        if (wasOpen != null && wasOpen) {
            CaraCrea.LOGGER.info("{} created their character", player.getGameProfile().getName());
        }
    }

    /** Stores a new look and shows it to everyone. */
    public static void apply(ServerPlayer player, Appearance appearance) {
        AppearanceStore.set(player, appearance);
        CaraCreaNetwork.sendToAll(SyncAppearances.of(player.getUUID(), appearance));
        CaraCrea.LOGGER.debug("{} now looks like {}", player.getGameProfile().getName(), appearance);
    }

    /** A new random character for a player (operators' /caracrea random and reset). */
    public static Appearance randomize(ServerPlayer player) {
        Appearance appearance = AppearanceRandomizer.random(RANDOM);
        apply(player, appearance);
        return appearance;
    }

    /** Operators' /caracrea reset: random look, and the first-time creator (now, or once the player is free). */
    public static void reset(ServerPlayer player) {
        randomize(player);
        AppearanceStore.setChosen(player, false);
        openWhenFree(player, true);
    }
}
