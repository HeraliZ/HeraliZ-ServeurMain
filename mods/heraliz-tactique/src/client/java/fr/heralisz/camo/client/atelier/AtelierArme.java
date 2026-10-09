package fr.heralisz.camo.client.atelier;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import fr.heralisz.camo.client.RefitSkinTab;
import fr.heralisz.camo.client.kit.Echelle;
import fr.heralisz.camo.client.kit.SonsUi;
import fr.heralisz.camo.client.kit.Ui;
import fr.heralisz.camo.decor.client.RefitDecorTab;
import fr.heralisz.camo.knife.KnifeSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * L'Atelier d'arme : remplace l'écran de modification de TaCZ (sa touche, arme en main) par un écran au style des écrans
 * de clan, en trois onglets : Accessoires (emplacements de l'arme, accessoires de l'inventaire, caractéristiques avant /
 * après), Camo (collection à vie et objets camo, aperçu sur l'arme) et Personnaliser (stickers, StatTrack).
 * <p>
 * Sous-classe de GunRefitScreen : TaCZ y reconnaît toujours son écran (caméra sur l'arme, sa touche pour fermer, barre
 * d'objets bloquée, rafraîchissement après une pose, couleur du laser envoyée à la fermeture). La pose et le retrait des
 * accessoires passent par les paquets de TaCZ, les camos et les stickers par les nôtres : le serveur revérifie tout. Si
 * l'atelier plante, l'écran de TaCZ (avec nos anciens onglets) reprend la main jusqu'au redémarrage du jeu.
 */
public final class AtelierArme extends GunRefitScreen {
   private static final Logger LOG = LogUtils.getLogger();
   /** L'atelier a planté dans cette session : l'écran de TaCZ s'ouvre à la place. */
   private static boolean enPanne;

   enum Onglet {
      ACCESSOIRES("accessoires"), CAMO("camo"), PERSO("perso");

      final String cle;

      Onglet(String cle) {
         this.cle = cle;
      }
   }

   /** Un onglet : ses éléments (construire), puis son dessin sous et sur les éléments. */
   interface Partie {
      void construire();

      /** Panneaux et textes, sous les éléments. */
      void dessiner(GuiGraphics g, int mouseX, int mouseY, float partialTick);

      /** Par-dessus les éléments (rien par défaut). */
      default void dessinerDessus(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      }

      /** Molette ; vrai si l'onglet l'a prise. */
      default boolean molette(double mouseX, double mouseY, double delta) {
         return false;
      }

      /** Ce qui, en changeant, demande de reconstruire l'onglet (inventaire, collection…). */
      default String signature() {
         return "";
      }
   }

   private Onglet onglet = Onglet.ACCESSOIRES;
   private final OngletAccessoires accessoires = new OngletAccessoires(this);
   private final OngletCamo camo = new OngletCamo(this);
   private final OngletPerso perso = new OngletPerso(this);
   /** Infobulle à dessiner à la fin de l'image (posée par l'élément survolé pendant son dessin). */
   @Nullable
   private List<Component> infobulle;
   private String signature = "";
   // en-tête, calculé à la construction
   private String nomArme = "", calibre = "";
   private int barreX, barreW;

   public AtelierArme() {
      super();
   }

   /** L'atelier a-t-il planté dans cette session ? (l'écran de TaCZ s'ouvre alors à sa place) */
   public static boolean enPanne() {
      return enPanne;
   }

   /** L'atelier a planté : journal, puis l'écran de TaCZ (au tick suivant) pour le reste de la session. */
   static void secours(Throwable t) {
      if (enPanne) return;
      enPanne = true;
      LOG.error("[Atelier] écran de modification HeraliZ en panne : retour à l'écran de TaCZ pour cette session", t);
      Minecraft mc = Minecraft.getInstance();
      mc.tell(() -> {
         if (mc.screen instanceof AtelierArme) mc.setScreen(new GunRefitScreen());
      });
   }

   // ------------------------------------------------------------------ accès pour les onglets
   static ItemStack arme() {
      LocalPlayer p = Minecraft.getInstance().player;
      return p == null ? ItemStack.EMPTY : p.getMainHandItem();
   }

   /** Texte de l'atelier (clé gui.heralisz_camo.atelier.*). */
   static String tr(String cle, Object... args) {
      return Component.translatable("gui.heralisz_camo.atelier." + cle, args).getString();
   }

   static Component trc(String cle, Object... args) {
      return Component.translatable("gui.heralisz_camo.atelier." + cle, args);
   }

   Font police() {
      return this.font;
   }

   void ajouter(AbstractWidget w) {
      this.addRenderableWidget(w);
   }

   /** Surface d'un panneau (voir {@link Bloc}). */
   void bloc(int x, int y, int w, int h) {
      this.addRenderableWidget(new Bloc(x, y, w, h));
   }

   void infobulle(List<Component> lignes) {
      this.infobulle = lignes;
   }

   void reconstruire() {
      this.rebuildWidgets();
   }

   /** Haut du bandeau du bas (caractéristiques, confirmation) : les panneaux s'arrêtent 8 px au-dessus. */
   int basPanneaux(int hauteurBandeau) {
      return this.height - 14 - hauteurBandeau - 8;
   }

   /** Les couteaux ont leurs finitions de lame : pas d'onglet Camo. */
   private static boolean camoPossible(ItemStack gun) {
      return KnifeSkins.of(gun) == null;
   }

   private Partie partie() {
      return switch (this.onglet) {
         case ACCESSOIRES -> this.accessoires;
         case CAMO -> this.camo;
         case PERSO -> this.perso;
      };
   }

   void changerOnglet(Onglet o) {
      if (o == this.onglet) return;
      this.onglet = o;
      RefitSkinTab.preview = null;
      RefitDecorTab.arreter();
      if (o != Onglet.ACCESSOIRES && RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
         RefitTransform.changeRefitScreenView(AttachmentType.NONE);
      }
      this.rebuildWidgets();
   }

   // ------------------------------------------------------------------ construction
   @Override
   public void init() {
      Echelle.forcer(this);
      this.clearWidgets();
      try {
         this.construire();
      } catch (RuntimeException | LinkageError e) {
         this.clearWidgets();
         secours(e);
      }
   }

   private void construire() {
      this.infobulle = null;
      Effets.vider();
      ItemStack gun = arme();
      IGun iGun = IGun.getIGunOrNull(gun);
      if (iGun == null) return;
      if (this.onglet == Onglet.CAMO && !camoPossible(gun)) this.onglet = Onglet.ACCESSOIRES;
      RefitDecorTab.open = this.onglet == Onglet.PERSO;
      if (this.onglet != Onglet.PERSO) RefitDecorTab.arreter();
      if (this.onglet != Onglet.CAMO) RefitSkinTab.preview = null;

      this.nomArme = gun.getHoverName().getString();
      GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).map(CommonGunIndex::getGunData).orElse(null);
      this.calibre = data == null ? "" : TimelessAPI.getClientAmmoIndex(data.getAmmoId()).map(ClientAmmoIndex::getName)
         .map(n -> Component.translatable(n).getString()).orElse("");

      // barre d'onglets, au centre en haut
      List<Onglet> onglets = new ArrayList<>();
      for (Onglet o : Onglet.values()) {
         if (o != Onglet.CAMO || camoPossible(gun)) onglets.add(o);
      }
      int[] largeurs = new int[onglets.size()];
      int total = 0;
      for (int i = 0; i < onglets.size(); i++) {
         largeurs[i] = (int) Ui.spacedWidth(this.font, tr("onglet." + onglets.get(i).cle).toUpperCase(Locale.ROOT), Ui.SMALL, 1.4f) + 20;
         total += largeurs[i];
      }
      this.barreW = total + 4;
      this.barreX = (this.width - this.barreW) / 2;
      int x = this.barreX + 2;
      for (int i = 0; i < onglets.size(); i++) {
         Onglet o = onglets.get(i);
         int w = largeurs[i];
         this.addRenderableWidget(new Zone(x, 12, w, 17, (g, z, mx, my, pt) -> this.dessinerOnglet(g, z, o), () -> this.changerOnglet(o), null));
         x += w;
      }

      // mode de tir (cliquable s'il y en a plusieurs) et fermeture
      FireMode mode = iGun.getFireMode(gun);
      boolean plusieurs = data != null && data.getFireModeSet() != null && data.getFireModeSet().size() > 1;
      String texteMode = tr("mode", tr("mode." + mode.name().toLowerCase(Locale.ROOT)));
      int cx = 16;
      if (!this.calibre.isEmpty()) {
         this.calibre = Dessins.couperEspace(this.font, this.calibre, Ui.SMALL, 0.6f, 110);
         cx += Dessins.largeurChip(this.font, this.calibre) + 5;
      }
      int mw = Dessins.largeurChip(this.font, texteMode) + (plusieurs ? 10 : 0);
      this.addRenderableWidget(new Zone(cx, 38, mw, 11, (g, z, mx, my, pt) -> {
         // pastille « MODE : AUTO » (avec la flèche de changement quand l'arme a plusieurs modes)
         int c = plusieurs ? Ui.accent() : Ui.MUTED;
         boolean survol = plusieurs && z.survol();
         int zx = z.getX(), zy = z.getY(), zw = z.getWidth();
         Ui.soft(g, zx, zy, zw, 11, Ui.argb(c, survol ? 56 : 34));
         Ui.softOutline(g, zx + 1, zy + 1, zw - 2, 9, Ui.argb(c, survol ? 170 : 90));
         int texte = Ui.argb(Ui.mix(c, 0xFFFFFF, survol ? 0.6f : 0.3f), 255);
         Ui.spaced(g, this.font, texteMode, Ui.SMALL, zx + 5, zy + 2.5f, texte, 0.6f);
         if (plusieurs) Dessins.cycle(g, zx + zw - 11, zy + 3, texte);
         if (survol) this.infobulle = List.of(trc("mode_aide"));
      }, plusieurs ? this::changerMode : null, null));

      String fermer = tr("fermer");
      String echap = tr("echap");
      int fw = this.font.width(Ui.lit(echap, Ui.SMALL)) + 8 + 5 + this.font.width(Ui.lit(fermer, Ui.FONT));
      this.addRenderableWidget(new Zone(this.width - 16 - fw, 14, fw, 12, (g, z, mx, my, pt) -> {
         int kw = Dessins.touche(g, this.font, echap, z.getX(), z.getY(), z.survol());
         g.drawString(this.font, Ui.lit(fermer, Ui.FONT), z.getX() + kw + 5, z.getY() + 1, Ui.argb(z.survol() ? Ui.TEXT : Ui.MUTED, 255), false);
      }, this::onClose, null));

      this.partie().construire();
      this.signature = this.signatureActuelle();
   }

   private void changerMode() {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p == null || p.isSpectator() || !IGun.mainHandHoldGun(p)) return;
      IClientPlayerGunOperator.fromLocalPlayer(p).fireSelect();
   }

   /** Ce qui, en changeant, reconstruit l'écran : accessoires posés, mode de tir, vue de la caméra, et ce que suit l'onglet. */
   private String signatureActuelle() {
      ItemStack gun = arme();
      IGun iGun = IGun.getIGunOrNull(gun);
      if (iGun == null) return "";
      StringBuilder sb = new StringBuilder();
      sb.append(iGun.getGunId(gun)).append('|').append(iGun.getFireMode(gun)).append('|').append(iGun.hasAttachmentLock(gun));
      for (AttachmentType t : AttachmentType.values()) {
         if (t != AttachmentType.NONE) sb.append('|').append(iGun.getAttachmentId(gun, t));
      }
      sb.append('|').append(RefitTransform.getCurrentTransformType()).append('|').append(this.partie().signature());
      return sb.toString();
   }

   // ------------------------------------------------------------------ vie de l'écran
   @Override
   public void tick() {
      try {
         ItemStack gun = arme();
         if (IGun.getIGunOrNull(gun) == null) {
            this.onClose();
            return;
         }
         // Camo et Personnaliser montrent l'arme entière (une vue refusée pendant un mouvement de caméra est redemandée)
         if (this.onglet != Onglet.ACCESSOIRES && RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
            RefitTransform.changeRefitScreenView(AttachmentType.NONE);
         }
         String s = this.signatureActuelle();
         if (!s.equals(this.signature)) this.rebuildWidgets();
      } catch (RuntimeException | LinkageError e) {
         secours(e);
      }
   }

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      try {
         this.infobulle = null;
         this.vignette(g);
         ItemStack gun = arme();
         if (IGun.getIGunOrNull(gun) == null) return;
         this.entete(g);
         Partie p = this.partie();
         p.dessiner(g, mouseX, mouseY, partialTick);
         for (GuiEventListener l : this.children()) {
            if (l instanceof Renderable r) r.render(g, mouseX, mouseY, partialTick);
         }
         p.dessinerDessus(g, mouseX, mouseY, partialTick);
         if (this.infobulle != null) Ui.tooltip(g, this.font, this.infobulle, mouseX, mouseY, Ui.accent());
      } catch (RuntimeException | LinkageError e) {
         secours(e);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      try {
         return super.mouseClicked(mouseX, mouseY, button);
      } catch (RuntimeException | LinkageError e) {
         secours(e);
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      try {
         if (this.partie().molette(mouseX, mouseY, delta)) return true;
      } catch (RuntimeException | LinkageError e) {
         secours(e);
      }
      return super.mouseScrolled(mouseX, mouseY, delta);
   }

   @Override
   public void onClose() {
      SonsUi.fermer(0.5F);
      super.onClose();
   }

   @Override
   public void removed() {
      Echelle.restaurer();
      RefitSkinTab.preview = null;
      RefitDecorTab.open = false;
      RefitDecorTab.arreter();
      super.removed();
   }

   // ------------------------------------------------------------------ dessin
   /** Bords assombris : l'arme au centre reste nette, les panneaux se lisent sur les côtés. */
   private void vignette(GuiGraphics g) {
      int w = this.width, h = this.height;
      Dessins.bandeVerticale(g, 0, Math.min(230, w / 3), h, 150, true);
      Dessins.bandeVerticale(g, w - Math.min(250, w / 3), w, h, 160, false);
      g.fillGradient(0, 0, w, 60, Ui.argb(0, 130), Ui.argb(0, 0));
      g.fillGradient(0, h - 100, w, h, Ui.argb(0, 0), Ui.argb(0, 160));
   }

   private void entete(GuiGraphics g) {
      Ui.spaced(g, this.font, tr("titre"), Ui.SMALL, 16, 12, Ui.argb(Ui.MUTED, 255), 1.4f);
      float max = (this.barreX - 24 - 16) / 1.45f;
      Dessins.titre(g, this.font, Dessins.couperEspace(this.font, this.nomArme, Ui.BOLD, 0.9f, max), Ui.BOLD, 16, 21, 1.45f,
         Ui.argb(Ui.TEXT, 255), 0.9f);
      if (!this.calibre.isEmpty()) Ui.chip(g, this.font, this.calibre, 16, 38, Ui.MUTED);
      Ui.glass(g, this.barreX, 10, this.barreW, 21, 170);
   }

   private void dessinerOnglet(GuiGraphics g, Zone z, Onglet o) {
      boolean actif = o == this.onglet;
      int x = z.getX(), y = z.getY(), w = z.getWidth(), h = z.getHeight();
      if (actif) {
         Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 16));
         g.fill(x + 8, y + h - 2, x + w - 8, y + h - 1, Ui.argb(Ui.accent(), 230));
      } else if (z.survol()) {
         Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 8));
      }
      int c = actif ? Ui.TEXT : z.survol() ? Ui.mix(Ui.MUTED, Ui.TEXT, 0.5f) : Ui.MUTED;
      Ui.spaced(g, this.font, tr("onglet." + o.cle), Ui.SMALL, x + 10, y + 5.5f, Ui.argb(c, 255), 1.4f);
   }
}
