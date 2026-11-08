package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class LampeFixe extends Deco {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ALLUME = Clignotement.ALLUME;

    public LampeFixe(Properties p) {
        super(p, s -> {
            return switch ((Direction)s.getValue(FACING)) {
                case DOWN -> boite(1.0, 10.5, 5.5, 15.0, 16.0, 10.5);
                case UP -> boite(1.0, 0.0, 5.5, 15.0, 5.5, 10.5);
                default -> tourner(boite(1.0, 5.5, 10.5, 15.0, 10.5, 16.0), s.getValue(FACING));
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

    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState ancien, boolean deplace) {
        super.onPlace(s, l, p, ancien, deplace);
        if (!ancien.is(this)) {
            Clignotement.demarrer(l, p, this);
        }
    }

    @Override
    public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        Clignotement.basculer(s, l, p, r);
    }

    @Override
    public boolean isRandomlyTicking(BlockState s) {
        return true;
    }

    @Override
    public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        Clignotement.relancer(l, p, this, r);
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
