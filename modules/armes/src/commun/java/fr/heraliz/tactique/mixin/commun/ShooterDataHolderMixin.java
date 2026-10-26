package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.allonge.EtatAllonge;
import fr.heraliz.tactique.allonge.Plongeon;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * S'allonger : ajoute aux données du tireur de TaCZ (une par entité vivante) l'état noté par HeraliZ (heure du coucher,
 * heure du relevé ; fr.heraliz.tactique.allonge.Allonge) et le plongeon (fr.heraliz.tactique.allonge.Plongeon, créé par
 * le serveur pour les joueurs seulement). Aucun point d'injection : seulement des champs.
 */
@Mixin(value = ShooterDataHolder.class, remap = false)
public abstract class ShooterDataHolderMixin implements EtatAllonge {
   @Unique
   private boolean heraliz$allonge;
   @Unique
   private long heraliz$heureCouche;
   @Unique
   private long heraliz$heureReleve;
   @Unique
   @Nullable
   private Plongeon heraliz$plongeon;

   @Override
   @Nullable
   public Plongeon heraliz$getPlongeon() {
      return this.heraliz$plongeon;
   }

   @Override
   public void heraliz$setPlongeon(Plongeon plongeon) {
      this.heraliz$plongeon = plongeon;
   }

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
