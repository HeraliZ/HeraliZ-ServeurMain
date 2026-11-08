package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class Cristaux extends Deco {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public Cristaux(Properties p) {
        super(p, s -> {
            return switch ((Direction)s.getValue(FACING)) {
                case UP -> boite(3.0, 0.0, 3.0, 13.0, 13.0, 13.0);
                case DOWN -> boite(3.0, 3.0, 3.0, 13.0, 16.0, 13.0);
                case NORTH -> boite(3.0, 3.0, 3.0, 13.0, 13.0, 16.0);
                case SOUTH -> boite(3.0, 3.0, 0.0, 13.0, 13.0, 13.0);
                case EAST -> boite(0.0, 3.0, 3.0, 13.0, 13.0, 13.0);
                case WEST -> boite(3.0, 3.0, 3.0, 16.0, 13.0, 13.0);
            };
        }, true, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return this.defaultBlockState().setValue(FACING, c.getClickedFace());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}
