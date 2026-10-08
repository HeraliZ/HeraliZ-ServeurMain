package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.entity.Mammoth;
import fr.heralisz.mammoth.entity.SpectralHand;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Mains spectrales : brume translucide, os lumineux ; elles s'enfoncent dans le sol avec Ymirak (même descente). */
public class SpectralHandRenderer extends GeoEntityRenderer<SpectralHand> {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszMammoth.MODID, "textures/entity/spectral_hand.png");

   public SpectralHandRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new GeoModel<>() {
         @Override
         public ResourceLocation getModelResource(SpectralHand e) {
            return new ResourceLocation(HeraliszMammoth.MODID, "geo/entity/spectral_hand.geo.json");
         }

         @Override
         public ResourceLocation getTextureResource(SpectralHand e) {
            return TEX;
         }

         @Override
         public ResourceLocation getAnimationResource(SpectralHand e) {
            return new ResourceLocation(HeraliszMammoth.MODID, "animations/entity/spectral_hand.animation.json");
         }

         @Override
         public RenderType getRenderType(SpectralHand e, ResourceLocation tex) {
            return RenderType.entityTranslucent(tex);
         }
      });
      addRenderLayer(new AutoGlowingGeoLayer<>(this));
      this.shadowRadius = 0;
      this.withScale(2.6F);                                           // à la taille d'Ymirak : la main enserre son buste
   }

   @Override
   public void render(SpectralHand e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      float sink = 0;
      if (e.level().getEntity(e.master()) instanceof Mammoth m && m.isDeadOrDying()) sink = Mammoth.sink(m.deathTime + partial);
      ps.pushPose();
      ps.translate(0, -sink, 0);
      // la paume vers la patte ; on annule la rotation de 180° qu'applique ensuite GeckoLib (entité sans corps)
      ps.mulPose(Axis.YP.rotationDegrees(-e.face()));
      ps.mulPose(Axis.YP.rotationDegrees(-180));
      super.render(e, yaw, partial, ps, buf, 0xF000F0);
      ps.popPose();
   }
}
