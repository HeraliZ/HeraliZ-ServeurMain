package fr.heraliz.crac.build.test;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.geom.Poly;
import fr.heraliz.crac.build.geom.Prism;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.net.PacketGuard;
import fr.heraliz.crac.build.test.GT.Dir4;
import fr.heraliz.crac.build.world.Occupant;
import fr.heraliz.crac.build.world.PieceSounds;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import fr.heraliz.crac.build.world.StructureIntegrity;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.event.ClaimBlockHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.raid.BlastDamage;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests of the v3 lattice (§12): round bases of squares and equilateral triangles, pieces half a wall up on
 * half walls, L stairs and the half ramp walked by a real entity, rotated walls against blasts and the hammer's line of
 * sight, and doors on rotated walls. The {@code lattice} template is 24 x 12 x 24 blocks; each test lays a stone floor at
 * relative y = 1, so a foundation's slab sits at y = 2 and its walking surface at y = 3.
 */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class LatticeGameTests {
    private static final String LATTICE = "lattice";
    private static final UUID BUILDER = new UUID(0L, 11L);
    /** Vanilla step-up height (0.6 block) in px. */
    private static final double STEP_UP_PX = 9.6;

    private LatticeGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    /** Lays the stone floor over the template and returns the absolute block at relative (x, 3, z). */
    static BlockPos ground(GameTestHelper h, int x, int z) {
        for (int i = 1; i <= 22; i++) {
            for (int j = 1; j <= 22; j++) {
                h.setBlock(i, 1, j, Blocks.STONE);
            }
        }
        return h.absolutePos(new BlockPos(x, 3, z));
    }

    static Piece place(GameTestHelper h, PieceKey key, Tier tier) {
        StructureService.Outcome out = StructureService.placeAdmin(h.getLevel(), key, tier, BUILDER);
        if (!out.ok() || out.piece() == null) {
            throw new GameTestAssertException("could not place " + key.kind().id + " at " + key.anchorBlock().toShortString() + " h" + key.h() + ": " + out.message().getString());
        }
        return out.piece();
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Every block of the piece is a structure block whose block entity lists the piece. */
    static void assertInWorld(GameTestHelper h, Piece p) {
        ServerLevel level = h.getLevel();
        check(!PieceIndex.blocksOf(p).isEmpty(), p.kind().id + " wrote no block");
        for (BlockPos pos : PieceIndex.blocksOf(p)) {
            check(level.getBlockState(pos).getBlock() instanceof StructureBlock, p.kind().id + ": no structure block at " + pos.toShortString());
            boolean listed = level.getBlockEntity(pos) instanceof StructureBlockEntity be
                && be.occupants().stream().map(Occupant::pieceId).anyMatch(id -> id == p.id);
            check(listed, p.kind().id + ": block entity at " + pos.toShortString() + " does not list it");
        }
    }

    /** The piece's base y in world blocks. */
    static double baseY(PieceKey k) {
        return k.origin().getY() + k.baseY() / 16.0;
    }

    /** World point of building px (x, y, z) of a key's building. */
    static Vec3 world(PieceKey k, double x, double y, double z) {
        BlockPos o = k.origin();
        return new Vec3(o.getX() + x / 16.0, o.getY() + y / 16.0, o.getZ() + z / 16.0);
    }

    /**
     * A square foundation at {@code o}, a triangle on one of its sides and a square room (turned by 30°) across the
     * triangle's outer edge of direction {@code dir6}.
     */
    record Rotated(Piece f0, Piece tri, Piece room, int edge) {
        /** A wall on the triangle / room edge: rot 0 puts +n on the triangle side, outside the room. */
        PieceKey wall(PieceKind kind, int h, int rot) {
            return Topology.wallOn(this.tri.key(), this.edge, kind, h, rot);
        }
    }

    /** {@link Rotated} for a wall direction of 30° ({@code dir6} 1) or 60° (2). */
    static Rotated rotated(GameTestHelper h, BlockPos o, int dir6, Tier tier) {
        Piece f0 = place(h, PieceKey.square(PieceKind.FOUNDATION, o, LPoint.ORIGIN, 0, 0, 0), tier);
        for (Dir4 side : Dir4.values()) {
            PieceKey tri = GT.triangleOn(f0.key(), side, PieceKind.TRI_FOUNDATION);
            int edge = GT.outerEdgeWithDir(tri, f0.key(), dir6);
            if (edge < 0) continue;
            Piece t = place(h, tri, tier);
            Piece room = place(h, Topology.across(tri, edge, PieceKind.FOUNDATION, 0), tier);
            return new Rotated(f0, t, room, edge);
        }
        throw new GameTestAssertException("test setup: no triangle on a square has an outer edge of direction " + dir6);
    }

    /** A tiny probe box of collision just under the walking surface at (x, z): true when something solid is there. */
    static boolean solidUnder(ServerLevel level, double x, double y, double z) {
        return !level.noCollision(new AABB(x - 0.01, y - 0.04, z - 0.01, x + 0.01, y - 0.01, z + 0.01));
    }

    // ------------------------------------------------------------------ R1: round bases

    /**
     * R1: an equilateral triangle on each edge of a square, then at each corner a square and a triangle: the 13-piece
     * round base closes exactly (each closing triangle shares an exact edge with the previous corner's triangle), joins
     * one building, and its seams hold collision all along.
     */
    @GameTest(template = LATTICE)
    public static void aRingOfSquaresAndTrianglesClosesExactly(GameTestHelper h) {
        BlockPos o = ground(h, 10, 10);
        ServerLevel level = h.getLevel();
        StructureStore store = StructureStore.get(level);
        PieceKey c = PieceKey.square(PieceKind.FOUNDATION, o, LPoint.ORIGIN, 0, 0, 0);
        List<PieceKey> ring = GT.ringAroundASquare(c);
        check(ring.size() == 13, "test setup: the ring has " + ring.size() + " pieces");
        List<Piece> placed = new ArrayList<>();
        for (PieceKey k : ring) {
            placed.add(place(h, k, Tier.STONE));
        }
        Piece centre = placed.get(0);
        for (Piece p : placed) {
            check(p.buildingId == centre.buildingId, p.kind().id + " at " + p.key().anchorBlock().toShortString() + " started another building");
            assertInWorld(h, p);
        }
        // a triangle on each edge of the square
        for (int i = 1; i <= 4; i++) {
            Piece t = placed.get(i);
            check(t.kind() == PieceKind.TRI_FOUNDATION, "test setup: piece " + i + " is not a triangle");
            check(Topology.foundationsSharingEdge(store.index, t.key()).contains(centre), "triangle " + i + " does not share an edge with the square");
        }
        // every closing triangle meets two pieces edge to edge: its own corner's square and the previous corner's triangle
        for (int i = 0; i < 4; i++) {
            Piece u = placed.get(6 + 2 * i);
            Piece prev = placed.get(1 + (i + 3) % 4);
            List<Piece> neighbours = Topology.foundationsSharingEdge(store.index, u.key());
            check(neighbours.contains(prev) && neighbours.contains(placed.get(5 + 2 * i)) && neighbours.size() == 2,
                "corner " + i + " does not close exactly: its triangle meets " + neighbours.size() + " foundations");
        }
        // no hole along any shared edge: the slab is solid under the walking surface on every seam
        double top = baseY(c);
        int seams = 0;
        for (int i = 0; i < ring.size(); i++) {
            for (int j = i + 1; j < ring.size(); j++) {
                for (EdgeKey e : ring.get(i).edges()) {
                    if (!ring.get(j).edges().contains(e)) continue;
                    seams++;
                    for (int s = 1; s < 8; s++) {
                        double t = s / 8.0;
                        double x = e.start().toX() + t * (e.end().toX() - e.start().toX());
                        double z = e.start().toZ() + t * (e.end().toZ() - e.start().toZ());
                        Vec3 p = world(c, x, 0, z);
                        check(solidUnder(level, p.x, top, p.z), "a hole in the seam of pieces " + i + " and " + j + " at " + p);
                    }
                }
            }
        }
        check(seams == 16, "the ring has " + seams + " shared edges, expected 16");
        h.succeed();
    }

    // ------------------------------------------------------------------ R2: half a wall up

    /**
     * R2: a wall on a half wall and floors (square and triangle) on half walls sit directly on them, 24 px up: no gap at
     * the joint, the wall's top at 72 px, the floors' walking surfaces at 24 px.
     */
    @GameTest(template = LATTICE)
    public static void wallsAndFloorsSitHalfAWallUpOnHalfWalls(GameTestHelper h) {
        BlockPos o = ground(h, 8, 10);
        ServerLevel level = h.getLevel();
        Piece f0 = place(h, PieceKey.square(PieceKind.FOUNDATION, o, LPoint.ORIGIN, 0, 0, 0), Tier.WOOD);
        PieceKey triKey = GT.triangleOn(f0.key(), Dir4.EAST, PieceKind.TRI_FOUNDATION);
        Piece tri = place(h, triKey, Tier.WOOD);
        List<Piece> halves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            halves.add(place(h, Topology.wallOn(f0.key(), i, PieceKind.HALF_WALL, 0, 0), Tier.WOOD));
        }
        for (int i = 0; i < 3; i++) {
            if (f0.key().edges().contains(triKey.edges().get(i))) continue;
            halves.add(place(h, Topology.wallOn(triKey, i, PieceKind.HALF_WALL, 0, 0), Tier.WOOD));
        }
        int vertical = BuildConfig.verticalLoss.get();

        // a wall on the west half wall: its base is the half wall's top
        Piece west = halves.get(3);
        Piece onHalf = place(h, west.key().with(PieceKind.WALL, 0).withH(1), Tier.WOOD);
        check(onHalf.stability == 100 - vertical, "a wall on a half wall has stability " + onHalf.stability);
        Vec3 joint = GT.wallPoint(onHalf.key(), 24, 0, 0);
        check(Math.abs(joint.y - (baseY(f0.key()) + 1.5)) < 1e-9, "the wall on the half wall does not start 24 px up: " + joint.y);
        for (double dy : new double[]{-0.5, -0.1, 0.1, 0.5}) {
            double y = joint.y + dy / 16.0;
            check(!level.noCollision(new AABB(joint.x - 0.02, y - 0.01, joint.z - 0.02, joint.x + 0.02, y + 0.01, joint.z + 0.02)),
                "gap between the half wall and the wall on it at " + dy + " px from the joint");
        }
        double wallTop = GT.columnTop(level, joint.x, joint.z, baseY(f0.key()), baseY(f0.key()) + 6);
        check(Math.abs(wallTop - (baseY(f0.key()) + 4.5)) < 1e-9, "the wall on the half wall ends at " + wallTop + ", expected 72 px up");

        // floors on the half walls: their walking surface is half a wall up
        Piece floor = place(h, PieceKey.on(PieceKind.FLOOR, o, f0.key().footprint(), 1, 0), Tier.WOOD);
        Piece triFloor = place(h, PieceKey.on(PieceKind.TRI_FLOOR, o, triKey.footprint(), 1, 0), Tier.WOOD);
        for (Piece p : List.of(floor, triFloor)) {
            check(p.stability == 100 - vertical, p.kind().id + " on half walls has stability " + p.stability);
            check(p.key().baseY() == 24.0, p.kind().id + " does not sit half a wall up");
            assertInWorld(h, p);
            Vec3 c = p.key().centreWorld();
            double y = GT.columnTop(level, c.x, c.z, baseY(f0.key()) + 0.1, baseY(f0.key()) + 2.9);
            check(Math.abs(y - (baseY(f0.key()) + 1.5)) < 1e-9, p.kind().id + " walking surface at " + y + ", expected 24 px up");
            check(level.noCollision(new AABB(c.x - 0.3, y + 0.01, c.z - 0.3, c.x + 0.3, y + 1.8, c.z + 0.3)), "no room to stand on the " + p.kind().id);
        }
        check(tri.buildingId == floor.buildingId && triFloor.buildingId == floor.buildingId, "the floors joined another building");

        // standing on the half-level floor, mid-block, you hear the floor and not the half walls or the foundation
        StructureService.setTier(level, floor, Tier.METAL);
        Vec3 c = floor.key().centreWorld();
        BlockPos slab = BlockPos.containing(c);
        Player walker = h.makeMockPlayer();
        walker.setPos(c.x, baseY(f0.key()) + 1.5, c.z);
        check(level.getBlockState(slab).getSoundType(level, slab, walker) == PieceSounds.of(Tier.METAL),
            "footsteps on a metal floor half a wall up do not sound like metal");
        h.succeed();
    }

    // ------------------------------------------------------------------ R3: L stairs

    /**
     * R3: L stairs walked by a real entity (vanilla movement, 9.6 px step-up, gravity): from the foundation in front of
     * the first flight to the landing (24 px), then along the second flight onto the floor two half levels up (48 px).
     * Every tread is at most a step-up above the previous one. The stairwell keeps floors out at h + 1..h + 3 and lets
     * one in at h + 4.
     */
    @GameTest(template = LATTICE)
    public static void lStairsAreClimbedStepByStep(GameTestHelper h) {
        BlockPos o = ground(h, 8, 8);
        ServerLevel level = h.getLevel();
        // stairs square at the origin (entry from the south, exit east), the entry square south, the exit square east
        Piece fS = place(h, PieceKey.square(PieceKind.FOUNDATION, o, LPoint.ORIGIN, 0, 0, 0), Tier.STONE);
        PieceKey southKey = Topology.across(fS.key(), GT.edgeIndex(fS.key(), Dir4.SOUTH), PieceKind.FOUNDATION, 0);
        PieceKey eastKey = Topology.across(fS.key(), GT.edgeIndex(fS.key(), Dir4.EAST), PieceKind.FOUNDATION, 0);
        place(h, southKey, Tier.STONE);
        Piece fE = place(h, eastKey, Tier.STONE);
        place(h, Topology.wallOn(eastKey, GT.edgeIndex(eastKey, Dir4.EAST), PieceKind.WALL, 0, 0), Tier.STONE);
        Piece exit = place(h, PieceKey.on(PieceKind.FLOOR, o, eastKey.footprint(), 2, 0), Tier.STONE);
        Piece stairs = place(h, PieceKey.on(PieceKind.STAIRS_L, o, fS.key().footprint(), 0, 0), Tier.STONE);
        check(fE.buildingId == stairs.buildingId && exit.buildingId == stairs.buildingId, "test setup: the pieces joined different buildings");

        // shapes: tread after tread, each one at most a step-up above the previous one
        List<Prism> treads = new ArrayList<>(PieceSolids.solids(stairs.key(), 0, 0L, null));
        treads.sort(Comparator.comparingDouble(Prism::y1));
        check(treads.size() == 9, "L stairs have " + treads.size() + " treads and landing");
        double ground0 = baseY(fS.key());
        double prevPx = 0;
        for (Prism t : treads) {
            double[] c = Poly.centroid(t.poly());
            Vec3 at = world(stairs.key(), c[0], 0, c[1]);
            double top = GT.columnTop(level, at.x, at.z, ground0 - 0.5, ground0 + 3.5);
            double px = (top - ground0) * 16.0;
            check(Math.abs(px - t.y1()) < 1e-6, "the tread at " + at + " is " + px + " px up, expected " + t.y1());
            check(px - prevPx <= STEP_UP_PX + 1e-9, "a rise of " + (px - prevPx) + " px between treads (step-up is 9.6 px)");
            check(level.noCollision(new AABB(at.x - 0.05, top + 0.01, at.z - 0.05, at.x + 0.05, top + 1.8, at.z + 0.05)), "no headroom over the tread at " + px + " px");
            prevPx = px;
        }

        // a real entity walks up: flight 1 along the west side towards north, the landing, flight 2 east to the floor
        Player walker = h.makeMockPlayer();
        Vec3 start = world(stairs.key(), 13, 0, 60);
        walker.setPos(start.x, ground0, start.z);
        Vec3 landing = world(stairs.key(), 13, 0, 13);
        List<Double> ys = GT.walkTo(walker, landing.x, landing.z, 80);
        check(walker.position().distanceTo(new Vec3(landing.x, walker.getY(), landing.z)) < 0.01, "the walker got stuck on the first flight at " + walker.position());
        check(Math.abs(walker.getY() - (ground0 + 1.5)) < 1e-6, "the walker is at " + walker.getY() + " on the landing, expected 24 px up");
        Vec3 out = world(stairs.key(), 64, 0, 13);
        ys.addAll(GT.walkTo(walker, out.x, out.z, 80));
        check(walker.position().distanceTo(new Vec3(out.x, walker.getY(), out.z)) < 0.01, "the walker got stuck on the second flight at " + walker.position());
        check(Math.abs(walker.getY() - (ground0 + 3.0)) < 1e-6, "the walker ended at " + walker.getY() + ", expected on the floor 48 px up");
        double prev = ground0;
        for (double y : ys) {
            check(y - prev <= 0.6 + 1e-9 && y >= prev - 1e-9, "the walker moved from " + prev + " to " + y + " in one tick");
            prev = y;
        }

        // stairwell: floors at h + 1, h + 2 and h + 3 over the stairs are refused, h + 4 is allowed
        int west = GT.edgeIndex(fS.key(), Dir4.WEST);
        int north = GT.edgeIndex(fS.key(), Dir4.NORTH);
        place(h, Topology.wallOn(fS.key(), west, PieceKind.HALF_WALL, 0, 0), Tier.STONE);
        place(h, Topology.wallOn(fS.key(), north, PieceKind.WALL, 0, 0), Tier.STONE);
        place(h, Topology.wallOn(fS.key(), west, PieceKind.WALL, 1, 0), Tier.STONE);
        place(h, Topology.wallOn(fS.key(), north, PieceKind.WALL, 2, 0), Tier.STONE);
        for (int dh = 1; dh <= 3; dh++) {
            StructureService.Outcome above = StructureService.placeAdmin(level, PieceKey.on(PieceKind.FLOOR, o, fS.key().footprint(), dh, 0), Tier.STONE, BUILDER);
            check(!above.ok(), "a floor was placed " + dh + " half levels over the stairs");
            check(fr.heraliz.crac.heraliz.Txt.c(PlacementRules.Result.STAIRWELL.message).getString().equals(above.message().getString()), "a floor at h + " + dh + " over the stairs was refused with \"" + above.message().getString() + "\"");
        }
        place(h, PieceKey.on(PieceKind.FLOOR, o, fS.key().footprint(), 4, 0), Tier.STONE);
        h.succeed();
    }

    /** R2: a half ramp climbs 24 px over one square, from the ground floor onto a floor sitting on half walls. */
    @GameTest(template = LATTICE)
    public static void aHalfRampReachesAHalfLevelFloor(GameTestHelper h) {
        BlockPos o = ground(h, 8, 10);
        ServerLevel level = h.getLevel();
        Piece fR = place(h, PieceKey.square(PieceKind.FOUNDATION, o, LPoint.ORIGIN, 0, 0, 0), Tier.WOOD);
        PieceKey northKey = Topology.across(fR.key(), GT.edgeIndex(fR.key(), Dir4.NORTH), PieceKind.FOUNDATION, 0);
        PieceKey southKey = Topology.across(fR.key(), GT.edgeIndex(fR.key(), Dir4.SOUTH), PieceKind.FOUNDATION, 0);
        place(h, northKey, Tier.WOOD);
        place(h, southKey, Tier.WOOD);
        for (Dir4 d : new Dir4[]{Dir4.NORTH, Dir4.EAST, Dir4.WEST}) {
            place(h, Topology.wallOn(northKey, GT.edgeIndex(northKey, d), PieceKind.HALF_WALL, 0, 0), Tier.WOOD);
        }
        Piece terrace = place(h, PieceKey.on(PieceKind.FLOOR, o, northKey.footprint(), 1, 0), Tier.WOOD);
        // quarter turn 2 climbs towards -v (north), bit 2 makes it a half ramp
        Piece ramp = place(h, PieceKey.on(PieceKind.RAMP, o, fR.key().footprint(), 0, 2 | 4), Tier.WOOD);
        check(ramp.key().topUnits() == 1 && ramp.key().top() == terrace.key().h(), "test setup: the half ramp does not end at the terrace's height");
        double ground0 = baseY(fR.key());
        Vec3 high = world(ramp.key(), 24, 0, 1);
        double rampTop = GT.columnTop(level, high.x, high.z, ground0, ground0 + 2.5);
        check(Math.abs(rampTop - (ground0 + 1.5)) < 1e-9, "the half ramp ends at " + rampTop + ", expected 24 px up");

        Player walker = h.makeMockPlayer();
        Vec3 start = world(ramp.key(), 24, 0, 60);
        walker.setPos(start.x, ground0, start.z);
        Vec3 onTerrace = world(ramp.key(), 24, 0, -24);
        List<Double> ys = GT.walkTo(walker, onTerrace.x, onTerrace.z, 120);
        check(walker.position().distanceTo(new Vec3(onTerrace.x, walker.getY(), onTerrace.z)) < 0.01, "the walker got stuck on the half ramp at " + walker.position());
        check(Math.abs(walker.getY() - (ground0 + 1.5)) < 1e-6, "the walker ended at " + walker.getY() + ", expected on the terrace 24 px up");
        double prev = ground0;
        for (double y : ys) {
            check(y - prev <= 0.6 + 1e-9, "the walker rose " + (y - prev) + " in one tick");
            prev = y;
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ rotated walls: blasts and sight

    /** 30° wall: see {@link #rotatedWallCase}. */
    @GameTest(template = LATTICE)
    public static void aThirtyDegreeWallHidesTheRoomBehindIt(GameTestHelper h) {
        rotatedWallCase(h, 1);
        h.succeed();
    }

    /** 60° wall: see {@link #rotatedWallCase}. */
    @GameTest(template = LATTICE)
    public static void aSixtyDegreeWallHidesTheRoomBehindIt(GameTestHelper h) {
        rotatedWallCase(h, 2);
        h.succeed();
    }

    /**
     * A rotated wall between a triangle (outside) and a rotated square room shares its top row with the floor over the
     * room. A blast outside damages the wall but never the floor; the hammer's line of sight from outside refuses the
     * floor and accepts the wall, and from inside the room accepts both (§7.5, §8).
     */
    private static void rotatedWallCase(GameTestHelper h, int dir6) {
        BlockPos o = ground(h, 6, 10);
        ServerLevel level = h.getLevel();
        Rotated r = rotated(h, o, dir6, Tier.STONE);
        Piece wall = place(h, r.wall(PieceKind.WALL, 0, 0), Tier.STONE);
        Piece floor = place(h, PieceKey.on(PieceKind.FLOOR, o, r.room().key().footprint(), 2, 0), Tier.STONE);
        BlockPos shared = GT.sharedBlock(wall, floor, o.getY() + 2);
        Vec3 n = GT.normal(wall.key());
        Vec3 centre = Vec3.atCenterOf(shared);
        Vec3 onPlane = GT.wallPoint(wall.key(), 24, 0, 40);
        Vec3 beside = new Vec3(centre.x, onPlane.y, centre.z);

        // hammer line of sight
        Vec3 outsideEye = beside.add(n.scale(2.5)).add(0, 0.6, 0);
        Vec3 insideEye = beside.subtract(n.scale(1.5)).subtract(0, 0.9, 0);
        check(PacketGuard.inSight(level, null, outsideEye, shared, wall), dir6 * 30 + "°: the wall is not in sight from outside");
        check(!PacketGuard.inSight(level, null, outsideEye, shared, floor), dir6 * 30 + "°: the floor behind the wall is in sight from outside");
        check(PacketGuard.inSight(level, null, insideEye, shared, wall), dir6 * 30 + "°: the wall is not in sight from inside");
        check(PacketGuard.inSight(level, null, insideEye, shared, floor), dir6 * 30 + "°: the floor is not in sight from under it");

        // an explosion outside the wall, at two thirds of its height: the wall takes it, the floor behind it does not
        int damage = 40;
        Vec3 blast = GT.wallPoint(wall.key(), 24, 40, 32);
        int wallHp = wall.hp;
        int floorHp = floor.hp;
        int roomHp = r.room().hp;
        BlastDamage.splash(level, blast, BlastDamage.sphere(BlockPos.containing(blast), 4), damage, 0, true);
        check(wallHp - wall.hp == damage, dir6 * 30 + "°: the wall facing the blast lost " + (wallHp - wall.hp));
        check(floor.hp == floorHp, dir6 * 30 + "°: a blast outside damaged the floor behind the wall");
        check(r.room().hp == roomHp, dir6 * 30 + "°: a blast outside damaged the room's foundation behind the wall");

        // the same blast inside the room reaches the floor over it
        Vec3 inside = GT.wallPoint(wall.key(), 24, -24, 24);
        floorHp = floor.hp;
        BlastDamage.splash(level, inside, BlastDamage.sphere(BlockPos.containing(inside), 3), damage, 0, true);
        check(floorHp - floor.hp == damage, dir6 * 30 + "°: a blast inside the room did not reach the floor over it");
    }

    /**
     * A rotated wall rasterizes into many small boxes per block, so the 16 sight lines of a blast must be spread over
     * its blocks (nearest points of each block first): a block in front of the wall's nearest bottom blocks must not
     * hide the rows above it, in plain view. A 30° wall on a free-standing triangle, a blast of radius 3 beyond the
     * triangle's far corner, a cobblestone on the triangle between them.
     */
    @GameTest(template = LATTICE)
    public static void aBlastSeesPastABlockerInFrontOfARotatedWall(GameTestHelper h) {
        BlockPos o = ground(h, 6, 6);
        ServerLevel level = h.getLevel();
        place(h, PieceKey.triangle(PieceKind.TRI_FOUNDATION, o, LPoint.ORIGIN, 1, 0), Tier.STONE);
        Piece wall = place(h, PieceKey.wall(PieceKind.WALL, o, LPoint.ORIGIN, 1, 0, 0), Tier.STONE);
        BlockPos blocker = o.offset(1, 0, 2);
        check(level.getBlockState(blocker).isAir() && StructureStore.get(level).index.occupants(blocker).isEmpty(), "test setup: the blocker's block is taken");
        level.setBlock(blocker, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        Vec3 blast = new Vec3(o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 3.5);
        List<BlockPos> range = BlastDamage.sphere(BlockPos.containing(blast), 3);
        boolean upperInSight = false;
        for (BlockPos b : PieceIndex.blocksOf(wall)) {
            if (b.getY() > o.getY() && range.contains(b) && PacketGuard.inSight(level, null, blast, b, wall)) {
                upperInSight = true;
            }
        }
        check(upperInSight, "test setup: no block of the wall above the blocker is in plain view of the blast");
        int hp = wall.hp;
        BlastDamage.splash(level, blast, range, 40, 0, true);
        check(hp - wall.hp == 40, "the wall in plain view above the blocker lost " + (hp - wall.hp) + " instead of 40");
        h.succeed();
    }

    // ------------------------------------------------------------------ R4: doors on rotated walls

    /** Doorway and double doorway on a 30° wall: see {@link #doorPassageCase}. */
    @GameTest(template = LATTICE)
    public static void doorsOnAThirtyDegreeWallLeaveAPassage(GameTestHelper h) {
        doorPassageCase(h, 1);
        h.succeed();
    }

    /** Doorway and double doorway on a 60° wall: see {@link #doorPassageCase}. */
    @GameTest(template = LATTICE)
    public static void doorsOnASixtyDegreeWallLeaveAPassage(GameTestHelper h) {
        doorPassageCase(h, 2);
        h.succeed();
    }

    /**
     * On a rotated wall, a closed door seals the opening (no 9.6 px player box gets through between the jambs and the
     * leaf) and an open one, folded beside the opening, leaves a passage for it (§5.4, §5.9).
     */
    private static void doorPassageCase(GameTestHelper h, int dir6) {
        BlockPos o = ground(h, 6, 10);
        ServerLevel level = h.getLevel();
        Rotated r = rotated(h, o, dir6, Tier.METAL);
        for (PieceKind kind : new PieceKind[]{PieceKind.DOORWAY, PieceKind.DOUBLE_DOORWAY}) {
            for (int rot = 0; rot < 4; rot++) {
                Piece door = place(h, r.wall(kind, 0, rot), Tier.METAL);
                String what = kind.id + " rot " + rot + " on a " + dir6 * 30 + "° wall";
                check(!GT.passage(level, door.key(), 8, 40, 20), what + ": a player gets through the closed door");
                String leak = GT.rayLeak(level, door.key(), 200, 31L * rot + dir6);
                check(leak == null, what + ": a line of sight passes the closed door's collision: " + leak);
                door.setOpen(true);
                StructureService.refresh(level, door);
                check(GT.passage(level, door.key(), 8, 40, 20), what + ": no 9.6 px passage through the open door");
                door.setOpen(false);
                StructureService.refresh(level, door);
                check(!GT.passage(level, door.key(), 8, 40, 20), what + ": a player gets through the door closed again");
                StructureService.remove(level, door, StructureService.Cause.ADMIN);
            }
        }
    }

    // ------------------------------------------------------------------ claims and placables on the lattice

    /** Area of block column (x, z) covered by the footprint of {@code k}, in px². */
    static double coverage(PieceKey k, int x, int z) {
        double x0 = (x - k.origin().getX()) * 16.0;
        double z0 = (z - k.origin().getZ()) * 16.0;
        double[][] c = Poly.clipRect(Footprints.solidPolygon(k), x0, z0, x0 + 16, z0 + 16);
        return c == null ? 0 : Poly.area(c);
    }

    /** Area of the column covered by any of the foundations. */
    static double coverage(List<PieceKey> foundations, int x, int z) {
        double sum = 0;
        for (PieceKey k : foundations) {
            sum += coverage(k, x, z);
        }
        return sum;
    }

    /** The rotated layout plus half walls on the square's three free sides and a floor on them, half a wall up. */
    private static Piece halfLevelFloorOn(GameTestHelper h, Rotated r, Tier tier) {
        PieceKey f0 = r.f0().key();
        for (int i = 0; i < 4; i++) {
            if (r.tri().key().edges().contains(f0.edges().get(i))) continue;
            place(h, Topology.wallOn(f0, i, PieceKind.HALF_WALL, 0, 0), tier);
        }
        return place(h, PieceKey.on(PieceKind.FLOOR, f0.origin(), f0.footprint(), 1, 0), tier);
    }

    /** A column of {@code inside}'s slab whose top face the foundations cover by more than 0 and at most {@code maxArea} px². */
    private static BlockPos sliver(Rotated r, PieceKey inside, double maxArea) {
        List<PieceKey> all = List.of(r.f0().key(), r.tri().key(), r.room().key());
        BlockPos best = null;
        double bestArea = 0;
        for (int[] c : Footprints.columns(inside)) {
            double a = coverage(all, c[0], c[1]);
            if (a > 16 && a <= maxArea && a > bestArea) {
                best = new BlockPos(c[0], inside.origin().getY(), c[1]);
                bestArea = a;
            }
        }
        check(best != null, "test setup: no partly covered block along the " + inside.kind().id + "'s edges");
        return best;
    }

    /** A column of {@code inside}'s slab fully covered by it alone (the block above it is a seat), as a seat position. */
    private static BlockPos fullyCovered(PieceKey inside) {
        for (int[] c : Footprints.columns(inside)) {
            if (coverage(inside, c[0], c[1]) >= 256.0 - 1e-6) {
                return new BlockPos(c[0], inside.origin().getY(), c[1]);
            }
        }
        throw new GameTestAssertException("test setup: no block fully covered by the " + inside.kind().id);
    }

    /**
     * §7.5 seat rule in the world: a Claim Block needs the top face of the block below it fully covered by walkable
     * surfaces flush with it. Refused on a floor half a wall up (its top is mid-block) and on a block cut by a rotated
     * or triangle edge, each with its own reason; accepted on a fully covered block of a foundation, also of a rotated
     * one, and on a block a rotated seam splits between two foundations.
     */
    @GameTest(template = LATTICE, batch = "claim_zone_seat")
    public static void claimBlocksNeedAFullyCoveredFlushSeat(GameTestHelper h) {
        // a batch of its own, away from other tests' buildings: the Claim Block rule looks at the whole claim zone
        GT.clearAround(h, 24, 24);
        BlockPos o = ground(h, 6, 10);
        ServerLevel level = h.getLevel();
        Rotated r = rotated(h, o, 1, Tier.STONE);
        Piece floor = halfLevelFloorOn(h, r, Tier.STONE);

        // the half-level floor: the block over its slab would float 8 px above it
        BlockPos slab = BlockPos.containing(floor.key().centreWorld());
        check(StructureService.store(level).index.occupants(slab).contains(floor), "test setup: the floor's slab is not in " + slab.toShortString());
        check(StructureService.seatUnder(level, slab.above()) == null, "a Claim Block seat was found on a floor half a wall up");
        String why = StructureService.claimProblem(level, slab.above(), 1);
        check(why != null && fr.heraliz.crac.heraliz.Txt.c(why).getString().contains("half a wall up"), "a Claim Block on a half-level floor: \"" + why + "\"");
        check(StructureService.claimProblem(level, slab, 1) != null, "a Claim Block was accepted inside the half-level floor's block");

        // a sliver at the triangle's outer edge
        BlockPos cut = sliver(r, r.tri().key(), 192);
        check(StructureService.seatUnder(level, cut) == null, "a Claim Block seat was found on a partly covered block at " + cut.toShortString());
        why = StructureService.claimProblem(level, cut, 1);
        check(why != null && fr.heraliz.crac.heraliz.Txt.c(why).getString().contains("fully covered"), "a Claim Block on a sliver block: \"" + why + "\"");

        // fully covered blocks: the square, the rotated room, and a block the rotated seam shares between two foundations
        BlockPos onSquare = GT.seat(r.f0());
        check(StructureService.seatUnder(level, onSquare) == r.f0(), "no seat on the square foundation's centre");
        check(StructureService.claimProblem(level, onSquare, 1) == null, "a Claim Block was refused on the square foundation: " + StructureService.claimProblem(level, onSquare, 1));
        BlockPos onRoom = fullyCovered(r.room().key());
        check(StructureService.seatUnder(level, onRoom) == r.room(), "no seat on a fully covered block of the rotated room at " + onRoom.toShortString());
        check(StructureService.claimProblem(level, onRoom, 1) == null, "a Claim Block was refused on the rotated room: " + StructureService.claimProblem(level, onRoom, 1));
        List<PieceKey> pair = List.of(r.tri().key(), r.room().key());
        for (int[] c : Footprints.columns(r.room().key())) {
            double own = coverage(r.room().key(), c[0], c[1]);
            if (own > 16 && own < 240 && coverage(pair, c[0], c[1]) >= 256.0 - 1e-6) {
                BlockPos seam = new BlockPos(c[0], o.getY(), c[1]);
                Piece seat = StructureService.seatUnder(level, seam);
                check(seat == r.room() || seat == r.tri(), "no seat on the block the rotated seam splits at " + seam.toShortString());
            }
        }

        // the whole Claim Block rule (group, zone) agrees on the square
        GroupData groups = GroupData.get(level.getServer());
        Player owner = h.makeMockPlayer();
        Group g = groups.create("gametest", "GTS3", 0xFFFFFF, owner.getUUID());
        check(g != null, "test setup: could not create a group");
        try {
            String problem = ClaimBlockHandler.placementProblem(level, owner, g, onSquare);
            check(problem == null, "the Claim Block rule refused the square foundation: " + problem);
            check(ClaimBlockHandler.placementProblem(level, owner, g, slab.above()) != null, "the Claim Block rule accepted the half-level floor");
            check(ClaimBlockHandler.placementProblem(level, owner, g, cut) != null, "the Claim Block rule accepted a sliver block");
        } finally {
            groups.disband(g);
        }
        h.succeed();
    }

    /** A server player that keeps the messages shown to it. */
    private static final class Listener extends FakePlayer {
        final List<String> messages = new ArrayList<>();

        Listener(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "gt-builder"));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            this.messages.add(message.getString());
        }
    }

    /** Places the main-hand item against the top face of {@code against}, as a vanilla right click does. */
    private static void placeOnTop(Player player, BlockPos against) {
        Vec3 hit = Vec3.atCenterOf(against).add(0, 0.5, 0);
        ForgeHooks.onPlaceItemIntoWorld(new UseOnContext(player,
            InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, against, false)));
    }

    /**
     * Placables need a full top face under them (§7.5): on a floor half a wall up (its top is mid-block) and on a block
     * cut by a rotated edge they are refused, each with a message that says why; on a fully covered block of the rotated
     * room they stand.
     */
    @GameTest(template = LATTICE)
    public static void placablesSayWhyAHalfLevelFloorCannotHoldThem(GameTestHelper h) {
        BlockPos o = ground(h, 6, 10);
        ServerLevel level = h.getLevel();
        if (!Config.placablesNeedSupport.get()) {
            h.succeed();
            return;
        }
        check(PlacableSet.contains(level, Blocks.CRAFTING_TABLE), "test setup: crafting tables are not placables");
        Rotated r = rotated(h, o, 1, Tier.STONE);
        Piece floor = halfLevelFloorOn(h, r, Tier.STONE);
        Listener member = new Listener(level);
        Vec3 stand = r.f0().key().centreWorld();
        member.moveTo(stand.x, o.getY(), stand.z, 0.0F, 0.0F);
        GroupData groups = GroupData.get(level.getServer());
        Group g = groups.create("gametest", "GTP3", 0xFFFFFF, member.getUUID());
        check(g != null, "test setup: could not create a group");
        // anchored on the bare stone, not on the building: a linked building would keep the group as its owner after the
        // claim goes, and other tests nearby would meet "another group's building" in their claim zones
        BlockPos anchor = h.absolutePos(new BlockPos(2, 2, 12));
        ClaimManager.addClaim(level, anchor, g.id(), 1);
        try {
            // on the half-level floor
            BlockPos slab = BlockPos.containing(floor.key().centreWorld());
            member.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
            placeOnTop(member, slab);
            check(level.getBlockState(slab.above()).isAir(), "a placable was placed half a block above a half-level floor");
            check(member.messages.stream().anyMatch(m -> m.contains("half a wall up")), "no clear refusal on the half-level floor: " + member.messages);

            // on a block the triangle's edge cuts
            member.messages.clear();
            BlockPos cut = sliver(r, r.tri().key(), 192);
            member.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
            placeOnTop(member, cut.below());
            check(level.getBlockState(cut).isAir(), "a placable was placed on a partly covered block at " + cut.toShortString());
            check(member.messages.stream().anyMatch(m -> m.contains("fully covered")), "no clear refusal on a sliver block: " + member.messages);

            // on a fully covered block of the rotated room
            member.messages.clear();
            BlockPos full = fullyCovered(r.room().key());
            member.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
            placeOnTop(member, full.below());
            check(level.getBlockState(full).is(Blocks.CRAFTING_TABLE), "a placable was refused on a fully covered block of the rotated room: " + member.messages);
            level.removeBlock(full, false);
            PlacableHpManager.clear(level, full);
        } finally {
            if (ClaimManager.getByGroup(level, g.id()) != null) {
                ClaimManager.removeByGroup(level, g.id());
            }
            groups.disband(g);
        }
        h.succeed();
    }

    /**
     * Player rule 14 on roofs: a 2 x 2 merged roof on half walls (h = 1) has a flat plateau at 48 px, a block boundary,
     * over whole blocks whose collision makes their top face sturdy. A placable is still refused there (only foundations
     * and floors hold placables), and one standing there anyway (placed before the rule) falls when the roof goes.
     */
    @GameTest(template = LATTICE)
    public static void placablesNeverStandOnRoofs(GameTestHelper h) {
        BlockPos o = ground(h, 6, 10);
        ServerLevel level = h.getLevel();
        if (!Config.placablesNeedSupport.get()) {
            h.succeed();
            return;
        }
        Piece f0 = place(h, PieceKey.square(PieceKind.FOUNDATION, o, LPoint.ORIGIN, 0, 0, 0), Tier.STONE);
        List<Piece> squares = new ArrayList<>(List.of(f0));
        squares.add(place(h, Topology.across(f0.key(), GT.edgeIndex(f0.key(), Dir4.EAST), PieceKind.FOUNDATION, 0), Tier.STONE));
        squares.add(place(h, Topology.across(f0.key(), GT.edgeIndex(f0.key(), Dir4.SOUTH), PieceKind.FOUNDATION, 0), Tier.STONE));
        squares.add(place(h, Topology.across(squares.get(1).key(), GT.edgeIndex(squares.get(1).key(), Dir4.SOUTH), PieceKind.FOUNDATION, 0), Tier.STONE));
        List<PieceKey> keys = squares.stream().map(Piece::key).toList();
        for (PieceKey k : keys) {
            for (int i = 0; i < 4; i++) {
                EdgeKey e = k.edges().get(i);
                if (keys.stream().filter(q -> q.edges().contains(e)).count() == 1) {
                    place(h, Topology.wallOn(k, i, PieceKind.HALF_WALL, 0, 0), Tier.STONE);
                }
            }
        }
        Piece roof = null;
        for (PieceKey k : keys) {
            Piece r = place(h, PieceKey.on(PieceKind.ROOF, o, k.footprint(), 1, 0), Tier.STONE);
            if (k.equals(f0.key())) roof = r;
        }
        // the plateau: building px [32, 48] in x and z, top at 48 px, i.e. the block row o.y + 2 under the row o.y + 3
        BlockPos plateau = o.offset(2, 2, 2);
        BlockPos top = plateau.above();
        check(holds(level, plateau, roof), "test setup: the roof is not in " + plateau.toShortString());
        check(level.getBlockState(plateau).isFaceSturdy(level, plateau, Direction.UP), "test setup: the roof plateau is not a sturdy top face");
        check(level.getBlockState(top).isAir(), "test setup: the block over the plateau is taken");
        Listener member = new Listener(level);
        member.moveTo(o.getX() + 3.0, o.getY() + 3, o.getZ() + 3.0, 0.0F, 0.0F);
        GroupData groups = GroupData.get(level.getServer());
        Group g = groups.create("gametest", "GTP4", 0xFFFFFF, member.getUUID());
        check(g != null, "test setup: could not create a group");
        BlockPos anchor = h.absolutePos(new BlockPos(2, 2, 12));
        ClaimManager.addClaim(level, anchor, g.id(), 1);
        try {
            member.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
            placeOnTop(member, plateau);
            check(level.getBlockState(top).isAir(), "a placable was placed on a roof");
            check(member.messages.stream().anyMatch(m -> m.contains("foundation or a floor")), "no clear refusal on a roof: " + member.messages);

            // a placable already there falls with the roof under it
            level.setBlock(top, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
            StructureService.remove(level, roof, StructureService.Cause.DEMOLISH);
            check(!level.getBlockState(top).is(Blocks.CRAFTING_TABLE), "a placable kept floating where a roof was");
        } finally {
            level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            PlacableHpManager.clear(level, top);
            if (ClaimManager.getByGroup(level, g.id()) != null) {
                ClaimManager.removeByGroup(level, g.id());
            }
            groups.disband(g);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ integrity with rotated pieces

    private static boolean holds(ServerLevel level, BlockPos pos, Piece p) {
        return level.getBlockEntity(pos) instanceof StructureBlockEntity be && be.occupants().stream().anyMatch(o -> o.pieceId() == p.id);
    }

    private static boolean exists(ServerLevel level, Piece p) {
        return StructureStore.get(level).index.piece(p.id) != null;
    }

    /** Makes a block disappear without any block callback, as a region rollback or a lost chunk save would. */
    private static void vanish(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.removeBlockEntity(pos);
        chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, Blocks.AIR.defaultBlockState());
    }

    private static void heal(ServerLevel level, BlockPos pos) {
        StructureIntegrity.queueChunk(level, new ChunkPos(pos));
        StructureService.tick(level);
        StructureService.tick(level);
    }

    /**
     * The self-heal on rotated pieces: a block a 30° wall shares with the floor over the rotated room gets both records
     * back, a stale record in the rotated room's slab is rewritten; a vanished block of the wall drops the wall and the
     * floor resting on it; the rotated room's foundation stands while one of its columns rests on ground and falls with
     * the last one, leaving the square and the triangle.
     */
    @GameTest(template = LATTICE)
    public static void selfHealWorksOnRotatedPieces(GameTestHelper h) {
        BlockPos o = ground(h, 6, 10);
        ServerLevel level = h.getLevel();
        Rotated r = rotated(h, o, 1, Tier.WOOD);
        Piece wall = place(h, r.wall(PieceKind.WALL, 0, 0), Tier.WOOD);
        Piece floor = place(h, PieceKey.on(PieceKind.FLOOR, o, r.room().key().footprint(), 2, 0), Tier.WOOD);

        BlockPos shared = GT.sharedBlock(wall, floor, o.getY() + 2);
        ((StructureBlockEntity) level.getBlockEntity(shared)).setOccupants(List.of());
        heal(level, shared);
        check(holds(level, shared, wall) && holds(level, shared, floor), "the shared block of the rotated wall and the floor was not rewritten");
        BlockPos slabBlock = fullyCovered(r.room().key()).below();
        Occupant right = Occupant.of(r.room());
        Occupant stale = new Occupant(right.pieceId(), right.buildingId(), right.key(), Tier.ARMORED, right.state(), right.variant(), 3);
        ((StructureBlockEntity) level.getBlockEntity(slabBlock)).setOccupants(List.of(stale));
        // a stale record (older tier and crack) is what a block entity reloaded with its chunk can hold: the block side
        // check that runs on load compares whole records
        StructureService.queueVerify(level, slabBlock);
        StructureService.tick(level);
        check(level.getBlockEntity(slabBlock) instanceof StructureBlockEntity be && be.occupants().equals(List.of(Occupant.of(r.room()))),
            "a stale record in the rotated room's slab was not rewritten");

        // a block of the wall vanishes: the wall goes, and the floor that rested only on it
        BlockPos low = GT.wallBlock(wall, 24, 0, 8);
        check(!StructureService.store(level).index.occupants(low).contains(floor), "test setup: the floor shares the wall's bottom block");
        vanish(level, low);
        heal(level, low);
        check(!exists(level, wall), "a rotated wall with a vanished block is still in the store");
        check(!exists(level, floor), "the floor is still held up by a rotated wall that lost a block");
        check(exists(level, r.room()) && exists(level, r.tri()) && exists(level, r.f0()), "a foundation fell with the wall");

        // the rotated room keeps standing on one column of ground, and falls with the last one
        PieceKey room = r.room().key();
        List<int[]> cols = Footprints.columns(room);
        List<int[]> others = new ArrayList<>(Footprints.columns(r.tri().key()));
        others.addAll(Footprints.columns(r.f0().key()));
        int keep = -1;
        double far = -1;
        Vec3 tc = r.tri().key().centreWorld();
        for (int i = 0; i < cols.size(); i++) {
            int[] c = cols.get(i);
            boolean sharedColumn = others.stream().anyMatch(q -> q[0] == c[0] && q[1] == c[1]);
            double d = new Vec3(c[0] + 0.5, 0, c[1] + 0.5).distanceToSqr(tc.x, 0, tc.z);
            if (!sharedColumn && d > far) {
                far = d;
                keep = i;
            }
        }
        check(keep >= 0, "test setup: every column of the rotated room is shared");
        int slabY = PieceSolids.slabRow(room);
        for (int i = 0; i < cols.size(); i++) {
            if (i == keep) continue;
            BlockPos under = new BlockPos(cols.get(i)[0], slabY - r.room().skirt[i] - 1, cols.get(i)[1]);
            level.setBlock(under, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        StructureService.tick(level);
        check(exists(level, r.room()), "the rotated room fell although one of its columns still rests on ground");
        BlockPos last = new BlockPos(cols.get(keep)[0], slabY - r.room().skirt[keep] - 1, cols.get(keep)[1]);
        level.setBlock(last, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        StructureService.tick(level);
        check(!exists(level, r.room()), "the rotated room still stands with no ground under any column");
        check(exists(level, r.tri()) && exists(level, r.f0()), "the triangle or the square fell with the rotated room");
        h.succeed();
    }

    /**
     * §7.6: a structure block still holding v2 occupant data keeps it untouched (no v3 occupant is read from it). The
     * test world's store never saw a v2 file, as a store standing in for an unreadable or missing one: the block is
     * kept, counts as an unknown piece and takes no new piece. Only once the old data was explicitly discarded
     * ({@code discardOldBuildings}) does the self-heal remove it. The stand-in for the old file itself (old file, no
     * flag) is covered by {@code StructureStoreTest}.
     */
    @GameTest(template = LATTICE)
    public static void oldStructureBlocksStayUntilTheOldDataIsDiscarded(GameTestHelper h) {
        ground(h, 6, 10);
        ServerLevel level = h.getLevel();
        StructureStore store = StructureStore.get(level);
        check(!store.holdsOldData() && !store.discardedOldData(), "test setup: the test world's store saw old data");
        BlockPos pos = h.absolutePos(new BlockPos(20, 2, 20));
        level.setBlock(pos, ModBlocks.STRUCTURE.get().defaultBlockState(), Block.UPDATE_ALL);
        StructureBlockEntity be = (StructureBlockEntity) level.getBlockEntity(pos);
        CompoundTag tag = be.saveWithoutMetadata();
        tag.remove("V");
        tag.remove("Occ3");
        tag.putLongArray("Occ", new long[]{7L, 1L, 0x1234L});
        be.load(tag);
        check(be.hasLegacyData() && be.occupants().isEmpty(), "v2 data was read as v3 occupants or dropped");
        check(be.saveWithoutMetadata().getLongArray("Occ").length == 3, "v2 data was not kept untouched");
        check(be.shape().isEmpty(), "a block holding only v2 data has a collision shape");
        StructureService.queueVerify(level, pos);
        StructureService.tick(level);
        check(level.getBlockEntity(pos) instanceof StructureBlockEntity kept && kept.hasLegacyData(),
            "an old structure block was erased although the old data was never discarded");
        check(StructureIntegrity.holdsUnknownPieces(level, pos), "kept v2 data does not count as an unknown piece");
        PieceKey over = GT.surface(level, PieceKind.FOUNDATION, h.absolutePos(new BlockPos(18, 3, 18)));
        check(PieceSolids.reservedBlocks(over, null).contains(pos), "test setup: the foundation does not cover the old block");
        StructureService.Outcome out = StructureService.placeAdmin(level, over, Tier.TWIG, BUILDER);
        check(!out.ok() && out.message().getString().equals(fr.heraliz.crac.heraliz.Txt.c(PlacementRules.Result.OTHER_BUILDING.message).getString()),
            "a foundation was built into a block holding v2 data: " + out.message().getString());
        check(((StructureBlockEntity) level.getBlockEntity(pos)).hasLegacyData(), "placing a piece dropped the v2 data");
        try {
            store.markOldDataDiscarded(true);
            StructureService.queueVerify(level, pos);
            StructureService.tick(level);
            check(level.getBlockState(pos).isAir(), "an old structure block was kept after the old data was discarded");
        } finally {
            store.markOldDataDiscarded(false);
        }
        h.succeed();
    }
}
