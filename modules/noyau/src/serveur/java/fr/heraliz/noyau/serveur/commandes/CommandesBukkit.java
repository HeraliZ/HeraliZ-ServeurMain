package fr.heraliz.noyau.serveur.commandes;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Côté Bukkit des commandes (02/11, audit point 36), sur le serveur hybride (Mohist) : la liste des commandes envoyée à
 * chaque joueur (complétion du chat, onglet « Commandes du Fondateur » du panneau du staff) perd les noms de
 * {@code [commandes] cachees} de {@code heraliz_noyau-server.toml} : autres noms d'une même commande de plugin (WorldEdit
 * ~62, LiteBans 73...) et commandes de plugins sans usage. Rien n'est retiré : un nom caché marche toujours si on le tape
 * (le staff tapait {@code /namehist}). Événement Bukkit {@code PlayerCommandSendEvent} (fait pour ça), à chaque envoi de
 * la liste (connexion, changement de droits).
 * <p>Écouteurs Bukkit créés par réflexion (aucune dépendance de compilation), rattachés à un plugin déjà chargé, comme
 * {@code ClicObjetBukkit}. Sans Bukkit (Forge seul) : rien.</p>
 */
public final class CommandesBukkit {
    private static final Logger LOG = LogUtils.getLogger();
    /** Plugins qui peuvent porter les écouteurs, dans l'ordre de préférence. */
    private static final String[] PORTEURS = {"WorldGuard", "LuckPerms", "AuthMe"};

    private static boolean fini;
    private static Method envoiCommandes;

    private CommandesBukkit() {
    }

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> brancher(e.getServer()));
        // plugins pas encore prêts au démarrage : nouvel essai à la première connexion (liste renvoyée à ce joueur)
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (!fini && e.getEntity() instanceof ServerPlayer p && brancher(p.server)) p.server.getCommands().sendCommands(p);
        });
    }

    /** Rend vrai si les écouteurs viennent d'être branchés. */
    private static synchronized boolean brancher(MinecraftServer srv) {
        if (fini) return false;
        Class<?> bukkit;
        try {
            bukkit = Class.forName("org.bukkit.Bukkit", false, MinecraftServer.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ex) {
            fini = true;   // pas de partie Bukkit
            return false;
        }
        try {
            ClassLoader cl = bukkit.getClassLoader();
            Class<?> ecouteur = Class.forName("org.bukkit.event.Listener", false, cl);
            Class<?> executeur = Class.forName("org.bukkit.plugin.EventExecutor", false, cl);
            Class<?> priorite = Class.forName("org.bukkit.event.EventPriority", false, cl);
            Class<?> plugin = Class.forName("org.bukkit.plugin.Plugin", false, cl);
            Class<?> envoi = Class.forName("org.bukkit.event.player.PlayerCommandSendEvent", false, cl);
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object proprio = porteur(pm);
            if (proprio == null) return false;   // nouvel essai plus tard
            envoiCommandes = envoi.getMethod("getCommands");
            Object e = Proxy.newProxyInstance(cl, new Class<?>[]{ecouteur}, (px, m, a) -> objet(px, m, a));
            Object cacher = Proxy.newProxyInstance(cl, new Class<?>[]{executeur}, (px, m, a) -> {
                if (m.getName().equals("execute") && a != null && a.length == 2 && envoi.isInstance(a[1])) {
                    cacher(a[1]);
                    return null;
                }
                return objet(px, m, a);
            });
            Method enregistrer = pm.getClass().getMethod("registerEvent", Class.class, ecouteur, priorite, executeur, plugin, boolean.class);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object haute = Enum.valueOf((Class) priorite, "HIGHEST");
            // HIGHEST : après les plugins qui ajoutent ou retirent des noms
            enregistrer.invoke(pm, envoi, e, haute, cacher, proprio, false);
            fini = true;
            LOG.info("[noyau] liste des commandes : {} nom(s) caché(s) aux joueurs ([commandes] cachees)", cachees().size());
            return true;
        } catch (Throwable ex) {
            fini = true;
            LOG.warn("[noyau] liste des commandes : noms cachés impossibles à brancher ({})", ex.toString());
            return false;
        }
    }

    /** Les noms du réglage, en minuscules (relu à chaque envoi : le fichier peut changer pendant que le serveur tourne). */
    private static Set<String> cachees() {
        List<? extends String> brut;
        try {
            brut = NoyauConfig.COMMANDES_CACHEES.get();
        } catch (IllegalStateException ex) {
            brut = NoyauConfig.CACHEES_DEFAUT;
        }
        Set<String> s = new HashSet<>();
        for (String c : brut) {
            if (c == null) continue;
            String r = c.trim().toLowerCase(Locale.ROOT);
            if (!r.isEmpty()) s.add(r);
        }
        return s;
    }

    /** Liste envoyée à un joueur : les noms cachés en sortent, avec leur variante « plugin:nom ». */
    private static void cacher(Object ev) {
        try {
            Set<String> caches = cachees();
            if (caches.isEmpty() || !(envoiCommandes.invoke(ev) instanceof Collection<?> l)) return;
            l.removeIf(o -> {
                if (!(o instanceof String n)) return false;
                String b = n.toLowerCase(Locale.ROOT);
                int deux = b.indexOf(':');
                return caches.contains(b) || (deux > 0 && caches.contains(b.substring(deux + 1)));
            });
        } catch (Throwable ex) {
            LOG.warn("[noyau] liste des commandes : noms cachés non appliqués ({})", ex.toString());
        }
    }

    private static Object porteur(Object pm) throws ReflectiveOperationException {
        Method getPlugin = pm.getClass().getMethod("getPlugin", String.class);
        for (String nom : PORTEURS) {
            Object p = getPlugin.invoke(pm, nom);
            if (p != null && Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p))) return p;
        }
        return null;
    }

    private static Object objet(Object px, Method m, Object[] a) {
        return switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(px);
            case "equals" -> a != null && a.length == 1 && px == a[0];
            case "toString" -> "HeraliZ Noyau (commandes)";
            default -> null;
        };
    }
}
