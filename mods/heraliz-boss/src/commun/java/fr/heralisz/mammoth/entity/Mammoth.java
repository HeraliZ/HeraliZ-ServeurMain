package fr.heralisz.mammoth.entity;

import fr.heralisz.mammoth.dungeon.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.Locale;

/**
 * YMIRAK, le Mammouth Mort. Un colosse de glace et d'os (rendu x1,5 : 8 blocs au garrot).
 * <p>
 * Phase 1, le Colosse : coup de défenses montant (projette en l'air), balayage des défenses, il se cabre et retombe (onde de
 * choc à sauter), coup de trompe en fouet, charge (annoncée ; s'il percute la paroi il est sonné et des stalactites tombent),
 * barrissement qui repousse et assombrit tout.
 * Phase 2, le Blizzard : souffle de blizzard qui balaie et gèle, séisme (des lignes de pics de glace foncent vers chaque
 * joueur), bloc de glace arraché et lancé sur le plus éloigné, prison de glace autour d'un joueur ; ses charges laissent une
 * traînée de givre ; brouillard de neige dans l'arène (client).
 * Phase 3, l'Apocalypse : avalanche (pluie de stalactites sur toute l'arène), ruée (trois charges d'affilée), le Cœur de
 * l'Hiver (il s'agenouille, son flanc s'ouvre, tout est aspiré puis la nova gèle tout dans un grand rayon), aura de froid.
 * Mort : il s'effondre, le gel le prend, puis il vole en éclats en lâchant son butin.
 */
public class Mammoth extends Monster implements GeoEntity, CineSource {
   public enum Action {
      NONE(0), SPAWN(320), PHASE2(130), PHASE3(140), GORE(32), SWEEP(36), STOMP(44), LASH(32), RAMPAGE(28), CHARGE(64), BELLOW(44),
      BREATH(60), QUAKE(60), HURL(48), PRISON(40), AVALANCHE(52), HEART(84), STAGGER(40), TRAMPLE(56), LEAP(60), BUCK(30);
      public final int ticks;

      Action(int t) {
         this.ticks = t;
      }
   }

   public static final float RS = 1.5F;
   /**
    * Mort (26 s) : à genoux (40), la Gueule des Ancêtres s'éveille (MAW_ON), l'âme sort (SOUL_OUT) et monte jusqu'à elle
    * (SOUL_IN) ; les mains spectrales jaillissent (HANDS), l'entraînent sous terre (SINK_FROM), il résiste (RESIST), la tête
    * éclate (HEAD_POP), le corps disparaît (GONE). Le crâne reste dans la salle jusqu'à la remise en état (REMAINS).
    */
   public static final int DEATH_TICKS = 520, MAW_ON = 60, SOUL_OUT = 90, SOUL_IN = 200, HANDS = 222, SINK_FROM = 262, RESIST = 300,
      HEAD_POP = 390, GONE = 450, MAW_OFF = 470, REMAINS = 520 + 6000, SHATTER = GONE;
   public static final int SPAWN_K = 0, PHASE2_K = 1, PHASE3_K = 2, DEATH_K = 3, THAW_K = 4, HEAT_K = 6;

   static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(Mammoth.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Mammoth.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION_TICK = SynchedEntityData.defineId(Mammoth.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> VIRTUAL_MAX = SynchedEntityData.defineId(Mammoth.class, EntityDataSerializers.INT);
   /** Allure : 0 pas, 1 course, 2 recul, 3 pivot sur place. */
   static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(Mammoth.class, EntityDataSerializers.INT);
   public static final int M_WALK = 0, M_RUN = 1, M_BACK = 2, M_TURN = 3;
   /** Agonie : il marche jusqu'au centre de la salle (sous la Gueule) avant que la mort ne commence. */
   static final EntityDataAccessor<Boolean> DEATH_WALK = SynchedEntityData.defineId(Mammoth.class, EntityDataSerializers.BOOLEAN);
   static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(Mammoth.class, EntityDataSerializers.BOOLEAN);

   static final Action[] ACTS = Action.values();

   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public int clientLastActionTick = -1;

   public Mammoth(EntityType<? extends Mammoth> type, Level level) {
      super(type, level);
      this.xpReward = 0;
      this.setPersistenceRequired();
      this.setMaxUpStep(2.0F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.ARMOR, 20.0).add(Attributes.ARMOR_TOUGHNESS, 10.0)
         .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 64.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.ATTACK_DAMAGE, 10.0);
   }

   // ------------------------------------------------------------------ état synchronisé
   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(PHASE, 1);
      this.entityData.define(ACTION, 0);
      this.entityData.define(ACTION_TICK, 0);
      this.entityData.define(VIRTUAL_MAX, 36000);
      this.entityData.define(DORMANT, false);
      this.entityData.define(DEATH_WALK, false);
      this.entityData.define(MOVE, 0);
   }

