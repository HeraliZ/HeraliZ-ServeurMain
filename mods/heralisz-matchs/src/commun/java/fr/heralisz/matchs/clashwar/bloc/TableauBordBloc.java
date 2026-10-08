package fr.heralisz.matchs.clashwar.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Bloc de bord du mur « tableau du tournoi » (sans bloc-entite) : simple cadre sombre ; sa face avant est recouverte par
 * le rendu du maitre. Le casser retire tout le mur.
 */
public class TableauBordBloc extends HorizontalDirectionalBlock {
    public TableauBordBloc(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState neuf, boolean deplace) {
        if (!state.is(neuf.getBlock()) && !level.isClientSide) MurTableau.depuisBord(level, pos, state.getValue(FACING));
        super.onRemove(state, level, pos, neuf, deplace);
    }
}
