package fr.heraliz.crac.build.client.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.geom.RoofField;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.Occupant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Turns piece meshes into baked quads, per block of the piece, and caches them per configuration and lattice phase
 * (§10.2): kind, orient, rot, tier, normalized state, variant, crack and {@code (xa mod 4, xb, za mod 4, zb, h mod 2)}.
 * Two pieces of the same phase differ by whole blocks (12 px * 4 = 3 blocks, 24 px * 2 = 3 blocks), so axis-aligned
 * builds share 32 phases and round builds are baked per position. The mesh is placed relative to the anchor block,
 * split into texture tiles and cut into the reserved blocks. Sprites and caches are swapped as a whole when the block
 * atlas is re-stitched, so a chunk build racing a resource reload never mixes old and new UVs.
 */
@OnlyIn(Dist.CLIENT)
public final class PieceBakery {
    public static final int SIDES = 7;
    public static final int NO_SIDE = 6;
    private static final Direction[] DIRS = Direction.values();
    private static final double UV_INSET = 0.002;
    /** Pixels every baked fragment grows by in its plane, see {@link MeshClipper#inflate}. */
    private static final double SEAL = 0.01;
    private static final BlockQuads EMPTY = new BlockQuads(new List[0], 0);

    private static volatile PieceBakery current;

    /** Upper bound of the cached quads (about 180 bytes each). */
    private static final long MAX_QUADS = 500_000;

    /** What a bake depends on: the configuration and the lattice phase (see {@link #canonical}). */
    record BakeKey(PieceKind kind, int orient, int rot, Tier tier, int state, long variant, int crack,
                   int pxa, int xb, int pza, int zb, int ph) {
    }

    /** What a ghost depends on (it is drawn relative to the anchor point, so the position does not matter). */
    record GhostKey(PieceKind kind, int orient, int rot, Tier tier, int state, long variant) {
    }

    /** Baked blocks of one piece configuration and their quad count (the cache weight). */
    private record Baked(Map<Long, BlockQuads> blocks, int quads) {
    }

    private final TextureAtlasSprite[] sprites;
    private final LoadingCache<BakeKey, Baked> pieces;
    private final Cache<GhostKey, GhostQuads> ghosts = CacheBuilder.newBuilder().maximumSize(4096).build();

    private PieceBakery(TextureAtlasSprite[] sprites) {
        this.sprites = sprites;
        this.pieces = CacheBuilder.newBuilder()
            .maximumWeight(MAX_QUADS)
            .weigher((BakeKey k, Baked b) -> 1 + b.quads())
            .build(new CacheLoader<>() {
                @Override
                public Baked load(BakeKey k) {
                    return PieceBakery.this.bakePiece(k);
                }
            });
    }

    /** Quads of one block of one piece, by layer and side ({@link #NO_SIDE} for unculled quads). */
    public static final class BlockQuads {
        private final List<BakedQuad>[] lists;
        public final int layerMask;

        BlockQuads(List<BakedQuad>[] lists, int layerMask) {
            this.lists = lists;
            this.layerMask = layerMask;
        }

        int count() {
            int n = 0;
            for (List<BakedQuad> l : this.lists) {
                if (l != null) n += l.size();
            }
            return n;
        }

        public List<BakedQuad> get(int layer, int side) {
            if (this.lists.length == 0) {
                return List.of();
            }
            List<BakedQuad> l = this.lists[layer * SIDES + side];
            return l == null ? List.of() : l;
        }
    }

    /** Whole-piece mesh for the ghost preview: per quad 4 vertices of {x, y, z, u, v, shade} in block units. */
    public static final class GhostQuads {
        public final float[] data;
        public final int quads;

        GhostQuads(float[] data, int quads) {
            this.data = data;
            this.quads = quads;
        }
    }

    public static void onAtlasStitched(TextureAtlas atlas) {
        if (!InventoryMenu.BLOCK_ATLAS.equals(atlas.location())) {
            return;
        }
        current = new PieceBakery(resolve(atlas));
    }

    static PieceBakery get() {
        PieceBakery b = current;
        if (b == null) {
            synchronized (PieceBakery.class) {
                b = current;
                if (b == null) {
                    TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
                    b = new PieceBakery(resolve(atlas));
                    current = b;
                }
            }
        }
        return b;
    }

