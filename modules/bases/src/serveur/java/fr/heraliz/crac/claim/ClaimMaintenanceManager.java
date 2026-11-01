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
    /** Groups whose upkeep had run out at the last pass, to warn their members once when it starts and stops. */
    private static final Set<UUID> UNPAID = new HashSet<>();
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
        passe = new Passe(mondes);
    }

    /** Ce qu'il reste à faire de la passe d'une minute, monde par monde : claims, puis bâtiments, puis objets posés. */
    private static final class Passe {
        final List<ServerLevel> mondes;
        int monde = -1;
        @Nullable
        Monde courant;

        Passe(List<ServerLevel> mondes) {
            this.mondes = mondes;
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
        final Map<UUID, Boolean> decaying = new HashMap<>();
        int claim;
        @Nullable
        List<Building> buildings;
        int building;
        @Nullable
        long[] placables;
        int placable;

        Monde(ServerLevel level) {
            this.level = level;
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
                    UNPAID.removeIf(id -> ClaimManager.getByGroupAnywhere(server, id) == null);
                    return;
                }
                m = new Monde(p.mondes.get(p.monde));
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
        boolean unpaid = false;
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
            unpaid = ClaimUpkeep.consumeAndCheck(level, claim, be, b) && m.decayOn;
            be.majEcran();   // écran de l'armoire : entretien demandé et temps restant
            warnUpkeep(server, claim.groupId(), unpaid);
        }
        m.decaying.put(claim.groupId(), m.decayOn && (unpaid || isInactive(server, claim.groupId())));
        return cout;
    }

    /** Un bâtiment : ses pièces s'usent (entretien pas payé, groupe inactif, sans claim) ou se réparent. Rend son coût. */
    private static int building(Monde m, Building b) {
        ServerLevel level = m.level;
        Claim claim = StructureService.claimOf(level, b);
        boolean decay;
        if (claim == null) {
            if (!m.unclaimedDecays) return 1;
            decay = true;
        } else if (m.decaying.getOrDefault(claim.groupId(), false)) {
            decay = true;
        } else if (m.repairOn && !RaidBlockManager.isRaidBlocked(level, claim.groupId())) {
            decay = false;
        } else {
            return 1;
        }
        List<Piece> pieces = m.store.piecesOf(b);
        for (Piece p : pieces) {
            boolean skip = decay ? m.now - p.modifiedAt < m.graceTicks : p.hp >= p.maxHp;
            if (skip || !StructureService.isLoaded(level, p)) continue;
            if (decay) {
                StructureService.damage(level, p, m.decayHp, false, StructureService.Cause.DECAY);
            } else {
                StructureService.heal(level, p, m.repairHp);
            }
        }
        return 1 + pieces.size();
    }

    /** Un objet posé (lit, porte, caisse…) : il s'use avec le claim qui l'entoure, ou sans claim s'il en a eu un. */
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
            decays = m.decaying.getOrDefault(claim.groupId(), false);
        } else {
            decays = m.unclaimedDecays && ppd.wasClaimed(pos);
        }
        if (decays) {
            PlacableHpManager.decayDamage(level, pos, m.decayHp);
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

    /** Tells the online members when their base starts decaying for lack of upkeep, and when it is paid again. */
    private static void warnUpkeep(MinecraftServer server, UUID groupId, boolean unpaid) {
        boolean changed = unpaid ? UNPAID.add(groupId) : UNPAID.remove(groupId);
        Group g = changed ? GroupData.get(server).getById(groupId) : null;
        if (g == null) {
            return;
        }
        Component msg = unpaid ? Txt.red("crac.upkeep.decaying") : Txt.green("crac.upkeep.paid");
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
