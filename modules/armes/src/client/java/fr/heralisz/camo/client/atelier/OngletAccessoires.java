package fr.heralisz.camo.client.atelier;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.sound.SoundManager;
import fr.heralisz.camo.client.kit.HzButton;
import fr.heralisz.camo.client.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Onglet Accessoires : à gauche les emplacements de l'arme (accessoire posé, nombre d'accessoires compatibles dans
 * l'inventaire), à droite les accessoires de l'emplacement choisi (effets en pastilles), en bas les caractéristiques de
 * l'arme et ce que changerait l'accessoire choisi. Clic : choisir (aperçu des caractéristiques) ; double-clic ou bouton :
 * poser. Pose et retrait par les paquets de TaCZ (le serveur revérifie, rend l'ancien accessoire à l'inventaire).
 */
final class OngletAccessoires implements AtelierArme.Partie {
   /** Ordre des emplacements, comme sur l'arme : viseur, bouche, poignée, crosse, laser, chargeur. */
   private static final AttachmentType[] ORDRE = {AttachmentType.SCOPE, AttachmentType.MUZZLE, AttachmentType.GRIP, AttachmentType.STOCK,
      AttachmentType.LASER, AttachmentType.EXTENDED_MAG};
   /** Couleurs proposées pour un laser : rouge, orange, jaune, vert, cyan, bleu, violet, blanc. */
   private static final int[] LASERS = {0xFF2D2D, 0xFF8A1F, 0xFFE53B, 0x3BFF5C, 0x2EE6FF, 0x3B6BFF, 0xB04DFF, 0xFFFFFF};
   /** Panneau des caractéristiques : titre + 3 rangées (Stats.RANGEES). */
   private static final int STATS_H = 24 + Stats.RANGEES * 17 + 2, CARTE_H = 34, ECART = 4;

   private final AtelierArme a;
   /** Accessoire choisi : son emplacement d'inventaire (-1 : aucun) et une copie pour vérifier que c'est toujours lui. */
   private int slotChoisi = -1;
   private ItemStack choisi = ItemStack.EMPTY;
   /** Première carte affichée (molette). */
   private int defil;
   @Nullable
   private Component erreur;
   private long erreurFin;
   private final Map<AttachmentType, List<Integer>> candidats = new EnumMap<>(AttachmentType.class);
   private List<AttachmentType> types = List.of();
   private AttachmentType courant = AttachmentType.NONE;
   // géométrie (construire)
   private int gx, gy, gw, gh, rangH;
   private int dx, dy, dw, dh, listeY, listeH, cartes, visibles, aideY;
   @Nullable
   private LaserConfig laser;
   private int laserY;
   private int sx, sy, sw;
   private List<Stats.Ligne> stats = List.of();
   @Nullable
   private String statsAvec;

   OngletAccessoires(AtelierArme a) {
      this.a = a;
   }

   // ------------------------------------------------------------------ construction
   @Override
   public void construire() {
      LocalPlayer p = Minecraft.getInstance().player;
      ItemStack gun = AtelierArme.arme();
      IGun iGun = IGun.getIGunOrNull(gun);
      if (p == null || iGun == null) return;
      Inventory inv = p.getInventory();
      List<AttachmentType> t = new ArrayList<>();
      for (AttachmentType type : ORDRE) {
         if (iGun.allowAttachmentType(gun, type)) t.add(type);
      }
      this.types = t;
      this.candidats.clear();
      for (AttachmentType type : this.types) this.candidats.put(type, new ArrayList<>());
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         IAttachment ia = IAttachment.getIAttachmentOrNull(s);
         if (ia == null) continue;
         List<Integer> l = this.candidats.get(ia.getType(s));
         if (l != null && iGun.allowAttachment(gun, s)) l.add(i);
      }
      AttachmentType vue = RefitTransform.getCurrentTransformType();
      this.courant = this.types.contains(vue) ? vue : AttachmentType.NONE;
      if (this.slotChoisi >= 0) {
         List<Integer> l = this.candidats.get(this.courant);
         if (l == null || !l.contains(this.slotChoisi) || !ItemStack.isSameItemSameTags(inv.getItem(this.slotChoisi), this.choisi)) {
            this.slotChoisi = -1;
            this.choisi = ItemStack.EMPTY;
         }
      }