   public int phase() {
      return this.entityData.get(PHASE);
   }

   public Action action() {
      return ACTS[Mth.clamp(this.entityData.get(ACTION), 0, ACTS.length - 1)];
   }

   public int actionTick() {
      return this.entityData.get(ACTION_TICK);
   }

   public int clock() {
      return (int) this.level().getGameTime();
   }

   public int actionTime() {
      return dormant() ? 0 : clock() - actionTick();
   }

   public int virtualMax() {
      return this.entityData.get(VIRTUAL_MAX);
   }

   public int moveMode() {
      return this.entityData.get(MOVE);
   }

   public boolean dormant() {
      return this.entityData.get(DORMANT);
   }

   /**
    * Libération (16 s, cinématique) : le cœur du Gardien bat dans la glace, tout tremble (0-2 s) ; les fissures incandescentes
    * gagnent la prison depuis le réceptacle, par vagues (1 s, 2,75 s, 4,5 s) ; ses yeux s'allument en rouge à travers la glace
    * (5-8,5 s) ; la prison vole en éclats (7,5 s) ; il sort lentement de sa niche (8-11,75 s), se cabre et barrit (12,25 s),
    * retombe (14,5 s) : le combat commence.
    */
   public static final int FREE_AT = 150, EYES_FROM = 100, EYES_TO = 175;

   /** Ses yeux s'allument à travers la glace (lu par le rendu). */
   public boolean eyesInIce() {
      return action() == Action.SPAWN && actionTime() >= EYES_FROM && actionTime() < FREE_AT;
   }

   public boolean eyesAblaze() {
      return action() == Action.SPAWN && actionTime() >= EYES_FROM && actionTime() <= EYES_TO;
   }

   public Vec3 fwd() {
      return Vec3.directionFromRotation(0, this.yBodyRot);
   }

   Vec3 right() {
      Vec3 f = fwd();
      return new Vec3(-f.z, 0, f.x);
   }

