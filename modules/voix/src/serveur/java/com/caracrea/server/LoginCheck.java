package com.caracrea.server;

import com.caracrea.CaraCrea;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Whether a player has logged in on AuthMe (cracked HeraliZ server: /register then /login), read through the Bukkit side
 * of the hybrid server (Mohist) by reflection only. The first-join creator waits for it: before /login the player cannot
 * type anything else, and AuthMe kicks them after its timeout. Fail-open: no Bukkit, no AuthMe, premium mode or an
 * unreachable API all count as logged in (the creator must never be blocked for good). Server thread.
 */
final class LoginCheck {
    private static boolean looked;
    private static Method getPlayer;
    private static Object api;
    private static Method isAuthenticated;
    private static boolean warned;

    private LoginCheck() {
    }

    static boolean loggedIn(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!server.isDedicatedServer() || server.usesAuthentication()) {
            return true;
        }
        try {
            if (!looked) {
                looked = true;
                hook();
            }
            if (isAuthenticated == null) {
                return true;
            }
            Object bukkitPlayer = getPlayer.invoke(null, player.getUUID());
            return bukkitPlayer == null || Boolean.TRUE.equals(isAuthenticated.invoke(api, bukkitPlayer));
        } catch (Throwable e) {
            isAuthenticated = null;
            if (!warned) {
                warned = true;
                CaraCrea.LOGGER.warn("AuthMe cannot be read: the first-join creator no longer waits for /login ({})", e.toString());
            }
            return true;
        }
    }

    private static void hook() {
        Class<?> bukkit = null;
        for (ClassLoader cl : new ClassLoader[]{MinecraftServer.class.getClassLoader(), LoginCheck.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()}) {
            if (cl == null) {
                continue;
            }
            try {
                bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
                break;
            } catch (ReflectiveOperationException | LinkageError e) {
                bukkit = null;
            }
        }
        if (bukkit == null) {
            return;
        }
        try {
            getPlayer = bukkit.getMethod("getPlayer", UUID.class);
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "AuthMe");
            if (plugin == null || !Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin))) {
                return;
            }
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, plugin.getClass().getClassLoader());
            Object instance = c.getMethod("getInstance").invoke(null);
            for (Method m : c.getMethods()) {
                if (m.getName().equals("isAuthenticated") && m.getParameterCount() == 1) {
                    api = instance;
                    isAuthenticated = m;
                }
            }
            CaraCrea.LOGGER.info("AuthMe found: the first-join creator opens after /login");
        } catch (ReflectiveOperationException | LinkageError e) {
            CaraCrea.LOGGER.warn("AuthMe is not readable: the first-join creator does not wait for /login ({})", e.toString());
        }
    }
}
