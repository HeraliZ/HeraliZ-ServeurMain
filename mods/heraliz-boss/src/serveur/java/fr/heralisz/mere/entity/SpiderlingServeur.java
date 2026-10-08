package fr.heralisz.mere.entity;

import fr.heralisz.mere.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link Spiderling} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new Spiderling(...) » côté serveur.
 */
public class SpiderlingServeur extends Spiderling {
   public SpiderlingServeur(EntityType<? extends CaveSpider> type, Level level) {
      super(type, level);
   }

   private Vec3 goal;
   private double speed;
   private Vec3 orbit;
   private double orbitR, orbitA;
   private boolean keepHanging;

   public void setEgg(boolean on) {
      this.entityData.set(EGG, on);
   }

   /** Descend au bout d'un fil de `from` (sous un sac d'œufs) jusqu'à `to`, en `ticks` ; `stay` : reste pendue à l'arrivée. */
   public void hangDown(Vec3 from, Vec3 to, int ticks, boolean stay) {
      this.entityData.set(MODE, HANG);
      this.entityData.set(THREAD_Y, (float) from.y + 0.6F);
      this.moveTo(from.x, from.y, from.z, this.random.nextFloat() * 360F, 0);
      this.goal = to;
      this.speed = from.distanceTo(to) / Math.max(1, ticks);
      this.keepHanging = stay;
      this.setNoGravity(true);
      this.setNoAi(true);
   }

   /** Remonte le long de son fil jusqu'au point to (le fil reste accroché à la hauteur anchorY), puis y reste pendue. */
   public void climbTo(Vec3 to, float anchorY, int ticks) {
      this.entityData.set(MODE, HANG);
      this.entityData.set(THREAD_Y, anchorY);
      this.goal = to;
      this.speed = position().distanceTo(to) / Math.max(1, ticks);
      this.keepHanging = true;
      this.setNoGravity(true);
      this.setNoAi(true);
   }

   /** Tourne autour de `c` (rayon r, à la hauteur de c) en tissant, jusqu'à nouvel ordre. */
   public void wrapAround(Vec3 c, double r) {
      this.entityData.set(MODE, WRAP);
      this.orbit = c;
      this.orbitR = r;
      this.orbitA = Math.atan2(getZ() - c.z, getX() - c.x);
      this.setNoGravity(true);
      this.setNoAi(true);
   }

   /** Le centre de la ronde se déplace (la Mère emmaillotée est hissée) : la ronde suit. */
   public void moveOrbit(Vec3 c) {
      this.orbit = c;
   }

   /**
    * Marionnette de la Mère (porteurs, grimpeurs, fileuses) : position et orientation imposées à chaque tick. up : son dos (à
    * l'opposé de la surface sur laquelle elle marche) ; fwd : sa tête.
    */
   public void puppet(Vec3 pos, Vec3 up, Vec3 fwd) {
      puppet(pos, up, fwd, A_WALK);
   }

   public void puppet(Vec3 pos, Vec3 up, Vec3 fwd, int anim) {
      this.entityData.set(ANIM, anim);
      if (mode() != CARRY) {
         this.entityData.set(MODE, CARRY);
         this.setNoGravity(true);
         this.setNoAi(true);
         this.noPhysics = true;
      }
      this.setPos(pos.x, pos.y, pos.z);
      this.setDeltaMovement(Vec3.ZERO);
      this.entityData.set(ORIENT, basis(up, fwd));
   }

   /** Retour à la vie normale (IA, gravité). */
   public void release() {
      this.entityData.set(MODE, FREE);
      this.noPhysics = false;
      this.setNoGravity(false);
      this.setNoAi(false);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         // escalade : le corps bascule en douceur contre la paroi (1 bloc : les pattes avant se posent ; plus : elle grimpe à la verticale)
         climbO = climb;
         double dy = getY() - yo;
         float want = this.isClimbing() || this.horizontalCollision && dy > 0.02 ? 1F : dy > 0.05 ? 0.45F : dy < -0.15 ? -0.3F : 0F;
         climb += (want - climb) * 0.3F;
         org.joml.Quaternionf q = this.entityData.get(ORIENT);
         clientPrev = clientCur == null ? new org.joml.Quaternionf(q) : clientCur;
         clientCur = new org.joml.Quaternionf(q);
         return;
      }
      int m = mode();
      if (m == HANG && goal != null) {
         Vec3 d = goal.subtract(position());
         if (d.length() <= speed) {
            this.setPos(goal.x, goal.y, goal.z);
            if (!keepHanging) release();
         } else {
            Vec3 p = position().add(d.normalize().scale(speed));
            this.setPos(p.x, p.y, p.z);
         }
         this.setDeltaMovement(Vec3.ZERO);
      } else if (m == WRAP && orbit != null) {
         orbitA += 0.22;
         Vec3 p = orbit.add(Math.cos(orbitA) * orbitR, 0, Math.sin(orbitA) * orbitR);
         this.setPos(p.x, orbit.y, p.z);
         this.setYRot((float) Math.toDegrees(orbitA) + 180F);
         this.yBodyRot = this.getYRot();
         this.setDeltaMovement(Vec3.ZERO);
         if (this.tickCount % 4 == 0 && this.level() instanceof ServerLevel sl)
            sl.sendParticles(RuinMother.WEB_FX, getX(), getY() + 0.5, getZ(), 2, 0.2, 0.2, 0.2, 0.01);
      }
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      release();
   }

   @Override
   public boolean hurt(DamageSource src, float amount) {
      if (mode() != FREE) return false;
      return super.hurt(src, amount);
   }

   // ------------------------------------------------------------------ sons propres (plus de sons d'araignée vanilla)
   @Override
   protected SoundEvent getAmbientSound() {
      return Sounds.CHITTER.get();
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource src) {
      return Sounds.HISS.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return Sounds.SQUELCH.get();
   }

   @Override
   public float getVoicePitch() {
      return 1.7F + this.random.nextFloat() * 0.3F;
   }

   @Override
   protected float getSoundVolume() {
      return 0.6F;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      if (this.random.nextInt(4) == 0) this.playSound(Sounds.STEP.get(), 0.15F, 2.0F);
   }
}
