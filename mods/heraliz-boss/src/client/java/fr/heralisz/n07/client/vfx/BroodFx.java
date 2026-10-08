package fr.heralisz.n07.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** Effets de la Mère des Ruines : toiles gluantes au sol, fils de soie (visée, harpon, descente), couvée qui tisse le cocon. */
final class BroodFx {
   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszN07.MODID, "textures/vfx/" + n + ".png");
   }

   private static final ResourceLocation T_WEB = tex("web"), T_SILK = tex("silk"), T_SPIDER = tex("spiderling");
   private static final Map<Integer, Vec3> LAST = new HashMap<>();

   private BroodFx() {}

   static void draw(Vfx.Packet p, float seed, float age, float k, float partial, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
      Matrix4f m = ps.last().pose();
      switch (p.type()) {
         case WEB -> {
            float in = Mth.clamp(age / 5F, 0, 1), out = 1 - Mth.clamp((k - 0.8F) / 0.2F, 0, 1);
            float grow = 0.4F + 0.6F * (1 - (1 - in) * (1 - in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_WEB)), m, p.x(), p.y() + 0.05, p.z(), p.a() * grow, seed, VfxDraw.argb(0xF2F0E8, 0.85F * in * out));
         }
         case SILK -> silk(p, age, k, partial, m, buf, cam);
         case SWARM -> swarm(p, seed, age, k, m, buf);
         default -> {
         }
      }
   }

   /** Ruban face caméra de a à b. */
   private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, double w, float v0, float v1, int argb) {
      Vec3 d = b.subtract(a);
      Vec3 side = d.cross(cam.subtract(a));
      if (side.lengthSqr() < 1e-8) return;
      side = side.normalize().scale(w / 2);
      VfxDraw.v(vc, m, a.x - side.x, a.y - side.y, a.z - side.z, 0, v0, argb);
      VfxDraw.v(vc, m, a.x + side.x, a.y + side.y, a.z + side.z, 1, v0, argb);
      VfxDraw.v(vc, m, b.x + side.x, b.y + side.y, b.z + side.z, 1, v1, argb);
      VfxDraw.v(vc, m, b.x - side.x, b.y - side.y, b.z - side.z, 0, v1, argb);
   }

   private static void silk(Vfx.Packet p, float age, float k, float partial, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      Minecraft mc = Minecraft.getInstance();
      int mode = (int) p.b();
      Vec3 from = p.pos(), to;
      Entity e = p.extra() != 0 ? mc.level.getEntity(p.extra()) : null;
      if (mode == 3) {
         // victime emmaillotée : spirales de soie serrées autour d'elle
         if (e == null) return;
         Vec3 base = e.getPosition(partial);
         float grow = Mth.clamp(age / 6F, 0, 1), out = 1 - Mth.clamp((k - 0.85F) / 0.15F, 0, 1);
         double h = e.getBbHeight() * grow, r = e.getBbWidth() * 0.75;
         VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glass(T_SILK));
         for (int s = 0; s < 3; s++) {
            Vec3 prev = null;
            for (int i = 0; i <= 24; i++) {
               double u = i / 24.0, a = u * Mth.TWO_PI * 3.2 + s * 2.1 + age * 0.02;
               Vec3 q = base.add(Math.cos(a) * r * (1 - 0.15 * Math.sin(u * Math.PI)), u * h, Math.sin(a) * r);
               if (prev != null) ribbon(vc, m, prev, q, cam, 0.14, 0, 1, VfxDraw.argb(0xF4F2EA, 0.95F * out));
               prev = q;
            }
         }
         return;
      }
      if (e != null) {
         Vec3 ep = e.getPosition(partial);
         if (mode == 2) {
            // la Mère descend au bout de son fil (même courbe que l'animation « spawn »)
            float u = Mth.clamp((age + partial) / 49F, 0, 1);
            double h = 15.4 + (5.7 - 15.4) * (1 - (1 - u) * (1 - u));
            to = ep.add(0, h + 3.2, 0);
         } else {
            to = ep.add(0, e.getBbHeight() * 0.6, 0);
         }
         LAST.put(p.extra(), to);
      } else if (p.extra() != 0) {
         to = LAST.getOrDefault(p.extra(), from);
      } else {
         to = from.add(p.dx(), p.dy(), p.dz());
      }
      if (k >= 0.99F) LAST.remove(p.extra());
      if (mode == 0) {
         float pulse = 0.5F + 0.5F * Mth.sin(age * 1.2F);
         ribbon(buf.getBuffer(VfxRenderTypes.glow(T_SILK)), m, from, to, cam, 0.08, 0, 1, VfxDraw.argb(0xFF3A2A, 0.5F + 0.4F * pulse));
         return;
      }
      float out = 1 - Mth.clamp((k - 0.85F) / 0.15F, 0, 1);
      // léger ventre du fil (tendu en mode harpon)
      double sag = mode == 2 ? 0 : 0.25 * from.distanceTo(to) * 0.05;
      int n = 10;
      Vec3 prev = from;
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glass(T_SILK));
      for (int i = 1; i <= n; i++) {
         double u = i / (double) n;
         Vec3 q = from.add(to.subtract(from).scale(u)).add(0, -sag * Math.sin(Math.PI * u), 0);
         ribbon(vc, m, prev, q, cam, mode == 2 ? 0.14 : 0.11, (float) ((i - 1) / (double) n), (float) u, VfxDraw.argb(0xF4F2EA, 0.9F * out));
         prev = q;
      }
   }

   /** Des dizaines d'araignées accourent en spirale vers le corps et grimpent dessus en tissant. */
   private static void swarm(Vfx.Packet p, float seed, float age, float k, Matrix4f m, MultiBufferSource buf) {
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.decal(T_SPIDER));
      RandomSource rng = RandomSource.create((long) (seed * 7919));
      float fade = 1 - Mth.clamp((k - 0.88F) / 0.12F, 0, 1);
      int n = 70;
      for (int i = 0; i < n; i++) {
         double a0 = rng.nextDouble() * Mth.TWO_PI, r0 = p.a() * (0.35 + rng.nextDouble() * 0.65), sp = 0.08 + rng.nextDouble() * 0.1;
         double w = (rng.nextBoolean() ? 1 : -1) * (0.03 + rng.nextDouble() * 0.05);
         float delay = rng.nextFloat() * 25;
         float t = age - delay;
         if (t < 0) continue;
         double r = Math.max(2.0, r0 - sp * t);
         double arrive = (r0 - 2.0) / sp;
         double ang = a0 + w * t;
         double climb = t > arrive ? Math.min(1.7, (t - arrive) * 0.03) : 0;
         double rr = r - climb * 0.35;
         double x = p.x() + Math.cos(ang) * rr, z = p.z() + Math.sin(ang) * rr;
         // orientation : le long de la trajectoire
         double dx = -Math.sin(ang) * w * rr - Math.cos(ang) * (t < arrive ? sp : 0), dz = Math.cos(ang) * w * rr - Math.sin(ang) * (t < arrive ? sp : 0);
         float rot = (float) Math.atan2(dx, -dz);
         float s = 0.16F + (i % 5) * 0.02F;
         VfxDraw.disc(vc, m, x, p.y() + 0.06 + climb, z, s, rot, VfxDraw.argb(0xFFFFFF, fade));
      }
   }
}
