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
 *    <li>nom affiché Bukkit (le chat des serveurs hybrides l'utilise) : on y met le préfixe du grade ;</li>
 *    <li>/staff vanish du staff (2.7.0) : {@code Player.hidePlayer(Plugin, Player)} de Bukkit (liste Tab, entité, suivi), et
 *    messages d'arrivée et de départ retirés pour un joueur caché.</li>
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
        if (!srv.isDedicatedServer() || srv.usesAuthentication() || essaiLocal(srv)) return true;
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

    private static boolean essaiSignale;

    /**
     * Serveur d'essai local sans Bukkit ni AuthMe (25/10, captures des écrans, docs/AUDIT_UI.md) : le contrôle AuthMe est
     * levé seulement si le serveur est lancé avec {@code -Dheraliz.essai.sansAuthme=true} ET n'écoute que ce PC
     * ({@code server-ip=127.0.0.1} : personne d'autre ne peut s'y connecter). Sur le Ptero (ni l'un ni l'autre), rien ne
     * change.
     */
    private static boolean essaiLocal(MinecraftServer srv) {
        if (!Boolean.getBoolean("heraliz.essai.sansAuthme") || !"127.0.0.1".equals(srv.getLocalIp())) return false;
        if (!essaiSignale) {
            essaiSignale = true;
            LOG.warn("[grades] serveur d'essai local (127.0.0.1, -Dheraliz.essai.sansAuthme) : contrôle AuthMe levé");
        }
        return true;
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
    /** Le chat à canaux prend les messages à la fin de l'événement de Bukkit ({@link ChatServeur#prendre}). */
    private static volatile boolean chatCanaux;

    /** Vrai si l'écouteur Bukkit du chat à canaux est branché (sinon ChatServeur passe par l'événement de Forge). */
    static boolean chatCanaux() {
        return chatCanaux;
    }
    /**
     * Code couleur du message de chaque joueur qui a le chat en couleur (/avantages couleur, Roi de la Zone, 22/10) : rempli sur
     * le fil serveur ({@link SurnomsServeur#majChat}), lu par l'écouteur du chat, qui tourne sur un autre fil (événement
     * asynchrone de Bukkit) : jamais de lecture des grades depuis ce fil-là.
     */
    static final java.util.Map<UUID, String> CODE_CHAT = new java.util.concurrent.ConcurrentHashMap<>();

    /** Format du chat d'un joueur : message en couleur s'il en a choisi une (et y a droit), sinon blanc. */
    private static String formatChat(UUID joueur) {
        String code = joueur == null ? null : CODE_CHAT.get(joueur);
        return code == null ? FORMAT_CHAT : "%1$s §8» " + code + "%2$s";
    }

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
            Object proprio = porteur();
            if (proprio == null) throw new IllegalStateException("aucun plugin chargé pour porter l'écouteur");
            Method setFormat = evenement.getMethod("setFormat", String.class);
            Method getPlayerChat = evenement.getMethod("getPlayer");
            Method getUniqueId = Class.forName("org.bukkit.entity.Entity", false, cl).getMethod("getUniqueId");
            Object e = Proxy.newProxyInstance(cl, new Class<?>[]{ecouteur}, (px, m, a) -> objet(px, m, a));
            Object x = Proxy.newProxyInstance(cl, new Class<?>[]{executeur}, (px, m, a) -> {
                if (m.getName().equals("execute") && a != null && a.length == 2 && evenement.isInstance(a[1])) {
                    UUID u = null;
                    try {
                        Object bp = getPlayerChat.invoke(a[1]);
                        if (bp != null && getUniqueId.invoke(bp) instanceof UUID id) u = id;
                    } catch (Throwable ex) {
                        u = null;
                    }
                    setFormat.invoke(a[1], formatChat(u));
                    return null;
                }
                return objet(px, m, a);
            });
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object haute = Enum.valueOf((Class) priorite, "HIGHEST");
            pm.getClass().getMethod("registerEvent", Class.class, ecouteur, priorite, executeur, plugin, boolean.class)
                    .invoke(pm, evenement, e, haute, x, proprio, true);
            LOG.info("[grades] format du chat branché (« Pseudo » message »)");
            // chat à canaux (25/10) : en MONITOR, le message est définitif (AuthMe, LiteBans et les autres ont pu l'annuler,
            // ignoreCancelled = true) : on l'annule pour Minecraft et on le répartit nous-mêmes (Proximité, Global, Clan)
            Method getMessage = evenement.getMethod("getMessage");
            Method setCancelled = evenement.getMethod("setCancelled", boolean.class);
            Object y = Proxy.newProxyInstance(cl, new Class<?>[]{executeur}, (px, m, a) -> {
                if (m.getName().equals("execute") && a != null && a.length == 2 && evenement.isInstance(a[1])) {
                    UUID u = null;
                    Object bp = getPlayerChat.invoke(a[1]);
                    if (bp != null && getUniqueId.invoke(bp) instanceof UUID id) u = id;
                    if (getMessage.invoke(a[1]) instanceof String msg && ChatServeur.prendre(u, msg)) setCancelled.invoke(a[1], true);
                    return null;
                }
                return objet(px, m, a);
            });
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object monitor = Enum.valueOf((Class) priorite, "MONITOR");
            pm.getClass().getMethod("registerEvent", Class.class, ecouteur, priorite, executeur, plugin, boolean.class)
                    .invoke(pm, evenement, e, monitor, y, proprio, true);
            chatCanaux = true;
            LOG.info("[grades] chat à canaux branché (Proximité, Global, Clan ; mutes de LiteBans respectés)");
        } catch (Throwable ex) {
            LOG.warn("[grades] format du chat impossible à brancher, chat de Mohist inchangé : {}", ex.toString());
        }
    }

    /** Un plugin Bukkit chargé et actif (le mod n'est pas un plugin : écouteurs et joueurs cachés lui sont rattachés), null sinon. */
    private static Object porteur() throws ReflectiveOperationException {
        if (bukkit() == null) return null;
        Object pm = bukkit.getMethod("getPluginManager").invoke(null);
        for (String nom : new String[]{"spark", "LuckPerms", "AuthMe", "WorldGuard"}) {
            Object p = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, nom);
            if (p != null && Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p))) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ /staff vanish
    private static Method hidePlayer, showPlayer;
    private static Object porteurCache;
    private static boolean alerteCache;

    /**
     * Cache (ou remontre) {@code cible} à {@code voyant} par Bukkit ({@code hidePlayer} / {@code showPlayer} : retiré de sa
     * liste Tab et de son suivi d'entités, gardé par Bukkit tant que le voyant reste connecté). Faux si Bukkit est absent
     * ou si l'appel échoue : l'appelant fait alors lui-même le travail ({@link VanishServeur}).
     */
    static boolean cacher(ServerPlayer voyant, ServerPlayer cible, boolean cacher) {
        if (bukkit() == null) return false;
        try {
            if (hidePlayer == null || porteurCache == null) {
                ClassLoader cl = bukkit.getClassLoader();
                Class<?> joueur = Class.forName("org.bukkit.entity.Player", false, cl);
                Class<?> plugin = Class.forName("org.bukkit.plugin.Plugin", false, cl);
                porteurCache = porteur();
                if (porteurCache == null) throw new IllegalStateException("aucun plugin chargé pour porter les joueurs cachés");
                hidePlayer = joueur.getMethod("hidePlayer", plugin, joueur);
                showPlayer = joueur.getMethod("showPlayer", plugin, joueur);
            }
            Object v = joueurBukkit(voyant.getUUID()), c = joueurBukkit(cible.getUUID());
            if (v == null || c == null) return false;
            (cacher ? hidePlayer : showPlayer).invoke(v, porteurCache, c);
            return true;
        } catch (Throwable ex) {
            hidePlayer = null;
            porteurCache = null;
            if (!alerteCache) {
                alerteCache = true;
                LOG.warn("[grades] /staff vanish par Bukkit impossible, paquets de Minecraft à la place : {}", ex.toString());
            }
            return false;
        }
    }

    private static boolean connexionsBranchees;

    /**
     * Messages « X a rejoint / quitté la partie » de Bukkit (Mohist) retirés pour un joueur caché par /staff vanish : écouteur
     * de PlayerJoinEvent et PlayerQuitEvent en priorité HIGHEST, rattaché à un plugin déjà chargé. Sans Bukkit, rien.
     */
    static void brancherConnexions(java.util.function.Predicate<UUID> cache) {
        if (connexionsBranchees || bukkit() == null) return;
        try {
            Object proprio = porteur();
            if (proprio == null) return;
            connexionsBranchees = true;
            ClassLoader cl = bukkit.getClassLoader();
            Class<?> ecouteur = Class.forName("org.bukkit.event.Listener", false, cl);
            Class<?> executeur = Class.forName("org.bukkit.plugin.EventExecutor", false, cl);
            Class<?> priorite = Class.forName("org.bukkit.event.EventPriority", false, cl);
            Class<?> plugin = Class.forName("org.bukkit.plugin.Plugin", false, cl);
            Class<?> arrivee = Class.forName("org.bukkit.event.player.PlayerJoinEvent", false, cl);
            Class<?> depart = Class.forName("org.bukkit.event.player.PlayerQuitEvent", false, cl);
            Method joueurArrivee = arrivee.getMethod("getPlayer"), joueurDepart = depart.getMethod("getPlayer");
            Method msgArrivee = arrivee.getMethod("setJoinMessage", String.class), msgDepart = depart.getMethod("setQuitMessage", String.class);
            Method getUniqueId = Class.forName("org.bukkit.entity.Entity", false, cl).getMethod("getUniqueId");
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object e = Proxy.newProxyInstance(cl, new Class<?>[]{ecouteur}, (px, m, a) -> objet(px, m, a));
            Object x = Proxy.newProxyInstance(cl, new Class<?>[]{executeur}, (px, m, a) -> {
                if (m.getName().equals("execute") && a != null && a.length == 2 && a[1] != null) {
                    boolean entre = arrivee.isInstance(a[1]);
                    if (!entre && !depart.isInstance(a[1])) return null;
                    try {
                        Object bp = (entre ? joueurArrivee : joueurDepart).invoke(a[1]);
                        if (bp != null && getUniqueId.invoke(bp) instanceof UUID id && cache.test(id)) {
                            (entre ? msgArrivee : msgDepart).invoke(a[1], (Object) null);
                        }
                    } catch (Throwable ex) {
                        // message laissé tel quel
                    }
                    return null;
                }
                return objet(px, m, a);
            });
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object haute = Enum.valueOf((Class) priorite, "HIGHEST");
            Method enregistrer = pm.getClass().getMethod("registerEvent", Class.class, ecouteur, priorite, executeur, plugin, boolean.class);
            enregistrer.invoke(pm, arrivee, e, haute, x, proprio, false);
            enregistrer.invoke(pm, depart, e, haute, x, proprio, false);
            LOG.info("[grades] messages d'arrivée et de départ des joueurs cachés (/staff vanish) branchés");
        } catch (Throwable ex) {
            LOG.warn("[grades] messages d'arrivée et de départ des joueurs cachés impossibles à couper : {}", ex.toString());
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
