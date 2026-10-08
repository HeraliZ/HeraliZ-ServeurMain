package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class DownedHud {
   private static final ResourceLocation VIGNETTE = new ResourceLocation("reanimation", "textures/gui/vignette.png");
   private static final ResourceLocation SPLATTER = new ResourceLocation("reanimation", "textures/gui/splatter.png");
   private static final int RED = -2084822;
   private static final int GREEN = -11214726;
   private static final int GREY = -4671304;

   private DownedHud() {
   }

   public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         ClientDowned.Info me = ClientDowned.local();
         float bars = ExecutionFx.bars(partialTick);
         if (me != null && me.has(256)) {
            renderExecuted(mc, g, me, partialTick, width, height);
         } else if (me != null && me.has(1)) {
            renderDowned(mc, g, me, partialTick, width, height);
         } else if (me != null && me.has(128)) {
            if (!mc.options.hideGui) {
               renderExecuting(mc, g, me, partialTick, width, height);
            }
         } else if (me != null && me.has(16)) {
            if (!mc.options.hideGui) {
               renderReviving(mc, g, me, partialTick, width, height);
            }
         } else if (!mc.options.hideGui) {
            renderPrompt(mc, g, width, height);
         }

         if (LocalEffects.flashTicks > 0) {
            float a = (LocalEffects.flashTicks - partialTick) / 30.0F;
            g.fill(0, 0, width, height, argb(0.75F * a * a, 16777215));
         }

         renderExecutionOverlay(mc, g, bars, partialTick, width, height);
      }
   }

   private static void renderExecutionOverlay(Minecraft mc, GuiGraphics g, float bars, float pt, int w, int h) {
      float dark = ExecutionFx.darkness(pt);
      if (dark > 0.01F) {
         tinted(g, VIGNETTE, 0, 0, w, h, 256, 0.08F, 0.0F, 0.02F, 0.6F * dark);
         g.fill(0, 0, w, h, argb(0.45F * dark, 656134));
      }

      float blast = ExecutionFx.nearFlash(pt);
      if (blast > 0.01F) {
         g.fill(0, 0, w, h, argb(0.85F * blast * blast, 16054527));
      }

      float beat = Mth.lerp(pt, ExecutionFx.beatO, ExecutionFx.beat);
      if (beat > 0.01F) {
         tinted(g, VIGNETTE, 0, 0, w, h, 256, 0.75F, 0.0F, 0.0F, 0.85F * beat);
      }

      if (bars > 0.0F) {
         int bh = Math.round(h * 0.12F * bars * bars * (3.0F - 2.0F * bars));
         g.fill(0, 0, w, bh, -16777216);
         g.fill(0, h - bh, w, h, -16777216);
      }

      if (ExecutionFx.flashTicks > 0) {
         float a = (ExecutionFx.flashTicks - pt) / 14.0F;
         g.fill(0, 0, w, h, argb(0.85F * a * a, 16777215));
         g.fill(0, 0, w, h, argb(0.35F * a, 9043968));
      }

      if (ExecutionFx.bannerTicks > 0 && !mc.options.hideGui) {
         float age = 70 - ExecutionFx.bannerTicks + pt;
         float in = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
         float out = Mth.clamp(ExecutionFx.bannerTicks / 12.0F, 0.0F, 1.0F);
         float alpha = Math.min(in, out);
         float scale = 3.4F - 0.8F * in;
         int y = (int)(h * 0.3F);
         int color = ExecutionFx.bannerForVictim ? -2084822 : -863622;
         int lw = Math.round(w * 0.22F * in);
         g.fill(w / 2 - lw, y + 34, w / 2 + lw, y + 35, argb(alpha * 0.8F, color & 16777215));
         text(g, mc.font, ExecutionFx.bannerTitle, w / 2.0F, y, scale, color, alpha);
         text(g, mc.font, ExecutionFx.bannerSubtitle, w / 2.0F, y + 40, 1.4F, -1, alpha);
      }
   }

   private static void renderExecuting(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
      ExecutionType type = ExecutionType.byIndex(me.style);
      if (me.revive < 96) {
         Entity partner = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
         Component name = (Component)(partner != null ? partner.getDisplayName() : Component.literal("?"));
         int y = h - (int)(h * 0.12F) - 24;
         text(g, mc.font, Component.translatable("hud.reanimation.executing", new Object[]{type.displayName(), name}), w / 2.0F, y, 1.0F, -863622, 1.0F);
         if (me.revive < 58) {
            text(g, mc.font, Component.translatable("hud.reanimation.move_to_cancel"), w / 2.0F, y + 11, 1.0F, -4671304, 1.0F);
         }
      }
   }

   private static void renderExecuted(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
      ExecutionType type = ExecutionType.byIndex(me.style);
      float t = Mth.clamp((me.revive + pt) / 96.0F, 0.0F, 1.0F);
      tinted(g, VIGNETTE, 0, 0, w, h, 256, 0.05F, 0.0F, 0.12F, 0.35F + 0.55F * t);
      g.fill(0, 0, w, h, argb(0.25F * t, 0));
      if (!mc.options.hideGui) {
         Entity partner = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
         Component name = (Component)(partner != null ? partner.getDisplayName() : Component.literal("?"));
         int y = h - (int)(h * 0.12F) - 24;
         text(g, mc.font, Component.translatable("hud.reanimation.being_executed", new Object[]{name}), w / 2.0F, y, 1.0F, -2084822, 1.0F);
         text(g, mc.font, type.displayName(), w / 2.0F, y + 11, 1.0F, -863622, 1.0F);
      }
   }

   private static void renderDowned(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
      boolean revived = me.has(4);
      float life = me.bleedFraction();
      float revive = revived ? me.reviveProgress(pt) : 0.0F;
      float fadeIn = Mth.clamp((LocalEffects.downedTicks + pt) / 20.0F, 0.0F, 1.0F);
      float beat = Math.max(0.0F, 1.0F - (LocalEffects.ticksSinceBeat + pt) / 7.0F);
      float dark = (0.18F + 0.35F * (1.0F - life)) * (1.0F - revive) * fadeIn;
      g.fill(0, 0, w, h, argb(dark, 0));
      float edge = (0.55F + 0.3F * (1.0F - life) + 0.18F * beat) * (1.0F - 0.7F * revive) * fadeIn;
      tinted(g, VIGNETTE, 0, 0, w, h, 256, 0.55F, 0.02F, 0.02F, edge);
      int s = Math.min(w, h) / 2;
      float splat = 0.85F * fadeIn * (1.0F - 0.6F * revive);
      tinted(g, SPLATTER, 0, 0, s, s, 128, 0.42F, 0.0F, 0.0F, splat);
      g.pose().pushPose();
      g.pose().translate(w, h, 0.0F);
      g.pose().scale(-1.0F, -1.0F, 1.0F);
      tinted(g, SPLATTER, 0, 0, (int)(s * 0.8F), (int)(s * 0.8F), 128, 0.42F, 0.0F, 0.0F, splat);
      g.pose().popPose();
      if (!mc.options.hideGui) {
         Font font = mc.font;
         int cx = w / 2;
         int top = (int)(h * 0.26F);
         float shake = revived ? 0.0F : (1.0F - life) * 1.2F * Mth.sin((mc.player.tickCount + pt) * 1.7F);
         text(
            g,
            font,
            Component.translatable(revived ? "hud.reanimation.reviving_title" : "hud.reanimation.downed"),
            cx + shake,
            top,
            3.0F,
            revived ? -11214726 : -2084822,
            fadeIn
         );
         int statusColor = -4671304;
         Entity partner = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
         Component name = (Component)(partner != null ? partner.getDisplayName() : Component.literal("?"));
         Component status;
         if (revived) {
            status = Component.translatable("hud.reanimation.being_revived", new Object[]{name});
            statusColor = -11214726;
         } else if (me.has(2)) {
            status = Component.translatable("hud.reanimation.being_searched", new Object[]{name});
            statusColor = -1531840;
         } else {
            status = Component.translatable("hud.reanimation.bleeding");
         }

         text(g, font, status, cx, top + 34, 1.0F, statusColor, fadeIn);
         int mw = Math.min(180, w - 40);
         int my = (int)(h * 0.62F);
         float time = mc.player.tickCount + pt;
         g.fill(cx - mw / 2 - 3, my - 3, cx + mw / 2 + 3, my + 31, argb(0.45F * fadeIn, 0));
         if (revived) {
            ecg(g, cx - mw / 2, my, mw, 28, me.revive + pt, 12.0F, 1.0F, -11214726);
         } else {
            float period = Mth.lerp(life, 34.0F, 18.0F);
            ecg(g, cx - mw / 2, my, mw, 28, time, period, 0.25F + 0.75F * life, -2084822);
         }

         int by = my + 34;
         g.fill(cx - mw / 2 - 1, by - 1, cx + mw / 2 + 1, by + 5, -1073741824);
         float fill = revived ? revive : life;
         int fw = Math.round(mw * fill);
         g.fill(cx - mw / 2, by, cx - mw / 2 + fw, by + 4, revived ? -11214726 : lerpColor(-8779254, -2084822, life));
         int seconds = (me.bleed + 19) / 20;
         Component timer = revived
            ? Component.translatable("hud.reanimation.revive_progress", new Object[]{Math.round(revive * 100.0F)})
            : Component.translatable("hud.reanimation.bleed_timer", new Object[]{seconds});
         text(g, font, timer, cx, by + 9, 1.0F, revived ? -11214726 : -1, fadeIn);
         if (!revived) {
            Component key = ClientSetup.GIVE_UP.getTranslatedKeyMessage();
            float hold = LocalEffects.giveUpHold / 50.0F;
            int gy = h - 34;
            text(g, font, Component.translatable("hud.reanimation.give_up", new Object[]{key}), cx, gy, 1.0F, hold > 0.0F ? -1 : -4671304, fadeIn);
            if (hold > 0.0F) {
               int gw = 120;
               g.fill(cx - gw / 2 - 1, gy + 11, cx + gw / 2 + 1, gy + 15, -1073741824);
               g.fill(cx - gw / 2, gy + 12, cx - gw / 2 + Math.round(gw * Math.min(1.0F, hold)), gy + 14, -2084822);
            }
         }
      }
   }

   private static void renderReviving(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
      Font font = mc.font;
      int cx = w / 2;
      int mw = Math.min(180, w - 40);
      int y = 30;
      Entity partner = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
      Component name = (Component)(partner != null ? partner.getDisplayName() : Component.literal("?"));
      text(g, font, Component.translatable("hud.reanimation.reviving", new Object[]{name}), cx, y - 16, 1.0F, -11214726, 1.0F);
      g.fill(cx - mw / 2 - 3, y - 3, cx + mw / 2 + 3, y + 31, -1879048192);
      ecg(g, cx - mw / 2, y, mw, 28, me.revive + pt, 12.0F, 1.0F, -11214726);
      float p = me.reviveProgress(pt);
      int by = y + 36;
      g.fill(cx - mw / 2 - 1, by - 1, cx + mw / 2 + 1, by + 5, -1073741824);
      g.fill(cx - mw / 2, by, cx - mw / 2 + Math.round(mw * p), by + 4, -11214726);
      text(g, font, Component.translatable("hud.reanimation.revive_progress", new Object[]{Math.round(p * 100.0F)}), cx, by + 9, 1.0F, -1, 1.0F);
      text(g, font, Component.translatable("hud.reanimation.move_to_cancel"), cx, by + 22, 1.0F, -4671304, 1.0F);
   }

   private static void renderPrompt(Minecraft mc, GuiGraphics g, int w, int h) {
      if (mc.hitResult instanceof EntityHitResult entityHit && mc.screen == null) {
         Entity target = entityHit.getEntity();
         ClientDowned.Info info = ClientDowned.get(target.getId());
         if (info != null && info.has(1) && ClientDowned.localFlags() == 0) {
            Font font = mc.font;
            int cx = w / 2;
            int y = h / 2 + 14;
            text(g, font, target.getDisplayName(), cx, y, 1.0F, -1, 1.0F);
            int bw = 70;
            g.fill(cx - bw / 2 - 1, y + 11, cx + bw / 2 + 1, y + 15, -1073741824);
            g.fill(cx - bw / 2, y + 12, cx - bw / 2 + Math.round(bw * info.bleedFraction()), y + 14, -2084822);
            if (info.has(4)) {
               text(g, font, Component.translatable("hud.reanimation.target_revived"), cx, y + 20, 1.0F, -11214726, 1.0F);
            } else if (info.has(2)) {
               text(g, font, Component.translatable("hud.reanimation.target_searched"), cx, y + 20, 1.0F, -1531840, 1.0F);
            } else if (info.has(256)) {
               text(g, font, Component.translatable("hud.reanimation.target_executed"), cx, y + 20, 1.0F, -2084822, 1.0F);
            } else {
               Component use = mc.options.keyUse.getTranslatedKeyMessage();
               Component sneak = mc.options.keyShift.getTranslatedKeyMessage();
               text(g, font, Component.translatable("hud.reanimation.search_prompt", new Object[]{use}), cx, y + 20, 1.0F, -1, 1.0F);
               text(g, font, Component.translatable("hud.reanimation.revive_prompt", new Object[]{sneak, use}), cx, y + 31, 1.0F, -11214726, 1.0F);
               Component execute = ClientSetup.EXECUTE.getTranslatedKeyMessage();
               text(g, font, Component.translatable("hud.reanimation.execute_prompt", new Object[]{execute}), cx, y + 42, 1.0F, -2084822, 1.0F);
               if (LocalEffects.executeHold > 0) {
                  int ew = 60;
                  float hold = Math.min(1.0F, LocalEffects.executeHold / 10.0F);
                  g.fill(cx - ew / 2 - 1, y + 53, cx + ew / 2 + 1, y + 57, -1073741824);
                  g.fill(cx - ew / 2, y + 54, cx - ew / 2 + Math.round(ew * hold), y + 56, -2084822);
               }
            }
         }
      }
   }

   static void ecg(GuiGraphics g, int x, int y, int w, int h, float time, float period, float amplitude, int color) {
      int mid = y + h / 2;
      int prev = mid;

      for (int i = 0; i < w; i++) {
         float t = time - (w - 1 - i) * 0.4F;
         float phase = (t % period + period) % period / period;
         int yy = mid - Math.round(ecgShape(phase) * amplitude * (h / 2.0F - 1.0F));
         float fade = 0.15F + 0.85F * i / w;
         int c = Math.round(255.0F * fade) << 24 | color & 16777215;
         g.fill(x + i, Math.min(prev, yy), x + i + 1, Math.max(prev, yy) + 1, c);
         prev = yy;
      }

      g.fill(x + w - 2, prev - 1, x + w + 1, prev + 2, -1);
   }

   private static float ecgShape(float p) {
      if (p > 0.08F && p < 0.18F) {
         return 0.12F * Mth.sin((p - 0.08F) / 0.1F * (float) Math.PI);
      } else if (p >= 0.24F && p < 0.27F) {
         return -0.18F * (p - 0.24F) / 0.03F;
      } else if (p >= 0.27F && p < 0.3F) {
         return Mth.lerp((p - 0.27F) / 0.03F, -0.18F, 1.0F);
      } else if (p >= 0.3F && p < 0.33F) {
         return Mth.lerp((p - 0.3F) / 0.03F, 1.0F, -0.35F);
      } else if (p >= 0.33F && p < 0.36F) {
         return Mth.lerp((p - 0.33F) / 0.03F, -0.35F, 0.0F);
      } else {
         return p > 0.46F && p < 0.62F ? 0.26F * Mth.sin((p - 0.46F) / 0.16F * (float) Math.PI) : 0.0F;
      }
   }

   private static void text(GuiGraphics g, Font font, Component c, float cx, float y, float scale, int color, float alpha) {
      int a = Math.round((color >>> 24 & 0xFF) * Mth.clamp(alpha, 0.0F, 1.0F));
      if (a >= 5) {
         g.pose().pushPose();
         g.pose().translate(cx, y, 0.0F);
         g.pose().scale(scale, scale, 1.0F);
         g.drawString(font, c, -font.width(c) / 2, 0, a << 24 | color & 16777215, true);
         g.pose().popPose();
      }
   }

   private static void tinted(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int size, float r, float gr, float b, float a) {
      if (!(a <= 0.01F)) {
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         g.setColor(r, gr, b, Mth.clamp(a, 0.0F, 1.0F));
         g.blit(texture, x, y, w, h, 0.0F, 0.0F, size, size, size, size);
         g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.disableBlend();
      }
   }

   private static int argb(float a, int rgb) {
      return Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F) << 24 | rgb & 16777215;
   }

   private static int lerpColor(int from, int to, float t) {
      int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
      int gr = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
      int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
      return 0xFF000000 | r << 16 | gr << 8 | b;
   }
}
