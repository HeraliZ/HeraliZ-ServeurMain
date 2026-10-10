package fr.heraliz.decors.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Surface d'eau animée posée DANS un bloc d'eau : le bloc est noyé (l'eau reste, on nage comme avant) et dessine juste
 * au-dessus de la surface une nappe translucide (léger voile, fines caustiques qui ondulent, lente dérive vers FACING).
 * Une seule texture partout et jamais tournée au hasard : aucun raccord d'un bloc à l'autre. Aucune collision.
 */
public class EauDecor extends Block implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public EauDecor(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH)
                .setValue(WATERLOGGED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        boolean eau = c.getLevel().getFluidState(c.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(WATERLOGGED, eau).setValue(FACING, c.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    public FluidState getFluidState(BlockState s) {
        return s.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(s);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) {
        if (s.getValue(WATERLOGGED)) {
            l.scheduleTick(p, Fluids.WATER, Fluids.WATER.getTickDelay(l));
        }
        return s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }
}
