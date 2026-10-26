package fr.heralisz.mere.entity;

import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.MereConfig;
import fr.heralisz.mere.dungeon.NestHallBEServeur;
import fr.heralisz.mere.fx.Particles;
import fr.heralisz.mere.sound.Sounds;
import fr.heralisz.mere.vfx.Vfx;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link RuinMother} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new RuinMother(...) » côté serveur.
 */
public class RuinMotherServeur extends RuinMother {
   public RuinMotherServeur(EntityType<? extends RuinMother> type, Level level) {
      super(type, level);
   }

   private static final int[] SP_BEATS = {10, 30, 47, 61, 72, 80, 86, 91, 95};
   private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.heralisz_mere.ruin_mother"), BossEvent.BossBarColor.RED,
      BossEvent.BossBarOverlay.PROGRESS);
   private final float[] dpsWindow = new float[20];
   private final int[] cooldowns = new int[ACTS.length];
   private final Map<UUID, Float> threat = new HashMap<>();
   private BlockPos home, hallPos;
   private float homeYaw = Float.NaN;
   private int globalCd = 30, peakPlayers = 1, peakHold;
   private boolean fromSave, lootDone;

   private record WebZone(Vec3 c, float r, int until) {}

   private final List<WebZone> webs = new ArrayList<>();
   private final List<SpiderlingServeur> brood = new ArrayList<>();
   /** Joueurs emmaillotés : cloués sur place jusqu'à la fin du cocon {x, y, z, fin}. */
   private final Map<Player, double[]> wrapped = new HashMap<>();
   private final List<Player> pulled = new ArrayList<>();
   private Player prey;
   private Vec3 dropAt, leapFrom, leapTo;
   private boolean landed;
   private final Vec3[] shotAim = new Vec3[5];
   /** Éclosion : rejetons accrochés au cocon {angle, hauteur, sens, arrivée}, puis ceux qui redescendent au sol. */
   private final List<SpiderlingServeur> crawlers = new ArrayList<>();
   private final List<double[]> crawlerAt = new ArrayList<>();
   private final List<SpiderlingServeur> scatter = new ArrayList<>();
   private final List<Vec3[]> scatterTo = new ArrayList<>();
   private Vec3 pillarF;
   /** Les rejetons de la mort : chemin sur la paroi (du haut vers le pied) et ses normales, point au sol, point d'attente. */
   private final List<SpiderlingServeur> crew = new ArrayList<>();
   private final List<List<Vec3>> crewWall = new ArrayList<>();
   private final List<List<Vec3>> crewNorm = new ArrayList<>();
   private final List<Vec3> crewFloor = new ArrayList<>();
   private final List<Vec3> crewFront = new ArrayList<>();
   private int[] crewDown = new int[0], crewArrive = new int[0];
   private final Vec3[] crewMark = new Vec3[6];
   private Vec3 camSide = new Vec3(1, 0, 0);

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
   }

   private void setPhase(int p) {
      this.entityData.set(PHASE, p);
   }

   private static org.joml.Quaternionf qBasis(Vec3 x, Vec3 y, Vec3 z) {
      return new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y,
         (float) y.z, (float) z.x, (float) z.y, (float) z.z));
   }

   /** À plat, tête vers f (la même chose que la rotation normale d'un modèle de lacet f). */
   public static org.joml.Quaternionf qHeading(Vec3 f) {
      return qBasis(new Vec3(-f.z, 0, f.x), new Vec3(0, 1, 0), f.scale(-1));
   }

   /** Contre le pilier : tête en haut, son dos (retourné) contre le fût, qui est dans la direction f. */
   public static org.joml.Quaternionf qPillar(Vec3 f) {
      return qBasis(new Vec3(-f.z, 0, f.x), f.scale(-1), new Vec3(0, -1, 0));
   }

   private static Vec3 rot(org.joml.Quaternionf q, double x, double y, double z) {
      org.joml.Vector3f v = q.transform(new org.joml.Vector3f((float) x, (float) y, (float) z));
      return new Vec3(v.x, v.y, v.z);
   }

   /** Endormie dans son cocon : figée, invisible, invulnérable, sans IA, sans barre ni musique. */
   public void setDormant(boolean on) {
      this.entityData.set(DORMANT, on);
      if (on) {
         this.entityData.set(ACTION, Action.NONE.ordinal());
         this.setTarget(null);
         this.getNavigation().stop();
      }
   }

   public void setHome(BlockPos p, float yaw) {
      this.home = p;
      this.homeYaw = yaw;
   }

   public void setHall(BlockPos p) {
      this.hallPos = p;
   }

   public NestHallBEServeur hall() {
      if (hallPos == null || !this.level().isLoaded(hallPos)) return null;
      return this.level().getBlockEntity(hallPos) instanceof NestHallBEServeur h ? h : null;
   }

   /** Le rituel est fini : elle éclôt (l'animation « spawn » commence dans le cocon). */
   public void wake() {
      if (!dormant()) return;
      this.entityData.set(DORMANT, false);
      start(Action.SPAWN);
   }

   /** Plus personne : elle remonte dans son cocon, se soigne et se rendort. */
   public void sleep() {
      if (this.level().isClientSide || this.isDeadOrDying()) return;
      stopAction();
      bossBar.removeAllPlayers();
      this.setHealth(this.getMaxHealth());
      setPhase(1);
      threat.clear();
      webs.clear();
      wrapped.clear();
      discardBrood();
      peakPlayers = 1;
      this.noPhysics = false;
      if (home != null) {
         this.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
         faceYaw(homeYaw());
      }
      setDormant(true);
   }

   private float homeYaw() {
      return Float.isNaN(homeYaw) ? this.getYRot() : homeYaw;
   }

   private void start(Action a) {
      this.entityData.set(ACTION, a.ordinal());
      this.entityData.set(ACTION_TICK, clock() + 1);
      this.getNavigation().stop();
      pulled.clear();
   }

   private void stopAction() {
      this.entityData.set(ACTION, 0);
      this.noPhysics = false;
      this.setNoGravity(false);
      if (!this.isDeadOrDying()) this.entityData.set(ORIENT_ON, false);
      perches.clear();
   }

   private boolean idle() {
      return action() == Action.NONE;
   }

   /** Centre de l'arène : sous le cocon. */
   private Vec3 arena() {
      return home != null ? Vec3.atBottomCenterOf(home) : this.position();
   }

   // ------------------------------------------------------------------ cycle de vie
   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide && this.tickCount == 0 && !fromSave && home == null) {
         home = this.blockPosition();
         homeYaw = this.getYRot();
      }
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      ServerLevel level = (ServerLevel) this.level();
      if (dormant()) {
         this.setTarget(null);
         this.getNavigation().stop();
         if (home != null && this.position().distanceToSqr(Vec3.atBottomCenterOf(home)) > 0.3) this.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
         faceYaw(homeYaw());
         this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
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
         this.entityData.set(VIRTUAL_MAX, (int) (MereConfig.VIRTUAL_HP.get() * groupMult()));
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
            Action next = a == Action.ASCEND ? Action.LURK : a == Action.LURK ? Action.DROP : null;
            if (next != null) {
               start(next);
               return;
            }
            stopAction();
            pulled.clear();
            globalCd = phase() == 3 ? 3 : phase() == 2 ? 7 : 11;
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
      if (target == null) {
         this.getNavigation().stop();
         return;
      }
      this.getLookControl().setLookAt(target, 30F, 30F);
      double d = horizontal(target.position());
      turnToward(target.position(), 6F);
      if (globalCd == 0) {
         Action next = choose(target, d);
         if (next != null) {
            cooldowns[next.ordinal()] = cooldownOf(next);
            faceTo(target.position());
            start(next);
            return;
         }
      }
      if (d > 4.5) {
         double sp = phase() == 3 ? 1.55 : d > 16 ? 1.35 : 1.1;
         if (this.tickCount % 10 == 0 || this.getNavigation().isDone()) this.getNavigation().moveTo(target, sp);
      } else {
         this.getNavigation().stop();
      }
   }

   /** Le choix du coup (pondéré, selon la distance, la phase, et les tireurs qui restent loin). */
   private Action choose(LivingEntity target, double d) {
      int ph = phase();
      Player far = farthest(48);
      boolean sniped = far != null && far.distanceTo(this) > 22;
      // un tireur planqué derrière un mur ou un pilier : il le déloge (tirs en cloche, sauts, ondes qui passent par-dessus)
      boolean hid = target != null && !this.hasLineOfSight(target);
      return pick(
         Action.BITE, d < 6 ? 3F : 0F,
         Action.SWEEP, d < 8 ? 2.2F : 0F,
         Action.FRENZY, ph == 3 && d < 12 ? 2.5F : 0F,
         Action.WEBSHOT, hid ? 0.2F : d > 6 ? 2F : 0.6F,
         Action.PULL, sniped ? 3F : d > 12 ? 1.5F : 0F,
         Action.LEAP, hid ? 3.5F : sniped ? 2.2F : d > 14 ? 1.4F : 0F,
         Action.BROOD, brood.size() < 6 ? (hid ? 3F : 1.1F) : 0F,
         Action.EGGRAIN, hid ? 4F : ph >= 2 ? 1.6F : 0F,
         Action.COCOON, ph >= 2 && d > 5 ? 1.8F : 0F,
         Action.ASCEND, ph == 3 ? (sniped ? 2.8F : 1.2F) : 0F,
         Action.HOP, ph >= 2 ? (sniped ? 2.4F : 1.4F) : (sniped ? 1.2F : 0.4F),
         Action.BARRAGE, targets(40).size() >= 2 ? 2.2F : d > 6 ? (ph >= 2 ? 1.6F : 0.9F) : 0.3F);
   }

   private boolean ready(Action a) {
      return cooldowns[a.ordinal()] == 0;
   }

   private Action pick(Object... aw) {
      float total = 0;
      for (int i = 0; i < aw.length; i += 2) if (ready((Action) aw[i])) total += (Float) aw[i + 1];
      if (total <= 0) return null;
      float r = random.nextFloat() * total;
      for (int i = 0; i < aw.length; i += 2) {
         Action a = (Action) aw[i];
         float w = (Float) aw[i + 1];
         if (!ready(a) || w <= 0) continue;
         r -= w;
         if (r <= 0) return a;
      }
      return null;
   }

   private int cooldownOf(Action a) {
      boolean fast = phase() == 3;
      return switch (a) {
         case BITE -> 16;
         case SWEEP -> 50;
         case WEBSHOT -> fast ? 110 : 150;
         case BARRAGE -> fast ? 200 : 280;
         case PULL -> fast ? 200 : 260;
         case BROOD -> 420;
         case LEAP -> fast ? 170 : 230;
         case EGGRAIN -> 300;
         case FRENZY -> 200;
         case COCOON -> 260;
         case ASCEND -> 420;
         case HOP -> fast ? 360 : 520;
         default -> 0;
      };
   }

   // ------------------------------------------------------------------ les actions
   private void runAction(ServerLevel level, Action a, int t, LivingEntity target) {
      // les coups au corps à corps se donnent en avançant : elle fond sur sa cible pendant l'élan
      if (target != null && (a == Action.BITE && t < 10 || a == Action.SWEEP && t < 12)) {
         Vec3 d = target.position().subtract(this.position());
         double dist = Math.sqrt(d.x * d.x + d.z * d.z);
         if (dist > 3.4) {
            Vec3 step = new Vec3(d.x / dist, 0, d.z / dist).scale(Math.min(phase() == 3 ? 0.3 : 0.22, dist - 3.2));
            this.move(MoverType.SELF, new Vec3(step.x, -0.08, step.z));
         }
      }
      switch (a) {
         case SPAWN -> spawnSeq(level, t);
         case PHASE2 -> phase2(level, t);
         case PHASE3 -> phase3(level, t);
         case BITE -> bite(level, t, target, 12, 18);
         case SWEEP -> sweep(level, t, target);
         case WEBSHOT -> webshot(level, t, target);
         case BARRAGE -> barrage(level, t, target);
         case PULL -> pull(level, t, target);
         case BROOD -> {
            if (t == 1) sound(Sounds.SQUELCH.get(), 3F, 0.9F);
            if (t == 30) {
               hatch(level, surf(spinnerets().add(0, -3.5, 0)), Math.min(6, 2 + targets(40).size()));
               sound(Sounds.CHITTER.get(), 3F, 0.9F);
            }
         }
         case LEAP -> leap(level, t, target);
         case EGGRAIN -> eggrain(level, t);
         case FRENZY -> frenzy(level, t, target);
         case COCOON -> cocoonShot(level, t, target);
         case ASCEND -> {
            if (t == 12) {
               sound(Sounds.SILK.get(), 3.5F, 0.8F);
               Vfx.debris(level, surf(this.position()), 1.6F, 5F, 12, ground(this.position()), Vec3.ZERO);
               Vfx.silk(level, this.position().add(0, 40, 0), this.getId(), 1, 16);
            }
         }
         case LURK -> lurk(level, t, target);
         case HOP -> hop(level, t, target);
         case DROP -> drop(level, t);
         default -> {
         }
      }
   }

   private void screech(ServerLevel level) {
      sound(Sounds.SCREECH.get(), 5F, 0.95F + random.nextFloat() * 0.1F);
      Vfx.shake(level, this.position(), 0.9F, 40, 20);
   }

   /** Éclosion (18 s, animation « spawn ») : le cocon bat, des rejetons descendent des grappes et s'y accrochent ; une patte
    *  perce la soie (3,3 s), l'autre (4,1 s), les deux (4,6 s) ; le cocon se déchire (4,95 s) : elle en glisse, descend lentement
    *  au bout de son fil en tournant, s'arrête face à nous, lâche le fil, se retourne en tombant et s'écrase (13 s) ; se dresse et
    *  hurle ; abat ses pattes (16,45 s). */
   private void spawnSeq(ServerLevel level, int t) {
      Vec3 o = surf(this.position());
      Vec3 cc = o.add(0, COCOON_C, 0);
      NestHallBEServeur h = hall();
      faceYaw(homeYaw());
      if (t == 1) {
         if (MereConfig.CINEMATICS.get()) Vfx.cinematic(level, o, this.getId(), SPAWN_K, Action.SPAWN.ticks, Vec3.directionFromRotation(0, homeYaw()));
         Vfx.silk(level, o.add(0, COCOON_C - COCOON_HH * 0.2, 0), this.getId(), 2, SP_RELEASE);          // du fond crevé du cocon
         sound(Sounds.CHITTER.get(), 3F, 0.7F);
         crawlers.clear();
         crawlerAt.clear();
         if (h != null) h.cocoonTear(level);                                   // l'animation « s2 » du cocon : il se déchire à 4,95 s
      }
      if (t < SP_TEAR) {
         for (int b : SP_BEATS) {
            if (t != b) continue;
            level.playSound(null, cc.x, cc.y, cc.z, Sounds.EGG_PULSE.get(), SoundSource.HOSTILE, 3.5F, 0.55F + t * 0.002F);
            level.sendParticles(WEB_FX, cc.x, cc.y - COCOON_HH * 0.5, cc.z, 4, 1.6, 1.2, 1.6, 0.01);
         }
         if (t % 25 == 5) level.playSound(null, cc.x, cc.y, cc.z, Sounds.SILK_STRAIN.get(), SoundSource.HOSTILE, 2.2F, 0.6F + t * 0.003F);
      }
      List<BlockPos> clusters = h != null ? h.clusters(level) : List.of();
      if (t >= 1 && t <= 11 && (t - 1) % 2 == 0 && !clusters.isEmpty()) {         // des rejetons viennent s'accrocher au cocon
         int k = (t - 1) / 2;
         BlockPos cp = clusters.get(k % clusters.size());
         double a = Math.toRadians(RIPS[k % RIPS.length][0]) + (k % 2 == 0 ? 0.5 : -0.5), hh = RIPS[k % RIPS.length][1] + 2.2;
         SpiderlingServeur sp = dropFrom(level, cp, cocoonPoint(cc, a, hh), 14, true);
         if (sp != null) {
            crawlers.add(sp);
            crawlerAt.add(new double[] {a, hh, k % 2 == 0 ? 1 : -1, t + 15, k});
         }
      }
      // les rejetons rampent jusqu'à leur trou, mordent la soie (elle cède à RIP_TICK), puis tirent sur les lambeaux
      for (int i = 0; i < crawlers.size() && t < SP_TEAR; i++) {
         SpiderlingServeur sp = crawlers.get(i);
         double[] c = crawlerAt.get(i);
         if (!sp.isAlive() || t < c[3]) continue;
         int k = (int) c[4] % RIPS.length;
         double ra = Math.toRadians(RIPS[k][0]), rh = RIPS[k][1];
         int rip = RIP_TICK[k];
         double go = Math.max(0, 1 - (t - c[3]) / 8.0);
         double goA = ra + (c[0] - ra) * go, goH = rh + 0.9 + (c[1] - rh - 0.9) * go;
         Vec3 n = new Vec3(Math.cos(goA), 0, Math.sin(goA));
         Vec3 at = cocoonPoint(cc, goA, goH);
         Vec3 look = cocoonPoint(cc, ra, rh).subtract(at).add(n.scale(-0.3));
         int anim = t - c[3] < 8 ? Spiderling.A_WALK : Spiderling.A_HOLD;
         if (t >= rip - 12 && t < rip) {                                        // elle mord : la tête plonge dans la soie, par saccades
            double j = Math.max(0, Math.sin((t - rip) * 1.9));
            at = at.subtract(n.scale(0.22 * j)).add(0, -0.12 * j, 0);
            anim = Spiderling.A_WALK;
            if (t % 3 == 0) {
               Vec3 hp = cocoonPoint(cc, ra, rh);
               level.sendParticles(WEB_FX, hp.x, hp.y, hp.z, 3, 0.15, 0.15, 0.15, 0.02);
               level.playSound(null, hp.x, hp.y, hp.z, Sounds.CHITTER.get(), SoundSource.HOSTILE, 1.2F, 1.5F + random.nextFloat() * 0.3F);
            }
         } else if (t >= rip) {                                                 // elle recule en tirant un lambeau
            double pull = Math.min(1, (t - rip) / 6.0);
            at = at.add(n.scale(0.35 * pull + 0.08 * Math.sin(t * 1.3 + i))).add(0, 0.25 * pull, 0);
         }
         if (t == rip) {
            Vec3 hp = cocoonPoint(cc, ra, rh);
            level.playSound(null, hp.x, hp.y, hp.z, Sounds.COCOON_TEAR.get(), SoundSource.HOSTILE, 3F, 1.2F + random.nextFloat() * 0.2F);
            level.sendParticles(WEB_FX, hp.x, hp.y, hp.z, 24, 0.3, 0.4, 0.3, 0.12);
            level.sendParticles(GOO_FX, hp.x, hp.y - 0.3, hp.z, 6, 0.2, 0.2, 0.2, 0.05);
            Vfx.shake(level, o, 0.25F, 20, 5);
         }
         sp.puppet(at, n, look, anim);
      }
      if (t == SP_TEAR) {                                                      // le cocon se déchire
         level.playSound(null, o.x, o.y + COCOON_Y, o.z, Sounds.COCOON_TEAR.get(), SoundSource.HOSTILE, 3.5F, 0.7F);
         level.sendParticles(WEB_FX, o.x, o.y + COCOON_Y, o.z, 30, 1.4, 0.4, 1.4, 0.02);
         level.sendParticles(Particles.MIST.get(), o.x, o.y + COCOON_Y, o.z, 20, 1.4, 0.4, 1.4, 0.02);
         scatter.clear();
         scatterTo.clear();
         Vec3 f = fwd();
         for (SpiderlingServeur sp : crawlers) {
            if (!sp.isAlive()) continue;
            Vec3 out = sp.position().subtract(cc).multiply(1, 0, 1).normalize();
            Vec3 down = surf(sp.position().add(out.scale(1.5)));
            int land = t + 30 + random.nextInt(12);
            sp.hangDown(sp.position(), down, land - t, true);
            Vec3 away = out.subtract(f.scale(1.5)).multiply(1, 0, 1).normalize();        // vers le fond : pas devant la caméra
            Vec3 to = surf(o.add(away.scale(10 + random.nextDouble() * 3)));
            scatter.add(sp);
            scatterTo.add(new Vec3[] {down, to, new Vec3(land, 0, 0)});
            brood.add(sp);
         }
         crawlers.clear();
         crawlerAt.clear();
      }
      for (int i = 0; i < scatter.size(); i++) {
         SpiderlingServeur sp = scatter.get(i);
         Vec3[] w = scatterTo.get(i);
         int land = (int) w[2].x;
         if (!sp.isAlive() || t < land) continue;
         if (t >= Action.SPAWN.ticks - 12) {
            sp.release();
            continue;
         }
         double u = Mth.clamp((t - land) / 45.0, 0, 1);
         Vec3 q = w[0].lerp(w[1], u * u * (3 - 2 * u));
         Vec3 p = new Vec3(q.x, groundY(level, q.x, q.y + 1.5, q.z), q.z);
         sp.puppet(p, new Vec3(0, 1, 0), u < 0.98 ? w[1].subtract(w[0]) : o.subtract(p), u < 0.98 ? Spiderling.A_RUN : Spiderling.A_HOLD);
      }
      if (t == 130 || t == 165 || t == 200) sound(Sounds.SILK.get(), 2.2F, 0.8F + (t - 100) * 0.002F);
      if (t > SP_TEAR && t < SP_RELEASE && t % 6 == 0) {
         double hh = spawnBodyHeight(t / 20.0);
         level.sendParticles(WEB_FX, o.x, o.y + hh + 2, o.z, 2, 0.5, 0.6, 0.5, 0.01);
      }
      if (t == 224) sound(Sounds.HISS.get(), 3F, 0.7F);
      if (t == 238) sound(Sounds.CHITTER.get(), 3F, 0.6F);
      if (t == SP_RELEASE) {
         sound(Sounds.SILK.get(), 4F, 1.3F);
         sound(Sounds.HISS.get(), 3F, 0.9F);
      }
      if (t == SP_LAND) {
         Vfx.debris(level, o, 2.6F, 7F, 32, ground(o), Vec3.ZERO);
         Vfx.shake(level, o, 1.2F, 40, 16);
         Vfx.flash(level, o.add(0, 1, 0), 4F, 5, 0xE8D8C0);
         addWeb(o, 5.5F, 200);
         sound(Sounds.LAND.get(), 5F, 0.9F);
      }
      if (t == SP_SCREAM) {
         screech(level);
         if (h != null) h.motherScreams(level);
      }
      if (t == SP_SCREAM + 24) sound(Sounds.SCREECH.get(), 4F, 0.8F);
      if (t == SP_SLAM) {
         Vfx.debris(level, o.add(fwd().scale(2.5)), 1.8F, 5F, 18, ground(o), Vec3.ZERO);
         Vfx.shake(level, o, 0.8F, 30, 12);
         sound(Sounds.LAND.get(), 3F, 1.2F);
      }
      if (t >= Action.SPAWN.ticks - 1) {
         for (SpiderlingServeur sp : scatter) if (sp.isAlive()) sp.release();
         scatter.clear();
         setPhase(1);
      }
   }

   /** Point à la surface du cocon (angle a, hauteur h par rapport à son centre), un peu à l'extérieur. */
   private static Vec3 cocoonPoint(Vec3 cc, double a, double h) {
      double r = cocoonRadius(Mth.clamp(h, -COCOON_HH * 0.95, COCOON_HH * 0.95)) + 0.45;
      return cc.add(Math.cos(a) * r, h, Math.sin(a) * r);
   }

   private void phase2(ServerLevel level, int t) {
      if (t == 1) {
         faceYaw(stageYaw());
         if (MereConfig.CINEMATICS.get()) Vfx.cinematic(level, this.position(), this.getId(), PHASE2_K, Action.PHASE2.ticks, fwd());
         sound(Sounds.HURT.get(), 4F, 0.8F);
      }
      if (t == 8 || t == 20) sound(Sounds.SQUELCH.get(), 4F, t == 8 ? 0.8F : 1F);
      if (t >= 52 && t < 100 && t % 12 == 4) sound(Sounds.SQUELCH.get(), 3F, 0.7F + (t - 52) * 0.008F);
      if (t == 100) {
         setPhase(2);
         Vec3 s = spinnerets();
         Vfx.flash(level, s, 4F, 6, 0xE8C8A8);
         level.sendParticles(BLOOD_FX, s.x, s.y, s.z, 60, 1, 1, 1, 0.2);
         level.sendParticles(GOO_FX, s.x, s.y, s.z, 30, 1, 1, 1, 0.2);
         hatch(level, surf(s), 3);
         sound(Sounds.BURST.get(), 5F, 0.9F);
         for (int i = 0; i < 6; i++) {
            double a = i * Mth.TWO_PI / 6;
            addWeb(surf(this.position().add(Math.cos(a) * 7, 0, Math.sin(a) * 7)), 2.6F, 240);
         }
         sound(Sounds.GOO.get(), 3F, 0.5F);
         NestHallBEServeur h = hall();
         if (h != null) h.phase2(level);
      }
      if (t == 106) screech(level);
   }

   private void phase3(ServerLevel level, int t) {
      if (t == 1) {
         faceYaw(stageYaw());
         if (MereConfig.CINEMATICS.get()) Vfx.cinematic(level, this.position(), this.getId(), PHASE3_K, Action.PHASE3.ticks, fwd());
         sound(Sounds.HURT.get(), 4F, 0.7F);
      }
      if (t == 10) {
         Vec3 o = surf(this.position());
         Vfx.debris(level, o, 2.5F, 6F, 24, ground(o), Vec3.ZERO);
         Vfx.shake(level, o, 1.0F, 30, 12);
         addWeb(o, 8F, 300);
      }
      if (t == 40 || t == 60) sound(Sounds.HISS.get(), 2F, 1.3F);
      if (t == 100) {
         setPhase(3);
         screech(level);
         sound(Sounds.HISS.get(), 4F, 0.7F);
         Vfx.flash(level, mouth(), 5F, 8, 0xFFF0C0);
         NestHallBEServeur h = hall();
         if (h != null) {                                                     // la voûte lui répond : un rejeton par grappe
            h.phase3(level);
            int n = 0;
            for (BlockPos cp : h.clusters(level)) {
               if (n++ >= 6) break;
               SpiderlingServeur sp = dropFrom(level, cp, surf(clusterBottom(cp)), 30 + random.nextInt(20), false);
               if (sp != null) brood.add(sp);
            }
         }
         for (int i = 0; i < 8; i++) {
            double a = i * Mth.TWO_PI / 8;
            addWeb(surf(this.position().add(Math.cos(a) * 9, 0, Math.sin(a) * 9)), 2.4F, 300);
         }
         Vfx.shockwave(level, surf(this.position()), 14F, 2F, 22);
      }
   }

   /** Les transitions de phase se jouent tournées vers le centre de l'arène : la caméra a de la place devant elle. */
   private float stageYaw() {
      Vec3 d = arena().subtract(this.position());
      d = new Vec3(d.x, 0, d.z);
      if (d.lengthSqr() < 16) return this.getYRot();
      return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
   }

   /** Morsure : elle siffle, puis ses crochets frappent devant elle (cône de 5,5 blocs, 100°) : dégâts, poison, recul. */
   private void bite(ServerLevel level, int t, LivingEntity target, int hitAt, float damage) {
      if (t < hitAt - 3 && target != null) faceTo(target.position());
      if (t == hitAt - 6) sound(Sounds.HISS.get(), 3F, 1.1F);
      if (t == hitAt) {
         sound(Sounds.BITE.get(), 3.5F, 0.9F + random.nextFloat() * 0.2F);
         for (Player p : inCone(5.5, 50)) {
            hurtP(p, damage);
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1, false, false));
            Vec3 d = flat(p.position().subtract(this.position()));
            p.setDeltaMovement(d.x * 0.8, 0.35, d.z * 0.8);
            p.hurtMarked = true;
         }
      }
   }

   /** Balayage des pattes avant : cône annoncé (7,5 blocs, 160°), puis un coup qui projette loin. */
   private void sweep(ServerLevel level, int t, LivingEntity target) {
      if (t < 8 && target != null) faceTo(target.position());
      if (t == 3) Vfx.cone(level, this.position(), fwd(), 7.5F, 160F, 11);
      if (t == 14) {
         sound(Sounds.SWIPE.get(), 2.5F, 0.5F);
         for (Player p : inCone(7.5, 80)) {
            hurtP(p, 16);
            Vec3 d = flat(p.position().subtract(this.position()));
            p.setDeltaMovement(d.x * 1.6, 0.5, d.z * 1.6);
            p.hurtMarked = true;
         }
      }
   }

   /** Barrage de soie : dressée sur ses pattes arrière, elle crache six boules de soie en éventail, puis retombe de tout son poids. */
   private void barrage(ServerLevel level, int t, LivingEntity target) {
      if (t < 16 && target != null) faceTo(target.position());
      if (t == 4) sound(Sounds.SCREECH.get(), 3.5F, 1.2F);
      if (t >= 18 && t <= 33 && (t - 18) % 3 == 0) {
         int k = (t - 18) / 3;
         double yaw = Math.toRadians(-40 + k * 16);
         Vec3 dir = fwd().yRot((float) yaw);
         Vec3 aim = surf(this.position().add(dir.scale(11 + random.nextDouble() * 5)));
         for (Player p : targets(28)) {
            Vec3 fl = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            if (fl.lengthSqr() > 1e-3 && dir.dot(fl.normalize()) > Math.cos(Math.toRadians(9))) aim = surf(p.position());
         }
         Vec3 from = spinnerets();
         int flight = (int) Mth.clamp(from.distanceTo(aim) * 0.8, 10, 26);
         WebGlobServeur g = new WebGlobServeur(level, this, 0);
         int lf = g.launch(from, aim, flight);
         level.addFreshEntity(g);
         Vfx.circle(level, aim, 3.2F, lf);
         sound(Sounds.SILK.get(), 2.5F, 1.1F + k * 0.05F);
      }
      if (t == 34) Vfx.circle(level, this.position(), 6.5F, 6);
      if (t == 40) {
         Vec3 o = surf(this.position());
         Vfx.shockwave(level, o, 7F, 1.4F, 12);
         Vfx.shake(level, o, 1.0F, 30, 10);
         Vfx.debris(level, o, 2.5F, 5F, 16, ground(o), Vec3.ZERO);
         sound(Sounds.LAND.get(), 4F, 0.9F);
         hitCircle(level, o, 6.5, 12, 1.6, 0.6);
      }
   }

   /** Globes de toile en cloche (3, puis un de plus par phase ; le premier sur le tireur le plus éloigné) : plaques gluantes. */
   private void webshot(ServerLevel level, int t, LivingEntity target) {
      if (t < 14 && target != null) faceTo(target.position());
      if (t == 6) {
         List<Player> ps = targets(44);
         Player far = farthest(44);
         for (int i = 0; i < shotAim.length; i++) {
            if (i >= 2 + phase()) {
               shotAim[i] = null;
               continue;
            }
            LivingEntity who = i == 0 && far != null ? far : !ps.isEmpty() ? ps.get(random.nextInt(ps.size())) : target;
            shotAim[i] = who == null ? null : surf(who.position().add(who.getDeltaMovement().multiply(14, 0, 14)).add(
               (random.nextDouble() - 0.5) * (i == 0 ? 1 : 5), 0, (random.nextDouble() - 0.5) * (i == 0 ? 1 : 5)));
         }
      }
      for (int i = 0; i < shotAim.length; i++) {
         if (t != 16 + i * 4 || shotAim[i] == null) continue;
         Vec3 from = spinnerets();
         int flight = (int) Mth.clamp(from.distanceTo(shotAim[i]) * 0.8, 12, 30);
         WebGlobServeur g = new WebGlobServeur(level, this, 0);
         int lf = g.launch(from, shotAim[i], flight);
         level.addFreshEntity(g);
         Vfx.circle(level, shotAim[i], 3.2F, lf);
         sound(Sounds.SPIT.get(), 3.5F, 0.9F + random.nextFloat() * 0.2F);
      }
   }

   /** Harpon de soie : visée (fil rouge qui suit la cible), tir ; si la ligne est dégagée, la proie est ramenée jusqu'aux crochets
    *  et mordue. On l'évite en cassant la ligne de vue (colonne, relief). */
   private void pull(ServerLevel level, int t, LivingEntity target) {
      if (t == 0 || t == 1 && pulled.isEmpty()) {
         pulled.clear();
         List<Player> ps = targets(40);
         ps.sort((x, y) -> Double.compare(y.distanceTo(this), x.distanceTo(this)));
         for (Player p : ps) if (pulled.size() < (phase() == 3 ? 2 : 1) && p.distanceTo(this) > 6) pulled.add(p);
         if (pulled.isEmpty() && target instanceof Player tp) pulled.add(tp);
      }
      if (pulled.isEmpty()) return;
      if (t < 16) faceTo(pulled.get(0).position());
      if (t == 4) {
         for (Player p : pulled) {
            Vfx.silk(level, mouth(), p.getId(), 0, 12);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), Sounds.SILK.get(), SoundSource.HOSTILE, 2.5F, 1.3F);
         }
      }
      if (t == 16) {
         Vec3 from = mouth();
         sound(Sounds.SILK.get(), 4F, 0.9F);
         pulled.removeIf(p -> {
            Vec3 eye = p.getEyePosition();
            HitResult hr = level.clip(new ClipContext(from, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hr.getType() != HitResult.Type.MISS || from.distanceTo(eye) > 40) {
               Vfx.silk(level, from, 1, 30, hr.getLocation());                  // la soie se plante dans l'obstacle
               return true;
            }
            Vfx.silk(level, from, p.getId(), 1, 26);
            hurtP(p, 4);
            return false;
         });
      }
      if (t > 16 && t < 43) {
         pulled.removeIf(p -> !p.isAlive());
         for (int i = 0; i < pulled.size(); i++) {
            Player p = pulled.get(i);
            Vec3 dest = this.position().add(fwd().scale(3.2)).add(right().scale(i == 0 ? 0 : 2.5)).add(0, 0.2, 0);
            Vec3 d = dest.subtract(p.position());
            if (t >= 38 || d.length() < 0.8) {
               if (d.length() > 0.2) p.teleportTo(dest.x, Math.max(dest.y, p.getY() - 0.3), dest.z);
               p.setDeltaMovement(Vec3.ZERO);
            } else {
               Vec3 v = d.normalize().scale(Mth.clamp(d.length() / Math.max(1, 38 - t), 0.35, 1.9));
               p.setDeltaMovement(v.x, Math.max(0.08, v.y + 0.06), v.z);
            }
            p.hurtMarked = true;
            p.fallDistance = 0;
         }
      }
      if (t == 42) bite(level, 42, null, 42, 20);
   }

   /** Paquet de soie (phase 2+) : fil de visée, puis la soie ; la victime est emmaillotée (immobile, aveugle, empoisonnée). */
   private void cocoonShot(ServerLevel level, int t, LivingEntity target) {
      if (t == 0 || t == 1 && prey == null) {
         Player far = farthest(36);
         prey = far != null ? far : target instanceof Player tp ? tp : null;
      }
      if (prey == null || !prey.isAlive()) return;
      if (t < 14) faceTo(prey.position());
      if (t == 2) Vfx.silk(level, spinnerets(), prey.getId(), 0, 14);
      if (t == 16) {
         Vec3 from = spinnerets();
         Vec3 aim = prey.position().add(prey.getDeltaMovement().multiply(6, 0, 6)).add(0, 0.8, 0);
         WebGlobServeur g = new WebGlobServeur(level, this, 2);
         g.launch(from, aim, (int) Mth.clamp(from.distanceTo(aim) * 0.45, 8, 18));
         level.addFreshEntity(g);
         sound(Sounds.SPIT.get(), 4F, 0.8F);
      }
      if (t >= Action.COCOON.ticks - 1) prey = null;
   }

   /** Un joueur pris dans le cocon : 3 s immobile et aveugle, soie enroulée autour de lui. */
   public void wrap(ServerLevel level, Player p) {
      wrapped.put(p, new double[] {p.getX(), p.getY(), p.getZ(), this.tickCount + 60});
      hurtP(p, 6);
      p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 128, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1, false, false));
      Vfx.screen(p, 0.9F, 0F, 60);
      Vfx.silk(level, p.position(), p.getId(), 3, 60);
      level.playSound(null, p.getX(), p.getY(), p.getZ(), Sounds.COCOON.get(), SoundSource.HOSTILE, 2.5F, 1F);
   }

   /** Là-haut sur son fil : son ombre de toile suit le tireur le plus éloigné, se fige, puis elle tombe. */
   private void lurk(ServerLevel level, int t, LivingEntity target) {
      if (t == 0 || t == 1 && dropAt == null) {
         Player far = farthest(44);
         prey = far != null ? far : target instanceof Player tp ? tp : null;
         dropAt = surf(this.position());
      }
      if (prey != null && prey.isAlive() && t <= 26) dropAt = underDome(inLeash(surf(prey.position())));
      if (t <= 26 && t % 3 == 0) Vfx.web(level, dropAt, 3.5F, 8);
      Vec3 cur = this.position();
      Vec3 step = dropAt.subtract(cur);
      double sp = Math.min(0.6, step.length());
      Vec3 nx = step.lengthSqr() < 1e-4 ? cur : cur.add(step.normalize().scale(sp));
      this.teleportTo(nx.x, dropAt.y, nx.z);
      if (t % 4 == 0) Vfx.silk(level, nx.add(0, 40, 0), 1, 6, nx.add(0, 200 / 16.0 * RS + 2.5, 0));
      if (t == 27) {
         Vfx.circle(level, dropAt, 5.5F, 13);
         Vfx.silk(level, dropAt.add(0, 30, 0), 1, 13, dropAt);
         level.playSound(null, dropAt.x, dropAt.y + 12, dropAt.z, Sounds.HISS.get(), SoundSource.HOSTILE, 4F, 0.7F);
      }
      if (t >= Action.LURK.ticks - 1) prey = null;
   }

   // ------------------------------------------------------------------ de fût en fût
   private final List<Vec3[]> perches = new ArrayList<>();
   private Vec3 hopFrom;
   private org.joml.Quaternionf hopQ0;
   private static final int HOP_JUMP = 18, HOP_HOLD = 36, HOP_N = 3;

   /** Elle bondit sur un fût, s'y accroche en travers (le dos vers la salle, la tête vers la voûte), crache sa soie de là-haut,
    *  bondit sur un autre fût, puis retombe près de sa proie. Il faut viser juste : elle ne reste jamais longtemps. */
   private void hop(ServerLevel level, int t, LivingEntity target) {
      if (t <= 1 && perches.isEmpty()) {
         List<Vec3> pcs = pillarCenters(level, this.position());
         Vec3 ref = target != null ? target.position() : this.position();
         pcs.removeIf(pc -> Math.hypot(pc.x - getX(), pc.z - getZ()) > 26);
         Vec3 last = null;
         for (int k = 0; k < HOP_N && !pcs.isEmpty(); k++) {
            Vec3 pc = null;
            double best = 1e9;
            for (Vec3 c : pcs) {
               if (last != null && Math.hypot(c.x - last.x, c.z - last.z) < 5) continue;
               double sc = Math.hypot(c.x - ref.x, c.z - ref.z) + random.nextDouble() * 8;
               if (sc < best) {
                  best = sc;
                  pc = c;
               }
            }
            if (pc == null) break;
            pcs.remove(pc);
            last = pc;
            Vec3 out = new Vec3(ref.x - pc.x, 0, ref.z - pc.z);
            out = out.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : out.normalize();
            double fy = gy(level, pc.x + out.x * (PILLAR_R + 1), this.getY() + 1, pc.z + out.z * (PILLAR_R + 1));
            Vec3 at = new Vec3(pc.x + out.x * (PILLAR_R + 0.3), fy + 5.5 + random.nextDouble() * 2, pc.z + out.z * (PILLAR_R + 0.3));
            perches.add(new Vec3[] {at, out});
         }
         if (perches.isEmpty()) {
            stopAction();
            return;
         }
         hopFrom = this.position();
         hopQ0 = qHeading(fwd());
         this.noPhysics = true;
         this.setNoGravity(true);
         sound(Sounds.HISS.get(), 3.5F, 0.8F);
      }
      if (perches.isEmpty()) return;
      int n = perches.size();
      int seg = t / (HOP_JUMP + HOP_HOLD), u = t % (HOP_JUMP + HOP_HOLD);
      this.setDeltaMovement(Vec3.ZERO);
      if (seg < n) {
         Vec3[] pr = perches.get(seg);
         org.joml.Quaternionf cling = Spiderling.basis(pr[1], new Vec3(0, 1, 0));
         if (u < HOP_JUMP) {                                                         // le bond, en cloche
            double k = (u + 1) / (double) HOP_JUMP, e = k * k * (3 - 2 * k);
            Vec3 a = hopFrom, b = pr[0];
            Vec3 p = a.lerp(b, e).add(0, (2.5 + 0.1 * a.distanceTo(b)) * 4 * k * (1 - k), 0);
            this.setPos(p.x, p.y, p.z);
            this.entityData.set(ORIENT_ON, true);
            this.entityData.set(ORIENT, new org.joml.Quaternionf(hopQ0).slerp(cling, (float) e));
            if (u == 0) sound(Sounds.SWOOP.get(), 3.5F, 1F);
            if (u == HOP_JUMP - 1) {                                                // elle s'agrippe : la pierre s'effrite sous ses pattes
               sound(Sounds.LAND.get(), 3F, 1.3F);
               sound(Sounds.STEP.get(), 3F, 0.7F);
               level.sendParticles(STONE_FX, p.x, p.y + 1, p.z, 30, 1.2, 1.4, 1.2, 0.08);
               Vfx.shake(level, p, 0.4F, 26, 6);
            }
         } else {                                                                    // accrochée en travers du fût, elle crache
            Vec3 p = pr[0];
            this.setPos(p.x, p.y, p.z);
            this.entityData.set(ORIENT_ON, true);
            this.entityData.set(ORIENT, cling);
            int h = u - HOP_JUMP;
            if (h == 4) sound(Sounds.CHITTER.get(), 3F, 0.9F);
            if (h == 10 || h == 22 || phase() == 3 && h == 30) {
               Player who = !targets(44).isEmpty() ? targets(44).get(random.nextInt(targets(44).size())) : null;
               if (who != null) {
                  Vec3 from = p.add(pr[1].scale(2.2)).add(0, 1.5, 0);
                  Vec3 aim = surf(who.position().add(who.getDeltaMovement().multiply(10, 0, 10)));
                  WebGlobServeur g = new WebGlobServeur(level, this, h == 22 && phase() >= 2 ? 1 : 0);
                  int lf = g.launch(from, aim, (int) Mth.clamp(from.distanceTo(aim) * 0.7, 10, 24));
                  level.addFreshEntity(g);
                  Vfx.circle(level, aim, 3F, lf);
                  sound(Sounds.SPIT.get(), 3.5F, 0.9F + random.nextFloat() * 0.2F);
               }
            }
            if (u == HOP_JUMP + HOP_HOLD - 1) {
               hopFrom = p;
               hopQ0 = cling;
            }
         }
         return;
      }
      // la chute finale, près de sa proie
      int u2 = t - n * (HOP_JUMP + HOP_HOLD);
      if (u2 == 0) {
         Vec3 ref = target != null ? target.position().add(flat(hopFrom.subtract(target.position())).scale(4)) : arena();
         leapTo = inLeash(surf(ref));
         sound(Sounds.SWOOP.get(), 4F, 0.8F);
         Vfx.circle(level, leapTo, 5F, 20);
      }
      if (leapTo == null) leapTo = surf(arena());
      double k = Mth.clamp((u2 + 1) / 20.0, 0, 1), e = k * k;
      Vec3 p = hopFrom.lerp(leapTo, e).add(0, 2 * 4 * k * (1 - k), 0);
      this.setPos(p.x, p.y, p.z);
      Vec3 dir = flat(leapTo.subtract(hopFrom));
      this.entityData.set(ORIENT, new org.joml.Quaternionf(hopQ0).slerp(qHeading(dir), (float) Math.min(1, k * 1.4)));
      if (u2 >= 19) {
         this.entityData.set(ORIENT_ON, false);
         faceTo(p.add(dir));
         this.noPhysics = false;
         this.setNoGravity(false);
         Vfx.debris(level, leapTo, 2.5F, 7F, 26, ground(leapTo), Vec3.ZERO);
         Vfx.shake(level, leapTo, 1.2F, 36, 16);
         addWeb(leapTo, 5F, 160);
         sound(Sounds.LAND.get(), 4F, 1F);
         for (Player pl : hitCircle(level, leapTo, 5, 18, 1.2, 0.6)) pl.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1, false, false));
         perches.clear();
         leapTo = null;
         stopAction();
      }
   }

   /** Point de chute ramené sous le haut du dôme (au bord, la voûte est trop basse pour qu'elle y pende). */
   private Vec3 underDome(Vec3 p) {
      if (home == null) return p;
      Vec3 h = Vec3.atBottomCenterOf(home);
      Vec3 d = new Vec3(p.x - h.x, 0, p.z - h.z);
      if (d.length() <= 17) return p;
      Vec3 q = h.add(d.normalize().scale(17));
      return surf(new Vec3(q.x, p.y, q.z));
   }

   /** Chute (après LURK) : elle s'écrase : débris, secousse, toile, poison autour d'elle, puis hurlement. */
   private void drop(ServerLevel level, int t) {
      if (t == 10) {
         Vec3 at = surf(this.position());
         Vfx.debris(level, at, 2.6F, 8F, 30, ground(at), Vec3.ZERO);
         Vfx.shake(level, at, 1.4F, 40, 18);
         addWeb(at, 6F, 200);
         sound(Sounds.LAND.get(), 5F, 1F);
         for (Player p : hitCircle(level, at, 5.5, 26, 1.3, 0.8)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1, false, false));
         dropAt = null;
      }
      if (t == 16) screech(level);
   }

   /** Bond sur le tireur le plus éloigné : toile annoncée au sol, réception en cercle, plaque gluante. */
   private void leap(ServerLevel level, int t, LivingEntity target) {
      if (t == 0 || t == 1 && leapTo == null) {
         Player far = farthest(36);
         LivingEntity who = far != null ? far : target;
         leapFrom = this.position();
         leapTo = inLeash(who != null ? surf(who.position().add(who.getDeltaMovement().multiply(12, 0, 12))) : surf(this.position().add(fwd().scale(10))));
         if (leapTo.distanceTo(leapFrom) > 28) leapTo = inLeash(surf(leapFrom.add(leapTo.subtract(leapFrom).normalize().scale(28))));
         landed = false;
         Vfx.circle(level, leapTo, 5F, 30);
         sound(Sounds.HISS.get(), 3.5F, 0.9F);
      }
      if (leapTo == null) return;
      if (t < 14) faceTo(leapTo);
      if (t == 14) {
         Vfx.debris(level, surf(this.position()), 1.6F, 5F, 12, ground(this.position()), Vec3.ZERO);
         sound(Sounds.SWOOP.get(), 4F, 0.9F);
      }
      if (t >= 14 && t < 30) {                                                 // la cloche du bond
         double u = (t - 14 + 1) / 16.0, hgt = 5 + 0.15 * leapFrom.distanceTo(leapTo);
         Vec3 p = leapFrom.lerp(leapTo, u).add(0, hgt * 4 * u * (1 - u), 0);
         this.noPhysics = true;
         this.setNoGravity(true);
         this.setPos(p.x, p.y, p.z);
         this.setDeltaMovement(Vec3.ZERO);
      }
      if (t == 30) {
         this.noPhysics = false;
         this.setNoGravity(false);
      }
      if (!landed && t >= 30) {
         landed = true;
         Vec3 at = surf(this.position());
         Vfx.debris(level, at, 2.5F, 7F, 26, ground(at), Vec3.ZERO);
         Vfx.shake(level, at, 1.2F, 36, 16);
         addWeb(at, 5F, 160);
         sound(Sounds.LAND.get(), 4F, 1.15F);
         for (Player p : hitCircle(level, at, 5, 22, 1.2, 0.7)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1, false, false));
      }
      if (t >= Action.LEAP.ticks - 1) leapTo = null;
   }

   /** Pluie de cocons (phase 2+) : un par joueur (+2), annoncés au sol ; touchés, ils empoisonnent, ignorés, ils éclosent. */
   private void eggrain(ServerLevel level, int t) {
      if (t == 20) {
         List<Vec3> aims = new ArrayList<>();
         for (Player p : targets(40)) if (aims.size() < 6) aims.add(surf(p.position().add(p.getDeltaMovement().multiply(20, 0, 20))));
         for (int i = 0; i < 2; i++) aims.add(surf(this.position().add((random.nextDouble() - 0.5) * 18, 0, (random.nextDouble() - 0.5) * 18)));
         Vec3 from = spinnerets().add(0, 1, 0);
         for (Vec3 aim : aims) {
            WebGlobServeur g = new WebGlobServeur(level, this, 1);
            int lf = g.launch(from, aim, 22 + random.nextInt(6));
            level.addFreshEntity(g);
            Vfx.circle(level, aim, 2.8F, lf);
         }
         sound(Sounds.SPIT.get(), 4F, 0.7F);
         sound(Sounds.SQUELCH.get(), 3F, 1.1F);
      }
   }

   /** Frénésie (phase 3) : trois morsures en fondant sur la cible. */
   private void frenzy(ServerLevel level, int t, LivingEntity target) {
      int k = t % 20;
      if (target != null && k < 8) faceTo(target.position());
      if (k >= 4 && k < 12 && t < 60 && target != null) {
         Vec3 d = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
         if (d.length() > 3) {
            d = d.normalize().scale(0.55);
            this.move(MoverType.SELF, new Vec3(d.x, -0.08, d.z));
         }
      }
      if (t == 12 || t == 32 || t == 52) bite(level, t, null, t, 14);
   }

   /** Une grappe de la voûte crève au-dessus d'un joueur (phase 3, appelé par la salle) : cercle, puis le sac d'œufs s'écrase. */
   public void clusterFall(ServerLevel level, BlockPos cluster, Player p) {
      Vec3 aim = surf(p.position());
      Vec3 from = clusterBottom(cluster);
      WebGlobServeur g = new WebGlobServeur(level, this, 1);
      int lf = g.launch(from, aim, 26);
      level.addFreshEntity(g);
      Vfx.circle(level, aim, 2.8F, lf);
      level.sendParticles(AMBER_FX, from.x, from.y + 1, from.z, 24, 0.8, 0.8, 0.8, 0.1);
      level.playSound(null, from.x, from.y, from.z, Sounds.BURST.get(), SoundSource.HOSTILE, 3F, 1.2F);
   }

   // ------------------------------------------------------------------ couvée, toiles
   private void ambient(ServerLevel level) {
      webs.removeIf(w -> w.until() < this.tickCount);
      if (this.tickCount % 5 == 0) {
         for (WebZone w : webs) {
            for (Player p : targets(60)) {
               if (p.position().distanceToSqr(w.c().x, p.getY(), w.c().z) > w.r() * w.r() || Math.abs(p.getY() - w.c().y) > 2) continue;
               p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 3, false, false));
               p.addEffect(new MobEffectInstance(MobEffects.JUMP, 12, 128, false, false));
            }
         }
      }
      brood.removeIf(s -> !s.isAlive());
      wrapped.entrySet().removeIf(en -> {
         Player p = en.getKey();
         double[] w = en.getValue();
         if (!p.isAlive() || this.tickCount > w[3]) return true;
         if (p.distanceToSqr(w[0], w[1], w[2]) > 0.01) p.teleportTo(w[0], w[1], w[2]);
         p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
         p.hurtMarked = true;
         return false;
      });
      if (phase() >= 2 && this.tickCount % 300 == 0 && brood.size() < 6 && idle()) hatch(level, surf(spinnerets()), 1);
      if (phase() >= 2 && this.tickCount % 4 == 0) level.sendParticles(BLOOD_FX, spinnerets().x, spinnerets().y - 0.5, spinnerets().z, 1, 0.6, 0.3, 0.6, 0.01);
   }

   /** Bas d'une grappe (d'où part le fil des rejetons). */
   public static Vec3 clusterBottom(BlockPos p) {
      return Vec3.atBottomCenterOf(p).add(0, 0.2, 0);
   }

   /** Un rejeton descend au bout de son fil depuis une grappe jusqu'à to ; il rejoint la couvée à l'arrivée. */
   private SpiderlingServeur dropFrom(ServerLevel level, BlockPos cluster, Vec3 to, int ticks, boolean stay) {
      if (!(HeraliszMere.SPIDERLING.get().create(level) instanceof SpiderlingServeur s)) return null;      // créée par l'usine
      s.hangDown(clusterBottom(cluster), to, ticks, stay);
      level.addFreshEntity(s);
      level.playSound(null, s.getX(), s.getY(), s.getZ(), Sounds.SILK.get(), SoundSource.HOSTILE, 1.5F, 1.4F);
      return s;
   }

   /** Plaque de toile gluante (rayon r) pendant dur ticks. */
   public void addWeb(Vec3 c, float r, int dur) {
      webs.add(new WebZone(c, r, this.tickCount + dur));
      if (this.level() instanceof ServerLevel sl) Vfx.web(sl, c, r, dur);
   }

   /** Rejetons : araignées venimeuses rattachées à la mère, qui disparaissent avec elle. */
   public void hatch(ServerLevel level, Vec3 at, int n) {
      for (int i = 0; i < n && brood.size() < 10; i++) {
         if (!(HeraliszMere.SPIDERLING.get().create(level) instanceof SpiderlingServeur s)) return;          // créée par l'usine
         double a = random.nextDouble() * Mth.TWO_PI;
         Vec3 p = surf(at.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2));
         s.moveTo(p.x, p.y, p.z, random.nextFloat() * 360F, 0);
         Player tgt = level.getNearestPlayer(s, 40);
         if (tgt != null && !tgt.isCreative() && !tgt.isSpectator()) s.setTarget(tgt);
         level.addFreshEntity(s);
         brood.add(s);
      }
      level.sendParticles(GOO_FX, at.x, at.y + 0.5, at.z, 20, 0.6, 0.4, 0.6, 0.1);
      level.playSound(null, at.x, at.y, at.z, Sounds.BURST.get(), SoundSource.HOSTILE, 2F, 1.3F);
   }

   private void discardBrood() {
      for (Spiderling s : brood) if (s.isAlive()) s.discard();
      for (Spiderling s : crawlers) if (s.isAlive()) s.discard();
      for (Spiderling s : scatter) if (s.isAlive()) s.discard();
      brood.clear();
      crawlers.clear();
      scatter.clear();
   }

   // ------------------------------------------------------------------ cibles, groupe
   private void pickTarget() {
      threat.replaceAll((k, v) -> v * 0.8F);
      Player best = null;
      float bs = -1;
      Vec3 c = arena();
      double lim = MereConfig.LEASH_RADIUS.get() + 6;
      for (Player p : targets(70)) {
         if (p.position().distanceToSqr(c.x, p.getY(), c.z) > lim * lim) continue;
         float sc = threat.getOrDefault(p.getUUID(), 0F) + (float) (70 - p.distanceTo(this)) * 0.5F;
         if (sc > bs) {
            bs = sc;
            best = p;
         }
      }
      this.setTarget(best);
   }

   public List<Player> targets(double r) {
      return this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(r),
         p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) <= r);
   }

   private Player farthest(double r) {
      Player best = null;
      double bd = -1;
      for (Player p : targets(r)) {
         double d = p.distanceTo(this);
         if (d > bd) {
            bd = d;
            best = p;
         }
      }
      return best;
   }

   private void updateGroup() {
      int n = Math.max(1, targets(60).size());
      if (n >= peakPlayers) {
         peakPlayers = n;
         peakHold = 30;
      } else if (--peakHold <= 0) {
         peakPlayers--;
         peakHold = 30;
      }
   }

   private float groupMult() {
      return 1F + Math.max(0, peakPlayers - MereConfig.GROUP_BASE.get()) * MereConfig.GROUP_STEP.get().floatValue();
   }

   // ------------------------------------------------------------------ géométrie
   private void faceTo(Vec3 p) {
      faceYaw((float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90F);
   }

   private void faceYaw(float yaw) {
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
      this.yBodyRotO = yaw;
      this.yRotO = yaw;
   }

   private void turnToward(Vec3 p, float step) {
      float want = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90F;
      this.yBodyRot = Mth.approachDegrees(this.yBodyRot, want, step);
      this.setYRot(this.yBodyRot);
   }

   public Vec3 fwd() {
      return Vec3.directionFromRotation(0, this.yBodyRot);
   }

   private Vec3 right() {
      Vec3 f = fwd();
      return new Vec3(-f.z, 0, f.x);
   }

   /** Bouche / crochets (le harpon de soie en part). */
   private Vec3 mouth() {
      return this.position().add(fwd().scale(1.9)).add(0, 2.2, 0);
   }

   /** Filières au bout du sac (globes de toile, cocons d'œufs). */
   private Vec3 spinnerets() {
      return this.position().add(fwd().scale(-2.6)).add(0, 4.2, 0);
   }

   private static Vec3 flat(Vec3 v) {
      Vec3 f = new Vec3(v.x, 0, v.z);
      return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
   }

   private double horizontal(Vec3 p) {
      return Math.sqrt(Mth.square(p.x - getX()) + Mth.square(p.z - getZ()));
   }

   /** Hauteur du sol sous (x, z), en descendant depuis y (jusqu'à 24 blocs). */
   public static double groundY(BlockGetter level, double x, double y, double z) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int x0 = Mth.floor(x), z0 = Mth.floor(z), y0 = Mth.floor(y);
      for (int dy = 0; dy <= 24; dy++) {
         m.set(x0, y0 - dy, z0);
         BlockState st = level.getBlockState(m);
         var shape = st.getCollisionShape(level, m);
         if (!shape.isEmpty()) return m.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
      }
      return y;
   }

   /** Le même point, posé sur le sol. */
   private Vec3 surf(Vec3 p) {
      return new Vec3(p.x, groundY(this.level(), p.x, Math.max(p.y, this.getY()) + 1.5, p.z), p.z);
   }

   /** Point ramené dans le cercle de l'arène. */
   private Vec3 inLeash(Vec3 p) {
      if (home == null) return p;
      Vec3 c = arena();
      double r = Math.min(26, MereConfig.LEASH_RADIUS.get());
      Vec3 d = new Vec3(p.x - c.x, 0, p.z - c.z);
      if (d.length() <= r) return p;
      Vec3 q = c.add(d.normalize().scale(r));
      return surf(new Vec3(q.x, p.y, q.z));
   }

   private BlockState ground(Vec3 p) {
      BlockState s = this.level().getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
      return s.isAir() || s.getLightEmission() > 0 ? Blocks.TUFF.defaultBlockState() : s;
   }

   private float dmg(float base) {
      return base * MereConfig.DAMAGE_MULT.get().floatValue() * (phase() == 3 ? 1.2F : 1F);
   }

   private void sound(SoundEvent s, float vol, float pitch) {
      this.level().playSound(null, getX(), getY() + 2, getZ(), s, SoundSource.HOSTILE, vol, pitch);
      if (s == Sounds.SCREECH.get() || s == Sounds.HURT.get())                  // ses cris : doublés, c'est ce qu'on doit entendre
         this.level().playSound(null, getX(), getY() + 2, getZ(), s, SoundSource.HOSTILE, vol, pitch);
   }

   private void hurtP(Player p, float base) {
      if (p.hurt(this.damageSources().mobAttack(this), dmg(base))) {
         Vfx.hit(p, Math.min(1F, 0.25F + base / 26F));
         p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
      }
   }

   private List<Player> hitCircle(ServerLevel level, Vec3 c, double r, float damage, double push, double lift) {
      List<Player> out = new ArrayList<>();
      for (Player p : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r, 4, r))) {
         if (p.isCreative() || p.isSpectator() || p.position().distanceToSqr(c.x, p.getY(), c.z) > r * r) continue;
         if (damage > 0) hurtP(p, damage);
         Vec3 d = flat(p.position().subtract(c));
         p.setDeltaMovement(d.x * push, lift, d.z * push);
         p.hurtMarked = true;
         out.add(p);
      }
      return out;
   }

   private List<Player> inCone(double range, double halfDeg) {
      List<Player> out = new ArrayList<>();
      for (Player p : targets(range + 3)) {
         Vec3 to = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
         if (to.length() > range || (to.lengthSqr() > 1e-4 && fwd().dot(to.normalize()) < Math.cos(Math.toRadians(halfDeg)))) continue;
         out.add(p);
      }
      return out;
   }

   // ------------------------------------------------------------------ garde-fous, barre
   private void leash(ServerLevel level) {
      Vec3 c = arena();
      int r = MereConfig.LEASH_RADIUS.get();
      if (horizontal(c) > r + 30 || Math.abs(this.getY() - c.y) > 24) {
         stopAction();
         this.teleportTo(c.x, c.y + 1, c.z);
      }
   }

   private void updateBossBar(ServerLevel level) {
      for (ServerPlayer p : level.players()) {
         boolean near = p.distanceTo(this) < 80;
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
      int t = actionTime();
      if (a == Action.SPAWN || a == Action.PHASE2 || a == Action.PHASE3) return false;
      boolean hanging = a == Action.ASCEND && t >= 20 || a == Action.LURK || a == Action.DROP && t < 9;
      if (source.getEntity() instanceof Player dp && dp.getMainHandItem().is(HeraliszMere.DEBUG_BLADE.get())) {
         this.invulnerableTime = 0;
         return super.hurt(source, this.getMaxHealth() * 0.34F);
      }
      float raw = Math.min(amount, MereConfig.HIT_CAP.get().floatValue());
      Vec3 weak = mouth();
      if (WeakPoint.hit(source, this, weak, 1.5)) {
         raw *= WeakPoint.CRIT;
         WeakPoint.feedback(this, source, weak);
      }
      if (hanging) raw *= MereConfig.SHIELD_BLOCK.get().floatValue();                // là-haut sur son fil : (presque) intouchable
      if (raw <= 0) return false;
      float window = 0;
      for (float v : dpsWindow) window += v;
      if (window > MereConfig.DPS_CAP.get()) raw *= 0.25F;
      dpsWindow[this.tickCount % 20] += raw;
      float scaled = raw * this.getMaxHealth() / Math.max(1F, MereConfig.VIRTUAL_HP.get() * groupMult());
      this.invulnerableTime = 0;
      boolean ok = super.hurt(source, Math.max(scaled, 0.01F));
      this.invulnerableTime = 0;
      if (ok && source.getEntity() instanceof Player pl) threat.merge(pl.getUUID(), raw, Float::sum);
      return ok;
   }

   /** Son repère pendant la mort : origine (pieds du modèle) et orientation, au tick t. */
   private Vec3 frameO;
   private org.joml.Quaternionf frameQ;

   /** Soulèvement (0 → 1) : la moyenne de la progression des rejetons qui se glissent sous elle (2 ticks d'écart, 18 chacun). */
   private static double liftProgress(int t) {
      double sum = 0;
      for (int i = 0; i < 6; i++) {
         double u = Mth.clamp((t - SLIDE0 - 2 * i) / 18.0, 0, 1);
         sum += u * u * (3 - 2 * u);
      }
      return sum / 6;
   }

   /** Début du hissage : les rejetons du haut ont d'abord lancé leurs fils sur elle. */
   private static final int HAUL0 = TOPUP1 + 18;

   /** Hissage (0 → 1) : par à-coups, à chaque traction des rejetons du haut (5 tractions). */
   private static double raiseProgress(int t) {
      double u = Mth.clamp((t - HAUL0) / (double) (RAISE1 - HAUL0), 0, 1);
      double n = 5;
      return Mth.clamp(u - Math.sin(Mth.TWO_PI * n * u) / (Mth.TWO_PI * n), 0, 1);
   }

   private void frame(int t, Vec3 f0) {
      Vec3 f = pillarF;
      double g = deathFrom.y;
      if (t < SLIDE0) {
         // sur le dos (animation, 1,95 s) : on la remonte d'autant que son dos dépasse sous ses pieds
         double k = Mth.clamp((t - 34) / 10.0, 0, 1);
         frameO = deathFrom.add(0, BACK_Y * k * k * (3 - 2 * k), 0);
         frameQ = qHeading(f0);
         return;
      }
      // soulevée à mesure que les rejetons se glissent dessous ; elle pivote vers le pilier
      double turn = Mth.clamp((t - SLIDE0) / 45.0, 0, 1);
      turn = turn * turn * (3 - 2 * turn);
      org.joml.Quaternionf head = qHeading(f0).slerp(qHeading(f), (float) turn);
      if (t < SLIDE1) {
         double u = liftProgress(t);
         frameO = deathFrom.add(0, Mth.lerp(u, BACK_Y, LIFT) + 0.06 * Math.sin(u * Math.PI), 0);
         frameQ = head;
         return;
      }
      Vec3 carryEnd = pillarC == null ? deathFrom.add(f.scale(5)) : new Vec3(pillarC.x, 0, pillarC.z).subtract(f.scale(PILLAR_R + 3.8));
      if (t < CARRY1) {
         double u = Mth.clamp((t - SLIDE1) / (double) (CARRY1 - SLIDE1), 0, 1);
         double e = u * u * (3 - 2 * u);
         Vec3 p = new Vec3(Mth.lerp(e, deathFrom.x, carryEnd.x), 0, Mth.lerp(e, deathFrom.z, carryEnd.z));
         // à la façon des fourmis : elle roule et tangue un peu au rythme des pattes
         Vec3 side = new Vec3(-f.z, 0, f.x);
         p = p.add(side.scale(0.09 * Math.sin(t * 0.9)));
         double gy = gy(this.level(), p.x, g + 2, p.z);
         frameO = new Vec3(p.x, gy + LIFT + 0.05 * Math.sin(t * 1.3), p.z);
         frameQ = head.rotateAxis((float) (0.035 * Math.sin(t * 0.7)), (float) f.x, 0, (float) f.z, new org.joml.Quaternionf());
         return;
      }
      double gy = gy(this.level(), carryEnd.x, g + 2, carryEnd.z);
      if (pillarC == null) {
         // pas de pilier : posée au sol, les porteuses s'accroupissent
         double u = Mth.clamp((t - CARRY1) / (double) (SET1 - CARRY1), 0, 1);
         u = u * u * (3 - 2 * u);
         frameO = new Vec3(carryEnd.x, gy + Mth.lerp(u, LIFT, SET_Y), carryEnd.z);
         frameQ = qHeading(f);
         return;
      }
      Vec3 top = new Vec3(pillarC.x, 0, pillarC.z).subtract(f.scale(PILLAR_R + LIFT));
      Vec3 a = new Vec3(carryEnd.x, gy + LIFT, carryEnd.z), b = new Vec3(top.x, pillarC.y + BODY_BACK, top.z);
      if (t < RAISE1) {
         // toujours sur le dos de ses six porteuses : elles l'amènent au fût en la basculant peu à peu contre lui, puis montent,
         // d'un seul mouvement continu (aucun à-coup), la tête la première ; elle tangue doucement au rythme des pattes
         double u = Mth.clamp((t - CARRY1) / (double) (RAISE1 - CARRY1), 0, 1);
         double e = u - Math.sin(Mth.TWO_PI * u) / Mth.TWO_PI;
         double k = Mth.clamp(u / 0.28, 0, 1);
         k = k * k * k * (k * (k * 6 - 15) + 10);
         frameQ = qHeading(f).slerp(qPillar(f), (float) k);
         Vec3 side = new Vec3(-f.z, 0, f.x);
         double climb = Mth.clamp((u - 0.12) / 0.88, 0, 1);
         climb = climb * climb * (3 - 2 * climb);
         Vec3 foot = new Vec3(b.x, a.y + 0.6 * k, b.z);
         frameO = u < 0.12 ? a.lerp(foot, e / Math.max(1e-3, (0.12 - Math.sin(Mth.TWO_PI * 0.12) / Mth.TWO_PI))) : foot.lerp(b, climb);
         frameO = frameO.add(side.scale(0.06 * Math.sin(t * 0.55))).add(0, 0.03 * Math.sin(t * 1.1), 0);
         return;
      }
      frameO = b;
      frameQ = qPillar(f);
   }

   /** Place de la porteuse i sous son dos (monde), au sol. */
   private Vec3 slot(int i, int t) {
      double[] sl = SLOTS[i % SLOTS.length];
      double lift = t >= CARRY1 && pillarC != null ? LIFT : frameO.y - gy(this.level(), frameO.x, frameO.y + 1, frameO.z);
      return frameO.add(rot(frameQ, sl[0] + 0.04 * Math.sin(t * 0.8 + i * 1.7), -lift, sl[1]));
   }

   /** Premier bloc plein en partant de `from` dans la direction horizontale `dir`, à la hauteur y (2 à 32 blocs) ; un peu avant. */
   private Vec3 wallHit(Level level, Vec3 from, Vec3 dir, double y) {
      net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
      for (double d = 2; d <= 32; d += 0.25) {
         m.set(Mth.floor(from.x + dir.x * d), Mth.floor(y), Mth.floor(from.z + dir.z * d));
         if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
            double back = d - 0.15;
            return new Vec3(from.x + dir.x * back, y, from.z + dir.z * back);
         }
      }
      return null;
   }

   /** Passage libre entre a et b à hauteur de rejeton, sans marche de plus de 2 blocs (elles ne traversent pas un fût). */
   private boolean clearPath(Level level, Vec3 a, Vec3 b) {
      double len = Math.hypot(b.x - a.x, b.z - a.z);
      net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
      for (double d = 0; d <= len; d += 0.4) {
         double x = a.x + (b.x - a.x) * d / len, z = a.z + (b.z - a.z) * d / len;
         double y = gy(level, x, a.y + 1, z);
         if (Math.abs(y - a.y) > 2.2) return false;
         m.set(Mth.floor(x), Mth.floor(y + 0.6), Mth.floor(z));
         if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) return false;
      }
      return true;
   }

   /**
    * Les chemins des six rejetons : ils apparaissent sur la paroi d'en face (côté opposé à la caméra : on les voit arriver),
    * la descendent tête en bas, traversent la salle et attendent devant elle (entre elle et la caméra), chacune face à sa place
    * sous son dos.
    */
   private void planCrew(ServerLevel level) {
      crew.clear();
      crewWall.clear();
      crewNorm.clear();
      crewFloor.clear();
      crewFront.clear();
      Vec3 cf = Vec3.directionFromRotation(0, deathCineYaw());
      camSide = new Vec3(cf.x, 0, cf.z).normalize();
      double far = Math.atan2(-camSide.z, -camSide.x);
      frameO = deathFrom.add(0, BACK_Y, 0);
      frameQ = qHeading(deathF0);
      // d'abord les fûts du temple : elles surgissent de l'ombre de la voûte et descendent les colonnes, tête en bas, deux par fût
      for (Vec3 pc : pillarCenters(level, deathFrom)) {
         if (crewWall.size() >= 6) break;
         if (pillarC != null && Math.hypot(pc.x - pillarC.x, pc.z - pillarC.z) < 3) continue;
         Vec3 toHer = new Vec3(deathFrom.x - pc.x, 0, deathFrom.z - pc.z).normalize();
         for (double off : new double[] {-0.5, 0.5}) {
            if (crewWall.size() >= 6) break;
            Vec3 d = toHer.yRot((float) off);
            double R = PILLAR_R + 0.35;
            double sx = pc.x + d.x * R, sz = pc.z + d.z * R;
            double inX = pc.x + d.x * (PILLAR_R - 0.6), inZ = pc.z + d.z * (PILLAR_R - 0.6);
            double floorY = gy(level, sx, deathFrom.y + 1, sz);
            double top = floorY + 1;
            net.minecraft.core.BlockPos.MutableBlockPos mm = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (double yy = floorY + 1; yy < floorY + 34; yy += 1) {
               mm.set(Mth.floor(inX), Mth.floor(yy), Mth.floor(inZ));
               if (level.getBlockState(mm).isAir()) break;
               mm.set(Mth.floor(sx), Mth.floor(yy), Mth.floor(sz));
               if (!level.getBlockState(mm).getCollisionShape(level, mm).isEmpty()) break;
               top = yy;
            }
            if (top - floorY < 6) continue;
            List<Vec3> pts = new ArrayList<>(), ns = new ArrayList<>();
            for (double h = top; h >= floorY + 0.4; h -= 0.35) {
               pts.add(new Vec3(sx, h, sz));
               ns.add(d);
            }
            Vec3 foot = pts.get(pts.size() - 1);
            Vec3 fl = foot.add(d.scale(0.9));
            Vec3 floor = new Vec3(fl.x, gy(level, fl.x, floorY + 1, fl.z), fl.z);
            int i = crewWall.size();
            Vec3 fr = slot(i, SLIDE0).add(camSide.scale(2.6));
            Vec3 front = new Vec3(fr.x, gy(level, fr.x, deathFrom.y + 1, fr.z), fr.z);
            if (!clearPath(level, floor, front)) continue;
            crewWall.add(pts);
            crewNorm.add(ns);
            crewFloor.add(floor);
            crewFront.add(front);
         }
      }
      int[] order = {-2, 2, -3, 3, 0, -1, 1, -4, 4, -5, 5, -6, 6, -7, 7, -8, 8};
      java.util.Set<Integer> used = new java.util.HashSet<>();
      // 1er passage : la paroi d'en face, à bonne distance ; 2e passage (elle est morte près d'un mur, loin de tout) : toute
      // paroi hors de l'axe de la caméra, plus près ou plus loin ; 3e (voir plus bas) : elles surgissent des recoins du sol
      for (int pass = 0; pass < 2 && crewWall.size() < 6; pass++)
      for (int kk = 0; kk < (pass == 0 ? order.length : 27); kk++) {
         int k = pass == 0 ? order[kk] : (kk % 2 == 0 ? 1 : -1) * ((kk + 1) / 2);
         if (crewWall.size() >= 6) break;
         if (!used.add(k)) continue;
         double minD = pass == 0 ? 6 : 4, maxD = pass == 0 ? 15 : 24;
         double ang = far + k * Math.toRadians(10);
         Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
         List<Vec3> pts = new ArrayList<>();
         for (double h = 7.5; h >= 0.4; h -= 0.35) {
            Vec3 w = wallHit(level, deathFrom, dir, deathFrom.y + h);
            if (w == null) break;
            if (!pts.isEmpty() && w.distanceTo(pts.get(pts.size() - 1)) > 1.6) break;       // un surplomb : on s'arrête là
            pts.add(w);
         }
         if (pts.size() < 12) continue;
         double dist = Math.hypot(pts.get(0).x - deathFrom.x, pts.get(0).z - deathFrom.z);
         if (dist < minD || dist > maxD) continue;
         Vec3 foot = pts.get(pts.size() - 1);
         Vec3 fl = foot.subtract(dir.scale(0.9));
         Vec3 floor = new Vec3(fl.x, gy(level, fl.x, foot.y + 1, fl.z), fl.z);
         int i = crewWall.size();
         Vec3 fr = slot(i, SLIDE0).add(camSide.scale(2.6));
         Vec3 front = new Vec3(fr.x, gy(level, fr.x, deathFrom.y + 1, fr.z), fr.z);
         if (!clearPath(level, floor, front)) continue;
         List<Vec3> ns = new ArrayList<>();
         Vec3 side = new Vec3(-dir.z, 0, dir.x);
         for (int q = 0; q < pts.size(); q++) {
            Vec3 tn = pts.get(Math.min(pts.size() - 1, q + 1)).subtract(pts.get(Math.max(0, q - 1)));
            tn = tn.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : tn.normalize();
            Vec3 n = side.cross(tn);
            if (n.dot(dir) > 0) n = n.scale(-1);
            ns.add(n.normalize());
         }
         crewWall.add(pts);
         crewNorm.add(ns);
         crewFloor.add(floor);
         crewFront.add(front);
      }
      for (int k = 0; crewWall.size() < 6 && k < 36; k++) {
         // pas assez de parois utilisables : elles surgissent d'un recoin du sol, loin d'elle
         double ang = far + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * Math.toRadians(17);
         Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
         Vec3 fl = deathFrom.add(dir.scale(9 + (k % 3)));
         Vec3 floor = new Vec3(fl.x, gy(level, fl.x, deathFrom.y + 1, fl.z), fl.z);
         int i = crewWall.size();
         Vec3 fr = slot(i, SLIDE0).add(camSide.scale(2.6));
         Vec3 front = new Vec3(fr.x, gy(level, fr.x, deathFrom.y + 1, fr.z), fr.z);
         if (!clearPath(level, floor, front)) continue;
         crewWall.add(List.of(floor));
         crewNorm.add(List.of(new Vec3(0, 1, 0)));
         crewFloor.add(floor);
         crewFront.add(front);
      }
      int n = crewWall.size();
      crewDown = new int[n];
      crewArrive = new int[n];
      for (int i = 0; i < n; i++) {
         List<Vec3> pts = crewWall.get(i);
         double len = 0;
         for (int q = 1; q < pts.size(); q++) len += pts.get(q).distanceTo(pts.get(q - 1));
         // (surgie du sol : ni descente ni bascule)
         crewDown[i] = pts.size() < 2 ? WALL0 + 4 * i - 8 : WALL0 + 4 * i + (int) Math.ceil(len / CREW_SPEED);
         int run0 = crewDown[i] + 8;
         double d = crewFloor.get(i).distanceTo(crewFront.get(i));
         // elles courent (0,34 bloc/tick), plus vite s'il le faut pour être là avant qu'on la soulève
         crewArrive[i] = Math.min(run0 + (int) Math.ceil(d / CREW_RUN), SLIDE0 - 4);
         if (crewArrive[i] <= run0) crewArrive[i] = run0 + 4;
      }
   }

   /** Point sur la paroi, à l'abscisse curviligne s (du haut vers le pied), et sa normale. */
   private static Vec3[] along(List<Vec3> pts, List<Vec3> ns, double s) {
      for (int q = 1; q < pts.size(); q++) {
         double seg = pts.get(q).distanceTo(pts.get(q - 1));
         if (s <= seg || q == pts.size() - 1) {
            double u = seg < 1e-6 ? 1 : Mth.clamp(s / seg, 0, 1);
            Vec3 p = pts.get(q - 1).lerp(pts.get(q), u);
            Vec3 n = ns.get(q - 1).lerp(ns.get(q), u).normalize();
            Vec3 tn = pts.get(q).subtract(pts.get(q - 1));
            return new Vec3[] {p, n, tn.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : tn.normalize()};
         }
         s -= seg;
      }
      Vec3 last = pts.get(pts.size() - 1);
      return new Vec3[] {last, ns.get(ns.size() - 1), new Vec3(0, -1, 0)};
   }

   /** Point sur le fût (azimut : décalage latéral x en blocs autour du côté de la salle), à la hauteur y, et sa normale. */
   private Vec3[] onPillar(double x, double y) {
      Vec3 out = pillarF.scale(-1), sx = new Vec3(-pillarF.z, 0, pillarF.x);
      double r = PILLAR_R + 0.35, a = x / r;
      Vec3 d = out.scale(Math.cos(a)).add(sx.scale(Math.sin(a)));
      return new Vec3[] {new Vec3(pillarC.x + d.x * r, y, pillarC.z + d.z * r), d};
   }

   private static Vec3 slerpDir(Vec3 a, Vec3 b, double u) {
      Vec3 v = a.scale(1 - u).add(b.scale(u));
      return v.lengthSqr() < 1e-6 ? b : v.normalize();
   }

   /** Un tick des rejetons de la mort : paroi, sol, attente, glissade, portage, pilier, tissage, départ. */
   private void tickCrew(ServerLevel level, int t) {
      int n = crewWall.size();
      Vec3 up0 = new Vec3(0, 1, 0);
      for (int i = 0; i < n; i++) {
         int appear = WALL0 + 4 * i;
         if (t == appear) {
            Vec3 top = crewWall.get(i).get(0);
            if (!(HeraliszMere.SPIDERLING.get().create(level) instanceof SpiderlingServeur sp)) continue;   // créée par l'usine
            sp.moveTo(top.x, top.y, top.z, 0, 0);
            level.addFreshEntity(sp);
            while (crew.size() < i) crew.add(null);
            crew.add(sp);
            level.playSound(null, top.x, top.y, top.z, Sounds.CHITTER.get(), SoundSource.HOSTILE, 1.6F, 1.5F + random.nextFloat() * 0.3F);
            // elle surgit de l'ombre en haut du fût : un peu de poussière et de soie tombe
            level.sendParticles(Particles.MIST.get(), top.x, top.y + 0.3, top.z, 8, 0.4, 0.2, 0.4, 0.02);
            level.sendParticles(WEB_FX, top.x, top.y + 0.4, top.z, 6, 0.4, 0.3, 0.4, 0.02);
         }
         if (i >= crew.size() || crew.get(i) == null || !crew.get(i).isAlive() || t < appear) continue;
         SpiderlingServeur sp = crew.get(i);
         boolean top = true, hasPillar = pillarC != null;
         int k = i;
         Vec3 p, up = up0, fw;
         int anim;
         if (t < crewDown[i]) {
            // elle descend la paroi, tête en bas
            Vec3[] w = along(crewWall.get(i), crewNorm.get(i), (t - appear) * CREW_SPEED);
            p = w[0];
            up = w[1];
            fw = w[2];
            anim = Spiderling.A_RUN;
         } else if (t < crewDown[i] + 8) {
            // au pied de la paroi, elle bascule sur le sol
            double u = (t - crewDown[i]) / 8.0;
            List<Vec3> pts = crewWall.get(i);
            Vec3 foot = pts.get(pts.size() - 1), fl = crewFloor.get(i);
            p = foot.lerp(fl, u).add(0, 0.25 * Math.sin(Math.PI * u), 0);
            up = slerpDir(crewNorm.get(i).get(pts.size() - 1), up0, u);
            Vec3 toF = crewFront.get(i).subtract(fl).multiply(1, 0, 1);
            fw = slerpDir(new Vec3(0, -1, 0), toF.normalize(), u);
            anim = Spiderling.A_RUN;
         } else if (t < crewArrive[i]) {
            // elle traverse la salle
            double u = (t - crewDown[i] - 8) / (double) Math.max(1, crewArrive[i] - crewDown[i] - 8);
            Vec3 q = crewFloor.get(i).lerp(crewFront.get(i), u);
            p = new Vec3(q.x, gy(level, q.x, q.y + 1, q.z), q.z);
            fw = crewFront.get(i).subtract(crewFloor.get(i));
            anim = Spiderling.A_RUN;
         } else if (t < SLIDE0 + 2 * i) {
            // devant elle, face à sa place
            p = crewFront.get(i);
            fw = slot(i, t).subtract(p);
            anim = Spiderling.A_HOLD;
         } else if (t < SLIDE1) {
            // elle se glisse dessous
            double u = Mth.clamp((t - SLIDE0 - 2 * i) / 18.0, 0, 1);
            u = u * u * (3 - 2 * u);
            Vec3 sl = slot(i, t);
            p = crewFront.get(i).lerp(sl, u);
            fw = sl.subtract(crewFront.get(i));
            anim = u < 0.98 ? Spiderling.A_CARRY : Spiderling.A_HOLD;
         } else if (t < CARRY1 || !hasPillar) {
            // elles la portent (même cap qu'elle), puis la posent
            p = slot(i, t);
            fw = rot(frameQ, 0, 0, -1);
            anim = t < CARRY1 ? Spiderling.A_CARRY : Spiderling.A_HOLD;
            if (!hasPillar && t >= SET1) {
               // pas de pilier : elles l'emmaillotent au sol en tournant autour d'elle
               if (t >= WRAP0 && t < LEAVE0) {
                  double a = i * Mth.TWO_PI / 6 + (t - WRAP0) * 0.045 * (i % 2 == 0 ? 1 : -1);
                  Vec3 c = frameO.add(rot(frameQ, 0, 0, 1.3));
                  p = new Vec3(c.x + Math.cos(a) * 3.2, gy(level, c.x + Math.cos(a) * 3.2, c.y + 1, c.z + Math.sin(a) * 3.2), c.z + Math.sin(a) * 3.2);
                  fw = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(i % 2 == 0 ? 1 : -1);
                  anim = Spiderling.A_RUN;
               }
            }
         } else if (t < RAISE1) {
            // elles la portent sur leur dos jusqu'en haut du fût : même cap qu'elle, collées à la pierre, pattes qui s'agrippent
            p = slot(i, t);
            up = rot(frameQ, 0, 1, 0);
            fw = rot(frameQ, 0, 0, -1);
            anim = Spiderling.A_CARRY;
            if (t % 6 == i % 6 && t > CARRY1 + 30) level.sendParticles(STONE_FX, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.02);
         } else if (t < LEAVE0) {
            // toutes l'emmaillotent : elles tournent autour du pilier ET d'elle, du bas au haut du cocon, lentement
            Vec3 out = pillarF.scale(-1), sx = new Vec3(-out.z, 0, out.x);
            // la cinquième fait d'abord deux tours pour l'amarrer au fût, les autres la tiennent ; puis toutes tournent, lentement
            boolean first = i == 5;
            if (!first && t < WRAP0 + SECURE) {
               p = slot(i, t);
               up = rot(frameQ, 0, 1, 0);
               fw = rot(frameQ, 0, 0, -1);
               anim = Spiderling.A_HOLD;
               sp.puppet(p, up, fw, anim);
               continue;
            }
            int t0w = first ? WRAP0 : WRAP0 + SECURE;
            double spd = first && t < WRAP0 + SECURE ? 0.16 : 0.024;
            double turned = first ? Math.min(t - WRAP0, SECURE) * 0.16 + Math.max(0, t - WRAP0 - SECURE) * 0.024 : (t - t0w) * spd;
            double kk = Mth.clamp((t - t0w) / 15.0, 0, 1);
            double a = i * Mth.TWO_PI / 6 + turned * (i % 2 == 0 ? 1 : -1);
            double h = COCOON_HALF * 0.85 * Math.sin(i * 1.1 + (t - t0w) * 0.016);
            Vec3 dd = out.scale(Math.cos(a)).add(sx.scale(Math.sin(a)));
            double r = loopR((float) a, (float) h) + 0.3;
            Vec3 ring = new Vec3(pillarC.x, pillarC.y + COCOON_UP + h, pillarC.z).add(dd.scale(r));
            if (t == t0w || crewMark[i] == null) crewMark[i] = sp.position();
            p = crewMark[i].lerp(ring, kk * kk * (3 - 2 * kk));
            up = dd;
            Vec3 tang = out.scale(-Math.sin(a)).add(sx.scale(Math.cos(a))).scale(i % 2 == 0 ? 1 : -1);
            fw = tang.add(0, 0.25 * Math.cos(i * 1.1 + (t - t0w) * 0.016), 0);
            anim = Spiderling.A_RUN;
            if (t % 5 == i % 5) level.sendParticles(WEB_FX, p.x, p.y + 0.3, p.z, 2, 0.2, 0.2, 0.2, 0.01);
         } else {
            // elles remontent le long du fût et disparaissent dans l'ombre de la voûte
            double u = (t - LEAVE0) / (double) (LEAVE1 - LEAVE0);
            if (t == LEAVE0 || crewMark[i] == null) crewMark[i] = sp.position();
            Vec3[] w = onPillar((i - 2.5) * 0.9, pillarC.y + COCOON_UP + COCOON_HALF + 1);
            p = crewMark[i].lerp(w[0], Math.min(1, u * 2.5)).add(0, Math.max(0, u - 0.3) * 8, 0);
            up = w[1];
            fw = up0;
            anim = Spiderling.A_RUN;
         }
         sp.puppet(p, up, fw, anim);
      }
   }

   @Override
   public void die(DamageSource src) {
      super.die(src);
      bossBar.removeAllPlayers();
      stopAction();
   }

   @Override
   protected void tickDeath() {
      if (!(this.level() instanceof ServerLevel level)) {
         this.deathTime = Math.min(this.deathTime + 1, REMAINS);
         return;
      }
      this.deathTime++;
      int t = this.deathTime;
      if (t % 10 == 0 || t == 100) this.entityData.set(DEATH_T, t);
      if (t <= DEATH_TICKS) deathTick(level, t);
      if (t == 2 && MereConfig.CINEMATICS.get())
         Vfx.cinematic(level, deathFrom, this.getId(), DEATH_K, DEATH_CINE, Vec3.directionFromRotation(0, deathCineYaw()));
      if (t > DEATH_TICKS && frameO != null) {
         this.setPos(frameO.x, frameO.y, frameO.z);
         this.setDeltaMovement(Vec3.ZERO);
      }
      if (t >= REMAINS) this.remove(RemovalReason.KILLED);
   }

   private void deathTick(ServerLevel level, int t) {
      if (t == 1) {
         sound(Sounds.DEATH.get(), 5F, 1F);
         pulled.clear();
         wrapped.clear();
         deathFrom = new Vec3(getX(), gy(this.level(), getX(), getY(), getZ()), getZ());
         hangAt = null;
         pillarC = null;
         discardBrood();
         NestHallBEServeur hh = hall();
         if (hh != null) hh.deathDark(level, deathFrom);
         webs.clear();
         pickPillar(level);
         deathF0 = fwd();
         pillarF = pillarC != null ? new Vec3(pillarC.x - deathFrom.x, 0, pillarC.z - deathFrom.z).normalize() : deathF0;
         this.entityData.set(PILLAR, pillarC == null ? new org.joml.Vector3f(0, -9999, 0) : new org.joml.Vector3f((float) pillarC.x, (float) pillarC.y, (float) pillarC.z));
         this.entityData.set(PILLAR_F, new org.joml.Vector3f((float) pillarF.x, 0, (float) pillarF.z));
         this.noPhysics = true;
         this.setNoGravity(true);
         java.util.Arrays.fill(crewMark, null);
         planCrew(level);
      }
      // elle n'est plus placée que par son repère (la vitesse qui lui restait l'enfonçait lentement dans le sol)
      frame(t, deathF0 == null ? fwd() : deathF0);
      this.entityData.set(ORIENT_ON, true);
      this.entityData.set(ORIENT, new org.joml.Quaternionf(frameQ));
      this.setPos(frameO.x, frameO.y, frameO.z);
      this.setDeltaMovement(Vec3.ZERO);
      Vec3 o = surf(deathFrom);
      if (t == 5) sound(Sounds.HURT.get(), 4F, 0.7F);
      if (t == 20) screech(level);
      if (t == 39) {
         // elle s'abat sur le dos
         level.sendParticles(Particles.MIST.get(), o.x, o.y + 0.3, o.z, 40, 2.2, 0.2, 2.2, 0.05);
         Vfx.shake(level, o, 0.6F, 24, 10);
         sound(Sounds.LAND.get(), 3.5F, 0.8F);
      }
      if (t >= WALL0 && t < LEAVE1) tickCrew(level, t);
      if (t == SLIDE0) sound(Sounds.CHITTER.get(), 3F, 1.4F);
      if (t > SLIDE0 && t < SLIDE1 && t % 6 == 0) level.sendParticles(Particles.MIST.get(), o.x, o.y + 0.3, o.z, 8, 1.8, 0.1, 1.8, 0.03);
      if (t > SLIDE1 && t < CARRY1 && t % 4 == 0) {
         level.playSound(null, frameO.x, frameO.y, frameO.z, net.minecraft.sounds.SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 1.0F, 0.9F + random.nextFloat() * 0.2F);
         level.sendParticles(Particles.MIST.get(), frameO.x, frameO.y - LIFT + 0.1, frameO.z, 4, 1.6, 0.1, 1.6, 0.02);
      }
      if (pillarC == null && t == SET1 - 2) sound(Sounds.LAND.get(), 1.5F, 1.1F);
      if (pillarC != null && t > CARRY1 && t < RAISE1 && t % 4 == 0) {                   // la montée : un pas après l'autre, sans arrêt
         level.playSound(null, frameO.x, frameO.y, frameO.z, net.minecraft.sounds.SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.9F, 0.85F + random.nextFloat() * 0.3F);
         if (t % 12 == 0) level.sendParticles(STONE_FX, frameO.x, frameO.y, frameO.z, 2, 0.8, 0.8, 0.8, 0.03);
      }
      if (t == RAISE1 - 10) sound(Sounds.SILK.get(), 3F, 0.9F);
      if (t == WRAP0) {
         sound(Sounds.COCOON.get(), 4F, 0.8F);
         NestHallBEServeur hh = hall();
         if (hh != null && pillarC != null) hh.deathBeam(level, pillarC.subtract(pillarF.scale(PILLAR_R + COCOON_OUT)), pillarC.y + 16);
      }
      if (t > WRAP0 && t < WRAP1 && t % 6 == 0) {
         if (t % 18 == 0) sound(Sounds.COCOON.get(), 2.5F, 0.8F + random.nextFloat() * 0.3F);
         if (t % 12 == 0) level.playSound(null, frameO.x, frameO.y, frameO.z, net.minecraft.sounds.SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.7F, 1F);
         Vec3 c = pillarC != null ? pillarC.subtract(pillarF.scale(PILLAR_R + COCOON_OUT)).add(0, COCOON_UP, 0) : frameO.add(0, 2, 0);
         level.sendParticles(WEB_FX, c.x, c.y, c.z, 6, 2.2, 2.4, 2.2, 0.02);
      }
      if (t == LEAVE0) sound(Sounds.CHITTER.get(), 3F, 1.5F);
      if (t == LOOT) {
         Vec3 at = pillarC != null ? surf(new Vec3(pillarC.x, deathFrom.y, pillarC.z).subtract(pillarF.scale(PILLAR_R + 4.5))).add(0, 2.5, 0)
            : surf(deathFrom).add(0, 2.5, 0);
         dropLoot(level, at);
      }
      if (t == LEAVE1) {
         for (Spiderling sp : crew) if (sp != null && sp.isAlive()) sp.discard();
         crew.clear();
      }
      if (t == DEATH_TICKS) {
         NestHallBEServeur hh = hall();                                               // le cocon reste contre le pilier (la dépouille)
         if (hh != null) hh.deathEnd(level);
      }
   }

   private Vec3 deathF0;

   /** Cap de la cinématique de mort : de profil, le pilier (et le cocon qu'on y emporte) à droite de l'image. */
   public float deathCineYaw() {
      if (this.isDeadOrDying()) {
         Vec3 h = hangPoint();
         Vec3 d = new Vec3(h.x - getX(), 0, h.z - getZ());
         if (d.lengthSqr() > 1) {
            d = d.normalize();
            // caméra de profil : le pilier (et le cocon qu'on y emporte) à droite de l'image
            return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG) - 90F;
         }
      }
      return this.yBodyRot;
   }

   /** Les fûts ronds du temple autour de c (centres, du plus proche au plus lointain). */
   private List<Vec3> pillarCenters(ServerLevel level, Vec3 c) {
      List<Vec3> out = new ArrayList<>();
      net.minecraft.world.level.block.Block brick = fr.heralisz.mere.dungeon.ModBlocks.RUIN_BRICKS.get();
      net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
      int y = Mth.floor(c.y) + 6;
      for (int dx = -30; dx <= 30; dx++) {
         for (int dz = -30; dz <= 30; dz++) {
            m.set(Mth.floor(c.x) + dx, y, Mth.floor(c.z) + dz);
            if (!level.getBlockState(m).is(brick) || !level.getBlockState(m.above(4)).is(brick)) continue;
            double sx = 0, sz = 0;
            int n = 0;
            for (int ax = -4; ax <= 4; ax++) {
               for (int az = -4; az <= 4; az++) {
                  if (level.getBlockState(new net.minecraft.core.BlockPos(m.getX() + ax, y, m.getZ() + az)).is(brick)) {
                     sx += m.getX() + ax + 0.5;
                     sz += m.getZ() + az + 0.5;
                     n++;
                  }
               }
            }
            if (n < 12) continue;
            Vec3 ctr = new Vec3(sx / n, c.y, sz / n);
            boolean dup = false;
            for (Vec3 o : out) if (Math.hypot(o.x - ctr.x, o.z - ctr.z) < 3.5) dup = true;
            if (!dup) out.add(ctr);
         }
      }
      out.sort(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x - c.x, p.z - c.z)));
      return out;
   }

   /** Sol sous (x, z) près de la hauteur yRef (sort d'abord d'un bloc plein, puis descend). */
   public static double gy(net.minecraft.world.level.Level level, double x, double yRef, double z) {
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(yRef + 1.5), Mth.floor(z));
      for (int i = 0; i < 6 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty(); i++) p.move(net.minecraft.core.Direction.UP);
      for (int i = 0; i < 48; i++) {
         p.move(net.minecraft.core.Direction.DOWN);
         var sh = level.getBlockState(p).getCollisionShape(level, p);
         if (!sh.isEmpty()) return p.getY() + sh.max(net.minecraft.core.Direction.Axis.Y);
      }
      return yRef;
   }

   private void dropLoot(ServerLevel level, Vec3 at) {
      if (lootDone) return;
      lootDone = true;
      List<ItemStack> loot = new ArrayList<>();
      loot.add(new ItemStack(HeraliszMere.RUIN_FANG.get()));
      loot.add(new ItemStack(HeraliszMere.RUIN_VENOM.get(), 1 + random.nextInt(2)));
      loot.add(new ItemStack(HeraliszMere.RUIN_EYE.get(), 2 + random.nextInt(2)));
      loot.add(new ItemStack(HeraliszMere.RUIN_SILK.get(), 8 + random.nextInt(7)));
      Vec3 p = at.add(0, 1.2, 0);
      for (ItemStack st : loot) {
         ItemEntity it = new ItemEntity(level, p.x, p.y, p.z, st);
         it.setDeltaMovement((random.nextDouble() - 0.5) * 0.25, 0.3, (random.nextDouble() - 0.5) * 0.25);
         it.setGlowingTag(true);
         it.setUnlimitedLifetime();
         level.addFreshEntity(it);
      }
      level.sendParticles(AMBER_FX, p.x, p.y, p.z, 30, 0.6, 0.4, 0.6, 0.05);
      level.playSound(null, p.x, p.y, p.z, Sounds.EGG_PLACE.get(), SoundSource.HOSTILE, 2F, 0.8F);
      for (Player pl : targets(80)) {
         if (pl instanceof ServerPlayer sp) {
            net.minecraft.world.entity.ExperienceOrb.award(level, pl.position(), 400);
            sp.displayClientMessage(Component.translatable("msg.heralisz_mere.victory").withStyle(net.minecraft.ChatFormatting.GOLD), true);
         }
      }
   }

   @Override
   public void remove(RemovalReason r) {
      if (!this.level().isClientSide) {
         discardBrood();
         for (Spiderling s : crew) if (s != null && s.isAlive()) s.discard();
         crew.clear();
      }
      bossBar.removeAllPlayers();
      super.remove(r);
   }

   // ------------------------------------------------------------------ sauvegarde
   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Phase", phase());
      tag.putBoolean("Dormant", dormant());
      if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
      if (hallPos != null) tag.put("Hall", NbtUtils.writeBlockPos(hallPos));
      if (!Float.isNaN(homeYaw)) tag.putFloat("HomeYaw", homeYaw);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      fromSave = true;
      setPhase(Math.max(1, tag.getInt("Phase")));
      if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
      if (tag.contains("Hall")) hallPos = NbtUtils.readBlockPos(tag.getCompound("Hall"));
      if (tag.contains("HomeYaw")) homeYaw = tag.getFloat("HomeYaw");
      if (tag.getBoolean("Dormant")) setDormant(true);
      else if (home != null) sleep();                                             // un combat interrompu : elle regagne son cocon
   }

   // ------------------------------------------------------------------ sons
   @Override
   protected SoundEvent getAmbientSound() {
      return dormant() ? null : Sounds.CHITTER.get();
   }

   @Override
   public int getAmbientSoundInterval() {
      return 160;
   }

   private int lastHurtSound;

   @Override
   protected void playHurtSound(DamageSource s) {
      if (this.tickCount - lastHurtSound < 40 || random.nextFloat() > 0.5F) return;
      lastHurtSound = this.tickCount;
      this.playSound(Sounds.HURT.get(), 2.5F, 0.9F + random.nextFloat() * 0.2F);
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource s) {
      return null;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected float getSoundVolume() {
      return 2.5F;
   }

   private int lastStepTick;

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      if (dormant() || this.tickCount - lastStepTick < 5) return;
      lastStepTick = this.tickCount;
      this.playSound(Sounds.STEP.get(), 1.6F, 0.85F + this.random.nextFloat() * 0.25F);
   }
}
