package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.entity.GuardianSpear;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** La lance en vol : le modèle de la lance plantée, pointe orientée selon la trajectoire. */
public class SpearRenderer extends GeoEntityRenderer<GuardianSpear> {
   private static ResourceLocation r(String p) {
      return new ResourceLocation(HeraliszMammoth.MODID, p);
   }

   public SpearRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new GeoModel<>() {
         @Override
         public ResourceLocation getModelResource(GuardianSpear e) {
            return r("geo/block/hero_planted_spear.geo.json");
         }

         @Override
         public ResourceLocation getTextureResource(GuardianSpear e) {
            // D-054 : textures GeckoLib rangees dans textures/entity/prop (hors de l'atlas des blocs, qui ne prend que textures/block)
            return r("textures/entity/prop/hero_planted_spear.png");
         }

         @Override
         public ResourceLocation getAnimationResource(GuardianSpear e) {
            return r("animations/entity/draugr.animation.json");
         }
      });
      this.shadowRadius = 0;
   }

   @Override
   public void render(GuardianSpear e, float yaw, float partial, PoseStack pose, MultiBufferSource buf, int light) {
      Vec3 v = e.getDeltaMovement();
      double h = Math.sqrt(v.x * v.x + v.z * v.z);
      float heading = (float) (Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG);
      float pitch = (float) (Mth.atan2(-v.y, h) * Mth.RAD_TO_DEG);
      pose.pushPose();
      // pointe du modèle (vers le bas) alignée sur la vitesse ; on annule la rotation de 180° appliquée ensuite par GeckoLib
      pose.mulPose(Axis.YP.rotationDegrees(-heading));
      pose.mulPose(Axis.XP.rotationDegrees(pitch));
      pose.mulPose(Axis.XP.rotationDegrees(-90));
      pose.translate(0, -1.2, 0);
      pose.mulPose(Axis.YP.rotationDegrees(-180));
      super.render(e, yaw, partial, pose, buf, light);
      pose.popPose();
   }
}
