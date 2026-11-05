package fr.heraliz.crac.block.entity;

import fr.heraliz.crac.block.EchelleBlock;
import fr.heraliz.crac.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Partie du bas d'une échelle ({@link EchelleBlock}) : ses points de vie (150 comme la barricade, une seule réserve pour les
 * trois cases) et, depuis la pose libre (05/11), sa vraie place : le point d'appui sur la surface visée et la direction
 * vers laquelle elle regarde (n'importe quel angle : murs en biais compris), plus la case de la pièce qui la porte.
 * <ul>
 *   <li>{@code S} : point d'appui (milieu du dos de l'échelle, contre la surface), en blocs depuis le coin de la case du
 *   bas ; il peut sortir de la case (l'échelle est dessinée où elle est, ses cases sont là où se tient le grimpeur).</li>
 *   <li>{@code N} : direction (horizontale, unitaire) vers laquelle l'échelle regarde, du mur vers le grimpeur.</li>
 *   <li>Échelle posée avant le 05/11 (rien de tout ça gardé) : place d'avant, d'après son sens (FACING), contre le mur de
 *   sa case.</li>
 * </ul>
 * Dessinée par {@code client/render/EchelleRendu} ; envoyée aux joueurs avec le tronçon et à la pose.
 */
public class EchelleBlockEntity extends BlockEntity {
    public static final float PV_MAX = 150.0F;
    /** Épaisseur et demi-largeur du modèle (px) : rails de z 10,75 à 14, de x 1,25 à 14,75 (modèle tourné vers le nord). */
    public static final double EPAISSEUR_MODELE = 3.25, DEMI_LARGEUR = 6.75;
    private float pv = PV_MAX;
    private boolean libre;
    private double sx, sz, nx, nz;
    @Nullable
    private BlockPos appui;
    /** Formes des trois cases (gardées : lues à chaque image et chaque mouvement). */
    @Nullable
    private VoxelShape[] formes;

    public EchelleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ECHELLE.get(), pos, state);
    }

    public float pv() {
        return this.pv;
    }

    /** Retire {@code degats} points de vie ; rend ceux qui restent. */
    public float abimer(float degats) {
        this.pv = Math.max(0F, this.pv - Math.max(0F, degats));
        this.setChanged();
        return this.pv;
    }

    /** Pose libre (05/11) : place de l'échelle et case de la pièce qui la porte. */
    public void placer(double sx, double sz, double nx, double nz, BlockPos appui) {
        this.libre = true;
        this.sx = sx;
        this.sz = sz;
        this.nx = nx;
        this.nz = nz;
        this.appui = appui.immutable();
        this.formes = null;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /** Case de la pièce qui porte l'échelle (pose libre), sinon null (échelle d'avant : le mur de sa case). */
    @Nullable
    public BlockPos appui() {
        return this.libre ? this.appui : null;
    }

    /** Point d'appui {x, z} en blocs depuis le coin de la case du bas, et direction {nx, nz} vers le grimpeur. */
    public double[] place() {
        if (this.libre) {
            return new double[]{this.sx, this.sz, this.nx, this.nz};
        }
        Direction f = this.getBlockState().hasProperty(EchelleBlock.FACING) ? this.getBlockState().getValue(EchelleBlock.FACING) : Direction.NORTH;
        double ex = f.getStepX(), ez = f.getStepZ();
        // échelle d'avant : dos contre le mur de sa case, à 2 px du bord (moitié du mur dessinée par la voisine)
        return new double[]{0.5 - ex * 0.375, 0.5 - ez * 0.375, ex, ez};
    }

    /** Forme de la partie {@code partie} (null : celle d'avant, d'après le sens). */
    @Nullable
    public VoxelShape forme(int partie) {
        if (!this.libre) {
            return null;
        }
        if (this.formes == null) {
            // rectangle tourné de l'échelle (dos en S, épaisseur vers N), sa boîte dans la case, sinon une fine plaque côté mur
            double t = EPAISSEUR_MODELE / 16.0, l = DEMI_LARGEUR / 16.0;
            double cx = this.sx + this.nx * t / 2, cz = this.sz + this.nz * t / 2;
            double hx = Math.abs(this.nz) * l + Math.abs(this.nx) * t / 2;
            double hz = Math.abs(this.nx) * l + Math.abs(this.nz) * t / 2;
            double x0 = Math.max(0, cx - hx), x1 = Math.min(1, cx + hx), z0 = Math.max(0, cz - hz), z1 = Math.min(1, cz + hz);
            VoxelShape f;
            if (x1 - x0 > 0.02 && z1 - z0 > 0.02) {
                f = Shapes.box(x0, 0, z0, x1, 1, z1);
            } else {
                Direction d = Direction.getNearest(this.nx, 0, this.nz);
                f = EchelleBlock.formeDAvant(d);
            }
            this.formes = new VoxelShape[]{f, f, f};
        }
        return this.formes[Math.max(0, Math.min(this.formes.length - 1, partie))];
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Pv", this.pv);
        if (this.libre) {
            tag.putDouble("Sx", this.sx);
            tag.putDouble("Sz", this.sz);
            tag.putDouble("Nx", this.nx);
            tag.putDouble("Nz", this.nz);
            if (this.appui != null) {
                tag.putLong("Appui", this.appui.asLong());
            }
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        this.pv = tag.contains("Pv") ? tag.getFloat("Pv") : PV_MAX;
        this.libre = tag.contains("Nx");
        if (this.libre) {
            this.sx = tag.getDouble("Sx");
            this.sz = tag.getDouble("Sz");
            this.nx = tag.getDouble("Nx");
            this.nz = tag.getDouble("Nz");
            this.appui = tag.contains("Appui") ? BlockPos.of(tag.getLong("Appui")) : null;
        }
        this.formes = null;
    }

    /** Envoyée aux joueurs (place de l'échelle ; les PV restent au serveur). */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag t = this.saveWithoutMetadata();
        t.remove("Pv");
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** L'échelle peut déborder de sa case (elle est dessinée contre la surface) : boîte de dessin un peu plus large. */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = this.worldPosition;
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + EchelleBlock.HAUTEUR, p.getZ() + 2);
    }
}
