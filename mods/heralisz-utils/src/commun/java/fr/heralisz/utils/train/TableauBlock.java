package fr.heralisz.utils.train;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Panneau de gare, posé au sol : « horaire » (boîtier de tôle rouillée sur deux poteaux, nom de la gare, prochaine
 * gare, et l'attente du prochain train en diodes orangées) ou « nom » (plaque émaillée au nom de la gare, en latin et
 * en cyrillique). FACING = côté de la face lisible. La gare est celle du réseau la plus proche (TableauBlockEntity).
 */
public class TableauBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Genre> GENRE = EnumProperty.create("genre", Genre.class);
    private static final VoxelShape POTEAUX_NS = Shapes.or(Block.box(0, 0, 7, 2, 16, 9), Block.box(14, 0, 7, 16, 16, 9));
    private static final VoxelShape POTEAUX_EO = Shapes.or(Block.box(7, 0, 0, 9, 16, 2), Block.box(7, 0, 14, 9, 16, 16));

    public enum Genre implements StringRepresentable {
        HORAIRE("horaire"), NOM("nom");

        private final String id;

        Genre(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public TableauBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GENRE, Genre.HORAIRE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, GENRE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Direction f = s.getValue(FACING);
        return f.getAxis() == Direction.Axis.Z ? POTEAUX_NS : POTEAUX_EO;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new TableauBlockEntity(p, s);
    }
}
