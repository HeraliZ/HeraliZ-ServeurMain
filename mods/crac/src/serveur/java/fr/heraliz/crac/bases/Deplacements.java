package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.heraliz.Reply;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Les commandes de déplacement des joueurs (le staff, permission 2, n'a ni délai ni règle de lieu) :
 * <ul>
 *   <li>{@code /home [nom]} ({@link Homes}) : du spawn vers un de ses homes ; sans home, rien (message).</li>
 *   <li>{@code /zone base} (alias {@code /rtp}) : seulement depuis le spawn, vers un point plat tiré au hasard dans le
 *   monde des bases, loin des bases des autres (même tirage que le tutoriel) ; une fois toutes les
 *   {@code [home] zone_delai_s} secondes (5 min, gardé au redémarrage).</li>
 *   <li>{@code /spawn} : seulement depuis SA base (dans la zone de son claim, monde des bases), hors combat, vers le point
 *   « spawn » posé par le staff ({@code /spawn definir}, {@link PointSpawn}) ; sans point, vers le point de réveil du
 *   spawn (HeraliZ Utils, {@code fr.heralisz.utils.api.SpawnApi.allerAuSpawn}, par réflexion). Staff :
 *   {@code /spawn definir | effacer | voir}.</li>
 * </ul>
 * « Au spawn » = dans le monde du spawn ({@code heralisz:spawn}) ou à moins de {@code [home] spawn_rayon} blocs du point
 * /spawn défini. Pour aller sur la carte : la borne de départ du spawn. Départ après {@code [home] immobile_s} secondes
 * sans bouger ni être blessé ; toujours une vraie téléportation ({@code teleportTo}, suivie par Spartan). Tous les
 * messages vont dans le chat (ceux d'après une téléportation une demi-seconde après l'arrivée, écran de chargement
 * passé).
 */
@EventBusSubscriber(modid = "crac")
public final class Deplacements {
    private record Attente(String genre, Vec3 depart, int ticks, Runnable action) {
    }

    private record Message(UUID joueur, int ticks, Component texte) {
    }

    private static final Map<UUID, Attente> ATTENTES = new HashMap<>();
    private static final List<Message> MESSAGES = new ArrayList<>();
    /** Ticks entre l'arrivée d'une téléportation et les messages qui la suivent. */
    private static final int APRES_ARRIVEE = 10;
    @Nullable
    private static Method allerAuSpawn;
    private static boolean spawnCherche;

    private Deplacements() {
    }

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("spawn").executes(c -> spawn(c.getSource().getPlayerOrException()))
            .then(Commands.literal("definir").requires(s -> s.hasPermission(2)).executes(c -> definirSpawn(c.getSource())))
            .then(Commands.literal("effacer").requires(s -> s.hasPermission(2)).executes(c -> effacerSpawn(c.getSource())))
            .then(Commands.literal("voir").requires(s -> s.hasPermission(2)).executes(c -> voirSpawn(c.getSource()))));
        d.register(Commands.literal("zone").then(Commands.literal("base").executes(c -> zoneBase(c.getSource().getPlayerOrException()))));
        d.register(Commands.literal("rtp").executes(c -> zoneBase(c.getSource().getPlayerOrException())));
    }

    static boolean staff(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    /**
     * Refus commun des commandes de CRac (23/10, demande du propriétaire : aucune commande en combat) : tag de combat de
     * HeraliZ Utils, joueur à terre (Réanimation). Le staff (permission 2) n'est jamais arrêté. Rend
     * vrai (et prévient le joueur) quand la commande est refusée.
     */
    public static boolean refuse(ServerPlayer p) {
        if (staff(p)) {
            return false;
        }
        int combat = Heraliz.combatSecondsLeft(p);
        if (combat > 0) {
            T.chat(p, T.t("crac.cmd.combat", "Tu es en combat : commande impossible encore %s s.", String.valueOf(combat)),
                ChatFormatting.RED);
            return true;
        }
        if (fr.heraliz.crac.heraliz.SaisonPont.aTerre(p)) {
            T.chat(p, T.t("crac.cmd.a_terre", "Impossible à terre."), ChatFormatting.RED);
            return true;
        }
        return false;
    }

    /** Le joueur est-il « au spawn » : monde du spawn, ou près du point /spawn défini par le staff ? */
    static boolean auSpawn(ServerPlayer p) {
        if (MondeBases.estSpawn(p.level())) {
            return true;
        }
        PointSpawn.Point pt = PointSpawn.get(p.server);
        if (pt == null || !pt.monde().equals(p.level().dimension().location())) {
            return false;
        }
        double r = BasesReglages.i(BasesReglages.SPAWN_RAYON, 48);
        return p.distanceToSqr(pt.x(), pt.y(), pt.z()) <= r * r;
    }

    // ---------------------------------------------------------------------------------------------- /zone base

    static int zoneBase(ServerPlayer p) {
        if (refuse(p)) {
            return 0;
        }
        boolean staff = staff(p);
        if (!staff && !auSpawn(p)) {
            T.chat(p, T.t("crac.zone.depuis_spawn", "/zone base ne marche que depuis le spawn. Depuis ta base, /spawn y ramène."),
                ChatFormatting.RED);
            return 0;
        }
        if (p.server.getLevel(MondeBases.BASES) == null) {
            T.chat(p, T.t("crac.bases.absent", "La carte n'est pas chargée sur ce serveur : préviens le staff."), ChatFormatting.RED);
            return 0;
        }
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).joueur(p.getUUID());
        int permDelai = Homes.permGrade(p.getUUID(), "rtp_recharge");
        long delai = (permDelai >= 0 ? permDelai : BasesReglages.i(BasesReglages.ZONE_DELAI_S, 300)) * 1000L;
        long reste = j.dernierZone + delai - System.currentTimeMillis();
        if (!staff && reste > 0) {
            T.chat(p, T.t("crac.zone.attendre", "Prochain /zone base dans %s.", T.duree(reste + 999)), ChatFormatting.RED);
            return 0;
        }
        attendre(p, "zone", () -> partirZone(p));
        return 1;
    }

    private static void partirZone(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return;
        }
        Vec3 dest = Points.auHasard(bases, p);
        if (dest == null) {
            T.chat(p, T.t("crac.home.pas_de_place", "Aucune place sûre trouvée : réessaie dans un instant."),
                ChatFormatting.RED);
            return;
        }
        p.stopRiding();
        p.teleportTo(bases, dest.x, dest.y, dest.z, p.getYRot(), 0f);
        p.resetFallDistance();
        BasesDonnees d = BasesDonnees.get(p.server);
        d.joueur(p.getUUID()).dernierZone = System.currentTimeMillis();
        d.marquer();
        apres(p, T.t("crac.zone.arrive", "Te voici sur la carte, en PvP : trouve un coin où ton clan peut bâtir sa base.")
            .withStyle(ChatFormatting.GREEN));
        apres(p, T.t("crac.zone.astuce", "Dans ta base, /sethome enregistre un home : du spawn, /home t'y ramène.")
            .withStyle(ChatFormatting.GRAY));
    }

    /** Le claim de la base du joueur dans le monde des bases (son groupe), ou null. */
    @Nullable
    static Claim baseDe(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return null;
        }
        Group g = GroupData.get(p.server).getByMember(p.getUUID());
        return g == null ? null : ClaimManager.getByGroup(bases, g.id());
    }

    // ---------------------------------------------------------------------------------------------- /spawn

    static int spawn(ServerPlayer p) {
        if (refuse(p)) {
            return 0;
        }
        if (!staff(p)) {
            // 23/10 (choix du propriétaire) : sur la carte, on ne la quitte que par une extraction ; les bases y sont
            if (MondeBases.estBases(p.level()) && !p.isCreative() && !p.isSpectator()) {
                T.chat(p, T.t("crac.spawn.carte", "Sur la carte, on ne revient au spawn que par une extraction (/extractions)."),
                    ChatFormatting.RED);
                return 0;
            }
            if (!MondeBases.estBases(p.level()) || !dansSaBase(p)) {
                T.chat(p, T.t("crac.spawn.depuis_base", "/spawn ne marche que depuis ta base (dans la zone de ton Claim Block)."),
                    ChatFormatting.RED);
                return 0;
            }
            int combat = Heraliz.combatSecondsLeft(p);
            if (combat > 0) {
                T.chat(p, T.t("crac.spawn.combat", "Tu es en combat : /spawn dans %s s.", String.valueOf(combat)), ChatFormatting.RED);
                return 0;
            }
        }
        attendre(p, "spawn", () -> {
            if (allerAuPointSpawn(p)) {
                // au-dessus de la barre d'objets, pas dans le chat : il passait par-dessus la radio de Varga (20/10)
                p.displayClientMessage(T.t("crac.spawn.arrive", "Te voici au spawn.").withStyle(ChatFormatting.GREEN), true);
            } else {
                T.chat(p, T.t("crac.spawn.indisponible", "Le spawn n'est pas disponible pour l'instant : préviens le staff."),
                    ChatFormatting.RED);
            }
        });
        return 1;
    }

    /**
     * Seulement vers le point /spawn défini par le staff (vraie téléportation) ; rend false sans rien faire s'il n'y en a
     * pas ou si son monde est absent. Lu par {@code SpawnCracApi} (retour d'une extraction).
     */
    public static boolean allerAuPointDefini(ServerPlayer p) {
        PointSpawn.Point pt = PointSpawn.get(p.server);
        ServerLevel l = pt == null ? null : PointSpawn.niveau(p.server, pt);
        if (l == null) {
            return false;
        }
        p.stopRiding();
        p.teleportTo(l, pt.x(), pt.y(), pt.z(), pt.lacet(), pt.tangage());
        p.resetFallDistance();
        return true;
    }

    /** Vers le point /spawn défini par le staff (si son monde existe), sinon vers le point de réveil (SpawnApi). */
    static boolean allerAuPointSpawn(ServerPlayer p) {
        PointSpawn.Point pt = PointSpawn.get(p.server);
        ServerLevel l = pt == null ? null : PointSpawn.niveau(p.server, pt);
        if (pt != null && l != null) {
            p.stopRiding();
            p.teleportTo(l, pt.x(), pt.y(), pt.z(), pt.lacet(), pt.tangage());
            p.resetFallDistance();
            return true;
        }
        if (pt != null) {
            Crac.LOGGER.warn("[CRac] point /spawn dans un monde absent ({}) : point de réveil à la place", pt.monde());
        }
        return allerAuSpawn(p);
    }

    private static int definirSpawn(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PointSpawn.Point pt = new PointSpawn.Point(p.level().dimension().location(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        if (!PointSpawn.definir(src.getServer(), pt)) {
            Reply.fail(src, T.t("crac.spawn.ecriture", "Impossible d'enregistrer le point /spawn (voir le journal du serveur)."));
            return 0;
        }
        Crac.LOGGER.info("[CRac] point /spawn défini par {} : {} {}", p.getGameProfile().getName(), pt.monde(), position(pt));
        Reply.ok(src, T.t("crac.spawn.defini",
            "Point /spawn défini ici : %s, %s (regard compris). La première arrivée (cinématique) ne change pas.",
            pt.monde().toString(), position(pt)).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int effacerSpawn(CommandSourceStack src) {
        if (!PointSpawn.definir(src.getServer(), null)) {
            Reply.fail(src, T.t("crac.spawn.ecriture", "Impossible d'enregistrer le point /spawn (voir le journal du serveur)."));
            return 0;
        }
        Reply.ok(src, T.t("crac.spawn.efface", "Point /spawn effacé : /spawn mène de nouveau au point de réveil de la cinématique.")
            .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int voirSpawn(CommandSourceStack src) {
        PointSpawn.Point pt = PointSpawn.get(src.getServer());
        if (pt == null) {
            Reply.ok(src, T.t("crac.spawn.voir_aucun", "Aucun point /spawn défini : /spawn mène au point de réveil de la cinématique.")
                .withStyle(ChatFormatting.YELLOW));
        } else if (PointSpawn.niveau(src.getServer(), pt) != null) {
            Reply.ok(src, T.t("crac.spawn.voir", "Point /spawn : %s, %s.", pt.monde().toString(), position(pt)).withStyle(ChatFormatting.GREEN));
        } else {
            Reply.ok(src, T.t("crac.spawn.voir_absent", "Point /spawn : %s, %s, mais ce monde n'existe pas ici : /spawn mène au point de réveil.",
                pt.monde().toString(), position(pt)).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static String position(PointSpawn.Point pt) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", pt.x(), pt.y(), pt.z());
    }

    /** Le joueur est-il dans la zone d'un claim de son groupe (ou de son clan, droit d'entrer) ? */
    static boolean dansSaBase(ServerPlayer p) {
        return p.level() instanceof ServerLevel sl && baseEn(sl, p.blockPosition(), p.getUUID());
    }

    /** Ce point est-il dans la zone d'un claim du groupe du joueur (ou de son clan, droit d'entrer) ? */
    static boolean baseEn(ServerLevel sl, BlockPos pos, UUID joueur) {
        Claim c = ClaimManager.getAt(sl, pos);
        if (c == null) {
            return false;
        }
        Group g = GroupData.get(sl.getServer()).getById(c.groupId());
        return g != null && (g.isMember(joueur) || ClanAccess.allows(g, joueur, ClanAccess.Action.ENTER));
    }

    /** {@code SpawnApi.allerAuSpawn(ServerPlayer)} de HeraliZ Utils, lu par son nom (pas de dépendance). */
    static boolean allerAuSpawn(ServerPlayer p) {
        if (!spawnCherche) {
            spawnCherche = true;
            try {
                allerAuSpawn = Class.forName("fr.heralisz.utils.api.SpawnApi").getMethod("allerAuSpawn", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                Crac.LOGGER.warn("[CRac] SpawnApi de HeraliZ Utils introuvable : /spawn ne peut pas marcher ({})", e.toString());
            }
        }
        if (allerAuSpawn == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(allerAuSpawn.invoke(null, p));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Crac.LOGGER.warn("[CRac] SpawnApi.allerAuSpawn en erreur", e);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------- messages

    /** Message dans le chat un peu après l'arrivée d'une téléportation (l'écran de chargement d'un monde passé). */
    static void apres(ServerPlayer p, Component texte) {
        MESSAGES.add(new Message(p.getUUID(), APRES_ARRIVEE, texte));
    }

    // ---------------------------------------------------------------------------------------------- départ immobile

    static void attendre(ServerPlayer p, String genre, Runnable action) {
        int s = staff(p) ? 0 : BasesReglages.i(BasesReglages.IMMOBILE_S, 3);
        if (s <= 0) {
            ATTENTES.remove(p.getUUID());
            action.run();
            return;
        }
        ATTENTES.put(p.getUUID(), new Attente(genre, p.position(), s * 20, action));
        T.chat(p, T.t("crac.depart.ne_bouge_pas", "Ne bouge pas : départ dans %s s.", String.valueOf(s)), ChatFormatting.YELLOW);
        T.barre(p, T.t("crac.depart.ne_bouge_pas", "Ne bouge pas : départ dans %s s.", String.valueOf(s)), ChatFormatting.YELLOW);
    }

    /** Chaque tick (MondeBasesServeur). */
    static void tick(MinecraftServer server) {
        if (!MESSAGES.isEmpty()) {
            List<Message> encore = new ArrayList<>();
            List<Message> prets = new ArrayList<>();
            for (Message m : MESSAGES) {
                if (m.ticks() > 1) {
                    encore.add(new Message(m.joueur(), m.ticks() - 1, m.texte()));
                } else {
                    prets.add(m);
                }
            }
            MESSAGES.clear();
            MESSAGES.addAll(encore);
            for (Message m : prets) {
                ServerPlayer p = server.getPlayerList().getPlayer(m.joueur());
                if (p != null) {
                    p.sendSystemMessage(m.texte());
                }
            }
        }
        if (ATTENTES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Attente>> it = ATTENTES.entrySet().iterator();
        Map<UUID, Attente> suite = new HashMap<>();
        List<Runnable> departs = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Attente> e = it.next();
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Attente a = e.getValue();
            if (p == null || !p.isAlive()) {
                continue;
            }
            if (p.position().distanceToSqr(a.depart()) > 0.36) {
                // poussé par un autre corps ou repoussé par un coup (1.21.1) : le départ suit le joueur
                if (!pousse(p)) {
                    T.chat(p, T.t("crac.depart.annule", "Départ annulé : tu as bougé."), ChatFormatting.RED);
                    continue;
                }
                a = new Attente(a.genre(), p.position(), a.ticks(), a.action());
            }
            int t = a.ticks() - 1;
            if (t <= 0) {
                departs.add(a.action());
                continue;
            }
            if (t % 20 == 0) {
                T.barre(p, T.t("crac.depart.ne_bouge_pas", "Ne bouge pas : départ dans %s s.", String.valueOf(t / 20)), ChatFormatting.YELLOW);
            }
            suite.put(e.getKey(), new Attente(a.genre(), a.depart(), t, a.action()));
        }
        ATTENTES.putAll(suite);
        // les téléportations après le parcours (une action peut relancer une attente)
        for (Runnable r : departs) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                Crac.LOGGER.error("[CRac] départ (/home, /zone base, /spawn) en erreur", ex);
            }
        }
    }

    /** Un autre corps touche le joueur, ou il vient d'être repoussé : ce déplacement n'est pas le sien. */
    private static boolean pousse(ServerPlayer p) {
        if (p.hurtTime > 0) {
            return true;
        }
        for (net.minecraft.world.entity.Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(0.35))) {
            if (e.isPushable() && !e.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    /** Blessé pendant l'attente : départ annulé. */
    @SubscribeEvent
    public static void blesse(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ATTENTES.remove(p.getUUID()) != null) {
            T.chat(p, T.t("crac.depart.blesse", "Départ annulé : tu as été blessé."), ChatFormatting.RED);
        }
    }

    static void oublier(UUID joueur) {
        ATTENTES.remove(joueur);
        MESSAGES.removeIf(m -> m.joueur().equals(joueur));
    }
}
