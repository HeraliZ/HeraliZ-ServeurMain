package fr.heralisz.mammoth.entity;

import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.MammothConfig;
import fr.heralisz.mammoth.dungeon.HornAltarBlockEntityServeur;
import fr.heralisz.mammoth.dungeon.ModBlocks;
import fr.heralisz.mammoth.fx.Particles;
import fr.heralisz.mammoth.sound.Sounds;
import fr.heralisz.mammoth.vfx.Vfx;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

/**
 * Partie SERVEUR de {@link Mammoth} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new Mammoth(...) » côté serveur.
 */
public class MammothServeur extends Mammoth {
   public MammothServeur(EntityType<? extends Mammoth> type, Level level) {
      super(type, level);
   }

   private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.heralisz_mammoth.mammoth"), BossEvent.BossBarColor.BLUE,
      BossEvent.BossBarOverlay.PROGRESS);
   private final float[] dpsWindow = new float[20];
   private final int[] cooldowns = new int[ACTS.length];
   private final Map<UUID, Float> threat = new HashMap<>();
   private final Set<UUID> hitOnce = new HashSet<>();
   private BlockPos home, altarPos;
   private int globalCd = 30, peakPlayers = 1, peakHold, lonely;
   private boolean fromSave;

   private record Strike(Vec3 at, int tick, float r, float dmg, int kind) {}

   private record Wave(Vec3 c, int t0, float r0, float r1, int dur, float dmg, Set<UUID> hit) {}

   private record FrostZone(Vec3 c, float r, int until) {}

   private record Cage(Vec3 c, UUID player, int until) {}

   private final List<Strike> strikes = new ArrayList<>();
   private final List<Wave> waves = new ArrayList<>();
   private final List<FrostZone> frost = new ArrayList<>();
   private final List<Cage> cages = new ArrayList<>();
   /** Pilier qui s'effondre : fissures et alerte au sol, puis chute (le long de dir) et gravats. */
   private record PillarFall(BlockPos c, Vec3 dir, int t0) {}

   private final List<PillarFall> falls = new ArrayList<>();
   private final Map<UUID, Integer> breathExposure = new HashMap<>();
   private List<BlockPos> pillars;
   private int stuck, navTicks, returning;
   private boolean lootDone;
   private BlockPos heldProp;
   private Vec3 chargeDir, chargeFrom;
   private int rampageLeft;
   private boolean crashed;

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
   }

   private void setPhase(int p) {
      this.entityData.set(PHASE, p);
   }

   private void setMove(int m) {
      if (moveMode() != m) this.entityData.set(MOVE, m);
   }

   /** Coup porté à un joueur : dégâts, et son écran se fend d'autant plus que le coup est lourd. */
   private void hurtP(Player p, float base) {
      if (p.hurt(this.damageSources().mobAttack(this), dmg(base))) Vfx.hit(p, Math.min(1F, 0.25F + base / 30F));
   }

   /** Pris dans la glace : figé, invulnérable, sans IA, sans barre ni musique. */
   public void setDormant(boolean on) {
      this.entityData.set(DORMANT, on);
      if (on) {
         this.entityData.set(ACTION, Action.SPAWN.ordinal());
         this.entityData.set(ACTION_TICK, clock());
         this.setTarget(null);
         this.getNavigation().stop();
      }
   }

   public void setHome(BlockPos p) {
      this.home = p;
   }

   public void setAltar(BlockPos p) {
      this.altarPos = p;
   }

   /** L'autel a sonné : il s'arrache de la glace. */
   public void wake() {
      if (!dormant()) return;
      this.entityData.set(DORMANT, false);
      start(Action.SPAWN);
   }

   /** Plus personne : il rentre dans sa niche, se soigne et se rendort. */
   public void sleep() {
      if (this.level().isClientSide || this.isDeadOrDying()) return;
      stopAction();
      bossBar.removeAllPlayers();
      this.setHealth(this.getMaxHealth());
      setPhase(1);
      threat.clear();
      strikes.clear();
      waves.clear();
      frost.clear();
      cages.clear();
      falls.clear();
      pillars = null;
      if (this.level() instanceof ServerLevel sl) for (DraugrServeur dr : DraugrServeur.of(sl, this, 80)) dr.discard();
      peakPlayers = 1;
      if (home != null) this.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
      setDormant(true);
   }

   private void start(Action a) {
      this.entityData.set(ACTION, a.ordinal());
      this.entityData.set(ACTION_TICK, clock() + 1);
      this.hitOnce.clear();
      this.getNavigation().stop();
      this.backTicks = 0;
      setMove(M_WALK);
   }

   private void stopAction() {
      this.entityData.set(ACTION, 0);
   }

   public HornAltarBlockEntityServeur altar() {
      if (altarPos == null || !this.level().isLoaded(altarPos)) return null;
      return this.level().getBlockEntity(altarPos) instanceof HornAltarBlockEntityServeur a ? a : null;
   }

   /** Centre de la zone de combat : l'autel (ou son point d'apparition). */
   private Vec3 arena() {
      if (altarPos != null && home != null) return Vec3.atBottomCenterOf(altarPos).lerp(Vec3.atBottomCenterOf(home), 0.35);
      BlockPos c = altarPos != null ? altarPos : home != null ? home : this.blockPosition();
      return Vec3.atBottomCenterOf(c);
   }

   // ------------------------------------------------------------------ cycle de vie
   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide && this.tickCount == 0 && !fromSave) {
         if (home == null) home = this.blockPosition();
         if (!dormant()) start(Action.SPAWN);
      }
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      ServerLevel level = (ServerLevel) this.level();
      if (dormant() || action() == Action.SPAWN) snapToFloor(level);
      if (dormant()) {
         this.setTarget(null);
         this.getNavigation().stop();
         this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
         if (this.tickCount % 50 == 0) level.sendParticles(Particles.FLAKE.get(), getX(), getY() + 5, getZ(), 6, 3, 2, 3, 0.01);
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
         this.entityData.set(VIRTUAL_MAX, (int) (MammothConfig.VIRTUAL_HP.get() * groupMult()));
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
            Action next = chainAfter(a);
            if (next != null) {
               start(next);
               return;
            }
            stopAction();
            globalCd = Math.round((phase() == 3 ? 6 : phase() == 2 ? 10 : 14) * cdScale());
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
      if (goHome(arena())) return;
      if (target == null) {
         this.getNavigation().stop();
         return;
      }
      this.getLookControl().setLookAt(target, 30F, 30F);
      double d = horizontal(target.position());
      float want = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
      float off = Math.abs(Mth.wrapDegrees(want - this.yBodyRot));
      turnToward(target.position(), off > 60 ? 4F : 6F);
      if (globalCd == 0) {
         Action next = choose(target, d);
         if (next != null) {
            cooldowns[next.ordinal()] = Math.round(cooldownOf(next) * cdScale());
            faceTo(target.position());
            start(next);
            return;
         }
      }
      if (backTicks > 0) {                                             // il recule pour reprendre de l'élan, face à sa proie
         backTicks--;
         setMove(M_BACK);
         this.getNavigation().stop();
         Vec3 v = fwd().scale(-0.09);
         this.setDeltaMovement(v.x, this.getDeltaMovement().y, v.z);
         return;
      }
      if (d < 5.0 && globalCd > 6 && random.nextInt(30) == 0) {
         backTicks = 30 + random.nextInt(20);
         return;
      }
      if (d > 7.5) {
         boolean run = d > 18 || phase() == 3;
         setMove(run ? M_RUN : M_WALK);
         double sp = run ? 1.6 : 1.0;
         if (navTicks > 0) {
            navTicks--;
            if (this.tickCount % 10 == 0 || this.getNavigation().isDone()) this.getNavigation().moveTo(target, sp);
         } else {
            this.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), sp);
         }
         unstick(level, target);
      } else {
         stuck = 0;
         this.getNavigation().stop();
         setMove(off > 35 ? M_TURN : M_WALK);
      }
   }

   private int backTicks;

   /** Coincé contre un pilier ou une paroi : il abat le pilier qui le gêne, sinon il cherche un chemin. */
   private void unstick(ServerLevel level, LivingEntity target) {
      Vec3 v = this.getDeltaMovement();
      if (this.horizontalCollision || v.horizontalDistanceSqr() < 0.0004) stuck++;
      else stuck = Math.max(0, stuck - 2);
      if (stuck < 24) return;
      stuck = 0;
      BlockPos p = pillarNear(this.position().add(fwd().scale(5)), 6.5);
      if (p != null) {
         faceTo(Vec3.atBottomCenterOf(p));
         start(Action.GORE);
         collapse(level, p, fwd(), 8);
         return;
      }
      navTicks = 80;
   }

   /** Rentre vers le centre de l'arène (on ne le tire jamais hors de sa salle) ; il ne se téléporte pas. */
   private boolean goHome(Vec3 c) {
      if (returning <= 0) return false;
      returning--;
      if (horizontal(c) < 10) {
         returning = 0;
         return false;
      }
      turnToward(c, 8F);
      this.getMoveControl().setWantedPosition(c.x, c.y, c.z, 1.1);
      return true;
   }

   /** Plus il y a de joueurs, plus ses attaques s'enchaînent vite. */
   private float cdScale() {
      return Math.max(0.55F, 1F - 0.09F * (peakPlayers - 1));
   }

   // ------------------------------------------------------------------ choix des attaques
   private Action choose(LivingEntity target, double d) {
      int ph = phase();
      Player far = farthest(56);
      boolean sniped = far != null && horizontal(far.position()) > 20;
      // un tireur planqué derrière un mur ou un pilier : il le déloge (tirs en cloche, sauts, ondes qui passent par-dessus)
      boolean hid = target != null && !this.hasLineOfSight(target);
      return pick(
         Action.GORE, d < 11 ? 3F : 0F,
         Action.SWEEP, d < 12 ? 2.4F : 0F,
         Action.STOMP, d < 10 ? 2.0F : 0.4F,
         Action.LASH, d < 16 ? 2.0F : 0F,
         Action.RAMPAGE, d > 9 ? (hid ? 3F : ph == 3 ? 2.2F : 1.8F) : 0.5F,
         Action.BELLOW, 1.0F,
         Action.BREATH, ph >= 2 ? (d < 18 ? 2.4F : 0.8F) : 0F,
         Action.QUAKE, ph >= 2 ? (hid ? 3F : 1.8F) : 0F,
         Action.HURL, hid ? 4F : ph >= 2 ? (sniped ? 3.2F : 1.2F) : 0F,
         Action.PRISON, ph >= 2 ? 1.4F : 0F,
         Action.AVALANCHE, ph == 3 ? 2.0F : 0F,
         Action.HEART, ph == 3 ? 1.4F : 0F,
         Action.TRAMPLE, d < 10 ? 2.2F : 0F,
         Action.LEAP, d > 10 && d < 26 ? (hid ? 3.5F : ph >= 2 ? 2.0F : 1.2F) : 0F,
         Action.BUCK, behind() ? 4.0F : 0F);
   }

   private int cooldownOf(Action a) {
      boolean fast = phase() == 3;
      return switch (a) {
         case GORE -> 40;
         case SWEEP -> 70;
         case STOMP -> fast ? 140 : 180;
         case LASH -> 90;
         case RAMPAGE -> fast ? 260 : 300;
         case BELLOW -> 420;
         case BREATH -> fast ? 240 : 300;
         case QUAKE -> fast ? 260 : 320;
         case HURL -> fast ? 160 : 220;
         case PRISON -> 360;
         case AVALANCHE -> 400;
         case HEART -> 700;
         case TRAMPLE -> 110;
         case LEAP -> fast ? 200 : 260;
         case BUCK -> 60;
         default -> 0;
      };
   }

   private Action chainAfter(Action a) {
      if (a == Action.RAMPAGE) {
         rampageLeft = phase() == 3 ? 2 : 0;
         previousWasCharge = false;
         return Action.CHARGE;
      }
      if (a == Action.CHARGE) {
         if (crashed) {
            crashed = false;
            return Action.STAGGER;
         }
         if (rampageLeft > 0 && !targets(48).isEmpty()) {
            rampageLeft--;
            previousWasCharge = true;
            chargeDir = null;
            return Action.CHARGE;
         }
      }
      return null;
   }

   /** Tirage pondéré parmi les actions prêtes : pick(a1, w1, a2, w2, ...). */
   private Action pick(Object... aw) {
      List<Action> o = new ArrayList<>();
      List<Float> w = new ArrayList<>();
      for (int i = 0; i + 1 < aw.length; i += 2) {
         Action act = (Action) aw[i];
         float wt = ((Number) aw[i + 1]).floatValue();
         if (wt > 0 && cooldowns[act.ordinal()] == 0) {
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

   // ------------------------------------------------------------------ actions
   private void runAction(ServerLevel level, Action a, int t, LivingEntity target) {
      switch (a) {
         case SPAWN -> spawnSeq(level, t);
         case PHASE2 -> phase2(level, t);
         case PHASE3 -> phase3(level, t);
         case GORE -> gore(level, t, target);
         case SWEEP -> sweep(level, t, target);
         case STOMP -> stomp(level, t);
         case LASH -> lash(level, t, target);
         case RAMPAGE -> rampage(level, t, target);
         case CHARGE -> charge(level, t);
         case BELLOW -> bellow(level, t);
         case BREATH -> breath(level, t, target);
         case QUAKE -> quake(level, t);
         case HURL -> hurl(level, t, target);
         case PRISON -> prison(level, t, target);
         case AVALANCHE -> avalanche(level, t);
         case HEART -> heart(level, t);
         case TRAMPLE -> trample(level, t, target);
         case LEAP -> leap(level, t, target);
         case BUCK -> buck(level, t);
         case STAGGER -> {
            if (t % 8 == 0) level.sendParticles(Particles.FLAKE.get(), getX(), getY() + 7, getZ(), 8, 1.5, 0.5, 1.5, 0.02);
         }
         default -> {
         }
      }
   }

   private void roar(ServerLevel level, float pitch) {
      sound(Sounds.ROAR.get(), 6F, pitch);
      Vfx.shake(level, this.position(), 1.0F, 50, 24);
   }

   private void trumpet(ServerLevel level, float pitch) {
      sound(Sounds.TRUMPET.get(), 7F, pitch);
      Vfx.shake(level, this.position(), 0.8F, 48, 20);
   }

   private void spawnSeq(ServerLevel level, int t) {
      Vec3 o = this.position();
      if (t == 1) {
         if (MammothConfig.CINEMATICS.get()) Vfx.cinematic(level, o, this.getId(), SPAWN_K, Action.SPAWN.ticks);
         prisonPos = findPrison(level);
      }
      if (t < 60 && t % 8 == 1) {
         Vfx.shake(level, o, 0.35F + t / 150F, 60, 8);
         sound(Sounds.STEP.get(), 3F, 0.4F);
      }
      if (t < FREE_AT && t % 14 == 2) sound(Sounds.HEARTBEAT.get(), 7F, 0.8F + t / 300F);
      if (t == 20 || t == 55 || t == 90) {
         int stage = t == 20 ? 1 : t == 55 ? 2 : 3;
         setPrisonStage(level, stage);
         sound(Sounds.ICE_CRACK.get(), 7F, 0.5F + stage * 0.1F);
         sound(Sounds.SKULL_CRACK.get(), 4F, 0.6F + stage * 0.1F);
         Vfx.shake(level, o, 0.6F + stage * 0.3F, 60, 12);
         if (prisonPos != null) {
            Vec3 c = Vec3.atBottomCenterOf(prisonPos).add(0, 3.5, 0);
            level.sendParticles(Particles.SHARD.get(), c.x, c.y, c.z, 20 * stage, 2.5 * stage, 1.5 * stage, 0.4, 0.08);
            level.sendParticles(Particles.CINDER.get(), c.x, c.y, c.z, 12 * stage, 1.2 * stage, 1.2 * stage, 0.3, 0.05);
         }
      }
      if (t == EYES_FROM) {                                            // deux yeux rouges s'allument d'un coup dans la glace
         sound(Sounds.SKULL_CRACK.get(), 6F, 0.4F);
      }
      if (t == EYES_FROM + 4) {
         sound(Sounds.AMB_MOAN.get(), 8F, 0.45F);
         sound(Sounds.HEARTBEAT.get(), 8F, 0.6F);
      }
      if (t == FREE_AT) breakFree(level);
      if (t > FREE_AT + 10 && t < 235) {                               // il sort de sa niche, lentement
         Vec3 v = fwd().scale(0.1);
         this.setDeltaMovement(v.x, this.getDeltaMovement().y, v.z);
         if (t % 20 == 0) {
            sound(Sounds.STEP.get(), 6F, 0.6F);
            Vfx.shake(level, o, 0.6F, 40, 8);
            Vfx.debris(level, floorAt(o.add(fwd().scale(3))), 1.5F, 2.5F, 8, ground(o), Vec3.ZERO);
         }
      }
      if (t == 250) trumpet(level, 0.7F);
      if (t == 256) {
         HornAltarBlockEntityServeur alt = altar();
         if (alt != null) alt.flare(level);
      }
      if (t == 290) {
         Vec3 g = floorAt(o.add(fwd().scale(3)));
         Vfx.shockwave(level, g, 22F, 3F, 26);
         Vfx.debris(level, g, 4F, 7F, 40, ground(g), Vec3.ZERO);
         Vfx.frost(level, g, 12F, 260);
         Vfx.shake(level, o, 1.8F, 70, 26);
         sound(Sounds.STOMP.get(), 7F, 0.7F);
         for (Player p : hitCircle(level, g, 10, 0, 1.4, 0.5)) chill(p, 0.35F, 0.2F, 40);
      }
      if (t == 300) roar(level, 0.7F);
   }

   private BlockPos prisonPos;

   /** Il repose sur le sol de sa niche (jamais en l'air pendant la cinématique). */
   private void snapToFloor(ServerLevel level) {
      BlockPos b = BlockPos.containing(getX(), getY() + 0.5, getZ());
      for (int i = 0; i < 6; i++) {
         BlockPos q = b.below(i + 1);
         net.minecraft.world.phys.shapes.VoxelShape sh = level.getBlockState(q).getCollisionShape(level, q);
         if (!sh.isEmpty()) {
            double top = q.getY() + sh.max(net.minecraft.core.Direction.Axis.Y);
            if (getY() - top > 0.15) this.setPos(getX(), top, getZ());
            return;
         }
      }
   }

   private BlockPos findPrison(ServerLevel level) {
      BlockPos c = this.blockPosition();
      return fr.heralisz.mammoth.dungeon.BlocsProches.premier(level, c.offset(-16, -3, -16), c.offset(16, 4, 16), ModBlocks.ICE_PRISON.get());
   }

   private void setPrisonStage(ServerLevel level, int stage) {
      if (prisonPos == null) return;
      BlockState st = level.getBlockState(prisonPos);
      if (!st.is(ModBlocks.ICE_PRISON.get())) return;
      HornAltarBlockEntityServeur alt = altar();
      if (alt != null) alt.recordChange(prisonPos, st);
      level.setBlock(prisonPos, st.setValue(fr.heralisz.mammoth.dungeon.IcePrisonBlock.STAGE, stage), 3);
   }

   /** La prison vole en éclats : le glaçon disparaît, le volume invisible qui bouchait la niche et le réceptacle aussi. */
   private void breakFree(ServerLevel level) {
      HornAltarBlockEntityServeur alt = altar();
      if (prisonPos == null) prisonPos = findPrison(level);
      setPrisonStage(level, 4);
      BlockPos c = prisonPos != null ? prisonPos : this.blockPosition().relative(this.getDirection(), 8);
      BlockState ps = level.getBlockState(c);
      net.minecraft.core.Direction along = ps.hasProperty(fr.heralisz.mammoth.dungeon.IcePrisonBlock.FACING)
         ? ps.getValue(fr.heralisz.mammoth.dungeon.IcePrisonBlock.FACING).getClockWise() : net.minecraft.core.Direction.EAST;
      BlockPos a0 = c.relative(along, -13), a1 = c.relative(along, 13).above(14);
      for (BlockPos p : BlockPos.betweenClosed(a0, a1)) {
         BlockState st = level.getBlockState(p);
         if (!st.is(ModBlocks.PROP_SOLID.get()) && !st.is(ModBlocks.CLEAR_ICE.get()) && !st.is(ModBlocks.HEART_SOCKET.get())) continue;
         if (alt != null) alt.recordChange(p, st);
         level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
      }
      Vec3 f = Vec3.atBottomCenterOf(c).add(0, 4, 0);
      Vfx.debris(level, f, 7F, 10F, 70, ModBlocks.CLEAR_ICE.get().defaultBlockState(), fwd().scale(1.1));
      Vfx.debris(level, f, 6F, 8F, 50, ice(), fwd().scale(0.9));
      Vfx.debris(level, f.add(0, -1, 0), 2F, 6F, 20, ModBlocks.GLACIER_GLOW.get().defaultBlockState(), fwd().scale(1.2));
      level.sendParticles(Particles.SHARD.get(), f.x, f.y, f.z, 160, 6, 3.5, 1, 0.3);
      level.sendParticles(Particles.MIST.get(), f.x, f.y - 2, f.z, 40, 6, 1.5, 1.5, 0.05);
      Vfx.flash(level, f, 14F, 12);
      Vfx.shake(level, this.position(), 2.2F, 80, 30);
      sound(Sounds.ICE_SHATTER.get(), 8F, 0.7F);
      sound(Sounds.SKULL_CRACK.get(), 7F, 0.5F);
      sound(Sounds.ROAR.get(), 5F, 0.5F);
      for (Player p : targets(18)) {
         Vec3 d = p.position().subtract(this.position()).multiply(1, 0, 1).normalize();
         p.setDeltaMovement(d.x * 1.3, 0.5, d.z * 1.3);
         p.hurtMarked = true;
         chill(p, 0.35F, 0.6F, 30);
      }
   }

   /**
    * Phase 2 (6,5 s) : la glace le recouvre. Il s'ébroue, fonce sur un pilier et l'abat d'un coup d'épaule, puis barrit :
    * trois (ou plus, selon le groupe) chasseurs gelés se relèvent autour de l'autel.
    */
   private void phase2(ServerLevel level, int t) {
      if (t == 1) {
         if (MammothConfig.CINEMATICS.get()) Vfx.cinematic(level, this.position(), this.getId(), PHASE2_K, Action.PHASE2.ticks);
         sound(Sounds.ROAR.get(), 5F, 0.55F);
         strikes.clear();
      }
      if (t > 6 && t < 40 && t % 5 == 0) {
         sound(Sounds.ICE_CRACK.get(), 5F, 0.9F + random.nextFloat() * 0.3F);
         level.sendParticles(Particles.MIST.get(), getX(), getY() + 5, getZ(), 8, 3, 2, 3, 0.05);
      }
      if (t == 40) {
         setPhase(2);
         Vec3 o = floorAt(this.position());
         Vfx.nova(level, o.add(0, 3, 0), 12F, 16);
         Vfx.shockwave(level, o, 14F, 4F, 20);
         Vfx.frost(level, o, 14F, 400);
         sound(Sounds.ICE_SHATTER.get(), 6F, 1.1F);
         for (Player p : hitCircle(level, o, 12, 0, 1.8, 0.7)) chill(p, 0.35F, 0.3F, 40);
         collapseNearest(level, 1);
      }
      if (t == 70) trumpet(level, 0.7F);
      if (t == 78) awaken(level, 2 + Math.min(4, peakPlayers));
   }

   /**
    * Phase 3 (7 s) : il se cabre et se fracasse le crâne contre les dalles (le crâne se fend), les fissures courent jusqu'aux
    * piliers : deux s'écroulent ; il se redresse, flanc ouvert, et relève tous les chasseurs qui restent.
    */
   private void phase3(ServerLevel level, int t) {
      if (t == 1) {
         if (MammothConfig.CINEMATICS.get()) Vfx.cinematic(level, this.position(), this.getId(), PHASE3_K, Action.PHASE3.ticks);
         sound(Sounds.ROAR.get(), 5F, 0.5F);
         strikes.clear();
      }
      if (t == 34) {
         Vec3 o = floorAt(this.position().add(fwd().scale(5)));
         sound(Sounds.SKULL_CRACK.get(), 7F, 0.8F);
         sound(Sounds.STOMP.get(), 6F, 0.6F);
         Vfx.shockwave(level, o, 22F, 5F, 26);
         Vfx.debris(level, o, 4F, 9F, 50, ground(o), Vec3.ZERO);
         Vfx.flash(level, o.add(0, 2, 0), 12F, 10);
         Vfx.shake(level, o, 2.0F, 70, 30);
         for (Player p : hitCircle(level, o, 14, 10, 2.0, 0.8)) chill(p, 0.45F, 0.5F, 50);
      }
      if (t == 40) setPhase(3);
      if (t == 50) collapseNearest(level, 2);
      if (t == 96) roar(level, 0.55F);
      if (t == 104) awaken(level, 99);
   }

   /** Coup de défenses montant : cône devant lui, on part en l'air. */
   private void gore(ServerLevel level, int t, LivingEntity target) {
      if (t < 8 && target != null) faceTo(target.position());
      if (t == 2) Vfx.cone(level, floorAt(this.position()), fwd(), 11F, 80F, 11);
      if (t == 9) sound(Sounds.TUSK_SWING.get(), 5F, 0.8F);
      if (t == 13) {
         sound(Sounds.BONE_HIT.get(), 5F, 0.8F);
         Vec3 g = floorAt(this.position().add(fwd().scale(6)));
         Vfx.debris(level, g, 3F, 6F, 20, ground(g), fwd().scale(0.3));
         Vfx.shake(level, g, 0.9F, 30, 10);
         for (Player p : inCone(11, 40)) {
            hurtP(p, (22));
            Vec3 d = flat(p.position().subtract(this.position()));
            p.setDeltaMovement(d.x * 0.9, 1.25, d.z * 0.9);
            p.hurtMarked = true;
         }
      }
   }

   /** Balayage : tout l'avant est fauché de droite à gauche. */
   private void sweep(ServerLevel level, int t, LivingEntity target) {
      if (t < 6 && target != null) faceTo(target.position());
      if (t == 2) Vfx.cone(level, floorAt(this.position()), fwd(), 12.5F, 210F, 15);
      if (t == 13) sound(Sounds.TUSK_SWING.get(), 5F, 0.7F);
      if (t == 17) {
         sound(Sounds.BONE_HIT.get(), 5F, 0.7F);
         Vfx.shake(level, this.position(), 0.8F, 30, 10);
         for (Player p : inCone(12.5, 105)) {
            hurtP(p, (18));
            Vec3 side = right().scale(-1);
            p.setDeltaMovement(side.x * 1.9, 0.55, side.z * 1.9);
            p.hurtMarked = true;
         }
      }
   }

   /** Il se cabre et retombe : écrasement au centre, onde de choc qu'il faut sauter. */
   private void stomp(ServerLevel level, int t) {
      Vec3 g = floorAt(this.position().add(fwd().scale(2)));
      if (t == 3) Vfx.circle(level, g, 7F, 19);
      if (t == 8) trumpet(level, 1.0F);
      if (t == 22) {
         sound(Sounds.STOMP.get(), 7F, 0.8F);
         Vfx.shockwave(level, g, 17F, 1.6F, 20);
         Vfx.debris(level, g, 4F, 7F, 36, ground(g), Vec3.ZERO);
         Vfx.shake(level, g, 1.5F, 50, 18);
         for (Player p : hitCircle(level, g, 7, 26, 1.2, 0.9)) chill(p, 0.25F, 0F, 33);
         waves.add(new Wave(g, this.tickCount, 7F, 17F, 14, 12F, new HashSet<>()));
         if (phase() >= 2) Vfx.frost(level, g, 10F, 200);
      }
   }

   /** Coup de trompe : un fouet qui s'abat en ligne devant lui. */
   private void lash(ServerLevel level, int t, LivingEntity target) {
      if (t < 10 && target != null) faceTo(target.position());
      Vec3 from = floorAt(this.position().add(fwd().scale(3)));
      if (t == 3) Vfx.line(level, from, fwd(), 14F, 3.2F, 12);
      if (t == 13) sound(Sounds.LASH.get(), 6F, 0.9F);
      if (t == 15) {
         Vfx.debris(level, from.add(fwd().scale(8)), 3F, 5F, 24, ground(from), Vec3.ZERO);
         Vfx.shake(level, from, 1.0F, 30, 10);
         for (Player p : targets(20)) {
            Vec3 rel = p.position().subtract(from);
            double along = rel.dot(fwd()), across = Math.abs(rel.dot(right()));
            if (along < -1 || along > 15 || across > 2.2) continue;
            hurtP(p, (20));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, false));
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.5, 0));
            p.hurtMarked = true;
         }
         if (phase() >= 2) addFrost(level, from.add(fwd().scale(10)), 3.5F, 160);
      }
   }

   /** Avant la ruée : il gratte le sol, choisit sa cible (le plus éloigné en phase 3) et l'annonce. */
   private void rampage(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         Player far = phase() == 3 ? farthest(48) : null;
         LivingEntity who = far != null ? far : target;
         chargeDir = who != null ? flat(who.position().subtract(this.position())) : fwd();
         faceTo(this.position().add(chargeDir));
      }
      if (chargeDir != null) faceTo(this.position().add(chargeDir));
      if (t == 5 || t == 14) {
         sound(Sounds.STEP.get(), 5F, 0.7F);
         Vfx.debris(level, floorAt(this.position().add(fwd().scale(2))), 1.5F, 4F, 10, ground(this.position()), Vec3.ZERO);
      }
      if (t == 6 && chargeDir != null) Vfx.line(level, floorAt(this.position()), chargeDir, 30F, 5.5F, 22);
      if (t == 18) trumpet(level, 1.1F);
      if (t == 0) crashed = false;
   }

   /** Charge : il fonce droit ; si la paroi l'arrête, il est sonné et la voûte lâche des stalactites. */
   private void charge(ServerLevel level, int t) {
      if (t == 0) {
         chargeFrom = this.position();
         crashed = false;
         hitOnce.clear();
         if (chargeDir == null) {
            LivingEntity who = phase() == 3 ? farthest(48) : this.getTarget();
            if (who == null) who = this.getTarget();
            if (who != null) chargeDir = flat(who.position().subtract(this.position()));
         }
         if (chargeDir == null) chargeDir = fwd();
         faceTo(this.position().add(chargeDir));
         if (previousWasCharge) Vfx.line(level, floorAt(this.position()), chargeDir, 26F, 5.5F, 10);
         sound(Sounds.CHARGE.get(), 5F, 1F);
      }
      int run0 = previousWasCharge ? 10 : 0;
      if (t < run0) {
         faceTo(this.position().add(chargeDir));
         return;
      }
      if (crashed) {
         this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
         return;
      }
      double speed = phase() == 3 ? 1.0 : 0.85;
      Vec3 v = chargeDir.scale(speed);
      this.setDeltaMovement(v.x, this.getDeltaMovement().y, v.z);
      faceTo(this.position().add(chargeDir));
      if ((t - run0) % 20 == 0) sound(Sounds.CHARGE.get(), 5F, 1F);
      if (t % 3 == 0) {
         Vec3 g = floorAt(this.position());
         Vfx.debris(level, g, 2F, 3F, 6, ground(g), Vec3.ZERO);
         if (phase() >= 2 && t % 6 == 0) {                              // le sol gèle sous ses pas (sans traînée peinte au sol)
            frost.add(new FrostZone(g, 2.6F, this.tickCount + 200));
            level.sendParticles(Particles.FLAKE.get(), g.x, g.y + 0.3, g.z, 14, 1.6, 0.2, 1.6, 0.02);
            level.sendParticles(Particles.MIST.get(), g.x, g.y + 0.4, g.z, 4, 1.4, 0.2, 1.4, 0.01);
         }
      }
      for (Player p : targets(10)) {
         if (hitOnce.contains(p.getUUID())) continue;
         Vec3 rel = p.position().subtract(this.position());
         double along = rel.dot(chargeDir), across = rel.dot(new Vec3(-chargeDir.z, 0, chargeDir.x));
         if (along < -2 || along > 7 || Math.abs(across) > 3.6) continue;
         hitOnce.add(p.getUUID());
         hurtP(p, (24));
         double s = across >= 0 ? 1 : -1;
         p.setDeltaMovement(-chargeDir.z * s * 1.6 + chargeDir.x * 0.8, 0.8, chargeDir.x * s * 1.6 + chargeDir.z * 0.8);
         p.hurtMarked = true;
         sound(Sounds.BONE_HIT.get(), 5F, 0.8F);
      }
      double run = this.position().subtract(chargeFrom).horizontalDistance();
      boolean far = arena().distanceTo(this.position()) > MammothConfig.LEASH_RADIUS.get() - 4;
      if (t > run0 + 4 && this.horizontalCollision) {
         crashed = true;
         Vec3 o = this.position().add(chargeDir.scale(4));
         BlockPos pil = pillarNear(this.position().add(chargeDir.scale(5)), 5.5);
         if (pil != null) collapse(level, pil, chargeDir, 10);
         sound(Sounds.STOMP.get(), 7F, 0.6F);
         sound(Sounds.ICE_SHATTER.get(), 6F, 0.8F);
         Vfx.debris(level, o.add(0, 3, 0), 3F, 8F, 40, ice(), chargeDir.scale(-0.6));
         Vfx.shake(level, o, 1.8F, 60, 22);
         for (int i = 0; i < 6; i++) {
            Vec3 at = floorAt(this.position().add((random.nextDouble() - 0.5) * 22, 0, (random.nextDouble() - 0.5) * 22));
            icicleStrike(level, at, 20 + i * 3);
         }
         this.entityData.set(ACTION_TICK, clock() - Action.CHARGE.ticks);
      } else if (run > 34 || far || t >= Action.CHARGE.ticks - 2) {
         this.entityData.set(ACTION_TICK, clock() - Action.CHARGE.ticks);
      }
   }

   private boolean previousWasCharge;

   /** Barrissement : cône qui repousse, ralentit et plonge dans le noir. */
   private void bellow(ServerLevel level, int t) {
      if (t == 2) Vfx.cone(level, floorAt(this.position()), fwd(), 18F, 70F, 8);
      if (t == 10) trumpet(level, 0.75F);
      if (t >= 10 && t <= 36 && t % 6 == 0) {
         Vfx.shockwave(level, floorAt(this.position().add(fwd().scale(4))), 18F, 3F, 14);
         for (Player p : inCone(18, 35)) {
            Vec3 d = flat(p.position().subtract(this.position()));
            p.setDeltaMovement(d.x * 1.1, 0.25, d.z * 1.1);
            p.hurtMarked = true;
            hurtP(p, (3));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1, false, false, false));
            chill(p, 0.12F, 0.35F, 30);
         }
      }
   }

   private Vec3 mouth() {
      return this.position().add(fwd().scale(6.5)).add(0, 3.0, 0);
   }

   /** Souffle de blizzard : il balaie de droite à gauche ; rester dedans gèle sur place. */
   private void breath(ServerLevel level, int t, LivingEntity target) {
      if (t < 8 && target != null) faceTo(target.position());
      if (t == 3) sound(Sounds.VORTEX.get(), 4F, 1.6F);
      if (t < 12 || t > 52) return;
      if (t == 12) {
         sound(Sounds.BLIZZARD.get(), 6F, 0.9F);
         breathExposure.clear();
      }
      if (t == 32) sound(Sounds.BLIZZARD.get(), 6F, 0.85F);
      // même balayage que l'animation (tête : +26° -> -26° -> +18°)
      float u = (t - 12) / 40F;
      float yaw = u < 0.5F ? Mth.lerp(u / 0.5F, 26F, -26F) : Mth.lerp((u - 0.5F) / 0.5F, -26F, 18F);
      Vec3 dir = fwd().yRot(-yaw * Mth.DEG_TO_RAD);
      Vec3 m = mouth();
      if (t % 2 == 0) Vfx.breath(level, m, dir.add(0, -0.25, 0), 17F, 26F, 12);
      if (t % 4 == 0) addFrost(level, floorAt(m.add(dir.scale(6 + random.nextDouble() * 9))), 2.8F, 220);
      for (Player p : targets(19)) {
         Vec3 to = flat(p.position().subtract(m));
         double dist = p.position().subtract(m).multiply(1, 0, 1).length();
         if (dist > 17 || dir.dot(to) < Math.cos(Math.toRadians(15))) continue;
         if (t % 4 == 0) p.hurt(this.damageSources().freeze(), dmg(3));
         chill(p, 0.05F, 0F, 20);
         if (breathExposure.merge(p.getUUID(), 1, Integer::sum) >= 16) {
            breathExposure.remove(p.getUUID());
            freezeSolid(level, p, 40);
         }
      }
   }

   /** Trois coups de patte : des lignes de pics de glace foncent vers chaque joueur. */
   private void quake(ServerLevel level, int t) {
      if (t == 12 || t == 28 || t == 44) {
         Vec3 o = floorAt(this.position().add(fwd().scale(2)));
         sound(Sounds.STOMP.get(), 6F, 1.0F);
         Vfx.shake(level, o, 1.2F, 50, 12);
         Vfx.debris(level, o, 2.5F, 5F, 20, ground(o), Vec3.ZERO);
         for (Player p : targets(34)) {
            Vec3 d = flat(p.position().subtract(o));
            double len = Math.min(30, o.distanceTo(p.position()) + 5);
            int k = 0;
            for (double s = 4; s <= len; s += 2.6, k++) {
               Vec3 at = floorAt(o.add(d.scale(s)));
               strikes.add(new Strike(at, this.tickCount + 8 + k * 2, 1.7F, 16F, 1));
               Vfx.circle(level, at, 1.7F, 8 + k * 2);
            }
         }
      }
   }

   /**
    * Il arrache un rocher de glace de la salle (s'il y en a un à portée, sinon une dalle du sol) avec ses défenses et le lance
    * sur le joueur le plus éloigné (anti-tireurs). Le rocher disparaît vraiment de la salle (remis en place après le combat).
    */
   private void hurl(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) heldProp = propNear(ModBlocks.HERO.get(fr.heralisz.mammoth.dungeon.HeroKind.GLACIER_BOULDER).get(), 13);
      if (t < 11) {
         if (heldProp != null) faceTo(Vec3.atBottomCenterOf(heldProp));
         else if (target != null) faceTo(target.position());
      }
      Vec3 tusks = this.position().add(fwd().scale(5.5)).add(0, 3.5, 0);
      if (t == 11) {
         sound(Sounds.RIP.get(), 6F, 0.9F);
         if (heldProp != null && level.getBlockState(heldProp).is(ModBlocks.HERO.get(fr.heralisz.mammoth.dungeon.HeroKind.GLACIER_BOULDER).get())) {
            Vec3 at = Vec3.atCenterOf(heldProp);
            removeRecorded(level, heldProp);
            BlockPos below = heldProp.below();
            if (level.getBlockState(below).is(ModBlocks.PROP_SOLID.get())) removeRecorded(level, below);
            Vfx.debris(level, at, 1.5F, 5F, 24, ice(), Vec3.ZERO);
            Vfx.boulder(level, at, tusks, 2.4F, 12);
         } else {
            heldProp = null;
            Vec3 g = floorAt(this.position().add(fwd().scale(6)));
            Vfx.debris(level, g, 2F, 5F, 24, ground(g), Vec3.ZERO);
            Vfx.boulder(level, g, tusks, 2.0F, 12);
         }
      }
      if (t == 26) {
         Player far = farthest(56);
         LivingEntity who = far != null ? far : target;
         if (who == null) return;
         faceTo(who.position());
         Vec3 aim = floorAt(who.position().add(who.getDeltaMovement().multiply(12, 0, 12)));
         Vec3 from = this.position().add(fwd().scale(5.5)).add(0, 5, 0);
         int flight = (int) Mth.clamp(from.distanceTo(aim) * 0.75, 12, 30);
         Vfx.boulder(level, from, aim, 2.4F, flight);
         Vfx.circle(level, aim, 4F, flight);
         sound(Sounds.WHOOSH.get(), 6F, 0.8F);
         strikes.add(new Strike(aim, this.tickCount + flight, 4F, 24F, 2));
      }
   }

   /** Piétinement : trois coups de pattes en avançant ; le dernier, des deux pattes, fait trembler les dalles. */
   private void trample(ServerLevel level, int t, LivingEntity target) {
      if (t < 8 && target != null) faceTo(target.position());
      if (t < 32) {
         Vec3 v = fwd().scale(0.1);
         this.setDeltaMovement(v.x, this.getDeltaMovement().y, v.z);
      }
      if (t == 2) Vfx.circle(level, floorAt(this.position().add(fwd().scale(6)).add(right().scale(-1.8))), 3.2F, 9);
      if (t == 12) Vfx.circle(level, floorAt(this.position().add(fwd().scale(6)).add(right().scale(1.8))), 3.2F, 9);
      if (t == 20) Vfx.circle(level, floorAt(this.position().add(fwd().scale(5.5))), 6.5F, 12);
      if (t == 11 || t == 21) {
         Vec3 g = floorAt(this.position().add(fwd().scale(6)).add(right().scale(t == 11 ? -1.8 : 1.8)));
         sound(Sounds.STOMP.get(), 5F, 1.1F);
         Vfx.debris(level, g, 2F, 4F, 14, ground(g), Vec3.ZERO);
         Vfx.shockwave(level, g, 4F, 1F, 10);
         Vfx.shake(level, g, 0.8F, 30, 8);
         hitCircle(level, g, 3.2, 14, 0.6, 0.5);
      }
      if (t == 32) {
         Vec3 g = floorAt(this.position().add(fwd().scale(5.5)));
         sound(Sounds.STOMP.get(), 7F, 0.8F);
         Vfx.debris(level, g, 4F, 7F, 34, ground(g), Vec3.ZERO);
         Vfx.shockwave(level, g, 12F, 2F, 16);
         Vfx.shake(level, g, 1.4F, 50, 16);
         for (Player p : hitCircle(level, g, 6.5, 22, 1.4, 0.8)) chill(p, 0.15F, 0.1F, 20);
      }
   }

   private Vec3 leapTo;

   /** Bond : il s'accroupit, bondit par-dessus la salle et s'écrase là où se tenait sa proie (cercle au sol pour esquiver). */
   private void leap(ServerLevel level, int t, LivingEntity target) {
      if (t <= 3 && target != null) {                                // la cible est figée quand le cercle apparaît
         faceTo(target.position());
         leapTo = floorAt(target.position());
      }
      if (t > 3 && t < 20 && leapTo != null) faceTo(leapTo);
      if (t == 3 && leapTo != null) {
         Vfx.circle(level, leapTo, 6.5F, 29);
         sound(Sounds.ROAR.get(), 4F, 1.3F);
      }
      if (t == 18) sound(Sounds.WHOOSH.get(), 7F, 0.6F);
      if (t >= 20 && t < 32 && leapTo != null) {
         Vec3 to = leapTo.subtract(this.position());
         double left = 32 - t;
         Vec3 v = new Vec3(to.x / left, t == 20 ? 0.9 : this.getDeltaMovement().y, to.z / left);
         this.setDeltaMovement(v);
         this.hasImpulse = true;
      }
      if (t == 32 && leapTo != null) {
         Vec3 g = leapTo;                                              // les dégâts tombent exactement dans le cercle affiché
         sound(Sounds.STOMP.get(), 8F, 0.5F);
         sound(Sounds.ICE_SHATTER.get(), 6F, 0.7F);
         Vfx.shockwave(level, g, 16F, 3F, 20);
         Vfx.debris(level, g, 5F, 9F, 50, ground(g), Vec3.ZERO);
         Vfx.flash(level, g.add(0, 1, 0), 9F, 8);
         Vfx.frost(level, g, 8F, 200);
         Vfx.shake(level, g, 2.0F, 70, 24);
         hitCircle(level, g, 6.5, 26, 1.8, 1.0);
         leapTo = null;
      }
   }

   /** Quelqu'un dans son dos (à moins de 9 blocs) ? */
   private boolean behind() {
      for (Player p : targets(9)) {
         Vec3 to = flat(p.position().subtract(this.position()));
         if (fwd().dot(to) < -0.55) return true;
      }
      return false;
   }

   /** Ruade : les pattes arrière se détendent vers ceux qui se croyaient à l'abri dans son dos. */
   private void buck(ServerLevel level, int t) {
      if (t == 2) Vfx.cone(level, floorAt(this.position()), fwd().scale(-1), 9F, 110F, 10);
      if (t == 10) sound(Sounds.TUSK_SWING.get(), 5F, 0.6F);
      if (t == 12) {
         Vec3 g = floorAt(this.position().add(fwd().scale(-6)));
         sound(Sounds.BONE_HIT.get(), 6F, 0.6F);
         Vfx.debris(level, g, 2.5F, 5F, 18, ground(g), fwd().scale(-0.5));
         Vfx.shake(level, g, 1.0F, 30, 10);
         for (Player p : targets(10)) {
            Vec3 to = flat(p.position().subtract(this.position()));
            if (fwd().dot(to) > -0.4 || horizontal(p.position()) > 9.5) continue;
            hurtP(p, 18);
            p.setDeltaMovement(to.x * 2.2, 0.7, to.z * 2.2);
            p.hurtMarked = true;
         }
      }
   }

   /** Prison de glace : un anneau de pics jaillit autour d'un joueur ; pris dedans, il gèle sur place. */
   private void prison(ServerLevel level, int t, LivingEntity target) {
      if (t == 1) {
         List<Player> ps = targets(40);
         LivingEntity who = !ps.isEmpty() ? ps.get(random.nextInt(ps.size())) : target;
         if (who != null) {
            prisonAt = floorAt(who.position());
            Vfx.circle(level, prisonAt, 3.0F, 19);
         }
      }
      if (t == 20 && prisonAt != null) {
         sound(Sounds.SPIKES.get(), 6F, 0.9F);
         Vfx.cage(level, prisonAt, 2.8F, 70);
         Vfx.frost(level, prisonAt, 4F, 160);
         for (Player p : targets(48)) {
            if (p.position().distanceToSqr(prisonAt.x, p.getY(), prisonAt.z) > 2.9 * 2.9) continue;
            p.hurt(this.damageSources().freeze(), dmg(10));
            freezeSolid(level, p, 60);
            cages.add(new Cage(prisonAt, p.getUUID(), this.tickCount + 60));
         }
      }
   }

   private Vec3 prisonAt;

   /** Avalanche : il barrit vers la voûte, les stalactites pleuvent sur toute l'arène. */
   private void avalanche(ServerLevel level, int t) {
      if (t == 12) trumpet(level, 0.6F);
      if (t >= 12 && t <= 40 && t % 2 == 0) {
         List<Player> ps = targets(40);
         Vec3 at;
         if (!ps.isEmpty() && random.nextFloat() < 0.6F) {
            Player p = ps.get(random.nextInt(ps.size()));
            at = p.position().add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6);
         } else {
            at = this.position().add((random.nextDouble() - 0.5) * 44, 0, (random.nextDouble() - 0.5) * 44);
         }
         icicleStrike(level, floorAt(at), 22);
      }
      if (t == 45) {
         Vec3 g = floorAt(this.position());
         sound(Sounds.STOMP.get(), 6F, 0.7F);
         Vfx.shockwave(level, g, 12F, 2F, 16);
         Vfx.shake(level, g, 1.3F, 50, 16);
      }
   }

   /** Le Cœur de l'Hiver : le flanc s'ouvre, tout est aspiré, puis la nova. Loin = sauf. */
   private void heart(ServerLevel level, int t) {
      Vec3 c = floorAt(this.position());
      if (t == 18) {
         sound(Sounds.MAW_OPEN.get(), 6F, 0.8F);
         Vfx.circle(level, c, 16F, 48);
         Vfx.vortex(level, c, 18F, 46);
      }
      if (t >= 18 && t <= 64 && t % 14 == 4) sound(Sounds.HEARTBEAT.get(), 7F, 0.8F + (t - 18) / 80F);
      if (t == 22) sound(Sounds.VORTEX.get(), 7F, 0.8F);
      if (t >= 22 && t <= 62) {
         for (Player p : targets(24)) {
            Vec3 d = c.subtract(p.position()).multiply(1, 0, 1);
            double len = d.length();
            if (len < 1) continue;
            Vec3 pull = d.scale(0.075 / len);
            p.setDeltaMovement(p.getDeltaMovement().add(pull));
            p.hurtMarked = true;
            if (len < 5 && t % 10 == 0) p.hurt(this.damageSources().freeze(), dmg(4));
         }
         if (t % 3 == 0) level.sendParticles(Particles.FLAKE.get(), c.x, c.y + 2, c.z, 20, 10, 2, 10, 0.3);
      }
      if (t == 66) {
         sound(Sounds.NOVA.get(), 8F, 1F);
         Vfx.nova(level, c.add(0, 3, 0), 16F, 18);
         Vfx.shockwave(level, c, 18F, 6F, 22);
         Vfx.frost(level, c, 16F, 400);
         Vfx.flash(level, c.add(0, 4, 0), 18F, 12);
         Vfx.shake(level, c, 2.0F, 70, 30);
         for (Player p : hitCircle(level, c, 16, 34, 2.2, 1.0)) freezeSolid(level, p, 50);
      }
      if (t == 70) roar(level, 0.5F);
   }

   // ------------------------------------------------------------------ passifs : frappes différées, ondes, givre, cages
   private void ambient(ServerLevel level) {
      falls.removeIf(f -> fallTick(level, f));
      strikes.removeIf(s -> {
         if (s.tick() > this.tickCount) return false;
         strike(level, s);
         return true;
      });
      waves.removeIf(w -> {
         int age = this.tickCount - w.t0();
         if (age > w.dur()) return true;
         float r = w.r0() + (w.r1() - w.r0()) * age / (float) w.dur();
         for (Player p : targets(w.r1() + 4)) {
            if (w.hit().contains(p.getUUID())) continue;
            double d = Math.sqrt(p.position().distanceToSqr(w.c().x, p.getY(), w.c().z));
            if (d < r - 1.3 || d > r + 0.4 || !p.onGround() || Math.abs(p.getY() - w.c().y) > 1.5) continue;
            w.hit().add(p.getUUID());
            hurtP(p, (w.dmg()));
            Vec3 dd = flat(p.position().subtract(w.c()));
            p.setDeltaMovement(dd.x * 0.9, 0.6, dd.z * 0.9);
            p.hurtMarked = true;
         }
         return false;
      });
      frost.removeIf(f -> f.until() < this.tickCount);
      if (this.tickCount % 5 == 0 && !frost.isEmpty()) {
         for (Player p : targets(60)) {
            for (FrostZone f : frost) {
               if (p.position().distanceToSqr(f.c().x, p.getY(), f.c().z) > f.r() * f.r() || Math.abs(p.getY() - f.c().y) > 2) continue;
               p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, false));
               chill(p, 0.04F, 0F, 10);
               break;
            }
         }
      }
      cages.removeIf(c -> {
         if (c.until() > this.tickCount) {
            Player p = level.getPlayerByUUID(c.player());
            if (p != null && p.position().distanceToSqr(c.c()) < 16) {
               p.teleportTo(c.c().x, p.getY(), c.c().z);
               p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
            }
            return false;
         }
         sound(Sounds.ICE_SHATTER.get(), 4F, 1.3F);
         Vfx.debris(level, c.c().add(0, 1, 0), 2F, 5F, 20, ice(), Vec3.ZERO);
         Player p = level.getPlayerByUUID(c.player());
         if (p != null && p.position().distanceToSqr(c.c()) < 16) p.hurt(this.damageSources().freeze(), dmg(8));
         return true;
      });
      // phase 3 : aura de froid autour de lui
      if (phase() == 3 && this.tickCount % 4 == 0) {
         for (Player p : targets(7)) chill(p, 0.05F, 0F, 10);
         level.sendParticles(Particles.FLAKE.get(), getX(), getY() + 4, getZ(), 6, 3.5, 2.5, 3.5, 0.02);
      }
      if (phase() >= 2 && this.tickCount % 10 == 0) level.sendParticles(Particles.FLAKE.get(), getX(), getY() + 8, getZ(), 4, 4, 1, 4, 0.05);
   }

   private void strike(ServerLevel level, Strike s) {
      switch (s.kind()) {
         case 0 -> {
            level.playSound(null, s.at().x, s.at().y, s.at().z, Sounds.ICICLE_HIT.get(), SoundSource.HOSTILE, 3F, 0.9F + random.nextFloat() * 0.3F);
            Vfx.debris(level, s.at().add(0, 0.5, 0), 1.2F, 5F, 10, ice(), Vec3.ZERO);
            Vfx.frost(level, s.at(), s.r() * 1.2F, 140);
         }
         case 3 -> Vfx.debris(level, s.at().add(0, 0.5, 0), 1.5F, 4F, 10, ModBlocks.RIME_BRICKS.get().defaultBlockState(), Vec3.ZERO);
         case 1 -> {
            Vfx.spikes(level, s.at(), 1.6F, 40);
            if (random.nextInt(3) == 0) level.playSound(null, s.at().x, s.at().y, s.at().z, Sounds.SPIKES.get(), SoundSource.HOSTILE, 3F, 1F);
         }
         default -> {
            level.playSound(null, s.at().x, s.at().y, s.at().z, Sounds.ICE_SHATTER.get(), SoundSource.HOSTILE, 5F, 1F);
            Vfx.debris(level, s.at().add(0, 0.5, 0), 2.5F, 8F, 40, ice(), Vec3.ZERO);
            Vfx.shockwave(level, s.at(), 7F, 2F, 12);
            Vfx.frost(level, s.at(), 5F, 200);
            Vfx.shake(level, s.at(), 1.2F, 30, 10);
         }
      }
      for (Player p : hitCircle(level, s.at(), s.r(), s.dmg(), s.kind() == 2 ? 1.3 : 0.4, s.kind() == 1 ? 0.95 : 0.4)) {
         chill(p, 0.2F, 0F, 30);
      }
   }

   private void icicleStrike(ServerLevel level, Vec3 at, int fall) {
      Vfx.circle(level, at, 2.2F, fall);
      Vfx.icicle(level, at, 1.3F, fall);
      strikes.add(new Strike(at, this.tickCount + fall, 2.2F, 18F, 0));
   }

   private void addFrost(ServerLevel level, Vec3 c, float r, int dur) {
      frost.add(new FrostZone(c, r, this.tickCount + dur));
      Vfx.frost(level, c, r * 1.1F, dur);
   }

   /** Gelé sur place : on ne bouge plus, on ne frappe plus (quelques secondes). */
   private void freezeSolid(ServerLevel level, Player p, int ticks) {
      chill(p, 0.50F, 0F, 66);
      p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true));
      p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 3, false, true));
      level.sendParticles(Particles.FLAKE.get(), p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 1, 0.5, 0.05);
   }

   /** Le froid mord : givre sur l'écran du joueur (a = intensité, white = voile blanc), tenu quelques ticks. */
   private void chill(Player p, float a, float white, int hold) {
      // plus de givre à l'écran (jugé illisible) : le froid se lit au ralentissement et aux particules
   }

   private static BlockState ice() {
      return ModBlocks.ICE_CHUNK.get().defaultBlockState();
   }

   // ------------------------------------------------------------------ la salle : décors arrachés, piliers, chasseurs
   private void removeRecorded(ServerLevel level, BlockPos p) {
      HornAltarBlockEntityServeur alt = altar();
      BlockState st = level.getBlockState(p);
      if (alt != null) alt.recordChange(p, st);
      level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
   }

   private void placeRecorded(ServerLevel level, BlockPos p, BlockState st) {
      HornAltarBlockEntityServeur alt = altar();
      if (alt != null) alt.recordChange(p, level.getBlockState(p));
      level.setBlock(p, st, 3);
   }

   /** Le décor de ce type le plus proche devant lui (dans un rayon r). */
   private BlockPos propNear(Block b, double r) {
      BlockPos best = null;
      double bd = r * r;
      BlockPos c = this.blockPosition();
      for (BlockPos p : BlockPos.betweenClosed(c.offset((int) -r, -1, (int) -r), c.offset((int) r, 3, (int) r))) {
         if (!this.level().getBlockState(p).is(b)) continue;
         double d = p.distToCenterSqr(this.position());
         if (d < bd) {
            bd = d;
            best = p.immutable();
         }
      }
      return best;
   }

   /** Les six piliers de la salle : colonnes d'os 3 x 3 (quatre colonnes aux angles), repérés une fois. */
   private List<BlockPos> pillars() {
      if (pillars != null) return pillars;
      pillars = new ArrayList<>();
      Vec3 c = arena();
      int y = Mth.floor(floorAt(c).y) + 2;
      List<BlockPos> cols = new ArrayList<>();
      int R = MammothConfig.LEASH_RADIUS.get();
      for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(c.x - R, y, c.z - R), BlockPos.containing(c.x + R, y, c.z + R))) {
         if (this.level().getBlockState(p).is(ModBlocks.BONE_PILLAR.get())) cols.add(p.immutable());
      }
      for (BlockPos p : cols) {
         BlockPos mid = p.offset(1, 0, 1);
         int n = 0;
         for (BlockPos q : cols) if (Math.abs(q.getX() - mid.getX()) == 1 && Math.abs(q.getZ() - mid.getZ()) == 1) n++;
         if (n == 4 && !pillars.contains(mid)) pillars.add(mid);
      }
      return pillars;
   }

   private BlockPos pillarNear(Vec3 at, double r) {
      for (BlockPos p : pillars()) if (Math.sqrt(Mth.square(p.getX() + 0.5 - at.x) + Mth.square(p.getZ() + 0.5 - at.z)) < r) return p;
      return null;
   }

   /** Abat les n piliers encore debout les plus proches de lui (ils tombent vers le centre de la salle). */
   private void collapseNearest(ServerLevel level, int n) {
      List<BlockPos> ps = new ArrayList<>(pillars());
      ps.sort((a, b) -> Double.compare(a.distToCenterSqr(this.position()), b.distToCenterSqr(this.position())));
      Vec3 c = arena();
      int k = 0;
      for (BlockPos p : ps) {
         if (k >= n) break;
         Vec3 dir = flat(c.subtract(Vec3.atBottomCenterOf(p))).yRot((random.nextFloat() - 0.5F) * 0.8F);
         collapse(level, p, dir, 30 + k * 26);
         k++;
      }
   }

   /** Un pilier se fend (fissures, craquements, alerte au sol sur sa ligne de chute) puis tombe après `warn` ticks. */
   private void collapse(ServerLevel level, BlockPos p, Vec3 dir, int warn) {
      if (!pillars().remove(p)) return;
      Vec3 base = Vec3.atBottomCenterOf(p);
      Vec3 g = floorAt(base);
      Vfx.line(level, g, dir, 13F, 4F, warn);
      level.playSound(null, p, Sounds.ICE_CRACK.get(), SoundSource.HOSTILE, 5F, 0.6F);
      falls.add(new PillarFall(p, dir, this.tickCount + warn));
   }

   private boolean fallTick(ServerLevel level, PillarFall f) {
      int age = this.tickCount - f.t0();
      BlockPos p = f.c();
      if (age < 0) {
         if (age % 8 == 0) {
            level.playSound(null, p, Sounds.ICE_CRACK.get(), SoundSource.HOSTILE, 4F, 0.7F + random.nextFloat() * 0.3F);
            level.sendParticles(Particles.SHARD.get(), p.getX() + 0.5, p.getY() + 4 + random.nextInt(8), p.getZ() + 0.5, 6, 0.8, 1.5, 0.8, 0.05);
         }
         return false;
      }
      Vec3 g = floorAt(Vec3.atBottomCenterOf(p));
      int y0 = Mth.floor(g.y);
      // le fût tombe comme un arbre abattu : les débris partent d'autant plus loin qu'ils étaient haut
      for (int y = y0; y < y0 + 24; y++) {
         boolean any = false;
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               BlockPos q = new BlockPos(p.getX() + dx, y, p.getZ() + dz);
               BlockState st = level.getBlockState(q);
               boolean core = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
               if (!(st.is(ModBlocks.BONE_PILLAR.get()) || (core && (st.is(ModBlocks.RIME_BRICKS.get()) || st.is(ModBlocks.GLACIER_GLOW.get())))
                  || (y == y0 && st.is(ModBlocks.RIME_BRICK_SLAB.get())))) continue;
               removeRecorded(level, q);
               any = true;
            }
         }
         if (any && (y - y0) % 2 == 0) {
            double h = y - y0;
            Vfx.debris(level, new Vec3(p.getX() + 0.5, y + 0.5, p.getZ() + 0.5), 1.2F, 1.5F + (float) h * 0.12F, 8,
               (y - y0) % 4 == 0 ? ModBlocks.RIME_BRICKS.get().defaultBlockState() : ModBlocks.BONE_PILLAR.get().defaultBlockState(), f.dir().scale(0.12 + h * 0.035));
         }
      }
      Vec3 land = g.add(f.dir().scale(7));
      level.playSound(null, land.x, land.y, land.z, Sounds.STOMP.get(), SoundSource.HOSTILE, 7F, 0.5F);
      level.playSound(null, land.x, land.y, land.z, Sounds.ICE_SHATTER.get(), SoundSource.HOSTILE, 6F, 0.6F);
      Vfx.shockwave(level, land, 9F, 2F, 16);
      Vfx.shake(level, land, 1.6F, 60, 20);
      for (int i = 0; i < 4; i++) strikes.add(new Strike(g.add(f.dir().scale(3 + i * 3)), this.tickCount + 6 + i * 2, 2.6F, 20F, 3));
      // gravats : des tas d'os et de pierre restent au sol, le long de la chute
      for (int i = 0; i < 4; i++) {
         Vec3 at = g.add(f.dir().scale(2.5 + i * 3.2)).add(f.dir().yRot(Mth.HALF_PI).scale((random.nextDouble() - 0.5) * 2));
         BlockPos q = BlockPos.containing(at.x, g.y, at.z);
         if (level.getBlockState(q).isAir() && !level.getBlockState(q.below()).isAir())
            placeRecorded(level, q, ModBlocks.HERO.get(i % 2 == 0 ? fr.heralisz.mammoth.dungeon.HeroKind.BONE_HEAP : fr.heralisz.mammoth.dungeon.HeroKind.GLACIER_BOULDER).get().defaultBlockState()
               .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.from2DDataValue(random.nextInt(4))));
      }
      return true;
   }

   /** Les chasseurs gelés agenouillés dans la salle se relèvent : leur statue disparaît, un Revenant se lève à sa place. */
   private void awaken(ServerLevel level, int max) {
      Block statue = ModBlocks.HERO.get(fr.heralisz.mammoth.dungeon.HeroKind.KNEELING_HUNTER).get();
      Vec3 c = arena();
      int R = MammothConfig.LEASH_RADIUS.get();
      List<BlockPos> found = new ArrayList<>();
      int y = Mth.floor(floorAt(c).y);
      for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(c.x - R, y - 1, c.z - R), BlockPos.containing(c.x + R, y + 2, c.z + R))) {
         if (level.getBlockState(p).is(statue)) found.add(p.immutable());
      }
      found.sort((a, b) -> Double.compare(a.distToCenterSqr(this.position()), b.distToCenterSqr(this.position())));
      int n = 0;
      for (BlockPos p : found) {
         if (n >= max) break;
         BlockState st = level.getBlockState(p);
         float yaw = st.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
            ? st.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot() : 0F;
         removeRecorded(level, p);
         if (!(HeraliszMammoth.DRAUGR.get().create(level) instanceof DraugrServeur d)) continue;
         d.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0);
         d.setYBodyRot(yaw);
         d.setYHeadRot(yaw);
         d.setMaster(this);
         level.addFreshEntity(d);
         level.sendParticles(Particles.MIST.get(), p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 10, 0.4, 0.8, 0.4, 0.02);
         level.playSound(null, p, Sounds.ICE_CRACK.get(), SoundSource.HOSTILE, 3F, 1.2F);
         n++;
      }
   }

   // ------------------------------------------------------------------ cibles, groupe
   private void pickTarget() {
      threat.replaceAll((k, v) -> v * 0.8F);
      Player best = null;
      float bs = -1;
      Vec3 c = arena();
      double lim = MammothConfig.LEASH_RADIUS.get() + 4;
      for (Player p : targets(64)) {
         if (p.position().distanceToSqr(c.x, p.getY(), c.z) > lim * lim) continue;
         float sc = threat.getOrDefault(p.getUUID(), 0F) + (float) (64 - p.distanceTo(this)) * 0.5F;
         if (sc > bs) {
            bs = sc;
            best = p;
         }
      }
      this.setTarget(best);
   }

   private List<Player> targets(double r) {
      return this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(r),
         p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) <= r);
   }

   /** Le joueur le plus éloigné au-delà de 12 blocs : le tireur embusqué. */
   private Player farthest(double r) {
      Player best = null;
      double bd = 12;
      for (Player p : targets(r)) {
         double d = horizontal(p.position());
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

   private float groupMult() {
      return 1F + Math.max(0, peakPlayers - MammothConfig.GROUP_BASE.get()) * MammothConfig.GROUP_STEP.get().floatValue();
   }

   // ------------------------------------------------------------------ géométrie
   private void faceTo(Vec3 p) {
      float yaw = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90F;
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
   }

   private void turnToward(Vec3 p, float step) {
      float want = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90F;
      this.yBodyRot = Mth.approachDegrees(this.yBodyRot, want, step);
      this.setYRot(this.yBodyRot);
   }

   private static Vec3 flat(Vec3 v) {
      Vec3 f = new Vec3(v.x, 0, v.z);
      return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
   }

   private double horizontal(Vec3 p) {
      return Math.sqrt(Mth.square(p.x - getX()) + Mth.square(p.z - getZ()));
   }

   /** Sol sous un point (l'arène est sous terre : on cherche le premier sol solide autour de sa hauteur). */
   private Vec3 floorAt(Vec3 p) {
      int x = Mth.floor(p.x), z = Mth.floor(p.z), y0 = Mth.floor(this.getY());
      for (int dy = 5; dy >= -10; dy--) {
         BlockPos bp = new BlockPos(x, y0 + dy, z);
         BlockState st = this.level().getBlockState(bp);
         if (!st.getCollisionShape(this.level(), bp).isEmpty() && this.level().getBlockState(bp.above()).getCollisionShape(this.level(), bp.above()).isEmpty()) {
            return new Vec3(p.x, y0 + dy + 1, p.z);
         }
      }
      return new Vec3(p.x, this.getY(), p.z);
   }

   private BlockState ground(Vec3 p) {
      BlockState s = this.level().getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
      return s.isAir() || s.getBlock().getDescriptionId().startsWith("block.minecraft.") ? ModBlocks.RIME_FLAGSTONE.get().defaultBlockState() : s;
   }

   private float dmg(float base) {
      return base * MammothConfig.DAMAGE_MULT.get().floatValue() * (phase() == 3 ? 1.2F : 1F);
   }

   private void sound(SoundEvent s, float vol, float pitch) {
      this.level().playSound(null, getX(), getY() + 3, getZ(), s, SoundSource.HOSTILE, vol, pitch);
   }

   private List<Player> hitCircle(ServerLevel level, Vec3 c, double r, float damage, double push, double lift) {
      List<Player> hit = new ArrayList<>();
      for (Player p : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r, 4, r))) {
         if (p.isCreative() || p.isSpectator() || p.position().distanceToSqr(c.x, p.getY(), c.z) > r * r) continue;
         if (damage > 0) hurtP(p, damage);
         Vec3 d = flat(p.position().subtract(c));
         if (push > 0 || lift > 0) {
            p.setDeltaMovement(d.x * push, lift, d.z * push);
            p.hurtMarked = true;
         }
         hit.add(p);
      }
      return hit;
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

   private void dropLoot(ServerLevel level, Vec3 c, List<ItemStack> loot, int xp) {
      for (ItemStack st : loot) {
         ItemEntity ie = new ItemEntity(level, c.x, c.y, c.z, st);
         double a = random.nextDouble() * Mth.TWO_PI;
         ie.setDeltaMovement(Math.cos(a) * 0.25, 0.45, Math.sin(a) * 0.25);
         ie.setDefaultPickUpDelay();
         ie.setExtendedLifetime();
         ie.setGlowingTag(true);
         level.addFreshEntity(ie);
      }
   }

   // ------------------------------------------------------------------ garde-fous
   private void leash(ServerLevel level) {
      Vec3 c = arena();
      int r = MammothConfig.LEASH_RADIUS.get();
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(BlockPos.containing(c)).inflate(r + 12),
         p -> !p.isCreative() && !p.isSpectator()).isEmpty();
      lonely = anyone ? 0 : lonely + 20;
      double d = horizontal(c);
      if (d > r + 30 || Math.abs(this.getY() - c.y) > 24) {             // sorti de sa salle par un bug : on le remet au centre
         stopAction();
         this.teleportTo(c.x, c.y + 1, c.z);
      } else if (d > r - 6 && action() == Action.NONE) {
         returning = 60;                                               // trop loin : il revient au pas, sans lâcher le combat
      }
      if (lonely > 400 && altarPos == null) {
         this.setHealth(this.getMaxHealth());
         setPhase(1);
         lonely = 0;
      }
   }

   private void updateBossBar(ServerLevel level) {
      for (ServerPlayer p : level.players()) {
         boolean near = p.distanceTo(this) < 72;
         if (near && !bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
         else if (!near && bossBar.getPlayers().contains(p)) bossBar.removePlayer(p);
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide || this.isDeadOrDying()) return false;
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
      if (dormant()) return false;
      Action a = action();
      if (a == Action.SPAWN || a == Action.PHASE2 || a == Action.PHASE3) return false;
      if (source.getEntity() instanceof Player dp && dp.getMainHandItem().is(fr.heralisz.mammoth.HeraliszMammoth.DEBUG_BLADE.get())) {
         this.invulnerableTime = 0;                                     // lame de test : un tiers de sa vie par coup
         return super.hurt(source, this.getMaxHealth() * 0.34F);
      }
      float mult = a == Action.STAGGER ? 1.5F : 1F;
      float raw = Math.min(amount, MammothConfig.HIT_CAP.get().floatValue());
      Vec3 weak = mouth();
      if (WeakPoint.hit(source, this, weak, 1.9)) {
         raw *= WeakPoint.CRIT;
         WeakPoint.feedback(this, source, weak);
      }
      float window = 0;
      for (float v : dpsWindow) window += v;
      if (window > MammothConfig.DPS_CAP.get()) raw *= 0.25F;
      dpsWindow[this.tickCount % 20] += raw;
      raw *= mult;
      float scaled = raw * this.getMaxHealth() / Math.max(1F, MammothConfig.VIRTUAL_HP.get() * groupMult());
      this.invulnerableTime = 0;
      boolean ok = super.hurt(source, Math.max(scaled, 0.01F));
      this.invulnerableTime = 0;
      if (ok && source.getEntity() instanceof Player pl) threat.merge(pl.getUUID(), raw, Float::sum);
      return ok;
   }

   private BlockPos mawPos;
   private final List<SpectralHand> hands = new ArrayList<>();

   /** La Gueule des Ancêtres la plus proche, pendue au-dessus de lui (null : aucune, l'âme monte alors vers la voûte). */
   private BlockPos findMaw(ServerLevel level) {
      BlockPos c = this.blockPosition();
      return fr.heralisz.mammoth.dungeon.BlocsProches.plusProche(level, c.offset(-40, 2, -40), c.offset(40, 34, 40), ModBlocks.SOUL_MAW.get(), p -> p.distSqr(c));
   }

   /** Le fond de la gorge de la Gueule (modèle : 4,1 blocs sous l'accroche, 3,5 en arrière de sa face). */
   private Vec3 mouth(ServerLevel level) {
      if (mawPos == null) return this.position().add(0, 18, 0);
      BlockState st = level.getBlockState(mawPos);
      net.minecraft.core.Direction f = st.hasProperty(fr.heralisz.mammoth.dungeon.TwoStateBlock.FACING) ? st.getValue(fr.heralisz.mammoth.dungeon.TwoStateBlock.FACING) : net.minecraft.core.Direction.NORTH;
      Vec3 back = Vec3.atLowerCornerOf(f.getOpposite().getNormal());
      return Vec3.atBottomCenterOf(mawPos).add(0, -4.1, 0).add(back.scale(3.5));
   }

   private void setMaw(ServerLevel level, boolean on) {
      if (mawPos == null) return;
      BlockState st = level.getBlockState(mawPos);
      if (st.is(ModBlocks.SOUL_MAW.get()) && st.getValue(fr.heralisz.mammoth.dungeon.TwoStateBlock.LIT) != on) level.setBlock(mawPos, st.setValue(fr.heralisz.mammoth.dungeon.TwoStateBlock.LIT, on), 3);
   }

   /** Point du corps (repère : devant, droite, hauteur ; en blocs) pendant la mort, à genoux. */
   private Vec3 bodyAt(double f, double r, double h) {
      return this.position().add(fwd().scale(f)).add(right().scale(r)).add(0, h, 0);
   }

   /**
    * Mort (26 s, cinématique) : il titube et tombe à genoux ; la Gueule des Ancêtres, pendue à la voûte, s'éveille ; son âme
    * lui est arrachée du flanc et monte, en un globe de lumière et des filets de brume, jusqu'à la gueule qui l'avale ; vidé,
    * il s'affaisse. Les morts de la tribu remontent du sol en mains spectrales, lui agrippent les pattes et l'entraînent sous
    * terre ; il résiste, rugit, se redresse une dernière fois, puis est aspiré. La tête ne passe pas : elle éclate, et son
    * crâne reste là, posé sur les dalles, tandis que le reste du corps disparaît. Le butin roule du crâne.
    */
   private int walkT = -1, arrivedAt = -1;
   private Vec3 walkTo;

   /**
    * Agonie : blessé à mort, il marche lentement jusqu'au centre de la salle (sous la Gueule des Ancêtres), en s'effondrant à
    * moitié à chaque cycle, pour que tout se joue au milieu. Renvoie vrai tant qu'il marche (la mort n'a pas commencé).
    */
   private boolean deathWalk(ServerLevel level) {
      if (walkT < 0) {
         walkT = 0;
         stopAction();
         bossBar.setProgress(0);
         strikes.clear();
         waves.clear();
         frost.clear();
         cages.clear();
         mawPos = findMaw(level);
         walkTo = mawPos == null ? null : new Vec3(mawPos.getX() + 0.5, this.getY(), mawPos.getZ() + 0.5);
         if (walkTo != null) {
            this.entityData.set(DEATH_WALK, true);
            sound(Sounds.DEATH.get(), 7F, 0.9F);
         }
      }
      if (!this.entityData.get(DEATH_WALK)) return false;
      walkT++;
      this.deathTime = 0;
      Vec3 to = new Vec3(walkTo.x - getX(), 0, walkTo.z - getZ());
      double dist = Math.sqrt(to.x * to.x + to.z * to.z);
      int cyc = walkT % 160;                                            // boucle de 8 s de l'animation d'agonie
      boolean moving = cyc < 116 || cyc >= 136;
      faceTo(walkTo);
      if (moving && dist > 0.05) {
         double sp = Math.min(dist, 0.08);
         this.setPos(getX() + to.x / dist * sp, getY(), getZ() + to.z / dist * sp);
         if (cyc % 20 == 5) {
            sound(Sounds.STEP.get(), 5F, 0.5F);
            Vfx.shake(level, this.position(), 0.35F, 30, 6);
         }
      }
      if (cyc == 118) {                                                 // une patte avant fléchit
         Vec3 g = floorAt(this.position().add(fwd().scale(3)));
         sound(Sounds.STOMP.get(), 5F, 0.55F);
         sound(Sounds.HURT.get(), 5F, 0.5F);
         Vfx.debris(level, g, 2.5F, 3.5F, 14, ground(g), Vec3.ZERO);
         Vfx.shake(level, this.position(), 0.8F, 40, 12);
      }
      if (cyc == 60) sound(Sounds.HEARTBEAT.get(), 6F, 0.7F);
      if (dist < 1.2 || walkT > 900) {
         // arrivé au centre : il se tourne vers l'autel (l'entrée), toujours la même mise en place pour la cinématique
         if (arrivedAt < 0) arrivedAt = walkT;
         HornAltarBlockEntityServeur alt = altar();
         Vec3 look = alt != null ? Vec3.atCenterOf(alt.getBlockPos()) : (altarPos != null ? Vec3.atCenterOf(altarPos) : null);
         if (look != null) faceTo(look);
         if (walkT - arrivedAt >= 10) {
            this.entityData.set(DEATH_WALK, false);
            this.deathTime = 0;
         }
      }
      return true;
   }

   @Override
   protected void tickDeath() {
      if (!this.level().isClientSide && deathWalk((ServerLevel) this.level())) return;
      if (this.level().isClientSide && this.entityData.get(DEATH_WALK)) {
         this.deathTime = 0;
         return;
      }
      ++this.deathTime;
      if (this.level().isClientSide) return;
      ServerLevel level = (ServerLevel) this.level();
      int t = this.deathTime;
      Vec3 o = this.position();
      this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
      if (t == 1) {
         stopAction();
         bossBar.setProgress(0);
         strikes.clear();
         waves.clear();
         frost.clear();
         cages.clear();
         if (mawPos == null) mawPos = findMaw(level);
         if (MammothConfig.CINEMATICS.get()) Vfx.cinematic(level, o, this.getId(), DEATH_K, DEATH_TICKS);
         sound(Sounds.DEATH.get(), 7F, 1F);
      }
      falls.removeIf(f -> fallTick(level, f));
      Vec3 mouth = mouth(level);
      // --- il tombe à genoux
      if (t == 12 || t == 28) sound(Sounds.STEP.get(), 5F, 0.6F);
      if (t == 40) {
         Vec3 g = floorAt(o.add(fwd().scale(3)));
         Vfx.debris(level, g, 3F, 5F, 26, ground(g), Vec3.ZERO);
         Vfx.shake(level, o, 1.2F, 60, 14);
         sound(Sounds.STOMP.get(), 6F, 0.6F);
      }
      // --- la Gueule s'éveille
      if (t == MAW_ON) {
         setMaw(level, true);
         Vfx.flash(level, mouth, 7F, 14);
         Vfx.shake(level, mouth, 0.7F, 80, 30);
         level.playSound(null, BlockPos.containing(mouth), Sounds.AMB_MOAN.get(), SoundSource.HOSTILE, 10F, 0.4F);
         level.playSound(null, BlockPos.containing(mouth), Sounds.SKULL_CRACK.get(), SoundSource.HOSTILE, 8F, 0.45F);
         level.sendParticles(Particles.MIST.get(), mouth.x, mouth.y, mouth.z, 30, 2.5, 1.5, 2.5, 0.02);
      }
      if (t > MAW_ON && t < MAW_OFF && t % 3 == 0) level.sendParticles(Particles.AURORA.get(), mouth.x, mouth.y + 0.5, mouth.z, 2, 1.6, 0.6, 1.6, 0.01);
      // --- l'âme lui est arrachée : filets de brume du corps vers la gueule, puis le globe
      if (t >= MAW_ON + 10 && t < SOUL_IN) {
         int n = t < SOUL_OUT ? 2 : 5;
         for (int i = 0; i < n; i++) {
            Vec3 from = bodyAt((random.nextDouble() - 0.3) * 6, (random.nextDouble() - 0.5) * 4, 1.5 + random.nextDouble() * 4.5);
            Vec3 v = mouth.subtract(from).scale(1 / 40.0);
            level.sendParticles(Particles.SOUL.get(), from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
         }
         if (t % 20 == 0) sound(Sounds.VORTEX.get(), 5F, 0.5F + (t - MAW_ON) / 300F);
      }
      if (t == SOUL_OUT) {
         Vec3 chest = bodyAt(1.0, 2.2, 3.4);                           // du flanc qui s'ouvre
         Vfx.heart(level, chest, mouth, 1.3F, SOUL_IN - SOUL_OUT + 4);
         Vfx.flash(level, chest, 5F, 10);
         sound(Sounds.MAW_OPEN.get(), 6F, 0.6F);
         sound(Sounds.HEARTBEAT.get(), 8F, 0.9F);
      }
      if (t > SOUL_OUT && t < SOUL_IN && (t - SOUL_OUT) % 22 == 0) sound(Sounds.HEARTBEAT.get(), 7F, 0.9F + (t - SOUL_OUT) / 250F);
      if (t == SOUL_IN) {                                               // la gueule l'avale
         Vfx.flash(level, mouth, 12F, 16);
         Vfx.nova(level, mouth, 9F, 22);
         Vfx.shake(level, o, 1.0F, 80, 20);
         level.playSound(null, BlockPos.containing(mouth), Sounds.NOVA.get(), SoundSource.HOSTILE, 10F, 0.5F);
         level.playSound(null, BlockPos.containing(mouth), Sounds.ROAR.get(), SoundSource.HOSTILE, 8F, 0.35F);
         level.sendParticles(Particles.SOUL.get(), mouth.x, mouth.y, mouth.z, 40, 1.5, 1.0, 1.5, 0.02);
      }
      // --- deux grandes mains spectrales jaillissent de part et d'autre et empoignent son buste
      if (t == HANDS || t == HANDS + 8) {
         double side = t == HANDS ? 1 : -1;
         Vec3 p = floorAt(bodyAt(-0.4, 5.6 * side, 0));
         Vec3 target = bodyAt(-0.4, 0, 0);
         SpectralHand h = HeraliszMammoth.SPECTRAL_HAND.get().create(level);
         if (h != null) {
            Vec3 d = target.subtract(p);
            float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
            h.setup(this, yaw + 180F);
            h.moveTo(p.x, p.y, p.z, 0, 0);
            level.addFreshEntity(h);
            hands.add(h);
         }
         Vfx.debris(level, p, 2.5F, 5F, 24, ground(p), Vec3.ZERO);
         Vfx.shockwave(level, p, 5F, 1.5F, 16);
         level.sendParticles(Particles.MIST.get(), p.x, p.y + 0.5, p.z, 24, 1.5, 0.4, 1.5, 0.03);
         level.sendParticles(Particles.SOUL.get(), p.x, p.y + 1, p.z, 20, 0.8, 2.0, 0.8, 0.04);
         sound(Sounds.ICE_CRACK.get(), 7F, 0.5F);
         sound(Sounds.AMB_MOAN.get(), 6F, 0.6F + (float) side * 0.05F);
      }
      if (t == HANDS + 30) {
         sound(Sounds.AMB_MOAN.get(), 8F, 0.7F);
         Vfx.shake(level, o, 0.9F, 60, 20);
      }
      // --- il est entraîné sous terre ; il résiste
      if (t >= SINK_FROM && t < GONE && t % 4 == 0) {
         Vec3 g = floorAt(o);
         Vfx.debris(level, g.add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6), 2.5F, 2.5F, 5, ground(g), Vec3.ZERO);
         level.sendParticles(Particles.MIST.get(), g.x, g.y + 0.3, g.z, 5, 3, 0.2, 3, 0.02);
         if (t % 16 == 0) sound(Sounds.STEP.get(), 5F, 0.4F);
      }
      if (t == SINK_FROM) Vfx.shockwave(level, floorAt(o), 10F, 1.2F, 20);
      if (t == RESIST + 5) {
         sound(Sounds.ROAR.get(), 8F, 0.5F);
         sound(Sounds.TRUMPET.get(), 7F, 0.45F);
         Vfx.shake(level, o, 1.6F, 70, 26);
         Vfx.debris(level, floorAt(o), 5F, 6F, 30, ground(o), Vec3.ZERO);
      }
      if (t > RESIST + 20 && t < HEAD_POP && t % 25 == 0) sound(Sounds.HURT.get(), 6F, 0.5F);
      // --- la tête ne passe pas : elle éclate ; son crâne reste
      if (t == HEAD_POP) {
         Vec3 head = bodyAt(4.9, 0, 6.2 - sink(HEAD_POP));
         Vec3 g = floorAt(head);
         sound(Sounds.SKULL_CRACK.get(), 10F, 0.5F);
         sound(Sounds.ICE_SHATTER.get(), 8F, 0.6F);
         sound(Sounds.RIP.get(), 8F, 0.5F);
         Vfx.flash(level, head, 9F, 12);
         Vfx.debris(level, head, 3F, 7F, 40, ModBlocks.BONE_BRICKS.get().defaultBlockState(), Vec3.ZERO);
         Vfx.debris(level, head, 3F, 6F, 30, ice(), Vec3.ZERO);
         Vfx.shockwave(level, g, 14F, 2F, 20);
         Vfx.shake(level, o, 2.0F, 80, 30);
         level.sendParticles(Particles.CINDER.get(), head.x, head.y, head.z, 60, 1.6, 1.2, 1.6, 0.25);
         level.sendParticles(Particles.MIST.get(), head.x, head.y, head.z, 40, 2.0, 1.5, 2.0, 0.05);
         BlockPos sp = BlockPos.containing(g.x, g.y + 0.1, g.z);
         if (level.getBlockState(sp).isAir() || level.getBlockState(sp).canBeReplaced()) {
            HornAltarBlockEntityServeur alt = altar();
            if (alt != null) alt.recordChange(sp, level.getBlockState(sp));
            level.setBlock(sp, ModBlocks.HERO.get(fr.heralisz.mammoth.dungeon.HeroKind.ANCESTOR_SKULL).get().defaultBlockState()
               .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, this.getDirection()), 3);
         }
      }
      if (t == HEAD_POP + 25 && !lootDone) {                            // le butin roule du crâne
         lootDone = true;
         Vec3 h = floorAt(bodyAt(4.9, 0, 0)).add(0, 1.2, 0);
         List<ItemStack> loot = new ArrayList<>();
         loot.add(new ItemStack(HeraliszMammoth.WINTER_HEART.get()));
         loot.add(new ItemStack(HeraliszMammoth.YMIRAK_TUSK.get()));
         loot.add(new ItemStack(HeraliszMammoth.FROZEN_FUR.get(), 6 + random.nextInt(5)));
         loot.add(new ItemStack(HeraliszMammoth.BLACK_ICE_SHARD.get(), 3 + random.nextInt(4)));
         dropLoot(level, h, loot, 0);
         sound(Sounds.TRUMPET.get(), 5F, 0.5F);
      }
      if (t == GONE) {                                                  // le sol se referme
         Vec3 g = floorAt(o);
         Vfx.debris(level, g, 4F, 3F, 30, ground(g), Vec3.ZERO);
         level.sendParticles(Particles.MIST.get(), g.x, g.y + 0.3, g.z, 40, 4, 0.3, 4, 0.02);
         sound(Sounds.STOMP.get(), 6F, 0.4F);
         for (SpectralHand hh : hands) hh.discard();
         hands.clear();
      }
      // la malédiction reflue : le rouge de l'alerte s'éteint en vague depuis le crâne
      if (t >= GONE && t < GONE + 60 && t % 2 == 0) {
         HornAltarBlockEntityServeur alt = altar();
         double r0 = (t - GONE) * 1.0, r1 = r0 + 1.5;
         if (alt != null) alt.calmWave(level, o, r0, r1);
      }
      if (t == GONE + 30) {                                             // les passages secrets s'effondrent : d'autres sorties
         HornAltarBlockEntityServeur alt = altar();
         if (alt == null) alt = HornAltarBlockEntityServeur.nearestServeur(level, this.blockPosition(), 140);
         if (alt != null) alt.openExits(level);
      }
      if (t == MAW_OFF) {
         setMaw(level, false);
         level.playSound(null, BlockPos.containing(mouth), Sounds.AMB_MOAN.get(), SoundSource.HOSTILE, 6F, 0.3F);
      }
      if (t == DEATH_TICKS) bossBar.removeAllPlayers();
      if (t >= DEATH_TICKS + 2) this.remove(RemovalReason.KILLED);
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
      tag.putBoolean("BossSaved", true);
      tag.putBoolean("Dormant", dormant());
      tag.putBoolean("LootDone", lootDone);
      if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
      if (altarPos != null) tag.put("Altar", NbtUtils.writeBlockPos(altarPos));
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      fromSave = tag.getBoolean("BossSaved");
      lootDone = tag.getBoolean("LootDone");
      if (tag.getBoolean("Dormant")) setDormant(true);
      setPhase(Math.max(1, tag.getInt("Phase")));
      if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
      if (tag.contains("Altar")) altarPos = NbtUtils.readBlockPos(tag.getCompound("Altar"));
   }

   // ------------------------------------------------------------------ sons
   @Override
   protected SoundEvent getAmbientSound() {
      return dormant() ? null : Sounds.HEARTBEAT.get();
   }

   @Override
   public int getAmbientSoundInterval() {
      return 160;
   }

   private int lastHurtSound;

   /** Pas un cri à chaque balle : un craquement de glace sourd, de temps en temps seulement. */
   @Override
   protected void playHurtSound(DamageSource s) {
      if (this.tickCount - lastHurtSound < 70 || random.nextFloat() > 0.35F) return;
      lastHurtSound = this.tickCount;
      this.playSound(Sounds.ICE_CRACK.get(), 1.4F, 0.55F + random.nextFloat() * 0.15F);
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource s) {
      return null;
   }

   @Override
   protected float getSoundVolume() {
      return 3.5F;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      if (dormant()) return;
      this.playSound(Sounds.STEP.get(), 3F, 0.85F + this.random.nextFloat() * 0.2F);
      if (this.level() instanceof ServerLevel sl && this.tickCount % 2 == 0) Vfx.shake(sl, this.position(), 0.25F, 20, 5);
   }
}
