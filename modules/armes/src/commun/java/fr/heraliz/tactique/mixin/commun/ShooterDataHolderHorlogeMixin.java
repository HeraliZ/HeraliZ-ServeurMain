package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.tir.HorlogeTir;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Horloge des tirs (fr.heraliz.tactique.tir.HorlogeTirs) : ajoute aux données du tireur de TaCZ (une par entité vivante)
 * l'ancienne base et l'heure du dernier recalage. Aucun point d'injection : seulement des champs.
 */
@Mixin(value = ShooterDataHolder.class, remap = false)
public abstract class ShooterDataHolderHorlogeMixin implements HorlogeTir {
   @Unique
   private long heraliz$ancienneBase;
   @Unique
   private long heraliz$recaleA;

   @Override
   public long heraliz$ancienneBase() {
      return this.heraliz$ancienneBase;
   }

   @Override
   public long heraliz$recaleA() {
      return this.heraliz$recaleA;
   }

   @Override
   public void heraliz$noterRecalage(long ancienneBase, long quand) {
      this.heraliz$ancienneBase = ancienneBase;
      this.heraliz$recaleA = quand;
   }
}
