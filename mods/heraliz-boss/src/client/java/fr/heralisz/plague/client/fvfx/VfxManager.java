package fr.heralisz.plague.client.fvfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.client.sick.FireFx;
import fr.heralisz.plague.client.Shake;
import fr.heralisz.plague.fvfx.Vfx;
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
 * Effets en cours côté client. Alertes au sol façon MOBA (contour net + remplissage qui avance jusqu'à l'impact),
 * ondes au ras du sol, jets, flashs, brûlures, fissures, griffures, et débris 3D (vrais morceaux de sol qui volent).
 */
@Mod.EventBusSubscriber(modid = HeraliszPlague.MODID, value = Dist.CLIENT)
public final class VfxManager {
   private static final List<Fx> ACTIVE = new ArrayList<>();
   private static final List<Debris> DEBRIS = new ArrayList<>();
   private static final RandomSource RNG = RandomSource.create();

   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszPlague.MODID, "textures/fvfx/" + n + ".png");
   }

   private static final ResourceLocation T_RING = tex("telegraph_ring"), T_FILL = tex("telegraph_fill"), T_BAND = tex("telegraph_band"),
      T_CHEV = tex("telegraph_chevron"), T_GROUND = tex("ring_ground"), T_WALL = tex("ring_wall"), T_JET = tex("jet"), T_FLASH = tex("flash"),
      T_GLOW = tex("glow"), T_SCORCH = tex("scorch"), T_FROST = tex("frost"), T_CRYSTAL = tex("crystal"), T_TRAIL = tex("trail"), T_VORTEX = tex("vortex"), T_CRACK = tex("crack"), T_SLASH = tex("slash"),
      T_ROT = tex("rot_stain"), T_VEINS = tex("rot_veins");

   /** Orange d'alerte (lisible sur tous les sols), vert toxique du boss. */
   private static final int WARN = 0xFF7A2A, WARN_HOT = 0xFFD04A;

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
         case DEBRIS -> spawnDebris(p);
         default -> ACTIVE.add(new Fx(p, mc.level.getGameTime(), (float) Math.floorMod((long) (p.x() * 131 + p.z() * 71), 628) / 100F));
      }
   }

   /** a = rayon de départ, b = force, color = nombre, extra = id d'état de bloc. */
   private static void spawnDebris(Vfx.Packet p) {
      BlockState state = Block.stateById(p.extra());
      if (state.isAir()) return;
      int n = Math.min(60, p.color());
      for (int i = 0; i < n; i++) {
         Debris d = new Debris();
         double a = RNG.nextDouble() * Mth.TWO_PI, r = Math.sqrt(RNG.nextDouble()) * p.a();
         d.pos = p.pos().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
         double out = p.b() * (0.25 + RNG.nextDouble() * 0.5);
         Vec3 dir = new Vec3(p.dx(), p.dy(), p.dz());
         d.vel = new Vec3(Math.cos(a) * out, p.b() * (0.6 + RNG.nextDouble() * 0.8), Math.sin(a) * out).add(dir.scale(p.b()));
         d.vrx = (RNG.nextFloat() - 0.5F) * 900;
         d.vry = (RNG.nextFloat() - 0.5F) * 900;
         d.vrz = (RNG.nextFloat() - 0.5F) * 900;
         d.size = 0.18F + RNG.nextFloat() * 0.32F;
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

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || (ACTIVE.isEmpty() && DEBRIS.isEmpty())) return;
      long now = System.nanoTime();
      float dt = lastNanos == 0 ? 0.016F : Mth.clamp((now - lastNanos) / 1e9F, 0.001F, 0.05F);
      lastNanos = now;
      if (mc.isPaused()) dt = 0;
      float partial = e.getPartialTick();
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
         Vfx.Type ty = fx.p.type();
         if (ty == Vfx.Type.FLAME || ty == Vfx.Type.METEOR) FireFx.draw(fx.p, fx.seed, age, k, partial, ps, buf, cam);
         else if (ty == Vfx.Type.GAS || ty == Vfx.Type.BILE || ty == Vfx.Type.CHAIN || ty == Vfx.Type.SPOREPOD) PlagueFx.draw(fx.p, fx.seed, age, k, partial, ps, buf, cam);
         else draw(fx, age, k, m, buf, cam);
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
         // physique à la seconde (dt), rebond amorti au contact d'un bloc
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

   private static void draw(Fx fx, float age, float k, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
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
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y, z, r, 0, VfxDraw.argb(c, 0.16F * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, r * ease(k), 0, VfxDraw.argb(c, 0.42F * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.008, z, r, 0, VfxDraw.argb(c, (0.85F + 0.15F * Mth.sin(age)) * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.01, z, Math.max(0.05, r * ease(k)), 0, VfxDraw.argb(WARN_HOT, 0.7F * in));
         }
         case LINE -> {
            float len = p.a(), w = p.b();
            int c = lerpColor(WARN, WARN_HOT, endFlash);
            VertexConsumer band = buf.getBuffer(VfxRenderTypes.decal(T_BAND));
            VfxDraw.strip(band, m, x, y, z, d.x, d.z, len, w, 0, 1, VfxDraw.argb(c, 0.45F * in));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, d.x, d.z, len * ease(k), w * 0.96, 0.5F, 0.5F, VfxDraw.argb(c, 0.4F * in));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.glow(T_CHEV)), m, x, y + 0.008, z, d.x, d.z, len, w, -age * 0.06F, len / w - age * 0.06F,
               VfxDraw.argb(WARN_HOT, 0.55F * in));
         }
         case CONE -> {
            float r = p.a(), half = (float) Math.toRadians(p.b() / 2);
            double a = Math.atan2(d.z, d.x);
            int c = lerpColor(WARN, WARN_HOT, endFlash);
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y, z, 0.2, r, a - half, a + half, 24, VfxDraw.argb(c, 0.16F * in));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, 0.2, r * ease(k), a - half, a + half, 24, VfxDraw.argb(c, 0.4F * in));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.008, z, r * 0.9, r, a - half, a + half, 24, VfxDraw.argb(c, 0.9F * in));
         }
         case SHOCKWAVE -> {
            float e = 1 - (1 - k) * (1 - k);
            double r = 0.5 + (p.a() - 0.5) * e;
            float al = (1 - k) * (1 - k);
            VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_GROUND)), m, x, y, z, Math.max(0, r - 2.2), r, 64, 0, VfxDraw.argb(col, al));
            VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_WALL)), m, x, y, z, r * 0.98, p.b() * (1 - k * 0.6), 64, 10, -age * 0.03F,
               VfxDraw.argb(col, al * 0.8F), VfxDraw.argb(col, 0));
         }
         case COLUMN -> {
            float grow = Mth.clamp(k * 6, 0, 1);
            float al = k < 0.35F ? 1 : 1 - (k - 0.35F) / 0.65F;
            double r = p.a() * (0.6 + 0.6 * k);
            double h = p.b() * grow * (1 + 0.15 * k);
            VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_JET)), m, x, y, z, r, h, 24, 3, -age * 0.09F, VfxDraw.argb(col, al), VfxDraw.argb(col, 0));
            int core = VfxDraw.lighten(col, 0.75F);
            VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_JET)), m, x, y, z, r * 0.45, h * 1.1, 16, 2, -age * 0.15F, VfxDraw.argb(core, al * 0.9F),
               VfxDraw.argb(core, 0));
            VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_GROUND)), m, x, y + 0.01, z, 0, r * (1.6 + k * 2), 32, 0, VfxDraw.argb(col, al * 0.7F));
         }
         case NOVA -> {
            // éclair lumineux bref + anneau au sol ; plus de dôme plein écran
            float e = 1 - (float) Math.pow(1 - k, 3);
            double r = p.a() * e;
            float al = (1 - k) * (1 - k);
            VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_GROUND)), m, x, y, z, Math.max(0, r - 3), r, 64, 0, VfxDraw.argb(col, al));
            float fl = Mth.clamp(1 - age / 6F, 0, 1);
            if (fl > 0) billboard(buf.getBuffer(VfxRenderTypes.glow(T_FLASH)), m, new Vec3(x, y + 1.5, z), cam, p.a() * 0.35F * (0.6F + 0.4F * (1 - fl)), 0,
               VfxDraw.argb(0xE8FFC8, fl * 0.9F));
         }
         case ROTCIRCLE -> {
            // la tache pourrit le sol depuis le centre ; les veines pulsent de plus en plus vite avant l'impact
            float grow = 0.25F + 0.75F * ease(k);
            float pulse = 0.55F + 0.45F * Mth.sin(age * (0.25F + 0.9F * k));
            float fade = 1 - Mth.clamp((k - 0.96F) / 0.04F, 0, 1);
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), m, x, y, z, p.a() * 1.08, fx.seed, VfxDraw.argb(0xFFFFFF, 0.35F * in * fade));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), m, x, y + 0.004, z, p.a() * grow, fx.seed, VfxDraw.argb(0xFFFFFF, 0.95F * in * fade));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_VEINS)), m, x, y + 0.008, z, p.a() * grow, fx.seed, VfxDraw.argb(0x9CE06A, pulse * in * fade));
         }
         case ROTLINE -> {
            float grow = 0.2F + 0.8F * ease(k);
            float pulse = 0.55F + 0.45F * Mth.sin(age * (0.25F + 0.9F * k));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), m, x, y, z, d.x, d.z, p.a(), p.b(), 0, 1, VfxDraw.argb(0xFFFFFF, 0.3F * in));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), m, x, y + 0.004, z, d.x, d.z, p.a() * grow, p.b(), 0, 1, VfxDraw.argb(0xFFFFFF, 0.95F * in));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.glow(T_VEINS)), m, x, y + 0.008, z, d.x, d.z, p.a() * grow, p.b(), 0, 1, VfxDraw.argb(0x9CE06A, pulse * in));
         }
         case ROTCONE -> {
            float r = p.a(), half = (float) Math.toRadians(p.b() / 2);
            double a = Math.atan2(d.z, d.x);
            float pulse = 0.55F + 0.45F * Mth.sin(age * (0.25F + 0.9F * k));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), m, x, y, z, 0.2, r, a - half, a + half, 24, VfxDraw.argb(0xFFFFFF, 0.3F * in));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), m, x, y + 0.004, z, 0.2, r * ease(k), a - half, a + half, 24, VfxDraw.argb(0xFFFFFF, 0.95F * in));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.glow(T_VEINS)), m, x, y + 0.008, z, 0.2, r * ease(k), a - half, a + half, 24, VfxDraw.argb(0x9CE06A, pulse * in));
         }
         case FLASH -> {
            float fl = 1 - k;
            billboard(buf.getBuffer(VfxRenderTypes.glow(T_FLASH)), m, p.pos(), cam, p.a() * (0.5F + 0.5F * k), fx.seed, VfxDraw.argb(col, fl * fl));
         }
         case FROST -> {
            float grow = ease(Mth.clamp(k * 5, 0, 1));
            float al = 1 - Mth.clamp((k - 0.7F) / 0.3F, 0, 1);
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_FROST)), m, x, y - 0.015, z, p.a() * (0.3 + 0.7 * grow), fx.seed, VfxDraw.argb(0xFFFFFF, 0.9F * al));
         }
         case SPIKES -> {
            // grappe de cristaux : jaillit (effet ressort), reste, puis se fend et disparaît
            float rise = k < 0.14F ? backOut(k / 0.14F) : 1F;
            float die = Mth.clamp((k - 0.8F) / 0.2F, 0, 1);
            float sc = p.a() * rise * (1 - die * 0.6F);
            RandomSource r = RandomSource.create((long) (fx.seed * 1000));
            VertexConsumer body = buf.getBuffer(VfxRenderTypes.glass(T_CRYSTAL));
            VertexConsumer glow = buf.getBuffer(VfxRenderTypes.glow(T_CRYSTAL));
            for (int i = 0; i < 5; i++) {
               double ang = r.nextDouble() * Mth.TWO_PI, off = i == 0 ? 0 : 0.35 + r.nextDouble() * 0.5;
               double h = (i == 0 ? 2.2 : 0.9 + r.nextDouble() * 1.1) * sc, rad = (i == 0 ? 0.42 : 0.2 + r.nextDouble() * 0.15) * p.a();
               float lx = (float) (Math.cos(ang) * off * p.a()), lz = (float) (Math.sin(ang) * off * p.a());
               float tilt = i == 0 ? 0 : (float) (18 + r.nextDouble() * 20);
               PoseStackLite.push(m, x + lx, y - 0.1, z + lz, (float) Math.toDegrees(ang), tilt);
               Matrix4f mm = PoseStackLite.top();
               VfxDraw.prism(body, mm, 0, 0, 0, h, h * 0.45, rad, 6, VfxDraw.argb(0x8CCBFF, 0.62F * (1 - die)));
               VfxDraw.prism(glow, mm, 0, 0, 0, h, h * 0.45, rad * 1.02, 6, VfxDraw.argb(0x3A8CE0, 0.5F * (1 - die)));
               PoseStackLite.pop();
            }
         }
         case ICICLE -> {
            float fall = Mth.clamp(k / 0.92F, 0, 1);
            double hgt = 18 * (1 - fall * fall);
            float al = k > 0.95F ? (1 - k) / 0.05F : 1;
            PoseStackLite.push(m, x, y + hgt, z, fx.seed * 57F, 180F);
            Matrix4f mm = PoseStackLite.top();
            VfxDraw.prism(buf.getBuffer(VfxRenderTypes.glass(T_CRYSTAL)), mm, 0, -2.6 * p.a(), 0, 2.6 * p.a(), 1.3 * p.a(), 0.45 * p.a(), 6,
               VfxDraw.argb(0x9CD4FF, 0.75F * al));
            VfxDraw.prism(buf.getBuffer(VfxRenderTypes.glow(T_CRYSTAL)), mm, 0, -2.6 * p.a(), 0, 2.6 * p.a(), 1.3 * p.a(), 0.47 * p.a(), 6,
               VfxDraw.argb(0x7CC8FF, 0.5F * al));
            PoseStackLite.pop();
         }
         case SCORCH -> {
            float al = Math.min(in, 1 - Mth.clamp((k - 0.6F) / 0.4F, 0, 1));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_SCORCH)), m, x, y - 0.02, z, p.a(), fx.seed, VfxDraw.argb(col, al));
         }
         case TRAIL -> {
            float al = 1 - k;
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_TRAIL)), m, x, y - 0.015, z, d.x, d.z, p.a(), p.b(), 0, p.a() / p.b() / 2, VfxDraw.argb(col, al));
         }
         case VORTEX -> {
            float al = Math.min(in, 1 - Mth.clamp((k - 0.85F) / 0.15F, 0, 1));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_VORTEX)), m, x, y, z, p.a(), -age * 0.12F, VfxDraw.argb(col, 0.55F * al));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_VORTEX)), m, x, y + 0.01, z, p.a() * 0.6, -age * 0.2F + 1, VfxDraw.argb(VfxDraw.lighten(col, 0.7F), 0.4F * al));
         }
         case CRACK -> {
            float reveal = Mth.clamp(k * 6, 0, 1);
            float al = 1 - Mth.clamp((k - 0.55F) / 0.45F, 0, 1);
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_CRACK)), m, x, y - 0.01, z, d.x, d.z, p.a() * reveal, p.b(), 0, p.a() / p.b() / 3 * reveal,
               VfxDraw.argb(VfxDraw.darken(col, 0.88F), al * 0.9F));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.glow(T_CRACK)), m, x, y, z, d.x, d.z, p.a() * reveal, p.b() * 0.7, 0, p.a() / p.b() / 3 * reveal,
               VfxDraw.argb(col, al));
         }
         case SLASH -> {
            double r = p.a(), half = Math.toRadians(p.b() / 2), a = Math.atan2(d.z, d.x);
            float sweep = Mth.clamp(k * 2.5F, 0, 1);
            int side = p.extra() >= 0 ? 1 : -1;
            double a0 = a - side * half, a1 = a0 + side * 2 * half * sweep;
            float al = 1 - Mth.clamp((k - 0.35F) / 0.65F, 0, 1);
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.glow(T_SLASH)), m, x, p.y(), z, r * 0.35, r, Math.min(a0, a1), Math.max(a0, a1), 24, VfxDraw.argb(col, al));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.glow(T_SLASH)), m, x, p.y() + 0.5, z, r * 0.45, r * 0.95, Math.min(a0, a1), Math.max(a0, a1), 24,
               VfxDraw.argb(VfxDraw.lighten(col, 0.8F), al * 0.8F));
         }
         default -> {
         }
      }
   }

   private static float backOut(float x) {
      return 1 + 2.70158F * (float) Math.pow(x - 1, 3) + 1.70158F * (float) Math.pow(x - 1, 2);
   }

   /** Mini pile de matrices pour poser les cristaux (translation + rotation Y puis inclinaison X). */
   private static final class PoseStackLite {
      private static final java.util.ArrayDeque<Matrix4f> S = new java.util.ArrayDeque<>();

      static void push(Matrix4f base, double x, double y, double z, float yawDeg, float tiltDeg) {
         Matrix4f mm = new Matrix4f(base).translate((float) x, (float) y, (float) z).rotateY((float) Math.toRadians(yawDeg)).rotateX((float) Math.toRadians(tiltDeg));
         S.push(mm);
      }

      static Matrix4f top() {
         return S.peek();
      }

      static void pop() {
         S.pop();
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
