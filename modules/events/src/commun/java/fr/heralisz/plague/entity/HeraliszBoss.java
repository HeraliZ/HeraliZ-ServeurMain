package fr.heralisz.plague.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
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
 * Socle commun des boss HeralisZ (à partir de la Mère des Ruines) : phases à 66 % / 33 %, actions scénarisées synchronisées
 * (l'animation GeckoLib « animation.&lt;id&gt;.&lt;action&gt; » suit l'action), barre de boss, menace, PV virtuels (échelle des armes,
 * plafonds par coup et par seconde, montée au-delà de N joueurs), laisse autour du point d'apparition, mort scénarisée puis
 * dépouille. Convention : les 4 premières constantes de l'enum d'actions sont NONE, SPAWN, PHASE2, PHASE3.
 * <p>
 * Partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.5) : état synchronisé, animations GeckoLib, ce que lisent le rendu, le HUD
 * et les cinématiques. Toute la logique de cette classe mère (IA, actions, barre de boss, menace, PV virtuels, laisse, mort
 * scénarisée, sauvegarde) est dans la sous-classe serveur de la seule classe concrète : PlagueBearerServeur.
 */
public abstract class HeraliszBoss<A extends Enum<A> & HeraliszBoss.Act> extends Monster implements GeoEntity, CineSource {
   /** Durée de l'action en ticks. */
   public interface Act {
      int ticks();
   }

