package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

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

    // 19/11 : plus de tick programmé ni de tick au hasard, la lampe clignote par sa texture (voir Clignotement)
}
