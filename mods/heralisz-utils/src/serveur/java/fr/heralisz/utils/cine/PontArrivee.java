package fr.heralisz.utils.cine;

import fr.heralisz.utils.HeraliszUtils;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Ce que la cinématique d'arrivée attend, par réflexion (aucune dépendance de compilation) : le joueur connecté sur
 * AuthMe ({@code /register} puis {@code /login}, partie Bukkit du serveur hybride) et son personnage fait dans le
 * créateur de CaraCrea ({@code com.caracrea.api.Arrivee.personnageFait}). Ordre d'arrivée voulu : login, personnage,
 * cinématique. Rien de lisible (mod ou plugin absent, mode premium, solo) = rien à attendre : la cinématique ne doit
 * jamais rester bloquée.
 */
final class PontArrivee {
    private static boolean authMeCherche;
    private static Method getPlayer;
    private static Object authMe;
    private static Method estConnecte;

    private static boolean caraCreaCherche;
    private static Method personnageFait;

    private PontArrivee() {
    }

    static boolean pret(ServerPlayer sp) {
        return connecte(sp) && personnage(sp);
    }

    private static boolean connecte(ServerPlayer sp) {
        MinecraftServer srv = sp.server;
        if (!srv.isDedicatedServer() || srv.usesAuthentication()) {
            return true;
        }
        try {
            if (!authMeCherche) {
                authMeCherche = true;
                brancherAuthMe();
            }
            if (estConnecte == null) {
                return true;
            }
            Object bp = getPlayer.invoke(null, sp.getUUID());
            return bp == null || Boolean.TRUE.equals(estConnecte.invoke(authMe, bp));
        } catch (Throwable t) {
            estConnecte = null;
            HeraliszUtils.LOG.warn("[cinematique] AuthMe illisible : la cinématique n'attend plus le /login ({})", t.toString());
            return true;
        }
    }

    private static void brancherAuthMe() {
        Class<?> bukkit = null;
        for (ClassLoader cl : new ClassLoader[]{MinecraftServer.class.getClassLoader(), PontArrivee.class.getClassLoader(),
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
            Class<?> api = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, plugin.getClass().getClassLoader());
            Object instance = api.getMethod("getInstance").invoke(null);
            for (Method m : api.getMethods()) {
                if (m.getName().equals("isAuthenticated") && m.getParameterCount() == 1) {
                    authMe = instance;
                    estConnecte = m;
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            HeraliszUtils.LOG.warn("[cinematique] AuthMe illisible : la cinématique n'attend pas le /login ({})", e.toString());
        }
    }

    private static boolean personnage(ServerPlayer sp) {
        if (!caraCreaCherche) {
            caraCreaCherche = true;
            if (ModList.get().isLoaded("caracrea")) {
                try {
                    personnageFait = Class.forName("com.caracrea.api.Arrivee").getMethod("personnageFait", ServerPlayer.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    HeraliszUtils.LOG.warn("[cinematique] CaraCrea sans com.caracrea.api.Arrivee : la cinématique n'attend pas le personnage ({})",
                            e.toString());
                }
            }
        }
        if (personnageFait == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(personnageFait.invoke(null, sp));
        } catch (Throwable t) {
            personnageFait = null;
            return true;
        }
    }
}
