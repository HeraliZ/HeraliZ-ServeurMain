package fr.heraliz.crac.build.test;

import java.util.Collection;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.admin.AdminManager;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.net.PacketGuard;
import fr.heraliz.crac.build.test.GT.Dir4;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests of the server-side guards: hammer line of sight and the admin wipe.
 * Same layout as {@link BuildGameTests}: a stone floor at relative y = 1, the first foundation's walking surface at y = 3.
 */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class SecurityGameTests {
    private static final String EMPTY = "empty";
    private static final UUID BUILDER = new UUID(0L, 8L);

    private SecurityGameTests() {
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
            throw new GameTestAssertException("could not place " + key.kind().id + ": " + out.message().getString());
        }
        return out.piece();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    @GameTest(template = EMPTY)
    public static void hammerNeedsLineOfSight(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o));
        Piece wall = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0));
        // the wall straddles the cell's north edge: this block holds its outer half
        BlockPos outer = GT.wallBlock(wall, 24, -1, 24);
        check(PacketGuard.pieceAt(level, outer, wall.id) != null, "test setup: the wall does not occupy " + outer.toShortString());
        Vec3 outside = GT.wallPoint(wall.key(), 24, -56, 24);
        Vec3 inside = GT.wallPoint(wall.key(), 24, 40, 24);
        BlockPos screen = BlockPos.containing(GT.wallPoint(wall.key(), 24, -40, 24));
        check(PacketGuard.inSight(level, null, outside, outer, wall), "wall not in sight from the open side");
        check(PacketGuard.inSight(level, null, inside, outer, wall), "wall not in sight from inside the cell (through its own inner half)");
        // a stone screen between the outside eye and the wall
        for (int x = -2; x <= 3; x++) {
            for (int y = -2; y <= 3; y++) {
                level.setBlock(screen.offset(x, y, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        check(!PacketGuard.inSight(level, null, outside, outer, wall), "wall in sight through solid stone");
        check(PacketGuard.inSight(level, null, inside, outer, wall), "stone outside hid the wall from inside");
        h.succeed();
    }

    /**
     * A 30 degree wall shares its top row with the floor of the room behind it: from outside the wall is in sight in
     * those blocks but the floor is not; from inside both are (§12).
     */
    @GameTest(template = EMPTY)
    public static void hammerSightThroughARotatedWall(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece f0 = place(h, GT.surface(level, PieceKind.FOUNDATION, o));
        PieceKey tri = GT.triangleOn(f0.key(), Dir4.EAST, PieceKind.TRI_FOUNDATION);
        place(h, tri);
        int edge = -1;
        for (int i = 0; i < 3; i++) {
            if (tri.edgeDir(i) % 6 == 1) {
                edge = i;
            }
        }
        check(edge >= 0, "test setup: the triangle has no 30 degree edge");
        PieceKey roomKey = Topology.across(tri, edge, PieceKind.FOUNDATION, 0);
        place(h, roomKey);
        // rot 0 on the triangle's edge: +n is the triangle side, outside the room
        Piece wall = place(h, Topology.wallOn(tri, edge, PieceKind.WALL, 0, 0));
        Piece floor = place(h, PieceKey.on(PieceKind.FLOOR, roomKey.origin(), roomKey.footprint(), 2, 0));
        BlockPos shared = GT.sharedBlock(wall, floor, o.getY() + 2);
        Vec3 n = GT.normal(wall.key());
        Vec3 onPlane = GT.wallPoint(wall.key(), 24, 0, 40);
        Vec3 centre = Vec3.atCenterOf(shared);
        Vec3 beside = new Vec3(centre.x, onPlane.y, centre.z);
        Vec3 outside = beside.add(n.scale(2.5)).add(0, 0.6, 0);
        Vec3 inside = beside.subtract(n.scale(1.5)).subtract(0, 0.9, 0);
        check(PacketGuard.inSight(level, null, outside, shared, wall), "the wall is not in sight from outside");
        check(!PacketGuard.inSight(level, null, outside, shared, floor), "the floor behind the rotated wall is in sight from outside");
        check(PacketGuard.inSight(level, null, inside, shared, wall), "the wall is not in sight from inside the room");
        check(PacketGuard.inSight(level, null, inside, shared, floor), "the floor is not in sight from under it");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void wipeFindsPiecesInSmallAndHugeZones(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o));
        Piece wall = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, Dir4.NORTH, 0));
        // looked up block by block: a zone holding only the wall's top row
        BlockPos top = GT.wallBlock(wall, 24, -1, 40);
        Collection<Piece> small = AdminManager.piecesIn(level, top, top);
        check(small.contains(wall) && !small.contains(foundation), "small zone found " + small.size() + " pieces");
        // walked piece by piece: zones far larger than any block-by-block lookup
        Collection<Piece> huge = AdminManager.piecesIn(level, o.offset(-600, -20, -600), o.offset(600, 20, 600));
        check(huge.contains(wall) && huge.contains(foundation), "huge zone missed a piece");
        Collection<Piece> beside = AdminManager.piecesIn(level, o.offset(5, -20, -600), o.offset(605, 20, 600));
        check(!beside.contains(wall) && !beside.contains(foundation), "huge zone beside the pieces found them");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void wipeRemovesOnlyPlacablesInsideTheZone(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        check(PlacableHpManager.isTracked(Blocks.CRAFTING_TABLE.defaultBlockState()), "test setup: crafting tables are not placable");
        BlockPos min = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos max = h.absolutePos(new BlockPos(8, 6, 8));
        BlockPos[] inside = {min, h.absolutePos(new BlockPos(5, 4, 7)), max};
        BlockPos outside = h.absolutePos(new BlockPos(9, 2, 2));
        BlockPos stone = h.absolutePos(new BlockPos(3, 3, 3));
        for (BlockPos p : inside) {
            level.setBlock(p, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(outside, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        int removed = AdminManager.removePlacables(level, min, max);
        check(removed == inside.length, "removed " + removed + " placables, expected " + inside.length);
        for (BlockPos p : inside) {
            check(level.getBlockState(p).isAir(), "placable left at " + p.toShortString());
        }
        check(level.getBlockState(outside).is(Blocks.CRAFTING_TABLE), "a placable outside the zone was removed");
        check(level.getBlockState(stone).is(Blocks.STONE), "a block that is not placable was removed");
        h.succeed();
    }
}
