package fr.heraliz.crac.build.test;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.MetalCrateBlock;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.test.GT.Dir4;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.event.ClaimProtectionHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.C2SPickup;
import fr.heraliz.crac.network.C2SPickupTraitement;
import fr.heraliz.crac.network.C2SPlacePlacable;
import fr.heraliz.crac.network.C2SPlacePlacableTraitement;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.raid.BlastDamage;
import fr.heraliz.crac.raid.RaidBlockManager;
import fr.heraliz.crac.registry.ModBlocks;
import fr.heraliz.crac.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests of claim protection, placables and sleeping bags. Each test lays a stone floor at relative y = 1;
 * placables stand at y = 2. Claims reach far past a test's own area, so a test that keeps one across ticks runs in
 * a batch of its own; the others create and remove theirs within the same call.
 */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private static final String EMPTY = "empty";
    private static final String ALONE = "protection_alone";

    private ProtectionGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static BlockPos at(GameTestHelper h, int x, int y, int z) {
        return h.absolutePos(new BlockPos(x, y, z));
    }

    /** A survival server player (not in the world, packets go nowhere) standing at the relative position. */
    private static ServerPlayer player(GameTestHelper h, double x, double y, double z, float yRot) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "gt-player"));
        Vec3 abs = h.absoluteVec(new Vec3(x, y, z));
        p.moveTo(abs.x, abs.y, abs.z, yRot, 0.0F);
        // a player looks where its head points (pick, view vector), which moveTo leaves alone
        p.setYHeadRot(yRot);
        return p;
    }

    /** A group owned by {@code owner} with a tier 1 claim anchored at {@code anchor}. Undo with {@link #dissolve}. */
    private static Group claimFor(ServerLevel level, Player owner, BlockPos anchor) {
        Group g = GroupData.get(level.getServer()).create("gametest", "GTP", 0xFFFFFF, owner.getUUID());
        check(g != null, "test setup: could not create a group");
        ClaimManager.addClaim(level, anchor, g.id(), 1);
        return g;
    }

    private static void dissolve(ServerLevel level, Group g) {
        if (ClaimManager.getByGroup(level, g.id()) != null) {
            ClaimManager.removeByGroup(level, g.id());
        }
        GroupData.get(level.getServer()).disband(g);
    }

    /**
     * Sets a placable block that starts at full HP, like a real placement does. The test world is kept between runs,
     * so an HP entry left at this spot by an earlier run must not carry over.
     */
    private static void setPlacable(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        PlacableHpManager.forget(level, state, pos);
    }


    /** Right-click on the top of a block with an empty hand. */
    private static InteractionResult use(ServerLevel level, Player player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, hit);
    }

    /** Places the main-hand item against a face of {@code against}, the way a vanilla right click does. */
    private static InteractionResult placeAgainst(Player player, BlockPos against, Direction face) {
        Vec3 hit = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return ForgeHooks.onPlaceItemIntoWorld(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, against, false)));
    }

    private static List<ItemEntity> itemsAround(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0));
    }

    private static int countItems(List<ItemEntity> entities, Item item) {
        return entities.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    // ------------------------------------------------------------------ placables



    /** F21, F45: picking a damaged placable up keeps its HP in the item; putting it down again does not heal it. */
    @GameTest(template = EMPTY)
    public static void aDamagedPlacableKeepsItsHpThroughPickup(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos fresh = at(h, 3, 2, 5);
        BlockPos table = at(h, 5, 2, 5);
        setPlacable(level, fresh, Blocks.CRAFTING_TABLE.defaultBlockState());
        setPlacable(level, table, Blocks.CRAFTING_TABLE.defaultBlockState());
        int max = PlacableHpManager.maxHpFor(Blocks.CRAFTING_TABLE);
        check(max > 1, "test setup: crafting table HP " + max);
        int left = max - max / 2;
        PlacableHpManager.decayDamage(level, table, max / 2);

        // two players: the server rate-limits pickups per player
        ServerPlayer other = player(h, 2.5, 2, 9.5, 180.0F);
        C2SPickupTraitement.process(other, fresh);
        ItemStack freshItem = other.getMainHandItem();
        check(freshItem.is(Items.CRAFTING_TABLE) && !freshItem.hasTag(), "an undamaged table came back as " + freshItem + " " + freshItem.getTag());

        ServerPlayer player = player(h, 5.5, 2, 9.5, 180.0F);
        C2SPickupTraitement.process(player, table);
        check(level.getBlockState(table).isAir(), "the damaged table was not picked up");
        ItemStack item = player.getMainHandItem();
        CompoundTag tag = item.getTag();
        check(item.is(Items.CRAFTING_TABLE) && tag != null && tag.getInt(PlacableHpManager.ITEM_HP_TAG) == left, "the picked up table lost its HP: " + tag);

        BlockPos spot = at(h, 8, 2, 5);
        placeAgainst(player, spot.below(), Direction.UP);
        check(level.getBlockState(spot).is(Blocks.CRAFTING_TABLE), "the table was not placed again");
        int hp = PlacableHpManager.currentHp(level, level.getBlockState(spot), spot);
        check(hp == left, "the table came back with " + hp + " HP, expected " + left);
        h.succeed();
    }

    /** F21, F47: under raid block a claim's placables can be neither picked up nor placed. */
    @GameTest(template = EMPTY)
    public static void raidBlockStopsPlacablePickupAndPlacement(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        ServerPlayer member = player(h, 5.5, 2, 9.5, 180.0F);
        Group group = claimFor(level, member, at(h, 7, 2, 7));
        try {
            BlockPos chest = at(h, 5, 2, 6);
            setPlacable(level, chest, Blocks.CHEST.defaultBlockState());
            RaidBlockManager.trigger(level, chest);
            check(RaidBlockManager.isRaidBlocked(level, group.id()), "test setup: no raid block");
            C2SPickupTraitement.process(member, chest);
            check(level.getBlockState(chest).is(Blocks.CHEST), "a chest was picked up under raid block");
            member.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
            BlockPos spot = at(h, 8, 2, 6);
            placeAgainst(member, spot.below(), Direction.UP);
            check(!level.getBlockState(spot).is(Blocks.CRAFTING_TABLE), "a placable was placed under raid block");
            check(member.getMainHandItem().getCount() == 1, "the refused placement used the item up");
        } finally {
            dissolve(level, group);
        }
        h.succeed();
    }

    /** F47: inside a claim a placable needs something under it, and falls when that goes. */
    @GameTest(template = EMPTY)
    public static void placablesNeedSupportAndFallWithIt(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        ServerPlayer member = player(h, 5.5, 2, 12.5, 180.0F);
        Group group = claimFor(level, member, at(h, 7, 2, 7));
        try {
            if (!Config.placablesNeedSupport.get()) {
                h.succeed();
                return;
            }
            BlockPos pillar = at(h, 3, 3, 5);
            level.setBlock(pillar.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pillar, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            member.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
            placeAgainst(member, pillar, Direction.EAST);
            check(level.getBlockState(pillar.east()).isAir(), "a placable was placed in mid-air");

            BlockPos chest = at(h, 8, 2, 5);
            member.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
            placeAgainst(member, chest.below(), Direction.UP);
            check(level.getBlockState(chest).is(Blocks.CHEST), "a chest on the floor was refused");
            member.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
            placeAgainst(member, chest, Direction.UP);
            check(level.getBlockState(chest.above()).is(Blocks.CRAFTING_TABLE), "a placable on another placable was refused");

            PlacableHpManager.decayDamage(level, chest, PlacableHpManager.maxHpFor(Blocks.CHEST));
            check(level.getBlockState(chest).isAir(), "test setup: the chest survived");
            check(level.getBlockState(chest.above()).isAir(), "the table kept floating after the chest under it was destroyed");
            check(countItems(itemsAround(level, chest), Items.CRAFTING_TABLE) == 1, "the fallen table did not drop");
        } finally {
            dissolve(level, group);
        }
        h.succeed();
    }


    /** HP written in the {@code item} lying around {@code pos} (0 when none is written), -1 when there is no such item. */
    private static int droppedHp(ServerLevel level, BlockPos pos, Item item) {
        for (ItemEntity e : itemsAround(level, pos)) {
            ItemStack stack = e.getItem();
            if (stack.is(item)) {
                CompoundTag tag = stack.getTag();
                return tag != null && tag.contains(PlacableHpManager.ITEM_HP_TAG) ? tag.getInt(PlacableHpManager.ITEM_HP_TAG) : 0;
            }
        }
        return -1;
    }

    /** F46: a shulker box destroyed by damage spills what it held. */
    @GameTest(template = EMPTY)
    public static void aDestroyedShulkerBoxSpillsItsContents(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos pos = at(h, 5, 2, 5);
        setPlacable(level, pos, Blocks.SHULKER_BOX.defaultBlockState());
        check(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity, "test setup: no shulker box");
        ((ShulkerBoxBlockEntity) level.getBlockEntity(pos)).setItem(0, new ItemStack(Items.DIAMOND, 5));
        PlacableHpManager.decayDamage(level, pos, PlacableHpManager.maxHpFor(Blocks.SHULKER_BOX));
        check(level.getBlockState(pos).isAir(), "test setup: the shulker box survived");
        int diamonds = countItems(itemsAround(level, pos), Items.DIAMOND);
        check(diamonds == 5, "the destroyed shulker box spilled " + diamonds + " of 5 diamonds");
        h.succeed();
    }



    /** F21: the HP comes from the hand that placed the block, never from a fresh copy in the other hand. */
    @GameTest(template = EMPTY)
    public static void theHandThatPlacesDecidesTheHp(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        int max = PlacableHpManager.maxHpFor(Blocks.CRAFTING_TABLE);
        check(max > 1, "test setup: crafting table HP " + max);
        int damaged = max / 2;
        ItemStack worn = new ItemStack(Items.CRAFTING_TABLE);
        worn.getOrCreateTag().putInt(PlacableHpManager.ITEM_HP_TAG, damaged);
        // adventure players placing through the packet, each looking down at the floor two blocks ahead
        ServerPlayer offHand = player(h, 3.5, 2, 9.5, 180.0F);
        ServerPlayer mainHand = player(h, 9.5, 2, 9.5, 180.0F);
        Group group = claimFor(level, offHand, at(h, 7, 2, 7));
        try {
            check(GroupData.get(level.getServer()).addMember(group, mainHand.getUUID()), "test setup: could not add a member");
            for (ServerPlayer p : List.of(offHand, mainHand)) {
                p.setGameMode(GameType.ADVENTURE);
                p.setXRot(45.0F);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
                p.setItemInHand(InteractionHand.OFF_HAND, worn.copy());
            }
            C2SPlacePlacableTraitement.process(offHand, 1);
            BlockPos fromOff = at(h, 3, 2, 7);
            check(level.getBlockState(fromOff).is(Blocks.CRAFTING_TABLE), "test setup: nothing was placed from the off hand");
            int hp = PlacableHpManager.currentHp(level, level.getBlockState(fromOff), fromOff);
            check(hp == damaged, "placed from the damaged off-hand copy, the table has " + hp + " HP, expected " + damaged);

            C2SPlacePlacableTraitement.process(mainHand, 0);
            BlockPos fromMain = at(h, 9, 2, 7);
            check(level.getBlockState(fromMain).is(Blocks.CRAFTING_TABLE), "test setup: nothing was placed from the main hand");
            hp = PlacableHpManager.currentHp(level, level.getBlockState(fromMain), fromMain);
            check(hp == max, "placed from the fresh main-hand copy, the table has " + hp + " HP, expected " + max);
        } finally {
            dissolve(level, group);
        }
        h.succeed();
    }

    /** F07: a placable behind a wall cannot be picked up, whatever block the packet names. */
    @GameTest(template = EMPTY)
    public static void aPlacableBehindAWallCannotBePickedUp(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos o = at(h, 4, 3, 4);
        Piece foundation = StructureService.placeAdmin(level, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.STONE, UUID.randomUUID()).piece();
        Piece wall = StructureService.placeAdmin(level, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0), Tier.STONE, UUID.randomUUID()).piece();
        check(foundation != null && wall != null, "test setup: could not build the wall");
        try {
            BlockPos chest = GT.seat(foundation);
            setPlacable(level, chest, Blocks.CHEST.defaultBlockState());
            // two players at the same spot: the server rate-limits pickups per player
            C2SPickupTraitement.process(player(h, 5.5, 2, 1.5, 0.0F), chest);
            check(level.getBlockState(chest).is(Blocks.CHEST), "a chest was picked up through a wall");

            StructureService.remove(level, wall, StructureService.Cause.ADMIN);
            wall = null;
            ServerPlayer looker = player(h, 5.5, 2, 1.5, 0.0F);
            C2SPickupTraitement.process(looker, chest);
            check(level.getBlockState(chest).isAir(), "a chest in plain sight was not picked up");
            check(looker.getInventory().countItem(Items.CHEST) == 1, "the picked up chest is not in the inventory");
        } finally {
            if (wall != null) {
                StructureService.remove(level, wall, StructureService.Cause.ADMIN);
            }
            StructureService.remove(level, foundation, StructureService.Cause.ADMIN);
        }
        h.succeed();
    }

    /** F21, F45: mining a damaged placable would give a fresh item back; only an undamaged one can be mined. */
    @GameTest(template = EMPTY)
    public static void aDamagedPlacableCannotBeMined(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos fresh = at(h, 4, 2, 5);
        BlockPos worn = at(h, 6, 2, 5);
        setPlacable(level, fresh, Blocks.CRAFTING_TABLE.defaultBlockState());
        setPlacable(level, worn, Blocks.CRAFTING_TABLE.defaultBlockState());
        PlacableHpManager.decayDamage(level, worn, Math.max(1, PlacableHpManager.maxHpFor(Blocks.CRAFTING_TABLE) / 2));
        ServerPlayer miner = player(h, 5.5, 2, 8.5, 180.0F);
        check(ForgeHooks.onBlockBreakEvent(level, GameType.SURVIVAL, miner, worn) == -1, "a damaged placable could be mined");
        check(ForgeHooks.onBlockBreakEvent(level, GameType.SURVIVAL, miner, fresh) != -1, "an undamaged placable could not be mined");
        h.succeed();
    }

    // ------------------------------------------------------------------ claims

    /** F07: in a foreign claim nothing behind a wall can be used, whatever the client says it clicked. */
    @GameTest(template = EMPTY)
    public static void blocksBehindAWallCannotBeUsedFromOutside(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos o = at(h, 4, 3, 4);
        Piece foundation = StructureService.placeAdmin(level, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.STONE, UUID.randomUUID()).piece();
        Piece wall = StructureService.placeAdmin(level, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0), Tier.STONE, UUID.randomUUID()).piece();
        check(foundation != null && wall != null, "test setup: could not build the wall");
        ServerPlayer owner = player(h, 5.5, 3, 6.5, 180.0F);
        ServerPlayer stranger = player(h, 5.5, 2, 1.5, 0.0F);
        Group group = claimFor(level, owner, BlockPos.containing(GT.localPoint(foundation.key(), 40, 0.5, 40)));
        try {
            BlockPos chest = GT.seat(foundation);
            setPlacable(level, chest, Blocks.CHEST.defaultBlockState());
            Vec3 face = new Vec3(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 1.0 / 16.0);
            BlockHitResult hit = new BlockHitResult(face, Direction.NORTH, chest, false);
            check(!ClaimProtectionHandler.inSight(level, stranger, chest, face), "the wall does not hide the chest");
            PlayerInteractEvent.RightClickBlock event = ForgeHooks.onRightClickBlock(stranger, InteractionHand.MAIN_HAND, chest, hit);
            check(event.getUseBlock() == Event.Result.DENY, "a stranger opened a chest through a wall");
            PlayerInteractEvent.RightClickBlock own = ForgeHooks.onRightClickBlock(owner, InteractionHand.MAIN_HAND, chest, hit);
            check(own.getUseBlock() != Event.Result.DENY, "a member was refused their own chest");

            StructureService.remove(level, wall, StructureService.Cause.ADMIN);
            check(ClaimProtectionHandler.inSight(level, stranger, chest, face), "with the wall gone the chest is still hidden");
            PlayerInteractEvent.RightClickBlock breached = ForgeHooks.onRightClickBlock(stranger, InteractionHand.MAIN_HAND, chest, hit);
            check(breached.getUseBlock() != Event.Result.DENY, "a chest in plain sight was refused");
        } finally {
            dissolve(level, group);
            StructureService.remove(level, foundation, StructureService.Cause.ADMIN);
        }
        h.succeed();
    }

    /** F22: buckets and pistons cannot reach into a foreign claim. */
    @GameTest(template = EMPTY)
    public static void bucketsAndPistonsStopAtAForeignClaim(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        int r = Config.claimRadius(1);
        ServerPlayer owner = player(h, 2.5, 2, 12.5, 180.0F);
        ServerPlayer stranger = player(h, 9.5, 2, 12.5, 90.0F);
        // the claim's east edge runs along relative x = 7
        Group group = claimFor(level, owner, at(h, 7 - r, 2, 7));
        try {
            BlockPos inside = at(h, 5, 1, 7);
            BlockHitResult intoClaim = new BlockHitResult(Vec3.atCenterOf(inside).add(0.0, 0.5, 0.0), Direction.UP, inside, false);
            InteractionResultHolder<ItemStack> refused = ForgeEventFactory.onBucketUse(stranger, level, new ItemStack(Items.LAVA_BUCKET), intoClaim);
            check(refused != null && refused.getResult() == InteractionResult.FAIL, "a stranger emptied a lava bucket in someone else's claim");
            BlockPos outside = at(h, 11, 1, 7);
            BlockHitResult wild = new BlockHitResult(Vec3.atCenterOf(outside).add(0.0, 0.5, 0.0), Direction.UP, outside, false);
            check(ForgeEventFactory.onBucketUse(stranger, level, new ItemStack(Items.LAVA_BUCKET), wild) == null, "a bucket outside any claim was refused");

            BlockPos piston = at(h, 9, 2, 3);
            level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
            level.setBlock(piston.west(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            check(ForgeEventFactory.onPistonMovePre(level, piston, Direction.WEST, true), "a piston pushed a block into a claim");
            BlockPos away = at(h, 10, 2, 10);
            level.setBlock(away, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
            level.setBlock(away.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            check(!ForgeEventFactory.onPistonMovePre(level, away, Direction.EAST, true), "a piston outside any claim was refused");
        } finally {
            dissolve(level, group);
        }
        h.succeed();
    }

    /** F22: fluids never flow into a claim from outside it. Keeps a claim across ticks: runs alone. */
    @GameTest(template = EMPTY, batch = ALONE, timeoutTicks = 200)
    public static void waterDoesNotFlowIntoAClaim(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        int r = Config.claimRadius(1);
        ServerPlayer owner = player(h, 2.5, 2, 12.5, 180.0F);
        // the claim's east edge runs along relative x = 7, the water starts two blocks east of it
        Group group = claimFor(level, owner, at(h, 7 - r, 2, 7));
        BlockPos source = at(h, 9, 2, 7);
        level.setBlock(source, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        h.runAfterDelay(40, () -> {
            try {
                check(!level.getFluidState(source.west()).isEmpty(), "test setup: the water did not spread outside the claim");
                check(level.getFluidState(source.west(2)).isEmpty(), "water flowed into the claim");
            } finally {
                level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                dissolve(level, group);
            }
            h.succeed();
        });
    }

    /** F22: fire outside a claim neither burns the blocks at its edge nor jumps into it; outside any claim it spreads. */
    @GameTest(template = EMPTY)
    public static void fireDoesNotSpreadIntoAClaim(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        int r = Config.claimRadius(1);
        ServerPlayer owner = player(h, 2.5, 2, 12.5, 180.0F);
        // the claim's east edge runs along relative x = 7
        Group group = claimFor(level, owner, at(h, 7 - r, 2, 7));
        BlockPos edgeFire = at(h, 8, 2, 5);
        BlockPos inside = at(h, 7, 2, 5);
        BlockPos wildFire = at(h, 10, 2, 10);
        BlockPos wild = at(h, 11, 2, 10);
        try {
            for (BlockPos fire : List.of(edgeFire, wildFire)) {
                // on netherrack a fire never burns out, so every tick may spread it
                level.setBlock(fire.below(), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(fire, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(inside, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(wild, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            RandomSource edgeRandom = RandomSource.create(17L);
            RandomSource wildRandom = RandomSource.create(23L);
            for (int i = 0; i < 300; i++) {
                if (level.getBlockState(edgeFire).is(Blocks.FIRE)) {
                    level.getBlockState(edgeFire).tick(level, edgeFire, edgeRandom);
                }
                if (level.getBlockState(wildFire).is(Blocks.FIRE)) {
                    level.getBlockState(wildFire).tick(level, wildFire, wildRandom);
                }
            }
            check(!level.getBlockState(wild).is(Blocks.OAK_PLANKS), "test setup: the fire outside any claim did not spread");
            check(level.getBlockState(inside).is(Blocks.OAK_PLANKS), "fire outside the claim burned a block inside it");
            for (Direction d : Direction.values()) {
                check(!level.getBlockState(inside.relative(d)).is(Blocks.FIRE) || inside.relative(d).equals(edgeFire), "fire jumped into the claim at " + inside.relative(d));
            }
        } finally {
            dissolve(level, group);
            for (BlockPos p : BlockPos.betweenClosed(at(h, 0, 2, 0), at(h, 14, 8, 14))) {
                if (level.getBlockState(p).is(Blocks.FIRE)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        h.succeed();
    }

    /** F48, RULES-03: chorus fruit and ender pearls take nobody into a claim, its own members included. */
    @GameTest(template = EMPTY)
    public static void teleportsStopAtAnyClaim(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        int r = Config.claimRadius(1);
        ServerPlayer owner = player(h, 5.5, 2, 5.5, 0.0F);
        ServerPlayer stranger = player(h, 5.5, 2, 9.5, 0.0F);
        Group group = claimFor(level, owner, at(h, 7, 2, 7));
        try {
            Vec3 inside = h.absoluteVec(new Vec3(7.5, 3.0, 7.5));
            check(ForgeEventFactory.onChorusFruitTeleport(stranger, inside.x, inside.y, inside.z).isCanceled(), "chorus fruit took a stranger into a claim");
            check(ForgeEventFactory.onChorusFruitTeleport(owner, inside.x, inside.y, inside.z).isCanceled(), "chorus fruit took a member into their own claim");
            Vec3 outside = inside.add(r + 4, 0.0, 0.0);
            check(!ForgeEventFactory.onChorusFruitTeleport(stranger, outside.x, outside.y, outside.z).isCanceled(), "chorus fruit was refused outside any claim");

            BlockHitResult hit = new BlockHitResult(inside, Direction.UP, BlockPos.containing(inside), false);
            check(ForgeEventFactory.onEnderPearlLand(stranger, inside.x, inside.y, inside.z, new ThrownEnderpearl(level, stranger), 5.0F, hit).isCanceled(),
                "an ender pearl took a stranger into a claim");
            check(ForgeEventFactory.onEnderPearlLand(owner, inside.x, inside.y, inside.z, new ThrownEnderpearl(level, owner), 5.0F, hit).isCanceled(),
                "an ender pearl took a member into their own claim");
            BlockHitResult wild = new BlockHitResult(outside, Direction.UP, BlockPos.containing(outside), false);
            check(!ForgeEventFactory.onEnderPearlLand(owner, outside.x, outside.y, outside.z, new ThrownEnderpearl(level, owner), 5.0F, wild).isCanceled(),
                "an ender pearl was refused outside any claim");
        } finally {
            dissolve(level, group);
        }
        h.succeed();
    }

    /**
     * RULES-03: under raid block anyone may set ladders, scaffolding, vines and wooden trapdoors in the base and use its
     * wooden trapdoors, so no way in (a ledge, a gap one only gets through crawling) is its owners' alone; the owners
     * clear what is left.
     */
    @GameTest(template = EMPTY)
    public static void raidersMayClimbAndCrawlInUnderRaidBlock(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        ServerPlayer owner = player(h, 3.5, 2, 3.5, 0.0F);
        ServerPlayer raider = player(h, 7.5, 2, 11.5, 180.0F);
        Group group = claimFor(level, owner, at(h, 7, 2, 7));
        try {
            BlockPos rock = at(h, 7, 2, 8);
            BlockPos ladder = rock.south();
            BlockPos hatch = at(h, 5, 2, 9);
            level.setBlock(rock, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(hatch, Blocks.OAK_TRAPDOOR.defaultBlockState(), Block.UPDATE_ALL);
            // the top of the closed trapdoor, in plain sight of the raider
            BlockHitResult onHatch = new BlockHitResult(new Vec3(hatch.getX() + 0.5, hatch.getY() + 3.0 / 16.0, hatch.getZ() + 0.5), Direction.UP, hatch, false);
            check(!RaidBlockManager.isRaidBlocked(level, group.id()), "test setup: the new claim is under raid block");

            raider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LADDER));
            placeAgainst(raider, rock, Direction.SOUTH);
            check(!level.getBlockState(ladder).is(Blocks.LADDER), "a stranger set a ladder in a claim that is not under raid block");
            raider.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(ForgeHooks.onRightClickBlock(raider, InteractionHand.MAIN_HAND, hatch, onHatch).getUseBlock() == Event.Result.DENY,
                "a stranger used a trapdoor in a claim that is not under raid block");

            RaidBlockManager.trigger(level, rock);
            check(RaidBlockManager.isRaidBlocked(level, group.id()), "test setup: no raid block");
            check(ForgeHooks.onRightClickBlock(raider, InteractionHand.MAIN_HAND, hatch, onHatch).getUseBlock() != Event.Result.DENY,
                "a raider could not use a wooden trapdoor of a base under raid block");
            raider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LADDER));
            placeAgainst(raider, rock, Direction.SOUTH);
            check(level.getBlockState(ladder).is(Blocks.LADDER), "a raider could not set a ladder in a base under raid block");
            BlockPos scaffold = at(h, 9, 2, 9);
            raider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SCAFFOLDING));
            placeAgainst(raider, scaffold.below(), Direction.UP);
            check(level.getBlockState(scaffold).is(Blocks.SCAFFOLDING), "a raider could not set scaffolding in a base under raid block");
            BlockPos cobble = at(h, 10, 2, 9);
            raider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
            placeAgainst(raider, cobble.below(), Direction.UP);
            check(!level.getBlockState(cobble).is(Blocks.COBBLESTONE), "a raider set cobblestone in a base under raid block");

            // the owners take the raiders' aids down; the raiders may not mine there
            check(ForgeHooks.onBlockBreakEvent(level, GameType.SURVIVAL, raider, scaffold) == -1, "a raider mined in someone else's claim");
            check(ForgeHooks.onBlockBreakEvent(level, GameType.SURVIVAL, owner, ladder) != -1, "a member could not take a raider's ladder down");
            if (!Config.allowBreakInClaim.get()) {
                check(ForgeHooks.onBlockBreakEvent(level, GameType.SURVIVAL, owner, rock) == -1, "a member mined an ordinary block of the claim");
            }
        } finally {
            RaidBlockManager.clear(level, group.id());
            dissolve(level, group);
        }
        h.succeed();
    }

    /** F32: with protectOutsideClaims only a new building's first foundation goes up outside a claim. */
    @GameTest(template = EMPTY)
    public static void protectOutsideClaimsLeavesOnlyTheFirstFoundation(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos o = at(h, 4, 3, 4);
        ServerPlayer builder = player(h, 5.5, 2, 1.5, 0.0F);
        for (PieceKind kind : new PieceKind[] {PieceKind.FOUNDATION, PieceKind.WALL, PieceKind.WALL}) {
            for (BuildCost cost : Costs.place(kind)) {
                ItemStack[] items = cost.ingredient().getItems();
                check(items.length > 0, "test setup: no item for " + cost.label());
                builder.getInventory().add(items[0].copyWithCount(cost.count()));
            }
        }
        boolean protect = Config.protectOutsideClaims.get();
        int maxPieces = BuildConfig.maxPiecesWithoutClaim.get();
        Piece foundation = null;
        Piece wall = null;
        try {
            Config.protectOutsideClaims.set(true);
            BuildConfig.maxPiecesWithoutClaim.set(4);
            StructureService.Outcome first = StructureService.place(level, builder, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o));
            check(first.ok(), "the first foundation was refused: " + first.message().getString());
            foundation = first.piece();
            StructureService.Outcome refused = StructureService.place(level, builder, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0));
            check(!refused.ok(), "a wall went up outside any claim with protectOutsideClaims");
            Config.protectOutsideClaims.set(false);
            StructureService.Outcome allowed = StructureService.place(level, builder, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0));
            check(allowed.ok(), "without protectOutsideClaims the wall was refused: " + allowed.message().getString());
            wall = allowed.piece();
        } finally {
            Config.protectOutsideClaims.set(protect);
            BuildConfig.maxPiecesWithoutClaim.set(maxPieces);
            if (wall != null) {
                StructureService.remove(level, wall, StructureService.Cause.ADMIN);
            }
            if (foundation != null) {
                StructureService.remove(level, foundation, StructureService.Cause.ADMIN);
            }
        }
        h.succeed();
    }

    /** F75: a plan or hammer only blocks melee from the main hand. */
    @GameTest(template = EMPTY)
    public static void aToolInTheOffhandDoesNotBlockMelee(GameTestHelper h) {
        Player player = h.makeMockPlayer();
        Entity target = EntityType.PIG.create(h.getLevel());
        check(target != null, "test setup: no pig");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BUILDING_PLAN.get()));
        check(ForgeHooks.onPlayerAttackTarget(player, target), "a plan in the offhand blocked melee");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BUILDING_HAMMER.get()));
        check(!ForgeHooks.onPlayerAttackTarget(player, target), "the hammer in the main hand did not stop melee");
        h.succeed();
    }

    // ------------------------------------------------------------------ sleeping bags






}
