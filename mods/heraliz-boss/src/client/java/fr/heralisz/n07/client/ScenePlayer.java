package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.scene.Scene;
import fr.heralisz.n07.api.scene.SkipMode;
import fr.heralisz.n07.scene.ScenePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture cote client des scenes scriptees (api.scene.Scene) : camera (spline, champ de vision, roulis, fondus enchaines,
 * raccord vers les yeux du joueur), bandes noires, fondus, titres, sous-titres, textes, sons, secousses, decor fantome qui
 * bouge, particules ; et l'invite « maintenir pour passer » (scenes et cinematiques du boss en combat pilote).
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID, value = Dist.CLIENT)
public final class ScenePlayer {
   /** Ticks d'appui avant que le vote soit envoye. */
   private static final int HOLD_TICKS = 12;

   /** Une lecture de scene : la principale (camera) ou l habillage seul (textes / sons par-dessus le jeu). */
   private static final class Play {
      int run = -1, skipMode, nextCue;
      Scene scene;
      long start;
      final List<Ghost> ghosts = new ArrayList<>();
      final List<Emitter> emitters = new ArrayList<>();

      void clear() {
         run = -1;
         scene = null;
         ghosts.clear();
         emitters.clear();
      }
   }

   private static final Play MAIN = new Play(), OVER = new Play();
   // raccourcis vers la lecture principale (camera)
   private static int run = -1, skipMode;
   private static Scene scene;
   private static long start;
   private static int votesRun = -2, votesHolding, votesNeeded;
   private static int holdTicks;
   private static boolean holdSent;
   private static int holdRun = -2;

   // derniere pose calculee (lue par le mixin de camera et les evenements de vue)
   private static float fov, roll, toPlayer;

   private ScenePlayer() {}

   private static final class Ghost {
      final List<BlockPos> rel = new ArrayList<>();
      final List<BlockState> states = new ArrayList<>();
      BlockPos min;
      Vec3 from, to;
      int t0, dur;
      boolean ease;

      Vec3 offset(float age) {
         float k = Mth.clamp((age - t0) / Math.max(1F, dur), 0, 1);
         if (ease) k = k * k * (3 - 2 * k);
         return from.lerp(to, k);
      }
   }

   private static final class Emitter {
      Scene.Cue cue;
      float acc;
   }

