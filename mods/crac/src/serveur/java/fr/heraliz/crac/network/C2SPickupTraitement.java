package fr.heraliz.crac.network;

import fr.heraliz.crac.placable.PlacableChutes;
import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.heraliz.Txt;
import fr.heraliz.crac.heraliz.ClanAccess;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.event.ClaimProtectionHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.placable.MultiBlocks;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.placable.PlacableSupport;
import fr.heraliz.crac.raid.RaidBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;
import org.jetbrains.annotations.Nullable;

/**
 * Traitement serveur du paquet C2SPickup (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SPickupTraitement {
    private C2SPickupTraitement() {
    }

    static final double RANGE_SQR = 36.0;
    public static void traiter(C2SPickup msg, Supplier<Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> process(player, msg.pos()));
        ctx.get().setPacketHandled(true);
    }    /** Server side of the packet, on the server thread (public for the GameTests). */
    public static void process(@Nullable ServerPlayer player, BlockPos pos) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.getAbilities().instabuild) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > RANGE_SQR
            || Heraliz.blocked(level)) {
            return;
        }
        Component combat = Heraliz.combatProblem(player);
        if (combat != null) {
            player.displayClientMessage(combat.copy().withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        // spawn protection and the world border, which the vanilla use path would have checked
        if (!level.mayInteract(player, pos) || !ActionCooldown.allow(player)) {
            return;
        }
        BlockState target = level.getBlockState(pos);
        Block block = target.getBlock();
        if (!PlacableSet.contains(level, block) || block instanceof StructureBlock || block.asItem() == Items.AIR) {
            return;
        }
        BlockPos other = MultiBlocks.partnerIn(level, target, pos);
        List<BlockPos> cells = other == null ? List.of(pos) : List.of(pos, other);
        // the packet only names a block: a modified client could name one behind a wall
        if (!ClaimProtectionHandler.blockInSight(level, player, pos, cells)) {
            player.displayClientMessage(Txt.red("crac.reach.no_sight"), true);
            return;
        }
        String deny = problem(level, player, pos, target, cells);
        if (deny != null) {
            player.displayClientMessage(Txt.red(deny), true);
            return;
        }

        // read before forget(): a damaged placable keeps its HP in the item
        ItemStack drop = PlacableHpManager.pickupItem(level, target, pos);
        PlacableHpManager.forget(level, target, pos);
        // both halves go without drops: the item below is the only one handed out
        MultiBlocks.remove(level, pos, false);
        level.playSound(null, pos, target.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
        PlacableChutes.dropUnsupportedAbove(level, pos);
        if (other != null) {
            PlacableChutes.dropUnsupportedAbove(level, other);
        }
        if (!player.getInventory().add(drop)) {
            player.drop(drop, false);
        }
    }    /** Why the player may not pick up the placable at {@code pos} (both halves in {@code cells}), or null. */
    @Nullable
    static String problem(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState target, List<BlockPos> cells) {
        boolean claimed = false;
        GroupData groups = GroupData.get(level.getServer());
        for (BlockPos cell : cells) {
            if (level.getBlockEntity(cell) instanceof Container c && !c.isEmpty()) {
                return "crac.placable.items_inside";
            }
            Claim claim = ClaimManager.getAt(level, cell);
            if (claim != null) {
                claimed = true;
                Group g = groups.getById(claim.groupId());
                if (!ClanAccess.allows(g, player.getUUID(), ClanAccess.Action.BREAK)) {
                    return "crac.claim.not_yours";
                }
                if (Config.raidBlockBuilding.get() && RaidBlockManager.isRaidBlocked(level, claim.groupId())) {
                    return "crac.placable.pickup_raid";
                }
            }
        }
        if (!claimed && !fr.heraliz.crac.heraliz.Heraliz.cracRulesOutsideClaims()) {
            // HeraliZ: out of a claim a chest, a furnace or a barrel is a block of the map (loot spot, decor), never
            // something to pocket
            return "crac.placable.only_claims";
        }
        return null;
    }}
