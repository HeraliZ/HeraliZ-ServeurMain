package fr.heraliz.crac.event;

import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.heraliz.Txt;
import fr.heraliz.crac.heraliz.ClanAccess;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import fr.heraliz.crac.raid.RaidBlockManager;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(
    modid = "crac"
)
public final class ClaimBlockHandler {
    /** A group cannot take its Claim Block away during its raid block, neither by breaking it nor by disbanding. */
    public static final String RAID_BLOCKED_REMOVAL = "crac.claim.raid_removal";

    private ClaimBlockHandler() {
    }

    @SubscribeEvent
    public static void onPlace(EntityPlaceEvent event) {
        if (event.getPlacedBlock().getBlock() != ModBlocks.CLAIM_BLOCK.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        Component deny = claim(level, player, pos);
        if (deny != null) {
            event.setCanceled(true);
            player.displayClientMessage(deny, true);
            PlacementSync.revert(player, level, pos);
        }
    }

    /**
     * Creates the claim of the Claim Block the player just set at {@code pos}, or returns why not. On a building grown
     * under a bigger claim it takes the smallest tier whose zone holds the whole building, and the player pays every
     * upgrade up to that tier (so a big base can still be claimed again or taken over).
     */
    @Nullable
    public static Component claim(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Component heraliz = Heraliz.buildProblem(player);
        if (heraliz != null) {
            return heraliz.copy().withStyle(ChatFormatting.RED);
        }
        // HeraliZ : pendant le tutoriel des bases, seulement à son étape, sur une fondation de la cabane
        Component tuto = fr.heraliz.crac.bases.Tutoriel.refusClaim(level, player, pos);
        if (tuto != null) {
            return tuto.copy().withStyle(ChatFormatting.YELLOW);
        }
        Group g;
        if (fr.heraliz.crac.heraliz.BasesClans.actif()) {
            // une base par clan (1.16.0) : le groupe du clan ; sans clan, pas de base (sauf la cabane du tutoriel)
            g = fr.heraliz.crac.heraliz.BasesClans.groupe(level.getServer(), player.getUUID());
            if (g == null && fr.heraliz.crac.bases.MondeBases.estTuto(level)) {
                g = GroupData.get(level.getServer()).getByMember(player.getUUID());
            }
            if (g == null && !player.getAbilities().instabuild) {
                return Component.translatableWithFallback("crac.claim.sans_clan",
                    "Il faut un clan pour poser une base : crée ou rejoins un clan (touche K).").withStyle(ChatFormatting.RED);
            }
        } else {
            g = GroupData.get(level.getServer()).getByMember(player.getUUID());
            if (g == null) {
                g = ClanAccess.groupForClanManager(level.getServer(), player.getUUID());
            }
        }
        // zones où l'on ne construit pas (villes, loot, rails, neige…), carte principale
        Component zone = fr.heraliz.crac.heraliz.ZonesConstruction.refus(level, pos, player);
        if (zone != null) {
            return zone;
        }
        int tier = StructureService.claimTierFor(level, pos);
        String problem = placementProblem(level, player, g, pos, tier);
        if (problem != null) {
            return Txt.red(problem);
        }
        List<BuildCost> cost = ClaimManager.tierCost(tier);
        if (!Costs.has(player, cost)) {
            return Txt.red("crac.claim.needs_tier", tier, Costs.describe(cost));
        }
        Costs.take(player, cost);
        ClaimManager.addClaim(level, pos, g.id(), tier);
        fr.heraliz.crac.bases.AccesBase.get(level.getServer()).armoirePosee(player.getUUID(), level, pos);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ClaimBlock && state.getValue(ClaimBlock.TIER) != tier) {
            level.setBlock(pos, state.setValue(ClaimBlock.TIER, tier), Block.UPDATE_ALL);
        }
        C2SRequestNearbyClaimsTraitement.pushAll(level);
        player.displayClientMessage(Txt.green("crac.claim.created", g.name(), tier, Config.claimRadius(tier)), false);
        // monde des bases : home « base » du joueur s'il n'en a aucun (/home du spawn)
        fr.heraliz.crac.bases.Homes.premiereBase(player, level, pos);
        // tutoriel : l'armoire tournée vers la pièce et poussée dans l'angle (1.11.0)
        fr.heraliz.crac.bases.Tutoriel.armoirePosee(player, level, pos);
        return null;
    }

    /** Why this player may not place a Claim Block at {@code pos}, or null. */
    @Nullable
    public static String placementProblem(ServerLevel level, Player player, @Nullable Group g, BlockPos pos) {
        return placementProblem(level, player, g, pos, 1);
    }

