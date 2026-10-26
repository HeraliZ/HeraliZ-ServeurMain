package fr.heralisz.camo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.CollectionVue;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.item.CamoItem;
import fr.heralisz.camo.knife.KnifeSkins;
import fr.heralisz.camo.network.ApplyCamoPacket;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.CollectionCamoPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Onglet « Skin » ajouté au menu de modification TaCZ : à gauche des emplacements d'accessoires.
 * Ouvert, il montre deux rangées de cases : les camos de l'inventaire (posés depuis l'objet : définitifs) et la collection
 * à vie du joueur (CollectionVue). Un clic = aperçu sur l'arme, puis bouton « Appliquer ». Un camo à vie ne se remet pas
 * pendant le délai qui suit une mort avec l'arme (case grisée, minutes restantes). Le serveur revérifie tout.
 */
public final class RefitSkinTab {
   public static boolean open;
   @Nullable public static Camo preview;
   private static int previewSlot = -1;
   /** L'aperçu vient de la collection à vie (sinon d'un objet de l'inventaire, previewSlot). */
   private static boolean previewVie;
   private static int page;
   @Nullable private static Camo lastSeen;
   @Nullable private static UUID lastOwner;
   private static int lastVersion = -1, lastRecharges = -1;

   private static final int COLONNES = 6, CASE = 18, MAX_INVENTAIRE = 12;
   /** Couleurs du kit des écrans de clan : or (camos à vie), gris (textes secondaires), rouge (délai). */
   private static final int OR = 0xFFE8C36A, OR_TEXTE = 0xE8C36A, GRIS_TEXTE = 0x8F95A0, ROUGE = 0xFFFF6B6B;

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
         previewVie = false;
      }
      Camo current = CamoData.get(gun);
      UUID owner = CamoData.owner(gun);
      lastSeen = current;
      lastOwner = owner;
      lastVersion = CollectionVue.version();
      lastRecharges = CollectionVue.enRecharge();
      int tabX = screen.width - 30 - 18 * 6 - 6;
      add.accept(new TabButton(tabX, 10, screen, current));
      if (!open) return;

      int right = screen.width - 12;
      int gridX = right - COLONNES * CASE;
      if (current != null && owner == null) {
         add.accept(new Label(screen.width - 320, 34, right, Component.translatable("gui.heralisz_camo.locked",
            Component.translatable(current.nameKey()).withStyle(current.tier.color, ChatFormatting.BOLD))));
         return;
      }
      boolean aMoi = current != null && player.getUUID().equals(owner);
      ResourceLocation gunId = gunIdOf(gun);

      // ligne d'action : appliquer l'aperçu choisi, sinon le camo à vie posé et le bouton pour le retirer
      if (preview != null && previewVie) {
         long reste = CollectionVue.rechargeRestanteMs(preview);
         Camo camo = preview;
         Component msg = reste > 0
            ? Component.translatable("gui.heralisz_camo.cooldown", nom(camo), minutes(reste))
            : Component.translatable("gui.heralisz_camo.apply_life", nom(camo));
         Button b = Button.builder(msg, btn -> {
            CamoNetwork.CHANNEL.sendToServer(new CollectionCamoPacket(CollectionCamoPacket.APPLIQUER, camo.id));
            player.playSound(SoundEvents.SMITHING_TABLE_USE, 0.8F, 1.2F);
            preview = null;
            previewVie = false;
         }).bounds(screen.width - 250, 30, 238, 16).build();
         b.active = reste <= 0 && !(aMoi && current == camo);
         add.accept(b);
      } else if (preview != null && previewSlot >= 0) {
         Component msg = Component.translatable("gui.heralisz_camo.apply", nom(preview));
         add.accept(Button.builder(msg, b -> {
            CamoNetwork.CHANNEL.sendToServer(new ApplyCamoPacket(previewSlot));
            player.playSound(SoundEvents.SMITHING_TABLE_USE, 0.8F, 1.2F);
            preview = null;
            previewSlot = -1;
         }).bounds(screen.width - 250, 30, 238, 16).build());
      } else if (aMoi) {
         add.accept(new Label(screen.width - 320, 34, right - 64, Component.translatable("gui.heralisz_camo.life_on",
            Component.translatable(current.nameKey()).withStyle(current.tier.color, ChatFormatting.BOLD))));
         add.accept(Button.builder(Component.translatable("gui.heralisz_camo.remove"), b -> {
            CamoNetwork.CHANNEL.sendToServer(new CollectionCamoPacket(CollectionCamoPacket.RETIRER, current.id));
            player.playSound(SoundEvents.BRUSH_GENERIC, 0.8F, 1.0F);
         }).bounds(right - 60, 30, 60, 16).build());
      }

      // camos de l'inventaire (définitifs)
      int y = 52;
      add.accept(new Label(gridX - 120, y, right, Component.translatable("gui.heralisz_camo.section_inventory").withStyle(s -> s.withColor(GRIS_TEXTE))));
      y += 12;
      Inventory inv = player.getInventory();
      int shown = 0;
      for (int i = 0; i < inv.getContainerSize() && shown < MAX_INVENTAIRE; i++) {
         if (inv.getItem(i).getItem() instanceof CamoItem ci && ci.camo().fits(gunId)
            && !fr.heraliz.tactique.avie.ObjetsAVie.estSkinAVie(ci.camo())) {
            add.accept(new CamoSlot(gridX + shown % COLONNES * CASE, y + shown / COLONNES * CASE, i, inv, screen));
            shown++;
         }
      }
      if (shown == 0) {
         add.accept(new Label(gridX - 120, y + 2, right, Component.translatable("gui.heralisz_camo.none").withStyle(ChatFormatting.DARK_GRAY)));
         y += 14;
      } else {
         y += (shown + COLONNES - 1) / COLONNES * CASE;
      }

      // collection à vie
      List<Camo> collection = new ArrayList<>();
      for (Camo c : CollectionVue.possedes()) {
         if (!c.isPackAPunch() && c.fits(gunId)) collection.add(c);
      }
      y += 6;
      add.accept(new Label(gridX - 120, y, right, Component.translatable("gui.heralisz_camo.section_collection", collection.size()).withStyle(s -> s.withColor(OR_TEXTE))));
      y += 12;
      if (collection.isEmpty()) {
         add.accept(new Label(gridX - 40, y + 2, right, Component.translatable("gui.heralisz_camo.collection_none").withStyle(ChatFormatting.DARK_GRAY)));
         return;
      }
      int rangees = Math.max(1, (screen.height - 34 - y) / CASE);
      int parPage = rangees * COLONNES;
      int pages = (collection.size() + parPage - 1) / parPage;
      page = Math.max(0, Math.min(page, pages - 1));
      int debut = page * parPage;
      for (int k = debut; k < Math.min(collection.size(), debut + parPage); k++) {
         int n = k - debut;
         add.accept(new VieSlot(gridX + n % COLONNES * CASE, y + n / COLONNES * CASE, collection.get(k), aMoi && current == collection.get(k), screen));
      }
      if (pages > 1) {
         int py = y + rangees * CASE + 4;
         Button prec = Button.builder(Component.literal("<"), b -> {
            page--;
            screen.init(Minecraft.getInstance(), screen.width, screen.height);
         }).bounds(gridX, py, 16, 14).build();
         prec.active = page > 0;
         Button suiv = Button.builder(Component.literal(">"), b -> {
            page++;
            screen.init(Minecraft.getInstance(), screen.width, screen.height);
         }).bounds(right - 16, py, 16, 14).build();
         suiv.active = page < pages - 1;
         add.accept(prec);
         add.accept(suiv);
         add.accept(new Label(gridX + 20, py + 1, right - 20, Component.literal((page + 1) + " / " + pages).withStyle(s -> s.withColor(GRIS_TEXTE)), true));
      }
   }

   @Nullable
   private static ResourceLocation gunIdOf(ItemStack stack) {
      IGun g = IGun.getIGunOrNull(stack);
      return g != null ? g.getGunId(stack) : null;
   }

   private static Component nom(Camo camo) {
      return Component.translatable(camo.nameKey()).withStyle(camo.tier.color);
   }

   private static long minutes(long ms) {
      return Math.max(1L, (ms + 59_999L) / 60_000L);
   }

   /**
    * Rafraîchit l'écran quand le serveur a changé le camo de l'arme (le NBT arrive un tick plus tard), quand la collection
    * arrive ou change, et quand un délai se termine. Appelé depuis le tick client, jamais pendant le dessin (reconstruire
    * les widgets en plein rendu = crash).
    */
   static void refreshIfChanged(GunRefitScreen screen) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      ItemStack gun = mc.player.getMainHandItem();
      if (CamoData.get(gun) != lastSeen || !Objects.equals(CamoData.owner(gun), lastOwner)
         || CollectionVue.version() != lastVersion || CollectionVue.enRecharge() != lastRecharges) {
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

   private static void contour(GuiGraphics g, int x, int y, int w, int h, int couleur) {
      g.fill(x, y, x + w, y + 1, couleur);
      g.fill(x, y + h - 1, x + w, y + h, couleur);
      g.fill(x, y + 1, x + 1, y + h - 1, couleur);
      g.fill(x + w - 1, y + 1, x + w, y + h - 1, couleur);
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

   /** Camo de l'inventaire (objet) : posé, il est définitif. */
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
            boolean same = !previewVie && preview == item.camo() && previewSlot == this.slot;
            preview = same ? null : item.camo();
            previewSlot = same ? -1 : this.slot;
            previewVie = false;
            Minecraft mc = Minecraft.getInstance();
            this.screen.init(mc, this.screen.width, this.screen.height);
         }
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         blitSlot(g, this.getX(), this.getY(), this.width, this.height, this.isHoveredOrFocused() || !previewVie && previewSlot == this.slot);
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

   /** Camo de la collection à vie : grisé avec les minutes restantes pendant le délai qui suit une mort. */
   private static final class VieSlot extends AbstractButton {
      private final Camo camo;
      private final boolean surArme;
      private final GunRefitScreen screen;
      private final ItemStack icone;

      VieSlot(int x, int y, Camo camo, boolean surArme, GunRefitScreen screen) {
         super(x, y, 18, 18, Component.translatable(camo.nameKey()));
         this.camo = camo;
         this.surArme = surArme;
         this.screen = screen;
         this.icone = new ItemStack(HeraliszCamo.CAMO_ITEMS.get(camo).get());
      }

      @Override
      public void onPress() {
         boolean same = previewVie && preview == this.camo;
         preview = same ? null : this.camo;
         previewVie = !same;
         previewSlot = -1;
         Minecraft mc = Minecraft.getInstance();
         this.screen.init(mc, this.screen.width, this.screen.height);
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         Font font = Minecraft.getInstance().font;
         blitSlot(g, this.getX(), this.getY(), this.width, this.height, this.isHoveredOrFocused() || previewVie && preview == this.camo);
         g.renderItem(this.icone, this.getX() + 1, this.getY() + 1);
         long reste = CollectionVue.rechargeRestanteMs(this.camo);
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 200.0F);
         if (reste > 0) {
            g.fill(this.getX() + 1, this.getY() + 1, this.getX() + 17, this.getY() + 17, 0xAA000000);
            String m = minutes(reste) + "m";
            g.drawString(font, m, this.getX() + 9 - font.width(m) / 2, this.getY() + 5, ROUGE);
         }
         if (this.surArme) {
            contour(g, this.getX(), this.getY(), this.width, this.height, OR);
         }
         g.pose().popPose();
         RenderSystem.enableDepthTest();
         if (this.isHovered()) {
            List<Component> lignes = new ArrayList<>();
            lignes.add(Component.translatable(this.camo.nameKey()).withStyle(this.camo.tier.color, ChatFormatting.BOLD));
            lignes.add(Component.translatable(this.camo.tier.key()).withStyle(this.camo.tier.color));
            lignes.add(Component.translatable("gui.heralisz_camo.tip_life").withStyle(ChatFormatting.GOLD));
            if (this.surArme) lignes.add(Component.translatable("gui.heralisz_camo.tip_on_gun").withStyle(ChatFormatting.GREEN));
            if (reste > 0) lignes.add(Component.translatable("gui.heralisz_camo.tip_cooldown", minutes(reste)).withStyle(ChatFormatting.RED));
            g.renderComponentTooltip(font, lignes, mouseX, mouseY);
         }
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
         this.defaultButtonNarrationText(out);
      }
   }

   /** Texte aligné à droite sur {@code right} (ou à gauche sur x si {@code gauche}), coupé en lignes au-delà de la largeur. */
   private static final class Label extends AbstractWidget {
      private final int right;
      private final boolean gauche;

      Label(int x, int y, int right, Component text) {
         this(x, y, right, text, false);
      }

      Label(int x, int y, int right, Component text, boolean gauche) {
         super(x, y, Math.max(1, right - x), 12, text);
         this.right = right;
         this.gauche = gauche;
         this.active = false;
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
         Font font = Minecraft.getInstance().font;
         int y = this.getY() + 2;
         for (FormattedCharSequence ligne : font.split(this.getMessage(), Math.max(40, this.right - this.getX()))) {
            int x = this.gauche ? this.getX() : this.right - font.width(ligne);
            g.drawString(font, ligne, x, y, 0xFFFFFF);
            y += 10;
         }
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
      }
   }

   private RefitSkinTab() {}
}
