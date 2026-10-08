package com.caracrea.client;

import com.caracrea.character.Appearance;
import com.caracrea.character.AppearanceRandomizer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What every player looks like, as last told by the server. Players the
 * server has not described yet get the same UUID-derived default the
 * server assigns, so nobody ever flashes a Steve skin.
 */
public final class ClientAppearances {
    private static final Map<UUID, Appearance> SYNCED = new HashMap<>();
    private static final Map<UUID, Appearance> DEFAULTS = new HashMap<>();
    /** Appearances of preview dummies (the creator screen), keyed by their own UUID. */
    private static final Map<UUID, Appearance> PREVIEWS = new HashMap<>();

    private ClientAppearances() {
    }

    public static Appearance resolve(UUID uuid) {
        Appearance appearance = SYNCED.get(uuid);
        if (appearance != null) {
            return appearance;
        }
        appearance = PREVIEWS.get(uuid);
        if (appearance != null) {
            return appearance;
        }
        return DEFAULTS.computeIfAbsent(uuid, AppearanceRandomizer::defaultFor);
    }

    /** Vanilla model name ("default" or "slim") for the body type of a player. */
    public static String modelName(UUID uuid) {
        return resolve(uuid).bodyType().modelName();
    }

    public static void put(UUID uuid, Appearance appearance) {
        Appearance previous = SYNCED.put(uuid, appearance);
        if (!appearance.equals(previous)) {
            CompositeSkins.invalidate(uuid);
        }
    }

    public static void setPreview(UUID uuid, Appearance appearance) {
        Appearance previous = PREVIEWS.put(uuid, appearance);
        if (!appearance.equals(previous)) {
            CompositeSkins.invalidate(uuid);
        }
    }

    public static void removePreview(UUID uuid) {
        PREVIEWS.remove(uuid);
        CompositeSkins.release(uuid);
    }

    public static boolean isPreview(UUID uuid) {
        return PREVIEWS.containsKey(uuid);
    }

    public static void clear() {
        SYNCED.clear();
        DEFAULTS.clear();
        PREVIEWS.clear();
    }
}
