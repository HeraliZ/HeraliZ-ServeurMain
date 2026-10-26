package fr.heralisz.utils.cycle;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.heralisz.utils.HeraliszUtils;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.PacketDistributor;

/**
 * Cycle jour / nuit de la carte (25/10), côté serveur : la SEULE horloge qui écrit l'heure de la carte
 * ({@code overworld().setDayTime}). La règle doDaylightCycle reste à false (remise à false au démarrage et chaque minute ;
 * doMobSpawning n'est jamais touchée). Courbe et inverse : {@link Cycle}.
 *
 * <ul>
 *   <li>Au démarrage, la position dans le cycle est déduite de l'heure du level.dat (rien d'autre à sauvegarder).</li>
 *   <li>Chaque tick (début du tick du serveur, avant les mondes et l'envoi de l'heure aux joueurs) : un tick réel de plus,
 *       sauf en pause ; l'heure est recalculée depuis la position (jamais de flottants accumulés) et écrite seulement si
 *       sa valeur entière change. Elle ne fait que monter (la phase de la lune en dépend).</li>
 *   <li>Heure changée par quelqu'un d'autre (/time, plugin) : la position est recalculée depuis la nouvelle heure.</li>
 *   <li>Journal à chaque début de jour et de nuit, avec la durée réelle mesurée de la phase précédente (horloge du
 *       système, en millisecondes) : sert à vérifier les durées sur le serveur.</li>
 *   <li>Réglages : heraliz_cycle-server.toml ({@link CycleConfig}, relu chaque seconde) ; pause : gardée avec le monde
 *       (data/heraliz_cycle.dat).</li>
 *   <li>/cycle info | jour | nuit | heure &lt;hh:mm&gt; | pause | reprendre (staff, permission 2) : les sauts vont
 *       toujours en avant.</li>
 *   <li>Le jeu du joueur reçoit les réglages (paquet {@link Cycle.Reglages}) à la connexion, au changement de monde, à la
 *       réapparition et après chaque /cycle ou changement de réglage, et prédit l'heure entre deux recalages
 *       ({@code CycleClient}).</li>
 * </ul>
 */
public final class CycleServeur {
    private CycleServeur() {
    }

    /** Heure d'horloge : « 6 », « 06:30 », « 6h30 », « 6h ». */
    private static final Pattern HEURE = Pattern.compile("(\\d{1,2})(?:[:hH](\\d{2})?)?");
    private static final long PAS_CALE = Long.MIN_VALUE;

    private static final Cycle.Horloge H = new Cycle.Horloge();
    /** Dernière heure écrite par le cycle (ou lue au dernier calage) : une autre valeur = changée par quelqu'un d'autre. */
    private static long dernier = PAS_CALE;
    private static boolean pret, actif, pause;
    private static int jourTicks = Cycle.ticks(20), nuitTicks = Cycle.ticks(4);
    /** Phase en cours (null : pas encore connue) et heure système de son début (-1 : inconnue, mesure impossible). */
    private static Boolean nuitEnCours;
    private static long debutPhaseMs = -1;
    /** Dernières durées réelles mesurées d'un jour et d'une nuit entiers (ms ; -1 : pas encore). */
    private static long mesureJourMs = -1, mesureNuitMs = -1;
    private static long avertiMs;
    private static int compteur;

