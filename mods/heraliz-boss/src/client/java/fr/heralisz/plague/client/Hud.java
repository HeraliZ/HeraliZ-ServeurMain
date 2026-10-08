package fr.heralisz.plague.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.plague.entity.PlagueBearer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/** Barre de vie d'Aldric (fine, trois crans de phase, reflets d'or) et habillage des cinématiques (bandes, titres). */
public final class Hud {
   private static int trackedId = -1;
   private static float shown = 1, chip = 1, lastHp = 1, appear;
   private static long lastHitTick, lastFrameNanos;
   private static int lastPhase = 1;
   private static long phaseFlashTick = -100;
   private static final int BAR_W = 220, BAR_H = 5;

   private Hud() {}

   static PlagueBearer findBoss(Minecraft mc) {
      if (mc.level == null || mc.player == null) return null;
      List<PlagueBearer> list = mc.level.getEntitiesOfClass(PlagueBearer.class, mc.player.getBoundingBox().inflate(90), e -> !e.dormant() && !(e.isDeadOrDying() && e.deathTime > PlagueBearer.DEATH_TICKS));
      PlagueBearer best = null;
      double bd = Double.MAX_VALUE;
      for (PlagueBearer s : list) {
         double d = s.distanceToSqr(mc.player);
         if (d < bd) {
            bd = d;
            best = s;
         }
      }
      return best;
   }

