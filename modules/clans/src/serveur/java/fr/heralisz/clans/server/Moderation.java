package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Pont de moderation pour le chat de clan : les messages /cc (commande ou mode bascule) ne passent pas par le chat
 * public, donc pas par les plugins de mute. On interroge donc, par reflexion et sans dependance, l'API Bukkit du
 * serveur hybride (Mohist) :
 * <ul>
 * <li>le noeud de permission {@link #NOEUD_MUET} (LuckPerms : {@code lp user X permission settemp heralisz.clans.mute true 1h}) ;</li>
 * <li>Essentials : {@code getUser(uuid).isMuted()} quand le plugin est charge (ses classes ne sont visibles qu'a
 * travers l'instance du plugin, d'ou la resolution tardive).</li>
 * </ul>
 * Sans Bukkit (serveur Forge pur, client de dev), personne n'est muet. Les methodes sont resolues une seule fois ;
 * chaque message coute quelques appels reflexifs, rien n'est alloue.
 */
final class Moderation {
    private Moderation() {
    }

    /** Noeud de permission qui rend un joueur muet dans le chat de clan (faux par defaut). */
    static final String NOEUD_MUET = "heralisz.clans.mute";

    private static boolean resolu, essentialsResolu;
    private static Method bukkitGetPlayer, hasPermission, getPluginManager, getPlugin, essGetUser, essIsMuted;

    private static void resoudre() {
        resolu = true;
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            bukkitGetPlayer = bukkit.getMethod("getPlayer", UUID.class);
            hasPermission = Class.forName("org.bukkit.permissions.Permissible").getMethod("hasPermission", String.class);
            getPluginManager = bukkit.getMethod("getPluginManager");
            getPlugin = Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin", String.class);
            HeraliszClans.LOG.info("[clans] moderation du chat de clan : Bukkit detecte (noeud {}, mute Essentials)", NOEUD_MUET);
        } catch (ReflectiveOperationException | LinkageError ex) {
            bukkitGetPlayer = null;
        }
    }

    /** Vrai si Essentials considere le joueur muet ; les methodes sont resolues sur l'instance du plugin, une fois. */
    private static boolean muetEssentials(UUID joueur) throws ReflectiveOperationException {
        Object pm = getPluginManager.invoke(null);
        Object ess = pm == null ? null : getPlugin.invoke(pm, "Essentials");
        if (ess == null) return false;
        if (!essentialsResolu) {
            essentialsResolu = true;
            try {
                essGetUser = ess.getClass().getMethod("getUser", UUID.class);
                essIsMuted = essGetUser.getReturnType().getMethod("isMuted");
                HeraliszClans.LOG.info("[clans] moderation du chat de clan : Essentials detecte");
            } catch (ReflectiveOperationException | LinkageError ex) {
                essGetUser = null;
            }
        }
        if (essGetUser == null) return false;
        Object user = essGetUser.invoke(ess, joueur);
        return user != null && Boolean.TRUE.equals(essIsMuted.invoke(user));
    }

    /** Vrai si le joueur est muet (noeud de permission ou mute Essentials) ; faux en cas de doute ou sans Bukkit. */
    static boolean estMuet(ServerPlayer p) {
        if (!resolu) resoudre();
        if (bukkitGetPlayer == null) return false;
        try {
            Object joueur = bukkitGetPlayer.invoke(null, p.getUUID());
            if (joueur != null && Boolean.TRUE.equals(hasPermission.invoke(joueur, NOEUD_MUET))) return true;
            return muetEssentials(p.getUUID());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            // API absente ou changee : on ne bloque personne, et on ne retente pas a chaque message
            HeraliszClans.LOG.warn("[clans] moderation du chat de clan desactivee : {}", ex.toString());
            bukkitGetPlayer = null;
            return false;
        }
    }
}
