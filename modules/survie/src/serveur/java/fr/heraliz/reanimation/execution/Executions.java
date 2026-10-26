package fr.heraliz.reanimation.execution;

import fr.heraliz.reanimation.ReanimationConfig;
import fr.heraliz.reanimation.ReanimationMod;
import fr.heraliz.reanimation.core.DownedManagerServeur;
import fr.heraliz.reanimation.core.DownedState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

public final class Executions {
   public static final float REACH = 0.75F;
   public static final float LANDING_FWD = -0.3F;
   public static final float HEAD_SIDE = 0.585F;
   public static final float KICK_FWD = 0.32F;
   public static final float KICK_SIDE = 0.7F;
   private static final Map<UUID, Executions.Execution> BY_ACTOR = new HashMap<>();
   private static final List<Executions.Crack> CRACKS = new ArrayList<>();
   private static int crackIds;
   /**
    * Type de degats du « coup d'essai » ({@link #canHurt}) : comme un coup du bourreau, mais sans recul, sans usure
    * d'armure ni temps d'invulnerabilite, et SANS {@code bypasses_invulnerability} (les protections qui laissent passer
    * ce tag, comme la zone sure du casino, refusent donc l'essai).
    */
   public static final ResourceKey<DamageType> CHECK = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("reanimation", "execution_check"));
   /** Degats du coup d'essai (rendus aussitot). */
   private static final float CHECK_DAMAGE = 0.05F;
   // « probing » (coup d'essai en cours) : champ commun ExecutionsCommun.probing, lu aussi par les règles communes

   private Executions() {
   }

   @Nullable
   public static Executions.Execution of(Entity actor) {
      return !actor.level().isClientSide && !BY_ACTOR.isEmpty() ? BY_ACTOR.get(actor.getUUID()) : null;
   }

   @Nullable
   public static Executions.Execution on(Entity victim) {
      if (!victim.level().isClientSide && !BY_ACTOR.isEmpty()) {
         for (Executions.Execution x : BY_ACTOR.values()) {
            if (x.victim == victim && !x.struck) {
               return x;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static int flags(Entity e) {
      return of(e) != null ? 128 : 0;
   }

   public static void request(LivingEntity actor, LivingEntity victim) {
      if (!(Boolean)ReanimationConfig.EXECUTIONS_ENABLED.get()) {
         DownedManagerServeur.tell(actor, Component.translatable("message.reanimation.execution_disabled"));
      } else {
         ExecutionType type = forWeapon(actor.getMainHandItem());
         if (type != null && type.knife() && actor instanceof Player p) {
            type = ExecutionUnlocks.knifeExecution(p, type);
         }

         if (type == null) {
            type = actor instanceof Player p ? ExecutionUnlocks.selected(p) : ExecutionType.COUP_DE_GRACE;
         }

         if (type == null) {
            DownedManagerServeur.tell(actor, Component.translatable("message.reanimation.no_execution"));
         } else {
            start(actor, victim, type);
         }
      }
   }

   public static boolean start(LivingEntity actor, LivingEntity victim, ExecutionType type) {
      DownedState s = DownedManagerServeur.get(victim);
      if (s == null || s.dying || s.executor != null || actor == victim) {
         return false;
      }

      if (DownedManagerServeur.isDowned(actor) || DownedManagerServeur.actingOn(actor) != null || of(actor) != null) {
         return false;
      }

      if (s.reviver != null) {
         DownedManagerServeur.tell(actor, Component.translatable("message.reanimation.being_revived", new Object[]{victim.getDisplayName()}));
         return false;
      }

      if (s.searcher != null) {
         DownedManagerServeur.tell(actor, Component.translatable("message.reanimation.already_searched", new Object[]{victim.getDisplayName()}));
         return false;
      }

      double range = (Double)ReanimationConfig.EXECUTION_RANGE.get();
      // HeraliZ : vue dégagée exigée (avant : un joueur à terre s'achevait à travers un mur)
      if (actor.level() == victim.level() && !(actor.distanceToSqr(victim) > range * range)
         && fr.heraliz.reanimation.core.VueDegagee.voit(actor, victim)) {
         if (!canHurt(actor, victim)) {
            refuse(actor, victim);
            return false;
         }

         if (type.headroom > 0 && !open(victim, type.headroom)) {
            DownedManagerServeur.tell(
               actor, Component.translatable("message.reanimation.execution_no_room", new Object[]{type.displayName(), ExecutionType.COUP_DE_GRACE.displayName()})
            );
            type = ExecutionType.COUP_DE_GRACE;
         }

         Vec3 head = Vec3.directionFromRotation(0.0F, victim.yBodyRot);
         Vec3 right = new Vec3(-head.z, 0.0, head.x);
         Vec3 toActor = actor.position().subtract(victim.position());
         Vec3 dir = toActor.x * right.x + toActor.z * right.z >= 0.0 ? right : right.scale(-1.0);
         if (type.headOnPlusSide && head.x * -dir.z + head.z * dir.x < 0.0) {
            dir = dir.scale(-1.0);
         }

         float yaw = (float)Math.toDegrees(Math.atan2(-dir.x, dir.z));
         int headSide = head.x * -dir.z + head.z * dir.x >= 0.0 ? 1 : -1;
         Executions.Execution x = new Executions.Execution(actor, victim, type, victim.position(), yaw, headSide);
         place(actor, x.at(type.reach, 0.0, 0.0), yaw + 180.0F, 40.0F);
         actor.stopUsingItem();
         actor.setSprinting(false);
         if (actor instanceof ServerPlayer p) {
            p.closeContainer();
            p.setForcedPose((Pose)null);
         }

         if (actor instanceof Mob mob) {
            mob.getNavigation().stop();
         }

         s.executor = actor;
         DownedManagerServeur.holdStill(victim);
         BY_ACTOR.put(actor.getUUID(), x);
         play(x.origin, x, SoundEvents.ARMOR_EQUIP_CHAIN, 1.0F, 0.7F);
         DownedManagerServeur.sync(actor, 4);
         DownedManagerServeur.sync(victim, 4);
         ReanimationMod.LOGGER.info("{} executes {} ({})", new Object[]{actor.getName().getString(), victim.getName().getString(), type.id});
         return true;
      } else {
         return false;
      }
   }

   public static void tick() {
      tickCracks();
      if (!BY_ACTOR.isEmpty()) {
         for (Executions.Execution x : new ArrayList<>(BY_ACTOR.values())) {
            LivingEntity a = x.actor;
            if (!a.isRemoved() && a.isAlive() && !DownedManagerServeur.isDowned(a)) {
               if (!x.struck) {
                  DownedState s = DownedManagerServeur.get(x.victim);
                  if (s == null
                     || s.executor != a
                     || x.victim.isRemoved()
                     || !x.victim.isAlive()
                     || a.level() != x.victim.level()
                     || a.distanceToSqr(x.victim) > 25.0) {
                     stop(x, true);
                     continue;
                  }
               }

               if (a instanceof Mob mob) {
                  mob.getNavigation().stop();
               }

               a.setDeltaMovement(0.0, a.getDeltaMovement().y, 0.0);
               x.ticks++;
               if (x.type == ExecutionType.COUP_DE_GRACE) {
                  cues(x);
                  if (x.ticks == 64) {
                     slam(x);
                  }
               } else if (x.script != null && a.level() instanceof ServerLevel level) {
                  int from = (int)Math.floor(Timeline.old(x.type, x.ticks - 1));
                  int to = (int)Math.floor(Timeline.old(x.type, x.ticks));

                  for (int t = from + 1; t <= to; t++) {
                     x.script.tick(x, level, t);
                  }
               }

               if (!x.struck && x.ticks >= 96) {
                  strike(x);
               }

               if (x.ticks >= 118) {
                  stop(x, false);
               }
            } else {
               stop(x, !x.struck);
            }
         }
      }
   }

   private static void cues(Executions.Execution x) {
      int t = x.ticks;
      Vec3 body = x.at(0.4, 0.0, t < 28 ? 1.0 : 1.9);
      if (t == 11) {
         play(x.at(0.1, 0.0, 0.4), x, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9F, 0.6F);
      }

      if (t == 12 || t == 20 || t == 34 || t == 46) {
         play(body, x, SoundEvents.ARMOR_EQUIP_LEATHER, 0.9F, 0.6F);
      }

      if (t == 30 || t == 42 || t == 52) {
         play(body, x, SoundEvents.PLAYER_HURT, 0.9F, t == 30 ? 0.9F : (t == 42 ? 0.75F : 0.6F));
      }

      if (t == 26 || t == 38 || t == 48 || t == 54) {
         float k = (t - 26) / 28.0F;
         play(body, x, SoundEvents.WARDEN_HEARTBEAT, 0.8F + 0.7F * k, 0.7F + 0.3F * k);
      }

      if (t == 58) {
         play(body, x, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
      }

      if (t == 74 || t == 81) {
         Vec3 feet = x.at(0.55, 0.35, 0.0);
         BlockState ground = x.victim.level().getBlockState(BlockPos.containing(feet.x, feet.y - 0.2, feet.z));
         play(feet, x, ground.getSoundType().getStepSound(), 0.9F, 0.85F);
      }

      if (t == 96 - 4) {
         play(x.at(0.32F, 0.7F, 0.3), x, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.75F);
      }

      if (t == 96 + 6) {
         play(x.at(-0.3F, 0.0, 0.4), x, SoundEvents.SOUL_ESCAPE, 1.0F, 0.7F);
      }
   }

   private static void slam(Executions.Execution x) {
      if (x.victim.level() instanceof ServerLevel level) {
         Vec3 hit = x.at(-0.3F, 0.0, 0.15);
         BlockPos below = BlockPos.containing(hit.x, hit.y - 0.2, hit.z);
         BlockState ground = level.getBlockState(below);
         if (ground.isAir()) {
            ground = Blocks.DIRT.defaultBlockState();
         }

         BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
         level.sendParticles(debris, hit.x, hit.y + 0.1, hit.z, 110, 0.7, 0.15, 0.7, 0.35);
         level.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y + 0.3, hit.z, 6, 0.6, 0.15, 0.6, 0.0);
         level.sendParticles(ParticleTypes.POOF, hit.x, hit.y + 0.1, hit.z, 35, 0.8, 0.1, 0.8, 0.08);
         level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, hit.x, hit.y + 0.1, hit.z, 7, 0.7, 0.05, 0.7, 0.01);
         level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y + 0.4, hit.z, 25, 0.4, 0.3, 0.4, 0.5);
         play(hit, x, SoundEvents.GENERIC_EXPLODE, 0.8F, 1.25F);
         play(hit, x, SoundEvents.ANVIL_LAND, 0.9F, 0.5F);
         play(hit, x, SoundEvents.WARDEN_ATTACK_IMPACT, 1.0F, 0.7F);
         play(hit, x, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.7F, 0.6F);
         play(hit, x, SoundEvents.PLAYER_BIG_FALL, 1.0F, 0.7F);
         double[][] spots = new double[][]{{-0.3, 0.0}, {-0.3, 0.9}, {-0.3, -0.9}, {-1.1, 0.0}, {0.4, 0.3}, {-1.0, 0.8}, {-0.9, -0.9}};

         for (double[] spot : spots) {
            Vec3 p = x.at(spot[0], spot[1], 0.0);
            BlockPos pos = BlockPos.containing(p.x, p.y - 0.2, p.z);
            if (!level.getBlockState(pos).isAir()) {
               int id = -1000 - (crackIds++ & 65535);
               level.destroyBlockProgress(id, pos, 8);
               CRACKS.add(new Executions.Crack(level, pos, id, new int[]{0}));
            }
         }
      }
   }

   private static boolean open(LivingEntity body, int height) {
      BlockPos base = body.blockPosition();

      for (int dy = 2; dy <= height; dy++) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               if (body.level().getBlockState(base.offset(dx, dy, dz)).blocksMotion()) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   public static void cracks(ServerLevel level, Vec3 center, double radius) {
      int r = (int)Math.ceil(radius);
      BlockPos base = BlockPos.containing(center.x, center.y - 0.2, center.z);

      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (!(d > radius)) {
               BlockPos pos = base.offset(dx, 0, dz);
               if (level.getBlockState(pos).isAir()) {
                  pos = pos.below();
                  if (level.getBlockState(pos).isAir()) {
                     continue;
                  }
               }

               int stage = 9 - (int)Math.round(5.0 * d / Math.max(radius, 0.1));
               int id = -1000 - (crackIds++ & 65535);
               level.destroyBlockProgress(id, pos, stage);
               CRACKS.add(new Executions.Crack(level, pos, id, new int[]{(8 - stage) * 15}));
            }
         }
      }
   }

   private static void tickCracks() {
      Iterator<Executions.Crack> it = CRACKS.iterator();

      while (it.hasNext()) {
         Executions.Crack c = it.next();
         int age = ++c.age[0];
         if (age % 15 == 0) {
            int stage = 8 - age / 15;
            c.level.destroyBlockProgress(c.id, c.pos, stage >= 0 ? stage : -1);
            if (stage < 0) {
               it.remove();
            }
         }
      }
   }

   private static void strike(Executions.Execution x) {
      // le PvP a pu etre coupe pendant l'animation (ou la victime est entree dans une zone protegee) : on reverifie
      if (!canHurt(x.actor, x.victim)) {
         refuse(x.actor, x.victim);
         stop(x, false);
         return;
      }

      x.struck = true;
      LivingEntity v = x.victim;
      if (x.type == ExecutionType.COUP_DE_GRACE && v.level() instanceof ServerLevel level) {
         Vec3 head = x.at(-0.3F, 0.585F, 0.2);
         level.sendParticles(
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), head.x, head.y, head.z, 40, 0.2, 0.15, 0.2, 0.3
         );
         level.sendParticles(
            new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.02F), 1.4F), head.x, head.y + 0.1, head.z, 30, 0.3, 0.2, 0.3, 0.0
         );
         level.sendParticles(ParticleTypes.CRIT, head.x, head.y + 0.2, head.z, 25, 0.25, 0.2, 0.25, 0.6);
         level.sendParticles(ParticleTypes.SWEEP_ATTACK, head.x, head.y + 0.3, head.z, 1, 0.0, 0.0, 0.0, 0.0);
         burst(level, ParticleTypes.SOUL, x.at(-0.3F, 0.0, 0.3), 30, 0.4, 0.07);
         burst(level, ParticleTypes.SCULK_SOUL, x.at(-0.3F, 0.0, 0.3), 12, 0.3, 0.05);
         play(head, x, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.7F);
         play(head, x, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.9F);
         play(head, x, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.6F, 1.5F);
         play(head, x, SoundEvents.SKELETON_HURT, 0.9F, 0.55F);
         play(head, x, SoundEvents.TRIDENT_THUNDER, 0.45F, 1.4F);
      } else if (x.script != null && v.level() instanceof ServerLevel level) {
         x.script.strike(x, level);
      }

      DownedManagerServeur.sync(v, 5);
      DownedManagerServeur.sync(x.actor, 5);
      DownedManagerServeur.execute(v, x.actor);
   }

   private static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
      level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread * 1.2, spread, speed);
   }

   private static void stop(Executions.Execution x, boolean interrupted) {
      if (BY_ACTOR.get(x.actor.getUUID()) == x) {
         BY_ACTOR.remove(x.actor.getUUID());
         if (!x.struck) {
            DownedManagerServeur.releaseFromExecution(x.victim, x.actor);
         }

         if (interrupted) {
            DownedManagerServeur.tell(x.actor, Component.translatable("message.reanimation.execution_interrupted"));
         }

         if (x.struck && !x.actor.isRemoved() && x.actor.isAlive()) {
            place(x.actor, x.at(x.type.endFwd, x.type.endSide, 0.0), x.yaw + x.type.endYaw, 45.0F);
         }

         if (!x.actor.isRemoved()) {
            DownedManagerServeur.sync(x.actor, 0);
         }
      }
   }

   @Nullable
   public static ExecutionType forWeapon(ItemStack stack) {
      return ExecutionsCommun.forWeapon(stack);
   }

   /** Vrai pendant un coup d'essai ({@link #canHurt}). */
   public static boolean probing() {
      return ExecutionsCommun.probing();
   }

   /**
    * Le bourreau pourrait-il vraiment blesser la victime ici, avec un coup normal ? Verifie, dans l'ordre :
    * <ul>
    *    <li>le PvP du serveur et les equipes ({@code canHarmPlayer}) ;</li>
    *    <li>{@link AttackEntityEvent} (mods qui refusent le coup de poing, annulation respectee) ;</li>
    *    <li>un vrai coup d'essai par le circuit normal des degats ({@code hurt}) : {@code LivingAttackEvent},
    *    {@code LivingHurtEvent}, PvP du serveur et, sous Mohist, l'evenement Bukkit (zones WorldGuard sans PvP). L'essai est
    *    accepte seulement si la vie (ou l'absorption) a vraiment baisse ; elle est rendue tout de suite, ainsi que la
    *    vitesse et l'invulnerabilite.</li>
    * </ul>
    * Un bourreau qui n'est pas un joueur (bot) passe aussi par le coup d'essai.
    */
   public static boolean canHurt(LivingEntity actor, LivingEntity victim) {
      if (!(victim.level() instanceof ServerLevel level) || actor == victim) {
         return true;
      }

      boolean wasProbing = ExecutionsCommun.probing();
      ExecutionsCommun.probing(true);

      try {
         if (actor instanceof Player attacker) {
            if (victim instanceof Player target && !attacker.canHarmPlayer(target)) {
               return false;
            }

            if (MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(attacker, victim))) {
               return false;
            }
         }

         Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(CHECK).orElse(null);
         DamageSource source = type != null
            ? new DamageSource(type, actor)
            : (actor instanceof Player attacker ? level.damageSources().playerAttack(attacker) : level.damageSources().mobAttack(actor));
         float health = victim.getHealth();
         float absorption = victim.getAbsorptionAmount();
         Vec3 motion = victim.getDeltaMovement();
         int invulnerable = victim.invulnerableTime;
         boolean hit = victim.hurt(source, CHECK_DAMAGE);
         boolean took = hit && victim.getHealth() + victim.getAbsorptionAmount() < health + absorption;
         if (victim.isAlive()) {
            victim.setAbsorptionAmount(absorption);
            victim.setHealth(health);
         }

         victim.setDeltaMovement(motion);
         victim.invulnerableTime = invulnerable;
         return took;
      } catch (RuntimeException e) {
         ReanimationMod.LOGGER.warn("Execution check {} -> {} failed, execution refused", actor.getName().getString(), victim.getName().getString(), e);
         return false;
      } finally {
         ExecutionsCommun.probing(wasProbing);
      }
   }

   private static void refuse(LivingEntity actor, LivingEntity victim) {
      DownedManagerServeur.tell(actor, Component.translatable("message.reanimation.execution_protected"));
      ReanimationMod.LOGGER.info("{} cannot execute {} here (PvP off or protected area)", actor.getName().getString(), victim.getName().getString());
   }

   public static void cancelBy(Entity actor) {
      Executions.Execution x = of(actor);
      if (x != null && !x.struck && x.ticks < 58) {
         stop(x, true);
      }
   }

   public static void onDamaged(LivingEntity e) {
      Executions.Execution x = of(e);
      if (x != null && !x.struck && x.ticks < 58) {
         stop(x, true);
      }
   }

   public static void clearAll() {
      BY_ACTOR.clear();
      CRACKS.clear();
   }

   private static void play(Vec3 at, Executions.Execution x, SoundEvent sound, float volume, float pitch) {
      x.victim.level().playSound((Player)null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
   }

   private static void place(LivingEntity actor, Vec3 spot, float yaw, float pitch) {
      Vec3 target = new Vec3(spot.x, actor.getY(), spot.z);
      AABB box = actor.getBoundingBox().move(target.subtract(actor.position()));
      boolean room = Math.abs(spot.y - actor.getY()) < 0.6
         && actor.level().noCollision(actor, box)
         && !actor.level().noCollision(actor, box.move(0.0, -0.2, 0.0))
         && fr.heraliz.reanimation.core.VueDegagee.chemin(actor, target);
      if (!room) {
         target = actor.position();
      }

      if (actor instanceof ServerPlayer p) {
         p.connection.teleport(target.x, target.y, target.z, yaw, pitch);
      } else {
         actor.moveTo(target.x, target.y, target.z, yaw, pitch);
         actor.setYHeadRot(yaw);
         actor.setYBodyRot(yaw);
      }

      actor.setDeltaMovement(Vec3.ZERO);
   }

   private record Crack(ServerLevel level, BlockPos pos, int id, int[] age) {
   }

   public static final class Execution {
      public final LivingEntity actor;
      public final LivingEntity victim;
      public final ExecutionType type;
      public final Vec3 origin;
      public final float yaw;
      public final Vec3 forward;
      public final Vec3 side;
      public final int headSide;
      @Nullable
      public final ExecutionScript script;
      public int ticks;
      public boolean struck;

      Execution(LivingEntity actor, LivingEntity victim, ExecutionType type, Vec3 origin, float yaw, int headSide) {
         this.actor = actor;
         this.victim = victim;
         this.type = type;
         this.origin = origin;
         this.yaw = yaw;
         this.headSide = headSide;
         this.script = ExecutionScript.of(type);
         this.forward = Vec3.directionFromRotation(0.0F, yaw);
         this.side = new Vec3(-this.forward.z, 0.0, this.forward.x);
      }

      public Vec3 at(double fwd, double side, double up) {
         return this.origin.add(this.forward.scale(fwd)).add(this.side.scale(side * this.headSide)).add(0.0, up, 0.0);
      }
   }
}
