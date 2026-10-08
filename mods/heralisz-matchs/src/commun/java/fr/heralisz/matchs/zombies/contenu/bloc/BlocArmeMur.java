package fr.heralisz.matchs.zombies.contenu.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Arme dessinee a la craie au mur (voir {@link ArmeMurBE}). */
public class BlocArmeMur extends BlocMural {
    public BlocArmeMur(Properties p) {
        super(p, 0.06, 0.1, 0.9, 0.5);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArmeMurBE(pos, state);
    }
}
