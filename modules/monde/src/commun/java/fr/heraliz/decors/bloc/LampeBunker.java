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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class LampeBunker extends Deco {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ALLUME = Clignotement.ALLUME;

    public LampeBunker(Properties p) {
        super(p, s -> {
            return switch ((Direction)s.getValue(FACING)) {
                case DOWN -> boite(4.0, 7.5, 4.0, 12.0, 16.0, 12.0);
                case UP -> boite(4.0, 0.0, 4.0, 12.0, 8.5, 12.0);
                default -> tourner(boite(4.0, 4.0, 7.5, 12.0, 12.0, 16.0), s.getValue(FACING));
            };
        }, false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(ALLUME, true));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(FACING, ALLUME);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return this.defaultBlockState().setValue(FACING, c.getClickedFace());
    }

    // 19/11 : plus de tick programmé ni de tick au hasard, la lampe clignote par sa texture (voir Clignotement)

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}
