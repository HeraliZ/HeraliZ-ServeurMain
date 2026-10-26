package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.sound.Sounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

/**
 * Sylvorn, le Cerf Muté : colosse décharné mi-homme mi-cerf, bras surnuméraires dans le dos, masque de crâne qui se brise.
 *  Phase 1 : étreinte des racines (attrape, soulève, fracasse), balayage, piétinement, tronc arraché et lancé, graines, champ de racines,
 *            charge des bois (qui l'étourdit s'il percute un arbre ou un mur), volées de javelots d'épines (contre les tireurs).
 *  Phase 2 : le masque se fend ; enracinement (3 nœuds de sève à abattre), hurlement en cône, marque du chasseur sur les tireurs
 *            éloignés, disparition dans la brume : il rôde invisible (empreintes, pas lourds) et surgit dans le dos de sa proie.
 *  Phase 3 : le masque vole en éclats ; il chasse à quatre pattes, bondit sur les tireurs, chute des arbres, tempête de feuilles.
 *  L'Arbre-Monde : il était le gardien de l'Arbre et il a pourri avec lui. Il dort dans la paroi du tronc, pris dans l'écorce
 *  comme un bas-relief ; le cœur vert, tout là-haut, bat de plus en plus vite ; l'écorce se fend, il s'en arrache bras par bras,
 *  puis la tête et les bois, tombe à quatre pattes, se redresse et rugit (15 s).
 *  Mort (29 s) : deux branches descendent de la voûte et lui saisissent les chevilles ; il est hissé la tête en bas, se débat,
 *  griffe les branches ; elles lui arrachent les jambes ; le tronc tombe, rampe vers les joueurs sur ses griffes en hurlant ; une
 *  troisième branche le saisit au buste et le soulève ; le cœur aspire sa vie (filaments verts) ; il se dessèche en bois mort et
 *  l'Arbre le laisse tomber.
 * <p>
 * Classe COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5) : état synchronisé, animations, cinématiques, HUD et calculs lus par les
 * rendus. IA, attaques, phases, dégâts, mort scénarisée, butin et sauvegarde : SylvornServeur (usine SYLVORN_USINE).
 */
public class Sylvorn extends Monster implements GeoEntity, CineSource {
   public enum Action {
      NONE(0), SPAWN(300), PHASE2(160), PHASE3(180), SWEEP(30), STOMP(36), GRASP(80), LOG(50), SEEDS(40), ROOTFIELD(46), CHANNEL(160),
      STUN(50), TREEFALL(62), LEAFSTORM(100), HOWL(60), THORNS(44), MARK(56), CHARGE(60), VANISH(32), STALK(50), AMBUSH(36), POUNCE(60),
      GORE(40);
      public final int ticks;

      Action(int t) {
         this.ticks = t;
      }
   }

