package fr.heraliz.crac.build.geom;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cached per-block shapes of pieces: collision ({@link ShapeRaster} outward) and occlusion (inward) of the
 * {@link PieceGeometry} solids in one block. Thread-safe; shapes are immutable.
 */
public final class PieceShapes {
    private record Key(PieceKind kind, LPoint anchor, int h, int orient, int rot, int state, long variant,
                       int dx, int dy, int dz, boolean outward) {
    }

    private static final LoadingCache<Key, VoxelShape> CACHE = CacheBuilder.newBuilder()
        .maximumSize(32_768)
        .build(new CacheLoader<>() {
            @Override
            public VoxelShape load(Key k) {
                PieceKey key = new PieceKey(k.kind, BlockPos.ZERO, k.anchor, k.h, k.orient, k.rot);
                if (!k.outward && k.kind.isRoof()) {
                    // the collision cells of a roof rise above its facets: occlusion uses the cells below them
                    return ShapeRaster.rasterize(roofOcclusion(key, k.variant), k.dx, k.dy, k.dz, false);
                }
                return ShapeRaster.rasterize(PieceGeometry.solids(key, k.state, k.variant), k.dx, k.dy, k.dz, k.outward);
            }
        });

    private PieceShapes() {
    }

    /** {@link RoofField#occlusion()} of a roof key, placed in building px. */
    private static List<Prism> roofOcclusion(PieceKey key, long variant) {
        Placement p = Placement.of(key);
        List<Prism> out = new ArrayList<>();
        for (Prism prism : RoofField.of(key.kind().shape, variant).occlusion()) {
            out.add(prism.transform(p));
        }
        return out;
    }

    /** Collision shape of the piece inside the world block {@code pos}. */
    public static VoxelShape shape(PieceKey key, int state, long variant, BlockPos pos) {
        return get(key, state, variant, pos, true);
    }

    /** Occlusion shape (inward raster, used for face flags) of the piece inside the world block {@code pos}. */
    public static VoxelShape occlusion(PieceKey key, int state, long variant, BlockPos pos) {
        return get(key, state, variant, pos, false);
    }

    private static VoxelShape get(PieceKey key, int state, long variant, BlockPos pos, boolean outward) {
        PieceKind kind = key.kind();
        int dx = pos.getX() - key.origin().getX();
        int dy = pos.getY() - key.origin().getY();
        int dz = pos.getZ() - key.origin().getZ();
        if (kind.isFoundation()) {
            // every skirt block has the shape of the first skirt row, whatever its depth
            dy = Math.max(dy, PieceSolids.slabRow(key) - 1 - key.origin().getY());
        }
        return CACHE.getUnchecked(new Key(kind, key.anchor(), key.h(), key.orient(), key.rot(),
            PieceGeometry.normalizeState(kind, state), PieceGeometry.normalizeVariant(kind, variant), dx, dy, dz, outward));
    }

    public static void clear() {
        CACHE.invalidateAll();
    }
}
