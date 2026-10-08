package fr.heraliz.crac.build.test;

import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.test.GT.Dir4;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.StructureService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/** Footsteps and hits on building pieces sound like the piece's tier. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Crac.MODID)
@PrefixGameTestTemplate(false)
public final class PieceSoundGameTests {
    private static final UUID BUILDER = new UUID(0L, 9L);

    private PieceSoundGameTests() {
    }

    private static Piece place(GameTestHelper h, PieceKey key, Tier tier) {
        StructureService.Outcome out = StructureService.placeAdmin(h.getLevel(), key, tier, BUILDER);
        if (!out.ok() || out.piece() == null) {
            throw new GameTestAssertException("could not place " + key.kind().id + ": " + out.message().getString());
        }
        return out.piece();
    }

    private static void expect(ServerLevel level, BlockPos pos, @Nullable Entity entity, SoundType want, String what) {
        SoundType got = level.getBlockState(pos).getSoundType(level, pos, entity);
        if (got != want) {
            throw new GameTestAssertException(what + ": wrong sound type at " + pos.toShortString());
        }
    }

    @GameTest(template = "empty")
    public static void piecesSoundLikeTheirTier(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z <= 13; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(4, 3, 4));
        Piece foundation = place(h, GT.surface(h.getLevel(), PieceKind.FOUNDATION, o), Tier.STONE);
        Piece north = null;
        for (Dir4 s : Dir4.values()) {
            Piece w = place(h, GT.wall(h.getLevel(), PieceKind.WALL, o, s, 0), Tier.METAL);
            if (s == Dir4.NORTH) {
                north = w;
            }
        }
        place(h, GT.surface(h.getLevel(), PieceKind.FLOOR, o.above(3)), Tier.ARMORED);

        BlockPos slab = GT.blockNear(foundation, foundation.key().centreWorld());
        expect(level, slab, null, SoundType.STONE, "stone foundation");
        expect(level, GT.wallBlock(north, 24, 1, 8), null, SoundType.METAL, "foot of a sheet metal wall");
        // the floor slab shares its block with the top of the wall below: standing on it, you hear the floor
        BlockPos shared = GT.wallBlock(north, 24, 1, 40);
        Player walker = h.makeMockPlayer();
        walker.setPos(shared.getX() + 0.5, shared.getY() + 1.0, shared.getZ() + 0.5);
        expect(level, shared, walker, SoundType.NETHERITE_BLOCK, "armored floor over a sheet metal wall");

        StructureService.setTier(level, foundation, Tier.WOOD);
        expect(level, slab, null, SoundType.WOOD, "foundation upgraded to wood");
        h.succeed();
    }
}
