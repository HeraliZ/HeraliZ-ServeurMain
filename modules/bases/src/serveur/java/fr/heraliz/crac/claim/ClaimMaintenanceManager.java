package fr.heraliz.crac.claim;

import fr.heraliz.crac.heraliz.Txt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureIntegrity;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacablePositionData;
import fr.heraliz.crac.raid.RaidBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Once a minute: upkeep payment, decay of unpaid / inactive / unclaimed buildings and auto-repair of the others.
 * Only loaded chunks are touched; nothing is ever loaded for this.
 * <p>HeraliZ (01/11, perf) : la passe de chaque minute est étalée sur quelques ticks ({@link #BUDGET} unités de travail
 * par tick, une unité par claim, par pièce ou par objet posé) au lieu de passer toutes les bases dans le même tick. Chaque
 * claim paie une fois par passe, chaque pièce et chaque objet posé est vu une fois, dans le même ordre et avec les mêmes
 * règles qu'avant ; une passe pas finie à la minute suivante (ou à l'arrêt du serveur) est finie d'un coup d'abord.
 */
@EventBusSubscriber(
    modid = "crac"
)
public final class ClaimMaintenanceManager {
    private static final long INTERVAL_TICKS = 1200L;
    /** Game ticks in a real day: inactivity is measured in server uptime. */
    private static final long TICKS_PER_DAY = 24L * 60L * 60L * 20L;
    private static long nextRunTick = -1L;
    /** Unités de travail de la passe par tick (environ 0,1 à 0,2 ms). */
    private static final int BUDGET = 2000;
    /** La passe en cours (null entre deux passes). */
    @Nullable
    private static Passe passe;
    /**
     * Groups whose upkeep had run out at the last pass, to warn their members once when it starts and stops: HeraliZ
     * (03/11) {@link #VIDE} (a resource is empty, its delay runs) or {@link #USURE} (pieces decay); absent = paid.
     */
    private static final Map<UUID, Integer> UNPAID = new HashMap<>();
    private static final int PAYE = 0, VIDE = 1, USURE = 2;
    /**
     * HeraliZ (03/11) : bits des pièces qui s'usent ou ne sont pas entretenues : une ressource d'entretien par bit
     * ({@code 1 << slot}, comme {@link ClaimUpkeep#consume}), et les brindilles ({@link #BRINDILLES}, sans entretien : elles
     * ne s'usent que dans une base inactive ou sans armoire). {@link #TOUT} : toutes les pièces.
     */
    private static final int BRINDILLES = 1 << ClaimBlockEntity.SLOTS, TOUT = (1 << (ClaimBlockEntity.SLOTS + 1)) - 1;
    /**
     * HeraliZ (03/11) : horloge de l'usure ({@link PenteUsure}) : heures réelles des passes faites depuis le démarrage, et
     * l'heure (ms) de la dernière passe. Une passe compte le temps réel depuis la précédente, 2 min au plus (serveur arrêté
     * ou très en retard : pas de rattrapage d'un coup).
     */
    private static double horlogeH;
    private static long dernierePasseMs;
    private static final long PASSE_MAX_MS = 120_000L;
    /** Levels whose untrusted stores were reported this session (once each). */
    private static final Set<ResourceKey<Level>> DISTRUSTED = new HashSet<>();
    /** Claim Blocks kept on structure blocks the store does not know, reported this session (once each). */
    private static final Set<GlobalPos> KEPT = new HashSet<>();

    private ClaimMaintenanceManager() {
    }

    /** No member of the group was online for {@code decayDays} of server uptime (downtime does not count). */
    public static boolean isInactive(MinecraftServer server, UUID groupId) {
        if (!Config.decayEnabled.get() || !fr.heraliz.crac.heraliz.Heraliz.inactivityDecay()) {
            return false;
        }
        GroupData gd = GroupData.get(server);
        Group g = gd.getById(groupId);
        if (g == null) {
            return false;
        }
        long now = server.overworld().getGameTime();
        if (g.lastActiveTick() < 0L) {
            // saved before activity was tracked in game time: start counting from now
            g.touchActive(now);
            gd.markDirty();
            return false;
        }
        return inactiveFor(g.lastActiveTick(), now, Config.decayDays.get());
    }

