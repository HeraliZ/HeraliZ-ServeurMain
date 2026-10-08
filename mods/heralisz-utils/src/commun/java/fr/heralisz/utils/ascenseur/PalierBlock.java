package fr.heralisz.utils.ascenseur;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Panneau d'appel d'un palier (planche au mur, deux plaques de laiton) : un clic en haut = monter, en bas = descendre.
 * Posé à hauteur des yeux, un bloc au-dessus du plancher du palier. FACING = vers le joueur.
 * Jar fusionné : forme et pose ici ; l'appel de la cabine est dans PalierBlockServeur (Ascenseur.PALIER_USINE).
 */
public class PalierBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape[] FORMES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            FORMES[d.get2DDataValue()] = switch (d) {
                case NORTH -> Block.box(2, 1, 14, 14, 15, 16);
                case SOUTH -> Block.box(2, 1, 0, 14, 15, 2);
                case EAST -> Block.box(0, 1, 2, 2, 15, 14);
                default -> Block.box(14, 1, 2, 16, 15, 14);
            };
        }
    }

    public PalierBlock(BlockBehaviour.Properties p) {
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
        return FORMES[s.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    /** Clic : rien d'autre que le geste ici ; PalierBlockServeur (partie serveur) appelle la cabine. */
    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        return l.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }
}