   @Override
   public boolean isInvulnerableTo(DamageSource s) {
      return s.is(DamageTypes.FALL) || s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.DROWN) || s.is(DamageTypes.CACTUS) || s.is(DamageTypes.FREEZE)
         || super.isInvulnerableTo(s);
   }

   @Override
   public boolean canBeAffected(MobEffectInstance e) {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double d) {
      return false;
   }

   @Override
   public boolean canFreeze() {
      return false;
   }

   // ------------------------------------------------------------------ mort : il s'effondre, le gel le prend, il éclate
   /** Progression du gel de la mort (0 -> 1), lue par le rendu. */
   public float frozen(float partial) {
      return 0;
   }

   /** Profondeur (blocs) à laquelle les mains l'ont entraîné, selon le temps de mort : lue par les rendus (lui et les mains). */
   public static float sink(float t) {
      if (t < SINK_FROM) return 0;
      if (t < RESIST) return 1.6F * ease((t - SINK_FROM) / (RESIST - SINK_FROM));
      if (t < RESIST + 20) return 1.6F - 0.9F * ease((t - RESIST) / 20F);                  // il résiste, remonte un peu
      if (t < HEAD_POP) return 0.7F + 4.6F * ease((t - RESIST - 20) / (HEAD_POP - RESIST - 20F));
      return 5.3F + 7F * ease(Math.min(1, (t - HEAD_POP) / (GONE - HEAD_POP)));
   }

   private static float ease(float k) {
      k = Mth.clamp(k, 0, 1);
      return k * k * (3 - 2 * k);
   }

   /** La tête a éclaté (le rendu ne la dessine plus). */
   public boolean headGone(float partial) {
      return this.isDeadOrDying() && this.deathTime + partial >= HEAD_POP;
   }

   /** L'âme lui a été prise : plus aucune lueur. */
   public boolean soulless(float partial) {
      return this.isDeadOrDying() && this.deathTime + partial >= SOUL_IN;
   }

   /**
    * Côté client : le temps de mort avance (lu par les rendus : enfoncement, tête qui éclate, mains...), figé pendant
    * l'agonie. La mort scénarisée (côté serveur : Gueule, âme, mains, butin) est dans MammothServeur ; sans partie serveur
    * dans le jar, mort vanilla.
    */
   @Override
   protected void tickDeath() {
      if (!this.level().isClientSide) {
         super.tickDeath();
         return;
      }
      if (this.entityData.get(DEATH_WALK)) {
         this.deathTime = 0;
         return;
      }
      ++this.deathTime;
   }

   /** Invisible une fois la carcasse effondrée (les derniers ticks avant la disparition). */
   public boolean shattered() {
      return this.isDeadOrDying() && this.deathTime >= SHATTER;
   }

   // ------------------------------------------------------------------ cinématiques et HUD (lus côté client)
   /** Plans de caméra : {avancement 0-1, devant, à droite, hauteur, hauteur visée} dans le repère du boss au début. */
   /**
    * Plans de caméra : {avancement 0-1, devant, à droite, hauteur, hauteur visée[, 1 = coupe franche]} dans le repère du boss au
    * début. Entre deux coupes, la caméra glisse lentement (travelling) ; une coupe saute net au plan suivant, sur les impacts.
    */
   private static final float[][][] CINE = {
      // la prison tremble (lent travelling avant), les yeux s'allument (plan serré à hauteur des yeux), la glace éclate (large),
      // il se cabre et rugit (trois quarts face, en surplomb), il retombe (plan d'ensemble en hauteur)
      {{0, 30, 0, 6, 6}, {0.3F, 22, 2, 5.8F, 6},
         {0.31F, 27, 7, 7.5F, 6.1F, 1}, {0.46F, 25, 6, 7.5F, 6.1F},   // les yeux s'allument : plan large, à distance de la glace
         {0.47F, 30, -10, 7, 5, 1}, {0.73F, 27, -9, 7.5F, 6},
         {0.74F, 22, 12, 9, 6, 1}, {0.9F, 20, 11, 9.5F, 7},
         {0.905F, 30, -6, 10, 4, 1}, {1, 34, -4, 12, 5}},
      {{0, 14, 8, 4, 5}, {0.3F, 12, 9, 3.5F, 5}, {0.31F, 10, -10, 3, 5, 1}, {0.6F, 12, -9, 4, 5}, {0.61F, 16, -4, 8, 4, 1}, {1, 18, -2, 9, 4}},
      {{0, 13, -9, 3.5F, 4}, {0.24F, 11, -8, 3, 3.5F}, {0.25F, 9, -5, 2, 2, 1}, {0.55F, 8, -4, 1.6F, 2}, {0.56F, 18, 6, 9, 3, 1}, {0.75F, 20, 8, 10, 3},
         {0.76F, 15, -11, 6, 5, 1}, {1, 14, -12, 5, 6}},
      {{0, 14, 6, 6, 4}, {0.14F, 10, -9, 3, 3}, {0.29F, 11, -10, 3.5F, 3},
         {0.3F, 16, -14, 8, 2, 1}, {0.47F, 12, 12, 4, 3},
         {0.55F, 22, 0, 1.5F, 11, 1}, {0.64F, 20, 2, 1.8F, 10},
         {0.65F, 3, 11, 3.5F, 3, 1}, {0.72F, 4, 13, 5, 7},
         {0.78F, 10, 15, 4, 9, 1}, {0.845F, 12, 14, 5, 11},
         {0.85F, 16, -12, 3, 9, 1}, {1, 2, -24, 22, 2}},
      // l'éveil des Gardiens : vue d'ensemble, puis chaque titan qui rougit, puis la glace qui éclate
      {{0, 40, 0, 14, 4, 0, 20, 0}, {0.085F, 38, 0, 13, 4, 0, 20, 0},
         {0.09F, 30, 17, 5, 6, 1, 32, 28}, {0.265F, 29, 16, 5.3F, 6.5F, 0, 32, 28},
         {0.27F, 30, -17, 5, 6, 1, 32, -28}, {0.445F, 29, -16, 5.3F, 6.5F, 0, 32, -28},
         {0.45F, 17, 10, 4, 6, 1, 8, 16}, {0.625F, 16, 9.5F, 4.3F, 6.5F, 0, 8, 16},
         {0.63F, 17, -10, 4, 6, 1, 8, -16}, {0.8F, 16, -9.5F, 4.3F, 6.5F, 0, 8, -16},
         {0.81F, 44, 0, 10, 3, 1, 20, 0}, {1, 48, 0, 13, 3, 0, 20, 0}},
      {{0}},
      // la vague de chaleur : du piédestal (32 devant lui) jusqu'à sa prison (8 devant lui)
      {{0, 40, 10, 12, 1, 0, 32, 0}, {0.35F, 28, 12, 9, 1, 0, 22, 0}, {0.7F, 19, 8, 6, 3, 0, 10, 0}, {1, 21, 0, 5, 4, 0, 8, 0}}
   };

   @Override
   public float[][] cinematicKeys(int kind) {
      if (kind == DEATH_K) return deathKeys();
      return CINE[Mth.clamp(kind, 0, 6)];
   }

   /**
    * Plans de la mort, calés sur la Gueule (cherchée dans le monde, côté client) : à genoux ; contre-plongée sur la Gueule qui
    * s'éveille ; profil large de l'âme qui monte ; la gueule qui avale (vue d'en dessous) ; les mains qui jaillissent (trois
    * quarts arrière) ; il résiste et s'enfonce (trois quarts face) ; la tête éclate (face, serré) ; plan d'ensemble final.
    */
   private float[][] deathKeys() {
      float mf = 0, mr = 0, mh = 18;
      BlockPos c = this.blockPosition();
      double bd = Double.MAX_VALUE;
      Vec3 fw = fwd(), rt = right();
      for (BlockPos p : BlockPos.betweenClosed(c.offset(-40, 2, -40), c.offset(40, 34, 40))) {
         if (this.level().getBlockState(p).is(ModBlocks.SOUL_MAW.get())) {
            double d = p.distSqr(c);
            if (d < bd) {
               bd = d;
               Vec3 rel = Vec3.atBottomCenterOf(p).add(0, -4.1, 0).subtract(this.position());
               mf = (float) rel.dot(fw);
               mr = (float) rel.dot(rt);
               mh = (float) rel.y;
            }
         }
      }
      // plans larges : on voit toujours tout le corps, la Gueule et les mains en même temps
      return new float[][] {
         {0, 18, 11, 6, 4}, {0.075F, 16, 10, 5.5F, 3.5F},
         {0.08F, -5, 13, 4, mh, 1, mf, mr}, {0.17F, -4, 12, 4.5F, mh, 0, mf, mr},
         {0.175F, mf * 0.5F - 3, 20 + mr * 0.5F, 8, mh * 0.35F, 1, mf * 0.3F, mr * 0.3F}, {0.38F, mf * 0.5F, 19 + mr * 0.5F, 11, mh * 0.8F, 0, mf * 0.8F, mr * 0.8F},
         {0.385F, mf + 9, mr + 8, mh - 10, mh, 1, mf, mr}, {0.42F, mf + 8.5F, mr + 7.5F, mh - 9.5F, mh, 0, mf, mr},
         {0.425F, 15, 14, 7, 3.5F, 1, 0, 0}, {0.56F, 14, 13, 6.5F, 3, 0, 0, 0},
         {0.565F, 17, -12, 7, 3.5F, 1, 1, 0}, {0.74F, 16, -11, 6.5F, 2.5F, 0, 2, 0},
         {0.745F, 13, 7, 5, 1.5F, 1, 4.9F, 0}, {0.8F, 14, 8, 5.5F, 1.2F, 0, 4.9F, 0},
         {0.805F, 14, 8, 6, 2.2F, 0, 4.9F, 0}, {1, 15, 8.5F, 9, 2.0F, 0, 4.9F, 0}};   // on reste sur le plan d'avant et on s'élève
   }

   @Override
   public String[] cinematicTitles(int kind) {
      return switch (kind) {
         case PHASE2_K -> new String[] {"title.heralisz_mammoth.p2.top", "title.heralisz_mammoth.p2.name", "title.heralisz_mammoth.p2.sub"};
         case PHASE3_K -> new String[] {"title.heralisz_mammoth.p3.top", "title.heralisz_mammoth.p3.name", "title.heralisz_mammoth.p3.sub"};
         case DEATH_K -> new String[] {"title.heralisz_mammoth.death.top", "title.heralisz_mammoth.death.name", "title.heralisz_mammoth.death.sub"};
         case HEAT_K -> null;
         case THAW_K -> new String[] {"title.heralisz_mammoth.thaw.top", "title.heralisz_mammoth.thaw.name", "title.heralisz_mammoth.thaw.sub"};
         default -> new String[] {"title.heralisz_mammoth.spawn.top", "title.heralisz_mammoth.spawn.name", "title.heralisz_mammoth.spawn.sub"};
      };
   }

   @Override
   public float cinematicTitleAt(int kind) {
      return kind == SPAWN_K ? 0.8F : kind == DEATH_K ? 0.9F : kind == THAW_K ? 0.74F : 0.55F;
   }

   @Override
   public Vec3 cinematicFacing(int kind) {
      if (kind != PHASE2_K && kind != PHASE3_K) return null;
      // changement de phase : les plans se placent toujours du côté du centre de la salle (jamais contre un mur)
      Vec3 c = roomCentreClient();
      if (c == null) return null;
      Vec3 d = c.subtract(this.position());
      return d.x * d.x + d.z * d.z < 9 ? null : d;
   }

   private Vec3 roomCentre;

   /** Centre de la salle vu du client : sous la Gueule des Ancêtres (cherchée une fois). */
   private Vec3 roomCentreClient() {
      if (roomCentre != null) return roomCentre;
      BlockPos c = this.blockPosition();
      double bd = Double.MAX_VALUE;
      for (BlockPos p : BlockPos.betweenClosed(c.offset(-40, 2, -40), c.offset(40, 34, 40))) {
         if (this.level().getBlockState(p).is(ModBlocks.SOUL_MAW.get())) {
            double d = p.distSqr(c);
            if (d < bd) {
               bd = d;
               roomCentre = new Vec3(p.getX() + 0.5, this.getY(), p.getZ() + 0.5);
            }
         }
      }
      return roomCentre;
   }

   @Override
   public boolean cinematicFollows(int kind) {
      return kind == PHASE2_K || kind == PHASE3_K;
   }

   @Override
   public int accentColor() {
      return phase() == 3 ? 0xCFF2FF : phase() == 2 ? 0x7AC8FF : 0x5AA0D8;
   }

   public int hudTop(int ph) {
      return ph == 3 ? 0xE8FAFF : ph == 2 ? 0x8AD8FF : 0x6AB4E8;
   }

   public int hudBottom(int ph) {
      return ph == 3 ? 0x3A7AB0 : ph == 2 ? 0x1E5A96 : 0x163E6A;
   }

   // ------------------------------------------------------------------ GeckoLib
   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      RawAnimation walk = RawAnimation.begin().thenLoop("animation.mammoth.walk");
      RawAnimation idle = RawAnimation.begin().thenLoop("animation.mammoth.idle");
      RawAnimation death = RawAnimation.begin().thenPlayAndHold("animation.mammoth.death");
      RawAnimation dwalk = RawAnimation.begin().thenLoop("animation.mammoth.death_walk");
      RawAnimation sleeping = RawAnimation.begin().thenLoop("animation.mammoth.dormant");
      RawAnimation charge = RawAnimation.begin().thenLoop("animation.mammoth.charge");
      RawAnimation run = RawAnimation.begin().thenLoop("animation.mammoth.run");
      RawAnimation back = RawAnimation.begin().thenLoop("animation.mammoth.back");
      RawAnimation turn = RawAnimation.begin().thenLoop("animation.mammoth.turn");
      reg.add(new AnimationController<>(this, "move", 6, s -> {
         if (this.isDeadOrDying() || dormant()) return PlayState.STOP;
         int mm = moveMode();
         if (mm == M_TURN) return s.setAndContinue(turn);
         if (!s.isMoving()) return s.setAndContinue(idle);
         return s.setAndContinue(mm == M_RUN ? run : mm == M_BACK ? back : walk);
      }));
      reg.add(new AnimationController<>(this, "action", 4, s -> {
         if (this.isDeadOrDying()) return s.setAndContinue(this.entityData.get(DEATH_WALK) ? dwalk : death);
         if (dormant()) {
            clientLastActionTick = actionTick();
            return s.setAndContinue(sleeping);
         }
         Action a = action();
         if (a == Action.NONE) {
            clientLastActionTick = -1;
            return PlayState.STOP;
         }
         if (clientLastActionTick != actionTick()) {
            clientLastActionTick = actionTick();
            s.getController().forceAnimationReset();
         }
         if (a == Action.CHARGE) return s.setAndContinue(charge);
         return s.setAndContinue(RawAnimation.begin().thenPlay("animation.mammoth." + a.name().toLowerCase(Locale.ROOT)));
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
