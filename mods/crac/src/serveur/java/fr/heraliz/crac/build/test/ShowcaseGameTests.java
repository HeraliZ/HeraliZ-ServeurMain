package fr.heraliz.crac.build.test;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.BuildDebugCommand;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * {@code /cracbuild showcase} in the world, one tier per test (§12): the house goes up through the same code as the
 * command with zero refusals, as one building of that tier, every piece in its blocks. The {@code showcase} template
 * (26 x 14 x 22 blocks) holds one house standing on a stone floor at relative y = 1.
 */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class ShowcaseGameTests {
    private static final String SHOWCASE = "showcase";

    private ShowcaseGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void showcase(GameTestHelper h, Tier tier) {
        for (int x = 0; x < 26; x++) {
            for (int z = 0; z < 22; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        // as a player standing on the floor would type it: the house's slab rests on the stone
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        List<String> failures = new ArrayList<>();
        int placed = BuildDebugCommand.placeShowcase(level, pos, new Tier[]{tier}, failures);
        check(failures.isEmpty(), tier.id + " showcase: " + failures.size() + " pieces refused: " + String.join("; ", failures));
        List<PieceKey> keys = BuildDebugCommand.house(pos.above());
        check(placed == keys.size(), tier.id + " showcase placed " + placed + " of " + keys.size() + " pieces");

        StructureStore store = StructureStore.get(level);
        Long building = null;
        for (PieceKey k : keys) {
            Piece p = null;
            for (Piece q : store.index.occupants(PieceSolids.collisionBlocks(k).get(0))) {
                if (q.key().equals(k)) {
                    p = q;
                }
            }
            check(p != null, tier.id + " showcase: " + k.kind().id + " @" + k.anchorBlock().toShortString() + " h" + k.h() + " is not in the store");
            check(p.tier == tier, tier.id + " showcase: " + k.kind().id + " is " + p.tier.id);
            if (building == null) {
                building = p.buildingId;
            }
            check(p.buildingId == building, tier.id + " showcase: " + k.kind().id + " @" + k.anchorBlock().toShortString() + " is in another building");
            for (BlockPos b : PieceIndex.blocksOf(p)) {
                check(level.getBlockState(b).getBlock() instanceof StructureBlock, tier.id + " showcase: " + k.kind().id + " has no structure block at " + b.toShortString());
            }
        }
        check(StructureService.store(level).index.building(building) != null, "test setup: the showcase building is gone");
    }

    @GameTest(template = SHOWCASE)
    public static void twigShowcaseIsBuiltInFull(GameTestHelper h) {
        showcase(h, Tier.TWIG);
        h.succeed();
    }

    /** Same, and its upper floors are reached on foot: see {@link #walk}. */
    @GameTest(template = SHOWCASE)
    public static void woodShowcaseIsBuiltInFullAndWalkable(GameTestHelper h) {
        showcase(h, Tier.WOOD);
        walk(h);
        h.succeed();
    }

    /**
     * A player-sized entity (vanilla movement and 9.6 px step-up) walks from the room up the L stairs onto the second
     * floor, and out of the opened double doorway, across the landing and up the ramp onto the lookout.
     */
    private static void walk(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(4, 3, 4));
        // building px of the house: cells of 48 px, the room at x 0..96, z 0..96; L stairs in the south-east square
        Player climber = h.makeMockPlayer();
        climber.setPos(o.getX() + 83 / 16.0, o.getY(), o.getZ() + 36 / 16.0);
        GT.walkTo(climber, o.getX() + 83 / 16.0, o.getZ() + 83 / 16.0, 100);
        check(Math.abs(climber.getY() - (o.getY() + 1.5)) < 1e-6, "the showcase's L stairs: the climber is at " + climber.position() + ", expected on the landing");
        GT.walkTo(climber, o.getX() + 24 / 16.0, o.getZ() + 83 / 16.0, 100);
        check(Math.abs(climber.getY() - (o.getY() + 3.0)) < 1e-6 && Math.abs(climber.getX() - (o.getX() + 1.5)) < 1e-6,
            "the showcase's L stairs do not lead onto the second floor: the climber is at " + climber.position());

        Piece door = null;
        StructureStore store = StructureStore.get(level);
        for (PieceKey k : BuildDebugCommand.house(o)) {
            // the room's double doorway comes first (the wing has one too)
            if (k.kind() != PieceKind.DOUBLE_DOORWAY || door != null) continue;
            for (Piece q : store.index.occupants(PieceSolids.collisionBlocks(k).get(0))) {
                if (q.key().equals(k)) {
                    door = q;
                }
            }
        }
        check(door != null, "test setup: the showcase has no double doorway");
        door.setOpen(true);
        StructureService.refresh(level, door);
        Player walker = h.makeMockPlayer();
        walker.setPos(o.getX() + 1.5, o.getY(), o.getZ() + 5.0);
        GT.walkTo(walker, o.getX() + 1.5, o.getZ() + 13.5, 200);
        check(Math.abs(walker.getY() - (o.getY() + 3.0)) < 1e-6 && Math.abs(walker.getZ() - (o.getZ() + 13.5)) < 1e-6,
            "the showcase's ramp does not lead onto the lookout: the walker is at " + walker.position());
    }

    @GameTest(template = SHOWCASE)
    public static void stoneShowcaseIsBuiltInFull(GameTestHelper h) {
        showcase(h, Tier.STONE);
        h.succeed();
    }

    @GameTest(template = SHOWCASE)
    public static void metalShowcaseIsBuiltInFull(GameTestHelper h) {
        showcase(h, Tier.METAL);
        h.succeed();
    }

    @GameTest(template = SHOWCASE)
    public static void armoredShowcaseIsBuiltInFull(GameTestHelper h) {
        showcase(h, Tier.ARMORED);
        h.succeed();
    }
}
