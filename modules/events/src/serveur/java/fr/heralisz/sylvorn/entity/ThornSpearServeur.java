package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.SylvornConfig;
import fr.heralisz.sylvorn.particle.ModParticles;
import fr.heralisz.sylvorn.sound.Sounds;
import fr.heralisz.sylvorn.fvfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link ThornSpear} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new ThornSpear(...) » côté serveur.
 */
public class ThornSpearServeur extends ThornSpear {
   public ThornSpearServeur(EntityType<? extends ThornSpear> type, Level level) {
      super(type, level);
   }

   @Override
   protected void onHit(HitResult r) {
      if (this.level().isClientSide || stuck()) return;
      ServerLevel level = (ServerLevel) this.level();
      if (r instanceof EntityHitResult er) {
         if (er.getEntity() instanceof LivingEntity le && !(le instanceof Sylvorn)) {
            if (le instanceof Player p && (p.isCreative() || p.isSpectator())) return;
            le.hurt(this.damageSources().thrown(this, this.getOwner()), 12F * SylvornConfig.DAMAGE_MULT.get().floatValue());
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
            Vec3 v = this.getDeltaMovement().normalize();
            le.setDeltaMovement(le.getDeltaMovement().add(v.x * 0.6, 0.25, v.z * 0.6));
            le.hurtMarked = true;
            level.sendParticles(ModParticles.BLOOD.get(), getX(), getY(), getZ(),
               10, 0.2, 0.2, 0.2, 0.1);
            level.playSound(null, getX(), getY(), getZ(), Sounds.DG_ROOTS.get(), SoundSource.HOSTILE, 1.6F, 0.6F);
            level.playSound(null, getX(), getY(), getZ(), Sounds.KN_IMPACT.get(), SoundSource.HOSTILE, 1.2F, 0.6F);
            this.discard();
         }
         return;
      }
      // dans le sol : il se plante, gerbe de terre
      Vec3 p = this.position();
      var ground = level.getBlockState(BlockPos.containing(p.x, p.y - 0.2, p.z));
      if (!ground.isAir()) Vfx.debris(level, p, 0.4F, 3F, 5, ground, Vec3.ZERO);
      level.sendParticles(ModParticles.DUST.get(), p.x, p.y + 0.2, p.z, 6, 0.3, 0.1, 0.3, 0.02);
      level.playSound(null, p.x, p.y, p.z, Sounds.KN_IMPACT.get(), SoundSource.HOSTILE, 1.6F, 0.6F);
      // vitesse infime dans l'axe du vol : la hampe garde son inclinaison une fois plantée
      this.setDeltaMovement(this.getDeltaMovement().normalize().scale(0.001));
      this.setNoGravity(true);
      this.entityData.set(STUCK, true);
   }
}