   public static final float RS = 1.7F;
   /** Cinématiques. */
   public static final int SPAWN_K = 0, PHASE2_K = 1, PHASE3_K = 2, DEATH_K = 3, ENTRY_K = 4, OFFER_K = 5;
   /** Apparition : l'écorce se fend (SP_CRACK), le bras droit s'arrache (SP_ARM_R), l'écorce éclate (SP_BURST), le gauche
    * (SP_ARM_L), la tête et les bois (SP_HEAD) ; il bascule hors de la paroi (SP_OUT0 → SP_OUT1), s'écrase (SP_LAND), se relève,
    * rugit (SP_ROAR). */
   public static final int SP_CRACK = 60, SP_ARM_R = 100, SP_BURST = 100, SP_ARM_L = 130, SP_HEAD = 160, SP_OUT0 = 160, SP_OUT1 = 181,
      SP_LAND = 181, SP_ROAR = 264;
   /** Mort : les branches descendent (D_REACH) et saisissent ses chevilles (D_GRAB) ; hissé la tête en bas (D_LIFT0 → D_LIFT1),
    * il se débat ; les jambes arrachées (D_TEAR), le tronc tombe (→ D_LAND) et rampe (→ D_GRAB2) ; la troisième branche le
    * soulève (→ D_UP2), le cœur l'aspire (D_DRAIN0 → D_DRAIN1), il est lâché (D_DROP → D_DROPPED) ; butin (D_LOOT). */
   public static final int D_REACH = 18, D_GRAB = 50, D_LIFT0 = 50, D_LIFT1 = 90, D_TEAR = 196, D_LAND = 212, D_REACH2 = 318, D_GRAB2 = 348,
      D_UP2 = 392, D_DRAIN0 = 400, D_DRAIN1 = 500, D_DROP = 520, D_DROPPED = 534, D_LOOT = 548, DEATH_TICKS = 600, REMAINS = 6000;
   /** Hauteur des chevilles quand il pend (au-dessus du sol), hauteur du buste soulevé, hauteur des hanches du modèle (blocs). */
   public static final double HANG_H = 12.5, LIFT2_H = 7.5, HIPS_H = 50 / 16.0 * RS;
   /** Stades synchronisés de la mort (animations, rendu) : 0 coup, 1 pendu, 2 arraché, 3 rampe, 4 soulevé, 5 aspiré, 6 flétri. */
   public static final int DS_HIT = 0, DS_HANG = 1, DS_TEAR = 2, DS_CRAWL = 3, DS_LIFT = 4, DS_DRAIN = 5, DS_WITHERED = 6;
   static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(Sylvorn.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Sylvorn.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION_TICK = SynchedEntityData.defineId(Sylvorn.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> VIRTUAL_MAX = SynchedEntityData.defineId(Sylvorn.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(Sylvorn.class, EntityDataSerializers.BOOLEAN);
   /** Temps de mort (serveur → client : un client qui le voit tard reprend au bon stade). */
   static final EntityDataAccessor<Integer> DEATH_T = SynchedEntityData.defineId(Sylvorn.class, EntityDataSerializers.INT);
   /** Où sont les chevilles / le buste tenus par les branches (monde), et d'où viennent les branches. */
   static final EntityDataAccessor<org.joml.Vector3f> DEATH_AT = SynchedEntityData.defineId(Sylvorn.class, EntityDataSerializers.VECTOR3);

   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public int clientLastActionTick = -1;

   public Sylvorn(EntityType<? extends Sylvorn> type, Level level) {
      super(type, level);
      this.xpReward = 500;
      this.setPersistenceRequired();
      this.setMaxUpStep(2.0F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.ARMOR, 16.0).add(Attributes.ARMOR_TOUGHNESS, 8.0)
         .add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.FOLLOW_RANGE, 56.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   // ------------------------------------------------------------------ état
   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(PHASE, 1);
      this.entityData.define(ACTION, 0);
      this.entityData.define(ACTION_TICK, 0);
      this.entityData.define(VIRTUAL_MAX, 6000);
      this.entityData.define(DORMANT, false);
      this.entityData.define(DEATH_T, 0);
      this.entityData.define(DEATH_AT, new org.joml.Vector3f());
      this.entityData.define(DEATH_YAW, 0F);
   }

   public int phase() {
      return this.entityData.get(PHASE);
   }

   public Action action() {
      return Action.values()[this.entityData.get(ACTION)];
   }

   public int actionTick() {
      return this.entityData.get(ACTION_TICK);
   }

   public int actionTime() {
      return (int) this.level().getGameTime() - actionTick();
   }

   /** Tient un tronc arraché dans la main (rendu). */
   public boolean holdingLog() {
      int t = actionTime();
      return action() == Action.LOG && t >= 12 && t < 30;
   }

   public int virtualMax() {
      return this.entityData.get(VIRTUAL_MAX);
   }

   // ------------------------------------------------------------------ BossInfo
   public String hudName() {
      return "SYLVORN";
   }

   public SoundEvent music() {
      return Sounds.MUSIC_SYLVORN.get();
   }

   public int hudTop(int ph) {
      return ph == 3 ? 0xD8FF7A : ph == 2 ? 0xB8F07A : 0x8EDC5A;
   }

   public int hudBottom(int ph) {
      return ph == 3 ? 0x6A2A1E : ph == 2 ? 0x4A9A32 : 0x2E7A2A;
   }

   public int hudNameColor() {
      return 0xE8F2D0;
   }

   @Override
   public int accentColor() {
      return phase() == 3 ? 0xC8FF6A : 0x9CE06A;
   }

   private static final float[][][] CINE = {
      // RÉVEIL (15 s) — repère : la niche, devant = vers le centre du tronc. On lève les yeux vers le cœur qui bat tout là-haut ;
      // on redescend le long du tronc jusqu'au bas-relief ; l'écorce se fend ; le bras droit s'arrache (de trois quarts) ; le
      // gauche (contrechamp) ; gros plan sur la tête qui s'arrache ; il s'écrase au sol (large, bas) ; il se relève ; contre-plongée
      // sur le rugissement
      {{0, 14, 4, 2.5F, 70, 0, 24, 0}, {0.1F, 15, 4, 3, 45, 0, 18, 0}, {0.18F, 16, 3, 4, 6, 0, 0, 0}, {0.22F, 12, 2, 4.5F, 6, 0, 0, 0},
         {0.31F, 13, 3, 5, 6.5F, 0, 0, 0}, {0.37F, 12.3F, 4.5F, 5, 7, 0, 0, 0}, {0.42F, 11.6F, -3.5F, 5, 7, 1, 0, 0}, {0.48F, 12.3F, -4, 5.5F, 8, 0, 0, 0},
         {0.52F, 11, -1.5F, 8, 10, 1, 0, 0}, {0.56F, 11, -1, 8.5F, 9.5F, 0, 0, 0}, {0.6F, 16, 7, 1.5F, 3.5F, 1, 4, 0},
         {0.72F, 15, 7.5F, 1.5F, 3.5F, 0, 4, 0}, {0.82F, 12, 5, 1.5F, 7, 0, 4, 0}, {0.88F, 14.5F, 3, 1, 10, 1, 4, 0}, {1, 11, 4, 1.2F, 10, 0, 4, 0}},
      // PHASE 2 (8 s) : il recule sous le coup (large, de trois quarts) ; plan bas qui monte avec son regard vers le cœur qui
      // l'appelle ; il plante ses griffes dans le sol (moyen, l'anneau de racines jaillit) ; gros plan, le masque se fend ;
      // contre-plongée large sur le rugissement
      {{0, 14, 7, 3, 6F}, {0.14F, 12, 8, 3.5F, 6.5F}, {0.17F, 5, 3, 1, 12F, 1}, {0.3F, 4.5F, 2.5F, 1.2F, 16F},
         {0.32F, 10, -7, 2, 3F, 1}, {0.52F, 9, -8, 2.5F, 3.5F}, {0.55F, 6, 2.5F, 9.5F, 8.5F, 1}, {0.67F, 5.5F, 2, 9, 8.8F},
         {0.69F, 12, -7, 1.2F, 7.5F, 1}, {1, 16, -9, 1.5F, 7F}},
      // PHASE 3 (9 s) : plié en deux, griffes dans la terre ; de dos, l'écorce de son dos éclate ; gros plan, il agrippe son
      // propre masque et l'arrache ; travelling arrière en plongée large sur le dernier rugissement
      {{0, 11, 6, 2, 3F}, {0.3F, 9, 7, 2.5F, 3.5F}, {0.33F, -8, 4, 5, 6F, 1}, {0.44F, -7, 5, 6, 6.5F},
         {0.46F, 6, -1.5F, 8, 7.5F, 1}, {0.62F, 5, -1, 8.5F, 8.5F}, {0.64F, 10, -8, 3, 6F, 1}, {1, 18, -11, 9, 4F}},
      // MORT : clés calculées (cinematicKeys)
      {{0, 9, 4, 3, 5F}, {1, 16, 8, 6, 1F}},
      // ENTRÉE (15 s) — repère : le mur de racines, devant = vers le tronc. Derrière l'équipe, on fixe le mur qui se tord ; il
      // s'ouvre ; on entre avec eux en levant les yeux ; le tronc immense, le cœur vert tout là-haut ; de l'intérieur, on voit le mur
      // se refermer ; puis, au fond, dans la paroi, la silhouette prise dans l'écorce
      {{0, -10, 1.5F, 2.4F, 2.5F, 0, 1, 0}, {0.12F, -9, 1.2F, 2.3F, 3F, 0, 1, 0}, {0.2F, -6, 0.8F, 2.5F, 4F, 0, 6, 0},
         {0.34F, 1, 0.5F, 3, 12F, 0, 16, 0}, {0.44F, 6, -8, 3, 22F, 1, 28, 0}, {0.62F, 9, -10, 5, 34F, 0, 28, 0},
         {0.66F, 12, 2, 3, 2.5F, 1, -4, 0}, {0.78F, 12, 3, 3.2F, 3F, 0, -4, 0}, {0.82F, 16, 8, 3, 7F, 1, 50, 0}, {1, 18, 10, 3, 7F, 0, 50, 0}},
      // OFFRANDE (12 s) — repère : l'autel, devant = vers le mur de racines. La graine posée dans la gueule de bois ; la sève monte
      // dans les racines de l'autel ; la lumière court jusqu'au mur, qui se met à remuer
      {{0, -3, 2, 2.2F, 1.2F, 0, 0, 0}, {0.3F, -3.5F, 2.5F, 2.4F, 1.4F, 0, 0, 0}, {0.45F, -4, 3, 3, 3F, 0, 5, 0}, {0.75F, -3, 3.5F, 3, 4F, 0, 7, 0},
         {1, -2, 4, 3.2F, 4.5F, 0, 7, 0}}
   };

   /** La cinématique de mort dure un peu plus que la mort (plan final sur la dépouille). */
   public static final int DEATH_CINE = DEATH_TICKS + 20;

   @Override
   public float[][] cinematicKeys(int kind) {
      if (kind == DEATH_K) {
         float T = DEATH_CINE;
         float[][] k = {{0, 9, 4, 3, 5}, {D_REACH / T, 10, 5, 2, 12}, {(D_GRAB - 4) / T, 11, 6, 1.5F, 8}, {D_GRAB / T, 8, 3, 1, 1.5F, 1, 0, 0},
            {(D_GRAB + 12) / T, 9, 4, 1.2F, 4, 0, 0, 0}, {D_LIFT1 / T, 14, 8, 1, 9, 1, 0, 0}, {(D_TEAR - 14) / T, 12, 7, 2, 9, 0, 0, 0},
            {(D_TEAR - 4) / T, 8, 4, 8, 9.5F, 1, 0, 0}, {D_LAND / T, 10, 5, 3, 2, 0, 0, 0}, {(D_LAND + 6) / T, 9, 1.5F, 1, 1.2F, 1, 0, 0},
            {D_REACH2 / T, 13, 1.2F, 1.1F, 1.3F, 0, 5, 0}, {D_GRAB2 / T, 12, 6, 2, 5, 1, 6, 0}, {D_UP2 / T, 12, 6, 1.5F, 9, 0, 6, 0},
            {D_DRAIN0 / T, 18, 10, 1, 20, 1, 6, 0}, {(D_DRAIN1 + 8) / T, 20, 12, 1, 26, 0, 6, 0}, {D_DROP / T, 12, 6, 3, 6, 1, 6, 0},
            {D_LOOT / T, 11, 5, 3, 1.5F, 0, 6, 0}, {1, 16, 8, 6, 1, 0, 6, 0}};
         return k;
      }
      return CINE[Mth.clamp(kind, 0, CINE.length - 1)];
   }

   @Override
   public String[] cinematicTitles(int kind) {
      return switch (kind) {
         case PHASE2_K -> new String[] {"title.heralisz_sylvorn.p2.top", "title.heralisz_sylvorn.p2.name", "title.heralisz_sylvorn.p2.sub"};
         case PHASE3_K -> new String[] {"title.heralisz_sylvorn.p3.top", "title.heralisz_sylvorn.p3.name", "title.heralisz_sylvorn.p3.sub"};
         case DEATH_K -> new String[] {"title.heralisz_sylvorn.death.top", "title.heralisz_sylvorn.death.name", "title.heralisz_sylvorn.death.sub"};
         case ENTRY_K -> new String[] {"title.heralisz_sylvorn.entry.top", "title.heralisz_sylvorn.entry.name", "title.heralisz_sylvorn.entry.sub"};
         default -> new String[] {"title.heralisz_sylvorn.spawn.top", "title.heralisz_sylvorn.spawn.name", "title.heralisz_sylvorn.spawn.sub"};
      };
   }

   @Override
   public float cinematicTitleAt(int kind) {
      return kind == SPAWN_K ? 0.88F : kind == DEATH_K ? 0.9F : kind == ENTRY_K ? 0.06F : kind == OFFER_K ? 2F : 0.62F;
   }

   @Override
   public boolean cinematicFollows(int kind) {
      return kind == PHASE2_K || kind == PHASE3_K;
   }

   public boolean dormant() {
      return this.entityData.get(DORMANT);
   }

   @Override
   public boolean isInvulnerableTo(DamageSource s) {
      return s.is(DamageTypes.FALL) || s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.DROWN) || s.is(DamageTypes.CACTUS) || s.is(DamageTypes.SWEET_BERRY_BUSH)
         || super.isInvulnerableTo(s);
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

   // ------------------------------------------------------------------ mort : l'Arbre reprend son gardien
   static final EntityDataAccessor<Float> DEATH_YAW = SynchedEntityData.defineId(Sylvorn.class, EntityDataSerializers.FLOAT);
   /** Là où il finit de ramper : posé par la mort scénarisée (SylvornServeur), lu par branchAnchor (null côté client). */
   Vec3 crawlGoal;

   /** Temps de mort côté client (repris du serveur si l'entité est vue tard). */
   public float deathClock(float partial) {
      return Math.max(this.deathTime, this.entityData.get(DEATH_T)) + partial;
   }

   /** Là où il est mort (au sol), et son cap à ce moment (les branches viennent de part et d'autre). */
   public Vec3 deathOrigin() {
      org.joml.Vector3f v = this.entityData.get(DEATH_AT);
      return new Vec3(v.x, v.y, v.z);
   }

   public float deathYaw() {
      return this.entityData.get(DEATH_YAW);
   }

   public int deathStage(float t) {
      if (t < D_LIFT0) return DS_HIT;
      if (t < D_TEAR) return DS_HANG;
      if (t < D_LAND) return DS_TEAR;
      if (t < D_GRAB2) return DS_CRAWL;
      if (t < D_DRAIN0) return DS_LIFT;
      if (t < D_DROP) return DS_DRAIN;
      return DS_WITHERED;
   }

   static double ease(double u) {
      u = Mth.clamp(u, 0, 1);
      return u * u * (3 - 2 * u);
   }

   /** Bascule du corps (degrés, autour de l'axe gauche-droite) : la tête en bas quand il pend, puis il retombe à plat ventre. */
   public float flip(float t) {
      if (t < D_LIFT0) return 0;
      if (t < D_TEAR) return (float) (180 * ease((t - D_LIFT0) / 24.0));
      if (t < D_LAND) return (float) (180 + 180 * ease((t - D_TEAR) / (double) (D_LAND - D_TEAR)));
      return 0;
   }

   /** Pivot de la bascule, en unités du modèle (avant l'échelle) : les chevilles, puis les hanches une fois les jambes arrachées. */
   public float flipPivot(float t) {
      return t < D_TEAR ? 0.3F : 50 / 16F;
   }

   /** Il n'a plus de jambes. */
   public boolean legless(float t) {
      return this.isDeadOrDying() && t >= D_TEAR;
   }

   /** Dessèchement (0 → 1) : la vie aspirée par le cœur. */
   public float withered(float partial) {
      if (!this.isDeadOrDying()) return 0;
      return (float) ease((deathClock(partial) - D_DRAIN0 - 10) / (double) (D_DRAIN1 - D_DRAIN0 - 10));
   }

   /** Les deux branches de la voûte (0 : sa droite, 1 : sa gauche) et la troisième (2), qui prend le buste : leur ancrage. */
   public Vec3 branchAnchor(int i) {
      Vec3 o = deathOrigin();
      Vec3 f = Vec3.directionFromRotation(0, deathYaw()), r = new Vec3(-f.z, 0, f.x);
      if (i == 2) return crawlEnd().add(r.scale(-5)).add(f.scale(-2)).add(0, 30, 0);
      return o.add(r.scale(i == 0 ? 6.5 : -6.5)).add(f.scale(-4)).add(0, 32, 0);
   }

   /** Là où il finit de ramper (le serveur le synchronise dans sa position ; le client l'estime). */
   private Vec3 crawlEnd() {
      return crawlGoal != null ? crawlGoal : this.position();
   }

   /**
    * Côté client : le temps de mort avance (lu par les rendus : stades de la mort, bascule, dessèchement). La mort scénarisée
    * (côté serveur : branches, jambes arrachées, reptation, aspiration, butin) est dans SylvornServeur ; sans partie serveur
    * dans le jar, mort vanilla.
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
   private static final String A = "animation.sylvorn.";
   private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(A + "idle");
   private static final RawAnimation WALK = RawAnimation.begin().thenLoop(A + "walk");
   private static final RawAnimation RUN = RawAnimation.begin().thenLoop(A + "run");
   private static final RawAnimation[] DEATH_ANIMS = {RawAnimation.begin().thenPlayAndHold(A + "death_hit"),
      RawAnimation.begin().thenLoop(A + "death_hang"), RawAnimation.begin().thenPlayAndHold(A + "death_tear"),
      RawAnimation.begin().thenLoop(A + "crawl"), RawAnimation.begin().thenLoop(A + "lifted"),
      RawAnimation.begin().thenPlayAndHold(A + "drained"), RawAnimation.begin().thenLoop(A + "withered")};
   private int clientDeathStage = -1;

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      reg.add(new AnimationController<>(this, "move", 6, s -> {
         if (this.isDeadOrDying() || dormant()) return PlayState.STOP;
         // phase 3 : il chasse à quatre pattes
         return s.setAndContinue(s.isMoving() ? (phase() == 3 ? RUN : WALK) : IDLE);
      }));
      reg.add(new AnimationController<>(this, "action", 4, s -> {
         if (this.isDeadOrDying()) {
            int st = deathStage(deathClock(0));
            if (st != clientDeathStage) {
               clientDeathStage = st;
               s.getController().setTransitionLength(st == DS_TEAR || st == DS_HANG ? 2 : 5);
               s.getController().forceAnimationReset();
            }
            return s.setAndContinue(DEATH_ANIMS[st]);
         }
         if (dormant()) {
            clientLastActionTick = -1;
            return PlayState.STOP;
         }
         Action a = action();
         if (a == Action.NONE || a == Action.STALK) {
            clientLastActionTick = -1;
            return PlayState.STOP;
         }
         if (clientLastActionTick != actionTick()) {
            clientLastActionTick = actionTick();
            s.getController().setTransitionLength(a == Action.SPAWN ? 0 : 4);
            s.getController().forceAnimationReset();
         }
         String n = a == Action.SPAWN ? "spawn_bark" : a.name().toLowerCase();
         return s.setAndContinue(RawAnimation.begin().thenPlay(A + n));
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
