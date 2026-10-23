package fr.heraliz.reanimation.core;

import fr.heraliz.reanimation.ReanimationConfig;
import fr.heraliz.reanimation.ReanimationMod;
import fr.heraliz.reanimation.compat.CuriosFouille;
import fr.heraliz.reanimation.entity.BotEntity;
import fr.heraliz.reanimation.entity.BotEntityServeur;
import fr.heraliz.reanimation.execution.Executions;
import fr.heraliz.reanimation.menu.SearchMenu;
import fr.heraliz.reanimation.network.Network;
import fr.heraliz.reanimation.network.SyncPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

public final class DownedManagerServeur {
   private static final Map<UUID, DownedState> STATES = new HashMap<>();
   private static final UUID CRAWL_ID = UUID.fromString("6b1f4a36-2a0b-4f8e-9d7e-6a0e2c1b7d01");
   private static final UUID FREEZE_ID = UUID.fromString("6b1f4a36-2a0b-4f8e-9d7e-6a0e2c1b7d02");
   // mêmes clés que la face commune DownedManager (déclarées là-bas, lues par d'autres mods)
   public static final ResourceKey<DamageType> BLEED_OUT = DownedManager.BLEED_OUT;
   public static final ResourceKey<DamageType> GIVE_UP = DownedManager.GIVE_UP;
   public static final ResourceKey<DamageType> DISCONNECT = DownedManager.DISCONNECT;
   public static final ResourceKey<DamageType> EXECUTION = DownedManager.EXECUTION;
   private static final String SAVE_KEY = "reanimation";
   private static final double KNEEL_DISTANCE = 0.7;

   private DownedManagerServeur() {
   }

   /**
    * Partie serveur (docs/METHODE_CLIENT_SERVEUR.md, piège n°17) : branche la face commune {@link DownedManager} sur les
    * méthodes d'origine ci-dessous. Appelé par PartieServeur.init.
    */
   public static void installer() {
      DownedManager.moteur = new DownedManager.Moteur() {
         @Override
         public DownedState get(Entity e) {
            return DownedManagerServeur.get(e);
         }

         @Override
         public int flags(Entity e) {
            return DownedManagerServeur.flags(e);
         }

         @Override
         public void kill(DownedState s, ResourceKey<DamageType> type) {
            DownedManagerServeur.kill(s, type);
         }

         @Override
         public boolean reviveNow(LivingEntity e) {
            return DownedManagerServeur.reviveNow(e);
         }

         @Override
         public void giveUp(LivingEntity e) {
            DownedManagerServeur.giveUp(e);
         }

         @Override
         public void startSearch(LivingEntity actor, LivingEntity target) {
            DownedManagerServeur.startSearch(actor, target);
         }

         @Override
         public void startRevive(LivingEntity actor, LivingEntity target) {
            DownedManagerServeur.startRevive(actor, target);
         }

         @Override
         public void searchClosed(LivingEntity target, Player player) {
            DownedManagerServeur.searchClosed(target, player);
         }

         @Override
         public boolean isSearching(Player player, LivingEntity target) {
            return DownedManagerServeur.isSearching(player, target);
         }
      };
   }

   public static boolean canBeDowned(LivingEntity e) {
      return e instanceof Player || e instanceof BotEntity;
   }

   @Nullable
   public static DownedState get(Entity e) {
      return e.level().isClientSide ? null : STATES.get(e.getUUID());
   }

   public static boolean isDowned(Entity e) {
      return get(e) != null;
   }

