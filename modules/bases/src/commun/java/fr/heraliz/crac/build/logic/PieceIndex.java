package fr.heraliz.crac.build.logic;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.FootprintKey;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.VertexKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory index of pieces by socket (edges, footprints, vertices), by 48 px world bucket and by world block. Backs
 * the server store and the client mirror.
 */
public class PieceIndex implements StructureView {
    /** Width of a spatial bucket in blocks (48 px). */
    public static final int BUCKET = 3;

    private record EdgeAt(EdgeKey edge, int h) {
    }

    protected final Long2ObjectOpenHashMap<Piece> pieces = new Long2ObjectOpenHashMap<>();
    protected final Long2ObjectOpenHashMap<Building> buildings = new Long2ObjectOpenHashMap<>();
    private final Map<EdgeAt, List<Piece>> surfacesByEdge = new HashMap<>();
    private final Map<EdgeKey, List<Piece>> wallsByEdge = new HashMap<>();
    private final Map<FootprintKey, List<Piece>> byFootprint = new HashMap<>();
    private final Map<VertexKey, List<Piece>> byVertex = new HashMap<>();
    private final Long2ObjectOpenHashMap<List<Piece>> buckets = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<Piece>> blocks = new Long2ObjectOpenHashMap<>();
    /** Chunk -> ids of the pieces with a block in it. Only kept by {@link #addWithBlocks} / {@link #removeWithBlocks}. */
    private final Long2ObjectOpenHashMap<LongSet> chunks = new Long2ObjectOpenHashMap<>();

    @Override
    @Nullable
    public Piece piece(long id) {
        return this.pieces.get(id);
    }

    @Override
    @Nullable
    public Building building(long id) {
        return this.buildings.get(id);
    }

    public Collection<Piece> allPieces() {
        return Collections.unmodifiableCollection(this.pieces.values());
    }

    public Collection<Building> allBuildings() {
        return Collections.unmodifiableCollection(this.buildings.values());
    }

    @Override
    public List<Piece> occupants(BlockPos pos) {
        return orEmpty(this.blocks.get(pos.asLong()));
    }

    @Override
    public List<Piece> surfacesOnEdge(EdgeKey edge, int h) {
        return orEmpty(this.surfacesByEdge.get(new EdgeAt(edge, h)));
    }

    @Override
    public List<Piece> wallsOnEdge(EdgeKey edge) {
        return orEmpty(this.wallsByEdge.get(edge));
    }

    @Override
    public List<Piece> withFootprint(FootprintKey footprint) {
        return orEmpty(this.byFootprint.get(footprint));
    }

    @Override
    public List<Piece> atVertex(VertexKey vertex) {
        return orEmpty(this.byVertex.get(vertex));
    }

