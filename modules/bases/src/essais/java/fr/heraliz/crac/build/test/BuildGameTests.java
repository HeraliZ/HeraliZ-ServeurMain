package fr.heraliz.crac.build.test;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.Variants;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.model.VertexKey;
import fr.heraliz.crac.build.test.GT.Dir4;
import fr.heraliz.crac.build.world.Occupant;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.event.ClaimBlockHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.raid.BlastDamage;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests of the building service: placement, collapse, doors, claims and explosive damage. Each test lays a
 * stone floor at relative y = 1 so the first foundation's slab sits at y = 2 and its walking surface at y = 3.
 * Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class BuildGameTests {
    private static final String EMPTY = "empty";
    private static final UUID BUILDER = new UUID(0L, 7L);

    private BuildGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    /** Lays the stone floor and returns the reference block of a foundation cell standing on it. */
    private static BlockPos ground(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z <= 13; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        return h.absolutePos(new BlockPos(4, 3, 4));
    }

    private static Piece place(GameTestHelper h, PieceKey key, Tier tier) {
        StructureService.Outcome out = StructureService.placeAdmin(h.getLevel(), key, tier, BUILDER);
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

    /** Every block of the piece is a structure block whose block entity lists the piece. */
    private static void assertInWorld(GameTestHelper h, Piece p) {
        ServerLevel level = h.getLevel();
        for (BlockPos pos : PieceIndex.blocksOf(p)) {
            check(level.getBlockState(pos).getBlock() instanceof StructureBlock, p.kind().id + ": no structure block at " + pos.toShortString());
            boolean listed = level.getBlockEntity(pos) instanceof StructureBlockEntity be
                && be.occupants().stream().map(Occupant::pieceId).anyMatch(id -> id == p.id);
            check(listed, p.kind().id + ": block entity at " + pos.toShortString() + " does not list it");
        }
    }

    private static void assertGone(GameTestHelper h, Piece p) {
        check(!exists(h, p), p.kind().id + " should have been removed");
        for (BlockPos pos : PieceIndex.blocksOf(p)) {
            boolean listed = h.getLevel().getBlockEntity(pos) instanceof StructureBlockEntity be
                && be.occupants().stream().map(Occupant::pieceId).anyMatch(id -> id == p.id);
            check(!listed, p.kind().id + " still in the block at " + pos.toShortString());
        }
    }

    /** Right-click the leaf of a doorway: a point inside the closed leaf, near its +n face, in a block of the door. */
    private static void useDoor(GameTestHelper h, Player player, Piece door) {
        Vec3 hit = GT.wallPoint(door.key(), 24, 0.9, 16);
        BlockPos leaf = GT.blockNear(door, hit);
        StructureService.onUse(h.getLevel(), leaf, player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.SOUTH, leaf, false));
    }

    /** A player-sized box standing in the opening of a doorway (0.6 wide, 1.8 high, centred on the opening). */
    private static AABB doorwayBox(Piece door) {
        Vec3 c = GT.wallPoint(door.key(), 24, 0, 0);
        return new AABB(c.x - 0.3, c.y + 0.05, c.z - 0.3, c.x + 0.3, c.y + 1.85, c.z + 0.3);
    }

    // ------------------------------------------------------------------ tests

    @GameTest(template = EMPTY)
    public static void wallOnBareGroundIsRefused(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        StructureService.Outcome onGround = StructureService.placeAdmin(level, GT.wall(h.getLevel(), PieceKind.WALL, o.below(), Dir4.NORTH, 0), Tier.TWIG, BUILDER);
        check(!onGround.ok(), "a wall was placed on bare ground");
        StructureService.Outcome floor = StructureService.placeAdmin(level, GT.surface(h.getLevel(), PieceKind.FLOOR, o.above(3)), Tier.TWIG, BUILDER);
        check(!floor.ok(), "a floor was placed in mid-air");
        check(StructureService.piecesIn(level, new AABB(o).inflate(4)).isEmpty(), "refused pieces left something in the world");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void foundationWallsAndFloorArePlaced(GameTestHelper h) {
        BlockPos o = ground(h);
        Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.TWIG);
        Piece[] walls = new Piece[4];
        int i = 0;
        for (Dir4 s : Dir4.values()) {
            walls[i++] = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, s, 0), Tier.TWIG);
        }
        Piece floor = place(h, GT.surface(h.getLevel(), PieceKind.FLOOR, o.above(3)), Tier.TWIG);
        assertInWorld(h, foundation);
        for (Piece w : walls) {
            assertInWorld(h, w);
            check(w.buildingId == foundation.buildingId, "wall joined another building");
        }
        assertInWorld(h, floor);
        check(floor.buildingId == foundation.buildingId, "floor joined another building");
        check(floor.stability > 0 && floor.stability < 100, "floor stability " + floor.stability);
        check(!StructureService.placeAdmin(h.getLevel(), GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0), Tier.TWIG, BUILDER).ok(), "two walls on one edge");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void newSupportRaisesStandingPieces(GameTestHelper h) {
        BlockPos o = ground(h);
        int vertical = BuildConfig.verticalLoss.get();
        int cantilever = BuildConfig.cantileverLoss.get();
        place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.TWIG);
        place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0), Tier.TWIG);
        place(h, GT.surface(h.getLevel(), PieceKind.FLOOR, o.above(3)), Tier.TWIG);
        Piece hanging = place(h, GT.surface(h.getLevel(), PieceKind.FLOOR, o.above(3).offset(3, 0, 0)), Tier.TWIG);
        check(hanging.stability == 100 - vertical - cantilever, "cantilever stability " + hanging.stability);
        place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o.offset(3, 0, 0)), Tier.TWIG);
        place(h, GT.wall(h.getLevel(), PieceKind.WALL, o.offset(3, 0, 0), Dir4.NORTH, 0), Tier.TWIG);
        check(hanging.stability == 100 - vertical, "a wall under the floor did not raise it: " + hanging.stability);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void destroyingTheWallUnderAFloorCollapsesIt(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.TWIG);
        Piece wall = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0), Tier.TWIG);
        Piece floor = place(h, GT.surface(h.getLevel(), PieceKind.FLOOR, o.above(3)), Tier.TWIG);
        StructureService.damage(level, wall, wall.maxHp, false);
        assertGone(h, wall);
        assertGone(h, floor);
        check(exists(h, foundation), "the foundation fell too");
        assertInWorld(h, foundation);
        for (BlockPos pos : PieceIndex.blocksOf(floor)) {
            check(level.getBlockState(pos).isAir(), "collapsed floor left a block at " + pos.toShortString());
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void closedDoorBlocksAndOpenDoorLetsThrough(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.TWIG);
        Piece door = place(h, GT.wall(h.getLevel(), PieceKind.DOORWAY, o, Dir4.NORTH, 0), Tier.WOOD);
        AABB box = doorwayBox(door);
        check(!level.noCollision(box), "closed door does not block the doorway");
        Player player = h.makeMockPlayer();
        useDoor(h, player, door);
        check(door.isOpen(), "door did not open");
        check(level.noCollision(box), "open door still blocks the doorway");
        useDoor(h, player, door);
        check(!door.isOpen(), "door did not close");
        check(!level.noCollision(box), "closed door does not block the doorway again");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void doorStaysLockedAfterTheClaimIsGone(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        GroupData groups = GroupData.get(level.getServer());
        Group group = groups.create("gametest", "GT", 0xFFFFFF, UUID.randomUUID());
        check(group != null, "could not create a group");
        try {
            Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.TWIG);
            Piece door = place(h, GT.wall(h.getLevel(), PieceKind.DOORWAY, o, Dir4.NORTH, 0), Tier.WOOD);
            BlockPos claimPos = GT.seat(foundation);
            ClaimManager.addClaim(level, claimPos, group.id(), 1);
            Building b = StructureService.buildingOf(level, foundation);
            check(b != null && claimPos.equals(b.claimAnchor) && group.id().equals(b.ownerGroup), "claim not linked to the building");
            ClaimManager.removeByAnchor(level, claimPos);
            check(b.claimAnchor == null && group.id().equals(b.ownerGroup), "owner group lost with the claim");

            Player stranger = h.makeMockPlayer();
            useDoor(h, stranger, door);
            check(!door.isOpen(), "a stranger opened the door of a building that lost its claim");
            Player member = h.makeMockPlayer();
            groups.addMember(group, member.getUUID());
            useDoor(h, member, door);
            check(door.isOpen(), "a group member could not open the door");
        } finally {
            if (ClaimManager.getByGroup(level, group.id()) != null) {
                ClaimManager.removeByGroup(level, group.id());
            }
            groups.disband(group);
        }
        h.succeed();
    }

    /** Runs in a batch of its own, away from other tests' buildings (see {@link GT#clearAround}). */
    @GameTest(template = EMPTY, batch = "claim_zone_foundation")
    public static void claimBlockNeedsAFoundation(GameTestHelper h) {
        GT.clearAround(h, 15, 15);
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        GroupData groups = GroupData.get(level.getServer());
        Player owner = h.makeMockPlayer();
        Group group = groups.create("gametest", "GT", 0xFFFFFF, owner.getUUID());
        check(group != null, "could not create a group");
        try {
            Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.TWIG);
            Piece wall = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0), Tier.TWIG);
            BlockPos onFoundation = GT.seat(foundation);
            BlockPos onGround = new BlockPos((int) Math.floor(foundation.bounds().maxX) + 2, onFoundation.getY() - 1, onFoundation.getZ());
            BlockPos onWall = GT.topBlock(wall, GT.wallPoint(wall.key(), 24, 0, 40)).above();
            check(ClaimBlockHandler.placementProblem(level, owner, group, onGround) != null, "claim accepted on bare ground");
            check(ClaimBlockHandler.placementProblem(level, owner, group, onWall) != null, "claim accepted on top of a wall");
            String problem = ClaimBlockHandler.placementProblem(level, owner, group, onFoundation);
            check(problem == null, "claim refused on a foundation: " + problem);

            level.setBlock(onFoundation, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            check(level.getBlockState(onFoundation).is(ModBlocks.CLAIM_BLOCK.get()), "test setup: Claim Block not set");
            ClaimManager.addClaim(level, onFoundation, group.id(), 1);
            Building b = StructureService.buildingOf(level, foundation);
            check(b != null && onFoundation.equals(b.claimAnchor), "claim not linked to the building");
            check(StructureService.claimProblem(level, onFoundation, 1) != null, "a second claim was accepted on a claimed building");

            level.removeBlock(onFoundation, false);
            check(ClaimManager.getByAnchor(level, onFoundation) == null, "breaking the Claim Block left the claim");
            check(b.claimAnchor == null && group.id().equals(b.ownerGroup), "building not released (or owner lost) when the Claim Block broke");
        } finally {
            if (ClaimManager.getByGroup(level, group.id()) != null) {
                ClaimManager.removeByGroup(level, group.id());
            }
            groups.disband(group);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void blastsHitEachPieceOnce(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.ARMORED);
        Piece wall = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0), Tier.ARMORED);
        int damage = 100;

        // Explosive Charge on the middle of the wall (inner side): most of its 7 splash blocks are wall blocks
        BlockPos center = GT.wallBlock(wall, 24, 1, 24);
        Vec3 inRoom = Vec3.atCenterOf(center);
        List<BlockPos> cross = BlastDamage.cross(center);
        long wallCells = cross.stream().filter(p -> StructureStore.get(level).index.occupants(p).contains(wall)).count();
        check(wallCells >= 3, "test setup: the splash should cover several wall blocks, got " + wallCells);
        int hits = BlastDamage.splash(level, inRoom, cross, damage, 0, true);
        check(hits == 1, "charge hit " + hits + " targets, expected only the wall");
        check(wall.hp == wall.maxHp - damage, "wall took " + (wall.maxHp - wall.hp) + " damage, expected " + damage);
        check(foundation.hp == foundation.maxHp, "the foundation was damaged by the charge");

        // generic explosion, radius 2 around the same block: wall, foundation and floor each hit once
        Piece floor = place(h, GT.surface(h.getLevel(), PieceKind.FLOOR, o.above(3)), Tier.ARMORED);
        int wallBefore = wall.hp;
        BlastDamage.splash(level, inRoom, BlastDamage.sphere(center, 2), damage, 0, true);
        check(wall.hp == wallBefore - damage, "explosion hit the wall " + (wallBefore - wall.hp) / damage + " times");
        check(foundation.hp == foundation.maxHp - damage, "explosion hit the foundation " + (foundation.maxHp - foundation.hp) / damage + " times");
        check(floor.hp == floor.maxHp - damage, "explosion hit the floor " + (floor.maxHp - floor.hp) / damage + " times");

        // C4 stuck to the wall face, in the block the wall shares with the floor above: only the wall
        int wallHp = wall.hp;
        int floorHp = floor.hp;
        BlockPos shared = GT.wallBlock(wall, 24, 1, 40);
        check(StructureStore.get(level).index.occupants(shared).size() == 2, "test setup: wall and floor should share " + shared.toShortString());
        BlastDamage.stuck(level, shared, GT.wallPoint(wall.key(), 24, PieceSolids.WALL_HALF, 40), damage);
        check(wall.hp == wallHp - damage && floor.hp == floorHp, "C4 damaged the wrong piece");
        h.succeed();
    }

    /**
     * A wall half may only hide in terrain: a Claim Block standing where the wall would go refuses the wall (it used to
     * be skipped like terrain, leaving a see-through hole beside the flag), while a dirt bank still hides its half.
     */
    @GameTest(template = EMPTY)
    public static void onlyTerrainHidesAWallHalf(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.TWIG);
        PieceKey wallKey = GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0);
        // a block holding only the outer (-n) half of the wall's lower part
        BlockPos outer = BlockPos.containing(GT.wallPoint(wallKey, 24, -1, 8));
        check(PieceSolids.reservedBlocks(wallKey, null).contains(outer), "test setup: the outer half is not a wall block");
        level.setBlock(outer, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        check(!StructureService.placeAdmin(level, wallKey, Tier.TWIG, BUILDER).ok(), "a wall was placed through a Claim Block");
        level.setBlock(outer, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        Piece wall = place(h, wallKey, Tier.TWIG);
        check(!wall.blocks.contains(outer), "the wall half in the dirt bank was not left out");
        h.succeed();
    }

    /** Twig walls are open lattices: they close no block face (light, face culling) until they are upgraded. */
    @GameTest(template = EMPTY)
    public static void twigWallsLetLightThrough(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.TWIG);
        Piece wall = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0), Tier.TWIG);
        BlockPos inner = GT.wallBlock(wall, 24, 1, 24);
        int north = 1 << Direction.NORTH.get3DDataValue();
        check((StructureBlock.faceMask(level.getBlockState(inner)) & north) == 0, "a twig wall closes its block face");
        StructureService.setTier(level, wall, Tier.WOOD);
        check((StructureBlock.faceMask(level.getBlockState(inner)) & north) != 0, "a wooden wall leaves its block face open");
        h.succeed();
    }

    /**
     * {@code /cracbuild showcase} with a tier it does not know (a typo) reports it and builds nothing, like
     * {@code /cracbuild tier}, instead of building the house of every tier. Run high above the test area, where no
     * foundation would find ground anyway.
     */
    @GameTest(template = EMPTY)
    public static void showcaseRefusesAnUnknownTier(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = new BlockPos(h.absolutePos(BlockPos.ZERO).getX(), level.getMaxBuildHeight() - 20, h.absolutePos(BlockPos.ZERO).getZ());
        List<String> said = new ArrayList<>();
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack src = new CommandSourceStack(sink, Vec3.atCenterOf(pos), Vec2.ZERO, level, 4, "gametest",
            Component.literal("gametest"), level.getServer(), null);
        level.getServer().getCommands().performPrefixedCommand(src, "cracbuild showcase " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " stne");
        check(said.stream().anyMatch(m -> m.contains("Unknown tier stne")), "no failure for an unknown tier: " + said);
        check(said.stream().noneMatch(m -> m.startsWith("Showcase:")), "the showcase ran with an unknown tier: " + said);
        check(StructureService.piecesIn(level, new AABB(pos).inflate(130, 10, 130)).isEmpty(), "pieces were built for an unknown tier");
        h.succeed();
    }

    /**
     * Corner posts follow tiers (highest tier, then oldest, draws the post): changing a wall's tier recomputes its own
     * post bits as well as its neighbours', so exactly one wall draws each band of the shared post, in the store and
     * in the block entities clients draw from.
     */
    @GameTest(template = EMPTY)
    public static void tierChangesRecomputeTheWallsOwnPosts(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.TWIG);
        Piece north = place(h, GT.wall(level, PieceKind.WALL, o, Dir4.NORTH, 0), Tier.TWIG);
        Piece west = place(h, GT.wall(level, PieceKind.WALL, o, Dir4.WEST, 0), Tier.TWIG);
        VertexKey corner = null;
        for (VertexKey v : north.key().vertices()) {
            if (west.key().vertices().contains(v)) corner = v;
        }
        check(corner != null, "test setup: the walls share no corner");
        checkPost(h, corner, north, "two twig walls: the oldest");
        StructureService.setTier(level, west, Tier.WOOD);
        checkPost(h, corner, west, "admin tier change: the wooden wall");
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(BUILDER, "crac_gametest"));
        player.setGameMode(GameType.CREATIVE);
        StructureService.Outcome up = StructureService.upgrade(level, player, north, Tier.STONE);
        check(up.ok(), "upgrade refused: " + up.message().getString());
        checkPost(h, corner, north, "hammer upgrade: the stone wall");
        h.succeed();
    }

    /** Every wall at {@code corner} has an up-to-date variant, and {@code owner} alone draws both bands of the post. */
    private static void checkPost(GameTestHelper h, VertexKey corner, Piece owner, String when) {
        StructureStore store = StructureStore.get(h.getLevel());
        for (Piece w : Variants.wallsAt(store.index, corner)) {
            check(w.variant == Variants.of(store.index, w), when + ": stale variant of wall " + w.id);
            int end = w.key().vertices().indexOf(corner);
            long bits = (w.variant >> (2 * end)) & 3;
            check(bits == (w.id == owner.id ? 3 : 0), when + ": wall " + w.id + " has post bits " + bits + " at the corner");
            for (BlockPos pos : PieceIndex.blocksOf(w)) {
                if (h.getLevel().getBlockEntity(pos) instanceof StructureBlockEntity be) {
                    for (Occupant oc : be.occupants()) {
                        check(oc.pieceId() != w.id || oc.variant() == w.variant, when + ": block " + pos.toShortString() + " carries a stale variant");
                    }
                }
            }
        }
    }

    /**
     * v3 pieces in the world (§12): an equilateral triangle on a square's edge, a wall on a half wall, a floor sitting
     * on half walls half a wall up, and L stairs a player can stand on step after step.
     */
    @GameTest(template = EMPTY)
    public static void latticePiecesStandInTheWorld(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece f0 = place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        Piece tri = place(h, GT.triangleOn(f0.key(), Dir4.NORTH, PieceKind.TRI_FOUNDATION), Tier.STONE);
        check(tri.buildingId == f0.buildingId, "the triangle did not join the building");
        assertInWorld(h, tri);

        // half walls around the square, a wall on one of them, a floor on top of them
        Piece[] halves = new Piece[4];
        int i = 0;
        for (Dir4 d : Dir4.values()) {
            halves[i++] = place(h, GT.wall(level, PieceKind.HALF_WALL, o, d, 0), Tier.STONE);
        }
        Piece onHalf = place(h, halves[1].key().with(PieceKind.WALL, 0).withH(1), Tier.STONE);
        check(onHalf.stability == 100 - BuildConfig.verticalLoss.get(), "a wall on a half wall has stability " + onHalf.stability);
        Piece floor = place(h, PieceKey.on(PieceKind.FLOOR, f0.key().origin(), f0.key().footprint(), 1, 0), Tier.STONE);
        check(floor.key().baseY() == 24.0, "the floor does not sit half a wall up");
        assertInWorld(h, onHalf);
        assertInWorld(h, floor);
        // its top is mid-block: no Claim Block on it
        check(StructureService.seatUnder(level, GT.seatOf(floor.key())) == null, "a seat was found on a half-level floor");

        // L stairs on a second square, south of the first: every tread holds a standing player
        Piece f1 = place(h, GT.surface(level, PieceKind.FOUNDATION, o.offset(0, 0, 3)), Tier.STONE);
        Piece stairs = place(h, PieceKey.on(PieceKind.STAIRS_L, f1.key().origin(), f1.key().footprint(), 0, 0), Tier.STONE);
        assertInWorld(h, stairs);
        int treads = 0;
        for (fr.heraliz.crac.build.geom.Prism step : fr.heraliz.crac.build.geom.PieceSolids.solids(stairs.key(), 0, 0L, null)) {
            double[] c = fr.heraliz.crac.build.geom.Poly.centroid(step.poly());
            double x = o.getX() + c[0] / 16.0;
            double z = o.getZ() + c[1] / 16.0;
            double top = o.getY() + step.y1() / 16.0;
            // a thin column (the treads are 5.5 px deep; players climb them with their 9.6 px step-up, rises are 6 px)
            AABB player = new AABB(x - 0.1, top + 0.01, z - 0.1, x + 0.1, top + 1.8, z + 0.1);
            AABB under = new AABB(x - 0.1, top - 0.05, z - 0.1, x + 0.1, top - 0.01, z + 0.1);
            check(level.noCollision(player), "no room to stand on the step at " + top);
            check(!level.noCollision(under), "nothing to stand on at " + top);
            treads++;
        }
        check(treads == 9, "L stairs have " + treads + " treads");
        h.succeed();
    }
}
