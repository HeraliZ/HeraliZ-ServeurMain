package fr.heralisz.n07.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Effets du Chevalier Sans Visage : cercle de feu (annonce puis mur de flammes). */
final class KnightFx {
   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszN07.MODID, "textures/vfx/" + n + ".png");
   }

   private static final ResourceLocation T_RING = tex("telegraph_ring"), T_FLAME = tex("flame"), T_WALL = tex("ring_wall"), T_SCORCH = tex("scorch");

   private KnightFx() {}

   static void draw(Vfx.Packet p, float seed, float age, float k, float partial, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
      Matrix4f m = ps.last().pose();
      if (p.type() == Vfx.Type.FIRERING) fireRing(p, seed, age, k, m, buf);
   }

   private static void fireRing(Vfx.Packet p, float seed, float age, float k, Matrix4f m, MultiBufferSource buf) {
      double x = p.x(), y = p.y() + 0.05, z = p.z(), r = p.a();
      int col = p.color() & 0xFFFFFF;
      if (p.b() < 0.5F) {
         // annonce : anneau orange qui pulse de plus en plus vite
         float pulse = 0.55F + 0.45F * Mth.sin(age * (0.3F + k * 0.9F));
         VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y, z, r - 0.9, r + 0.9, 72, age * 0.02F, VfxDraw.argb(col, 0.8F * pulse));
         return;
      }
      float in = Mth.clamp(age / 6F, 0, 1), out = 1 - Mth.clamp((k - 0.85F) / 0.15F, 0, 1);
      float al = in * out;
      VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.decal(T_SCORCH)), m, x, y - 0.02, z, r - 1.6, r + 1.6, 72, seed, VfxDraw.argb(0xFFFFFF, 0.7F * al));
      double h = 3.2 * in * (1 + 0.08 * Mth.sin(age * 0.7F));
      VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_FLAME)), m, x, y, z, r, h, 72, 18, -age * 0.07F, VfxDraw.argb(col, 0.95F * al), VfxDraw.argb(col, 0));
      VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_WALL)), m, x, y, z, r * 1.01, h * 0.7, 72, 12, -age * 0.12F, VfxDraw.argb(0xFFE0A0, 0.6F * al),
         VfxDraw.argb(0xFFE0A0, 0));
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isPaused() && mc.level != null && al > 0.2F) {
         RandomSource rr = mc.level.random;
         for (int i = 0; i < 3; i++) {
            double a = rr.nextDouble() * Mth.TWO_PI;
            mc.level.addParticle(ParticleTypes.FLAME, x + Math.cos(a) * r, y + rr.nextDouble() * h * 0.6, z + Math.sin(a) * r, 0, 0.04, 0);
         }
      }
   }
}
