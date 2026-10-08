package fr.heralisz.n07.entity;

import fr.heralisz.n07.BossConfig;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.particle.ModParticles;
import fr.heralisz.n07.sound.BossSounds;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import fr.heralisz.n07.api.BossInfo;
import fr.heralisz.n07.api.DevControl;
import net.minecraft.world.BossEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ExperienceOrb;
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
 * Partie SERVEUR de {@link SujetN07} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client. Contient aussi,
 * en fin de fichier, la logique serveur de la classe mère {@link HeraliszBoss} (§5.5 : IA commune, phases, dégâts, barre de boss,
 * mort scénarisée, sauvegarde, commandes de test DevControl) : SujetN07 en est l'unique sous-classe concrète.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new SujetN07(...) » côté serveur.
 */
public class SujetN07Serveur extends SujetN07 implements DevControl {
   public SujetN07Serveur(EntityType<? extends SujetN07> type, Level level) {
      super(type, level);
   }


   private record Pool(Vec3 c, float r, int until) {}

   private record Strike(Vec3 at, int tick) {}

   private final List<Pool> pools = new ArrayList<>();
   private final List<Strike> geysers = new ArrayList<>();
   private final List<N07Isotope> isotopes = new ArrayList<>();
   private N07Boulder held;
   private LivingEntity prey;
   private Vec3 chargeDir = Vec3.ZERO, fissureFrom, fissureDir;
   private int chargeCount, fissureLen;
   private boolean chargeStopped;

   protected int virtualHpBase() {
      return BossConfig.VIRTUAL_HP.get();
   }

   public int deathTicks() {
      return DEATH_TICKS;
   }

   protected double chaseDistance() {
      return 7;
   }

   protected double chaseSpeed(double d) {
      return phase() == 3 ? 1.4 : d > 18 ? 1.2 : 1.0;
   }

   protected float damageTaken(Action a, int t) {
      // pendant les cinématiques il ne prend rien (sinon les phases s'enchaîneraient sous le feu)
      return a == Action.SPAWN || a == Action.PHASE2 || a == Action.PHASE3 ? 0F : 1F;
   }

   /** Point faible : le cœur irradié qui luit dans sa poitrine. */
   protected Vec3 weakPoint() {
      return core();
   }

   protected double weakRadius() {
      return 1.1;
   }

   private Vec3 valve() {
      return at(-1.4, 0, 7.4);
   }

   private Vec3 vat() {
      return at(-2.3, 0, 5.2);
   }

   private Vec3 claws() {
      return at(3.0, 1.8, 0.2);
   }

   // ------------------------------------------------------------------ IA
   protected Action choose(LivingEntity target, double d) {
      int ph = phase();
      Player far = farthest(56);
      boolean sniped = far != null && far.distanceTo(this) > 22;
      // un tireur planqué derrière un mur ou un pilier : il le déloge (tirs en cloche, sauts, ondes qui passent par-dessus)
      boolean hid = target != null && !this.hasLineOfSight(target);
      int near = targets(9).size();
      return pick(
         Action.CLAW, d < 8 ? 3F : 0F,
         Action.SLAM, d < 26 ? 2F : 0F,
         Action.MORTAR, hid ? 4.5F : d > 9 ? (sniped ? 2.8F : 1.6F) : 0.4F,
         Action.HURL, sniped ? 2.6F : d > 14 ? 1.2F : 0F,
         Action.CHARGE, d > 10 && d < 34 ? (hid ? 3F : ph == 3 ? 2.2F : 1.5F) : 0F,
         Action.PULSE, near >= 2 ? 2.6F : near == 1 ? 1.2F : 0F,
         Action.ISOTOPES, ph >= 2 ? (sniped ? 3F : 1.6F) : 0F,
         Action.GEYSERS, ph >= 2 ? (hid ? 3.5F : 2F) : 0F,
         Action.SPRAY, ph >= 2 && d < 20 ? 1.6F : 0F,
         Action.BEAM, ph == 3 ? (hid ? 0.2F : sniped ? 3F : 1.8F) : 0F,
         Action.ARC, ph == 3 && targets(40).size() >= 2 ? 2.2F : ph == 3 ? 0.8F : 0F,
         Action.MELTDOWN, ph == 3 ? 1.4F : 0F);
   }

   protected int cooldownOf(Action a) {
      boolean fast = phase() == 3;
      return switch (a) {
         case CLAW -> 20;
         case SLAM -> fast ? 100 : 140;
         case MORTAR -> fast ? 140 : 180;
         case HURL -> fast ? 160 : 220;
         case CHARGE -> fast ? 260 : 220;
         case PULSE -> 160;
         case ISOTOPES -> 320;
         case GEYSERS -> 240;
         case SPRAY -> 260;
         case BEAM -> 300;
         case ARC -> 200;
         case MELTDOWN -> 560;
         default -> 0;
      };
   }

   protected Action chainAfter(Action a) {
      // phase 3 : trois charges d'affilée
      if (a == Action.CHARGE && phase() == 3 && chargeCount < 2 && !this.isDeadOrDying()) {
         chargeCount++;
         return Action.CHARGE;
      }
      if (a == Action.CHARGE) chargeCount = 0;
      return null;
   }

   protected void onActionEnd(ServerLevel level, Action a) {
      if (held != null && held.isHeld()) {
         held.discard();
         held = null;
      }
      if (a == Action.SPAWN || a == Action.PHASE2 || a == Action.PHASE3) chargeCount = 0;
   }