    private static TextureAtlasSprite[] resolve(TextureAtlas atlas) {
        Tier[] tiers = Tier.values();
        TextureAtlasSprite[] out = new TextureAtlasSprite[tiers.length * Mat.count()];
        for (Tier t : tiers) {
            for (int m = 0; m < Mat.count(); m++) {
                out[t.ordinal() * Mat.count() + m] = atlas.getSprite(new ResourceLocation("crac", Mat.byOrdinal(m).texture(t)));
            }
        }
        return out;
    }

    TextureAtlasSprite sprite(Tier tier, Mat mat) {
        return this.sprites[tier.ordinal() * Mat.count() + mat.ordinal()];
    }

    public static TextureAtlasSprite particle(PieceKind kind, Tier tier) {
        Mat m = switch (kind.family) {
            case WALL -> Mat.WALL;
            case VOLUME -> Mat.SIDE;
            case SURFACE -> kind.isFoundation() ? Mat.FOUNDATION_SIDE : kind.isRoof() ? Mat.ROOF : Mat.FLOOR;
        };
        return get().sprite(tier, m);
    }

    // ------------------------------------------------------------------ keys

    /** Render-relevant state bits: door open, window insert (bars and glass look different). */
    static int normState(PieceKind kind, int state) {
        if (kind.hasDoor()) return state & 1;
        if (kind == PieceKind.WINDOW) return state & 6;
        return 0;
    }

    /** Roofs: normalized sector masks; walls: the post bits of their bands; nothing else has a variant. */
    static long normVariant(PieceKind kind, long variant) {
        if (kind.isRoof()) return RoofField.normalize(kind.shape, variant);
        if (kind.isWall()) return variant & (kind == PieceKind.HALF_WALL ? 0b0101 : 0b1111);
        return 0L;
    }

    static BakeKey bakeKey(PieceKey key, Tier tier, int state, long variant, int crack) {
        PieceKind kind = key.kind();
        LPoint a = key.anchor();
        return new BakeKey(kind, key.orient(), key.rot(), tier, normState(kind, state), normVariant(kind, variant), crack & 3,
            Math.floorMod(a.xa(), 4), a.xb(), Math.floorMod(a.za(), 4), a.zb(), Math.floorMod(key.h(), 2));
    }

    /** The phase's representative: origin at zero, the anchor and height reduced by whole 3-block steps. */
    static PieceKey canonical(BakeKey k) {
        return new PieceKey(k.kind(), BlockPos.ZERO, new LPoint(k.pxa(), k.xb(), k.pza(), k.zb()), k.ph(), k.orient(), k.rot());
    }

    /**
     * Block of the bake a world block reads, relative to the anchor block: deeper foundation skirt blocks reuse the
     * {@link PieceMeshes#SKIRT_ROWS} baked rows (alternating texture rows).
     */
    static long renderBlock(PieceKey key, BlockPos pos) {
        BlockPos a = key.anchorBlock();
        int dy = pos.getY() - a.getY();
        if (key.kind().isFoundation()) {
            int first = PieceSolids.slabRow(key) - 1 - a.getY();
            if (dy < first) {
                dy = first - Math.floorMod(first - dy, PieceMeshes.SKIRT_ROWS);
            }
        }
        return MeshClipper.blockKey(pos.getX() - a.getX(), dy, pos.getZ() - a.getZ());
    }

    // ------------------------------------------------------------------ chunk quads

    /** Quads of the occupant's piece inside the world block {@code pos}. */
    public static BlockQuads quads(Occupant o, BlockPos pos) {
        return quads(o.key(), o.tier(), o.state(), o.variant(), o.crack(), pos);
    }

    public static BlockQuads quads(PieceKey key, Tier tier, int state, long variant, int crack, BlockPos pos) {
        PieceBakery b = get();
        Baked baked = b.pieces.getUnchecked(bakeKey(key, tier, state, variant, crack));
        BlockQuads q = baked.blocks().get(renderBlock(key, pos));
        return q == null ? EMPTY : q;
    }

    /** Mesh of a bake in px relative to the anchor block of its canonical key. */
    static List<MeshFace> bakeMesh(BakeKey k) {
        PieceKey key = canonical(k);
        BlockPos a = key.anchorBlock();
        List<MeshFace> out = new ArrayList<>();
        for (MeshFace f : PieceMeshes.build(key, k.tier(), k.state(), k.variant(), k.crack())) {
            out.add(f.translate(-16.0 * a.getX(), -16.0 * a.getY(), -16.0 * a.getZ()));
        }
        return out;
    }

