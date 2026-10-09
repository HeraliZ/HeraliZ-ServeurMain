package fr.heralisz.camo.client.atelier;

import fr.heralisz.camo.client.kit.HzButton;
import fr.heralisz.camo.client.kit.Ui;
import fr.heralisz.camo.decor.DecorCatalog;
import fr.heralisz.camo.decor.DecorData;
import fr.heralisz.camo.decor.DecorItems;
import fr.heralisz.camo.decor.client.RefitDecorTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Onglet Personnaliser : à gauche ce qui est collé sur l'arme (stickers, 5 au plus, et le StatTrack), à déplacer ou à
 * gratter ; à droite les stickers et modules StatTrack de l'inventaire. Choisir un sticker, puis cliquer sur l'arme pour
 * le coller (molette : tourner, Maj + molette : taille, clic droit : annuler) ; le StatTrack se pose tout seul. Placement
 * et envoi au serveur : RefitDecorTab (le serveur revérifie tout).
 */
final class OngletPerso implements AtelierArme.Partie {
   private static final int CASE = 22, COLONNES = 6, RANG = 20;

   private final AtelierArme a;
   private List<DecorData.Sticker> stickers = List.of();
   private boolean statTrack;
   private List<Integer> objets = List.of();
   private int gx, gy, gw, gh, listeY;
   private int dx, dy, dw, dh, grilleY, aideY;

   OngletPerso(AtelierArme a) {
      this.a = a;
   }

   @Override
   public void construire() {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null) return;
      ItemStack gun = AtelierArme.arme();
      Font font = this.a.police();
      this.stickers = DecorData.stickers(gun);
      this.statTrack = DecorData.hasStatTrak(gun);

