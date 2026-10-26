package fr.heralisz.mere.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.client.Cinematic;
import fr.heralisz.mere.client.ScreenFx;
import fr.heralisz.mere.client.Shake;
import fr.heralisz.mere.fx.Particles;
import fr.heralisz.mere.vfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effets d'Aldric côté client : alertes au sol (contour net + remplissage qui avance jusqu'à l'impact, rouge braise), ondes de
 * poussière et de braises, fissures incandescentes, murs de flammes, gerbes d'étincelles, brûlures, pluie de cendres, débris
 * de pierre 3D, et le rayon du vitrail (lumière colorée qui tombe en biais de la rosace, poussière dorée en suspension).
 */
@Mod.EventBusSubscriber(modid = HeraliszMere.MODID, value = Dist.CLIENT)
public final class VfxManager {
   private static final List<Fx> ACTIVE = new ArrayList<>();
   private static final List<Debris> DEBRIS = new ArrayList<>();
   private static final RandomSource RNG = RandomSource.create();

   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszMere.MODID, "textures/vfx/" + n + ".png");
   }

   private static final ResourceLocation T_RING = tex("telegraph_ring"), T_FILL = tex("telegraph_fill"), T_BAND = tex("telegraph_band"),
      T_CHEV = tex("telegraph_chevron"), T_GROUND = tex("ring_ground"), T_WALL = tex("ring_wall"), T_FLASH = tex("flash"), T_GLOW = tex("glow"),
      T_CRACK = tex("crack"), T_SCORCH = tex("scorch"), T_TRAIL = tex("trail");

   /** Alerte : rouge braise, qui blanchit à l'impact. */
   private static final int WARN = 0xFF3A10, WARN_HOT = 0xFFE2A8;
   /** Les couleurs du vitrail (rouge, or, bleu, violet, vert) : les bandes du rayon. */
   private static final int[] GLASS = {0xE8402A, 0xF8C060, 0x5A7CE8, 0xB060E8, 0xF8C060, 0xE8402A, 0x5AD070};

   private record Fx(Vfx.Packet p, long start, float seed) {}

   private static final class Debris {
      Vec3 pos, vel;
      float rx, ry, rz, vrx, vry, vrz, size;
      int life, age;
      BlockState state;
   }

   private VfxManager() {}

   public static void receive(Vfx.Packet p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) return;
      switch (p.type()) {
         case SHAKE -> {
            double d = mc.player.position().distanceTo(p.pos());
            if (d < p.b()) Shake.add(p.a() * (float) (1 - d / p.b()), p.duration());
         }
         case CINEMATIC -> {
            if (mc.player.position().distanceTo(p.pos()) < 110) Cinematic.start(p.extra(), (int) p.b(), p.duration(), p.dir(), p.pos());
         }
         case DEBRIS -> spawnDebris(p);
         case SCREEN -> {
            if (p.color() == 1) ScreenFx.hit(p.extra() / 100F);
            else ScreenFx.add(p.a(), p.b(), p.duration());
         }
         case SPARKS -> sparks(mc, p);
         default -> ACTIVE.add(new Fx(p, mc.level.getGameTime(), (float) Math.floorMod((long) (p.x() * 131 + p.z() * 71), 628) / 100F));
      }
   }

   /** Gerbe d'étincelles : fer contre fer, balle sur le pavois. */
   private static void sparks(Minecraft mc, Vfx.Packet p) {
      Vec3 d = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : p.dir().normalize();
      int n = (int) (10 + p.a() * 14);
      for (int i = 0; i < n; i++) {
         Vec3 v = d.add((RNG.nextDouble() - 0.5) * 1.4, (RNG.nextDouble() - 0.2) * 1.0, (RNG.nextDouble() - 0.5) * 1.4).normalize()
            .scale(0.15 + RNG.nextDouble() * 0.35 * p.a());
         mc.level.addParticle(i % 4 == 0 ? Particles.AURORA.get() : Particles.CINDER.get(), p.x(), p.y(), p.z(), v.x, v.y, v.z);
      }
      mc.level.addParticle(Particles.MIST.get(), p.x(), p.y(), p.z(), d.x * 0.02, 0.02, d.z * 0.02);
   }

   private static void spawnDebris(Vfx.Packet p) {
      BlockState state = Block.stateById(p.extra());
      if (state.isAir()) return;
      int n = Math.min(70, p.color());
      for (int i = 0; i < n; i++) {
         Debris d = new Debris();
         double a = RNG.nextDouble() * Mth.TWO_PI, r = Math.sqrt(RNG.nextDouble()) * p.a();
         d.pos = p.pos().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
         double out = p.b() * (0.25 + RNG.nextDouble() * 0.5);
         d.vel = new Vec3(Math.cos(a) * out, p.b() * (0.6 + RNG.nextDouble() * 0.8), Math.sin(a) * out).add(p.dir().scale(p.b()));
         d.vrx = (RNG.nextFloat() - 0.5F) * 900;
         d.vry = (RNG.nextFloat() - 0.5F) * 900;
         d.vrz = (RNG.nextFloat() - 0.5F) * 900;
         d.size = 0.18F + RNG.nextFloat() * 0.36F;
         d.life = 40 + RNG.nextInt(40);
         d.state = state;
         DEBRIS.add(d);
      }
   }

   @SubscribeEvent
   public static void onUnload(LevelEvent.Unload e) {
      ACTIVE.clear();
      DEBRIS.clear();
   }

   private static long lastNanos;
   private static float lastPartial;

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      if (ACTIVE.isEmpty() && DEBRIS.isEmpty()) return;
      long now = System.nanoTime();
      float dt = lastNanos == 0 ? 0.016F : Mth.clamp((now - lastNanos) / 1e9F, 0.001F, 0.05F);
      lastNanos = now;
      if (mc.isPaused()) dt = 0;
      float partial = e.getPartialTick();
      lastPartial = partial;
      double time = mc.level.getGameTime() + partial;
      Vec3 cam = e.getCamera().getPosition();
      PoseStack ps = e.getPoseStack();
      ps.pushPose();
      ps.translate(-cam.x, -cam.y, -cam.z);
      Matrix4f m = ps.last().pose();
      MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
      Iterator<Fx> it = ACTIVE.iterator();
      while (it.hasNext()) {
         Fx fx = it.next();
         float age = (float) (time - fx.start);
         if (age > fx.p.duration()) {
            it.remove();
            continue;
         }
         float k = Mth.clamp(age / Math.max(1, fx.p.duration()), 0, 1);
         draw(mc, fx, age, k, m, buf, cam);
      }
      buf.endBatch();
      renderDebris(mc, ps, buf, dt);
      buf.endBatch();
      ps.popPose();
   }

   private static void renderDebris(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, float dt) {
      Iterator<Debris> it = DEBRIS.iterator();
      while (it.hasNext()) {
         Debris d = it.next();
         d.age++;
         if (d.age > d.life * 3) {
            it.remove();
            continue;
         }
         d.vel = d.vel.add(0, -22 * dt, 0).scale(Math.pow(0.6, dt));
         Vec3 next = d.pos.add(d.vel.scale(dt));
         BlockPos bp = BlockPos.containing(next);
         if (!mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) {
            if (d.vel.y < 0) {
               next = new Vec3(next.x, bp.getY() + 1.0 + d.size * 0.3, next.z);
               d.vel = new Vec3(d.vel.x * 0.45, -d.vel.y * 0.3, d.vel.z * 0.45);
               d.vrx *= 0.5F;
               d.vry *= 0.5F;
               d.vrz *= 0.5F;
            } else {
               d.vel = new Vec3(-d.vel.x * 0.3, d.vel.y, -d.vel.z * 0.3);
               next = d.pos;
            }
         }
         d.pos = next;
         d.rx += d.vrx * dt;
         d.ry += d.vry * dt;
         d.rz += d.vrz * dt;
         float k = d.age / (d.life * 3F);
         float s = d.size * (k > 0.75F ? (1 - k) / 0.25F : 1F);
         ps.pushPose();
         ps.translate(d.pos.x, d.pos.y, d.pos.z);
         ps.mulPose(Axis.XP.rotationDegrees(d.rx));
         ps.mulPose(Axis.YP.rotationDegrees(d.ry));
         ps.mulPose(Axis.ZP.rotationDegrees(d.rz));
         ps.scale(s, s, s);
         ps.translate(-0.5, -0.5, -0.5);
         int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(d.pos));
         mc.getBlockRenderer().renderSingleBlock(d.state, ps, buf, light, OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }
   }

   private static void draw(Minecraft mc, Fx fx, float age, float k, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      Vfx.Packet p = fx.p;
      double x = p.x(), y = p.y() + 0.04, z = p.z();
      int col = p.color() & 0xFFFFFF;
      Vec3 d = new Vec3(p.dx(), 0, p.dz());
      if (d.lengthSqr() > 1e-6) d = d.normalize();
      float in = Mth.clamp(age / 4F, 0, 1);
      float endFlash = Mth.clamp((k - 0.88F) / 0.12F, 0, 1);
      switch (p.type()) {
         case CIRCLE -> {
            float r = p.a();
            int c = lerpColor(WARN, WARN_HOT, endFlash);
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y, z, r, 0, VfxDraw.argb(c, 0.18F * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, r * ease(k), 0, VfxDraw.argb(c, 0.45F * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.008, z, r, 0, VfxDraw.argb(c, (0.85F + 0.15F * Mth.sin(age)) * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.01, z, Math.max(0.05, r * ease(k)), 0, VfxDraw.argb(WARN_HOT, 0.7F * in));
         }
         case LINE -> {
            float len = p.a(), w = p.b();
            int c = lerpColor(WARN, WARN_HOT, endFlash);
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_BAND)), m, x, y, z, d.x, d.z, len, w, 0, 1, VfxDraw.argb(c, 0.45F * in));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, d.x, d.z, len * ease(k), w * 0.96, 0.5F, 0.5F,
               VfxDraw.argb(c, 0.42F * in));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.glow(T_CHEV)), m, x, y + 0.008, z, d.x, d.z, len, w, -age * 0.06F, len / w - age * 0.06F,
               VfxDraw.argb(WARN_HOT, 0.55F * in));
         }
         case CONE -> {
            float r = p.a(), half = (float) Math.toRadians(p.b() / 2);
            double a = Math.atan2(d.z, d.x);
            int c = lerpColor(WARN, WARN_HOT, endFlash);
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y, z, 0.2, r, a - half, a + half, 32, VfxDraw.argb(c, 0.18F * in));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, 0.2, r * ease(k), a - half, a + half, 32, VfxDraw.argb(c, 0.42F * in));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.008, z, r * 0.9, r, a - half, a + half, 32, VfxDraw.argb(c, 0.9F * in));
         }
         case SHOCKWAVE -> {
            float e = 1 - (1 - k) * (1 - k);
            double r = 0.5 + (p.a() - 0.5) * e;
            float al = (1 - k) * (1 - k);
            VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_GROUND)), m, x, y, z, Math.max(0, r - 2.2), r, 64, 0, VfxDraw.argb(col, al));
            VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_WALL)), m, x, y, z, r * 0.98, p.b() * (1 - k * 0.6), 64, 10, -age * 0.03F,
               VfxDraw.argb(col, al * 0.7F), VfxDraw.argb(col, 0));
            if (k < 0.6F) {
               for (int i = 0; i < 2; i++) {
                  double a = RNG.nextDouble() * Mth.TWO_PI;
                  mc.level.addParticle(i == 0 ? Particles.MIST.get() : Particles.CINDER.get(), x + Math.cos(a) * r, y + 0.2, z + Math.sin(a) * r,
                     Math.cos(a) * 0.12, 0.06 + RNG.nextDouble() * 0.08, Math.sin(a) * 0.12);
               }
            }
         }
         case FLASH -> {
            float fl = 1 - k;
            billboard(buf.getBuffer(VfxRenderTypes.glow(T_FLASH)), m, p.pos(), cam, p.a() * (0.5F + 0.5F * k), fx.seed, VfxDraw.argb(col, fl * fl));
         }
         case CRACK -> {
            float reveal = Mth.clamp(k * 6, 0, 1);
            float al = 1 - Mth.clamp((k - 0.55F) / 0.45F, 0, 1);
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_CRACK)), m, x, y - 0.01, z, d.x, d.z, p.a() * reveal, p.b(), 0, p.a() / p.b() / 3 * reveal,
               VfxDraw.argb(0x0A0604, al * 0.9F));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.glow(T_CRACK)), m, x, y, z, d.x, d.z, p.a() * reveal, p.b() * 0.7, 0, p.a() / p.b() / 3 * reveal,
               VfxDraw.argb(col, al));
            if (RNG.nextInt(3) == 0 && al > 0.3F) {
               double t = RNG.nextDouble() * p.a() * reveal;
               mc.level.addParticle(Particles.CINDER.get(), x + d.x * t, y + 0.1, z + d.z * t, 0, 0.05, 0);
            }
         }
         case EYES -> {
            float on = Mth.clamp(age / 3F, 0, 1), off = Mth.clamp((p.duration() - age) / 15F, 0, 1);
            float pop = 1F + 1.2F * Math.max(0, 1 - age / 6F);
            float pulse = 0.85F + 0.15F * Mth.sin(age * 0.6F);
            float al = on * off;
            billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_GLOW)), m, p.pos(), cam, p.a() * 2.6F * pop, 0, VfxDraw.argb(col, 0.5F * al * pulse));
            billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_FLASH)), m, p.pos(), cam, p.a() * pop, age * 0.02F, VfxDraw.argb(0xFF7A30, 0.95F * al));
            billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_GLOW)), m, p.pos(), cam, p.a() * 0.4F, 0, VfxDraw.argb(0xFFE6C0, al));
         }
         case SHAFT -> shaft(mc, fx, age, m, buf);
         case WEB, SILK -> fr.heralisz.mere.client.BroodFx.draw(p, fx.seed, age, k, lastPartial, m, buf, cam);
         case FIREWALL -> {
            float grow = ease(Mth.clamp(age / 8F, 0, 1)), al = Math.min(1, (p.duration() - age) / 20F);
            double len = p.a() * grow;
            int n = Math.max(3, (int) (len / 0.8));
            RandomSource r = RandomSource.create((long) (fx.seed * 1000));
            Vec3 side = new Vec3(-d.z, 0, d.x);
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_SCORCH)), m, x, y - 0.005, z, d.x, d.z, len, p.b() * 1.4, 0, (float) (len / p.b()),
               VfxDraw.argb(0x000000, 0.7F * al));
            for (int i = 0; i < n; i++) {
               double t = (i + 0.5) / n * len, o = (r.nextDouble() - 0.5) * p.b();
               Vec3 c = new Vec3(x + d.x * t + side.x * o, y, z + d.z * t + side.z * o);
               float h = (float) (0.7 + r.nextDouble() * 0.9) * (0.8F + 0.2F * Mth.sin(age * 0.5F + i));
               flame(buf, m, c, cam, h * p.b() * 0.9F, al, age * 0.1F + i);
            }
            if (RNG.nextInt(2) == 0) {
               double t = RNG.nextDouble() * len;
               mc.level.addParticle(Particles.CINDER.get(), x + d.x * t, y + 0.3, z + d.z * t, 0, 0.08 + RNG.nextDouble() * 0.08, 0);
            }
            if (RNG.nextInt(4) == 0) {
               double t = RNG.nextDouble() * len;
               mc.level.addParticle(Particles.MIST.get(), x + d.x * t, y + 1.2, z + d.z * t, 0, 0.05, 0);
            }
         }
         case SCORCH -> {
            float al = Math.min(Mth.clamp(age / 4F, 0, 1), 1 - Mth.clamp((k - 0.6F) / 0.4F, 0, 1));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_SCORCH)), m, x, y - 0.012, z, p.a(), fx.seed, VfxDraw.argb(0x000000, 0.85F * al));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_SCORCH)), m, x, y - 0.008, z, p.a() * 0.7, fx.seed + 1, VfxDraw.argb(0xFF4A10,
               0.5F * al * (1 - k)));
            if (RNG.nextInt(6) == 0 && k < 0.7F) mc.level.addParticle(Particles.MIST.get(), x + (RNG.nextDouble() - 0.5) * p.a(), y + 0.2,
               z + (RNG.nextDouble() - 0.5) * p.a(), 0, 0.04, 0);
         }
         case ASHFALL -> {
            float al = Math.min(1, (p.duration() - age) / 20F);
            int n = (int) (3 * al * p.a() / 4) + 1;
            for (int i = 0; i < n; i++) {
               double a = RNG.nextDouble() * Mth.TWO_PI, r = Math.sqrt(RNG.nextDouble()) * p.a();
               mc.level.addParticle(i % 3 == 0 ? Particles.CINDER.get() : Particles.FLAKE.get(), x + Math.cos(a) * r, y + 8 + RNG.nextDouble() * 6,
                  z + Math.sin(a) * r, (RNG.nextDouble() - 0.5) * 0.02, -0.04, (RNG.nextDouble() - 0.5) * 0.02);
            }
         }
         default -> {
         }
      }
   }

   /**
    * Le rayon de la rosace : un faisceau épais, tombant en biais, fait de bandes colorées (les verres), plus dense au centre ;
    * une flaque de lumière sur les dalles au point d'impact ; de la poussière dorée qui danse dedans. b = intensité.
    */
   private static void shaft(Minecraft mc, Fx fx, float age, Matrix4f m, MultiBufferSource buf) {
      Vfx.Packet p = fx.p;
      float dur = p.duration();
      float al = Math.min(Mth.clamp(age / 30F, 0, 1), Mth.clamp((dur - age) / 30F, 0, 1)) * Mth.clamp(p.b(), 0, 1.5F);
      Vec3 a = p.pos(), dir = p.dir(), b = a.add(dir);
      Vec3 fw = dir.normalize();
      Vec3 side = new Vec3(-fw.z, 0, fw.x);
      if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
      side = side.normalize();
      Vec3 up = side.cross(fw).normalize();
      float w = p.a();
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_GLOW));
      float flick = 0.9F + 0.1F * Mth.sin(age * 0.05F + fx.seed);
      int bands = GLASS.length;
      for (int i = 0; i < bands; i++) {
         float t0 = (float) i / bands - 0.5F, t1 = (float) (i + 1) / bands - 0.5F;
         float core = 1 - Math.abs((t0 + t1) * 0.5F) * 1.3F;
         int c = p.color() != 0 ? p.color() : GLASS[i];
         int top = VfxDraw.argb(c, 0.26F * al * core * flick), bot = VfxDraw.argb(c, 0.1F * al * core);
         // deux plans croisés (le faisceau se voit de partout)
         for (Vec3 ax : new Vec3[] {side, up}) {
            Vec3 s0 = ax.scale(t0 * w), s1 = ax.scale(t1 * w);
            Vec3 e0 = ax.scale(t0 * w * 1.35), e1 = ax.scale(t1 * w * 1.35);
            VfxDraw.v(vc, m, a.x + s0.x, a.y + s0.y, a.z + s0.z, 0.5F, 0, top);
            VfxDraw.v(vc, m, a.x + s1.x, a.y + s1.y, a.z + s1.z, 0.5F, 0, top);
            VfxDraw.v(vc, m, b.x + e1.x, b.y + e1.y, b.z + e1.z, 0.5F, 1, bot);
            VfxDraw.v(vc, m, b.x + e0.x, b.y + e0.y, b.z + e0.z, 0.5F, 1, bot);
         }
      }
      // la flaque colorée sur les dalles
      for (int i = 0; i < 3; i++) {
         int c = p.color() != 0 ? p.color() : GLASS[(i * 2 + (int) (fx.seed * 3)) % bands];
         VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_GLOW)), m, b.x + side.x * (i - 1) * w * 0.25, b.y + 0.03 + i * 0.002,
            b.z + side.z * (i - 1) * w * 0.25, w * 0.7, 0, VfxDraw.argb(c, 0.35F * al));
      }
      if (RNG.nextInt(2) == 0 && al > 0.1F) {
         double t = RNG.nextDouble();
         Vec3 at = a.add(dir.scale(t)).add(side.scale((RNG.nextDouble() - 0.5) * w * 0.8)).add(up.scale((RNG.nextDouble() - 0.5) * w * 0.8));
         mc.level.addParticle(p.color() != 0 ? Particles.MIST.get() : Particles.AURORA.get(), at.x, at.y, at.z, (RNG.nextDouble() - 0.5) * 0.005, -0.002, (RNG.nextDouble() - 0.5) * 0.005);
      }
   }

   /** Une langue de feu : deux plans croisés, jaune au pied, rouge sombre au sommet, qui ondulent. */
   private static void flame(MultiBufferSource buf, Matrix4f m, Vec3 c, Vec3 cam, float h, float al, float ph) {
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_GLOW));
      float w = h * 0.45F;
      float sway = Mth.sin(ph) * 0.15F * h;
      for (int i = 0; i < 2; i++) {
         double a = i * Math.PI / 2 + ph * 0.1;
         double sx = Math.cos(a) * w, sz = Math.sin(a) * w;
         int bot = VfxDraw.argb(0xFFB040, 0.8F * al), top = VfxDraw.argb(0xC02008, 0.0F);
         VfxDraw.v(vc, m, c.x - sx, c.y, c.z - sz, 0, 1, bot);
         VfxDraw.v(vc, m, c.x + sx, c.y, c.z + sz, 1, 1, bot);
         VfxDraw.v(vc, m, c.x + sx * 0.2 + sway, c.y + h, c.z + sz * 0.2, 1, 0, top);
         VfxDraw.v(vc, m, c.x - sx * 0.2 + sway, c.y + h, c.z - sz * 0.2, 0, 0, top);
      }
   }

   private static float ease(float k) {
      return k * k * (3 - 2 * k);
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

   private static int lerpColor(int a, int b, float t) {
      int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
      return (r << 16) | (g << 8) | bl;
   }
}