    /** More than {@code days} of server uptime passed between the last activity and {@code now} (game ticks). */
    public static boolean inactiveFor(long lastActiveTick, long now, double days) {
        return now - lastActiveTick > (long) (days * TICKS_PER_DAY);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // an integrated server keeps these statics from the previous world
        nextRunTick = -1L;
        passe = null;
        UNPAID.clear();
        horlogeH = 0.0;
        dernierePasseMs = 0L;
        DISTRUSTED.clear();
        KEPT.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long tick = server.overworld().getGameTime();
        if (nextRunTick < 0L || nextRunTick - tick > INTERVAL_TICKS) {
            // first tick after start, or a world whose clock is behind the cursor: the first pass is one interval away,
            // which also leaves members time to log in after a restart
            nextRunTick = tick + INTERVAL_TICKS;
            passe = null;
            return;
        }
        if (tick >= nextRunTick) {
            nextRunTick = tick + INTERVAL_TICKS;
            if (passe != null) {
                avancer(server, Integer.MAX_VALUE);   // la passe d'avant n'a pas fini en une minute : finie d'abord
            }
            commencer(server, tick);
        }
        if (passe != null) {
            avancer(server, BUDGET);
        }
    }

    /** Arrêt du serveur : une passe commencée est finie (comme avant, quand elle se faisait d'un coup). */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (passe != null) {
            try {
                avancer(event.getServer(), Integer.MAX_VALUE);
            } catch (RuntimeException ex) {
                Crac.LOGGER.error("[CRac claims] upkeep pass at server stop failed", ex);
                passe = null;
            }
        }
    }

    /** Début d'une passe : activité des groupes (comme avant), puis les mondes un par un ({@link #avancer}). */
    private static void commencer(MinecraftServer server, long tick) {
        GroupData gd = GroupData.get(server);
        boolean dirty = false;
        for (Group g : gd.all()) {
            boolean online = false;
            for (UUID m : g.members()) {
                if (server.getPlayerList().getPlayer(m) != null) {
                    online = true;
                    break;
                }
            }
            // HeraliZ Clans: a member of the owner's clan online keeps the base lived in too
            if (online || ClanAccess.clanOnline(g)) {
                g.touchActive(tick);
                dirty = true;
            }
        }
        if (dirty) {
            gd.markDirty();
        }
        List<ServerLevel> mondes = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            mondes.add(level);
        }
        // horloge de l'usure : le temps réel depuis la passe d'avant (une minute à la première)
        long maintenant = System.currentTimeMillis();
        long ecart = dernierePasseMs <= 0L ? INTERVAL_TICKS * 50L : Math.max(0L, Math.min(PASSE_MAX_MS, maintenant - dernierePasseMs));
        dernierePasseMs = maintenant;
        double avant = horlogeH;
        horlogeH += ecart / 3_600_000.0;
        passe = new Passe(mondes, ecart, avant, horlogeH);
    }

    /** Ce qu'il reste à faire de la passe d'une minute, monde par monde : claims, puis bâtiments, puis objets posés. */
    private static final class Passe {
        final List<ServerLevel> mondes;
        /** HeraliZ (03/11) : temps réel compté par cette passe (ms), et l'horloge de l'usure avant et après elle (heures). */
        final long ecartMs;
        final double horlogeAvant, horlogeApres;
        int monde = -1;
        @Nullable
        Monde courant;

        Passe(List<ServerLevel> mondes, long ecartMs, double horlogeAvant, double horlogeApres) {
            this.mondes = mondes;
            this.ecartMs = ecartMs;
            this.horlogeAvant = horlogeAvant;
            this.horlogeApres = horlogeApres;
        }
    }

    /** Un monde de la passe : les réglages et les relevés de son début (comme la passe d'avant, faite d'un coup), et où on en est. */
    private static final class Monde {
        final ServerLevel level;
        final int decayHp = Config.decayHpPerMinute.get();
        final int repairHp = Config.repairHpPerMinute.get();
        final boolean repairOn = Config.autoRepairEnabled.get();
        final boolean decayOn = Config.decayEnabled.get();
        final long graceTicks = Config.decayGraceMinutes.get() * 1200L;
        final long now;
        final ClaimData cd;
        final StructureStore store;
        final boolean unclaimedDecays;
        final Map<Long, Building> claimed;
        final List<Claim> claims;
        /**
         * HeraliZ (03/11) : par groupe, les pièces qui s'usent (ressource sans entretien depuis le délai, groupe inactif) et
         * celles qui ne sont pas entretenues (pas de réparation toute seule), en bits ({@link #TOUT}...).
         */
        final Map<UUID, Integer> usure = new HashMap<>();
        final Map<UUID, Integer> impaye = new HashMap<>();
        /** HeraliZ (03/11) : pente de l'usure, délai avant l'usure (ms), temps de la passe et horloge de l'usure. */
        final PenteUsure pente = new PenteUsure(Config.decayHighPercentPerHour.get(), Config.decayThresholdPercent.get(),
            Config.decayLowPercentPerHour.get());
        final long delaiMs = Math.round(Config.decayDelayHours.get() * 3_600_000.0);
        final long ecartMs;
        final double horlogeAvant, horlogeApres;
        int claim;
        @Nullable
        List<Building> buildings;
        int building;
        @Nullable
        long[] placables;
        int placable;

        Monde(ServerLevel level, Passe passe) {
            this.level = level;
            this.ecartMs = passe.ecartMs;
            this.horlogeAvant = passe.horlogeAvant;
            this.horlogeApres = passe.horlogeApres;
            this.now = level.getGameTime();
            this.cd = ClaimData.get(level);
            this.store = StructureStore.get(level);
            String distrust = distrust(this.store, this.cd);
            if (distrust != null && DISTRUSTED.add(level.dimension())) {
                Crac.LOGGER.warn("[CRac claims] {} in {}: Claim Blocks, claims and building links are left as they are{} until the file is"
                    + " restored (with the server stopped) or repaired", distrust, level.dimension().location(),
                    this.cd.isTrusted() ? "" : ", and nothing decays for lack of a claim");
            }
            // what lacks a claim may only lack an unreadable one
            this.unclaimedDecays = Config.decayUnclaimed.get() && this.decayOn && this.cd.isTrusted();
            // claimed buildings by Claim Block position, built once per pass
            this.claimed = claimedBuildings(level);
            this.claims = new ArrayList<>(this.cd.all());
        }
    }

    /** Fait au plus {@code budget} unités de la passe en cours (une par claim, par pièce, par objet posé). */
    private static void avancer(MinecraftServer server, int budget) {
        Passe p = passe;
        while (p != null && budget > 0) {
            Monde m = p.courant;
            if (m == null) {
                if (++p.monde >= p.mondes.size()) {
                    passe = null;
                    // a group whose claim is gone starts afresh: its next claim warns again
                    UNPAID.keySet().removeIf(id -> ClaimManager.getByGroupAnywhere(server, id) == null);
                    return;
                }
                m = new Monde(p.mondes.get(p.monde), p);
                p.courant = m;
                budget -= 1 + m.claimed.size();
            } else if (m.claim < m.claims.size()) {
                budget -= claim(server, m, m.claims.get(m.claim++));
            } else if (m.buildings == null) {
                m.buildings = new ArrayList<>(m.store.index.allBuildings());
                budget -= 1 + m.buildings.size() / 16;
            } else if (m.building < m.buildings.size()) {
                budget -= building(m, m.buildings.get(m.building++));
            } else if (m.placables == null) {
                m.placables = Config.decayPlacables.get() ? PlacablePositionData.get(m.level).snapshot() : new long[0];
                budget -= 1 + m.placables.length / 16;
            } else if (m.placable < m.placables.length) {
                placable(m, m.placables[m.placable++]);
                budget--;
            } else {
                p.courant = null;
            }
        }
    }

    /** Un claim : vérifié contre le monde, entretien payé, écran de l'armoire, groupe qui s'use ou non. Rend son coût. */
    private static int claim(MinecraftServer server, Monde m, Claim claim) {
        ServerLevel level = m.level;
        BlockPos anchor = claim.anchor();
        if (m.cd.getByAnchor(anchor) != claim) {
            return 1;   // retiré (ou remplacé) depuis le début de la passe : plus rien à payer pour lui
        }
        int impayees = 0, usure = 0;
        int cout = 1;
        if (level.isLoaded(anchor)) {
            Building b = m.claimed.get(anchor.asLong());
            ClaimBlockEntity be = reconcile(level, claim, b);
            if (be == null) {
                return cout;
            }
            if (b == null) {
                // linked again by reconcile, or still unknown (costs nothing)
                b = StructureService.buildingOfClaim(level, anchor);
            }
            cout += b == null ? 0 : b.pieces.size();
            // HeraliZ (03/11, demande du propriétaire) : chaque ressource à part ; seules les pièces de celles qui manquent
            // s'usent, après decay.delayHours (0 : tout de suite)
            int vides = ClaimUpkeep.consume(level, claim, be, b);   // l'entretien se paie même usure coupée
            impayees = m.decayOn ? vides : 0;
            usure = be.majManque(impayees, m.ecartMs, m.delaiMs);
            be.majEcran();   // écran de l'armoire : entretien demandé et temps restant (délai compris)
            warnUpkeep(server, claim.groupId(), usure != 0 ? USURE : impayees != 0 ? VIDE : PAYE, be.upkeepMinutes());
        }
        if (m.decayOn && isInactive(server, claim.groupId())) {
            impayees = TOUT;
            usure = TOUT;
        }
        m.impaye.put(claim.groupId(), impayees);
        m.usure.put(claim.groupId(), m.decayOn ? usure : 0);
        return cout;
    }

    /** HeraliZ (03/11) : le bit d'une pièce dans les masques d'usure ({@link #BRINDILLES} ou sa ressource d'entretien). */
    private static int bit(Piece p) {
        return p.tier == fr.heraliz.crac.build.model.Tier.TWIG ? BRINDILLES : 1 << UpkeepResource.of(p.tier).slot();
    }

    /**
     * Un bâtiment : ses pièces s'usent (entretien de leur ressource pas payé, groupe inactif, sans claim) ou se réparent.
     * Rend son coût. HeraliZ (03/11, demande du propriétaire) : l'usure suit {@link PenteUsure} (10 % des PV max par heure
     * jusqu'à 30 %, puis 5 %), aussi pour une partie de base coupée de l'armoire (C4, explosion : un bâtiment sans claim
     * que tenait un groupe) ; seul un bâtiment qu'aucun clan n'a jamais tenu (fondations laissées dans la nature) garde
     * l'usure rapide de {@code decay.hpPerMinute}. Une pièce dont la ressource est payée se répare toute seule comme avant.
     */
    private static int building(Monde m, Building b) {
        ServerLevel level = m.level;
        Claim claim = StructureService.claimOf(level, b);
        int usure, impaye;
        boolean rapide = false;
        if (claim == null) {
            if (!m.unclaimedDecays) return 1;
            usure = TOUT;
            impaye = TOUT;
            rapide = b.ownerGroup == null;
        } else {
            usure = m.usure.getOrDefault(claim.groupId(), 0);
            impaye = m.impaye.getOrDefault(claim.groupId(), 0);
        }
        boolean repair = claim != null && impaye != TOUT && m.repairOn && !RaidBlockManager.isRaidBlocked(level, claim.groupId());
        if (usure == 0 && !repair) {
            return 1;
        }
        List<Piece> pieces = m.store.piecesOf(b);
        for (Piece p : pieces) {
            int bit = bit(p);
            if ((usure & bit) != 0) {
                if (m.now - p.modifiedAt < m.graceTicks || !StructureService.isLoaded(level, p)) continue;
                int perte = rapide ? m.decayHp : m.pente.perte(p.hp, p.maxHp, m.horlogeAvant, m.horlogeApres);
                if (perte > 0) {
                    StructureService.damage(level, p, perte, false, StructureService.Cause.DECAY);
                }
            } else if (repair && (impaye & bit) == 0 && p.hp < p.maxHp && StructureService.isLoaded(level, p)) {
                StructureService.heal(level, p, m.repairHp);
            }
        }
        return 1 + pieces.size();
    }

    /**
     * Un objet posé (lit, porte, caisse…) : il s'use avec le claim qui l'entoure, ou sans claim s'il en a eu un. HeraliZ
     * (03/11) : dès qu'une ressource de la base s'use, à la même pente que les pièces ({@link PenteUsure}).
     */
    private static void placable(Monde m, long l) {
        ServerLevel level = m.level;
        PlacablePositionData ppd = PlacablePositionData.get(level);
        BlockPos pos = BlockPos.of(l);
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!PlacableHpManager.isTracked(state)) {
            ppd.remove(pos);
            return;
        }
        if (!decaysAsPlacable(state)) return;
        Claim claim = m.cd.getAt(level, pos);
        boolean decays;
        if (claim != null) {
            ppd.markClaimed(pos);
            decays = m.usure.getOrDefault(claim.groupId(), 0) != 0;
        } else {
            decays = m.unclaimedDecays && ppd.wasClaimed(pos);
        }
        if (decays) {
            int perte = m.pente.perte(PlacableHpManager.currentHp(level, state, pos), PlacableHpManager.maxHpFor(state.getBlock()),
                m.horlogeAvant, m.horlogeApres);
            if (perte > 0) {
                PlacableHpManager.decayDamage(level, pos, perte);
            }
        }
    }

    /**
     * Why the claims and the buildings of a level cannot be checked against each other, or null when they can: a store
     * stands in for a lost file or kept entries it could not read, so a building or a claim missing from it may still
     * exist. Claim maintenance then pops, removes and unlinks nothing until the file is restored.
     */
    @Nullable
    public static String distrust(ServerLevel level) {
        return distrust(StructureStore.get(level), ClaimData.get(level));
    }

    /** Same as {@link #distrust(ServerLevel)} for the given stores. */
    @Nullable
    public static String distrust(StructureStore store, ClaimData claims) {
        List<String> why = new ArrayList<>();
        if (!store.isTrusted()) {
            why.add(StructureStore.ID + ".dat" + (store.isStandIn() ? " was lost or could not be read" : " has entries that could not be read"));
        }
        if (!claims.isTrusted()) {
            why.add(ClaimData.ID + ".dat" + (claims.isStandIn() ? " was lost or could not be read" : " has entries that could not be read"));
        }
        return why.isEmpty() ? null : String.join(" and ", why);
    }

    /**
     * Checks a claim against the world, its Claim Block's chunk being loaded. A claim whose block is gone (a crash
     * between the chunk and the claim data saves, a world edit) is removed. A Claim Block whose link to its building was
     * lost is linked again to the building it stands on; one left without a building is popped, since such a claim would
     * hold the land for free, unless the block under it holds pieces the store does not know. Nothing is removed while
     * {@link #distrust} finds a store that may only miss the data. Returns the block entity of a claim that holds, else null.
     */
    @Nullable
    public static ClaimBlockEntity reconcile(ServerLevel level, Claim claim, @Nullable Building building) {
        BlockPos anchor = claim.anchor();
        boolean trusted = distrust(level) == null;
        if (!(level.getBlockEntity(anchor) instanceof ClaimBlockEntity be)) {
            if (trusted) {
                Crac.LOGGER.warn("Removing the claim of group {} at {}: its Claim Block is missing", claim.groupId(), anchor.toShortString());
                ClaimManager.removeByAnchor(level, anchor);
                C2SRequestNearbyClaimsTraitement.pushAll(level);
            }
            return null;
        }
        if (building == null) {
            Building under = ClaimManager.buildingUnder(level, anchor);
            if (under != null && StructureService.claimOf(level, under) == null) {
                // claims restored from a file older than the buildings' links: the Claim Block still stands on its building
                StructureService.onClaimCreated(level, anchor);
                Crac.LOGGER.info("Linked the claim of group {} at {} back to the building under its Claim Block", claim.groupId(),
                    anchor.toShortString());
            } else if (trusted && !StructureIntegrity.holdsUnknownPieces(level, anchor.below())) {
                Crac.LOGGER.warn("Removing the claim of group {} at {}: {}", claim.groupId(), anchor.toShortString(),
                    under == null ? "no building under its Claim Block" : "the building under its Claim Block has another claim");
                level.destroyBlock(anchor, false);
                if (ClaimManager.getByAnchor(level, anchor) != null) {
                    ClaimManager.removeByAnchor(level, anchor);
                }
                C2SRequestNearbyClaimsTraitement.pushAll(level);
                return null;
            } else if (trusted && KEPT.add(GlobalPos.of(level.dimension(), anchor))) {
                Crac.LOGGER.warn("Keeping the claim of group {} at {}: its Claim Block stands on structure blocks that {}.dat does not know"
                    + " (restore the file, or remove them with /cracbuild clear)", claim.groupId(), anchor.toShortString(), StructureStore.ID);
            }
        }
        if (!claim.groupId().equals(be.ownerGroup())) {
            // Claim Blocks placed before they remembered their group
            be.setOwnerGroup(claim.groupId());
        }
        return be;
    }

    /**
     * Tells the online members when their base starts decaying for lack of upkeep, and when it is paid again. HeraliZ
     * (03/11) : with a delay before the decay ({@code decay.delayHours} above 0), also when a resource runs out
     * ({@code minutes} : until the decay starts).
     */
    private static void warnUpkeep(MinecraftServer server, UUID groupId, int etat, int minutes) {
        Integer avant = etat == PAYE ? UNPAID.remove(groupId) : UNPAID.put(groupId, etat);
        int a = avant == null ? PAYE : avant;
        // une ressource de nouveau payée pendant qu'une autre attend encore son délai : rien à dire
        Group g = a != etat && !(a == USURE && etat == VIDE) ? GroupData.get(server).getById(groupId) : null;
        if (g == null) {
            return;
        }
        Component msg = switch (etat) {
            case USURE -> Txt.red("crac.upkeep.decaying");
            case VIDE -> Txt.yellow("crac.upkeep.vide", minutes / 60, minutes % 60);
            default -> Txt.green("crac.upkeep.paid");
        };
        for (UUID m : g.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    /**
     * Whether a placable decays with the claim around it. The Claim Block does not: it goes when the piece it stands
     * on is destroyed, and keeps the claim and the upkeep stock until then.
     */
    public static boolean decaysAsPlacable(BlockState state) {
        return !(state.getBlock() instanceof ClaimBlock);
    }

    /**
     * Claimed buildings of the level by Claim Block position. A link to a claim that no longer exists is dropped, unless
     * {@link #distrust} finds a store that may only miss that claim.
     */
    public static Map<Long, Building> claimedBuildings(ServerLevel level) {
        boolean trusted = distrust(level) == null;
        ClaimData cd = ClaimData.get(level);
        StructureStore store = StructureStore.get(level);
        Map<Long, Building> claimed = new HashMap<>();
        for (Building b : store.index.allBuildings()) {
            if (b.claimAnchor == null) continue;
            if (cd.getByAnchor(b.claimAnchor) != null) {
                claimed.put(b.claimAnchor.asLong(), b);
            } else if (trusted) {
                b.claimAnchor = null;
                store.setDirty();
            }
        }
        return claimed;
    }
}
