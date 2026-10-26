package fr.heralisz.mere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.entity.RuinMother;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Les bandes de soie que la couvée enroule autour de la Mère ET du pilier (on la voit tourner entre les deux) : des rubans de soie
 * qui ceinturent le fût et viennent bomber par-dessus le cocon, posés un à un de bas en haut et de haut en bas (progress 0 → 1),
 * et quelques haubans qui retiennent le cocon au pilier au-dessus et en dessous. Tout en coordonnées monde, relatives à origin.
 */
public final class PillarWrap {
   private static final ResourceLocation SILK = new ResourceLocation(HeraliszMere.MODID, "textures/entity/cocoon_silk.png");
   public static final int BANDS = 46;
   private static final int SAMPLES = 40;

   private PillarWrap() {}

   /**
    * @param axis     axe du pilier (x, z) et hauteur du centre du cocon (y)
    * @param toPillar direction horizontale du cocon vers le pilier
    */
   public static void draw(PoseStack ps, MultiBufferSource buf, Vec3 origin, Vec3 axis, Vec3 toPillar, float progress, float time, int light) {
      if (progress <= 0.001F) return;
      Matrix4f m = ps.last().pose();
      Matrix3f nm = ps.last().normal();
      VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentCull(SILK));
      Vec3 out = toPillar.scale(-1);                                           // du pilier vers le cocon
      Vec3 side = new Vec3(-out.z, 0, out.x);
      float done = progress * BANDS;
      for (int i = 0; i < BANDS && i < done; i++) {
         float frac = Math.min(1F, done - i);
         // hauteur de la bande : réparties sur toute la longueur du cocon (quasi-aléatoire), un peu inclinées
         float hy = (float) (RuinMother.COCOON_HALF * 0.95 * (2 * ((i * 0.618034) % 1.0) - 1));
         float tilt = 0.45F + 0.35F * Mth.sin(i * 1.7F);
         float ph = i * 1.31F;
         float a0 = i * 2.4F;                                                  // angle de départ (là où l'araignée a commencé ce tour)
         int n = Math.max(2, Math.round(SAMPLES * frac));
         Vec3 prevP = null, prevN = null;
         for (int k = 0; k <= n; k++) {
            float a = a0 + Mth.TWO_PI * (k / (float) SAMPLES);
            float h = hy + tilt * Mth.sin(a + ph);
            float r = loopR(a, h);
            Vec3 d = out.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
            Vec3 p = axis.add(d.scale(r)).add(0, RuinMother.COCOON_UP + h, 0).subtract(origin);
            if (prevP != null) ribbon(vc, m, nm, prevP, p, prevN, d, 0.09F, light, (k - 1) / (float) SAMPLES, k / (float) SAMPLES);
            prevP = p;
            prevN = d;
         }
      }
      // haubans : quelques fils tendus du haut et du bas du cocon jusqu'au fût
      float an = Mth.clamp((progress - 0.35F) / 0.4F, 0, 1);
      if (an > 0) {
         for (int j = 0; j < 6; j++) {
            float s = j < 3 ? 1 : -1;
            float ang = (j % 3 - 1) * 0.55F;
            Vec3 d = out.scale(Math.cos(ang)).add(side.scale(Math.sin(ang)));
            Vec3 a = axis.add(out.scale(RuinMother.PILLAR_R + RuinMother.COCOON_OUT)).add(side.scale(ang * 1.6)).add(0, RuinMother.COCOON_UP + s * RuinMother.COCOON_HALF * 0.85, 0);
            Vec3 b = axis.add(d.scale(RuinMother.PILLAR_R + 0.05)).add(0, RuinMother.COCOON_UP + s * (RuinMother.COCOON_HALF + 1.6 + j % 3 * 0.6), 0);
            Vec3 bb = a.lerp(b, an);
            ribbon(vc, m, nm, a.subtract(origin), bb.subtract(origin), d, d, 0.07F, light, 0, 1);
         }
      }
   }

   /** Rayon de la bande autour de l'axe du pilier dans la direction a (0 = vers le cocon), à la hauteur h (relative au cocon). */
   static float loopR(float a, float h) {
      return RuinMother.loopR(a, h);
   }

   private static void ribbon(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 a, Vec3 b, Vec3 na, Vec3 nb, float w, int light, float u0, float u1) {
      Vec3 up = new Vec3(0, w, 0);
      vert(vc, m, nm, a.subtract(up), na, u0 * 6, 0, light);
      vert(vc, m, nm, b.subtract(up), nb, u1 * 6, 0, light);
      vert(vc, m, nm, b.add(up), nb, u1 * 6, 0.25F, light);
      vert(vc, m, nm, a.add(up), na, u0 * 6, 0.25F, light);
      // (et l'envers : la bande se voit aussi de l'intérieur, entre le cocon et le fût)
      vert(vc, m, nm, a.add(up), na.scale(-1), u0 * 6, 0.25F, light);
      vert(vc, m, nm, b.add(up), nb.scale(-1), u1 * 6, 0.25F, light);
      vert(vc, m, nm, b.subtract(up), nb.scale(-1), u1 * 6, 0, light);
      vert(vc, m, nm, a.subtract(up), na.scale(-1), u0 * 6, 0, light);
   }

   private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 p, Vec3 n, float u, float v, int light) {
      vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(1F, 1F, 1F, 0.92F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(nm, (float) n.x, (float) n.y, (float) n.z).endVertex();
   }
}
