package fr.heraliz.crac.bases;

import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Points d'arrivée dans le monde des bases : près de sa base, ou au hasard sur un sol plat loin des autres bases. */
final class Points {
    private static final int ESSAIS = 24;

    private Points() {
    }

    /** Une place pour un joueur près du Claim Block de sa base (à côté du bloc, dans la base), ou null. */
    @Nullable
    static Vec3 presDe(ServerLevel level, ServerPlayer p, BlockPos ancre) {
        for (int r = 1; r <= 4; r++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int sens = -1; sens <= 1; sens += 2) {
                    int y = ancre.getY() + sens * dy;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                continue;
                            }
                            BlockPos pieds = new BlockPos(ancre.getX() + dx, y, ancre.getZ() + dz);
                            if (libre(level, p, pieds)) {
                                return Vec3.atBottomCenterOf(pieds);
                            }
                        }
                    }
                    if (dy == 0) {
                        break;
                    }
                }
            }
        }
        return null;
    }

    /** Pieds libres (corps du joueur sans collision ni liquide), un sol dessous. */
    static boolean libre(ServerLevel level, ServerPlayer p, BlockPos pieds) {
        if (!level.isLoaded(pieds) || !level.getWorldBorder().isWithinBounds(pieds)) {
            return false;
        }
        AABB corps = p.getDimensions(p.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pieds));
        if (!level.noCollision(p, corps) || level.containsAnyLiquid(corps)) {
            return false;
        }
        BlockState sol = level.getBlockState(pieds.below());
        return !sol.getCollisionShape(level, pieds.below()).isEmpty() && sol.getFluidState().isEmpty();
    }

    /**
     * Un point au hasard dans le monde des bases : sol naturel plat (5 × 5 à un bloc près), pas d'eau, dans la bordure,
     * à {@code [bases] ecart_bases} blocs au moins de tout Claim Block. Génère au plus quelques tronçons. Null si rien
     * n'a été trouvé en {@value #ESSAIS} essais.
     */
    @Nullable
    static Vec3 auHasard(ServerLevel level, ServerPlayer p) {
        RandomSource rnd = level.getRandom();
        WorldBorder b = level.getWorldBorder();
        double demi = Math.max(32.0, b.getSize() / 2.0 - 200.0);
        double rayon = Math.min(BasesReglages.i(BasesReglages.RAYON_DEPART, 2500), demi);
        int ecart = BasesReglages.i(BasesReglages.ECART_BASES, 120);
        for (int i = 0; i < ESSAIS; i++) {
            int x = (int) Math.floor(b.getCenterX() + (rnd.nextDouble() * 2 - 1) * rayon);
            int z = (int) Math.floor(b.getCenterZ() + (rnd.nextDouble() * 2 - 1) * rayon);
            if (presDUneBase(level, x, z, ecart)) {
                continue;
            }
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!plat(level, x, y, z)) {
                continue;
            }
            BlockPos pieds = new BlockPos(x, y, z);
            if (libre(level, p, pieds)) {
                return Vec3.atBottomCenterOf(pieds);
            }
        }
        return null;
    }

    private static boolean presDUneBase(ServerLevel level, int x, int z, int ecart) {
        long e2 = (long) ecart * ecart;
        for (Claim c : ClaimData.get(level).all()) {
            long dx = c.anchor().getX() - x;
            long dz = c.anchor().getZ() - z;
            if (dx * dx + dz * dz < e2) {
                return true;
            }
        }
        return false;
    }

    /** Les coins et les milieux d'un carré de 5 × 5 sont à un bloc près de la même hauteur (dans le même tronçon ou à côté). */
    private static boolean plat(ServerLevel level, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                if (Math.abs(h - y) > 1) {
                    return false;
                }
            }
        }
        return true;
    }
}
