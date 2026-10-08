package fr.heralisz.n07.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.particle.ModParticles;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Effets du Sujet N-07 : flaques radioactives, arcs qui sautent de joueur en joueur, sphère de la criticité. */
final class N07Fx {
   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszN07.MODID, "textures/vfx/" + n + ".png");
   }

   private static final ResourceLocation T_POOL = tex("pool"), T_CAUSTIC = tex("pool_caustic"), T_GLOW = tex("glow"), T_CORE = tex("beam_core"),
      T_NOVA = tex("nova"), T_FLASH = tex("flash"), T_GROUND = tex("ring_ground");

   private N07Fx() {}

   static void draw(Vfx.Packet p, float seed, float age, float k, float partial, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
      Matrix4f m = ps.last().pose();
      switch (p.type()) {
         case POOL -> pool(p, seed, age, k, m, buf);
         case ARC -> arc(p, seed, age, k, m, buf, cam);
         case MELTDOWN -> meltdown(p, age, k, m, buf, cam);
         default -> {
         }
      }
   }

   /** Flaque de liquide radioactif : nappe verte opaque, reflets qui ondulent, bulles qui crèvent. */
   private static void pool(Vfx.Packet p, float seed, float age, float k, Matrix4f m, MultiBufferSource buf) {
      float in = Mth.clamp(age / 8F, 0, 1), out = 1 - Mth.clamp((k - 0.8F) / 0.2F, 0, 1);
      float s = in * out;
      if (s <= 0.01F) return;
      double r = p.a() * (0.55 + 0.45 * in) * (1 + 0.03 * Mth.sin(age * 0.15F + seed));
      double y = p.y() + 0.03;
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_POOL)), m, p.x(), y, p.z(), r, seed * 6.28F, VfxDraw.argb(0xFFFFFF, s));
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_CAUSTIC)), m, p.x(), y + 0.01, p.z(), r * 0.8, seed + age * 0.004F, VfxDraw.argb(0xAAFF78, 0.35F * s));
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_CAUSTIC)), m, p.x(), y + 0.012, p.z(), r * 0.75, seed + 1.7F - age * 0.003F, VfxDraw.argb(0xAAFF78, 0.3F * s));
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isPaused() && mc.level != null && mc.level.random.nextFloat() < 0.04F * p.a()) {
         RandomSource rr = mc.level.random;
         double a = rr.nextDouble() * Mth.TWO_PI, d = Math.sqrt(rr.nextDouble()) * r * 0.85;
         mc.level.addParticle(ModParticles.RAD_EMBER.get(), p.x() + Math.cos(a) * d, y + 0.05, p.z() + Math.sin(a) * d, 0, 0.02, 0);
      }
   }

   /** Arc radioactif : éclair vert brisé de pos à pos + direction, qui se redessine tous les 2 ticks puis s'éteint. */
   private static void arc(Vfx.Packet p, float seed, float age, float k, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      Vec3 a = p.pos(), b = a.add(p.dir());
      float al = (1 - k) * (1 - k);
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_CORE));
      VertexConsumer gv = buf.getBuffer(VfxRenderTypes.glow(T_GLOW));
      for (int strand = 0; strand < 3; strand++) {
         RandomSource r = RandomSource.create((long) (seed * 1e4) + (long) (age / 2) * 31 + strand * 977L);
         int n = Math.max(4, (int) (a.distanceTo(b) / 1.2));
         Vec3 prev = a;
         Vec3 dir = b.subtract(a);
         Vec3 side = dir.cross(new Vec3(0, 1, 0));
         side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
         Vec3 up = dir.cross(side).normalize();
         for (int i = 1; i <= n; i++) {
            float u = i / (float) n;
            double j = i == n ? 0 : Math.sin(u * Math.PI) * (strand == 0 ? 0.6 : 1.1);
            Vec3 q = a.add(dir.scale(u)).add(side.scale((r.nextDouble() - 0.5) * 2 * j)).add(up.scale((r.nextDouble() - 0.5) * 2 * j));
            ribbon(vc, m, prev, q, cam, strand == 0 ? 0.16F : 0.08F, VfxDraw.argb(0xF0FFE0, al * (strand == 0 ? 1F : 0.6F)));
            ribbon(gv, m, prev, q, cam, strand == 0 ? 0.9F : 0.5F, VfxDraw.argb(p.color() & 0xFFFFFF, al * 0.5F));
            prev = q;
         }
      }
   }

   /** Criticité : sphère lumineuse qui enfle très vite jusqu'au rayon a puis se dissipe, anneau au sol, éclair. */
   private static void meltdown(Vfx.Packet p, float age, float k, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      float e = 1 - (float) Math.pow(1 - Mth.clamp(k * 1.6F, 0, 1), 3);
      double r = p.a() * e;
      float al = (1 - k) * (1 - k);
      int col = p.color() & 0xFFFFFF;
      VfxDraw.sphere(buf.getBuffer(VfxRenderTypes.glow(T_NOVA)), m, p.x(), p.y(), p.z(), r, 24, -age * 0.02F, VfxDraw.argb(col, al * 0.55F));
      VfxDraw.sphere(buf.getBuffer(VfxRenderTypes.glow(T_NOVA)), m, p.x(), p.y(), p.z(), r * 0.7, 20, age * 0.03F, VfxDraw.argb(0xE8FFC8, al * 0.35F));
      VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_GROUND)), m, p.x(), p.y() + 0.05, p.z(), Math.max(0, r - 4), r, 64, 0, VfxDraw.argb(col, al));
      float fl = Mth.clamp(1 - age / 8F, 0, 1);
      if (fl > 0) billboard(buf.getBuffer(VfxRenderTypes.glow(T_FLASH)), m, p.pos().add(0, 3, 0), cam, p.a() * 0.5F, 0, VfxDraw.argb(0xF0FFE0, fl));
   }

   private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float w, int argb) {
      Vec3 d = b.subtract(a);
      Vec3 side = d.cross(cam.subtract(a.add(b).scale(0.5)));
      if (side.lengthSqr() < 1e-6) return;
      side = side.normalize().scale(w / 2);
      VfxDraw.v(vc, m, a.x - side.x, a.y - side.y, a.z - side.z, 0, 0, argb);
      VfxDraw.v(vc, m, a.x + side.x, a.y + side.y, a.z + side.z, 1, 0, argb);
      VfxDraw.v(vc, m, b.x + side.x, b.y + side.y, b.z + side.z, 1, 1, argb);
      VfxDraw.v(vc, m, b.x - side.x, b.y - side.y, b.z - side.z, 0, 1, argb);
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
