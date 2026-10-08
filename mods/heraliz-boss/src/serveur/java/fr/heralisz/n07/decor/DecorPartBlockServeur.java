package fr.heralisz.n07.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Partie SERVEUR de {@link DecorPartBlock} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dès que la partie serveur est dans le jar (avec -complet, aussi côté client : la logique
 * serveur reste derrière isClientSide ou le ticker serveur, comme à l'origine) ; ne jamais faire « new DecorPartBlock(...) ».
 */
public class DecorPartBlockServeur extends DecorPartBlock {
   public DecorPartBlockServeur(Properties p) {
      super(p);
   }


   @Override
   public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      super.playerWillDestroy(level, pos, state, player);
      if (level.isClientSide) return;
      BlockPos owner = owner(level, pos);
      if (owner != null) level.destroyBlock(owner, false, player);
   }

   /** Le décor à qui appartient cette case (recherche autour). */
   public static BlockPos owner(Level level, BlockPos pos) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dy = -7; dy <= 7; dy++) {
         for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
               m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
               BlockState st = level.getBlockState(m);
               if (st.getBlock() instanceof DecorBlock db && DecorBlockServeur.parts(m, st, db.spec()).contains(pos)) return m.immutable();
            }
         }
      }
      return null;
   }
}
