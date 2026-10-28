package fr.heraliz.crac.build.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceSolids;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** A placed building block. Server-side source of truth, owned by {@code StructureStore}. */
public final class Piece {
    public static final int STATE_OPEN = 1;
    /**
     * Doors only (HeraliZ, CRac 1.10.1; the insert bits are never used by doors): an open door of an airlock whose other
     * door is open too. It still looks open, but collides like a closed leaf: nobody walks straight through the airlock.
     */
    public static final int STATE_SAS = 0b010;
    public static final int INSERT_SHIFT = 1;
    public static final int INSERT_MASK = 0b110;
    public static final int INSERT_NONE = 0;
    public static final int INSERT_BARS = 1;
    public static final int INSERT_GLASS = 2;
    /** Deepest foundation skirt a saved piece may have (the upper bound of {@code foundationMaxHeight}). */
    public static final int MAX_SKIRT = 16;
    private static final byte[] NO_SKIRT = new byte[0];

    public final long id;
    public final PieceKey key;
    public long buildingId;
    public Tier tier;
    public int hp;
    public int maxHp;
    public final UUID owner;
    /** Game time of placement and of the last upgrade; used for the demolish window. */
    public long placedAt;
    public long modifiedAt;
    /** Door open bit and window insert, see the STATE_/INSERT_ constants. */
    public int state;
    /**
     * Foundation skirt depth (blocks below the slab down to the ground) per column, aligned with
     * {@link Footprints#columns(PieceKey)}; empty for other kinds. Never changes once the piece exists.
     */
    public final byte[] skirt;
    /**
     * The blocks actually written for this piece (absolute): the reserved blocks placement kept, buried wall halves
     * left out. Set once when the piece is committed; persisted, so tuning a geometry constant never moves them.
     */
    public List<BlockPos> blocks = List.of();

    /** Recomputed, not saved: support value 0..100 and the neighbour-dependent shape variant (posts, roofs). */
    public transient int stability;
    public transient long variant;

    /** Derived from the immutable key on first use. */
    private volatile AABB bounds;
    private volatile BlockPos centre;

    public Piece(long id, PieceKey key, long buildingId, Tier tier, int maxHp, UUID owner, long now, @Nullable byte[] skirt) {
        this.id = id;
        this.key = key;
        this.buildingId = buildingId;
        this.tier = tier;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.owner = owner;
        this.placedAt = now;
        this.modifiedAt = now;
        int columns = key.kind().isFoundation() ? Footprints.columns(key).size() : 0;
        this.skirt = skirt != null && skirt.length == columns ? skirt : columns == 0 ? NO_SKIRT : new byte[columns];
    }

    public PieceKey key() {
        return this.key;
    }

    public PieceKind kind() {
        return this.key.kind();
    }

    /** World box (blocks) of everything the piece can occupy, skirt excluded. */
    public AABB bounds() {
        AABB b = this.bounds;
        if (b == null) {
            b = PieceSolids.worldBounds(this.key);
            this.bounds = b;
        }
        return b;
    }

    /** A block the piece covers near its centre ({@link PieceKey#centreBlock}), cached: the key never changes. */
    public BlockPos centreBlock() {
        BlockPos c = this.centre;
        if (c == null) {
            c = this.key.centreBlock();
            this.centre = c;
        }
        return c;
    }

    /** L'état tel qu'on le voit et le vise : une porte de sas bloquée reste une porte ouverte (seul le passage est fermé). */
    public static int etatVisible(int state) {
        return state & ~STATE_SAS;
    }

    /**
     * {@link #etatVisible(int)} pour une pièce de type {@code kind} (HeraliZ, 28/10) : le bit {@link #STATE_SAS} est le
     * même que celui des barreaux d'une fenêtre ({@code INSERT_BARS << INSERT_SHIFT}) ; l'effacer sur une fenêtre lui
     * retirait ses barreaux de la visée et de l'occlusion. Seules les portes ont un état de sas.
     */
    public static int etatVisible(PieceKind kind, int state) {
        return kind.hasDoor() ? state & ~STATE_SAS : state;
    }

    public boolean isSasBloque() {
        return this.kind().hasDoor() && (this.state & STATE_SAS) != 0;
    }

    public void setSasBloque(boolean bloque) {
        this.state = bloque ? this.state | STATE_SAS : this.state & ~STATE_SAS;
    }

