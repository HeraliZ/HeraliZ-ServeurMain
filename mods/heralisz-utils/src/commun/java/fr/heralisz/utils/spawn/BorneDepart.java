package fr.heralisz.utils.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Borne de départ du spawn : table tactique (carte allumée, projecteur ; modèle de mods/heralisz-utils/outils/borne_depart.py)
 * et, au-dessus, l'hologramme animé dessiné chez le joueur (BorneDepartEntite, BorneHologramme). FACING = vers le joueur
 * qui la pose. Incassable en survie.
 * Jar fusionné : forme et pose ici ; le départ (délai, recherche d'un point sûr, téléportation) est dans
 * BorneDepartServeur / DepartServeur (Depart.BORNE_USINE).
 */
public class BorneDepart extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape FORME = Block.box(0, 0, 0, 16, 15, 16);

    public BorneDepart(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new BorneDepartEntite(p, s);
    }

    /** Clic : rien d'autre que le geste ici ; BorneDepartServeur (partie serveur) lance le départ. */
    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        return l.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }
}
