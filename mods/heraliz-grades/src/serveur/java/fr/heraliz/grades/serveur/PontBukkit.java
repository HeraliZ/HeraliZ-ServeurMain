package fr.heraliz.grades.serveur;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Lien avec la partie Bukkit du serveur hybride (Mohist), uniquement par réflexion (aucune dépendance de compilation) :
 * <ul>
 *    <li>AuthMe : un joueur n'a ses droits de grade qu'une fois connecté par mot de passe (en mode cracké, n'importe qui
 *    peut arriver avec le pseudo d'un Fondateur ; tant qu'il n'a pas fait /login, il n'est personne) ;</li>
 *    <li>nom affiché Bukkit (le chat des serveurs hybrides l'utilise) : on y met le préfixe du grade.</li>
 * </ul>
 * Règle de sécurité : sur un serveur dédié en mode cracké, si AuthMe ne peut pas être interrogé, le joueur est considéré
 * NON connecté (on refuse plutôt que d'accorder par erreur). En solo ou en mode premium, pas de contrôle.
 */
final class PontBukkit {
    private static final Logger LOG = LogUtils.getLogger();

    private static boolean cherche;
    private static Class<?> bukkit;
    private static Method getPlayer;
    private static Object authMe;
    private static Method estConnecte;
    private static boolean alerte;

    private PontBukkit() {
    }

    private static Class<?> bukkit() {
        if (!cherche) {
            cherche = true;
            for (ClassLoader cl : new ClassLoader[]{MinecraftServer.class.getClassLoader(), PontBukkit.class.getClassLoader(), Thread.currentThread().getContextClassLoader()}) {
                if (cl == null) continue;
                try {
                    bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
                    getPlayer = bukkit.getMethod("getPlayer", UUID.class);
                    break;
                } catch (ReflectiveOperationException | LinkageError ex) {
                    bukkit = null;
                }
            }
            LOG.info("[grades] partie Bukkit du serveur : {}", bukkit != null ? "présente" : "absente");
        }
        return bukkit;
    }

    /** Le joueur Bukkit (CraftPlayer) de ce joueur, null sans Bukkit. */
    private static Object joueurBukkit(UUID uuid) throws ReflectiveOperationException {
        return bukkit() == null ? null : getPlayer.invoke(null, uuid);
    }

    /** Vrai si le joueur a le droit d'utiliser ses grades : connecté sur AuthMe (serveur cracké), toujours vrai sinon. */
    static boolean authentifie(ServerPlayer p) {
        MinecraftServer srv = p.server;
        if (!srv.isDedicatedServer() || srv.usesAuthentication()) return true;
        try {
            if (estConnecte == null) brancherAuthMe();
            Object bp = joueurBukkit(p.getUUID());
            if (bp == null) return false;
            return Boolean.TRUE.equals(estConnecte.invoke(authMe, bp));
        } catch (Throwable ex) {
            estConnecte = null;
            if (!alerte) {
                alerte = true;
                LOG.error("[grades] AuthMe injoignable : les droits des grades sont REFUSÉS à tous tant que ça dure ({})", ex.toString());
            }
            return false;
        }
    }

    private static void brancherAuthMe() throws ReflectiveOperationException {
        if (bukkit() == null) throw new ClassNotFoundException("org.bukkit.Bukkit (serveur dédié cracké sans Bukkit : AuthMe obligatoire)");
        Object pm = bukkit.getMethod("getPluginManager").invoke(null);
        Object plugin = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "AuthMe");
        if (plugin == null) throw new ClassNotFoundException("plugin AuthMe absent");
        if (!Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin))) throw new IllegalStateException("plugin AuthMe désactivé");
        Class<?> api = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, plugin.getClass().getClassLoader());
        Object instance = api.getMethod("getInstance").invoke(null);
        if (instance == null) throw new IllegalStateException("API AuthMe pas prête");
        Method m = null;
        for (Method x : api.getMethods()) {
            if (x.getName().equals("isAuthenticated") && x.getParameterCount() == 1) m = x;
        }
        if (m == null) throw new NoSuchMethodException("AuthMeApi.isAuthenticated");
        authMe = instance;
        estConnecte = m;
        if (alerte) LOG.info("[grades] AuthMe de nouveau joignable");
        alerte = false;
    }

    /** Format du chat : « [ZK] FONDATEUR Naro » message » (le nom affiché porte déjà clan et grade). */
    private static final String FORMAT_CHAT = "%1$s §8» §f%2$s";
    private static boolean chatBranche;

    /**
     * Chat des serveurs hybrides : Mohist fabrique lui-même « <nom> message » (événement Bukkit AsyncPlayerChatEvent). On
     * change seulement son format, en priorité HIGHEST et sans toucher aux messages annulés (mute de LiteBans, joueur pas
     * encore connecté sur AuthMe...). Écouteur Bukkit créé par réflexion (Proxy), rattaché à un plugin déjà chargé (le
     * mod n'est pas un plugin). Sans Bukkit (solo), rien : le chat de Minecraft prend la clé chat.type.text des langues.
     */
    static void brancherChat() {
        if (chatBranche || bukkit() == null) return;
        chatBranche = true;
        try {
            ClassLoader cl = bukkit.getClassLoader();
            Class<?> ecouteur = Class.forName("org.bukkit.event.Listener", false, cl);
            Class<?> executeur = Class.forName("org.bukkit.plugin.EventExecutor", false, cl);
            Class<?> priorite = Class.forName("org.bukkit.event.EventPriority", false, cl);
            Class<?> evenement = Class.forName("org.bukkit.event.player.AsyncPlayerChatEvent", false, cl);
            Class<?> plugin = Class.forName("org.bukkit.plugin.Plugin", false, cl);
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object proprio = null;
            for (String nom : new String[]{"spark", "LuckPerms", "AuthMe", "WorldGuard"}) {
                Object p = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, nom);
                if (p != null && Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p))) {
                    proprio = p;
                    break;
                }
            }
            if (proprio == null) throw new IllegalStateException("aucun plugin chargé pour porter l'écouteur");
            Method setFormat = evenement.getMethod("setFormat", String.class);
            Object e = Proxy.newProxyInstance(cl, new Class<?>[]{ecouteur}, (px, m, a) -> objet(px, m, a));
            Object x = Proxy.newProxyInstance(cl, new Class<?>[]{executeur}, (px, m, a) -> {
                if (m.getName().equals("execute") && a != null && a.length == 2 && evenement.isInstance(a[1])) {
                    setFormat.invoke(a[1], FORMAT_CHAT);
                    return null;
                }
                return objet(px, m, a);
            });
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object haute = Enum.valueOf((Class) priorite, "HIGHEST");
            pm.getClass().getMethod("registerEvent", Class.class, ecouteur, priorite, executeur, plugin, boolean.class)
                    .invoke(pm, evenement, e, haute, x, proprio, true);
            LOG.info("[grades] format du chat branché (« Pseudo » message »)");
        } catch (Throwable ex) {
            LOG.warn("[grades] format du chat impossible à brancher, chat de Mohist inchangé : {}", ex.toString());
        }
    }

    /** Méthodes d'Object des proxys (hashCode, equals, toString). */
    private static Object objet(Object px, Method m, Object[] a) {
        return switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(px);
            case "equals" -> a != null && a.length == 1 && px == a[0];
            case "toString" -> "HeraliZ Grades (format du chat)";
            default -> null;
        };
    }

    /** Met le nom affiché Bukkit (chat des serveurs hybrides) ; sans Bukkit, rien. */
    static void nomAffiche(ServerPlayer p, String nom) {
        try {
            Object bp = joueurBukkit(p.getUUID());
            if (bp != null) bp.getClass().getMethod("setDisplayName", String.class).invoke(bp, nom);
        } catch (Throwable ex) {
            LOG.warn("[grades] nom affiché Bukkit impossible pour {} : {}", p.getGameProfile().getName(), ex.toString());
        }
    }
}