      // à gauche : ce qui est collé
      this.gx = 16;
      this.gy = 58;
      this.gw = 180;
      this.listeY = this.gy + 40;
      int y = this.listeY;
      String deplacer = AtelierArme.tr("deplacer"), gratter = AtelierArme.tr("gratter");
      int wd = (int) Ui.spacedWidth(font, deplacer.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 2;
      int wg = (int) Ui.spacedWidth(font, gratter.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 2;
      int droite = this.gx + this.gw - 12;
      for (int i = 0; i < this.stickers.size(); i++) {
         int index = i;
         this.a.ajouter(new HzButton(droite - wg, y + 5, wg, 10, Component.literal(gratter), Ui.DANGER, () -> RefitDecorTab.gratterSticker(index))
            .look(HzButton.Look.LINK).danger());
         this.a.ajouter(new HzButton(droite - wg - 10 - wd, y + 5, wd, 10, Component.literal(deplacer), Ui.accent(), () -> RefitDecorTab.deplacerSticker(index))
            .look(HzButton.Look.LINK));
         y += RANG;
      }
      if (this.statTrack) {
         this.a.ajouter(new HzButton(droite - wd, y + 5, wd, 10, Component.literal(deplacer), Ui.accent(), RefitDecorTab::deplacerStatTrack)
            .look(HzButton.Look.LINK));
         y += RANG;
      }
      if (this.stickers.isEmpty() && !this.statTrack) y += 16;
      this.gh = y - this.gy + 10;
      this.a.bloc(this.gx, this.gy, this.gw, this.gh);

      // à droite : les objets de l'inventaire
      Inventory inv = p.getInventory();
      List<Integer> o = new ArrayList<>();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.getItem() instanceof DecorItems.StickerItem || s.getItem() instanceof DecorItems.StatTrakItem) o.add(i);
      }
      this.objets = o;
      this.dw = 24 + COLONNES * CASE + (COLONNES - 1) * 2;
      this.dx = this.a.width - 16 - this.dw;
      this.dy = 58;
      this.grilleY = this.dy + 28;
      int bas = this.a.basPanneaux(24);
      int rangsMax = Math.max(1, (bas - this.grilleY - 40) / (CASE + 2));
      int n = Math.min(this.objets.size(), rangsMax * COLONNES);
      for (int k = 0; k < n; k++) {
         int slot = this.objets.get(k);
         int x = this.dx + 12 + (k % COLONNES) * (CASE + 2);
         int yy = this.grilleY + (k / COLONNES) * (CASE + 2);
         this.a.ajouter(new Zone(x, yy, CASE, CASE, (g, z, mx, my, pt) -> this.dessinerCase(g, z, slot), () -> RefitDecorTab.choisirObjet(slot), null));
      }
      int rangs = n == 0 ? 0 : (n + COLONNES - 1) / COLONNES;
      this.aideY = this.grilleY + (rangs == 0 ? 16 : rangs * (CASE + 2) + 6);
      int aideH = Dessins.hauteur(font, Ui.in(AtelierArme.trc("decor_aide"), Ui.SMALL), this.dw - 24, 9);
      this.dh = this.aideY + aideH + 8 - this.dy;
      this.a.bloc(this.dx, this.dy, this.dw, this.dh);
   }

   @Override
   public String signature() {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null) return "";
      StringBuilder sb = new StringBuilder();
      ItemStack gun = AtelierArme.arme();
      sb.append(gun.getTag() != null ? String.valueOf(gun.getTag().get(DecorData.KEY)) : "").append('|');
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.getItem() instanceof DecorItems.StickerItem || s.getItem() instanceof DecorItems.StatTrakItem) {
            sb.append(i).append(':').append(s.getItem()).append('x').append(s.getCount()).append(';');
         }
      }
      return sb.toString();
   }

   // ------------------------------------------------------------------ dessin
   @Override
   public void dessiner(GuiGraphics g, int mx, int my, float pt) {
      Font font = this.a.police();
      // ce qui est collé
      Ui.glass(g, this.gx, this.gy, this.gw, this.gh);
      Ui.section(g, font, AtelierArme.tr("sur_arme"), this.gx + 12, this.gy + 11, this.gw - 24);
      g.drawString(font, Ui.lit(AtelierArme.tr("stickers", this.stickers.size(), DecorData.MAX_STICKERS), Ui.FONT), this.gx + 12, this.gy + 25,
         Ui.argb(Ui.TEXT, 255), false);
      int y = this.listeY;
      for (int i = 0; i < this.stickers.size(); i++) {
         DecorData.Sticker s = this.stickers.get(i);
         boolean bouge = RefitDecorTab.enDeplacement(i, false);
         if (bouge) Ui.soft(g, this.gx + 6, y + 1, this.gw - 12, RANG - 2, Ui.argb(Ui.accent(), 34));
         int couleur = couleurFinition(s.finish());
         Ui.point(g, this.gx + 12, y + 7, couleur, 255);
         String nom = Component.translatable("sticker.heralisz_camo." + s.id()).getString();
         nom = Ui.ellipsis(font, nom, Ui.FONT, this.gw - 24 - 10 - 90);
         g.drawString(font, Ui.lit(nom, Ui.FONT), this.gx + 22, y + 5, Ui.argb(Ui.TEXT, 230), false);
         y += RANG;
      }
      if (this.statTrack) {
         if (RefitDecorTab.enDeplacement(0, true)) Ui.soft(g, this.gx + 6, y + 1, this.gw - 12, RANG - 2, Ui.argb(Ui.accent(), 34));
         Ui.point(g, this.gx + 12, y + 7, Ui.AMBRE, 255);
         g.drawString(font, Ui.lit(AtelierArme.tr("stattrack"), Ui.FONT), this.gx + 22, y + 5, Ui.argb(Ui.TEXT, 230), false);
      }
      if (this.stickers.isEmpty() && !this.statTrack) {
         g.drawString(font, Ui.lit(AtelierArme.tr("aucun_decor"), Ui.SMALL), this.gx + 12, this.listeY + 2, Ui.argb(Ui.MUTED, 255), false);
      }
      // objets de l'inventaire
      Ui.glass(g, this.dx, this.dy, this.dw, this.dh);
      Ui.section(g, font, AtelierArme.tr("inventaire"), this.dx + 12, this.dy + 11, this.dw - 24);
      if (this.objets.isEmpty()) {
         g.drawString(font, Ui.lit(AtelierArme.tr("decor_inventaire_vide"), Ui.SMALL), this.dx + 12, this.grilleY + 2, Ui.argb(Ui.MUTED, 255), false);
      }
      Component aide = AtelierArme.trc(this.stickers.size() >= DecorData.MAX_STICKERS ? "stickers_pleins" : "decor_aide");
      Dessins.paragraphe(g, font, Ui.in(aide, Ui.SMALL), this.dx + 12, this.aideY, this.dw - 24, Ui.argb(Ui.FAINT, 255), 9);
   }

   /** Pendant un placement : l'aide en bas de l'écran. */
   @Override
   public void dessinerDessus(GuiGraphics g, int mx, int my, float pt) {
      if (!RefitDecorTab.enPose(-1)) return;
      Font font = this.a.police();
      Component t = Ui.in(AtelierArme.trc("pose_aide"), Ui.SMALL);
      int w = Math.min(this.a.width - 32, font.width(t) + 32);
      int x = (this.a.width - w) / 2, y = this.a.height - 14 - 22;
      Ui.glass(g, x, y, w, 22);
      Ui.centered(g, font, t, this.a.width / 2f, y + 8, Ui.argb(Ui.TEXT, 255));
   }

   private void dessinerCase(GuiGraphics g, Zone z, int slot) {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null) return;
      ItemStack s = p.getInventory().getItem(slot);
      if (s.isEmpty()) return;
      boolean choisie = RefitDecorTab.enPose(slot);
      boolean posable = RefitDecorTab.posable(s);
      int x = z.getX(), y = z.getY();
      int accent = Ui.accent();
      Ui.soft(g, x, y, CASE, CASE, choisie ? Ui.argb(accent, 40) : Ui.argb(0xFFFFFF, z.survol() && posable ? 16 : 8));
      Ui.softOutline(g, x + 1, y + 1, CASE - 2, CASE - 2, choisie ? Ui.argb(accent, 220) : Ui.argb(0xFFFFFF, z.survol() && posable ? 60 : 22));
      g.renderItem(s, x + 3, y + 3);
      g.renderItemDecorations(this.a.police(), s, x + 3, y + 3);
      if (!posable) {
         g.pose().pushPose();
         g.pose().translate(0, 0, 300);
         g.fill(x + 1, y + 1, x + CASE - 1, y + CASE - 1, Ui.argb(0x0D0F14, 150));
         g.pose().popPose();
      }
      if (z.survol()) {
         List<Component> l = new ArrayList<>();
         l.add(Ui.in(s.getHoverName(), Ui.BOLD));
         l.add(Ui.in(AtelierArme.trc(!posable ? (s.getItem() instanceof DecorItems.StatTrakItem ? "stattrack_deja" : "stickers_pleins")
            : s.getItem() instanceof DecorItems.StatTrakItem ? "stattrack_aide" : "sticker_aide"), Ui.FONT)
            .withStyle(st -> st.withColor(posable ? Ui.MUTED : Ui.AMBRE)));
         this.a.infobulle(l);
      }
   }

   private static int couleurFinition(int finition) {
      DecorCatalog.Finish[] f = DecorCatalog.Finish.values();
      if (finition < 0 || finition >= f.length) return Ui.MUTED;
      Integer c = f[finition].color.getColor();
      return c == null ? Ui.MUTED : c;
   }
}
