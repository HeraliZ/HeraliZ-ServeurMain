package fr.heralisz.mere.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.Locale;

/**
 * La Mère des Ruines : araignée de chair, d'os et de ferraille, une femme pendue la tête en bas sous son céphalothorax. Elle dort
 * dans le cocon géant accroché à la voûte du Nid ; l'offrande d'un œuf sur l'autel la réveille.
 *  Phase 1 : morsure, faux des pattes avant, globes de toile (zones qui engluent), harpon de soie (ramène le tireur à découvert
 *            jusqu'à ses crochets), barrage de soie, couvée d'araignées, bond sur les tireurs.
 *  Phase 2 : le sac crève (des mains poussent de l'intérieur) ; pluie de cocons d'œufs (éclosent si on ne les évite pas), paquet de
 *            soie (un joueur emmailloté, immobile et aveugle 3 s), rejetons qui tombent du sac.
 *  Phase 3 : la pendue hurle avec elle ; frénésie de morsures, chute du ciel (elle remonte à son fil, son ombre traque le tireur le
 *            plus éloigné puis elle s'écrase dessus), harpon sur deux tireurs ; les grappes de la voûte crèvent sur les joueurs.
 *  Éclosion (18 s) : le cocon bat, des rejetons s'y accrochent ; ses pattes percent la soie, elle en glisse, descend au bout de
 *  son fil, lâche le fil, se retourne en tombant, se dresse et hurle.
 *  Mort (23 s) : elle bascule sur le dos ; les rejetons descendent des grappes au bout de leur fil, tournent autour d'elle et
 *  l'emmaillotent ; puis la soie la hisse sous la voûte, où son cocon reste pendu.
 */
public class RuinMother extends Monster implements GeoEntity, CineSource {
   public enum Action {
      NONE(0), SPAWN(360), PHASE2(160), PHASE3(170), BITE(24), SWEEP(30), WEBSHOT(36), PULL(60), BROOD(50), LEAP(56), EGGRAIN(60), FRENZY(70),
      COCOON(32), ASCEND(28), LURK(40), DROP(28), BARRAGE(56), HOP(186);
      public final int ticks;

      Action(int t) {
         this.ticks = t;
      }
   }

