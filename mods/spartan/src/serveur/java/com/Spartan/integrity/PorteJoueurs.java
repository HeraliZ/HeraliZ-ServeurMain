package com.Spartan.integrity;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Porte « joueur vérifié » (23/10) : tant qu'un joueur n'a ni jeton de lancement valide (si {@code tokenRequired}) ni
 * fait son {@code /login} AuthMe, aucun paquet des mods (canaux personnalisés) n'atteint le serveur. Sans elle, un jeu
 * lancé hors du launcher, sous le pseudo d'un joueur déconnecté, pouvait agir à sa place pendant le délai du jeton
 * (dissoudre son clan, éditeurs du staff d'un op...) : AuthMe ne voit que ce qui passe par Bukkit.
 *
 * <p>Les paquets retenus ne sont pas jetés : ils attendent (128 au plus, 2 Mo au plus) et repartent dans l'ordre dès que
 * le joueur est vérifié, sinon un jeu honnête perdrait ses paquets d'arrivée (réglages envoyés à la connexion). Canaux
 * toujours libres : Spartan lui-même (jeton, défis, captures), Forge et Minecraft.
 *
 * <p>Filtre posé dans la connexion réseau (Netty) dès la négociation de connexion, avant le moindre paquet de jeu :
 * aucun Mixin, donc aucun nom de Minecraft à réobfusquer à la main.
 */
public final class PorteJoueurs {
    private static final String NOM = "spartan_porte";
    private static final int MAX_PAQUETS = 128;
    private static final int MAX_OCTETS = 2 * 1024 * 1024;
    private static final Set<String> LIBRES = Set.of(Spartan.MOD_ID, "fml", "forge", "minecraft");

    /** Joueurs dont le jeton de lancement a été accepté (ou non exigé faute de secret). */
    private static final Set<UUID> JETON_OK = ConcurrentHashMap.newKeySet();
    /** Joueurs vérifiés (jeton + AuthMe) : lu par le fil réseau, écrit par le fil du serveur. */
    private static final Set<UUID> VERIFIES = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Filtre> FILTRES = new ConcurrentHashMap<>();

    private static int ticks;

    private PorteJoueurs() {
    }

    // ------------------------------------------------------------------ état

    /** Jeton du launcher accepté pour ce joueur (appelé par {@link LauncherGate}). */
    static void jetonAccepte(UUID uuid) {
        JETON_OK.add(uuid);
    }

    public static void oublier(UUID uuid) {
        JETON_OK.remove(uuid);
        VERIFIES.remove(uuid);
        Filtre f = FILTRES.remove(uuid);
        if (f != null) f.vider();
    }

    /** Vrai si le jeu du joueur doit présenter un jeton valide avant d'agir. */
    private static boolean jetonExige() {
        return SpartanConfig.LAUNCHER_TOKEN_REQUIRED.get() && !SpartanConfig.secret(SpartanConfig.LAUNCHER_SECRET).isBlank();
    }

    /** Joueur vérifié (jeton + /login) : avant, ses paquets Spartan (alertes, captures, rapports) ne sont pas crus. */
    public static boolean estVerifie(UUID uuid) {
        return VERIFIES.contains(uuid);
    }

    private static boolean verifie(ServerPlayer p) {
        return (!jetonExige() || JETON_OK.contains(p.getUUID())) && AuthMe.connecte(p);
    }

    /** Fil du serveur, chaque tick : met à jour les joueurs vérifiés et relâche leurs paquets retenus. */
    public static void tick(MinecraftServer srv) {
        boolean tous = ++ticks % 20 == 0;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            boolean deja = VERIFIES.contains(id);
            if (deja && !tous) continue;
            Filtre fc = FILTRES.get(id);
            if (fc != null && fc.chatsEnAttente && (!jetonExige() || JETON_OK.contains(id))) fc.relacherChats();
            boolean ok = verifie(p);
            if (ok && !deja) {
                VERIFIES.add(id);
                Filtre f = FILTRES.get(id);
                if (f != null) f.relacher();
            } else if (!ok && deja) {
                VERIFIES.remove(id); // /logout d'AuthMe : de nouveau retenu
            }
        }
    }

    // ------------------------------------------------------------------ filtre réseau

    /**
     * Pose le filtre dans la connexion d'un joueur qui négocie sa connexion (avant tout paquet de jeu). En mode hors
     * ligne, le profil n'a pas encore d'identifiant à ce moment : le filtre retient tout jusqu'à {@link #rattacher}.
     */
    public static void poser(Connection connexion) {
        if (connexion == null) return;
        try {
            Channel canal = connexion.channel();
            if (canal == null || canal.pipeline().get(NOM) != null) return;
            Filtre f = new Filtre(canal);
            if (canal.pipeline().get("packet_handler") != null) {
                canal.pipeline().addBefore("packet_handler", NOM, f);
            } else {
                canal.pipeline().addLast(NOM, f);
            }
        } catch (Throwable ex) {
            Spartan.LOGGER.error("[Spartan] porte des paquets impossible à poser : {}", ex.toString());
        }
    }

    /** À l'entrée du joueur dans le monde : le filtre de sa connexion apprend à qui il appartient (posé ici s'il manque). */
    public static void rattacher(ServerPlayer p) {
        try {
            Connection c = p.connection.connection;
            poser(c);
            if (c.channel().pipeline().get(NOM) instanceof Filtre f) {
                f.uuid = p.getUUID();
                FILTRES.put(p.getUUID(), f);
            }
        } catch (Throwable ex) {
            Spartan.LOGGER.error("[Spartan] porte des paquets introuvable pour {} : {}", p.getGameProfile().getName(), ex.toString());
        }
    }

    static final class Filtre extends ChannelInboundHandlerAdapter {
        private volatile UUID uuid;
        private final Channel canal;
        private final ArrayDeque<Object> retenus = new ArrayDeque<>();
        private ChannelHandlerContext ctx;
        private int octets;
        private boolean journal;

        Filtre(Channel canal) {
            this.canal = canal;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        /** Chat et commandes (/register, /login compris) retenus tant que le jeton n'est pas accepté (24/10). */
        private final ArrayDeque<Object> chats = new ArrayDeque<>();
        volatile boolean chatsEnAttente;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket
                    || msg instanceof net.minecraft.network.protocol.game.ServerboundChatPacket) {
                // sans jeton, ni chat ni commande : un jeu lancé hors du launcher sous le pseudo d'un autre ne doit pas
                // pouvoir s'inscrire sur AuthMe à sa place, ni agir pendant les secondes qui précèdent l'expulsion
                UUID u = uuid;
                if (!jetonExige() || (u != null && JETON_OK.contains(u) && chats.isEmpty())) {
                    ctx.fireChannelRead(msg);
                } else if (chats.size() < 32) {
                    chats.add(msg);
                    chatsEnAttente = true;
                }
                return;
            }
            if (!(msg instanceof ServerboundCustomPayloadPacket p)) {
                ctx.fireChannelRead(msg);
                return;
            }
            ResourceLocation id = p.getIdentifier();
            if (id != null && LIBRES.contains(id.getNamespace())) {
                ctx.fireChannelRead(msg);
                return;
            }
            UUID u = uuid;
            boolean ok = u != null && VERIFIES.contains(u);
            if (ok && retenus.isEmpty()) {
                ctx.fireChannelRead(msg);
                return;
            }
            retenir(p, id);
            if (ok) envoyer();
        }

        /** Copie le paquet (le tampon d'origine n'est pas gardé) et le met en attente. */
        private void retenir(ServerboundCustomPayloadPacket p, ResourceLocation id) {
            FriendlyByteBuf data = p.getData();
            int taille = data == null ? 0 : data.readableBytes();
            try {
                if (retenus.size() >= MAX_PAQUETS || octets + taille > MAX_OCTETS) {
                    if (!journal) {
                        journal = true;
                        Spartan.LOGGER.warn("[Spartan] {} : paquets de mods envoyés avant la vérification (jeton + /login), "
                                + "trop nombreux : les suivants sont jetés ({})", uuid, id);
                    }
                    return;
                }
                FriendlyByteBuf copie = new FriendlyByteBuf(data == null ? Unpooled.EMPTY_BUFFER : Unpooled.copiedBuffer(data));
                retenus.add(new ServerboundCustomPayloadPacket(id, copie));
                octets += taille;
            } finally {
                // Le paquet d'origine ne va pas plus loin : son tampon est libéré ici (la copie le remplace).
                if (data != null && data.refCnt() > 0) {
                    try {
                        data.release();
                    } catch (RuntimeException ignore) {
                        // déjà libéré
                    }
                }
            }
        }

        /** Fil réseau : envoie les paquets retenus, dans l'ordre. */
        private void envoyer() {
            if (ctx == null) return;
            Object m;
            while ((m = retenus.poll()) != null) ctx.fireChannelRead(m);
            octets = 0;
        }

        /** Fil du serveur : jeton accepté, le chat et les commandes retenus repartent dans l'ordre (fil réseau). */
        void relacherChats() {
            chatsEnAttente = false;
            if (canal.isActive()) {
                canal.eventLoop().execute(() -> {
                    if (ctx == null) return;
                    Object m;
                    while ((m = chats.poll()) != null) ctx.fireChannelRead(m);
                });
            }
        }

        /** Appelé par le fil du serveur : le travail se fait sur le fil réseau de la connexion. */
        void relacher() {
            if (canal.isActive()) canal.eventLoop().execute(this::envoyer);
        }

        void vider() {
            if (canal.isActive()) {
                canal.eventLoop().execute(() -> {
                    retenus.clear();
                    octets = 0;
                });
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            retenus.clear();
            chats.clear();
            if (uuid != null) FILTRES.remove(uuid, this);
            super.channelInactive(ctx);
        }
    }

    // ------------------------------------------------------------------ AuthMe (réflexion, comme HeraliZ Grades)

    /**
     * Connexion AuthMe d'un joueur, par réflexion (aucune dépendance). Pas de partie Bukkit (solo, serveur Forge seul),
     * serveur en mode premium ou plugin AuthMe pas installé : rien à attendre. Plugin AuthMe présent mais injoignable
     * (désactivé, API pas prête, erreur) : le joueur est considéré NON connecté.
     */
    static final class AuthMe {
        private static boolean cherche;
        private static Class<?> bukkit;
        private static Method getPlayer;
        private static boolean absent;
        private static Object api;
        private static Method estConnecte;
        private static boolean alerte;

        private AuthMe() {
        }

        static boolean connecte(ServerPlayer p) {
            MinecraftServer srv = p.server;
            if (!srv.isDedicatedServer() || srv.usesAuthentication()) return true;
            if (!cherche) chercherBukkit();
            if (bukkit == null) return true;
            try {
                if (estConnecte == null && !absent) brancher();
                if (absent) return true;
                Object bp = getPlayer.invoke(null, p.getUUID());
                if (bp == null) return false;
                return Boolean.TRUE.equals(estConnecte.invoke(api, bp));
            } catch (Throwable ex) {
                estConnecte = null;
                if (!alerte) {
                    alerte = true;
                    Spartan.LOGGER.error("[Spartan] AuthMe injoignable : paquets des mods retenus pour tous tant que ça dure ({})", ex.toString());
                }
                return false;
            }
        }

        private static void chercherBukkit() {
            cherche = true;
            for (ClassLoader cl : new ClassLoader[]{MinecraftServer.class.getClassLoader(), PorteJoueurs.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader()}) {
                if (cl == null) continue;
                try {
                    bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
                    getPlayer = bukkit.getMethod("getPlayer", UUID.class);
                    return;
                } catch (ReflectiveOperationException | LinkageError ex) {
                    bukkit = null;
                }
            }
        }

        private static void brancher() throws ReflectiveOperationException {
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "AuthMe");
            if (plugin == null) {
                absent = true;
                Spartan.LOGGER.warn("[Spartan] plugin AuthMe absent : la porte des paquets n'attend que le jeton du launcher");
                return;
            }
            if (!Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin))) {
                throw new IllegalStateException("plugin AuthMe désactivé");
            }
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, plugin.getClass().getClassLoader());
            Object instance = c.getMethod("getInstance").invoke(null);
            if (instance == null) throw new IllegalStateException("API AuthMe pas prête");
            Method m = null;
            for (Method x : c.getMethods()) {
                if (x.getName().equals("isAuthenticated") && x.getParameterCount() == 1) m = x;
            }
            if (m == null) throw new NoSuchMethodException("AuthMeApi.isAuthenticated");
            api = instance;
            estConnecte = m;
            if (alerte) Spartan.LOGGER.info("[Spartan] AuthMe de nouveau joignable");
            alerte = false;
        }
    }
}
