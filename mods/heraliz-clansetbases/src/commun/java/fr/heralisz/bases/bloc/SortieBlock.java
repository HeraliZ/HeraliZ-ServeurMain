package fr.heralisz.bases.bloc;

import fr.heraliz.clansetbases.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Bloc « sortie » pose dans chaque sas : clic droit = sortir de la base (retour a la position d'entree, ou au spawn).
 * Meme robustesse que la coque. Meme regle que le bouton et {@code /base sortir} : 10 s immobile, refus en combat
 * (voir {@code BaseService#sortir}, partie serveur) ; un nouveau clic pendant le compte a rebours ne le relance pas.
 */
public class SortieBlock extends CoqueBlock {
    /** Sortie de la base (BaseService.sortir) : branchee par la partie serveur du mod, neutre sans elle. */
    public static final Crochet<ServerPlayer> SORTIE_SERVEUR = new Crochet<>();

    public SortieBlock(Properties p) {
        super(p);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) SORTIE_SERVEUR.accept(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
