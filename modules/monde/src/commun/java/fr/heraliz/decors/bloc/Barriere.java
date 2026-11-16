package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Barriere extends Deco {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public Barriere(Properties p) {
        super(p, Barriere::formeBarriere, false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    private static VoxelShape formeBarriere(BlockState s) {
        VoxelShape v = boite(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);
        if (s.getValue(NORTH)) {
            v = Shapes.or(v, boite(7.3, 6.3, 0.0, 8.7, 14.0, 8.0));
        }

        if (s.getValue(SOUTH)) {
            v = Shapes.or(v, boite(7.3, 6.3, 8.0, 8.7, 14.0, 16.0));
        }

        if (s.getValue(WEST)) {
            v = Shapes.or(v, boite(0.0, 6.3, 7.3, 8.0, 14.0, 8.7));
        }

        if (s.getValue(EAST)) {
            v = Shapes.or(v, boite(8.0, 6.3, 7.3, 16.0, 14.0, 8.7));
        }

        return v;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(NORTH, EAST, SOUTH, WEST);
    }

    private static BooleanProperty cote(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    private boolean raccorde(BlockGetter l, BlockPos p, Direction d) {
        BlockPos v = p.relative(d);
        BlockState s = l.getBlockState(v);
        return s.is(this) || s.isFaceSturdy(l, v, d.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState s = this.defaultBlockState();

        for (Direction d : Plane.HORIZONTAL) {
            s = s.setValue(cote(d), this.raccorde(c.getLevel(), c.getClickedPos(), d));
        }

        return s;
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState voisin, LevelAccessor l, BlockPos p, BlockPos pv) {
        return d.getAxis().isHorizontal() ? s.setValue(cote(d), this.raccorde(l, p, d)) : s;
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        BlockState o = s;

        for (Direction d : Plane.HORIZONTAL) {
            o = o.setValue(cote(r.rotate(d)), s.getValue(cote(d)));
        }

        return o;
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        BlockState o = s;

        for (Direction d : Plane.HORIZONTAL) {
            o = o.setValue(cote(m.mirror(d)), s.getValue(cote(d)));
        }

        return o;
    }
}
