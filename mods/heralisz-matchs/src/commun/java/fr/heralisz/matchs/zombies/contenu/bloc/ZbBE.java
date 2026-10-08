package fr.heralisz.matchs.zombies.contenu.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base des blocs-entites du decor zombies : tout l'etat est synchronise au client (paquet de mise a jour = NBT complet,
 * quelques octets), et chaque changement d'etat note l'instant ({@link #depuis}, temps de jeu du monde) pour que le rendu
 * anime la transition. Ecriture seulement cote serveur, par le decor.
 */
public abstract class ZbBE extends BlockEntity {
    /** Temps de jeu (level.getGameTime) du dernier changement d'etat anime. */
    protected long depuis = -100000L;

    protected ZbBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public long depuis() {
        return depuis;
    }

    /** Ticks (avec fraction) ecoules depuis le dernier changement, pour le rendu. */
    public float ecoule(float partial) {
        return level == null ? 1e6F : (float) (level.getGameTime() - depuis) + partial;
    }

    /** Marque l'instant d'un changement anime. */
    protected void marquer() {
        if (level != null) depuis = level.getGameTime();
    }

    /** Enregistre et envoie l'etat aux clients. */
    public void synchroniser() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    protected abstract void ecrire(CompoundTag tag);

    protected abstract void lire(CompoundTag tag);

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Depuis", depuis);
        ecrire(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        depuis = tag.getLong("Depuis");
        lire(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
