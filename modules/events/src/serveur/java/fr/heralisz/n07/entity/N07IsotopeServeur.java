package fr.heralisz.n07.entity;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.particle.ModParticles;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link N07Isotope} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par SujetN07Serveur (new N07IsotopeServeur, comme l'ancien constructeur) ou par l'usine du code commun (mondes serveur) ;
 * ne jamais faire « new N07Isotope(...) » côté serveur.
 */
public class N07IsotopeServeur extends N07Isotope {
   public N07IsotopeServeur(EntityType<? extends N07Isotope> type, Level level) {
      super(type, level);
   }

   /** Ancien constructeur N07Isotope(level, owner, target, from, vel) : isotope lancé par le boss (le propriétaire est une donnée serveur). */
   public N07IsotopeServeur(Level level, SujetN07Serveur owner, LivingEntity target, Vec3 from, Vec3 vel) {
      this(HeraliszN07.N07_ISOTOPE.get(), level);
      this.owner = owner;
      this.entityData.set(TARGET, target.getId());
      this.setPos(from.x, from.y, from.z);
      this.setDeltaMovement(vel);
   }

   private SujetN07Serveur owner;

   @Override
   public void tick() {
      super.tick();
      Vec3 v = this.getDeltaMovement();
      if (this.level().isClientSide) {
         if (this.tickCount % 2 == 0) this.level().addParticle(ModParticles.RAD_EMBER.get(), getX(), getY() + 0.3, getZ(), 0, 0, 0);
         return;
      }
      ServerLevel level = (ServerLevel) this.level();
      Entity t = level.getEntity(this.entityData.get(TARGET));
      if (owner == null || !owner.isAlive() || owner.isDeadOrDying()) {
         fizzle(level);
         return;
      }
      if (this.tickCount >= LIFE || !(t instanceof LivingEntity target) || !target.isAlive()) {
         owner.isotopeBlast(level, this.position());
         this.discard();
         return;
      }
      if (this.tickCount < 20) {
         // il monte en ralentissant
         v = v.scale(0.9);
      } else {
         Vec3 goal = target.position().add(0, target.getBbHeight() * 0.6 + (this.tickCount < 60 ? 2.5 : 0.3), 0);
         Vec3 to = goal.subtract(this.position());
         double d = to.length();
         double speed = 0.2 + 0.04 * Math.min(1, this.tickCount / 160.0);
         Vec3 want = d > 1e-3 ? to.scale(Math.min(1, speed / d)) : Vec3.ZERO;
         v = v.lerp(want, 0.15).add(0, Mth.sin(this.tickCount * 0.3F) * 0.01, 0);
      }
      this.setDeltaMovement(v);
      this.setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
      if (this.tickCount % 30 == 0) level.playSound(null, getX(), getY(), getZ(), fr.heralisz.n07.sound.BossSounds.N7_ISOTOPE.get(), SoundSource.HOSTILE,
         1.8F, 0.9F + this.tickCount / 400F);
      for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(0.9))) {
         if (p.isCreative() || p.isSpectator()) continue;
         owner.isotopeBlast(level, this.position());
         this.discard();
         return;
      }
   }

   /** Abattu en vol : petite gerbe d'étincelles, sans dégâts. */
   private void fizzle(ServerLevel level) {
      level.sendParticles(ModParticles.TOXIC_SPARK.get(), getX(), getY(), getZ(), 20, 0.3, 0.3, 0.3, 0.2);
      Vfx.flash(level, this.position(), 1.5F, 4, 0xB8FF7A);
      level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.6F, 1.4F);
      this.discard();
   }

   @Override
   public boolean hurt(DamageSource s, float amount) {
      if (this.level().isClientSide || this.isRemoved()) return false;
      if (s.getEntity() instanceof SujetN07) return false;
      fizzle((ServerLevel) this.level());
      return true;
   }
}
