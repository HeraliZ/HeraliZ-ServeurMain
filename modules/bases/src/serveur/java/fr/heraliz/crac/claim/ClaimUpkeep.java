package fr.heraliz.crac.claim;

import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Upkeep of a claim: every piece of the claimed building costs its tier's resource over {@code upkeepHours}. */
public final class ClaimUpkeep {
    private ClaimUpkeep() {
    }

    /** Pieces of the claim's building per {@link UpkeepResource} slot. */
    public static int[] pieceCounts(ServerLevel level, Claim claim) {
        return pieceCounts(level, StructureService.buildingOfClaim(level, claim.anchor()));
    }

    public static int[] pieceCounts(ServerLevel level, @Nullable Building b) {
        int[] counts = new int[ClaimBlockEntity.SLOTS];
        if (b != null) {
            for (Piece p : StructureStore.get(level).piecesOf(b)) {
                // 24/10 (revue de la boucle de jeu) : les brindilles ne coûtent aucun entretien. Avant, payées en planches
                // (50 hérals pièce) : la première base du clan (14 pièces, sans planches) disparaissait en 30 minutes.
                if (p.tier == fr.heraliz.crac.build.model.Tier.TWIG) continue;
                counts[UpkeepResource.of(p.tier).slot()]++;
            }
        }
        return counts;
    }

    public static void recompute(ServerLevel level, Claim claim, ClaimBlockEntity be) {
        recompute(level, StructureService.buildingOfClaim(level, claim.anchor()), be);
    }

    private static void recompute(ServerLevel level, @Nullable Building b, ClaimBlockEntity be) {
        int[] counts = pieceCounts(level, b);
        int[] needed = new int[ClaimBlockEntity.SLOTS];
        for (int i = 0; i < needed.length; i++) {
            UpkeepResource res = ClaimBlockEntity.resourceForSlot(i);
            // a resource whose item id does not resolve costs nothing, rather than making every base decay
            needed[i] = res.isPayable() ? counts[i] * res.perPiece() : 0;
        }
        be.setNeeded(needed);
    }

    /** Pays one minute of upkeep; returns true when some resource ran out (the base decays). */
    public static boolean consumeAndCheck(ServerLevel level, Claim claim, ClaimBlockEntity be) {
        return consumeAndCheck(level, claim, be, StructureService.buildingOfClaim(level, claim.anchor()));
    }

    /** Same, with the claim's building already looked up. */
    public static boolean consumeAndCheck(ServerLevel level, Claim claim, ClaimBlockEntity be, @Nullable Building building) {
        recompute(level, building, be);
        double perMin = 60.0 * Config.upkeepHours.get();
        boolean unpaid = false;
        for (int i = 0; i < ClaimBlockEntity.SLOTS; i++) {
            int needed = be.getNeeded(i);
            if (needed <= 0) {
                be.setDebt(i, 0.0);
                continue;
            }
            double debt = be.getDebt(i) + needed / perMin;
            int take = (int) Math.floor(debt);
            if (take > 0) {
                debt -= be.takeStored(i, take);
            }
            if (be.getStored(i) <= 0) {
                debt = 0.0;
                unpaid = true;
            }
            be.setDebt(i, debt);
        }
        return unpaid;
    }
}
