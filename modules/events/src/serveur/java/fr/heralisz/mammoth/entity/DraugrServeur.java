package fr.heralisz.mammoth.entity;

import fr.heralisz.mammoth.MammothConfig;
import fr.heralisz.mammoth.dungeon.ModBlocks;
import fr.heralisz.mammoth.fx.Particles;
import fr.heralisz.mammoth.sound.Sounds;
import fr.heralisz.mammoth.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link Draugr} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new Draugr(...) » côté serveur.
 */
public class DraugrServeur extends Draugr {
   public DraugrServeur(EntityType<? extends Draugr> type, Level level) {
      super(type, level);
   }

   private BlockPos socket;
   /** Lance lancée : plantée ici (null tant qu'elle vole) ; throwCd entre deux lancers. */
   private BlockPos spearAt;
   private int throwCd = 60, spearWait;
   private UUID master;
   private int attackCd = 20;

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
   }

   /** En fait un Gardien : 100 PV, deux fois plus grand, sort de sa glace (animation « thaw »). */
   public void makeGuardian() {
      this.entityData.set(GUARDIAN, true);
      this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
      this.setHealth(100F);
      this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.27);
      this.refreshDimensions();
      setState(S_THAW);
   }

   public void setMaster(Mammoth m) {
      this.master = m.getUUID();
   }

   private void setState(int s) {
      this.entityData.set(STATE, s);
      this.entityData.set(STATE_TICK, this.tickCount);
   }

   private MammothServeur master(ServerLevel level) {
      return master != null && level.getEntity(master) instanceof MammothServeur m ? m : null;
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      ServerLevel level = (ServerLevel) this.level();
      MammothServeur m = master(level);
      int st = state();
      int t = stateTime();
      boolean orphan = m == null || !m.isAlive() || (m.dormant() && !guardian());
      if (this.tickCount > 40 && orphan) {
         if (st == S_CORPSE || guardian()) this.discard();
         else this.kill();
         return;
      }
      if (st == S_CORPSE) {
         this.getNavigation().stop();
         this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
         return;
      }
      if (st == S_THAW) {
         this.getNavigation().stop();
         if (t == 24) {
            playSound(Sounds.ICE_SHATTER.get(), 3F, 0.8F);
            level.sendParticles(Particles.SHARD.get(), getX(), getY() + 2.5, getZ(), 40, 1.0, 1.5, 1.0, 0.2);
         }
         if (t == 30) playSound(Sounds.ROAR.get(), 2.5F, 1.4F);
         if (t >= 60) setState(S_FREE);
         return;
      }
      if (st == S_RUN || st == S_RIP) {
         heartScene(level, m, st, t);
         return;
      }
      if (st == S_RISE) {
         this.getNavigation().stop();
         if (t == 18) {
            playSound(Sounds.ICE_CRACK.get(), 1.6F, 1.3F);
            level.sendParticles(Particles.SHARD.get(), getX(), getY() + 1.2, getZ(), 18, 0.4, 0.7, 0.4, 0.15);
            level.sendParticles(Particles.MIST.get(), getX(), getY() + 1, getZ(), 6, 0.4, 0.5, 0.4, 0.02);
         }
         if (t >= RISE) setState(S_FREE);
         return;
      }
      if (attackCd > 0) attackCd--;
      LivingEntity target = this.getTarget();
      if (st == S_FREE && t == 0 && guardian()) attackCd = 30;
      if (this.tickCount % 10 == 0 || target == null || !target.isAlive()) {
         target = nearest(level);
         this.setTarget(target);
      }
      if (st == S_THRUST) {
         this.getNavigation().stop();
         if (target != null && t < THRUST_HIT - 2) face(target.position());
         if (t == THRUST_HIT - 3) playSound(Sounds.WHOOSH.get(), 1.2F, 1.6F);
         if (t == THRUST_HIT) {
            Vec3 f = Vec3.directionFromRotation(0, this.yBodyRot);
            for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(3.4))) {
               Vec3 to = p.position().subtract(this.position());
               double along = to.dot(f), across = Math.abs(to.x * f.z - to.z * f.x);
               double reach = guardian() ? 5.5 : 3.4;
            if (along < 0 || along > reach || across > (guardian() ? 2.0 : 1.2) || p.isCreative() || p.isSpectator()) continue;
               p.hurt(this.damageSources().mobAttack(this), (guardian() ? 10F : 7F) * MammothConfig.DAMAGE_MULT.get().floatValue());
               p.setDeltaMovement(p.getDeltaMovement().add(f.x * 0.5, 0.15, f.z * 0.5));
               p.hurtMarked = true;
               Vfx.screen(p, 0.12F, 0F, 20);
               playSound(Sounds.BONE_HIT.get(), 1.4F, 1.4F);
            }
         }
         if (t >= THRUST) {
            setState(S_FREE);
            attackCd = 26 + random.nextInt(14);
         }
         return;
      }
      if (throwCd > 0) throwCd--;
      if (st == S_SWEEP) {
         sweepTick(level, target, t);
         return;
      }
      if (st == S_THROW) {
         throwTick(level, target, t);
         return;
      }
      if (st == S_PICK) {
         this.getNavigation().stop();
         if (spearAt != null) face(Vec3.atCenterOf(spearAt));
         if (t == PICK_AT) takeSpearBack(level);
         if (t >= PICK) setState(S_FREE);
         return;
      }
      if (spearPlanted()) {                                             // sans sa lance, il va la reprendre avant tout
         if (spearAt == null) {
            this.getNavigation().stop();
            if (target != null) this.getLookControl().setLookAt(target, 30F, 30F);
            if (++spearWait > 200) takeSpearBack(level);
            return;
         }
         if (st != S_FETCH) setState(S_FETCH);
         Vec3 g = Vec3.atBottomCenterOf(spearAt);
         if (Math.sqrt(this.distanceToSqr(g.x, this.getY(), g.z)) < 2.6) {
            this.noPhysics = false;
            setState(S_PICK);
            return;
         }
         steerTo(g, stateTime(), 1.5);
         return;
      }
      if (target == null) {
         this.getNavigation().stop();
         return;
      }
      double d = this.distanceTo(target);
      if (guardian() && attackCd == 0 && throwCd == 0 && d > 9 && d < 30 && this.hasLineOfSight(target) && this.random.nextInt(4) == 0) {
         face(target.position());
         setState(S_THROW);
         return;
      }
      this.getLookControl().setLookAt(target, 30F, 30F);
      if (d < (guardian() ? 5.5 : 3.0) && attackCd == 0) {
         face(target.position());
         setState(guardian() && this.random.nextInt(5) < 2 ? S_SWEEP : S_THRUST);
         return;
      }
      if (this.tickCount % 6 == 0 || this.getNavigation().isDone()) {
         double sp = guardian() ? 1.25 : 1.1;
         if (!this.getNavigation().moveTo(target, sp)) this.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), sp);
      }
   }

   /** Balayage au ras du sol : long armé tremblant et sifflement, puis fauche tout l'avant ; qui saute au bon moment passe dessus. */
   private void sweepTick(ServerLevel level, LivingEntity target, int t) {
      this.getNavigation().stop();
      this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
      if (target != null && t < 10) face(target.position());
      Vec3 f = Vec3.directionFromRotation(0, this.yBodyRot);
      if (t == 2) playSound(Sounds.ROAR.get(), 1.6F, 1.7F);
      if (t >= 4 && t < SWEEP_HIT && t % 3 == 0) {                      // l'arc de givre au sol annonce la zone
         for (int i = -4; i <= 4; i++) {
            double a = Math.toRadians(i * 20);
            Vec3 dir = new Vec3(f.x * Math.cos(a) - f.z * Math.sin(a), 0, f.x * Math.sin(a) + f.z * Math.cos(a));
            Vec3 p = this.position().add(dir.scale(4.5));
            level.sendParticles(Particles.MIST.get(), p.x, p.y + 0.15, p.z, 1, 0.2, 0.02, 0.2, 0.0);
         }
      }
      if (t == SWEEP_HIT - 3) playSound(Sounds.WHOOSH.get(), 2F, 0.8F);
      if (t == SWEEP_HIT) {
         for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(6.5, 1, 6.5))) {
            if (p.isCreative() || p.isSpectator()) continue;
            Vec3 to = p.position().subtract(this.position());
            double h = Math.sqrt(to.x * to.x + to.z * to.z);
            if (h > 6.2 || (h > 0.5 && (to.x * f.x + to.z * f.z) / h < -0.25)) continue;
            if (p.getY() > this.getY() + 0.6) continue;                    // en l'air : la lance passe sous ses pieds
            p.hurt(this.damageSources().mobAttack(this), 12F * MammothConfig.DAMAGE_MULT.get().floatValue());
            Vec3 k = h > 0.01 ? new Vec3(to.x / h, 0, to.z / h) : f;
            p.setDeltaMovement(p.getDeltaMovement().add(k.x * 0.9, 0.3, k.z * 0.9));
            p.hurtMarked = true;
            Vfx.hit(p, 0.6F);
            playSound(Sounds.BONE_HIT.get(), 1.6F, 1.0F);
         }
         level.sendParticles(Particles.SHARD.get(), getX() + f.x * 3, getY() + 0.3, getZ() + f.z * 3, 24, 2.2, 0.1, 2.2, 0.15);
      }
      if (t >= SWEEP) {
         setState(S_FREE);
         attackCd = 30 + random.nextInt(16);
      }
   }

   /** Lancer : il vise en anticipant la course du joueur ; la lance part en cloche et se plante là où elle tombe. */
   private void throwTick(ServerLevel level, LivingEntity target, int t) {
      this.getNavigation().stop();
      this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
      if (target != null && t < THROW_AT) face(target.position());
      if (t == 3) playSound(Sounds.ROAR.get(), 1.4F, 1.5F);
      if (t == THROW_AT) {
         Vec3 f = Vec3.directionFromRotation(0, this.yBodyRot);
         Vec3 hand = this.position().add(0, guardian() ? 4.4 : 2.2, 0).add(f.scale(0.8));
         Vec3 aim = target != null ? target.position().add(0, 0.9, 0) : hand.add(f.scale(16));
         double flat = Math.sqrt(Math.pow(aim.x - hand.x, 2) + Math.pow(aim.z - hand.z, 2));
         double speed = 1.5, ticks = Math.max(4, flat / speed);
         if (target != null) aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(ticks * 0.7));
         flat = Math.sqrt(Math.pow(aim.x - hand.x, 2) + Math.pow(aim.z - hand.z, 2));
         ticks = Math.max(4, flat / speed);
         double vy = (aim.y - hand.y) / ticks + 0.5 * 0.035 * ticks;
         GuardianSpear sp = fr.heralisz.mammoth.HeraliszMammoth.GUARDIAN_SPEAR.get().create(level);
         if (sp != null) {
            sp.setOwner(this);
            sp.setPos(hand);
            sp.setDeltaMovement((aim.x - hand.x) / ticks, vy, (aim.z - hand.z) / ticks);
            level.addFreshEntity(sp);
         }
         this.entityData.set(SPEAR_DOWN, true);
         spearAt = null;
         spearWait = 0;
         playSound(Sounds.WHOOSH.get(), 2F, 0.6F);
      }
      if (t >= THROW) {
         setState(S_FREE);
         attackCd = 20;
         throwCd = 160 + random.nextInt(80);
      }
   }

   /** La lance est tombée : elle se plante (bloc) et il vient la chercher. */
   public void spearLanded(ServerLevel level, BlockPos at, float yaw) {
      if (!this.isAlive() || !spearPlanted()) return;
      if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).blocksMotion()) {
         MammothServeur m = master(level);
         fr.heralisz.mammoth.dungeon.HornAltarBlockEntityServeur alt = m != null ? m.altar() : null;
         if (alt != null) alt.recordChange(at, level.getBlockState(at));
         level.setBlock(at, ModBlocks.HERO.get(fr.heralisz.mammoth.dungeon.HeroKind.PLANTED_SPEAR).get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.fromYRot(yaw)), 3);
      }
      spearAt = at;
      Vfx.debris(level, Vec3.atBottomCenterOf(at), 0.5F, 2F, 6, ModBlocks.RIME_FLAGSTONE.get().defaultBlockState(), Vec3.ZERO);
   }

   private void takeSpearBack(ServerLevel level) {
      if (spearAt != null && level.getBlockState(spearAt).is(ModBlocks.HERO.get(fr.heralisz.mammoth.dungeon.HeroKind.PLANTED_SPEAR).get())) {
         level.setBlock(spearAt, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
         level.sendParticles(Particles.SHARD.get(), spearAt.getX() + 0.5, spearAt.getY() + 0.3, spearAt.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.1);
         playSound(Sounds.BONE_HIT.get(), 1.4F, 1.3F);
      }
      spearAt = null;
      this.entityData.set(SPEAR_DOWN, false);
   }

   private Player nearest(ServerLevel level) {
      Player best = null;
      double bd = 64 * 64;
      for (Player p : level.players()) {
         if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
         double d = p.distanceToSqr(this);
         if (d < bd) {
            bd = d;
            best = p;
         }
      }
      return best;
   }

   private void face(Vec3 p) {
      float yaw = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90F;
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
   }

   @Override
   public boolean hurt(DamageSource s, float amount) {
      int st = state();
      if ((st == S_RISE || st == S_THAW || st == S_RUN || st == S_RIP || st == S_CORPSE) && !s.is(DamageTypes.GENERIC_KILL)) return false;
      // le dernier Gardien ne meurt pas : il court arracher son cœur pour libérer Ymirak
      if (guardian() && this.level() instanceof ServerLevel level && this.getHealth() - amount <= 0 && !s.is(DamageTypes.GENERIC_KILL)) {
         MammothServeur m = master(level);
         boolean last = m != null && level.getEntitiesOfClass(DraugrServeur.class, this.getBoundingBox().inflate(80),
            d -> d != this && d.guardian() && d.isAlive() && d.state() != S_CORPSE && m.getUUID().equals(d.master)).isEmpty();
         if (last) {
            this.setHealth(1F);
            startHeartScene(level, m);
            return true;
         }
      }
      return super.hurt(s, amount);
   }

   // ------------------------------------------------------------------ la scène du cœur
   private void startHeartScene(ServerLevel level, MammothServeur m) {
      if (spearPlanted()) takeSpearBack(level);
      this.noPhysics = false;
      socket = null;
      // le piédestal, quelle que soit l'orientation de la salle : le plus proche de lui (il est au centre de la salle)
      BlockPos c = m.blockPosition();
      net.minecraft.world.phys.Vec3 ici = this.position();
      socket = fr.heralisz.mammoth.dungeon.BlocsProches.plusProche(level, c.offset(-48, -4, -48), c.offset(48, 8, 48),
         fr.heralisz.mammoth.dungeon.ModBlocks.HEART_SOCKET.get(), p -> p.distToCenterSqr(ici));
      if (socket == null) socket = c.offset(0, 3, -8);
      face(Vec3.atCenterOf(socket));
      setState(S_RUN);
      this.getNavigation().stop();
      if (MammothConfig.CINEMATICS.get()) Vfx.cinematic(level, this.position(), this.getId(), 7, 900);
      playSound(Sounds.ROAR.get(), 3F, 1.2F);
   }

   private Vec3 progAt, detour;
   private int progT, detourT, fails;

   /**
    * Avance vers un point en surveillant sa progression : bloqué (statue, pilier), il recalcule un détour de côté, puis un autre
    * de l'autre côté ; après trois échecs il force le passage en ligne droite (il écarte l'obstacle, sans téléportation).
    */
   private void steerTo(Vec3 goal, int t, double speed) {
      if (progAt == null || t == 0) {
         progAt = this.position();
         progT = t;
         fails = 0;
         detour = null;
      }
      if (this.noPhysics) {
         Vec3 dir = goal.subtract(this.position());
         double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
         if (len < 1.2 || t - detourT > 50) {
            this.noPhysics = false;
            fails = 0;
         } else {
            this.getNavigation().stop();
            double fs = Math.min(0.32, 0.1 * speed);                    // au pas, jamais une ruée
            this.setDeltaMovement(dir.x / len * fs, 0, dir.z / len * fs);
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
            face(goal);
            return;
         }
      }
      if (t - progT >= 16) {
         boolean stuck = this.position().distanceTo(progAt) < Math.min(1.2, 0.35 * speed);   // selon son allure (il boite : 0,75)
         progAt = this.position();
         progT = t;
         if (stuck) {
            fails++;
            if (fails >= 3) {                                            // plus de chemin : il passe en force
               this.noPhysics = true;
               detourT = t;
               detour = null;
               return;
            }
            Vec3 to = goal.subtract(this.position());
            double a = Math.atan2(to.z, to.x) + (fails % 2 == 1 ? 1 : -1) * Math.toRadians(75);
            detour = this.position().add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
            detourT = t;
            this.getNavigation().stop();
         } else if (detour == null) fails = 0;
      }
      Vec3 aim = detour != null && t - detourT < 24 ? detour : goal;
      if (aim == goal) detour = null;
      if (t % 8 == 0 || this.getNavigation().isDone()) {
         if (!this.getNavigation().moveTo(aim.x, aim.y, aim.z, speed)) this.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
      }
   }

   /** Le point devant le réceptacle, côté salle (le réceptacle regarde vers l'entrée). */
   private Vec3 standPoint(ServerLevel level) {
      BlockState st = level.getBlockState(socket);
      net.minecraft.core.Direction f = st.hasProperty(fr.heralisz.mammoth.dungeon.TwoStateBlock.FACING)
         ? st.getValue(fr.heralisz.mammoth.dungeon.TwoStateBlock.FACING) : net.minecraft.core.Direction.NORTH;
      BlockPos p = socket.relative(f, 3);
      while (!level.getBlockState(p.below()).blocksMotion() && p.getY() > socket.getY() - 6) p = p.below();
      while (level.getBlockState(p).blocksMotion()) p = p.above();
      return Vec3.atBottomCenterOf(p);
   }

   private void heartScene(ServerLevel level, MammothServeur m, int st, int t) {
      if (socket == null) {
         setState(S_FREE);
         return;
      }
      Vec3 stand = standPoint(level);
      Vec3 sock = Vec3.atCenterOf(socket);
      if (st == S_RUN) {
         // il court au piédestal par un vrai chemin (il contourne murs et piliers), aussi longtemps qu'il le faut
         double d = Math.sqrt(this.distanceToSqr(stand.x, this.getY(), stand.z));
         if (d > 1.0 && t < RUN_MAX) {
            steerTo(stand, t, 0.9);                                      // blessé à mort : il se traîne, il titube
            if (t % 16 == 0) playSound(Sounds.STEP.get(), 0.9F, 0.8F);
            if (t % 40 == 20) playSound(Sounds.HEARTBEAT.get(), 2F, 0.7F);
            if (t % 5 == 0) level.sendParticles(Particles.CINDER.get(), getX(), getY() + 4.2, getZ(), 2, 0.2, 0.2, 0.2, 0.02);
            return;
         }
         this.noPhysics = false;
         this.getNavigation().stop();
         if (d > 1.0) this.teleportTo(stand.x, stand.y, stand.z);         // filet de sécurité (bloqué 45 s)
         face(sock);                                                        // face au piédestal : la caméra est de l'autre côté
         setState(S_RIP);
         return;
      }
      this.getNavigation().stop();
      this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
      face(sock);
      // la caméra part quand son orientation (face au piédestal) est arrivée chez le client : on le voit de face
      if (t == 4 && MammothConfig.CINEMATICS.get()) Vfx.cinematic(level, this.position(), this.getId(), 5, CINE_LEN - 4);
      Vec3 fwdv = Vec3.directionFromRotation(0, this.yBodyRot);
      Vec3 side = new Vec3(-fwdv.z, 0, fwdv.x);
      if (t == PLANT) {                                                 // la lance plantée dans les dalles, à sa droite
         this.entityData.set(SPEAR_DOWN, true);
         BlockPos sp = BlockPos.containing(this.position().add(side.scale(-0.9)).add(fwdv.scale(1.6)));   // là où ses mains l'enfoncent
         if (level.getBlockState(sp).isAir() && !level.getBlockState(sp.below()).isAir()) {
            fr.heralisz.mammoth.dungeon.HornAltarBlockEntityServeur alt = m != null ? m.altar() : null;
            if (alt != null) alt.recordChange(sp, level.getBlockState(sp));
            level.setBlock(sp, fr.heralisz.mammoth.dungeon.ModBlocks.HERO.get(fr.heralisz.mammoth.dungeon.HeroKind.PLANTED_SPEAR).get()
               .defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, this.getDirection()), 3);
         }
         playSound(Sounds.BONE_HIT.get(), 3F, 0.5F);
         Vfx.shake(level, this.position(), 0.5F, 30, 8);
         Vfx.debris(level, Vec3.atBottomCenterOf(sp), 0.8F, 3F, 10, fr.heralisz.mammoth.dungeon.ModBlocks.RIME_FLAGSTONE.get().defaultBlockState(), Vec3.ZERO);
      }
      Vec3 chest = this.position().add(0, 4.4, 0).add(fwdv.scale(0.5));
      if (t == PLUNGE) {                                                // la main s'enfonce dans la poitrine
         playSound(Sounds.BONE_HIT.get(), 3F, 0.6F);
         playSound(Sounds.HEARTBEAT.get(), 4F, 1.4F);
         level.sendParticles(Particles.CINDER.get(), chest.x, chest.y, chest.z, 30, 0.3, 0.3, 0.3, 0.08);
         Vfx.flash(level, chest, 2.5F, 6);
      }
      if (t > PLUNGE && t < HEART_OUT) {                               // il tire, ça brûle
         if (t % 3 == 0) level.sendParticles(Particles.CINDER.get(), chest.x, chest.y, chest.z, 6, 0.25, 0.3, 0.25, 0.05);
         if (t % 8 == 0) playSound(Sounds.HEARTBEAT.get(), 3F, 1.5F);
      }
      if (t == HEART_OUT) {
         this.entityData.set(HEART, 1);
         playSound(Sounds.RIP.get(), 3F, 1.2F);
         playSound(Sounds.HEARTBEAT.get(), 4F, 1.0F);
         Vfx.flash(level, chest, 4F, 10);
         level.sendParticles(Particles.CINDER.get(), chest.x, chest.y, chest.z, 50, 0.4, 0.4, 0.4, 0.14);
      }
      if (t == ROAR_AT) playSound(Sounds.ROAR.get(), 4F, 0.9F);             // le rugissement, bras au ciel, le cœur brandi
      if (t > HEART_OUT && t < HEART_IN && (t - HEART_OUT) % 16 == 0) playSound(Sounds.HEARTBEAT.get(), 3F, 1.1F);
      if (t == HEART_IN) {
         this.entityData.set(HEART, 2);
         BlockState s = level.getBlockState(socket);
         if (s.hasProperty(fr.heralisz.mammoth.dungeon.TwoStateBlock.LIT)) {
            fr.heralisz.mammoth.dungeon.HornAltarBlockEntityServeur alt = m != null ? m.altar() : null;
            if (alt != null) alt.recordChange(socket, s);
            level.setBlock(socket, s.setValue(fr.heralisz.mammoth.dungeon.TwoStateBlock.LIT, true), 3);
         }
         playSound(Sounds.NOVA.get(), 4F, 0.8F);
         Vfx.flash(level, sock.add(0, 1, 0), 6F, 14);
         Vfx.shake(level, sock, 0.8F, 40, 14);
         level.sendParticles(Particles.CINDER.get(), sock.x, sock.y + 1, sock.z, 50, 0.4, 0.4, 0.4, 0.12);
      }
      if (t >= BURN_FROM && t <= BURN_TO) {                             // il se consume, des pieds à la tête
         int b = (t - BURN_FROM) * 100 / (BURN_TO - BURN_FROM);
         this.entityData.set(BURN, b);
         double y = this.getY() + 5.0 * b / 100.0;
         level.sendParticles(Particles.CINDER.get(), getX(), y, getZ(), 8, 0.9, 0.3, 0.9, 0.04);
         level.sendParticles(Particles.MIST.get(), getX(), y + 0.3, getZ(), 3, 0.8, 0.2, 0.8, 0.03);
         if (t % 12 == 0) playSound(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 1.2F, 0.5F);
      }
      if (t == RIP_LEN) {
         fr.heralisz.mammoth.dungeon.HornAltarBlockEntityServeur alt = m != null ? m.altar() : null;
         if (alt != null) alt.startHeat(level, socket);
         else if (m != null) m.wake();
         this.discard();
      }
   }

   /** Mort : il tombe à genoux et se consume en cendres depuis les jambes (plus de bruit de verre, plus de disparition sèche). */
   @Override
   protected void tickDeath() {
      ++this.deathTime;
      if (this.level() instanceof ServerLevel level) {
         int t = this.deathTime;
         float s = guardian() ? 2F : 1F;
         if (t == 1 && spearAt != null) takeSpearBack(level);
         if (t >= DEATH_BURN_FROM && t <= DEATH_BURN_TO) {
            int b = (t - DEATH_BURN_FROM) * 100 / (DEATH_BURN_TO - DEATH_BURN_FROM);
            this.entityData.set(BURN, b);
            double y = this.getY() + 1.6 * s * b / 100.0;
            level.sendParticles(Particles.CINDER.get(), getX(), y, getZ(), (int) (4 * s), 0.4 * s, 0.2, 0.4 * s, 0.03);
            level.sendParticles(Particles.MIST.get(), getX(), y + 0.2, getZ(), (int) (2 * s), 0.3 * s, 0.1, 0.3 * s, 0.02);
            if (t % 14 == 0) playSound(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 0.6F * s, 0.6F);
         }
         if (t >= DEATH) this.remove(RemovalReason.KILLED);
      }
   }

   @Override
   protected void dropCustomDeathLoot(DamageSource s, int looting, boolean hit) {}

   @Override
   protected boolean shouldDropLoot() {
      return false;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource s) {
      return Sounds.BONE_HIT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return null;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState st) {
      this.playSound(Sounds.STEP.get(), 0.25F, 1.8F + random.nextFloat() * 0.2F);
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (master != null) tag.putUUID("Master", master);
      tag.putBoolean("Guardian", guardian());
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.hasUUID("Master")) master = tag.getUUID("Master");
      if (tag.getBoolean("Guardian")) {
         this.entityData.set(GUARDIAN, true);
         this.refreshDimensions();
         setState(S_FREE);
      }
   }

   /** Tous les revenants liés à ce maître. */
   public static List<DraugrServeur> of(ServerLevel level, Mammoth m, double r) {
      return level.getEntitiesOfClass(DraugrServeur.class, m.getBoundingBox().inflate(r), d -> m.getUUID().equals(d.master));
   }
}
