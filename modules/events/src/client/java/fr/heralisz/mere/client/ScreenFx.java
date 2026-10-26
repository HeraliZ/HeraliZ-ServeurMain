package fr.heralisz.mere.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * L'écran du joueur : coup encaissé (à-coup du champ de vision, voile rouge sur les bords), voile de cendre quand l'air brûle
 * (phase 3, pluie de braises), éclair blanc (la lumière de la rosace à la mort du roi).
 */
public final class ScreenFx {
   private static float ash, white, blood, punch;
   private static long holdUntil, lastNanos;

   private ScreenFx() {}

   public static void add(float ashAmount, float whiteout, int hold) {
      Minecraft mc = Minecraft.getInstance();
      ash = Mth.clamp(ash + ashAmount, 0, 1);
      white = Math.max(white, whiteout);
      if (mc.level != null) holdUntil = Math.max(holdUntil, mc.level.getGameTime() + hold);
   }

   public static void hit(float strength) {
      punch = Math.max(punch, Mth.clamp(strength, 0, 1));
      blood = Math.max(blood, Mth.clamp(strength * 1.2F, 0, 1));
   }

   public static float fovScale() {
      return 1F - punch * 0.09F;
   }

   public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      long now = System.nanoTime();
      float dt = lastNanos == 0 ? 0 : Math.min(0.1F, (now - lastNanos) / 1e9F);
      lastNanos = now;
      if (mc.level == null || mc.player == null) {
         ash = white = blood = 0;
         return;
      }
      if (mc.level.getGameTime() > holdUntil) ash = Math.max(0, ash - dt * 0.3F);
      white = Math.max(0, white - dt * 0.6F);
      blood = Math.max(0, blood - dt * 1.6F);
      punch = Math.max(0, punch - dt * 4.5F);
      if (ash <= 0.003F && white <= 0.003F && blood <= 0.003F) return;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      int edge = Math.max(w, h) / 3;
      if (ash > 0.003F) vignette(g, w, h, edge, 0x0E0A08, Mth.clamp(ash, 0, 1) * 0.75F);
      if (blood > 0.003F) vignette(g, w, h, edge / 2, 0x7A0806, blood * 0.55F);
      if (white > 0) g.fill(0, 0, w, h, ((int) (Mth.clamp(white, 0, 1) * 220) << 24) | 0xFFF4E0);
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   /** Bords assombris (quatre dégradés) de couleur c. */
   private static void vignette(GuiGraphics g, int w, int h, int e, int c, float a) {
      int A = (int) (Mth.clamp(a, 0, 1) * 255) << 24;
      g.fillGradient(0, 0, w, e, A | c, c);
      g.fillGradient(0, h - e, w, h, c, A | c);
      g.pose().pushPose();
      g.pose().translate(0, h, 0);
      g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
      g.fillGradient(0, 0, h, e, A | c, c);
      g.pose().popPose();
      g.pose().pushPose();
      g.pose().translate(w, 0, 0);
      g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
      g.fillGradient(0, 0, h, e, A | c, c);
      g.pose().popPose();
   }
}
