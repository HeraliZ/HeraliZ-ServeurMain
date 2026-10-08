package fr.heraliz.crac.event;

import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.heraliz.Txt;
import fr.heraliz.crac.heraliz.ClanAccess;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.placable.PlacableSupport;
import fr.heraliz.crac.raid.RaidBlockManager;
import fr.heraliz.crac.raid.RaidBlocks;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber
public class BuildEventHandler {
    private static final TagKey<Block> PLACABLE = BlockTags.create(new ResourceLocation("crac", "placable"));

    @SubscribeEvent
    public static void onBlockBreak(BreakEvent event) {
        Level level = (Level)event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getPlayer();
        boolean isBuildBlock = level.getBlockState(pos).getBlock() instanceof StructureBlock;
        if (player != null && player.getAbilities().instabuild) {
            if (isBuildBlock && level instanceof ServerLevel sl) {
                // creative: remove only the piece aimed at, not every piece sharing the block
                Piece piece = StructureService.pieceAt(sl, pos, player.pick(8.0, 1.0F, false).getLocation());
                if (piece != null) {
                    StructureService.remove(sl, piece, StructureService.Cause.ADMIN);
                }
                event.setCanceled(true);
            }
        } else if (isBuildBlock) {
            event.setCanceled(true);
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
                BlockEntity be = sl.getBlockEntity(pos);
                if (be != null) {
                    Packet<ClientGamePacketListener> pkt = be.getUpdatePacket();
                    if (pkt != null) {
                        sp.connection.send(pkt);
                    }
                }
            }
        } else if (level instanceof ServerLevel slx && player instanceof ServerPlayer spx) {
            Claim claim = ClaimManager.getAt(slx, pos);
            if (claim != null) {
                if (slx.getBlockState(pos).getBlock() != ModBlocks.CLAIM_BLOCK.get()) {
                    // members may always clear the ladders and trapdoors raiders left in their base (RaidBlocks.ACCESS)
                    boolean mayMine = isMember(slx, spx, claim, ClanAccess.Action.BREAK) && (Config.allowBreakInClaim.get() || event.getState().is(RaidBlocks.ACCESS));
                    if (!mayMine) {
                        event.setCanceled(true);
                        spx.displayClientMessage(Txt.red(Config.allowBreakInClaim.get() ? "crac.claim.not_yours" : "crac.claim.no_mining"), true);
                    } else if (Config.raidBlockBuilding.get()
                        && RaidBlockManager.isRaidBlocked(slx, claim.groupId())
                        && PlacableHpManager.isTracked(event.getState())) {
                        // mined and placed again, a damaged placable would come back at full HP mid-raid
                        event.setCanceled(true);
                        spx.displayClientMessage(Txt.red("crac.build.raid_blocked"), true);
                    }
                }
            }
            if (!event.isCanceled() && PlacableSet.contains(slx, event.getState().getBlock()) && PlacableHpManager.isDamaged(slx, event.getState(), pos)) {
                // its loot is a fresh item that would go back down at full HP; a pickup keeps the HP in the item
                event.setCanceled(true);
                spx.displayClientMessage(Txt.red("crac.placable.damaged_pickup"), true);
            }
        }
    }

    /** A block of CRac itself (sleeping bag, Metal Crate, Claim Block...). */
    private static boolean isCracBlock(BlockState state) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && "crac".equals(id.getNamespace());
    }

    private static boolean isSystemBlock(BlockState state) {
        Block b = state.getBlock();
        return b instanceof StructureBlock || b == ModBlocks.CLAIM_BLOCK.get();
    }

    @SubscribeEvent
    public static void onBlockPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getLevel() instanceof ServerLevel sl) {
                if (!player.getAbilities().instabuild) {
                    // HeraliZ: no sleeping bag, crate or Claim Block in the match maps and the clan bunkers
                    if (Heraliz.blocked(sl) && isCracBlock(event.getPlacedBlock())) {
                        event.setCanceled(true);
                        player.displayClientMessage(Heraliz.blockedMessage().withStyle(net.minecraft.ChatFormatting.RED), true);
                        for (BlockPos cell : PlacableHpManager.cells(event)) {
                            PlacementSync.revert(player, sl, cell);
                        }
                        return;
                    }
                    if (!isSystemBlock(event.getPlacedBlock())) {
                        // a bed or a door writes two blocks: every one of them is checked, not just the first
                        List<BlockPos> cells = PlacableHpManager.cells(event);
                        String deny = placeProblem(sl, player, event.getPlacedBlock(), cells);
                        if (deny != null) {
                            event.setCanceled(true);
                            player.displayClientMessage(Txt.red(deny), true);
                            for (BlockPos cell : cells) {
                                PlacementSync.revert(player, sl, cell);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Why the player may not place {@code state} over these cells, or null. */
    @Nullable
    private static String placeProblem(ServerLevel sl, ServerPlayer player, BlockState state, List<BlockPos> cells) {
        Claim claim = ClaimManager.getAt(sl, cells.get(0));
        for (BlockPos cell : cells) {
            Claim c = ClaimManager.getAt(sl, cell);
            if (c != null && !isMember(sl, player, c, ClanAccess.Action.BUILD) && !RaidBlocks.isRaidAccess(sl, c, state)) {
                return "crac.claim.not_yours";
            }
            if (c != claim) {
                return "crac.claim.must_fit";
            }
        }
        if (claim == null) {
            return Config.protectOutsideClaims.get() ? "crac.claim.only_inside" : null;
        }
        if (!isMember(sl, player, claim, ClanAccess.Action.BUILD)) {
            // a raider's ladder, scaffolding, vine or trapdoor in a base under raid block
            return null;
        }
        boolean placable = PlacableSet.contains(sl, state.getBlock());
        if (!placable && !Config.allowBreakInClaim.get()) {
            return "crac.claim.only_placables";
        }
        if (Config.raidBlockBuilding.get() && RaidBlockManager.isRaidBlocked(sl, claim.groupId())) {
            return "crac.build.raid_blocked";
        }
        if (placable && PlacableSupport.enabled()) {
            return PlacableSupport.supportProblem(sl, cells, state);
        }
        return null;
    }

    /**
     * Claim rule for an item used on a block (buckets, fire, spawn eggs...): why the player may not put something
     * at {@code placePos} / {@code clicked}, or null. Shared with the bucket handler, whose fluid is placed by a
     * second packet that the RightClickBlock denial does not cover.
     */
    @Nullable
    public static String useProblem(ServerLevel sl, ServerPlayer player, BlockPos clicked, BlockPos placePos, boolean placable) {
        Claim atPlace = ClaimManager.getAt(sl, placePos);
        Claim atClicked = ClaimManager.getAt(sl, clicked);
        // a foreign claim at either end wins, even when the other end is in the player's own claim
        if (atPlace != null && !isMember(sl, player, atPlace, ClanAccess.Action.BUILD) || atClicked != null && !isMember(sl, player, atClicked, ClanAccess.Action.BUILD)) {
            return "crac.claim.not_yours";
        }
        if (atPlace != null || atClicked != null) {
            return placable || Config.allowBreakInClaim.get() ? null : "crac.claim.only_placables";
        }
        return Config.protectOutsideClaims.get() ? "crac.claim.only_inside" : null;
    }

    private static boolean placesSomething(Item item) {
        return item instanceof BlockItem
            || item instanceof BucketItem
            || item instanceof FireChargeItem
            || item instanceof FlintAndSteelItem
            || item instanceof HangingEntityItem
            || item instanceof ArmorStandItem
            || item instanceof BoatItem
            || item instanceof MinecartItem
            || item instanceof EndCrystalItem
            || item instanceof SpawnEggItem;
    }

    @SubscribeEvent
    public static void onRightClickBlock(RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getLevel() instanceof ServerLevel sl) {
                if (!player.getAbilities().instabuild) {
                    BlockState clickedState = sl.getBlockState(event.getPos());
                    Block clicked = clickedState.getBlock();
                    boolean openable = clicked instanceof DoorBlock || clicked instanceof TrapDoorBlock || clicked instanceof FenceGateBlock;
                    if (openable) {
                        Claim doorClaim = ClaimManager.getAt(sl, event.getPos());
                        if (doorClaim != null) {
                            Group dg = GroupData.get(sl.getServer()).getById(doorClaim.groupId());
                            // under raid block a wooden trapdoor is a crawling aid anyone may use (RaidBlocks.ACCESS)
                            if (!ClanAccess.allows(dg, player.getUUID(), ClanAccess.Action.DOORS) && !RaidBlocks.isRaidAccess(sl, doorClaim, clickedState)) {
                                event.setUseBlock(Result.DENY);
                                player.displayClientMessage(Txt.red("crac.claim.not_yours"), true);
                                sl.playSound(null, event.getPos(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
                                return;
                            }
                        }
                    }

                    Item item = event.getItemStack().getItem();
                    if (placesSomething(item)) {
                        if (!(item instanceof BlockItem bi && isSystemBlock(bi.getBlock().defaultBlockState()))) {
                            BlockPos placePos = event.getPos().relative(event.getFace() == null ? Direction.UP : event.getFace());
                            boolean placable = item instanceof BlockItem bi2 && PlacableSet.contains(sl, bi2.getBlock());
                            String deny = useProblem(sl, player, event.getPos(), placePos, placable);
                            if (deny != null && item instanceof BlockItem aid && raidAccessClick(sl, player, aid.getBlock().defaultBlockState(), event.getPos(), placePos)) {
                                // a raider's climbing or crawling aid: the place event checks the block it really sets
                                deny = null;
                            }
                            if (deny != null) {
                                event.setUseItem(Result.DENY);
                                player.displayClientMessage(Txt.red(deny), true);
                                PlacementSync.revert(player, sl, event.getPos(), event.getFace());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether a click with a climbing or crawling aid only touches claims of other groups under raid block (and no claim
     * of the player's own, where only placables go), see {@link RaidBlocks#isRaidAccess}.
     */
    private static boolean raidAccessClick(ServerLevel sl, ServerPlayer player, BlockState aid, BlockPos clicked, BlockPos placePos) {
        boolean any = false;
        for (BlockPos p : List.of(clicked, placePos)) {
            Claim c = ClaimManager.getAt(sl, p);
            if (c == null) {
                continue;
            }
            if (isMember(sl, player, c, ClanAccess.Action.BUILD) || !RaidBlocks.isRaidAccess(sl, c, aid)) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private static boolean isMember(ServerLevel sl, ServerPlayer player, Claim claim, ClanAccess.Action action) {
        Group g = GroupData.get(sl.getServer()).getById(claim.groupId());
        return ClanAccess.allows(g, player.getUUID(), action);
    }
}
