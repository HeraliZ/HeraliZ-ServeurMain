package fr.heraliz.ping.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ligne de vue verifiee par le serveur, sans jamais charger de tronçon : le rayon parcourt les blocs comme
 * {@code Level.clip} (formes de collision, liquides ignores, comme le client), mais un bloc dans un tronçon non charge
 * arrete la verification (refus). Ainsi un client ne peut ni pinguer a travers un mur, ni forcer le serveur a charger
 * le monde au loin.
 */
final class Vue {
    private Vue() {
    }

    /** Resultat d'un rayon : rien touche, bloc touche (point et bloc), ou tronçon non charge. */
    static final BlockHitResult INCONNU = BlockHitResult.miss(Vec3.ZERO, net.minecraft.core.Direction.UP, BlockPos.ZERO);

    /** Premier bloc solide entre a et b (null = rien), ou {@link #INCONNU} si le rayon passe dans un tronçon non charge. */
    static BlockHitResult rayon(ServerLevel niveau, Vec3 a, Vec3 b) {
        if (a.distanceToSqr(b) < 1.0E-6) return null;
        return BlockGetter.traverseBlocks(a, b, niveau, (lvl, pos) -> {
            if (lvl.isOutsideBuildHeight(pos)) return null;
            if (!lvl.hasChunkAt(pos)) return INCONNU;
            BlockState etat = lvl.getBlockState(pos);
            if (etat.isAir()) return null;
            VoxelShape forme = etat.getCollisionShape(lvl, pos, CollisionContext.empty());
            if (forme.isEmpty()) return null;
            return lvl.clipWithInteractionOverride(a, b, pos, forme, etat);
        }, lvl -> null);
    }

    /**
     * Bloc vise visible depuis l'oeil : le rayon (prolonge de 5 cm) ne touche rien avant le point, ou touche le bloc
     * annonce, ou s'arrete a moins de {@code tolerance} blocs du point.
     */
    static boolean blocVisible(ServerLevel niveau, Vec3 oeil, Vec3 point, BlockPos bloc, double tolerance) {
        Vec3 dir = point.subtract(oeil);
        double l = dir.length();
        if (l < 1.0E-3) return true;
        Vec3 fin = point.add(dir.scale(0.05 / l));
        BlockHitResult h = rayon(niveau, oeil, fin);
        if (h == null) return true;
        if (h == INCONNU) return false;
        return h.getBlockPos().equals(bloc) || h.getLocation().distanceToSqr(point) <= tolerance * tolerance;
    }

    /** Entite visible : un des trois points (centre, haut, bas) de sa boite est atteint sans mur. */
    static boolean boiteVisible(ServerLevel niveau, Vec3 oeil, AABB boite) {
        Vec3 c = boite.getCenter();
        double h = boite.getYsize();
        return pointLibre(niveau, oeil, c) || pointLibre(niveau, oeil, new Vec3(c.x, boite.maxY - Math.min(0.15, h * 0.2), c.z))
                || pointLibre(niveau, oeil, new Vec3(c.x, boite.minY + Math.min(0.15, h * 0.2), c.z));
    }

    private static boolean pointLibre(ServerLevel niveau, Vec3 oeil, Vec3 p) {
        BlockHitResult h = rayon(niveau, oeil, p);
        return h == null;
    }
}
