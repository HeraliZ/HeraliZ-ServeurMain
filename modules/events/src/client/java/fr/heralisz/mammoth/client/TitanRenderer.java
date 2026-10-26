package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.SetPieceBlocks;
import fr.heralisz.mammoth.dungeon.SetPieceEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Titan pris dans la glace : glace translucide, le chasseur géant dedans, son cœur de givre qui bat. En alerte, la lueur
 * vire au rouge et palpite plus vite.
 */
public class TitanRenderer extends GeoBlockRenderer<SetPieceEntities.TitanBE> {
   private static ResourceLocation r(String p) {
      return new ResourceLocation(HeraliszMammoth.MODID, p);
   }

   // D-054 : textures GeckoLib rangees dans textures/entity/prop (hors de l'atlas des blocs, qui ne prend que textures/block)
   private static final ResourceLocation TEX = r("textures/entity/prop/prop_frozen_titan.png"), GLOW = r("textures/entity/prop/prop_frozen_titan_glowmask.png");

   public TitanRenderer() {
      super(new GeoModel<SetPieceEntities.TitanBE>() {
         @Override
         public ResourceLocation getModelResource(SetPieceEntities.TitanBE be) {
            return r("geo/block/prop_frozen_titan.geo.json");
         }

         @Override
         public ResourceLocation getTextureResource(SetPieceEntities.TitanBE be) {
            return TEX;
         }

         @Override
         public ResourceLocation getAnimationResource(SetPieceEntities.TitanBE be) {
            return r("animations/block/prop_frozen_titan.animation.json");
         }
      });
   }

   @Override
   public RenderType getRenderType(SetPieceEntities.TitanBE be, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityTranslucent(tex);
   }

   @Override
   public void actuallyRender(PoseStack ps, SetPieceEntities.TitanBE be, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc,
                              boolean reRender, float partial, int light, int overlay, float r, float g, float b, float a) {
      super.actuallyRender(ps, be, model, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
      if (reRender || Minecraft.getInstance().level == null) return;
      boolean alert = be.getBlockState().hasProperty(SetPieceBlocks.ALERT) && be.getBlockState().getValue(SetPieceBlocks.ALERT);
      float t = (Minecraft.getInstance().level.getGameTime() + partial) / 20F;
      float k = alert ? 0.7F + 0.3F * Mth.sin(t * 7F) : 0.75F + 0.15F * Mth.sin(t * 1.4F);
      RenderType glow = RenderType.eyes(GLOW);
      super.actuallyRender(ps, be, model, glow, buf, buf.getBuffer(glow), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         k, alert ? k * 0.22F : k, alert ? k * 0.2F : k, 1F);
      if (alert) {                                                          // la glace chauffe : elle rougeoie, de plus en plus fort
         float heat = 0.45F + 0.25F * Mth.sin(t * 4F);
         RenderType hot = RenderType.eyes(TEX);
         super.actuallyRender(ps, be, model, hot, buf, buf.getBuffer(hot), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            heat, heat * 0.32F, heat * 0.08F, 1F);
      }
   }

   @Override
   protected void rotateBlock(Direction facing, PoseStack ps) {
      float deg = switch (facing) {
         case SOUTH -> 180F;
         case WEST -> 90F;
         case EAST -> 270F;
         default -> 0F;
      };
      ps.mulPose(Axis.YP.rotationDegrees(deg));
   }

   @Override
   public boolean shouldRenderOffScreen(SetPieceEntities.TitanBE be) {
      return true;
   }

   @Override
   public int getViewDistance() {
      return 80;
   }
}
