package fr.heralisz.matchs.zombies.contenu.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Etabli de fabrication (2 blocs de large centres sur la case de la carte, plateau a hauteur de hanche, panneau
 * d'outils contre le mur au-dessus). Seules les cases du bas existent (3 : centre + deux demi-cases) ; le panneau est
 * dessine sans collision. Case maitresse (centre) = {@link AtelierBE}.
 */
public class BlocAtelier extends BlocLarge {
    public BlocAtelier(Properties p) {
        super(p, 15, 0, 1, 15);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTIE, 0));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PARTIE) == 0 ? new AtelierBE(pos, state) : null;
    }
}
