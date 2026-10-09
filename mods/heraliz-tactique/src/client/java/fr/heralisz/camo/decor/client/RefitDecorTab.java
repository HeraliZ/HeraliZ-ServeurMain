package fr.heralisz.camo.decor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.client.RefitSkinTab;
import fr.heralisz.camo.decor.DecorData;
import fr.heralisz.camo.decor.DecorItems;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.DecorPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Onglet « Personnaliser » du menu de modification TaCZ : stickers (5 au plus), compteur StatTrack.
 * <p>On choisit un élément (dans l'inventaire, ou un élément déjà posé à déplacer) : un aperçu suit la souris sur l'arme ; clic
 * gauche = coller, molette = tourner, Maj + molette = taille, clic droit = annuler. Le serveur revérifie tout (DecorPacket).
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class RefitDecorTab {
   public static boolean open;

   /** Placement en cours : type (0 sticker, 2 StatTrack), emplacement d'inventaire (nouvel élément) ou index (déplacement). */
   private record Placing(int kind, String id, int finish, int slot, int index) {
   }

   @Nullable private static Placing placing;
   private static float rot, scale = 1;
   @Nullable private static DecorPick.Hit hover;

   private RefitDecorTab() {
   }

   public static void addWidgets(GunRefitScreen screen, Consumer<AbstractWidget> add) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null) return;
      ItemStack gun = player.getMainHandItem();
      if (IGun.getIGunOrNull(gun) == null) return;
      if (RefitTransform.getCurrentTransformType() != AttachmentType.NONE) open = false;
      int tabX = screen.width - 30 - 18 * 6 - 6 - 22;
      add.accept(new TabButton(tabX, 10, screen));
      if (!open) {
         stop();
         return;
      }
      int x = 8, y = 30;
      Inventory inv = player.getInventory();
      List<DecorData.Sticker> stickers = DecorData.stickers(gun);
      boolean st = DecorData.hasStatTrak(gun);
      add.accept(new Label(x, y, 200, Component.translatable("gui.heralisz_camo.decor.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
      y += 14;
      // éléments déjà posés
      add.accept(new Label(x, y, 200, Component.translatable("gui.heralisz_camo.decor.stickers", stickers.size(), DecorData.MAX_STICKERS)));
      y += 12;
      for (int i = 0; i < stickers.size(); i++) {
         int idx = i;
         DecorData.Sticker s = stickers.get(i);
         add.accept(new Label(x + 4, y + 2, 90, Component.translatable("sticker.heralisz_camo." + s.id()).withStyle(ChatFormatting.GRAY)));
         add.accept(Button.builder(Component.translatable("gui.heralisz_camo.decor.move"), b -> begin(new Placing(0, s.id(), s.finish(), -1, idx), s.at()))
            .bounds(x + 96, y, 52, 14).build());
         add.accept(Button.builder(Component.translatable("gui.heralisz_camo.decor.scrape"), b -> send(DecorPacket.SCRAPE_STICKER, -1, idx, null))
            .bounds(x + 150, y, 52, 14).build());
         y += 16;
      }
      if (st) {
         DecorData.Placement at = DecorData.statTrak(gun);
         add.accept(new Label(x + 4, y + 2, 90, Component.translatable("gui.heralisz_camo.decor.stattrack").withStyle(ChatFormatting.GRAY)));
         add.accept(Button.builder(Component.translatable("gui.heralisz_camo.decor.move"), b -> begin(new Placing(2, "stattrack", 0, -1, 0), at))
            .bounds(x + 96, y, 52, 14).build());
         y += 16;
      }
      // objets de l'inventaire
      y += 4;
      add.accept(new Label(x, y, 200, Component.translatable("gui.heralisz_camo.decor.inventory")));
      y += 12;
      int col = 0, shown = 0;
      for (int i = 0; i < inv.getContainerSize() && shown < 40; i++) {
         ItemStack s = inv.getItem(i);
         Placing p = null;
         if (s.getItem() instanceof DecorItems.StickerItem si && stickers.size() < DecorData.MAX_STICKERS) {
            p = new Placing(0, si.design.id(), si.finish.ordinal(), i, -1);
         } else if (s.getItem() instanceof DecorItems.StatTrakItem && !st) {
            p = new Placing(2, "stattrack", 0, i, -1);
         }
         if (p == null) continue;
         add.accept(new DecorSlot(x + col * 19, y, i, inv, p));
         shown++;
         if (++col == 10) {
            col = 0;
            y += 19;
         }
      }
      if (shown == 0) add.accept(new Label(x, y + 2, 200, Component.translatable("gui.heralisz_camo.decor.none").withStyle(ChatFormatting.GRAY)));
   }

   private static void begin(Placing p, @Nullable DecorData.Placement from) {
      placing = p;
      rot = from != null ? from.rot() : 0;
      // StatTrack : l'échelle est recalculée d'après l'arme et la surface visée
      scale = from != null && p.kind != 2 ? from.scale() : 1;
      hover = null;
      DecorRender.ghost = null;
   }

   /** Éléments déjà posés sur l'arme (le StatTrack automatique les évite). */
   public static List<DecorData.Placement> placedOn(ItemStack gun) {
      List<DecorData.Placement> out = new java.util.ArrayList<>();
      for (DecorData.Sticker s : DecorData.stickers(gun)) out.add(s.at());
      return out;
   }

   private static void stop() {
      placing = null;
      hover = null;
      DecorRender.ghost = null;
   }

   private static void send(int action, int slot, int index, @Nullable DecorData.Placement at) {
      CamoNetwork.CHANNEL.sendToServer(new DecorPacket(action, slot, index, at));
   }

   @Nullable
   private static DecorData.Placement placementOf(DecorPick.Hit h) {
      return placementFor(placing == null ? 0 : placing.kind, h, scale, rot);
   }

   /** Pose d'un élément visé : le StatTrack est ajusté à la surface (à plat, sans déborder), sans rotation libre. */
   @Nullable
   public static DecorData.Placement placementFor(int kind, @Nullable DecorPick.Hit h, float scale, float rot) {
      if (h == null) return null;
      if (kind == 2) {
         DecorPick.Fit fit = DecorPick.fitPlate(h, scale);
         if (fit == null) return null;
         h = fit.hit();
         scale = fit.scale();
         rot = 0;
      }
      Vector3f p = h.point(), n = h.normal(), u = h.up();
      return new DecorData.Placement(h.bone(), p.x, p.y, p.z, n.x, n.y, n.z, u.x, u.y, u.z, scale, rot);
   }

   // ------------------------------------------------------------------ souris : aperçu, coller, tourner, annuler
   @SubscribeEvent
   public static void onRender(ScreenEvent.Render.Pre e) {
      if (!(e.getScreen() instanceof GunRefitScreen) || placing == null) return;
      Minecraft mc = Minecraft.getInstance();
      hover = overWidget(e.getScreen(), e.getMouseX(), e.getMouseY()) ? null
         : DecorPick.pick(e.getMouseX(), e.getMouseY(), e.getScreen().width, e.getScreen().height);
      DecorData.Placement at = placementOf(hover);
      DecorRender.ghost = at == null ? null : new DecorRender.Ghost(placing.kind, placing.id, placing.finish, at, placing.index);
   }

   @SubscribeEvent
   public static void onRenderPost(ScreenEvent.Render.Post e) {
      if (!(e.getScreen() instanceof GunRefitScreen) || placing == null) return;
      GuiGraphics g = e.getGuiGraphics();
      var font = Minecraft.getInstance().font;
      g.drawCenteredString(font, Component.translatable("gui.heralisz_camo.decor.hint"), e.getScreen().width / 2, e.getScreen().height - 28, 0xFFE8A0);
   }

   @SubscribeEvent
   public static void onClick(ScreenEvent.MouseButtonPressed.Pre e) {
      if (!(e.getScreen() instanceof GunRefitScreen) || placing == null) return;
      if (e.getButton() == 1) {
         stop();
         e.setCanceled(true);
         return;
      }
      if (e.getButton() != 0 || overWidget(e.getScreen(), e.getMouseX(), e.getMouseY())) return;
      DecorPick.Hit h = DecorPick.pick(e.getMouseX(), e.getMouseY(), e.getScreen().width, e.getScreen().height);
      DecorData.Placement at = placementOf(h);
      if (at == null) return;
      Placing p = placing;
      int action = switch (p.kind) {
         case 0 -> p.slot >= 0 ? DecorPacket.ADD_STICKER : DecorPacket.MOVE_STICKER;
         default -> p.slot >= 0 ? DecorPacket.SET_STATTRACK : DecorPacket.MOVE_STATTRACK;
      };
      send(action, p.slot, p.index, at);
      stop();
      e.setCanceled(true);
   }

   @SubscribeEvent
   public static void onScroll(ScreenEvent.MouseScrolled.Pre e) {
      if (!(e.getScreen() instanceof GunRefitScreen) || placing == null) return;
      if (Screen.hasShiftDown()) {
         scale = Math.max(0.4F, Math.min(2.5F, scale * (e.getScrollDelta() > 0 ? 1.1F : 1 / 1.1F)));
      } else {
         rot = (rot + (e.getScrollDelta() > 0 ? 15 : -15)) % 360;
      }
      e.setCanceled(true);
   }

   @SubscribeEvent
   public static void onClose(ScreenEvent.Closing e) {
      if (e.getScreen() instanceof GunRefitScreen) {
         open = false;
         stop();
      }
   }

   /** Rafraîchit l'onglet quand le serveur a modifié l'arme (le NBT arrive un tick plus tard). */
   private static String lastSeen = "";

   @SubscribeEvent
   public static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
      if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || !open) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || !(mc.screen instanceof GunRefitScreen screen)) return;
      ItemStack gun = mc.player.getMainHandItem();
      String now = gun.getTag() != null ? String.valueOf(gun.getTag().get(DecorData.KEY)) : "";
      int decorItems = 0;
      for (ItemStack s : mc.player.getInventory().items) {
         if (s.getItem() instanceof DecorItems.StickerItem || s.getItem() instanceof DecorItems.StatTrakItem) {
            decorItems += s.getCount();
         }
      }
      now += "|" + decorItems;
      if (!now.equals(lastSeen)) {
         lastSeen = now;
         screen.init(mc, screen.width, screen.height);
      }
   }

   private static boolean overWidget(Screen screen, double mx, double my) {
      for (GuiEventListener l : screen.children()) {
         if (l instanceof AbstractWidget w && w.visible && w.isMouseOver(mx, my)) return true;
      }
      return false;
   }

   // ------------------------------------------------------------------ widgets
   private static final class TabButton extends AbstractButton {
      private final GunRefitScreen screen;

      TabButton(int x, int y, GunRefitScreen screen) {
         super(x, y, 18, 18, Component.translatable("gui.heralisz_camo.decor.tab"));
         this.screen = screen;
      }

      @Override
      public void onPress() {
         open = !open;
         if (open) {
            RefitSkinTab.open = false;
            RefitTransform.changeRefitScreenView(AttachmentType.NONE);
         } else {
            stop();
         }
         Minecraft mc = Minecraft.getInstance();
         this.screen.init(mc, this.screen.width, this.screen.height);
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         RenderSystem.enableBlend();
         if (open || this.isHoveredOrFocused()) {
            g.blit(GunRefitScreen.SLOT_TEXTURE, this.getX(), this.getY(), 0.0F, 0.0F, this.width, this.height, 18, 18);
         } else {
            g.blit(GunRefitScreen.SLOT_TEXTURE, this.getX() + 1, this.getY() + 1, 1.0F, 1.0F, this.width - 2, this.height - 2, 18, 18);
         }
         g.renderItem(new ItemStack(DecorItems.STATTRAK.get()), this.getX() + 1, this.getY() + 1);
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

   private static final class DecorSlot extends AbstractButton {
      private final int slot;
      private final Inventory inv;
      private final Placing p;

      DecorSlot(int x, int y, int slot, Inventory inv, Placing p) {
         super(x, y, 18, 18, Component.empty());
         this.slot = slot;
         this.inv = inv;
         this.p = p;
      }

      @Override
      public void onPress() {
         if (this.p.kind == 2) {
            // StatTrack : posé tout seul à la meilleure place, à la taille de l'arme ; « Déplacer » pour le mettre ailleurs
            Screen screen = Minecraft.getInstance().screen;
            DecorPick.Fit fit = screen == null ? null : DecorPick.autoPlate(screen.width, screen.height, placedOn(Minecraft.getInstance().player.getMainHandItem()));
            if (fit != null) {
               DecorData.Placement at = placementFor(2, fit.hit(), fit.scale(), 0);
               if (at != null) {
                  stop();
                  send(DecorPacket.SET_STATTRACK, this.slot, -1, new DecorData.Placement(at.bone(), at.px(), at.py(), at.pz(), at.nx(), at.ny(), at.nz(),
                     at.ux(), at.uy(), at.uz(), fit.scale(), 0));
                  return;
               }
            }
         }
         begin(this.p, null);
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         boolean sel = placing != null && placing.slot == this.slot;
         RenderSystem.enableBlend();
         if (sel || this.isHoveredOrFocused()) {
            g.blit(GunRefitScreen.SLOT_TEXTURE, this.getX(), this.getY(), 0.0F, 0.0F, this.width, this.height, 18, 18);
         } else {
            g.blit(GunRefitScreen.SLOT_TEXTURE, this.getX() + 1, this.getY() + 1, 1.0F, 1.0F, this.width - 2, this.height - 2, 18, 18);
         }
         ItemStack stack = this.inv.getItem(this.slot);
         g.renderItem(stack, this.getX() + 1, this.getY() + 1);
         g.renderItemDecorations(Minecraft.getInstance().font, stack, this.getX() + 1, this.getY() + 1);
         RenderSystem.enableDepthTest();
         if (this.isHovered()) g.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
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
         g.drawString(Minecraft.getInstance().font, this.getMessage(), this.getX(), this.getY(), 0xFFFFFF);
      }

      @Override
      public boolean isMouseOver(double mx, double my) {
         return false;
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
      }
   }
}