   /** Échelle du modèle. */
   public static final float RS = 1.3F;
   public static final int SPAWN_K = 0, PHASE2_K = 1, PHASE3_K = 2, DEATH_K = 3, ENTRY_K = 4, OFFER_K = 5;
   /**
    * Mort (20 s) : elle se cabre, titube, bascule sur le dos (animation « death », 2 s) ; six rejetons apparaissent sur la paroi
    * d'en face (WALL0), la descendent tête en bas, traversent la salle et viennent se poster devant elle, puis se glissent dessous
    * en la soulevant peu à peu (SLIDE0 → SLIDE1) ; ils la portent au pied d'un pilier (→ CARRY1) et, sans la poser, la basculent
    * contre le fût et le gravissent d'un seul mouvement, elle sur leur dos (→ RAISE1) ; toutes l'emmaillotent contre le pilier en tournant (WRAP0 → WRAP1) ; elles remontent dans l'ombre.
    */
   public static final int DEATH_TICKS = 842, REMAINS = 6000, WALL0 = 30, SLIDE0 = 252, SLIDE1 = 282, CARRY1 = 337, SET1 = 347, TOPUP1 = 477,
      RAISE1 = 557, WRAP0 = 557, SECURE = 80, WRAP1 = 777, LEAVE0 = 777, LEAVE1 = 837, LOOT = 757;
   /** Les fileuses descendent les fûts (blocs/tick), puis traversent la salle : lentement, on doit les voir venir. */
   static final double CREW_SPEED = 0.13, CREW_RUN = 0.24;
   /**
    * Géométrie (blocs, échelle du modèle comprise) : fût rond du pilier (rayon PILLAR_R) ; retournée sur le dos, son dos est à LIFT
    * au-dessus du sol une fois soulevée (hauteur d'un rejeton) ; le centre de son corps est à BODY_UP de son origine (vers son
    * ventre) et BODY_BACK en arrière ; contre le pilier, le centre du cocon est à BODY_OUT du fût ; demi-longueur du cocon.
    */
   public static final double PILLAR_R = 2.5, LIFT = 0.91, BODY_UP = 1.87, BODY_BACK = 1.58, BODY_OUT = LIFT + BODY_UP, COCOON_HALF = 5.0,
      COCOON_OUT = LIFT + 2.11, COCOON_UP = 0.6;
   /** Demi-largeur et demi-épaisseur du cocon (le fuseau de soie du rendu, pattes repliées comprises). */
   public static final float COCOON_A = 3.35F, COCOON_B = 3.75F;
   /**
    * Le convoi (repère du modèle, blocs ; +z = vers l'arrière) : six porteuses sous son dos, deux rangées de trois espacées
    * d'une envergure (1,9 x 1,7 bloc : elles ne se chevauchent pas) ; quatre escorteuses qui marchent à côté d'elle.
    */
   static final double[][] SLOTS = {{1.05, -0.9}, {-1.05, -0.9}, {1.25, 1.1}, {-1.25, 1.1}, {1.0, 3.0}, {-1.0, 3.0}};
   /** Sur le pilier : les trois du haut tiennent sa tête (côté x), les trois du bas poussent sous son abdomen. */
   static final double[] TOP_X = {1.6, -1.6, 0.6, -0.6, 2.4, -2.4}, PUSH_X = {-1.2, 1.2, 0.0};
   /** Les six grimpent : décalage en hauteur de chacune sur le fût (deux rangs). */
   static final double[] TOP_H = {0, 0, 1.1, 1.1, -0.9, -0.9};
   /** Hauteur de son origine au-dessus du sol une fois sur le dos (le dos de l'abdomen affleure le sol), puis posée au pilier. */
   static final double BACK_Y = 0.21, SET_Y = 0.5;
   static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Quaternionf> ORIENT =
      net.minecraft.network.syncher.SynchedEntityData.defineId(RuinMother.class, net.minecraft.network.syncher.EntityDataSerializers.QUATERNION);
   /** Temps écoulé depuis la mort (serveur → client) : un client qui la (re)voit tard reste sur la pose « morte ». */
   static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DEATH_T =
      net.minecraft.network.syncher.SynchedEntityData.defineId(RuinMother.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
   static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ORIENT_ON =
      net.minecraft.network.syncher.SynchedEntityData.defineId(RuinMother.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
   /** Axe du pilier (x, z) et hauteur du centre du cocon (y) ; y < -1000 : pas de pilier. */
   static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3f> PILLAR =
      net.minecraft.network.syncher.SynchedEntityData.defineId(RuinMother.class, net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);
   static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3f> PILLAR_F =
      net.minecraft.network.syncher.SynchedEntityData.defineId(RuinMother.class, net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);
   /** Cocon géant (props : hero_shrine) : bas du cocon, centre, demi-hauteur, rayon, au-dessus de ses pieds (blocs). */
   public static final double COCOON_Y = 8.3, COCOON_C = 14.3, COCOON_HH = 6.1, COCOON_R = 4.55;
   /** Les trous que rongent les rejetons (tools/props/mere_shrine.py : RIPS, RIP_T) : angle (degrés, repère du monde), hauteur
    *  par rapport au centre du cocon (blocs), tick de l'éclosion où la soie cède. */
   static final double[][] RIPS = {{330, -0.8}, {210, -0.8}, {295, 1.6}, {245, 1.6}, {352, -2.9}, {188, -2.9}};
   static final int[] RIP_TICK = {34, 46, 58, 70, 82, 94};

   /** Rayon du cocon (blocs) à la hauteur h de son centre : le fuseau de tools/props/mere_shrine.py. */
   public static double cocoonRadius(double h) {
      double u = Mth.clamp(h / COCOON_HH, -1, 1);
      return COCOON_R * Math.pow(1 - u * u, 0.55) * (1 - 0.12 * u);
   }
   /** Éclosion (ticks) : le cocon se déchire, elle lâche son fil, touche le sol, hurle, abat ses pattes avant. */
   public static final int SP_TEAR = 99, SP_RELEASE = 248, SP_LAND = 260, SP_SCREAM = 289, SP_SLAM = 329;

   /** Particules : soie (fils de toile), glu, sang noir, poussière de pierre. */
   public static final ParticleOptions WEB_FX = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
   public static final ParticleOptions GOO_FX = ParticleTypes.ITEM_SLIME;
   public static final ParticleOptions BLOOD_FX = new DustParticleOptions(new Vector3f(0.32F, 0.03F, 0.04F), 1.6F);
   public static final ParticleOptions STONE_FX = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState());
   public static final ParticleOptions AMBER_FX = new DustParticleOptions(new Vector3f(1.0F, 0.66F, 0.22F), 1.2F);

   static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(RuinMother.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(RuinMother.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION_TICK = SynchedEntityData.defineId(RuinMother.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> VIRTUAL_MAX = SynchedEntityData.defineId(RuinMother.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(RuinMother.class, EntityDataSerializers.BOOLEAN);

   static final Action[] ACTS = Action.values();

   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public int clientLastActionTick = -1;
   /** Lieu de la mort, centre du futur cocon, pilier choisi : lus par pickPillar / hangPoint (aussi côté client, pour la
    *  cinématique de mort), écrits par la mort scénarisée de RuinMotherServeur. */
   Vec3 deathFrom, hangAt;
   Vec3 pillarC;

   public RuinMother(EntityType<? extends RuinMother> type, Level level) {
      super(type, level);
      this.xpReward = 0;
      this.setPersistenceRequired();
      this.setMaxUpStep(1.5F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.ARMOR, 12.0).add(Attributes.ARMOR_TOUGHNESS, 6.0)
         .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 64.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.ATTACK_DAMAGE, 12.0);
   }

   // ------------------------------------------------------------------ état synchronisé
   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(PHASE, 1);
      this.entityData.define(ACTION, 0);
      this.entityData.define(ACTION_TICK, 0);
      this.entityData.define(VIRTUAL_MAX, 42000);
      this.entityData.define(DORMANT, false);
      this.entityData.define(ORIENT, new org.joml.Quaternionf());
      this.entityData.define(ORIENT_ON, false);
      this.entityData.define(DEATH_T, 0);
      this.entityData.define(PILLAR, new org.joml.Vector3f(0, -9999, 0));
      this.entityData.define(PILLAR_F, new org.joml.Vector3f(0, 0, 1));
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
      return clock() - actionTick();
   }

   public int virtualMax() {
      return this.entityData.get(VIRTUAL_MAX);
   }

   public boolean dormant() {
      return this.entityData.get(DORMANT);
   }

   /** Orientation libre pendant la mort (portée sur le dos, puis redressée contre le pilier) ; null : orientation normale. */
   public org.joml.Quaternionf orient(float partial) {
      if (!this.entityData.get(ORIENT_ON)) return null;
      org.joml.Quaternionf q = this.entityData.get(ORIENT);
      if (clientCur == null) return new org.joml.Quaternionf(q);
      return new org.joml.Quaternionf(clientPrev).slerp(clientCur, partial);
   }

   private org.joml.Quaternionf clientPrev, clientCur;
   /** Bascule du corps (client, radians) quand elle monte ou descend des marches. */
   public float tilt, tiltO;

   /** Le pilier où on l'emmaillote (axe, et y = hauteur du centre du cocon), ou null. */
   public Vec3 pillarAxis() {
      org.joml.Vector3f v = this.entityData.get(PILLAR);
      return v.y < -1000 ? null : new Vec3(v.x, v.y, v.z);
   }

   /** Direction horizontale d'elle vers le pilier. */
   public Vec3 pillarDir() {
      org.joml.Vector3f v = this.entityData.get(PILLAR_F);
      return new Vec3(v.x, v.y, v.z);
   }

   @Override
   public boolean isPickable() {
      return !dormant() && super.isPickable();
   }

   /** Hauteur (blocs) du pivot du corps pendant l'éclosion, t en secondes (SPAWN_Y de tools/anims/ruin_mother_anims.py). */
   public static double spawnBodyHeight(double t) {
      double[][] k = {{0, 145}, {5.0, 145}, {6.5, 118}, {11.0, 46}, {12.4, 42}, {12.95, 0}};
      double y = 0;
      if (t <= 0) y = k[0][1];
      else if (t >= k[k.length - 1][0]) y = 0;
      else for (int i = 0; i + 1 < k.length; i++) {
         if (t <= k[i + 1][0]) {
            double u = (t - k[i][0]) / (k[i + 1][0] - k[i][0]);
            u = i + 1 == k.length - 1 ? u * u : u * u * (3 - 2 * u);
            y = k[i][1] + (k[i + 1][1] - k[i][1]) * u;
            break;
         }
      }
      return (y + 26) * RS / 16.0;
   }

   @Override
   public boolean isInvulnerableTo(DamageSource s) {
      return s.is(DamageTypes.FALL) || s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.DROWN) || s.is(DamageTypes.CACTUS) || super.isInvulnerableTo(s);
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
   public boolean onClimbable() {
      return false;
   }

   @Override
   public void makeStuckInBlock(BlockState state, Vec3 mult) {
      // la reine des toiles ne s'y prend pas
   }

   // ------------------------------------------------------------------ la mort : la couvée reprend sa mère
   /** Croissance du cocon (0 → 1), lue par le rendu. */
   public float cocoon(float partial) {
      if (!this.isDeadOrDying()) return 0;
      return Mth.clamp((this.deathTime + partial - WRAP0) / (float) (WRAP1 - WRAP0), 0, 1);
   }

   private boolean clientOnPrev;

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         org.joml.Quaternionf q = this.entityData.get(ORIENT);
         boolean on = this.entityData.get(ORIENT_ON);
         // (au premier tick de l'orientation libre : pas d'interpolation depuis l'ancienne valeur, elle tournait sur elle-même)
         clientPrev = clientCur == null || !clientOnPrev ? new org.joml.Quaternionf(q) : clientCur;
         clientCur = new org.joml.Quaternionf(q);
         clientOnPrev = on;
         tiltO = tilt;                                                             // marches, talus : le corps suit la pente
         double dy = getY() - yo;
         float want = onGround() || Math.abs(dy) < 0.6 ? (float) Mth.clamp(dy * 3.2, -0.35, 0.45) : 0F;
         tilt += (want - tilt) * 0.2F;
      }
   }

   /**
    * Côté client : le temps de mort avance (lu par les rendus : cocon, pose « morte », cinématique). La mort scénarisée (côté
    * serveur : rejetons, portage, pilier, tissage, butin) est dans RuinMotherServeur ; sans partie serveur dans le jar, mort vanilla.
    */
   @Override
   protected void tickDeath() {
      if (!this.level().isClientSide) {
         super.tickDeath();
         return;
      }
      this.deathTime = Math.min(this.deathTime + 1, REMAINS);
   }

   /** La cinématique de mort dure 5 s de plus que le tissage : un dernier plan large sur le cocon achevé. */
   static final int DEATH_CINE = DEATH_TICKS + 90;

   /** Rayon d'une bande de soie autour du pilier (voir PillarWrap : même courbe pour les fileuses). */
   public static float loopR(float a, float h) {
      float R = (float) PILLAR_R + 0.12F;
      float L = (float) COCOON_HALF;
      float s = (float) Math.sqrt(Math.max(0, 1 - (h / L) * (h / L)));
      if (s < 0.05F) return R;
      float D = (float) (PILLAR_R + COCOON_OUT);
      float ea = COCOON_A * s, eb = COCOON_B * s;
      float c = Mth.cos(a), sn = Mth.sin(a);
      float A = c * c / (eb * eb) + sn * sn / (ea * ea);
      float B = D * c / (eb * eb);
      float disc = B * B - A * (D * D / (eb * eb) - 1);
      float re = disc > 0 ? (B + Mth.sqrt(disc)) / A : 0;
      return (float) Math.pow(Math.pow(R, 6) + Math.pow(Math.max(0, re + 0.1F), 6), 1 / 6.0);
   }

   private Vec3 clientHang, deathStartClient;

   /** Le centre du futur cocon (même recherche côté client) : la cinématique de mort filme de profil, le pilier à droite. */
   Vec3 hangPoint() {
      if (clientHang == null) {
         Vec3 keepH = hangAt, keepP = pillarC;
         pickPillar(this.level());
         clientHang = hangAt;
         if (!this.level().isClientSide) {
            hangAt = keepH == null ? hangAt : keepH;
            pillarC = keepP == null ? pillarC : keepP;
         }
      }
      return clientHang;
   }

   /** Le pilier du temple le plus proche encore debout (fût rond) : son axe, et la hauteur du centre du cocon (mi-hauteur). */
   void pickPillar(Level level) {
      Vec3 c = this.isDeadOrDying() && deathFrom != null ? deathFrom : this.position();
      int y0 = Mth.floor(c.y);
      double best = 1e9;
      net.minecraft.world.level.block.Block brick = fr.heralisz.mere.dungeon.ModBlocks.RUIN_BRICKS.get();
      net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
      Vec3 found = null;
      for (int dx = -26; dx <= 26; dx++) {
         for (int dz = -26; dz <= 26; dz++) {
            double d = dx * dx + dz * dz;
            if (d < 49 || d > 26 * 26 || d >= best) continue;
            m.set(Mth.floor(c.x) + dx, y0 + 8, Mth.floor(c.z) + dz);
            if (!level.getBlockState(m).is(brick) || !level.getBlockState(m.above(6)).is(brick) || !level.getBlockState(m.below(4)).is(brick)) continue;
            // centre du fût : moyenne des briques de la tranche
            double sx = 0, sz = 0;
            int n = 0;
            for (int ax = -4; ax <= 4; ax++) {
               for (int az = -4; az <= 4; az++) {
                  net.minecraft.core.BlockPos q = new net.minecraft.core.BlockPos(m.getX() + ax, m.getY(), m.getZ() + az);
                  if (level.getBlockState(q).is(brick)) {
                     sx += q.getX() + 0.5;
                     sz += q.getZ() + 0.5;
                     n++;
                  }
               }
            }
            if (n < 12) continue;                                              // un fût de 5 blocs, pas un moellon
            Vec3 ctr = new Vec3(sx / n, y0 + 10.5, sz / n);
            double dd = (ctr.x - c.x) * (ctr.x - c.x) + (ctr.z - c.z) * (ctr.z - c.z);
            if (dd < best) {
               best = dd;
               found = ctr;
            }
         }
      }
      pillarC = found;
      if (found != null) {
         Vec3 dir = new Vec3(c.x - found.x, 0, c.z - found.z).normalize();
         hangAt = found.add(dir.scale(PILLAR_R + BODY_OUT));
      } else {
         hangAt = c.add(0, 7.5, 0);
      }
   }

   private float deathCamSide;

   /**
    * Côté de la caméra pendant l'arrivée des rejetons (-1 : à gauche de l'axe de profil, +1 : à droite), choisi une fois au début
    * de la mort (côté client) : celui d'où l'on voit la Mère et le fond de la salle sans un fût en travers.
    */
   private float deathSide(Vec3 st, Vec3 h) {
      if (deathCamSide != 0) return deathCamSide;
      Vec3 d = new Vec3(h.x - st.x, 0, h.z - st.z);
      float yaw = d.lengthSqr() > 1 ? (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG) - 90F : this.yBodyRot;
      Vec3 f = Vec3.directionFromRotation(0, yaw);
      Vec3 right = new Vec3(-f.z, 0, f.x);
      float best = -1;
      int bestBlocked = Integer.MAX_VALUE;
      for (float sg : new float[] {-1, 1}) {
         Vec3 cam = st.add(f.scale(7)).add(right.scale(6 * sg)).add(0, 4.5, 0);
         int blocked = 0;
         for (Vec3 tgt : new Vec3[] {st.add(0, 1.2, 0), st.add(0, 2.6, 0), st.add(f.scale(-7)).add(0, 3, 0), st.add(f.scale(-9)).add(right.scale(3 * sg)).add(0, 3.5, 0),
            st.add(f.scale(-6)).add(right.scale(-3 * sg)).add(0, 2, 0)}) {
            if (this.level().clip(new ClipContext(cam, tgt, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) blocked++;
         }
         net.minecraft.core.BlockPos cp = net.minecraft.core.BlockPos.containing(cam);
         if (!this.level().getBlockState(cp).getCollisionShape(this.level(), cp).isEmpty()) blocked += 10;
         if (blocked < bestBlocked) {
            bestBlocked = blocked;
            best = sg;
         }
      }
      return deathCamSide = best;
   }

   // ------------------------------------------------------------------ cinématiques
   private static final float[][][] CINE = {
      // ÉCLOSION (18 s) : le cocon qui bat, des rejetons accrochés (contre-plongée) ; gros plan, des pattes percent la soie ; la
      // déchirure ; on la suit au bout de son fil ; suspendue face à nous, crochets qui s'ouvrent ; la chute (large, bas) ; la
      // menace en contre-plongée, pattes levées (titre) ; elle les abat
      {{0, 16, 7, 2, 12F}, {0.15F, 11, 4, 6, 13.5F}, {0.27F, 8, 2.5F, 10, 14F}, {0.36F, 8, 3, 10, 12.5F}, {0.47F, 8, 3.5F, 8, 10F},
         {0.6F, 7, 4, 5.5F, 6.5F}, {0.64F, 5.4F, 1.2F, 5.2F, 5.6F}, {0.685F, 5.8F, 1.6F, 4.8F, 5.2F}, {0.72F, 9, 5, 1.2F, 1.6F},
         {0.77F, 7.5F, 3.5F, 1F, 2.2F}, {0.8F, 5.8F, 1.2F, 0.8F, 3.2F}, {0.9F, 6.2F, 2, 1F, 3F}, {0.93F, 7, 3.5F, 1.5F, 2.2F}, {1, 8, 4, 2, 2.2F}},
      // PHASE 2 (8 s) : elle recule (de trois quarts) ; plan bas qui suit son regard vers les grappes de la voûte ; gros plan
      // sur le sac qui enfle, des mains poussent de l'intérieur ; il crève (large) ; contre-plongée sur le cri
      {{0, 9, 5, 2, 2F}, {0.12F, 8, 6, 2.4F, 2.4F}, {0.14F, 4, 2.5F, 0.6F, 9F, 1}, {0.3F, 3.8F, 2.2F, 0.7F, 12F},
         {0.33F, -3.5F, 3.5F, 2.5F, 2F, 1}, {0.6F, -3, 3, 2.3F, 2.2F}, {0.62F, 10, 7, 3, 2F, 1}, {0.66F, 11, 8, 3.2F, 2.2F},
         {0.68F, 5, -3, 0.6F, 3F, 1}, {1, 6.5F, -4.5F, 0.8F, 3.2F}},
      // PHASE 3 (8,5 s) : elle s'écrase (plongée) ; gros plan sur la femme pendue qui se débat, puis se fige et relève la
      // tête ; les deux hurlent ensemble (contre-plongée) ; recul large, la voûte répond
      {{0, 7, -5, 7, 0.5F}, {0.18F, 6, -6, 7.5F, 0.5F}, {0.2F, -2.5F, -2.5F, 1.6F, 1.4F, 1}, {0.56F, -2.2F, -2, 1.5F, 1.5F},
         {0.58F, 4, -3, 0.5F, 2.4F, 1}, {0.78F, 4.5F, -3.5F, 0.6F, 2.6F}, {0.8F, 12, -8, 4, 5F, 1}, {1, 14, -9, 6, 6F}},
      // MORT (22 s) : elle bascule sur le dos ; on lève les yeux vers les grappes d'où descendent les rejetons ; de près, la ronde
      // qui tisse ; le cocon est hissé sous la voûte (on le suit en contre-plongée, jusqu'au rayon pâle)
      {{0, 9, 5, 2.5F, 1.5F}, {0.1F, 8, 4, 2F, 1.2F}, {0.14F, 11, 7, 1.5F, 6F, 1}, {0.24F, 10, 6, 2F, 5F}, {0.28F, 6.5F, -3, 2F, 1.6F, 1},
         {0.44F, 5.5F, -2, 2.2F, 1.8F}, {0.57F, 6, 2.5F, 2.6F, 2F}, {0.61F, 13, 4, 1.2F, 3F, 1}, {0.78F, 14, 5, 1.5F, 9F}, {0.92F, 15, 6, 2F, 12F},
         {1, 16, 6, 2F, 13F}},
      // ENTRÉE (15 s) — repère : la toile, devant = vers la rotonde. Derrière l'équipe, dans la chapelle, on fixe la toile qui
      // frémit ; elle se déchire ; on franchit l'arc avec eux en levant les yeux ; plan large : le cocon géant sous l'oculus ; de
      // l'arène, on les voit se retourner, la toile se retisse derrière eux ; les sacs d'œufs s'allument, le cocon bat
      {{0, -11, 1.5F, 2.4F, 2.5F, 0, 1, 0}, {0.15F, -10, 1.2F, 2.3F, 3F, 0, 1, 0}, {0.22F, -7, 0.8F, 2.5F, 3.5F, 0, 6, 0},
         {0.34F, -1, 0.5F, 2.8F, 7F, 0, 16, 0}, {0.4F, 5, -9, 3, 12F, 1, 28, 0}, {0.55F, 9, -12, 5, 13F, 0, 28, 0}, {0.66F, 20, 2, 3, 2.5F, 1, 3, 0},
         {0.75F, 19, 2.5F, 3.2F, 3F, 0, 2, 0}, {0.8F, 14, 10, 2.5F, 12F, 1, 28, 0}, {1, 18, 12, 2, 13F, 0, 28, 0}},
      // OFFRANDE (12 s) — repère : l'idole, devant = de la toile vers l'idole. Plan de trois quarts, haut, derrière l'idole (au-dessus
      // des joueurs) : l'œuf posé au premier plan, la toile au fond ; la petite araignée en descend, trottine jusqu'au socle ;
      // contrechamp serré depuis la toile quand elle saisit l'œuf ; retour au plan large : elle repart et remonte la toile
      {{0, 3.5F, 4.5F, 5.5F, 2F, 0, -5, 0}, {0.17F, 2.8F, 4.2F, 4.8F, 1.6F, 0, -5.5F, 0}, {0.36F, 2F, 3.4F, 3.4F, 1.5F, 0, -2, 0},
         {0.44F, -2.8F, 1.8F, 2.4F, 1.3F, 1, 0, 0}, {0.55F, -2.5F, 2.2F, 2.3F, 1.3F, 0, 0, 0},
         {0.58F, 3.5F, 4.5F, 5, 1.6F, 1, -4, 0}, {0.8F, 3.2F, 4.3F, 5.2F, 2F, 0, -7, 0}, {1, 3, 4, 6, 5F, 0, -7.8F, 0}}
   };

   @Override
   public float[][] cinematicKeys(int kind) {
      if (kind == DEATH_K && this.isDeadOrDying()) {
         // (20 s, repère : là où elle est morte ; de profil, le pilier à droite) elle meurt sur le dos (0-0,1) ; de biais et en
         // hauteur (elle n'est plus entre nous et le fond), on regarde la paroi d'en face où les rejetons apparaissent et descendent
         // (0,12-0,29, visée vers le fond : 7e valeur) ; ils arrivent devant elle (0,33) et se glissent dessous (0,38) ; on suit le convoi vers le pilier (0,44-0,52) ; trois grimpent (0,6) ; on la
         // hisse (0,72) ; le tissage, de près (0,82-0,93) ; le cocon fini
         Vec3 h = hangPoint();
         Vec3 st = deathStartClient != null ? deathStartClient : (deathStartClient = this.position());
         float dd = (float) Math.min(24, Math.hypot(h.x - st.x, h.z - st.z));
         float hy = (float) (h.y - st.y);
         float sg = deathSide(st, h);
         float[][] k = {{0.000F, 10, 2, 3, 1.5F, 0, 0, 0}, {0.042F, 8, 3, 2.2F, 1.2F, 0, 0, 0}, {0.087F, 7, 6 * sg, 4.5F, 2.5F, 0, -6, 0},
            {0.145F, 7, 6 * sg, 5, 3.5F, 0, -9, 0}, {0.225F, 7.5F, 5 * sg, 4.5F, 2F, 0, -6, 0}, {0.282F, 9, 3 * sg, 3.5F, 1.2F, 0, -2, 0},
            {0.311F, 9, 1.5F, 2.4F, 1F, 0, 0, 0}, {0.335F, 8.5F, 2, 2.6F, 1.4F, 0, 0, 0}, {0.363F, 9, dd * 0.35F, 3, 1.6F, 0, 0, dd * 0.25F},
            {0.402F, 8, dd * 0.8F, 3, 2F, 0, 0, dd * 0.9F}, {0.554F, 7.5F, dd * 0.85F, 3.5F, hy * 0.55F, 0, 0, dd}, {0.665F, 8, dd * 0.85F, hy * 0.55F, hy * 0.85F, 0, 0, dd},
            {0.780F, 8.5F, dd * 0.85F, hy * 0.8F, hy, 0, 0, dd}, {0.908F, 8, dd * 0.95F, hy, hy, 0, 0, dd}, {1.000F, 12, dd * 0.8F, hy * 0.8F, hy * 0.95F, 0, 0, dd}};
         // les clés ci-dessus couvrent le tissage (DEATH_TICKS - 10) ; on les tasse, puis on recule lentement : le cocon achevé,
         // pendu au fût, dans la lumière pâle de l'oculus
         float sc = (DEATH_TICKS - 10) / (float) DEATH_CINE;
         float[][] out = new float[k.length + 2][];
         for (int i = 0; i < k.length; i++) {
            out[i] = k[i].clone();
            out[i][0] *= sc;
         }
         out[k.length] = new float[] {sc + (1 - sc) * 0.35F, 17, dd * 0.7F, hy * 0.85F, hy * 0.9F, 0, 0, dd};
         out[k.length + 1] = new float[] {1, 22, dd * 0.55F, hy * 0.95F, hy * 0.85F, 0, 0, dd};
         return out;
      }
      return CINE[Mth.clamp(kind, 0, CINE.length - 1)];
   }

   @Override
   public String[] cinematicTitles(int kind) {
      return switch (kind) {
         case PHASE2_K -> new String[] {"title.heralisz_mere.p2.top", "title.heralisz_mere.p2.name", "title.heralisz_mere.p2.sub"};
         case PHASE3_K -> new String[] {"title.heralisz_mere.p3.top", "title.heralisz_mere.p3.name", "title.heralisz_mere.p3.sub"};
         case DEATH_K -> new String[] {"title.heralisz_mere.death.top", "title.heralisz_mere.death.name", "title.heralisz_mere.death.sub"};
         case ENTRY_K -> new String[] {"title.heralisz_mere.entry.top", "title.heralisz_mere.entry.name", "title.heralisz_mere.entry.sub"};
         default -> new String[] {"title.heralisz_mere.spawn.top", "title.heralisz_mere.spawn.name", "title.heralisz_mere.spawn.sub"};
      };
   }

   @Override
   public float cinematicTitleAt(int kind) {
      return kind == SPAWN_K ? 0.8F : kind == DEATH_K ? 0.84F : kind == ENTRY_K ? 0.06F : kind == OFFER_K ? 2F : 0.64F;
   }

   @Override
   public boolean cinematicFollows(int kind) {
      return kind == PHASE2_K || kind == PHASE3_K;
   }

   @Override
   public int accentColor() {
      return phase() == 3 ? 0xFFE8B0 : 0xE8D8A8;
   }

   public int hudTop(int ph) {
      return ph == 3 ? 0xFF6A5A : ph == 2 ? 0xE0504A : 0xC23A3A;
   }

   public int hudBottom(int ph) {
      return ph == 3 ? 0x5A0A10 : 0x4A0A0E;
   }

   // ------------------------------------------------------------------ GeckoLib
   private static final String A = "animation.ruin_mother.";

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      reg.add(new AnimationController<>(this, "move", 5, s -> {
         if (this.isDeadOrDying() || dormant()) return PlayState.STOP;
         if (!s.isMoving()) return s.setAndContinue(RawAnimation.begin().thenLoop(A + "idle"));
         return s.setAndContinue(RawAnimation.begin().thenLoop(A + (phase() == 3 ? "run" : "walk")));
      }));
      reg.add(new AnimationController<>(this, "action", 3, s -> {
         if (this.isDeadOrDying()) {
            // déjà retournée : on reste sur le dos (jamais on ne rejoue la chute)
            if (Math.max(this.deathTime, this.entityData.get(DEATH_T)) > 92) return s.setAndContinue(RawAnimation.begin().thenLoop(A + "dead"));
            return s.setAndContinue(RawAnimation.begin().thenPlay(A + "death").thenLoop(A + "dead"));
         }
         if (dormant()) {
            clientLastActionTick = -1;
            return PlayState.STOP;
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
         if (a == Action.HOP) return s.setAndContinue(RawAnimation.begin().thenLoop(A + "hop"));          // un bond toutes les 2,7 s (HOP_JUMP + HOP_HOLD)
         return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(A + a.name().toLowerCase(Locale.ROOT)));
      }));
      reg.add(new AnimationController<>(this, "flinch", 1, s -> {
         if (this.hurtTime > 0 && action() == Action.NONE && !this.isDeadOrDying() && !dormant())
            return s.setAndContinue(RawAnimation.begin().thenPlay(A + "hurt"));
         s.getController().forceAnimationReset();
         return PlayState.STOP;
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
