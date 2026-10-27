package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Distributeur de capsules (« ATM », 27/10, demande du proprietaire) : le joueur y depose les capsules qu'il porte sur
 * son compte de capsules, ou en retire. Le compte est garde par le serveur hors du monde (CompteCapsules) ; la borne ne
 * garde que son dernier mouvement, pour l'animation (capsules qui tombent dans l'entonnoir ou sortent dans le bac).
 */
public class AtmBlockEntity extends MachineBlockEntity {
    /** args : aucun (le serveur renvoie le compte a jour). */
    public static final int ACTION_OUVRIR = 0;
    /** args = {nombre} ; 0 = toutes les capsules portees. */
    public static final int ACTION_DEPOSER = 1;
    /** args = {nombre}. */
    public static final int ACTION_RETIRER = 2;
    public static final int TX_DEPOT = 0, TX_RETRAIT = 1;

    // ---- synchronise (animation)
    public long lastTx = Long.MIN_VALUE / 4;
    public int lastTxType;
    public long lastTxAmount;

    public AtmBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.ATM, pos, state);
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        tag.putLong("LastTx", lastTx);
        tag.putInt("LastTxType", lastTxType);
        tag.putLong("LastTxAmount", lastTxAmount);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        if (!tag.contains("LastTx")) return;
        lastTx = tag.getLong("LastTx");
        lastTxType = tag.getInt("LastTxType");
        lastTxAmount = tag.getLong("LastTxAmount");
    }
}
