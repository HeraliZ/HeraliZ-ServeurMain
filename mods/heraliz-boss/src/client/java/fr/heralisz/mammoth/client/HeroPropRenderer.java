package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.HeroPropBE;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Rendu des décors « héros » (un seul rendu pour tous : modèle, texture et lueur choisis d'après le type du bloc). */
public class HeroPropRenderer extends GeoBlockRenderer<HeroPropBE> {
   private static ResourceLocation r(String p) {
      return new ResourceLocation(HeraliszMammoth.MODID, p);
   }

   public HeroPropRenderer() {
      super(new GeoModel<HeroPropBE>() {
         @Override
         public void setCustomAnimations(HeroPropBE be, long id, software.bernie.geckolib.core.animation.AnimationState<HeroPropBE> st) {
            super.setCustomAnimations(be, id, st);
            if (be.kind() == fr.heralisz.mammoth.dungeon.HeroKind.BONE_TORCH) getBone("flame").ifPresent(b -> b.setHidden(true));
         }

         @Override
         public ResourceLocation getModelResource(HeroPropBE be) {
            return r("geo/block/" + be.kind().id + ".geo.json");
         }

         @Override
         public ResourceLocation getTextureResource(HeroPropBE be) {
            // D-054 : textures GeckoLib rangees dans textures/entity/prop (hors de l'atlas des blocs, qui ne prend que textures/block)
            return r("textures/entity/prop/" + be.kind().id + ".png");
         }

         @Override
         public ResourceLocation getAnimationResource(HeroPropBE be) {
            return r("animations/block/" + be.kind().id + ".animation.json");
         }
      });
      addRenderLayer(new AutoGlowingGeoLayer<>(this) {
         @Override
         public void render(PoseStack ps, HeroPropBE be, software.bernie.geckolib.cache.object.BakedGeoModel model, net.minecraft.client.renderer.RenderType rt,
                            net.minecraft.client.renderer.MultiBufferSource buf, com.mojang.blaze3d.vertex.VertexConsumer vc, float partial, int light,
                            int overlay) {
            if (be.kind() == fr.heralisz.mammoth.dungeon.HeroKind.BONE_TORCH) {   // la torche : flamme douce au lieu de la flamme en cubes
               model.getBone("flame").ifPresent(fb -> fb.setHidden(true));
               FireQuads.draw(ps, buf, 0F, 0.93F, -0.19F, 0.3F, 0.5F, false, be.getBlockPos().asLong(), partial);
            }
            if (be.kind().glow) super.render(ps, be, model, rt, buf, vc, partial, light, overlay);
         }
      });
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
   public boolean shouldRenderOffScreen(HeroPropBE be) {
      return true;
   }

   @Override
   public int getViewDistance() {
      return 64;
   }
}
