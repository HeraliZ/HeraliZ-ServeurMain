package fr.heraliz.crac.build.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import fr.heraliz.crac.build.geom.PieceSolids;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Exact description of a piece of a building: what it is, where it sits on the lattice and how it is turned.
 * <ul>
 * <li>SURFACE / VOLUME: {@code (anchor, orient)} is the canonical footprint of the kind's shape (the vertex whose
 * outgoing direction is {@code orient}, 0..2 for squares, 0..3 for triangles). Volume {@code rot}: bits 0-1 quarter
 * turn, bit 2 mirror (L stairs) or half rise (ramp); triangle ramp: low corner 0..2, + 3 for a half rise. Surfaces have
 * {@code rot = 0}.</li>
 * <li>WALL: {@code (anchor, orient)} is the canonical edge (direction 0..5). {@code rot} bit 0 flips the inside face
 * (door swing side), bit 1 mirrors the hinge.</li>
 * </ul>
 * The compact constructor canonicalizes {@code anchor}/{@code orient} and rewrites {@code rot} with the frame, so
 * every description of the same physical piece gives the same key and the same solids.
 *
 * @param origin building origin: block corner at the walking surface of the ground level
 * @param h      base height in 24 px units: the piece's base is at {@code y = 24 * h} px in the building frame
 */
public record PieceKey(PieceKind kind, BlockPos origin, LPoint anchor, int h, int orient, int rot) {
    /** Largest |lattice coordinate| a stored key may carry. */
    public static final int LATTICE_MAX = 4096;
    /** Height of one h unit in px (half a wall). */
    public static final double UNIT_H = 24.0;

