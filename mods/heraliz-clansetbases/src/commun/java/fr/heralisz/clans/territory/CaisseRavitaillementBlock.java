package fr.heralisz.clans.territory;

import fr.heralisz.clans.registry.ClansRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Caisse de ravitaillement posee : conteneur 27 cases ouvrable par tout le monde (sauf config
 * caisse_verrouillee_clan : membres du clan qui a lance le signal), disparait 15 min apres l'atterrissage (ticker
 * serveur), casser la caisse fait tomber son contenu. Verrou de clan (regle serveur) : {@code CaisseRavitaillementBlockServeur},
 * cree par {@code ClansRegistry.CAISSE_USINE}. Rendu par territory/client/CaisseRenderer (parachute degonfle
 * 30 s puis caisse seule).
 */
public class CaisseRavitaillementBlock extends BaseEntityBlock {
    private static final VoxelShape FORME = Block.box(0, 0, 0, 16, 16, 16);

    public CaisseRavitaillementBlock(Properties props) {
        super(props);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CaisseRavitaillementBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORME;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ClansRegistry.CAISSE_BE.get(), CaisseRavitaillementBlockEntity::tickServeur);
    }

    /** Version commune : prediction du client ; la regle du verrou et l'ouverture sont dans la version serveur. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CaisseRavitaillementBlockEntity be) {
            Containers.dropContents(level, pos, be);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