    /** Branché par la partie serveur (serveur.PartieServeur), pendant le constructeur du @Mod. */
    public static void init() {
        Cycle.enregistrer();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CycleConfig.SPEC, "heraliz_cycle-server.toml");
        MinecraftForge.EVENT_BUS.addListener(CycleServeur::demarre);
        MinecraftForge.EVENT_BUS.addListener(CycleServeur::arrete);
        MinecraftForge.EVENT_BUS.addListener(CycleServeur::tick);
        MinecraftForge.EVENT_BUS.addListener(CycleServeur::commandes);
        MinecraftForge.EVENT_BUS.addListener(CycleServeur::connexion);
        MinecraftForge.EVENT_BUS.addListener(CycleServeur::changeMonde);
        MinecraftForge.EVENT_BUS.addListener(CycleServeur::reapparition);
    }

    // ------------------------------------------------------------------ démarrage, arrêt

    private static void reinitialiser() {
        pret = false;
        actif = false;
        pause = false;
        dernier = PAS_CALE;
        nuitEnCours = null;
        debutPhaseMs = -1;
        mesureJourMs = -1;
        mesureNuitMs = -1;
        avertiMs = 0;
        compteur = 0;
    }

    private static void demarre(ServerStartedEvent e) {
        MinecraftServer srv = e.getServer();
        reinitialiser();
        lireReglages(srv, false);
        pause = Pause.de(srv).pause;
        if (actif) {
            verifierRegle(srv);
            long d = srv.overworld().getDayTime();
            caler(d, false);
            HeraliszUtils.LOG.info("[cycle] démarré : jour {} min, nuit {} min réelles ; il est {} sur la carte (jour {}, {}){}",
                    jourTicks / 1200, nuitTicks / 1200, Cycle.heure(d), d / Cycle.TOUR_MC, H.nuit() ? "nuit" : "jour",
                    pause ? ", EN PAUSE (/cycle reprendre)" : "");
        } else {
            HeraliszUtils.LOG.info("[cycle] désactivé (heraliz_cycle-server.toml) : l'heure de la carte n'est pas touchée");
        }
        pret = true;
    }

    private static void arrete(ServerStoppedEvent e) {
        reinitialiser();
    }

    /** Relit heraliz_cycle-server.toml ; {@code annoncer} : un changement en cours de partie est appliqué et envoyé. */
    private static void lireReglages(MinecraftServer srv, boolean annoncer) {
        boolean a;
        int j, n;
        try {
            a = CycleConfig.ACTIF.get();
            j = Cycle.ticks(CycleConfig.JOUR_MINUTES.get());
            n = Cycle.ticks(CycleConfig.NUIT_MINUTES.get());
        } catch (IllegalStateException ex) {                      // réglages pas encore chargés
            return;
        }
        if (a == actif && j == jourTicks && n == nuitTicks) return;
        boolean etaitActif = actif;
        actif = a;
        jourTicks = j;
        nuitTicks = n;
        if (!annoncer) return;
        HeraliszUtils.LOG.info("[cycle] réglages changés : {}, jour {} min, nuit {} min réelles",
                actif ? "actif" : "désactivé", jourTicks / 1200, nuitTicks / 1200);
        if (actif) {
            if (!etaitActif) verifierRegle(srv);
            caler(srv.overworld().getDayTime(), false);          // même heure, nouvelle courbe : rien ne saute
        }
        envoyerATous(srv);
    }

    /** doDaylightCycle à true sur la carte : remise à false (sinon Minecraft ferait avancer l'heure en plus du cycle). */
    private static void verifierRegle(MinecraftServer srv) {
        GameRules.BooleanValue regle = srv.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        if (regle.get()) {
            regle.set(false, srv);
            HeraliszUtils.LOG.info("[cycle] doDaylightCycle était à true sur la carte : remis à false (c'est le cycle HeraliZ qui fait avancer l'heure)");
            srv.forceTimeSynchronization();
        }
    }

    // ------------------------------------------------------------------ horloge

    /**
     * Cale l'horloge sur cette heure. {@code debutDePhase} : l'heure est le début exact d'un jour ou d'une nuit (/cycle jour,
     * /cycle nuit), la phase qui commence pourra être mesurée ; sinon sa durée ne sera pas mesurée.
     */
    private static void caler(long d, boolean debutDePhase) {
        H.caler(d, jourTicks, nuitTicks);
        dernier = d;
        nuitEnCours = H.nuit();
        debutPhaseMs = debutDePhase ? System.currentTimeMillis() : -1;
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !pret) return;
        MinecraftServer srv = e.getServer();
        compteur++;
        if (compteur % 20 == 0) lireReglages(srv, true);
        if (!actif) return;
        if (compteur % 1200 == 0) verifierRegle(srv);
        ServerLevel carte = srv.overworld();
        long d = carte.getDayTime();
        if (d != dernier) {                                      // /time, plugin, sommeil... : on repart de la nouvelle heure
            if (dernier != PAS_CALE) noterChangement(d);
            caler(d, false);
        }
        if (pause) return;
        H.avancer();
        boolean nuit = H.nuit();
        if (nuitEnCours == null) {
            nuitEnCours = nuit;
        } else if (nuit != nuitEnCours) {
            changementDePhase(nuit);
        }
        long v = (long) Math.floor(H.temps() + 1e-6);
        if (v > d) {                                             // jamais en arrière
            carte.setDayTime(v);
            dernier = v;
        }
    }

    private static void noterChangement(long d) {
        long now = System.currentTimeMillis();
        if (now - avertiMs < 60_000L) return;                    // au plus une ligne par minute
        avertiMs = now;
        HeraliszUtils.LOG.info("[cycle] heure de la carte changée par une autre source ({} -> {}, {}) : cycle recalé sur la nouvelle heure",
                dernier, d, Cycle.heure(d));
    }

    private static void changementDePhase(boolean nuit) {
        long now = System.currentTimeMillis();
        long duree = debutPhaseMs < 0 ? -1 : now - debutPhaseMs;
        if (nuit) {
            if (duree >= 0) mesureJourMs = duree;
            HeraliszUtils.LOG.info("[cycle] nuit commencée (jour précédent : {})", texteDuree(duree));
        } else {
            if (duree >= 0) mesureNuitMs = duree;
            HeraliszUtils.LOG.info("[cycle] jour commencé (nuit précédente : {})", texteDuree(duree));
        }
        nuitEnCours = nuit;
        debutPhaseMs = now;
    }

    /** Durée pour le journal (en français, comme tous les journaux) : « 20 min 00 s réelles (1200012 ms) ». */
    private static String texteDuree(long ms) {
        if (ms < 0) return "durée non mesurée (serveur démarré, pause ou heure changée pendant la phase)";
        return String.format(Locale.ROOT, "%d min %02d s réelles (%d ms)", ms / 60_000L, (ms / 1000L) % 60L, ms);
    }

    /**
     * Saut d'heure demandé par le staff (toujours en avant) : écrit l'heure, recale l'horloge, envoie l'heure tout de suite
     * à tous les joueurs (au lieu d'attendre l'envoi de chaque seconde), puis les réglages (le jeu du joueur repart de la
     * nouvelle heure sans lissage).
     */
    private static void sauter(MinecraftServer srv, long cible, boolean debutDePhase) {
        ServerLevel carte = srv.overworld();
        if (carte.getDayTime() != cible) carte.setDayTime(cible);
        if (actif) {
            caler(cible, debutDePhase);
        } else {
            dernier = cible;
        }
        srv.forceTimeSynchronization();
        envoyerATous(srv);
    }

    // ------------------------------------------------------------------ joueurs

    /** Le monde du joueur suit l'heure de la carte (la carte, ou un monde sans heure fixe qui reprend celle de la carte). */
    private static boolean suitLaCarte(ServerLevel l) {
        return !l.dimensionType().hasFixedTime()
                && (l.dimension() == Level.OVERWORLD || l.getLevelData() instanceof DerivedLevelData);
    }

    private static Cycle.Reglages reglagesPour(ServerPlayer p) {
        return new Cycle.Reglages(actif && suitLaCarte(p.serverLevel()), jourTicks, nuitTicks, pause);
    }

    private static void envoyer(ServerPlayer p) {
        if (p.connection == null) return;
        if (!p.connection.connection.isMemoryConnection() && !Cycle.CANAL.isRemotePresent(p.connection.connection)) return;
        Cycle.CANAL.send(PacketDistributor.PLAYER.with(() -> p), reglagesPour(p));
    }

    private static void envoyerATous(MinecraftServer srv) {
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            envoyer(p);
        }
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) envoyer(p);
    }

    private static void changeMonde(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) envoyer(p);
    }

    private static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) envoyer(p);
    }

    // ------------------------------------------------------------------ /cycle

    private static Component t(String cle, String francais, Object... args) {
        return Component.translatableWithFallback("heralisz_utils.cycle." + cle, francais, args);
    }

    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("cycle").requires(s -> s.hasPermission(2))
                .executes(ctx -> info(ctx.getSource()))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("jour").executes(ctx -> phase(ctx.getSource(), false)))
                .then(Commands.literal("nuit").executes(ctx -> phase(ctx.getSource(), true)))
                .then(Commands.literal("heure").then(Commands.argument("heure", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"06:00", "12:00", "18:00", "00:00"}, b))
                        .executes(ctx -> heure(ctx.getSource(), StringArgumentType.getString(ctx, "heure")))))
                .then(Commands.literal("pause").executes(ctx -> pause(ctx.getSource(), true)))
                .then(Commands.literal("reprendre").executes(ctx -> pause(ctx.getSource(), false))));
    }

    private static Component duree(long ms) {
        return t("duree", "%s min %s s", String.valueOf(ms / 60_000L), String.format(Locale.ROOT, "%02d", (ms / 1000L) % 60L));
    }

    private static Component mesure(long ms) {
        return ms < 0 ? t("pas_mesure", "pas encore mesurée") : duree(ms);
    }

    private static int info(CommandSourceStack src) {
        long d = src.getServer().overworld().getDayTime();
        Component etat = !actif ? t("etat.desactive", "désactivé (heraliz_cycle-server.toml)")
                : pause ? t("etat.pause", "en pause") : t("etat.actif", "actif");
        src.sendSuccess(() -> t("info.titre", "Cycle jour / nuit : %s", etat), false);
        src.sendSuccess(() -> t("info.heure", "Heure de la carte : %s (jour %s, %s ticks).", Cycle.heure(d),
                String.valueOf(Math.floorDiv(d, Cycle.TOUR_MC)), String.valueOf(d)), false);
        if (actif && pause) {
            src.sendSuccess(() -> t("info.en_pause", "En pause : l'heure ne bouge plus (/cycle reprendre)."), false);
        } else if (actif) {
            boolean nuit = H.nuit();
            Component reste = duree((long) Math.ceil(H.restant()) * 50L);
            src.sendSuccess(() -> nuit ? t("info.reste_nuit", "Nuit : lever du soleil dans %s réelles.", reste)
                    : t("info.reste_jour", "Jour : tombée de la nuit dans %s réelles.", reste), false);
        }
        src.sendSuccess(() -> t("info.reglages", "Réglages : jour %s min, nuit %s min (heraliz_cycle-server.toml).",
                String.valueOf(jourTicks / 1200), String.valueOf(nuitTicks / 1200)), false);
        src.sendSuccess(() -> t("info.mesures", "Dernières durées mesurées : jour %s, nuit %s.", mesure(mesureJourMs), mesure(mesureNuitMs)),
                false);
        return 1;
    }

    /** /cycle jour, /cycle nuit : au prochain lever du soleil, ou à la prochaine tombée de la nuit, en avançant. */
    private static int phase(CommandSourceStack src, boolean nuit) {
        MinecraftServer srv = src.getServer();
        long d = srv.overworld().getDayTime();
        long cible = Cycle.prochain(d, nuit ? Cycle.DEBUT_NUIT : Cycle.DEBUT_JOUR);
        sauter(srv, cible, true);
        HeraliszUtils.LOG.info("[cycle] {} ({}) : {} -> {} ({})", nuit ? "/cycle nuit" : "/cycle jour", src.getTextName(), d, cible,
                Cycle.heure(cible));
        src.sendSuccess(() -> nuit ? t("nuit", "La nuit tombe sur la carte (%s).", Cycle.heure(cible))
                : t("jour", "Le soleil se lève sur la carte (%s).", Cycle.heure(cible)), false);
        avertirDesactive(src);
        return 1;
    }

    /** /cycle heure hh:mm : à la prochaine fois qu'il est cette heure-là, en avançant. */
    private static int heure(CommandSourceStack src, String texte) {
        Matcher m = HEURE.matcher(texte.trim());
        int h = -1, mi = -1;
        if (m.matches()) {
            h = Integer.parseInt(m.group(1));
            mi = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        }
        if (h < 0 || h > 23 || mi < 0 || mi > 59) {
            src.sendFailure(t("heure_invalide", "Heure invalide : « %s » (exemple : /cycle heure 06:30).", texte));
            return 0;
        }
        MinecraftServer srv = src.getServer();
        long d = srv.overworld().getDayTime();
        long cible = Cycle.prochain(d, Cycle.heureMc(h, mi));
        sauter(srv, cible, false);
        HeraliszUtils.LOG.info("[cycle] /cycle heure ({}) : {} -> {} ({})", src.getTextName(), d, cible, Cycle.heure(cible));
        src.sendSuccess(() -> t("heure", "Il est maintenant %s sur la carte.", Cycle.heure(cible)), false);
        avertirDesactive(src);
        return 1;
    }

    /** /cycle pause, /cycle reprendre : gardé avec le monde (survit au redémarrage). */
    private static int pause(CommandSourceStack src, boolean enPause) {
        MinecraftServer srv = src.getServer();
        long d = srv.overworld().getDayTime();
        Pause donnees = Pause.de(srv);
        if (donnees.pause == enPause && pause == enPause) {
            src.sendSuccess(() -> enPause ? t("deja_pause", "Le cycle est déjà en pause.")
                    : t("deja_reprise", "Le cycle n'est pas en pause."), false);
            return 1;
        }
        donnees.pause = enPause;
        donnees.setDirty();
        pause = enPause;
        if (actif) caler(d, false);                              // phase en cours : plus mesurable
        srv.forceTimeSynchronization();
        envoyerATous(srv);
        HeraliszUtils.LOG.info("[cycle] {} ({}) à {}", enPause ? "pause" : "reprise", src.getTextName(), Cycle.heure(d));
        src.sendSuccess(() -> enPause ? t("pause", "Cycle en pause : il restera %s sur la carte (/cycle reprendre).", Cycle.heure(d))
                : t("reprise", "Le cycle reprend (%s).", Cycle.heure(d)), false);
        avertirDesactive(src);
        return 1;
    }

    private static void avertirDesactive(CommandSourceStack src) {
        if (!actif) {
            src.sendSuccess(() -> t("desactive", "Attention : le cycle est désactivé (heraliz_cycle-server.toml, actif = false) : l'heure ne bouge pas toute seule."),
                    false);
        }
    }

    // ------------------------------------------------------------------ pause gardée avec le monde

    /** Pause du cycle (/cycle pause), gardée avec la carte : data/heraliz_cycle.dat. */
    static final class Pause extends SavedData {
        private static final String NOM = "heraliz_cycle";
        boolean pause;

        Pause() {
        }

        static Pause de(MinecraftServer srv) {
            return srv.overworld().getDataStorage().computeIfAbsent(Pause::lire, Pause::new, NOM);
        }

        private static Pause lire(CompoundTag t) {
            Pause p = new Pause();
            p.pause = t.getBoolean("pause");
            return p;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putBoolean("pause", pause);
            return t;
        }
    }
}
