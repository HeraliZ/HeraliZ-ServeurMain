package fr.heralisz.tactical.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Partie SERVEUR de {@link SmokeGrenadeEntity} (fumigène ; docs/METHODE_CLIENT_SERVEUR.md §5.4) : les impacts (sons de
 * rebond, coup sur l'entité touchée, réaction du bloc), code d'origine de la classe mère. Le reste du fumigène (vol, nuage
 * de fumée côté client, son de libération envoyé par le serveur) est commun, comme avant. Créée par l'usine
 * (SmokeGrenadeEntity.USINE) dans les mondes serveur.
 */
public class SmokeGrenadeEntityServeur extends SmokeGrenadeEntity {
   public SmokeGrenadeEntityServeur(EntityType<SmokeGrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void impact(HitResult result) {
      LancersServeur.impact(this, result);
   }
}
