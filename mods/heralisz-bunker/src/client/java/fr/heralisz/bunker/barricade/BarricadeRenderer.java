package fr.heralisz.bunker.barricade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.bunker.HeraliszBunker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rendu de la barricade en bois : mur de rondins couches, chacun different, coince entre trois poteaux avec des agrafes de
 * fer, deux jambes de force derriere (modele BarricadeMesh, meme encombrement que la collision). Version allegee de loin.
 * Texture selon l'etat (intacte -> tres abimee) : fissures et eclats de plus en plus nombreux (outils/barricade_modele.py).
 */
@Mod.EventBusSubscriber(modid = HeraliszBunker.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BarricadeRenderer extends EntityRenderer<BarricadeEntity> {
   static final ResourceLocation[] TEXTURES = new ResourceLocation[4];
   /** Au-dela de cette distance (blocs), version allegee du modele. */
   private static final double LOD_DISTANCE = 24.0;

   static {
      for (int i = 0; i < 4; i++) {
         TEXTURES[i] = new ResourceLocation(HeraliszBunker.MODID, "textures/entity/wooden_barricade_" + i + ".png");
      }
   }

   public BarricadeRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
   }

   @Override
   public void render(BarricadeEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
      float[] mesh = BarricadeMesh.get(this.entityRenderDispatcher.distanceToSqr(entity) > LOD_DISTANCE * LOD_DISTANCE);
      if (mesh != null) {
         ps.pushPose();
         ps.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
         BarricadeMesh.render(mesh, ps.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(entity))), light, 1F, 1F, 1F, 1F);
         ps.popPose();
      }
      super.render(entity, yaw, partialTick, ps, buffers, light);
   }

   @Override
   public ResourceLocation getTextureLocation(BarricadeEntity entity) {
      return TEXTURES[entity.damageStage()];
   }

   @SubscribeEvent
   public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(BarricadeRegistry.BARRICADE.get(), BarricadeRenderer::new);
   }
}
