package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.LitPropBE;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Totems et braseros : éteints, pas de flamme ni de lueur ; allumés, le feu d'aurore danse et les runes brûlent. */
public class LitPropRenderer extends GeoBlockRenderer<LitPropBE> {
   private static ResourceLocation r(String p) {
      return new ResourceLocation(HeraliszMammoth.MODID, p);
   }

   public LitPropRenderer() {
      super(new GeoModel<LitPropBE>() {
         @Override
         public ResourceLocation getModelResource(LitPropBE be) {
            return r("geo/block/" + be.heroId() + ".geo.json");
         }

         @Override
         public ResourceLocation getTextureResource(LitPropBE be) {
            // D-054 : textures GeckoLib rangees dans textures/entity/prop (hors de l'atlas des blocs, qui ne prend que textures/block)
            return r("textures/entity/prop/" + be.heroId() + ".png");
         }

         @Override
         public ResourceLocation getAnimationResource(LitPropBE be) {
            return r("animations/block/" + be.heroId() + ".animation.json");
         }

         @Override
         public void setCustomAnimations(LitPropBE be, long id, AnimationState<LitPropBE> st) {
            super.setCustomAnimations(be, id, st);
            // brasero : le feu brûle toujours (rouge au repos, bleu quand Ymirak combat) ; réceptacle : le cœur n'est là qu'allumé
            // la flamme / le cœur ne se dessinent que dans la passe lumineuse (couleur choisie là)
            getBone("flame").ifPresent(b -> b.setHidden(true));
         }
      });
      addRenderLayer(new software.bernie.geckolib.renderer.layer.GeoRenderLayer<>(this) {
         @Override
         public void render(PoseStack ps, LitPropBE be, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, float partial,
                            int light, int overlay) {
            String id = be.heroId();
            boolean brazier = id.equals("hero_tribe_brazier"), socket = id.equals("hero_heart_socket"), maw = id.equals("hero_soul_maw");
            if (brazier) {                                                  // vraie flamme : rouge au repos, bleue au combat
               FireQuads.draw(ps, buf, 0F, 0.78F, 0F, 0.62F, 1.0F, be.lit(), be.getBlockPos().asLong(), partial);
               if (!be.lit()) return;                                     // au repos, rien d'autre ne luit
            }
            if (!be.lit() && !brazier) return;
            float r = 1F, g = 1F, b = 1F;
            if (socket) {                                                   // cœur brûlant
               g = 0.38F;
               b = 0.22F;
            }
            if (maw) {                                                      // lueur spectrale bleue
               r = 0.55F;
               g = 0.85F;
            }
            RenderType glow = RenderType.eyes(new ResourceLocation(HeraliszMammoth.MODID, "textures/entity/prop/" + id + "_glowmask.png"));
            if (!brazier) model.getBone("flame").ifPresent(fb -> fb.setHidden(false));
            getRenderer().reRender(model, ps, buf, be, glow, buf.getBuffer(glow), partial, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
               r, g, b, 1F);
            model.getBone("flame").ifPresent(fb -> fb.setHidden(true));
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
   public int getViewDistance() {
      return 128;
   }
}
