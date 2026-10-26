package fr.heralisz.utils.ascenseur;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Panneau d'appel côté serveur (Ascenseur.PALIER_USINE ; clic d'origine tel quel) : haut = monter, bas = descendre. */
public class PalierBlockServeur extends PalierBlock {
    public PalierBlockServeur(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        if (l.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean monter = hit.getLocation().y - pos.getY() >= 0.5;
        l.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.7f, monter ? 1.1f : 0.9f);
        MachineBlockEntityServeur m = MachineBlockEntityServeur.trouver(l, pos);
        if (m == null) {
            p.displayClientMessage(Component.translatableWithFallback("heralisz_utils.ascenseur.ne_repond_pas", "L'ascenseur ne répond pas"), true);
        } else if (p instanceof ServerPlayer sp) {
            m.appel((ServerLevel) l, sp, pos.getY() - 1, monter);
        }
        return InteractionResult.CONSUME;
    }
}
