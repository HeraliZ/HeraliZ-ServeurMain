package fr.heralisz.mammoth.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Le Revenant de la tribu : un des chasseurs gelés agenouillés dans la salle, qu'Ymirak relève aux phases 2 et 3. Il se
 * relève (invulnérable pendant ce temps), traque le joueur le plus proche, plante sa lance (coup annoncé), et s'effondre en
 * éclats de glace quand on l'abat ou quand son maître tombe.
 */
public class Draugr extends Monster implements GeoEntity, CineSource {
   public static final int RISE = 48, THRUST = 20, THRUST_HIT = 10, DEATH = 70;
   static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> STATE_TICK = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.INT);
   public static final int S_RISE = 0, S_FREE = 1, S_THRUST = 2, S_THAW = 3, S_RUN = 4, S_RIP = 5, S_CORPSE = 6, S_SWEEP = 7, S_THROW = 8, S_FETCH = 9, S_PICK = 10;
   public static final int SWEEP = 28, SWEEP_HIT = 16, THROW = 26, THROW_AT = 14, PICK = 20, PICK_AT = 11;
   /** Gardien d'Ymirak : deux fois plus grand, un cœur brûlant dans la poitrine ; HEART = 0 poitrine, 1 main, 2 donné. */
   static final EntityDataAccessor<Boolean> GUARDIAN = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.BOOLEAN);
   static final EntityDataAccessor<Integer> HEART = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.INT);
   public static final int RUN_MAX = 1200, PLANT = 64, PLUNGE = 120, HEART_OUT = 170, ROAR_AT = 184, HEART_IN = 246, BURN_FROM = 264, BURN_TO = 320,
      RIP_LEN = 330, CINE_LEN = 340, DEATH_BURN_FROM = 18, DEATH_BURN_TO = 64;
   /** Combustion 0..100 : le corps se consume en cendres depuis les pieds (le rendu efface les os, synchronisé). */
   static final EntityDataAccessor<Integer> BURN = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Boolean> SPEAR_DOWN = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.BOOLEAN);

   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private int lastStateTick = -1;

   public Draugr(EntityType<? extends Draugr> type, Level level) {
      super(type, level);
      this.xpReward = 0;
      this.setPersistenceRequired();
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.ARMOR, 8.0).add(Attributes.MOVEMENT_SPEED, 0.26)
         .add(Attributes.FOLLOW_RANGE, 72.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.ATTACK_DAMAGE, 8.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(STATE, S_RISE);
      this.entityData.define(STATE_TICK, 0);
      this.entityData.define(GUARDIAN, false);
      this.entityData.define(HEART, 0);
      this.entityData.define(BURN, 0);
      this.entityData.define(SPEAR_DOWN, false);
   }

   public boolean guardian() {
      return this.entityData.get(GUARDIAN);
   }

   public int burn() {
      return this.entityData.get(BURN);
   }

   public boolean spearPlanted() {
      return this.entityData.get(SPEAR_DOWN);
   }

   public int heartStage() {
      return this.entityData.get(HEART);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (GUARDIAN.equals(key)) this.refreshDimensions();
   }

   @Override
   public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose p) {
      return guardian() ? super.getDimensions(p).scale(2.0F) : super.getDimensions(p);
   }

   @Override
   protected net.minecraft.network.chat.Component getTypeName() {
      return guardian() ? net.minecraft.network.chat.Component.translatable("entity.heralisz_mammoth.draugr.guardian") : super.getTypeName();
   }

   public int state() {
      return this.entityData.get(STATE);
   }

   public int stateTime() {
      return this.tickCount - this.entityData.get(STATE_TICK);
   }

   @Override
   public boolean isInvulnerableTo(DamageSource s) {
      return s.is(DamageTypes.FALL) || s.is(DamageTypes.FREEZE) || s.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(s);
   }

   @Override
   public boolean canBeAffected(MobEffectInstance e) {
      return false;
   }

   @Override
   public boolean canFreeze() {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double d) {
      return false;
   }

   /**
    * Mort : côté client, le temps de mort avance (animation, cendres lues dans BURN). La combustion et la disparition sont
    * dans DraugrServeur ; sans partie serveur, mort vanilla.
    */
   @Override
   protected void tickDeath() {
      if (!this.level().isClientSide) {
         super.tickDeath();
         return;
      }
      ++this.deathTime;
   }

   // ------------------------------------------------------------------ GeckoLib
   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      RawAnimation walk = RawAnimation.begin().thenLoop("animation.draugr.walk");
      RawAnimation run = RawAnimation.begin().thenLoop("animation.draugr.run");
      RawAnimation idle = RawAnimation.begin().thenLoop("animation.draugr.idle");
      reg.add(new AnimationController<>(this, "move", 4, s -> {
         if (this.isDeadOrDying() || (state() != S_FREE && state() != S_FETCH)) return PlayState.STOP;
         return s.setAndContinue(s.isMoving() ? (guardian() ? run : walk) : idle);
      }));
      reg.add(new AnimationController<>(this, "action", 2, s -> {
         if (this.isDeadOrDying()) return s.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.draugr.death"));
         int st = state();
         if (st == S_RUN) return s.setAndContinue(RawAnimation.begin().thenLoop("animation.draugr.limp"));
         if (st == S_THAW || st == S_RIP || st == S_CORPSE) {
            int stamp0 = this.entityData.get(STATE_TICK);
            if (st != S_CORPSE && stamp0 != lastStateTick) {
               lastStateTick = stamp0;
               s.getController().forceAnimationReset();
            }
            return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(st == S_THAW ? "animation.draugr.thaw" : "animation.draugr.heart_rip"));
         }
         if (st == S_FREE || st == S_FETCH) {
            lastStateTick = -1;
            return PlayState.STOP;
         }
         int stamp = this.entityData.get(STATE_TICK);
         if (stamp != lastStateTick) {
            lastStateTick = stamp;
            s.getController().forceAnimationReset();
         }
         String clip = switch (st) {
            case S_RISE -> "animation.draugr.rise";
            case S_SWEEP -> "animation.draugr.sweep";
            case S_THROW -> "animation.draugr.throw";
            case S_PICK -> "animation.draugr.pickup";
            default -> "animation.draugr.thrust";
         };
         return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(clip));
      }));
   }

   // ------------------------------------------------------------------ cinématique du cœur (repère : direction de sa course)
   /** Course vers le piédestal : caméra qui le suit de trois quarts arrière (remplacée par le rituel à l'arrivée). */
   private static final float[][] RUN_CINE = {{0, -8, 5, 5, 3.5F}, {1, -8, 5, 5, 3.5F}};
   /** Le rituel, face à lui (repère : il regarde le piédestal ; la caméra est derrière le piédestal). */
   private static final float[][] RITUAL_CINE = {
      {0, 7.5F, 4, 5, 4.5F}, {0.17F, 7, 3.5F, 5, 4.5F},
      {0.18F, 3.5F, -3.8F, 2.6F, 2, 1}, {0.33F, 3.8F, -4, 2.7F, 2.2F},
      {0.34F, 5.2F, 1.2F, 5.2F, 4.6F, 1}, {0.49F, 4.8F, 1, 5.1F, 4.6F},
      {0.5F, 10, 0.5F, 2.5F, 6.5F, 1}, {0.62F, 9.5F, -0.5F, 2.4F, 7.2F},
      {0.63F, 3.5F, 5.5F, 3.2F, 2.2F, 1}, {0.74F, 3.6F, 5, 3.2F, 2.4F},
      {0.75F, 7, 2.5F, 3.2F, 2.4F, 1}, {1, 8.5F, 2, 4, 2.4F}};
   private static final float[][] HEART_CINE = {
      {0, 5, 2.5F, 3.5F, 4}, {0.28F, -6, 3, 4.5F, 4.5F}, {0.3F, -7, 2, 4.5F, 4.5F},
      {0.31F, -10, 3, 5, 5, 1}, {0.44F, -8, 2, 5, 5.2F},
      {0.45F, -4.2F, 1.0F, 4.8F, 4.6F, 1}, {0.6F, -4.5F, 1.3F, 5.0F, 4.8F},
      {0.62F, -3.5F, 1.5F, 5.4F, 5.2F, 1}, {0.7F, -4, 2, 5.6F, 5.4F},
      {0.72F, -1, 7, 4.5F, 3, 1}, {0.86F, -1.5F, 6.5F, 4, 2.5F},
      {0.88F, -14, 4, 7, 2, 1}, {1, -18, 4, 9, 2}};

   @Override
   public float[][] cinematicKeys(int kind) {
      return kind == 7 ? RUN_CINE : RITUAL_CINE;
   }

   @Override
   public String[] cinematicTitles(int kind) {
      if (kind == 7) return null;
      return new String[] {"title.heralisz_mammoth.heart.top", "title.heralisz_mammoth.heart.name", "title.heralisz_mammoth.heart.sub"};
   }

   @Override
   public float cinematicTitleAt(int kind) {
      return 0.36F;
   }

   @Override
   public int accentColor() {
      return 0xFF5A30;
   }

   @Override
   public boolean cinematicFollows(int kind) {
      return true;
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