      // caractéristiques, en bas au centre
      this.sw = Math.min(460, this.a.width - 32);
      this.sx = (this.a.width - this.sw) / 2;
      this.sy = this.a.height - 14 - STATS_H;
      ItemStack avec = this.slotChoisi >= 0 ? inv.getItem(this.slotChoisi) : ItemStack.EMPTY;
      this.stats = Stats.calculer(gun, avec.isEmpty() ? null : avec);
      this.statsAvec = avec.isEmpty() ? null : avec.getHoverName().getString();
      int bas = this.a.basPanneaux(STATS_H);

      // emplacements, à gauche
      this.gx = 16;
      this.gy = 58;
      this.gw = 146;
      int entete = 26, pied = 30;
      this.rangH = this.types.isEmpty() ? 0 : Math.max(22, Math.min(30, (bas - this.gy - entete - pied) / this.types.size()));
      this.gh = this.types.isEmpty() ? 64 : entete + this.types.size() * this.rangH + pied;
      this.a.bloc(this.gx, this.gy, this.gw, this.gh);
      int y = this.gy + entete;
      for (AttachmentType type : this.types) {
         this.a.ajouter(new Zone(this.gx + 6, y, this.gw - 12, this.rangH - 2, (g, z, mx, my, pt) -> this.dessinerRang(g, z, type),
            () -> this.choisirType(type), null));
         y += this.rangH;
      }

