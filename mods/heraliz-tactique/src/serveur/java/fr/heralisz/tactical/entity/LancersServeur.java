package fr.heralisz.tactical.entity;

import fr.heralisz.tactical.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Partie SERVEUR commune à tous les lancers (classe mère {@link ThrowableItemEntity}, docs/METHODE_CLIENT_SERVEUR.md §5.5) :
 * les effets d'un impact, code d'origine de ThrowableItemEntity.onHit (après le test « éclate au contact ») avec « this »
 * remplacé par l'entité. Chaque lancer serveur (GrenadeEntityServeur, SmokeGrenadeEntityServeur, StunGrenadeEntityServeur,
 * EffectCloudGrenadeEntityServeur) redéfinit {@code impact} en appelant cette méthode.
 */
public final class LancersServeur {
   private LancersServeur() {
   }

   /** ThrowableItemEntity.onHit (d'origine), partie « impact » : sons de rebond, réaction du bloc, coup sur l'entité touchée. */
   static void impact(ThrowableItemEntity e, HitResult result) {
      switch (result.getType()) {
         case BLOCK:
            BlockHitResult blockResult = (BlockHitResult)result;
            BlockPos resultPos = blockResult.getBlockPos();
            BlockState state = e.level().getBlockState(resultPos);
            SoundEvent event = state.getBlock().getSoundType(state, e.level(), resultPos, e).getStepSound();
            double speed = e.getDeltaMovement().length();
            if (speed > 0.1) {
               e.level().playSound(null, result.getLocation().x, result.getLocation().y, result.getLocation().z, event, SoundSource.AMBIENT, 2.0F, 1.0F);
               e.level()
                  .playSound(
                     null,
                     result.getLocation().x,
                     result.getLocation().y,
                     result.getLocation().z,
                     (SoundEvent)ModSounds.GRENADE_BOUNCE.get(),
                     SoundSource.AMBIENT,
                     2.0F,
                     1.0F
                  );
            }

            state.onProjectileHit(e.level(), state, blockResult, e);
            break;
         case ENTITY:
            EntityHitResult entityResult = (EntityHitResult)result;
            Entity entity = entityResult.getEntity();
            if (entity == e.getOwner() || entity == e.getVehicle()) {
               return;
            }

            double hitSpeed = e.getDeltaMovement().length();
            if (hitSpeed > 0.1) {
               entity.hurt(entity.damageSources().thrown(e, e.getOwner()), e.getHitDamage());
            }
      }
   }
}
