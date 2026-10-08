package fr.heralisz.tactical.client.tooltip;

import java.util.ArrayList;
import java.util.List;
import fr.heralisz.tactical.util.TooltipLine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public abstract class AbstractClientItemTooltip implements ClientTooltipComponent {
   private static final int LINE_HEIGHT = 10;
   private static final int MAX_DESC_WIDTH = 300;
   private static final int MAX_VISIBLE_DESC_LINES = 3;
   private static final int MAX_VISIBLE_EFFECT_LINES = 3;
   @Nullable
   private List<FormattedCharSequence> desc;
   private final List<Component> normalLines = new ArrayList<>();
   private final List<Component> effectLines = new ArrayList<>();
   @Nullable
   private Component moreHint;
   @Nullable
   private Component shiftHint;
   private int maxWidth;

   protected void build(@Nullable String tooltipKey, List<TooltipLine> lines) {
      Font font = Minecraft.getInstance().font;
      if (tooltipKey != null && !tooltipKey.isEmpty()) {
         this.desc = font.split(Component.translatable(tooltipKey), 300);

         for (FormattedCharSequence sequence : this.desc) {
            this.maxWidth = Math.max(font.width(sequence), this.maxWidth);
         }
      }

      for (TooltipLine line : lines) {
         this.maxWidth = Math.max(font.width(line.text()), this.maxWidth);
         if (line.collapsible()) {
            this.effectLines.add(line.text());
         } else {
            this.normalLines.add(line.text());
         }
      }

      boolean descCollapsed = this.desc != null && this.desc.size() > 3;
      boolean effectsCollapsed = this.effectLines.size() > 3;
      if (effectsCollapsed) {
         this.moreHint = Component.translatable("tooltip.heralisz_tactical.more_lines", new Object[]{this.effectLines.size() - 3});
         this.maxWidth = Math.max(font.width(this.moreHint), this.maxWidth);
      }

      if (descCollapsed || effectsCollapsed) {
         this.shiftHint = Component.translatable("tooltip.heralisz_tactical.shift_hint").withStyle(ChatFormatting.ITALIC);
         this.maxWidth = Math.max(font.width(this.shiftHint), this.maxWidth);
      }
   }

   private boolean isShiftDown() {
      return Screen.hasShiftDown();
   }

   private List<FormattedCharSequence> getVisibleDesc() {
      if (this.desc == null) {
         return List.of();
      } else {
         return !this.isShiftDown() && this.desc.size() > 3 ? this.desc.subList(0, 3) : this.desc;
      }
   }

   private List<Component> getVisibleLines() {
      boolean shift = this.isShiftDown();
      List<Component> visible = new ArrayList<>(this.normalLines.size() + this.effectLines.size() + 2);
      visible.addAll(this.normalLines);
      boolean collapsed = !shift && this.effectLines.size() > 3;
      if (collapsed) {
         visible.addAll(this.effectLines.subList(0, 3));
         if (this.moreHint != null) {
            visible.add(this.moreHint);
         }
      } else {
         visible.addAll(this.effectLines);
      }

      if (!shift && this.desc != null && this.desc.size() > 3) {
         collapsed = true;
      }

      if (collapsed && this.shiftHint != null) {
         visible.add(this.shiftHint);
      }

      return visible;
   }

   public int getHeight() {
      int height = 0;
      if (!this.getVisibleDesc().isEmpty()) {
         height += this.getVisibleDesc().size() * 10 + 2;
      }

      return height + this.getVisibleLines().size() * 10;
   }

   public int getWidth(Font font) {
      return this.maxWidth;
   }

   public void renderText(Font font, int pX, int pY, Matrix4f matrix4f, BufferSource bufferSource) {
      int yOffset = pY;
      List<FormattedCharSequence> visibleDesc = this.getVisibleDesc();
      if (!visibleDesc.isEmpty()) {
         yOffset += 2;

         for (FormattedCharSequence sequence : visibleDesc) {
            font.drawInBatch(sequence, pX, yOffset, 11184810, false, matrix4f, bufferSource, DisplayMode.NORMAL, 0, 15728880);
            yOffset += 10;
         }
      }

      for (Component component : this.getVisibleLines()) {
         font.drawInBatch(component, pX, yOffset, 16755200, false, matrix4f, bufferSource, DisplayMode.NORMAL, 0, 15728880);
         yOffset += 10;
      }
   }

   public void renderImage(Font font, int mouseX, int mouseY, GuiGraphics guiGraphics) {
   }
}
