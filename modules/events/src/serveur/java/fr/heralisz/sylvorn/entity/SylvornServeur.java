package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.SylvornConfig;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.entity.Ground;
import fr.heralisz.sylvorn.particle.ModParticles;
import fr.heralisz.sylvorn.sound.Sounds;
import fr.heralisz.sylvorn.fvfx.Vfx;
import fr.heralisz.sylvorn.dungeon.TreeHallBEServeur;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Partie SERVEUR de {@link Sylvorn} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new Sylvorn(...) » côté serveur.
 */
public class SylvornServeur extends Sylvorn {
   public SylvornServeur(EntityType<? extends Sylvorn> type, Level level) {
      super(type, level);
   }

   private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.heralisz_sylvorn.sylvorn"),
      BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
   private final float[] dpsWindow = new float[20];
   private final int[] cooldowns = new int[Action.values().length];
   private final Map<UUID, Float> threat = new HashMap<>();
   private final Set<UUID> hitOnce = new HashSet<>();
   private final List<Vec3> marks = new ArrayList<>();
   private final List<Vec3> dirs = new ArrayList<>();
   private final List<SapNode> nodes = new ArrayList<>();
   private BlockPos home;
   private Vec3 attackDir = Vec3.ZERO, anchor;
   private int globalCd = 30, lonelyTicks, grabTick = -1;
   private float waveDist;
   private Player grabbed;
   private boolean fromSave, landed;
   private final List<Player> marked = new ArrayList<>();
   private final List<Vec3> markLocks = new ArrayList<>();
   private final LivingEntity[] thornTargets = new LivingEntity[2];
   private final Vec3[] thornAim = new Vec3[2];
   private Vec3 chargeDir = Vec3.ZERO, lastFurrow, leapFrom, leapTo;
   /** Plus grand nombre de joueurs engagés récemment (les PV montent au-delà de groupBase joueurs). */
   private int peakPlayers = 1, peakHold;
   public boolean devPassive;

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
         e -> !(e instanceof Player p && (p.isCreative() || p.isSpectator()))));
   }

   private void start(Action a) {
      this.entityData.set(ACTION, a.ordinal());
      this.entityData.set(ACTION_TICK, (int) this.level().getGameTime() + 1);
      this.hitOnce.clear();
      this.grabbed = null;
      this.grabTick = -1;
      this.getNavigation().stop();
   }

   public boolean devAction(String name) {
      try {
         Action a = Action.valueOf(name);
         Player p = this.level().getNearestPlayer(this, 64);
         if (p != null) this.setTarget(p);
         if (a == Action.CHANNEL) cooldowns[a.ordinal()] = 0;
         start(a);
         return true;
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   public void devPhase(int n) {
      this.entityData.set(PHASE, n);
      this.setHealth(this.getMaxHealth() * (n == 1 ? 1F : n == 2 ? 0.6F : 0.3F));
      this.entityData.set(ACTION, 0);
   }

   public void setDevPassive(boolean on) {
      devPassive = on;
   }

   public boolean devTransitioning() {
      return action() == Action.PHASE2 || action() == Action.PHASE3;
   }

   // ------------------------------------------------------------------ l'Arbre-Monde (la salle)
   private BlockPos hallPos;
   private float homeYaw = 180F;

   public void setHall(BlockPos p) {
      hallPos = p;
   }

   public void setHome(BlockPos p, float yaw) {
      home = p;
      homeYaw = yaw;
   }

   public float homeYaw() {
      return homeYaw;
   }

   public TreeHallBEServeur hall() {
      if (hallPos != null && this.level().getBlockEntity(hallPos) instanceof TreeHallBEServeur h) return h;
      TreeHallBEServeur h = TreeHallBEServeur.nearestServeur(this.level(), this.blockPosition(), 140);
      if (h != null) hallPos = h.getBlockPos();
      return h;
   }

   public void setDormant(boolean d) {
      this.entityData.set(DORMANT, d);
      this.setInvisible(d);
      this.setNoAi(false);
   }

   /** Il retourne dans l'écorce (combat interrompu, remise à zéro) : soigné, invisible, immobile. */
   public void sleep() {
      this.setTarget(null);
      this.entityData.set(ACTION, 0);
      this.entityData.set(PHASE, 1);
      this.setHealth(this.getMaxHealth());
      releaseGrab();
      if (this.level() instanceof ServerLevel sl) clearNodes(sl);
      bossBar.removeAllPlayers();
      if (home != null) this.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, homeYaw, 0);
      this.yBodyRot = homeYaw;
      this.yHeadRot = homeYaw;
      setDormant(true);
   }

   /** Le rituel est fini : il s'arrache de l'écorce. */
   public void wake() {
      setDormant(false);
      this.setInvisible(true);                                                     // (encore dans l'écorce : visible quand elle se fend)
      start(Action.SPAWN);
   }

   /** Joueurs engagés (pour la salle). */
   public List<Player> fighters(double r) {
      return targets(r);
   }

   // ------------------------------------------------------------------ cycle de vie
   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide && this.tickCount == 0 && !fromSave && home == null) home = this.blockPosition();
   }

   private BlockState ground(Vec3 p) {
      BlockPos b = BlockPos.containing(p.x, surfaceY(p) - 1, p.z);
      BlockState s = this.level().getBlockState(b);
      return s.isAir() ? Blocks.DIRT.defaultBlockState() : s;
   }

   private double surfaceY(Vec3 p) {
      return Ground.y(this.level(), p.x, p.y, p.z);
   }

   private Vec3 surf(Vec3 p) {
      return new Vec3(p.x, surfaceY(p), p.z);
   }

   private Vec3 fwd() {
      return Vec3.directionFromRotation(0, this.yBodyRot);
   }

   private Vec3 right() {
      Vec3 f = fwd();
      return new Vec3(-f.z, 0, f.x);
   }

   /** Crâne de cerf (modèle v3 : tête basse, projetée vers l'avant). */
   private Vec3 headPos() {
      return this.position().add(fwd().scale(2.45)).add(0, 8.5, 0);
   }

   /** Ramène un point dans sa zone (sinon la laisse le renverrait chez lui, soigné) : bonds, charges et traques restent dedans. */
   private Vec3 inLeash(Vec3 p) {
      if (home == null) return p;
      double r = SylvornConfig.LEASH_RADIUS.get() - 4;
      double hx = home.getX() + 0.5, hz = home.getZ() + 0.5, dx = p.x - hx, dz = p.z - hz, d = Math.sqrt(dx * dx + dz * dz);
      if (d <= r || d < 1e-3) return p;
      return surf(new Vec3(hx + dx / d * r, p.y, hz + dz / d * r));
   }

   private boolean outsideLeash(Vec3 p, double margin) {
      if (home == null) return false;
      double dx = p.x - (home.getX() + 0.5), dz = p.z - (home.getZ() + 0.5);
      return Math.sqrt(dx * dx + dz * dz) > SylvornConfig.LEASH_RADIUS.get() - margin;
   }

   /** Main droite plantée devant lui (côté du tronc tenu dans hand_r). */
   private Vec3 handPos() {
      return this.position().add(fwd().scale(3.0)).add(right().scale(1.7));
   }

   // ------------------------------------------------------------------ IA
   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      ServerLevel level = (ServerLevel) this.level();
      if (dormant()) {
         // endormi dans l'écorce : rien ne bouge
         this.getNavigation().stop();
         this.setDeltaMovement(0, 0, 0);
         if (!bossBar.getPlayers().isEmpty()) bossBar.removeAllPlayers();
         if (home != null && this.tickCount % 20 == 0) this.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, homeYaw, 0);
         return;
      }
      for (int i = 0; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
      if (globalCd > 0) globalCd--;
      dpsWindow[this.tickCount % 20] = 0;
      bossBar.setProgress(this.getHealth() / this.getMaxHealth());
      if (this.tickCount % 20 == 0) {
         updateBossBar(level);
         leash(level);
         updateGroup();
         this.entityData.set(VIRTUAL_MAX, (int) (SylvornConfig.VIRTUAL_HP.get() * groupMult()));
         pickTarget();
      }
      ambient(level);
      LivingEntity target = this.getTarget();
      if (target != null && (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
         this.setTarget(null);
         target = null;
      }
      Action a = action();
      if (a != Action.NONE) {
         int t = actionTime();
         runAction(level, a, t, target);
         if (action() == a && t >= a.ticks) {
            if (a == Action.CHANNEL) clearNodes(level);
            releaseGrab();
            // la disparition s'enchaîne : rôder invisible, puis surgir
            if (a == Action.VANISH) {
               start(Action.STALK);
               return;
            }
            if (a == Action.STALK) {
               start(Action.AMBUSH);
               return;
            }
            this.setInvisible(false);
            this.entityData.set(ACTION, 0);
            globalCd = phase() == 3 ? 5 : phase() == 2 ? 9 : 14;
         }
         return;
      }
      float hp = this.getHealth() / this.getMaxHealth();
      if (phase() == 1 && hp <= 0.66F) {
         start(Action.PHASE2);
         return;
      }
      if (phase() == 2 && hp <= 0.33F) {
         start(Action.PHASE3);
         return;
      }
      if (devPassive || target == null) {
         this.getNavigation().stop();
         return;
      }
      this.getLookControl().setLookAt(target, 30F, 30F);
      turnToward(target, 8F);
      double d = this.distanceTo(target);
      if (globalCd == 0) {
         Action next = choose(target, d);
         if (next != null) {
            cooldowns[next.ordinal()] = cooldownOf(next);
            faceTarget(target);
            start(next);
            return;
         }
      }
      if (d > 7) {
         // phase 3 : il chasse à quatre pattes, bien plus vite
         double speed = phase() == 3 ? (d > 10 ? 1.45 : 1.15) : phase() == 2 ? 1.08 : 1.0;
         if (this.tickCount % 6 == 0 || this.getNavigation().isDone()) this.getNavigation().moveTo(target, speed);
      } else {
         this.getNavigation().stop();
      }
   }

   private Action choose(LivingEntity target, double d) {
      int ph = phase();
      List<Action> o = new ArrayList<>();
      List<Float> w = new ArrayList<>();
      BiConsumer<Action, Float> add = (act, weight) -> {
         if (cooldowns[act.ordinal()] == 0 && weight > 0) {
            o.add(act);
            w.add(weight);
         }
      };
      // un tireur planqué derrière un mur ou un pilier : il le déloge (tronc lancé en cloche, graines, charge)
      if (target != null && !this.hasLineOfSight(target) && d > 6) {
         add.accept(Action.LOG, 4F);
         add.accept(Action.SEEDS, 2.5F);
         add.accept(Action.CHARGE, 2.5F);
      }
      if (d > 3 && d < 11) add.accept(Action.GORE, ph >= 2 ? 2.2F : 1.4F);
      if (d < 8.5) {
         add.accept(Action.SWEEP, 3F);
         add.accept(Action.STOMP, 2F);
         add.accept(Action.GRASP, 1.2F);
         if (ph >= 2) add.accept(Action.HOWL, 1.6F);
      } else if (d < 24) {
         add.accept(Action.GRASP, 2.6F);
         add.accept(Action.LOG, 1.8F);
         add.accept(Action.SEEDS, 1.3F);
         add.accept(Action.ROOTFIELD, 1.5F);
         add.accept(Action.CHARGE, 2F);
         add.accept(Action.THORNS, 1.6F);
         if (ph >= 2 && d < 16) add.accept(Action.HOWL, 1.2F);
      } else {
         add.accept(Action.LOG, 1.8F);
         add.accept(Action.SEEDS, 1.6F);
         add.accept(Action.THORNS, 2.6F);
         add.accept(Action.CHARGE, 1F);
      }
      // des tireurs restent loin : il les punit (javelots, marque, bond)
      Player far = farthest(56);
      if (far != null && far.distanceTo(this) > 24) {
         add.accept(Action.THORNS, 2F);
         if (ph >= 2) add.accept(Action.MARK, 3F);
         if (ph == 3) add.accept(Action.POUNCE, 3F);
      } else {
         if (ph >= 2 && d > 14) add.accept(Action.MARK, 1.2F);
         if (ph == 3 && d > 12) add.accept(Action.POUNCE, 2F);
      }
      if (ph >= 2) add.accept(Action.VANISH, 1F);
      if (ph >= 2 && this.getHealth() < this.getMaxHealth() * (ph == 2 ? 0.58F : 0.3F)) add.accept(Action.CHANNEL, 2.5F);
      if (ph == 3) {
         add.accept(Action.TREEFALL, 2.2F);
         add.accept(Action.LEAFSTORM, 1.6F);
      }
      if (o.isEmpty()) return null;
      float tot = 0;
      for (float f : w) tot += f;
      float r = random.nextFloat() * tot;
      for (int i = 0; i < o.size(); i++) {
         r -= w.get(i);
         if (r <= 0) return o.get(i);
      }
      return o.get(o.size() - 1);
   }

   private int cooldownOf(Action a) {
      boolean fast = phase() == 3;
      return switch (a) {
         case SWEEP -> 30;
         case GORE -> fast ? 110 : 160;
         case STOMP -> fast ? 120 : 160;
         case GRASP -> fast ? 160 : 220;
         case LOG -> fast ? 120 : 170;
         case SEEDS -> 200;
         case ROOTFIELD -> 220;
         case CHANNEL -> 900;
         case TREEFALL -> 260;
         case LEAFSTORM -> 360;
         case HOWL -> 320;
         case THORNS -> fast ? 130 : 190;
         case MARK -> 340;
         case CHARGE -> fast ? 170 : 230;
         case VANISH -> 560;
         case POUNCE -> 240;
         default -> 0;
      };
   }

   private void pickTarget() {
      threat.replaceAll((k, v) -> v * 0.8F);
      Player best = null;
      float bs = -1;
      for (Player p : targets(48)) {
         float sc = threat.getOrDefault(p.getUUID(), 0F) + (float) (48 - p.distanceTo(this)) * 0.5F;
         if (sc > bs) {
            bs = sc;
            best = p;
         }
      }
      if (best != null) this.setTarget(best);
   }

   /** Le joueur le plus éloigné (au-delà de 14 blocs) : le tireur embusqué. */
   private Player farthest(double r) {
      Player best = null;
      double bd = 14;
      for (Player p : targets(r)) {
         double d = p.distanceTo(this);
         if (d > bd) {
            bd = d;
            best = p;
         }
      }
      return best;
   }

   /** Joueurs engagés (retenus 30 s) : au-delà de groupBase, chaque joueur de plus ajoute groupStep aux PV. */
   private void updateGroup() {
      int n = Math.max(1, targets(56).size());
      if (n >= peakPlayers) {
         peakPlayers = n;
         peakHold = 30;
      } else if (--peakHold <= 0) {
         peakPlayers--;
         peakHold = 30;
      }
   }

   private float groupMult() {
      int extra = Math.max(0, peakPlayers - SylvornConfig.GROUP_BASE.get());
      return 1F + extra * SylvornConfig.GROUP_STEP.get().floatValue();
   }

   private List<Player> targets(double r) {
      return this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(r),
         p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) <= r);
   }

   private void faceTarget(LivingEntity t) {
      float yaw = (float) (Mth.atan2(t.getZ() - getZ(), t.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
   }

   private void turnToward(LivingEntity t, float step) {
      float want = (float) (Mth.atan2(t.getZ() - getZ(), t.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
      this.yBodyRot = Mth.approachDegrees(this.yBodyRot, want, step);
      this.setYRot(this.yBodyRot);
   }

   private float dmg(float base) {
      return base * SylvornConfig.DAMAGE_MULT.get().floatValue() * (phase() == 3 ? 1.25F : 1F);
   }

   private void sound(SoundEvent s, float vol, float pitch) {
      this.level().playSound(null, getX(), getY() + 3, getZ(), s, SoundSource.HOSTILE, vol, pitch);
   }

   private void ambient(ServerLevel level) {
      if (this.tickCount % 3 == 0) {
         level.sendParticles(ModParticles.SPORE.get(), getX(), getY() + 4, getZ(), 1, 2.5, 3, 2.5, 0.01);
      }
      if (phase() == 3 && this.tickCount % 2 == 0) {
         level.sendParticles(ModParticles.LEAF_AUTUMN.get(), getX(), getY() + 7, getZ(), 1, 2.5, 1.5, 2.5, 0.02);
      }
      // les os pendus à ses bois s'entrechoquent
      if (this.tickCount % 140 == 0 && this.random.nextBoolean()) sound(Sounds.SY_CHIMES.get(), 1.6F, 0.9F + this.random.nextFloat() * 0.2F);
   }

   // ------------------------------------------------------------------ actions
   private void runAction(ServerLevel level, Action a, int t, LivingEntity target) {
      switch (a) {
         case SPAWN -> spawnSeq(level, t);
         case PHASE2 -> phaseSeq(level, t, 2);
         case PHASE3 -> phaseSeq(level, t, 3);
         case SWEEP -> sweep(level, t, target);
         case GORE -> gore(level, t, target);
         case STOMP -> stomp(level, t);
         case GRASP -> grasp(level, t, target);
         case LOG -> log(level, t, target);
         case SEEDS -> seeds(level, t, target);
         case ROOTFIELD -> rootfield(level, t);
         case CHANNEL -> channel(level, t);
         // Sonné : quand ses nœuds de sève sont tous détruits (CHANNEL) ou que ses bois se plantent dans un mur (CHARGE) ; il encaisse 50 % de plus.
         case STUN -> {
            if (t % 4 == 0) level.sendParticles(ModParticles.LEAF.get(), getX(), getY() + 6, getZ(), 6, 1.5, 1, 1.5, 0.05);
         }
         case TREEFALL -> treefall(level, t);
         case LEAFSTORM -> leafstorm(level, t);
         case HOWL -> howl(level, t, target);
         case THORNS -> thorns(level, t, target);
         case MARK -> mark(level, t, target);
         case CHARGE -> charge(level, t, target);
         case VANISH -> vanish(level, t);
         case STALK -> stalk(level, t, target);
         case AMBUSH -> ambush(level, t, target);
         case POUNCE -> pounce(level, t, target);
         default -> {
         }
      }
   }

   private Vec3 deathFrom;
   private Vec3 spawnFrom, spawnTo;

   private void faceDir(Vec3 d) {
      if (d.lengthSqr() < 1e-6) return;
      float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
   }

   private void turnTo(Vec3 d, float step) {
      if (d.lengthSqr() < 1e-6) return;
      float want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
      this.yBodyRot = Mth.approachDegrees(this.yBodyRot, want, step);
      this.setYRot(this.yBodyRot);
      this.yHeadRot = this.yBodyRot;
   }

   /** Le cœur de l'Arbre (tout là-haut), ou à défaut un point haut au-dessus de lui. */
   public Vec3 heartPos() {
      TreeHallBEServeur h = hall();
      return h != null ? h.heartPos() : this.position().add(0, 60, 0);
   }

   /**
    * Réveil (15 s, animation « spawn_bark ») : pris dans l'écorce de la paroi, bras écartés, tête renversée. Le cœur, là-haut,
    * s'emballe ; l'écorce se fend (3 s) ; le bras droit s'arrache (5 s) et l'écorce éclate ; le gauche (6,5 s) ; la tête et les
    * bois (8 s) ; il bascule hors de la niche et s'écrase à quatre pattes (9 s) ; il reprend son souffle, se relève, fait craquer
    * sa nuque, et rugit (13,2 s).
    */
   private void spawnSeq(ServerLevel level, int t) {
      TreeHallBEServeur h = hall();
      if (t == 1 || spawnFrom == null || spawnTo == null) {                   // (aussi si le premier tick a été manqué : boss hors distance de simulation)
         if (home == null) home = this.blockPosition();
         spawnFrom = new Vec3(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
         Vec3 f = Vec3.directionFromRotation(0, homeYaw);
         spawnTo = surf(spawnFrom.add(f.scale(5.5)).add(0, 1, 0));
         this.setPos(spawnFrom.x, spawnFrom.y, spawnFrom.z);
         faceDir(f);
         this.noPhysics = true;
         this.setNoGravity(true);
         if (SylvornConfig.CINEMATICS.get())
            fr.heralisz.sylvorn.vfx.Vfx.cinematic(level, spawnFrom, this.getId(), SPAWN_K, Action.SPAWN.ticks, f);
         if (h != null) h.heartStage(level, 2);
      }
      this.getNavigation().stop();
      Vec3 heart = heartPos();
      Vec3 chest = spawnFrom.add(0, 6, 0);
      // le cœur s'emballe, jusqu'à ce que l'écorce cède
      int period = t < SP_CRACK ? 20 : t < SP_BURST ? 12 : t < SP_OUT1 ? 9 : 16;
      if (t < SP_ROAR && t % period == 0)
         level.playSound(null, heart.x, heart.y, heart.z, Sounds.FX_HEARTBEAT.get(), SoundSource.HOSTILE, 6F, 0.6F + Math.min(t, 180) / 360F);
      if (t % 4 == 0 && t < SP_BURST) {
         // la sève descend le long des veines du tronc jusqu'à lui
         double a = random.nextDouble() * Mth.TWO_PI;
         level.sendParticles(ModParticles.SPORE.get(), chest.x + Math.cos(a) * 3, chest.y + random.nextDouble() * 6, chest.z + Math.sin(a) * 1.5, 1, 0.2, 0.2, 0.2, 0.01);
      }
      if (t == 30) level.playSound(null, chest.x, chest.y, chest.z, Sounds.SY_BREATH.get(), SoundSource.HOSTILE, 3F, 0.55F);
      if (t == SP_CRACK) {
         if (h != null) h.barkStage(level, 1);
         level.playSound(null, chest.x, chest.y, chest.z, Sounds.FX_WOOD.get(), SoundSource.HOSTILE, 4F, 0.5F);
         level.playSound(null, chest.x, chest.y, chest.z, Sounds.DG_CREAK.get(), SoundSource.HOSTILE, 4F, 0.6F);
         Vfx.shake(level, chest, 0.5F, 40, 12);
         level.sendParticles(ModParticles.WOOD_CHIP.get(), chest.x, chest.y, chest.z, 20, 2.5, 3, 0.6, 0.08);
      }
      if (t > SP_CRACK && t < SP_BURST && t % 9 == 0) {
         level.playSound(null, chest.x, chest.y, chest.z, Sounds.DG_CREAK.get(), SoundSource.HOSTILE, 2.5F, 0.5F + random.nextFloat() * 0.3F);
         level.sendParticles(ModParticles.WOOD_CHIP.get(), chest.x, chest.y, chest.z, 4, 2.5, 3, 0.5, 0.05);
      }
      if (t == SP_BURST - 4) this.setInvisible(false);
      if (t == SP_ARM_R || t == SP_ARM_L) {
         // un bras s'arrache de l'écorce : éclats, sève, un cri
         boolean right = t == SP_ARM_R;
         Vec3 side = new Vec3(-fwd().z, 0, fwd().x).scale(right ? 1 : -1);
         Vec3 arm = chest.add(side.scale(3.2)).add(0, 1, 0);
         if (right && h != null) h.barkStage(level, 2);
         level.playSound(null, arm.x, arm.y, arm.z, Sounds.FX_WOOD.get(), SoundSource.HOSTILE, 5F, right ? 0.45F : 0.55F);
         level.playSound(null, arm.x, arm.y, arm.z, Sounds.FX_BONE.get(), SoundSource.HOSTILE, 3F, 0.6F);
         sound(Sounds.SY_HURT.get(), 4F, right ? 0.55F : 0.6F);
         Vfx.shake(level, arm, right ? 1.0F : 0.8F, 40, 14);
         level.sendParticles(ModParticles.WOOD_CHIP.get(), arm.x, arm.y, arm.z, right ? 70 : 40, 1.6, 2.2, 1.6, 0.3);
         level.sendParticles(ModParticles.SPORE.get(), arm.x, arm.y, arm.z, 30, 1.5, 2, 1.5, 0.1);
         level.sendParticles(ModParticles.LEAF.get(), arm.x, arm.y + 10, arm.z, right ? 80 : 30, 6, 4, 6, 0.06);
         if (right) Vfx.debris(level, arm, 2.2F, 7F, 26, net.minecraft.world.level.block.Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState(),
            fwd().scale(0.6));
      }
      if (t == SP_HEAD) {
         Vec3 hd = chest.add(0, 4, 0);
         level.playSound(null, hd.x, hd.y, hd.z, Sounds.FX_WOOD.get(), SoundSource.HOSTILE, 5F, 0.4F);
         sound(Sounds.SY_HOWL.get(), 4F, 0.6F);
         Vfx.shake(level, hd, 1.1F, 40, 14);
         level.sendParticles(ModParticles.WOOD_CHIP.get(), hd.x, hd.y, hd.z, 50, 2.5, 1.5, 1.5, 0.25);
         Vfx.debris(level, hd, 2.5F, 6F, 20, net.minecraft.world.level.block.Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState(), fwd().scale(0.5));
         if (h != null) h.barkStage(level, 3);
      }
      if (t >= SP_OUT0 && t <= SP_OUT1 && spawnFrom != null) {
         // il bascule hors de la niche, entraîné par son poids
         double u = (t - SP_OUT0) / (double) (SP_OUT1 - SP_OUT0);
         Vec3 p = spawnFrom.lerp(spawnTo, u * u);
         this.setPos(p.x, p.y, p.z);
         this.setDeltaMovement(Vec3.ZERO);
      }
      if (t == SP_LAND) {
         this.noPhysics = false;
         this.setNoGravity(false);
         Vec3 o = this.position();
         sound(Sounds.FX_EARTH.get(), 5F, 0.4F);
         sound(Sounds.SY_STEP.get(), 5F, 0.45F);
         Vfx.shake(level, o, 1.4F, 50, 18);
         Vfx.debris(level, o.add(fwd().scale(3)), 3F, 7F, 34, ground(o), Vec3.ZERO);
         level.sendParticles(ModParticles.DUST.get(), o.x, o.y + 0.3, o.z, 60, 3.5, 0.3, 3.5, 0.08);
         level.sendParticles(ModParticles.LEAF.get(), o.x, o.y + 16, o.z, 60, 8, 3, 8, 0.05);
      }
      if (t > SP_LAND && t < 225 && t % 22 == 0) sound(Sounds.SY_BREATH.get(), 3F, 0.5F + random.nextFloat() * 0.1F);
      if (t == 238) sound(Sounds.FX_BONE.get(), 3F, 0.6F);
      if (t == SP_ROAR) {
         sound(Sounds.SY_ROAR.get(), 6F, 0.9F);
         sound(Sounds.SY_ROAR.get(), 6F, 0.9F);
         Vfx.shake(level, this.position(), 1.3F, 50, 26);
         level.sendParticles(ModParticles.LEAF_AUTUMN.get(), getX(), getY() + 7, getZ(), 50, 4, 2, 4, 0.15);
         level.sendParticles(ModParticles.LEAF.get(), getX(), getY() + 22, getZ(), 160, 12, 5, 12, 0.08);
         if (h != null) h.heartStage(level, 1);
      }
   }

   private void phaseSeq(ServerLevel level, int t, int ph) {
      if (t == 1) {
         if (SylvornConfig.CINEMATICS.get())
            fr.heralisz.sylvorn.vfx.Vfx.cinematic(level, this.position(), this.getId(), ph == 2 ? PHASE2_K : PHASE3_K,
               (ph == 2 ? Action.PHASE2 : Action.PHASE3).ticks, fwd());
         sound(Sounds.SY_HURT.get(), 4F, 0.8F);
      }
      int at = ph == 2 ? 98 : 104;
      phaseBeats(level, t, ph);
      if (t == at) {
         this.entityData.set(PHASE, ph);
         TreeHallBEServeur hh = hall();
         if (hh != null) hh.heartStage(level, ph == 3 ? 2 : 1);
         earthBurst(level, this.position(), 16F);
         Vfx.flash(level, headPos(), 6F, 8, ph == 2 ? 0xE8F2D0 : 0xD8FFB0);
         Vfx.chips(level, headPos(), 1F, 5F, 18, ModParticles.BONE_CHIP.get());
         Vfx.shake(level, this.position(), 1.2F, 40, 24);
         level.sendParticles(ph == 2 ? ModParticles.PETAL.get() : ModParticles.LEAF_AUTUMN.get(), getX(), getY() + 5, getZ(), 120, 3, 3, 3, 0.25);
         sound(Sounds.SY_CHIMES.get(), 4F, ph == 2 ? 1F : 0.8F);
         sound(ph == 2 ? Sounds.FX_BONE.get() : Sounds.FX_WOOD.get(), 3F, 0.5F);
      }
      if (t == at + 12) {
         sound(Sounds.SY_ROAR.get(), 5F, ph == 2 ? 1.05F : 0.88F);
      }
      if (t > at && t % 3 == 0) {
         level.sendParticles(ph == 2 ? ModParticles.PETAL.get() : ModParticles.LEAF_AUTUMN.get(), getX(), getY() + 7, getZ(), 6, 3, 2, 3, 0.05);
      }
   }

   /** Temps forts des transitions : griffes plantées et anneau de racines (II) ; l'écorce du dos éclate (III). */
   private void phaseBeats(ServerLevel level, int t, int ph) {
      if (ph == 2) {
         if (t == 28) sound(Sounds.SY_CHIMES.get(), 3F, 0.6F);
         if (t >= 28 && t < 44 && t % 4 == 0)
            level.sendParticles(ModParticles.LEAF.get(), getX(), getY() + 18, getZ(), 12, 6, 3, 6, 0.04);
         if (t == 76) {
            sound(Sounds.FX_WOOD.get(), 4F, 0.5F);
            Vfx.shake(level, this.position(), 0.9F, 24, 22);
            for (int k = 0; k < 14; k++) {
               Vec3 p = surf(this.position().add(new Vec3(Math.cos(k * Math.PI / 7), 0, Math.sin(k * Math.PI / 7)).scale(7)));
               Vfx.debris(level, p, 0.8F, 5F, 4, ground(p), new Vec3(0, 0.5, 0));
               level.sendParticles(ModParticles.DUST.get(), p.x, p.y + 0.4, p.z, 4, 0.5, 0.3, 0.5, 0.03);
            }
         }
      } else {
         if (t > 14 && t < 60 && t % 10 == 0) sound(Sounds.FX_BONE.get(), 2F, 0.5F + 0.05F * (t / 10));
         if (t == 64) {
            sound(Sounds.FX_WOOD.get(), 4F, 0.6F);
            Vfx.shake(level, this.position(), 1F, 20, 22);
            Vec3 back = this.position().add(fwd().scale(-1.5)).add(0, 6, 0);
            Vfx.debris(level, back, 2F, 6F, 26, net.minecraft.world.level.block.Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState(),
               fwd().scale(-0.4).add(0, 0.4, 0));
            level.sendParticles(ModParticles.LEAF_AUTUMN.get(), back.x, back.y, back.z, 60, 2, 2, 2, 0.2);
         }
         if (t == 84) sound(Sounds.FX_BONE.get(), 3F, 0.4F);
      }
   }

   /** Balayage : cône annoncé (9,5 blocs, 160°), puis un revers qui projette loin tout ce qui est devant lui. */
   private void sweep(ServerLevel level, int t, LivingEntity target) {
      if (t < 10 && target != null) faceTarget(target);
      if (t == 3) Vfx.cone(level, this.position(), fwd(), 9.5F, 160F, 11);
      if (t == 8) sound(Sounds.SY_HOWL.get(), 2F, 0.6F);
      if (t == 14) {
         sound(Sounds.FX_SWIPE.get(), 2F, 0.4F);
         for (int k = -4; k <= 4; k++) {
            Vec3 d = fwd().yRot(k * 0.3F);
            Vec3 p = surf(this.position().add(d.scale(6)));
            Vfx.debris(level, p, 0.6F, 4F, 3, ground(p), d.scale(0.5));
            level.sendParticles(ModParticles.DUST.get(), p.x, p.y + 0.4, p.z, 3, 0.6, 0.2, 0.6, 0.02);
         }
         level.sendParticles(ModParticles.LEAF.get(), getX() + fwd().x * 5, getY() + 3, getZ() + fwd().z * 5, 30, 3, 1, 3, 0.2);
         for (Player p : targets(10)) {
            Vec3 to = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ()).normalize();
            if (fwd().dot(to) < Math.cos(Math.toRadians(80))) continue;
            p.hurt(this.damageSources().mobAttack(this), dmg(18));
            p.setDeltaMovement(to.x * 1.9, 0.6, to.z * 1.9);
            p.hurtMarked = true;
         }
      }
   }

   /**
    * Coup de bois : il baisse la tête (annonce en couloir), bondit de trois-quatre blocs, puis relève violemment ses bois :
    * la proie est projetée en l'air et en arrière.
    */
   private void gore(ServerLevel level, int t, LivingEntity target) {
      if (t < 7 && target != null) faceTarget(target);
      if (t == 1) {
         Vfx.line(level, surf(this.position()), fwd(), 7F, 3F, 11);
         sound(Sounds.SY_ROAR.get(), 1.6F, 1.3F);
      }
      if (t >= 7 && t <= 12) this.setDeltaMovement(fwd().scale(0.75).add(0, getDeltaMovement().y, 0));
      if (t == 14) {
         sound(Sounds.SY_HOWL.get(), 2.5F, 0.7F);
         level.sendParticles(ModParticles.LEAF.get(), getX() + fwd().x * 3, getY() + 3, getZ() + fwd().z * 3, 24, 1.5, 1, 1.5, 0.2);
         for (Player p : targets(7)) {
            Vec3 to = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            if (to.length() > 6.5 || fwd().dot(to.normalize()) < Math.cos(Math.toRadians(60))) continue;
            p.hurt(this.damageSources().mobAttack(this), dmg(20));
            Vec3 d = to.normalize();
            p.setDeltaMovement(d.x * 1.2, 1.25, d.z * 1.2);
            p.hurtMarked = true;
         }
      }
   }

   /** Coup de sabot : cercle annoncé, puis le sol explose sous son sabot : deux anneaux de racines ; dégâts, projection et ralentissement à moins de 8 blocs. */
   private void stomp(ServerLevel level, int t) {
      Vec3 foot = surf(this.position().add(fwd().scale(1.5)).add(right().scale(-1.5)));
      if (t == 5) Vfx.circle(level, foot, 8F, 13);
      if (t == 18) {
         sound(Sounds.FX_EXPLODE.get(), 2F, 0.5F);
         sound(Sounds.FX_EARTH.get(), 3F, 0.5F);
         earthBurst(level, foot, 13F);
         Vfx.debris(level, foot, 3F, 8F, 30, ground(foot), Vec3.ZERO);
         Vfx.shake(level, foot, 1.1F, 30, 16);
         for (int ring = 0; ring < 2; ring++) {
            double r = ring == 0 ? 4.5 : 7.5;
            for (int i = 0; i < 9; i++) {
               double ang = i / 9.0 * Mth.TWO_PI + ring * 0.35;
               earthBurst(level, surf(foot.add(Math.cos(ang) * r, 0, Math.sin(ang) * r)), (1.1F + ring * 0.2F) * 3F);
            }
         }
         for (Player p : level.getEntitiesOfClass(Player.class, new AABB(foot, foot).inflate(8, 3, 8))) {
            if (p.isCreative() || p.isSpectator() || p.distanceToSqr(foot) > 64) continue;
            p.hurt(this.damageSources().mobAttack(this), dmg(15));
            Vec3 push = p.position().subtract(foot).normalize();
            p.setDeltaMovement(push.x * 1.1, 0.9, push.z * 1.1);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, false));
         }
      }
   }

   /**
    * Étreinte des racines : la main plantée dans le sol lance une vague de racines vers la cible ; celle qu'elle atteint
    * est saisie par une racine géante, soulevée puis fracassée au sol.
    */
   private void grasp(ServerLevel level, int t, LivingEntity target) {
      if (t == 1) {
         Vec3 to = target != null ? target.position().subtract(this.position()) : fwd().scale(12);
         attackDir = new Vec3(to.x, 0, to.z).normalize();
         float len = (float) Math.min(24, to.horizontalDistance() + 4);
         Vfx.line(level, surf(this.position().add(attackDir.scale(3))), attackDir, len, 3.4F, 12);
         marks.clear();
         marks.add(new Vec3(len + 3, 0, 0));
         sound(Sounds.SY_ROAR.get(), 3F, 1.2F);
      }
      if (t < 12 && target != null) faceTarget(target);
      if (t == 12) {
         Vec3 h = surf(handPos());
         Vfx.debris(level, h, 1.5F, 6F, 16, ground(h), Vec3.ZERO);
         earthBurst(level, h, 4F);
         Vfx.shake(level, h, 0.8F, 30, 10);
         sound(Sounds.FX_EARTH.get(), 3F, 0.6F);
         sound(Sounds.SY_ROOTS.get(), 3.5F, 1F);
         waveDist = 3;
      }
      float maxLen = marks.isEmpty() ? 20 : (float) marks.get(0).x;
      if (t > 12 && grabbed == null && waveDist < maxLen && t < 44) {
         waveDist += 0.95F;
         Vec3 head = surf(this.position().add(attackDir.scale(waveDist)));
         if (t % 2 == 0) {
            earthBurst(level, head, (0.9F) * 3F);
            Vfx.debris(level, head, 0.8F, 4F, 3, ground(head), Vec3.ZERO);
            level.playSound(null, head.x, head.y, head.z, Sounds.FX_EARTH.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
         }
         for (Player p : level.getEntitiesOfClass(Player.class, new AABB(head, head).inflate(2.2, 2.5, 2.2))) {
            if (p.isCreative() || p.isSpectator()) continue;
            grabbed = p;
            grabTick = t;
            anchor = surf(p.position());
            Vfx.debris(level, anchor, 1.5F, 7F, 14, ground(anchor), Vec3.ZERO);
            sound(Sounds.DG_ROOTS.get(), 2F, 0.5F);
            p.hurt(this.damageSources().mobAttack(this), dmg(4));
            break;
         }
      }
      if (grabbed != null && grabTick >= 0) {
         if (!grabbed.isAlive() || grabbed.isSpectator()) {
            releaseGrab();
            return;
         }
         if (t < 48) {
            float k = Mth.clamp((t - grabTick) / (float) Math.max(1, 44 - grabTick), 0, 1);
            double h = 7.0 * (1 - (1 - k) * (1 - k));
            Vec3 at = anchor.add(Mth.sin(t * 0.6F) * 0.3, h, Mth.cos(t * 0.5F) * 0.3);
            grabbed.teleportTo(at.x, at.y, at.z);
            grabbed.setDeltaMovement(Vec3.ZERO);
            grabbed.fallDistance = 0;
            if (t % 8 == 0) grabbed.hurt(this.damageSources().mobAttack(this), dmg(2));
         } else if (t < 52) {
            double h = 7.0 * (1 - (t - 47) / 4.0);
            grabbed.teleportTo(anchor.x, anchor.y + Math.max(0.1, h), anchor.z);
            grabbed.setDeltaMovement(0, -2, 0);
         } else if (t == 52) {
            grabbed.teleportTo(anchor.x, anchor.y + 0.1, anchor.z);
            grabbed.hurt(this.damageSources().mobAttack(this), dmg(26));
            grabbed.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, false));
            grabbed.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false));
            earthBurst(level, anchor, 7F);
            Vfx.debris(level, anchor, 2F, 9F, 30, ground(anchor), Vec3.ZERO);
            Vfx.flash(level, anchor.add(0, 0.5, 0), 3F, 5, 0xE8F2D0);
            Vfx.shake(level, anchor, 1.4F, 24, 18);
            sound(Sounds.FX_EXPLODE.get(), 2F, 0.6F);
            sound(Sounds.FX_METAL.get(), 1.5F, 0.5F);
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(anchor, anchor).inflate(4, 2, 4))) {
               if (p == grabbed || p.isCreative() || p.isSpectator()) continue;
               p.hurt(this.damageSources().mobAttack(this), dmg(10));
               Vec3 push = p.position().subtract(anchor).normalize();
               p.setDeltaMovement(push.x, 0.7, push.z);
               p.hurtMarked = true;
            }
            releaseGrab();
         }
      } else if (t == 44 && grabbed == null) {
         // personne attrapé : les racines jaillissent au bout de la vague
         Vec3 head = surf(this.position().add(attackDir.scale(waveDist)));
         earthBurst(level, head, (1.8F) * 3F);
         Vfx.debris(level, head, 1.5F, 6F, 12, ground(head), Vec3.ZERO);
      }
   }

   /** Impact de Sylvorn : la terre explose en mottes et en poussière (pas d'anneau lumineux). */
   private void earthBurst(ServerLevel level, Vec3 at, float r) {
      Vec3 g = surf(at);
      Vfx.debris(level, g, r * 0.3F, 6F + r * 0.2F, (int) Math.min(40, 8 + r * 2), ground(g), Vec3.ZERO);
      level.sendParticles(ModParticles.DUST.get(), g.x, g.y + 0.4, g.z, (int) (10 + r * 3), r * 0.3, 0.3, r * 0.3, 0.05);
      for (int i = 0; i < 10; i++) {
         double a = i / 10.0 * Mth.TWO_PI;
         level.sendParticles(ModParticles.DUST.get(), g.x + Math.cos(a) * r * 0.5, g.y + 0.3, g.z + Math.sin(a) * r * 0.5, 0, Math.cos(a), 0.05, Math.sin(a), 0.25);
      }
   }

   private void releaseGrab() {
      grabbed = null;
      grabTick = -1;
   }

   /** Tronc : il arrache un tronc du sol et le lance en cloche sur sa cible (point d'impact annoncé). */
   private void log(ServerLevel level, int t, LivingEntity target) {
      if (t < 30 && target != null) faceTarget(target);
      Vec3 h = surf(handPos());
      if (t == 8) {
         sound(Sounds.FX_WOOD.get(), 3F, 0.5F);
         sound(Sounds.FX_EARTH.get(), 2.5F, 0.5F);
         Vfx.debris(level, h, 1.5F, 6F, 18, ground(h), Vec3.ZERO);
         Vfx.chips(level, h, 1F, 5F, 6, ModParticles.WOOD_CHIP.get());
      }
      if (t == 30 && target != null) {
         Vec3 aim = target.position().add(target.getDeltaMovement().scale(15));
         Vec3 from = this.position().add(fwd().scale(2)).add(0, 7, 0);
         int flight = (int) Mth.clamp(from.distanceTo(aim) * 0.9, 12, 30);
         ThrownLog l = HeraliszSylvorn.THROWN_LOG.get().create(level);   // par le type (usine : version serveur), jamais « new »
         if (l != null) {
            l.setOwner(this);
            l.setPos(from.x, from.y, from.z);
            double g = 0.06, drag = 0.99;
            double f = (1 - Math.pow(drag, flight)) / (1 - drag);
            l.setDeltaMovement((aim.x - from.x) / f, (aim.y - from.y + 0.5 * g * flight * flight) / f, (aim.z - from.z) / f);
            level.addFreshEntity(l);
         }
         Vfx.circle(level, surf(aim), 3.8F, flight);
         sound(Sounds.FX_SWIPE.get(), 2F, 0.3F);
      }
   }

   /** Cosses : cinq cosses de graines lancées en cloche autour des joueurs (cercles annoncés) ; elles se plantent, gonflent et éclatent en racines (SeedPod). */
   private void seeds(ServerLevel level, int t, LivingEntity target) {
      if (t < 18 && target != null) faceTarget(target);
      if (t == 18) {
         List<Player> ps = targets(34);
         for (int i = 0; i < 5; i++) {
            Vec3 aim = !ps.isEmpty() ? ps.get(i % ps.size()).position().add((random.nextDouble() - 0.5) * 7, 0, (random.nextDouble() - 0.5) * 7)
               : this.position().add((random.nextDouble() - 0.5) * 20, 0, (random.nextDouble() - 0.5) * 20);
            aim = surf(aim);
            Vec3 from = this.position().add(0, 8, 0);
            SeedPod s = HeraliszSylvorn.SEED_POD.get().create(level);   // par le type (usine : version serveur), jamais « new »
            int flight = 24;
            if (s != null) {
               s.setOwner(this);
               s.setPos(from.x, from.y, from.z);
               double g = 0.05, drag = 0.99;
               double f = (1 - Math.pow(drag, flight)) / (1 - drag);
               s.setDeltaMovement((aim.x - from.x) / f, (aim.y - from.y + 0.5 * g * flight * flight) / f, (aim.z - from.z) / f);
               level.addFreshEntity(s);
            }
            Vfx.circle(level, aim, 3.5F, flight);
         }
         sound(Sounds.DG_ROOTS.get(), 3F, 0.5F);
      }
   }

   /** Champ de racines : un cercle sous chaque joueur (5 au moins, les autres au hasard), puis des racines jaillissent : dégâts, projection vers le haut, fort ralentissement. */
   private void rootfield(ServerLevel level, int t) {
      if (t == 1) {
         marks.clear();
         for (Player p : targets(30)) marks.add(surf(p.position()));
         for (int i = marks.size(); i < 5; i++) marks.add(surf(this.position().add((random.nextDouble() - 0.5) * 22, 0, (random.nextDouble() - 0.5) * 22)));
         for (Vec3 m : marks) Vfx.circle(level, m, 3.2F, 20);
      }
      if (t == 20) {
         sound(Sounds.FX_EARTH.get(), 3F, 0.5F);
         sound(Sounds.SY_ROOTS.get(), 4F, 0.9F);
         Vfx.shake(level, this.position(), 0.8F, 30, 10);
         for (Vec3 m : marks) {
            earthBurst(level, m, (1.8F) * 3F);
            Vfx.debris(level, m, 1.5F, 6F, 12, ground(m), Vec3.ZERO);
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m, m).inflate(3.2, 3, 3.2))) {
               if (p.isCreative() || p.isSpectator()) continue;
               p.hurt(this.damageSources().mobAttack(this), dmg(14));
               p.setDeltaMovement(p.getDeltaMovement().x, 0.8, p.getDeltaMovement().z);
               p.hurtMarked = true;
               p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 4, false, false));
            }
         }
      }
   }

   /** Enracinement : il puise la sève de 3 nœuds lumineux ; tant qu'ils vivent, il se soigne et encaisse moins. */
   private void channel(ServerLevel level, int t) {
      if (t == 16) {
         nodes.clear();
         double base = random.nextDouble() * Mth.TWO_PI;
         for (int i = 0; i < 3; i++) {
            double ang = base + i * Mth.TWO_PI / 3;
            Vec3 p = surf(this.position().add(Math.cos(ang) * 11, 0, Math.sin(ang) * 11));
            if (HeraliszSylvorn.SAP_NODE.get().create(level) instanceof SapNodeServeur n) {   // par le type (usine), jamais « new »
               n.setup(this, p);
               level.addFreshEntity(n);
               nodes.add(n);
            }
            earthBurst(level, p, (1.4F) * 3F);
            for (int k = 1; k < 5; k++) level.sendParticles(ModParticles.SPORE.get(), surf(this.position().add(p.subtract(this.position()).scale(k / 5.0))).x, p.y + 0.3,
               surf(this.position().add(p.subtract(this.position()).scale(k / 5.0))).z, 6, 0.4, 0.2, 0.4, 0.02);
         }
         sound(Sounds.FX_POWER_UP.get(), 2.5F, 0.6F);
      }
      nodes.removeIf(n -> !n.isAlive());
      if (t > 16 && t % 10 == 0 && !nodes.isEmpty()) {
         this.heal(this.getMaxHealth() * 0.006F * nodes.size());
         for (SapNode n : nodes) {
            Vec3 d = this.position().add(0, 4, 0).subtract(n.position());
            level.sendParticles(ModParticles.SPORE.get(), n.getX(), n.getY() + 2.5, n.getZ(), 0, d.x, d.y, d.z, 0.08);
         }
      }
      if (t > 20 && nodes.isEmpty()) {
         // tous les nœuds détruits : il vacille
         sound(Sounds.SY_HURT.get(), 3F, 0.6F);
         earthBurst(level, surf(this.position()), 6F);
         start(Action.STUN);
      }
   }

   private void clearNodes(ServerLevel level) {
      for (SapNode n : nodes) if (n.isAlive()) n.withdraw();
      nodes.clear();
   }

   /** Chute des arbres (phase 3) : des lignes annoncées, puis des troncs entiers s'abattent dessus. */
   private void treefall(ServerLevel level, int t) {
      if (t == 3) {
         marks.clear();
         dirs.clear();
         List<Player> ps = targets(34);
         for (int i = 0; i < Math.max(3, Math.min(6, ps.size() * 2)); i++) {
            Vec3 target = !ps.isEmpty() ? ps.get(i % ps.size()).position() : this.position().add((random.nextDouble() - 0.5) * 20, 0, (random.nextDouble() - 0.5) * 20);
            double ang = random.nextDouble() * Mth.TWO_PI;
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 base = surf(target.subtract(dir.scale(6 + random.nextDouble() * 3)));
            marks.add(base);
            dirs.add(dir);
            Vfx.line(level, base, dir, 13F, 3.6F, 40);
         }
         sound(Sounds.FX_WOOD.get(), 3F, 0.4F);
      }
      if (t == 24) {
         for (int i = 0; i < marks.size(); i++) Vfx.treefall(level, marks.get(i), dirs.get(i), 12F, 34);
         sound(Sounds.FX_WOOD.get(), 3F, 0.5F);
      }
      if (t == 42) {
         Vfx.shake(level, this.position(), 1.3F, 40, 20);
         for (int i = 0; i < marks.size(); i++) {
            Vec3 b = marks.get(i), d = dirs.get(i);
            Vec3 mid = b.add(d.scale(7));
            Vfx.chips(level, mid, 3F, 7F, 18, ModParticles.WOOD_CHIP.get());
            Vfx.chips(level, mid, 3F, 5F, 12, ModParticles.LEAF.get());
            level.playSound(null, mid.x, mid.y, mid.z, Sounds.FX_EXPLODE.get(), SoundSource.HOSTILE, 1.6F, 0.7F);
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(b, b.add(d.scale(13))).inflate(2, 3, 2))) {
               if (p.isCreative() || p.isSpectator()) continue;
               Vec3 rel = p.position().subtract(b);
               double along = rel.dot(d);
               double side = rel.subtract(d.scale(along)).horizontalDistance();
               if (along < 0 || along > 13 || side > 1.9) continue;
               p.hurt(this.damageSources().mobAttack(this), dmg(22));
               p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
            }
         }
      }
   }

   /** Tempête de feuilles-rasoirs : trois bras de feuilles qui tournent autour de lui. */
   private void leafstorm(ServerLevel level, int t) {
      Vec3 o = surf(this.position());
      if (t == 1) {
         sound(Sounds.FX_SWIPE.get(), 3F, 0.6F);
      }
      if (t < 10 || t > 92) return;
      double base = t * 0.07;
      for (int arm = 0; arm < 3; arm++) {
         double ang = base + arm * Mth.TWO_PI / 3;
         for (int k = 0; k < 4; k++) {
            double r = 4 + random.nextDouble() * 14;
            double a2 = ang - r * 0.04;
            level.sendParticles(ModParticles.LEAF_AUTUMN.get(), o.x + Math.cos(a2) * r, o.y + 0.6 + random.nextDouble() * 2.5, o.z + Math.sin(a2) * r, 0,
               -Math.sin(a2), 0.05, Math.cos(a2), 0.6);
         }
      }
      if (t % 5 == 0) {
         for (Player p : targets(19)) {
            double d = Math.hypot(p.getX() - o.x, p.getZ() - o.z);
            if (d < 3.5) continue;
            double pa = Math.atan2(p.getZ() - o.z, p.getX() - o.x);
            for (int arm = 0; arm < 3; arm++) {
               double ang = base + arm * Mth.TWO_PI / 3 - d * 0.04;
               double diff = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(pa - ang)));
               if (diff < 16) {
                  p.hurt(this.damageSources().mobAttack(this), dmg(4.5F));
                  break;
               }
            }
         }
      }
   }

   // ------------------------------------------------------------------ nouvelles attaques
   /**
    * Hurlement (phase 2+) : il inspire, puis hurle droit devant ; l'onde (cône de 70°) part de la gueule et avance :
    * repousse, assourdit (ténèbres, nausée). Le cône pourri s'affiche avant ; on s'en sort en quittant le cône.
    */
   private void howl(ServerLevel level, int t, LivingEntity target) {
      if (t < 14 && target != null) faceTarget(target);
      if (t == 2) Vfx.cone(level, this.position(), fwd(), 18F, 70F, 15);
      if (t == 8) sound(Sounds.SY_BREATH.get(), 3F, 0.8F);
      if (t == 17) {
         Vfx.howl(level, headPos().add(0, -0.8, 0), fwd(), 20F, 70F, 30);
         Vfx.shake(level, this.position(), 0.9F, 40, 30);
         sound(Sounds.SY_HOWL.get(), 5F, 1F);
         sound(Sounds.FX_EXPLODE.get(), 2F, 0.5F);
         hitOnce.clear();
      }
      if (t < 17 || t > 46) return;
      float front = (t - 17) * 1.1F + 2F;
      for (Player p : targets(22)) {
         if (hitOnce.contains(p.getUUID())) continue;
         Vec3 to = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
         double d = to.length();
         if (d > front || d > 20 || d < 0.1) continue;
         if (fwd().dot(to.normalize()) < Math.cos(Math.toRadians(35))) continue;
         hitOnce.add(p.getUUID());
         float near = (float) (1 - d / 22);
         p.hurt(this.damageSources().mobAttack(this), dmg(6 + 8 * near));
         Vec3 push = to.normalize().scale(1.2 + 1.4 * near);
         p.setDeltaMovement(push.x, 0.45 + 0.3 * near, push.z);
         p.hurtMarked = true;
         p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
         p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1, false, false));
         p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 70, 0, false, false));
      }
      if (t % 3 == 0) {
         for (int k = -2; k <= 2; k++) {
            Vec3 q = surf(this.position().add(fwd().yRot(k * 0.25F).scale(Math.min(18, front + 1))));
            level.sendParticles(ModParticles.LEAF.get(), q.x, q.y + 0.6, q.z, 2, 0.6, 0.4, 0.6, 0.12);
            level.sendParticles(ModParticles.DUST.get(), q.x, q.y + 0.2, q.z, 2, 0.6, 0.1, 0.6, 0.04);
         }
      }
   }

   /**
    * Javelots d'épines : deux volées de trois (main droite 0,85 s, main gauche 1,45 s), la première sur le joueur le plus
    * éloigné (tireur). Les trois lignes de visée pourrissent le sol avant le lancer : un pas de côté suffit.
    */
   private void thorns(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         Player far = farthest(48);
         thornTargets[0] = far != null ? far : target;
         thornTargets[1] = target != null ? target : thornTargets[0];
         thornAim[0] = thornAim[1] = null;
      }
      for (int v = 0; v < 2; v++) {
         LivingEntity who = thornTargets[v];
         int tel = v == 0 ? 5 : 19, fire = v == 0 ? 17 : 29;
         if (who == null || !who.isAlive()) continue;
         if (t >= (v == 0 ? 0 : 18) && t < fire - 2) faceTarget(who);
         if (t == tel) {
            Vec3 aim = who.position().add(who.getDeltaMovement().multiply(8, 0, 8));
            thornAim[v] = aim;
            Vec3 from = surf(this.position());
            Vec3 d0 = new Vec3(aim.x - from.x, 0, aim.z - from.z).normalize();
            float len = (float) Math.min(48, from.distanceTo(aim) + 6);
            for (int k = -1; k <= 1; k++) Vfx.line(level, from.add(d0.yRot(k * 0.07F).scale(2)), d0.yRot(k * 0.07F), len, 1.1F, fire - tel);
            sound(Sounds.FX_WOOD.get(), 1.8F, 0.5F);
         }
         if (t == fire && thornAim[v] != null) {
            Vec3 hand = this.position().add(fwd().scale(1.5)).add(right().scale(v == 0 ? 1.6 : -1.6)).add(0, 8.5, 0);
            Vec3 aim = thornAim[v];
            Vec3 flat = new Vec3(aim.x - hand.x, 0, aim.z - hand.z);
            double speed = 2.6;
            int ticks = (int) Math.max(4, flat.length() / (speed * 0.9));
            double vy = (aim.y + 1.0 - hand.y) / ticks + 0.5 * 0.012 * ticks;
            for (int k = -1; k <= 1; k++) {
               Vec3 dir = flat.normalize().yRot(k * 0.07F);
               ThornSpear s = HeraliszSylvorn.THORN_SPEAR.get().create(level);   // par le type (usine : version serveur), jamais « new »
               if (s == null) continue;
               s.setOwner(this);
               s.setPos(hand.x, hand.y, hand.z);
               s.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
               level.addFreshEntity(s);
            }
            level.sendParticles(ModParticles.LEAF.get(), hand.x, hand.y, hand.z, 12, 0.5, 0.5, 0.5, 0.2);
            sound(Sounds.SY_THORNS.get(), 3F, 0.9F + random.nextFloat() * 0.2F);
            sound(Sounds.FX_SWIPE.get(), 2F, 0.4F);
         }
      }
   }

   /**
    * Marque du chasseur (phase 2+) : il pointe le tireur le plus éloigné (deux en phase 3) ; une rune de bois colle à ses pieds
    * pendant 0,9 s, se verrouille (rouge) et, 0,6 s plus tard, des racines géantes jaillissent : il faut sortir du cercle.
    */
   private void mark(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         marked.clear();
         markLocks.clear();
         List<Player> ps = targets(56);
         ps.sort((a, b) -> Double.compare(b.distanceTo(this), a.distanceTo(this)));
         int n = phase() == 3 ? 2 : 1;
         for (Player p : ps) {
            if (marked.size() >= n) break;
            marked.add(p);
         }
         if (marked.isEmpty() && target instanceof Player tp) marked.add(tp);
      }
      if (!marked.isEmpty() && t < 16) faceTarget(marked.get(0));
      if (t == 16) {
         for (Player p : marked) {
            Vfx.mark(level, surf(p.position()), p.getId(), 3.4F, 18, false);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), Sounds.FX_HEARTBEAT.get(), SoundSource.HOSTILE, 2F, 1.3F);
         }
         sound(Sounds.SY_WHISPER.get(), 4F, 1F);
      }
      if (t == 34) {
         for (Player p : marked) {
            Vec3 at = surf(p.position());
            markLocks.add(at);
            Vfx.mark(level, at, 0, 3.4F, 12, true);
            level.playSound(null, at.x, at.y, at.z, Sounds.FX_WOOD.get(), SoundSource.HOSTILE, 2F, 0.4F);
         }
      }
      if (t == 43) {
         Vec3 h = surf(handPos());
         earthBurst(level, h, 5F);
         Vfx.shake(level, h, 0.8F, 30, 10);
         sound(Sounds.FX_EARTH.get(), 3F, 0.5F);
      }
      if (t == 46) {
         for (Vec3 at : markLocks) {
            earthBurst(level, at, (2.4F) * 3F);
            Vfx.debris(level, at, 1.8F, 7F, 18, ground(at), Vec3.ZERO);
            Vfx.shake(level, at, 1.0F, 20, 12);
            level.playSound(null, at.x, at.y, at.z, Sounds.FX_EARTH.get(), SoundSource.HOSTILE, 3F, 0.5F);
            level.playSound(null, at.x, at.y, at.z, Sounds.FX_EXPLODE.get(), SoundSource.HOSTILE, 1.5F, 0.6F);
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(3.4, 3, 3.4))) {
               if (p.isCreative() || p.isSpectator() || p.position().distanceToSqr(at.x, p.getY(), at.z) > 3.4 * 3.4) continue;
               p.hurt(this.damageSources().mobAttack(this), dmg(24));
               p.setDeltaMovement(0, 1.0, 0);
               p.hurtMarked = true;
               p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4, false, false));
            }
         }
      }
   }

   /**
    * Charge des bois : il gratte deux fois le sol du sabot, puis fonce en ligne droite (couloir annoncé) en arrachant la terre.
    * Percuter un arbre ou un mur le plante là, étourdi (1,5 x dégâts) : c'est l'ouverture à provoquer.
    */
   private void charge(ServerLevel level, int t, LivingEntity target) {
      if (t < 14 && target != null) faceTarget(target);
      if (t == 2) {
         chargeDir = fwd();
         Vfx.line(level, surf(this.position()), chargeDir, 26F, 4.4F, 14);
         sound(Sounds.SY_BREATH.get(), 3F, 1.1F);
      }
      if (t == 6 || t == 11) {
         Vec3 hoof = surf(this.position().add(right().scale(1.0)));
         Vfx.debris(level, hoof, 0.6F, 3F, 5, ground(hoof), chargeDir.scale(-0.6));
         level.playSound(null, hoof.x, hoof.y, hoof.z, Sounds.FX_EARTH.get(), SoundSource.HOSTILE, 1.5F, 0.6F);
      }
      if (t == 16) {
         lastFurrow = surf(this.position());
         sound(Sounds.SY_ROAR.get(), 4F, 1.05F);
         hitOnce.clear();
      }
      if (t >= 16 && t < 46 && outsideLeash(this.position().add(chargeDir.scale(2)), 2)) {
         // bord de son territoire : il freine net
         this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1, 0.3));
         return;
      }
      if (t >= 16 && t < 46) {
         double sp = t < 20 ? 0.5 + (t - 16) * 0.12 : 0.95;
         this.setDeltaMovement(chargeDir.x * sp, this.getDeltaMovement().y, chargeDir.z * sp);
         float yaw = (float) (Mth.atan2(chargeDir.z, chargeDir.x) * Mth.RAD_TO_DEG) - 90F;
         this.setYRot(yaw);
         this.yBodyRot = yaw;
         this.yHeadRot = yaw;
         if (t % 4 == 0) {
            Vec3 now = surf(this.position());
            if (lastFurrow != null && now.distanceTo(lastFurrow) > 0.5)
               Vfx.furrow(level, lastFurrow, now.subtract(lastFurrow), (float) now.distanceTo(lastFurrow), 120);
            lastFurrow = now;
            level.playSound(null, now.x, now.y, now.z, Sounds.FX_STEP.get(), SoundSource.HOSTILE, 2F, 0.6F);
            Vfx.shake(level, now, 0.4F, 16, 4);
         }
         for (Player p : targets(5)) {
            if (hitOnce.contains(p.getUUID()) || p.distanceToSqr(this.getX(), p.getY(), this.getZ()) > 3.2 * 3.2) continue;
            hitOnce.add(p.getUUID());
            Vec3 side = new Vec3(-chargeDir.z, 0, chargeDir.x);
            if (side.dot(p.position().subtract(this.position())) < 0) side = side.scale(-1);
            p.hurt(this.damageSources().mobAttack(this), dmg(22));
            p.setDeltaMovement(chargeDir.x * 1.4 + side.x, 0.8, chargeDir.z * 1.4 + side.z);
            p.hurtMarked = true;
         }
         if (t > 20 && this.horizontalCollision) {
            // les bois plantés dans un tronc ou un mur : sonné
            Vec3 at = this.position().add(chargeDir.scale(2.5)).add(0, 4, 0);
            Vfx.chips(level, at, 1.5F, 6F, 20, ModParticles.WOOD_CHIP.get());
            Vfx.shake(level, at, 1.3F, 30, 16);
            sound(Sounds.FX_WOOD.get(), 3F, 0.5F);
            sound(Sounds.FX_METAL.get(), 1.5F, 0.4F);
            this.setDeltaMovement(Vec3.ZERO);
            start(Action.STUN);
         }
         return;
      }
      if (t == 46) {
         Vec3 g = surf(this.position());
         Vfx.debris(level, g, 1.6F, 5F, 16, ground(g), chargeDir.scale(0.6));
         sound(Sounds.FX_EARTH.get(), 2.5F, 0.6F);
      }
      if (t > 46) this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1, 0.6));
   }

   /** Disparition (phase 2+) : il se recroqueville dans un tourbillon de feuilles, la brume tombe, il s'évanouit (1,45 s). */
   private void vanish(ServerLevel level, int t) {
      if (t == 1) sound(Sounds.SY_WHISPER.get(), 4F, 0.8F);
      if (t % 2 == 0) {
         for (int i = 0; i < 6; i++) {
            double a = t * 0.4 + i * Mth.TWO_PI / 6, r = Math.max(1.2, 3.8 - t * 0.08);
            level.sendParticles(ModParticles.LEAF.get(), getX() + Math.cos(a) * r, getY() + 0.8 + i * 1.1, getZ() + Math.sin(a) * r, 0,
               -Math.sin(a) * 0.5, 0.1, Math.cos(a) * 0.5, 0.4);
         }
      }
      if (t == 10) sound(Sounds.FX_SWIPE.get(), 2F, 0.5F);
      if (t == 29) {
         this.setInvisible(true);
         level.sendParticles(ModParticles.LEAF_AUTUMN.get(), getX(), getY() + 3, getZ(), 70, 2, 2.5, 2, 0.25);
         level.sendParticles(ModParticles.SPORE.get(), getX(), getY() + 3, getZ(), 30, 1.5, 2, 1.5, 0.05);
         sound(Sounds.DG_ROOTS.get(), 3F, 0.5F);
      }
   }

   /** Il rôde, invisible, jusque dans le dos de sa proie : empreintes de sabots fendus et pas lourds trahissent sa route. */
   private void stalk(ServerLevel level, int t, LivingEntity target) {
      if (target == null) return;
      Vec3 look = target.getLookAngle();
      Vec3 back = new Vec3(look.x, 0, look.z);
      back = back.lengthSqr() < 1e-4 ? fwd() : back.normalize();
      Vec3 dest = inLeash(surf(target.position().subtract(back.scale(4.5))));
      Vec3 step = new Vec3(dest.x - getX(), 0, dest.z - getZ());
      double len = step.length();
      if (len > 0.15) {
         Vec3 mv = step.scale(Math.min(0.75, len) / len);
         Vec3 np = surf(this.position().add(mv));
         this.teleportTo(np.x, np.y, np.z);
         float yaw = (float) (Mth.atan2(mv.z, mv.x) * Mth.RAD_TO_DEG) - 90F;
         this.setYRot(yaw);
         this.yBodyRot = yaw;
         if (t % 4 == 0) {
            Vec3 foot = surf(this.position().add(right().scale((t / 4) % 2 == 0 ? 0.9 : -0.9)));
            Vfx.print(level, foot, mv, 0.8F, 240);
            level.playSound(null, foot.x, foot.y, foot.z, Sounds.FX_STEP.get(), SoundSource.HOSTILE, 1.4F, 0.45F);
         }
      }
      if (t % 12 == 0) level.playSound(null, getX(), getY() + 4, getZ(), Sounds.FX_HEARTBEAT.get(), SoundSource.HOSTILE, 1.6F, 0.55F);
      if (t % 5 == 0) level.sendParticles(ModParticles.SPORE.get(), getX(), getY() + 2, getZ(), 2, 0.8, 1.2, 0.8, 0.01);
      if (t >= Action.STALK.ticks - 2) faceTarget(target);
   }

   /** Il surgit des feuilles derrière sa proie et abat ses deux griffes (0,55 s) : cône de 120°, on esquive en roulant/sprintant. */
   private void ambush(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         this.setInvisible(false);
         if (target != null) faceTarget(target);
         level.sendParticles(ModParticles.LEAF_AUTUMN.get(), getX(), getY() + 3, getZ(), 80, 2.2, 2.5, 2.2, 0.3);
         Vfx.cone(level, this.position(), fwd(), 8F, 120F, 11);
         sound(Sounds.SY_ROAR.get(), 4F, 1.15F);
         Vfx.shake(level, this.position(), 0.8F, 30, 10);
      }
      if (t == 11) {
         sound(Sounds.FX_SWIPE.get(), 2.5F, 0.4F);
         earthBurst(level, surf(this.position().add(fwd().scale(4))), 6F);
         for (Player p : targets(9)) {
            Vec3 to = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            if (to.length() > 8.5 || fwd().dot(to.normalize()) < Math.cos(Math.toRadians(60))) continue;
            p.hurt(this.damageSources().mobAttack(this), dmg(24));
            p.setDeltaMovement(to.normalize().x * 1.3, 0.6, to.normalize().z * 1.3);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
         }
      }
   }

   /**
    * Bond (phase 3) : à quatre pattes, il fixe le tireur le plus éloigné, bondit (0,7 s) en cloche de 16 ticks et retombe griffes
    * en avant sur le cercle annoncé : cratère, racines, projection.
    */
   private void pounce(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         Player far = farthest(40);
         LivingEntity who = far != null ? far : target;
         leapFrom = this.position();
         leapTo = who != null ? surf(who.position().add(who.getDeltaMovement().multiply(12, 0, 12))) : surf(this.position().add(fwd().scale(12)));
         if (leapTo.distanceTo(leapFrom) > 30) leapTo = surf(leapFrom.add(leapTo.subtract(leapFrom).normalize().scale(30)));
         leapTo = inLeash(leapTo);
         landed = false;
         Vfx.circle(level, leapTo, 5.5F, 30);
         sound(Sounds.SY_ROAR.get(), 3.5F, 1.2F);
      }
      if (leapTo == null) return;
      if (t < 14) {
         float yaw = (float) (Mth.atan2(leapTo.z - getZ(), leapTo.x - getX()) * Mth.RAD_TO_DEG) - 90F;
         this.setYRot(yaw);
         this.yBodyRot = yaw;
         this.yHeadRot = yaw;
      }
      if (t == 14) {
         Vec3 g = surf(this.position());
         Vfx.debris(level, g, 1.8F, 6F, 16, ground(g), leapTo.subtract(g).normalize().scale(-0.4));
         Vfx.shake(level, g, 0.8F, 30, 8);
         sound(Sounds.FX_STEP.get(), 3F, 0.5F);
         sound(Sounds.FX_SWIPE.get(), 2.5F, 0.5F);
      }
      if (t >= 14 && t < 30) {
         double u = (t - 13) / 16.0;
         double h = (6 + 0.15 * leapFrom.distanceTo(leapTo)) * 4 * u * (1 - u);
         Vec3 next = leapFrom.add(leapTo.subtract(leapFrom).scale(u)).add(0, h, 0);
         this.setDeltaMovement(next.subtract(this.position()));
         this.fallDistance = 0;
      }
      if (!landed && t >= 29 && (this.onGround() || t >= 40)) {
         landed = true;
         Vec3 at = surf(this.position());
         Vfx.crater(level, at, 5.5F, 90);
         earthBurst(level, at, 12F);
         Vfx.debris(level, at, 3F, 9F, 34, ground(at), Vec3.ZERO);
         Vfx.shake(level, at, 1.4F, 40, 20);
         for (int i = 0; i < 8; i++) {
            double a = i * Mth.TWO_PI / 8;
            earthBurst(level, surf(at.add(Math.cos(a) * 4.5, 0, Math.sin(a) * 4.5)), (1.1F) * 3F);
         }
         sound(Sounds.FX_EXPLODE.get(), 2F, 0.5F);
         sound(Sounds.FX_STEP.get(), 3F, 0.4F);
         for (Player p : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(5.5, 3, 5.5))) {
            if (p.isCreative() || p.isSpectator() || p.distanceToSqr(at) > 5.5 * 5.5) continue;
            Vec3 push = new Vec3(p.getX() - at.x, 0, p.getZ() - at.z);
            push = push.lengthSqr() < 1e-4 ? fwd() : push.normalize();
            p.hurt(this.damageSources().mobAttack(this), dmg(26));
            p.setDeltaMovement(push.x * 1.3, 0.9, push.z * 1.3);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, false));
         }
      }
   }

   // ------------------------------------------------------------------ garde-fous
   private int strayTicks;

   /** Remise à zéro seulement si personne ne se bat dans la salle depuis 30 s (voir HeraliszBoss.leash). */
   private void leash(ServerLevel level) {
      if (home == null) return;
      int r = SylvornConfig.LEASH_RADIUS.get();
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(home).inflate(r), p -> !p.isCreative() && !p.isSpectator() && p.isAlive()).isEmpty();
      lonelyTicks = anyone ? 0 : lonelyTicks + 20;
      strayTicks = Math.sqrt(this.blockPosition().distSqr(home)) > r ? strayTicks + 20 : 0;
      if (strayTicks > 100 && lonelyTicks <= 600) {
         // sorti de sa clairière : il y revient, sans se soigner
         this.teleportTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5);
         strayTicks = 0;
      }
      if (lonelyTicks > 600) {
         TreeHallBEServeur h = hall();
         if (h != null) {
            h.bossLeft(level);
            return;
         }
         this.setTarget(null);
         this.teleportTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5);
         this.setHealth(this.getMaxHealth());
         this.entityData.set(PHASE, 1);
         this.entityData.set(ACTION, 0);
         this.setInvisible(false);
         peakPlayers = 1;
         lonelyTicks = 0;
      }
   }

   private void updateBossBar(ServerLevel level) {
      for (ServerPlayer p : level.players()) {
         boolean near = p.distanceTo(this) < 64;
         if (near && !bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
         else if (!near && bossBar.getPlayers().contains(p)) bossBar.removePlayer(p);
      }
   }

   // ------------------------------------------------------------------ dégâts
   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide || this.isDeadOrDying()) return false;
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
      if (dormant()) return false;
      Action a = action();
      if (a == Action.SPAWN || a == Action.PHASE2 || a == Action.PHASE3 || a == Action.STALK || (a == Action.VANISH && actionTime() >= 20)) return false;
      float raw = Math.min(amount, SylvornConfig.HIT_CAP.get().floatValue());
      Vec3 weak = headPos();
      if (WeakPoint.hit(source, this, weak, 1.4)) {
         raw *= WeakPoint.CRIT;
         WeakPoint.feedback(this, source, weak);
      }
      float window = 0;
      for (float v : dpsWindow) window += v;
      if (window > SylvornConfig.DPS_CAP.get()) raw *= 0.25F;
      dpsWindow[this.tickCount % 20] += raw;
      if (a == Action.CHANNEL && !nodes.isEmpty()) raw *= 0.4F;
      if (a == Action.STUN) raw *= 1.5F;
      if (source.is(DamageTypeTags.IS_FIRE)) raw *= 1.5F;           // le bois craint le feu
      float scaled = raw * this.getMaxHealth() / Math.max(1F, SylvornConfig.VIRTUAL_HP.get() * groupMult());
      this.invulnerableTime = 0;
      boolean ok = super.hurt(source, Math.max(scaled, 0.01F));
      this.invulnerableTime = 0;
      if (ok && source.getEntity() instanceof Player pl) threat.merge(pl.getUUID(), raw, Float::sum);
      if (ok && this.level() instanceof ServerLevel sl)
         sl.sendParticles(ModParticles.WOOD_CHIP.get(), getX(), getY() + 4, getZ(), 3, 1, 1.5, 1, 0.1);
      return ok;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return Sounds.SY_BREATH.get();
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource s) {
      return Sounds.SY_HURT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return Sounds.SY_HURT.get();
   }

   @Override
   public float getVoicePitch() {
      return 0.9F + this.random.nextFloat() * 0.2F;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      this.playSound(Sounds.SY_STEP.get(), 1.6F, 0.9F + this.random.nextFloat() * 0.2F);
      if (this.level() instanceof ServerLevel sl && this.tickCount % 2 == 0) Vfx.shake(sl, this.position(), 0.15F, 14, 4);
   }
   private Vec3 hangAt, dropFrom;
   private SylvornLegs legs;
   private boolean lootDone;

   @Override
   protected void tickDeath() {
      ++this.deathTime;
      if (this.level().isClientSide) return;
      ServerLevel level = (ServerLevel) this.level();
      int t = this.deathTime;
      if (t % 10 == 0) this.entityData.set(DEATH_T, t);
      if (t <= DEATH_TICKS) deathTick(level, t);
      if (t > DEATH_TICKS && dropFrom != null) {
         Vec3 g = surf(dropFrom);
         this.setPos(g.x, g.y, g.z);
         this.setDeltaMovement(Vec3.ZERO);
      }
      if (t >= REMAINS) {
         bossBar.removeAllPlayers();
         this.remove(RemovalReason.KILLED);
      }
   }

   private void deathTick(ServerLevel level, int t) {
      TreeHallBEServeur h = hall();
      Vec3 heart = heartPos();
      if (t == 1) {
         this.entityData.set(ACTION, 0);
         this.setInvisible(false);
         releaseGrab();
         clearNodes(level);
         bossBar.setProgress(0);
         deathFrom = surf(this.position());
         this.noPhysics = true;
         this.setNoGravity(true);
         Player near = this.level().getNearestPlayer(this, 48);
         Vec3 toP = near != null ? new Vec3(near.getX() - getX(), 0, near.getZ() - getZ()) : fwd();
         if (toP.lengthSqr() < 1e-3) toP = fwd();
         toP = toP.normalize();
         faceDir(toP);
         this.entityData.set(DEATH_AT, new org.joml.Vector3f((float) deathFrom.x, (float) deathFrom.y, (float) deathFrom.z));
         this.entityData.set(DEATH_YAW, this.yBodyRot);
         crawlGoal = deathFrom.add(toP.scale(6.5));
         if (SylvornConfig.CINEMATICS.get())
            fr.heralisz.sylvorn.vfx.Vfx.cinematic(level, deathFrom, this.getId(), DEATH_K, DEATH_CINE, toP);
         sound(Sounds.SY_HURT.get(), 5F, 0.7F);
         if (h != null) h.heartStage(level, 2);
      }
      this.setDeltaMovement(Vec3.ZERO);
      Vec3 f0 = Vec3.directionFromRotation(0, deathYaw()), r0 = new Vec3(-f0.z, 0, f0.x);
      // le cœur bat, fort, tout au long de sa mort
      if (t < D_DROP && t % (t < D_TEAR ? 12 : t < D_DRAIN0 ? 16 : 8) == 0)
         level.playSound(null, heart.x, heart.y, heart.z, Sounds.FX_HEARTBEAT.get(), SoundSource.HOSTILE, 8F, t >= D_DRAIN0 ? 1.1F : 0.8F);
      if (t == 20) sound(Sounds.SY_HOWL.get(), 4F, 0.7F);
      if (t == D_REACH) {
         for (int i = 0; i < 2; i++) {
            Vec3 a = branchAnchor(i);
            level.playSound(null, a.x, a.y - 8, a.z, Sounds.DG_CREAK.get(), SoundSource.HOSTILE, 6F, 0.45F);
            level.playSound(null, a.x, a.y - 8, a.z, Sounds.SY_ROOTS.get(), SoundSource.HOSTILE, 5F, 0.6F);
            level.sendParticles(ModParticles.LEAF.get(), a.x, a.y - 4, a.z, 40, 2, 2, 2, 0.05);
            level.sendParticles(ModParticles.WOOD_CHIP.get(), a.x, a.y - 4, a.z, 16, 1, 1, 1, 0.1);
         }
      }
      if (t > D_REACH && t < D_GRAB && t % 6 == 0) {
         for (int i = 0; i < 2; i++) {
            Vec3 a = branchAnchor(i).lerp(deathFrom, (t - D_REACH) / (double) (D_GRAB - D_REACH));
            level.sendParticles(ModParticles.LEAF.get(), a.x, a.y, a.z, 3, 0.8, 0.8, 0.8, 0.03);
         }
      }
      if (t == D_GRAB) {
         // elles saisissent ses chevilles
         sound(Sounds.FX_WOOD.get(), 5F, 0.6F);
         sound(Sounds.SY_HURT.get(), 5F, 0.6F);
         Vfx.shake(level, deathFrom, 0.9F, 40, 14);
         for (int i = -1; i <= 1; i += 2) {
            Vec3 a = deathFrom.add(r0.scale(i * 1.2)).add(0, 0.5, 0);
            level.sendParticles(ModParticles.WOOD_CHIP.get(), a.x, a.y, a.z, 12, 0.5, 0.4, 0.5, 0.12);
         }
      }
      if (t >= D_LIFT0 && t < D_TEAR) {
         // hissé la tête en bas ; il se débat, tire sur les branches, hurle
         double u = ease((t - D_LIFT0) / (double) (D_LIFT1 - D_LIFT0));
         double sw = t > D_LIFT1 ? Math.sin((t - D_LIFT1) * 0.13) * 0.45 : 0;
         hangAt = deathFrom.add(0, HANG_H * u, 0).add(r0.scale(sw)).add(f0.scale(0.25 * Math.sin(t * 0.21)));
         this.setPos(hangAt.x, hangAt.y, hangAt.z);
         if (t == D_LIFT0 + 2) sound(Sounds.SY_ROAR.get(), 5F, 0.7F);
         if (t > D_LIFT1 && t % 26 == 0) sound(t % 52 == 0 ? Sounds.SY_HURT.get() : Sounds.SY_HOWL.get(), 4.5F, 0.6F + random.nextFloat() * 0.15F);
         if (t % 11 == 0) {
            level.playSound(null, hangAt.x, hangAt.y, hangAt.z, Sounds.DG_CREAK.get(), SoundSource.HOSTILE, 3F, 0.5F + random.nextFloat() * 0.3F);
            level.sendParticles(ModParticles.WOOD_CHIP.get(), hangAt.x, hangAt.y + 0.5, hangAt.z, 4, 0.8, 0.5, 0.8, 0.08);
         }
         if (t % 3 == 0 && t > D_LIFT1) level.sendParticles(ModParticles.BLOOD.get(), hangAt.x, hangAt.y - 1, hangAt.z, 1, 0.6, 0.3, 0.6, 0.02);
      }
      if (t == D_TEAR) {
         // les branches tirent : les jambes cèdent aux hanches
         Vec3 hips = hangAt.subtract(0, HIPS_H, 0);
         sound(Sounds.FX_BONE.get(), 6F, 0.5F);
         sound(Sounds.FX_BONE.get(), 6F, 0.7F);
         sound(Sounds.FX_GOO.get(), 6F, 0.5F);
         sound(Sounds.SY_HURT.get(), 6F, 0.45F);
         sound(Sounds.SY_ROAR.get(), 6F, 1.1F);
         Vfx.shake(level, hips, 1.3F, 50, 22);
         Vfx.flash(level, hips, 3F, 5, 0x8A0A0A);
         level.sendParticles(ModParticles.BLOOD.get(), hips.x, hips.y, hips.z, 140, 1.4, 0.8, 1.4, 0.35);
         level.sendParticles(ModParticles.BLOOD.get(), hips.x, hips.y, hips.z, 60, 0.4, 0.4, 0.4, 0.8);
         level.sendParticles(ModParticles.BONE_CHIP.get(), hips.x, hips.y, hips.z, 20, 0.8, 0.5, 0.8, 0.3);
         legs = HeraliszSylvorn.SYLVORN_LEGS.get().create(level);
         if (legs instanceof SylvornLegsServeur lg) {
            lg.setup(hangAt, deathYaw(), this.getId());
            level.addFreshEntity(lg);
         }
         dropFrom = hangAt.subtract(0, 2 * HIPS_H, 0);
      }
      if (t >= D_TEAR && t < D_LAND) {
         // le tronc tombe, tête la première, et bascule à plat ventre
         double u = (t - D_TEAR) / (double) (D_LAND - D_TEAR);
         Vec3 p = dropFrom.lerp(deathFrom, u * u);
         this.setPos(p.x, p.y, p.z);
         if (t % 2 == 0) level.sendParticles(ModParticles.BLOOD.get(), p.x, p.y + HIPS_H, p.z, 6, 0.5, 0.3, 0.5, 0.1);
      }
      if (t == D_LAND) {
         this.setPos(deathFrom.x, deathFrom.y, deathFrom.z);
         sound(Sounds.FX_EARTH.get(), 6F, 0.4F);
         sound(Sounds.FX_GOO.get(), 5F, 0.6F);
         Vfx.shake(level, deathFrom, 1.3F, 50, 20);
         Vfx.debris(level, deathFrom, 2.5F, 6F, 26, ground(deathFrom), Vec3.ZERO);
         level.sendParticles(ModParticles.DUST.get(), deathFrom.x, deathFrom.y + 0.3, deathFrom.z, 50, 3, 0.3, 3, 0.08);
         level.sendParticles(ModParticles.BLOOD.get(), deathFrom.x, deathFrom.y + 0.4, deathFrom.z, 70, 2, 0.2, 2, 0.2);
      }
      if (t > D_LAND && t < D_GRAB2) {
         // il rampe vers eux sur ses griffes, la gueule ouverte, en hurlant ; il laisse une traînée de sang et de sève
         Vec3 p = this.position();
         Vec3 to = new Vec3(crawlGoal.x - p.x, 0, crawlGoal.z - p.z);
         Player near = this.level().getNearestPlayer(this, 12);
         boolean blocked = near != null && near.distanceTo(this) < 3.5;
         double pull = 0.5 + 0.5 * Math.sin((t - D_LAND) * Mth.TWO_PI / 36.0);     // à chaque traction des bras
         if (to.length() > 0.2 && !blocked) {
            Vec3 step = to.normalize().scale(0.052 * (0.3 + 1.4 * pull));
            Vec3 q = surf(p.add(step).add(0, 1, 0));
            if (Math.abs(q.y - p.y) < 1.2) this.setPos(q.x, q.y, q.z);
         }
         faceDir(near != null ? new Vec3(near.getX() - getX(), 0, near.getZ() - getZ()) : to);
         if ((t - D_LAND) % 36 == 8) {
            sound(Sounds.FX_EARTH.get(), 2.5F, 0.6F + random.nextFloat() * 0.2F);
            Vec3 hd = this.position().add(fwd().scale(3.5));
            level.sendParticles(ModParticles.DUST.get(), hd.x, hd.y + 0.2, hd.z, 10, 1.2, 0.1, 1.2, 0.04);
         }
         if ((t - D_LAND) % 30 == 4) sound(random.nextBoolean() ? Sounds.SY_HOWL.get() : Sounds.SY_ROAR.get(), 5F, 1.15F + random.nextFloat() * 0.2F);
         if (t % 3 == 0) level.sendParticles(ModParticles.BLOOD.get(), getX() - fwd().x, getY() + 0.3, getZ() - fwd().z, 2, 0.4, 0.05, 0.4, 0.01);
      }
      if (t == D_REACH2) {
         Vec3 a = branchAnchor(2);
         level.playSound(null, a.x, a.y - 10, a.z, Sounds.DG_CREAK.get(), SoundSource.HOSTILE, 6F, 0.4F);
         level.playSound(null, a.x, a.y - 10, a.z, Sounds.SY_ROOTS.get(), SoundSource.HOSTILE, 5F, 0.55F);
         level.sendParticles(ModParticles.LEAF.get(), a.x, a.y - 4, a.z, 40, 2, 2, 2, 0.05);
      }
      if (t == D_GRAB2) {
         crawlGoal = this.position();
         sound(Sounds.FX_WOOD.get(), 5F, 0.55F);
         sound(Sounds.SY_HURT.get(), 6F, 0.55F);
         Vfx.shake(level, this.position(), 1.0F, 40, 14);
         level.sendParticles(ModParticles.WOOD_CHIP.get(), getX(), getY() + 2, getZ(), 24, 1.2, 0.8, 1.2, 0.15);
      }
      if (t >= D_GRAB2 && t < D_DROP) {
         // la troisième branche l'enserre au buste et le soulève ; il se débat, puis le cœur l'aspire
         Vec3 g = crawlGoal;
         double u = ease((t - D_GRAB2) / (double) (D_UP2 - D_GRAB2));
         double sway = t > D_UP2 ? Math.sin((t - D_UP2) * 0.07) * 0.3 * (1 - withered(0)) : 0;
         Vec3 p = g.add(0, LIFT2_H * u, 0).add(fwd().scale(sway));
         this.setPos(p.x, p.y, p.z);
         if (t < D_DRAIN0 && t % 20 == 0) sound(Sounds.SY_HURT.get(), 5F, 0.7F);
      }
      if (t >= D_DRAIN0 && t < D_DRAIN1) {
         // les filaments de vie : de lui jusqu'au cœur, verts, qui pulsent
         Vec3 c = this.position().add(0, 5, 0);
         if ((t - D_DRAIN0) % 25 == 0) {
            for (int i = 0; i < 4; i++) {
               Vec3 off = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(2.4);
               fr.heralisz.sylvorn.vfx.Vfx.beam(level, heart.add(off.scale(0.4)), c.add(off), 0.3F + 0.15F * random.nextFloat(), 0.9F, 34, 0x7CFF4A);
            }
         }
         if (t % 2 == 0) {
            // la sève remonte le long des filaments
            double k = random.nextDouble();
            Vec3 q = c.lerp(heart, k);
            level.sendParticles(ModParticles.SPORE.get(), q.x, q.y, q.z, 2, 0.4, 0.4, 0.4, 0.02);
         }
         if (t % 20 == 0) level.playSound(null, getX(), getY() + 4, getZ(), Sounds.DG_WHISPER.get(), SoundSource.HOSTILE, 3F, 0.6F);
         if (t == D_DRAIN0 + 6) sound(Sounds.SY_HOWL.get(), 6F, 0.5F);
         if (t == D_DRAIN0 + 60) sound(Sounds.SY_PETRIFY.get(), 5F, 0.6F);
         if (t % 14 == 0) level.playSound(null, getX(), getY() + 4, getZ(), Sounds.DG_CREAK.get(), SoundSource.HOSTILE, 2.5F, 0.35F + random.nextFloat() * 0.2F);
      }
      if (t == D_DRAIN1) {
         sound(Sounds.DG_GROAN.get(), 5F, 0.5F);
         if (h != null) h.heartStage(level, 3);
      }
      if (t >= D_DROP && t <= D_DROPPED) {
         // l'Arbre le lâche : une carcasse de bois mort qui tombe
         if (t == D_DROP) {
            dropFrom = this.position();
            level.playSound(null, getX(), getY() + 5, getZ(), Sounds.FX_WOOD.get(), SoundSource.HOSTILE, 4F, 0.8F);
         }
         double u = (t - D_DROP) / (double) (D_DROPPED - D_DROP);
         Vec3 g = surf(dropFrom);
         Vec3 p = new Vec3(dropFrom.x, Mth.lerp(u * u, dropFrom.y, g.y), dropFrom.z);
         this.setPos(p.x, p.y, p.z);
      }
      if (t == D_DROPPED) {
         Vec3 o = this.position();
         sound(Sounds.FX_WOOD.get(), 6F, 0.4F);
         sound(Sounds.FX_EARTH.get(), 5F, 0.5F);
         Vfx.shake(level, o, 1.1F, 50, 18);
         Vfx.debris(level, o, 2F, 5F, 20, net.minecraft.world.level.block.Blocks.STRIPPED_OAK_WOOD.defaultBlockState(), Vec3.ZERO);
         level.sendParticles(ModParticles.WOOD_CHIP.get(), o.x, o.y + 1, o.z, 40, 1.8, 0.6, 1.8, 0.2);
         level.sendParticles(ModParticles.DUST.get(), o.x, o.y + 0.3, o.z, 60, 3, 0.3, 3, 0.07);
         level.sendParticles(ModParticles.LEAF_AUTUMN.get(), o.x, o.y + 20, o.z, 120, 10, 4, 10, 0.05);
      }
      if (t == D_LOOT) dropTrophies(level);
      if (t == DEATH_TICKS && h != null) h.deathEnd(level);
   }

   /** Butin : il tombe de la carcasse flétrie. */
   private void dropTrophies(ServerLevel level) {
      if (lootDone) return;
      lootDone = true;
      Vec3 c = this.position().add(0, 1.5, 0);
      List<ItemStack> loot = new ArrayList<>();
      loot.add(new ItemStack(HeraliszSylvorn.SYLVORN_ANTLER.get()));
      loot.add(new ItemStack(HeraliszSylvorn.SYLVORN_HEART.get()));
      loot.add(new ItemStack(HeraliszSylvorn.SYLVORN_MASK_SHARD.get(), 2 + random.nextInt(3)));
      loot.add(new ItemStack(HeraliszSylvorn.PETRIFIED_BARK.get(), 6 + random.nextInt(7)));
      loot.add(new ItemStack(HeraliszSylvorn.ANCESTRAL_SEED.get()));
      for (ItemStack st : loot) {
         ItemEntity ie = new ItemEntity(level, c.x, c.y, c.z, st);
         double a = random.nextDouble() * Mth.TWO_PI;
         ie.setDeltaMovement(Math.cos(a) * 0.22, 0.42, Math.sin(a) * 0.22);
         ie.setDefaultPickUpDelay();
         ie.setExtendedLifetime();
         ie.setGlowingTag(true);
         level.addFreshEntity(ie);
      }
      net.minecraft.world.entity.ExperienceOrb.award(level, c, 500);
      Vfx.flash(level, c, 2.5F, 8, 0x9CFF7A);
      level.sendParticles(ModParticles.WOOD_CHIP.get(), c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.15);
      sound(Sounds.FX_WOOD.get(), 3F, 0.6F);
   }

   @Override
   public void remove(RemovalReason r) {
      bossBar.removeAllPlayers();
      super.remove(r);
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Phase", phase());
      tag.putBoolean("N07Saved", true);
      if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
      if (hallPos != null) tag.put("Hall", NbtUtils.writeBlockPos(hallPos));
      tag.putFloat("HomeYaw", homeYaw);
      tag.putBoolean("Dormant", dormant());
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      fromSave = tag.getBoolean("N07Saved");
      this.entityData.set(PHASE, Math.max(1, tag.getInt("Phase")));
      this.setInvisible(false);
      if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
      if (tag.contains("Hall")) hallPos = NbtUtils.readBlockPos(tag.getCompound("Hall"));
      if (tag.contains("HomeYaw")) homeYaw = tag.getFloat("HomeYaw");
      if (tag.getBoolean("Dormant") || home != null) sleep();                    // un combat interrompu : il regagne l'écorce
   }
}
