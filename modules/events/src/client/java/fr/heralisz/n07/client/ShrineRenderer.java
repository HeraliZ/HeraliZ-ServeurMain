package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.dungeon.DungeonKind;
import fr.heralisz.n07.dungeon.ShrineBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Rendu des sanctuaires (terminal, vanne, brasero, monolithe, autel + cocon) : modèle GeckoLib par type de donjon, lueurs
 * (écrans, braises, runes), et l'écran du terminal N-07 selon l'état (insérer la carte / accès autorisé / refusé).
 */
public class ShrineRenderer extends GeoBlockRenderer<ShrineBlockEntity> {
   private static final int N = DungeonKind.values().length;
   private static final ResourceLocation[] GEO = new ResourceLocation[N], TEX = new ResourceLocation[N], ANIM = new ResourceLocation[N];

   static {
      for (DungeonKind k : DungeonKind.values()) {
         GEO[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "geo/block/" + k.model + ".geo.json");
         TEX[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "textures/entity/prop/" + k.model + ".png");
         ANIM[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "animations/block/" + k.model + ".animation.json");
      }
   }

   public ShrineRenderer() {
      super(new Model());
      addRenderLayer(new AutoGlowingGeoLayer<>(this));
   }

   @Override
   protected void rotateBlock(Direction facing, PoseStack ps) {
      // modèles tournés vers -Z (le nord) : la face avant regarde FACING
      float deg = switch (facing) {
         case SOUTH -> 180F;
         case WEST -> 90F;
         case EAST -> 270F;
         default -> 0F;
      };
      ps.mulPose(Axis.YP.rotationDegrees(deg));
   }

   @Override
   public boolean shouldRenderOffScreen(ShrineBlockEntity be) {
      // 1.1.0 : soumis a l occlusion des sections (ne se dessine plus a travers la roche, ex. depuis la carte au-dessus)
      return false;
   }

   @Override
   public int getViewDistance() {
      return 64;
   }

   static final class Model extends GeoModel<ShrineBlockEntity> {
      @Override
      public ResourceLocation getModelResource(ShrineBlockEntity be) {
         return GEO[be.kind.ordinal()];
      }

      @Override
      public ResourceLocation getTextureResource(ShrineBlockEntity be) {
         return TEX[be.kind.ordinal()];
      }

      @Override
      public ResourceLocation getAnimationResource(ShrineBlockEntity be) {
         return ANIM[be.kind.ordinal()];
      }

      @Override
      public void setCustomAnimations(ShrineBlockEntity be, long instanceId, AnimationState<ShrineBlockEntity> state) {
         super.setCustomAnimations(be, instanceId, state);
         if (be.kind != DungeonKind.N07 || be.getLevel() == null) return;
         long now = be.getLevel().getGameTime();
         float s = be.ritualTime(state.getPartialTick());
         boolean denied = now - be.denyTime() < 30 && (now / 4) % 2 == 0 || be.state() == ShrineBlockEntity.COOLDOWN;
         boolean ok = be.state() == ShrineBlockEntity.AWAKE || be.state() == ShrineBlockEntity.RITUAL && s >= be.kind.grant;
         screen("screen_idle", !ok && !denied);
         screen("screen_ok", ok);
         screen("screen_denied", denied && !ok);
      }

      private void screen(String name, boolean on) {
         CoreGeoBone b = getAnimationProcessor().getBone(name);
         if (b != null) b.setHidden(!on);
      }
   }
}
