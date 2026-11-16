package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;

public class Gyrophare extends Deco {
    public Gyrophare(Properties p) {
        super(p, s -> boite(3.8, 0.0, 3.8, 12.2, 9.6, 12.2), false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(Clignotement.ALLUME, true));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(Clignotement.ALLUME);
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
}
