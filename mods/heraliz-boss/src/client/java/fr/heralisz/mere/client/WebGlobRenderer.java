package fr.heralisz.mere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.mere.entity.WebGlob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Globe de toile (pelote de soie hérissée), cocon d'œufs (sac blanc qui palpite) ou paquet de soie de la Mère des Ruines. */
public class WebGlobRenderer extends EntityRenderer<WebGlob> {
   public WebGlobRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
      this.shadowRadius = 0.3F;
   }

   private static void cube(PoseStack ps, MultiBufferSource buf, BlockState st, int light) {
      ps.pushPose();
      ps.translate(-0.5, -0.5, -0.5);
      Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }

   @Override
   public void render(WebGlob e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      float t = e.tickCount + partial;
      ps.pushPose();
      ps.translate(0, 0.3, 0);
      ps.mulPose(Axis.YP.rotationDegrees(t * 14));
      ps.mulPose(Axis.XP.rotationDegrees(t * 9));
      if (e.kind() != 1) {
         ps.scale(0.5F, 0.5F, 0.5F);
         cube(ps, buf, Blocks.WHITE_WOOL.defaultBlockState(), light);
         ps.scale(1.6F, 1.6F, 1.6F);
         cube(ps, buf, Blocks.COBWEB.defaultBlockState(), light);
      } else {
         float s = 0.6F * (1 + 0.1F * Mth.sin(t * 0.8F));
         ps.scale(s, s * 1.3F, s);
         cube(ps, buf, Blocks.WHITE_WOOL.defaultBlockState(), light);
         ps.scale(0.7F, 0.7F, 0.7F);
         cube(ps, buf, Blocks.HONEY_BLOCK.defaultBlockState(), light);
      }
      ps.popPose();
      super.render(e, yaw, partial, ps, buf, light);
   }

   @Override
   public ResourceLocation getTextureLocation(WebGlob e) {
      return TextureAtlas.LOCATION_BLOCKS;
   }
}
