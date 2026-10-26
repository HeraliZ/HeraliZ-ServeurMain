package fr.heralisz.tactical.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.tactical.api.client.HeraliszTacticalClientAPI;
import fr.heralisz.tactical.client.renderer.item.ThrowableItemRendererWrapper;
import fr.heralisz.tactical.client.renderer.model.CustomBedrockModel;
import fr.heralisz.tactical.entity.StickyGrenadeEntity;
import fr.heralisz.tactical.entity.ThrowableItemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class ThrowableEntityRenderer extends EntityRenderer<ThrowableItemEntity> {
   public ThrowableEntityRenderer(Context renderManager) {
      super(renderManager);
   }

   public void render(ThrowableItemEntity entityIn, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferIn, int light) {
      poseStack.pushPose();
      if (entityIn.getItem() != null) {
         if (entityIn instanceof StickyGrenadeEntity stickyGrenade) {
            Direction stuckFace = stickyGrenade.getStuckFace();
            if (stuckFace != null) {
               poseStack.translate(-stuckFace.getStepX() * 0.15, (1 - stuckFace.getStepY()) * 0.15, -stuckFace.getStepZ() * 0.15);
            }
         }

         float yRot = Mth.lerp(partialTicks, entityIn.yRotO, entityIn.getYRot());
         poseStack.mulPose(Axis.YN.rotationDegrees(yRot));
         poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, entityIn.xRotO, entityIn.getXRot())));
         HeraliszTacticalClientAPI.getThrowableDisplay(entityIn.getItem()).ifPresent(display -> {
            ItemTransform entityTransform = display.getEntityTransform();
            if (entityTransform != null) {
               entityTransform.apply(false, poseStack);
            }
         });
         CustomBedrockModel model = null;
         if (IClientItemExtensions.of(entityIn.getItem()).getCustomRenderer() instanceof ThrowableItemRendererWrapper renderer
            && renderer.getModel(entityIn.getItem()) instanceof CustomBedrockModel customModel) {
            model = customModel;
            model.setEntityRendering(true);
         }

         Minecraft.getInstance()
            .getItemRenderer()
            .renderStatic(entityIn.getItem(), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, bufferIn, entityIn.level(), 0);
         if (model != null) {
            model.setEntityRendering(false);
         }
      }

      poseStack.popPose();
   }

   public ResourceLocation getTextureLocation(ThrowableItemEntity pEntity) {
      return null;
   }
}
