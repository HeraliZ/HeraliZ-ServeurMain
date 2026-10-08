package fr.heralisz.bunker.barricade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.bunker.HeraliszBunker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Aperçu de pose façon Rust : quand le joueur tient une barricade, un fantôme translucide montre où elle sera posée,
 * vert si la pose est possible, rouge sinon (même calcul que la pose, BarricadePlacement).
 */
@Mod.EventBusSubscriber(modid = HeraliszBunker.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BarricadePreview {
   private static ModelPart model;

   private BarricadePreview() {
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player == null || mc.level == null || mc.options.hideGui) {
         return;
      }
      if (!player.getMainHandItem().is(BarricadeRegistry.BARRICADE_ITEM.get()) && !player.getOffhandItem().is(BarricadeRegistry.BARRICADE_ITEM.get())) {
         return;
      }
      if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
         return;
      }
      if (model == null) {
         model = mc.getEntityModels().bakeLayer(BarricadeRenderer.LAYER);
      }
      BarricadePlacement.Result where = BarricadePlacement.compute(mc.level, player, hit);
      Vec3 cam = event.getCamera().getPosition();
      PoseStack ps = event.getPoseStack();
      ps.pushPose();
      ps.translate(where.pos().x - cam.x, where.pos().y - cam.y, where.pos().z - cam.z);
      ps.mulPose(Axis.YP.rotationDegrees(-where.yaw()));
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      RenderType type = RenderType.entityTranslucent(BarricadeRenderer.TEXTURES[0]);
      float r = where.valid() ? 0.35F : 1.0F, g = where.valid() ? 1.0F : 0.25F, b = where.valid() ? 0.4F : 0.25F;
      model.render(ps, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, 0.45F);
      buffers.endBatch(type);
      ps.popPose();
   }
}
