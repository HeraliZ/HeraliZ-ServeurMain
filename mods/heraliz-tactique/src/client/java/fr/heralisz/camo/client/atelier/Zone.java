package fr.heralisz.camo.client.atelier;

import fr.heralisz.camo.client.kit.SonsUi;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Zone cliquable de l'atelier : l'écran fournit son dessin et ses actions (clic, double-clic). Son de clic du kit des
 * écrans de clan.
 */
final class Zone extends AbstractWidget {
   interface Dessin {
      void dessiner(GuiGraphics g, Zone z, int mouseX, int mouseY, float partialTick);
   }

   /**
    * Dernier clic (heure et zone) : un clic reconstruit souvent l'écran, le second clic d'un double-clic tombe alors sur
    * une nouvelle zone à la même place. Le double-clic se reconnaît donc à la place, pas à l'objet.
    */
   private static long dernierClic;
   private static int dernierX, dernierY, dernierW, dernierH;

   private final Dessin dessin;
   @Nullable private final Runnable clic;
   @Nullable private final Runnable doubleClic;

   Zone(int x, int y, int w, int h, Dessin dessin, @Nullable Runnable clic, @Nullable Runnable doubleClic) {
      super(x, y, w, h, Component.empty());
      this.dessin = dessin;
      this.clic = clic;
      this.doubleClic = doubleClic;
      this.active = clic != null || doubleClic != null;
   }

   boolean survol() {
      return this.isHovered();
   }

   @Override
   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      this.dessin.dessiner(g, this, mouseX, mouseY, partialTick);
   }

   @Override
   public void onClick(double mouseX, double mouseY) {
      long t = Util.getMillis();
      boolean memePlace = dernierX == this.getX() && dernierY == this.getY() && dernierW == this.width && dernierH == this.height;
      boolean double_ = this.doubleClic != null && memePlace && t - dernierClic < 350L;
      dernierClic = double_ ? 0L : t;
      dernierX = this.getX();
      dernierY = this.getY();
      dernierW = this.width;
      dernierH = this.height;
      if (double_) {
         this.doubleClic.run();
      } else if (this.clic != null) {
         this.clic.run();
      }
   }

   @Override
   public void playDownSound(SoundManager sounds) {
      SonsUi.clic(0.6F);
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput out) {
   }
}