    @Override
    public List<Piece> near(@Nullable BlockPos origin, AABB box) {
        List<Piece> out = new ArrayList<>();
        LongSet seen = new LongOpenHashSet();
        int x0 = Math.floorDiv((int) Math.floor(box.minX), BUCKET);
        int x1 = Math.floorDiv((int) Math.floor(box.maxX), BUCKET);
        int z0 = Math.floorDiv((int) Math.floor(box.minZ), BUCKET);
        int z1 = Math.floorDiv((int) Math.floor(box.maxZ), BUCKET);
        for (int bz = z0; bz <= z1; bz++) {
            for (int bx = x0; bx <= x1; bx++) {
                List<Piece> l = this.buckets.get(bucket(bx, bz));
                if (l == null) continue;
                for (Piece p : l) {
                    if ((origin == null || p.key().origin().equals(origin)) && p.bounds().intersects(box) && seen.add(p.id)) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
    }

    private static List<Piece> orEmpty(@Nullable List<Piece> l) {
        return l == null ? List.of() : l;
    }

    private static long bucket(int bx, int bz) {
        return (long) bx << 32 | (bz & 0xFFFFFFFFL);
    }

    /** Ids of the pieces with at least one block in the chunk ({@link ChunkPos#toLong}); server side only. */
    public LongSet piecesInChunk(long chunk) {
        LongSet s = this.chunks.get(chunk);
        return s == null ? LongSets.EMPTY_SET : s;
    }

    public void putBuilding(Building b) {
        this.buildings.put(b.id, b);
    }

    public void removeBuilding(long id) {
        this.buildings.remove(id);
    }

    /** Index a piece by socket and bucket. Blocks are indexed separately ({@link #addBlock}). */
    public void addPiece(Piece p) {
        this.pieces.put(p.id, p);
        PieceKind kind = p.kind();
        if (kind.isWall()) {
            add(this.wallsByEdge, p.key().edgeKey(), p);
            for (VertexKey v : p.key().vertices()) {
                add(this.byVertex, v, p);
            }
        } else {
            add(this.byFootprint, p.key().footprint(), p);
            if (kind.isSurface()) {
                for (EdgeKey e : p.key().edges()) {
                    add(this.surfacesByEdge, new EdgeAt(e, p.key().h()), p);
                }
            }
            if (kind.isRoof()) {
                for (VertexKey v : p.key().vertices()) {
                    add(this.byVertex, v, p);
                }
            }
        }
        forBuckets(p, k -> this.buckets.computeIfAbsent(k, kk -> new ArrayList<>(4)).add(p));
        Building b = this.buildings.get(p.buildingId);
        if (b != null) {
            b.pieces.add(p.id);
        }
    }

    public void removePiece(Piece p) {
        this.pieces.remove(p.id);
        PieceKind kind = p.kind();
        if (kind.isWall()) {
            remove(this.wallsByEdge, p.key().edgeKey(), p);
            for (VertexKey v : p.key().vertices()) {
                remove(this.byVertex, v, p);
            }
        } else {
            remove(this.byFootprint, p.key().footprint(), p);
            if (kind.isSurface()) {
                for (EdgeKey e : p.key().edges()) {
                    remove(this.surfacesByEdge, new EdgeAt(e, p.key().h()), p);
                }
            }
            if (kind.isRoof()) {
                for (VertexKey v : p.key().vertices()) {
                    remove(this.byVertex, v, p);
                }
            }
        }
        forBuckets(p, k -> removeFrom(this.buckets, k, p));
        Building building = this.buildings.get(p.buildingId);
        if (building != null) {
            building.pieces.remove(p.id);
        }
    }

    /** Calls {@code action} with every bucket key the piece's world bounds touch. */
    private static void forBuckets(Piece p, LongConsumer action) {
        AABB b = p.bounds();
        int x0 = Math.floorDiv((int) Math.floor(b.minX), BUCKET);
        int x1 = Math.floorDiv((int) Math.floor(b.maxX), BUCKET);
        int z0 = Math.floorDiv((int) Math.floor(b.minZ), BUCKET);
        int z1 = Math.floorDiv((int) Math.floor(b.maxZ), BUCKET);
        for (int bz = z0; bz <= z1; bz++) {
            for (int bx = x0; bx <= x1; bx++) {
                action.accept(bucket(bx, bz));
            }
        }
    }

    private static <K> void add(Map<K, List<Piece>> map, K key, Piece p) {
        List<Piece> l = map.computeIfAbsent(key, k -> new ArrayList<>(2));
        if (!l.contains(p)) {
            l.add(p);
        }
    }

    private static <K> void remove(Map<K, List<Piece>> map, K key, Piece p) {
        List<Piece> l = map.get(key);
        if (l != null) {
            l.remove(p);
            if (l.isEmpty()) {
                map.remove(key);
            }
        }
    }

    public void addBlock(long pos, Piece p) {
        List<Piece> l = this.blocks.computeIfAbsent(pos, k -> new ArrayList<>(2));
        if (!l.contains(p)) {
            l.add(p);
        }
    }

    public void removeBlock(long pos, Piece p) {
        removeFrom(this.blocks, pos, p);
    }

    private static void removeFrom(Long2ObjectOpenHashMap<List<Piece>> map, long key, Piece p) {
        List<Piece> l = map.get(key);
        if (l != null) {
            l.remove(p);
            if (l.isEmpty()) {
                map.remove(key);
            }
        }
    }

    /** World blocks a piece actually fills: the blocks placement wrote for it (buried wall halves left out). */
    public static List<BlockPos> blocksOf(Piece p) {
        return p.blocks;
    }

    /** Server-side helper: index a piece and all of its blocks. */
    public void addWithBlocks(Piece p) {
        this.addPiece(p);
        for (BlockPos pos : blocksOf(p)) {
            this.addBlock(pos.asLong(), p);
            this.chunks.computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), k -> new LongOpenHashSet()).add(p.id);
        }
    }

    public void removeWithBlocks(Piece p) {
        for (BlockPos pos : blocksOf(p)) {
            this.removeBlock(pos.asLong(), p);
            long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            LongSet ids = this.chunks.get(chunk);
            if (ids != null && ids.remove(p.id) && ids.isEmpty()) {
                this.chunks.remove(chunk);
            }
        }
        this.removePiece(p);
    }
}
