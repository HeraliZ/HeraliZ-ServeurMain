package fr.heraliz.banc;

import fr.heraliz.banc.HeralizBanc;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Connexion AuthMe des bots, par réflexion (aucune dépendance) : sans elle, AuthMe les garderait non connectés et les
 * expulserait au bout du délai, et les mods qui attendent le {@code /login} (créateur de personnage, outils du staff)
 * les tiendraient à l'écart. Bot jamais inscrit : inscription forcée avec un mot de passe tiré au hasard (personne ne le
 * connaît, jamais écrit ailleurs) et connexion ; déjà inscrit : connexion forcée. Sans Bukkit ni AuthMe (solo, serveur
 * Forge seul) : rien à faire.
 */
final class AuthMeBots {
    private AuthMeBots() {
    }

    private static boolean cherche;
    private static Object api, plugin;
    private static Method getPlayer, estInscrit, forcerConnexion, forcerInscription;

    static void connecter(BotJoueur b) {
        MinecraftServer srv = b.server;
        if (!srv.isDedicatedServer() || srv.usesAuthentication()) return;
        if (!cherche) brancher();
        if (api == null) return;
        try {
            Object bp = getPlayer.invoke(null, b.getUUID());
            if (bp == null) return;
            // tous les bots viennent de 127.0.0.1 : sans cette permission, AuthMe en refuse au-delà de 5 par adresse
            for (Method m : bp.getClass().getMethods()) {
                if (m.getName().equals("addAttachment") && m.getParameterCount() == 3 && m.getParameterTypes()[1] == String.class
                        && m.getParameterTypes()[2] == boolean.class && m.getParameterTypes()[0].isInstance(plugin)) {
                    m.invoke(bp, plugin, "authme.allowmultipleaccounts", true);
                    break;
                }
            }
            boolean inscrit = Boolean.TRUE.equals(estInscrit.invoke(api, b.getGameProfile().getName()));
            if (inscrit) {
                forcerConnexion.invoke(api, bp);
            } else {
                forcerInscription.invoke(api, bp, motDePasse(), true);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeralizBanc.LOG.warn("[bots] connexion AuthMe de {} impossible", b.getGameProfile().getName(), ex);
        }
    }

    private static void brancher() {
        cherche = true;
        for (ClassLoader cl : new ClassLoader[]{MinecraftServer.class.getClassLoader(), AuthMeBots.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()}) {
            if (cl == null) continue;
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
                Object pm = bukkit.getMethod("getPluginManager").invoke(null);
                Object pl = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "AuthMe");
                if (pl == null) return;
                plugin = pl;
                Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, pl.getClass().getClassLoader());
                Object instance = c.getMethod("getInstance").invoke(null);
                if (instance == null) return;
                getPlayer = bukkit.getMethod("getPlayer", UUID.class);
                for (Method m : c.getMethods()) {
                    switch (m.getName()) {
                        case "isRegistered" -> {
                            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) estInscrit = m;
                        }
                        case "forceLogin" -> {
                            if (m.getParameterCount() == 1) forcerConnexion = m;
                        }
                        case "forceRegister" -> {
                            if (m.getParameterCount() == 3) forcerInscription = m;
                        }
                        default -> {
                        }
                    }
                }
                if (estInscrit != null && forcerConnexion != null && forcerInscription != null) api = instance;
                else HeralizBanc.LOG.warn("[bots] API AuthMe incomplete : bots non connectes");
                return;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                api = null;
            }
        }
    }

    private static String motDePasse() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        String abc = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 32; i++) sb.append(abc.charAt(r.nextInt(abc.length())));
        return sb.toString();
    }
}
