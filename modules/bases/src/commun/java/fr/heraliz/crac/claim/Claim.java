package fr.heraliz.crac.claim;

import java.util.UUID;
import fr.heraliz.crac.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class Claim {
    private final UUID groupId;
    private final BlockPos anchor;
    private int tier;
    /**
     * HeraliZ (03/11, demande du propriétaire) : home de la base, posé par un membre avec la commande du home
     * ({@code Homes.COMMANDE_POSER}) : pieds du joueur, regard et nom ; null tant qu'aucun n'est posé. Plus aucun home créé
     * tout seul à la pose de l'armoire.
     */
    @Nullable
    private Vec3 home;
    private float homeLacet;
    private String homeNom = "";
    /**
     * HeraliZ (03/11) : base posée avant le home à poser soi-même : son home reste celui créé tout seul, devant l'armoire,
     * tant qu'aucun home n'est posé (choix du propriétaire : on le garde). Faux pour toute base posée depuis.
     */
    private boolean homeArmoire;

    public Claim(UUID groupId, BlockPos anchor, int tier) {
        this.groupId = groupId;
        this.anchor = anchor.immutable();
        this.tier = clampTier(tier);
    }

    /** HeraliZ (03/11) : le home posé de la base (pieds), ou null. */
    @Nullable
    public Vec3 home() {
        return this.home;
    }

    public float homeLacet() {
        return this.homeLacet;
    }

    /** Nom donné au home (vide : sans nom). */
    public String homeNom() {
        return this.homeNom;
    }

    /** HeraliZ (03/11) : sans home posé, le home de cette base d'avant le 03/11 est devant l'armoire. */
    public boolean homeArmoire() {
        return this.home == null && this.homeArmoire;
    }

    /** La base a un home : posé, ou devant l'armoire pour une base d'avant le 03/11. */
    public boolean aUnHome() {
        return this.home != null || this.homeArmoire;
    }

    /** HeraliZ (03/11) : pose le home de la base (remplace l'ancien, et celui devant l'armoire). */
    public void setHome(Vec3 pieds, float lacet, String nom) {
        this.home = pieds;
        this.homeLacet = lacet;
        this.homeNom = nom == null ? "" : nom;
        this.homeArmoire = false;
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
        if (this.home != null) {
            CompoundTag h = new CompoundTag();
            h.putDouble("X", this.home.x);
            h.putDouble("Y", this.home.y);
            h.putDouble("Z", this.home.z);
            h.putFloat("Lacet", this.homeLacet);
            h.putString("Nom", this.homeNom);
            tag.put("Home", h);
        }
        tag.putBoolean("HomeArmoire", this.homeArmoire);
        return tag;
    }

    public static Claim load(CompoundTag tag) {
        Claim c = new Claim(tag.getUUID("Group"), BlockPos.of(tag.getLong("Anchor")), tag.getInt("Tier"));
        if (tag.contains("Home", Tag.TAG_COMPOUND)) {
            CompoundTag h = tag.getCompound("Home");
            c.setHome(new Vec3(h.getDouble("X"), h.getDouble("Y"), h.getDouble("Z")), h.getFloat("Lacet"), h.getString("Nom"));
        }
        // HeraliZ (03/11) : un claim écrit avant le home à poser soi-même garde son home devant l'armoire
        c.homeArmoire = c.home == null && (!tag.contains("HomeArmoire") || tag.getBoolean("HomeArmoire"));
        return c;
    }
}
