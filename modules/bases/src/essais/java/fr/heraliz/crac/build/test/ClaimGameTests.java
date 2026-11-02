package fr.heraliz.crac.build.test;

import fr.heraliz.crac.block.ClaimBlockLogique;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.build.world.Occupant;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import fr.heraliz.crac.build.world.StructureIntegrity;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimMaintenanceManager;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.data.SafeSavedData;
import fr.heraliz.crac.event.ClaimBlockHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.menu.ClaimUpkeepMenu;
import fr.heraliz.crac.network.C2SGroupAction;
import fr.heraliz.crac.network.C2SGroupActionTraitement;
import fr.heraliz.crac.network.ConfigResync;
import fr.heraliz.crac.network.S2CNearbyClaims;
import fr.heraliz.crac.raid.RaidBlockData;
import fr.heraliz.crac.raid.RaidBlockManager;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests of claims, upkeep and raid block. Same layout as {@link BuildGameTests}: a stone floor at relative
 * y = 1, the first foundation's walking surface at y = 3. Every test removes the claims, groups and raid blocks it made
 * (raid blocks are saved with the world, which the GameTest server keeps between runs).
 */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class ClaimGameTests {
    private static final String EMPTY = "empty";

    private ClaimGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static BlockPos ground(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z <= 13; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        return h.absolutePos(new BlockPos(4, 3, 4));
    }

    private static Piece place(GameTestHelper h, PieceKey key, Tier tier, UUID owner) {
        StructureService.Outcome out = StructureService.placeAdmin(h.getLevel(), key, tier, owner);
        if (!out.ok() || out.piece() == null) {
            BlockPos slab = key.anchorBlock().below();
            throw new GameTestAssertException("could not place " + key.kind().id + " at " + key.anchorBlock().toShortString() + " h" + key.h() + ": " + out.message().getString()
                + " (under it: " + h.getLevel().getBlockState(slab) + ", " + h.getLevel().getBlockState(slab.below()) + ")");
        }
        return out.piece();
    }

    private static Piece foundation(GameTestHelper h, BlockPos ref, Tier tier, UUID owner) {
        return place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, ref), tier, owner);
    }

    /**
     * Stone under the cell at {@code ref}, two blocks down, so that a foundation there gets its slab right on it, and
     * air for the slab and above it (above the area the framework clears, the test world is solid terrain).
     */
    private static void platform(ServerLevel level, BlockPos ref) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                level.setBlock(ref.offset(x, -2, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                for (int y = -1; y <= 2; y++) {
                    level.setBlock(ref.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void clearPlatform(ServerLevel level, BlockPos ref) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                level.setBlock(ref.offset(x, -2, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** A server-side player that is in no player list and ignores packets (vanilla's mock player needs a channel). */
    private static ServerPlayer fakePlayer(ServerLevel level) {
        ServerPlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "crac_gametest"));
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static Group group(GameTestHelper h, UUID owner, String tag) {
        Group g = GroupData.get(h.getLevel().getServer()).create("gametest", tag, 0x55AAFF, owner);
        check(g != null, "test setup: could not create a group");
        return g;
    }

    /** Sets a Claim Block at {@code pos} and creates its claim. */
    private static Claim claim(ServerLevel level, BlockPos pos, Group g) {
        level.setBlock(pos, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        ClaimManager.addClaim(level, pos, g.id(), 1);
        Claim c = ClaimManager.getByAnchor(level, pos);
        check(c != null, "test setup: no claim at " + pos.toShortString());
        return c;
    }

    /** Removes what a test made: the Claim Block (without dropping its stock), the claim, the raid block and the group. */
    private static void cleanup(ServerLevel level, BlockPos claimPos, Group g, boolean disband) {
        if (level.getBlockEntity(claimPos) instanceof ClaimBlockEntity be) {
            be.drainToStacks();
        }
        if (level.getBlockState(claimPos).is(ModBlocks.CLAIM_BLOCK.get())) {
            level.removeBlock(claimPos, false);
        }
        if (ClaimManager.getByGroup(level, g.id()) != null) {
            ClaimManager.removeByGroup(level, g.id());
        }
        RaidBlockManager.clear(level, g.id());
        if (disband) {
            GroupData.get(level.getServer()).disband(g);
        }
    }

    // ------------------------------------------------------------------ claim placement (F12)

    /** Runs in a batch of its own, away from other tests' buildings (see {@link GT#clearAround}). */
    @GameTest(template = EMPTY, batch = "claim_zone_swallow")
    public static void claimCannotSwallowAnotherGroupsBuilding(GameTestHelper h) {
        GT.clearAround(h, 15, 15);
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Player owner = h.makeMockPlayer();
        Group g = group(h, owner.getUUID(), "GTC");
        UUID stranger = UUID.randomUUID();
        try {
            foundation(h, o, Tier.TWIG, owner.getUUID());
            // the stranger builds 9 blocks above, inside the zone; stacked so that nothing reaches the neighbouring tests
            BlockPos strangerRef = o.above(9);
            platform(level, strangerRef);
            Building foreign = StructureService.buildingOf(level, foundation(h, strangerRef, Tier.TWIG, stranger));
            check(foreign != null, "test setup: the stranger's foundation has no building");

            String problem = ClaimBlockHandler.placementProblem(level, owner, g, GT.cellSeat(o));
            check(problem != null && fr.heraliz.crac.heraliz.Txt.c(problem).getString().contains("Another group"), "claim accepted over a stranger's building: " + problem);

            // a takeover is a Claim Block on that building itself; the group's own building around it is no obstacle
            String takeover = ClaimBlockHandler.placementProblem(level, owner, g, GT.cellSeat(strangerRef));
            check(takeover == null, "Rust-style takeover refused: " + takeover);

            // the stranger joins the group: the building is no longer foreign
            GroupData.get(level.getServer()).addMember(g, stranger);
            String joined = ClaimBlockHandler.placementProblem(level, owner, g, GT.cellSeat(o));
            check(joined == null, "claim refused over a member's building: " + joined);
        } finally {
            cleanup(level, GT.cellSeat(o), g, true);
        }
        h.succeed();
    }

    /** Runs in a batch of its own, away from other tests' buildings (see {@link GT#clearAround}). */
    @GameTest(template = EMPTY, batch = "claim_zone_upgrade")
    public static void claimUpgradeCannotSwallowAnotherGroupsBuilding(GameTestHelper h) {
        GT.clearAround(h, 15, 15);
        ServerLevel level = h.getLevel();
        Player owner = h.makeMockPlayer();
        Group g = group(h, owner.getUUID(), "GTU");
        UUID stranger = UUID.randomUUID();
        // built 40 blocks up: a tier 2 zone is wide enough to reach the neighbouring tests' pieces at ground level
        BlockPos base = h.absolutePos(new BlockPos(4, 43, 4));
        // a stranger's foundation whose slab is 17 blocks above: outside the tier 1 zone (16 high), inside tier 2 (19 high)
        BlockPos high = base.above(18);
        BlockPos claimPos = GT.cellSeat(base);
        Piece mine = null;
        Piece theirs = null;
        try {
            platform(level, base);
            platform(level, high);
            mine = foundation(h, base, Tier.TWIG, owner.getUUID());
            theirs = foundation(h, high, Tier.TWIG, stranger);
            String problem = ClaimBlockHandler.placementProblem(level, owner, g, claimPos);
            check(problem == null, "test setup: tier 1 claim refused: " + problem);
            Claim c = claim(level, claimPos, g);

            String upgrade = ClaimManager.upgradeProblem(level, c, 2);
            check(upgrade != null && fr.heraliz.crac.heraliz.Txt.c(upgrade).getString().contains("Another group"), "the claim grew over a stranger's building: " + upgrade);
            GroupData.get(level.getServer()).addMember(g, stranger);
            String joined = ClaimManager.upgradeProblem(level, c, 2);
            check(joined == null, "upgrade refused over a member's building: " + joined);
        } finally {
            cleanup(level, claimPos, g, true);
            // above the area the GameTest framework clears between runs: remove everything by hand
            for (Piece p : new Piece[]{mine, theirs}) {
                if (p != null) {
                    StructureService.remove(level, p, StructureService.Cause.ADMIN);
                }
            }
            clearPlatform(level, base);
            clearPlatform(level, high);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ raid block (F14, F15, F25)

    @GameTest(template = EMPTY)
    public static void raidBlockOutlivesTheClaimBlockAndTheGroup(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        GroupData groups = GroupData.get(level.getServer());
        Player owner = h.makeMockPlayer();
        Group g = group(h, owner.getUUID(), "GTR");
        Group again = null;
        boolean disbanded = false;
        BlockPos claimPos = GT.cellSeat(o);
        ServerPlayer builder;
        try {
            Piece base = foundation(h, o, Tier.STONE, owner.getUUID());
            claim(level, claimPos, g);
            RaidBlockManager.trigger(level, base.key().centreBlock());
            check(RaidBlockManager.isRaidBlocked(level, g.id()), "explosive damage did not raid block the claim");
            check(RaidBlockData.get(level).get(g.id()) != null, "the raid block is not kept in the saved data");
            check(ClaimManager.upgradeProblem(level, ClaimManager.getByAnchor(level, claimPos), 2) != null, "the claim was upgraded during its raid block");
            // disbanding would break the Claim Block, which the raid block forbids
            check(ClaimBlockHandler.RAID_BLOCKED_REMOVAL.equals(C2SGroupActionTraitement.disbandProblem(level.getServer(), g)),
                "the owner could disband the group, breaking its Claim Block, during its raid block");

            // the Claim Block is destroyed: its zone stays blocked
            level.removeBlock(claimPos, false);
            check(ClaimManager.getByAnchor(level, claimPos) == null, "test setup: the claim should go with its block");
            check(RaidBlockManager.isZoneRaidBlocked(level, claimPos), "losing the Claim Block lifted the raid block");
            check(C2SGroupActionTraitement.disbandProblem(level.getServer(), g) == null, "a group left without a Claim Block cannot be disbanded");

            // disband and recreate the group: still no Claim Block on the raided building
            groups.disband(g);
            disbanded = true;
            again = group(h, owner.getUUID(), "GTR2");
            String problem = ClaimBlockHandler.placementProblem(level, owner, again, claimPos);
            check(problem != null && fr.heraliz.crac.heraliz.Txt.c(problem).getString().contains("raid block"), "a recreated group claimed the raided building: " + problem);

            // nobody repairs or upgrades the unclaimed pieces of the raided zone either
            builder = fakePlayer(level);
            builder.moveTo(o.getX() + 1.5, o.getY(), o.getZ() + 4.5);
            StructureService.damage(level, base, 50, false);
            StructureService.Outcome repair = StructureService.repair(level, builder, base);
            check(!repair.ok() && repair.message().getString().contains("raid block"), "repair allowed in a raided zone: " + repair.message().getString());
            StructureService.Outcome upgrade = StructureService.upgrade(level, builder, base, Tier.METAL);
            check(!upgrade.ok() && upgrade.message().getString().contains("raid block"), "upgrade allowed in a raided zone: " + upgrade.message().getString());
        } finally {
            if (again != null) {
                cleanup(level, claimPos, again, true);
            }
            cleanup(level, claimPos, g, !disbanded);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mobExplosionsDoNotRaidBlock(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Player owner = h.makeMockPlayer();
        Group g = group(h, owner.getUUID(), "GTE");
        BlockPos claimPos = GT.cellSeat(o);
        try {
            Piece base = foundation(h, o, Tier.ARMORED, owner.getUUID());
            claim(level, claimPos, g);
            // the blast centre is a block of the slab, so the piece (not the Claim Block) is the nearest target
            Vec3 at = Vec3.atCenterOf(o.below());
            Creeper creeper = new Creeper(EntityType.CREEPER, level);
            creeper.moveTo(at.x, at.y, at.z);
            level.explode(creeper, at.x, at.y, at.z, 2.0F, Level.ExplosionInteraction.MOB);
            check(base.hp < base.maxHp, "the creeper's blast did not damage the foundation");
            check(!RaidBlockManager.isRaidBlocked(level, g.id()), "a creeper started raid block");

            int hp = base.hp;
            level.explode(null, at.x, at.y, at.z, 2.0F, Level.ExplosionInteraction.TNT);
            check(base.hp < hp, "the explosive did not damage the foundation");
            check(RaidBlockManager.isRaidBlocked(level, g.id()), "an explosive did not start raid block");
        } finally {
            cleanup(level, claimPos, g, true);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ upkeep stock (F16, F18, F49, F50, F71)

    @GameTest(template = EMPTY)
    public static void upkeepStockKeepsTheItemItWasPaidIn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(7, 2, 7));
        level.setBlock(pos, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        try {
            check(level.getBlockEntity(pos) instanceof ClaimBlockEntity, "test setup: no Claim Block entity");
            ClaimBlockEntity be = (ClaimBlockEntity) level.getBlockEntity(pos);
            // wood upkeep paid in iron while the config said so; the config names planks now
            CompoundTag tag = be.saveWithoutMetadata();
            tag.putIntArray("Stored", new int[]{100, 0, 0, 0});
            ListTag items = new ListTag();
            for (String id : new String[]{"minecraft:iron_ingot", "", "", ""}) {
                items.add(StringTag.valueOf(id));
            }
            tag.put("Items", items);
            be.load(tag);
            check(be.storedItem(0) == Items.IRON_INGOT, "the stock was relabelled as " + be.storedItem(0));
            check(!be.accepts(0, new ItemStack(Items.OAK_PLANKS)), "planks went in on top of iron");
            ItemStack out = be.withdraw(0, 64);
            check(out.is(Items.IRON_INGOT) && out.getCount() == 64, "withdrew " + out + " instead of 64 iron ingots");
            check(be.withdraw(0, 64).getCount() == 36 && be.getStored(0) == 0, "the rest of the iron did not come out");
            check(be.accepts(0, new ItemStack(Items.OAK_PLANKS)), "the emptied slot refused the configured item");

            // items that stack to 16 come out 16 at a time
            tag = be.saveWithoutMetadata();
            tag.putIntArray("Stored", new int[]{40, 0, 0, 0});
            items = new ListTag();
            for (String id : new String[]{"minecraft:ender_pearl", "", "", ""}) {
                items.add(StringTag.valueOf(id));
            }
            tag.put("Items", items);
            be.load(tag);
            check(be.window(0) == 16, "an ender pearl window of " + be.window(0));
            check(be.withdraw(0, 64).getCount() == 16, "more than a stack of ender pearls came out");

            // the stock and its item survive a save, the planks put in now included
            be.drainToStacks();
            check(be.addStored(0, Items.OAK_PLANKS, 70) == 70, "planks refused");
            check(be.addStored(0, Items.SPRUCE_PLANKS, 5) == 0, "two items mixed in one slot");
            ClaimBlockEntity copy = new ClaimBlockEntity(pos, level.getBlockState(pos));
            copy.load(be.saveWithoutMetadata());
            check(copy.storedItem(0) == Items.OAK_PLANKS && copy.getStored(0) == 70, "the stock was not saved with its item");

            // nothing of the stock or the owner reaches clients: only the colour does
            be.setOwnerGroup(UUID.randomUUID());
            CompoundTag update = be.getUpdateTag();
            check(update.contains("Color") && !update.contains("Stored") && !update.contains("Items")
                && !update.contains("Needed") && !update.contains("Debt") && !update.contains("Group"), "the update tag leaks " + update);

            // the Claim Block is no placable that decays on its own
            check(!ClaimMaintenanceManager.decaysAsPlacable(level.getBlockState(pos)), "the Claim Block decays like a placable");
            check(ClaimMaintenanceManager.decaysAsPlacable(Blocks.CRAFTING_TABLE.defaultBlockState()), "placables stopped decaying");
            be.drainToStacks();
        } finally {
            level.removeBlock(pos, false);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void hoppersFeedTheUpkeepButCannotDrainIt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(7, 4, 7));
        BlockPos above = pos.above();
        BlockPos below = pos.below();
        level.setBlock(pos, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), Block.UPDATE_ALL);
        level.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), Block.UPDATE_ALL);
        ClaimBlockEntity be = (ClaimBlockEntity) level.getBlockEntity(pos);
        HopperBlockEntity top = (HopperBlockEntity) level.getBlockEntity(above);
        HopperBlockEntity bottom = (HopperBlockEntity) level.getBlockEntity(below);
        check(be != null && top != null && bottom != null, "test setup: block entities missing");
        be.addStored(0, Items.OAK_PLANKS, 100);
        top.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        // not an upkeep item: it stays in the hopper
        top.setItem(1, new ItemStack(Items.DIRT, 5));
        h.runAtTickTime(60, () -> {
            try {
                int fed = 64 - top.getItem(0).getCount();
                check(fed > 0, "the hopper above fed nothing into the upkeep");
                check(be.getStored(0) == 100 + fed, "the stock is " + be.getStored(0) + " after " + fed + " planks went in: items were lost");
                check(top.getItem(1).is(Items.DIRT) && top.getItem(1).getCount() == 5, "dirt went into the upkeep");
                check(bottom.isEmpty(), "the hopper below pulled " + bottom.getItem(0));
            } finally {
                top.clearContent();
                bottom.clearContent();
                be.drainToStacks();
                level.removeBlock(above, false);
                level.removeBlock(below, false);
                level.removeBlock(pos, false);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void kickedMemberLosesTheUpkeepMenu(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        GroupData groups = GroupData.get(level.getServer());
        Player owner = h.makeMockPlayer();
        Player member = h.makeMockPlayer();
        Player stranger = h.makeMockPlayer();
        Group g = group(h, owner.getUUID(), "GTK");
        BlockPos claimPos = GT.cellSeat(o);
        try {
            foundation(h, o, Tier.TWIG, owner.getUUID());
            claim(level, claimPos, g);
            check(groups.addMember(g, member.getUUID()), "test setup: could not add the member");
            ClaimBlockEntity be = (ClaimBlockEntity) level.getBlockEntity(claimPos);
            for (Player p : new Player[]{member, stranger}) {
                p.moveTo(claimPos.getX() + 0.5, claimPos.getY(), claimPos.getZ() + 2.5);
            }
            ClaimUpkeepMenu menu = new ClaimUpkeepMenu(1, member.getInventory(), be);
            check(menu.stillValid(member), "a member cannot keep the upkeep menu open");
            check(!be.stillValidFor(stranger), "a stranger can use the upkeep menu");
            groups.removeMember(g, member.getUUID());
            check(!menu.stillValid(member), "a kicked member keeps the upkeep menu");
        } finally {
            cleanup(level, claimPos, g, true);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ claims vs world (F17, F34, F86)

    @GameTest(template = EMPTY)
    public static void claimsAreReconciledWithTheWorld(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        ServerPlayer owner = fakePlayer(level);
        Group g = group(h, owner.getUUID(), "GTW");
        BlockPos claimPos = GT.cellSeat(o);
        BlockPos bare = h.absolutePos(new BlockPos(11, 2, 11));
        try {
            foundation(h, o, Tier.TWIG, owner.getUUID());
            // a claim whose Claim Block is gone (lost in a crash) is removed instead of being paid forever
            ClaimManager.addClaim(level, claimPos, g.id(), 1);
            Claim orphan = ClaimManager.getByAnchor(level, claimPos);
            check(ClaimMaintenanceManager.reconcile(level, orphan, StructureService.buildingOfClaim(level, claimPos)) == null, "an orphan claim held");
            check(ClaimManager.getByAnchor(level, claimPos) == null, "the orphan claim was kept");

            // a Claim Block whose claim was lost: its owner gets the claim back by using it
            level.setBlock(claimPos, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            ((ClaimBlockEntity) level.getBlockEntity(claimPos)).setOwnerGroup(g.id());
            check(ClaimBlockLogique.restoreClaim(level, fakePlayer(level), claimPos, level.getBlockState(claimPos)) == null, "a stranger restored the claim");
            ClaimBlockLogique.restoreClaim(level, owner, claimPos, level.getBlockState(claimPos));
            Claim restored = ClaimManager.getByAnchor(level, claimPos);
            check(restored != null && restored.groupId().equals(g.id()), "the owner could not restore the lost claim");
            Building b = StructureService.buildingOfClaim(level, claimPos);
            check(b != null && g.id().equals(b.ownerGroup), "the restored claim is not linked to its building");
            cleanup(level, claimPos, g, false);

            // a Claim Block standing on no building gives its claim up
            level.setBlock(bare, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            ClaimManager.addClaim(level, bare, g.id(), 1);
            Claim floating = ClaimManager.getByAnchor(level, bare);
            check(ClaimMaintenanceManager.reconcile(level, floating, StructureService.buildingOfClaim(level, bare)) == null, "a claim without a building held");
            check(ClaimManager.getByAnchor(level, bare) == null && !level.getBlockState(bare).is(ModBlocks.CLAIM_BLOCK.get()), "the claim without a building was kept");
        } finally {
            cleanup(level, bare, g, false);
            cleanup(level, claimPos, g, true);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void inactivityCountsFromTheLastMemberSeen(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Group g = group(h, UUID.randomUUID(), "GTI");
        try {
            long now = level.getServer().overworld().getGameTime();
            // never seen in game time (saved by an older version): not inactive, the counting starts now
            check(g.lastActiveTick() == -1L, "test setup: a new group should not be seen yet");
            check(!ClaimMaintenanceManager.isInactive(level.getServer(), g.id()), "an unseen group counts as inactive");
            check(g.lastActiveTick() == now, "the counting did not start at the current game time");
            check(!ClaimMaintenanceManager.isInactive(level.getServer(), g.id()), "a group seen just now is inactive");
        } finally {
            GroupData.get(level.getServer()).disband(g);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void overlappingZonesGoToTheNearestClaimBlock(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // a standalone claim list: nothing is added to the world's claims
        ClaimData data = new ClaimData();
        BlockPos a = h.absolutePos(new BlockPos(4, 3, 4));
        BlockPos b = a.east(10);
        UUID ga = new UUID(9L, 1L);
        UUID gb = new UUID(9L, 2L);
        data.add(new Claim(ga, a, 1));
        data.add(new Claim(gb, b, 1));
        check(ga.equals(data.getAt(level, a.east(3)).groupId()), "a block near the first Claim Block went to the other claim");
        check(gb.equals(data.getAt(level, a.east(7)).groupId()), "a block near the second Claim Block went to the other claim");
        Claim tie = data.getAt(level, a.east(5));
        check(tie != null && tie.anchor().asLong() == Math.min(a.asLong(), b.asLong()), "a tie is not settled by position");

        // the client settles overlaps the same way, whatever order its list is in (CLIENT-02)
        List<S2CNearbyClaims.ClaimEntry> entries = new ArrayList<>();
        for (Claim c : data.all()) {
            entries.add(new S2CNearbyClaims.ClaimEntry(c.center(), c.radius(), c.yRadius(), c.tier(), 0, c.groupId().equals(ga)));
        }
        List<S2CNearbyClaims.ClaimEntry> reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);
        for (int dx = -20; dx <= 30; dx++) {
            for (int dy = -20; dy <= 20; dy += 4) {
                BlockPos pos = a.offset(dx, dy, 3);
                Claim server = data.getAt(level, pos);
                for (List<S2CNearbyClaims.ClaimEntry> list : List.of(entries, reversed)) {
                    S2CNearbyClaims.ClaimEntry client = S2CNearbyClaims.claimAt(list, pos);
                    check(server == null ? client == null : client != null && client.center().equals(server.anchor()),
                        "client and server disagree on the claim at " + pos.toShortString());
                }
            }
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ claims vs lost or unreadable stores (SERVER-01, RULES-01, NETWORK-01)

    @GameTest(template = EMPTY)
    public static void untrustedStoresCostNoClaimNorLink(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        StructureStore store = StructureStore.get(level);
        ClaimData claims = ClaimData.get(level);
        check(ClaimMaintenanceManager.distrust(level) == null, "test setup: the test world's stores are not trusted: " + ClaimMaintenanceManager.distrust(level));
        Group g = group(h, UUID.randomUUID(), "GTL");
        Group other = group(h, UUID.randomUUID(), "GTO");
        BlockPos bare = h.absolutePos(new BlockPos(11, 2, 11));
        BlockPos gone = h.absolutePos(new BlockPos(11, 2, 2));
        BlockPos lostClaim = GT.cellSeat(o);
        Building b = null;
        try {
            // a Claim Block standing on no building, and a claim whose Claim Block is missing
            level.setBlock(bare, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            ClaimManager.addClaim(level, bare, g.id(), 1);
            ClaimManager.addClaim(level, gone, other.id(), 1);
            Claim floating = ClaimManager.getByAnchor(level, bare);
            Claim orphan = ClaimManager.getByAnchor(level, gone);
            // a building linked to a claim the claim data does not have (it may only be unreadable)
            Piece f = foundation(h, o, Tier.TWIG, g.owner());
            b = StructureService.buildingOf(level, f);
            check(b != null, "test setup: the foundation has no building");
            b.claimAnchor = lostClaim;

            // either store standing in for a lost or unreadable file: nothing is popped, removed or unlinked
            for (SafeSavedData data : new SafeSavedData[]{store, claims}) {
                data.setStandIn(true);
                try {
                    String why = ClaimMaintenanceManager.distrust(level);
                    check(why != null, "a stand-in store is trusted");
                    ClaimMaintenanceManager.reconcile(level, floating, null);
                    check(level.getBlockState(bare).is(ModBlocks.CLAIM_BLOCK.get()) && ClaimManager.getByAnchor(level, bare) != null,
                        "a Claim Block was popped while " + why);
                    ClaimMaintenanceManager.reconcile(level, orphan, null);
                    check(ClaimManager.getByAnchor(level, gone) != null, "a claim was removed while " + why);
                    ClaimMaintenanceManager.claimedBuildings(level);
                    check(lostClaim.equals(b.claimAnchor), "a building lost its claim link while " + why);
                } finally {
                    data.setStandIn(false);
                }
            }

            // entries kept unread make a store just as untrusted
            CompoundTag future = f.save();
            future.putString("Kind", "gold_foundation");
            CompoundTag file = new CompoundTag();
            file.putInt("Format", StructureStore.FORMAT);
            ListTag buildings = new ListTag();
            buildings.add(b.save());
            file.put("Buildings", buildings);
            ListTag pieces = new ListTag();
            pieces.add(future);
            file.put("Pieces", pieces);
            StructureStore incomplete = StructureStore.load(file);
            check(!incomplete.isComplete() && ClaimMaintenanceManager.distrust(incomplete, claims) != null, "a store keeping unread entries is trusted");
            CompoundTag broken = new CompoundTag();
            broken.putLong("Anchor", gone.asLong());
            ListTag claimList = new ListTag();
            claimList.add(broken);
            CompoundTag claimFile = new CompoundTag();
            claimFile.put("Claims", claimList);
            check(ClaimMaintenanceManager.distrust(store, ClaimData.load(claimFile)) != null, "claim data keeping unread entries is trusted");

            // both stores trusted again: the floating claim, the orphan claim and the dangling link go
            check(ClaimMaintenanceManager.reconcile(level, floating, null) == null && ClaimManager.getByAnchor(level, bare) == null
                && !level.getBlockState(bare).is(ModBlocks.CLAIM_BLOCK.get()), "a Claim Block on no building kept its claim");
            check(ClaimMaintenanceManager.reconcile(level, orphan, null) == null && ClaimManager.getByAnchor(level, gone) == null,
                "a claim without its Claim Block was kept");
            ClaimMaintenanceManager.claimedBuildings(level);
            check(b.claimAnchor == null, "a link to a claim that is gone was kept");
        } finally {
            if (b != null && lostClaim.equals(b.claimAnchor)) {
                b.claimAnchor = null;
            }
            cleanup(level, bare, g, true);
            cleanup(level, gone, other, true);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void unreadableOrLostFilesGiveStandIns(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        String id = "crac_gametest_unreadable";
        Path file = SafeSavedData.dataFile(level, id);
        Group g = group(h, UUID.randomUUID(), "GTF");
        BlockPos seat = GT.cellSeat(o);
        try {
            check(!SafeSavedData.fresh(level, id, new ClaimData()).isStandIn(), "data started without a file stands in for a lost one");
            try {
                Files.writeString(file, "not nbt");
            } catch (IOException e) {
                throw new GameTestAssertException("test setup: cannot write " + file + ": " + e);
            }
            check(SafeSavedData.fresh(level, id, new ClaimData()).isStandIn(), "data started over an unreadable file is trusted");

            // what tells a lost file from a new world: the other store has data that needs it
            boolean claimsBefore = ClaimData.hasClaims(level);
            foundation(h, o, Tier.TWIG, g.owner());
            claim(level, seat, g);
            check(ClaimData.hasClaims(level) && StructureStore.hasClaimLinks(level), "claims and claim links are not seen");
            cleanup(level, seat, g, false);
            check(ClaimData.hasClaims(level) == claimsBefore, "test setup: the claim was not removed");
        } finally {
            deleteWithCopies(file);
            cleanup(level, seat, g, true);
        }
        h.succeed();
    }

    /** Deletes a test's data file and the copies made of it while it was unreadable. */
    private static void deleteWithCopies(Path file) {
        String name = file.getFileName().toString();
        try (Stream<Path> siblings = Files.list(file.getParent())) {
            for (Path p : siblings.toList()) {
                if (p.getFileName().toString().startsWith(name)) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            throw new GameTestAssertException("cleanup: cannot delete " + file + ": " + e);
        }
    }

    @GameTest(template = EMPTY)
    public static void claimBlockOnAnUnknownOrUnlinkedBuildingKeepsItsClaim(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Group g = group(h, UUID.randomUUID(), "GTN");
        BlockPos slab = h.absolutePos(new BlockPos(11, 2, 11));
        BlockPos onUnknown = slab.above();
        BlockPos seat = GT.cellSeat(o);
        try {
            // a block of a piece the store cannot vouch for, as a lost file or an entry kept unread leaves them
            level.setBlock(slab, ModBlocks.STRUCTURE.get().defaultBlockState(), Block.UPDATE_ALL);
            // a foundation whose centre slab block is this one
            PieceKey unknownKey = PieceKey.square(PieceKind.FOUNDATION, slab.offset(-1, 1, -1), LPoint.ORIGIN, 0, 0, 0);
            Occupant unknown = new Occupant(0L, 0L, unknownKey, Tier.STONE, 0, 0L, 0);
            ((StructureBlockEntity) level.getBlockEntity(slab)).setOccupants(List.of(unknown));
            check(StructureIntegrity.holdsUnknownPieces(level, slab), "test setup: the store vouches for piece 0");
            Claim kept = claim(level, onUnknown, g);
            check(ClaimMaintenanceManager.reconcile(level, kept, null) != null && level.getBlockState(onUnknown).is(ModBlocks.CLAIM_BLOCK.get())
                && ClaimManager.getByAnchor(level, onUnknown) != null, "a Claim Block standing on a building the store does not know was popped");
            // once an operator cleared those blocks, the Claim Block stands on nothing and gives its claim up
            level.setBlock(slab, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            check(ClaimMaintenanceManager.reconcile(level, kept, null) == null && ClaimManager.getByAnchor(level, onUnknown) == null
                && !level.getBlockState(onUnknown).is(ModBlocks.CLAIM_BLOCK.get()), "a Claim Block standing on nothing kept its claim");

            // a link dropped while the claim file was lost comes back once the file is restored
            Building b = StructureService.buildingOf(level, foundation(h, o, Tier.TWIG, g.owner()));
            Claim restored = claim(level, seat, g);
            check(b != null && seat.equals(b.claimAnchor), "test setup: the claim is not linked to its building");
            b.claimAnchor = null;
            b.ownerGroup = null;
            check(ClaimMaintenanceManager.reconcile(level, restored, null) != null && ClaimManager.getByAnchor(level, seat) != null,
                "the claim of a building that lost its link was dropped");
            check(seat.equals(b.claimAnchor) && g.id().equals(b.ownerGroup), "the building under the Claim Block was not linked to its claim again");
        } finally {
            level.setBlock(slab, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockState(onUnknown).is(ModBlocks.CLAIM_BLOCK.get())) {
                level.removeBlock(onUnknown, false);
            }
            cleanup(level, seat, g, true);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ claim tier on placement (RULES-05)

    /** Runs in a batch of its own, away from other tests' buildings (see {@link GT#clearAround}). */
    @GameTest(template = EMPTY, batch = "claim_zone_big")
    public static void bigBuildingIsClaimedAtTheTierItNeeds(GameTestHelper h) {
        GT.clearAround(h, 15, 15);
        ServerLevel level = h.getLevel();
        ServerPlayer owner = fakePlayer(level);
        Group g = group(h, owner.getUUID(), "GTB");
        UUID builder = UUID.randomUUID();
        // 40 blocks up: a tier 3 zone at ground level would reach the neighbouring tests' pieces
        BlockPos base = h.absolutePos(new BlockPos(4, 43, 4));
        BlockPos claimPos = GT.cellSeat(base);
        List<Piece> row = new ArrayList<>();
        try {
            // seven foundations in a row: the far end is 19 blocks from the Claim Block, past tier 2's 18
            for (int i = 0; i < 7; i++) {
                BlockPos ref = base.east(3 * i);
                platform(level, ref);
                row.add(foundation(h, ref, Tier.TWIG, builder));
            }
            Building b = StructureService.buildingOf(level, row.get(0));
            check(b != null && b.pieces.size() == 7, "test setup: the foundations are not one building");
            // its group lost the Claim Block: another group takes it over by placing its own
            b.ownerGroup = UUID.randomUUID();
            int tier = StructureService.claimTierFor(level, claimPos);
            check(tier == 3, "a building reaching 19 blocks from its Claim Block gets tier " + tier);
            List<BuildCost> cost = ClaimManager.tierCost(3);
            check(!cost.isEmpty() && cost.stream().allMatch(BuildCost::isValid), "test setup: no valid tier 3 cost");
            check(ClaimManager.tierCost(1).isEmpty(), "a tier 1 claim costs more than its Claim Block");
            level.setBlock(claimPos, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);

            // one item short: refused, naming the tier and its cost
            for (int i = 0; i < cost.size(); i++) {
                owner.getInventory().add(cost.get(i).ingredient().getItems()[0].copyWithCount(cost.get(i).count() - (i == 0 ? 1 : 0)));
            }
            Component refused = ClaimBlockHandler.claim(level, owner, claimPos);
            String text = refused == null ? "(accepted)" : refused.getString();
            check(text.contains("tier 3") && text.contains(Costs.describe(cost).getString()), "short of the tier 3 cost, the claim gave: " + text);
            check(ClaimManager.getByAnchor(level, claimPos) == null, "a claim was created without its cost");

            owner.getInventory().add(cost.get(0).ingredient().getItems()[0].copyWithCount(1));
            Component denied = ClaimBlockHandler.claim(level, owner, claimPos);
            check(denied == null, "the takeover of a big building was refused: " + (denied == null ? "" : denied.getString()));
            Claim c = ClaimManager.getByAnchor(level, claimPos);
            check(c != null && c.tier() == 3 && g.id().equals(c.groupId()), "the claim is not the group's tier 3 claim");
            check(level.getBlockState(claimPos).getValue(ClaimBlock.TIER) == 3, "the Claim Block does not show tier 3");
            check(claimPos.equals(b.claimAnchor) && g.id().equals(b.ownerGroup), "the building was not taken over");
            for (BuildCost each : cost) {
                check(owner.getInventory().countItem(each.ingredient().getItems()[0].getItem()) == 0, "the tier 3 cost was not taken");
            }
        } finally {
            cleanup(level, claimPos, g, true);
            // above the area the GameTest framework clears between runs: remove everything by hand
            for (Piece p : row) {
                StructureService.remove(level, p, StructureService.Cause.ADMIN);
            }
            for (int i = 0; i < 7; i++) {
                clearPlatform(level, base.east(3 * i));
            }
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ client views (CLIENT-01, CLIENT-04)

    @GameTest(template = EMPTY)
    public static void claimRadiusEditsRefreshTheClaimViews(GameTestHelper h) {
        int radius = Config.claimRadiusL2.get();
        int yRadius = Config.claimYRadiusL3.get();
        try {
            ConfigResync.claimViewChanged();
            check(!ConfigResync.claimViewChanged(), "the claim views are sent again although no claim setting changed");
            Config.claimRadiusL2.set(radius + 1);
            check(ConfigResync.claimViewChanged(), "a claim radius edit does not send the claim views again");
            Config.claimYRadiusL3.set(yRadius + 1);
            // the admin panel and config reloads go through pushChanged
            ConfigResync.pushChanged(h.getLevel().getServer());
            check(!ConfigResync.claimViewChanged(), "pushing a claim height edit did not send the claim views again");
        } finally {
            Config.claimRadiusL2.set(radius);
            Config.claimYRadiusL3.set(yRadius);
            ConfigResync.claimViewChanged();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void upkeepMenuTellsWhetherBasesDecay(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(7, 2, 7));
        level.setBlock(pos, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        boolean decay = Config.decayEnabled.get();
        try {
            check(level.getBlockEntity(pos) instanceof ClaimBlockEntity, "test setup: no Claim Block entity");
            ClaimBlockEntity be = (ClaimBlockEntity) level.getBlockEntity(pos);
            ClaimUpkeepMenu menu = new ClaimUpkeepMenu(1, h.makeMockPlayer().getInventory(), be);
            for (boolean on : new boolean[]{false, true}) {
                Config.decayEnabled.set(on);
                check(menu.decayEnabled() == on, "the upkeep menu says decay is " + (on ? "off" : "on") + " while it is " + (on ? "on" : "off"));
            }
            check(menu.upkeepMinutes() == be.upkeepMinutes(), "the minutes left no longer come through the menu");
        } finally {
            Config.decayEnabled.set(decay);
            level.removeBlock(pos, false);
        }
        h.succeed();
    }
}
