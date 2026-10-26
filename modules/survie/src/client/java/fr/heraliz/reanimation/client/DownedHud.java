package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.reanimation.client.hud.CarteCible;
import fr.heraliz.reanimation.client.hud.HudATerre;
import fr.heraliz.reanimation.client.hud.HudReanimateur;
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
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

/**
 * HUD de la réanimation. Refonte du 25/10 (docs/HUD_REFONTE.md, kit HUD du noyau) : joueur à terre ({@link HudATerre}),
 * celui qui réanime ({@link HudReanimateur}), carte quand on vise un joueur à terre ({@link CarteCible}) ; chacun décide
 * lui-même s'il est visible et garde son fondu de sortie. Restent ici, inchangés : exécution (bandeau, voile, flash),
 * joueur exécuté, exécuteur.
 */
public final class DownedHud {
   private static final ResourceLocation VIGNETTE = new ResourceLocation("reanimation", "textures/gui/vignette.png");
   private static final int ROUGE = 0xFF4A3D, OR = 0xF2D27A, GRIS = 0xC9CDD4;
   private static final int[][] HALO = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
   /** DM Sans gras 26 (titre du bandeau) et normal 14 (son sous-titre), à leur vraie taille. */
   private static final ResourceLocation BANDEAU = new ResourceLocation("reanimation", "ui_bandeau"),
         SOUS_TITRE = new ResourceLocation("reanimation", "ui_sous_titre");
   /** Échelle de l'ancien titre une fois posé (gras 11 agrandi) : écarts du halo et de l'ombre, milieu des capitales. */
   private static final float POSE = 2.4F;

   private DownedHud() {
   }

   public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         ClientDowned.Info me = ClientDowned.local();
         float bars = ExecutionFx.bars(partialTick);
         if (me != null && me.has(256)) {
            renderExecuted(mc, g, me, partialTick, width, height);
         } else if (me != null && !me.has(1) && me.has(128)) {
            if (!mc.options.hideGui) {
               renderExecuting(mc, g, me, partialTick, width, height);
            }
         }

