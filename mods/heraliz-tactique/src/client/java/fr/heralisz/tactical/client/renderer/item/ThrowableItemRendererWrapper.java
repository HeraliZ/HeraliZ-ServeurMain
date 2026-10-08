package fr.heralisz.tactical.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.client.HeraliszTacticalClientAPI;
import fr.heralisz.tactical.api.animation.ThrowableAnimationStateContext;
import fr.heralisz.tactical.client.renderer.JumpSwayUtil;
import fr.heralisz.tactical.client.resource.display.ThrowableDisplayInstance;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class ThrowableItemRendererWrapper extends AnimateGeoItemRenderer<BedrockAnimatedModel, ThrowableAnimationStateContext> {
   private static final SlotModel SLOT_MODEL = new SlotModel();

   public ThrowableAnimationStateContext initContext(ItemStack stack, Player player, float partialTick) {
      ThrowableAnimationStateContext context = new ThrowableAnimationStateContext();
      this.updateContext(context, stack, player, partialTick);
      return context;
   }

   public void updateContext(ThrowableAnimationStateContext context, ItemStack stack, Player player, float partialTick) {
      context.setUsing(player.isUsingItem());
      context.setUsingTick(player.getTicksUsingItem());
      context.setPartialTicks(partialTick);
      context.setCurrentItem(stack);
   }

   public ResourceLocation getTextureLocation(ItemStack stack) {
      return HeraliszTacticalClientAPI.getThrowableDisplay(stack).map(ThrowableDisplayInstance::getTexture).orElse(null);
   }

   @Nullable
   public LuaAnimationStateMachine<ThrowableAnimationStateContext> getStateMachine(ItemStack stack) {
      return HeraliszTacticalClientAPI.getThrowableDisplay(stack).map(ThrowableDisplayInstance::getStateMachine).orElse(null);
   }

   public BedrockAnimatedModel getModel(ItemStack stack) {
      return HeraliszTacticalClientAPI.getThrowableDisplay(stack).map(ThrowableDisplayInstance::getModel).orElse(null);
   }

   public long getPutAwayTime(ItemStack stack) {
      return HeraliszTacticalAPI.getThrowableIndex(stack).map(ThrowableIndex::getData).map(ThrowableData::getPutAwayTime).orElse(0L);
   }

   public void renderFirstPerson(
      LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick
   ) {
      BedrockAnimatedModel model = this.getModel(stack);
      if (model != null) {
         poseStack.pushPose();
         LuaAnimationStateMachine<ThrowableAnimationStateContext> stateMachine = this.getStateMachine(stack);
         if (stateMachine != null) {
            stateMachine.processContextIfExist(context -> this.updateContext(context, stack, player, partialTick));
            stateMachine.update();
         }

         float xRotOffset = Mth.lerp(partialTick, player.xBobO, player.xBob);
         float yRotOffset = Mth.lerp(partialTick, player.yBobO, player.yBob);
         float xRot = player.getViewXRot(partialTick) - xRotOffset;
         float yRot = player.getViewYRot(partialTick) - yRotOffset;
         poseStack.mulPose(Axis.XP.rotationDegrees(xRot * -0.1F));
         poseStack.mulPose(Axis.YP.rotationDegrees(yRot * -0.1F));
         BedrockPart rootNode = model.getRootNode();
         if (rootNode != null) {
            xRot = (float)Math.tanh(xRot / 25.0F) * 25.0F;
            yRot = (float)Math.tanh(yRot / 25.0F) * 25.0F;
            rootNode.offsetX += yRot * 0.1F / 16.0F / 3.0F;
            rootNode.offsetY += -xRot * 0.1F / 16.0F / 3.0F;
            rootNode.additionalQuaternion.mul(Axis.XP.rotationDegrees(xRot * 0.05F));
            rootNode.additionalQuaternion.mul(Axis.YP.rotationDegrees(yRot * 0.05F));
         }

         poseStack.translate(0.0F, 1.5F, 0.0F);
         poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
         this.doExtraTransforms(poseStack, model, stack);
         model.render(poseStack, ctx, this.getRenderType(stack), light, OverlayTexture.NO_OVERLAY);
         model.cleanAnimationTransform();
         poseStack.popPose();
      }
   }

   public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, float multiplier) {
      super.applyItemInHandCameraAnimation(event, stack, multiplier);
      BedrockAnimatedModel model = this.getModel(stack);
      if (model != null) {
         model.cleanCameraAnimationTransform();
      }
   }

   public void doExtraTransforms(PoseStack poseStack, BedrockAnimatedModel model, ItemStack stack) {
      super.doExtraTransforms(poseStack, model, stack);
      JumpSwayUtil.applyJumpingSway(model, Minecraft.getInstance().getFrameTime());
   }

   public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
      if (!ctx.firstPerson()) {
         HeraliszTacticalClientAPI.getThrowableDisplay(stack).ifPresentOrElse(display -> {
            BedrockAnimatedModel model = display.getModel();
            if (ctx == ItemDisplayContext.GUI && display.getSlotTexture() != null) {
               poseStack.translate(0.5, 1.5, 0.5);
               poseStack.mulPose(Axis.ZN.rotationDegrees(180.0F));
               VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(display.getSlotTexture()));
               SLOT_MODEL.renderToBuffer(poseStack, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            } else {
               poseStack.pushPose();
               ItemTransforms transforms = display.getTransforms();
               if (transforms != null) {
                  poseStack.translate(0.5F, 0.5F, 0.5F);
                  ItemTransform transform = transforms.getTransform(ctx);
                  transform.apply(false, poseStack);
                  poseStack.translate(-0.5F, -0.5F, -0.5F);
               }

               Vector3f displayOffset = display.getDisplayOffset();
               if (displayOffset != null) {
                  poseStack.translate(displayOffset.x(), displayOffset.y(), displayOffset.z());
               }

               poseStack.translate(0.5, 1.5, 0.5);
               poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
               if (model != null) {
                  model.render(poseStack, ctx, RenderType.entityCutout(this.getTextureLocation(stack)), light, overlay);
               } else {
                  VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(MissingTextureAtlasSprite.getLocation()));
                  SLOT_MODEL.renderToBuffer(poseStack, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
               }

               poseStack.popPose();
            }
         }, () -> {
            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.ZN.rotationDegrees(180.0F));
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(MissingTextureAtlasSprite.getLocation()));
            SLOT_MODEL.renderToBuffer(poseStack, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
         });
      }
   }
}
