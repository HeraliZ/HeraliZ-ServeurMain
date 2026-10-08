package fr.heralisz.matchs.clashwar.bloc;

import fr.heralisz.matchs.clashwar.CwRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Bloc-entite du mur « tableau du tournoi » : ne garde que la taille du mur (largeur x hauteur en blocs), synchronisee
 * avec les clients. Le contenu affiche est l'etat global du ClashWar recu par le client ({@code TableauS2C}) : tous les
 * murs du serveur montrent le meme tableau, mis a jour en direct.
 */
public class TableauBlocEntite extends BlockEntity {
    private int largeur = MurTableau.LARGEUR_DEFAUT, hauteur = MurTableau.HAUTEUR_DEFAUT;
    /** Boite de rendu (recalculee quand la taille change). */
    private AABB boite;

    public TableauBlocEntite(BlockPos pos, BlockState state) {
        super(CwRegistres.TABLEAU_BE.get(), pos, state);
    }

    public int largeur() {
        return largeur;
    }

    public int hauteur() {
        return hauteur;
    }

    /** Fixe la taille et la diffuse aux clients. */
    public void taille(int l, int h) {
        largeur = Math.max(1, Math.min(MurTableau.LARGEUR_MAX, l));
        hauteur = Math.max(1, Math.min(MurTableau.HAUTEUR_MAX, h));
        boite = null;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Direction vers laquelle l'ecran regarde. */
    public Direction face() {
        BlockState s = getBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING) : Direction.SOUTH;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("largeur")) largeur = Math.max(1, Math.min(MurTableau.LARGEUR_MAX, tag.getInt("largeur")));
        if (tag.contains("hauteur")) hauteur = Math.max(1, Math.min(MurTableau.HAUTEUR_MAX, tag.getInt("hauteur")));
        boite = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("largeur", largeur);
        tag.putInt("hauteur", hauteur);
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
        if (boite == null) {
            BlockPos fin = MurTableau.bloc(worldPosition, face(), largeur - 1, hauteur - 1);
            boite = new AABB(worldPosition).minmax(new AABB(fin)).inflate(0.5);
        }
        return boite;
    }
}
