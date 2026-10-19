package fr.heralisz.tactical.client.overlay;

import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.item.IConsumable;
import fr.heralisz.tactical.api.item.ICustomItem;
import fr.heralisz.tactical.api.item.IThrowable;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import fr.heraliz.tactique.client.kit.Ui;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Jauges d'usage autour du réticule (maquette validée « petites jauges ») : un anneau qui se remplit pendant un soin (vert),
 * pendant qu'on dégoupille une grenade (blanc), puis la mèche (rouge, qui se vide jusqu'à l'explosion) ; rien pendant la
 * recharge des coups. Sous l'anneau : le nom de l'objet et le temps qui reste ; « Lancer doux
 * (accroupi) » quand on est accroupi avec une grenade ; pour un soin qui dure tant qu'on le garde, la touche pour arrêter.
 */
public class UsingProgressOverlay implements IGuiOverlay {
   private static final ResourceLocation NOM = new ResourceLocation("heraliz_tactique", "ui_moyen_bold"),
         MINI = new ResourceLocation("heraliz_tactique", "ui_mini"), MINI_GRAS = new ResourceLocation("heraliz_tactique", "ui_mini_bold");
   private static final int VERT = 0x5FD08A, VERT_PALE = 0xCFE9D8, ROUGE = 0xFF4A3D, ROUGE_PALE = 0xFFB3AC, GRIS = 0x9AA3B0;
   private static final float R_INT = 10.5F, R_EXT = 12.0F;

