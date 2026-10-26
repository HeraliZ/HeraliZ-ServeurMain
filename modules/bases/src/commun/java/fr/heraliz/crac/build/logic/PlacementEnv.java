package fr.heraliz.crac.build.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** World queries the placement rules need, beyond the pieces themselves. */
public interface PlacementEnv {
    /** Air or a replaceable block that is not a structure block (grass, flowers, snow layer...). */
    boolean isFree(BlockPos pos);

    /** One of the mod's structure blocks. */
    boolean isStructure(BlockPos pos);

    /** A structure block still holding v2 data (an old building kept as it is): no new piece may share it. */
    default boolean holdsOldData(BlockPos pos) {
        return false;
    }

    /** Sturdy terrain a foundation can rest on. Structure blocks never count. */
    boolean isGround(BlockPos pos);

    /**
     * Natural solid terrain a wall half may be buried in: a full block that is not a structure block, a placable, the
     * Claim Block or any block with a block entity (the wall would show a hole once it is gone).
     */
    boolean isTerrain(BlockPos pos);

    /**
     * An object meant to stand against a wall ({@code Adosse.Adossable}: the Claim Block and its top, the metal crate).
     * A wall on the edge of the foundation it stands on only needs the outer half of that block: the object closes the
     * inner half (HeraliZ, 26/10: walls could not be built around a Claim Block placed first in a corner).
     */
    default boolean isAdossable(BlockPos pos) {
        return false;
    }

    /** A living entity (player, mob...) overlaps this world-space box. */
    boolean entityIn(AABB box);
}
