package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.mammoth.HeraliszMammoth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Flamme douce et animée des braseros et des torches : trois plans croisés (comme le feu vanilla) portant une planche de
 * huit images de flamme, en additif (elle éclaire, ne cache rien). Rouge-orangé, ou bleu d'aurore.
 */
public final class FireQuads {
   private static final ResourceLocation RED = new ResourceLocation(HeraliszMammoth.MODID, "textures/entity/fire_red.png");
   private static final ResourceLocation BLUE = new ResourceLocation(HeraliszMammoth.MODID, "textures/entity/fire_blue.png");

   private FireQuads() {}

   /**
    * Flamme posée en (x, y, z) (repère local de la case), largeur w, hauteur h : deux plans toujours tournés vers la caméra
    * (axe vertical), une enveloppe et un cœur plus étroit décalé dans l'animation, pour une flamme qui a du volume.
    */
   public static void draw(PoseStack ps, MultiBufferSource buf, float x, float y, float z, float w, float h, boolean blue, long seed, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      float time = mc.level.getGameTime() + partial;
      VertexConsumer vc = buf.getBuffer(RenderType.eyes(blue ? BLUE : RED));
      // repère centré sur la caméra : la verticale du monde y est la normale transformée (la case ne tourne qu'autour de Y),
      // la caméra est à l'origine -> flamme debout, toujours face à nous, quelle que soit la direction regardée
      Vector3f c = ps.last().pose().transformPosition(new Vector3f(x, y, z));
      Vector3f up = ps.last().normal().transform(new Vector3f(0, 1, 0)).normalize();
      Vector3f right = new Vector3f(up).cross(new Vector3f(c).negate());
      if (right.lengthSquared() < 1e-6F) right.set(1, 0, 0);
      right.normalize();
      float flick = 1F + 0.08F * Mth.sin(time * 0.9F + seed) + 0.05F * Mth.sin(time * 2.3F + seed * 3);
      layer(vc, c, right, up, w, h * flick, (int) ((time / 2.2F + (seed & 7)) % 8), 0F);
      layer(vc, c, right, up, w * 0.55F, h * 0.72F * flick, (int) ((time / 1.7F + (seed & 7) + 3) % 8), 0.01F);
   }

   private static void layer(VertexConsumer vc, Vector3f c, Vector3f r, Vector3f up, float w, float h, int frame, float unused) {
      float u0 = frame / 8F, u1 = (frame + 1) / 8F, hw = w / 2F;
      Vector3f a = new Vector3f(c).sub(new Vector3f(r).mul(hw)), b = new Vector3f(c).add(new Vector3f(r).mul(hw));
      Vector3f bt = new Vector3f(b).add(new Vector3f(up).mul(h)), at = new Vector3f(a).add(new Vector3f(up).mul(h));
      // les deux faces (le type de rendu élimine les faces arrière)
      v(vc, a.x, a.y, a.z, u0, 1);
      v(vc, b.x, b.y, b.z, u1, 1);
      v(vc, bt.x, bt.y, bt.z, u1, 0);
      v(vc, at.x, at.y, at.z, u0, 0);
      v(vc, at.x, at.y, at.z, u0, 0);
      v(vc, bt.x, bt.y, bt.z, u1, 0);
      v(vc, b.x, b.y, b.z, u1, 1);
      v(vc, a.x, a.y, a.z, u0, 1);
   }

   private static void v(VertexConsumer vc, float x, float y, float z, float u, float vv) {
      vc.vertex(x, y, z).color(255, 255, 255, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
         .normal(0, 1, 0).endVertex();
   }
}
