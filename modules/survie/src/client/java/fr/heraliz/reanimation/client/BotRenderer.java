package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.reanimation.entity.BotEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class BotRenderer extends LivingEntityRenderer<BotEntity, AnimatedPlayerModel<BotEntity>> {
   private static final ResourceLocation[] SKINS = new ResourceLocation[BotEntity.SKINS.length];

   public BotRenderer(Context context) {
      super(context, new AnimatedPlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
      this.addLayer(
         new HumanoidArmorLayer(
            this,
            new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()
         )
      );
      this.addLayer(new ItemInHandLayer(this, context.getItemInHandRenderer()));
      this.addLayer(new ArrowLayer(context, this));
      this.addLayer(new CustomHeadLayer(this, context.getModelSet(), context.getItemInHandRenderer()));
      this.addLayer(new ExecutionLayer(this));
   }

   public void render(BotEntity bot, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
      if (!ExecutionScenes.hidden(bot)) {
         AnimatedPlayerModel<BotEntity> m = (AnimatedPlayerModel<BotEntity>)this.getModel();
         m.crouching = bot.isCrouching();
         m.head.visible = true;
         m.hat.visible = true;
         m.rightArmPose = bot.getMainHandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
         m.leftArmPose = bot.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
         boolean pushed = Poses.pushTransform(bot, poseStack, partialTick);
         ExecutionScenes.stashHands(bot);

         try {
            super.render(bot, yaw, partialTick, poseStack, buffers, light);
         } finally {
            ExecutionScenes.restoreHands(bot);
         }

         if (pushed) {
            poseStack.popPose();
         }
      }
   }

   static ResourceLocation skin(BotEntity bot) {
      return SKINS[bot.skin()];
   }

   static ResourceLocation skin(int index) {
      return SKINS[Math.floorMod(index, SKINS.length)];
   }

   public Vec3 getRenderOffset(BotEntity bot, float partialTick) {
      return bot.isCrouching() ? new Vec3(0.0, -0.125, 0.0) : super.getRenderOffset(bot, partialTick);
   }

   protected void scale(BotEntity bot, PoseStack poseStack, float partialTick) {
      poseStack.scale(0.9375F, 0.9375F, 0.9375F);
   }

   protected void setupRotations(BotEntity bot, PoseStack poseStack, float bob, float bodyYaw, float partialTick) {
      super.setupRotations(bot, poseStack, bob, bodyYaw, partialTick);
      float swim = bot.getSwimAmount(partialTick);
      if (swim > 0.0F) {
         poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(swim, 0.0F, -90.0F)));
         if (bot.isVisuallySwimming()) {
            poseStack.translate(0.0F, -1.0F, 0.3F);
         }
      }
   }

   public ResourceLocation getTextureLocation(BotEntity bot) {
      return SKINS[bot.skin()];
   }

   static {
      for (int i = 0; i < SKINS.length; i++) {
         SKINS[i] = new ResourceLocation("textures/entity/player/wide/" + BotEntity.SKINS[i] + ".png");
      }
   }
}
