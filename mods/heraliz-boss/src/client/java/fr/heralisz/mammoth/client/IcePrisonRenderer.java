package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.IcePrisonBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** La prison d'Ymirak : glace translucide ; les fissures n'apparaissent qu'avec les étapes, incandescentes et palpitantes. */
public class IcePrisonRenderer extends GeoBlockRenderer<IcePrisonBlock.BE> {
   private static ResourceLocation r(String p) {
      return new ResourceLocation(HeraliszMammoth.MODID, p);
   }

   // D-054 : textures GeckoLib rangees dans textures/entity/prop (hors de l'atlas des blocs, qui ne prend que textures/block)
   private static final ResourceLocation TEX = r("textures/entity/prop/hero_ice_prison.png"), GLOW = r("textures/entity/prop/hero_ice_prison_glowmask.png");

   public IcePrisonRenderer() {
      super(new GeoModel<IcePrisonBlock.BE>() {
         @Override
         public ResourceLocation getModelResource(IcePrisonBlock.BE be) {
            return r("geo/block/hero_ice_prison.geo.json");
         }

         @Override
         public ResourceLocation getTextureResource(IcePrisonBlock.BE be) {
            return TEX;
         }

         @Override
         public ResourceLocation getAnimationResource(IcePrisonBlock.BE be) {
            return r("animations/block/hero_ice_prison.animation.json");
         }

         @Override
         public void setCustomAnimations(IcePrisonBlock.BE be, long id, AnimationState<IcePrisonBlock.BE> st) {
            super.setCustomAnimations(be, id, st);
            int s = be.stage();
            getBone("shell").ifPresent(b -> b.setHidden(s >= 4));            // éclatée : plus rien n'est dessiné
            for (int i = 1; i <= 3; i++) {
               int k = i;
               getBone("crack" + i).ifPresent(b -> b.setHidden(s < k || s >= 4));
            }
         }
      });
      addRenderLayer(new GeoRenderLayer<>(this) {
         @Override
         public void render(PoseStack ps, IcePrisonBlock.BE be, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, float partial,
                            int light, int overlay) {
            if (be.stage() == 0 || be.stage() >= 4 || Minecraft.getInstance().level == null) return;
            float t = (Minecraft.getInstance().level.getGameTime() + partial) / 20F;
            float k = 0.75F + 0.25F * Mth.sin(t * (3F + be.stage() * 2F));
            RenderType g = RenderType.eyes(GLOW);
            getRenderer().reRender(model, ps, buf, be, g, buf.getBuffer(g), partial, 0xF000F0, OverlayTexture.NO_OVERLAY, k, k, k, 1F);
         }
      });
   }

   @Override
   public RenderType getRenderType(IcePrisonBlock.BE be, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityTranslucent(tex);
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
   public boolean shouldRenderOffScreen(IcePrisonBlock.BE be) {
      return true;
   }

   @Override
   public int getViewDistance() {
      return 96;
   }
}