   public static void renderBar(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      PlagueBearer boss = findBoss(mc);
      long nanos = System.nanoTime();
      float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1F, (nanos - lastFrameNanos) / 1e9F);
      lastFrameNanos = nanos;
      if (boss == null || Cinematic.active() || (boss.isDeadOrDying() && boss.deathTime > 120)) {
         appear = Math.max(0, appear - dt * 3);
         if (boss == null) trackedId = -1;
         if (appear <= 0) return;
      } else {
         appear = Math.min(1, appear + dt * 2.5F);
      }
      long tick = mc.level.getGameTime();
      float hp = boss == null ? 0 : Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0, 1);
      if (boss != null && boss.getId() != trackedId) {
         trackedId = boss.getId();
         shown = chip = lastHp = hp;
         lastPhase = boss.phase();
      }
      if (hp < lastHp - 1e-4F) lastHitTick = tick;
      lastHp = hp;
      shown += (hp - shown) * Math.min(1, dt * 16);
      if (tick - lastHitTick > 12) chip += (shown - chip) * Math.min(1, dt * 3F);
      if (chip < shown) chip = shown;
      int phase = boss == null ? lastPhase : boss.phase();
      if (phase != lastPhase) {
         phaseFlashTick = tick;
         lastPhase = phase;
      }
      float t = tick + partial;
      float ease = 1 - (1 - appear) * (1 - appear);
      int a8 = (int) (ease * 255);
      int bw = Math.min(BAR_W, w - 40);
      int x0 = (w - bw) / 2 + (tick - lastHitTick < 3 ? (int) (Mth.sin(t * 9) * 1.5F) : 0);
      int y0 = (int) (6 - (1 - ease) * 20);
      Font font = mc.font;
      boolean dead = boss == null || boss.isDeadOrDying();
      String name = Component.translatable("hud.heralisz_plague.name").getString();
      g.drawString(font, name, x0, y0, (a8 << 24) | (dead ? 0xB4A08A : 0xF2E2C8), true);
      int acc = boss != null ? boss.accentColor() : 0xD8A850;
      for (int i = 0; i < 3; i++) {
         int px = x0 + bw - 3 - (2 - i) * 7, py = y0 + 3;
         int c = i < phase ? acc : 0x3A2A22;
         g.fill(px - 1, py - 2, px + 2, py + 3, (a8 << 24) | 0x0C0806);
         g.fill(px, py - 1, px + 1, py + 2, (a8 << 24) | c);
         g.fill(px - 1, py, px + 2, py + 1, (a8 << 24) | c);
      }
      int by = y0 + 11;
      g.fillGradient(x0, by + BAR_H, x0 + bw, by + BAR_H + 4, ((int) (ease * 70) << 24) | acc, acc & 0xFFFFFF);
      g.fill(x0 - 1, by - 1, x0 + bw + 1, by + BAR_H + 1, ((int) (ease * 200) << 24) | 0x0A0605);
      g.fill(x0 - 1, by - 1, x0 + bw + 1, by, ((int) (ease * 90) << 24) | 0xF0D8A8);
      int fillW = (int) (bw * shown), chipW = (int) (bw * chip);
      if (chipW > fillW) g.fill(x0 + fillW, by, x0 + chipW, by + BAR_H, (a8 << 24) | 0xFFF0D8);
      int top = boss != null ? boss.hudTop(phase) : 0xE0B45A, bot = boss != null ? boss.hudBottom(phase) : 0x6A1A12;
      if (phase == 3) top = lerp(top, 0xFFFFFF, (0.5F + 0.5F * Mth.sin(t * 0.35F)) * 0.3F);
      g.fillGradient(x0, by, x0 + fillW, by + BAR_H, (a8 << 24) | top, (a8 << 24) | bot);
      g.fill(x0, by, x0 + fillW, by + 1, ((int) (ease * 110) << 24) | 0xFFFFFF);
      float sweep = ((t % 60) / 60F) * (bw + 40) - 20;
      int sx0 = (int) Math.max(x0, x0 + sweep - 8), sx1 = (int) Math.min(x0 + fillW, x0 + sweep + 8);
      if (sx1 > sx0) g.fill(sx0, by, sx1, by + BAR_H, ((int) (ease * 60) << 24) | 0xFFFFFF);
      if (tick - lastHitTick < 2) g.fill(x0, by, x0 + fillW, by + BAR_H, ((int) (ease * 120) << 24) | 0xFFFFFF);
      for (float mark : new float[] {0.66F, 0.33F}) {
         int mx = x0 + (int) (bw * mark);
         g.fill(mx, by, mx + 1, by + BAR_H, (a8 << 24) | 0x0A0605);
      }
      float pf = Mth.clamp(1 - (t - phaseFlashTick) / 16F, 0, 1);
      if (pf > 0) g.fill(x0 - 1, by - 1, x0 + bw + 1, by + BAR_H + 1, ((int) (pf * 150 * ease) << 24) | 0xFFE8C0);
      RenderSystem.setShaderColor(1, 1, 1, 1);
   }

   private static int lerp(int a, int b, float k) {
      int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255), gg = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255), bb = (int) Mth.lerp(k, a & 255, b & 255);
      return (r << 16) | (gg << 8) | bb;
   }

   public static void renderCinematic(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      if (!Cinematic.active()) return;
      Minecraft mc = Minecraft.getInstance();
      float age = Cinematic.ageTicks(partial), dur = Cinematic.duration();
      float k = Cinematic.progress(partial);
      float bars = Mth.clamp(Math.min(age / 10F, (dur - age) / 10F), 0, 1);
      bars = bars * bars * (3 - 2 * bars);
      int bh = (int) (h * 0.12F * bars);
      g.fill(0, 0, w, bh, 0xFF000000);
      g.fill(0, h - bh, w, h, 0xFF000000);
      int vg = (int) (70 * bars);
      g.fillGradient(0, bh, w, bh + 40, (vg << 24) | 0x1A0C06, 0x000E2236);
      g.fillGradient(0, h - bh - 40, w, h - bh, 0x000E2236, (vg << 24) | 0x1A0C06);
      String[] keys = Cinematic.titles();
      if (keys == null) return;
      String top = Component.translatable(keys[0]).getString(), title = Component.translatable(keys[1]).getString(),
         sub = Component.translatable(keys[2]).getString();
      float at = Cinematic.titleAt();
      int accent = Cinematic.accent() & 0xFFFFFF;
      glowColor = accent;
      float flash = Mth.clamp(1 - Math.abs(k - at) * dur / 2.5F, 0, 1);
      if (flash > 0) g.fill(0, 0, w, h, argb(0xFFF0D8, 0.25F * flash));
      if (k < at) return;
      float tt = (k - at) * dur;
      float out = Mth.clamp((dur - age) / 8F, 0, 1);
      Font font = mc.font;
      int cy = (int) (h * 0.8F);
      int lw = (int) (Math.min(1, tt / 12F) * w * 0.32F);
      g.fill(w / 2 - lw, cy - 24, w / 2 + lw, cy - 23, argb(accent, 0.8F * out));
      g.fill(w / 2 - lw, cy + 20, w / 2 + lw, cy + 21, argb(accent, 0.8F * out));
      float topA = Mth.clamp(tt / 6F, 0, 1) * out;
      drawSpaced(g, font, top, w / 2F, cy - 34, 0.75F, 2, argb(0xE8D2A8, topA), false, 0, top.length());
      int shownChars = Math.min(title.length(), (int) (tt / 1.6F));
      drawSpaced(g, font, title.substring(0, shownChars), w / 2F, cy - 18, 2.4F, 3, argb(lerp(accent, 0xFFFFFF, 0.45F), out), true, tt < 14 ? tt : 0,
         title.length());
      float subA = Mth.clamp((tt - 10) / 10F, 0, 1) * out;
      if (subA > 0) drawSpaced(g, font, sub, w / 2F, cy + 8, 1.1F, 4, argb(0xE8DCCC, subA), false, 0, sub.length());
   }

   private static int glowColor = 0xD8A850;

   private static void drawSpaced(GuiGraphics g, Font font, String s, float cx, float y, float scale, int spacing, int argb, boolean glow, float glitch,
                                  int fullLen) {
      if ((argb >>> 24) < 4) return;
      g.pose().pushPose();
      g.pose().translate(cx, y, 0);
      g.pose().scale(scale, scale, 1);
      String full = s + " ".repeat(Math.max(0, fullLen - s.length()));
      int total = 0;
      for (int i = 0; i < full.length(); i++) total += font.width(String.valueOf(full.charAt(i))) + spacing;
      int x = -total / 2;
      int alpha = argb >>> 24;
      for (int i = 0; i < s.length(); i++) {
         String c = String.valueOf(s.charAt(i));
         int jx = 0, jy = 0;
         if (glitch > 0 && ((i * 7 + (int) glitch) % 5 == 0)) {
            jx = ((i * 13 + (int) (glitch * 3)) % 5) - 2;
            jy = ((i * 5 + (int) (glitch * 7)) % 3) - 1;
            g.drawString(font, c, x + jx - 1, jy, (alpha / 2 << 24) | 0xFF6A1E, false);
            g.drawString(font, c, x + jx + 1, jy, (alpha / 2 << 24) | 0xFFE8C0, false);
         }
         if (glow) {
            int ga = (alpha / 4) << 24;
            g.drawString(font, c, x + jx - 1, jy, ga | glowColor, false);
            g.drawString(font, c, x + jx + 1, jy, ga | glowColor, false);
            g.drawString(font, c, x + jx, jy - 1, ga | glowColor, false);
            g.drawString(font, c, x + jx, jy + 1, ga | glowColor, false);
         }
         g.drawString(font, c, x + jx, jy, argb, !glow);
         x += font.width(c) + spacing;
      }
      g.pose().popPose();
   }

   private static int argb(int rgb, float a) {
      return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
   }
}