   @Nullable
   public static DownedState actingOn(Entity actor) {
      if (!actor.level().isClientSide && !STATES.isEmpty()) {
         for (DownedState s : STATES.values()) {
            if (s.searcher == actor || s.reviver == actor) {
               return s;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static int flags(Entity e) {
      int f = 0;
      DownedState s = get(e);
      if (s != null) {
         f |= 1;
         if (s.searcher != null) {
            f |= 2;
         }

         if (s.reviver != null || s.executor != null || s.searcher != null && (Boolean)ReanimationConfig.SEARCH_FREEZES.get()) {
            f |= 64;
         }

         if (s.executor != null) {
            f |= 288;
         }

         if (s.reviver != null) {
            f |= 4;
            if ((Boolean)ReanimationConfig.PAUSE_BLEED_WHILE_REVIVING.get()) {
               f |= 32;
            }
         }
      }

      DownedState act = actingOn(e);
      if (act != null) {
         f |= act.reviver == e ? 16 : 8;
      }

      return f | Executions.flags(e);
   }

   public static DownedState down(LivingEntity e, @Nullable DamageSource source) {
      int max = (Integer)ReanimationConfig.BLEED_OUT_SECONDS.get() * 20;
      DownedState s = start(e, max, max);
      e.setHealth((float)Math.min(e.getMaxHealth(), (Double)ReanimationConfig.DOWNED_HEALTH.get()));
      Entity attacker = source == null ? null : source.getEntity();
      if (attacker instanceof LivingEntity && attacker != e) {
         s.attacker = attacker.getUUID();
      }

      e.level().playSound((Player)null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
      ReanimationMod.LOGGER.info("{} is down ({})", e.getName().getString(), source == null ? "command" : source.getMsgId());
      return s;
   }

   private static DownedState start(LivingEntity e, int bleedTicks, int bleedMax) {
      DownedState old = STATES.get(e.getUUID());
      if (old != null) {
         return old;
      }

      endAction(e, true);
      DownedState s = new DownedState(e, bleedTicks, bleedMax);
      STATES.put(e.getUUID(), s);
      e.stopUsingItem();
      e.stopRiding();
      e.ejectPassengers();
      e.setSprinting(false);
      e.setShiftKeyDown(false);
      if (e instanceof ServerPlayer p) {
         p.closeContainer();
         p.stopFallFlying();
         if (p.getAbilities().flying) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
         }

         p.setForcedPose(Pose.SWIMMING);
      } else {
         e.setPose(Pose.SWIMMING);
      }

      if (e instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget((LivingEntity)null);
      }

      speed(e, CRAWL_ID, "Crawling", (Double)ReanimationConfig.CRAWL_SPEED.get() - 1.0);
      if ((Boolean)ReanimationConfig.MOBS_IGNORE_DOWNED.get() && e.level() instanceof ServerLevel level) {
         for (Mob mob : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(48.0))) {
            if (mob.getTarget() == e) {
               mob.setTarget((LivingEntity)null);
            }
         }
      }

      retirerSaignement(e);
      sync(e, 1);
      return s;
   }

   /** Effet « bleeding » de geckolib222 : jamais sur un joueur à terre (demande du propriétaire du 23/10). */
   public static final net.minecraft.resources.ResourceLocation SAIGNEMENT = new net.minecraft.resources.ResourceLocation("geckolib", "bleeding");

   public static void retirerSaignement(LivingEntity e) {
      net.minecraft.world.effect.MobEffect m = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(SAIGNEMENT);
      if (m != null && e.hasEffect(m)) {
         e.removeEffect(m);
      }
   }

   public static void tick() {
      if (!STATES.isEmpty()) {
         for (DownedState s : new ArrayList<>(STATES.values())) {
            if (STATES.get(s.entity.getUUID()) == s) {
               LivingEntity e = s.entity;
               if (!e.isRemoved() && e.isAlive()) {
                  tick(e, s);
               } else {
                  forget(s);
               }
            }
         }
      }
   }

   private static void tick(LivingEntity e, DownedState s) {
      s.ticks++;
      Pose pose = Pose.SWIMMING;
      if (e instanceof ServerPlayer p) {
         if (p.getForcedPose() != pose) {
            p.setForcedPose(pose);
         }

         if (p.containerMenu != p.inventoryMenu) {
            p.closeContainer();
         }

         if (p.getAbilities().flying) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
         }

         if (p.isFallFlying()) {
            p.stopFallFlying();
         }
      } else if (e.getPose() != pose) {
         e.setPose(pose);
      }

      if (e.isUsingItem()) {
         e.stopUsingItem();
      }

      if (e.isSprinting()) {
         e.setSprinting(false);
      }

      if (e.isPassenger()) {
         e.stopRiding();
      }

      if (s.searcher != null && !searchValid(s)) {
         endSearch(s);
      }

      if (s.reviver != null) {
         if (!reviveValid(s)) {
            cancelRevive(s);
         } else {
            s.reviveTicks++;
            if (s.reviver instanceof Mob mob) {
               mob.getLookControl().setLookAt(e, 30.0F, 30.0F);
            }

            if (s.reviveTicks >= s.reviveMax) {
               completeRevive(s);
               return;
            }
         }
      }

      boolean frozen = s.reviver != null || s.executor != null || s.searcher != null && (Boolean)ReanimationConfig.SEARCH_FREEZES.get();
      speed(e, FREEZE_ID, "Held still", frozen ? -1.0 : 0.0);
      speed(e, CRAWL_ID, "Crawling", (Double)ReanimationConfig.CRAWL_SPEED.get() - 1.0);
      if (frozen && e instanceof Mob mob) {
         mob.getNavigation().stop();
      }

      boolean paused = s.executor != null || s.reviver != null && (Boolean)ReanimationConfig.PAUSE_BLEED_WHILE_REVIVING.get();
      if (!paused && --s.bleedTicks <= 0) {
         kill(s, BLEED_OUT);
      } else if (s.ticks % 100 == 0) {
         sync(e, 0);
      }
   }

   public static void giveUp(LivingEntity e) {
      DownedState s = get(e);
      if (s != null && s.reviver == null && s.executor == null && s.ticks >= 20) {
         kill(s, GIVE_UP);
      }
   }

   public static void kill(DownedState s, ResourceKey<DamageType> type) {
      kill(s, type, (LivingEntity)null);
   }

   public static void execute(LivingEntity victim, LivingEntity executioner) {
      DownedState s = get(victim);
      if (s != null) {
         kill(s, EXECUTION, executioner);
      }
   }

   public static void releaseFromExecution(LivingEntity victim, LivingEntity executioner) {
      DownedState s = get(victim);
      if (s != null && s.executor == executioner) {
         s.executor = null;
         sync(victim, 0);
      }
   }

   private static void kill(DownedState s, ResourceKey<DamageType> type, @Nullable LivingEntity killer) {
      LivingEntity e = s.entity;
      if (!s.dying) {
         s.dying = true;
         endSearch(s);
         if (s.reviver != null) {
            cancelRevive(s);
         }

         if (s.attacker != null && e.level() instanceof ServerLevel level) {
            Entity credit = level.getEntity(s.attacker);
            if (credit instanceof Player p) {
               e.setLastHurtByPlayer(p);
            } else if (credit instanceof LivingEntity living && living.isAlive()) {
               e.setLastHurtByMob(living);
            }
         }

         DamageSource source = new DamageSource(e.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), killer);
         e.invulnerableTime = 0;
         e.hurt(source, Float.MAX_VALUE);
         if (e.isAlive() && e.getHealth() > 0.0F) {
            e.setHealth(0.0F);
            e.die(source);
         }

         if (STATES.get(e.getUUID()) == s) {
            s.dying = false;
            if (e.isAlive() && !(e.getHealth() <= 0.0F)) {
               stand(s);
            } else {
               forget(s);
            }
         }
      }
   }

   public static void onDeath(LivingEntity e) {
      endAction(e, true);
      DownedState s = STATES.get(e.getUUID());
      if (s != null) {
         forget(s);
      }
   }

   private static void forget(DownedState s) {
      endSearch(s);
      if (s.reviver != null) {
         cancelRevive(s);
      }

      STATES.remove(s.entity.getUUID());
      clearBody(s.entity);
      sync(s.entity, 0);
   }

   private static void stand(DownedState s) {
      endSearch(s);
      LivingEntity reviver = s.reviver;
      s.reviver = null;
      STATES.remove(s.entity.getUUID());
      clearBody(s.entity);
      if (reviver != null) {
         clearBody(reviver);
         sync(reviver, 0);
      }
   }

   private static void clearBody(LivingEntity e) {
      speed(e, CRAWL_ID, "Crawling", 0.0);
      speed(e, FREEZE_ID, "Held still", 0.0);
      if (e instanceof Player p) {
         if (p.getForcedPose() == Pose.SWIMMING || p.getForcedPose() == Pose.CROUCHING) {
            p.setForcedPose((Pose)null);
         }
      } else if (e.getPose() == Pose.SWIMMING || e.getPose() == Pose.CROUCHING) {
         e.setPose(Pose.STANDING);
      }
   }

   public static void startSearch(LivingEntity actor, LivingEntity target) {
      DownedState s = get(target);
      if (ReanimationMod.TRACE) {
         ReanimationMod.LOGGER
            .info(
               "[SEARCH] {} on {}: state {} dying {} actorDowned {} acting {} dist {}",
               new Object[]{
                  actor.getName().getString(),
                  target.getName().getString(),
                  s != null,
                  s != null && s.dying,
                  isDowned(actor),
                  actingOn(actor) != null,
                  actor.distanceTo(target)
               }
            );
      }

      if (s != null && !s.dying && !isDowned(actor) && actingOn(actor) == null) {
         if (!(Boolean)ReanimationConfig.ALLOW_SEARCH.get()) {
            tell(actor, Component.translatable("message.reanimation.search_disabled"));
         } else if (s.executor == null && Executions.of(actor) == null) {
            if (s.reviver != null) {
               tell(actor, Component.translatable("message.reanimation.being_revived", new Object[]{target.getDisplayName()}));
            } else if (s.searcher != null) {
               tell(actor, Component.translatable("message.reanimation.already_searched", new Object[]{target.getDisplayName()}));
            } else if (inRange(actor, target)) {
               s.searcher = actor;
               // Un joueur fouille de là où il est (à portée : inRange ci-dessus, puis searchValid tant que la fouille
               // reste ouverte) : il n'est plus téléporté à côté du corps (retour des propriétaires, 12/10). Les bots
               // gardent leur place à genoux à côté du corps (ils y vont d'eux-mêmes, BotGoals).
               if (!(actor instanceof ServerPlayer)) {
                  placeBeside(actor, target);
               }
               if (actor instanceof ServerPlayer p) {
                  Container inventory = inventoryOf(target);
                  if (inventory == null) {
                     s.searcher = null;
                     return;
                  }

                  // emplacements Curios de la victime (colonne a gauche de la fouille) ; aucun sans Curios
                  SearchMenu.Accessoires curios = CuriosFouille.de(target);
                  NetworkHooks.openScreen(
                     p,
                     new SimpleMenuProvider(
                        (id, playerInventory, player) -> new SearchMenu(id, playerInventory, inventory, target, curios),
                        Component.translatable("gui.reanimation.search", new Object[]{target.getDisplayName()})
                     ),
                     buf -> {
                        buf.writeVarInt(target.getId());
                        curios.ecrire(buf);
                     }
                  );
                  if (!(p.containerMenu instanceof SearchMenu menu) || menu.owner() != target) {
                     s.searcher = null;
                     return;
                  }

                  p.setForcedPose(Pose.CROUCHING);
               } else {
                  s.searchTicks = 140;
                  actor.setPose(Pose.CROUCHING);
               }

               if ((Boolean)ReanimationConfig.SEARCH_FREEZES.get()) {
                  holdStill(target);
               }

               target.level()
                  .playSound((Player)null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 0.8F);
               sync(actor, 0);
               sync(target, 0);
               return;
            }
         }
      }
   }

   @Nullable
   public static Container inventoryOf(LivingEntity e) {
      if (e instanceof Player p) {
         return p.getInventory();
      } else {
         return e instanceof BotEntityServeur bot ? bot.inventory() : null;
      }
   }

   public static void searchClosed(LivingEntity target, Player player) {
      DownedState s = get(target);
      if (s != null && s.searcher == player) {
         endSearch(s);
      }
   }

   public static boolean isSearching(Player player, LivingEntity target) {
      DownedState s = get(target);
      return s != null && s.searcher == player && searchValid(s);
   }

   private static boolean searchValid(DownedState s) {
      LivingEntity a = s.searcher;
      if (a != null && !a.isRemoved() && a.isAlive() && !isDowned(a) && a.level() == s.entity.level()) {
         double range = (Double)ReanimationConfig.INTERACT_RANGE.get() + 1.5;
         if (a.distanceToSqr(s.entity) > range * range) {
            return false;
         }

         // HeraliZ : plus de fouille à travers un mur (revérifié tous les 5 ticks)
         if (s.ticks % 5 == 0 && !VueDegagee.voit(a, s.entity)) {
            return false;
         }

         if (!(a instanceof ServerPlayer p)) {
            if (s.ticks % 30 == 0) {
               s.entity
                  .level()
                  .playSound(
                     (Player)null,
                     s.entity.getX(),
                     s.entity.getY(),
                     s.entity.getZ(),
                     s.ticks % 60 == 0 ? SoundEvents.BUNDLE_REMOVE_ONE : SoundEvents.BUNDLE_INSERT,
                     SoundSource.PLAYERS,
                     0.8F,
                     0.9F
                  );
            }

            return --s.searchTicks > 0;
         } else {
            return p.containerMenu instanceof SearchMenu menu && menu.owner() == s.entity;
         }
      } else {
         return false;
      }
   }

   private static void endSearch(DownedState s) {
      LivingEntity a = s.searcher;
      if (a != null) {
         s.searcher = null;
         clearBody(a);
         if (a instanceof ServerPlayer p && p.containerMenu instanceof SearchMenu menu && menu.owner() == s.entity) {
            p.closeContainer();
         }

         sync(a, 0);
         if (!s.entity.isRemoved()) {
            sync(s.entity, 0);
         }
      }
   }

   public static void startRevive(LivingEntity actor, LivingEntity target) {
      DownedState s = get(target);
      if (ReanimationMod.TRACE) {
         ReanimationMod.LOGGER
            .info(
               "[REVIVE] {} on {}: state {} dying {} actorDowned {} acting {} dist {}",
               new Object[]{
                  actor.getName().getString(),
                  target.getName().getString(),
                  s != null,
                  s != null && s.dying,
                  isDowned(actor),
                  actingOn(actor) != null,
                  actor.distanceTo(target)
               }
            );
      }

      if (s != null && !s.dying && !isDowned(actor) && actingOn(actor) == null && s.executor == null && Executions.of(actor) == null) {
         if (s.reviver != null) {
            tell(actor, Component.translatable("message.reanimation.being_revived", new Object[]{target.getDisplayName()}));
         } else if (s.searcher != null) {
            tell(actor, Component.translatable("message.reanimation.already_searched", new Object[]{target.getDisplayName()}));
         } else if (inRange(actor, target)) {
            placeBeside(actor, target);
            s.reviver = actor;
            s.reviveTicks = 0;
            s.reviveMax = (Integer)ReanimationConfig.REVIVE_SECONDS.get() * 20;
            s.reviverStart = actor.position();
            if (actor instanceof ServerPlayer p) {
               p.closeContainer();
               p.stopUsingItem();
               p.setForcedPose(Pose.CROUCHING);
            } else {
               actor.setPose(Pose.CROUCHING);
               if (actor instanceof Mob mob) {
                  mob.getNavigation().stop();
               }
            }

            holdStill(target);
            target.level()
               .playSound((Player)null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 0.7F);
            sync(actor, 0);
            sync(target, 0);
         }
      }
   }

   public static void cancelReviveBy(LivingEntity actor) {
      DownedState s = actingOn(actor);
      if (s != null && s.reviver == actor) {
         cancelRevive(s);
      }
   }

   private static boolean reviveValid(DownedState s) {
      LivingEntity a = s.reviver;
      if (a != null && !a.isRemoved() && a.isAlive() && !isDowned(a) && a.level() == s.entity.level()) {
         double range = (Double)ReanimationConfig.INTERACT_RANGE.get() + 1.0;
         return a.distanceToSqr(s.entity) > range * range ? false : a.position().distanceToSqr(s.reviverStart) < 2.25;
      } else {
         return false;
      }
   }

   private static void cancelRevive(DownedState s) {
      LivingEntity a = s.reviver;
      if (a != null) {
         if (ReanimationMod.TRACE) {
            ReanimationMod.LOGGER.info("[REVIVE] cancelled after {} ticks", s.reviveTicks);
         }

         s.reviver = null;
         s.reviveTicks = 0;
         clearBody(a);
         tell(a, Component.translatable("message.reanimation.revive_interrupted"));
         sync(a, 0);
         if (!s.entity.isRemoved()) {
            sync(s.entity, 3);
         }
      }
   }

   private static void completeRevive(DownedState s) {
      LivingEntity e = s.entity;
      LivingEntity a = s.reviver;
      stand(s);
      e.setHealth((float)Math.min(e.getMaxHealth(), (Double)ReanimationConfig.REVIVE_HEALTH.get()));
      int groggy = (Integer)ReanimationConfig.GROGGY_SECONDS.get() * 20;
      if (groggy > 0) {
         e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, groggy, 0, false, false, true));
         e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, groggy, 0, false, false, true));
      }

      e.invulnerableTime = 20;
      // 23/10 (demande du propriétaire) : plus de particules de totem ni de cœurs, plus de son de totem en se relevant
      // (effets de Minecraft, pas la DA du serveur)

      if (a != null) {
         tell(e, Component.translatable("message.reanimation.revived_by", new Object[]{a.getDisplayName()}));
         tell(a, Component.translatable("message.reanimation.you_revived", new Object[]{e.getDisplayName()}));
      }

      sync(e, 2);
      ReanimationMod.LOGGER.info("{} was revived by {}", e.getName().getString(), a == null ? "?" : a.getName().getString());
   }

   public static boolean reviveNow(LivingEntity e) {
      DownedState s = get(e);
      if (s == null) {
         return false;
      }

      stand(s);
      e.setHealth((float)Math.min(e.getMaxHealth(), (Double)ReanimationConfig.REVIVE_HEALTH.get()));
      sync(e, 2);
      return true;
   }

   public static void endAction(LivingEntity actor, boolean quiet) {
      Executions.cancelBy(actor);
      DownedState s = actingOn(actor);
      if (s != null) {
         if (s.searcher == actor) {
            endSearch(s);
         }

         if (s.reviver == actor) {
            cancelRevive(s);
         }
      }
   }

   public static void onDamaged(LivingEntity e) {
      Executions.onDamaged(e);
      DownedState s = get(e);
      if (s != null && s.reviver != null && !s.dying) {
         cancelRevive(s);
      }

      DownedState act = actingOn(e);
      if (act != null && act.reviver == e) {
         cancelRevive(act);
      }
   }

   public static void onLogout(ServerPlayer p) {
      endAction(p, true);
      DownedState s = get(p);
      if (s != null) {
         boolean shuttingDown = p.server == null || !p.server.isRunning();
         if (!shuttingDown && (Boolean)ReanimationConfig.DIE_ON_DISCONNECT.get()) {
            kill(s, DISCONNECT);
         } else {
            save(p, s);
            endSearch(s);
            if (s.reviver != null) {
               cancelRevive(s);
            }

            STATES.remove(p.getUUID());
         }
      }
   }

   public static void onSave(Player p) {
      DownedState s = get(p);
      if (s != null) {
         save(p, s);
      } else {
         p.getPersistentData().remove("reanimation");
      }
   }

   private static void save(Player p, DownedState s) {
      CompoundTag tag = new CompoundTag();
      tag.putInt("Bleed", Math.max(20, s.bleedTicks));
      tag.putInt("BleedMax", s.bleedMax);
      if (s.attacker != null) {
         tag.putUUID("Attacker", s.attacker);
      }

      p.getPersistentData().put("reanimation", tag);
   }

   public static void onLogin(ServerPlayer p) {
      CompoundTag data = p.getPersistentData();
      if (data.contains("reanimation") && p.isAlive()) {
         CompoundTag tag = data.getCompound("reanimation");
         DownedState s = start(p, tag.getInt("Bleed"), Math.max(tag.getInt("Bleed"), tag.getInt("BleedMax")));
         if (tag.hasUUID("Attacker")) {
            s.attacker = tag.getUUID("Attacker");
         }
      }

      data.remove("reanimation");
      sync(p, 0);
   }

   public static void clearAll() {
      STATES.clear();
      Executions.clearAll();
   }

   public static Vec3 kneelSpot(LivingEntity actor, LivingEntity body) {
      Vec3[] spots = kneelSpots(actor, body);
      return spots[0];
   }

   private static Vec3[] kneelSpots(LivingEntity actor, LivingEntity body) {
      double yaw = Math.toRadians(body.yBodyRot);
      Vec3 side = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw)).scale(0.7);
      Vec3 a = body.position().add(side);
      Vec3 b = body.position().subtract(side);
      return actor.position().distanceToSqr(a) <= actor.position().distanceToSqr(b) ? new Vec3[]{a, b} : new Vec3[]{b, a};
   }

   private static void placeBeside(LivingEntity actor, LivingEntity body) {
      if (!(Math.abs(actor.getY() - body.getY()) > 0.6)) {
         for (Vec3 spot : kneelSpots(actor, body)) {
            Vec3 target = new Vec3(spot.x, body.getY(), spot.z);
            AABB box = actor.getBoundingBox().move(target.subtract(actor.position()));
            // HeraliZ : jamais de l'autre côté d'un mur (VueDegagee.chemin)
            if (actor.level().noCollision(actor, box) && !actor.level().noCollision(actor, box.move(0.0, -0.2, 0.0))
               && VueDegagee.chemin(actor, target)) {
               Vec3 d = body.position().subtract(target);
               float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
               if (actor instanceof ServerPlayer p) {
                  p.connection.teleport(target.x, target.y, target.z, yaw, 40.0F);
               } else {
                  actor.moveTo(target.x, target.y, target.z, yaw, 30.0F);
                  actor.setYHeadRot(yaw);
                  actor.setYBodyRot(yaw);
               }

               actor.setDeltaMovement(Vec3.ZERO);
               return;
            }
         }
      }
   }

   private static boolean inRange(LivingEntity actor, LivingEntity target) {
      double range = (Double)ReanimationConfig.INTERACT_RANGE.get();
      return actor.level() == target.level() && actor.distanceToSqr(target) <= range * range && VueDegagee.voit(actor, target);
   }

   public static void tell(LivingEntity e, Component message) {
      if (e instanceof Player p) {
         p.displayClientMessage(message, true);
      }
   }

   public static void holdStill(LivingEntity body) {
      speed(body, FREEZE_ID, "Held still", -1.0);
      body.setDeltaMovement(0.0, body.getDeltaMovement().y, 0.0);
      if (body instanceof Mob mob) {
         mob.getNavigation().stop();
      }
   }

   private static void speed(LivingEntity e, UUID id, String name, double amount) {
      AttributeInstance attribute = e.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attribute != null) {
         AttributeModifier current = attribute.getModifier(id);
         if (current == null || current.getAmount() != amount) {
            if (current != null) {
               attribute.removeModifier(id);
            }

            if (amount != 0.0) {
               attribute.addTransientModifier(new AttributeModifier(id, name, amount, Operation.MULTIPLY_TOTAL));
            }
         }
      }
   }

   public static void sync(LivingEntity e, int event) {
      if (!e.level().isClientSide) {
         Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), packet(e, event));
      }
   }

   public static SyncPacket packet(LivingEntity e, int event) {
      int flags = flags(e);
      DownedState s = get(e);
      DownedState act = actingOn(e);
      Executions.Execution execution = Executions.of(e);
      if (execution == null && s != null && s.executor != null) {
         execution = Executions.of(s.executor);
      }

      if (execution != null) {
         LivingEntity partner = execution.actor == e ? execution.victim : execution.actor;
         return new SyncPacket(
               e.getId(),
               flags,
               partner.getId(),
               s != null ? s.bleedTicks : 0,
               s != null ? s.bleedMax : 0,
               execution.ticks,
               118,
               event,
               execution.type.ordinal()
            )
            .anchor(execution.origin.x, execution.origin.y, execution.origin.z, execution.yaw, execution.headSide);
      } else if (s != null) {
         LivingEntity partner = s.partner();
         return new SyncPacket(e.getId(), flags, partner == null ? -1 : partner.getId(), s.bleedTicks, s.bleedMax, s.reviveTicks, s.reviveMax, event, 0);
      } else {
         return act != null
            ? new SyncPacket(e.getId(), flags, act.entity.getId(), act.bleedTicks, act.bleedMax, act.reviveTicks, act.reviveMax, event, 0)
            : new SyncPacket(e.getId(), 0, -1, 0, 0, 0, 0, event, 0);
      }
   }

   public static void onStartTracking(ServerPlayer tracker, Entity target) {
      if (target instanceof LivingEntity living && flags(living) != 0) {
         Network.sendTo(tracker, packet(living, 0));
      }
   }

   public static void onStopTracking(ServerPlayer tracker, Entity target) {
      if (target instanceof LivingEntity living && canBeDowned(living)) {
         Network.sendTo(tracker, new SyncPacket(target.getId(), 0, -1, 0, 0, 0, 0, 0, 0));
      }
   }
}
