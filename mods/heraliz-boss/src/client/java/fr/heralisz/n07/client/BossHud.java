package fr.heralisz.n07.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.BossInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/** Barre de vie du boss (dessinée sur mesure) et habillage des cinématiques (bandes noires, titres). */
public final class BossHud {
   private static final ResourceLocation FRAME = new ResourceLocation(HeraliszN07.MODID, "textures/hud/frame.png");
   private static final ResourceLocation FILL = new ResourceLocation(HeraliszN07.MODID, "textures/hud/fill.png");
   private static final ResourceLocation ICON = new ResourceLocation(HeraliszN07.MODID, "textures/hud/icon.png");
   private static final int FW = 300, FH = 46, BX = 52, BY = 21, BW = 240, BH = 11;

   private static int trackedId = -1;
   private static String lastName = "";
   private static float shown = 1, chip = 1, lastHp = 1, appear;
   private static long lastHitTick, lastFrameNanos;
   private static int lastPhase = 1;
   private static long phaseFlashTick = -100;

   private BossHud() {}

   private static LivingEntity findBoss(Minecraft mc) {
      if (mc.level == null || mc.player == null) return null;
      List<LivingEntity> list = mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(72), e -> e instanceof BossInfo bi && !bi.dormant());
      LivingEntity best = null;
      double bd = Double.MAX_VALUE;
      for (LivingEntity s : list) {
         double d = s.distanceToSqr(mc.player);
         if (d < bd) {
            bd = d;
            best = s;
         }
      }
      return best;
   }

   // ------------------------------------------------------------------ barre de vie : fine et sobre
   private static final int BAR_W = 200, BAR_H = 5;

   public static void renderBar(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      LivingEntity boss = findBoss(mc);
      BossInfo info = boss instanceof BossInfo bi ? bi : null;
      long nanos = System.nanoTime();
      float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1F, (nanos - lastFrameNanos) / 1e9F);
      lastFrameNanos = nanos;
      if (boss == null || Cinematic.active() || ScenePlayer.active() || (boss.isDeadOrDying() && boss.deathTime > 70)) {
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
         lastPhase = info.phase();
      }
      if (hp < lastHp - 1e-4F) lastHitTick = tick;
      lastHp = hp;
      shown += (hp - shown) * Math.min(1, dt * 16);
      if (tick - lastHitTick > 12) chip += (shown - chip) * Math.min(1, dt * 3F);
      if (chip < shown) chip = shown;
      int phase = info == null ? lastPhase : info.phase();
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

      // nom + points de phase
      String name = info != null ? info.hudName() : lastName;
      lastName = name;
      g.drawString(font, name, x0, y0, (a8 << 24) | (dead ? 0xFF6A5A : (info != null ? info.hudNameColor() : 0xD2F5B4)), true);
      for (int i = 0; i < 3; i++) {
         int px = x0 + bw - 3 - (2 - i) * 7, py = y0 + 3;
         boolean on = i < phase;
         int c = on ? (info != null ? info.accentColor() : 0x9CFF5A) : 0x3A4A34;
         g.fill(px - 1, py - 2, px + 2, py + 3, (a8 << 24) | 0x0C120A);
         g.fill(px, py - 1, px + 1, py + 2, (a8 << 24) | c);
         g.fill(px - 1, py, px + 2, py + 1, (a8 << 24) | c);
      }

      int by = y0 + 11;
      // lueur douce sous la barre
      int acc = (info != null ? info.accentColor() : 0x5AFF3A) & 0xFFFFFF;
      g.fillGradient(x0, by + BAR_H, x0 + bw, by + BAR_H + 4, ((int) (ease * 70) << 24) | acc, acc);
      // fond + contour fin
      g.fill(x0 - 1, by - 1, x0 + bw + 1, by + BAR_H + 1, ((int) (ease * 200) << 24) | 0x050805);
      g.fill(x0 - 1, by - 1, x0 + bw + 1, by, ((int) (ease * 90) << 24) | 0xC8FFB0);
      int fillW = (int) (bw * shown), chipW = (int) (bw * chip);
      if (chipW > fillW) g.fill(x0 + fillW, by, x0 + chipW, by + BAR_H, (a8 << 24) | 0xE8DC9A);
      int top = info != null ? info.hudTop(phase) : 0xA8FF7A, bot = info != null ? info.hudBottom(phase) : 0x2F9A2A;
      if (phase == 3) {
         float pl = 0.5F + 0.5F * Mth.sin(t * 0.35F);
         top = lerp(top, 0xFFFFFF, pl * 0.25F);
      }
      g.fillGradient(x0, by, x0 + fillW, by + BAR_H, (a8 << 24) | top, (a8 << 24) | bot);
      g.fill(x0, by, x0 + fillW, by + 1, ((int) (ease * 110) << 24) | 0xFFFFFF);
      // reflet qui balaye la barre
      float sweep = ((t % 60) / 60F) * (bw + 40) - 20;
      int sx0 = (int) Math.max(x0, x0 + sweep - 8), sx1 = (int) Math.min(x0 + fillW, x0 + sweep + 8);
      if (sx1 > sx0) g.fill(sx0, by, sx1, by + BAR_H, ((int) (ease * 60) << 24) | 0xFFFFFF);
      if (tick - lastHitTick < 2) g.fill(x0, by, x0 + fillW, by + BAR_H, ((int) (ease * 120) << 24) | 0xFFFFFF);
      // crans de phase
      for (float mark : new float[] {0.66F, 0.33F}) {
         int mx = x0 + (int) (bw * mark);
         g.fill(mx, by, mx + 1, by + BAR_H, (a8 << 24) | 0x050805);
      }
      float pf = Mth.clamp(1 - (t - phaseFlashTick) / 16F, 0, 1);
      if (pf > 0) g.fill(x0 - 1, by - 1, x0 + bw + 1, by + BAR_H + 1, ((int) (pf * 150 * ease) << 24) | 0xE8FFB0);
      RenderSystem.setShaderColor(1, 1, 1, 1);
   }

   static int lerp(int a, int b, float k) {
      int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255), gg = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255), bb = (int) Mth.lerp(k, a & 255, b & 255);
      return (r << 16) | (gg << 8) | bb;
   }

   // ------------------------------------------------------------------ cinématique : bandes noires + titres
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
      // voile vert sur les bords
      int vg = (int) (60 * bars);
      g.fillGradient(0, bh, w, bh + 40, (vg << 24) | 0x1A3A10, 0x001A3A10);
      g.fillGradient(0, h - bh - 40, w, h - bh, 0x001A3A10, (vg << 24) | 0x1A3A10);

      BossInfo bi = Cinematic.boss();
      String[] tt3 = bi != null ? bi.cinematicTitles(Cinematic.kind()) : new String[] {"", "", ""};
      String top = tr(tt3[0]), title = tr(tt3[1]), sub = tr(tt3[2]);
      float at = bi != null ? bi.cinematicTitleAt(Cinematic.kind()) : 0.5F;
      int accent = bi != null ? bi.accentColor() & 0xFFFFFF : 0x9CFF5A;
      glowColor = accent;
      // flash blanc au moment fort
      float flash = Mth.clamp(1 - Math.abs(k - at) * dur / 2.5F, 0, 1);
      if (flash > 0) g.fill(0, 0, w, h, argb(0xE8FFD8, 0.22F * flash));
      if (k < at) return;
      float tt = (k - at) * dur;               // ticks depuis l'apparition du titre
      float out = Mth.clamp((dur - age) / 8F, 0, 1);
      drawTitleCard(g, w, h, tt, out, top, title, sub, accent);
   }

   /** Carte de titre (bandeau, titre lettre par lettre avec parasites, sous-titre) ; tt = ticks depuis son apparition. */
   static void drawTitleCard(GuiGraphics g, int w, int h, float tt, float out, String top, String title, String sub, int accent) {
      Minecraft mc = Minecraft.getInstance();
      glowColor = accent;
      Font font = mc.font;
      int cy = (int) (h * 0.8F);
      // lignes qui s'étirent depuis le centre
      int lw = (int) (Math.min(1, tt / 12F) * w * 0.32F);
      g.fill(w / 2 - lw, cy - 24, w / 2 + lw, cy - 23, argb(accent, 0.8F * out));
      g.fill(w / 2 - lw, cy + 20, w / 2 + lw, cy + 21, argb(accent, 0.8F * out));
      // petit bandeau d'alerte
      float topA = Mth.clamp(tt / 6F, 0, 1) * out;
      drawSpaced(g, font, top, w / 2F, cy - 34, 0.75F, 2, argb(0xFFD23A, topA), false, 0);
      // titre lettre par lettre, avec parasites
      int shownChars = Math.min(title.length(), (int) (tt / 1.6F));
      drawSpaced(g, font, title.substring(0, shownChars), w / 2F, cy - 18, 2.4F, 3, argb(lerp(accent, 0xFFFFFF, 0.4F), out), true, tt < 14 ? tt : 0,
         title.length());
      float subA = Mth.clamp((tt - 10) / 10F, 0, 1) * out;
      if (subA > 0) drawSpaced(g, font, sub, w / 2F, cy + 8, 1.1F, 4, argb(0xE6E6D0, subA), false, 0);
   }

   private static int glowColor = 0x7CFF3A;

   /** Cle de traduction -> texte (un texte deja traduit passe tel quel). */
   static String tr(String key) {
      return key != null && net.minecraft.client.resources.language.I18n.exists(key) ? net.minecraft.client.resources.language.I18n.get(key) : key == null ? "" : key;
   }

   static void drawSpaced(GuiGraphics g, Font font, String s, float cx, float y, float scale, int spacing, int argb, boolean glow, float glitch) {
      drawSpaced(g, font, s, cx, y, scale, spacing, argb, glow, glitch, s.length());
   }

   /** Texte agrandi, lettres espacées, centré sur la largeur finale (fullLen) pour ne pas « glisser » pendant l'apparition. */
   private static void drawSpaced(GuiGraphics g, Font font, String s, float cx, float y, float scale, int spacing, int argb, boolean glow, float glitch, int fullLen) {
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
            g.drawString(font, c, x + jx - 1, jy, (alpha / 2 << 24) | 0xFF3AD0, false);
            g.drawString(font, c, x + jx + 1, jy, (alpha / 2 << 24) | 0x3AFFE0, false);
         }
         if (glow) {
            int ga = (alpha / 4) << 24;
            int gc = glowColor;
            g.drawString(font, c, x + jx - 1, jy, ga | gc, false);
            g.drawString(font, c, x + jx + 1, jy, ga | gc, false);
            g.drawString(font, c, x + jx, jy - 1, ga | gc, false);
            g.drawString(font, c, x + jx, jy + 1, ga | gc, false);
         }
         g.drawString(font, c, x + jx, jy, argb, !glow);
         x += font.width(c) + spacing;
      }
      g.pose().popPose();
   }

   static int argb(int rgb, float a) {
      return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
   }
}