    /** Same for a claim of the given tier (a Claim Block whose claim is restored keeps its tier). */
    @Nullable
    public static String placementProblem(ServerLevel level, Player player, @Nullable Group g, BlockPos pos, int tier) {
        if (pos.getY() < Config.claimMinY.get() || pos.getY() > Config.claimMaxY.get()) {
            return Txt.k("crac.claim.y_range", Config.claimMinY.get(), Config.claimMaxY.get());
        }
        // the Nether roof: above the dimension's logical height nobody can reach a base
        if (pos.getY() >= level.getMinBuildHeight() + level.dimensionType().logicalHeight()) {
            return "crac.claim.ceiling";
        }
        if (g == null) {
            return "crac.claim.no_group";
        }
        boolean clan = fr.heraliz.crac.heraliz.BasesClans.estGroupeClan(level.getServer(), g.id());
        if (clan ? !fr.heraliz.crac.heraliz.BasesClans.peutGerer(player.getUUID()) && !player.getAbilities().instabuild
            : !g.isOwner(player.getUUID())) {
            return clan ? "crac.claim.gerer_clan" : "crac.claim.owner_only";
        }
        // une base par clan (1.16.0) : un groupe ne tient qu'un claim (one_per_group plus bas), plus de limite par joueur
        // le tutoriel (monde à part) : sa cabane d'essai ne compte pas pour la base du clan
        if (fr.heraliz.crac.bases.MondeBases.estTuto(level)) {
            return ClaimManager.tooCloseTo(level, pos, g.id()) != null ? "crac.build.too_close" : null;
        }
        if (RaidBlockManager.isRaidBlocked(level, g.id())) {
            return "crac.claim.raid_new";
        }
        if (RaidBlockManager.isZoneRaidBlocked(level, pos)) {
            return "crac.claim.raid_zone";
        }
        if (ClaimManager.getByGroupAnywhere(level.getServer(), g.id()) != null) {
            return "crac.claim.one_per_group";
        }
        if (ClaimManager.tooCloseTo(level, pos, g.id()) != null) {
            return "crac.build.too_close";
        }
        String problem = StructureService.claimProblem(level, pos, tier);
        if (problem != null) {
            return problem;
        }
        // monde des bases (1.5.0) : la zone du claim ne touche aucune carrière
        problem = fr.heraliz.crac.bases.CarrieresServeur.probleme(level, pos, Config.claimRadius(tier));
        if (problem != null) {
            return problem;
        }
        Building own = ClaimManager.buildingUnder(level, pos);
        if (ClaimManager.foreignBuildingIn(level, new Claim(g.id(), pos, tier), own, g.id()) != null) {
            return "crac.claim.foreign_building";
        }
        return null;
    }

    @SubscribeEvent
    public static void onBreak(BreakEvent event) {
        if (event.getState().getBlock() != ModBlocks.CLAIM_BLOCK.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getAbilities().instabuild) {
            return;
        }
        BlockPos pos = event.getPos();
        Claim claim = ClaimManager.getByAnchor(level, pos);
        if (claim == null) {
            // a Claim Block that lost its claim: only its group may break it and take back its upkeep
            UUID owner = level.getBlockEntity(pos) instanceof ClaimBlockEntity be ? be.ownerGroup() : null;
            Group og = owner == null ? null : GroupData.get(level.getServer()).getById(owner);
            if (og != null && !ClanAccess.allows(og, player.getUUID(), ClanAccess.Action.MANAGE)) {
                event.setCanceled(true);
                player.displayClientMessage(Txt.red("crac.claim.not_your_block"), true);
            }
            return;
        }
        Group g = GroupData.get(level.getServer()).getById(claim.groupId());
        if (!ClanAccess.allows(g, player.getUUID(), ClanAccess.Action.MANAGE)) {
            event.setCanceled(true);
            player.displayClientMessage(Txt.red("crac.claim.not_yours"), true);
            return;
        }
        if (RaidBlockManager.isRaidBlocked(level, claim, pos)) {
            event.setCanceled(true);
            player.displayClientMessage(Txt.red(RAID_BLOCKED_REMOVAL), true);
        }
        // the claim itself goes in ClaimBlock.onRemove, once the block is really gone (a later listener may still cancel)
    }

    /** Runs only when nobody cancelled the break. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterBreak(BreakEvent event) {
        if (event.getState().getBlock() == ModBlocks.CLAIM_BLOCK.get()
            && event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer player
            && ClaimManager.getByAnchor(level, event.getPos()) != null) {
            player.displayClientMessage(Txt.yellow("crac.claim.removed"), true);
        }
    }
}
