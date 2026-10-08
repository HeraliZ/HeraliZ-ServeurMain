package fr.heralisz.utils.ascenseur;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Machine de l'ascenseur (sommet du puits). Côté serveur : repère les paliers du puits (panneaux d'appel à moins de 4
 * blocs de l'axe, en dessous), fait exister une cabine et une seule (attend que les entités du chunk soient chargées,
 * en recrée une si elle a disparu), et répond aux appels. Les paliers sont envoyés au client pour le rendu (roue,
 * cordes, contrepoids).
 * Jar fusionné : ici les paliers (synchronisés) et ce que lit le rendu ; la logique serveur (paliers repérés, cabine
 * unique, appels, sauvegarde) est dans MachineBlockEntityServeur (src/serveur), créée par Ascenseur.MACHINE_USINE.
 */
public class MachineBlockEntity extends BlockEntity {
    /** Dessus du plancher de chaque palier, du bas vers le haut. */
    int[] paliers = new int[0];
    private Cabine cacheClient;
    private int attenteClient;

    public MachineBlockEntity(BlockPos p, BlockState s) {
        super(Ascenseur.MACHINE_BE.get(), p, s);
    }

    public Direction facade() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    public double centreX() {
        return worldPosition.getX() + 0.5;
    }

    public double centreZ() {
        return worldPosition.getZ() + 0.5;
    }

    /** Boîte du puits (cabine, cordes, contrepoids). */
    public AABB puits() {
        int bas = paliers.length > 0 ? paliers[0] - 3 : worldPosition.getY() - 200;
        return new AABB(worldPosition.getX() - 6, bas, worldPosition.getZ() - 6, worldPosition.getX() + 7, worldPosition.getY() + 8,
                worldPosition.getZ() + 7);
    }

    /** Cabine vue par le client (cherchée dans le puits de temps en temps). */
    public Cabine cabineClient() {
        if (level == null) {
            return null;
        }
        if (cacheClient != null && (cacheClient.isRemoved() || !cacheClient.isAlive())) {
            cacheClient = null;
        }
        if (cacheClient == null && --attenteClient <= 0) {
            attenteClient = 20;
            List<Cabine> cs = level.getEntitiesOfClass(Cabine.class, puits());
            cacheClient = cs.isEmpty() ? null : cs.get(0);
        }
        return cacheClient;
    }

    public int[] paliers() {
        return paliers;
    }

    /** Ticker serveur (MachineBlock) : neutre ici ; MachineBlockEntityServeur (partie serveur) le redéfinit. */
    void tickServeur(ServerLevel l) {}

    /** Lit ce que getUpdateTag envoie (paliers, pour le rendu) ; la sauvegarde complète : MachineBlockEntityServeur. */
    @Override
    public void load(CompoundTag t) {
        super.load(t);
        paliers = t.getIntArray("paliers");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = super.getUpdateTag();
        t.putIntArray("paliers", paliers);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return puits();
    }
}
