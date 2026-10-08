package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.HornAltarBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** L'autel du Cor : pierre runique, crâne de mammouth sur sa perche ; le cor se pose, les runes et les orbites s'allument. */
public class AltarRenderer extends GeoBlockRenderer<HornAltarBlockEntity> {
   private static final ResourceLocation GEO = new ResourceLocation(HeraliszMammoth.MODID, "geo/block/shrine_mammoth.geo.json");
   // D-054 : textures GeckoLib rangees dans textures/entity/prop (hors de l'atlas des blocs, qui ne prend que textures/block)
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszMammoth.MODID, "textures/entity/prop/shrine_mammoth.png");
   private static final ResourceLocation ANIM = new ResourceLocation(HeraliszMammoth.MODID, "animations/block/shrine_mammoth.animation.json");

   public AltarRenderer() {
      super(new GeoModel<HornAltarBlockEntity>() {
         @Override
         public ResourceLocation getModelResource(HornAltarBlockEntity be) {
            return GEO;
         }

         @Override
         public ResourceLocation getTextureResource(HornAltarBlockEntity be) {
            return TEX;
         }

         @Override
         public ResourceLocation getAnimationResource(HornAltarBlockEntity be) {
            return ANIM;
         }
      });
      addRenderLayer(new AutoGlowingGeoLayer<>(this));
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
   public boolean shouldRenderOffScreen(HornAltarBlockEntity be) {
      return true;
   }

   @Override
   public int getViewDistance() {
      return 96;
   }
}
