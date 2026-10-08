package fr.heralisz.mere.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.client.vfx.VfxDraw;
import fr.heralisz.mere.client.vfx.VfxRenderTypes;
import fr.heralisz.mere.entity.RuinMother;
import fr.heralisz.mere.vfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** Effets de la Mère des Ruines : toiles gluantes au sol, fils de soie (visée, harpon, descente, hissage, victime emmaillotée). */
public final class BroodFx {
   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszMere.MODID, "textures/vfx/" + n + ".png");
   }

   private static final ResourceLocation T_WEB = tex("web"), T_SILK = tex("silk");
   /** Fil opaque (texture de soie du cocon) : il ne disparaît plus derrière les décors translucides. */
   private static final ResourceLocation T_THREAD = new ResourceLocation(fr.heralisz.mere.HeraliszMere.MODID, "textures/entity/cocoon_silk.png");
   private static final Map<Integer, Vec3> LAST = new HashMap<>();

   private BroodFx() {}

   public static void draw(Vfx.Packet p, float seed, float age, float k, float partial, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      if (p.type() == Vfx.Type.WEB) {
         float in = Mth.clamp(age / 5F, 0, 1), out = 1 - Mth.clamp((k - 0.8F) / 0.2F, 0, 1);
         float grow = 0.4F + 0.6F * (1 - (1 - in) * (1 - in));
         VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_WEB)), m, p.x(), p.y() + 0.05, p.z(), p.a() * grow, seed, VfxDraw.argb(0xF2F0E8, 0.85F * in * out));
         return;
      }
      silk(p, age, k, partial, m, buf, cam);
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
      if (mode == 3) {                                                           // victime emmaillotée : spirales serrées autour d'elle
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
         if (mode == 2) {                                                        // l'éclosion : le fil suit la courbe de l'animation
            double h = RuinMother.spawnBodyHeight((age + partial) / 20.0);
            to = ep.add(0, h + 1.8, 0);
         } else if (e instanceof RuinMother rm && rm.isDeadOrDying()) {
            to = ep.add(0, 2.4, 0);                                              // le cocon hissé
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
      float out = mode == 2 ? 1 : 1 - Mth.clamp((k - 0.85F) / 0.15F, 0, 1);
      double sag = mode == 2 ? 0 : 0.25 * from.distanceTo(to) * 0.05;
      int n = 10;
      Vec3 prev = from;
      VertexConsumer vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(T_THREAD));
      for (int i = 1; i <= n; i++) {
         double u = i / (double) n;
         Vec3 q = from.add(to.subtract(from).scale(u)).add(0, -sag * Math.sin(Math.PI * u), 0);
         ribbon(vc, m, prev, q, cam, mode == 2 ? 0.11 : 0.1, (float) ((i - 1) / (double) n), (float) u, VfxDraw.argb(0xF4F2EA, out > 0.5F ? 1F : 0F));
         prev = q;
      }
   }
}
