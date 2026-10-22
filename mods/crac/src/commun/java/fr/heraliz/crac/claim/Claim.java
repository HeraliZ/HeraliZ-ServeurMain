package fr.heraliz.crac.claim;

import java.util.UUID;
import fr.heraliz.crac.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class Claim {
    private final UUID groupId;
    private final BlockPos anchor;
    private int tier;

    public Claim(UUID groupId, BlockPos anchor, int tier) {
        this.groupId = groupId;
        this.anchor = anchor.immutable();
        this.tier = clampTier(tier);
    }

    public UUID groupId() {
        return this.groupId;
    }

    public BlockPos anchor() {
        return this.anchor;
    }

    public BlockPos center() {
        return this.anchor;
    }

    public int tier() {
        return this.tier;
    }

    public void setTier(int t) {
        this.tier = clampTier(t);
    }

    public int radius() {
        return Config.claimRadius(this.tier);
    }

    public int yRadius() {
        return Config.claimYRadius(this.tier);
    }

    public boolean contains(BlockPos pos) {
        int r = this.radius();
        int yr = this.yRadius();
        return Math.abs(pos.getX() - this.anchor.getX()) <= r
            && Math.abs(pos.getZ() - this.anchor.getZ()) <= r
            && Math.abs(pos.getY() - this.anchor.getY()) <= yr;
    }

    public static int clampTier(int t) {
        return Math.max(1, Math.min(4, t));   // 4 : dernier palier « base » de l'arbre du clan (HeraliZ)
    }

    /**
     * Where two zones overlap (only after a radius was raised), whether the one whose Claim Block is at {@code a} owns
     * the block rather than the one at {@code b}: the nearest Claim Block wins, ties by position. Server and client
     * resolve overlaps with this same rule.
     */
    public static boolean nearer(BlockPos a, BlockPos b, BlockPos pos) {
        long da = distSq(a, pos);
        long db = distSq(b, pos);
        return da < db || da == db && a.asLong() < b.asLong();
    }

    private static long distSq(BlockPos anchor, BlockPos pos) {
        long dx = anchor.getX() - pos.getX();
        long dy = anchor.getY() - pos.getY();
        long dz = anchor.getZ() - pos.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Group", this.groupId);
        tag.putLong("Anchor", this.anchor.asLong());
        tag.putInt("Tier", this.tier);
        return tag;
    }

    public static Claim load(CompoundTag tag) {
        return new Claim(tag.getUUID("Group"), BlockPos.of(tag.getLong("Anchor")), tag.getInt("Tier"));
    }
}
