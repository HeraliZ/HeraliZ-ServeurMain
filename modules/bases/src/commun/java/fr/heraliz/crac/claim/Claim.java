package fr.heraliz.crac.claim;

import java.util.Collection;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.PieceKey;
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

    /** HeraliZ (11/11) : la zone tient la pièce {@code key} qui prend les blocs {@code blocs} ({@link #tientPiece(BlockPos, int, int, PieceKey, Collection)}). */
    public boolean tientPiece(PieceKey key, Collection<BlockPos> blocs) {
        return tientPiece(this.anchor, this.radius(), this.yRadius(), key, blocs);
    }

    /** Jeu (blocs) des bouts d'un mur comparés au bord de la zone : un bout de mur en biais ne tombe jamais pile sur le bord. */
    private static final double JEU = 1.0E-6;

    /**
     * HeraliZ (11/11, retour du propriétaire : « faire en sorte qu'on puisse build jusqu'à l'extrémité maximum de la zone
     * définie ») : la zone de l'armoire posée en {@code centre} (les blocs à {@code rayon} au plus de lui en x et en z, à
     * {@code rayonY} au plus en hauteur ; son bord est la ligne dessinée plan en main, {@code client/ZoneArmoire}) tient la
     * pièce {@code key} qui prend les blocs {@code blocs}. Mêmes règles au serveur et dans le fantôme du plan.
     * <ul>
     * <li>Fondation, plancher, toit, escalier, rampe : tous leurs blocs dans la zone, comme avant (ils suivent leur contour,
     * jamais au-delà).</li>
     * <li>Mur, demi-mur, porte, fenêtre : jugé sur le bord où il se pose, ses deux bouts dans le carré dessiné, ligne
     * comprise. Avant, tous ses blocs devaient y être ; or un mur fait 4 px d'épaisseur centrés sur son bord (et une porte
     * ouverte va jusqu'à 16 px de lui) : posé sur le dernier bord de la zone, sur la ligne dessinée, sa moitié extérieure
     * prenait la rangée de blocs juste derrière la ligne et il était refusé (« Hors de la zone du Claim Block »).</li>
     * <li>En hauteur, rien ne change : tous les blocs de la pièce dans la zone.</li>
     * </ul>
     */
    public static boolean tientPiece(BlockPos centre, int rayon, int rayonY, PieceKey key, Collection<BlockPos> blocs) {
        boolean mur = key.kind().isWall();
        for (BlockPos b : blocs) {
            if (Math.abs(b.getY() - centre.getY()) > rayonY) {
                return false;
            }
            if (!mur && (Math.abs(b.getX() - centre.getX()) > rayon || Math.abs(b.getZ() - centre.getZ()) > rayon)) {
                return false;
            }
        }
        if (mur) {
            // le carré dessiné, en blocs : du bord ouest (nord) du bloc centre - rayon au bord est (sud) du bloc centre + rayon
            double x0 = centre.getX() - rayon - JEU;
            double x1 = centre.getX() + rayon + 1 + JEU;
            double z0 = centre.getZ() - rayon - JEU;
            double z1 = centre.getZ() + rayon + 1 + JEU;
            BlockPos o = key.origin();
            for (LPoint p : key.lattice()) {
                double x = o.getX() + p.toX() / 16.0;
                double z = o.getZ() + p.toZ() / 16.0;
                if (x < x0 || x > x1 || z < z0 || z > z1) {
                    return false;
                }
            }
        }
        return true;
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
