package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.client.kit.Ui;
import fr.heraliz.reanimation.menu.SearchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Fouille d'un joueur a terre : style des interfaces HeraliZ (verre, DM Sans, capitales espacees) ; memes cases, memes
 * positions que le menu (l'echelle reste celle du joueur, comme un inventaire).
 */
public class SearchScreen extends AbstractContainerScreen<SearchMenu> {
   public SearchScreen(SearchMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
      this.imageWidth = 176;
      this.imageHeight = 226;
      this.titleLabelX = 8;
      this.titleLabelY = 6;
      this.inventoryLabelX = 8;
      this.inventoryLabelY = 132;
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      Ui.fond(g, this.width, this.height);
      super.render(g, mouseX, mouseY, partialTick);
      this.renderTooltip(g, mouseX, mouseY);
   }

   protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
      int x = this.leftPos;
      int y = this.topPos;
      int accent = Ui.accent();
      Ui.glass(g, x - 4, y - 4, this.imageWidth + 8, this.imageHeight + 8, 225);
      // bandeau de titre : leger degrade d'accent qui s'efface vers la droite
      Ui.band(g, x - 2, y - 2, x + this.imageWidth + 2, y + 16, accent, 26, 60);
      Ui.hairline(g, x + 4, x + this.imageWidth - 4, y + 16, 0xFFFFFF, 40, false, true);
      Ui.hairline(g, x + 6, x + this.imageWidth - 6, y + 132 - 5, 0xFFFFFF, 30, true, true);

      for (Slot slot : this.menu.slots) {
         int sx = x + slot.x - 1;
         int sy = y + slot.y - 1;
         boolean target = slot.index < 41;
         boolean hover = this.hoveredSlot == slot;
         Ui.soft(g, sx, sy, 18, 18, Ui.argb(0x000000, target ? 120 : 95));
         Ui.softOutline(g, sx + 1, sy + 1, 16, 16, hover ? Ui.argb(accent, 200) : Ui.argb(0xFFFFFF, target ? 26 : 18));
      }
   }

   protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
      g.drawString(this.font, Ui.in(this.title, Ui.BOLD), this.titleLabelX, this.titleLabelY - 1, Ui.argb(0xFFFFFF, 255), false);
      Ui.spaced(g, this.font, Component.translatable("gui.reanimation.backpack").getString(), Ui.SMALL, 8, 40.5F, Ui.argb(Ui.MUTED, 255), 1.0F);
      Ui.spaced(g, this.font, this.playerInventoryTitle.getString(), Ui.SMALL, this.inventoryLabelX, this.inventoryLabelY + 0.5F,
         Ui.argb(Ui.MUTED, 255), 1.0F);
      ClientDowned.Info info = ClientDowned.get(this.menu.targetId());
      if (info != null && info.has(1)) {
         String timer = Component.translatable("gui.reanimation.bleed", (info.bleed + 19) / 20).getString();
         int w = (int)Ui.spacedWidth(this.font, timer.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 0.6F) + 10;
         Ui.chip(g, this.font, timer, this.imageWidth - 8 - w, 23, Ui.DANGER);
      }
   }
}
