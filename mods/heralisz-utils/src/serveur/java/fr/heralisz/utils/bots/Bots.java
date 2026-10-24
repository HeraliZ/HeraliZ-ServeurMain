package fr.heralisz.utils.bots;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heralisz.utils.HeraliszUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bots de charge (demande du propriétaire, 24/10) : de faux joueurs qui jouent comme de vrais joueurs pour éprouver
 * le serveur (tirs TaCZ, grenades, flashs, fumigènes, soins, frappes aériennes, largages, fouille des conteneurs,
 * course à travers la carte, morts et cadavres), sans aucun jeu de joueur derrière : ils ne pèsent que sur le serveur,
 * pas sur le réseau (voir {@link BotJoueur}).
 * <p>Commande {@code /bots} (op niveau 4 ou console) : {@code lancer <nombre> [rayon]} (autour de l'endroit d'où on la
 * tape ; {@code /execute positioned X Y Z run bots lancer ...} ailleurs), {@code retirer [nom]}, {@code etat} (bots,
 * temps d'un tick du serveur, actions depuis la dernière lecture), {@code action <action> <oui|non>},
 * {@code joueurs <oui|non>} (tirer aussi sur les vrais joueurs, non par défaut), {@code frappes <max>}.
 * <p>Ils portent l'étiquette {@value #TAG} : l'anticheat les ignore, le reste du serveur les traite comme des joueurs.
 * Connexion AuthMe faite pour eux (inscription à un mot de passe au hasard, puis connexion forcée). Noms {@code HzBot001}
 * à {@code HzBot200} ; leurs sauvegardes (joueur, statistiques, progrès) sont effacées à leur départ. Rien n'est gardé
 * d'un redémarrage à l'autre : tous partent à l'arrêt du serveur.
 */
public final class Bots {
    private Bots() {
    }

    public static final String TAG = "heraliz_bot";
    static final int MAX = 200;
    static final Reglages reglages = new Reglages();
    static final Reglages.Stats stats = new Reglages.Stats();

    /** Bot connecté, avec son lieu de vie (centre et rayon de ses déplacements). */
    private record Place(ServerLevel niveau, Vec3 centre, int rayon) {
    }

    private static final Map<String, BotJoueur> BOTS = new LinkedHashMap<>();
    private static final Map<UUID, Place> PLACES = new LinkedHashMap<>();
    /** Bots morts : nom -> tick du serveur où il repart. */
    private static final Map<String, Integer> A_RELANCER = new LinkedHashMap<>();
    /** Bots à préparer (connexion AuthMe faite, puis kit et départ) : uuid -> ticks restants. */
    private static final Map<UUID, Integer> A_PREPARER = new LinkedHashMap<>();
    private static final RandomSource R = RandomSource.create();

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(Bots::commandes);
        MinecraftForge.EVENT_BUS.addListener(Bots::tick);
        MinecraftForge.EVENT_BUS.addListener(Bots::mort);
        MinecraftForge.EVENT_BUS.addListener(Bots::arret);
    }

    /** Vrai pour un bot de charge (lu aussi par réflexion par d'autres mods, comme l'étiquette). */
    public static boolean estBot(@Nullable Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    // ------------------------------------------------------------------ commandes

    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("bots").requires(s -> s.hasPermission(4))
                .then(Commands.literal("lancer").then(Commands.argument("nombre", IntegerArgumentType.integer(1, MAX))
                        .executes(x -> lancer(x, 64))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(8, 2000)).executes(x ->
                                lancer(x, IntegerArgumentType.getInteger(x, "rayon"))))))
                .then(Commands.literal("retirer").executes(x -> {
                    int n = BOTS.size();
                    toutRetirer();
                    ok(x, "heralisz_utils.bots.retires", "%s bot(s) retiré(s).", n);
                    return n;
                }).then(Commands.argument("nom", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(BOTS.keySet(), b)).executes(x -> {
                            BotJoueur b = BOTS.get(StringArgumentType.getString(x, "nom"));
                            if (b == null) return 0;
                            retirer(b, true);
                            ok(x, "heralisz_utils.bots.retires", "%s bot(s) retiré(s).", 1);
                            return 1;
                        })))
                .then(Commands.literal("etat").executes(Bots::etat))
                .then(Commands.literal("joueurs").then(Commands.argument("oui", BoolArgumentType.bool()).executes(x -> {
                    reglages.joueurs = BoolArgumentType.getBool(x, "oui");
                    ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "joueurs", String.valueOf(reglages.joueurs));
                    return 1;
                })))
                .then(Commands.literal("frappes").then(Commands.argument("max", IntegerArgumentType.integer(0, 50)).executes(x -> {
                    reglages.frappesMax = IntegerArgumentType.getInteger(x, "max");
                    ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "frappes", String.valueOf(reglages.frappesMax));
                    return 1;
                })))
                .then(Commands.literal("action").then(Commands.argument("action", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("tir", "grenades", "fumigenes", "soins", "frappes",
                                "largages", "fouille", "course", "reapparition"), b))
                        .then(Commands.argument("oui", BoolArgumentType.bool()).executes(Bots::action)))));
    }

    private static int lancer(CommandContext<CommandSourceStack> x, int rayon) {
        CommandSourceStack s = x.getSource();
        int voulu = IntegerArgumentType.getInteger(x, "nombre");
        int faits = 0;
        for (int i = 1; i <= MAX && faits < voulu; i++) {
            String nom = String.format("HzBot%03d", i);
            if (BOTS.containsKey(nom) || A_RELANCER.containsKey(nom) || s.getServer().getPlayerList().getPlayerByName(nom) != null) continue;
            BotJoueur b = creer(s.getServer(), nom, new Place(s.getLevel(), s.getPosition(), rayon));
            if (b == null) break;
            faits++;
        }
        ok(x, "heralisz_utils.bots.lances", "%s bot(s) lancé(s) (%s en tout, rayon %s).", faits, BOTS.size(), rayon);
        return faits;
    }

    private static int action(CommandContext<CommandSourceStack> x) {
        String a = StringArgumentType.getString(x, "action");
        boolean v = BoolArgumentType.getBool(x, "oui");
        switch (a) {
            case "tir" -> reglages.tir = v;
            case "grenades" -> reglages.grenades = v;
            case "fumigenes" -> reglages.fumigenes = v;
            case "soins" -> reglages.soins = v;
            case "frappes" -> reglages.frappes = v;
            case "largages" -> reglages.largages = v;
            case "fouille" -> reglages.fouille = v;
            case "course" -> reglages.course = v;
            case "reapparition" -> reglages.reapparition = v;
            default -> {
                x.getSource().sendFailure(Component.translatableWithFallback("heralisz_utils.bots.action_inconnue", "Action inconnue : %s", a));
                return 0;
            }
        }
        ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", a, String.valueOf(v));
        return 1;
    }

    private static int etat(CommandContext<CommandSourceStack> x) {
        MinecraftServer srv = x.getSource().getServer();
        long vivants = BOTS.values().stream().filter(Entity::isAlive).count();
        double mspt = srv.getAverageTickTime();
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 1e-3));
        double sec = Math.max(1, (System.currentTimeMillis() - stats.depuis) / 1000.0);
        int entites = 0;
        for (ServerLevel l : srv.getAllLevels()) for (Entity ignored : l.getAllEntities()) entites++;
        Runtime rt = Runtime.getRuntime();
        long memo = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024), memoMax = rt.maxMemory() / (1024 * 1024);
        ok(x, "heralisz_utils.bots.etat1", "Bots : %s (%s en vie), joueurs connectés : %s, entités : %s",
                BOTS.size(), vivants, srv.getPlayerCount(), entites);
        ok(x, "heralisz_utils.bots.etat2", "Serveur : %s ms par tick (%s TPS), mémoire %s / %s Mo",
                String.format("%.1f", mspt), String.format("%.1f", tps), memo, memoMax);
        ok(x, "heralisz_utils.bots.etat3", "En %s s : %s tirs, %s lancers, %s soins, %s fouilles, %s frappes, %s largages, %s morts",
                (long) sec, stats.tirs, stats.lancers, stats.soins, stats.fouilles, stats.frappes, stats.largages, stats.morts);
        HeraliszUtils.LOG.info("[bots] etat : {} bots, {} ms/tick, {} entites, {} tirs/{} s", BOTS.size(), String.format("%.1f", mspt), entites,
                stats.tirs, (long) sec);
        stats.zero();
        return 1;
    }

    private static void ok(CommandContext<CommandSourceStack> x, String cle, String fr, Object... args) {
        Component c = Component.translatableWithFallback(cle, fr, args).withStyle(ChatFormatting.GRAY);
        x.getSource().sendSuccess(() -> c, false);
    }

    // ------------------------------------------------------------------ vie des bots

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || (BOTS.isEmpty() && A_RELANCER.isEmpty())) return;
        MinecraftServer srv = e.getServer();
        int t = srv.getTickCount();
        // préparation : connexion AuthMe, puis kit, survie et départ sur la carte
        if (!A_PREPARER.isEmpty()) {
            for (var it = A_PREPARER.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                BotJoueur b = parUuid(en.getKey());
                if (b == null) {
                    it.remove();
                    continue;
                }
                int reste = en.getValue() - 1;
                if (reste == 30) AuthMeBots.connecter(b);
                if (reste > 0) {
                    en.setValue(reste);
                    continue;
                }
                it.remove();
                partir(b);
            }
        }
        // morts : le corps reste (cadavre, fouille), le bot repart 10 s après sous le même nom
        if (t % 20 == 0) {
            for (BotJoueur b : new ArrayList<>(BOTS.values())) {
                if (!b.isAlive() && !A_RELANCER.containsKey(b.getGameProfile().getName())) {
                    Place p = PLACES.get(b.getUUID());
                    String nom = b.getGameProfile().getName();
                    retirer(b, false);
                    if (reglages.reapparition && p != null) {
                        A_RELANCER.put(nom, t + 200);
                        RELANCE_PLACE.put(nom, p);
                    }
                }
            }
            for (var it = A_RELANCER.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                if (en.getValue() > t) continue;
                it.remove();
                Place p = RELANCE_PLACE.remove(en.getKey());
                if (p != null && BOTS.size() < MAX) creer(srv, en.getKey(), p);
            }
        }
    }

    private static final Map<String, Place> RELANCE_PLACE = new LinkedHashMap<>();

    private static void mort(LivingDeathEvent e) {
        if (estBot(e.getEntity())) stats.morts++;
    }

    private static void arret(ServerStoppingEvent e) {
        toutRetirer();
    }

    private static void toutRetirer() {
        for (BotJoueur b : new ArrayList<>(BOTS.values())) retirer(b, true);
        A_RELANCER.clear();
        RELANCE_PLACE.clear();
        A_PREPARER.clear();
    }

    /** Envoie le bot sur la carte : survie, kit, point au hasard dans son rayon, posé sur le sol. */
    private static void partir(BotJoueur b) {
        Place p = PLACES.get(b.getUUID());
        if (p == null) return;
        b.setGameMode(GameType.SURVIVAL);
        Kit.donner(b, R);
        b.setHealth(b.getMaxHealth());
        b.getFoodData().setFoodLevel(20);
        ServerLevel lv = p.niveau();
        for (int i = 0; i < 8; i++) {
            double a = R.nextDouble() * Math.PI * 2, rr = R.nextDouble() * p.rayon();
            int x = Mth.floor(p.centre().x + Math.cos(a) * rr), z = Mth.floor(p.centre().z + Math.sin(a) * rr);
            int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!lv.getFluidState(new BlockPos(x, y - 1, z)).isEmpty() && i < 7) continue;
            b.teleportTo(lv, x + 0.5, y, z + 0.5, R.nextFloat() * 360f, 0f);
            return;
        }
    }

    static Vec3 centre(BotJoueur b) {
        Place p = PLACES.get(b.getUUID());
        return p == null ? b.position() : p.centre();
    }

    static int rayon(BotJoueur b) {
        Place p = PLACES.get(b.getUUID());
        return p == null ? 64 : p.rayon();
    }

    static void erreur(BotJoueur b, RuntimeException ex) {
        HeraliszUtils.LOG.warn("[bots] erreur dans le cerveau de {}", b.getGameProfile().getName(), ex);
    }

    @Nullable
    private static BotJoueur parUuid(UUID id) {
        for (BotJoueur b : BOTS.values()) if (b.getUUID().equals(id)) return b;
        return null;
    }

    // ------------------------------------------------------------------ connexion et départ

    /** Crée le bot et l'inscrit comme une connexion ; sa sauvegarde d'avant (s'il en reste une) est d'abord effacée. */
    @Nullable
    private static BotJoueur creer(MinecraftServer s, String nom, Place place) {
        try {
            UUID id = UUIDUtil.createOfflinePlayerUUID(nom);
            effacerFichiers(s, id);
            GameProfile g = new GameProfile(id, nom);
            BotJoueur b = new BotJoueur(s, place.niveau(), g);
            Connection c = new Connection(PacketFlow.SERVERBOUND);
            // canal factice : tout paquet envoyé au bot est jeté
            EmbeddedChannel canal = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    ReferenceCountUtil.release(msg);
                    promise.setSuccess();
                }
            });
            for (Field f : Connection.class.getDeclaredFields()) {
                if (f.getType() == Channel.class) {
                    f.setAccessible(true);
                    f.set(c, canal);
                } else if (f.getType() == SocketAddress.class) {
                    f.setAccessible(true);
                    f.set(c, new InetSocketAddress(InetAddress.getLoopbackAddress(), 25565));
                }
            }
            PLACES.put(id, place);
            BOTS.put(nom, b);
            s.getPlayerList().placeNewPlayer(c, b);
            b.addTag(TAG);   // une sauvegarde chargée remplace les étiquettes
            A_PREPARER.put(id, 60);
            return b;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszUtils.LOG.error("[bots] creation de {} impossible", nom, ex);
            BOTS.remove(nom);
            return null;
        }
    }

    /** Déconnecte le bot (comme un joueur qui quitte : son corps et ses objets restent s'il est mort) et efface ses fichiers. */
    private static void retirer(BotJoueur b, boolean oublier) {
        String nom = b.getGameProfile().getName();
        BOTS.remove(nom);
        A_PREPARER.remove(b.getUUID());
        if (oublier) {
            PLACES.remove(b.getUUID());
            A_RELANCER.remove(nom);
            RELANCE_PLACE.remove(nom);
        }
        try {
            if (!b.hasDisconnected()) b.connection.onDisconnect(Component.literal("bots"));
        } catch (RuntimeException ex) {
            HeraliszUtils.LOG.debug("[bots] {} deja parti", nom, ex);
        }
        effacerFichiers(b.server, b.getUUID());
    }

    private static void effacerFichiers(MinecraftServer s, UUID id) {
        String u = id.toString();
        for (Path p : List.of(s.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(u + ".dat"),
                s.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(u + ".dat_old"),
                s.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(u + ".json"),
                s.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(u + ".json"))) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ex) {
                HeraliszUtils.LOG.debug("[bots] {} pas efface", p, ex);
            }
        }
    }

    // ------------------------------------------------------------------ frappes aériennes et largages

    private static boolean frappesCherche;
    @Nullable
    private static Method lancerFrappe, typeParNom, frappesActives;

    /**
     * Appelle une frappe aérienne (au hasard parmi bombardement, précision, sous-munitions, mitraillage, fumée, gaz) ou
     * un largage sur {@code cible}, comme le désignateur d'un joueur (HeraliZ Airstrike, par réflexion : sans lui, rien).
     */
    static void appelerFrappe(BotJoueur b, Vec3 cible, boolean largage) {
        if (!frappesCherche) {
            frappesCherche = true;
            try {
                Class<?> m = Class.forName("fr.heraliz.airstrike.strike.StrikeManager");
                Class<?> t = Class.forName("fr.heraliz.airstrike.strike.StrikeType");
                typeParNom = t.getMethod("byName", String.class);
                lancerFrappe = m.getMethod("launch", ServerLevel.class, t, Vec3.class, Vec3.class, ServerPlayer.class);
                frappesActives = m.getMethod("countActive", UUID.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                lancerFrappe = null;
                HeraliszUtils.LOG.info("[bots] HeraliZ Airstrike absent : pas de frappes ni de largages");
            }
        }
        if (lancerFrappe == null || typeParNom == null || frappesActives == null) return;
        try {
            if (((Number) frappesActives.invoke(null, (Object) null)).intValue() >= reglages.frappesMax) return;
            String[] types = {"bombing", "precision", "cluster", "gun_run", "smoke", "gas"};
            Object type = typeParNom.invoke(null, largage ? "supply" : types[R.nextInt(types.length)]);
            if (type == null) return;
            ServerLevel lv = b.serverLevel();
            int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(cible.x), Mth.floor(cible.z));
            lancerFrappe.invoke(null, lv, type, new Vec3(cible.x, y, cible.z), b.position(), b);
            if (largage) stats.largages++;
            else stats.frappes++;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszUtils.LOG.warn("[bots] frappe de {} impossible", b.getGameProfile().getName(), ex);
        }
    }
}
