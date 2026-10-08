package fr.heralisz.n07.dungeon;

import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Partie SERVEUR de {@link PropBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dès que la partie serveur est dans le jar (avec -complet, aussi côté client : la logique
 * serveur reste derrière isClientSide ou le ticker serveur, comme à l'origine) ; ne jamais faire « new PropBlockEntity(...) ».
 */
public class PropBlockEntityServeur extends PropBlockEntity {
   public PropBlockEntityServeur(BlockPos pos, BlockState st) {
      super(pos, st);
   }


   /** Met en marche / coupe le jet (serveur) ; synchronise aux clients. */
   public void setFlow(boolean on) {
      if (level == null || level.isClientSide || flow == on) return;
      flow = on;
      flowTime = level.getGameTime();
      setChanged();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
   }

   // ------------------------------------------------------------------ luminaires électriques (serveur)
   private int flicker;
   private long nextFlicker = -1;

   @Override
   void tick(Level level, BlockPos pos, BlockState st) {
      lampTick(level, pos, st, this);
   }

   /** Corps d'origine de PropBlockEntity.serverTick. */
   private static void lampTick(Level level, BlockPos pos, BlockState st, PropBlockEntityServeur be) {
      if (!(st.getBlock() instanceof LampBlock)) return;
      long now = level.getGameTime();
      var r = level.random;
      ShrineBlockEntity sh = be.shrine();
      // salle pilotee (mode Zombies) : aucun clignotement, la lumiere ne change que sur ordre (aucune mise a jour d'eclairage)
      if (sh != null && sh.managed()) return;
      if (sh != null && sh.alert()) {
         // alerte : les néons sont coupés, seules les sirènes éclairent
         if (st.getValue(LampBlock.POWER) != 0) level.setBlock(pos, st.setValue(LampBlock.POWER, 0), 3);
         return;
      }
      float surge = sh == null ? 0 : sh.surge(0);
      // (chaque coupure de vraie lumière recalcule l'éclairage et reconstruit les sections voisines : des à-coups espacés,
      // le grésillement rapide est purement visuel côté client)
      if (surge > 0.05F) {
         if (be.flicker <= 0 && r.nextFloat() < 0.03F + 0.1F * surge) be.flicker = 6 + r.nextInt(10);
      } else if (be.faulty()) {
         if (be.nextFlicker < 0) be.nextFlicker = now + 60 + r.nextInt(300);
         if (now >= be.nextFlicker) {
            be.flicker = 8 + r.nextInt(12);
            be.nextFlicker = now + 200 + r.nextInt(500);
         }
      }
      int want = 2;
      if (be.flicker > 0) {
         be.flicker--;
         if (now % 4 != 0 && be.flicker != 0) return;
         float x = r.nextFloat();
         want = be.flicker == 0 ? 2 : x < 0.45F ? 0 : x < 0.7F ? 1 : 2;
      }
      if (st.getValue(LampBlock.POWER) != want) level.setBlock(pos, st.setValue(LampBlock.POWER, want), 3);
   }
}