   @Override
   public void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player == null || mc.options.hideGui) return;
      float cx = w / 2.0F, cy = h / 2.0F;
      Font font = mc.font;
      ItemStack stack = player.getUseItem();
      if (stack.getItem() instanceof ICustomItem item) {
         int ticks = player.getTicksUsingItem();
         float max = Math.max(1, item.getMaxUsingTick(stack));
         float p = Math.min(1.0F, ticks / max);
         // pleine : le blanc respire doucement en attendant le lâcher
         int pulse = p >= 1.0F ? Math.round(150 + 70 * Mth.sin(ticks / 2.0F)) : 220;
         String nom = stack.getHoverName().getString();
         if (stack.getItem() instanceof IThrowable throwable) {
            ThrowableData data = throwable.getThrowableIndex(stack).map(ThrowableIndex::getData).orElse(null);
            int life = data == null ? 0 : data.getEntityData().getLifeTime();
            if (data != null && data.isCookable() && life > 0 && ticks >= data.getPrepareTime()) {
               // dégoupillée : la mèche se vide (rouge), le temps avant l'explosion en dessous
               float reste = Math.max(0, life - (ticks - data.getPrepareTime()) - partialTick);
               anneau(g, cx, cy, 1.0F, Ui.argb(0xFFFFFF, 140), 0);
               anneau(g, cx, cy, reste / life, Ui.argb(ROUGE, 255), ROUGE);
               nom(g, font, nom, cx, cy);
               detail(g, font, Component.translatable("overlay.heraliz_tactique.explose", secondes(reste)).getString(), cx, cy, ROUGE_PALE);
            } else {
               anneau(g, cx, cy, 1.0F, Ui.argb(0xFFFFFF, 46), 0);
               anneau(g, cx, cy, p, Ui.argb(0xFFFFFF, pulse), 0);
               nom(g, font, nom, cx, cy);
            }
            if (player.isCrouching()) lancerDoux(g, font, cx, cy + 36);
         } else if (stack.getItem() instanceof IConsumable) {
            ConsumableIndex index = HeraliszTacticalAPI.getConsumableIndex(stack).orElse(null);
            boolean bascule = index != null && index.getData().isToggleUse();
            anneau(g, cx, cy, 1.0F, Ui.argb(0xFFFFFF, 46), 0);
            anneau(g, cx, cy, p, Ui.argb(VERT, p >= 1.0F ? pulse : 255), VERT);
            nom(g, font, nom, cx, cy);
            if (bascule && p >= 1.0F) {
               arreter(g, font, mc.options.keyUse.getTranslatedKeyMessage().getString(), cx, cy + 25);
            } else if (p < 1.0F) {
               float reste = Math.max(0, max - ticks - partialTick);
               detail(g, font, Component.translatable("overlay.heraliz_tactique.encore", secondes(reste)).getString(), cx, cy, VERT_PALE);
            }
         } else {
            anneau(g, cx, cy, 1.0F, Ui.argb(0xFFFFFF, 46), 0);
            anneau(g, cx, cy, p, Ui.argb(0xFFFFFF, pulse), 0);
         }
         return;
      }
      // recharge des coups de mêlée et des couteaux : plus d'anneau (demande du propriétaire, 19/10) ; le délai reste
      // le même, vérifié par le serveur
   }

   /** Anneau autour du réticule, de midi jusqu'à la fraction {@code f} ; {@code lueur} : couleur d'un halo (0 : aucun). */
   private static void anneau(GuiGraphics g, float cx, float cy, float f, int argb, int lueur) {
      if (f <= 0.0F) return;
      if (lueur != 0) Ui.anneau(g, cx, cy, R_INT - 1.0F, R_EXT + 1.0F, 0.0F, f, Ui.argb(lueur, 50));
      Ui.anneau(g, cx, cy, R_INT, R_EXT, 0.0F, f, argb);
   }

   /** « 1,8 » (une décimale, virgule en français). */
   private static String secondes(float ticks) {
      return String.format(Ui.french() ? Locale.FRANCE : Locale.ROOT, "%.1f", ticks / 20.0F);
   }

   private static void nom(GuiGraphics g, Font font, String s, float cx, float cy) {
      centre(g, font, Ui.lit(s, NOM), cx, cy + 16, 0xFFFFFF);
   }

   private static void detail(GuiGraphics g, Font font, String s, float cx, float cy, int rgb) {
      centre(g, font, Ui.lit(s, MINI), cx, cy + 26, rgb);
   }

   /** Texte centré avec une ombre douce (lisible sur le monde). */
   private static void centre(GuiGraphics g, Font font, MutableComponent c, float cx, float y, int rgb) {
      float x = cx - font.width(c) / 2.0F;
      g.pose().pushPose();
      g.pose().translate(x + 0.5F, y + 0.7F, 0.0F);
      g.drawString(font, c, 0, 0, Ui.argb(0x000000, 150), false);
      g.pose().translate(-0.5F, -0.7F, 0.0F);
      g.drawString(font, c, 0, 0, Ui.argb(rgb, 255), false);
      g.pose().popPose();
   }

   /** Pastille « ▼ Lancer doux (accroupi) » centrée. */
   private static void lancerDoux(GuiGraphics g, Font font, float cx, float y) {
      String t = Component.translatable("overlay.heraliz_tactique.lancer_doux").getString().toUpperCase(Locale.ROOT);
      float tw = Ui.spacedWidth(font, t, MINI_GRAS, 0.8F);
      int lw = Math.round(5 + 5 + 3 + tw + 5), x0 = Math.round(cx - lw / 2.0F), y0 = Math.round(y);
      Ui.soft(g, x0, y0, lw, 9, Ui.argb(GRIS, 34));
      Ui.softOutline(g, x0 + 1, y0 + 1, lw - 2, 7, Ui.argb(GRIS, 90));
      int fleche = Ui.argb(Ui.mix(GRIS, 0xFFFFFF, 0.3F), 255);
      g.fill(x0 + 5, y0 + 3, x0 + 10, y0 + 4, fleche);
      g.fill(x0 + 6, y0 + 4, x0 + 9, y0 + 5, fleche);
      g.fill(x0 + 7, y0 + 5, x0 + 8, y0 + 6, fleche);
      Ui.spaced(g, font, t, MINI_GRAS, x0 + 13, y0 + 0.5F, fleche, 0.8F);
   }

   /** « [Clic droit] pour arrêter », centré. */
   private static void arreter(GuiGraphics g, Font font, String touche, float cx, float y) {
      MutableComponent k = Ui.lit(touche, MINI_GRAS), suite = Ui.lit(Component.translatable("overlay.heraliz_tactique.pour_arreter").getString(), MINI);
      int kw = font.width(k) + 8, total = kw + 5 + font.width(suite);
      int x0 = Math.round(cx - total / 2.0F), y0 = Math.round(y);
      Ui.soft(g, x0, y0, kw, 10, Ui.argb(0xFFFFFF, 32));
      Ui.softOutline(g, x0 + 1, y0 + 1, kw - 2, 8, Ui.argb(0xFFFFFF, 64));
      g.drawString(font, k, x0 + 4, y0 + 1, Ui.argb(0xFFFFFF, 255), false);
      centre(g, font, suite, x0 + kw + 5 + font.width(suite) / 2.0F, y0 + 1, VERT_PALE);
   }
}
