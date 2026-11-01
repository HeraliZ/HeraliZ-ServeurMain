package fr.heraliz.banc;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bots de charge (demande du propriétaire, 24/10) : de faux joueurs qui jouent comme de vrais joueurs pour éprouver
 * le serveur (tirs TaCZ, grenades, flashs, fumigènes, soins, frappes aériennes, largages, fouille des conteneurs,
 * course à travers la carte, morts et cadavres), sans aucun jeu de joueur derrière : ils ne pèsent que sur le serveur.
 * <p>Commande {@code /bots} (op niveau 4 ou console) : {@code lancer <nombre> [rayon]} (autour de l'endroit d'où on la
 * tape ; {@code /execute positioned X Y Z run bots lancer ...} ailleurs), {@code retirer [nom]}, {@code etat},
 * {@code action <action> <oui|non>}, {@code joueurs <oui|non>} (tirer aussi sur les vrais joueurs, non par défaut),
 * {@code frappes <max>}, {@code commandes <secondes>} ({@link Commandes}), {@code mesure <secondes>|stop}
 * ({@link Mesure}).
 * <p>Banc réaliste (01/11, 1.2.0) :
 * <ul>
 *     <li>chaque bot est un {@link ServerPlayer} ordinaire relié à une connexion factice, « joué » par un
 *     {@link Pilote} : comme la connexion d'un vrai joueur, le banc fait le tick de la connexion ({@code doTick}) et
 *     envoie les « paquets de position » ({@code ChunkMap.move} chaque fois qu'il bouge ou tourne la tête, au moins
 *     une fois par seconde) ;</li>
 *     <li>mort : écran de mort de 4 à 12 s, puis le même clic « Réapparaître » qu'un vrai joueur
 *     ({@code ServerboundClientCommandPacket}, donc {@code PlayerList.respawn} et nos règles de réapparition), le point
 *     de réveil du spawn (« Au camp », {@link Camp}), 15 à 45 s au camp, puis la vraie borne de départ (avant : une
 *     déconnexion puis une connexion complète 10 s après, 11 % du profil) ;</li>
 *     <li>départs : le tronçon d'arrivée est demandé à Minecraft (ticket, chargé en tâche de fond), le bot part quand il
 *     est prêt (avant : {@code getChunk} sur le fil du serveur, blocages de 7 s à l'arrivée des bots) ;</li>
 *     <li>rythmes réalistes ({@link Rythmes}), zones de groupe et fenêtres de combat ({@link Zones}), terrain créé d'avance
 *     et tickets ({@link Troncons}), pics de téléportations ({@link Pics}), coût du banc à part ({@link Couts}) :
 *     {@code camp}, {@code zone <rayon> <arrivée>}, {@code pregenerer <rayon>|stop}, {@code rythmes ...|off},
 *     {@code gamerule <règle> <valeur>} (dans tous les mondes), {@code pic <cas> <nombre>}, {@code troncons},
 *     {@code jvm}.</li>
 * </ul>
 * Ils portent l'étiquette {@value #TAG} : l'anticheat les ignore, le reste du serveur les traite comme des joueurs.
 * Connexion AuthMe faite pour eux. Noms {@code HzBot001} à {@code HzBot200} ; leurs sauvegardes sont effacées à leur
 * départ définitif (gardées pendant une déconnexion des rythmes : ils reviennent là où ils étaient). Rien n'est gardé
 * d'un redémarrage à l'autre : tous partent à l'arrêt du serveur.
 */
public final class Bots {
    private Bots() {
    }

    public static final String TAG = fr.heraliz.noyau.api.Bots.TAG;
    static final int MAX = 200;
    static final Reglages reglages = new Reglages();
    static final Reglages.Stats stats = new Reglages.Stats();

    private static final Map<String, Pilote> PILOTES = new LinkedHashMap<>();
    private static final Map<UUID, Pilote> PAR_ID = new HashMap<>();
    private static Pilote[] tableau = new Pilote[0];
    private static boolean change;
    private static final RandomSource R = RandomSource.create();
    private static final String TUTO = "crac:tuto";

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(Bots::commandes);
        MinecraftForge.EVENT_BUS.addListener(Bots::tick);
        MinecraftForge.EVENT_BUS.addListener(Bots::mort);
        MinecraftForge.EVENT_BUS.addListener(Bots::arret);
        MinecraftForge.EVENT_BUS.addListener(Bots::changementMonde);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingHurtEvent e) -> Rythmes.degats(e));
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> HeralizBanc.LOG.info("[banc] JVM {}", Jvm.infos(e.getServer())));
        Scenario.init();
        Mesure.init();
        Pics.init();
        Troncons.init();
        // seul un vrai bot garde l'étiquette : le noyau la retire aux autres (connexion et chaque seconde)
        fr.heraliz.noyau.api.Bots.brancher(Bots::vraiBot);
    }

    /** Vrai pour un bot de charge (lu aussi par réflexion par d'autres mods, comme l'étiquette). */
    public static boolean estBot(@Nullable Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    /** Un bot de ce banc : son pseudo est réservé, et il est relié à la connexion factice faite pour lui. */
    static boolean vraiBot(ServerPlayer p) {
        Pilote x = PAR_ID.get(p.getUUID());
        return x != null && x.canal != null && p.connection != null && p.connection.connection == x.canal;
    }

    /** Les pilotes (tableau gardé, refait seulement quand la liste change). */
    static Pilote[] pilotes() {
        if (change) {
            tableau = PILOTES.values().toArray(new Pilote[0]);
            change = false;
        }
        return tableau;
    }

    // ------------------------------------------------------------------ commandes

    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("bots").requires(s -> s.hasPermission(4))
                .then(Commands.literal("lancer").then(Commands.argument("nombre", IntegerArgumentType.integer(1, MAX))
                        .executes(x -> lancer(x, 64))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(8, 2000)).executes(x ->
                                lancer(x, IntegerArgumentType.getInteger(x, "rayon"))))))
                .then(Commands.literal("retirer").executes(x -> {
                    int n = PILOTES.size();
                    toutRetirer(x.getSource().getServer());
                    ok(x, "heralisz_utils.bots.retires", "%s bot(s) retiré(s).", n);
                    return n;
                }).then(Commands.argument("nom", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(PILOTES.keySet(), b)).executes(x -> {
                            Pilote p = PILOTES.get(StringArgumentType.getString(x, "nom"));
                            if (p == null) return 0;
                            retirer(x.getSource().getServer(), p);
                            ok(x, "heralisz_utils.bots.retires", "%s bot(s) retiré(s).", 1);
                            return 1;
                        })))
                .then(Commands.literal("etat").executes(Bots::etat))
                .then(Commands.literal("scenario")
                        .then(Commands.literal("stop").executes(x -> Scenario.arreter(x.getSource())))
                        .then(Commands.argument("nombre", IntegerArgumentType.integer(1, MAX))
                                .executes(x -> Scenario.lancer(x.getSource(), IntegerArgumentType.getInteger(x, "nombre"), 3, 96))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 30))
                                        .executes(x -> Scenario.lancer(x.getSource(), IntegerArgumentType.getInteger(x, "nombre"),
                                                IntegerArgumentType.getInteger(x, "minutes"), 96))
                                        .then(Commands.argument("rayon", IntegerArgumentType.integer(16, 2000))
                                                .executes(x -> Scenario.lancer(x.getSource(), IntegerArgumentType.getInteger(x, "nombre"),
                                                        IntegerArgumentType.getInteger(x, "minutes"), IntegerArgumentType.getInteger(x, "rayon")))))))
                .then(Commands.literal("mesure")
                        .then(Commands.literal("stop").executes(x -> Mesure.arreter(x.getSource())))
                        .then(Commands.argument("secondes", IntegerArgumentType.integer(10, 3600)).executes(x -> {
                            int sec = IntegerArgumentType.getInteger(x, "secondes");
                            Mesure.lancer(x.getSource(), sec);
                            ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "mesure", sec + " s");
                            return 1;
                        })))
                .then(Commands.literal("commandes").then(Commands.argument("secondes", IntegerArgumentType.integer(0, 3600)).executes(x -> {
                    int sec = IntegerArgumentType.getInteger(x, "secondes");
                    int n = Commandes.regler(sec);
                    ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "commandes", sec == 0 ? "0" : sec + " s (" + n + ")");
                    return 1;
                })))
                .then(Commands.literal("reseau").then(Commands.argument("oui", BoolArgumentType.bool()).executes(x -> {
                    Reseau.actif = BoolArgumentType.getBool(x, "oui");
                    ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "reseau", String.valueOf(Reseau.actif));
                    return 1;
                })))
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
                                "largages", "fouille", "course", "reapparition", "construction"), b))
                        .then(Commands.argument("oui", BoolArgumentType.bool()).executes(Bots::action))))
                // ---- banc réaliste (01/11)
                .then(Commands.literal("jvm").executes(x -> {
                    String j = Jvm.infos(x.getSource().getServer()).toString();
                    HeralizBanc.LOG.info("[banc] JVM {}", j);
                    ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "jvm", j);
                    return 1;
                }))
                .then(Commands.literal("camp").executes(x -> {
                    String r = Camp.preparer(x.getSource().getServer());
                    HeralizBanc.LOG.info("[banc] CAMP {}", r);
                    ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "camp", r);
                    return 1;
                }))
                .then(Commands.literal("zone").then(Commands.argument("rayon", IntegerArgumentType.integer(8, 2000))
                        .then(Commands.argument("arrivee", IntegerArgumentType.integer(0, 2000)).executes(x -> {
                            Zones.Zone z = Zones.ajouter(x.getSource().getLevel(), x.getSource().getPosition(),
                                    IntegerArgumentType.getInteger(x, "rayon"), IntegerArgumentType.getInteger(x, "arrivee"));
                            String r = String.format(java.util.Locale.ROOT, "%d : %.0f %.0f, rayon %d, arrivée de la borne %d", z.n() + 1,
                                    z.centre().x, z.centre().z, z.rayon(), z.arrivee());
                            HeralizBanc.LOG.info("[banc] zone {}", r);
                            ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "zone", r);
                            return 1;
                        }))))
                .then(Commands.literal("pregenerer")
                        .then(Commands.literal("stop").executes(x -> {
                            int n = Troncons.arreter();
                            ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "pregenerer", "stop (" + n + ")");
                            return 1;
                        }))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(16, 4000)).executes(x -> {
                            Vec3 c = x.getSource().getPosition();
                            int n = Troncons.pregenerer(x.getSource().getLevel(), Mth.floor(c.x), Mth.floor(c.z), IntegerArgumentType.getInteger(x, "rayon"));
                            ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "pregenerer", n + " (" + Troncons.enAttente() + ")");
                            return 1;
                        })))
                .then(Commands.literal("rythmes")
                        .then(Commands.literal("off").executes(x -> {
                            Rythmes.couper();
                            ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "rythmes", "off");
                            return 1;
                        }))
                        .then(Commands.argument("morts", DoubleArgumentType.doubleArg(0, 1000))
                                .then(Commands.argument("connexions", DoubleArgumentType.doubleArg(0, 1000))
                                        .then(Commands.argument("voyages", DoubleArgumentType.doubleArg(0, 1000))
                                                .then(Commands.argument("combat", IntegerArgumentType.integer(0, 100)).executes(x -> {
                                                    Rythmes.regler(DoubleArgumentType.getDouble(x, "morts"), DoubleArgumentType.getDouble(x, "connexions"),
                                                            DoubleArgumentType.getDouble(x, "voyages"), IntegerArgumentType.getInteger(x, "combat"));
                                                    String r = String.format(java.util.Locale.ROOT, "%.1f morts, %.1f connexions, %.1f voyages par minute pour 150 joueurs, combat %d %%",
                                                            Rythmes.morts, Rythmes.connexions, Rythmes.voyages, Rythmes.combat);
                                                    HeralizBanc.LOG.info("[banc] rythmes : {}", r);
                                                    ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "rythmes", r);
                                                    return 1;
                                                }))))))
                .then(Commands.literal("gamerule").then(Commands.argument("regle", StringArgumentType.word())
                        .then(Commands.argument("valeur", StringArgumentType.word()).executes(Bots::gamerule))))
                .then(Commands.literal("pic").then(Commands.argument("cas", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("extraction", "spawn", "borne", "connexion"), b))
                        .then(Commands.argument("nombre", IntegerArgumentType.integer(1, MAX)).executes(x -> {
                            String cas = StringArgumentType.getString(x, "cas");
                            if (!Pics.demander(cas, IntegerArgumentType.getInteger(x, "nombre"))) {
                                x.getSource().sendFailure(Component.translatableWithFallback("heralisz_utils.bots.action_inconnue", "Action inconnue : %s", cas));
                                return 0;
                            }
                            ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "pic", cas);
                            return 1;
                        }))))
                .then(Commands.literal("troncons").executes(x -> {
                    HeralizBanc.LOG.info("[banc] TRONCONS {}", Troncons.releve(x.getSource().getServer()));
                    ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", "troncons", "diag");
                    return 1;
                })));
    }

    private static int lancer(CommandContext<CommandSourceStack> x, int rayon) {
        CommandSourceStack s = x.getSource();
        int faits = lancer(s.getServer(), s.getLevel(), s.getPosition(), IntegerArgumentType.getInteger(x, "nombre"), rayon);
        ok(x, "heralisz_utils.bots.lances", "%s bot(s) lancé(s) (%s en tout, rayon %s).", faits, PILOTES.size(), rayon);
        return faits;
    }

    /** Lance jusqu'à {@code voulu} bots autour de {@code centre} (zone de groupe s'il y en a une là) ; nombre lancé. */
    static int lancer(MinecraftServer srv, ServerLevel niveau, Vec3 centre, int voulu, int rayon) {
        Place place = Zones.place(niveau, centre, rayon);
        int faits = 0;
        for (int i = 1; i <= MAX && faits < voulu; i++) {
            String nom = String.format("HzBot%03d", i);
            if (PILOTES.containsKey(nom) || srv.getPlayerList().getPlayerByName(nom) != null) continue;
            UUID id = UUIDUtil.createOfflinePlayerUUID(nom);
            Pilote p = new Pilote(nom, id, place);
            PILOTES.put(nom, p);
            PAR_ID.put(id, p);
            change = true;
            if (!creer(srv, p)) {
                PILOTES.remove(nom);
                PAR_ID.remove(id);
                break;
            }
            faits++;
        }
        return faits;
    }

    /** Bots connectés. */
    static int nombre() {
        int n = 0;
        for (Pilote p : pilotes()) if (p.enLigne()) n++;
        return n;
    }

    /** Tous les bots, hors ligne compris (base des rythmes). */
    static int total() {
        return PILOTES.size();
    }

    static long vivants() {
        long n = 0;
        for (Pilote p : pilotes()) if (p.vivant()) n++;
        return n;
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
            case "construction" -> reglages.construction = v;
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
        double mspt = srv.getAverageTickTime();
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 1e-3));
        double sec = Math.max(1, (System.currentTimeMillis() - stats.depuis) / 1000.0);
        int entites = 0;
        for (ServerLevel l : srv.getAllLevels()) for (Entity ignored : l.getAllEntities()) entites++;
        Runtime rt = Runtime.getRuntime();
        long memo = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024), memoMax = rt.maxMemory() / (1024 * 1024);
        Map<Pilote.Etat, Integer> etats = new EnumMap<>(Pilote.Etat.class);
        for (Pilote p : pilotes()) etats.merge(p.etat, 1, Integer::sum);
        ok(x, "heralisz_utils.bots.etat1", "Bots : %s (%s en vie), joueurs connectés : %s, entités : %s",
                PILOTES.size(), vivants(), srv.getPlayerCount(), entites);
        ok(x, "heralisz_utils.bots.etat2", "Serveur : %s ms par tick (%s TPS), mémoire %s / %s Mo",
                String.format("%.1f", mspt), String.format("%.1f", tps), memo, memoMax);
        ok(x, "heralisz_utils.bots.etat3", "En %s s : %s tirs, %s lancers, %s soins, %s fouilles, %s frappes, %s largages, %s morts",
                (long) sec, stats.tirs, stats.lancers, stats.soins, stats.fouilles, stats.frappes, stats.largages, stats.morts);
        String net = Reseau.ligne(Reseau.relever(), sec, nombre());
        ok(x, "heralisz_utils.bots.etat4", "%s", net);
        HeralizBanc.LOG.info("[bots] etat : {} bots ({}), {} ms/tick, {} entites, {} tirs/{} s, facteur des dégâts entre bots {} ; {}", PILOTES.size(), etats,
                String.format("%.1f", mspt), entites, stats.tirs, (long) sec, String.format(java.util.Locale.ROOT, "%.2f", Rythmes.facteur), net);
        stats.zero();
        return 1;
    }

    /** Même règle dans tous les mondes (la commande gamerule de Mohist ne règle que le monde d'où on la tape). */
    private static int gamerule(CommandContext<CommandSourceStack> x) {
        String nom = StringArgumentType.getString(x, "regle"), val = StringArgumentType.getString(x, "valeur");
        MinecraftServer srv = x.getSource().getServer();
        StringBuilder lu = new StringBuilder();
        int faits = 0, mondes = 0;
        for (ServerLevel l : srv.getAllLevels()) {
            mondes++;
            int r = srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withLevel(l).withSuppressedOutput(), "gamerule " + nom + " " + val);
            if (r > 0) faits++;
            lu.append(lu.length() > 0 ? ", " : "").append(l.dimension().location()).append('=').append(lire(l, nom));
        }
        HeralizBanc.LOG.info("[banc] GAMERULE {} {} : {} monde(s) sur {} ({})", nom, val, faits, mondes, lu);
        ok(x, "heralisz_utils.bots.reglage", "Réglage des bots : %s = %s", nom, lu.toString());
        return faits;
    }

    private static String lire(ServerLevel l, String nom) {
        String[] v = {"?"};
        GameRules regles = l.getGameRules();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> cle, GameRules.Type<T> type) {
                if (cle.getId().equals(nom)) v[0] = String.valueOf(regles.getRule(cle));
            }
        });
        return v[0];
    }

    private static void ok(CommandContext<CommandSourceStack> x, String cle, String fr, Object... args) {
        Component c = Component.translatableWithFallback(cle, fr, args).withStyle(ChatFormatting.GRAY);
        x.getSource().sendSuccess(() -> c, false);
    }

    // ------------------------------------------------------------------ vie des bots

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PILOTES.isEmpty()) return;
        long t0 = System.nanoTime(), j0 = Couts.joueurs();
        MinecraftServer srv = e.getServer();
        int t = srv.getTickCount();
        Zones.tick(t);
        Rythmes.tick(srv, t);
        Pics.tick(srv);
        for (Pilote p : pilotes()) {
            try {
                piloter(srv, p, t);
            } catch (RuntimeException ex) {
                HeralizBanc.LOG.warn("[bots] erreur pour {}", p.nom, ex);
            }
        }
        Couts.banc += (System.nanoTime() - t0) - (Couts.joueurs() - j0);
    }

    private static void piloter(MinecraftServer srv, Pilote p, int t) {
        if (p.etat == Pilote.Etat.HORS_LIGNE) {
            if (t >= p.retour) reconnecter(srv, p);
            return;
        }
        ServerPlayer j = p.joueur;
        if (j == null) return;
        if (j.hasDisconnected()) {
            // déconnecté par autre chose que le banc (expulsé) : revient dans 30 s
            HeralizBanc.LOG.info("[bots] {} déconnecté par le serveur : retour dans 30 s", p.nom);
            p.joueur = null;
            p.canal = null;
            p.etat = Pilote.Etat.HORS_LIGNE;
            p.retour = t + 600;
            return;
        }
        if (!j.isAlive() && p.etat != Pilote.Etat.MORT && p.etat != Pilote.Etat.PREPARATION) mourir(p);
        switch (p.etat) {
            case PREPARATION -> preparation(p);
            case DEPART -> departer(p);
            case EN_JEU -> enJeu(p, j, t);
            case MORT -> {
                if (--p.attente <= 0) {
                    if (reglages.reapparition) reapparaitre(srv, p);
                    else p.attente = 200;
                }
            }
            case CAMP -> camp(srv, p);
            case BORNE -> {
                p.cerveau.camp(null, false);
                // la borne n'a rien donné (pas de point d'arrivée sûr, départ refusé) : il rentre par lui-même
                if (--p.attente <= 0) allerVers(p, p.origine, Rythmes.RETOURS);
            }
            default -> {
            }
        }
        // ce que ferait sa connexion : paquets de position, tick de la connexion
        j = p.joueur;
        if (j == null || j.hasDisconnected()) return;
        if (j.isAlive()) position(p, j);
        long t1 = System.nanoTime();
        j.doTick();
        Couts.ajouter(Couts.TICK_CONNEXION, t1);
    }

    private static void preparation(Pilote p) {
        ServerPlayer j = p.joueur;
        if (--p.attente == 30) AuthMeBots.connecter(j);
        if (p.attente > 0) return;
        if (!j.isAlive()) {
            mourir(p);
            return;
        }
        if (p.neuf) {
            p.neuf = false;
            j.setGameMode(GameType.SURVIVAL);
            Kit.donner(j, R);
            j.setHealth(j.getMaxHealth());
            j.getFoodData().setFoodLevel(20);
            allerVers(p, p.zone, -1);
            return;
        }
        // reconnexion : il reprend là où il était
        p.etat = Pilote.Etat.EN_JEU;
        placer(p, j, false);
    }

    private static void enJeu(Pilote p, ServerPlayer j, int t) {
        p.cerveau.tick();
        // téléporté loin dans le même monde par autre chose que le banc (/home, autre mod) : vit là où il est arrivé
        if ((t + p.id.hashCode()) % 20 == 0 && p.etat == Pilote.Etat.EN_JEU && j.serverLevel() == p.zone.niveau()) {
            double r = p.zone.rayon() + 128;
            if (j.position().distanceToSqr(p.zone.centre().x, j.getY(), p.zone.centre().z) > r * r) {
                placer(p, j, true);
                Rythmes.compter(Rythmes.AUTRES);
            }
        }
    }

    /** Lieu de vie d'après là où il est : camp, tutoriel, zone de groupe la plus proche, ou autour de lui. */
    private static void placer(Pilote p, ServerPlayer j, boolean loin) {
        ServerLevel lv = j.serverLevel();
        if (Camp.auCamp(j)) {
            entrerAuCamp(p);
            return;
        }
        if (TUTO.equals(lv.dimension().location().toString())) {
            p.tuto = true;
            p.zone = new Place(lv, j.position(), 24);
            return;
        }
        Zones.Zone z = Zones.plusProche(lv, j.position());
        if (z != null) {
            double r = Math.max(z.rayon(), z.arrivee()) + 64;
            if (j.position().distanceToSqr(z.centre().x, j.getY(), z.centre().z) <= r * r) {
                p.zone = z.place();
                return;
            }
        }
        if (loin || lv != p.zone.niveau()) p.zone = lv == p.origine.niveau() && !loin ? p.origine : new Place(lv, j.position(), 32);
    }

    private static void mourir(Pilote p) {
        p.etat = Pilote.Etat.MORT;
        p.attente = 80 + R.nextInt(160);   // écran de mort : 4 à 12 s
        p.destination = null;
        p.retenu = false;
    }

    /** Le clic « Réapparaître » d'un vrai joueur, puis « Au camp » (point de réveil du spawn). */
    private static void reapparaitre(MinecraftServer srv, Pilote p) {
        ServerPlayer j = p.joueur;
        long t0 = System.nanoTime();
        try {
            j.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        } catch (RuntimeException ex) {
            HeralizBanc.LOG.warn("[bots] réapparition de {} impossible", p.nom, ex);
        }
        ServerPlayer n = srv.getPlayerList().getPlayer(p.id);
        if (n == null || n.hasDisconnected() || n.isDeadOrDying()) {
            Couts.ajouter(Couts.REAPPARITIONS, t0);
            p.attente = 100;   // réessaie dans 5 s
            return;
        }
        p.joueur = n;
        n.addTag(TAG);
        Kit.donner(n, R);
        n.getFoodData().setFoodLevel(20);
        p.cerveau.renaitre();
        p.depuisPosition = 20;
        // « Au camp » : le serveur y pose les vrais joueurs pendant leur réapparition (les bots en sont exclus par leur
        // étiquette) : le banc le fait aussitôt, dans le même tick
        boolean camp = Camp.versSpawn(n);
        Couts.ajouter(Couts.REAPPARITIONS, t0);
        Rythmes.compter(Rythmes.REAPPARITIONS);
        if (camp) entrerAuCamp(p);
        else allerVers(p, p.origine, Rythmes.RETOURS);
    }

    private static void entrerAuCamp(Pilote p) {
        p.etat = Pilote.Etat.CAMP;
        p.attente = 300 + R.nextInt(600);   // 15 à 45 s au camp
        Vec3 c = Camp.point;
        if (c != null) {
            double a = R.nextDouble() * Math.PI * 2, r = 2 + R.nextDouble() * 8;
            p.pointCamp = new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
        } else {
            p.pointCamp = null;
        }
    }

    /** Pics : envoie le bot au camp ({@code retenir} : il y attend le déclenchement) ; faux si impossible. */
    static boolean allerAuCamp(Pilote p, boolean retenir) {
        ServerPlayer j = p.joueur;
        if (j == null || !j.isAlive()) return false;
        Pilote.Etat avant = p.etat;
        entrerAuCamp(p);   // avant la téléportation : son changement de monde n'est pas compté comme une extraction
        p.retenu = retenir;
        if (!Camp.auCamp(j)) {
            long t0 = System.nanoTime();
            boolean ok = Camp.versSpawn(j);
            Couts.ajouter(Couts.TELEPORTATIONS, t0);
            if (!ok) {
                p.etat = avant;
                p.retenu = false;
                return false;
            }
        }
        return true;
    }

    /** Pendant le déclenchement d'un pic : les changements de monde ne comptent pas dans les rythmes (déjà comptés). */
    static boolean dansUnPic;

    private static void camp(MinecraftServer srv, Pilote p) {
        p.cerveau.camp(p.pointCamp, p.retenu || p.attente > 40);   // il va à sa place, puis ne bouge plus (borne)
        if (p.retenu) return;
        if (--p.attente > 0) return;
        if (p.tuto) {
            // retour au tutoriel des bases, comme il y était allé (console)
            int r = srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), "tuto lancer " + p.nom);
            p.etat = Pilote.Etat.BORNE;
            p.attente = r > 0 ? 400 : 1;
            return;
        }
        if (!borne(p)) allerVers(p, p.origine, Rythmes.RETOURS);
    }

    /** Clic sur la vraie borne de départ ; faux si elle n'est pas là. */
    static boolean borne(Pilote p) {
        ServerPlayer j = p.joueur;
        if (j == null || !Camp.borne(j)) return false;
        p.etat = Pilote.Etat.BORNE;
        p.attente = 20 * 20;
        return true;
    }

    // ------------------------------------------------------------------ départs (tronçon d'arrivée en tâche de fond)

    /** Départ vers {@code d} : un point au hasard, son tronçon demandé à Minecraft ; le bot part quand il est prêt. */
    static void allerVers(Pilote p, Place d, int raison) {
        p.destination = d;
        p.raison = raison;
        p.essais = 0;
        p.etat = Pilote.Etat.DEPART;
        viser(p, d);
    }

    private static void viser(Pilote p, Place d) {
        double a = R.nextDouble() * Math.PI * 2, rr = Math.sqrt(R.nextDouble()) * Math.max(1, d.rayon() - 8);
        p.destX = Mth.floor(d.centre().x + Math.cos(a) * rr);
        p.destZ = Mth.floor(d.centre().z + Math.sin(a) * rr);
        ChunkPos cp = new ChunkPos(p.destX >> 4, p.destZ >> 4);
        d.niveau().getChunkSource().addRegionTicket(Troncons.DEPART, cp, 2, cp);
        p.attente = 20 * 20;
    }

    private static boolean pret(ServerLevel lv, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (lv.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
            }
        }
        return true;
    }

    private static void departer(Pilote p) {
        ServerPlayer j = p.joueur;
        Place d = p.destination;
        if (d == null) {
            p.etat = Pilote.Etat.EN_JEU;
            return;
        }
        ServerLevel lv = d.niveau();
        if (!pret(lv, p.destX >> 4, p.destZ >> 4)) {
            if (--p.attente > 0) return;
            // trop long (tronçon pas créé, disque lent) : un autre point, trois fois, puis il reste où il est
            if (++p.essais < 3) {
                viser(p, d);
                return;
            }
            HeralizBanc.LOG.info("[bots] {} : arrivée jamais prête, il reste où il est", p.nom);
            p.destination = null;
            p.etat = Pilote.Etat.EN_JEU;
            return;
        }
        int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.destX, p.destZ);
        boolean eau = !lv.getFluidState(new BlockPos(p.destX, y - 1, p.destZ)).isEmpty();
        if ((y <= lv.getMinBuildHeight() + 1 || eau) && ++p.essais < 8) {
            viser(p, d);
            return;
        }
        long t0 = System.nanoTime();
        j.stopRiding();
        j.teleportTo(lv, p.destX + 0.5, y, p.destZ + 0.5, R.nextFloat() * 360f, 0f);
        j.resetFallDistance();
        // comme le jeu du joueur qui confirme sa nouvelle position : ses tronçons suivent tout de suite
        lv.getChunkSource().move(j);
        Couts.ajouter(Couts.TELEPORTATIONS, t0);
        p.zone = d;
        p.destination = null;
        p.etat = Pilote.Etat.EN_JEU;
        p.depuisPosition = 0;
        p.px = j.getX();
        p.py = j.getY();
        p.pz = j.getZ();
        if (p.raison >= 0) Rythmes.compter(p.raison);
    }

    /**
     * « Paquet de position » : comme le jeu d'un joueur (un paquet à chaque tick où il bouge ou tourne la tête, sinon un
     * par seconde), le serveur met à jour ses tronçons et ce qu'il voit ({@code ChunkMap.move}, fait pour chaque paquet
     * de position d'un vrai joueur). Avant : une fois toutes les 10 ticks, quoi qu'il fasse.
     */
    private static void position(Pilote p, ServerPlayer j) {
        double dx = j.getX() - p.px, dy = j.getY() - p.py, dz = j.getZ() - p.pz;
        boolean bouge = dx * dx + dy * dy + dz * dz > 4.0E-8 || j.getYRot() != p.pLacet || j.getXRot() != p.pTangage;
        if (!bouge && ++p.depuisPosition < 20) return;
        long t1 = System.nanoTime();
        j.serverLevel().getChunkSource().move(j);
        Couts.ajouter(Couts.DEPLACEMENTS, t1);
        p.px = j.getX();
        p.py = j.getY();
        p.pz = j.getZ();
        p.pLacet = j.getYRot();
        p.pTangage = j.getXRot();
        p.depuisPosition = 0;
    }

    /**
     * Bot passé dans un autre monde : arrivée de la borne (il vit dans la zone de groupe la plus proche), extraction ou
     * {@code /spawn} (au camp), tutoriel des bases ({@code /tuto lancer}), autre monde (il vit autour de son point
     * d'arrivée).
     */
    private static void changementMonde(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer j) || !vraiBot(j)) return;
        Pilote p = PAR_ID.get(j.getUUID());
        if (p == null || p.etat == Pilote.Etat.DEPART || p.etat == Pilote.Etat.PREPARATION || p.etat == Pilote.Etat.MORT) return;
        p.joueur = j;
        if (Camp.auCamp(j)) {
            if (p.etat == Pilote.Etat.EN_JEU && !dansUnPic) Rythmes.compter(Rythmes.EXTRACTIONS);
            if (p.etat != Pilote.Etat.CAMP) entrerAuCamp(p);
            return;
        }
        boolean tuto = TUTO.equals(j.serverLevel().dimension().location().toString());
        boolean borne = p.etat == Pilote.Etat.BORNE;
        if (!dansUnPic) {
            if (borne) Rythmes.compter(tuto ? Rythmes.TUTORIEL : Rythmes.BORNE);
            else if (tuto) Rythmes.compter(Rythmes.TUTORIEL);
        }
        p.etat = Pilote.Etat.EN_JEU;
        p.retenu = false;
        // arrivé par la borne : il vit dans la zone de groupe la plus proche (sinon autour de son point d'arrivée)
        placer(p, j, borne);
    }

    private static void mort(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer b) || !vraiBot(b)) return;
        stats.morts++;
        Rythmes.mort(b.server.getTickCount());
        // rien à ramasser sur un bot (son kit revient neuf à chaque réapparition : arme, grenades, munitions sans fin)
        b.getInventory().clearContent();
    }

    // ------------------------------------------------------------------ connexions et déconnexions

    /** Rythmes : un bot au calme se déconnecte et revient 30 à 90 s plus tard. */
    static void deconnecterAuHasard(MinecraftServer srv) {
        Pilote p = auHasard(true);
        if (p != null) deconnecter(p, 600 + R.nextInt(1200));
    }

    /** Rythmes : un bot au calme part dans une autre zone (comme un /home ou un /tpa). */
    static void voyageAuHasard(MinecraftServer srv) {
        if (Zones.LISTE.size() < 2) return;
        Pilote p = auHasard(true);
        if (p == null) return;
        Zones.Zone z = Zones.LISTE.get(R.nextInt(Zones.LISTE.size()));
        if (z.n() == p.zone.zone() || z.niveau() != p.zone.niveau()) return;
        allerVers(p, z.place(), Rythmes.VOYAGES);
    }

    @Nullable
    private static Pilote auHasard(boolean calme) {
        Pilote[] t = pilotes();
        if (t.length == 0) return null;
        for (int i = 0; i < 8; i++) {
            Pilote p = t[R.nextInt(t.length)];
            if (calme ? p.calme() : p.vivant()) return p;
        }
        return null;
    }

    /** Déconnecte le bot (sa sauvegarde est gardée), retour dans {@code dans} ticks (MAX_VALUE : jamais tout seul). */
    static void deconnecter(Pilote p, int dans) {
        ServerPlayer j = p.joueur;
        if (j == null) return;
        int t = j.server.getTickCount();
        long t0 = System.nanoTime();
        try {
            if (!j.hasDisconnected()) j.connection.onDisconnect(Component.literal("bots"));
        } catch (RuntimeException ex) {
            HeralizBanc.LOG.debug("[bots] {} deja parti", p.nom, ex);
        }
        Couts.ajouter(Couts.CONNEXIONS, t0);
        p.joueur = null;
        p.canal = null;
        p.etat = Pilote.Etat.HORS_LIGNE;
        p.retour = dans == Integer.MAX_VALUE ? Integer.MAX_VALUE : t + dans;
        Rythmes.compter(Rythmes.DECONNEXIONS);
    }

    /** Reconnexion d'un bot hors ligne (là où il était) ; vrai si c'est fait. */
    static boolean reconnecter(MinecraftServer srv, Pilote p) {
        if (p.etat != Pilote.Etat.HORS_LIGNE) return false;
        if (!creer(srv, p)) {
            p.retour = srv.getTickCount() + 600;
            return false;
        }
        Rythmes.compter(Rythmes.CONNEXIONS);
        return true;
    }

    /** Crée le joueur du bot et l'inscrit comme une connexion ; sauvegarde effacée d'abord pour un bot neuf. */
    private static boolean creer(MinecraftServer s, Pilote p) {
        long t0 = System.nanoTime();
        try {
            if (p.neuf) effacerFichiers(s, p.id);
            GameProfile g = new GameProfile(p.id, p.nom);
            ServerPlayer b = new ServerPlayer(s, p.origine.niveau(), g);
            b.addTag(TAG);
            Connection c = new Connection(PacketFlow.SERVERBOUND);
            // canal factice : tout paquet envoyé au bot est mis en forme et compressé comme pour un vrai joueur, puis
            // compté et oublié (Reseau ; /bots reseau false : jeté tout de suite)
            Reseau.seuil = s.getCompressionThreshold();
            EmbeddedChannel canal = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    Reseau.recevoir(msg);
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
            p.canal = c;
            p.joueur = b;
            s.getPlayerList().placeNewPlayer(c, b);
            ServerPlayer en = s.getPlayerList().getPlayer(p.id);
            if (en != null) p.joueur = en;
            p.joueur.addTag(TAG);   // une sauvegarde chargée remplace les étiquettes
            p.etat = Pilote.Etat.PREPARATION;
            p.attente = 60;
            p.depuisPosition = 20;
            p.cerveau.renaitre();
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeralizBanc.LOG.error("[bots] creation de {} impossible", p.nom, ex);
            p.joueur = null;
            p.canal = null;
            return false;
        } finally {
            Couts.ajouter(Couts.CONNEXIONS, t0);
        }
    }

    private static void arret(ServerStoppingEvent e) {
        toutRetirer(e.getServer());
    }

    static void toutRetirer(MinecraftServer srv) {
        for (Pilote p : pilotes()) retirer(srv, p);
        PILOTES.clear();
        PAR_ID.clear();
        change = true;
        effacerConstructions(srv);
        Pics.vider();
        Rythmes.couper();
        Zones.vider(srv);
        Camp.oublier();
    }

    /** Départ définitif : déconnexion (son corps reste s'il est mort), fichiers effacés. */
    private static void retirer(MinecraftServer srv, Pilote p) {
        PILOTES.remove(p.nom);
        PAR_ID.remove(p.id);
        change = true;
        ServerPlayer j = p.joueur;
        try {
            if (j != null && !j.hasDisconnected()) j.connection.onDisconnect(Component.literal("bots"));
        } catch (RuntimeException ex) {
            HeralizBanc.LOG.debug("[bots] {} deja parti", p.nom, ex);
        }
        p.joueur = null;
        p.canal = null;
        effacerFichiers(srv, p.id);
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
                HeralizBanc.LOG.debug("[bots] {} pas efface", p, ex);
            }
        }
    }

    // ------------------------------------------------------------------ constructions (CRac)

    /** Bots qui ont bâti quelque chose : tout est effacé à leur départ. */
    private static final java.util.Set<UUID> CONSTRUCTEURS = new java.util.LinkedHashSet<>();
    private static boolean cracCherche;
    @Nullable
    private static Method cracConstruire, cracEffacer, cracPieces;

    private static void chercherCrac() {
        if (cracCherche) return;
        cracCherche = true;
        try {
            Class<?> api = Class.forName("fr.heraliz.crac.api.BancApi");
            cracConstruire = api.getMethod("construire", ServerLevel.class, BlockPos.class, UUID.class, long.class);
            cracEffacer = api.getMethod("effacer", MinecraftServer.class, UUID.class);
            cracPieces = api.getMethod("pieces", MinecraftServer.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            cracConstruire = null;
            HeralizBanc.LOG.info("[bots] CRac absent : pas de constructions");
        }
    }

    /** Petite base de CRac devant le bot (8 blocs) ; vrai si au moins une pièce a été posée. */
    static boolean construire(ServerPlayer b, long graine) {
        chercherCrac();
        if (cracConstruire == null) return false;
        ServerLevel lv = b.serverLevel();
        Vec3 devant = b.position().add(b.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
        int x = Mth.floor(devant.x), z = Mth.floor(devant.z);
        int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        try {
            int n = ((Number) cracConstruire.invoke(null, lv, new BlockPos(x, y, z), b.getUUID(), graine)).intValue();
            if (n <= 0) return false;
            CONSTRUCTEURS.add(b.getUUID());
            stats.constructions++;
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeralizBanc.LOG.warn("[bots] construction de {} impossible", b.getGameProfile().getName(), ex);
            return false;
        }
    }

    /** Pièces de CRac dans le monde (rapports), -1 sans CRac. */
    static int piecesCrac(MinecraftServer srv) {
        chercherCrac();
        if (cracPieces == null) return -1;
        try {
            return ((Number) cracPieces.invoke(null, srv)).intValue();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }

    private static void effacerConstructions(MinecraftServer srv) {
        if (CONSTRUCTEURS.isEmpty() || cracEffacer == null) return;
        int n = 0;
        for (UUID u : CONSTRUCTEURS) {
            try {
                n += ((Number) cracEffacer.invoke(null, srv, u)).intValue();
            } catch (ReflectiveOperationException | RuntimeException ex) {
                HeralizBanc.LOG.warn("[bots] constructions de {} pas effacées", u, ex);
            }
        }
        HeralizBanc.LOG.info("[bots] {} pièce(s) de CRac des bots effacée(s)", n);
        CONSTRUCTEURS.clear();
    }

    // ------------------------------------------------------------------ frappes aériennes et largages

    private static boolean frappesCherche;
    @Nullable
    private static Method lancerFrappe, typeParNom, frappesActives;

    /**
     * Appelle une frappe aérienne (au hasard parmi bombardement, précision, sous-munitions, mitraillage, fumée, gaz) ou
     * un largage sur {@code cible}, comme le désignateur d'un joueur (HeraliZ Airstrike, par réflexion : sans lui, rien).
     */
    static void appelerFrappe(ServerPlayer b, Vec3 cible, boolean largage) {
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
                HeralizBanc.LOG.info("[bots] HeraliZ Airstrike absent : pas de frappes ni de largages");
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
            HeralizBanc.LOG.warn("[bots] frappe de {} impossible", b.getGameProfile().getName(), ex);
        }
    }
}
