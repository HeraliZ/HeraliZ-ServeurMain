package fr.heralisz.mere.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Rejeton de la Mère des Ruines : l'IA et le venin d'une araignée des cavernes (grimpe aux murs), mais son propre modèle velu,
 * ses propres sons, et des modes scriptés pour les cinématiques : descendre au bout d'un fil depuis un sac d'œufs (HANG), tourner
 * autour de la Mère en l'emmaillotant (WRAP). Pendant un script, pas d'IA ni de gravité.
 */
public class Spiderling extends CaveSpider implements GeoEntity {
   public static final int FREE = 0, HANG = 1, WRAP = 2, CARRY = 3;
   /** Animation en mode marionnette : se tenir arc-boutée, porter (petits pas rapides), courir (fileuse), marcher. */
   public static final int A_HOLD = 0, A_CARRY = 1, A_RUN = 2, A_WALK = 3;
   static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(Spiderling.class, EntityDataSerializers.INT);
   private static final RawAnimation CARRY_A = RawAnimation.begin().thenLoop("animation.spiderling.carry");
   private static final RawAnimation RUN_A = RawAnimation.begin().thenLoop("animation.spiderling.run");
   private static final RawAnimation HOLD_A = RawAnimation.begin().thenLoop("animation.spiderling.hold");
   /** Elle porte l'Œuf de la Couvée entre ses crochets (le rituel). */
   static final EntityDataAccessor<Boolean> EGG = SynchedEntityData.defineId(Spiderling.class, EntityDataSerializers.BOOLEAN);
   static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(Spiderling.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Float> THREAD_Y = SynchedEntityData.defineId(Spiderling.class, EntityDataSerializers.FLOAT);
   /** Orientation libre (mode CARRY : sur le dos de la Mère, sur un pilier) : modèle → monde. */
   static final EntityDataAccessor<org.joml.Quaternionf> ORIENT = SynchedEntityData.defineId(Spiderling.class, EntityDataSerializers.QUATERNION);
   private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.spiderling.idle");
   private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.spiderling.walk");
   private static final RawAnimation HANG_A = RawAnimation.begin().thenLoop("animation.spiderling.hang");
   private static final RawAnimation WRAP_A = RawAnimation.begin().thenLoop("animation.spiderling.wrap");
   private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.spiderling.attack");

   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

   public Spiderling(EntityType<? extends CaveSpider> type, Level level) {
      super(type, level);
      this.xpReward = 2;
   }

   public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
      return CaveSpider.createCaveSpider().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 14.0)
         .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.34);
   }

   /** Les rejetons ne sortent jamais du nid : pas de disparition au loin. */
   @Override
   public boolean removeWhenFarAway(double d) {
      return false;
   }

   public boolean carriesEgg() {
      return this.entityData.get(EGG);
   }

   public int mode() {
      return this.entityData.get(MODE);
   }

   /** Hauteur (monde) du point d'attache du fil (sac d'œufs, voûte) : le rendu tire la soie jusque-là. */
   public float threadY() {
      return this.entityData.get(THREAD_Y);
   }

   /** Modèle → monde : la tête (modèle -Z) vers fwd, le dos (modèle +Y) vers up. */
   public static org.joml.Quaternionf basis(Vec3 up, Vec3 fwd) {
      Vec3 u = up.normalize();
      Vec3 f = fwd.subtract(u.scale(fwd.dot(u)));
      f = f.lengthSqr() < 1e-6 ? new Vec3(u.y, u.z, u.x) : f.normalize();
      Vec3 z = f.scale(-1);
      Vec3 x = u.cross(z);
      org.joml.Matrix3f m = new org.joml.Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) u.x, (float) u.y, (float) u.z, (float) z.x, (float) z.y,
         (float) z.z);
      return new org.joml.Quaternionf().setFromNormalized(m);
   }

   org.joml.Quaternionf clientPrev, clientCur;
   /** Bascule du corps (client) : 0 à plat, 1 à la verticale contre la paroi, négatif en descendant. */
   public float climb, climbO;

   /** Orientation interpolée (rendu). */
   public org.joml.Quaternionf orient(float partial) {
      org.joml.Quaternionf q = this.entityData.get(ORIENT);
      if (clientCur == null) return new org.joml.Quaternionf(q);
      return new org.joml.Quaternionf(clientPrev).slerp(clientCur, partial);
   }

   @Override
   public boolean isPushable() {
      return mode() == FREE && super.isPushable();
   }

   /**
    * Côté client : bascule du corps et orientation interpolée (lues par le rendu). Les scripts (fil, ronde) sont dans
    * SpiderlingServeur.
    */
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
      }
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(MODE, FREE);
      this.entityData.define(THREAD_Y, 0F);
      this.entityData.define(ORIENT, new org.joml.Quaternionf());
      this.entityData.define(ANIM, A_WALK);
      this.entityData.define(EGG, false);
   }

   // ------------------------------------------------------------------ animations
   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
      controllers.add(new AnimationController<>(this, "main", 3, s -> {
         int m = mode();
         if (m == HANG) return s.setAndContinue(HANG_A);
         if (m == WRAP) return s.setAndContinue(WRAP_A);
         if (m == CARRY) {
            int an = this.entityData.get(ANIM);
            return s.setAndContinue(an == A_HOLD ? HOLD_A : an == A_CARRY ? CARRY_A : an == A_RUN ? RUN_A : WALK);
         }
         return s.setAndContinue(s.isMoving() ? WALK : IDLE);
      }));
      controllers.add(new AnimationController<>(this, "attack", 0, s -> {
         if (this.swinging) return s.setAndContinue(ATTACK);
         s.getController().forceAnimationReset();
         return PlayState.STOP;
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
