package fr.heralisz.utils.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Borne de départ côté serveur (Depart.BORNE_USINE) : le clic lance le départ (DepartServeur). */
public class BorneDepartServeur extends BorneDepart {
    public BorneDepartServeur(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        if (l.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (h == InteractionHand.MAIN_HAND && p instanceof ServerPlayer sp && !sp.isSpectator()) {
            DepartServeur.demander(sp, pos);
        }
        return InteractionResult.CONSUME;
    }
}
