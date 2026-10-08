package fr.heralisz.camo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.item.CamoItem;
import fr.heralisz.camo.knife.KnifeSkins;
import fr.heralisz.camo.network.ApplyCamoPacket;
import fr.heralisz.camo.network.CamoNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Onglet « Skin » ajouté au menu de modification TaCZ : à gauche des emplacements d'accessoires.
 * Ouvert, il liste les camos de l'inventaire ; un clic = aperçu sur l'arme, puis bouton « Appliquer » (définitif).
 */
public final class RefitSkinTab {
   public static boolean open;
   @Nullable public static Camo preview;
   private static int previewSlot = -1;
   @Nullable private static Camo lastSeen;

   public static void addWidgets(GunRefitScreen screen, Consumer<AbstractWidget> add) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null) return;
      ItemStack gun = player.getMainHandItem();
      if (IGun.getIGunOrNull(gun) == null || KnifeSkins.of(gun) != null) return;   // les couteaux ont des skins uniques
      if (RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
         open = false;
      }
      if (!open) {
         preview = null;
         previewSlot = -1;
      }
      Camo current = CamoData.get(gun);
      lastSeen = current;
      int tabX = screen.width - 30 - 18 * 6 - 6;
      add.accept(new TabButton(tabX, 10, screen, current));
      if (!open) return;

      if (current != null) {
         add.accept(new Label(screen.width - 250, 34, 220, Component.translatable("gui.heralisz_camo.locked",
            Component.translatable(current.nameKey()).withStyle(current.tier.color, ChatFormatting.BOLD))));
         return;
      }
      Inventory inv = player.getInventory();
      int x = screen.width - 30, y = 50, shown = 0;
      for (int i = 0; i < inv.getContainerSize() && shown < 8; i++) {
         if (inv.getItem(i).getItem() instanceof CamoItem ci && ci.camo().fits(gunIdOf(player.getMainHandItem()))) {
            add.accept(new CamoSlot(x, y, i, inv, screen));
            y += 18;
            shown++;
         }
      }
      if (shown == 0) {
         add.accept(new Label(screen.width - 250, 34, 220, Component.translatable("gui.heralisz_camo.none").withStyle(ChatFormatting.GRAY)));
      }
      if (preview != null && previewSlot >= 0) {
         Component msg = Component.translatable("gui.heralisz_camo.apply", Component.translatable(preview.nameKey()).withStyle(preview.tier.color));
         add.accept(Button.builder(msg, b -> {
            CamoNetwork.CHANNEL.sendToServer(new ApplyCamoPacket(previewSlot));
            player.playSound(SoundEvents.SMITHING_TABLE_USE, 0.8F, 1.2F);
            preview = null;
            previewSlot = -1;
         }).bounds(screen.width - 250, 30, 212, 16).build());
      }
   }

   @javax.annotation.Nullable
   private static net.minecraft.resources.ResourceLocation gunIdOf(net.minecraft.world.item.ItemStack stack) {
      com.tacz.guns.api.item.IGun g = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
      return g != null ? g.getGunId(stack) : null;
   }

   /**
    * Rafraîchit l'écran quand le serveur a posé le camo (le NBT arrive un tick plus tard).
    * Appelé depuis le tick client, jamais pendant le dessin (reconstruire les widgets en plein rendu = crash).
    */
   static void refreshIfChanged(GunRefitScreen screen) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && CamoData.get(mc.player.getMainHandItem()) != lastSeen) {
         screen.init(mc, screen.width, screen.height);
      }
   }

   private static void blitSlot(GuiGraphics g, int x, int y, int w, int h, boolean highlight) {
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      if (highlight) {
         g.blit(GunRefitScreen.SLOT_TEXTURE, x, y, 0.0F, 0.0F, w, h, 18, 18);
      } else {
         g.blit(GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1.0F, 1.0F, w - 2, h - 2, 18, 18);
      }
   }

   private static final class TabButton extends AbstractButton {
      private final GunRefitScreen screen;
      @Nullable private final Camo current;

      TabButton(int x, int y, GunRefitScreen screen, @Nullable Camo current) {
         super(x, y, 18, 18, Component.translatable("gui.heralisz_camo.tab"));
         this.screen = screen;
         this.current = current;
      }

      @Override
      public void onPress() {
         open = !open;
         if (open) {
            fr.heralisz.camo.decor.client.RefitDecorTab.open = false;
            RefitTransform.changeRefitScreenView(AttachmentType.NONE);
         }
         Minecraft mc = Minecraft.getInstance();
         this.screen.init(mc, this.screen.width, this.screen.height);
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         blitSlot(g, this.getX(), this.getY(), this.width, this.height, open || this.isHoveredOrFocused());
         Camo icon = this.current != null ? this.current : Camo.DARK_MATTER;
         g.renderItem(new ItemStack(HeraliszCamo.CAMO_ITEMS.get(icon).get()), this.getX() + 1, this.getY() + 1);
         if (this.current == null && !open) {
            g.fill(this.getX() + 1, this.getY() + 1, this.getX() + 17, this.getY() + 17, 0x88000000);
         }
         RenderSystem.enableDepthTest();
         if (this.isHoveredOrFocused()) {
            g.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + 20, 0xFFFFFF);
         }
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
         this.defaultButtonNarrationText(out);
      }
   }

   private static final class CamoSlot extends AbstractButton {
      private final int slot;
      private final Inventory inv;
      private final GunRefitScreen screen;

      CamoSlot(int x, int y, int slot, Inventory inv, GunRefitScreen screen) {
         super(x, y, 18, 18, Component.empty());
         this.slot = slot;
         this.inv = inv;
         this.screen = screen;
      }

      @Override
      public void onPress() {
         if (this.inv.getItem(this.slot).getItem() instanceof CamoItem item) {
            boolean same = preview == item.camo() && previewSlot == this.slot;
            preview = same ? null : item.camo();
            previewSlot = same ? -1 : this.slot;
            Minecraft mc = Minecraft.getInstance();
            this.screen.init(mc, this.screen.width, this.screen.height);
         }
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         blitSlot(g, this.getX(), this.getY(), this.width, this.height, this.isHoveredOrFocused() || previewSlot == this.slot);
         ItemStack stack = this.inv.getItem(this.slot);
         g.renderItem(stack, this.getX() + 1, this.getY() + 1);
         g.renderItemDecorations(Minecraft.getInstance().font, stack, this.getX() + 1, this.getY() + 1);
         RenderSystem.enableDepthTest();
         if (this.isHovered()) {
            g.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
         }
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
         this.defaultButtonNarrationText(out);
      }
   }

   private static final class Label extends AbstractWidget {
      Label(int x, int y, int w, Component text) {
         super(x, y, w, 12, text);
         this.active = false;
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         g.drawString(Minecraft.getInstance().font, this.getMessage(), this.getX(), this.getY() + 2, 0xFFFFFF);
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
      }
   }

   private RefitSkinTab() {}
}