         // refonte du 25/10 : chaque HUD décide s'il est visible (à terre, réanimateur, carte de la cible)
         HudATerre.rendre(mc, g, me, partialTick, width, height);
         HudReanimateur.rendre(mc, g, me, partialTick, width, height);
         CarteCible.rendre(mc, g, partialTick, width, height);

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
         // le titre se pose : agrandi 1,25 fois à l'apparition, exactement 1 une fois posé (avant : gras 11 de 3 à 2,4 fois)
         float pop = 1.0F + 0.25F * (1.0F - in);
         int y = (int)(h * 0.3F);
         int color = ExecutionFx.bannerForVictim ? ROUGE : OR;
         titre(g, mc.font, ExecutionFx.bannerTitle.getString(), w / 2.0F, y, pop, color, alpha, 2.6F * POSE);
         int lw = Math.round(w * 0.22F * in);
         filet(g, w / 2 - lw, w / 2 + lw, y + 30, color, Math.round(200 * alpha));
         // sous-titre en 14 à sa vraie taille (avant : 11 agrandi 1,3 fois), milieu des capitales au même endroit
         texte(g, mc.font, ExecutionFx.bannerSubtitle, w / 2.0F, y + 36 + 1.3F * milieu(11.0F) - milieu(14.0F), SOUS_TITRE, 0xFFFFFF, alpha);
      }
   }

   private static void renderExecuting(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
      ExecutionType type = ExecutionType.byIndex(me.style);
      if (me.revive < 96) {
         Entity partner = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
         Component name = (Component)(partner != null ? partner.getDisplayName() : Component.literal("?"));
         int y = h - (int)(h * 0.12F) - 26;
         texte(g, mc.font, Component.translatable("hud.reanimation.executing", new Object[]{type.displayName(), name}), w / 2.0F, y, Ui.FONT, OR, 1.0F);
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
         texte(g, mc.font, Component.translatable("hud.reanimation.being_executed", new Object[]{name}), w / 2.0F, y, Ui.FONT, ROUGE, 1.0F);
         texte(g, mc.font, type.displayName(), w / 2.0F, y + 12, Ui.FONT, OR, 1.0F);
      }
   }

   // ------------------------------------------------------------------ dessin

   /**
    * Grand titre en capitales espacées (DM Sans gras 26, à sa vraie taille) avec un halo de sa couleur. {@code pop} :
    * agrandissement passager de l'apparition, exactement 1 une fois le titre posé (lettres alors sur les points de l'écran).
    * Écart des lettres en unités d'interface ; halo, ombre et milieu des capitales repris de l'ancien titre (gras 11 agrandi
    * {@link #POSE} fois).
    */
   private static void titre(GuiGraphics g, Font font, String s, float cx, float y, float pop, int rgb, float alpha, float espace) {
      int a = Math.round(255 * Mth.clamp(alpha, 0.0F, 1.0F));
      if (!Ui.lisible(a)) return;
      String up = s.toUpperCase(Locale.ROOT);
      g.pose().pushPose();
      g.pose().translate(cx, y, 0.0F);
      if (pop != 1.0F) g.pose().scale(pop, pop, 1.0F);
      float tw = Ui.spacedWidth(font, up, BANDEAU, espace);
      float haut = POSE * milieu(11.0F) - milieu(26.0F);
      int halo = a * 60 / 255;
      if (Ui.lisible(halo)) {
         for (int[] d : HALO) lettres(g, font, up, -tw / 2.0F + d[0] * 0.7F * POSE, haut + d[1] * 0.7F * POSE, Ui.argb(rgb, halo), espace);
      }
      int ombre = a * 150 / 255;
      if (Ui.lisible(ombre)) lettres(g, font, up, -tw / 2.0F + 0.6F * POSE, haut + 0.8F * POSE, Ui.argb(0x000000, ombre), espace);
      lettres(g, font, up, -tw / 2.0F, haut, Ui.argb(rgb, a), espace);
      g.pose().popPose();
   }

   /** Capitales espacées du titre (comme {@link Ui#spaced}), chaque lettre posée sur un point de l'écran. */
   private static void lettres(GuiGraphics g, Font font, String up, float x, float y, int argb, float espace) {
      float cx = x;
      for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
         Component c = Ui.lit(new String(Character.toChars(up.codePointAt(i))), BANDEAU);
         g.pose().pushPose();
         placer(g, cx, y);
         g.drawString(font, c, 0, 0, argb, false);
         g.pose().popPose();
         cx += font.width(c) + espace;
      }
   }

   /** Texte DM Sans centré, avec ombre (lisible sur le monde) : police à sa vraie taille, posée sur un point de l'écran. */
   private static void texte(GuiGraphics g, Font font, Component c, float cx, float y, ResourceLocation police, int rgb, float alpha) {
      int a = Math.round(255 * Mth.clamp(alpha, 0.0F, 1.0F));
      if (!Ui.lisible(a)) return;
      Component cc = Ui.in(c, police);
      g.pose().pushPose();
      placer(g, cx - font.width(cc) / 2, y);
      g.drawString(font, cc, 0, 0, Ui.argb(rgb, a), true);
      g.pose().popPose();
   }

   /** Milieu des capitales sous le point de dessin, pour une police DM Sans du mod de cette taille (décalage 1). */
   private static float milieu(float taille) {
      return taille * 0.49309F - 1.0F;
   }

   /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes ; repris du kit des Caisses). */
   private static void placer(GuiGraphics g, float x, float y) {
      Matrix4f m = g.pose().last().pose();
      double s = Minecraft.getInstance().getWindow().getGuiScale();
      float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
      float rx = (float)(Math.round(ex * s) / s), ry = (float)(Math.round(ey * s) / s);
      float kx = m.m00() == 0.0F ? 1.0F : m.m00(), ky = m.m11() == 0.0F ? 1.0F : m.m11();
      g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0.0F);
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

}
