package fr.heralisz.n07.block;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Garde le lien avec « son » boss : un seul à la fois, et un délai de réapparition après sa mort. Partie COMMUNE (aucune donnée
 * synchronisée) ; logique et sauvegarde : ContainmentVatBlockEntityServeur (usine HeraliszN07.CONTAINMENT_VAT_BE_USINE).
 */
public class ContainmentVatBlockEntity extends BlockEntity {
   public ContainmentVatBlockEntity(BlockPos pos, BlockState state) {
      super(HeraliszN07.CONTAINMENT_VAT_BE.get(), pos, state);
   }

   /** Ticker serveur : la logique (réveil du boss, délai de réapparition, sauvegarde) est dans ContainmentVatBlockEntityServeur. */
   public static void serverTick(Level lvl, BlockPos pos, BlockState state, ContainmentVatBlockEntity be) {
      be.tick(lvl, pos, state);
   }

   /** Neutre ici (jar sans partie serveur) ; ContainmentVatBlockEntityServeur la redéfinit. */
   void tick(Level lvl, BlockPos pos, BlockState state) {
   }
}
