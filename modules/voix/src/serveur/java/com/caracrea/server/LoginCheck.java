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
 *
 * <p>21/11 (fin de la validation) : la voix s'en sert aussi (pas de voix avant le /login). Avant, une seule erreur de
 * lecture d'AuthMe coupait cette règle jusqu'au redémarrage ; maintenant AuthMe est relu une minute plus tard (ouvert dans
 * le doute entre-temps, comme avant), et le journal dit quand il redevient lisible.
 */
final class LoginCheck {
    /** Après une erreur, ou un AuthMe pas encore prêt : nouvel essai au bout d'une minute. */
    private static final long REESSAI_MS = 60_000L;
    private static boolean looked;
    /** Prochain essai de lecture d'AuthMe (System.currentTimeMillis), 0 : aucun prévu. */
    private static long retryAt;
    private static Method getPlayer;
    private static Object api;
    private static Method isAuthenticated;
    private static boolean warned;
    private static boolean recoveryLogged;

    private LoginCheck() {
    }

    static boolean loggedIn(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!server.isDedicatedServer() || server.usesAuthentication()) {
            return true;
        }
        try {
            if (!looked || isAuthenticated == null && retryAt != 0 && System.currentTimeMillis() >= retryAt) {
                looked = true;
                retryAt = 0;
                hook();
            }
            if (isAuthenticated == null) {
                return true;
            }
            Object bukkitPlayer = getPlayer.invoke(null, player.getUUID());
            return bukkitPlayer == null || Boolean.TRUE.equals(isAuthenticated.invoke(api, bukkitPlayer));
        } catch (Throwable e) {
            isAuthenticated = null;
            retryAt = System.currentTimeMillis() + REESSAI_MS;
            if (!warned) {
                warned = true;
                CaraCrea.LOGGER.warn("AuthMe cannot be read: the first-join creator and the voice no longer wait for /login;"
                        + " trying again in a minute ({})", e.toString());
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
            return; // no Bukkit side (Forge only): nothing to wait for, never asked again
        }
        try {
            getPlayer = bukkit.getMethod("getPlayer", UUID.class);
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "AuthMe");
            if (plugin == null) {
                return; // no AuthMe: nothing to wait for, never asked again
            }
            if (!Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin))) {
                retryAt = System.currentTimeMillis() + REESSAI_MS; // switched off: asked again in a minute (open until then)
                return;
            }
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, plugin.getClass().getClassLoader());
            Object instance = c.getMethod("getInstance").invoke(null);
            if (instance == null) {
                // AuthMe there but its API not ready yet: asked again in a minute (open until then)
                retryAt = System.currentTimeMillis() + REESSAI_MS;
                return;
            }
            for (Method m : c.getMethods()) {
                if (m.getName().equals("isAuthenticated") && m.getParameterCount() == 1) {
                    api = instance;
                    isAuthenticated = m;
                }
            }
            if (isAuthenticated != null && !warned) {
                CaraCrea.LOGGER.info("AuthMe found: the first-join creator and the voice wait for /login");
            } else if (isAuthenticated != null && !recoveryLogged) {
                recoveryLogged = true; // une seule fois : jamais une ligne par minute si AuthMe tombe et revient sans cesse
                CaraCrea.LOGGER.info("AuthMe can be read again: the first-join creator and the voice wait for /login");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            retryAt = System.currentTimeMillis() + REESSAI_MS;
            if (!warned) {
                warned = true;
                CaraCrea.LOGGER.warn("AuthMe is not readable: the first-join creator does not wait for /login; trying again in"
                        + " a minute ({})", e.toString());
            }
        }
    }
}
