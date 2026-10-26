package fr.heralisz.mammoth.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Main spectrale : un mort de la tribu qui remonte du sol quand Ymirak a perdu son âme. Elle jaillit (rise), se referme sur
 * une patte ou le flanc (grab), puis tire (pull) ; elle s'enfonce avec lui (même descente que son corps, lue sur son maître
 * par le rendu) et disparaît. Aucune collision, aucune IA : c'est Ymirak (sa mort) qui la place et l'oriente.
 */
public class SpectralHand extends Entity implements GeoEntity {
   private static final EntityDataAccessor<Integer> MASTER = SynchedEntityData.defineId(SpectralHand.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> FACE = SynchedEntityData.defineId(SpectralHand.class, EntityDataSerializers.FLOAT);
   public static final int GRAB_AT = 24, PULL_AT = 34, LIFE = 320;
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

   public SpectralHand(EntityType<? extends SpectralHand> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.setNoGravity(true);
   }

   public void setup(Mammoth m, float faceYaw) {
      this.entityData.set(MASTER, m.getId());
      this.entityData.set(FACE, faceYaw);
   }

   public int master() {
      return this.entityData.get(MASTER);
   }

   /** Orientation (degrés, convention yaw Minecraft) : la paume regarde vers la patte qu'elle saisit. */
   public float face() {
      return this.entityData.get(FACE);
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(MASTER, -1);
      this.entityData.define(FACE, 0F);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level() instanceof ServerLevel level) {
         Entity m = level.getEntity(master());
         if (this.tickCount > LIFE || m == null || !(m instanceof Mammoth mm) || mm.deathTime > Mammoth.DEATH_TICKS) this.discard();
      }
   }

   @Override
   public boolean hurt(DamageSource s, float a) {
      return false;
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 160 * 160;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag t) {}

   @Override
   protected void addAdditionalSaveData(CompoundTag t) {}

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      reg.add(new AnimationController<>(this, "hand", 3, s -> {
         int t = this.tickCount;
         if (t < GRAB_AT) return s.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.spectral_hand.rise"));
         if (t < PULL_AT) return s.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.spectral_hand.grab"));
         return s.setAndContinue(RawAnimation.begin().thenLoop("animation.spectral_hand.pull"));
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
