package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.entity.Draugr;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Rendu des Revenants et des Gardiens : yeux de givre qui luisent ; le Gardien est deux fois plus grand et porte un cœur
 * incandescent dans la poitrine, puis dans la main quand il se l'arrache (scène du cœur), puis plus rien.
 */
public class DraugrRenderer extends GeoEntityRenderer<Draugr> {
   private static final String[][] BURN_ORDER = {
      {"foot_r", "6"}, {"foot_l", "10"}, {"shin_r", "20"}, {"shin_l", "26"}, {"thigh_r", "36"}, {"thigh_l", "40"}, {"body", "48"},
      {"torso", "56"}, {"spear", "60"}, {"cloak", "64"}, {"hand_l", "68"}, {"forearm_l", "72"}, {"arm_l", "76"}, {"chest", "80"},
      {"hand_r", "83"}, {"forearm_r", "86"}, {"arm_r", "89"}, {"heart_chest", "90"}, {"neck", "92"}, {"head", "97"}};

   public DraugrRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(HeraliszMammoth.MODID, "draugr")) {
         @Override
         public void setCustomAnimations(Draugr e, long id, AnimationState<Draugr> st) {
            super.setCustomAnimations(e, id, st);
            boolean g = e.guardian();
            int h = e.heartStage();
            // combustion : chaque os disparaît quand la braise monte jusqu'à lui (pieds d'abord, tête en dernier)
            int burn = e.burn();
            for (String[] pair : BURN_ORDER) {
               boolean gone = burn >= Integer.parseInt(pair[1]);
               boolean rule = switch (pair[0]) {
                  case "heart_chest" -> !g || h != 0;
                  case "spear" -> e.spearPlanted();
                  default -> false;
               };
               getBone(pair[0]).ifPresent(b -> {
                  b.setHidden(gone || rule);
                  b.setChildrenHidden(false);
               });
            }
            getBone("heart_hand").ifPresent(b -> b.setHidden(!g || h != 1 || burn >= 86));
         }
      });
      addRenderLayer(new AutoGlowingGeoLayer<>(this));
      this.shadowRadius = 0.6F;
   }

   @Override
   public void render(Draugr e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      float s = e.guardian() ? 2.0F : 1.0F;
      this.scaleWidth = s;
      this.scaleHeight = s;
      this.shadowRadius = 0.6F * s;
      super.render(e, yaw, partial, ps, buf, light);
   }

   @Override
   protected float getDeathMaxRotation(Draugr e) {
      return 0F;
   }
}