   // ------------------------------------------------------------------ cycle
   public static void start(int id, Scene sc, int mode) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      Play p = sc.overlayOnly ? OVER : MAIN;
      p.clear();
      p.run = id;
      p.scene = sc;
      p.skipMode = sc.overlayOnly ? 0 : mode;
      p.start = mc.level.getGameTime();
      p.nextCue = 0;
      if (!sc.overlayOnly) {
         run = id;
         scene = sc;
         skipMode = mode;
         start = p.start;
         votesRun = -2;
         resetHold();
      }
   }

   public static void stop(int id) {
      if (id < 0 || id == OVER.run) OVER.clear();
      if (id < 0 || id == MAIN.run) end();
   }

   private static void end() {
      MAIN.clear();
      run = -1;
      scene = null;
      resetHold();
   }

   private static void resetHold() {
      if (holdSent) ScenePackets.sendToServer(new ScenePackets.Hold(holdRun, false));
      holdTicks = 0;
      holdSent = false;
      holdRun = -2;
   }

   public static void votes(int id, int holding, int needed) {
      votesRun = id;
      votesHolding = holding;
      votesNeeded = needed;
   }

   public static boolean active() {
      Minecraft mc = Minecraft.getInstance();
      if (scene == null || mc.level == null) return false;
      if (mc.level.getGameTime() - start > scene.length) {
         end();
         return false;
      }
      return true;
   }

   static float age(float partial) {
      Minecraft mc = Minecraft.getInstance();
      return (float) (mc.level.getGameTime() + partial - start);
   }

   private static Scene.Shot shotAt(float age) {
      Scene.Shot cur = scene.shots.get(0);
      for (Scene.Shot s : scene.shots) {
         if (s.start <= age) cur = s;
         else break;
      }
      return cur;
   }

   // ------------------------------------------------------------------ camera
   /** {position, point vise} ; met a jour fov / roulis. */
   public static Vec3[] camera(float partial) {
      Minecraft mc = Minecraft.getInstance();
      float age = Math.min(age(partial), scene.length);
      Scene.Shot s = shotAt(age);
      float local = age - s.start;
      float[] pose = pose(s, Mth.clamp(local / s.duration, 0, 1));
      int idx = scene.shots.indexOf(s);
      if (s.blendIn > 0 && local < s.blendIn && idx > 0) {
         float[] prev = pose(scene.shots.get(idx - 1), 1);
         float u = local / s.blendIn;
         u = u * u * (3 - 2 * u);
         for (int i = 0; i < pose.length; i++) pose[i] = Mth.lerp(u, prev[i], pose[i]);
      }
      Vec3 cam = new Vec3(pose[0], pose[1], pose[2]), look = new Vec3(pose[3], pose[4], pose[5]);
      float baseFov = mc.options.fov().get().floatValue();
      fov = pose[6] <= 1 ? baseFov : pose[6];
      roll = pose[7];
      toPlayer = 0;
      if (s.toPlayer && mc.player != null) {
         float u = Mth.clamp(local / s.duration, 0, 1);
         u = u * u * (3 - 2 * u);
         toPlayer = u;
         Vec3 eye = mc.player.getEyePosition(partial);
         Vec3 view = mc.player.getViewVector(partial);
         cam = cam.lerp(eye, u);
         look = look.lerp(eye.add(view.scale(8)), u);
         fov = Mth.lerp(u, fov, baseFov);
         roll = Mth.lerp(u, roll, 0);
      }
      if (s.avoidWalls) cam = clear(mc, cam, look);
      // camera a l'epaule : une derive tres lente
      float tt = (float) (mc.level.getGameTime() + partial);
      float drift = 1 - toPlayer;
      cam = cam.add(Mth.sin(tt * 0.031F) * 0.05 * drift, Mth.sin(tt * 0.023F + 1.3F) * 0.035 * drift, Mth.cos(tt * 0.027F) * 0.05 * drift);
      return new Vec3[] {cam, look};
   }

   /** Tire la camera devant le premier obstacle entre le point vise et elle. */
   private static Vec3 clear(Minecraft mc, Vec3 cam, Vec3 target) {
      Vec3 dir = cam.subtract(target);
      double len = dir.length();
      if (len < 1e-3 || mc.player == null) return cam;
      Vec3 from = target.add(dir.normalize().scale(Math.min(1.5, len * 0.5)));
      var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, cam, net.minecraft.world.level.ClipContext.Block.OUTLINE,
         net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
      if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
         double d = Math.max(1.0, hit.getLocation().distanceTo(target) - 0.8);
         if (d < len) return target.add(dir.normalize().scale(d));
      }
      return cam;
   }

   /** Pose d'un plan a k (0..1) : {x, y, z, vx, vy, vz, fov, roulis} (monde). */
   private static float[] pose(Scene.Shot s, float k) {
      List<float[]> keys = s.keys;
      int i = 0;
      while (i < keys.size() - 2 && k > keys.get(i + 1)[0]) i++;
      float[] a = keys.get(i), b = keys.get(i + 1);
      float t = Mth.clamp((k - a[0]) / Math.max(1e-4F, b[0] - a[0]), 0, 1);
      float[] p0 = i > 0 ? keys.get(i - 1) : a, p3 = i + 2 < keys.size() ? keys.get(i + 2) : b;
      // depart et arrivee adoucis, sans arret aux cles intermediaires (orbites fluides)
      boolean first = p0 == a, last = p3 == b;
      float ts = first && last ? t * t * (3 - 2 * t) : first ? t * t * (2 - t) : last ? t * (1 + t - t * t) : t;
      float[] v = new float[9];
      for (int j = 1; j < 9; j++) v[j] = j >= 7 ? Mth.lerp(ts, a[j], b[j]) : spline(p0[j], a[j], b[j], p3[j], ts);
      Scene.Frame f = scene.frames.get(Mth.clamp(s.frame, 0, scene.frames.size() - 1));
      Vec3 cam = f.at(v[1], v[2], v[3]), look = f.at(v[4], v[5], v[6]);
      return new float[] {(float) cam.x, (float) cam.y, (float) cam.z, (float) look.x, (float) look.y, (float) look.z, v[7], v[8]};
   }

   private static float spline(float v0, float v1, float v2, float v3, float t) {
      float t2 = t * t, t3 = t2 * t;
      return 0.5F * (2 * v1 + (-v0 + v2) * t + (2 * v0 - 5 * v1 + 4 * v2 - v3) * t2 + (-v0 + 3 * v1 - 3 * v2 + v3) * t3);
   }

   /** Le corps du joueur est dessine (camera detachee) sauf a la toute fin du raccord vers ses yeux. */
   public static boolean detached() {
      return toPlayer < 0.93F;
   }

   @SubscribeEvent
   public static void fov(ViewportEvent.ComputeFov e) {
      if (active() && e.usedConfiguredFov()) {
         camera((float) e.getPartialTick());
         e.setFOV(fov);
      }
   }

   @SubscribeEvent
   public static void angles(ViewportEvent.ComputeCameraAngles e) {
      if (active() && Math.abs(roll) > 0.01F) e.setRoll(roll);
   }

   // ------------------------------------------------------------------ repliques (tick client)
   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      CinematiqueEtat.tick();
      skipInput(mc);
      active();
      if (OVER.scene != null && mc.level != null && mc.level.getGameTime() - OVER.start > OVER.scene.length) OVER.clear();
      for (Play p : new Play[] {MAIN, OVER}) {
         if (p.scene == null || mc.level == null) continue;
         int age = (int) (mc.level.getGameTime() - p.start);
         while (p.nextCue < p.scene.cues.size() && p.scene.cues.get(p.nextCue).tick() <= age) fire(mc, p, p.scene.cues.get(p.nextCue++));
         // particules en cours
         p.emitters.removeIf(em -> age > em.cue.tick() + em.cue.f()[0]);
         for (Emitter em : p.emitters) emit(mc, p, em, age);
      }
   }

   private static void fire(Minecraft mc, Play p, Scene.Cue c) {
      switch (c.type()) {
         case Scene.SOUND -> sound(mc, p, c);
         case Scene.SHAKE -> Shake.add(c.f()[0], (int) c.f()[1]);
         case Scene.GHOST -> ghost(mc, p, c);
         case Scene.PARTICLES -> {
            Emitter em = new Emitter();
            em.cue = c;
            p.emitters.add(em);
         }
         default -> {
         }
      }
   }

   private static void sound(Minecraft mc, Play play, Scene.Cue c) {
      ResourceLocation rl = ResourceLocation.tryParse(c.s()[0]);
      if (rl == null) return;
      float[] f = c.f();
      if (f[5] > 0) {
         mc.getSoundManager().play(new SimpleSoundInstance(rl, SoundSource.AMBIENT, f[0], f[1], SoundInstance.createUnseededRandom(), false, 0,
            SoundInstance.Attenuation.NONE, 0, 0, 0, true));
      } else {
         Vec3 p = play.scene.frames.get(c.frame()).at(f[2], f[3], f[4]);
         mc.getSoundManager().play(new SimpleSoundInstance(rl, SoundSource.AMBIENT, f[0], f[1], SoundInstance.createUnseededRandom(), false, 0,
            SoundInstance.Attenuation.LINEAR, p.x, p.y, p.z, false));
      }
   }

   private static void ghost(Minecraft mc, Play p, Scene.Cue c) {
      float[] f = c.f();
      Ghost g = new Ghost();
      g.t0 = c.tick() + (f.length > 14 ? (int) f[14] : 0);
      g.dur = (int) f[0];
      BlockPos min = BlockPos.containing(f[1], f[2], f[3]), max = BlockPos.containing(f[4], f[5], f[6]);
      g.min = min;
      g.from = new Vec3(f[7], f[8], f[9]);
      g.to = new Vec3(f[10], f[11], f[12]);
      g.ease = f[13] > 0;
      int budget = 512;
      for (BlockPos q : BlockPos.betweenClosed(min, max)) {
         BlockState st = mc.level.getBlockState(q);
         if (st.isAir() || budget-- <= 0) continue;
         g.rel.add(q.subtract(min));
         g.states.add(st);
      }
      p.ghosts.add(g);
   }

   private static void emit(Minecraft mc, Play p, Emitter em, int age) {
      float[] f = em.cue.f();
      ParticleOptions type = ForgeRegistries.PARTICLE_TYPES.getValue(ResourceLocation.tryParse(em.cue.s()[0])) instanceof net.minecraft.core.particles.SimpleParticleType st
         ? st : null;
      if (type == null) return;
      Vec3 base = p.scene.frames.get(em.cue.frame()).at(f[1], f[2], f[3]);
      int gi = (int) f[9];
      if (gi >= 0 && gi < p.ghosts.size()) base = base.add(p.ghosts.get(gi).offset(age));
      em.acc += f[7];
      var r = mc.level.random;
      while (em.acc >= 1) {
         em.acc -= 1;
         double sp = f[8];
         mc.level.addParticle(type, base.x + (r.nextDouble() - 0.5) * 2 * f[4], base.y + (r.nextDouble() - 0.5) * 2 * f[5],
            base.z + (r.nextDouble() - 0.5) * 2 * f[6], (r.nextDouble() - 0.5) * sp, (r.nextDouble() - 0.5) * sp, (r.nextDouble() - 0.5) * sp);
      }
   }

   // ------------------------------------------------------------------ decor fantome
   @SubscribeEvent
   public static void renderGhosts(RenderLevelStageEvent e) {
      if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || MAIN.ghosts.isEmpty() || !active()) return;
      Minecraft mc = Minecraft.getInstance();
      float age = age(e.getPartialTick());
      Vec3 cam = e.getCamera().getPosition();
      PoseStack ps = e.getPoseStack();
      MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
      for (Ghost g : MAIN.ghosts) {
         Vec3 off = g.offset(age);
         for (int i = 0; i < g.rel.size(); i++) {
            BlockPos r = g.rel.get(i);
            double x = g.min.getX() + r.getX() + off.x, y = g.min.getY() + r.getY() + off.y + 0.01, z = g.min.getZ() + r.getZ() + off.z;
            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x + 0.5, y + 0.5, z + 0.5));
            mc.getBlockRenderer().renderSingleBlock(g.states.get(i), ps, buf, Math.max(light, 0x00A000A0), OverlayTexture.NO_OVERLAY);
            ps.popPose();
         }
      }
      buf.endBatch();
   }

   @SubscribeEvent
   public static void unload(LevelEvent.Unload e) {
      if (e.getLevel().isClientSide()) {
         end();
         OVER.clear();
      }
   }

   // ------------------------------------------------------------------ passer
   private static int currentSkipRun() {
      if (active() && skipMode != SkipMode.NONE.ordinal()) return run;
      if (Cinematic.skippable()) return -1;
      return -2;
   }

   private static void skipInput(Minecraft mc) {
      int target = currentSkipRun();
      if (target == -2 || mc.player == null || mc.screen != null) {
         if (holdSent || holdTicks > 0) resetHold();
         return;
      }
      if (holdRun != target && holdSent) resetHold();
      holdRun = target;
      boolean down = mc.options.keyJump.isDown();
      holdTicks = down ? Math.min(HOLD_TICKS, holdTicks + 1) : Math.max(0, holdTicks - 2);
      if (down && holdTicks >= HOLD_TICKS && !holdSent) {
         holdSent = true;
         ScenePackets.sendToServer(new ScenePackets.Hold(target, true));
      } else if (!down && holdSent) {
         holdSent = false;
         ScenePackets.sendToServer(new ScenePackets.Hold(target, false));
      }
   }

   // ------------------------------------------------------------------ habillage
   public static void renderOverlay(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      if (active()) drawScene(mc, g, partial, w, h, scene, start, true);
      if (OVER.scene != null) drawScene(mc, g, partial, w, h, OVER.scene, OVER.start, false);
      int sk = currentSkipRun();
      if (sk != -2) drawSkip(mc, g, w, h, sk);
   }

   private static void drawScene(Minecraft mc, GuiGraphics g, float partial, int w, int h, Scene scene, long start, boolean main) {
      float age = (float) (mc.level.getGameTime() + partial - start);
      int bh = 0;
      float black = 0;
      if (main) {
         Scene.Shot s = shotAt(age);
         float local = age - s.start;
         // bandes noires
         float bars = s.letterbox ? 1 : 0;
         bars *= Mth.clamp(age / 10F, 0, 1) * Mth.clamp((scene.length - age) / 6F, 0, 1);
         if (s.toPlayer) bars *= 1 - Mth.clamp(local / s.duration, 0, 1);
         bars = bars * bars * (3 - 2 * bars);
         bh = (int) (h * 0.12F * bars);
         if (bh > 0) {
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
         }
         // fondus au noir de plan
         if (s.fadeIn > 0) black = Math.max(black, 1 - Mth.clamp(local / s.fadeIn, 0, 1));
         if (s.fadeOut > 0) black = Math.max(black, 1 - Mth.clamp((s.duration - local) / s.fadeOut, 0, 1));
      } else if (Cinematic.active()) {
         bh = (int) (h * 0.12F);
      }
      int fadeColor = 0x000000;
      for (Scene.Cue c : scene.cues) {
         if (c.tick() > age) break;
         float a = age - c.tick();
         float[] f = c.f();
         if (c.type() == Scene.FADE) {
            float in = f[0], hold = f[1], out = f[2];
            float v = a < in ? a / Math.max(1, in) : a < in + hold ? 1 : 1 - (a - in - hold) / Math.max(1, out);
            if (v > black) {
               black = Mth.clamp(v, 0, 1);
               fadeColor = (int) f[3] & 0xFFFFFF;
            }
         } else if (c.type() == Scene.FLASH && a < f[0]) {
            g.fill(0, 0, w, h, BossHud.argb((int) f[1], 0.6F * (1 - a / f[0])));
         }
      }
      // le noir d'abord : titres et sous-titres restent lisibles par-dessus un fondu
      if (black > 0.003F) g.fill(0, 0, w, h, BossHud.argb(fadeColor, black));
      // repliques textuelles
      Font font = mc.font;
      int subLine = 0;
      for (Scene.Cue c : scene.cues) {
         if (c.tick() > age) break;
         float a = age - c.tick(), dur = c.f()[0];
         if (a > dur) continue;
         float out = Mth.clamp((dur - a) / 8F, 0, 1);
         switch (c.type()) {
            case Scene.TITLE -> BossHud.drawTitleCard(g, w, h, a, out, tr(c.s()[0]), tr(c.s()[1]), tr(c.s()[2]), (int) c.f()[1] & 0xFFFFFF);
            case Scene.SUBTITLE -> {
               float alpha = Mth.clamp(a / 5F, 0, 1) * Mth.clamp((dur - a) / 5F, 0, 1);
               drawSubtitle(g, font, w, h - bh - 14 - subLine * 26, tr(c.s()[0]), tr(c.s()[1]), (int) c.f()[1], alpha);
               subLine++;
            }
            case Scene.LABEL -> {
               float[] f = c.f();
               String txt = tr(c.s()[0]);
               int n = f[4] <= 0 ? txt.length() : Math.min(txt.length(), (int) (a * f[4]));
               String shown = txt.substring(0, n) + (n < txt.length() || ((int) (a / 6) % 2 == 0) ? "_" : "");
               g.pose().pushPose();
               g.pose().translate(f[1] * w, f[2] * h, 0);
               g.pose().scale(f[3], f[3], 1);
               g.drawString(font, shown, 0, 0, BossHud.argb((int) f[5], out), true);
               g.pose().popPose();
            }
            default -> {
            }
         }
      }
   }

   private static String tr(String key) {
      return key == null || key.isEmpty() ? "" : I18n.exists(key) ? I18n.get(key) : key;
   }

   private static void drawSubtitle(GuiGraphics g, Font font, int w, int y, String speaker, String text, int color, float alpha) {
      if (alpha <= 0.01F) return;
      int maxW = (int) (w * 0.6F);
      Component line = speaker.isEmpty() ? Component.literal(text) : Component.literal(speaker + " : ").withStyle(st -> st.withColor(color & 0xFFFFFF))
         .append(Component.literal(text).withStyle(st -> st.withColor(0xF0F0E0)));
      List<FormattedCharSequence> lines = font.split(line, maxW);
      int lh = 10, total = lines.size() * lh;
      int top = y - total;
      int bw = 0;
      for (FormattedCharSequence l : lines) bw = Math.max(bw, font.width(l));
      g.fill(w / 2 - bw / 2 - 6, top - 4, w / 2 + bw / 2 + 6, top + total + 2, BossHud.argb(0x000000, 0.55F * alpha));
      int a8 = (int) (alpha * 255) << 24;
      for (int i = 0; i < lines.size(); i++) {
         FormattedCharSequence l = lines.get(i);
         g.drawString(font, l, w / 2 - font.width(l) / 2, top + i * lh, 0xFFFFFF | a8, true);
      }
   }

   private static void drawSkip(Minecraft mc, GuiGraphics g, int w, int h, int target) {
      Font font = mc.font;
      net.minecraft.network.chat.MutableComponent txt = Component.translatable("scene.heralisz_n07.skip", mc.options.keyJump.getTranslatedKeyMessage());
      if (votesRun == target && votesNeeded > 1 && votesNeeded < 100) txt.append("  (" + votesHolding + "/" + votesNeeded + ")");
      int tw = font.width(txt);
      int x = w - tw - 14, y = h - 22;
      float k = holdTicks / (float) HOLD_TICKS;
      g.fill(x - 6, y - 5, w - 8, y + 12, 0x88000000);
      g.fill(x - 6, y + 11, x - 6 + (int) ((w - 8 - (x - 6)) * k), y + 12, 0xFFB8FF6A);
      g.drawString(font, txt, x, y, holdSent ? 0xFFB8FF6A : 0xFFE6E6D0, true);
   }
}
