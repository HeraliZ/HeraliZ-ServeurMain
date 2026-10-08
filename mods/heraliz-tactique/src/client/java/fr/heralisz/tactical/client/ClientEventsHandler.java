package fr.heralisz.tactical.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.client.input.InteractKey;
import fr.heralisz.tactical.api.animation.FlashShieldAnimationStateContext;
import fr.heralisz.tactical.api.animation.MeleeAnimationStateContext;
import fr.heralisz.tactical.api.collision.OBB;
import fr.heralisz.tactical.api.item.ICustomItem;
import fr.heralisz.tactical.client.renderer.item.FlashShieldItemRenderer;
import fr.heralisz.tactical.client.renderer.item.MeleeItemRenderer;
import fr.heralisz.tactical.config.ClientConfig;
import fr.heralisz.tactical.init.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(value = Dist.CLIENT, modid = "heralisz_tactical")
public class ClientEventsHandler {
   public static double shakeTime = 0.0;
   public static double shakeRadius = 0.0;
   public static double shakeAmplitude = 0.0;
   public static Vec3 shakePos = Vec3.ZERO;
   public static double shakeType = 0.0;

   public static void handleShakeClient(double time, double radius, double amplitude, Vec3 position) {
      Player player = Minecraft.getInstance().player;
      if (player != null && !player.isSpectator()) {
         float shakeStrength = ((Double)ClientConfig.EXPLODE_SCREEN_SHAKE_MULTIPLIER.get()).floatValue();
         if (!(shakeStrength <= 0.0F)) {
            shakeTime = time;
            shakeRadius = radius;
            shakeAmplitude = amplitude * (float) (Math.PI / 180.0) * shakeStrength;
            shakePos = position;
            shakeType = 2.0 * (Math.random() - 0.5);
         }
      }
   }

   @SubscribeEvent
   public static void computeCameraAngles(ComputeCameraAngles event) {
      Entity entity = event.getCamera().getEntity();
      if (entity instanceof LivingEntity) {
         LocalPlayer player = Minecraft.getInstance().player;
         float yaw = event.getYaw();
         float pitch = event.getPitch();
         float roll = event.getRoll();
         shakeTime = Mth.lerp(0.05 * event.getPartialTick(), shakeTime, 0.0);
         if (player != null && shakeTime > 0.0) {
            float shakeRadiusAmplitude = (float)Mth.clamp(1.0 - player.position().distanceTo(shakePos) / shakeRadius, 0.0, 1.0);
            boolean onVehicle = player.getVehicle() != null;
            double f = shakeTime * Math.sin((Math.PI / 2) * shakeTime) * shakeAmplitude * shakeRadiusAmplitude * (onVehicle ? 0.1 : 1.0);
            if (shakeType > 0.0) {
               event.setYaw((float)(yaw + f * shakeType));
               event.setPitch((float)(pitch - f * shakeType));
               event.setRoll((float)(roll - f));
            } else {
               event.setYaw((float)(yaw - f * shakeType));
               event.setPitch((float)(pitch + f * shakeType));
               event.setRoll((float)(roll + f));
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (event.getHand() == InteractionHand.OFF_HAND) {
            ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
            if (stack.getItem() instanceof ICustomItem item && item.blockOffhandRendering(stack)) {
               event.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void tickAnimation(ClientTickEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         ItemStack mainHandItem = player.getMainHandItem();
         BlockEntityWithoutLevelRenderer renderer = IClientItemExtensions.of(mainHandItem).getCustomRenderer();
         if (renderer instanceof MeleeItemRenderer renderer1) {
            LuaAnimationStateMachine<MeleeAnimationStateContext> animationStateMachine = renderer1.getStateMachine(mainHandItem);
            if (animationStateMachine != null) {
               tickMove(player, animationStateMachine);
            }
         } else if (renderer instanceof FlashShieldItemRenderer renderer2) {
            LuaAnimationStateMachine<FlashShieldAnimationStateContext> animationStateMachine = renderer2.getStateMachine(mainHandItem);
            if (animationStateMachine != null) {
               tickMove(player, animationStateMachine);
            }
         }
      }
   }

   private static boolean tickMove(LocalPlayer player, LuaAnimationStateMachine<?> animationStateMachine) {
      if (player.input == null) {
         animationStateMachine.trigger("idle");
         return true;
      }

      if (!player.isMovingSlowly() && player.isSprinting()) {
         animationStateMachine.trigger("run");
      } else if (!player.isMovingSlowly() && player.input.getMoveVector().length() > 0.01) {
         animationStateMachine.trigger("walk");
      } else {
         animationStateMachine.trigger("idle");
      }

      return false;
   }

   public static void afterLevel(float pPartialTicks, long pNanoTime, boolean pRenderLevel) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (pRenderLevel && mc.level != null && player != null) {
         MobEffectInstance effect = player.getEffect((MobEffect)ModEffects.BLIND.get());
         if (effect != null) {
            int tickRemain = effect.getDuration();
            int alpha = tickRemain > 100 ? 255 : (int)(tickRemain / 100.0F * 255.0F);
            int color = (ClientConfig.BLACK_FLASH.get() ? 0 : 16777215) + (alpha << 24);
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            graphics.fill(0, 0, width, height, color);
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
         }
      }
   }

   @SubscribeEvent
   public static void onClickInput(InteractionKeyMappingTriggered event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (!InteractKey.INTERACT_KEY.isDown()) {
            ItemStack itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (itemInHand.getItem() instanceof ICustomItem customItem) {
               boolean flag = event.isAttack() && customItem.shouldBlockAttack()
                  || event.isUseItem() && customItem.shouldBlockUse()
                  || event.isPickBlock() && customItem.shouldBlockUse();
               if (flag) {
                  event.setSwingHand(false);
                  event.setCanceled(true);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void renderAttackHitBox(RenderLevelStageEvent event) {
   }

   public static void renderOBB(
      PoseStack poseStack,
      VertexConsumer buffer,
      float centerX,
      float centerY,
      float centerZ,
      Quaternionf rotation,
      float halfX,
      float halfY,
      float halfZ,
      float red,
      float green,
      float blue,
      float alpha
   ) {
      poseStack.pushPose();
      poseStack.translate(centerX, centerY, centerZ);
      poseStack.mulPose(rotation);
      LevelRenderer.renderLineBox(poseStack, buffer, -halfX, -halfY, -halfZ, halfX, halfY, halfZ, red, green, blue, alpha);
      poseStack.popPose();
   }

   public static void renderOBB(Vec3 position, OBB obb, PoseStack poseStack, VertexConsumer buffer, float red, float green, float blue, float alpha) {
      Vector3f center = obb.center();
      Vector3f halfExtents = obb.extents();
      Quaternionf rotation = obb.rotation();
      renderOBB(
         poseStack,
         buffer,
         (float)(center.x() - position.x()),
         (float)(center.y() - position.y()),
         (float)(center.z() - position.z()),
         rotation,
         halfExtents.x(),
         halfExtents.y(),
         halfExtents.z(),
         red,
         green,
         blue,
         alpha
      );
   }
}
