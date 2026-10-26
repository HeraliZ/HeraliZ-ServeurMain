package fr.heraliz.crac.build.model;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * A connected set of pieces sharing one lattice. At most one claim (tool cupboard) is attached to it. Every piece of
 * the building has {@code key.origin() == origin}; after a split both buildings keep the origin.
 */
public final class Building {
    /** {@link #claimSeat} when no Claim Block stands on the building. Piece ids start at 1. */
    public static final long NO_SEAT = 0L;

    public final long id;
    /** Lattice origin: block corner at the walking surface of the ground level (the first foundation's anchor). */
    public final BlockPos origin;
    @Nullable
    public BlockPos claimAnchor;
    /** Id of the surface piece under the Claim Block: {@code split()} starts from it and claim links use it. */
    public long claimSeat = NO_SEAT;
    /**
     * Group that last claimed this building. Kept when the claim is destroyed: doors stay locked to its members and
     * they keep the right to demolish. Null while the building was never claimed.
     */
    @Nullable
    public UUID ownerGroup;
    public final LongSet pieces = new LongOpenHashSet();

    public Building(long id, BlockPos origin) {
        this.id = id;
        this.origin = origin.immutable();
    }

    public boolean hasClaim() {
        return this.claimAnchor != null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Id", this.id);
        t.putLong("Origin", this.origin.asLong());
        if (this.claimAnchor != null) {
            t.putLong("Claim", this.claimAnchor.asLong());
        }
        if (this.claimSeat != NO_SEAT) {
            t.putLong("ClaimSeat", this.claimSeat);
        }
        if (this.ownerGroup != null) {
            t.putUUID("OwnerGroup", this.ownerGroup);
        }
        return t;
    }

    public static Building load(CompoundTag t) {
        Building b = new Building(t.getLong("Id"), BlockPos.of(t.getLong("Origin")));
        if (t.contains("Claim", Tag.TAG_LONG)) {
            b.claimAnchor = BlockPos.of(t.getLong("Claim"));
        }
        if (t.contains("ClaimSeat", Tag.TAG_LONG)) {
            b.claimSeat = t.getLong("ClaimSeat");
        }
        if (t.hasUUID("OwnerGroup")) {
            b.ownerGroup = t.getUUID("OwnerGroup");
        }
        return b;
    }
}
