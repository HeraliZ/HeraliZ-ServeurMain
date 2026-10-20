package fr.heraliz.crac.build.geom;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.Arrays;
import java.util.List;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import net.minecraft.core.BlockPos;

/**
 * Shared cache of per-instance collision solids: {@code (key, normalized state, roof variant)} -> immutable solids in
 * building px and their bounds. Server and client use the same entries, so there is one code path from
 * {@code (key, state, variant)} to a block's shape. Concurrent, weight-bounded, loads deduplicated.
 * <p>
 * Foundations carry a one-block skirt under every column: per-block shapes clamp deeper skirt blocks to that row
 * (DEEP_SKIRT trick), so collision never depends on the skirt depth.
 */
public final class PieceGeometry {
    /** Solids in building px and their bounds {minX, minY, minZ, maxX, maxY, maxZ}. */
    public record Entry(List<Prism> solids, double[] bounds) {
    }

    /** Everything solids depend on: the origin is left out (solids are relative to it). */
    private record Key(PieceKind kind, LPoint anchor, int h, int orient, int rot, int state, long variant) {
        PieceKey pieceKey() {
            return new PieceKey(this.kind, BlockPos.ZERO, this.anchor, this.h, this.orient, this.rot);
        }
    }

    private static final LoadingCache<Key, Entry> CACHE = CacheBuilder.newBuilder()
        .maximumWeight(400_000)
        .weigher((Key k, Entry e) -> 4 + e.solids().size())
        .build(new CacheLoader<>() {
            @Override
            public Entry load(Key k) {
                return compute(k);
            }
        });

    private PieceGeometry() {
    }

    /** Collision-relevant bits of a piece state: door open bit, window insert present or not. */
    public static int normalizeState(PieceKind kind, int state) {
        if (kind.hasDoor()) {
            return state & Piece.STATE_OPEN;
        }
        if (kind == PieceKind.WINDOW) {
            return (state & Piece.INSERT_MASK) != 0 ? Piece.INSERT_BARS << Piece.INSERT_SHIFT : 0;
        }
        return 0;
    }

    /** Roof variants (normalized sector masks); 0 for every other kind (wall posts do not change collision). */
    public static long normalizeVariant(PieceKind kind, long variant) {
        return kind.isRoof() ? RoofField.normalize(kind.shape, variant) : 0L;
    }

    public static Entry get(PieceKey key, int state, long variant) {
        PieceKind kind = key.kind();
        return CACHE.getUnchecked(new Key(kind, key.anchor(), key.h(), key.orient(), key.rot(),
            normalizeState(kind, state), normalizeVariant(kind, variant)));
    }

    /** Solids of the instance in building px (foundations: with a one-block skirt under every column). */
    public static List<Prism> solids(PieceKey key, int state, long variant) {
        return get(key, state, variant).solids();
    }

    private static Entry compute(Key k) {
        PieceKey key = k.pieceKey();
        byte[] skirt = null;
        if (k.kind.isFoundation()) {
            skirt = new byte[Footprints.columns(key).size()];
            Arrays.fill(skirt, (byte) 1);
        }
        List<Prism> solids = List.copyOf(PieceSolids.solids(key, k.state, k.variant, skirt));
        return new Entry(solids, PieceSolids.boundsPx(solids));
    }

    public static void clear() {
        CACHE.invalidateAll();
    }
}