   static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(HeraliszBoss.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(HeraliszBoss.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION_TICK = SynchedEntityData.defineId(HeraliszBoss.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> VIRTUAL_MAX = SynchedEntityData.defineId(HeraliszBoss.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(HeraliszBoss.class, EntityDataSerializers.BOOLEAN);

   protected final A[] acts;
   final String animId;
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public int clientLastActionTick = -1;

   protected HeraliszBoss(EntityType<? extends HeraliszBoss<A>> type, Level level, Class<A> actions, String animId) {
      super(type, level);
      this.acts = actions.getEnumConstants();
      this.animId = animId;
      this.xpReward = 500;
      this.setPersistenceRequired();
   }

   // ------------------------------------------------------------------ état synchronisé
   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(PHASE, 1);
      this.entityData.define(ACTION, 0);
      this.entityData.define(ACTION_TICK, 0);
      this.entityData.define(VIRTUAL_MAX, 6000);
      this.entityData.define(DORMANT, false);
   }

   public int phase() {
      return this.entityData.get(PHASE);
   }

   public A action() {
      return acts[Mth.clamp(this.entityData.get(ACTION), 0, acts.length - 1)];
   }

   public boolean idle() {
      return this.entityData.get(ACTION) == 0;
   }

   public int actionTick() {
      return this.entityData.get(ACTION_TICK);
   }

   /** Horloge commune serveur / clients (temps du monde) : un client qui arrive en plein combat voit la même action au même instant. */
   public int clock() {
      return (int) this.level().getGameTime();
   }

   /** Ticks écoulés depuis le début de l'action (figé à 0 tant qu'il dort). */
   public int actionTime() {
      return dormant() ? 0 : clock() - actionTick();
   }

   // ------------------------------------------------------------------ sommeil (donjons : la statue qui attend son rituel)
   /** Endormi : figé sur la première image de son apparition, invulnérable, sans IA, sans barre ni musique. */
   public boolean dormant() {
      return this.entityData.get(DORMANT);
   }

   public int virtualMax() {
      return this.entityData.get(VIRTUAL_MAX);
   }

   @Override
   public boolean isInvulnerableTo(DamageSource s) {
      return s.is(DamageTypes.FALL) || s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.DROWN) || s.is(DamageTypes.CACTUS) || super.isInvulnerableTo(s);
   }

   @Override
   public boolean canBeAffected(MobEffectInstance e) {
      return e.getEffect() != MobEffects.MOVEMENT_SLOWDOWN && e.getEffect() != MobEffects.POISON && super.canBeAffected(e);
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double d) {
      return false;
   }

   // ------------------------------------------------------------------ mort
   /**
    * La mort scénarisée (cinématique, butin, dépouille, fin de la barre de boss) est dans PlagueBearerServeur. Ici : côté client,
    * le temps de mort avance (lu par le rendu : moisissure, champignons) ; dans un monde serveur sans partie serveur, mort vanilla.
    */
   @Override
   protected void tickDeath() {
      if (!this.level().isClientSide) {   // sans partie serveur dans le jar : mort vanilla
         super.tickDeath();
         return;
      }
      ++this.deathTime;
   }

   // ------------------------------------------------------------------ BossInfo (cinématiques) par défaut
   public float cinematicYaw() {
      return this.yBodyRot;
   }

   @Override
   public float cinematicTitleAt(int kind) {
      return kind == 0 ? 0.8F : kind == 3 ? 0.6F : 0.45F;
   }

   // ------------------------------------------------------------------ GeckoLib
   protected RawAnimation moveAnimation(boolean moving) {
      return RawAnimation.begin().thenLoop("animation." + animId + (moving ? ".walk" : ".idle"));
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      RawAnimation death = RawAnimation.begin().thenPlayAndHold("animation." + animId + ".death");
      RawAnimation sleeping = RawAnimation.begin().thenLoop("animation." + animId + ".dormant");
      reg.add(new AnimationController<>(this, "move", 5, s -> {
         if (this.isDeadOrDying() || dormant()) return PlayState.STOP;
         return s.setAndContinue(moveAnimation(s.isMoving()));
      }));
      reg.add(new AnimationController<>(this, "action", 4, s -> {
         if (this.isDeadOrDying()) return s.setAndContinue(death);
         if (dormant()) {
            clientLastActionTick = actionTick();
            return s.setAndContinue(sleeping);
         }
         A a = action();
         if (a.ordinal() == 0 || !hasAnimation(a)) {
            clientLastActionTick = -1;
            return PlayState.STOP;
         }
         if (clientLastActionTick != actionTick()) {
            clientLastActionTick = actionTick();
            // l'apparition part de sa propre première image (pas de fondu depuis la pose au sol : sinon le boss « saute »)
            s.getController().setTransitionLength(a.ordinal() == 1 ? 0 : 4);
            s.getController().forceAnimationReset();
         }
         return s.setAndContinue(RawAnimation.begin().thenPlay("animation." + animId + "." + animName(a)));
      }));
      // sursaut quand on le frappe : joué par-dessus l'idle ou la marche (pas pendant une attaque), sans redémarrer à chaque coup
      if (hasHurtAnimation()) {
         RawAnimation flinch = RawAnimation.begin().thenPlay("animation." + animId + ".hurt");
         reg.add(new AnimationController<>(this, "hurt", 2, s -> {
            if (this.isDeadOrDying() || dormant() || action().ordinal() != 0) {
               clientHurtStart = -100;
               return PlayState.STOP;
            }
            if (this.hurtTime == this.hurtDuration && this.tickCount - clientHurtStart > 11) {
               clientHurtStart = this.tickCount;
               s.getController().forceAnimationReset();
            }
            if (this.tickCount - clientHurtStart > 11) return PlayState.STOP;
            return s.setAndContinue(flinch);
         }));
      }
   }

   private int clientHurtStart = -100;

   /** Nom de l'animation d'une action (par défaut son nom ; une action peut reprendre l'animation d'une autre). */
   protected String animName(A a) {
      return a.name().toLowerCase(java.util.Locale.ROOT);
   }

   /** true si le boss a une animation « hurt » : il sursaute quand on le frappe (hors attaque). */
   protected boolean hasHurtAnimation() {
      return false;
   }

   /** false pour une action sans animation propre (ex. phase invisible). */
   protected boolean hasAnimation(A a) {
      return true;
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
