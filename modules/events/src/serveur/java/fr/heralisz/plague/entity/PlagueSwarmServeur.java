package fr.heralisz.plague.entity;

import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.PlagueConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link PlagueSwarm} (docs/METHODE_CLIENT_SERVEUR.md §5.4) : traque, piqûres (poison, nausée, dégâts) et
 * dispersion. Créée par l'usine du code commun dans les mondes serveur, ou directement par le Porteur de Peste (constructeur
 * (niveau, maître, cible, vitesse, durée)) ; ne jamais faire « new PlagueSwarm(...) » côté serveur.
 */
public class PlagueSwarmServeur extends PlagueSwarm {
   public PlagueSwarmServeur(EntityType<? extends PlagueSwarm> type, Level level) {
      super(type, level);
   }

   /** Essaim lancé par le Porteur de Peste (l'ancien constructeur de PlagueSwarm, repris tel quel). */
   public PlagueSwarmServeur(Level level, LivingEntity owner, LivingEntity target, float speed, int life) {
      this(HeraliszPlague.PLAGUE_SWARM.get(), level);
      this.owner = owner;
      this.speed = speed;
      this.entityData.set(TARGET, target.getId());
      this.entityData.set(LIFE, life);
   }

   private LivingEntity owner;
   private float speed = 0.32F;
   private int onTarget;

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) return;
      ServerLevel level = (ServerLevel) this.level();
      Entity t = level.getEntity(targetId());
      if (this.tickCount >= life() || !(t instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()
         || target instanceof Player p && p.isCreative()) {
         disperse(level);
         return;
      }
      if (target.isInWater()) {
         // sous l'eau, les mouches lâchent prise
         disperse(level);
         return;
      }
      Vec3 goal = target.position().add(0, target.getBbHeight() * 0.6, 0);
      Vec3 to = goal.subtract(this.position());
      double d = to.length();
      Vec3 wobble = new Vec3(Mth.sin(this.tickCount * 0.7F) * 0.08, Mth.cos(this.tickCount * 0.9F) * 0.06, Mth.cos(this.tickCount * 0.6F) * 0.08);
      Vec3 step = d > 1e-3 ? to.scale(Math.min(1, speed / d)) : Vec3.ZERO;
      this.setPos(this.getX() + step.x + wobble.x, this.getY() + step.y + wobble.y, this.getZ() + step.z + wobble.z);
      if (this.tickCount % 50 == 1) level.playSound(null, getX(), getY(), getZ(), fr.heralisz.plague.sound.Sounds.PB_FLIES.get(), SoundSource.HOSTILE, 1.6F,
         0.95F + random.nextFloat() * 0.1F);
      if (d < 1.8) {
         onTarget++;
         if (onTarget % 10 == 0) {
            float mult = PlagueConfig.DAMAGE_MULT.get().floatValue();
            target.hurt(this.damageSources().indirectMagic(this, owner != null ? owner : this), 2.5F * mult);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
         }
         // collé à sa proie, il ne la lâche plus longtemps
         if (onTarget > 110) disperse(level);
      }
   }

   private void disperse(ServerLevel level) {
      level.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 20, 0.8, 0.8, 0.8, 0.05);
      this.discard();
   }
}
