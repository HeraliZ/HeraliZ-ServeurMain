package fr.heraliz.reanimation.entity;

import fr.heraliz.reanimation.ReanimationMod;
import fr.heraliz.reanimation.core.DownedManagerServeur;
import fr.heraliz.reanimation.core.DownedState;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.execution.Executions;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class BotGoals {
   private BotGoals() {
   }

   static final class Downed extends Goal {
      private final BotEntity bot;

      Downed(BotEntity bot) {
         this.bot = bot;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
      }

      public boolean canUse() {
         return DownedManagerServeur.isDowned(this.bot);
      }

      public void stop() {
         this.bot.getNavigation().stop();
      }

      public void tick() {
         DownedState s = DownedManagerServeur.get(this.bot);
         if (s != null) {
            if (s.searcher != null || s.reviver != null || s.executor != null) {
               this.bot.getNavigation().stop();
               this.bot.getLookControl().setLookAt(s.partner(), 10.0F, 10.0F);
            } else if (this.bot.getNavigation().isDone() && this.bot.getRandom().nextInt(50) == 0) {
               Player near = this.bot.level().getNearestPlayer(this.bot, 12.0);
               Vec3 dest = near != null ? DefaultRandomPos.getPosAway(this.bot, 5, 2, near.position()) : DefaultRandomPos.getPos(this.bot, 4, 2);
               if (dest != null) {
                  this.bot.getNavigation().moveTo(dest.x, dest.y, dest.z, 0.4);
               }
            }
         }
      }
   }

   static final class Help extends Goal {
      private final BotEntity bot;
      private final Map<UUID, Long> searched = new HashMap<>();
      private LivingEntity target;
      private int cooldown;
      private int ticks;
      private boolean started;
      private int closeTicks;

      Help(BotEntity bot) {
         this.bot = bot;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      }

      public boolean canUse() {
         if (this.bot.mode() != BotEntity.Mode.PASSIVE && !DownedManagerServeur.isDowned(this.bot)) {
            if (--this.cooldown > 0) {
               return false;
            }

            this.cooldown = 20;
            long now = this.bot.level().getGameTime();
            LivingEntity best = null;
            double bestDistance = 576.0;

            for (LivingEntity e : this.bot.level().getEntitiesOfClass(LivingEntity.class, this.bot.getBoundingBox().inflate(24.0))) {
               if (e != this.bot) {
                  DownedState s = DownedManagerServeur.get(e);
                  if (s != null
                     && !s.dying
                     && s.searcher == null
                     && s.reviver == null
                     && s.executor == null
                     && (this.bot.mode() != BotEntity.Mode.LOOTER || now - this.searched.getOrDefault(e.getUUID(), -100000L) >= 600L)) {
                     double d = this.bot.distanceToSqr(e);
                     if (d < bestDistance) {
                        bestDistance = d;
                        best = e;
                     }
                  }
               }
            }

            this.target = best;
            return best != null;
         } else {
            return false;
         }
      }

      public void start() {
         this.ticks = 0;
         this.closeTicks = 0;
         this.started = false;
         this.bot.getNavigation().moveTo(this.target, 1.0);
      }

      public boolean canContinueToUse() {
         if (this.target != null && this.target.isAlive() && !DownedManagerServeur.isDowned(this.bot) && this.ticks <= 900) {
            DownedState s = DownedManagerServeur.get(this.target);
            if (s == null) {
               return false;
            }

            boolean mine = s.searcher == this.bot || s.reviver == this.bot || s.executor == this.bot;
            return this.started && !mine ? false : mine || s.searcher == null && s.reviver == null && s.executor == null;
         } else {
            return false;
         }
      }

      public void tick() {
         this.ticks++;
         this.bot.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
         DownedState s = DownedManagerServeur.get(this.target);
         if (s != null) {
            if (s.searcher != this.bot && s.reviver != this.bot && s.executor != this.bot) {
               Vec3 spot = DownedManagerServeur.kneelSpot(this.bot, this.target);
               double toSpot = this.bot.position().distanceToSqr(spot.x, this.bot.getY(), spot.z);
               boolean close = this.bot.distanceToSqr(this.target) < 3.61;
               this.closeTicks = close ? this.closeTicks + 1 : 0;
               if (ReanimationMod.TRACE && this.ticks % 20 == 0) {
                  ReanimationMod.LOGGER
                     .info(
                        "[BOT] {} -> {}: dist {} spot {} nav done {}",
                        new Object[]{
                           this.bot.getName().getString(),
                           this.target.getName().getString(),
                           String.format(Locale.ROOT, "%.2f", this.bot.distanceTo(this.target)),
                           String.format(Locale.ROOT, "%.2f", Math.sqrt(toSpot)),
                           this.bot.getNavigation().isDone()
                        }
                     );
               }

               if (close && (toSpot < 0.6400000000000001 || this.bot.getNavigation().isDone() || this.closeTicks > 30)) {
                  this.bot.getNavigation().stop();
                  this.started = true;
                  if (this.bot.mode() == BotEntity.Mode.MEDIC) {
                     DownedManagerServeur.startRevive(this.bot, this.target);
                  } else if (this.bot.mode() == BotEntity.Mode.EXECUTIONER) {
                     Executions.start(this.bot, this.target, ExecutionType.values()[this.bot.getRandom().nextInt(ExecutionType.values().length)]);
                  } else {
                     DownedManagerServeur.startSearch(this.bot, this.target);
                  }

                  if (ReanimationMod.TRACE) {
                     ReanimationMod.LOGGER
                        .info(
                           "[BOT] {} starts on {}: {}",
                           new Object[]{
                              this.bot.getName().getString(),
                              this.target.getName().getString(),
                              s.searcher != this.bot && s.reviver != this.bot && s.executor != this.bot ? "refused" : "ok"
                           }
                        );
                  }
               } else if (this.ticks % 10 == 1 || this.bot.getNavigation().isDone()) {
                  boolean moving = this.bot.distanceToSqr(this.target) < 9.0
                     && this.bot.getNavigation().moveTo(spot.x, this.target.getY(), spot.z, 1.0);
                  if (!moving) {
                     this.bot.getNavigation().moveTo(this.target, 1.0);
                  }
               }
            } else {
               this.bot.getNavigation().stop();
            }
         }
      }

      public void stop() {
         DownedManagerServeur.endAction(this.bot, true);
         if (this.target != null && this.started && this.bot.mode() == BotEntity.Mode.LOOTER) {
            this.searched.put(this.target.getUUID(), this.bot.level().getGameTime());
         }

         this.target = null;
         this.cooldown = 40;
         this.bot.getNavigation().stop();
      }
   }
}
