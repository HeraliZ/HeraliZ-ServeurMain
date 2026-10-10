package fr.heraliz.decors.effet;

import fr.heraliz.decors.EntitesBloc;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Mesures d'un rayon de soleil (en blocs) : longueur jusqu'au sol, rayon en haut et en bas, décalage du pied (dx, dz :
 * le rayon penche), intensité.
 */
public class RayonBE extends BlockEntity {
    public float longueur = 24;
    public float r0 = 1.8f;
    public float r1 = 5.5f;
    public float dx = 0;
    public float dz = 0;
    public float force = 1;

    public RayonBE(BlockPos p, BlockState s) {
        super(EntitesBloc.RAYON.get(), p, s);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putFloat("longueur", longueur);
        t.putFloat("r0", r0);
        t.putFloat("r1", r1);
        t.putFloat("dx", dx);
        t.putFloat("dz", dz);
        t.putFloat("force", force);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        longueur = t.contains("longueur") ? Math.max(1, Math.min(200, t.getFloat("longueur"))) : longueur;
        r0 = t.contains("r0") ? Math.max(0.1f, Math.min(16, t.getFloat("r0"))) : r0;
        r1 = t.contains("r1") ? Math.max(0.1f, Math.min(24, t.getFloat("r1"))) : r1;
        dx = t.contains("dx") ? Math.max(-48, Math.min(48, t.getFloat("dx"))) : dx;
        dz = t.contains("dz") ? Math.max(-48, Math.min(48, t.getFloat("dz"))) : dz;
        force = t.contains("force") ? Math.max(0, Math.min(2, t.getFloat("force"))) : force;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        double r = r1 * 1.8 + 1;
        return new AABB(Math.min(p.getX(), p.getX() + dx) - r, p.getY() - longueur - 1, Math.min(p.getZ(), p.getZ() + dz) - r,
                Math.max(p.getX(), p.getX() + dx) + 1 + r, p.getY() + 2, Math.max(p.getZ(), p.getZ() + dz) + 1 + r);
    }
}
