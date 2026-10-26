package fr.heraliz.crac.build.world;

import java.util.List;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** What a structure block sounds like when it is stepped on, hit or fallen on: the tier of one of its pieces. */
public final class PieceSounds {
    /** How far below the feet a shape top may be and still count as the surface under them (blocks). */
    private static final double FEET_SLACK = 1.0 / 32;

    private PieceSounds() {
    }

    /**
     * Tier a structure block at {@code pos} sounds like, or null for an empty block. An entity standing on it ({@code
     * feet} given) hears the piece whose collision shape is right under its feet: the floor, foundation, roof, stairs or
     * ramp rather than the top of a wall sharing the block, and in a block shared by two floors the one it stands on.
     * Without a shape under the feet, an entity above the block's lower quarter hears its first piece that is not a wall;
     * otherwise the block's first piece (the one its particles are taken from) is heard.
     */
    @Nullable
    public static Tier tierOf(List<Occupant> occupants, BlockPos pos, @Nullable Vec3 feet) {
        if (occupants.isEmpty()) {
            return null;
        }
        if (feet != null) {
            double lx = feet.x - pos.getX();
            double ly = feet.y - pos.getY();
            double lz = feet.z - pos.getZ();
            Occupant best = null;
            double bestTop = Double.NEGATIVE_INFINITY;
            for (Occupant o : occupants) {
                for (AABB b : o.shape(pos).toAabbs()) {
                    if (lx >= b.minX - FEET_SLACK && lx <= b.maxX + FEET_SLACK && lz >= b.minZ - FEET_SLACK && lz <= b.maxZ + FEET_SLACK
                        && b.maxY <= ly + FEET_SLACK && b.maxY > bestTop) {
                        best = o;
                        bestTop = b.maxY;
                    }
                }
            }
            if (best != null) {
                return best.tier();
            }
            if (ly >= 0.25) {
                for (Occupant o : occupants) {
                    if (!o.kind().isWall()) {
                        return o.tier();
                    }
                }
            }
        }
        return occupants.get(0).tier();
    }

    /** Same material families as the place, upgrade and destroy sounds played by {@link StructureService}. */
    public static SoundType of(Tier tier) {
        return switch (tier) {
            case TWIG, WOOD -> SoundType.WOOD;
            case STONE -> SoundType.STONE;
            case METAL -> SoundType.METAL;
            case ARMORED -> SoundType.NETHERITE_BLOCK;
        };
    }
}
