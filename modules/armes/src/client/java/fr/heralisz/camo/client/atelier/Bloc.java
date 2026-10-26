package fr.heralisz.camo.client.atelier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Surface d'un panneau, invisible : la souris y est « sur l'interface ». Un clic sur un panneau ne colle donc pas un
 * sticker sur l'arme qui est derrière (RefitDecorTab ne vise l'arme que hors des éléments de l'écran). Ne prend aucun clic.
 */
final class Bloc extends AbstractWidget {
   Bloc(int x, int y, int w, int h) {
      super(x, y, w, h, Component.empty());
   }

   @Override
   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return false;
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput out) {
   }
}
