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

/** Mesures de la grande cascade (en blocs) : largeur du rideau, hauteur de chute jusqu'à l'eau, profondeur de la fissure. */
public class CascadeBE extends BlockEntity {
    public float largeur = 8;
    public float hauteur = 16;
    public float recul = 2;

    public CascadeBE(BlockPos p, BlockState s) {
        super(EntitesBloc.CASCADE.get(), p, s);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putFloat("largeur", largeur);
        t.putFloat("hauteur", hauteur);
        t.putFloat("recul", recul);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        if (t.contains("largeur")) {
            largeur = Math.max(1, Math.min(48, t.getFloat("largeur")));
        }
        if (t.contains("hauteur")) {
            hauteur = Math.max(1, Math.min(200, t.getFloat("hauteur")));
        }
        if (t.contains("recul")) {
            recul = Math.max(0, Math.min(8, t.getFloat("recul")));
        }
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
        double r = largeur / 2 + 5;
        return new AABB(p.getX() - r, p.getY() - hauteur - 6, p.getZ() - r, p.getX() + 1 + r, p.getY() + 3, p.getZ() + 1 + r);
    }
}
