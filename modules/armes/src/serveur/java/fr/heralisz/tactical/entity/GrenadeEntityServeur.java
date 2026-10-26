package fr.heralisz.tactical.entity;

import fr.heralisz.tactical.config.CommonConfig;
import fr.heralisz.tactical.util.CustomExplosion;
import fr.heralisz.tactical.util.ParticleUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Partie SERVEUR de {@link GrenadeEntity} (grenades explosives : M67, RGN… ; docs/METHODE_CLIENT_SERVEUR.md §5.4) :
 * explosion, réaction en chaîne et impacts, code d'origine. Créée par l'usine (GrenadeEntity.USINE) dans les mondes serveur ;
 * les lancers sont créés par leur type ({@code GrenadeEntity.TYPE.create(level)}, ExplodeType), jamais par « new ».
 * Les méthodes statiques servent aussi au C4 (StickyGrenadeEntityServeur), qui est une grenade.
 */
public class GrenadeEntityServeur extends GrenadeEntity {
   public GrenadeEntityServeur(EntityType<? extends GrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected boolean exploser(Vec3 pos) {
      return explosion(this, pos);
   }

   @Override
   protected boolean reactionEnChaine(DamageSource pSource) {
      return reaction(this, pSource);
   }

   @Override
   protected void impact(HitResult result) {
      LancersServeur.impact(this, result);
   }

   /** GrenadeEntity.onDeath (d'origine), partie serveur : l'explosion. False si un autre mod l'annule (grenade gardée). */
   static boolean explosion(GrenadeEntity e, Vec3 pos) {
      BlockInteraction type = e.isDestroyBlocks() && CommonConfig.GRENADE_EXPLOSION_BLOCK_DAMAGE.get() ? BlockInteraction.DESTROY : BlockInteraction.KEEP;
      CustomExplosion explosion = new CustomExplosion(e.level(), e, e.getDamage(), e.getRadius(), type);
      explosion.setScreenShakeAmplitude(e.getScreenShakeAmplitude());
      explosion.setScreenShakeTime(e.getScreenShakeTime());
      explosion.setDestroyMultiplier(e.getDestroyMultiplier());
      if (ForgeEventFactory.onExplosionStart(e.level(), explosion)) {
         return false;
      }

      explosion.explode();
      explosion.finalizeExplosion(true);
      if (e.level() instanceof ServerLevel level) {
         double x = pos.x();
         double y = pos.y();
         double z = pos.z();
         ParticleUtil.sendParticle(level, ParticleTypes.FLASH, x, y + 0.5, z, 50, 0.2, 0.2, 0.2, 20.0, true);
         ParticleUtil.sendParticle(level, ParticleTypes.EXPLOSION_EMITTER, x, y + 1.0, z, 5, 0.7, 0.7, 0.7, 1.0, true);
      }

      return true;
   }

   /** GrenadeEntity.hurt (d'origine), partie serveur : touchée par une explosion, la grenade explose 3 ticks plus tard. */
   static boolean reaction(GrenadeEntity e, DamageSource pSource) {
      if (e.isTriggerOnExplode() && pSource.is(DamageTypeTags.IS_EXPLOSION) && !e.exploded) {
         e.setLife(e.tickCount + 3);
         return true;
      }

      return false;
   }
}
