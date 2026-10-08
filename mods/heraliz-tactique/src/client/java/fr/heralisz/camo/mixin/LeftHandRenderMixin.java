package fr.heralisz.camo.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.functional.LeftHandRender;
import fr.heralisz.camo.client.CamoFx;
import fr.heralisz.camo.client.SkinReveal;
import fr.heralisz.camo.fx.FxFrame;
import fr.heralisz.camo.fx.GunShape;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apparition d'un skin (SkinReveal) : la main gauche quitte sa prise et glisse au-dessus de l'arme, de la crosse vers le
 * canon, juste derrière la tête de la créature. On déplace la main là où TaCZ allait la dessiner (repère de l'arme de
 * l'image précédente, CamoFx), puis elle revient à sa place.
 */
@Mixin(value = LeftHandRender.class, remap = false)
public abstract class LeftHandRenderMixin {
   @Inject(method = "render", at = @At("HEAD"))
   private void heralisz$revealSweep(PoseStack poseStack, VertexConsumer buffer, ItemDisplayContext ctx, int light, int overlay, CallbackInfo ci) {
      // inspection d'un skin : la position de la main gauche (au rendu) sert aux effets (feu sur la main, tentacule qui l'agrippe).
      // TaCZ tourne ce repère de 180° puis dessine le bras depuis l'épaule (pivot (5, 2, 0), cube 4x12) : la paume est au
      // bout du bras, en (-6, -9.6, 0) pixels ici (tools/tacz_rig.py, arm_point(0.8)).
      if (ctx.firstPerson() && fr.heralisz.camo.client.SkinInspect.showcase()) {
         Vector3f hp = poseStack.last().pose().transformPosition(new Vector3f(-6F / 16F, -9.6F / 16F, 0));
         fr.heralisz.camo.client.SkinInspect.handPos = new fr.heralisz.camo.fx.V(hp.x, hp.y, hp.z);
         Vector3f wp = poseStack.last().pose().transformPosition(new Vector3f(-6F / 16F, -7.5F / 16F, 0));   // poignet (bout de la manche)
         fr.heralisz.camo.client.SkinInspect.wristPos = new fr.heralisz.camo.fx.V(wp.x, wp.y, wp.z);
         // et sa place à l'écran (l'encre du kraken part avec le revers de main)
         Vector4f clip = RenderSystem.getProjectionMatrix().transform(new Vector4f(hp.x, hp.y, hp.z, 1));
         fr.heralisz.camo.client.SkinInspect.handScreenX = clip.w() > 1e-4F ? (clip.x() / clip.w() + 1) * 0.5F : -1;
      }
      float w = SkinReveal.handWeight();
      FxFrame f = CamoFx.lastFpFrame;
      GunShape s = CamoFx.lastFpShape;
      if (w <= 0.001F || f == null || s == null || !ctx.firstPerson()) return;
      // la main glisse le long de l'arme, à sa hauteur de prise (elle « passe » sur le garde-main), du boîtier jusqu'à la
      // bouche du canon, juste derrière le front de matérialisation, puis revient à sa place
      float gx = s.xAt(0.42F + 0.5F * SkinReveal.handPos());
      Matrix4f m = poseStack.last().pose();
      Vector3f here = m.transformPosition(new Vector3f(0, 0, 0));
      float along = (here.x - f.o.x) * f.x.x + (here.y - f.o.y) * f.x.y + (here.z - f.o.z) * f.x.z;
      float move = (gx * f.s - along) * w;
      Vector3f d = new Vector3f(f.x.x * move, f.x.y * move, f.x.z * move);
      Matrix3f lin = new Matrix3f();
      m.get3x3(lin);
      if (Math.abs(lin.determinant()) < 1e-12F) return;
      lin.invert().transform(d);
      poseStack.translate(d.x, d.y, d.z);
   }
}