    public PieceKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(anchor, "anchor");
        origin = origin.immutable();
        int k = Dirs.norm(orient);
        int r = Math.floorMod(rot, kind.rotations);
        switch (kind.shape) {
            case SQUARE -> {
                // the given frame is the canonical one turned by j quarter turns: the canonical anchor is given
                // vertex (4 - j) mod 4, and a volume's quarter turn grows by j (the mirror applies before turning)
                int o = k % 3;
                int j = k / 3;
                for (int m = 0; m < ((4 - j) & 3); m++) {
                    anchor = anchor.plus(k + 3 * m);
                }
                if (kind.isVolume()) {
                    r = (r & ~3) | ((r + j) & 3);
                }
                orient = o;
            }
            case TRIANGLE -> {
                int o = k % 4;
                int j = k / 4;
                for (int m = 0; m < (3 - j) % 3; m++) {
                    anchor = anchor.plus(k + 4 * m);
                }
                if (kind.isVolume()) {
                    // given vertex i is canonical vertex (i + j) mod 3: the low corner of a triangle ramp follows
                    r = Math.floorMod(r, kind.rotations);
                    r = r / 3 * 3 + (r % 3 + j) % 3;
                }
                orient = o;
            }
            case EDGE -> {
                if (k >= 6) {
                    // seen from the other end: u -> 48 - u mirrors the hinge and n -> -n flips the facing
                    anchor = anchor.plus(k);
                    k -= 6;
                    r ^= 3;
                }
                orient = k;
            }
        }
        rot = Math.floorMod(r, kind.rotations);
    }

    // ---------------------------------------------------------------- factories

    /** A square surface or volume whose footprint has vertex {@code v} with outgoing direction {@code k}. */
    public static PieceKey square(PieceKind kind, BlockPos origin, LPoint v, int k, int h, int rot) {
        require(kind, PieceKind.Shape.SQUARE);
        return new PieceKey(kind, origin, v, h, k, rot);
    }

    /** A triangle surface whose footprint has vertex {@code v} with outgoing direction {@code k}. */
    public static PieceKey triangle(PieceKind kind, BlockPos origin, LPoint v, int k, int h) {
        require(kind, PieceKind.Shape.TRIANGLE);
        return new PieceKey(kind, origin, v, h, k, 0);
    }

    /** A wall on the edge from {@code p} in direction {@code k} (any of the 12 directions). */
    public static PieceKey wall(PieceKind kind, BlockPos origin, LPoint p, int k, int h, int rot) {
        require(kind, PieceKind.Shape.EDGE);
        return new PieceKey(kind, origin, p, h, k, rot);
    }

    /** The piece of the given kind on a canonical footprint. */
    public static PieceKey on(PieceKind kind, BlockPos origin, FootprintKey fp, int h, int rot) {
        require(kind, fp.shape());
        return new PieceKey(kind, origin, fp.anchor(), h, fp.orient(), rot);
    }

    /** A wall on a canonical edge. */
    public static PieceKey on(PieceKind kind, BlockPos origin, EdgeKey edge, int h, int rot) {
        require(kind, PieceKind.Shape.EDGE);
        return new PieceKey(kind, origin, edge.start(), h, edge.dir6(), rot);
    }

    private static void require(PieceKind kind, PieceKind.Shape shape) {
        if (kind.shape != shape) {
            throw new IllegalArgumentException(kind + " is not a " + shape + " piece");
        }
    }

    /** The same socket with another kind of the same shape and another rotation. */
    public PieceKey with(PieceKind kind, int rot) {
        require(kind, this.kind.shape);
        return new PieceKey(kind, this.origin, this.anchor, this.h, this.orient, rot);
    }

    public PieceKey withRot(int rot) {
        return new PieceKey(this.kind, this.origin, this.anchor, this.h, this.orient, rot);
    }

    public PieceKey withH(int h) {
        return new PieceKey(this.kind, this.origin, this.anchor, h, this.orient, this.rot);
    }

    // ---------------------------------------------------------------- validation

    /** Whether stored fields (NBT, packets, block entity data) describe a canonical key this version can use. */
    public static boolean isWellFormed(@Nullable PieceKind kind, int xa, int xb, int za, int zb, int h, int orient, int rot) {
        return kind != null
            && orient >= 0 && orient < kind.shape.orients
            && Math.abs(xa) <= LATTICE_MAX && Math.abs(xb) <= LATTICE_MAX
            && Math.abs(za) <= LATTICE_MAX && Math.abs(zb) <= LATTICE_MAX
            && Math.abs(h) <= PieceKind.H_MAX
            && rot >= 0 && rot < kind.rotations;
    }

    public boolean isWellFormed() {
        return isWellFormed(this.kind, this.anchor.xa(), this.anchor.xb(), this.anchor.za(), this.anchor.zb(), this.h, this.orient, this.rot);
    }

    // ---------------------------------------------------------------- lattice

    public long originKey() {
        return this.origin.asLong();
    }

    /** Building direction of edge i (from vertex i to vertex i + 1; for walls the wall's direction). */
    public int edgeDir(int i) {
        return Dirs.norm(this.orient + this.kind.shape.turn * i);
    }

    /** Exact vertices in canonical order (footprints turn by +steps; walls: end A then end B). */
    public LPoint[] lattice() {
        int n = this.kind.shape.vertices;
        LPoint[] out = new LPoint[n];
        out[0] = this.anchor;
        for (int i = 1; i < n; i++) {
            out[i] = out[i - 1].plus(this.edgeDir(i - 1));
        }
        return out;
    }

    public List<VertexKey> vertices() {
        long o = this.originKey();
        List<VertexKey> out = new ArrayList<>(4);
        for (LPoint p : this.lattice()) {
            out.add(new VertexKey(o, p));
        }
        return out;
    }

    /** Canonical edges: the footprint's edges in vertex order, or the wall's own edge. */
    public List<EdgeKey> edges() {
        long o = this.originKey();
        if (this.kind.isWall()) {
            return List.of(new EdgeKey(o, this.anchor, this.orient));
        }
        LPoint[] v = this.lattice();
        List<EdgeKey> out = new ArrayList<>(v.length);
        for (int i = 0; i < v.length; i++) {
            out.add(EdgeKey.of(o, v[i], this.edgeDir(i)));
        }
        return out;
    }

    /** The wall's edge. */
    public EdgeKey edgeKey() {
        if (!this.kind.isWall()) {
            throw new IllegalStateException(this.kind + " has no edge key");
        }
        return new EdgeKey(this.originKey(), this.anchor, this.orient);
    }

    /** The footprint of a surface or volume; null for walls. */
    @Nullable
    public FootprintKey footprint() {
        return this.kind.isWall() ? null : new FootprintKey(this.originKey(), this.kind.shape, this.anchor, this.orient);
    }

    // ---------------------------------------------------------------- heights

    /** Top above the base in 24 px units; a half ramp (rot bit 2) rises one unit only. */
    public int topUnits() {
        return this.halfRamp() ? 1 : this.kind.topUnits();
    }

    /** A ramp rising one unit only: RAMP with rot bit 2, TRI_RAMP with rot 3..5. */
    public boolean halfRamp() {
        return this.kind == PieceKind.RAMP ? (this.rot & 4) != 0 : this.kind == PieceKind.TRI_RAMP && this.rot >= 3;
    }

    /** Height h of the top of the piece (where a wall or floor resting on it starts). */
    public int top() {
        return this.h + this.topUnits();
    }

    /** Base height in building px. */
    public double baseY() {
        return UNIT_H * this.h;
    }

    // ---------------------------------------------------------------- positions

    /** Centre of the piece in building px: footprint centroid or edge midpoint, middle of its height. */
    public Vec3 centre() {
        double x = 0;
        double z = 0;
        LPoint[] v = this.lattice();
        for (LPoint p : v) {
            x += p.toX();
            z += p.toZ();
        }
        double dy = switch (this.kind) {
            case FOUNDATION, TRI_FOUNDATION -> -8.0;
            case FLOOR, TRI_FLOOR -> -1.5;
            case ROOF, TRI_ROOF -> 10.5;
            default -> 12.0 * this.topUnits();
        };
        return new Vec3(x / v.length, this.baseY() + dy, z / v.length);
    }

    /** Centre in world coordinates (blocks). */
    public Vec3 centreWorld() {
        Vec3 c = this.centre();
        return new Vec3(this.origin.getX() + c.x / 16.0, this.origin.getY() + c.y / 16.0, this.origin.getZ() + c.z / 16.0);
    }

    /** The block containing the anchor's world point at the base height. */
    public BlockPos anchorBlock() {
        return new BlockPos(
            this.origin.getX() + (int) Math.floor(this.anchor.toX() / 16.0),
            this.origin.getY() + Math.floorDiv(24 * this.h, 16),
            this.origin.getZ() + (int) Math.floor(this.anchor.toZ() / 16.0));
    }

    /** A block the piece occupies near its centre (the reserved block closest to {@link #centre()}). */
    public BlockPos centreBlock() {
        return PieceSolids.centreBlock(this);
    }

    // ---------------------------------------------------------------- wire and NBT

    /** {@code [u8 kind][long origin][4 x i16 lattice][i16 h][u8 orient][u8 rot]}. */
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(this.kind.ordinal());
        buf.writeLong(this.originKey());
        buf.writeShort(this.anchor.xa());
        buf.writeShort(this.anchor.xb());
        buf.writeShort(this.anchor.za());
        buf.writeShort(this.anchor.zb());
        buf.writeShort(this.h);
        buf.writeByte(this.orient);
        buf.writeByte(this.rot);
    }

    /** Returns null when the buffer holds an unknown kind or a field out of range. */
    @Nullable
    public static PieceKey read(FriendlyByteBuf buf) {
        PieceKind kind = PieceKind.byOrdinal(buf.readUnsignedByte());
        BlockPos origin = BlockPos.of(buf.readLong());
        int xa = buf.readShort();
        int xb = buf.readShort();
        int za = buf.readShort();
        int zb = buf.readShort();
        int h = buf.readShort();
        int orient = buf.readUnsignedByte();
        int rot = buf.readUnsignedByte();
        if (!isWellFormed(kind, xa, xb, za, zb, h, orient, rot)) {
            return null;
        }
        return new PieceKey(kind, origin, new LPoint(xa, xb, za, zb), h, orient, rot);
    }

    /** Writes Kind, Origin, Lattice, H, Orient, Rot into {@code t}. */
    public void save(CompoundTag t) {
        t.putString("Kind", this.kind.id);
        t.putLong("Origin", this.originKey());
        t.putIntArray("Lattice", this.anchor.toArray());
        t.putInt("H", this.h);
        t.putByte("Orient", (byte) this.orient);
        t.putByte("Rot", (byte) this.rot);
    }

    /** Reads what {@link #save} wrote; null for an unknown kind or any malformed field. */
    @Nullable
    public static PieceKey load(CompoundTag t) {
        PieceKind kind = PieceKind.byId(t.getString("Kind"));
        if (kind == null || !t.contains("Origin", Tag.TAG_LONG) || !t.contains("Lattice", Tag.TAG_INT_ARRAY)) {
            return null;
        }
        int[] l = t.getIntArray("Lattice");
        if (l.length != 4) {
            return null;
        }
        int h = t.getInt("H");
        int orient = t.getByte("Orient");
        int rot = t.getByte("Rot");
        if (!isWellFormed(kind, l[0], l[1], l[2], l[3], h, orient, rot)) {
            return null;
        }
        return new PieceKey(kind, BlockPos.of(t.getLong("Origin")), new LPoint(l[0], l[1], l[2], l[3]), h, orient, rot);
    }
}
