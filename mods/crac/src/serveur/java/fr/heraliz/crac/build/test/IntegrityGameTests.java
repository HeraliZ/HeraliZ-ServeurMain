package fr.heraliz.crac.build.test;

import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.test.GT.Dir4;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.build.world.LevelEnv;
import fr.heraliz.crac.build.world.Occupant;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import fr.heraliz.crac.build.world.StructureIntegrity;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests of the store / world agreement: the self-heal never erases blocks it cannot vouch for, pieces whose
 * blocks vanished stop holding up the rest, foundations need ground, and replaced blocks stay replaced. Same layout as
 * {@link BuildGameTests}: a stone floor at relative y = 1, the first foundation's walking surface at y = 3.
 */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class IntegrityGameTests {
    private static final String EMPTY = "empty";
    private static final UUID BUILDER = new UUID(0L, 9L);

    private IntegrityGameTests() {
    }

    private static BlockPos ground(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z <= 13; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        return h.absolutePos(new BlockPos(4, 3, 4));
    }

    private static Piece place(GameTestHelper h, PieceKey key) {
        StructureService.Outcome out = StructureService.placeAdmin(h.getLevel(), key, Tier.TWIG, BUILDER);
        if (!out.ok() || out.piece() == null) {
            throw new GameTestAssertException("could not place " + key.kind().id + " at " + key.anchorBlock().toShortString() + " h" + key.h() + ": " + out.message().getString());
        }
        return out.piece();
    }

    private static boolean exists(GameTestHelper h, Piece p) {
        return StructureStore.get(h.getLevel()).index.piece(p.id) != null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A foundation with a north wall and a floor resting on that wall. */
    private static Piece[] smallHouse(GameTestHelper h, BlockPos o) {
        Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o));
        Piece wall = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0));
        Piece floor = place(h, GT.surface(h.getLevel(), PieceKind.FLOOR, o.above(3)));
        return new Piece[]{foundation, wall, floor};
    }

    /** Makes a block disappear without any block callback, as a region rollback or a lost chunk save would. */
    private static void vanish(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.removeBlockEntity(pos);
        chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, Blocks.AIR.defaultBlockState());
    }

    private static boolean holds(ServerLevel level, BlockPos pos, long pieceId) {
        return level.getBlockEntity(pos) instanceof StructureBlockEntity be && be.occupants().stream().anyMatch(o -> o.pieceId() == pieceId);
    }

    @GameTest(template = EMPTY)
    public static void setblockOverAStructureBlockKeepsTheNewBlock(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece[] house = smallHouse(h, o);
        BlockPos slab = GT.blockNear(house[0], house[0].key().centreWorld());
        BlockPos corner = GT.blockNear(house[0], GT.localPoint(house[0].key(), 8, -8, 8));
        check(!corner.equals(slab), "test setup: the corner slab block is the centre one");
        check(level.setBlock(slab, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL), "setBlock over the foundation reported a failure");
        check(level.getBlockState(slab).is(Blocks.STONE), "the block put over the structure block was replaced by " + level.getBlockState(slab));
        for (Piece p : house) {
            check(!exists(h, p), p.kind().id + " survived its foundation");
        }
        check(level.getBlockState(corner).isAir(), "the rest of the foundation was left in the world");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pieceWhoseBlockVanishedStopsHoldingTheRest(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece[] house = smallHouse(h, o);
        Piece wall = house[1];
        BlockPos wallBlock = GT.wallBlock(wall, 24, 1, 24);
        check(holds(level, wallBlock, wall.id), "test setup: the wall does not occupy " + wallBlock.toShortString());
        vanish(level, wallBlock);
        StructureIntegrity.queueChunk(level, new ChunkPos(wallBlock));
        StructureService.tick(level);
        check(!exists(h, wall), "a wall with a vanished block is still in the store");
        check(!exists(h, house[2]), "the floor is still held up by a wall that lost a block");
        check(exists(h, house[0]), "the foundation was removed too");
        check(level.getBlockState(wallBlock).isAir(), "the vanished position was filled again");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void blockThatLostItsRecordIsRewritten(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o));
        BlockPos slab = GT.blockNear(foundation, foundation.key().centreWorld());
        check(level.getBlockEntity(slab) instanceof StructureBlockEntity, "test setup: no block entity under the foundation");
        ((StructureBlockEntity) level.getBlockEntity(slab)).setOccupants(List.of());
        StructureIntegrity.queueChunk(level, new ChunkPos(slab));
        StructureService.tick(level);
        StructureService.tick(level);
        check(exists(h, foundation), "the foundation was removed instead of repaired");
        check(holds(level, slab, foundation.id), "the block entity was not rewritten from the store");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void foundationLeftWithoutGroundCollapses(GameTestHelper h) {
        BlockPos o = ground(h);
        Piece[] house = smallHouse(h, o);
        ServerLevel level = h.getLevel();
        // mine the ground under every foundation column but the last one: it still rests on that one
        Piece f = house[0];
        List<int[]> cols = Footprints.columns(f.key());
        int slabY = PieceSolids.slabRow(f.key());
        BlockPos[] ground = new BlockPos[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            ground[i] = new BlockPos(cols.get(i)[0], slabY - f.skirt[i] - 1, cols.get(i)[1]);
            check(level.getBlockState(ground[i]).is(Blocks.STONE), "test setup: no stone under column " + i);
        }
        for (int i = 0; i < ground.length - 1; i++) {
            level.setBlock(ground[i], Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        StructureService.tick(level);
        for (Piece p : house) {
            check(exists(h, p), p.kind().id + " fell although the foundation still rests on one column");
        }
        level.setBlock(ground[ground.length - 1], Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check(level.getBlockState(ground[ground.length - 1]).isAir(), "test setup: the last column still has its stone");
        StructureService.tick(h.getLevel());
        for (Piece p : house) {
            check(!exists(h, p), p.kind().id + " still stands on a foundation with no ground under it");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void deployablesAreNotGroundAndTheSkyLimitIsNotFree(GameTestHelper h) {
        ground(h);
        ServerLevel level = h.getLevel();
        LevelEnv env = new LevelEnv(level);
        check(env.isGround(h.absolutePos(new BlockPos(2, 1, 2))), "stone is not ground");
        h.setBlock(3, 2, 3, Blocks.CHEST);
        check(!env.isGround(h.absolutePos(new BlockPos(3, 2, 3))), "a chest counts as ground");
        h.setBlock(5, 2, 3, Blocks.CRAFTING_TABLE);
        if (PlacableSet.contains(level, Blocks.CRAFTING_TABLE)) {
            check(!env.isGround(h.absolutePos(new BlockPos(5, 2, 3))), "a placable workbench counts as ground");
        }
        BlockPos column = h.absolutePos(new BlockPos(7, 2, 7));
        BlockPos top = new BlockPos(column.getX(), level.getMaxBuildHeight(), column.getZ());
        check(!env.isFree(top), "the void above the build limit counts as free space");
        check(env.isFree(top.below()), "the highest buildable block is not free");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void withersAndTheDragonCannotBreakStructures(GameTestHelper h) {
        for (Block b : new Block[]{ModBlocks.STRUCTURE.get(), ModBlocks.CLAIM_BLOCK.get()}) {
            check(b.defaultBlockState().is(BlockTags.WITHER_IMMUNE), b + " can be broken by a wither");
            check(b.defaultBlockState().is(BlockTags.DRAGON_IMMUNE), b + " can be broken by the ender dragon");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void selfHealKeepsBlocksItCannotVouchFor(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        StructureStore store = StructureStore.get(level);
        BlockPos pos = h.absolutePos(new BlockPos(10, 2, 10));
        long removed = Long.MAX_VALUE - 7;
        check(store.vouchesFor(removed) && store.index.piece(removed) == null, "test setup: piece id " + removed + " is not a removed piece");
        orphan(level, pos, removed);
        StructureService.queueVerify(level, pos);
        StructureService.tick(level);
        check(level.getBlockState(pos).isAir(), "a block of a removed piece survived the self-heal");

        if (!store.vouchesFor(1L)) {
            // pieces older than the store (lost or unreadable file) are never erased
            orphan(level, pos, 1L);
            StructureService.queueVerify(level, pos);
            StructureService.tick(level);
            check(level.getBlockState(pos).getBlock() instanceof StructureBlock && holds(level, pos, 1L), "the self-heal erased a block of a piece older than the store");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        h.succeed();
    }

    /** A structure block holding a record of piece {@code id}, which the store does not have. */
    private static void orphan(ServerLevel level, BlockPos pos, long id) {
        level.setBlock(pos, ModBlocks.STRUCTURE.get().defaultBlockState(), Block.UPDATE_ALL);
        // a floor one level up whose centre slab block is this one
        PieceKey key = PieceKey.square(PieceKind.FLOOR, pos.offset(-1, -2, -1), LPoint.ORIGIN, 0, 2, 0);
        Occupant floor = new Occupant(id, id, key, Tier.STONE, 0, 0L, 0);
        ((StructureBlockEntity) level.getBlockEntity(pos)).setOccupants(List.of(floor));
    }

    @GameTest(template = EMPTY)
    public static void costsCountEachItemOnceAndTyposBlock(GameTestHelper h) {
        Player player = h.makeMockPlayer();
        player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 5));
        List<BuildCost> overlapping = BuildCost.parseAll(List.of("#minecraft:planks=5", "minecraft:oak_planks=3"));
        check(overlapping.stream().allMatch(BuildCost::isValid), "test setup: valid entries refused");
        check(!Costs.has(player, overlapping), "5 planks paid for 8");
        player.getInventory().add(new ItemStack(Items.BIRCH_PLANKS, 5));
        check(Costs.has(player, overlapping), "3 oak + 5 other planks could not pay 3 oak + 5 planks");

        List<BuildCost> typo = BuildCost.parseAll(List.of("minecraft:stick=1", "crac:nails=4"));
        player.getInventory().add(new ItemStack(Items.STICK, 1));
        check(typo.size() == 2 && !typo.get(1).isValid(), "an unknown item was accepted or dropped");
        check(!Costs.has(player, typo), "a cost with an unknown item could be paid");
        check(!BuildCost.parse("#forge:no_such_tag=2").isValid(), "an unknown tag was accepted");
        h.succeed();
    }
}
