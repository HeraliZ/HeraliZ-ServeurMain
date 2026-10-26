package fr.heralisz.plague.client.sick;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.client.fvfx.VfxDraw;
import fr.heralisz.plague.client.fvfx.VfxRenderTypes;
import fr.heralisz.plague.fvfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Effets de Vulkhar : jets de flammes (souffle) et météores en fusion avec leur traînée. */
public final class FireFx {
   private static final ResourceLocation T_FLAME = new ResourceLocation(HeraliszPlague.MODID, "textures/fvfx/flame.png");

   private FireFx() {}

   public static void draw(Vfx.Packet p, float seed, float age, float k, float partial, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
      Matrix4f m = ps.last().pose();
      if (p.type() == Vfx.Type.FLAME) flame(p, seed, age, m, buf, cam);
      else meteor(p, age, k, ps, buf, cam);
   }

   private static int fireColor(float u, float a) {
      // blanc-jaune au ras de la gueule, orange, puis rouge sombre au bout du jet
      int r = 255, g = (int) Mth.lerp(Math.min(1, u * 1.6F), 240, 70), b = (int) Mth.lerp(Math.min(1, u * 3F), 170, 10);
      return VfxDraw.argb((r << 16) | (g << 8) | b, a);
   }

   private static void flame(Vfx.Packet p, float seed, float age, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      Vec3 d = p.dir();
      if (d.lengthSqr() < 1e-6) return;
      d = d.normalize();
      Vec3 s1 = d.cross(new Vec3(0, 1, 0));
      if (s1.lengthSqr() < 1e-4) s1 = new Vec3(1, 0, 0);
      s1 = s1.normalize();
      Vec3 s2 = s1.cross(d).normalize();
      double spread = Math.tan(Math.toRadians(p.b() / 2));
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_FLAME));
      RandomSource rng = RandomSource.create((long) (seed * 1000));
      int n = 28;
      for (int i = 0; i < n; i++) {
         float u = (i / (float) n + age / Math.max(1, p.duration())) % 1F;
         double dist = u * p.a();
         double ang = rng.nextDouble() * Mth.TWO_PI, rr = Math.sqrt(rng.nextDouble()) * spread * dist;
         Vec3 c = p.pos().add(d.scale(dist)).add(s1.scale(Math.cos(ang) * rr)).add(s2.scale(Math.sin(ang) * rr)).add(0, u * u * 1.2, 0);
         float size = (float) (0.5 + dist * 0.16);
         float a = (1 - u) * Math.min(1, u * 8) * 0.85F;
         billboard(vc, m, c, cam, size, rng.nextFloat() * 6F + age * 0.2F, fireColor(u, a));
      }
   }

   private static void meteor(Vfx.Packet p, float age, float k, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
      float e = (float) Math.pow(k, 1.6);
      Vec3 from = p.pos(), to = from.add(p.dir());
      Vec3 at = from.add(p.dir().scale(e));
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_FLAME));
      for (int j = 0; j < 14; j++) {
         float kk = (float) Math.pow(Math.max(0, k - j * 0.018F), 1.6);
         Vec3 q = from.add(p.dir().scale(kk));
         float a = (1 - j / 14F) * 0.8F;
         billboard(vc, ps.last().pose(), q, cam, p.a() * (1.3F - j * 0.05F), j * 0.7F + age * 0.3F, fireColor(j / 14F, a));
      }
      Minecraft mc = Minecraft.getInstance();
      ps.pushPose();
      ps.translate(at.x, at.y, at.z);
      ps.mulPose(new Quaternionf().rotationXYZ(age * 0.3F, age * 0.2F, age * 0.25F));
      float s = p.a() * 0.9F;
      ps.scale(s, s, s);
      ps.translate(-0.5, -0.5, -0.5);
      mc.getBlockRenderer().renderSingleBlock(Blocks.MAGMA_BLOCK.defaultBlockState(), ps, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }

   private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 cam, float s, float rot, int argb) {
      Vec3 f = cam.subtract(c).normalize();
      Vec3 up = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
      Vec3 r = up.cross(f).normalize();
      Vec3 u = f.cross(r).normalize();
      Vec3 r2 = r.scale(Math.cos(rot)).add(u.scale(Math.sin(rot))).scale(s);
      Vec3 u2 = u.scale(Math.cos(rot)).subtract(r.scale(Math.sin(rot))).scale(s);
      VfxDraw.v(vc, m, c.x - r2.x - u2.x, c.y - r2.y - u2.y, c.z - r2.z - u2.z, 0, 1, argb);
      VfxDraw.v(vc, m, c.x + r2.x - u2.x, c.y + r2.y - u2.y, c.z + r2.z - u2.z, 1, 1, argb);
      VfxDraw.v(vc, m, c.x + r2.x + u2.x, c.y + r2.y + u2.y, c.z + r2.z + u2.z, 1, 0, argb);
      VfxDraw.v(vc, m, c.x - r2.x + u2.x, c.y - r2.y + u2.y, c.z - r2.z + u2.z, 0, 0, argb);
   }
}
