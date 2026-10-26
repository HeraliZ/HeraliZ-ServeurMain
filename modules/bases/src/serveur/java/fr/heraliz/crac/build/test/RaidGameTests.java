package fr.heraliz.crac.build.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.test.GT.Dir4;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.entity.C4Entity;
import fr.heraliz.crac.entity.Fuse;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.placable.PlacableHpData;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.raid.BlastDamage;
import fr.heraliz.crac.raid.PlacedTerrainData;
import fr.heraliz.crac.raid.RaidBlocks;
import fr.heraliz.crac.registry.ModBlocks;
import fr.heraliz.crac.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests of raid damage: sight lines of charges and blasts, armed explosives across a save, ordinary blocks
 * that raids may break inside claims (never the ground of a foundation; placables standing on them fall), and the
 * block rules. Same layout as {@link BuildGameTests}: a stone floor at relative y = 1, the first
 * foundation's walking surface at y = 3.
 */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class RaidGameTests {
    private static final String EMPTY = "empty";
    private static final UUID BUILDER = new UUID(0L, 9L);

    private RaidGameTests() {
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

    private static Piece place(GameTestHelper h, PieceKey key, Tier tier) {
        StructureService.Outcome out = StructureService.placeAdmin(h.getLevel(), key, tier, BUILDER);
        if (!out.ok() || out.piece() == null) {
            throw new GameTestAssertException("could not place " + key.kind().id + " at " + key.anchorBlock().toShortString() + " h" + key.h() + ": " + out.message().getString());
        }
        return out.piece();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static int lost(Piece p, int before) {
        return before - p.hp;
    }

    /** A group claiming the building under {@code claimPos} (the Claim Block stands there); undo with {@link #unclaim}. */
    private static Group claim(ServerLevel level, BlockPos claimPos) {
        Group group = GroupData.get(level.getServer()).create("raidtest", "GTR", 0xFFFFFF, UUID.randomUUID());
        check(group != null, "could not create a group");
        set(level, claimPos, ModBlocks.CLAIM_BLOCK.get());
        ClaimManager.addClaim(level, claimPos, group.id(), 1);
        return group;
    }

    private static void unclaim(ServerLevel level, Group group, BlockPos claimPos) {
        if (level.getBlockState(claimPos).is(ModBlocks.CLAIM_BLOCK.get())) {
            level.removeBlock(claimPos, false);
        }
        if (ClaimManager.getByGroup(level, group.id()) != null) {
            ClaimManager.removeByGroup(level, group.id());
        }
        GroupData.get(level.getServer()).disband(group);
    }

    // ------------------------------------------------------------------ sight lines

    @GameTest(template = EMPTY)
    public static void chargeDoesNotReachThroughThePanel(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        Piece wall = place(h, GT.wall(level, PieceKind.WALL, o, Dir4.NORTH, 0), Tier.STONE);
        Piece ceiling = place(h, GT.surface(level, PieceKind.FLOOR, o.above(3)), Tier.STONE);
        Piece upper = place(h, GT.wall(level, PieceKind.WALL, o.above(3), Dir4.NORTH, 0), Tier.STONE);
        int damage = 50;

        // stuck to the outer face of the wall's top row: the ceiling shares that row, but behind the panel
        BlockPos outer = GT.wallBlock(wall, 24, -1, 40);
        Vec3 hit = GT.wallPoint(wall.key(), 24, -PieceSolids.WALL_HALF, 40);
        check(StructureService.pieceAt(level, outer, hit) == wall, "test setup: the outer face of the wall is not at " + hit);
        int wallHp = wall.hp;
        int ceilingHp = ceiling.hp;
        int upperHp = upper.hp;
        BlastDamage.charge(level, outer, hit, hit.add(0.0, 0.0, -0.001), damage);
        check(lost(wall, wallHp) == damage, "the wall the charge is stuck to lost " + lost(wall, wallHp));
        check(lost(ceiling, ceilingHp) == 0, "a charge outside the wall damaged the ceiling of the room behind it");
        check(lost(upper, upperHp) == damage, "the upper wall's outer face is in sight of the charge, it lost " + lost(upper, upperHp));

        // stuck to the inner face of the same row: the ceiling is in sight, the upper wall is behind the ceiling
        BlockPos inner = GT.wallBlock(wall, 24, 1, 40);
        Vec3 innerHit = GT.wallPoint(wall.key(), 24, PieceSolids.WALL_HALF, 40);
        check(StructureService.pieceAt(level, inner, innerHit) == wall, "test setup: the inner face of the wall is not at " + innerHit);
        wallHp = wall.hp;
        ceilingHp = ceiling.hp;
        upperHp = upper.hp;
        BlastDamage.charge(level, inner, innerHit, innerHit.add(0.0, 0.0, 0.001), damage);
        check(lost(wall, wallHp) == damage, "the wall lost " + lost(wall, wallHp) + " from inside");
        check(lost(ceiling, ceilingHp) == damage, "the ceiling above a charge inside the room lost " + lost(ceiling, ceilingHp));
        check(lost(upper, upperHp) == 0, "a charge under the ceiling damaged the wall standing on it");
        h.succeed();
    }

    /**
     * Where a charge thrown at {@code wall} sticks in the block {@code pos}, coming from the wall's +n side ({@code side}
     * 1) or its -n side (-1) at the block's mid-height: the hit point on the wall and, as the charge sets it, the blast
     * origin just off that face. The line aims at the wall's centre plane inside the block.
     */
    private static Vec3[] stickTo(ServerLevel level, BlockPos pos, Piece wall, double side) {
        Vec3 n = GT.wallPoint(wall.key(), 24, 1, 0).subtract(GT.wallPoint(wall.key(), 24, 0, 0)).scale(16.0);
        Vec3 w0 = GT.wallPoint(wall.key(), 24, 0, 0);
        Vec3 c = Vec3.atCenterOf(pos);
        // the block centre projected on the wall's centre plane
        Vec3 onPlane = c.subtract(n.scale(c.subtract(w0).dot(n)));
        Vec3 from = onPlane.add(n.scale(side * 1.5));
        Vec3 to = onPlane.subtract(n.scale(side * 0.05));
        BlockHitResult hit = level.getBlockState(pos).getCollisionShape(level, pos).clip(from, to, pos);
        check(hit != null, "test setup: nothing to stick to in " + pos.toShortString());
        check(StructureService.pieceAt(level, pos, hit.getLocation()) == wall, "test setup: the charge does not stick to the wall at " + hit.getLocation());
        return new Vec3[]{hit.getLocation(), hit.getLocation().add(n.scale(side * 0.001))};
    }

    /**
     * RULES-07 on the lattice: a 30° wall between a triangle (outside) and a square turned by 30° (the room) shares blocks
     * with the ramp and the floor of the room. A charge on the wall's outer face never reaches them; from inside, the
     * floor over the room is in sight.
     */
    @GameTest(template = EMPTY)
    public static void chargeOnARotatedWallSparesWhatIsBehindIt(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        StructureStore store = StructureStore.get(level);
        Piece f0 = place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        PieceKey tri = GT.triangleOn(f0.key(), Dir4.EAST, PieceKind.TRI_FOUNDATION);
        place(h, tri, Tier.STONE);
        int edge = -1;
        for (int i = 0; i < 3; i++) {
            if (tri.edgeDir(i) % 6 == 1) {
                edge = i;
            }
        }
        check(edge >= 0, "test setup: the triangle has no 30 degree edge");
        PieceKey roomKey = Topology.across(tri, edge, PieceKind.FOUNDATION, 0);
        place(h, roomKey, Tier.STONE);
        // the wall's +n side (rot 0 on the triangle's edge) is the triangle: the outside of the room
        Piece wall = place(h, Topology.wallOn(tri, edge, PieceKind.WALL, 0, 0), Tier.STONE);
        Piece ramp = place(h, PieceKey.on(PieceKind.RAMP, roomKey.origin(), roomKey.footprint(), 0, 0), Tier.STONE);
        int damage = 50;

        BlockPos mid = GT.sharedBlock(wall, ramp, o.getY() + 1);
        check(store.index.occupants(mid).contains(ramp), "test setup: the ramp does not share the wall's block");
        Vec3[] outer = stickTo(level, mid, wall, 1.0);
        check(BlockPos.containing(outer[1]).equals(mid), "test setup: the blast is not inside the shared block");
        int wallHp = wall.hp;
        int rampHp = ramp.hp;
        BlastDamage.charge(level, mid, outer[0], outer[1], damage);
        check(lost(wall, wallHp) == damage, "the rotated wall the charge is stuck to lost " + lost(wall, wallHp));
        check(lost(ramp, rampHp) == 0, "a charge outside the rotated wall damaged the ramp behind it");

        // a floor over the room rests on the wall's top row
        StructureService.remove(level, ramp, StructureService.Cause.ADMIN);
        Piece floor = place(h, PieceKey.on(PieceKind.FLOOR, roomKey.origin(), roomKey.footprint(), 2, 0), Tier.STONE);
        BlockPos top = GT.sharedBlock(wall, floor, o.getY() + 2);
        Vec3[] outerTop = stickTo(level, top, wall, 1.0);
        wallHp = wall.hp;
        int floorHp = floor.hp;
        BlastDamage.charge(level, top, outerTop[0], outerTop[1], damage);
        check(lost(wall, wallHp) == damage, "the rotated wall lost " + lost(wall, wallHp) + " to a charge on its top row");
        check(lost(floor, floorHp) == 0, "a charge outside the rotated wall damaged the floor over the room behind it");

        // stuck on the inner face of the same block, the floor of the room is in sight
        Vec3[] innerTop = stickTo(level, top, wall, -1.0);
        floorHp = floor.hp;
        BlastDamage.charge(level, top, innerTop[0], innerTop[1], damage);
        check(lost(floor, floorHp) == damage, "a charge inside the room did not hit the floor above it: lost " + lost(floor, floorHp));
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void blastsDoNotReachThroughWalls(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece foundation = place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        Piece north = place(h, GT.wall(level, PieceKind.WALL, o, Dir4.NORTH, 0), Tier.STONE);
        Piece south = place(h, GT.wall(level, PieceKind.WALL, o, Dir4.SOUTH, 0), Tier.STONE);
        Piece east = place(h, GT.wall(level, PieceKind.WALL, o, Dir4.EAST, 0), Tier.STONE);
        Piece west = place(h, GT.wall(level, PieceKind.WALL, o, Dir4.WEST, 0), Tier.STONE);
        Piece ceiling = place(h, GT.surface(level, PieceKind.FLOOR, o.above(3)), Tier.STONE);
        BlockPos table = GT.seat(foundation);
        set(level, table, Blocks.CRAFTING_TABLE);
        check(PlacableHpManager.isTracked(level.getBlockState(table)), "test setup: crafting tables are not placable");
        int damage = 40;
        int tableMax = PlacableHpManager.maxHpFor(Blocks.CRAFTING_TABLE);

        // outside the north wall, at mid-height: whatever is behind that wall is out of sight
        Vec3 outside = GT.wallPoint(north.key(), 24, -24, 24);
        int[] before = {foundation.hp, north.hp, south.hp, ceiling.hp};
        BlastDamage.splash(level, outside, BlastDamage.sphere(BlockPos.containing(outside), 4), damage, 0, true);
        check(lost(north, before[1]) == damage, "the wall facing the blast lost " + lost(north, before[1]));
        check(lost(foundation, before[0]) == 0, "the blast hit the foundation through the wall");
        check(lost(south, before[2]) == 0, "the blast hit the far wall through the room");
        check(lost(ceiling, before[3]) == 0, "the blast hit the ceiling through the wall");
        check(PlacableHpData.get(level).getHp(table, tableMax) == tableMax, "the blast hit the crafting table inside the room");

        // inside the room: every piece around it is in sight, once each
        level.removeBlock(table, false);
        PlacableHpManager.clear(level, table);
        Vec3 inside = GT.localPoint(foundation.key(), 24, 24, 24);
        Piece[] all = {foundation, north, south, east, west, ceiling};
        int[] hp = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            hp[i] = all[i].hp;
        }
        BlastDamage.splash(level, inside, BlastDamage.sphere(BlockPos.containing(inside), 2), damage, 0, true);
        for (int i = 0; i < all.length; i++) {
            check(lost(all[i], hp[i]) == damage, all[i].kind().id + " " + all[i].key().anchorBlock().toShortString() + " lost " + lost(all[i], hp[i]) + " to a blast inside the room");
        }

        // a real TNT blast on the ground outside: only the north wall is in range and in sight, no block breaks
        int generic = Config.genericExplosionDamage.get();
        Vec3 tnt = GT.wallPoint(north.key(), 24, -24, -15);
        BlockPos floorBelow = BlockPos.containing(tnt).below();
        check(level.getBlockState(floorBelow).is(Blocks.STONE), "test setup: no stone under the TNT");
        int northHp = north.hp;
        int ceilingHp = ceiling.hp;
        level.explode(null, tnt.x, tnt.y, tnt.z, 4.0F, Level.ExplosionInteraction.TNT);
        check(lost(north, northHp) == generic, "TNT took " + lost(north, northHp) + " off the wall, expected genericDamage " + generic);
        check(lost(ceiling, ceilingHp) == 0, "TNT outside hit the ceiling");
        check(level.getBlockState(floorBelow).is(Blocks.STONE), "TNT outside any claim broke a world block");
        h.succeed();
    }

    // ------------------------------------------------------------------ armed explosives

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void armedC4SurvivesASaveAndGoesOff(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        Piece wall = place(h, GT.wall(level, PieceKind.WALL, o, Dir4.NORTH, 0), Tier.STONE);
        int wallHp = wall.hp;
        BlockPos outer = GT.wallBlock(wall, 24, -1, 24);
        Vec3 hit = GT.wallPoint(wall.key(), 24, -PieceSolids.WALL_HALF, 24);

        C4Entity c4 = new C4Entity(ModEntities.C4.get(), level);
        c4.setPos(hit.x, hit.y, hit.z - 0.5);
        c4.stickTo(outer, hit, Direction.NORTH);
        check(level.addFreshEntity(c4), "test setup: the C4 was not added");
        int fuse = Fuse.ticks(Config.c4FuseSeconds.get());
        check(c4.isStuck() && c4.getFuseLength() == fuse, "fuse length " + c4.getFuseLength() + ", expected " + fuse);
        check(c4.getFuseLength() <= 300, "test setup: c4.fuseSeconds is too long for this test");

        // an armed charge saved before the stuck state was kept: it falls instead of hanging forever
        CompoundTag legacy = c4.saveWithoutId(new CompoundTag());
        legacy.remove("Stuck");
        C4Entity dud = new C4Entity(ModEntities.C4.get(), level);
        dud.load(legacy);
        check(!dud.isStuck() && !dud.isNoGravity(), "a legacy armed charge still hangs in the air");

        C4Entity[] reloaded = new C4Entity[1];
        h.runAfterDelay(20, () -> {
            int burnt = c4.getFuseTicks();
            check(burnt > 0 && c4.isAlive(), "the fuse does not burn: " + burnt);
            CompoundTag saved = c4.saveWithoutId(new CompoundTag());
            c4.discard();
            C4Entity copy = new C4Entity(ModEntities.C4.get(), level);
            copy.load(saved);
            copy.setUUID(UUID.randomUUID());
            check(copy.isStuck(), "reloaded C4 is not stuck");
            check(outer.equals(copy.getTargetPos()) && hit.equals(copy.getTargetHit()) && copy.getStuckFace() == Direction.NORTH,
                "reloaded C4 lost its target: " + copy.getTargetPos() + " " + copy.getTargetHit() + " " + copy.getStuckFace());
            check(copy.getFuseTicks() == burnt && copy.getFuseLength() == fuse, "reloaded fuse " + copy.getFuseTicks() + "/" + copy.getFuseLength());
            check(Math.abs(copy.fuseElapsedSeconds(0.0F) - burnt / 20.0) < 1.0E-6, "synced fuse start is off: " + copy.fuseElapsedSeconds(0.0F));
            check(level.addFreshEntity(copy), "reloaded C4 was not added");
            reloaded[0] = copy;
        });
        h.succeedWhen(() -> {
            check(reloaded[0] != null && reloaded[0].isRemoved(), "the reloaded C4 has not gone off yet");
            check(lost(wall, wallHp) == Config.c4Damage.get(), "the wall lost " + lost(wall, wallHp) + ", expected the C4 damage");
        });
    }

    // ------------------------------------------------------------------ claims

    @GameTest(template = EMPTY)
    public static void piecesNeverSwallowPlacables(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        BlockPos underSlab = BlockPos.containing(GT.localPoint(GT.surface(level, PieceKind.FOUNDATION, o), 24, -8, 24));
        set(level, underSlab, Blocks.CHEST);
        check(!StructureService.placeAdmin(level, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE, BUILDER).ok(),
            "a foundation was placed over a chest");
        check(level.getBlockState(underSlab).is(Blocks.CHEST), "the refused foundation replaced the chest");
        level.removeBlock(underSlab, false);

        place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        // a chest where half of a wall would go refuses the wall (only terrain may hide a wall half)
        PieceKey wallKey = GT.wall(level, PieceKind.WALL, o, Dir4.NORTH, 0);
        BlockPos inTheWay = BlockPos.containing(GT.wallPoint(wallKey, 24, -1, 8));
        set(level, inTheWay, Blocks.CHEST);
        check(!StructureService.placeAdmin(level, wallKey, Tier.STONE, BUILDER).ok(), "a wall was placed through a chest");
        check(level.getBlockState(inTheWay).is(Blocks.CHEST), "the refused wall replaced the chest");
        check(StructureStore.get(level).index.occupants(inTheWay).isEmpty(), "the chest's block is counted as part of a piece");
        level.removeBlock(inTheWay, false);

        // a chest right against the wall: C4 on the chest hits the chest, not the wall
        Piece wall = place(h, wallKey, Tier.STONE);
        BlockPos chest = BlockPos.containing(GT.wallPoint(wallKey, 24, -24, 8));
        set(level, chest, Blocks.CHEST);
        int max = PlacableHpManager.maxHpFor(Blocks.CHEST);
        int wallHp = wall.hp;
        int damage = Math.max(1, max / 4);
        Vec3 face = new Vec3(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 15.0 / 16.0);
        check(BlastDamage.stuck(level, chest, face, damage), "C4 found nothing to hit on the chest");
        check(PlacableHpData.get(level).getHp(chest, max) == max - damage, "the chest did not take the C4 damage");
        check(wall.hp == wallHp, "C4 on the chest damaged the wall");
        PlacableHpManager.clear(level, chest);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void raidsBreakOrdinaryBlocksInClaims(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        Piece foundation = place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        BlockPos slab = GT.blockNear(foundation, GT.localPoint(foundation.key(), 8, -8, 8));
        // a little vanilla shell next to the building: planks, obsidian, natural and player-placed dirt, a chest
        BlockPos planks = o.offset(6, -1, 1);
        BlockPos obsidian = planks.east();
        BlockPos naturalDirt = planks.west();
        BlockPos placedDirt = planks.above();
        BlockPos chest = planks.south();
        BlockPos stone = planks.below();
        set(level, planks, Blocks.OAK_PLANKS);
        set(level, obsidian, Blocks.OBSIDIAN);
        set(level, naturalDirt, Blocks.DIRT);
        set(level, placedDirt, Blocks.DIRT);
        set(level, chest, Blocks.CHEST);
        PlacedTerrainData terrain = PlacedTerrainData.get(level);
        terrain.add(placedDirt);
        terrain.remove(naturalDirt);

        // no claim around: explosives leave world blocks alone
        if (ClaimManager.getAt(level, planks) == null) {
            check(RaidBlocks.breakAround(level, planks, 1.0) == 0, "a C4 outside any claim broke world blocks");
            check(level.getBlockState(planks).is(Blocks.OAK_PLANKS), "planks outside any claim were broken");
        }

        BlockPos claimPos = GT.cellSeat(o);
        Group group = claim(level, claimPos);
        try {
            check(ClaimManager.getAt(level, planks) != null, "test setup: the shell is not inside the claim");
            int broken = RaidBlocks.breakAround(level, planks, 1.0);
            check(level.getBlockState(planks).isAir(), "C4 on planks inside a claim did not break them");
            check(level.getBlockState(obsidian).isAir(), "C4 did not break the obsidian next to it");
            check(level.getBlockState(placedDirt).isAir(), "C4 did not break dirt a player placed");
            check(!terrain.contains(placedDirt), "the broken dirt is still recorded as placed");
            check(level.getBlockState(naturalDirt).is(Blocks.DIRT), "C4 broke natural terrain");
            check(level.getBlockState(stone).is(Blocks.STONE), "C4 broke natural stone");
            check(level.getBlockState(chest).is(Blocks.CHEST), "C4 broke a placable outright instead of damaging it");
            check(broken == 3, "C4 broke " + broken + " blocks, expected 3");
            check(level.getBlockState(claimPos).is(ModBlocks.CLAIM_BLOCK.get()), "the Claim Block went");

            // a vanilla blast's block list keeps only ordinary blocks, and nothing at all for a mob
            set(level, planks, Blocks.OAK_PLANKS);
            List<BlockPos> list = new ArrayList<>(List.of(planks, naturalDirt, stone, chest, slab, claimPos));
            RaidBlocks.keepBreakable(level, list, true);
            check(list.equals(List.of(planks)), "a blast inside the claim may break " + list);
            List<BlockPos> byMob = new ArrayList<>(List.of(planks, naturalDirt));
            RaidBlocks.keepBreakable(level, byMob, false);
            check(byMob.isEmpty(), "a mob's blast may break " + byMob);

            // real explosions next to the planks: TNT breaks them, a creeper does not
            Vec3 at = Vec3.atCenterOf(planks.north());
            Creeper creeper = EntityType.CREEPER.create(level);
            check(creeper != null, "test setup: no creeper");
            creeper.setPos(at.x, at.y, at.z);
            level.explode(creeper, at.x, at.y, at.z, 3.0F, Level.ExplosionInteraction.TNT);
            check(level.getBlockState(planks).is(Blocks.OAK_PLANKS), "a creeper blew up planks inside a claim");
            level.explode(null, at.x, at.y, at.z, 4.0F, Level.ExplosionInteraction.TNT);
            check(level.getBlockState(planks).isAir(), "TNT inside a claim did not break the planks");
            check(level.getBlockState(naturalDirt).is(Blocks.DIRT) && level.getBlockState(stone).is(Blocks.STONE), "TNT broke natural terrain in the claim");
            check(level.getBlockState(claimPos).is(ModBlocks.CLAIM_BLOCK.get()), "TNT broke the Claim Block");
        } finally {
            unclaim(level, group, claimPos);
            PlacableHpManager.clear(level, chest);
        }
        h.succeed();
    }

    /** RULES-02: what a foundation stands on is never blown away, so a base cannot be undermined around its pieces' HP. */
    @GameTest(template = EMPTY)
    public static void raidsDoNotUndermineFoundations(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        // a pad of planks under the foundation and one block around it; one column rests on sand held up by planks
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                set(level, o.offset(dx, -2, dz), Blocks.OAK_PLANKS);
            }
        }
        BlockPos sand = o.offset(2, -2, 2);
        BlockPos underSand = sand.below();
        set(level, underSand, Blocks.OAK_PLANKS);
        set(level, sand, Blocks.SAND);
        PlacedTerrainData.get(level).add(sand);
        Piece foundation = place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        BlockPos claimPos = GT.cellSeat(o);
        Group group = claim(level, claimPos);
        try {
            // the ground under each foundation column (the slab rests right on the pad: no skirt)
            List<int[]> cols = Footprints.columns(foundation.key());
            int groundY = PieceSolids.slabRow(foundation.key()) - 1;
            List<BlockPos> grounds = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                grounds.add(new BlockPos(cols.get(i)[0], groundY - foundation.skirt[i], cols.get(i)[1]));
            }
            BlockPos centre = GT.blockNear(foundation, foundation.key().centreWorld()).below();
            check(grounds.contains(centre) && grounds.contains(sand), "test setup: the centre and sand are not ground of a column");
            BlockPos corner = grounds.get(0);
            BlockPos outside = new BlockPos((int) Math.floor(foundation.bounds().minX) - 1, groundY, centre.getZ());
            check(ClaimManager.getAt(level, outside) != null, "test setup: the pad is not inside the claim");
            check(RaidBlocks.breakAround(level, centre, 1.0) == 0, "a C4 under the foundation broke its ground");
            check(RaidBlocks.breakAround(level, sand, 0.0) == 0, "a charge broke the placed sand a foundation column stands on");
            check(RaidBlocks.breakAround(level, underSand, 0.0) == 0, "a charge broke the planks holding up the sand under a column");
            List<BlockPos> list = new ArrayList<>(List.of(centre, corner, sand, underSand, outside));
            RaidBlocks.keepBreakable(level, list, true);
            check(list.equals(List.of(outside)), "a blast under the foundation may break " + list);

            // the rest of the pad is ordinary blocks: a C4 next to the foundation takes what is not its ground
            check(RaidBlocks.breakAround(level, outside, 1.0) == 3, "a C4 beside the foundation did not break the pad around it");
            // and so does TNT going off on the pad
            Vec3 tnt = Vec3.atCenterOf(o.offset(-1, -1, 2));
            level.explode(null, tnt.x, tnt.y, tnt.z, 4.0F, Level.ExplosionInteraction.TNT);
            for (BlockPos g : grounds) {
                check(!level.getBlockState(g).isAir(), "a blast blew away the ground under the foundation at " + g.toShortString());
            }
            check(level.getBlockState(underSand).is(Blocks.OAK_PLANKS), "a blast blew away the planks under the sand");
            StructureService.tick(level);
            check(StructureStore.get(level).index.piece(foundation.id) != null, "the foundation collapsed under a raid");
        } finally {
            unclaim(level, group, claimPos);
            PlacedTerrainData.get(level).remove(sand);
        }
        h.succeed();
    }

    /** SERVER-04, RULES-08: what a raid blows away from under a placable makes it fall, a damaged one with its HP. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void raidBlastsDropThePlacablesTheyUndercut(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        if (!Config.placablesNeedSupport.get()) {
            h.succeed();
            return;
        }
        place(h, GT.surface(level, PieceKind.FOUNDATION, o), Tier.STONE);
        // a damaged chest on planks takes a charge under it; a crafting table on planks is next to a TNT blast
        BlockPos chestFloor = h.absolutePos(new BlockPos(10, 1, 10));
        BlockPos chest = chestFloor.above();
        BlockPos tableFloor = h.absolutePos(new BlockPos(10, 1, 2));
        BlockPos table = tableFloor.above();
        for (BlockPos p : List.of(chestFloor, tableFloor)) {
            // stone under the planks keeps what falls in reach of the checks
            set(level, p.below(), Blocks.STONE);
            set(level, p, Blocks.OAK_PLANKS);
        }
        set(level, chest, Blocks.CHEST);
        set(level, table, Blocks.CRAFTING_TABLE);
        // the test world is kept between runs: no HP left over from an earlier one
        PlacableHpManager.forget(level, level.getBlockState(chest), chest);
        PlacableHpManager.forget(level, level.getBlockState(table), table);
        int chestMax = PlacableHpManager.maxHpFor(Blocks.CHEST);
        int chestLeft = chestMax - chestMax / 2;
        PlacableHpManager.decayDamage(level, chest, chestMax / 2);
        int tableLeft = PlacableHpManager.maxHpFor(Blocks.CRAFTING_TABLE) - Config.genericExplosionDamage.get();
        check(tableLeft > 0, "test setup: TNT destroys a crafting table outright");

        BlockPos claimPos = GT.cellSeat(o);
        Group group = claim(level, claimPos);
        try {
            check(ClaimManager.getAt(level, chestFloor) != null && ClaimManager.getAt(level, tableFloor) != null, "test setup: the placables are not in the claim");
            check(RaidBlocks.breakAround(level, chestFloor, 0.0) == 1, "the charge did not break the planks under the chest");
            Vec3 tnt = Vec3.atCenterOf(table.south());
            level.explode(null, tnt.x, tnt.y, tnt.z, 4.0F, Level.ExplosionInteraction.TNT);
            check(level.getBlockState(tableFloor).isAir(), "TNT did not break the planks under the crafting table");
        } finally {
            unclaim(level, group, claimPos);
        }
        h.succeedWhen(() -> {
            check(!level.getBlockState(chest).is(Blocks.CHEST), "the chest still hangs where its floor was");
            check(!level.getBlockState(table).is(Blocks.CRAFTING_TABLE), "the crafting table still hangs where its floor was");
            check(droppedHp(level, chest, Items.CHEST) == chestLeft, "the fallen chest dropped with " + droppedHp(level, chest, Items.CHEST) + " HP, expected " + chestLeft);
            check(droppedHp(level, table, Items.CRAFTING_TABLE) == tableLeft,
                "the fallen crafting table dropped with " + droppedHp(level, table, Items.CRAFTING_TABLE) + " HP, expected " + tableLeft);
        });
    }

    /** HP of the {@code item} lying around {@code pos}: the one written in it, or its full HP; -1 when there is none. */
    private static int droppedHp(ServerLevel level, BlockPos pos, Item item) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0))) {
            ItemStack stack = e.getItem();
            if (stack.is(item)) {
                CompoundTag tag = stack.getTag();
                int max = item instanceof BlockItem bi ? PlacableHpManager.maxHpFor(bi.getBlock()) : 0;
                return tag != null && tag.contains(PlacableHpManager.ITEM_HP_TAG) ? tag.getInt(PlacableHpManager.ITEM_HP_TAG) : max;
            }
        }
        return -1;
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void placedTerrainIsTrackedThroughFallsAndPistons(GameTestHelper h) {
        BlockPos o = ground(h);
        ServerLevel level = h.getLevel();
        PlacedTerrainData data = PlacedTerrainData.get(level);
        Player player = h.makeMockPlayer();

        // dirt a player places is recorded until it is broken; planks are no terrain
        BlockPos placed = o.offset(5, -1, 5);
        set(level, placed, Blocks.DIRT);
        data.remove(placed);
        MinecraftForge.EVENT_BUS.post(new EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, placed), Blocks.STONE.defaultBlockState(), player));
        check(data.contains(placed), "dirt a player placed is not recorded");
        MinecraftForge.EVENT_BUS.post(new BreakEvent(level, placed, Blocks.DIRT.defaultBlockState(), player));
        check(!data.contains(placed), "dirt a player broke is still recorded");
        set(level, placed, Blocks.OAK_PLANKS);
        MinecraftForge.EVENT_BUS.post(new EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, placed), Blocks.STONE.defaultBlockState(), player));
        check(!data.contains(placed), "planks were recorded as placed terrain");

        // sand a player put in the air falls: where it lands is player-made too
        BlockPos sky = o.offset(6, 3, 6);
        BlockPos landing = new BlockPos(sky.getX(), o.getY() - 1, sky.getZ());
        data.add(sky);
        data.remove(landing);
        FallingBlockEntity.fall(level, sky, Blocks.SAND.defaultBlockState());
        check(!data.contains(sky), "the record stayed where the sand fell from");

        // RULES-06: natural sand that falls is player-made where it lands too (someone mined or blew what held it)
        BlockPos naturalSky = o.offset(8, 3, 6);
        BlockPos naturalLanding = new BlockPos(naturalSky.getX(), o.getY() - 1, naturalSky.getZ());
        data.remove(naturalSky);
        data.remove(naturalLanding);
        FallingBlockEntity.fall(level, naturalSky, Blocks.SAND.defaultBlockState());

        // dirt pushed by a piston keeps its record
        BlockPos piston = o.offset(-2, -1, 6);
        BlockPos pushed = piston.east();
        level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        set(level, pushed, Blocks.DIRT);
        data.add(pushed);
        data.remove(pushed.east());
        set(level, piston.west(), Blocks.REDSTONE_BLOCK);

        // RULES-06: natural dirt a piston moves is recorded where it ends up
        BlockPos naturalPiston = o.offset(-2, -1, 8);
        BlockPos naturalPushed = naturalPiston.east();
        level.setBlock(naturalPiston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        set(level, naturalPushed, Blocks.DIRT);
        data.remove(naturalPushed);
        data.remove(naturalPushed.east());
        set(level, naturalPiston.west(), Blocks.REDSTONE_BLOCK);

        // water a player put down freezes without any event: ice is no natural terrain
        for (Block ice : new Block[]{Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE}) {
            check(!ice.defaultBlockState().is(RaidBlocks.TERRAIN), ice + " is raid terrain");
        }

        h.succeedWhen(() -> {
            check(level.getBlockState(landing).is(Blocks.SAND), "the sand has not landed yet");
            check(data.contains(landing), "the landed sand is not recorded as placed");
            check(level.getBlockState(naturalLanding).is(Blocks.SAND), "the natural sand has not landed yet");
            check(data.contains(naturalLanding) && !data.contains(naturalSky), "natural sand that fell is not recorded where it landed");
            check(level.getBlockState(pushed.east()).is(Blocks.DIRT), "the piston has not pushed the dirt yet");
            check(data.contains(pushed.east()) && !data.contains(pushed), "the record did not follow the pushed dirt");
            check(level.getBlockState(naturalPushed.east()).is(Blocks.DIRT), "the piston has not pushed the natural dirt yet");
            check(data.contains(naturalPushed.east()), "natural dirt a piston moved is not recorded where it ended up");
        });
    }

}