    public boolean isOpen() {
        return (this.state & STATE_OPEN) != 0;
    }

    public void setOpen(boolean open) {
        this.state = open ? this.state | STATE_OPEN : this.state & ~STATE_OPEN;
    }

    public int insert() {
        return (this.state & INSERT_MASK) >> INSERT_SHIFT;
    }

    public void setInsert(int insert) {
        this.state = (this.state & ~INSERT_MASK) | ((insert << INSERT_SHIFT) & INSERT_MASK);
    }

    /** 0 = intact .. 3 = badly damaged. */
    public int crackStage() {
        if (this.maxHp <= 0 || this.hp >= this.maxHp) {
            return 0;
        }
        double r = (double) this.hp / this.maxHp;
        if (r > 0.75) return 0;
        if (r > 0.5) return 1;
        return r > 0.25 ? 2 : 3;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Id", this.id);
        this.key.save(t);
        t.putLong("Building", this.buildingId);
        t.putString("Tier", this.tier.id);
        t.putInt("Hp", this.hp);
        t.putInt("MaxHp", this.maxHp);
        t.putUUID("Owner", this.owner);
        t.putLong("PlacedAt", this.placedAt);
        t.putLong("ModifiedAt", this.modifiedAt);
        t.putInt("State", this.state);
        t.putByteArray("Skirt", this.skirt);
        BlockPos anchor = this.key.anchorBlock();
        long[] packed = new long[this.blocks.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = packOffset(anchor, this.blocks.get(i));
        }
        t.putLongArray("Blocks", packed);
        return t;
    }

    /** {@code (dx+2048)<<24 | (dy+2048)<<12 | (dz+2048)} for a block near the anchor block (|d| < 2048). */
    public static long packOffset(BlockPos anchor, BlockPos pos) {
        int dx = pos.getX() - anchor.getX();
        int dy = pos.getY() - anchor.getY();
        int dz = pos.getZ() - anchor.getZ();
        if (Math.abs(dx) >= 2048 || Math.abs(dy) >= 2048 || Math.abs(dz) >= 2048) {
            throw new IllegalArgumentException("block " + pos + " too far from anchor " + anchor);
        }
        return (long) (dx + 2048) << 24 | (long) (dy + 2048) << 12 | (dz + 2048);
    }

    public static BlockPos unpackOffset(BlockPos anchor, long v) {
        return anchor.offset((int) (v >> 24 & 0xFFF) - 2048, (int) (v >> 12 & 0xFFF) - 2048, (int) (v & 0xFFF) - 2048);
    }

    /** Returns null for an entry this version cannot use: unknown kind or tier, malformed key, no owner, no blocks. */
    @Nullable
    public static Piece load(CompoundTag t) {
        PieceKey key = PieceKey.load(t);
        Tier tier = Tier.byId(t.getString("Tier"));
        if (key == null || tier == null || !t.hasUUID("Owner") || !t.contains("Id", Tag.TAG_LONG)) {
            return null;
        }
        long[] packed = t.getLongArray("Blocks");
        if (packed.length == 0 || packed.length > 4096) {
            return null;
        }
        byte[] skirt = t.getByteArray("Skirt");
        for (int i = 0; i < skirt.length; i++) {
            skirt[i] = (byte) Math.max(0, Math.min(MAX_SKIRT, skirt[i]));
        }
        int maxHp = Math.max(1, t.getInt("MaxHp"));
        Piece p = new Piece(t.getLong("Id"), key, t.getLong("Building"), tier, maxHp, t.getUUID("Owner"), t.getLong("PlacedAt"), skirt);
        p.hp = Math.max(1, Math.min(maxHp, t.getInt("Hp")));
        p.modifiedAt = t.getLong("ModifiedAt");
        p.state = t.getInt("State") & 0b111;
        BlockPos anchor = key.anchorBlock();
        List<BlockPos> blocks = new ArrayList<>(packed.length);
        for (long v : packed) {
            if ((v & ~0xF_FFFF_FFFFL) != 0) {
                return null;
            }
            BlockPos b = unpackOffset(anchor, v);
            if (!blocks.contains(b)) {
                blocks.add(b);
            }
        }
        p.blocks = List.copyOf(blocks);
        return p;
    }
}
