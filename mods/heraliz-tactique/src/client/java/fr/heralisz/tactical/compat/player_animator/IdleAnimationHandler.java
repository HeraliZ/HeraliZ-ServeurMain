package fr.heralisz.tactical.compat.player_animator;

import fr.heralisz.tactical.api.client.HeraliszTacticalClientAPI;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.client.resource.display.MeleeDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class IdleAnimationHandler {
   @SubscribeEvent
   public static void onClientTick(RenderTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            for (AbstractClientPlayer clientPlayer : mc.level.players()) {
               if (clientPlayer.getPose() != Pose.SLEEPING
                  && !clientPlayer.onClimbable()
                  && !clientPlayer.isSwimming()
                  && clientPlayer.getPose() != Pose.FALL_FLYING) {
                  ItemStack stack = clientPlayer.getMainHandItem();
                  ThirdPersonAnimationConfig config = getConfig(stack);
                  if (config == null) {
                     stopAll(clientPlayer);
                  } else {
                     if (!PlayerAnimatorIntegration.isAttackAnimationActive(clientPlayer, PlayerAnimatorIntegration.AnimationLayer.UPPER)) {
                        applyIdleAnimationFor(clientPlayer, config);
                     }

                     applyLowerBodyAnimation(clientPlayer, config);
                     enableRotationModifier(clientPlayer, config);
                  }
               } else {
                  stopAll(clientPlayer);
               }
            }
         }
      }
   }

   public static void stopAll(AbstractClientPlayer clientPlayer) {
      PlayerAnimatorIntegration.stopAnimation(clientPlayer, PlayerAnimatorIntegration.AnimationLayer.UPPER, 8);
      PlayerAnimatorIntegration.stopAnimation(clientPlayer, PlayerAnimatorIntegration.AnimationLayer.LOWER, 8);
      PlayerAnimatorIntegration.stopAnimation(clientPlayer, PlayerAnimatorIntegration.AnimationLayer.ROTATION, 4);
   }

   public static void enableRotationModifier(AbstractClientPlayer player, @NotNull ThirdPersonAnimationConfig config) {
      ResourceLocation basePath = config.getAnimationPath();
      if (basePath != null) {
         PlayerAnimatorIntegration.enableRotationModifier(player, basePath, 4);
      }
   }

   public static void applyIdleAnimationFor(AbstractClientPlayer player, @NotNull ThirdPersonAnimationConfig config) {
      String state = getPlayerMovementState(player);
      ResourceLocation basePath = config.getAnimationPath();
      String animName = config.getAnimation(PlayerAnimatorIntegration.AnimationLayer.UPPER, state, 0);
      if (basePath != null && animName != null) {
         PlayerAnimatorIntegration.playIdleAnimation(player, basePath, animName, PlayerAnimatorIntegration.AnimationLayer.UPPER, 4);
      }
   }

   private static void applyLowerBodyAnimation(AbstractClientPlayer player, @NotNull ThirdPersonAnimationConfig config) {
      String state = getPlayerMovementState(player);
      ResourceLocation basePath = config.getAnimationPath();
      String animName = config.getAnimation(PlayerAnimatorIntegration.AnimationLayer.LOWER, state, 0);
      if (basePath != null && animName != null) {
         PlayerAnimatorIntegration.playIdleAnimation(player, basePath, animName, PlayerAnimatorIntegration.AnimationLayer.LOWER, 8);
      }
   }

   private static String getPlayerMovementState(AbstractClientPlayer player) {
      if (!player.onGround()) {
         return player.getDeltaMovement().y > 0.0 ? "jump" : "fall";
      } else {
         double speed = player.getDeltaMovement().horizontalDistanceSqr();
         if (player.isCrouching()) {
            return speed > 0.001 ? "sneak_walk" : "sneak";
         } else if (player.isSprinting()) {
            return "sprint";
         } else if (speed > 0.001) {
            return speed > 0.05 ? "run" : "walk";
         } else {
            return "idle";
         }
      }
   }

   private static ThirdPersonAnimationConfig getConfig(ItemStack stack) {
      return stack.getItem() instanceof IMeleeWeapon
         ? HeraliszTacticalClientAPI.getMeleeDisplay(stack).map(MeleeDisplayInstance::getThirdPersonAnimation).orElse(null)
         : null;
   }
}
