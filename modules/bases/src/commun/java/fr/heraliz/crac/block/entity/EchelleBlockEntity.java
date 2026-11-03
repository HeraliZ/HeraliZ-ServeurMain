package fr.heraliz.crac.block.entity;

import fr.heraliz.crac.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Points de vie d'une échelle ({@link fr.heraliz.crac.block.EchelleBlock}) : 150 comme la barricade, gardés avec le monde.
 * Aucun tick, rien d'envoyé aux joueurs (les coups sont décidés au serveur, {@code placable/EchelleServeur}).
 */
public class EchelleBlockEntity extends BlockEntity {
    public static final float PV_MAX = 150.0F;
    private float pv = PV_MAX;

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

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Pv", this.pv);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        this.pv = tag.contains("Pv") ? tag.getFloat("Pv") : PV_MAX;
    }
}
