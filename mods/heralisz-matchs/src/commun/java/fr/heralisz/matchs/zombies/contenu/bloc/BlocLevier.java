package fr.heralisz.matchs.zombies.contenu.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

/** Levier du courant (voir {@link LevierBE}). */
public class BlocLevier extends BlocMural {
    public static final BooleanProperty ACTIF = BooleanProperty.create("actif");

    public BlocLevier(Properties p) {
        super(p, 0.25, 0.0, 1.0, 0.5);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIF, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ACTIF);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LevierBE(pos, state);
    }
}
