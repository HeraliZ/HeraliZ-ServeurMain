package fr.heraliz.crac.build.logic;

import java.util.List;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.FootprintKey;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.VertexKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Read access to the pieces of a level. Implemented by the server store (truth) and by the client mirror (ghost
 * preview), so placement rules run identically on both sides. Socket lookups are exact: keys carry the building
 * origin, so pieces of buildings with another origin never match.
 */
public interface StructureView {
    @Nullable
    Piece piece(long id);

    @Nullable
    Building building(long id);

    /** Pieces occupying a world block. */
    List<Piece> occupants(BlockPos pos);

    /** Surfaces (foundations, floors, roofs) at height {@code h} having {@code edge} as one of their edges. */
    List<Piece> surfacesOnEdge(EdgeKey edge, int h);

    /** Walls on {@code edge}, at any height. */
    List<Piece> wallsOnEdge(EdgeKey edge);

    /** Surfaces and volumes with exactly this footprint, at any height. */
    List<Piece> withFootprint(FootprintKey footprint);

    /** Walls ending at the vertex and roofs having it as a vertex. */
    List<Piece> atVertex(VertexKey vertex);

    /**
     * Pieces whose world bounds intersect {@code worldBox} (blocks), restricted to the building origin {@code origin}
     * when it is not null. Candidates for conflicts and for the aim.
     */
    List<Piece> near(@Nullable BlockPos origin, AABB worldBox);
}