    private Baked bakePiece(BakeKey k) {
        PieceKey key = canonical(k);
        List<MeshFace> faces = MeshClipper.splitTiles(bakeMesh(k));
        Map<Long, List<MeshFace>> byBlock = MeshClipper.byBlock(faces, PieceMeshes.renderBlocks(key));
        Map<Long, BlockQuads> out = new HashMap<>();
        int quads = 0;
        for (Map.Entry<Long, List<MeshFace>> e : byBlock.entrySet()) {
            long bk = e.getKey();
            BlockQuads q = this.bakeBlock(e.getValue(), k.kind(), k.tier(), MeshClipper.keyX(bk), MeshClipper.keyY(bk), MeshClipper.keyZ(bk));
            out.put(bk, q);
            quads += q.count();
        }
        return new Baked(Map.copyOf(out), quads);
    }

    @SuppressWarnings("unchecked")
    private BlockQuads bakeBlock(List<MeshFace> frags, PieceKind kind, Tier tier, int bx, int by, int bz) {
        List<BakedQuad>[] lists = new List[Mat.Layer.values().length * SIDES];
        int mask = 0;
        for (MeshFace f : frags) {
            int layer = f.mat.layer(tier).ordinal();
            int side = cullSide(f, bx, by, bz);
            if (side == Direction.DOWN.get3DDataValue()) {
                // observed in game: faces on a block's bottom plane given a DOWN cull face were not drawn (the head of
                // a doorway showed the sky above the leaf), whatever the block below; they are never culled
                side = NO_SIDE;
            }
            if (side == Direction.UP.get3DDataValue() && kind.family == PieceKind.Family.SURFACE) {
                // stairs and ramps standing here have full collision faces but not a full render
                side = NO_SIDE;
            }
            if (side != NO_SIDE && (kind == PieceKind.FLOOR || kind == PieceKind.TRI_FLOOR || kind.isRoof())) {
                // HeraliZ (21/10, CRac 1.14.4): the edge (y -3..0) of a floor or roof resting on a full wall lies on a
                // block border whose face flags come from the wall's collision (up to 48) on both sides, but the wall
                // is drawn up to 47 only: the edge was culled and a 1 px slit showed the inside of the base
                side = NO_SIDE;
            }
            Direction dir = side == NO_SIDE ? Direction.getNearest((float) f.nx, (float) f.ny, (float) f.nz) : DIRS[side];
            TextureAtlasSprite sprite = this.sprite(tier, f.mat);
            int idx = layer * SIDES + side;
            if (lists[idx] == null) {
                lists[idx] = new ArrayList<>();
            }
            emitQuads(MeshClipper.inflate(f, SEAL), sprite, dir, bx, by, bz, lists[idx]);
            mask |= 1 << layer;
        }
        for (int i = 0; i < lists.length; i++) {
            if (lists[i] != null) {
                lists[i] = List.copyOf(lists[i]);
            }
        }
        return new BlockQuads(lists, mask);
    }

    /** Direction index when the fragment lies on the block's border plane facing out of it, else {@link #NO_SIDE}. */
    static int cullSide(MeshFace f, int bx, int by, int bz) {
        double e = 1e-4;
        int axis;
        boolean positive;
        if (Math.abs(f.nx) > 1 - 1e-6) {
            axis = 0;
            positive = f.nx > 0;
        } else if (Math.abs(f.ny) > 1 - 1e-6) {
            axis = 1;
            positive = f.ny > 0;
        } else if (Math.abs(f.nz) > 1 - 1e-6) {
            axis = 2;
            positive = f.nz > 0;
        } else {
            return NO_SIDE;
        }
        int b = axis == 0 ? bx : axis == 1 ? by : bz;
        double plane = (positive ? b + 1 : b) * 16.0;
        for (int i = 0; i < f.size(); i++) {
            if (Math.abs(f.pos[i * 3 + axis] - plane) > e) {
                return NO_SIDE;
            }
        }
        Direction d = switch (axis) {
            case 0 -> positive ? Direction.EAST : Direction.WEST;
            case 1 -> positive ? Direction.UP : Direction.DOWN;
            default -> positive ? Direction.SOUTH : Direction.NORTH;
        };
        return d.get3DDataValue();
    }

    /**
     * Continuous directional shade of a face that is not axis-aligned (baked with {@code shade = false}): vanilla's
     * 0.6 / 0.8 / 1.0 / 0.5 weights interpolated by the squared normal, so walls at 30° and 60° do not jump between
     * vanilla's per-direction shades.
     */
    static float directional(MeshFace f) {
        double up = f.ny > 0 ? 1.0 : 0.5;
        return (float) (f.nx * f.nx * 0.6 + f.nz * f.nz * 0.8 + f.ny * f.ny * up);
    }

