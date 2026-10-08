package fr.heralisz.n07.entity;

import fr.heralisz.n07.api.BossInfo;
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
import net.minecraft.world.phys.Vec3;
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
 * Partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.5) : état synchronisé, lectures pour le rendu, le HUD et les cinématiques,
 * animations GeckoLib. Toute la logique serveur de ce socle (IA, phases, dégâts, barre de boss, mort scénarisée, sauvegarde,
 * commandes de test DevControl) est dans la sous-classe serveur de l'unique boss concret : SujetN07Serveur.
 */
public abstract class HeraliszBoss<A extends Enum<A> & HeraliszBoss.Act> extends Monster implements GeoEntity, BossInfo {
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
   static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(HeraliszBoss.class, EntityDataSerializers.INT);
   /** Drapeau synchronise : pas de musique d'ambiance du boss (le jeu joue la sienne). */
   public static final int FLAG_NO_MUSIC = 1;

   /** La barre de boss (couleur) et les délais des actions sont créés par la version serveur (SujetN07Serveur). */
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
      this.entityData.define(FLAGS, 0);
   }

   public int flags() {
      return this.entityData.get(FLAGS);
   }

   @Override
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
   @Override
   public boolean dormant() {
      return this.entityData.get(DORMANT);
   }

   /** À appeler avant de l'ajouter au monde (ou pour le rendormir). Commun : le rendu du clone dans la cuve (client) l'utilise aussi. */
   public void setDormant(boolean on) {
      this.entityData.set(DORMANT, on);
      if (on) {
         this.entityData.set(ACTION, 1);
         this.entityData.set(ACTION_TICK, clock());
         this.setTarget(null);
         this.getNavigation().stop();
      }
   }

   /** Action en cours (numero d'ordre ; 1 apparition, 2 et 3 phases). */
   public int actionOrdinal() {
      return this.entityData.get(ACTION);
   }

   @Override
   public int virtualMax() {
      return this.entityData.get(VIRTUAL_MAX);
   }

   protected Vec3 fwd() {
      return Vec3.directionFromRotation(0, this.yBodyRot);
   }

   protected Vec3 right() {
      Vec3 f = fwd();
      return new Vec3(-f.z, 0, f.x);
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
    * Côté client : le temps de mort avance (lu par les rendus : vitrification, statue...). La mort scénarisée (côté serveur :
    * cinématique, butin, dépouille, fin du combat piloté) est dans SujetN07Serveur ; sans partie serveur dans le jar, mort vanilla.
    */
   @Override
   protected void tickDeath() {
      if (!this.level().isClientSide) {
         super.tickDeath();
         return;
      }
      ++this.deathTime;
   }

   // ------------------------------------------------------------------ BossInfo (cinématiques) par défaut
   @Override
   public float cinematicYaw() {
      return this.yBodyRot;
   }

   @Override
   public float cinematicTitleAt(int kind) {
      return kind == SPAWN ? 0.8F : kind == DEATH ? 0.6F : 0.45F;
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
            s.getController().forceAnimationReset();
         }
         return s.setAndContinue(RawAnimation.begin().thenPlay("animation." + animId + "." + a.name().toLowerCase(java.util.Locale.ROOT)));
      }));
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
