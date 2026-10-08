package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.DecorBlock;
import fr.heraliz.casino.block.FlickerDecorBlock;
import fr.heraliz.casino.decor.DecorType;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Etat d'un element de decor : variante choisie (texte, motif, couleur, chaine). L'allumage (et l'ouverture de la
 * porte du coffre) est dans l'etat du bloc (lumiere emise).
 */
public class DecorBlockEntity extends BlockEntity {
    public int mode;
    /** Poteaux : cotes dont la chaine est decrochee (bits DecorBlock.sideBit). */
    public int unhooked;
    /** Valeur propre a l'element (0..1) : chacun gresille a son rythme. */
    public final float seed;

    // ---- serveur uniquement : rafale en cours de la lumiere qui gresille (non sauvegarde)
    public int flickerBurst;
    public boolean flickerDead;

    // ---- client uniquement : dernier tick d'effets joue, poteaux voisins (chaines) et date de la recherche
    public long fxLastTick = Long.MIN_VALUE;
    public BlockPos[] clientLinks = new BlockPos[0];
    public long clientLinksAt = Long.MIN_VALUE;
    /** Client : dernier etat vu (allume / porte ouverte, saute de lumiere) et heure du monde du changement. */
    public boolean clientOn;
    public int clientFlicker;
    public long clientOnAt = Long.MIN_VALUE, clientFlickerAt = Long.MIN_VALUE;
    private boolean clientSeen;

    public DecorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DECOR_BE.get(), pos, state);
        this.seed = (Mth.murmurHash3Mixer(pos.hashCode() * 31 + 7) & 0xFFFF) / 65535f;
    }

    public DecorType type() {
        return getBlockState().getBlock() instanceof DecorBlock d ? d.type : DecorType.ENSEIGNE;
    }

    public boolean on() {
        BlockState s = getBlockState();
        return !s.hasProperty(DecorBlock.ON) || s.getValue(DecorBlock.ON);
    }

    public int flicker() {
        BlockState s = getBlockState();
        return s.hasProperty(FlickerDecorBlock.FLICKER) ? s.getValue(FlickerDecorBlock.FLICKER) : 0;
    }

    /**
     * Client : note les changements d'etat (porte qui s'ouvre, tube qui claque) pour les animer. A la premiere vue
     * (chunk charge), l'etat est pris tel quel, sans animation.
     */
    public void watch(long gameTime) {
        boolean on = on();
        int fl = flicker();
        if (!clientSeen) {
            clientSeen = true;
            clientOn = on;
            clientFlicker = fl;
            return;
        }
        if (on != clientOn) {
            clientOn = on;
            clientOnAt = gameTime;
        }
        if (fl != clientFlicker) {
            clientFlicker = fl;
            clientFlickerAt = gameTime;
        }
    }

    public void setMode(int m) {
        mode = m;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Envoye meme sans changement : le poteau d'en face (qui dessine peut-etre la chaine) revoit ses liens. */
    public void setUnhooked(int sides) {
        unhooked = sides;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Ticker du client (effets, chaines, animations) : branche par la partie client (DecorClientTicker). */
    public static volatile BlockEntityTicker<DecorBlockEntity> TICK_CLIENT;

    public static void clientTick(Level level, BlockPos pos, BlockState state, DecorBlockEntity be) {
        BlockEntityTicker<DecorBlockEntity> t = TICK_CLIENT;
        if (t != null) t.tick(level, pos, state, be);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Mode", mode);
        if (unhooked != 0) tag.putInt("Unhooked", unhooked);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mode = tag.getInt("Mode");
        unhooked = tag.getInt("Unhooked");
        // client : chaines a revoir tout de suite
        clientLinksAt = Long.MIN_VALUE;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Mode", mode);
        tag.putInt("Unhooked", unhooked);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Boite de rendu : tout l'encombrement (antennes, cable qui pend compris), et la chaine d'un poteau jusqu'a son voisin. */
    @Override
    public AABB getRenderBoundingBox() {
        DecorType t = type();
        int reach = t.footprint.reach() + (t.linked() ? DecorBlock.CHAIN_REACH : 0);
        return new AABB(worldPosition).inflate(reach, 1, reach).expandTowards(0, t.footprint.height(), 0);
    }
}