    static boolean axisAligned(MeshFace f) {
        return Math.abs(f.nx) > 1 - 1e-6 || Math.abs(f.ny) > 1 - 1e-6 || Math.abs(f.nz) > 1 - 1e-6;
    }

    static float shade(MeshFace f, double y) {
        float s = f.tone;
        if (!Double.isNaN(f.contactY)) {
            double t = Math.min(1.0, Math.max(0.0, (y - f.contactY) / 14.0));
            s *= (float) (0.72 + 0.28 * t);
        }
        return s;
    }

    /**
     * Vanilla smooth lighting gives vertex i the light of corner i of the face in {@code FaceInfo} order, interpolated
     * over the quad's bounds. Axis-aligned rectangles and right triangles are emitted in that order and keep smooth
     * lighting; any other polygon is lit flat.
     */
    private static final int[][][] CORNERS = {
        {{0, 1}, {0, 0}, {1, 0}, {1, 1}},  // DOWN: (x, z)
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},  // UP: (x, z)
        {{1, 1}, {1, 0}, {0, 0}, {0, 1}},  // NORTH: (x, y)
        {{0, 1}, {0, 0}, {1, 0}, {1, 1}},  // SOUTH: (x, y)
        {{1, 0}, {0, 0}, {0, 1}, {1, 1}},  // WEST: (y, z)
        {{1, 1}, {0, 1}, {0, 0}, {1, 0}},  // EAST: (y, z)
    };

    private static void emitQuads(MeshFace f, TextureAtlasSprite sprite, Direction dir, int bx, int by, int bz, List<BakedQuad> out) {
        int n = f.size();
        if (n <= 4 && axisAligned(f, dir)) {
            int[] order = canonical(f, dir);
            if (order != null) {
                out.add(quad(f, sprite, dir, bx, by, bz, order[0], order[1], order[2], order[3], true));
                return;
            }
        }
        int i = 1;
        while (i + 2 < n) {
            out.add(quad(f, sprite, dir, bx, by, bz, 0, i, i + 1, i + 2, false));
            i += 2;
        }
        if (i + 1 < n) {
            out.add(quad(f, sprite, dir, bx, by, bz, 0, i, i + 1, i + 1, false));
        }
    }

    private static boolean axisAligned(MeshFace f, Direction dir) {
        return Math.abs(f.nx * dir.getStepX() + f.ny * dir.getStepY() + f.nz * dir.getStepZ()) > 1 - 1e-6;
    }

    /** Vertex indices in FaceInfo corner order (a missing corner repeats its predecessor), or null. */
    private static int[] canonical(MeshFace f, Direction dir) {
        int a;
        int b;
        switch (dir.getAxis()) {
            case Y -> {
                a = 0;
                b = 2;
            }
            case Z -> {
                a = 0;
                b = 1;
            }
            default -> {
                a = 1;
                b = 2;
            }
        }
        int n = f.size();
        double minA = Double.MAX_VALUE;
        double maxA = -Double.MAX_VALUE;
        double minB = Double.MAX_VALUE;
        double maxB = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minA = Math.min(minA, f.pos[i * 3 + a]);
            maxA = Math.max(maxA, f.pos[i * 3 + a]);
            minB = Math.min(minB, f.pos[i * 3 + b]);
            maxB = Math.max(maxB, f.pos[i * 3 + b]);
        }
        double e = 1e-4;
        int[] at = {-1, -1, -1, -1};
        int[][] corners = CORNERS[dir.get3DDataValue()];
        for (int i = 0; i < n; i++) {
            double va = f.pos[i * 3 + a];
            double vb = f.pos[i * 3 + b];
            int ca = Math.abs(va - minA) < e ? 0 : Math.abs(va - maxA) < e ? 1 : -1;
            int cb = Math.abs(vb - minB) < e ? 0 : Math.abs(vb - maxB) < e ? 1 : -1;
            if (ca < 0 || cb < 0) {
                return null;
            }
            int slot = -1;
            for (int k = 0; k < 4; k++) {
                if (corners[k][0] == ca && corners[k][1] == cb) slot = k;
            }
            if (at[slot] >= 0) {
                return null;
            }
            at[slot] = i;
        }
        int present = 0;
        for (int k = 0; k < 4; k++) {
            if (at[k] >= 0) present++;
        }
        if (present < 3) {
            return null;
        }
        for (int k = 0; k < 4; k++) {
            if (at[k] < 0) {
                at[k] = at[(k + 3) % 4];
            }
        }
        return at;
    }

    private static BakedQuad quad(MeshFace f, TextureAtlasSprite sprite, Direction dir, int bx, int by, int bz, int a, int b, int c, int d, boolean ao) {
        int[] v = new int[32];
        int[] idx = {a, b, c, d};
        int normal = (((int) Math.round(f.nx * 127)) & 0xFF)
            | ((((int) Math.round(f.ny * 127)) & 0xFF) << 8)
            | ((((int) Math.round(f.nz * 127)) & 0xFF) << 16);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        boolean axis = axisAligned(f);
        float dirShade = axis ? 1f : directional(f);
        for (int k = 0; k < 4; k++) {
            int j = idx[k];
            int o = k * 8;
            double y = f.y(j);
            v[o] = Float.floatToRawIntBits((float) ((f.x(j) - bx * 16) / 16.0));
            v[o + 1] = Float.floatToRawIntBits((float) ((y - by * 16) / 16.0));
            v[o + 2] = Float.floatToRawIntBits((float) ((f.z(j) - bz * 16) / 16.0));
            int g = Math.max(0, Math.min(255, Math.round(shade(f, y) * dirShade * 255)));
            v[o + 3] = 0xFF000000 | g << 16 | g << 8 | g;
            double tu = Math.min(1 - UV_INSET, Math.max(UV_INSET, f.u(j)));
            double tv = Math.min(1 - UV_INSET, Math.max(UV_INSET, f.v(j)));
            v[o + 4] = Float.floatToRawIntBits((float) (u0 + (u1 - u0) * tu));
            v[o + 5] = Float.floatToRawIntBits((float) (v0 + (v1 - v0) * tv));
            v[o + 6] = 0;
            v[o + 7] = normal;
        }
        return new BakedQuad(v, -1, dir, sprite, axis, ao);
    }

    // ------------------------------------------------------------------ ghost

    /**
     * Ghost mesh of a key (§9.4): cached by configuration in block units relative to the anchor point, already turned
     * by the orientation; {@link GhostMesh} translates it to the anchor's world point.
     */
    public static GhostQuads ghost(PieceKey key, Tier tier, int state, long variant) {
        PieceBakery b = get();
        PieceKind kind = key.kind();
        GhostKey k = new GhostKey(kind, key.orient(), key.rot(), tier, normState(kind, state), normVariant(kind, variant));
        try {
            return b.ghosts.get(k, () -> b.bakeGhost(key, tier, k.state(), k.variant()));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private GhostQuads bakeGhost(PieceKey key, Tier tier, int state, long variant) {
        List<MeshFace> faces = MeshClipper.splitTiles(PieceMeshes.ghost(key, tier, state, variant));
        List<float[]> quads = new ArrayList<>();
        for (MeshFace f : faces) {
            TextureAtlasSprite sp = this.sprite(tier, f.mat);
            float light = (float) (0.62 + 0.38 * Math.max(0, f.ny) + 0.18 * Math.abs(f.nx) + 0.26 * Math.abs(f.nz));
            int n = f.size();
            int i = 1;
            while (i + 1 < n) {
                int c = i + 1;
                int d = i + 2 < n ? i + 2 : i + 1;
                quads.add(ghostQuad(f, sp, light, 0, i, c, d));
                i += 2;
            }
        }
        float[] data = new float[quads.size() * 24];
        for (int q = 0; q < quads.size(); q++) {
            System.arraycopy(quads.get(q), 0, data, q * 24, 24);
        }
        return new GhostQuads(data, quads.size());
    }

    private static float[] ghostQuad(MeshFace f, TextureAtlasSprite sp, float light, int a, int b, int c, int d) {
        float[] out = new float[24];
        int[] idx = {a, b, c, d};
        for (int k = 0; k < 4; k++) {
            int j = idx[k];
            out[k * 6] = (float) (f.x(j) / 16.0);
            out[k * 6 + 1] = (float) (f.y(j) / 16.0);
            out[k * 6 + 2] = (float) (f.z(j) / 16.0);
            out[k * 6 + 3] = sp.getU0() + (sp.getU1() - sp.getU0()) * (float) Math.min(1 - UV_INSET, Math.max(UV_INSET, f.u(j)));
            out[k * 6 + 4] = sp.getV0() + (sp.getV1() - sp.getV0()) * (float) Math.min(1 - UV_INSET, Math.max(UV_INSET, f.v(j)));
            out[k * 6 + 5] = Math.min(1f, light * shade(f, f.y(j)));
        }
        return out;
    }
}
