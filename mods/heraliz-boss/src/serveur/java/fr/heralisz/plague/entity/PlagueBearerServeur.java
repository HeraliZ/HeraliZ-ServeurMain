package fr.heralisz.plague.entity;

import fr.heralisz.plague.PlagueConfig;
import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.particle.ModParticles;
import fr.heralisz.plague.sound.Sounds;
import fr.heralisz.plague.fvfx.Vfx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Partie SERVEUR du Porteur de Peste (docs/METHODE_CLIENT_SERVEUR.md §5.1 et §5.5) : logique de jeu absente du jar client.
 * Elle porte À LA FOIS la logique du socle HeraliszBoss (IA, actions, phases, barre de boss, menace, PV virtuels, laisse, mort
 * scénarisée, sauvegarde ; Java n'a qu'un héritage) et celle de PlagueBearer (attaques, gaz, infectés, essaims, crochet, mort,
 * butin, salle). Créée par l'usine du code commun (HeraliszPlague.PLAGUE_BEARER_USINE) dans les mondes serveur seulement ;
 * ne jamais faire « new PlagueBearer(...) » côté serveur.
 */
public class PlagueBearerServeur extends PlagueBearer {
   public PlagueBearerServeur(EntityType<? extends PlagueBearer> type, Level level) {
      super(type, level);
   }

