package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Partie SERVEUR de {@link Teleporter935Block} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dès que la partie serveur est dans le jar (avec -complet, aussi côté client : la logique
 * serveur reste derrière isClientSide ou le ticker serveur, comme à l'origine) ; ne jamais faire « new Teleporter935Block(...) ».
 */
public class Teleporter935BlockServeur extends Teleporter935Block {
   public Teleporter935BlockServeur() {
      super();
   }


   @Override
   @SuppressWarnings("deprecation")
   public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!s.getValue(ACTIVE)) return InteractionResult.PASS;
      if (!level.isClientSide && player instanceof ServerPlayer sp) fr.heralisz.n07.hall.HallRegistry.padUsed(sp, pos);
      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
