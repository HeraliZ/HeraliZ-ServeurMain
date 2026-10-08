package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.reanimation.entity.BotEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

final class SceneRenderer {
   private static PlayerModel<LivingEntity> wide;
   private static PlayerModel<LivingEntity> slim;

   private SceneRenderer() {
   }

   static void render(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         Vec3 cam = event.getCamera().getPosition();
         PoseStack ps = event.getPoseStack();
         float pt = event.getPartialTick();
         BufferSource buffers = mc.renderBuffers().bufferSource();
         boolean drawn = false;

         for (ExecutionScenes.Scene s : ExecutionScenes.all()) {
            if (s.level == mc.level && s.state(pt) instanceof Script.Frame f) {
               ps.pushPose();
               ps.translate(s.origin.x - cam.x, s.origin.y - cam.y, s.origin.z - cam.z);
               ps.mulPose(Axis.YP.rotationDegrees(-s.yaw));

               for (Script.Prop prop : f.props) {
                  ps.pushPose();
                  ps.mulPoseMatrix(prop.matrix());
                  Vector3f centre = prop.matrix().transformPosition(new Vector3f(0.5F, 0.5F, 0.5F));
                  mc.getBlockRenderer().renderSingleBlock(state(prop.material()), ps, buffers, light(s, centre), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                  ps.popPose();
                  drawn = true;
               }

               if (f.knife.holder == Knife.Holder.SCENE) {
                  ItemStack stack = Weapons.of(s);
                  if (!stack.isEmpty()) {
                     ps.pushPose();
                     ps.mulPoseMatrix(f.knife.matrix);
                     Vector3f at = Knife.through(f.knife.matrix, f.knife.blade.pivot);
                     Weapons.render(s, f.knife, stack, s.actor, ps, buffers, light(s, at), s.time(pt));
                     ps.popPose();
                     drawn = true;
                  }
               }

               if (f.headOff && s.victim != null) {
                  head(mc, s, f, ps, buffers);
                  drawn = true;
               }

               ps.popPose();
            }
         }

         if (drawn) {
            buffers.endBatch();
         }
      }
   }

   private static void head(Minecraft mc, ExecutionScenes.Scene s, Script.Frame f, PoseStack ps, MultiBufferSource buffers) {
      LivingEntity victim = s.victim;
      ResourceLocation skin = victim instanceof AbstractClientPlayer p ? p.getSkinTextureLocation() : (victim instanceof BotEntity bot ? BotRenderer.skin(bot) : null);
      if (skin != null) {
         boolean isSlim = victim instanceof AbstractClientPlayer p && "slim".equals(p.getModelName());
         if (wide == null) {
            wide = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
         }

         PlayerModel<LivingEntity> model = isSlim ? slim : wide;
         ps.pushPose();
         ps.mulPoseMatrix(f.head);
         Vector3f centre = f.head.transformPosition(new Vector3f(Script.HEAD_CENTRE).div(16.0F));
         VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
         int light = light(s, centre);

         for (ModelPart part : new ModelPart[]{model.head, model.hat}) {
            part.setPos(0.0F, 0.0F, 0.0F);
            part.setRotation(0.0F, 0.0F, 0.0F);
            part.visible = true;
            part.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
         }

         ps.popPose();
      }
   }

   private static BlockState state(Script.Material material) {
      return switch (material) {
         case POST -> Blocks.DARK_OAK_LOG.defaultBlockState();
         case BEAM -> (BlockState)Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X);
      };
   }

   private static int light(ExecutionScenes.Scene s, Vector3f scene) {
      Vec3 w = s.world(scene);
      return LevelRenderer.getLightColor(s.level, BlockPos.containing(w.x, w.y + 0.1, w.z));
   }
}
