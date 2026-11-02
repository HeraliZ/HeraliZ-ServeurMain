package fr.heraliz.crac.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sol réel sous les zones dessinées plan en main ({@link ZoneArmoire}, {@link ZonesInterdites}) : le dessus du terrain,
 * sans les arbres ni les objets posés (03/11, retour du propriétaire : « la délimitation monte au-dessus des arbres et
 * redescend ensuite »).
 * <p>Le jeu du joueur n'a que deux cartes des hauteurs (MOTION_BLOCKING et WORLD_SURFACE, envoyées par le serveur ;
 * MOTION_BLOCKING_NO_LEAVES y reste vide) et toutes deux comptent les feuilles, les troncs et les objets posés. On part donc
 * de MOTION_BLOCKING et on descend la colonne tant que le bloc du dessus n'est pas du terrain : feuilles, troncs, caisses et
 * conteneurs de loot, meubles, lampadaires, barrières, plantes, vitres... Est du terrain : un bloc plein et opaque (terre,
 * pierre, sable, béton, briques, planches, pavés, neige tassée), la glace, l'eau, et un bloc bas qui couvre toute la case
 * (chemin, terre labourée, dalle du bas). Une colonne sans terrain sur {@link #DESCENTE} blocs garde le haut de la carte des
 * hauteurs, comme avant. Les appelants gardent le résultat quelques ticks (jamais relu à chaque image).
 */
final class SolZones {
    /** Descente au plus (blocs) sous le haut de la carte des hauteurs : un grand arbre fait une vingtaine de blocs. */
    static final int DESCENTE = 32;
    /** Valeur rendue hors du terrain chargé. */
    static final int INCONNU = Integer.MIN_VALUE;

    private SolZones() {
    }

    /**
     * Hauteur du dessus du terrain de la colonne (x, z), comme une carte des hauteurs (le bloc du sol est en y - 1) ;
     * {@link #INCONNU} si son tronçon n'est pas chargé. {@code curseur} : position de travail de l'appelant.
     */
    static int dessus(Level lvl, int x, int z, BlockPos.MutableBlockPos curseur) {
        if (!lvl.hasChunk(x >> 4, z >> 4)) {
            return INCONNU;
        }
        int haut = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int bas = Math.max(lvl.getMinBuildHeight(), haut - DESCENTE);
        for (int y = haut; y > bas; y--) {
            curseur.set(x, y - 1, z);
            if (terrain(lvl, curseur, lvl.getBlockState(curseur))) {
                return y;
            }
        }
        return haut;
    }

    /** Le bloc fait-il partie du terrain (on peut poser la zone dessus) ? */
    static boolean terrain(BlockGetter lvl, BlockPos pos, BlockState s) {
        if (!s.getFluidState().isEmpty()) {
            return true;   // la surface de l'eau compte
        }
        if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
            return false;
        }
        if (s.is(BlockTags.ICE) || s.isSolidRender(lvl, pos)) {
            return true;
        }
        // un objet posé n'est ni plein ni opaque ; un bloc bas qui couvre toute la case (chemin, dalle du bas) reste du sol
        VoxelShape forme = s.getCollisionShape(lvl, pos);
        if (forme.isEmpty()) {
            return false;   // herbes, fleurs, rails, neige fine
        }
        AABB b = forme.bounds();
        return b.minX <= 0.01 && b.minZ <= 0.01 && b.maxX >= 0.99 && b.maxZ >= 0.99 && b.minY <= 0.01
            && b.maxY >= 0.25 && b.maxY < 0.99;
    }
}
