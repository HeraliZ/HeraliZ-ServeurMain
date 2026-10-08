package fr.heraliz.crac.dev;

import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.test.GT;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side helpers of the test bench (server thread, dev client only): a test hut on the cell at {@code ref}
 * (square foundation, walls N/E/S/W of the given tiers, the south one a doorway) claimed by a group.
 */
public final class BancServeur {
    private static final Logger LOG = LogManager.getLogger("crac-banc");

    private BancServeur() {
    }

    /**
     * Builds the hut and claims it for {@code owner}'s group ({@code groupName}/{@code tag}, created when missing). The
     * Claim Block stands on the foundation. Returns the group.
     */
    public static Group cabane(ServerLevel level, BlockPos ref, UUID owner, String groupName, String tag, List<Tier> tiers) {
        PieceKey f = GT.surface(level, PieceKind.FOUNDATION, ref);
        StructureService.Outcome out = StructureService.placeAdmin(level, f, Tier.STONE, owner);
        LOG.info("[crac-banc] cabane fondation : {} {}", out.ok(), out.message().getString());
        Piece foundation = out.piece();
        GT.Dir4[] sides = {GT.Dir4.NORTH, GT.Dir4.EAST, GT.Dir4.SOUTH, GT.Dir4.WEST};
        for (int i = 0; i < sides.length; i++) {
            PieceKind kind = sides[i] == GT.Dir4.SOUTH ? PieceKind.DOORWAY : PieceKind.WALL;
            Tier t = i < tiers.size() ? tiers.get(i) : Tier.WOOD;
            StructureService.Outcome w = StructureService.placeAdmin(level, GT.wall(level, kind, ref, sides[i], 0), t, owner);
            LOG.info("[crac-banc] cabane mur {} {} : {} {}", sides[i], t, w.ok(), w.message().getString());
        }
        GroupData groups = GroupData.get(level.getServer());
        Group g = groups.getByMember(owner);
        if (g == null) {
            g = groups.create(groupName, tag, 0xC03030, owner);
        }
        if (foundation != null && g != null && ClaimManager.getByGroupAnywhere(level.getServer(), g.id()) == null) {
            BlockPos seat = GT.seat(foundation);
            level.setBlock(seat, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            ClaimManager.addClaim(level, seat, g.id(), 1);
            LOG.info("[crac-banc] cabane claim en {} pour {}", seat.toShortString(), g.name());
        }
        return g;
    }
}
