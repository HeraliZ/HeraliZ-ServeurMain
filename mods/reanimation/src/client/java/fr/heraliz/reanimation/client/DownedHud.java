package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.reanimation.client.kit.Ui;
import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * HUD de la réanimation, au style des écrans de clan (maquettes validées) : joueur à terre (titre, carte du pouls avec le
 * temps restant, aide, abandon), joueur qu'on réanime (même carte en vert), carte d'actions quand on vise un joueur à
 * terre, anneau de progression autour du réticule pour celui qui réanime, bandeau d'exécution. Les effets plein écran
 * (voile, vignette qui bat, sang, image grisée) ne changent pas.
 */
public final class DownedHud {
   private static final ResourceLocation VIGNETTE = new ResourceLocation("reanimation", "textures/gui/vignette.png");
   private static final ResourceLocation SPLATTER = new ResourceLocation("reanimation", "textures/gui/splatter.png");
   private static final int ROUGE = 0xFF4A3D, VERT = 0x5FD08A, ORANGE = 0xE8A040, OR = 0xF2D27A;
   private static final int ROUGE_PALE = 0xE9B8B4, VERT_PALE = 0xCFF3DA, GRIS = 0xC9CDD4;
   private static final int[][] HALO = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

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
         // bandeau : titre doré (ou rouge pour la victime) qui se pose, filet qui s'étire, sous-titre
         float age = 70 - ExecutionFx.bannerTicks + pt;
         float in = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
         float out = Mth.clamp(ExecutionFx.bannerTicks / 12.0F, 0.0F, 1.0F);
         float alpha = Math.min(in, out);
         float scale = 3.0F - 0.6F * in;
         int y = (int)(h * 0.3F);
         int color = ExecutionFx.bannerForVictim ? ROUGE : OR;
         titre(g, mc.font, ExecutionFx.bannerTitle.getString(), w / 2.0F, y, scale, color, alpha, 2.6F);
         int lw = Math.round(w * 0.22F * in);
         filet(g, w / 2 - lw, w / 2 + lw, y + 30, color, Math.round(200 * alpha));
         texte(g, mc.font, ExecutionFx.bannerSubtitle, w / 2.0F, y + 36, 1.3F, 0xFFFFFF, alpha);
      }
   }

   private static void renderExecuting(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
      ExecutionType type = ExecutionType.byIndex(me.style);
      if (me.revive < 96) {
         Entity partner = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
         Component name = (Component)(partner != null ? partner.getDisplayName() : Component.literal("?"));
         int y = h - (int)(h * 0.12F) - 26;
         texte(g, mc.font, Component.translatable("hud.reanimation.executing", new Object[]{type.displayName(), name}), w / 2.0F, y, 1.0F, OR, 1.0F);
         if (me.revive < 58) {
            petitCentre(g, mc.font, Component.translatable("hud.reanimation.move_to_cancel").getString(), w / 2.0F, y + 12, GRIS, 255);
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
         int y = h - (int)(h * 0.12F) - 26;
         texte(g, mc.font, Component.translatable("hud.reanimation.being_executed", new Object[]{name}), w / 2.0F, y, 1.0F, ROUGE, 1.0F);
         texte(g, mc.font, type.displayName(), w / 2.0F, y + 12, 1.0F, OR, 1.0F);
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
      if (mc.options.hideGui) {
         return;
      }
      Font font = mc.font;
      int a = Math.round(255 * fadeIn);
      int cx = w / 2;
      int couleur = revived ? VERT : ROUGE;

      // titre, état, puis la carte du pouls (calés sur la maquette ; remontés si l'écran est trop bas pour l'aide)
      int ch = 40;
      int y0 = Math.min(Math.round(h * 0.555F) + 35, h - 98);
      int top = y0 - 44;
      float shake = revived ? 0.0F : (1.0F - life) * 1.2F * Mth.sin((mc.player.tickCount + pt) * 1.7F);
      titre(g, font, Component.translatable(revived ? "hud.reanimation.reviving_title" : "hud.reanimation.downed").getString(), cx + shake, top, 2.0F, couleur, fadeIn, 3.0F);
      Entity partner = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
      Component name = (Component)(partner != null ? partner.getDisplayName() : Component.literal("?"));
      Component status;
      int statusColor;
      if (revived) {
         status = Component.translatable("hud.reanimation.being_revived", new Object[]{nom(name)});
         statusColor = VERT_PALE;
      } else if (me.has(2)) {
         status = Component.translatable("hud.reanimation.being_searched", new Object[]{nom(name)});
         statusColor = ORANGE;
      } else {
         status = Component.translatable("hud.reanimation.bleeding");
         statusColor = ROUGE_PALE;
      }
      texte(g, font, status, cx, y0 - 20, 1.0F, statusColor, fadeIn);

      // carte du pouls : tracé, temps (ou %), barre
      int cw = Math.min(200, w - 24);
      int x0 = cx - cw / 2;
      Ui.glass(g, x0, y0, cw, ch, Math.round(185 * fadeIn));
      int pale = revived ? 0x8FD8A6 : 0xD7837E;
      petit(g, font, Component.translatable(revived ? "hud.reanimation.pulse_stable" : "hud.reanimation.pulse_weak").getString(), x0 + 7, y0 + 6, pale, a);
      int ew = cw - 82;
      float time = mc.player.tickCount + pt;
      if (revived) {
         ecg(g, x0 + 7, y0 + 14, ew, 16, me.revive + pt, 12.0F, 1.0F, VERT | 0xFF000000, a);
      } else {
         float period = Mth.lerp(life, 34.0F, 18.0F);
         ecg(g, x0 + 7, y0 + 14, ew, 16, time, period, 0.25F + 0.75F * life, ROUGE | 0xFF000000, a);
      }
      int seconds = (me.bleed + 19) / 20;
      String valeur = revived ? Math.round(revive * 100.0F) + " %" : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
      gros(g, font, valeur, x0 + cw - 7, y0 + 6, 1.6F, 0xFFFFFF, a);
      petitDroite(g, font, Component.translatable(revived ? "hud.reanimation.revive_label" : "hud.reanimation.before_death").getString(), x0 + cw - 7, y0 + 25, pale, a);
      int bw = cw - 14, by = y0 + ch - 5;
      g.fill(x0 + 7, by, x0 + 7 + bw, by + 2, Ui.argb(0xFFFFFF, 26 * a / 255));
      float fill = revived ? revive : life;
      int fw = Math.round(bw * Mth.clamp(fill, 0.0F, 1.0F));
      if (fw > 0) {
         int c0 = revived ? 0x2E8A52 : 0x7A0A0A, c1 = revived ? VERT : lerpRgb(0x7A0A0A, ROUGE, life);
         gradientH(g, x0 + 7, by, x0 + 7 + fw, by + 2, Ui.argb(c0, a), Ui.argb(c1, a));
      }

      if (!revived) {
         // aide : comment se faire réanimer (touches réelles)
         String accroupi = mc.options.keyShift.getTranslatedKeyMessage().getString();
         String utiliser = mc.options.keyUse.getTranslatedKeyMessage().getString();
         ligneTouches(g, font, cx, y0 + ch + 3, a, 0xB9A9A7,
               Component.translatable("hud.reanimation.ally_hint").getString(), accroupi, "+", utiliser,
               Component.translatable("hud.reanimation.ally_hint_end").getString());
         // abandonner (maintenir)
         float hold = LocalEffects.giveUpHold / 50.0F;
         int gy = h - 37;
         ligneTouches(g, font, cx, gy, a, hold > 0.0F ? 0xFFFFFF : 0xD0C6C5,
               Component.translatable("hud.reanimation.hold").getString(), ClientSetup.GIVE_UP.getTranslatedKeyMessage().getString(), null, null,
               Component.translatable("hud.reanimation.to_give_up").getString());
         int gw = 74;
         g.fill(cx - gw / 2, gy + 14, cx + gw / 2, gy + 15, Ui.argb(0xFFFFFF, 30 * a / 255));
         if (hold > 0.0F) {
            g.fill(cx - gw / 2, gy + 14, cx - gw / 2 + Math.round(gw * Math.min(1.0F, hold)), gy + 15, Ui.argb(ROUGE, a));
         }
      }
   }

   private static void renderReviving(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
      // anneau de progression autour du réticule (au lieu du bloc en haut de l'écran, qui cachait les barres de boss)
      Font font = mc.font;
      float cx = w / 2.0F, cy = h / 2.0F;
      float p = me.reviveProgress(pt);
      Ui.disque(g, cx, cy, 26.0F, Ui.argb(0x0A0C10, 140));
      Ui.anneau(g, cx, cy, 20.0F, 22.0F, 0.0F, 1.0F, Ui.argb(0xFFFFFF, 40));
      Ui.anneau(g, cx, cy, 19.0F, 23.0F, 0.0F, p, Ui.argb(VERT, 60));
      Ui.anneau(g, cx, cy, 20.0F, 22.0F, 0.0F, p, Ui.argb(VERT, 255));
      gros(g, font, Math.round(p * 100.0F) + " %", cx + font.width(Ui.lit(Math.round(p * 100.0F) + " %", Ui.BOLD)) * 0.65F, cy - 9, 1.3F, 0xFFFFFF, 255);
      ecg(g, Math.round(cx) - 12, Math.round(cy) + 5, 24, 6, me.revive + pt, 12.0F, 1.0F, VERT | 0xFF000000, 255);
      Entity partner = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
      Component name = (Component)(partner != null ? partner.getDisplayName() : Component.literal("?"));
      texte(g, font, Component.translatable("hud.reanimation.reviving", new Object[]{nom(name)}), cx, cy + 36, 1.0F, 0xE3E6EB, 1.0F);
      petitCentre(g, font, Component.translatable("hud.reanimation.move_to_cancel").getString(), cx, cy + 48, 0xB0B6BF, 255);
   }

   private static void renderPrompt(Minecraft mc, GuiGraphics g, int w, int h) {
      if (mc.hitResult instanceof EntityHitResult entityHit && mc.screen == null) {
         Entity target = entityHit.getEntity();
         ClientDowned.Info info = ClientDowned.get(target.getId());
         if (info != null && info.has(1) && ClientDowned.localFlags() == 0) {
            Font font = mc.font;
            // carte à droite sous le réticule : nom + « À terre », temps restant, actions avec leurs touches
            int x0 = w / 2 + 14, y0 = h / 2 + 8, cw = 140;
            boolean etat = info.has(4) || info.has(2) || info.has(256);
            int ch = etat ? 52 : 84;
            Ui.glass(g, x0, y0, cw, ch, 185);
            String puce = Component.translatable("hud.reanimation.downed").getString();
            int pw = (int) Ui.spacedWidth(font, puce.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6F) + 10;
            Ui.chip(g, font, puce, x0 + cw - 7 - pw, y0 + 6, ROUGE);
            String nom = Ui.ellipsis(font, target.getDisplayName().getString(), Ui.BOLD, cw - 14 - pw - 6);
            g.drawString(font, Ui.lit(nom, Ui.BOLD), x0 + 7, y0 + 7, 0xFFFFFFFF, false);
            int seconds = (info.bleed + 19) / 20;
            String mort = Component.translatable("hud.reanimation.bleed_timer", new Object[]{seconds}).getString();
            int mw = font.width(Ui.lit(mort, Ui.FONT));
            int bw = cw - 14 - mw - 8;
            g.fill(x0 + 7, y0 + 23, x0 + 7 + bw, y0 + 25, Ui.argb(0xFFFFFF, 26));
            int fw = Math.round(bw * Mth.clamp(info.bleedFraction(), 0.0F, 1.0F));
            if (fw > 0) gradientH(g, x0 + 7, y0 + 23, x0 + 7 + fw, y0 + 25, Ui.argb(0x7A0A0A, 255), Ui.argb(ROUGE, 255));
            g.drawString(font, Ui.lit(mort, Ui.FONT), x0 + cw - 7 - mw, y0 + 20, Ui.argb(ROUGE_PALE, 255), false);
            g.fill(x0 + 7, y0 + 32, x0 + cw - 7, y0 + 33, Ui.argb(0xFFFFFF, 22));
            int ly = y0 + 38;
            if (info.has(4)) {
               g.drawString(font, Ui.tr(Ui.FONT, "hud.reanimation.target_revived"), x0 + 7, ly, Ui.argb(VERT, 255), false);
            } else if (info.has(2)) {
               g.drawString(font, Ui.tr(Ui.FONT, "hud.reanimation.target_searched"), x0 + 7, ly, Ui.argb(ORANGE, 255), false);
            } else if (info.has(256)) {
               g.drawString(font, Ui.tr(Ui.FONT, "hud.reanimation.target_executed"), x0 + 7, ly, Ui.argb(ROUGE, 255), false);
            } else {
               String use = mc.options.keyUse.getTranslatedKeyMessage().getString();
               String sneak = mc.options.keyShift.getTranslatedKeyMessage().getString();
               String exec = ClientSetup.EXECUTE.getTranslatedKeyMessage().getString();
               action(g, font, x0 + 7, ly, sneak, use, Component.translatable("hud.reanimation.revive_action").getString(), 0x7FE0A0);
               action(g, font, x0 + 7, ly + 14, use, null, Component.translatable("hud.reanimation.search_action").getString(), 0xE3E6EB);
               int fin = action(g, font, x0 + 7, ly + 28, exec, null, Component.translatable("hud.reanimation.execute_action").getString(), 0xFF8A80);
               g.drawString(font, Ui.tr(Ui.FONT, "hud.reanimation.hold_hint"), fin + 4, ly + 30, Ui.argb(Ui.MUTED, 255), false);
               if (LocalEffects.executeHold > 0) {
                  float hold = Math.min(1.0F, LocalEffects.executeHold / 10.0F);
                  g.fill(x0 + 7, y0 + ch - 4, x0 + cw - 7, y0 + ch - 3, Ui.argb(0xFFFFFF, 30));
                  g.fill(x0 + 7, y0 + ch - 4, x0 + 7 + Math.round((cw - 14) * hold), y0 + ch - 3, Ui.argb(ROUGE, 255));
               }
            }
         }
      }
   }

   // ------------------------------------------------------------------ dessin

   /** Une action : touche(s) puis le libellé ; rend le x de fin du libellé. */
   private static int action(GuiGraphics g, Font font, int x, int y, String touche1, String touche2, String libelle, int couleur) {
      int cx = x + touche(g, font, touche1, x, y, 255);
      if (touche2 != null) {
         g.drawString(font, Ui.lit("+", Ui.FONT), cx + 3, y + 2, Ui.argb(Ui.MUTED, 255), false);
         cx += 10 + touche(g, font, touche2, cx + 10, y, 255);
      }
      Component c = Ui.lit(libelle, Ui.FONT);
      g.drawString(font, c, cx + 6, y + 2, Ui.argb(couleur, 255), false);
      return cx + 6 + font.width(c);
   }

   /** Une touche : petit cadre clair avec son nom ; rend sa largeur. */
   private static int touche(GuiGraphics g, Font font, String nom, int x, int y, int a) {
      Component c = Ui.lit(nom, Ui.BOLD);
      int w = font.width(c) + 8;
      Ui.soft(g, x, y, w, 12, Ui.argb(0xFFFFFF, 32 * a / 255));
      Ui.softOutline(g, x + 1, y + 1, w - 2, 10, Ui.argb(0xFFFFFF, 64 * a / 255));
      if (Ui.lisible(a)) g.drawString(font, c, x + 4, y + 2, Ui.argb(0xFFFFFF, a), false);
      return w;
   }

   /** Ligne centrée : texte, touche, (« + », touche), texte. */
   private static void ligneTouches(GuiGraphics g, Font font, float cx, int y, int a, int couleur, String avant, String t1, String plus, String t2, String apres) {
      if (!Ui.lisible(a)) return;
      Component ca = Ui.lit(avant, Ui.FONT), cp = Ui.lit(apres, Ui.FONT);
      int w1 = font.width(Ui.lit(t1, Ui.BOLD)) + 8, w2 = t2 == null ? 0 : font.width(Ui.lit(t2, Ui.BOLD)) + 8;
      int total = font.width(ca) + 5 + w1 + (t2 == null ? 0 : 14 + w2) + 5 + font.width(cp);
      int x = Math.round(cx - total / 2.0F);
      g.drawString(font, ca, x, y + 2, Ui.argb(couleur, a), true);
      x += font.width(ca) + 5;
      x += touche(g, font, t1, x, y, a);
      if (t2 != null) {
         g.drawString(font, Ui.lit(plus, Ui.FONT), x + 4, y + 2, Ui.argb(Ui.MUTED, a), true);
         x += 14;
         x += touche(g, font, t2, x - 4, y, a) - 4;
      }
      g.drawString(font, cp, x + 5, y + 2, Ui.argb(couleur, a), true);
   }

   /** Grand titre en capitales espacées (DM Sans gras) avec un halo de sa couleur. */
   private static void titre(GuiGraphics g, Font font, String s, float cx, float y, float scale, int rgb, float alpha, float espace) {
      int a = Math.round(255 * Mth.clamp(alpha, 0.0F, 1.0F));
      if (!Ui.lisible(a)) return;
      String up = s.toUpperCase(Locale.ROOT);
      g.pose().pushPose();
      g.pose().translate(cx, y, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      float tw = Ui.spacedWidth(font, up, Ui.BOLD, espace);
      int halo = a * 60 / 255;
      if (Ui.lisible(halo)) {
         for (int[] d : HALO) Ui.spaced(g, font, up, Ui.BOLD, -tw / 2.0F + d[0] * 0.7F, d[1] * 0.7F, Ui.argb(rgb, halo), espace);
      }
      int ombre = a * 150 / 255;
      if (Ui.lisible(ombre)) Ui.spaced(g, font, up, Ui.BOLD, -tw / 2.0F + 0.6F, 0.8F, Ui.argb(0x000000, ombre), espace);
      Ui.spaced(g, font, up, Ui.BOLD, -tw / 2.0F, 0.0F, Ui.argb(rgb, a), espace);
      g.pose().popPose();
   }

   /** Nom d'un joueur dans une phrase : en gras et en blanc. */
   private static Component nom(Component name) {
      return Ui.in(name, Ui.BOLD).withStyle(st -> st.withColor(0xFFFFFF));
   }

   /** Texte DM Sans centré, avec ombre (lisible sur le monde). */
   private static void texte(GuiGraphics g, Font font, Component c, float cx, float y, float scale, int rgb, float alpha) {
      int a = Math.round(255 * Mth.clamp(alpha, 0.0F, 1.0F));
      if (!Ui.lisible(a)) return;
      Component cc = Ui.in(c, Ui.FONT);
      g.pose().pushPose();
      g.pose().translate(cx, y, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.drawString(font, cc, -font.width(cc) / 2, 0, Ui.argb(rgb, a), true);
      g.pose().popPose();
   }

   /** Gros chiffre en gras, aligné à droite sur xDroite. */
   private static void gros(GuiGraphics g, Font font, String s, float xDroite, float y, float scale, int rgb, int a) {
      if (!Ui.lisible(a)) return;
      Component c = Ui.lit(s, Ui.BOLD);
      g.pose().pushPose();
      g.pose().translate(xDroite - font.width(c) * scale, y, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.drawString(font, c, 0, 0, Ui.argb(rgb, a), false);
      g.pose().popPose();
   }

   private static void petit(GuiGraphics g, Font font, String s, float x, float y, int rgb, int a) {
      if (Ui.lisible(a)) Ui.spaced(g, font, s, Ui.SMALL, x, y, Ui.argb(rgb, a), 0.8F);
   }

   private static void petitDroite(GuiGraphics g, Font font, String s, float xDroite, float y, int rgb, int a) {
      if (Ui.lisible(a)) Ui.spacedRight(g, font, s, Ui.SMALL, xDroite, y, Ui.argb(rgb, a), 0.8F);
   }

   private static void petitCentre(GuiGraphics g, Font font, String s, float cx, float y, int rgb, int a) {
      if (Ui.lisible(a)) Ui.spacedCentered(g, font, s, Ui.SMALL, cx, y, Ui.argb(rgb, a), 0.8F);
   }

   /** Filet horizontal qui s'efface vers les deux bouts. */
   private static void filet(GuiGraphics g, int x0, int x1, int y, int rgb, int a) {
      int m = (x0 + x1) / 2;
      if (x1 - x0 < 2 || a <= 0) return;
      gradientH(g, x0, y, m, y + 1, Ui.argb(rgb, 0), Ui.argb(rgb, a));
      gradientH(g, m, y, x1, y + 1, Ui.argb(rgb, a), Ui.argb(rgb, 0));
   }

   /** Rectangle en dégradé horizontal (gauche -> droite), sans allocation. */
   private static void gradientH(GuiGraphics g, int x0, int y0, int x1, int y1, int gauche, int droite) {
      org.joml.Matrix4f m = g.pose().last().pose();
      com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
      vc.vertex(m, x0, y0, 0).color(gauche >> 16 & 0xFF, gauche >> 8 & 0xFF, gauche & 0xFF, gauche >>> 24).endVertex();
      vc.vertex(m, x0, y1, 0).color(gauche >> 16 & 0xFF, gauche >> 8 & 0xFF, gauche & 0xFF, gauche >>> 24).endVertex();
      vc.vertex(m, x1, y1, 0).color(droite >> 16 & 0xFF, droite >> 8 & 0xFF, droite & 0xFF, droite >>> 24).endVertex();
      vc.vertex(m, x1, y0, 0).color(droite >> 16 & 0xFF, droite >> 8 & 0xFF, droite & 0xFF, droite >>> 24).endVertex();
      g.flush();
   }

   static void ecg(GuiGraphics g, int x, int y, int w, int h, float time, float period, float amplitude, int color) {
      ecg(g, x, y, w, h, time, period, amplitude, color, 255);
   }

   /** Tracé du pouls (le plus récent à droite, avec un point blanc) ; {@code alpha} : opacité globale. */
   static void ecg(GuiGraphics g, int x, int y, int w, int h, float time, float period, float amplitude, int color, int alpha) {
      int mid = y + h / 2;
      int prev = mid;

      for (int i = 0; i < w; i++) {
         float t = time - (w - 1 - i) * 0.4F;
         float phase = (t % period + period) % period / period;
         int yy = mid - Math.round(ecgShape(phase) * amplitude * (h / 2.0F - 1.0F));
         float fade = 0.15F + 0.85F * i / w;
         int c = Math.round(alpha * fade) << 24 | color & 16777215;
         g.fill(x + i, Math.min(prev, yy), x + i + 1, Math.max(prev, yy) + 1, c);
         prev = yy;
      }

      g.fill(x + w - 2, prev - 1, x + w + 1, prev + 2, alpha << 24 | 0xFFFFFF);
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

   private static int lerpRgb(int from, int to, float t) {
      int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
      int gr = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
      int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
      return r << 16 | gr << 8 | b;
   }
}
