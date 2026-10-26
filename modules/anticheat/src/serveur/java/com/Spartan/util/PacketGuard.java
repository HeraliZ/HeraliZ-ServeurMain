package com.Spartan.util;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-spam de paquets. Les paquets client->serveur ne doivent être traités
 * qu'une fois (ou en nombre borné) par connexion, sinon un client malveillant
 * peut inonder le serveur (I/O fichier, fausses alertes Discord, chat staff).
 */
public final class PacketGuard {
    private static final Map<UUID, Set<String>> ONCE = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> SERIALS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> REPORTS = new ConcurrentHashMap<>();
    private static final int MAX_SERIALS_PER_CONNECTION = 5;
    private static final int MAX_REPORTS_PER_CONNECTION = 20;

    private PacketGuard() {
    }

    /** true la première fois que ce type de paquet arrive pour ce joueur (par connexion). */
    public static boolean firstTime(UUID uuid, String key) {
        return ONCE.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    /** Ce type de paquet a-t-il déjà été reçu pendant cette connexion ? */
    public static boolean hasSeen(UUID uuid, String key) {
        Set<String> set = ONCE.get(uuid);
        return set != null && set.contains(key);
    }

    /** Accepte un serial s'il est nouveau et sous le plafond par connexion. */
    public static boolean acceptSerial(UUID uuid, String serial) {
        return acceptBounded(SERIALS, uuid, serial, MAX_SERIALS_PER_CONNECTION);
    }

    /** Accepte un signalement client s'il est nouveau et sous le plafond par connexion. */
    public static boolean allowReport(UUID uuid, String key) {
        return acceptBounded(REPORTS, uuid, key, MAX_REPORTS_PER_CONNECTION);
    }

    private static boolean acceptBounded(Map<UUID, Set<String>> map, UUID uuid, String key, int max) {
        Set<String> set = map.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        if (set.contains(key) || set.size() >= max) {
            return false;
        }
        set.add(key);
        return true;
    }

    public static void clear(UUID uuid) {
        ONCE.remove(uuid);
        SERIALS.remove(uuid);
        REPORTS.remove(uuid);
    }
}
