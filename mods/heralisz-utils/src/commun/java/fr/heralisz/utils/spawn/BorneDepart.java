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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Borne de départ du spawn (pupitre de tôle, écran de la carte incliné vers le joueur, gros bouton, feu vert sur un mât :
 * modèle de mods/heralisz-utils/outils/borne_depart.py). FACING = vers le joueur qui la pose. Incassable en survie.
 * Jar fusionné : forme et pose ici ; le départ (délai, recherche d'un point sûr, téléportation) est dans
 * BorneDepartServeur / DepartServeur (Depart.BORNE_USINE).
 */
public class BorneDepart extends HorizontalDirectionalBlock {
    private static final VoxelShape FORME = Block.box(2, 0, 2, 14, 15, 14);

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

    /** Clic : rien d'autre que le geste ici ; BorneDepartServeur (partie serveur) lance le départ. */
    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        return l.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }
}
