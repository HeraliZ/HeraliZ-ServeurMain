package fr.heralisz.camo.client.atelier;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.CollectionVue;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.client.RefitSkinTab;
import fr.heralisz.camo.client.kit.HzButton;
import fr.heralisz.camo.client.kit.Ui;
import fr.heralisz.camo.item.CamoItem;
import fr.heralisz.camo.network.ApplyCamoPacket;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.CollectionCamoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Onglet Camo : à gauche le camo de l'arme et ce que veulent dire « à vie » et « objet » ; à droite les camos du joueur
 * en cartes (l'arme photographiée avec le camo), collection à vie ou objets de l'inventaire ; en bas, l'aperçu choisi à
 * confirmer. Un clic montre le camo sur l'arme (aperçu, chez le joueur seulement) ; « Appliquer » l'envoie au serveur, qui
 * revérifie tout (possession, délai après une mort, arme compatible, camo définitif).
 */
final class OngletCamo implements AtelierArme.Partie {
   private enum Vue { VIE, INVENTAIRE }

   private static final int COLONNES = 3, CARTE_H = 58, ECART = 4, BANDEAU_H = 40;

   private final AtelierArme a;
   private Vue vue = Vue.VIE;
   /** Première rangée affichée (molette). */
   private int defil;
   @Nullable
   private Camo apercu;
   private boolean apercuVie;
   private int apercuSlot = -1;
   /** Copies de l'arme tenue avec chaque camo (cartes) ; refaites si l'arme change. */
   private final Map<Camo, ItemStack> rendus = new EnumMap<>(Camo.class);
   private String rendusArme = "";
   // état de l'arme et du joueur (construire)
   @Nullable
   private Camo courant;
   private boolean aMoi, definitif, autre;
   private List<Camo> vie = List.of();
   private List<Integer> objets = List.of();
   // géométrie
   private int gx, gy, gw, gh;
   private int dx, dy, dw, dh, grilleY, grilleH, carteW, rangs, rangsVisibles, piedY;
   private int bx, by, bw;

   OngletCamo(AtelierArme a) {
      this.a = a;
   }

   // ------------------------------------------------------------------ construction
   @Override
   public void construire() {
      LocalPlayer p = Minecraft.getInstance().player;
      ItemStack gun = AtelierArme.arme();
      IGun iGun = IGun.getIGunOrNull(gun);
      if (p == null || iGun == null) return;
      Font font = this.a.police();
      Inventory inv = p.getInventory();
      ResourceLocation gunId = iGun.getGunId(gun);
      this.courant = CamoData.get(gun);
      UUID owner = CamoData.owner(gun);
      this.aMoi = this.courant != null && p.getUUID().equals(owner);
      this.definitif = this.courant != null && owner == null;
      this.autre = this.courant != null && owner != null && !this.aMoi;
      List<Camo> v = new ArrayList<>();
      for (Camo c : CollectionVue.possedes()) {
         if (!c.isPackAPunch() && c.fits(gunId)) v.add(c);
      }
      this.vie = v;
      List<Integer> o = new ArrayList<>();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         // skins à vie (Séraphin, Chaînes de l'Enfer) : seulement depuis la collection, jamais posés depuis l'objet
         if (inv.getItem(i).getItem() instanceof CamoItem ci && ci.camo().fits(gunId)
            && !fr.heraliz.tactique.avie.ObjetsAVie.estSkinAVie(ci.camo())) o.add(i);
      }
      this.objets = o;
      // l'aperçu choisi existe-t-il encore ?
      if (this.definitif) {
         this.apercu = null;
      } else if (this.apercu != null) {
         boolean ok = this.apercuVie ? this.vie.contains(this.apercu)
            : this.apercuSlot >= 0 && inv.getItem(this.apercuSlot).getItem() instanceof CamoItem ci && ci.camo() == this.apercu;
         if (!ok) this.apercu = null;
      }
      if (this.apercu == null) this.apercuSlot = -1;
      RefitSkinTab.preview = this.apercu;
      String cle = gunId + "|" + iGun.getGunDisplayId(gun);
      if (!cle.equals(this.rendusArme)) {
         this.rendus.clear();
         this.rendusArme = cle;
      }

      // à gauche : le camo de l'arme
      this.gx = 16;
      this.gy = 58;
      this.gw = 150;
      this.gh = this.hauteurGauche(font);
      this.a.bloc(this.gx, this.gy, this.gw, this.gh);
      if (this.aMoi) {
         Camo c = this.courant;
         String t = AtelierArme.tr("retirer");
         int w = (int) Ui.spacedWidth(font, t.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 2;
         this.a.ajouter(new HzButton(this.gx + this.gw - 12 - w, this.gy + 41, w, 10, Component.literal(t), Ui.DANGER, () -> this.retirer(c))
            .look(HzButton.Look.LINK).danger());
      }

      // à droite : les camos du joueur (rien à choisir sur un camo définitif)
      this.dw = 0;
      if (!this.definitif) this.construireDroite(font, inv);

      // en bas : l'aperçu à confirmer
      if (this.apercu != null) this.construireBandeau();
   }

   private void construireDroite(Font font, Inventory inv) {
      this.dw = 204;
      this.dx = this.a.width - 16 - this.dw;
      this.dy = 58;
      // sélecteur À vie | Inventaire, en haut à droite du panneau
      int sx = this.dx + this.dw - 12;
      for (Vue v : new Vue[]{Vue.INVENTAIRE, Vue.VIE}) {
         String label = AtelierArme.tr(v == Vue.VIE ? "vue.vie" : "vue.inventaire");
         String n = String.valueOf(v == Vue.VIE ? this.vie.size() : this.objets.size());
         int w = (int) Ui.spacedWidth(font, label.toUpperCase(Locale.ROOT), Ui.SMALL, 1.0f) + 5 + font.width(Ui.lit(n, Ui.SMALL)) + 14;
         sx -= w;
         this.a.ajouter(new Zone(sx, this.dy + 9, w, 15, (g, z, mx, my, pt) -> this.dessinerSegment(g, z, v, label, n), () -> this.changerVue(v), null));
      }
      // grille de cartes
      this.carteW = (this.dw - 24 - (COLONNES - 1) * ECART) / COLONNES;
      this.grilleY = this.dy + 32;
      int pied = 24;
      int bas = this.a.basPanneaux(BANDEAU_H);
      this.rangsVisibles = Math.max(1, (bas - this.grilleY - pied - 6 + ECART) / (CARTE_H + ECART));
      int total = this.vue == Vue.VIE ? this.vie.size() : this.objets.size();
      this.rangs = (total + COLONNES - 1) / COLONNES;
      this.defil = Math.max(0, Math.min(this.defil, this.rangs - this.rangsVisibles));
      int affiches = Math.min(this.rangsVisibles, this.rangs);
      this.grilleH = total == 0 ? 44 : affiches * (CARTE_H + ECART) - ECART;
      for (int k = this.defil * COLONNES; k < Math.min(total, (this.defil + affiches) * COLONNES); k++) {
         int n = k - this.defil * COLONNES;
         int x = this.dx + 12 + (n % COLONNES) * (this.carteW + ECART);
         int y = this.grilleY + (n / COLONNES) * (CARTE_H + ECART);
         if (this.vue == Vue.VIE) {
            Camo c = this.vie.get(k);
            this.a.ajouter(new Zone(x, y, this.carteW, CARTE_H, (g, z, mx, my, pt) -> this.dessinerCarte(g, z, c, -1), () -> this.choisirVie(c), null));
         } else {
            int slot = this.objets.get(k);
            if (!(inv.getItem(slot).getItem() instanceof CamoItem ci)) continue;
            Camo c = ci.camo();
            this.a.ajouter(new Zone(x, y, this.carteW, CARTE_H, (g, z, mx, my, pt) -> this.dessinerCarte(g, z, c, slot),
               () -> this.choisirObjet(slot, c), null));
         }
      }
      this.piedY = this.grilleY + this.grilleH + 8;
      this.dh = this.piedY + pied - this.dy;
      this.a.bloc(this.dx, this.dy, this.dw, this.dh);
   }

   private void construireBandeau() {
      Camo c = this.apercu;
      if (c == null) return;
      this.bw = Math.min(330, this.a.width - 32);
      this.bx = (this.a.width - this.bw) / 2;
      this.by = this.a.height - 14 - BANDEAU_H;
      this.a.bloc(this.bx, this.by, this.bw, BANDEAU_H);
      int aw = 76, nw = 62;
      HzButton appliquer = new HzButton(this.bx + this.bw - 12 - aw, this.by + 11, aw, 18, AtelierArme.trc("appliquer"), Ui.OR, this::appliquer)
         .look(HzButton.Look.PRIMARY);
      appliquer.active = this.apercuVie ? CollectionVue.rechargeRestanteMs(c) <= 0 && !(this.aMoi && this.courant == c) : this.apercuSlot >= 0;
      HzButton annuler = new HzButton(this.bx + this.bw - 12 - aw - 6 - nw, this.by + 11, nw, 18, AtelierArme.trc("annuler"), Ui.accent(), this::annuler);
      this.a.ajouter(annuler);
      this.a.ajouter(appliquer);
   }

   // ------------------------------------------------------------------ actions
   private void changerVue(Vue v) {
      if (v == this.vue) return;
      this.vue = v;
      this.defil = 0;
      this.a.reconstruire();
   }

   private void choisirVie(Camo c) {
      boolean meme = this.apercuVie && this.apercu == c;
      this.apercu = meme ? null : c;
      this.apercuVie = !meme;
      this.apercuSlot = -1;
      RefitSkinTab.preview = this.apercu;
      this.a.reconstruire();
   }

   private void choisirObjet(int slot, Camo c) {
      boolean meme = !this.apercuVie && this.apercu != null && this.apercuSlot == slot;
      this.apercu = meme ? null : c;
      this.apercuVie = false;
      this.apercuSlot = meme ? -1 : slot;
      RefitSkinTab.preview = this.apercu;
      this.a.reconstruire();
   }

   private void annuler() {
      this.apercu = null;
      this.apercuSlot = -1;
      RefitSkinTab.preview = null;
      this.a.reconstruire();
   }

   private void appliquer() {
      LocalPlayer p = Minecraft.getInstance().player;
      Camo c = this.apercu;
      if (p == null || c == null || this.definitif) return;
      if (this.apercuVie) {
         if (CollectionVue.rechargeRestanteMs(c) > 0) return;
         CamoNetwork.CHANNEL.sendToServer(new CollectionCamoPacket(CollectionCamoPacket.APPLIQUER, c.id));
      } else {
         if (this.apercuSlot < 0) return;
         CamoNetwork.CHANNEL.sendToServer(new ApplyCamoPacket(this.apercuSlot));
      }
      p.playSound(SoundEvents.SMITHING_TABLE_USE, 0.8F, 1.2F);
      this.annuler();
   }

   private void retirer(Camo c) {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null) return;
      CamoNetwork.CHANNEL.sendToServer(new CollectionCamoPacket(CollectionCamoPacket.RETIRER, c.id));
      p.playSound(SoundEvents.BRUSH_GENERIC, 0.8F, 1.0F);
   }

   @Override
   public boolean molette(double mx, double my, double delta) {
      if (this.dw == 0 || mx < this.dx || mx >= this.dx + this.dw || my < this.grilleY || my >= this.grilleY + this.grilleH) return false;
      int avant = this.defil;
      this.defil = Math.max(0, Math.min(this.rangs - this.rangsVisibles, this.defil - (int) Math.signum(delta)));
      if (this.defil != avant) this.a.reconstruire();
      return true;
   }

   @Override
   public String signature() {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null) return "";
      ItemStack gun = AtelierArme.arme();
      StringBuilder sb = new StringBuilder();
      sb.append(CamoData.get(gun)).append('|').append(CamoData.owner(gun)).append('|').append(CamoData.token(gun)).append('|')
         .append(CollectionVue.version()).append('|').append(CollectionVue.enRecharge()).append('|');
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.getItem() instanceof CamoItem ci) sb.append(i).append(':').append(ci.camo().id).append('x').append(s.getCount()).append(';');
      }
      return sb.toString();
   }

   // ------------------------------------------------------------------ dessin
   @Override
   public void dessiner(GuiGraphics g, int mx, int my, float pt) {
      Font font = this.a.police();
      this.dessinerGauche(g, font);
      if (this.dw > 0) this.dessinerDroite(g, font);
      if (this.apercu != null) this.dessinerBandeau(g, font, this.apercu);
   }

   /** Hauteur du panneau de gauche (mêmes mesures que son dessin). */
   private int hauteurGauche(Font font) {
      int largeur = this.gw - 24;
      int h = 26 + 34;
      if (this.definitif) h += Dessins.hauteur(font, Ui.in(AtelierArme.trc("definitif_aide"), Ui.SMALL), largeur, 9) + 4;
      if (this.autre) h += Dessins.hauteur(font, Ui.in(AtelierArme.trc("vie_autre"), Ui.SMALL), largeur, 9) + 4;
      h += 10;
      h += 12 + Dessins.hauteur(font, Ui.in(AtelierArme.trc("a_vie_aide"), Ui.SMALL), largeur - 14, 9) + 8;
      h += 12 + Dessins.hauteur(font, Ui.in(AtelierArme.trc("objet_aide"), Ui.SMALL), largeur - 14, 9) + 6;
      return h;
   }

   private void dessinerGauche(GuiGraphics g, Font font) {
      int x = this.gx + 12, largeur = this.gw - 24;
      Ui.glass(g, this.gx, this.gy, this.gw, this.gh);
      Ui.section(g, font, AtelierArme.tr("sur_arme"), x, this.gy + 11, largeur);
      int y = this.gy + 26;
      Camo c = this.courant;
      Dessins.caseIcone(g, x, y, 26, c != null);
      int tx = x + 26 + 8;
      if (c == null) {
         Dessins.cadenas(g, x + 9, y + 8, Ui.argb(Ui.MUTED, 200));
         g.drawString(font, Ui.lit(AtelierArme.tr("aucun_camo"), Ui.FONT), tx, y + 3, Ui.argb(Ui.TEXT, 200), false);
         g.drawString(font, Ui.lit(AtelierArme.tr(this.definitif ? "aucun_camo" : "aucun_camo_aide"), Ui.SMALL), tx, y + 15, Ui.argb(Ui.MUTED, 255), false);
      } else {
         g.renderItem(new ItemStack(HeraliszCamo.CAMO_ITEMS.get(c).get()), x + 5, y + 5);
         String nom = Ui.ellipsis(font, Component.translatable(c.nameKey()).getString(), Ui.FONT, this.gx + this.gw - 12 - tx);
         g.drawString(font, Ui.lit(nom, Ui.FONT), tx, y + 3, Ui.argb(couleurTier(c.tier), 255), false);
         if (this.definitif) Ui.chip(g, font, AtelierArme.tr("definitif"), tx, y + 15, Ui.MUTED);
         else Ui.chip(g, font, AtelierArme.tr("a_vie"), tx, y + 15, Ui.OR);
      }
      y += 34;
      if (this.definitif) y += Dessins.paragraphe(g, font, Ui.in(AtelierArme.trc("definitif_aide"), Ui.SMALL), x, y, largeur, Ui.argb(Ui.MUTED, 255), 9) + 4;
      if (this.autre) y += Dessins.paragraphe(g, font, Ui.in(AtelierArme.trc("vie_autre"), Ui.SMALL), x, y, largeur, Ui.argb(Ui.AMBRE, 255), 9) + 4;
      Ui.hairline(g, x, x + largeur, y + 2, 0xFFFFFF, 30, false, true);
      y += 10;
      // ce que veulent dire « à vie » et « objet »
      Dessins.infini(g, font, x - 1, y - 1, Ui.argb(Ui.OR, 255));
      g.drawString(font, Ui.lit(AtelierArme.tr("a_vie"), Ui.BOLD), x + 14, y, Ui.argb(Ui.OR, 255), false);
      y += 12;
      y += Dessins.paragraphe(g, font, Ui.in(AtelierArme.trc("a_vie_aide"), Ui.SMALL), x + 14, y, largeur - 14, Ui.argb(Ui.MUTED, 255), 9) + 8;
      Dessins.cadenas(g, x + 1, y - 1, Ui.argb(Ui.MUTED, 255));
      g.drawString(font, Ui.lit(AtelierArme.tr("objet"), Ui.BOLD), x + 14, y, Ui.argb(Ui.TEXT, 255), false);
      y += 12;
      Dessins.paragraphe(g, font, Ui.in(AtelierArme.trc("objet_aide"), Ui.SMALL), x + 14, y, largeur - 14, Ui.argb(Ui.MUTED, 255), 9);
   }

   private void dessinerDroite(GuiGraphics g, Font font) {
      Ui.glass(g, this.dx, this.dy, this.dw, this.dh);
      Dessins.titre(g, font, AtelierArme.tr("camos"), Ui.BOLD, this.dx + 12, this.dy + 11, 1.15f, Ui.argb(Ui.TEXT, 255), 1.2f);
      int total = this.vue == Vue.VIE ? this.vie.size() : this.objets.size();
      int largeur = this.dw - 24;
      if (total == 0) {
         String vide = AtelierArme.tr(this.vue == Vue.VIE ? "collection_vide" : "inventaire_vide");
         g.drawString(font, Ui.lit(vide, Ui.FONT), this.dx + 12, this.grilleY + 6, Ui.argb(Ui.TEXT, 200), false);
         if (this.vue == Vue.VIE) {
            Dessins.paragraphe(g, font, Ui.in(AtelierArme.trc("collection_vide_aide"), Ui.SMALL), this.dx + 12, this.grilleY + 19, largeur,
               Ui.argb(Ui.MUTED, 255), 9);
         }
      } else if (this.rangs > this.rangsVisibles) {
         int bh = Math.max(10, this.grilleH * this.rangsVisibles / this.rangs);
         int y = this.grilleY + (this.grilleH - bh) * this.defil / Math.max(1, this.rangs - this.rangsVisibles);
         g.fill(this.dx + this.dw - 7, this.grilleY, this.dx + this.dw - 5, this.grilleY + this.grilleH, Ui.argb(0xFFFFFF, 18));
         g.fill(this.dx + this.dw - 7, y, this.dx + this.dw - 5, y + bh, Ui.argb(Ui.OR, 200));
      }
      Component pied = AtelierArme.trc(this.vue == Vue.VIE ? "pied_vie" : "pied_inventaire");
      Dessins.paragraphe(g, font, Ui.in(pied, Ui.SMALL), this.dx + 12, this.piedY, largeur, Ui.argb(Ui.FAINT, 255), 9);
   }

   private void dessinerSegment(GuiGraphics g, Zone z, Vue v, String label, String n) {
      Font font = this.a.police();
      boolean actif = v == this.vue;
      int x = z.getX(), y = z.getY(), w = z.getWidth(), h = z.getHeight();
      if (actif) {
         Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 16));
         g.fill(x + 6, y + h - 2, x + w - 6, y + h - 1, Ui.argb(Ui.OR, 230));
      } else if (z.survol()) {
         Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 8));
      }
      int c = actif ? Ui.TEXT : z.survol() ? Ui.mix(Ui.MUTED, Ui.TEXT, 0.5f) : Ui.MUTED;
      Ui.spaced(g, font, label, Ui.SMALL, x + 7, y + 4.5f, Ui.argb(c, 255), 1.0f);
      float lw = Ui.spacedWidth(font, label.toUpperCase(Locale.ROOT), Ui.SMALL, 1.0f);
      g.drawString(font, Ui.lit(n, Ui.SMALL), (int) (x + 7 + lw + 5), y + 4, Ui.argb(Ui.FAINT, 255), false);
   }

   private void dessinerCarte(GuiGraphics g, Zone z, Camo c, int slot) {
      Font font = this.a.police();
      int x = z.getX(), y = z.getY(), w = z.getWidth(), h = z.getHeight();
      boolean survol = z.survol();
      boolean choisie = this.apercu == c && (slot < 0 ? this.apercuVie : !this.apercuVie && this.apercuSlot == slot);
      boolean surArme = slot < 0 && this.aMoi && this.courant == c;
      long reste = slot < 0 ? CollectionVue.rechargeRestanteMs(c) : 0L;
      int tier = couleurTier(c.tier);
      Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, survol ? 14 : 7));
      Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, choisie ? Ui.argb(Ui.OR, 235) : Ui.argb(0xFFFFFF, survol ? 50 : 20));
      g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Ui.argb(tier, 210));
      // l'arme tenue, photographiée avec ce camo
      float echelle = 1.9f;
      g.pose().pushPose();
      g.pose().translate(x + w / 2f - 8 * echelle, y + 2, 0);
      g.pose().scale(echelle, echelle, 1);
      g.renderItem(this.rendu(c), 0, 0);
      g.pose().popPose();
      String nom = Ui.ellipsis(font, Component.translatable(c.nameKey()).getString(), Ui.FONT, w - 8);
      g.drawString(font, Ui.lit(nom, Ui.FONT), x + 4, y + h - 26, Ui.argb(Ui.TEXT, 255), false);
      Ui.chip(g, font, AtelierArme.tr("tier." + c.tier.name().toLowerCase(Locale.ROOT)), x + 4, y + h - 15, tier);
      // par-dessus l'arme : délai, sur l'arme, aperçu, nombre d'objets
      g.pose().pushPose();
      g.pose().translate(0, 0, 300);
      if (reste > 0) {
         g.fill(x + 1, y + 1, x + w - 1, y + 33, Ui.argb(0x0D0F14, 175));
         Component m = Ui.lit(AtelierArme.tr("minutes", minutes(reste)), Ui.BOLD);
         int mw = font.width(m) + 12;
         int mx0 = x + (w - mw) / 2;
         Dessins.horloge(g, mx0, y + 12, Ui.argb(Ui.DANGER, 255));
         g.drawString(font, m, mx0 + 12, y + 13, Ui.argb(Ui.DANGER, 255), false);
      }
      if (surArme || choisie) {
         String t = AtelierArme.tr(surArme ? "sur_arme_chip" : "apercu");
         int cw = Dessins.largeurChip(font, t);
         Ui.soft(g, x + w - 3 - cw, y + 3, cw, 11, Ui.argb(0x0D0F14, 200));
         Ui.chip(g, font, t, x + w - 3 - cw, y + 3, surArme ? Ui.OK : Ui.OR);
      }
      if (slot >= 0) {
         LocalPlayer p = Minecraft.getInstance().player;
         int n = p == null ? 1 : p.getInventory().getItem(slot).getCount();
         if (n > 1) g.drawString(font, Ui.lit("×" + n, Ui.SMALL), x + 4, y + 4, Ui.argb(Ui.MUTED, 255), false);
      }
      g.pose().popPose();
      if (survol) {
         List<Component> l = new ArrayList<>();
         l.add(Ui.lit(Component.translatable(c.nameKey()).getString(), Ui.BOLD).withStyle(st -> st.withColor(tier)));
         l.add(Ui.in(Component.translatable(c.tier.key()), Ui.FONT).withStyle(st -> st.withColor(tier)));
         l.add(Ui.in(AtelierArme.trc(slot < 0 ? "info_vie" : "info_objet"), Ui.FONT).withStyle(st -> st.withColor(slot < 0 ? Ui.OR : Ui.MUTED)));
         if (surArme) l.add(Ui.in(AtelierArme.trc("deja"), Ui.FONT).withStyle(st -> st.withColor(Ui.OK)));
         if (reste > 0) l.add(Ui.in(AtelierArme.trc("attente", minutes(reste)), Ui.FONT).withStyle(st -> st.withColor(Ui.DANGER)));
         this.a.infobulle(l);
      }
   }

   private void dessinerBandeau(GuiGraphics g, Font font, Camo c) {
      Ui.glass(g, this.bx, this.by, this.bw, BANDEAU_H);
      int x = this.bx + 12;
      if (this.apercuVie) Dessins.infini(g, font, x, this.by + 14, Ui.argb(Ui.OR, 255));
      else Dessins.cadenas(g, x + 2, this.by + 15, Ui.argb(Ui.MUTED, 255));
      int tx = x + 20;
      int textW = this.bw - 12 - 76 - 6 - 62 - 10 - (tx - this.bx);
      MutableComponent ligne = Ui.lit(AtelierArme.tr("apercu_de") + " ", Ui.FONT)
         .append(Ui.lit(Component.translatable(c.nameKey()).getString(), Ui.BOLD).withStyle(st -> st.withColor(couleurTier(c.tier))))
         .append(Ui.lit(" · " + AtelierArme.tr(this.apercuVie ? "a_vie_min" : "objet_min"), Ui.FONT));
      g.drawString(font, ligne, tx, this.by + 8, Ui.argb(Ui.TEXT, 255), false);
      long reste = this.apercuVie ? CollectionVue.rechargeRestanteMs(c) : 0L;
      Component sous;
      int couleur = Ui.MUTED;
      if (this.apercuVie && this.aMoi && this.courant == c) {
         sous = AtelierArme.trc("deja");
         couleur = Ui.OK;
      } else if (reste > 0) {
         sous = AtelierArme.trc("attente", minutes(reste));
         couleur = Ui.DANGER;
      } else {
         sous = AtelierArme.trc(this.apercuVie ? "apercu_vie" : "apercu_objet");
      }
      Dessins.paragraphe(g, font, Ui.in(sous, Ui.SMALL), tx, this.by + 20, textW, Ui.argb(couleur, 255), 9);
   }

   // ------------------------------------------------------------------ outils
   /** L'arme tenue avec ce camo (copie, pour la photo de la carte). */
   private ItemStack rendu(Camo c) {
      return this.rendus.computeIfAbsent(c, k -> {
         ItemStack copie = AtelierArme.arme().copy();
         CompoundTag tag = copie.getTag();
         if (tag != null) {
            for (AttachmentType t : AttachmentType.values()) tag.remove("Attachment" + t.name());
         }
         CamoData.set(copie, k);
         return copie;
      });
   }

   static int couleurTier(Camo.Tier t) {
      return switch (t) {
         case COMMON -> 0x9AA0A6;
         case UNCOMMON -> 0x5FD08A;
         case RARE -> 0x5B9BFF;
         case EPIC -> 0xB57BFF;
         case LEGENDARY -> 0xF0A43A;
         case MYTHIC -> 0xFF5A5A;
      };
   }

   private static long minutes(long ms) {
      return Math.max(1L, (ms + 59_999L) / 60_000L);
   }
}
