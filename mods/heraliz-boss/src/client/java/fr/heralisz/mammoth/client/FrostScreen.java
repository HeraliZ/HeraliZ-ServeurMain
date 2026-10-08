package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.mammoth.HeraliszMammoth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Givre d'Ymirak sur l'écran : des fougères de glace poussent depuis les bords, un voile blanc au plus fort (souffle, nova). */
public final class FrostScreen {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszMammoth.MODID, "textures/gui/frost_screen.png");
   private static final ResourceLocation CRACK = new ResourceLocation(HeraliszMammoth.MODID, "textures/gui/crack_screen.png");
   private static float level, white, crack, punch;
   private static int crackRot;
   private static long holdUntil, lastNanos;

   private FrostScreen() {}

   public static void add(float amount, float whiteout, int hold) {
      Minecraft mc = Minecraft.getInstance();
      level = Mth.clamp(level + amount, 0, 1);
      white = Math.max(white, whiteout);
      if (mc.level != null) holdUntil = Math.max(holdUntil, mc.level.getGameTime() + hold);
   }

   /** Coup encaissé : l'écran se fend comme de la glace, le champ de vision se contracte d'un coup (0..1). */
   public static void hit(float strength) {
      // plus de givre fendu à l'écran : seul l'à-coup du champ de vision reste
      punch = Math.max(punch, Mth.clamp(strength, 0, 1));
      crackRot = (crackRot + 1 + (int) (Math.random() * 3)) % 4;
   }

   /** Multiplicateur du champ de vision (à-coup à l'impact). */
   public static float fovScale() {
      return 1F - punch * 0.09F;
   }

   public static float level() {
      return level;
   }

   public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      long now = System.nanoTime();
      float dt = lastNanos == 0 ? 0 : Math.min(0.1F, (now - lastNanos) / 1e9F);
      lastNanos = now;
      if (mc.level == null || mc.player == null) {
         level = white = 0;
         return;
      }
      if (mc.level.getGameTime() > holdUntil) level = Math.max(0, level - dt * 0.35F);
      white = Math.max(0, white - dt * 0.9F);
      crack = Math.max(0, crack - dt * 0.8F);
      punch = Math.max(0, punch - dt * 4.5F);
      if (level <= 0.003F && white <= 0.003F && crack <= 0.003F) return;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      float breathe = 0.94F + 0.06F * Mth.sin((mc.level.getGameTime() + partial) * 0.05F);
      // le givre pousse depuis les bords : on agrandit l'image quand il est faible (seuls les coins entrent dans l'écran)
      float grow = 1.9F - 0.9F * level;
      int iw = (int) (w * grow), ih = (int) (h * grow);
      g.setColor(1F, 1F, 1F, Mth.clamp(level * 1.3F, 0, 1) * breathe);
      g.blit(TEX, (w - iw) / 2, (h - ih) / 2, iw, ih, 0, 0, 512, 512, 512, 512);
      g.setColor(1F, 1F, 1F, 1F);
      if (crack > 0.003F) {
         g.pose().pushPose();
         g.pose().translate(w / 2F, h / 2F, 0);
         g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(crackRot * 90));
         int s = Math.max(w, h);
         g.setColor(1F, 1F, 1F, Math.min(1F, crack * 1.4F));
         g.blit(CRACK, -s / 2, -s / 2, s, s, 0, 0, 512, 512, 512, 512);
         g.setColor(1F, 1F, 1F, 1F);
         g.pose().popPose();
      }
      if (white > 0) g.fill(0, 0, w, h, ((int) (Mth.clamp(white, 0, 1) * 200) << 24) | 0xE8F6FF);
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }
}