   protected void ambient(ServerLevel level) {
      int ph = phase();
      // flaques radioactives : on s'y brûle tant qu'on reste dedans
      pools.removeIf(p -> p.until() < this.tickCount);
      if (this.tickCount % 10 == 0) {
         for (Pool p : pools) {
            for (Player pl : targets(90)) {
               if (pl.position().distanceToSqr(p.c().x, pl.getY(), p.c().z) > p.r() * p.r() || Math.abs(pl.getY() - p.c().y) > 2.5) continue;
               if (!hurtPlayer(pl, this.damageSources().indirectMagic(this, this), 2.5F, "pool")) continue;
               pl.addEffect(new MobEffectInstance(HeraliszN07.IRRADIATED.get(), 60, 0));
               pl.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            }
         }
      }
      if (this.tickCount % 7 == 0) {
         for (Pool p : pools) level.sendParticles(ModParticles.RAD_EMBER.get(), p.c().x, p.c().y + 0.2, p.c().z, 1, p.r() * 0.4, 0.05, p.r() * 0.4, 0.01);
      }
      // geysers programmés
      geysers.removeIf(s -> {
         if (s.tick() > this.tickCount) return false;
         geyser(level, s.at());
         return true;
      });
      isotopes.removeIf(o -> !o.isAlive());
      if (this.isDeadOrDying()) return;
      // aura : en phase 2+, il irradie ceux qui le collent
      if (ph >= 2 && this.tickCount % 20 == 0) {
         for (Player p : targets(ph == 3 ? 7 : 5)) {
            if (!hurtPlayer(p, this.damageSources().indirectMagic(this, this), 2F, "aura")) continue;
            p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1));
         }
      }
      if (this.tickCount % 5 == 0) {
         Vec3 m = mouth();
         if (ph < 3) level.sendParticles(ModParticles.TOXIC_SMOKE.get(), m.x, m.y - 0.3, m.z, 1, 0.15, 0.05, 0.15, 0.005);
         if (ph >= 2) {
            Vec3 v = vat();
            level.sendParticles(ModParticles.RAD_EMBER.get(), v.x, v.y, v.z, 2, 0.8, 1.2, 0.8, 0.02);
            level.sendParticles(ModParticles.ACID_DROP.get(), getX(), getY() + 3.5, getZ(), 1, 1.2, 1.0, 1.2, 0);
         }
         if (ph == 3) {
            Vec3 c = core();
            level.sendParticles(ModParticles.TOXIC_SPARK.get(), c.x, c.y, c.z, 2, 0.25, 0.25, 0.25, 0.04);
         }
      }
      // compteur Geiger : il crépite de plus en plus fort avec les phases
      if (this.tickCount % 70 == 0 && random.nextInt(3) > 0) sound(BossSounds.N7_GEIGER.get(), 0.8F + 0.4F * ph, 0.9F + 0.1F * ph);
   }

   private void addPool(ServerLevel level, Vec3 c, float r, int dur) {
      if (pools.size() >= 28) pools.remove(0);
      pools.add(new Pool(c, r, this.tickCount + dur));
      Vfx.send(level, Vfx.Type.POOL, c, Vec3.ZERO, r, 0, dur, 0xFF000000 | RAD, 0);
   }

   private void irradiate(Player p, int seconds, int amp) {
      p.addEffect(new MobEffectInstance(HeraliszN07.IRRADIATED.get(), seconds * 20, amp));
      p.addEffect(new MobEffectInstance(MobEffects.HUNGER, seconds * 40, 1));
      p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, seconds * 30, 0));
   }

   protected void runAction(ServerLevel level, Action a, int t, LivingEntity target) {
      switch (a) {
         case SPAWN -> spawnSeq(level, t);
         case PHASE2 -> phase2(level, t);
         case PHASE3 -> phase3(level, t);
         case CLAW -> claw(level, t, target);
         case SLAM -> slam(level, t, target);
         case MORTAR -> mortar(level, t, target);
         case HURL -> hurl(level, t, target);
         case CHARGE -> charge(level, t, target);
         case PULSE -> pulse(level, t);
         case ISOTOPES -> isotopes(level, t, target);
         case GEYSERS -> geyserSeq(level, t);
         case SPRAY -> spray(level, t);
         case BEAM -> beam(level, t, target);
         case ARC -> arc(level, t, target);
         case MELTDOWN -> meltdown(level, t);
         default -> {
         }
      }
   }

   private void roar(ServerLevel level, float pitch) {
      sound((phase() == 3 ? BossSounds.N7_ROAR_RAW : BossSounds.N7_ROAR).get(), 5F, pitch);
      Vfx.shake(level, this.position(), 1.0F, 44, 22);
   }

   private void impactFx(ServerLevel level, Vec3 at, float r) {
      Vfx.debris(level, at, r * 0.45F, 7F, 26, ground(at), Vec3.ZERO);
      Vfx.shockwave(level, at, r * 1.8F, 14, RAD);
      Vfx.shake(level, at, 1.1F, 36, 14);
      level.sendParticles(ModParticles.DUST.get(), at.x, at.y + 0.3, at.z, 40, r * 0.4, 0.3, r * 0.4, 0.08);
      level.playSound(null, at.x, at.y, at.z, BossSounds.N7_SLAM.get(), SoundSource.HOSTILE, 4F, 0.9F + random.nextFloat() * 0.2F);
   }

   // ------------------------------------------------------------------ cinématiques
   /**
    * Apparition : l'alarme de confinement hurle, il est voûté, endormi, pendant que sa cuve se remplit de liquide radioactif ;
    * les verres de son respirateur s'allument, il se redresse, arrache la chaîne rivée à son poignet, abat son bras monstrueux
    * sur le sol (l'onde fissure le sol tout autour) et rugit.
    */
   private void spawnSeq(ServerLevel level, int t) {
      Vec3 o = surf(this.position());
      if (t == 1) {
         startCinematic(level, o, SPAWN, Action.SPAWN.ticks());
         sound(BossSounds.N7_ALARM.get(), 4F, 1F);
         sound(BossSounds.DG_ARC_ZAP.get(), 4F, 0.8F);
      }
      if (t < SPAWN_PRE) {
         if (t % 40 == 10) sound(BossSounds.N7_ALARM.get(), 2.5F, 0.85F);
         if (t % 20 == 0) {
            Vec3 c = o.add(0, 3, 0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.6F + t / 80F, 0.6F);
         }
         if (t == 52 || t == 78) sound(BossSounds.N7_BUBBLE.get(), 2F, 0.7F);
         if (t == 64) sound(BossSounds.DG_ARC_ZAP.get(), 3F, 0.6F);
         if (t % 4 == 0) level.sendParticles(ModParticles.RAD_EMBER.get(), o.x, o.y + 1.5, o.z, 1, 1.4, 1.2, 1.4, 0.02);
         return;
      }
      // dans la cuve : il flotte, des bulles montent, les arcs des bobines frappent le verre
      if (t < SPAWN_BREAK && t % 2 == 0) {
         level.sendParticles(ModParticles.RAD_EMBER.get(), o.x, o.y + 1.5, o.z, 2, 1.6, 1.0, 1.6, 0.06);
      }
      if (t < SPAWN_BREAK && t % 9 == 4) sound(BossSounds.DG_ARC_ZAP.get(), 3F, 0.8F + random.nextFloat() * 0.4F);
      if (t == SPAWN_EYES) {
         Vfx.flash(level, at(2.1, 0, 7.3), 2.5F, 6, RAD);
         sound(BossSounds.N7_BREATH.get(), 4F, 0.9F);
         sound(SoundEvents.BEACON_ACTIVATE, 2F, 0.6F);
      }
      if (t == SPAWN_HIT1 || t == SPAWN_HIT2) {
         // le bras monstrueux frappe le verre : il se fêle
         Vec3 g = at(2.9, 0.6, 4.2);
         sound(BossSounds.N7_CRACK.get(), 4F, t == SPAWN_HIT1 ? 0.8F : 1.0F);
         sound(BossSounds.N7_SLAM.get(), 3F, 1.3F);
         Vfx.flash(level, g, 1.6F, 4, HOT);
         Vfx.shake(level, o, t == SPAWN_HIT1 ? 0.25F : 0.35F, 28, 6);
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, g.x, g.y, g.z, 20, 0.6, 0.6, 0.6, 0.3);
      }
      if (t == SPAWN_BREAK) breakContainment(level);
      if (t == SPAWN_LAND) {
         impactFx(level, o, 5F);
         for (int i = 0; i < 6; i++) {
            double ang = i * Mth.TWO_PI / 6 + random.nextDouble() * 0.5;
            Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vfx.crack(level, o.add(d.scale(1.5)), d, 7F + random.nextFloat() * 5, 1.4F, 200, RAD);
         }
      }
      if (t == SPAWN_FILL0) sound(BossSounds.N7_BUBBLE.get(), 3F, 0.8F);
      if (t > SPAWN_LAND && t < SPAWN_FILL1 && t % 3 == 0) level.sendParticles(ModParticles.TOXIC_SMOKE.get(), o.x, o.y + 0.4, o.z, 3, 3, 0.2, 3, 0.005);
      if (t == SPAWN_ROAR) {
         roar(level, 0.9F);
         Vfx.shockwave(level, o, 18F, 24, RAD);
      }
   }

   /**
    * La cuve de confinement vole en éclats : détonation, éclats de verre, le liquide déferle sur la salle (repousse ceux qui sont
    * trop près) ; son volume de collision disparaît (noté par le sanctuaire, il sera reconstruit après le combat).
    */
   private void breakContainment(ServerLevel level) {
      Vec3 c = this.position();
      sound(BossSounds.DG_GLASS_BURST.get(), 5F, 1F);
      Vfx.flash(level, c.add(0, 5, 0), 9F, 8, RAD);
      Vfx.shake(level, c, 0.5F, 36, 14);
      Vfx.debris(level, c.add(0, 5, 0), 3.2F, 1.6F, 70, Blocks.LIME_STAINED_GLASS.defaultBlockState(), Vec3.ZERO);
      Vfx.debris(level, c.add(0, 2, 0), 3.0F, 1.2F, 40, Blocks.GLASS.defaultBlockState(), Vec3.ZERO);
      Vfx.shockwave(level, c, 16F, 30, RAD);
      level.sendParticles(ModParticles.ACID_SPLASH.get(), c.x, c.y + 1.0, c.z, 160, 3.0, 1.5, 3.0, 0.4);
      level.sendParticles(ModParticles.TOXIC_SMOKE.get(), c.x, c.y + 2.0, c.z, 60, 3.0, 2.0, 3.0, 0.05);
      for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(9))) {
         if (p.isCreative() || p.isSpectator() || fr.heralisz.n07.scene.CinematicGuard.isProtected(p)) continue;
         Vec3 d = p.position().subtract(c).multiply(1, 0, 1);
         double dist = d.length();
         if (dist < 0.1) continue;
         double k = 1.1 * (1 - dist / 10);
         p.push(d.x / dist * k, 0.35, d.z / dist * k);
         p.hurtMarked = true;
      }
      // la cuve n'est plus un obstacle
      BlockPos b = this.blockPosition();
      var shrine = fr.heralisz.n07.dungeon.ShrineBlockEntityServeur.nearestServeur(level, b, 72);
      boolean tank = false;
      for (BlockPos p : BlockPos.betweenClosed(b.offset(-1, -1, -1), b.offset(1, 1, 1))) {
         if (level.getBlockEntity(p) instanceof fr.heralisz.n07.dungeon.PropBlockEntity pb && pb.kind == fr.heralisz.n07.dungeon.PropKind.CONTAINMENT_TANK) {
            tank = true;
            break;
         }
      }
      if (!tank) return;
      for (BlockPos p : BlockPos.betweenClosed(b.offset(-5, 0, -5), b.offset(5, 14, 5))) {
         var st = level.getBlockState(p);
         if (!st.is(fr.heralisz.n07.dungeon.DungeonRegistry.PROP_SOLID.get())) continue;
         if (shrine != null) shrine.recordChange(p, st);
         level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
      }
   }

   /** Phase 2 : la cuve bout et se fêle, il y plaque la main ; elle explose, le liquide l'inonde, les os lui percent les épaules. */
   private void phase2(ServerLevel level, int t) {
      if (t == 1) {
         startCinematic(level, this.position(), PHASE2, Action.PHASE2.ticks());
         sound(BossSounds.N7_HURT.get(), 4F, 0.8F);
         chargeCount = 0;
         pauseAttacks();
      }
      if (t == 18 || t == 44) sound(BossSounds.N7_BUBBLE.get(), 4F, t == 18 ? 1.1F : 0.9F);
      if (t == 37 || t == 56) {
         Vec3 v = vat();
         level.playSound(null, v.x, v.y, v.z, BossSounds.N7_CRACK.get(), SoundSource.HOSTILE, 3.5F, t == 37 ? 0.9F : 1.1F);
      }
      if (t > 18 && t < P2_BURST && t % 3 == 0) {
         Vec3 v = valve();
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), v.x, v.y, v.z, 3, 0.3, 0.2, 0.3, 0.05);
      }
      if (t == P2_BURST) {
         setPhase(2);
         Vec3 v = vat();
         Vfx.flash(level, v, 7F, 8, 0xC8FF8A);
         level.sendParticles(ModParticles.GLASS_SHARD.get(), v.x, v.y, v.z, 80, 1.2, 1.2, 1.2, 0.6);
         level.sendParticles(ModParticles.ACID_DROP.get(), v.x, v.y + 1, v.z, 80, 2.5, 1.5, 2.5, 0.3);
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), v.x, v.y, v.z, 40, 1.5, 1.5, 1.5, 0.1);
         sound(BossSounds.N7_VAT_BURST.get(), 5F, 1F);
         Vec3 o = surf(this.position());
         Vfx.shockwave(level, o, 16F, 18, RAD);
         Vfx.shake(level, o, 1.3F, 44, 22);
         for (Player p : hitCircle(level, o, 12, 8, 1.8, 0.6, "p2_burst")) irradiate(p, 4, 0);
         addPool(level, o, 7.5F, 360);
         for (int i = 0; i < 5; i++) {
            double ang = i * Mth.TWO_PI / 5 + random.nextDouble();
            addPool(level, surf(o.add(Math.cos(ang) * 9, 0, Math.sin(ang) * 9)), 2.4F, 260);
         }
      }
      if (t == P2_BONES) {
         Vec3 s = at(-0.2, 0, 5.2);
         Vfx.debris(level, s, 1F, 4F, 14, Blocks.BONE_BLOCK.defaultBlockState(), Vec3.ZERO);
         sound(BossSounds.N7_BONES.get(), 4F, 1F);
      }
      if (t == 99) roar(level, 0.85F);
   }

   /** Phase 3 : il arrache son respirateur, rugit gueule à nu, puis se plante le tuyau de la cuve dans le cœur : tout s'allume. */
   private void phase3(ServerLevel level, int t) {
      if (t == 1) {
         startCinematic(level, this.position(), PHASE3, Action.PHASE3.ticks());
         sound(BossSounds.N7_HURT.get(), 4F, 0.7F);
         chargeCount = 0;
         pauseAttacks();
      }
      if (t == 27 || t == 45) sound(BossSounds.N7_BREATH.get(), 4F, t == 27 ? 1.1F : 1.25F);
      if (t == P3_MASK) {
         Vec3 h = at(2.1, -0.4, 5.2);
         Vfx.debris(level, h, 0.7F, 5F, 10, Blocks.BLACK_CONCRETE.defaultBlockState(), right().scale(-0.8));
         Vfx.debris(level, h, 0.5F, 4F, 6, Blocks.IRON_BLOCK.defaultBlockState(), right().scale(-0.8));
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), h.x, h.y, h.z, 30, 0.4, 0.4, 0.4, 0.08);
         sound(BossSounds.N7_MASK_RIP.get(), 4F, 1F);
      }
      if (t == 68) roar(level, 1F);
      if (t == 104) sound(SoundEvents.WARDEN_HEARTBEAT, 3F, 0.8F);
      if (t == P3_STAB) {
         setPhase(3);
         Vec3 c = core();
         Vfx.flash(level, c, 6F, 10, HOT);
         level.sendParticles(ModParticles.TOXIC_SPARK.get(), c.x, c.y, c.z, 60, 0.4, 0.4, 0.4, 0.4);
         sound(BossSounds.N7_STAB.get(), 5F, 1F);
         sound(SoundEvents.WARDEN_HEARTBEAT, 4F, 1.2F);
      }
      if (t == 144) {
         Vec3 o = surf(this.position());
         Vfx.nova(level, o, 22F, 18);
         Vfx.shockwave(level, o, 22F, 22, RAD);
         for (Player p : hitCircle(level, o, 14, 12, 2.2, 0.7, "p3_nova")) irradiate(p, 5, 0);
         roar(level, 0.9F);
         sound(BossSounds.N7_PULSE.get(), 4F, 0.7F);
      }
   }

   // ------------------------------------------------------------------ phase 1
   /** Revers du bras monstrueux (cône de 160°). */
   private void claw(ServerLevel level, int t, LivingEntity target) {
      if (t < 10 && target != null) faceTarget(target);
      if (t == 3) Vfx.cone(level, this.position(), fwd(), 8.5F, 160F, 11);
      if (t == 8) sound(BossSounds.N7_GROWL.get(), 3F, 1.2F);
      if (t == 14) {
         sound(BossSounds.N7_SWIPE.get(), 3.5F, 0.9F + random.nextFloat() * 0.2F);
         Vfx.slash(level, this.position().add(0, 1.5, 0), fwd(), 8F, 160F, -1);
         for (Player p : inCone(8.5, 80)) {
            if (!hurtPlayer(p, this.damageSources().mobAttack(this), 18, "claw")) continue;
            Vec3 d = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ()).normalize();
            p.setDeltaMovement(d.x * 1.8, 0.55, d.z * 1.8);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
         }
      }
   }

   /** Abattage : le bras frappe le sol devant lui et une fissure radioactive court vers la cible en jaillissant par à-coups. */
   private void slam(ServerLevel level, int t, LivingEntity target) {
      if (t < 14 && target != null) faceTarget(target);
      if (t == 2) {
         fissureFrom = surf(at(3.2, 1.2, 0));
         Vec3 to = target != null ? target.position().subtract(fissureFrom) : fwd().scale(20);
         fissureDir = new Vec3(to.x, 0, to.z).lengthSqr() < 1e-3 ? fwd() : new Vec3(to.x, 0, to.z).normalize();
         fissureLen = (int) Mth.clamp(to.horizontalDistance() + 5, 10, 30);
         Vfx.circle(level, fissureFrom, 4.5F, 16);
         Vfx.line(level, fissureFrom, fissureDir, fissureLen, 3.2F, 20);
      }
      if (t == 18 && fissureFrom != null) {
         impactFx(level, fissureFrom, 5F);
         for (Player p : hitCircle(level, fissureFrom, 4.5, 24, 1.2, 0.8, "slam")) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
         Vfx.crack(level, fissureFrom, fissureDir, fissureLen, 1.8F, 160, RAD);
      }
      // la fissure : un jaillissement tous les 2 ticks, tous les 3 blocs
      if (t > 18 && fissureFrom != null && (t - 18) % 2 == 0) {
         int k = (t - 18) / 2;
         double d = 3 + k * 3.0;
         if (d <= fissureLen) {
            Vec3 p = surf(fissureFrom.add(fissureDir.scale(d)));
            Vfx.column(level, p, 1.3F, 6F, 20);
            Vfx.debris(level, p, 0.8F, 5F, 6, ground(p), Vec3.ZERO);
            for (Player pl : hitCircle(level, p, 2.2, 16, 0.3, 1.1, "slam_column")) irradiate(pl, 3, 0);
            if (k % 2 == 0) level.playSound(null, p.x, p.y, p.z, BossSounds.N7_GEYSER.get(), SoundSource.HOSTILE, 2.5F, 1.2F + random.nextFloat() * 0.2F);
         }
      }
   }

   /** Mortier : il tourne la vanne de sa cuve ; trois obus d'acide sous pression partent en cloche (le premier sur le tireur le plus éloigné). */
   private void mortar(ServerLevel level, int t, LivingEntity target) {
      if (t < 12 && target != null) faceTarget(target);
      if (t == 12) {
         Vec3 v = valve();
         level.playSound(null, v.x, v.y, v.z, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.HOSTILE, 2F, 0.6F);
      }
      if (t == 18 || t == 26 || t == 34) {
         Player far = farthest(60);
         List<Player> ps = targets(60);
         LivingEntity who = t == 18 && far != null ? far : !ps.isEmpty() ? ps.get(random.nextInt(ps.size())) : target;
         if (who == null) return;
         Vec3 from = valve();
         int flight = (int) Mth.clamp(from.distanceTo(who.position()) * 0.7, 16, 34);
         Vec3 aim = surf(who.position().add(who.getDeltaMovement().multiply(flight * 0.6, 0, flight * 0.6))
            .add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3));
         N07Shell s = new N07ShellServeur(level, this, N07Shell.SHELL);
         s.launch(from, aim, flight);
         mark(s);
         level.addFreshEntity(s);
         Vfx.circle(level, aim, 3.6F, flight);
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), from.x, from.y, from.z, 12, 0.3, 0.3, 0.3, 0.08);
         level.playSound(null, from.x, from.y, from.z, BossSounds.N7_MORTAR.get(), SoundSource.HOSTILE, 4F, 0.9F + random.nextFloat() * 0.2F);
      }
   }

   /** Impact d'un obus (SHELL) ou d'une goutte (DROP). */
   public void shellImpact(ServerLevel level, Vec3 at, int kind) {
      if (kind == N07Shell.SHELL) {
         Vfx.flash(level, at.add(0, 0.8, 0), 3.5F, 6, RAD);
         Vfx.debris(level, at, 1.2F, 5F, 12, ground(at), Vec3.ZERO);
         Vfx.shockwave(level, at, 5F, 10, RAD);
         level.sendParticles(ModParticles.ACID_SPLASH.get(), at.x, at.y + 0.3, at.z, 30, 1.2, 0.3, 1.2, 0.2);
         level.playSound(null, at.x, at.y, at.z, BossSounds.N7_SPLASH.get(), SoundSource.HOSTILE, 3.5F, 0.9F + random.nextFloat() * 0.2F);
         for (Player p : hitCircle(level, at, 3.6, 16, 0.6, 0.5, "shell")) irradiate(p, 4, 1);
         addPool(level, at, 3.2F, 180);
      } else {
         level.sendParticles(ModParticles.ACID_SPLASH.get(), at.x, at.y + 0.2, at.z, 8, 0.4, 0.1, 0.4, 0.1);
         level.playSound(null, at.x, at.y, at.z, BossSounds.N7_SPLASH.get(), SoundSource.HOSTILE, 1.4F, 1.4F + random.nextFloat() * 0.3F);
         for (Player p : hitCircle(level, at, 1.9, 8, 0, 0, "drop")) irradiate(p, 3, 0);
         addPool(level, at, 1.7F, 120);
      }
   }

   /** Il arrache un bloc de béton du sol et le lance sur le tireur le plus éloigné. */
   private void hurl(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         Player far = farthest(60);
         prey = far != null ? far : target;
      }
      if (t < 14 && prey != null) faceTarget(prey);
      if (t == 14) {
         Vec3 c = surf(claws());
         Vfx.debris(level, c, 1.6F, 5F, 18, ground(c), Vec3.ZERO);
         Vfx.shake(level, c, 0.7F, 30, 10);
         level.playSound(null, c.x, c.y, c.z, BossSounds.N7_RIP.get(), SoundSource.HOSTILE, 4F, 1F);
         held = new N07BoulderServeur(level, this);
         held.hold(heldPos(t));
         mark(held);
         level.addFreshEntity(held);
      }
      if (held != null && held.isHeld() && t > 14 && t < 30) {
         held.hold(heldPos(t));
         if (prey != null && t > 22) faceTarget(prey);
      }
      if (t == 30 && held != null && held.isHeld()) {
         Vec3 from = held.position();
         LivingEntity who = prey != null && prey.isAlive() ? prey : target;
         Vec3 aim = who != null ? who.position() : at(20, 0, 0);
         int flight = (int) Mth.clamp(from.distanceTo(aim) / 1.6, 8, 28);
         aim = surf(aim.add(who != null ? who.getDeltaMovement().multiply(flight * 0.5, 0, flight * 0.5) : Vec3.ZERO));
         held.throwAt(aim, flight);
         Vfx.circle(level, aim, 4.2F, flight);
         sound(BossSounds.N7_THROW.get(), 4F, 0.9F);
         held = null;
      }
   }

   /** Trajet du bloc dans sa main pendant le geste (arraché devant, levé, armé au-dessus de la tête). */
   private Vec3 heldPos(int t) {
      float u = (t - 14) / 16F;
      Vec3[] path = {at(3.0, 1.7, 0.9), at(2.2, 1.9, 3.8), at(0.4, 1.9, 6.4), at(-0.6, 1.8, 7.4), at(-0.4, 1.7, 7.2)};
      float x = Mth.clamp(u, 0, 0.999F) * (path.length - 1);
      int i = (int) x;
      return path[i].lerp(path[i + 1], x - i);
   }

   public void boulderImpact(ServerLevel level, Vec3 at) {
      Vfx.debris(level, at, 2F, 7F, 34, Blocks.GRAY_CONCRETE.defaultBlockState(), Vec3.ZERO);
      Vfx.debris(level, at, 1.6F, 6F, 20, ground(at), Vec3.ZERO);
      Vfx.shockwave(level, at, 7F, 12, 0xD8D0C0);
      Vfx.shake(level, at, 1.3F, 40, 16);
      level.sendParticles(ModParticles.DUST.get(), at.x, at.y + 0.5, at.z, 50, 1.8, 0.6, 1.8, 0.1);
      level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5F, 0.7F);
      level.playSound(null, at.x, at.y, at.z, BossSounds.N7_SLAM.get(), SoundSource.HOSTILE, 4F, 0.8F);
      for (Player p : hitCircle(level, at, 4.2, 26, 1.4, 0.7, "boulder")) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
      if (phase() >= 2) addPool(level, at, 2.6F, 160);
   }

   /** Charge tête baissée en appui sur son bras ; en phase 2+ il laisse une traînée d'acide, en phase 3 il enchaîne trois charges. */
   private void charge(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         Player far = farthest(48);
         LivingEntity who = far != null && (phase() >= 2 || random.nextBoolean()) ? far : target;
         Vec3 d = who != null ? new Vec3(who.getX() - getX(), 0, who.getZ() - getZ()) : fwd();
         chargeDir = d.lengthSqr() < 1e-3 ? fwd() : d.normalize();
         faceTo(this.position().add(chargeDir));
         float len = (float) Mth.clamp(d.length() + 6, 12, 34);
         Vfx.line(level, surf(this.position()), chargeDir, len, 4.4F, 14);
         sound(BossSounds.N7_GROWL.get(), 4F, 0.9F);
         chargeStopped = false;
      }
      if (chargeStopped) {
         this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
         return;
      }
      if (t < 14) faceTo(this.position().add(chargeDir));
      if (t >= 14 && t < 48) {
         Vec3 next = this.position().add(chargeDir.scale(1.6));
         if (inLeash(next) != next || this.horizontalCollision && t > 16) {
            // bord de son territoire, ou un mur : il s'arrête net
            if (this.horizontalCollision) {
               Vfx.shake(level, this.position(), 1.2F, 30, 12);
               Vfx.debris(level, this.position().add(chargeDir.scale(2)).add(0, 1.5, 0), 1.5F, 5F, 16, Blocks.STONE.defaultBlockState(), Vec3.ZERO);
               sound(BossSounds.N7_SLAM.get(), 4F, 0.8F);
            }
            this.setDeltaMovement(Vec3.ZERO);
            chargeStopped = true;
            return;
         }
         this.setDeltaMovement(chargeDir.x * 0.95, this.getDeltaMovement().y, chargeDir.z * 0.95);
         faceTo(this.position().add(chargeDir));
         if (t % 4 == 0) {
            Vec3 f = surf(this.position());
            Vfx.debris(level, f, 1F, 4F, 6, ground(f), chargeDir.scale(-0.5));
            level.playSound(null, f.x, f.y, f.z, BossSounds.N7_STEP.get(), SoundSource.HOSTILE, 2.5F, 1.1F);
            if (phase() >= 2 && t % 8 == 0) addPool(level, f, 1.8F, 140);
         }
         for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(1.4).move(chargeDir.scale(1.2)))) {
            if (p.isCreative() || p.isSpectator() || !hitOnce.add(p.getUUID())) continue;
            if (!hurtPlayer(p, this.damageSources().mobAttack(this), 24, "charge")) continue;
            p.setDeltaMovement(chargeDir.x * 2.0, 0.9, chargeDir.z * 2.0);
            p.hurtMarked = true;
            sound(BossSounds.N7_SLAM.get(), 3F, 1.2F);
         }
      }
      if (t == 48) {
         this.setDeltaMovement(Vec3.ZERO);
         Vec3 f = surf(this.position());
         Vfx.debris(level, f, 1.8F, 5F, 14, ground(f), chargeDir.scale(0.6));
      }
   }

   /** Onde radioactive : il se replie, la cuve s'illumine, puis tout son corps s'ouvre d'un coup. */
   private void pulse(ServerLevel level, int t) {
      Vec3 o = surf(this.position());
      if (t == 2) {
         Vfx.circle(level, o, 11F, 22);
         sound(BossSounds.N7_CHARGE_UP.get(), 4F, 1F);
      }
      if (t > 4 && t < 24 && t % 3 == 0) {
         Vec3 c = core();
         level.sendParticles(ModParticles.TOXIC_SPARK.get(), c.x, c.y, c.z, 4, 1.2, 1.2, 1.2, 0.02);
      }
      if (t == 24) {
         Vfx.shockwave(level, o, 12F, 16, RAD);
         Vfx.nova(level, o, 12F, 14);
         for (Player p : hitCircle(level, o, 11, 18, 2.2, 0.7, "pulse")) irradiate(p, 5, 0);
         sound(BossSounds.N7_PULSE.get(), 5F, 1F);
         Vfx.shake(level, o, 1.0F, 30, 12);
      }
   }

   // ------------------------------------------------------------------ phase 2
   /** Isotopes : il arrache les tumeurs de son avant-bras et les jette en l'air ; elles dérivent vers les tireurs. */
   private void isotopes(ServerLevel level, int t, LivingEntity target) {
      if (t < 16 && target != null) faceTarget(target);
      if (t == 20) {
         Vec3 h = at(1.6, 1.7, 3.0);
         level.sendParticles(ModParticles.ACID_SPLASH.get(), h.x, h.y, h.z, 20, 0.3, 0.3, 0.3, 0.2);
         level.sendParticles(ModParticles.TOXIC_SPARK.get(), h.x, h.y, h.z, 20, 0.3, 0.3, 0.3, 0.2);
         sound(BossSounds.N7_RIP_FLESH.get(), 4F, 1F);
      }
      if (t == 28) {
         List<Player> ps = new ArrayList<>(targets(60));
         ps.sort(Comparator.comparingDouble(p -> -p.distanceTo(this)));
         int n = Math.min(6, 3 + ps.size() / 2);
         Vec3 from = at(0.6, -1.4, 6.0);
         for (int i = 0; i < n; i++) {
            LivingEntity who = !ps.isEmpty() ? ps.get(i % ps.size()) : target;
            if (who == null) break;
            double ang = (i - (n - 1) / 2.0) * 0.5;
            Vec3 v = fwd().yRot((float) ang).scale(0.18).add(0, 0.34 + random.nextDouble() * 0.1, 0);
            N07Isotope o = new N07IsotopeServeur(level, this, who, from, v);
            mark(o);
            level.addFreshEntity(o);
            isotopes.add(o);
         }
         Vfx.flash(level, from, 2.5F, 5, RAD);
         sound(BossSounds.N7_THROW.get(), 3F, 1.3F);
      }
   }

   /** Un isotope a atteint sa cible (ou sa fin de vie) : explosion radioactive. */
   public void isotopeBlast(ServerLevel level, Vec3 at) {
      Vfx.nova(level, at, 5F, 10);
      Vfx.flash(level, at, 4F, 6, HOT);
      level.sendParticles(ModParticles.TOXIC_SPARK.get(), at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.3);
      level.playSound(null, at.x, at.y, at.z, BossSounds.N7_ISOTOPE_BOOM.get(), SoundSource.HOSTILE, 4F, 0.9F + random.nextFloat() * 0.2F);
      for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(at, at).inflate(4.2))) {
         if (p.isCreative() || p.isSpectator() || p.distanceToSqr(at) > 4.2 * 4.2) continue;
         if (!hurtPlayer(p, this.damageSources().indirectMagic(this, this), 20, "isotope")) continue;
         irradiate(p, 6, 1);
      }
      addPool(level, surf(at), 2.4F, 160);
   }

   /** Geysers : il enfonce ses poings dans le sol ; trois vagues de geysers jaillissent sous les joueurs (cercles annoncés). */
   private void geyserSeq(ServerLevel level, int t) {
      if (t == 4 || t == 16 || t == 28) {
         for (Player p : targets(52)) {
            Vec3 m = surf(p.position().add(p.getDeltaMovement().multiply(6, 0, 6)));
            Vfx.circle(level, m, 2.8F, 14);
            geysers.add(new Strike(m, this.tickCount + 14));
         }
      }
      if (t == 18) {
         Vec3 c = surf(at(2.8, 0, 0));
         impactFx(level, c, 5F);
         hitCircle(level, c, 5, 20, 1.0, 0.8, "geyser_fist");
      }
   }

   private void geyser(ServerLevel level, Vec3 m) {
      Vfx.column(level, m, 1.6F, 9F, 26);
      Vfx.debris(level, m, 1F, 6F, 10, ground(m), Vec3.ZERO);
      level.sendParticles(ModParticles.ACID_SPLASH.get(), m.x, m.y + 1, m.z, 30, 0.8, 1.5, 0.8, 0.3);
      level.playSound(null, m.x, m.y, m.z, BossSounds.N7_GEYSER.get(), SoundSource.HOSTILE, 3.5F, 0.9F + random.nextFloat() * 0.2F);
      for (Player p : hitCircle(level, m, 2.8, 18, 0.2, 1.4, "geyser")) irradiate(p, 4, 0);
      addPool(level, m, 2.0F, 120);
   }

   /** Aspersion : il se secoue comme un chien mouillé, le liquide qui le couvre gicle tout autour (gouttes annoncées au sol). */
   private void spray(ServerLevel level, int t) {
      if (t == 12) sound(BossSounds.N7_SPRAY.get(), 4F, 1F);
      if (t >= 12 && t <= 44 && t % 2 == 0) {
         for (int i = 0; i < 2; i++) {
            double ang = random.nextDouble() * Mth.TWO_PI, dist = 6 + random.nextDouble() * 18;
            Vec3 from = at(0, 0, 4.2);
            Vec3 aim = surf(this.position().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist));
            int flight = (int) Mth.clamp(dist * 0.9, 10, 22);
            N07Shell s = new N07ShellServeur(level, this, N07Shell.DROP);
            s.launch(from, aim, flight);
            mark(s);
            level.addFreshEntity(s);
            Vfx.circle(level, aim, 1.8F, flight);
         }
         // qui reste collé à lui prend la douche
         for (Player p : targets(5)) {
            if (!hurtPlayer(p, this.damageSources().indirectMagic(this, this), 2, "spray_hug")) continue;
            irradiate(p, 2, 0);
         }
      }
   }

   // ------------------------------------------------------------------ phase 3
   /** Rayon : la gorge se charge, puis un rayon qui suit lentement la cible ; les murs l'arrêtent. */
   private void beam(ServerLevel level, int t, LivingEntity target) {
      if (t == 0) {
         prey = target;
         sound(BossSounds.N7_BEAM_CHARGE.get(), 5F, 1F);
      }
      LivingEntity who = prey != null && prey.isAlive() ? prey : target;
      if (t < BEAM_FIRE) {
         if (who != null) faceTarget(who);
         Vec3 end = beamTrace(level, who != null ? who.getEyePosition() : mouth().add(fwd().scale(40)));
         this.entityData.set(BEAM_END_POS, end.toVector3f());
         if (t % 3 == 0) {
            Vec3 m = mouth();
            level.sendParticles(ModParticles.TOXIC_SPARK.get(), m.x, m.y, m.z, 3, 0.8, 0.8, 0.8, 0.02);
         }
         return;
      }
      if (t == BEAM_FIRE) sound(BossSounds.N7_BEAM_FIRE.get(), 6F, 1F);
      if (t >= BEAM_END) return;
      // il suit sa cible, mais lentement : on peut le semer en courant perpendiculairement
      if (who != null) {
         float want = (float) (Mth.atan2(who.getZ() - getZ(), who.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
         this.yBodyRot = Mth.approachDegrees(this.yBodyRot, want, 2.4F);
         this.setYRot(this.yBodyRot);
         this.yHeadRot = this.yBodyRot;
      }
      Vec3 m = mouth();
      double dy = who != null ? who.getEyeY() - 0.4 - m.y : -2;
      double dh = who != null ? Math.max(4, Math.hypot(who.getX() - m.x, who.getZ() - m.z)) : 20;
      Vec3 dir = fwd().scale(dh).add(0, Mth.clamp(dy, -dh * 0.7, dh * 0.5), 0).normalize();
      Vec3 end = beamTrace(level, m.add(dir.scale(48)));
      this.entityData.set(BEAM_END_POS, end.toVector3f());
      if (t % 3 == 0) {
         Vfx.scorch(level, surf(end), 1.6F, 100);
         level.sendParticles(ModParticles.TOXIC_SPARK.get(), end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.2);
         level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.02);
      }
      if (t % 4 == 0) {
         Vec3 seg = end.subtract(m);
         double len = seg.length();
         Vec3 n = seg.scale(1 / Math.max(1e-3, len));
         for (Player p : targets(52)) {
            Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(m);
            double along = c.dot(n);
            if (along < 0 || along > len + 0.8) continue;
            if (c.subtract(n.scale(along)).length() > 1.3) continue;
            if (!hurtPlayer(p, this.damageSources().indirectMagic(this, this), 8, "beam")) continue;
            irradiate(p, 3, 1);
         }
      }
   }

   /** Point d'arrêt du rayon vers `aim` (premier bloc touché, ou 48 blocs). */
   private Vec3 beamTrace(ServerLevel level, Vec3 aim) {
      Vec3 m = mouth();
      Vec3 d = aim.subtract(m);
      Vec3 far = m.add(d.normalize().scale(48));
      BlockHitResult r = level.clip(new ClipContext(m, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      return r.getType() == HitResult.Type.MISS ? far : r.getLocation();
   }

   /** Arc radioactif : il frappe la cible puis saute vers le joueur le plus proche de la dernière victime (jusqu'à 4 sauts). */
   private void arc(ServerLevel level, int t, LivingEntity target) {
      if (t < 18 && target != null) faceTarget(target);
      if (t == 4) sound(BossSounds.N7_CHARGE_UP.get(), 3F, 1.4F);
      if (t > 4 && t < 20 && t % 2 == 0) {
         Vec3 h = at(0.2, -1.6, 6.5);
         level.sendParticles(ModParticles.TOXIC_SPARK.get(), h.x, h.y, h.z, 4, 0.4, 0.6, 0.4, 0.08);
      }
      if (t == 20) {
         Vec3 from = at(1.5, -1.4, 4.5);
         List<Player> left = new ArrayList<>(targets(40));
         Player cur = target instanceof Player tp && left.contains(tp) ? tp : left.stream().min(Comparator.comparingDouble(p -> p.distanceTo(this))).orElse(null);
         float d = 16;
         for (int hop = 0; hop < 5 && cur != null; hop++) {
            Vec3 to = cur.position().add(0, 1, 0);
            Vfx.send(level, Vfx.Type.ARC, from, to.subtract(from), 1F, 0, 10, 0xFF000000 | RAD, 0);
            if (hurtPlayer(cur, this.damageSources().indirectMagic(this, this), d, "arc")) {
               irradiate(cur, 3, 0);
               cur.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
            }
            level.sendParticles(ModParticles.TOXIC_SPARK.get(), to.x, to.y, to.z, 16, 0.3, 0.5, 0.3, 0.2);
            level.playSound(null, to.x, to.y, to.z, BossSounds.N7_ARC.get(), SoundSource.HOSTILE, 3F, 0.9F + hop * 0.08F);
            left.remove(cur);
            final Vec3 c = cur.position();
            Player next = left.stream().filter(p -> p.position().distanceTo(c) < 10).min(Comparator.comparingDouble(p -> p.position().distanceTo(c))).orElse(null);
            from = to;
            cur = next;
            d *= 0.85F;
         }
      }
   }

   /** Criticité : à genoux, son cœur s'emballe ; puis tout explose autour de lui. Seul un mur entre lui et soi protège. */
   private void meltdown(ServerLevel level, int t) {
      Vec3 o = surf(this.position());
      if (t == 2) {
         Vfx.circle(level, o, 24F, MELT_BOOM - 2);
         sound(BossSounds.N7_ALARM.get(), 5F, 1.1F);
         for (Player p : targets(40)) p.displayClientMessage(Component.translatable("boss.heralisz_n07.sujet_n07.cover"), true);
      }
      if (t > 10 && t < MELT_BOOM) {
         int period = t < 40 ? 10 : t < 58 ? 6 : 3;
         if (t % period == 0) {
            Vec3 c = core();
            Vfx.flash(level, c, 2F + t / 30F, 4, HOT);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3F, 0.8F + t / 100F);
         }
         if (t % 2 == 0) level.sendParticles(ModParticles.TOXIC_SPARK.get(), o.x, o.y + 0.3, o.z, 6, 8, 0.2, 8, 0.05);
      }
      if (t == 40) sound(BossSounds.N7_CHARGE_UP.get(), 5F, 0.7F);
      if (t == MELT_BOOM) {
         Vec3 c = core();
         Vfx.send(level, Vfx.Type.MELTDOWN, o, Vec3.ZERO, 26F, 0, 30, 0xFF000000 | RAD, 0);
         Vfx.shockwave(level, o, 26F, 24, RAD);
         Vfx.flash(level, c, 14F, 10, HOT);
         Vfx.shake(level, o, 1.8F, 60, 30);
         sound(BossSounds.N7_MELTDOWN.get(), 7F, 1F);
         for (Player p : targets(26)) {
            BlockHitResult r = level.clip(new ClipContext(c, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (r.getType() != HitResult.Type.MISS) continue;          // à couvert
            float k = (float) (1 - p.distanceTo(this) / 30);
            if (!hurtPlayer(p, this.damageSources().indirectMagic(this, this), Math.max(12, 34 * k), "meltdown")) continue;
            irradiate(p, 8, 1);
            Vec3 d = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            d = d.lengthSqr() < 1e-3 ? fwd() : d.normalize();
            p.setDeltaMovement(d.x * 2.0 * k, 0.6, d.z * 2.0 * k);
            p.hurtMarked = true;
         }
         for (int i = 0; i < 6; i++) {
            double ang = i * Mth.TWO_PI / 6 + random.nextDouble() * 0.5;
            addPool(level, surf(o.add(Math.cos(ang) * (6 + random.nextDouble() * 8), 0, Math.sin(ang) * (6 + random.nextDouble() * 8))), 2.6F, 240);
         }
      }
   }

   protected void deathTick(ServerLevel level, int t) {
      Vec3 o = surf(this.position());
      if (t == 1) {
         sound(BossSounds.N7_DEATH.get(), 5F, 1F);
         geysers.clear();
         for (N07Isotope i : isotopes) if (i.isAlive()) i.discard();
         isotopes.clear();
         if (held != null) held.discard();
         held = null;
      }
      if (t == 32) {
         Vfx.debris(level, o, 2.2F, 5F, 22, ground(o), Vec3.ZERO);
         Vfx.shake(level, o, 1.1F, 40, 16);
         level.sendParticles(ModParticles.DUST.get(), o.x, o.y + 0.3, o.z, 40, 2.5, 0.3, 2.5, 0.06);
         sound(BossSounds.N7_STEP.get(), 5F, 0.6F);
         sound(SoundEvents.ANVIL_LAND, 1.2F, 0.4F);
      }
      if (t > 38 && t < VITRIFY) {
         int period = t < 100 ? 8 : 4;
         if (t % period == 0) {
            Vec3 c = core();
            Vfx.flash(level, c, 1.5F + (t - 38) / 30F, 4, HOT);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3F, 0.8F + (t - 38) / 100F);
            level.sendParticles(ModParticles.TOXIC_SPARK.get(), c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.15);
         }
      }
      if (t == 78) sound(BossSounds.N7_HURT.get(), 3F, 0.6F);
      if (t == 106) sound(BossSounds.N7_BREATH.get(), 3F, 0.7F);
      if (t == 134) sound(BossSounds.N7_ROAR_RAW.get(), 5F, 0.8F);
      if (t == VITRIFY) {
         // la fusion : un éclair, et le verre le prend depuis le cœur
         Vec3 c = core();
         Vfx.flash(level, c, 16F, 12, HOT);
         Vfx.send(level, Vfx.Type.MELTDOWN, o, Vec3.ZERO, 14F, 0, 24, 0xFF000000 | RAD, 0);
         Vfx.shockwave(level, o, 18F, 22, RAD);
         Vfx.shake(level, o, 1.5F, 60, 26);
         sound(BossSounds.N7_VITRIFY.get(), 6F, 1F);
         for (Player p : targets(10)) if (!fr.heralisz.n07.scene.CinematicGuard.isProtected(p)) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
      }
      if (t > VITRIFY && t < VITRIFY + 60 && t % 5 == 0) {
         // le verre refroidit : il craque et fume
         level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1 + random.nextDouble() * 4, getZ(), 3, 1.2, 1.2, 1.2, 0.01);
         if (t % 10 == 0) sound(SoundEvents.GLASS_HIT, 2.2F, 0.5F + random.nextFloat() * 0.4F);
      }
      if (t == 210 && (encounter == null || encounter.loot())) {
         List<ItemStack> loot = new ArrayList<>();
         loot.add(new ItemStack(HeraliszN07.N07_RESPIRATOR.get()));
         loot.add(new ItemStack(HeraliszN07.N07_CORE.get()));
         loot.add(new ItemStack(HeraliszN07.N07_BADGE.get()));
         loot.add(new ItemStack(HeraliszN07.TRINITITE.get(), 8 + random.nextInt(9)));
         dropLoot(level, core().add(fwd().scale(0.4)), loot, 700);
         Vfx.flash(level, core(), 4F, 8, RAD);
         sound(SoundEvents.AMETHYST_BLOCK_CHIME, 2F, 0.6F);
      }
      if (t == DEATH_TICKS) {
         if (encounter == null) BossRemainsServeur.spawn(level, BossRemains.Kind.SUJET_N07, this.position(), this.yBodyRot, phase());
         else if (encounter.remains()) {
            BossRemains r = BossRemainsServeur.spawn(level, BossRemains.Kind.SUJET_N07, this.position(), this.yBodyRot, phase(), encounter.remainsLife());
            if (r != null) {
               mark(r);
               encounter.remainsSpawned(r);
            }
         }
      }
   }

   /** Apparition passee par les joueurs : la cuve cede tout de suite, il retombe au sol et l'action s'arrete. */
   public void skipSpawn() {
      if (this.level().isClientSide || action() != Action.SPAWN || dormant()) return;
      ServerLevel level = (ServerLevel) this.level();
      int t = actionTime();
      if (t < SPAWN_BREAK) breakContainment(level);
      if (t < SPAWN_LAND) impactFx(level, surf(this.position()), 4F);
      skipCurrentAction();
   }

   /** Marque une entite creee pour un combat pilote (le jeu peut la retrouver et la purger). */
   private void mark(net.minecraft.world.entity.Entity e) {
      if (encounter != null) encounter.mark(e);
   }

   /**
    * Debut d'une cinematique de phase : rien ne reste en suspens contre les joueurs figes (geysers programmes, isotopes en vol,
    * bloc tenu) ; les flaques et l'aura ne touchent pas les joueurs proteges.
    */
   private void pauseAttacks() {
      geysers.clear();
      for (N07Isotope i : isotopes) if (i.isAlive()) i.discard();
      isotopes.clear();
      if (held != null) held.discard();
      held = null;
   }

   /** Nettoyage complet (combat interrompu) : flaques et projectiles. */
   public void clearHazards() {
      pauseAttacks();
      pools.clear();
   }

   @Override
   public void remove(RemovalReason r) {
      for (N07Isotope i : isotopes) if (i.isAlive()) i.discard();
      isotopes.clear();
      if (held != null) held.discard();
      // (repris de HeraliszBoss.remove, appelé ensuite dans le mod d'origine)
      bossBar.removeAllPlayers();
      if (encounter != null && !this.level().isClientSide) encounter.removed(this, r);
      super.remove(r);
   }

   // ------------------------------------------------------------------ sons
   @Override
   protected SoundEvent getAmbientSound() {
      return phase() == 3 ? BossSounds.N7_GROWL.get() : BossSounds.N7_BREATH.get();
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource s) {
      return BossSounds.N7_HURT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected float getSoundVolume() {
      return 2.5F;
   }

   @Override
   public int getAmbientSoundInterval() {
      return 160;
   }

   @Override
   public float getVoicePitch() {
      return 0.9F + this.random.nextFloat() * 0.2F;
   }

   @Override
   protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
      this.playSound(BossSounds.N7_STEP.get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
      if (this.level() instanceof ServerLevel sl && this.tickCount % 2 == 0) Vfx.shake(sl, this.position(), 0.15F, 16, 4);
   }

   // ---- repris de HeraliszBoss (logique serveur de la classe mère)

   /** Barre de boss (créée par le constructeur de HeraliszBoss dans le mod d'origine : nom de l'entité, vert). */
   protected final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.heralisz_n07." + animId), BossEvent.BossBarColor.GREEN,
      BossEvent.BossBarOverlay.PROGRESS);
   private final float[] dpsWindow = new float[20];
   protected final int[] cooldowns = new int[acts.length];
   protected final Map<UUID, Float> threat = new HashMap<>();
   protected final Set<UUID> hitOnce = new HashSet<>();
   protected BlockPos home;
   protected int globalCd = 30;
   private int lonelyTicks, peakPlayers = 1, peakHold;
   private boolean fromSave, orphan;
   public boolean devPassive;
   /** Combat pilote par un autre mod (N07Api) : reglages propres, evenements ; null = comportement autonome d'origine. */
   public fr.heralisz.n07.encounter.EncounterImpl encounter;

   public void setFlags(int f) {
      this.entityData.set(FLAGS, f);
   }

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

   public void setHome(BlockPos p) {
      this.home = p;
   }

   /** Le rituel est accompli : il se réveille (son apparition joue depuis le début, cinématique comprise). */
   public void wake() {
      if (!dormant()) return;
      this.entityData.set(DORMANT, false);
      start(acts[1]);
   }

   /** Arrete net l'action en cours (cinematique passee par les joueurs). */
   public void skipCurrentAction() {
      if (this.level().isClientSide || this.isDeadOrDying()) return;
      stopAction((ServerLevel) this.level());
      this.setInvisible(false);
      globalCd = 30;
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
   @Override
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

   @Override
   public void devPhase(int n) {
      setPhase(n);
      this.setHealth(this.getMaxHealth() * (n == 1 ? 1F : n == 2 ? 0.6F : 0.3F));
      this.entityData.set(ACTION, 0);
   }

   @Override
   public void setDevPassive(boolean on) {
      devPassive = on;
   }

   // ------------------------------------------------------------------ cycle de vie
   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide && this.tickCount == 0 && !fromSave) {
         if (home == null) home = this.blockPosition();
         start(acts[1]);
      }
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      ServerLevel level = (ServerLevel) this.level();
      if (orphan && encounter == null) {
         this.discard();
         return;
      }
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
         this.entityData.set(VIRTUAL_MAX, (int) effectiveVirtualHp());
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
         if (encounter != null) encounter.actionTick(this, a.ordinal(), t, a.ticks());
         if (action() == a && t >= a.ticks()) {
            onActionEnd(level, a);
            if (encounter != null && a.ordinal() >= 1 && a.ordinal() <= 3) encounter.cinematicEnd(this, a.ordinal() - 1);
            Action next = chainAfter(a);
            if (next != null) {
               start(next);
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
         start(acts[2]);
         if (encounter != null) encounter.phase(this, 2);
         return;
      }
      if (phase() == 2 && hp <= 0.33F) {
         start(acts[3]);
         if (encounter != null) encounter.phase(this, 3);
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

   protected List<Player> targets(double r) {
      return this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(r),
         p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) <= r && (encounter == null || encounter.targetable(p)));
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

   /** PV virtuels en vigueur : reglage du combat pilote, sinon config x taille du groupe. */
   protected float effectiveVirtualHp() {
      return encounter != null ? encounter.virtualHp(peakPlayers) : virtualHpBase() * groupMult();
   }

   protected float groupMult() {
      return 1F + Math.max(0, peakPlayers - BossConfig.GROUP_BASE.get()) * BossConfig.GROUP_STEP.get().floatValue();
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

   /** Sol sous le point (descente depuis sa hauteur, pas le haut de la colonne : marche sous un toit). */
   protected double surfaceY(Vec3 p) {
      return Ground.floorY(this.level(), p.x, p.y, p.z);
   }

   protected Vec3 surf(Vec3 p) {
      return new Vec3(p.x, surfaceY(p), p.z);
   }

   protected BlockState ground(Vec3 p) {
      BlockState s = this.level().getBlockState(BlockPos.containing(p.x, surfaceY(p) - 0.5, p.z));
      return s.isAir() ? Blocks.DIRT.defaultBlockState() : s;
   }

   protected float dmg(float base) {
      float mult = encounter != null ? encounter.damageMultiplier() : BossConfig.DAMAGE_MULT.get().floatValue();
      return base * mult * (phase() == 3 ? 1.25F : 1F);
   }

   /**
    * Blesse un joueur avec une attaque nommee (kind : id stable, voir N07Api / API.md). Rend false si le joueur est protege
    * (cinematique en cours) : ni degats ni effets a appliquer. En combat pilote, le jeu peut remapper les degats.
    */
   protected boolean hurtPlayer(Player p, DamageSource src, float base, String kind) {
      if (fr.heralisz.n07.scene.CinematicGuard.isProtected(p)) return false;
      if (encounter != null && !encounter.targetable(p)) return false;
      float d = dmg(base);
      if (encounter != null) d = encounter.remap(p, kind, base, d, phase());
      if (d > 0) p.hurt(src, d);
      return true;
   }

   protected void sound(SoundEvent s, float vol, float pitch) {
      this.level().playSound(null, getX(), getY() + 2, getZ(), s, SoundSource.HOSTILE, vol, pitch);
   }

   /** Dégâts de zone sur les joueurs : cercle horizontal de rayon r autour de c ; renvoie les joueurs touchés. */
   protected List<Player> hitCircle(ServerLevel level, Vec3 c, double r, float damage, double push, double lift, String kind) {
      List<Player> hit = new ArrayList<>();
      for (Player p : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r, 3, r))) {
         if (p.isCreative() || p.isSpectator() || p.position().distanceToSqr(c.x, p.getY(), c.z) > r * r) continue;
         if (!hurtPlayer(p, this.damageSources().mobAttack(this), damage, kind)) continue;
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
      if (encounter != null) {
         AABB a = encounter.arena();
         if (a == null || p.x > a.minX + 1 && p.x < a.maxX - 1 && p.z > a.minZ + 1 && p.z < a.maxZ - 1) return p;
         return surf(new Vec3(Mth.clamp(p.x, a.minX + 1.5, a.maxX - 1.5), p.y, Mth.clamp(p.z, a.minZ + 1.5, a.maxZ - 1.5)));
      }
      if (home == null) return p;
      double r = BossConfig.LEASH_RADIUS.get() - 4;
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
      ExperienceOrb.award(level, c, xp);
   }

   // ------------------------------------------------------------------ garde-fous
   private void leash(ServerLevel level) {
      if (home == null || encounter != null && !encounter.leash()) return;
      int r = BossConfig.LEASH_RADIUS.get();
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(home).inflate(r + 8), p -> !p.isCreative() && !p.isSpectator()).isEmpty();
      lonelyTicks = anyone ? 0 : lonelyTicks + 20;
      if (Math.sqrt(this.blockPosition().distSqr(home)) > r || lonelyTicks > 200) {
         stopAction(level);
         this.setTarget(null);
         this.teleportTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5);
         this.setHealth(this.getMaxHealth());
         setPhase(1);
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

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide || this.isDeadOrDying()) return false;
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
      Action a = action();
      int o = a.ordinal();
      if (o >= 1 && o <= 3) return false;
      float mult = damageTaken(a, actionTime());
      if (mult <= 0) return false;
      float raw = Math.min(amount, encounter != null ? encounter.hitCap() : BossConfig.HIT_CAP.get().floatValue());
      Vec3 weak = weakPoint();
      if (WeakPoint.hit(source, this, weak, weakRadius())) {
         raw *= WeakPoint.CRIT;
         WeakPoint.feedback(this, source, weak);
      }
      float window = 0;
      for (float v : dpsWindow) window += v;
      if (window > (encounter != null ? encounter.dpsCap() : BossConfig.DPS_CAP.get())) raw *= 0.25F;
      dpsWindow[this.tickCount % 20] += raw;
      raw *= mult;
      float scaled = raw * this.getMaxHealth() / Math.max(1F, effectiveVirtualHp());
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
         startCinematic(level, this.position(), BossInfo.DEATH, deathTicks());
         if (encounter != null) encounter.deathStart(this);
      }
      deathTick(level, this.deathTime);
      if (this.deathTime >= deathTicks()) {
         bossBar.removeAllPlayers();
         if (encounter != null) encounter.deathEnd(this);
         this.remove(RemovalReason.KILLED);
      }
   }

   /** Saute la mort scenarisee (joueurs qui passent la cinematique) : la statue apparait au tick suivant. */
   public void skipDeath() {
      if (this.isDeadOrDying() && this.deathTime < deathTicks() - 1) this.deathTime = deathTicks() - 1;
   }

   @Override
   protected void dropAllDeathLoot(DamageSource src) {
      if (encounter != null && !encounter.loot()) return;
      super.dropAllDeathLoot(src);
   }

   // ------------------------------------------------------------------ cinematiques : spectateurs explicites, joueurs proteges
   /** Spectateurs d'une cinematique : les joueurs du combat pilote, sinon ceux du hall (64 blocs, meme etage a 32 pres). */
   protected List<ServerPlayer> cinematicViewers(ServerLevel level) {
      if (encounter != null) return encounter.viewers(level);
      List<ServerPlayer> out = new ArrayList<>();
      for (ServerPlayer p : level.players()) {
         double dx = p.getX() - getX(), dz = p.getZ() - getZ();
         if (dx * dx + dz * dz < 64 * 64 && Math.abs(p.getY() - getY()) < 32) out.add(p);
      }
      return out;
   }

   /** Lance une cinematique du boss pour ses spectateurs : ils sont proteges (aucun degat) le temps du plan. */
   protected void startCinematic(ServerLevel level, Vec3 pos, int kind, int ticks) {
      List<ServerPlayer> viewers = cinematicViewers(level);
      fr.heralisz.n07.scene.CinematicGuard.protect(viewers, ticks + 10);
      boolean skippable = encounter != null && encounter.skippable(kind);
      Vfx.cinematicTo(level, pos, this.getId(), kind, ticks, viewers, skippable);
      if (encounter != null) encounter.cinematicStart(this, kind, ticks, viewers);
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Phase", phase());
      tag.putBoolean("BossSaved", true);
      tag.putBoolean("Dormant", dormant());
      if (encounter != null) tag.putBoolean("N07Encounter", true);
      if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      fromSave = tag.getBoolean("BossSaved");
      // un boss de combat pilote recharge sans son combat (redemarrage) : il n'a plus rien a faire la
      if (tag.getBoolean("N07Encounter")) orphan = true;
      if (tag.getBoolean("Dormant")) setDormant(true);
      setPhase(Math.max(1, tag.getInt("Phase")));
      this.setInvisible(false);
      if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
   }
}