      // accessoires de l'emplacement choisi, à droite
      this.dw = 0;
      this.laser = laser(gun, iGun, this.courant);
      if (this.courant != AttachmentType.NONE) {
         this.construireDroite(gun, iGun, inv, bas);
      } else if (this.laser != null) {
         this.construireLaserSeul(gun, iGun);
      }
   }

   private void construireDroite(ItemStack gun, IGun iGun, Inventory inv, int bas) {
      this.dw = 168;
      this.dx = this.a.width - 16 - this.dw;
      this.dy = 58;
      AttachmentType type = this.courant;
      ItemStack pose = iGun.getAttachment(gun, type);
      boolean verrou = iGun.hasAttachmentLock(gun);
      List<Integer> toutes = new ArrayList<>();
      if (!pose.isEmpty()) toutes.add(-1);
      toutes.addAll(this.candidats.getOrDefault(type, List.of()));
      int entete = 30, action = 17 + 6 + 12, laserH = this.laser != null ? 34 : 0;
      int dispo = bas - this.dy - entete - action - laserH - 10;
      this.visibles = Math.max(1, (dispo + ECART) / (CARTE_H + ECART));
      this.cartes = toutes.size();
      this.defil = Math.max(0, Math.min(this.defil, this.cartes - this.visibles));
      int n = Math.min(this.visibles, this.cartes);
      this.listeY = this.dy + entete;
      this.listeH = n == 0 ? 26 : n * (CARTE_H + ECART) - ECART;
      int y = this.listeY;
      for (int k = this.defil; k < this.defil + n; k++) {
         int slot = toutes.get(k);
         if (slot < 0) {
            this.a.ajouter(new Zone(this.dx + 12, y, this.dw - 24, CARTE_H, (g, z, mx, my, pt) -> this.dessinerCarte(g, z, pose, true, false), null, null));
         } else {
            this.a.ajouter(new Zone(this.dx + 12, y, this.dw - 24, CARTE_H,
               (g, z, mx, my, pt) -> this.dessinerCarte(g, z, inv.getItem(slot), false, slot == this.slotChoisi),
               () -> this.choisir(slot, inv), () -> this.poser(slot)));
         }
         y += CARTE_H + ECART;
      }
      // boutons : poser l'accessoire choisi ; retirer celui qui est posé
      int by = this.listeY + this.listeH + 8;
      int bw = this.dw - 24;
      int accent = Ui.accent();
      if (!pose.isEmpty()) {
         int rw = 60;
         HzButton poser = new HzButton(this.dx + 12, by, bw - rw - 4, 17, AtelierArme.trc("poser"), accent, () -> this.poser(this.slotChoisi))
            .look(HzButton.Look.PRIMARY);
         poser.active = this.slotChoisi >= 0 && !verrou;
         HzButton retirer = new HzButton(this.dx + 12 + bw - rw, by, rw, 17, AtelierArme.trc("retirer"), accent, this::retirer).danger();
         retirer.active = !verrou;
         this.a.ajouter(poser);
         this.a.ajouter(retirer);
      } else {
         HzButton poser = new HzButton(this.dx + 12, by, bw, 17, AtelierArme.trc("poser"), accent, () -> this.poser(this.slotChoisi))
            .look(HzButton.Look.PRIMARY);
         poser.active = this.slotChoisi >= 0 && !verrou;
         this.a.ajouter(poser);
      }
      this.aideY = by + 17 + 6;
      this.dh = this.aideY + 12 - this.dy;
      if (this.laser != null) {
         this.laserY = this.dh + this.dy + 2;
         this.ajouterLasers(gun, iGun, this.dx + 12, this.laserY + 12);
         this.dh += laserH;
      }
      this.dh += 4;
      this.a.bloc(this.dx, this.dy, this.dw, this.dh);
   }

   /** Aucun emplacement choisi, mais l'arme a son propre laser : petit panneau de couleur à droite. */
   private void construireLaserSeul(ItemStack gun, IGun iGun) {
      this.dw = 168;
      this.dx = this.a.width - 16 - this.dw;
      this.dy = 58;
      this.laserY = this.dy + 10;
      this.ajouterLasers(gun, iGun, this.dx + 12, this.laserY + 12);
      this.dh = 46;
      this.a.bloc(this.dx, this.dy, this.dw, this.dh);
   }

   private void ajouterLasers(ItemStack gun, IGun iGun, int x, int y) {
      AttachmentType type = this.courant;
      for (int i = 0; i < LASERS.length; i++) {
         int rgb = LASERS[i];
         this.a.ajouter(new Zone(x + i * 18, y, 14, 14, (g, z, mx, my, pt) -> {
            LaserConfig cfg = this.laser;
            boolean actuel = cfg != null && (couleurLaser(AtelierArme.arme(), type, cfg) & 0xFFFFFF) == rgb;
            Ui.soft(g, z.getX(), z.getY(), 14, 14, Ui.argb(rgb, 255));
            if (actuel) Ui.softOutline(g, z.getX() - 1, z.getY() - 1, 16, 16, Ui.argb(0xFFFFFF, 230));
            else if (z.survol()) Ui.softOutline(g, z.getX(), z.getY(), 14, 14, Ui.argb(0xFFFFFF, 120));
         }, () -> ecrireLaser(AtelierArme.arme(), type, rgb), null));
      }
   }

   // ------------------------------------------------------------------ actions
   private void choisirType(AttachmentType type) {
      AttachmentType cible = RefitTransform.getCurrentTransformType() == type ? AttachmentType.NONE : type;
      // refusé pendant un mouvement de caméra (un quart de seconde) : il suffit de recliquer
      if (RefitTransform.changeRefitScreenView(cible)) {
         this.slotChoisi = -1;
         this.choisi = ItemStack.EMPTY;
         this.defil = 0;
         this.erreur = null;
         this.a.reconstruire();
      }
   }

   private void choisir(int slot, Inventory inv) {
      if (this.slotChoisi == slot) {
         this.slotChoisi = -1;
         this.choisi = ItemStack.EMPTY;
      } else {
         this.slotChoisi = slot;
         this.choisi = inv.getItem(slot).copy();
      }
      this.a.reconstruire();
   }

   private void poser(int slot) {
      LocalPlayer p = Minecraft.getInstance().player;
      ItemStack gun = AtelierArme.arme();
      IGun iGun = IGun.getIGunOrNull(gun);
      if (p == null || iGun == null || slot < 0 || iGun.hasAttachmentLock(gun) || this.courant == AttachmentType.NONE) return;
      Inventory inv = p.getInventory();
      ItemStack s = inv.getItem(slot);
      if (IAttachment.getIAttachmentOrNull(s) == null || !iGun.allowAttachment(gun, s)) return;
      SoundPlayManager.playerRefitSound(s, p, SoundManager.INSTALL_SOUND);
      NetworkHandler.CHANNEL.sendToServer(new ClientMessageRefitGun(slot, inv.selected, this.courant));
      this.slotChoisi = -1;
      this.choisi = ItemStack.EMPTY;
   }

   private void retirer() {
      LocalPlayer p = Minecraft.getInstance().player;
      ItemStack gun = AtelierArme.arme();
      IGun iGun = IGun.getIGunOrNull(gun);
      if (p == null || iGun == null || this.courant == AttachmentType.NONE) return;
      ItemStack pose = iGun.getAttachment(gun, this.courant);
      if (pose.isEmpty()) return;
      Inventory inv = p.getInventory();
      if (inv.getFreeSlot() == -1) {
         this.erreur = AtelierArme.trc("inventaire_plein");
         this.erreurFin = Util.getMillis() + 4000L;
         return;
      }
      SoundPlayManager.playerRefitSound(pose, p, SoundManager.UNINSTALL_SOUND);
      NetworkHandler.CHANNEL.sendToServer(new ClientMessageUnloadAttachment(inv.selected, this.courant));
   }

   @Override
   public boolean molette(double mx, double my, double delta) {
      if (this.dw == 0 || mx < this.dx || mx >= this.dx + this.dw || my < this.listeY || my >= this.listeY + this.listeH) return false;
      int avant = this.defil;
      this.defil = Math.max(0, this.defil - (int) Math.signum(delta));
      if (this.defil != avant) this.a.reconstruire();
      return true;
   }

   @Override
   public String signature() {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null) return "";
      StringBuilder sb = new StringBuilder();
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         IAttachment ia = IAttachment.getIAttachmentOrNull(s);
         if (ia != null) sb.append(i).append(':').append(ia.getAttachmentId(s)).append(';');
      }
      return sb.toString();
   }

   // ------------------------------------------------------------------ dessin
   @Override
   public void dessiner(GuiGraphics g, int mx, int my, float pt) {
      Font font = this.a.police();
      int accent = Ui.accent();
      // emplacements
      Ui.glass(g, this.gx, this.gy, this.gw, this.gh);
      Ui.section(g, font, AtelierArme.tr("sur_arme"), this.gx + 12, this.gy + 11, this.gw - 24);
      if (this.types.isEmpty()) {
         Dessins.paragraphe(g, font, Ui.in(AtelierArme.trc("aucun_emplacement"), Ui.SMALL), this.gx + 12, this.gy + 30, this.gw - 24, Ui.argb(Ui.MUTED, 255), 10);
      } else {
         int py = this.gy + this.gh - 26;
         Component aide = this.courant == AttachmentType.NONE ? AtelierArme.trc("choisir_emplacement") : AtelierArme.trc("compte_aide");
         Dessins.paragraphe(g, font, Ui.in(aide, Ui.SMALL), this.gx + 12, py, this.gw - 24, Ui.argb(Ui.FAINT, 255), 9);
      }
      // accessoires de l'emplacement
      if (this.dw > 0) {
         Ui.glass(g, this.dx, this.dy, this.dw, this.dh);
         if (this.courant != AttachmentType.NONE) {
            String titre = AtelierArme.tr("type." + this.courant.name().toLowerCase(Locale.ROOT));
            Dessins.titre(g, font, titre, Ui.BOLD, this.dx + 12, this.dy + 11, 1.15f, Ui.argb(Ui.TEXT, 255), 1.2f);
            Ui.spacedRight(g, font, AtelierArme.tr("inventaire"), Ui.SMALL, this.dx + this.dw - 12, this.dy + 13, Ui.argb(Ui.MUTED, 255), 0.8f);
            if (this.cartes == 0) {
               Dessins.paragraphe(g, font, Ui.in(AtelierArme.trc("aucun_candidat"), Ui.SMALL), this.dx + 12, this.listeY + 4, this.dw - 24,
                  Ui.argb(Ui.MUTED, 255), 10);
            } else if (this.cartes > this.visibles) {
               // plus de cartes que de place : petite barre de défilement
               int total = this.cartes, vis = this.visibles;
               int bh = Math.max(8, this.listeH * vis / total);
               int by = this.listeY + (this.listeH - bh) * this.defil / Math.max(1, total - vis);
               g.fill(this.dx + this.dw - 7, this.listeY, this.dx + this.dw - 5, this.listeY + this.listeH, Ui.argb(0xFFFFFF, 18));
               g.fill(this.dx + this.dw - 7, by, this.dx + this.dw - 5, by + bh, Ui.argb(accent, 200));
            }
            boolean enErreur = this.erreur != null && Util.getMillis() < this.erreurFin;
            Component aide = enErreur ? this.erreur : AtelierArme.trc("choisir_aide");
            Dessins.paragraphe(g, font, Ui.in(aide, Ui.SMALL), this.dx + 12, this.aideY, this.dw - 24,
               Ui.argb(enErreur ? Ui.DANGER : Ui.FAINT, 255), 9);
         }
         if (this.laser != null) {
            String titre = AtelierArme.tr(this.courant == AttachmentType.NONE ? "laser_arme" : "laser");
            Ui.section(g, font, titre, this.dx + 12, this.laserY + 1, this.dw - 24);
         }
      }
      Stats.dessiner(g, font, this.stats, this.statsAvec, this.sx, this.sy, this.sw, STATS_H, accent);
   }

   private void dessinerRang(GuiGraphics g, Zone z, AttachmentType type) {
      Font font = this.a.police();
      ItemStack gun = AtelierArme.arme();
      IGun iGun = IGun.getIGunOrNull(gun);
      if (iGun == null) return;
      int accent = Ui.accent();
      boolean actif = this.courant == type;
      int x = z.getX(), y = z.getY(), w = z.getWidth(), h = z.getHeight();
      if (actif) {
         Ui.soft(g, x, y, w, h, Ui.argb(accent, 34));
         Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(accent, 150));
         g.fill(x, y + 3, x + 2, y + h - 3, Ui.argb(accent, 255));
      } else if (z.survol()) {
         Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 10));
      }
      ItemStack pose = iGun.getAttachment(gun, type);
      int is = 20, ix = x + 5, iy = y + (h - is) / 2;
      Dessins.caseIcone(g, ix, iy, is, actif);
      if (!pose.isEmpty()) {
         g.renderItem(pose, ix + 2, iy + 2);
      } else {
         int u = GunRefitScreen.getSlotTextureXOffset(gun, type);
         if (u >= 0) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.45F);
            g.blit(GunRefitScreen.ICONS_TEXTURE, ix + 3, iy + 3, 14, 14, (float) u, 0.0F, 32, 32, GunRefitScreen.getSlotsTextureWidth(), 32);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         }
      }
      int tx = ix + is + 7;
      int compte = this.candidats.getOrDefault(type, List.of()).size();
      int droite = x + w - 6;
      if (compte > 0) {
         Component plus = Ui.lit("+", Ui.BOLD);
         Ui.right(g, font, plus, droite, y + h / 2f - 4, Ui.argb(accent, 255));
         droite -= font.width(plus) + 3;
         Component c = Ui.lit(String.valueOf(compte), Ui.FONT);
         Ui.right(g, font, c, droite, y + h / 2f - 4, Ui.argb(Ui.MUTED, 255));
         droite -= font.width(c) + 4;
      }
      Ui.spaced(g, font, AtelierArme.tr("type." + type.name().toLowerCase(Locale.ROOT)), Ui.SMALL, tx, y + h / 2f - 8, Ui.argb(Ui.MUTED, 255), 1.0f);
      String nom = pose.isEmpty() ? AtelierArme.tr("vide") : pose.getHoverName().getString();
      nom = Ui.ellipsis(font, nom, Ui.FONT, droite - tx - 2);
      g.drawString(font, Ui.lit(nom, Ui.FONT), tx, (int) (y + h / 2f + 1), Ui.argb(pose.isEmpty() ? Ui.FAINT : Ui.TEXT, 255), false);
      if (z.survol() && !pose.isEmpty()) this.a.infobulle(this.infobulleDe(pose));
   }

   private void dessinerCarte(GuiGraphics g, Zone z, ItemStack s, boolean pose, boolean choisie) {
      if (s.isEmpty()) return;
      Font font = this.a.police();
      int accent = Ui.accent();
      int x = z.getX(), y = z.getY(), w = z.getWidth(), h = z.getHeight();
      boolean survol = z.survol();
      Ui.soft(g, x, y, w, h, Ui.argb(choisie ? accent : 0xFFFFFF, choisie ? 30 : survol ? 13 : 6));
      Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, choisie ? Ui.argb(accent, 210) : Ui.argb(0xFFFFFF, survol ? 46 : 20));
      int ix = x + 6, iy = y + (h - 22) / 2;
      Dessins.caseIcone(g, ix, iy, 22, choisie);
      g.renderItem(s, ix + 3, iy + 3);
      int tx = ix + 22 + 7, tw = x + w - 6 - tx;
      int chipPose = 0;
      if (pose) {
         String t = AtelierArme.tr("pose");
         chipPose = Dessins.largeurChip(font, t);
         Ui.chip(g, font, t, x + w - 6 - chipPose, y + 5, Ui.OK);
      }
      String nom = Ui.ellipsis(font, s.getHoverName().getString(), Ui.FONT, tw - (pose ? chipPose + 4 : 0));
      g.drawString(font, Ui.lit(nom, Ui.FONT), tx, y + 6, Ui.argb(Ui.TEXT, 255), false);
      // effets : autant de pastilles que la place le permet, le reste dans l'infobulle
      List<Effets.Effet> liste = Effets.sur(AtelierArme.arme(), s);
      int cx = tx;
      for (int i = 0; i < liste.size(); i++) {
         Effets.Effet e = liste.get(i);
         int cw = Dessins.largeurChip(font, e.texte());
         int reste = liste.size() - i - 1;
         int place = x + w - 6 - cx - (reste > 0 ? Dessins.largeurChip(font, "+" + reste) + 3 : 0);
         if (cw > place) {
            Ui.chip(g, font, "+" + (liste.size() - i), cx, y + h - 15, Ui.MUTED);
            break;
         }
         Ui.chip(g, font, e.texte(), cx, y + h - 15, e.couleur());
         cx += cw + 3;
      }
      if (survol) this.a.infobulle(this.infobulleDe(s));
   }

   private List<Component> infobulleDe(ItemStack s) {
      List<Component> l = new ArrayList<>();
      l.add(Ui.lit(s.getHoverName().getString(), Ui.BOLD));
      for (Effets.Effet e : Effets.sur(AtelierArme.arme(), s)) {
         l.add(Ui.lit(e.texte(), Ui.FONT).withStyle(st -> st.withColor(e.couleur())));
      }
      return l;
   }

   // ------------------------------------------------------------------ couleur du laser
   /** Réglage du laser modifiable : celui de l'arme (aucun emplacement choisi) ou celui de l'accessoire posé. */
   @Nullable
   private static LaserConfig laser(ItemStack gun, IGun iGun, AttachmentType type) {
      if (type == AttachmentType.NONE) {
         return TimelessAPI.getGunDisplay(gun).map(GunDisplayInstance::getLaserConfig).filter(LaserConfig::canEdit).orElse(null);
      }
      ItemStack att = iGun.getAttachment(gun, type);
      IAttachment ia = IAttachment.getIAttachmentOrNull(att);
      if (ia == null) return null;
      return TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(att)).map(ClientAttachmentIndex::getLaserConfig)
         .filter(LaserConfig::canEdit).orElse(null);
   }

   private static int couleurLaser(ItemStack gun, AttachmentType type, LaserConfig cfg) {
      IGun iGun = IGun.getIGunOrNull(gun);
      if (iGun == null) return cfg.getDefaultColor();
      if (type == AttachmentType.NONE) return iGun.hasCustomLaserColor(gun) ? iGun.getLaserColor(gun) : cfg.getDefaultColor();
      ItemStack att = iGun.getAttachment(gun, type);
      IAttachment ia = IAttachment.getIAttachmentOrNull(att);
      return ia != null && ia.hasCustomLaserColor(att) ? ia.getLaserColor(att) : cfg.getDefaultColor();
   }

   /**
    * Nouvelle couleur, sur l'arme tenue (chez le joueur) : TaCZ l'envoie au serveur à la fermeture de l'écran
    * (ClientMessageLaserColor). L'accessoire rendu par getAttachment est une copie : il est réécrit dans l'arme.
    */
   private static void ecrireLaser(ItemStack gun, AttachmentType type, int rgb) {
      IGun iGun = IGun.getIGunOrNull(gun);
      if (iGun == null) return;
      if (type == AttachmentType.NONE) {
         iGun.setLaserColor(gun, rgb);
         return;
      }
      ItemStack att = iGun.getAttachment(gun, type);
      IAttachment ia = IAttachment.getIAttachmentOrNull(att);
      if (ia == null) return;
      ia.setLaserColor(att, rgb);
      CompoundTag tag = new CompoundTag();
      att.save(tag);
      gun.getOrCreateTag().put("Attachment" + type.name(), tag);
   }
}
