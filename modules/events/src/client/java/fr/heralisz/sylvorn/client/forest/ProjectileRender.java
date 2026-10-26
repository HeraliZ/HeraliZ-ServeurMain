package fr.heralisz.sylvorn.client.forest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.sylvorn.client.fvfx.VfxDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Outils communs aux rendus de projectiles des boss : un bloc entier, un rendu de base (atlas des blocs), un carré face caméra. */
public final class ProjectileRender {
   private ProjectileRender() {}

   /** Un bloc entier centré sur l'origine. */
   public static void cube(PoseStack ps, MultiBufferSource buf, BlockState st, int light) {
      ps.pushPose();
      ps.translate(-0.5, -0.5, -0.5);
      Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }

   /** Rendu de projectile fait de blocs : sa texture est l'atlas des blocs. */
   public abstract static class Base<T extends Entity> extends EntityRenderer<T> {
      protected Base(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      @Override
      public ResourceLocation getTextureLocation(T e) {
         return TextureAtlas.LOCATION_BLOCKS;
      }
   }

   /** Carré de côté 2s, tourné de rot degrés, dans le plan de la pose (face caméra si la pose l'est). */
   public static void quad(PoseStack ps, VertexConsumer vc, float s, float rot, int argb) {
      ps.pushPose();
      ps.mulPose(Axis.ZP.rotationDegrees(rot));
      Matrix4f m = ps.last().pose();
      VfxDraw.v(vc, m, -s, -s, 0, 0, 1, argb);
      VfxDraw.v(vc, m, s, -s, 0, 1, 1, argb);
      VfxDraw.v(vc, m, s, s, 0, 1, 0, argb);
      VfxDraw.v(vc, m, -s, s, 0, 0, 0, argb);
      ps.popPose();
   }
}
