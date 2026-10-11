package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.allonge.EtatAllonge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * S'allonger : ajoute aux données du tireur de TaCZ (une par entité vivante) l'état noté par HeraliZ (heure du coucher,
 * heure du relevé ; fr.heraliz.tactique.allonge.Allonge). Aucun point d'injection : seulement trois champs.
 */
@Mixin(value = ShooterDataHolder.class, remap = false)
public abstract class ShooterDataHolderMixin implements EtatAllonge {
   @Unique
   private boolean heraliz$allonge;
   @Unique
   private long heraliz$heureCouche;
   @Unique
   private long heraliz$heureReleve;

   @Override
   public boolean heraliz$getEtaitAllonge() {
      return this.heraliz$allonge;
   }

   @Override
   public void heraliz$setEtaitAllonge(boolean allonge) {
      this.heraliz$allonge = allonge;
   }

   @Override
   public long heraliz$getCoucheA() {
      return this.heraliz$heureCouche;
   }

   @Override
   public void heraliz$setCoucheA(long heure) {
      this.heraliz$heureCouche = heure;
   }

   @Override
   public long heraliz$getReleveA() {
      return this.heraliz$heureReleve;
   }

   @Override
   public void heraliz$setReleveA(long heure) {
      this.heraliz$heureReleve = heure;
   }
}