   // ================================================================== socle commun des boss (logique serveur de HeraliszBoss)
   protected final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.heralisz_plague." + animId), BossEvent.BossBarColor.GREEN,
      BossEvent.BossBarOverlay.PROGRESS);
   private final float[] dpsWindow = new float[20];
   protected final int[] cooldowns = new int[acts.length];
   protected final Map<UUID, Float> threat = new HashMap<>();
   protected final Set<UUID> hitOnce = new HashSet<>();
   protected BlockPos home;
   protected int globalCd = 30;
   private int lonelyTicks, peakPlayers = 1, peakHold;
   private boolean fromSave;
   public boolean devPassive;

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
         e -> !(e instanceof Player p && (p.isCreative() || p.isSpectator()))));
   }

   protected void setPhase(int p) {
      this.entityData.set(PHASE, p);
   }

   /** À appeler avant de l'ajouter au monde (ou pour le rendormir). */
   public void setDormant(boolean on) {
      this.entityData.set(DORMANT, on);
      if (on) {
         this.entityData.set(ACTION, 1);
         this.entityData.set(ACTION_TICK, clock());
         this.setTarget(null);
         this.getNavigation().stop();
      }
   }

   /** Le repère de la salle (là où il a été invoqué) ; null avant le premier tick. */
   public BlockPos home() {
      return home;
   }

   public void setHome(BlockPos p) {
      this.home = p;
   }

   /** Le rituel est accompli : il se réveille (son apparition joue depuis le début, cinématique comprise). */
   public void wake() {
      if (!dormant()) return;
      this.entityData.set(DORMANT, false);
      start(acts[1]);
   }

   /** Plus personne depuis longtemps : il rentre chez lui, se soigne et se rendort. */
   public void sleep() {
      if (this.level().isClientSide || this.isDeadOrDying()) return;
      stopAction((ServerLevel) this.level());
      bossBar.removeAllPlayers();
      this.setHealth(this.getMaxHealth());
      setPhase(1);
      this.setInvisible(false);
      threat.clear();
      peakPlayers = 1;
      if (home != null) this.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
      setDormant(true);
   }

   protected void start(Action a) {
      this.entityData.set(ACTION, a.ordinal());
      this.entityData.set(ACTION_TICK, clock() + 1);
      this.hitOnce.clear();
      this.getNavigation().stop();
   }

   private void stopAction(ServerLevel level) {
      Action a = action();
      if (a.ordinal() != 0) onActionEnd(level, a);
      this.entityData.set(ACTION, 0);
   }

   // ------------------------------------------------------------------ commandes de test
   public boolean devAction(String name) {
      for (Action a : acts) {
         if (a.name().equals(name)) {
            Player p = this.level().getNearestPlayer(this, 64);
            if (p != null) this.setTarget(p);
            start(a);
            return true;
         }
      }
      return false;
   }

   public void devPhase(int n) {
      setPhase(n);
      this.setHealth(this.getMaxHealth() * (n == 1 ? 1F : n == 2 ? 0.6F : 0.3F));
      this.entityData.set(ACTION, 0);
   }

   public void setDevPassive(boolean on) {
      devPassive = on;
   }

   public boolean devTransitioning() {
      int o = action().ordinal();
      return o == 2 || o == 3;
   }

   // ------------------------------------------------------------------ cycle de vie
   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide && this.tickCount == 0 && !fromSave) {
         if (home == null) home = this.blockPosition();
         if (!dormant()) start(acts[1]);
      }
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      ServerLevel level = (ServerLevel) this.level();
      if (dormant()) {
         this.setTarget(null);
         this.getNavigation().stop();
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
         this.entityData.set(VIRTUAL_MAX, (int) (virtualHpBase() * groupMult()));
         pickTarget();
      }
      ambient(level);
      LivingEntity target = this.getTarget();
      if (target != null && (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
         this.setTarget(null);
         target = null;
      }
      Action a = action();
      if (a.ordinal() != 0) {
         int t = actionTime();
         runAction(level, a, t, target);
         if (action() == a && t >= a.ticks()) {
            onActionEnd(level, a);
            Action next = chainAfter(a);
            if (next != null) {
               start(next);
               return;
            }
            this.setInvisible(false);
            this.entityData.set(ACTION, 0);
            globalCd = phase() == 3 ? 4 : phase() == 2 ? 7 : 10;
         }
         return;
      }
      float hp = this.getHealth() / this.getMaxHealth();
      if (phase() == 1 && hp <= 0.66F) {
         start(acts[2]);
         return;
      }
      if (phase() == 2 && hp <= 0.33F) {
         start(acts[3]);
         return;
      }
      if (devPassive || target == null) {
         this.getNavigation().stop();
         return;
      }
      this.getLookControl().setLookAt(target, 30F, 30F);
      turnToward(target, 9F);
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
      if (d > chaseDistance()) {
         if (this.tickCount % 6 == 0 || this.getNavigation().isDone()) this.getNavigation().moveTo(target, chaseSpeed(d));
      } else {
         this.getNavigation().stop();
      }
   }

   /** Tirage pondéré parmi les actions prêtes : pick(a1, w1, a2, w2, ...). */
   @SuppressWarnings("unchecked")
   protected Action pick(Object... aw) {
      List<Action> o = new ArrayList<>();
      List<Float> w = new ArrayList<>();
      for (int i = 0; i + 1 < aw.length; i += 2) {
         Action act = (Action) aw[i];
         float wt = ((Number) aw[i + 1]).floatValue();
         if (act != null && wt > 0 && cooldowns[act.ordinal()] == 0) {
            o.add(act);
            w.add(wt);
         }
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

   // ------------------------------------------------------------------ cibles, groupe
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

   public List<Player> targets(double r) {
      return this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(r),
         p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) <= r);
   }

   /** Le joueur le plus éloigné au-delà de 14 blocs : le tireur embusqué. */
   protected Player farthest(double r) {
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

   protected float groupMult() {
      return 1F + Math.max(0, peakPlayers - PlagueConfig.GROUP_BASE.get()) * PlagueConfig.GROUP_STEP.get().floatValue();
   }

   // ------------------------------------------------------------------ utilitaires de géométrie / effets
   protected void faceTarget(LivingEntity t) {
      faceTo(t.position());
   }

   protected void faceTo(Vec3 p) {
      float yaw = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90F;
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
   }

   protected void turnToward(LivingEntity t, float step) {
      float want = (float) (Mth.atan2(t.getZ() - getZ(), t.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
      this.yBodyRot = Mth.approachDegrees(this.yBodyRot, want, step);
      this.setYRot(this.yBodyRot);
   }

   protected Vec3 fwd() {
      return Vec3.directionFromRotation(0, this.yBodyRot);
   }

   protected Vec3 right() {
      Vec3 f = fwd();
      return new Vec3(-f.z, 0, f.x);
   }

   protected double surfaceY(Vec3 p) {
      return Ground.y(this.level(), p.x, p.y, p.z);
   }

   protected Vec3 surf(Vec3 p) {
      return new Vec3(p.x, surfaceY(p), p.z);
   }

   protected BlockState ground(Vec3 p) {
      BlockState s = this.level().getBlockState(BlockPos.containing(p.x, surfaceY(p) - 1, p.z));
      return s.isAir() ? Blocks.DIRT.defaultBlockState() : s;
   }

   public float dmg(float base) {
      return base * PlagueConfig.DAMAGE_MULT.get().floatValue() * (phase() == 3 ? 1.25F : 1F);
   }

   protected void sound(SoundEvent s, float vol, float pitch) {
      this.level().playSound(null, getX(), getY() + 2, getZ(), s, SoundSource.HOSTILE, vol, pitch);
   }

   /** Dégâts de zone sur les joueurs : cercle horizontal de rayon r autour de c ; renvoie les joueurs touchés. */
   protected List<Player> hitCircle(ServerLevel level, Vec3 c, double r, float damage, double push, double lift) {
      List<Player> hit = new ArrayList<>();
      for (Player p : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r, 3, r))) {
         if (p.isCreative() || p.isSpectator() || p.position().distanceToSqr(c.x, p.getY(), c.z) > r * r) continue;
         p.hurt(this.damageSources().mobAttack(this), dmg(damage));
         Vec3 d = new Vec3(p.getX() - c.x, 0, p.getZ() - c.z);
         d = d.lengthSqr() < 1e-4 ? fwd() : d.normalize();
         if (push > 0 || lift > 0) {
            p.setDeltaMovement(d.x * push, lift, d.z * push);
            p.hurtMarked = true;
         }
         hit.add(p);
      }
      return hit;
   }

   /** Joueurs dans un cône horizontal devant lui (demi-angle en degrés). */
   protected List<Player> inCone(double range, double halfDeg) {
      List<Player> out = new ArrayList<>();
      for (Player p : targets(range + 2)) {
         Vec3 to = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
         if (to.length() > range || (to.lengthSqr() > 1e-4 && fwd().dot(to.normalize()) < Math.cos(Math.toRadians(halfDeg)))) continue;
         out.add(p);
      }
      return out;
   }

   /** Ramène un point dans sa zone (sinon la laisse le renverrait chez lui, soigné). */
   protected Vec3 inLeash(Vec3 p) {
      if (home == null) return p;
      double r = PlagueConfig.LEASH_RADIUS.get() - 4;
      double hx = home.getX() + 0.5, hz = home.getZ() + 0.5, dx = p.x - hx, dz = p.z - hz, d = Math.sqrt(dx * dx + dz * dz);
      if (d <= r || d < 1e-3) return p;
      return surf(new Vec3(hx + dx / d * r, p.y, hz + dz / d * r));
   }

   /** Bond balistique scripté : appeler à chaque tick de l'envol (u = 0 → 1 sur `ticks`), hauteur de pointe h. */
   protected void flyArc(Vec3 from, Vec3 to, int tick, int ticks, double h) {
      double u = Math.min(1, (tick + 1) / (double) ticks);
      Vec3 next = from.add(to.subtract(from).scale(u)).add(0, h * 4 * u * (1 - u), 0);
      this.setDeltaMovement(next.subtract(this.position()));
      this.fallDistance = 0;
   }

   /** Butin qui jaillit (objets lumineux, longue durée) + XP. */
   protected void dropLoot(ServerLevel level, Vec3 c, List<ItemStack> loot, int xp) {
      for (ItemStack st : loot) {
         ItemEntity ie = new ItemEntity(level, c.x, c.y, c.z, st);
         double a = random.nextDouble() * Mth.TWO_PI;
         ie.setDeltaMovement(Math.cos(a) * 0.22, 0.42, Math.sin(a) * 0.22);
         ie.setDefaultPickUpDelay();
         ie.setExtendedLifetime();
         ie.setGlowingTag(true);
         level.addFreshEntity(ie);
      }
      grantXp(level, c, xp);
   }

   /**
    * XP du boss, versée directement (sans orbes ni bruit de ramassage) à chaque joueur présent dans la salle (64 blocs).
    */
   public static void grantXp(ServerLevel level, Vec3 c, int xp) {
      for (net.minecraft.server.level.ServerPlayer p : level.getPlayers(p -> !p.isSpectator() && p.position().distanceToSqr(c) < 64 * 64)) {
         p.giveExperiencePoints(xp);
      }
   }

   // ------------------------------------------------------------------ garde-fous
   private int strayTicks;

   /**
    * Garde-fous (toutes les secondes). Le combat ne se remet à zéro (PV pleins, phase 1) que si plus personne ne se bat dans
    * la salle depuis 30 s (joueurs morts ou partis). S'il sort de sa salle (poussé, coincé), il y revient au bout de 5 s,
    * sans se soigner : on ne perd jamais un combat parce qu'on l'a entraîné un peu loin.
    */
   private void leash(ServerLevel level) {
      if (home == null) return;
      int r = leashRange();
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(home).inflate(r), p -> !p.isCreative() && !p.isSpectator() && p.isAlive()).isEmpty();
      lonelyTicks = anyone ? 0 : lonelyTicks + 20;
      strayTicks = Math.sqrt(this.blockPosition().distSqr(home)) > r ? strayTicks + 20 : 0;
      if (lonelyTicks > 600) {
         stopAction(level);
         this.setTarget(null);
         this.teleportTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5);
         this.setHealth(this.getMaxHealth());
         setPhase(1);
         this.setInvisible(false);
         peakPlayers = 1;
         lonelyTicks = 0;
         strayTicks = 0;
      } else if (strayTicks > 100) {
         stopAction(level);
         this.teleportTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5);
         strayTicks = 0;
      }
   }

   /** Rayon de la salle autour du point d'apparition (au moins 64 blocs : les grandes arènes ne le font plus rentrer). */
   public static int leashRange() {
      return Math.max(64, PlagueConfig.LEASH_RADIUS.get());
   }

   private void updateBossBar(ServerLevel level) {
      for (ServerPlayer p : level.players()) {
         boolean near = p.distanceTo(this) < 64;
         if (near && !bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
         else if (!near && bossBar.getPlayers().contains(p)) bossBar.removePlayer(p);
      }
   }

   /** Multiplicateur des dégâts reçus pendant une action (0 = invulnérable). */
   protected float damageTaken(Action a, int t) {
      return 1F;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide || this.isDeadOrDying()) return false;
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
      Action a = action();
      int o = a.ordinal();
      if (o >= 1 && o <= 3) return false;
      float mult = damageTaken(a, actionTime());
      if (mult <= 0) return false;
      float raw = Math.min(amount, PlagueConfig.HIT_CAP.get().floatValue());
      Vec3 weak = weakPoint();
      if (WeakPoint.hit(source, this, weak, weakRadius())) {
         raw *= WeakPoint.CRIT;
         WeakPoint.feedback(this, source, weak);
      }
      float window = 0;
      for (float v : dpsWindow) window += v;
      if (window > PlagueConfig.DPS_CAP.get()) raw *= 0.25F;
      dpsWindow[this.tickCount % 20] += raw;
      raw *= mult;
      float scaled = raw * this.getMaxHealth() / Math.max(1F, virtualHpBase() * groupMult());
      this.invulnerableTime = 0;
      boolean ok = super.hurt(source, Math.max(scaled, 0.01F));
      this.invulnerableTime = 0;
      if (ok && source.getEntity() instanceof Player pl) threat.merge(pl.getUUID(), raw, Float::sum);
      return ok;
   }

   // ------------------------------------------------------------------ mort
   @Override
   protected void tickDeath() {
      ++this.deathTime;
      if (this.level().isClientSide) return;
      ServerLevel level = (ServerLevel) this.level();
      if (this.deathTime == 1) {
         stopAction(level);
         this.setInvisible(false);
         bossBar.setProgress(0);
      }
      deathTick(level, this.deathTime);
      if (this.deathTime >= deathTicks()) {
         bossBar.removeAllPlayers();
         this.remove(RemovalReason.KILLED);
      }
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Phase", phase());
      tag.putBoolean("BossSaved", true);
      tag.putBoolean("Dormant", dormant());
      if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      fromSave = tag.getBoolean("BossSaved");
      if (tag.getBoolean("Dormant")) setDormant(true);
      setPhase(Math.max(1, tag.getInt("Phase")));
      this.setInvisible(false);
      if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
   }

   // ================================================================== le Porteur de Peste
   private record GasZone(Vec3 c, float r, int until) {}

   private record Strike(Vec3 at, int tick, float r, float dmg) {}

   private final List<GasZone> gas = new ArrayList<>();
   private final List<Strike> strikes = new ArrayList<>();
   private final List<Zombie> infected = new ArrayList<>();
   private final List<PlagueSwarm> swarms = new ArrayList<>();
   private Player prey;
   private Vec3 hookAt, flyFrom, flyTo, chargeDir = Vec3.ZERO;
   private int hookArrive = -1;
   private boolean hooked, hookClear, landed;

   protected int virtualHpBase() {
      return PlagueConfig.VIRTUAL_HP.get();
   }

   public int deathTicks() {
      return REMAINS;
   }

   protected double chaseDistance() {
      return 6;
   }

   protected double chaseSpeed(double d) {
      return phase() == 3 ? 1.35 : d > 18 ? 1.15 : 1.0;
   }

   // ------------------------------------------------------------------ repères du corps (modèle x RS, en blocs)
   private Vec3 at(double front, double right, double up) {
      return this.position().add(fwd().scale(front)).add(right().scale(right)).add(0, up, 0);
   }

   /** Point faible : la tête sous le masque à gaz. */
   protected Vec3 weakPoint() {
      return mouth().add(0, 0.5, 0);
   }

   protected double weakRadius() {
      return 1.3;
   }

   /** D'où sort la bile : le filtre du masque (phases 1-2), la mâchoire nue (phase 3). */
   private Vec3 mouth() {
      return phase() == 3 ? at(1.8, 0, 4.0) : at(2.4, 0, 3.3);
   }

   private Vec3 clawPos() {
      return at(1.6, 2.2, 0.2);
   }

   /** Main gauche (le crochet, les grenades). */
   public Vec3 leftHand() {
      return at(1.1, -1.4, 3.4);
   }

   private Vec3 tanksTop() {
      return at(-1.1, 0, 4.8);
   }

   // ------------------------------------------------------------------ la Station de Quarantaine (la salle)
   private net.minecraft.core.BlockPos hallPos;
   private float homeYaw = 180F;

   public void setHall(net.minecraft.core.BlockPos p) {
      hallPos = p;
   }

   public void setHome(net.minecraft.core.BlockPos p, float yaw) {
      setHome(p);
      homeYaw = yaw;
   }

   public fr.heralisz.plague.dungeon.StationHallBEServeur hall() {
      if (hallPos != null && this.level().getBlockEntity(hallPos) instanceof fr.heralisz.plague.dungeon.StationHallBEServeur h) return h;
      fr.heralisz.plague.dungeon.StationHallBEServeur h = fr.heralisz.plague.dungeon.StationHallBEServeur.nearestServeur(this.level(), this.blockPosition(), 160);
      if (h != null) hallPos = h.getBlockPos();
      return h;
   }

   // ------------------------------------------------------------------ IA
   protected Action choose(LivingEntity target, double d) {
      int ph = phase();
      Player far = farthest(56);
      boolean sniped = far != null && far.distanceTo(this) > 22;
      infected.removeIf(z -> !z.isAlive());
      // un tireur planqué derrière un mur ou un pilier : il le déloge (grenades de bile en cloche, essaim, saut)
      boolean hid = target != null && !this.hasLineOfSight(target);
      return pick(
         Action.CLAW, d < 7 ? 3F : 0F,
         Action.SLAM, d < 9 ? 2F : 0F,
         Action.BILE, hid ? 0F : d < 15 ? 2.2F : 0F,
         Action.GRENADES, hid ? 4.5F : d > 8 ? (sniped ? 2.6F : 1.8F) : 0.5F,
         Action.HOOK, hid ? 0.2F : sniped ? 3F : d > 12 ? 1.5F : 0F,
         Action.CHARGE, d > 10 && d < 32 ? 1.6F : 0F,
         Action.CLANG, infected.size() < 4 + 2 * ph ? 1.3F : 0F,
         Action.SWARM, ph >= 2 ? (hid ? 3.5F : sniped ? 3F : 1.5F) : 0F,
         Action.PUSTULES, ph >= 2 && d > 6 ? 2F : 0F,
         Action.SPORES, ph == 3 ? 2F : 0F,
         Action.PANDEMIC, ph == 3 && d < 24 ? 1.8F : 0F,
         Action.LEAP, ph == 3 ? (hid ? 3.5F : sniped ? 2.6F : d > 12 ? 1.4F : 0F) : 0F);
   }

   protected int cooldownOf(Action a) {
      boolean fast = phase() == 3;
      return switch (a) {
         case CLAW -> 20;
         case SLAM -> 60;
         case BILE -> fast ? 160 : 200;
         case GRENADES -> fast ? 120 : 160;
         case HOOK -> fast ? 180 : 240;
         case CHARGE -> 220;
         case CLANG -> 500;
         case SWARM -> 300;
         case PUSTULES -> 180;
         case SPORES -> 380;
         case PANDEMIC -> 320;
         case LEAP -> 240;
         default -> 0;
      };
   }

   protected Action chainAfter(Action a) {
      if (a == Action.HOOK) return hooked ? Action.HAUL : hookAt != null ? Action.GRAPPLE : null;
      return null;
   }

   protected void onActionEnd(ServerLevel level, Action a) {
      if (a == Action.HAUL || a == Action.GRAPPLE) {
         hooked = false;
         hookAt = null;
      }
   }

   protected void ambient(ServerLevel level) {
      int ph = phase();
      // nappes de gaz : on s'y empoisonne tant qu'on reste dedans
      gas.removeIf(g -> g.until() < this.tickCount);
      if (this.tickCount % 10 == 0) {
         for (GasZone g : gas) {
            for (Player p : targets(80)) {
               if (p.position().distanceToSqr(g.c().x, p.getY(), g.c().z) > g.r() * g.r() || Math.abs(p.getY() - g.c().y) > 3) continue;
               p.hurt(this.damageSources().indirectMagic(this, this), dmg(2.5F));
               p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
               p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            }
         }
      }
      if (this.tickCount % 6 == 0) {
         for (GasZone g : gas) level.sendParticles(ModParticles.TOXIC_SMOKE.get(), g.c().x, g.c().y + 0.6, g.c().z, 2, g.r() * 0.45, 0.4, g.r() * 0.45, 0.01);
      }
      // phase 2+ : il suinte la peste (au corps à corps on s'empoisonne)
      if (ph >= 2 && this.tickCount % 10 == 0 && !this.isDeadOrDying()) {
         for (Player p : targets(ph == 3 ? 6 : 4.5)) {
            p.hurt(this.damageSources().indirectMagic(this, this), dmg(1.5F));
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0));
         }
      }
      if (this.tickCount % 4 == 0 && !this.isDeadOrDying()) {
         Vec3 m = mouth();
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), m.x, m.y, m.z, 1, 0.2, 0.1, 0.2, 0.01);
         if (ph >= 2) {
            Vec3 tk = tanksTop();
            level.sendParticles(ModParticles.TOXIC_SMOKE.get(), tk.x, tk.y, tk.z, 2, 0.6, 0.3, 0.6, 0.03);
         }
         if (ph == 3) level.sendParticles(ModParticles.SPORE.get(), getX(), getY() + 4, getZ(), 2, 1.5, 1.2, 1.5, 0.01);
      }
      // cosses de spores programmées
      strikes.removeIf(s -> {
         if (s.tick() > this.tickCount) return false;
         sporeImpact(level, s.at(), s.r(), s.dmg());
         return true;
      });
      swarms.removeIf(s -> !s.isAlive());
      // les infectés : en phase 2+ ils crèvent en gaz, en phase 3 ils éclatent au contact
      infected.removeIf(z -> {
         if (z.isAlive()) {
            LivingEntity zt = z.getTarget();
            if (ph == 3 && zt != null && z.distanceTo(zt) < 2.0F) {
               burst(level, z);
               return true;
            }
            return false;
         }
         if (ph >= 2 && z.isDeadOrDying()) addGas(level, surf(z.position()), 2.6F, 120);
         return true;
      });
   }

   private void addGas(ServerLevel level, Vec3 c, float r, int dur) {
      if (gas.size() >= 24) gas.remove(0);
      gas.add(new GasZone(c, r, this.tickCount + dur));
      Vfx.gas(level, c, r, dur, GAS);
   }

   protected void runAction(ServerLevel level, Action a, int t, LivingEntity target) {
      switch (a) {
         case SPAWN -> spawnSeq(level, t);
         case PHASE2 -> phase2(level, t);
         case PHASE3 -> phase3(level, t);
         case CLAW -> claw(level, t, target);
         case SLAM -> slam(level, t, target);
         case GRENADES -> grenades(level, t, target);
         case HOOK -> hook(level, t, target);
         case HAUL -> haul(level, t);
         case GRAPPLE -> grapple(level, t);
         case BILE -> bile(level, t, target);
         case CHARGE -> charge(level, t, target);
         case CLANG -> clang(level, t);
         case SWARM -> swarm(level, t, target);
         case PUSTULES -> pustules(level, t, target);
         case SPORES -> spores(level, t);
         case PANDEMIC -> pandemic(level, t, target);
         case LEAP -> leap(level, t, target);
         default -> {
         }
      }
   }

   private void roar(ServerLevel level, float pitch) {
      sound((phase() == 3 ? Sounds.PB_ROAR_OPEN : Sounds.PB_ROAR).get(), 4F, 0.9F + pitch * 0.2F);
      sound(SoundEvents.RAVAGER_ROAR, 1.6F, pitch);
      Vfx.shake(level, this.position(), 1.0F, 40, 20);
   }

   private void impactFx(ServerLevel level, Vec3 at, float r) {
      Vfx.debris(level, at, r * 0.4F, 7F, 26, ground(at), Vec3.ZERO);
      Vfx.shockwave(level, at, r * 1.8F, 14, GAS);
      Vfx.shake(level, at, 1.1F, 36, 14);
      level.sendParticles(ModParticles.DUST.get(), at.x, at.y + 0.3, at.z, 40, r * 0.4, 0.3, r * 0.4, 0.08);
      level.sendParticles(ModParticles.TOXIC_SMOKE.get(), at.x, at.y + 0.5, at.z, 20, r * 0.4, 0.3, r * 0.4, 0.05);
      level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2F, 0.6F);
      level.playSound(null, at.x, at.y, at.z, Sounds.PB_GAS_BURST.get(), SoundSource.HOSTILE, 3F, 0.7F);
   }

   // ------------------------------------------------------------------ cinématiques
   /**
    * Apparition : une nappe de gaz se lève, il est agenouillé dedans, immobile ; les verres de son masque s'allument, il se
    * relève en traînant son crochet, abat sa griffe sur le sol (le souffle chasse le gaz) et rugit à travers son masque.
    */
   private void spawnSeq(ServerLevel level, int t) {
      Vec3 o = surf(this.position());
      if (t == 1) {
         Vfx.cinematic(level, o, this.getId(), SPAWN_K, Action.SPAWN.ticks);
         Vfx.gas(level, o, 9F, 180, GAS);
         sound(Sounds.PB_HISS.get(), 2.5F, 0.6F);
      }
      if (t == 50) sound(Sounds.PB_BREATH.get(), 3F, 0.85F);
      if (t < 110 && t % 16 == 0) sound(SoundEvents.WARDEN_HEARTBEAT, 2.5F, 0.6F);
      if (t < 110 && t % 34 == 10) sound(Sounds.PB_BREATH.get(), 2.2F, 0.7F);     // le souffle lent sous le masque, endormi
      if (t == 70) sound(Sounds.PB_HISS.get(), 3F, 0.5F);
      if (t < 164 && t % 3 == 0) level.sendParticles(ModParticles.TOXIC_SMOKE.get(), o.x, o.y + 0.8, o.z, 6, 5, 0.6, 5, 0.01);
      if (t == 111) {
         Vfx.flash(level, at(1.9, 0, 2.3), 2.5F, 6, 0x8CF055);
         sound(SoundEvents.BEACON_ACTIVATE, 2F, 0.5F);
      }
      if (t > 120 && t < 150 && t % 6 == 0) {
         Vec3 h = at(1.6, -1.4, 0.1);
         level.playSound(null, h.x, h.y, h.z, SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.4F, 0.5F);
         level.sendParticles(ParticleTypes.CRIT, h.x, h.y + 0.1, h.z, 4, 0.2, 0.05, 0.2, 0.1);
      }
      if (t == 167) {
         Vec3 c = surf(clawPos());
         impactFx(level, c, 6F);
         Vfx.shockwave(level, o, 18F, 22, GAS);
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), o.x, o.y + 0.6, o.z, 150, 6, 0.5, 6, 0.25);
      }
      if (t == 174) roar(level, 0.5F);
   }

   /** Phase 2 : il convulse, les bonbonnes gonflent et explosent ; le souffle repousse tout le monde ; il hurle. */
   private void phase2(ServerLevel level, int t) {
      if (t == 1) {
         Vfx.cinematic(level, this.position(), this.getId(), PHASE2_K, Action.PHASE2.ticks);
         sound(SoundEvents.RAVAGER_HURT, 3F, 0.5F);
      }
      if (t > 4 && t < 82 && t % 4 == 0) {
         Vec3 tk = tanksTop();
         if (t % 12 == 8) sound(Sounds.PB_HISS.get(), 2F, 0.7F + t * 0.01F);
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), tk.x, tk.y, tk.z, 3 + t / 12, 0.4, 0.2, 0.4, 0.05);
      }
      if (t == 82) {
         setPhase(2);
         Vec3 tk = tanksTop();
         Vfx.flash(level, tk, 5F, 8, 0xB8FF6A);
         Vfx.debris(level, tk, 1.2F, 7F, 26, Blocks.GREEN_TERRACOTTA.defaultBlockState(), Vec3.ZERO);
         Vfx.debris(level, tk, 1.0F, 6F, 12, Blocks.IRON_BLOCK.defaultBlockState(), Vec3.ZERO);
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), tk.x, tk.y, tk.z, 80, 2, 1.5, 2, 0.15);
         level.sendParticles(ModParticles.ACID_DROP.get(), tk.x, tk.y, tk.z, 40, 1.5, 1, 1.5, 0.3);
         Vec3 o = surf(this.position());
         Vfx.shockwave(level, o, 16F, 20, GAS);
         Vfx.shake(level, o, 1.4F, 44, 20);
         addGas(level, o, 7F, 240);
         sound(Sounds.PB_TANK_BURST.get(), 5F, 1F);
         sound(SoundEvents.GENERIC_EXPLODE, 2F, 0.6F);
         for (Player p : targets(14)) {
            Vec3 d = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            double k = 1 - d.length() / 16;
            d = d.lengthSqr() < 1e-4 ? fwd() : d.normalize();
            p.setDeltaMovement(d.x * 2.0 * k, 0.5 * k + 0.2, d.z * 2.0 * k);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
         }
      }
      if (t == 102) roar(level, 0.6F);
   }

   /** Phase 3 : la griffe agrippe le masque, tire, l'arrache ; il rugit, la mâchoire nue, et crache un nuage de spores. */
   private void phase3(ServerLevel level, int t) {
      if (t == 1) {
         Vfx.cinematic(level, this.position(), this.getId(), PHASE3_K, Action.PHASE3.ticks);
         sound(SoundEvents.RAVAGER_HURT, 3F, 0.4F);
      }
      if (t == 35) sound(Sounds.PB_MASK_RIP.get(), 3F, 0.8F);
      if (t > 35 && t < 102 && t % 12 == 0) sound(Sounds.PB_HURT.get(), 2.5F, 0.8F + random.nextFloat() * 0.1F);
      if (t == 104) {
         setPhase(3);
         Vec3 h = at(1.9, 0.4, 4.3);
         Vfx.debris(level, h, 0.6F, 5F, 14, Blocks.BLACK_CONCRETE.defaultBlockState(), fwd().scale(0.3).add(right().scale(0.6)));
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), h.x, h.y, h.z, 30, 0.5, 0.5, 0.5, 0.2);
         level.sendParticles(ModParticles.ACID_DROP.get(), h.x, h.y, h.z, 20, 0.4, 0.3, 0.4, 0.2);
         Vfx.flash(level, h, 3F, 6, 0xC8FF8A);
         sound(Sounds.PB_MASK_RIP.get(), 3.5F, 1.1F);
      }
      if (t == 112) {
         roar(level, 0.45F);
         Vec3 o = surf(this.position());
         level.sendParticles(ModParticles.SPORE.get(), getX(), getY() + 4, getZ(), 120, 2.5, 2, 2.5, 0.15);
         sound(Sounds.PB_SPORES.get(), 4F, 0.8F);
         Vfx.shockwave(level, o, 12F, 18, SPORE);
         addGas(level, o, 6F, 200);
      }
   }

   // ------------------------------------------------------------------ phase 1
   private void claw(ServerLevel level, int t, LivingEntity target) {
      if (t < 10 && target != null) faceTarget(target);
      if (t == 3) Vfx.cone(level, this.position(), fwd(), 8.5F, 150F, 11);
      if (t == 9) sound(Sounds.PB_HURT.get(), 2.5F, 0.7F);
      if (t == 14) {
         sound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.5F, 0.5F);
         Vfx.slash(level, this.position().add(0, 1.4, 0), fwd(), 7.5F, 150F, -1);
         for (int k = -3; k <= 3; k++) {
            Vec3 p = surf(this.position().add(fwd().yRot(k * 0.35F).scale(5.5)));
            Vfx.debris(level, p, 0.5F, 3.5F, 3, ground(p), fwd().yRot(k * 0.35F).scale(0.4));
         }
         for (Player p : inCone(8.5, 75)) {
            p.hurt(this.damageSources().mobAttack(this), dmg(20));
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
            Vec3 d = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ()).normalize();
            p.setDeltaMovement(d.x * 1.5, 0.5, d.z * 1.5);
            p.hurtMarked = true;
         }
      }
   }

   private void slam(ServerLevel level, int t, LivingEntity target) {
      if (t < 12 && target != null) faceTarget(target);
      Vec3 at = surf(clawPos().add(fwd().scale(0.8)));
      if (t == 4) Vfx.circle(level, at, 6.5F, 16);
      if (t == 20) {
         impactFx(level, at, 6.5F);
         for (Player p : hitCircle(level, at, 6.5, 24, 1.2, 0.9)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
         if (phase() >= 2) addGas(level, at, 4F, 160);
      }
   }

   /** Trois grenades à gaz en cloche (la première sur le tireur le plus éloigné) : nappes de gaz qui stagnent 9 s. */
   private void grenades(ServerLevel level, int t, LivingEntity target) {
      if (t < 12 && target != null) faceTarget(target);
      if (t == 16 || t == 26 || t == 36) {
         Player far = farthest(56);
         List<Player> ps = targets(56);
         LivingEntity who = t == 16 && far != null ? far : !ps.isEmpty() ? ps.get(random.nextInt(ps.size())) : target;
         if (who == null) return;
         faceTarget(who);
         Vec3 aim = surf(who.position().add(who.getDeltaMovement().multiply(16, 0, 16)));
         Vec3 from = leftHand().add(0, 1.2, 0);
         int flight = (int) Mth.clamp(from.distanceTo(aim) * 0.65, 14, 34);
         PlagueGlob g = new PlagueGlobServeur(level, this, PlagueGlob.CANISTER);
         g.launch(from, aim, flight);
         level.addFreshEntity(g);
         Vfx.circle(level, aim, 4.5F, flight);
         sound(SoundEvents.WITCH_THROW, 2.5F, 0.4F);
      }
   }

   /** Impact d'un projectile du Porteur (appelé par PlagueGlob). */
   public void globImpact(ServerLevel level, Vec3 at, int kind) {
      if (kind == PlagueGlob.CANISTER) {
         level.playSound(null, at.x, at.y, at.z, Sounds.PB_GAS_BURST.get(), SoundSource.HOSTILE, 3F, 0.9F + random.nextFloat() * 0.2F);
         Vfx.flash(level, at.add(0, 0.8, 0), 3F, 5, 0xB8FF6A);
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), at.x, at.y + 0.6, at.z, 40, 1.5, 0.6, 1.5, 0.06);
         for (Player p : hitCircle(level, at, 3, 8, 0.5, 0.3)) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
         addGas(level, at, 4.5F, 180);
      } else {
         level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 2F, 0.5F);
         level.playSound(null, at.x, at.y, at.z, Sounds.PB_BILE.get(), SoundSource.HOSTILE, 1.5F, 1.3F);
         level.sendParticles(ModParticles.ACID_SPLASH.get(), at.x, at.y + 0.2, at.z, 16, 1.2, 0.1, 1.2, 0);
         level.sendParticles(ModParticles.ACID_DROP.get(), at.x, at.y + 0.5, at.z, 20, 0.8, 0.4, 0.8, 0.25);
         for (Player p : hitCircle(level, at, 2.6, 10, 0.4, 0.3)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
         addGas(level, at, 2.6F, 110);
      }
   }

   /**
    * Crochet : il fait tournoyer le crochet (fil de visée sur le tireur le plus éloigné), le lance. Ligne dégagée : le joueur est
    * accroché puis ramené (HAUL). Caché derrière un obstacle ou raté : le crochet mord le mur / le sol et il s'y hisse (GRAPPLE).
    */
   private void hook(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         hooked = false;
         hookAt = null;
         hookArrive = -1;
         Player far = farthest(44);
         prey = far != null ? far : target instanceof Player tp ? tp : null;
      }
      if (prey == null || !prey.isAlive()) return;
      if (t < 18) faceTarget(prey);
      if (t == 3) Vfx.chain(level, leftHand(), this.getId(), 0, 15, prey.getId());
      if (t == 4 || t == 12) sound(Sounds.PB_CHAIN.get(), 2.5F, 0.9F + t * 0.01F);
      if (t == 18) {
         Vec3 from = leftHand();
         Vec3 eye = prey.getEyePosition().add(0, -0.4, 0);
         HitResult hr = level.clip(new ClipContext(from, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
         hookClear = hr.getType() == HitResult.Type.MISS && from.distanceTo(eye) < 46;
         hookAt = hookClear ? eye : hr.getLocation();
         int flight = (int) Mth.clamp(from.distanceTo(hookAt) / 3.5, 3, 11);
         hookArrive = t + flight;
         Vfx.chain(level, from, this.getId(), 1, flight + 1, hookAt);
         sound(SoundEvents.TRIDENT_THROW, 2.5F, 0.5F);
         sound(Sounds.PB_CHAIN.get(), 3F, 1.2F);
      }
      if (t == hookArrive && hookAt != null) {
         if (hookClear && prey.position().add(0, 1, 0).distanceTo(hookAt) < 3.2) {
            hooked = true;
            prey.hurt(this.damageSources().mobAttack(this), dmg(6));
            level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), Sounds.PB_HOOK_HIT.get(), SoundSource.HOSTILE, 3F, 1F);
            Vfx.chain(level, leftHand(), this.getId(), 2, Action.HAUL.ticks - 8 + (Action.HOOK.ticks - t), prey.getId());
         } else {
            hookAt = inLeash(surf(hookAt));
            Vfx.chain(level, leftHand(), this.getId(), 3, Action.GRAPPLE.ticks - 8 + (Action.HOOK.ticks - t), hookAt);
            Vfx.debris(level, hookAt, 0.5F, 3F, 6, ground(hookAt), Vec3.ZERO);
            level.playSound(null, hookAt.x, hookAt.y, hookAt.z, Sounds.PB_HOOK_HIT.get(), SoundSource.HOSTILE, 3F, 0.8F);
         }
      }
   }

   /** Le joueur accroché est ramené en trois secousses, puis cueilli d'un uppercut de la griffe. */
   private void haul(ServerLevel level, int t) {
      if (prey == null || !prey.isAlive()) return;
      if (t < 30) faceTarget(prey);
      if (t < 28) {
         Vec3 dest = this.position().add(fwd().scale(2.8)).add(0, 0.4, 0);
         Vec3 d = dest.subtract(prey.position());
         if (d.length() > 1.4) {
            double k = t % 8 < 3 ? 1.25 : 0.5;
            Vec3 v = d.normalize().scale(Math.min(1.2, d.length() * 0.2 + 0.3) * k);
            prey.setDeltaMovement(v.x, Math.max(0.1, v.y + 0.12), v.z);
            prey.hurtMarked = true;
            prey.fallDistance = 0;
         }
      }
      if (t == 6 || t == 14 || t == 22) sound(Sounds.PB_CHAIN.get(), 2.5F, 0.8F);
      if (t == 30) {
         sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.5F, 0.5F);
         Vec3 f = fwd();
         for (Player p : inCone(5.5, 60)) {
            p.hurt(this.damageSources().mobAttack(this), dmg(22));
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            p.setDeltaMovement(f.x * 0.8, 1.2, f.z * 0.8);
            p.hurtMarked = true;
         }
         hooked = false;
      }
   }

   /** Le crochet a mordu près de sa proie : il est arraché du sol, traverse les airs et s'écrase griffe la première. */
   private void grapple(ServerLevel level, int t) {
      if (hookAt == null) return;
      if (t == 0) {
         flyFrom = this.position();
         flyTo = hookAt;
         if (flyTo.distanceTo(flyFrom) > 34) flyTo = inLeash(surf(flyFrom.add(flyTo.subtract(flyFrom).normalize().scale(34))));
         landed = false;
         faceTo(flyTo);
         Vfx.circle(level, flyTo, 6F, 26);
         sound(Sounds.PB_CHAIN.get(), 3F, 0.7F);
      }
      if (flyTo == null) return;
      if (t < 6) faceTo(flyTo);
      if (t == 6) {
         Vfx.debris(level, surf(this.position()), 1.6F, 5F, 12, ground(this.position()), Vec3.ZERO);
         sound(SoundEvents.PHANTOM_SWOOP, 2F, 0.6F);
      }
      if (t >= 6 && t < 26) flyArc(flyFrom, flyTo, t - 6, 20, 4 + 0.12 * flyFrom.distanceTo(flyTo));
      if (!landed && t >= 25 && (this.onGround() || t >= 30)) {
         landed = true;
         Vec3 at = surf(this.position());
         impactFx(level, at, 6F);
         for (Player p : hitCircle(level, at, 6, 22, 1.3, 0.8)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
         addGas(level, at, 5F, 180);
      }
   }

   /** Jet de bile : il se cambre (0,6 s) puis vomit un jet qui balaie devant lui ; flaques de gaz là où ça retombe. */
   private void bile(ServerLevel level, int t, LivingEntity target) {
      if (t < 12 && target != null) faceTarget(target);
      if (t == 2) Vfx.cone(level, this.position(), fwd(), 15F, 60F, 12);
      if (t == 4) sound(Sounds.PB_BREATH.get(), 3F, 1.1F);
      if (t == 13) sound(Sounds.PB_BILE.get(), 4F, 0.9F);
      if (t == 34) sound(Sounds.PB_BILE.get(), 3.5F, 0.8F);
      if (t < 14 || t > 56) return;
      float env = Math.min(1F, Math.min((t - 14.4F) / 5F, (56F - t) / 5F));
      float sweep = 28.6F * Mth.sin((t - 14.4F) / 20F * 2.6F) * env;
      Vec3 dir = fwd().yRot(sweep * Mth.DEG_TO_RAD);
      Vec3 m = mouth();
      if (t % 2 == 0) Vfx.bile(level, m, dir.add(0, -0.3, 0), 15F, 22F, 8);
      if (t % 4 == 0) {
         for (Player p : targets(17)) {
            Vec3 flat = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            if (flat.length() > 15.5 || flat.lengthSqr() < 1e-4 || dir.dot(flat.normalize()) < Math.cos(Math.toRadians(15))) continue;
            p.hurt(this.damageSources().indirectMagic(this, this), dmg(4F));
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
         }
      }
      if (t % 8 == 0) addGas(level, surf(this.position().add(dir.scale(5 + random.nextDouble() * 9))), 2.4F, 120);
   }

   /** Charge sur les phalanges : couloir annoncé, il fonce (1,5 s) en renversant tout sur son passage. */
   private void charge(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         Player far = farthest(40);
         LivingEntity who = far != null && far.distanceTo(this) < 32 ? far : target;
         if (who != null) faceTarget(who);
         chargeDir = fwd();
         Vfx.line(level, surf(this.position()), chargeDir, 28F, 4.2F, 15);
         sound(SoundEvents.RAVAGER_ROAR, 2F, 0.6F);
      }
      if (t >= 15 && t < 44) {
         if (t == 15) {
            hitOnce.clear();
            sound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.5F);
         }
         Vec3 ahead = this.position().add(chargeDir.scale(2));
         if (home != null && inLeash(ahead).distanceTo(ahead) > 0.5) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1, 0.3));
            return;
         }
         double sp = t < 19 ? 0.5 + (t - 15) * 0.12 : 0.95;
         this.setDeltaMovement(chargeDir.x * sp, this.getDeltaMovement().y, chargeDir.z * sp);
         faceTo(this.position().add(chargeDir));
         if (t % 4 == 0) {
            Vec3 g = surf(this.position());
            Vfx.debris(level, g, 0.9F, 3.5F, 5, ground(g), chargeDir.scale(-0.5));
            level.playSound(null, g.x, g.y, g.z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 2F, 0.6F);
            Vfx.shake(level, g, 0.4F, 16, 4);
         }
         for (Player p : targets(5)) {
            if (hitOnce.contains(p.getUUID()) || p.distanceToSqr(this.getX(), p.getY(), this.getZ()) > 3.0 * 3.0) continue;
            hitOnce.add(p.getUUID());
            Vec3 side = new Vec3(-chargeDir.z, 0, chargeDir.x);
            if (side.dot(p.position().subtract(this.position())) < 0) side = side.scale(-1);
            p.hurt(this.damageSources().mobAttack(this), dmg(22));
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
            p.setDeltaMovement(chargeDir.x * 1.3 + side.x, 0.8, chargeDir.z * 1.3 + side.z);
            p.hurtMarked = true;
         }
         if (t > 19 && this.horizontalCollision) {
            Vec3 w = this.position().add(chargeDir.scale(2)).add(0, 2, 0);
            Vfx.debris(level, w, 1.4F, 6F, 18, ground(this.position()), chargeDir.scale(-0.5));
            Vfx.shake(level, w, 1.2F, 30, 14);
            sound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 3F, 0.5F);
            this.setDeltaMovement(Vec3.ZERO);
            chargeDir = Vec3.ZERO;
         }
         return;
      }
      if (t >= 44) this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1, 0.6));
   }

   /** Il frappe trois fois son crochet contre ses bonbonnes : le vacarme réveille les infectés autour de chaque joueur. */
   private void clang(ServerLevel level, int t) {
      if (t == 12 || t == 22 || t == 32) {
         Vec3 tk = tanksTop();
         sound(Sounds.PB_CLANG.get(), 5F, 0.9F + t * 0.004F);
         level.sendParticles(ParticleTypes.CRIT, tk.x, tk.y, tk.z, 10, 0.4, 0.4, 0.4, 0.3);
         Vfx.shockwave(level, surf(this.position()), 10F + t * 0.3F, 12, 0xE8E8D8);
      }
      if (t == 40) {
         roar(level, 0.7F);
         List<Player> ps = targets(48);
         int n = 0, cap = 4 + 2 * phase();
         for (Player p : ps) {
            for (int k = 0; k < 2 && infected.size() < cap; k++, n++) {
               double a = random.nextDouble() * Mth.TWO_PI, r = 4 + random.nextDouble() * 3;
               riseInfected(level, surf(p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r)), p);
            }
         }
         if (n == 0 && infected.size() < cap) riseInfected(level, surf(this.position().add(fwd().scale(5))), null);
      }
   }

   /** Un infecté (zombie à la combinaison NBC) s'extirpe du sol. */
   private void riseInfected(ServerLevel level, Vec3 at, Player target) {
      Zombie z = EntityType.ZOMBIE.create(level);
      if (z == null) return;
      z.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0);
      z.setCustomName(Component.translatable("entity.heralisz_bosses.plague_infected"));
      ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
      helmet.getOrCreateTagElement("display").putInt("color", 0x4A5226);
      z.setItemSlot(EquipmentSlot.HEAD, helmet);
      z.setDropChance(EquipmentSlot.HEAD, 0F);
      AttributeInstance speed = z.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null) speed.setBaseValue(phase() == 3 ? 0.32 : 0.28);
      if (target != null) z.setTarget(target);
      level.addFreshEntity(z);
      infected.add(z);
      Vfx.debris(level, at, 0.8F, 3F, 8, ground(at), Vec3.ZERO);
      level.sendParticles(ModParticles.TOXIC_SMOKE.get(), at.x, at.y + 0.3, at.z, 10, 0.6, 0.2, 0.6, 0.02);
      level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.6F);
      level.playSound(null, at.x, at.y, at.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
   }

   /** Phase 3 : un infecté gonflé éclate au contact d'un joueur. */
   private void burst(ServerLevel level, Zombie z) {
      Vec3 at = surf(z.position());
      level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 2F, 0.4F);
      level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.3F);
      level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()), at.x, at.y + 1, at.z, 30, 0.5, 0.6, 0.5, 0.2);
      for (Player p : hitCircle(level, at, 3.2, 10, 0.8, 0.4)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
      addGas(level, at, 3F, 140);
      z.discard();
   }

   // ------------------------------------------------------------------ phase 2
   /** Il se cambre, ses bonbonnes éventrées vomissent des essaims de mouches qui traquent les tireurs. */
   private void swarm(ServerLevel level, int t, LivingEntity target) {
      if (t == 6) roar(level, 0.7F);
      if (t == 20) {
         Vec3 from = tanksTop().add(0, 0.5, 0);
         Player far = farthest(56);
         List<Player> ps = targets(56);
         int n = Math.min(3, Math.max(1, (ps.size() + 1) / 2));
         for (int i = 0; i < n; i++) {
            LivingEntity who = i == 0 && far != null ? far : !ps.isEmpty() ? ps.get(random.nextInt(ps.size())) : target;
            if (who == null) continue;
            PlagueSwarm s = new PlagueSwarmServeur(level, this, who, phase() == 3 ? 0.38F : 0.32F, phase() == 3 ? 240 : 200);
            s.setPos(from.x + (random.nextDouble() - 0.5), from.y, from.z + (random.nextDouble() - 0.5));
            level.addFreshEntity(s);
            swarms.add(s);
         }
         level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 30, 0.8, 0.5, 0.8, 0.08);
         sound(Sounds.PB_FLIES.get(), 4F, 0.9F);
         sound(Sounds.PB_HISS.get(), 2.5F, 0.8F);
      }
   }

   /** Il arrache une poignée de pustules à sa poitrine et les jette en éventail vers les tireurs. */
   private void pustules(ServerLevel level, int t, LivingEntity target) {
      if (t < 14 && target != null) faceTarget(target);
      if (t == 14) sound(SoundEvents.SLIME_SQUISH, 2F, 0.5F);
      if (t == 26) {
         Player far = farthest(48);
         LivingEntity who = far != null ? far : target;
         Vec3 from = clawPos().add(0, 3.2, 0);
         double dist = who != null ? Mth.clamp(who.distanceTo(this), 8, 32) : 16;
         Vec3 base = who != null ? new Vec3(who.getX() - getX(), 0, who.getZ() - getZ()).normalize() : fwd();
         for (int k = -2; k <= 2; k++) {
            Vec3 aim = surf(this.position().add(base.yRot(k * 0.3F).scale(dist * (1 - Math.abs(k) * 0.08))));
            if (k == 0 && who != null) aim = surf(who.position().add(who.getDeltaMovement().multiply(12, 0, 12)));
            int flight = (int) Mth.clamp(from.distanceTo(aim) * 0.6, 12, 30);
            PlagueGlob g = new PlagueGlobServeur(level, this, PlagueGlob.PUSTULE);
            g.launch(from, aim, flight);
            level.addFreshEntity(g);
            Vfx.circle(level, aim, 2.6F, flight);
         }
         sound(SoundEvents.WITCH_THROW, 2.5F, 0.3F);
         sound(SoundEvents.SLIME_BLOCK_BREAK, 2F, 0.6F);
      }
   }

   // ------------------------------------------------------------------ phase 3
   /** Il inspire, se cabre et souffle une colonne de spores vers le ciel : des cosses retombent sur toute l'arène. */
   private void spores(ServerLevel level, int t) {
      if (t == 6) sound(Sounds.PB_BREATH.get(), 3F, 0.8F);
      if (t == 25) {
         Vec3 m = mouth();
         roar(level, 0.5F);
         sound(Sounds.PB_SPORES.get(), 5F, 0.7F);
         Vfx.send(level, Vfx.Type.COLUMN, m, Vec3.ZERO, 1.2F, 16F, 26, 0xFF000000 | SPORE, 0);
         level.sendParticles(ModParticles.SPORE.get(), m.x, m.y + 1, m.z, 80, 0.6, 3, 0.6, 0.2);
         List<Vec3> aims = new ArrayList<>();
         for (Player p : targets(56)) {
            if (aims.size() >= 12) break;
            for (int k = 0; k < 2; k++) {
               aims.add(surf(p.position().add(p.getDeltaMovement().multiply(20, 0, 20))
                  .add((random.nextDouble() - 0.5) * (k == 0 ? 1.5 : 7), 0, (random.nextDouble() - 0.5) * (k == 0 ? 1.5 : 7))));
            }
         }
         for (int i = 0; i < 5; i++) aims.add(surf(this.position().add((random.nextDouble() - 0.5) * 34, 0, (random.nextDouble() - 0.5) * 34)));
         for (int i = 0; i < aims.size(); i++) {
            Vec3 aim = inLeash(aims.get(i));
            int fall = 30 + i * 3 + random.nextInt(6);
            Vec3 from = aim.add((random.nextDouble() - 0.5) * 8, 36, (random.nextDouble() - 0.5) * 8);
            Vfx.sporePod(level, from, aim, 1.1F, fall);
            Vfx.circle(level, aim, 3.5F, fall);
            strikes.add(new Strike(aim, this.tickCount + fall, 3.5F, 12F));
         }
      }
   }

   private void sporeImpact(ServerLevel level, Vec3 at, float r, float damage) {
      Vfx.flash(level, at.add(0, 0.8, 0), r, 5, SPORE);
      Vfx.shockwave(level, at, r * 1.6F, 12, SPORE);
      level.sendParticles(ModParticles.SPORE.get(), at.x, at.y + 0.5, at.z, 30, r * 0.4, 0.5, r * 0.4, 0.1);
      level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState()), at.x, at.y + 0.5, at.z, 16, 0.6, 0.4, 0.6, 0.15);
      level.playSound(null, at.x, at.y, at.z, Sounds.PB_SPORES.get(), SoundSource.HOSTILE, 2.5F, 1.1F + random.nextFloat() * 0.2F);
      for (Player p : hitCircle(level, at, r, damage, 0.6, 0.4)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
      addGas(level, at, 3F, 140);
   }

   /**
    * Pandémie : griffe et crochet levés, il frappe le sol ; une vague de peste roule au ras du sol jusqu'à 26 blocs. On l'évite
    * en sautant au-dessus au bon moment (ceux qui sont en l'air ne sont pas touchés).
    */
   private void pandemic(ServerLevel level, int t, LivingEntity target) {
      if (t < 12 && target != null) faceTarget(target);
      Vec3 o = surf(this.position());
      if (t == 2) Vfx.circle(level, o, 5F, 26);
      if (t == 10) roar(level, 0.55F);
      if (t == 28) {
         impactFx(level, o, 5F);
         sound(Sounds.PB_WAVE.get(), 5F, 0.9F);
         Vfx.send(level, Vfx.Type.SHOCKWAVE, o, Vec3.ZERO, 26F, 1.6F, 26, 0xFF000000 | GAS, 0);
         hitOnce.clear();
      }
      if (t >= 28 && t <= 54) {
         double r = 2 + (t - 28) * 0.95;
         for (Player p : targets(r + 3)) {
            if (hitOnce.contains(p.getUUID())) continue;
            double d = Math.sqrt(p.distanceToSqr(o.x, p.getY(), o.z));
            if (d < r - 1.4 || d > r + 0.4 || !p.onGround() || Math.abs(p.getY() - o.y) > 2.5) continue;
            hitOnce.add(p.getUUID());
            p.hurt(this.damageSources().indirectMagic(this, this), dmg(16));
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            Vec3 dd = new Vec3(p.getX() - o.x, 0, p.getZ() - o.z).normalize();
            p.setDeltaMovement(dd.x * 1.1, 0.6, dd.z * 1.1);
            p.hurtMarked = true;
         }
         if (t % 3 == 0) {
            for (int k = 0; k < 10; k++) {
               double a = k * Mth.TWO_PI / 10 + t * 0.2;
               level.sendParticles(ModParticles.TOXIC_SMOKE.get(), o.x + Math.cos(a) * r, o.y + 0.3, o.z + Math.sin(a) * r, 1, 0.3, 0.1, 0.3, 0.01);
            }
         }
      }
   }

   /** Bond sur le tireur le plus éloigné : cercle annoncé, réception en cratère de gaz. */
   private void leap(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         Player far = farthest(40);
         LivingEntity who = far != null ? far : target;
         flyFrom = this.position();
         flyTo = inLeash(who != null ? surf(who.position().add(who.getDeltaMovement().multiply(12, 0, 12))) : surf(this.position().add(fwd().scale(10))));
         if (flyTo.distanceTo(flyFrom) > 30) flyTo = inLeash(surf(flyFrom.add(flyTo.subtract(flyFrom).normalize().scale(30))));
         landed = false;
         Vfx.circle(level, flyTo, 6F, 30);
         sound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.7F);
      }
      if (flyTo == null) return;
      if (t < 14) faceTo(flyTo);
      if (t == 14) {
         Vfx.debris(level, surf(this.position()), 1.6F, 5F, 12, ground(this.position()), Vec3.ZERO);
         sound(SoundEvents.PHANTOM_SWOOP, 2F, 0.6F);
      }
      if (t >= 14 && t < 30) flyArc(flyFrom, flyTo, t - 14, 16, 5 + 0.15 * flyFrom.distanceTo(flyTo));
      if (!landed && t >= 29 && (this.onGround() || t >= 40)) {
         landed = true;
         Vec3 at = surf(this.position());
         impactFx(level, at, 6F);
         for (Player p : hitCircle(level, at, 6, 26, 1.3, 0.8)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
         addGas(level, at, 5F, 200);
      }
   }

   protected void deathTick(ServerLevel level, int t) {
      if (t > DEATH_TICKS) return;                                          // la dépouille, figée dans sa moisissure
      Vec3 o = surf(this.position());
      if (t == 1) {
         sound(Sounds.PB_ROAR_OPEN.get(), 4F, 0.75F);
         sound(SoundEvents.RAVAGER_DEATH, 1.5F, 0.5F);
         strikes.clear();
         for (PlagueSwarm s : swarms) if (s.isAlive()) s.discard();
         swarms.clear();
      }
      if (t == 30) {
         Vfx.debris(level, o, 2.2F, 5F, 22, ground(o), Vec3.ZERO);
         Vfx.shake(level, o, 1.1F, 40, 16);
         level.sendParticles(ModParticles.DUST.get(), o.x, o.y + 0.3, o.z, 40, 2.5, 0.3, 2.5, 0.06);
         sound(SoundEvents.RAVAGER_STEP, 3F, 0.4F);
         sound(SoundEvents.ANVIL_LAND, 1.2F, 0.4F);
         // les infectés tombent avec lui
         for (Zombie z : infected) {
            if (!z.isAlive()) continue;
            level.sendParticles(ModParticles.TOXIC_SMOKE.get(), z.getX(), z.getY() + 1, z.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
            z.discard();
         }
         infected.clear();
      }
      if (t > 30 && t < 120 && t % 14 == 0) {                                     // il étouffe à genoux, chaque râle plus faible
         sound(Sounds.PB_BREATH.get(), 2.6F, 0.9F - (t - 30) * 0.003F);
         Vec3 m = at(1.2, 0, 4.2);
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), m.x, m.y + 0.4, m.z, 6, 0.2, 0.3, 0.2, 0.02);
      }
      if (t == 120) sound(Sounds.PB_DEATH.get(), 4F, 1F);
      if (t == 130) {
         // une dernière bouffée d'air, qu'il relâche en un long sifflement
         Vec3 m = at(1.2, 0, 4.2);
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), m.x, m.y + 0.5, m.z, 30, 0.3, 0.8, 0.3, 0.05);
      }
      if (t >= MOLD_AT && t < MOLD_AT + MOLD_TICKS && t % 3 == 0) {
         float k = (t - MOLD_AT) / (float) MOLD_TICKS;
         level.sendParticles(ModParticles.SPORE.get(), getX(), getY() + 1 + k * 3, getZ(), 6, 1.4, 0.8, 1.4, 0.01);
         if (t % 9 == 0) sound(SoundEvents.SLIME_SQUISH, 1.4F, 0.5F + k * 0.3F);
      }
      if (t >= BLOOM_AT && t < BLOOM_AT + 44 && t % 4 == 0) {
         // les champignons crèvent la peau un à un
         level.sendParticles(ModParticles.SPORE.get(), getX(), getY() + 3.5, getZ(), 14, 1.8, 1.6, 1.8, 0.06);
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUSHROOM_STEM.defaultBlockState()), getX(), getY() + 3.5, getZ(), 6,
            1.6, 1.4, 1.6, 0.1);
         sound(SoundEvents.SLIME_BLOCK_PLACE, 1.6F, 0.5F + random.nextFloat() * 0.3F);
         if (t % 12 == 0) sound(Sounds.PB_SPORES.get(), 2F, 1.2F + random.nextFloat() * 0.3F);
      }
      if (t == 200) {
         List<ItemStack> loot = new ArrayList<>();
         loot.add(new ItemStack(HeraliszPlague.PLAGUE_MASK.get()));
         loot.add(new ItemStack(HeraliszPlague.PLAGUE_CANISTER.get(), 1 + random.nextInt(2)));
         loot.add(new ItemStack(HeraliszPlague.PLAGUE_HOOK.get()));
         loot.add(new ItemStack(HeraliszPlague.PLAGUE_SPORES.get(), 8 + random.nextInt(7)));
         dropLoot(level, this.position().add(fwd().scale(0.6)).add(0, 3, 0), loot, 600);
         Vfx.flash(level, this.position().add(0, 3, 0), 4F, 8, SPORE);
         sound(SoundEvents.AMETHYST_BLOCK_CHIME, 2F, 0.6F);
      }
      if (t == DEATH_TICKS) {
         fr.heralisz.plague.dungeon.StationHallBEServeur h = hall();
         if (h != null) h.deathEnd(level);
      }
   }

   @Override
   public void remove(RemovalReason r) {
      for (Zombie z : infected) if (z.isAlive()) z.discard();
      infected.clear();
      for (PlagueSwarm s : swarms) if (s.isAlive()) s.discard();
      swarms.clear();
      bossBar.removeAllPlayers();                                          // (HeraliszBoss.remove)
      super.remove(r);
   }

   @Override
   protected net.minecraft.sounds.SoundEvent getAmbientSound() {
      return Sounds.PB_BREATH.get();
   }

   @Override
   protected float getSoundVolume() {
      return 2.5F;
   }

   @Override
   protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource s) {
      return Sounds.PB_HURT.get();
   }

   @Override
   public float getVoicePitch() {
      return 0.9F + random.nextFloat() * 0.15F;
   }

   @Override
   protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
      this.playSound(Sounds.PB_STEP.get(), 1.6F, 0.9F + random.nextFloat() * 0.15F);
   }
}
