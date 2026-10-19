package fr.heraliz.crac.build.world;

import fr.heraliz.crac.build.geom.PieceShapes;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * What one structure block knows about a piece passing through it: the piece's full key (the client and the chunk
 * render threads have no store), tier, state, variant and crack stage. Value equality on integers only (integrity
 * compares occupant lists).
 */
public record Occupant(long pieceId, long buildingId, PieceKey key, Tier tier, int state, long variant, int crack) {
    /** Longs per occupant in the block entity NBT ({@code Occ3}). */
    public static final int STRIDE = 6;

    public static Occupant of(Piece p) {
        return new Occupant(p.id, p.buildingId, p.key(), p.tier, p.state, p.variant, p.crackStage());
    }

    public PieceKind kind() {
        return this.key.kind();
    }

    /** Whether this occupant closes block faces for light and face culling: twig walls are open lattices. */
    public boolean occludes() {
        return !(this.tier == Tier.TWIG && this.key.kind().isWall());
    }

    /** Forme de visée (contour, clic) : celle de la collision, sauf qu'une porte de sas bloquée se vise ouverte. */
    public VoxelShape visee(BlockPos pos) {
        return PieceShapes.shape(this.key, Piece.etatVisible(this.state), this.variant, pos);
    }

    /** Collision shape of the piece inside the world block {@code pos}. */
    public VoxelShape shape(BlockPos pos) {
        return PieceShapes.shape(this.key, this.state, this.variant, pos);
    }

    /** Occlusion shape (inward raster) of the piece inside the world block {@code pos}. */
    public VoxelShape occlusion(BlockPos pos) {
        // une porte de sas bloquée cache ce qu'une porte ouverte cache (rien de plus : sinon des faces disparaissent)
        return PieceShapes.occlusion(this.key, Piece.etatVisible(this.state), this.variant, pos);
    }

    /**
     * Writes {@link #STRIDE} longs at {@code at}: id, building, origin, lattice (4 x i16), meta (kind 5 | tier 3 |
     * state 3 | crack 2 | rot 4 | orient 4 | h i16), variant.
     */
    public void pack(long[] out, int at) {
        LPoint a = this.key.anchor();
        out[at] = this.pieceId;
        out[at + 1] = this.buildingId;
        out[at + 2] = this.key.originKey();
        out[at + 3] = (a.xa() & 0xFFFFL) | (a.xb() & 0xFFFFL) << 16 | (a.za() & 0xFFFFL) << 32 | (a.zb() & 0xFFFFL) << 48;
        out[at + 4] = (long) this.key.kind().ordinal()
            | (long) this.tier.ordinal() << 5
            | (long) (this.state & 7) << 8
            | (long) (this.crack & 3) << 11
            | (long) (this.key.rot() & 15) << 13
            | (long) (this.key.orient() & 15) << 17
            | (this.key.h() & 0xFFFFL) << 21;
        out[at + 5] = this.variant;
    }

    /**
     * Reads what {@link #pack} wrote. Returns null for an unknown kind or tier or any field out of range (data from
     * another version, corrupt or hand-made block entity data), which would otherwise break the shape and mesh code.
     */
    @Nullable
    public static Occupant unpack(long[] in, int at) {
        long lat = in[at + 3];
        long meta = in[at + 4];
        if ((meta >>> 37) != 0) {
            return null;
        }
        PieceKind kind = PieceKind.byOrdinal((int) (meta & 31));
        int tierOrd = (int) (meta >> 5 & 7);
        Tier tier = tierOrd < Tier.values().length ? Tier.byOrdinal(tierOrd) : null;
        int xa = (short) (lat & 0xFFFF);
        int xb = (short) (lat >> 16 & 0xFFFF);
        int za = (short) (lat >> 32 & 0xFFFF);
        int zb = (short) (lat >> 48 & 0xFFFF);
        int rot = (int) (meta >> 13 & 15);
        int orient = (int) (meta >> 17 & 15);
        int h = (short) (meta >> 21 & 0xFFFF);
        if (tier == null || !PieceKey.isWellFormed(kind, xa, xb, za, zb, h, orient, rot)) {
            return null;
        }
        PieceKey key = new PieceKey(kind, BlockPos.of(in[at + 2]), new LPoint(xa, xb, za, zb), h, orient, rot);
        return new Occupant(in[at], in[at + 1], key, tier, (int) (meta >> 8 & 7), in[at + 5], (int) (meta >> 11 & 3));
    }
}
