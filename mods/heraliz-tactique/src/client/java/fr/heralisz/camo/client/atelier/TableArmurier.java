package fr.heralisz.camo.client.atelier;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.components.GunPackList;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.RenderDistance;
import fr.heralisz.camo.client.kit.Echelle;
import fr.heralisz.camo.client.kit.HzButton;
import fr.heralisz.camo.client.kit.HzField;
import fr.heralisz.camo.client.kit.SonsUi;
import fr.heralisz.camo.client.kit.Ui;
import fr.heralisz.camo.mixin.GunSmithTableScreenAccessor;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Table d'armurier : remplace l'écran de fabrication de TaCZ par un écran au style des écrans de clan (maquette validée) :
 * catégories à gauche, objets en cartes au centre (« Fabricable » / « Il manque 2 »), à droite l'objet choisi en 3D,
 * ses caractéristiques, ses matériaux (« 9 / 16 ») et le bouton Fabriquer.
 * <p>
 * Sous-classe de GunSmithTableScreen qui PILOTE l'écran de TaCZ au lieu de le refaire : TaCZ classe toujours les
 * recettes (onglets de la table, filtre « arme en main »), compte les matériaux, et son bouton de fabrication (caché) est
 * celui qu'on presse : TaCZ Addon garde ainsi ses fonctions (matériaux des coffres proches, Maj + clic pour fabriquer en
 * série). Le serveur revérifie tout. Si l'écran plante, celui de TaCZ revient jusqu'au redémarrage du jeu.
 */
public final class TableArmurier extends GunSmithTableScreen {
   private static final Logger LOG = LogUtils.getLogger();
   private static boolean enPanne;
   /** Lecture des stocks des coffres proches de TaCZ Addon (méthode de son mixin sur l'écran) ; null : absente. */
   @Nullable
   private static Method stocksAddon;
   private static boolean stocksCherches;

   private static final int CARTE_W = 60, CARTE_H = 62, ECART = 4, COLONNES = 4;

   /** État d'une recette pour une carte : matériaux qui manquent (0 : fabricable). */
   private record Etat(int manquants) {}

   private final HzField recherche;
   private String texteRecherche = "";
   private boolean rechercheChangee;
   /** Première rangée de cartes affichée (molette). */
   private int defil;
   @Nullable
   private ImageButton boutonTaCZ;
   private final Map<ResourceLocation, GunSmithTableRecipe> recettes = new HashMap<>();
   private final Map<ResourceLocation, Etat> etats = new HashMap<>();
   /** Recettes affichées au centre (catégorie choisie, ou résultats de la recherche dans toutes les catégories). */
   private List<ResourceLocation> affichees = List.of();
   @Nullable
   private List<Component> infobulle;
   /** Nos éléments, les seuls dessinés (ceux de TaCZ restent dans l'écran, cachés et inactifs). */
   private final List<AbstractWidget> nos = new ArrayList<>();
   // géométrie
   private int px, py, pw, ph, gx, gy, gw, cx, cy, cw, rangsVisibles, dx, dy, dw, dh;

   public TableArmurier(GunSmithTableMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
      this.recherche = new HzField(0, 0, 10, 16, 40, HzField.TOUT).placeholder(tr("recherche")).remplacerAuFocus(false);
      this.recherche.setResponder(v -> {
         this.texteRecherche = v;
         this.rechercheChangee = true;
      });
   }

   public static boolean enPanne() {
      return enPanne;
   }

   private void secours(Throwable t) {
      if (enPanne) return;
      enPanne = true;
      LOG.error("[Table d'armurier] écran HeraliZ en panne : retour à l'écran de TaCZ pour cette session", t);
      Minecraft mc = Minecraft.getInstance();
      GunSmithTableMenu menu = this.getMenu();
      Component titre = this.getTitle();
      mc.tell(() -> {
         if (mc.screen instanceof TableArmurier && mc.player != null) mc.setScreen(new GunSmithTableScreen(menu, mc.player.getInventory(), titre));
      });
   }

   private GunSmithTableScreenAccessor tacz() {
      return (GunSmithTableScreenAccessor) (Object) this;
   }

   static String tr(String cle, Object... args) {
      return Component.translatable("gui.heralisz_camo.table." + cle, args).getString();
   }

   // ------------------------------------------------------------------ construction
   @Override
   public void init() {
      Echelle.forcer(this);
      try {
         // TaCZ classe les recettes, compte les matériaux et crée ses boutons (TaCZ Addon enveloppe celui de fabrication)
         super.init();
         this.boutonTaCZ = null;
         int bx = this.leftPos + 289, by = this.topPos + 162;
         for (GuiEventListener l : new ArrayList<>(this.children())) {
            if (l instanceof AbstractWidget w) {
               if (w instanceof ImageButton ib && ib.getX() == bx && ib.getY() == by) this.boutonTaCZ = ib;
               w.visible = false;
               w.active = false;
            }
         }
         this.nos.clear();
         this.construire();
      } catch (RuntimeException | LinkageError e) {
         this.clearWidgets();
         this.nos.clear();
         this.secours(e);
      }
   }

   private <W extends AbstractWidget> W ajouter(W w) {
      this.nos.add(w);
      return this.addRenderableWidget(w);
   }

   private void construire() {
      this.infobulle = null;
      Effets.vider();
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null) return;
      GunSmithTableScreenAccessor t = this.tacz();
      // recettes connues (une fois par construction) et état de chacune pour les cartes
      this.recettes.clear();
      this.etats.clear();
      List<ItemStack> stocks = this.stocks(p);
      boolean creatif = p.isCreative();
      for (List<ResourceLocation> ids : t.heralisz$recettes().values()) {
         for (ResourceLocation id : ids) {
            Recipe<?> r = mc.level.getRecipeManager().byKey(id).orElse(null);
            if (r instanceof GunSmithTableRecipe g) {
               this.recettes.put(id, g);
               this.etats.put(id, new Etat(creatif ? 0 : manquants(g, stocks)));
            }
         }
      }
      // ce qui s'affiche au centre
      ResourceLocation type = t.heralisz$type();
      String q = this.texteRecherche.trim().toLowerCase(Locale.ROOT);
      List<ResourceLocation> liste = new ArrayList<>();
      if (q.isEmpty()) {
         if (type != null) liste.addAll(t.heralisz$recettes().getOrDefault(type, List.of()));
      } else {
         for (List<ResourceLocation> ids : t.heralisz$recettes().values()) {
            for (ResourceLocation id : ids) {
               GunSmithTableRecipe g = this.recettes.get(id);
               if (g != null && g.getOutput().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) liste.add(id);
            }
         }
      }
      // « avec les composants disponibles » : seulement ce qu'on peut fabriquer tout de suite
      if (dispoSeulement && !creatif) {
         liste.removeIf(id -> {
            Etat e = this.etats.get(id);
            return e == null || e.manquants() > 0;
         });
      }
      this.affichees = liste;

      // cadre
      this.pw = Math.min(600, this.width - 24);
      this.ph = Math.min(326, this.height - 20);
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2;
      this.ajouter(new Bloc(this.px, this.py, this.pw, this.ph));

      // en-tête : recherche, « arme en main », fermer
      this.recherche.setX(this.px + 124);
      this.recherche.setY(this.py + 15);
      this.recherche.setWidth(Math.max(90, Math.min(176, this.pw - 124 - 440)));
      this.ajouter(this.recherche);
      GunPackList filtres = t.heralisz$filtres();
      String enMain = tr("en_main");
      int ew = 9 + 6 + this.font.width(Ui.lit(enMain, Ui.FONT)) + 4;
      int ex = this.recherche.getX() + this.recherche.getWidth() + 10;
      this.ajouter(new Zone(ex, this.py + 15, ew, 16, (g, z, mx, my, pt) -> {
         boolean coche = filtres != null && filtres.isByHandSelected();
         Ui.pastille(g, z.getX(), z.getY() + 4, 9, Ui.accent(), coche, z.survol());
         g.drawString(this.font, Ui.lit(enMain, Ui.FONT), z.getX() + 15, z.getY() + 4, Ui.argb(z.survol() ? Ui.TEXT : Ui.MUTED, 255), false);
      }, () -> {
         if (filtres == null) return;
         filtres.setByHandSelected(!filtres.isByHandSelected());
         this.defil = 0;
         this.rebuildWidgets();
      }, null));
      String dispo = tr("dispo");
      int dw2 = 9 + 6 + this.font.width(Ui.lit(dispo, Ui.FONT)) + 4;
      this.ajouter(new Zone(ex + ew + 8, this.py + 15, dw2, 16, (g, z, mx, my, pt) -> {
         Ui.pastille(g, z.getX(), z.getY() + 4, 9, Ui.accent(), dispoSeulement, z.survol());
         g.drawString(this.font, Ui.lit(dispo, Ui.FONT), z.getX() + 15, z.getY() + 4, Ui.argb(z.survol() ? Ui.TEXT : Ui.MUTED, 255), false);
      }, () -> {
         dispoSeulement = !dispoSeulement;
         this.defil = 0;
         this.rebuildWidgets();
      }, null));
      String fermer = AtelierArme.tr("fermer"), echap = AtelierArme.tr("echap");
      int fw = this.font.width(Ui.lit(echap, Ui.SMALL)) + 8 + 5 + this.font.width(Ui.lit(fermer, Ui.FONT));
      this.ajouter(new Zone(this.px + this.pw - 12 - fw, this.py + 17, fw, 12, (g, z, mx, my, pt) -> {
         int kw = Dessins.touche(g, this.font, echap, z.getX(), z.getY(), z.survol());
         g.drawString(this.font, Ui.lit(fermer, Ui.FONT), z.getX() + kw + 5, z.getY() + 1, Ui.argb(z.survol() ? Ui.TEXT : Ui.MUTED, 255), false);
      }, this::onClose, null));

      // catégories, à gauche
      this.gx = this.px + 8;
      this.gy = this.py + 46;
      this.gw = 104;
      int y = this.gy;
      int rang = Math.max(12, Math.min(15, (this.py + this.ph - 10 - this.gy) / Math.max(1, t.heralisz$onglets().size())));
      for (Map.Entry<ResourceLocation, TabConfig> e : t.heralisz$onglets().entrySet()) {
         ResourceLocation id = e.getKey();
         TabConfig cfg = e.getValue();
         int n = t.heralisz$recettes().getOrDefault(id, List.of()).size();
         this.ajouter(new Zone(this.gx, y, this.gw, rang - 1, (g, z, mx, my, pt) -> this.dessinerCategorie(g, z, id, cfg, n),
            () -> this.choisirCategorie(id), null));
         y += rang;
      }

      // cartes, au centre
      this.cx = this.px + 120;
      this.cy = this.py + 60;
      this.cw = COLONNES * CARTE_W + (COLONNES - 1) * ECART;
      this.rangsVisibles = Math.max(1, (this.py + this.ph - 10 - this.cy + ECART) / (CARTE_H + ECART));
      int rangs = (this.affichees.size() + COLONNES - 1) / COLONNES;
      this.defil = Math.max(0, Math.min(this.defil, rangs - this.rangsVisibles));
      GunSmithTableRecipe choisie = t.heralisz$recette();
      for (int k = this.defil * COLONNES; k < Math.min(this.affichees.size(), (this.defil + this.rangsVisibles) * COLONNES); k++) {
         int n = k - this.defil * COLONNES;
         ResourceLocation id = this.affichees.get(k);
         GunSmithTableRecipe g = this.recettes.get(id);
         if (g == null) continue;
         int x0 = this.cx + (n % COLONNES) * (CARTE_W + ECART), y0 = this.cy + (n / COLONNES) * (CARTE_H + ECART);
         this.ajouter(new Zone(x0, y0, CARTE_W, CARTE_H, (gg, z, mx, my, pt) -> this.dessinerCarte(gg, z, id, g),
            () -> this.choisirRecette(g), null));
      }

      // détail, à droite
      this.dx = this.cx + this.cw + 8;
      this.dy = this.py + 46;
      this.dw = this.px + this.pw - 8 - this.dx;
      this.dh = this.py + this.ph - 8 - this.dy;
      if (choisie != null) {
         Etat e = this.etatChoisie(choisie, p);
         int bw = this.dw - 20;
         HzButton fabriquer = new HzButton(this.dx + 10, this.dy + this.dh - 28, bw, 19,
            Component.literal(e.manquants() == 0 ? tr("fabriquer") : this.texteManque(choisie, p)), Ui.accent(), this::fabriquer) {
            @Override
            protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
               super.renderWidget(g, mouseX, mouseY, partialTick);
               if (this.isHovered() && this.active && addonPresent()) {
                  TableArmurier.this.infobulle = List.of(Ui.lit(tr("serie"), Ui.FONT));
               }
            }
         }.look(HzButton.Look.PRIMARY);
         if (e.manquants() > 0) {
            fabriquer.look(HzButton.Look.OUTLINE).danger();
            fabriquer.active = false;
         }
         this.ajouter(fabriquer);
      }
   }

   // ------------------------------------------------------------------ actions
   private void choisirCategorie(ResourceLocation id) {
      GunSmithTableScreenAccessor t = this.tacz();
      t.heralisz$setType(id);
      t.heralisz$setListeType(t.heralisz$recettes().getOrDefault(id, List.of()));
      this.defil = 0;
      if (!this.texteRecherche.isEmpty()) {
         this.recherche.setValue("");
         this.texteRecherche = "";
      }
      this.rebuildWidgets();
   }

   private void choisirRecette(GunSmithTableRecipe g) {
      GunSmithTableScreenAccessor t = this.tacz();
      ResourceLocation groupe = g.getResult().getGroup();
      if (groupe != null && t.heralisz$recettes().containsKey(groupe)) {
         t.heralisz$setType(groupe);
         t.heralisz$setListeType(t.heralisz$recettes().get(groupe));
      }
      t.heralisz$setRecette(g);
      t.heralisz$compter(g);
      this.rebuildWidgets();
   }

   /** Fabriquer : le bouton (caché) de TaCZ, enveloppé par TaCZ Addon s'il est là (Maj + clic : en série). */
   private void fabriquer() {
      if (this.boutonTaCZ != null) {
         this.boutonTaCZ.onPress();
      }
   }

   @Override
   protected void containerTick() {
      try {
         this.recherche.tick();
         if (this.rechercheChangee) {
            this.rechercheChangee = false;
            this.defil = 0;
            boolean focus = this.recherche.isFocused();
            this.rebuildWidgets();
            if (focus) {
               this.setFocused(this.recherche);
               this.recherche.setFocused(true);
            }
         }
      } catch (RuntimeException | LinkageError e) {
         this.secours(e);
      }
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (key == GLFW.GLFW_KEY_ESCAPE) {
         if (this.recherche.isFocused()) {
            this.recherche.setFocused(false);
            this.setFocused(null);
            return true;
         }
         this.onClose();
         return true;
      }
      if (this.recherche.isFocused()) {
         // la saisie garde ses touches (sinon « E », la touche de l'inventaire, fermerait l'écran)
         this.recherche.keyPressed(key, scan, mods);
         return true;
      }
      return super.keyPressed(key, scan, mods);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      try {
         if (!this.recherche.isMouseOver(mouseX, mouseY) && this.recherche.isFocused()) {
            this.recherche.setFocused(false);
         }
         return super.mouseClicked(mouseX, mouseY, button);
      } catch (RuntimeException | LinkageError e) {
         this.secours(e);
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (mouseX >= this.cx && mouseX < this.cx + this.cw && mouseY >= this.cy && mouseY < this.py + this.ph) {
         int rangs = (this.affichees.size() + COLONNES - 1) / COLONNES;
         int avant = this.defil;
         this.defil = Math.max(0, Math.min(rangs - this.rangsVisibles, this.defil - (int) Math.signum(delta)));
         if (this.defil != avant) this.rebuildWidgets();
         return true;
      }
      return false;
   }

   @Override
   public void onClose() {
      SonsUi.fermer(0.5F);
      super.onClose();
   }

   @Override
   public void removed() {
      Echelle.restaurer();
      super.removed();
   }

   // ------------------------------------------------------------------ matériaux
   /** Les stocks du joueur : inventaire (comme TaCZ) et, avec TaCZ Addon, coffres proches. */
   private List<ItemStack> stocks(LocalPlayer p) {
      List<ItemStack> out = new ArrayList<>(p.getInventory().items);
      out.addAll(this.stocksAddon());
      return out;
   }

   private List<ItemStack> stocksAddon() {
      if (!stocksCherches) {
         stocksCherches = true;
         try {
            stocksAddon = this.getClass().getMethod("taczaddon$getExternalDisplayStacks");
         } catch (ReflectiveOperationException | RuntimeException e) {
            stocksAddon = null;
         }
      }
      if (stocksAddon == null) return List.of();
      try {
         Object o = stocksAddon.invoke(this);
         List<ItemStack> out = new ArrayList<>();
         if (o instanceof List<?> l) {
            for (Object s : l) {
               if (s instanceof ItemStack is && !is.isEmpty()) out.add(is);
            }
         }
         return out;
      } catch (ReflectiveOperationException | RuntimeException e) {
         return List.of();
      }
   }

   private static boolean addonPresent() {
      return ModList.get() != null && ModList.get().isLoaded("taczaddon");
   }

   /** Nombre de matériaux de la recette qui manquent dans ces stocks. */
   private static int manquants(GunSmithTableRecipe g, List<ItemStack> stocks) {
      int n = 0;
      for (GunSmithTableIngredient in : g.getInputs()) {
         int a = 0;
         for (ItemStack s : stocks) {
            if (!s.isEmpty() && in.getIngredient().test(s)) a += s.getCount();
         }
         if (a < in.getCount()) n++;
      }
      return n;
   }

   /** Recette choisie : comptes de TaCZ (et TaCZ Addon), les mêmes que vérifie son bouton de fabrication. */
   private Etat etatChoisie(GunSmithTableRecipe g, LocalPlayer p) {
      if (p.isCreative()) return new Etat(0);
      Int2IntArrayMap comptes = this.tacz().heralisz$comptes();
      List<GunSmithTableIngredient> in = g.getInputs();
      int n = 0;
      for (int i = 0; i < in.size(); i++) {
         int a = comptes != null && i < comptes.size() ? comptes.get(i) : 0;
         if (a < in.get(i).getCount()) n++;
      }
      return new Etat(n);
   }

   private int dispo(int i) {
      Int2IntArrayMap comptes = this.tacz().heralisz$comptes();
      return comptes != null && i < comptes.size() ? comptes.get(i) : 0;
   }

   private String texteManque(GunSmithTableRecipe g, LocalPlayer p) {
      List<GunSmithTableIngredient> in = g.getInputs();
      int n = 0, seul = -1;
      for (int i = 0; i < in.size(); i++) {
         if (this.dispo(i) < in.get(i).getCount()) {
            n++;
            seul = i;
         }
      }
      if (n == 1) {
         GunSmithTableIngredient m = in.get(seul);
         return tr("manque_un", m.getCount() - this.dispo(seul), nomIngredient(m));
      }
      return tr("manque_plusieurs", n);
   }

   private static ItemStack apercu(GunSmithTableIngredient in) {
      ItemStack[] items = in.getIngredient().getItems();
      if (items.length == 0) return ItemStack.EMPTY;
      return items[(int) ((Util.getMillis() / 1000L) % items.length)];
   }

   private static String nomIngredient(GunSmithTableIngredient in) {
      ItemStack s = apercu(in);
      return s.isEmpty() ? "?" : s.getHoverName().getString();
   }

   // ------------------------------------------------------------------ dessin
   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      try {
         this.infobulle = null;
         Ui.fond(g, this.width, this.height);
         this.dessinerCadre(g);
         for (AbstractWidget w : this.nos) {
            w.render(g, mouseX, mouseY, partialTick);
         }
         this.dessinerDetail(g);
         if (this.infobulle != null) Ui.tooltip(g, this.font, this.infobulle, mouseX, mouseY, Ui.accent());
      } catch (RuntimeException | LinkageError e) {
         this.secours(e);
      }
   }

   private void dessinerCadre(GuiGraphics g) {
      GunSmithTableScreenAccessor t = this.tacz();
      Ui.glass(g, this.px, this.py, this.pw, this.ph);
      Ui.spaced(g, this.font, tr("titre_petit"), Ui.SMALL, this.px + 12, this.py + 10, Ui.argb(Ui.MUTED, 255), 1.4f);
      Dessins.titre(g, this.font, tr("titre"), Ui.BOLD, this.px + 12, this.py + 19, 1.45f, Ui.argb(Ui.TEXT, 255), 1.0f);
      Ui.hairline(g, this.px + 8, this.px + this.pw - 8, this.py + 38, 0xFFFFFF, 34, true, true);
      // titre de la grille
      ResourceLocation type = t.heralisz$type();
      String titre;
      if (!this.texteRecherche.trim().isEmpty()) titre = tr("resultats");
      else {
         TabConfig cfg = type == null ? null : t.heralisz$onglets().get(type);
         titre = cfg == null ? "" : cfg.getName().getString();
      }
      Ui.section(g, this.font, titre, this.cx, this.py + 48, this.cw - 24);
      Ui.spacedRight(g, this.font, String.valueOf(this.affichees.size()), Ui.SMALL, this.cx + this.cw, this.py + 48, Ui.argb(Ui.FAINT, 255), 0.8f);
      if (t.heralisz$onglets().isEmpty()) {
         Ui.centered(g, this.font, Ui.lit(tr("aucune"), Ui.FONT), this.cx + this.cw / 2f, this.cy + 30, Ui.argb(Ui.MUTED, 255));
      } else if (this.affichees.isEmpty()) {
         Ui.centered(g, this.font, Ui.lit(tr("aucun_resultat"), Ui.FONT), this.cx + this.cw / 2f, this.cy + 30, Ui.argb(Ui.MUTED, 255));
      }
      int rangs = (this.affichees.size() + COLONNES - 1) / COLONNES;
      if (rangs > this.rangsVisibles) {
         int h = this.rangsVisibles * (CARTE_H + ECART) - ECART;
         int bh = Math.max(10, h * this.rangsVisibles / rangs);
         int by = this.cy + (h - bh) * this.defil / Math.max(1, rangs - this.rangsVisibles);
         g.fill(this.cx + this.cw + 2, this.cy, this.cx + this.cw + 4, this.cy + h, Ui.argb(0xFFFFFF, 18));
         g.fill(this.cx + this.cw + 2, by, this.cx + this.cw + 4, by + bh, Ui.argb(Ui.accent(), 200));
      }
      // panneau de détail
      Ui.soft(g, this.dx, this.dy, this.dw, this.dh, Ui.argb(0xFFFFFF, 6));
      Ui.softOutline(g, this.dx + 1, this.dy + 1, this.dw - 2, this.dh - 2, Ui.argb(0xFFFFFF, 16));
   }

   private void dessinerCategorie(GuiGraphics g, Zone z, ResourceLocation id, TabConfig cfg, int n) {
      boolean actif = id.equals(this.tacz().heralisz$type()) && this.texteRecherche.trim().isEmpty();
      int x = z.getX(), y = z.getY(), w = z.getWidth(), h = z.getHeight();
      int accent = Ui.accent();
      if (actif) {
         Ui.soft(g, x, y, w, h, Ui.argb(accent, 34));
         g.fill(x, y + 2, x + 2, y + h - 2, Ui.argb(accent, 255));
      } else if (z.survol()) {
         Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 10));
      }
      ItemStack icone = cfg.icon();
      if (icone != null && !icone.isEmpty()) {
         g.pose().pushPose();
         g.pose().translate(x + 5, y + (h - 10) / 2f, 0);
         g.pose().scale(0.625f, 0.625f, 1);
         g.renderItem(icone, 0, 0);
         g.pose().popPose();
      }
      String nom = Ui.ellipsis(this.font, cfg.getName().getString(), Ui.FONT, w - 20 - 18);
      g.drawString(this.font, Ui.lit(nom, Ui.FONT), x + 19, y + (h - 8) / 2, Ui.argb(actif ? Ui.TEXT : Ui.mix(Ui.MUTED, Ui.TEXT, 0.4f), 255), false);
      Ui.right(g, this.font, Ui.lit(String.valueOf(n), Ui.SMALL), x + w - 5, y + (h - 6) / 2f, Ui.argb(Ui.FAINT, 255));
   }

   /** Filtre « avec les composants disponibles » (gardé tant que le jeu tourne). */
   private static boolean dispoSeulement;
   private static java.lang.reflect.Method couleurRarete;
   private static boolean couleurCherchee;

   /** Couleur RVB de la rareté (HeraliZ Loot, par réflexion), -1 : aucune. */
   private static int couleurRarete(ItemStack st) {
      if (!couleurCherchee) {
         couleurCherchee = true;
         try {
            couleurRarete = Class.forName("fr.heraliz.loot.client.RaretesClient").getMethod("couleur", ItemStack.class);
         } catch (ReflectiveOperationException | LinkageError ex) {
            couleurRarete = null;
         }
      }
      if (couleurRarete == null) return -1;
      try {
         return ((Number) couleurRarete.invoke(null, st)).intValue();
      } catch (ReflectiveOperationException | RuntimeException ex) {
         return -1;
      }
   }

   private void dessinerCarte(GuiGraphics g, Zone z, ResourceLocation id, GunSmithTableRecipe r) {
      int x = z.getX(), y = z.getY(), w = z.getWidth(), h = z.getHeight();
      GunSmithTableRecipe choisie = this.tacz().heralisz$recette();
      boolean sel = choisie != null && choisie.getId().equals(id);
      int accent = Ui.accent();
      Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, z.survol() ? 14 : 7));
      ItemStack sortie = r.getOutput();
      // rareté de l'objet : dégradé très léger de sa couleur, du haut de la carte vers le bas
      int rarete = couleurRarete(sortie);
      if (rarete >= 0) g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, Ui.argb(rarete, 46), Ui.argb(rarete, 0));
      Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, sel ? Ui.argb(accent, 230) : Ui.argb(0xFFFFFF, z.survol() ? 50 : 18));
      g.pose().pushPose();
      g.pose().translate(x + w / 2f - 16, y + 4, 0);
      g.pose().scale(2.0f, 2.0f, 1);
      g.renderItem(sortie, 0, 0);
      g.pose().popPose();
      if (sortie.getCount() > 1) {
         g.pose().pushPose();
         g.pose().translate(0, 0, 300);
         g.drawString(this.font, Ui.lit("×" + sortie.getCount(), Ui.SMALL), x + 4, y + 4, Ui.argb(Ui.MUTED, 255), false);
         g.pose().popPose();
      }
      String nom = Ui.ellipsis(this.font, sortie.getHoverName().getString(), Ui.FONT, w - 8);
      g.drawString(this.font, Ui.lit(nom, Ui.FONT), x + 4, y + h - 23, Ui.argb(Ui.TEXT, 255), false);
      Etat e = this.etats.get(id);
      boolean ok = e != null && e.manquants() == 0;
      String st = ok ? tr("fabricable") : tr("manque", e == null ? "?" : String.valueOf(e.manquants()));
      int c = ok ? Ui.OK : Ui.FAINT;
      Ui.point(g, x + 4, y + h - 11, c, 255);
      Ui.spaced(g, this.font, st, Ui.SMALL, x + 12, y + h - 12, Ui.argb(ok ? Ui.OK : Ui.MUTED, 255), 0.6f);
      if (z.survol()) this.infobulle = List.of(Ui.lit(sortie.getHoverName().getString(), Ui.BOLD));
   }

   /** À droite : l'objet choisi en 3D, ses caractéristiques et ses matériaux. */
   private void dessinerDetail(GuiGraphics g) {
      GunSmithTableRecipe r = this.tacz().heralisz$recette();
      LocalPlayer p = Minecraft.getInstance().player;
      if (r == null || p == null) {
         Ui.centered(g, this.font, Ui.lit(tr("choisir"), Ui.FONT), this.dx + this.dw / 2f, this.dy + this.dh / 2f, Ui.argb(Ui.MUTED, 255));
         return;
      }
      ItemStack sortie = r.getOutput();
      int x = this.dx + 10, w = this.dw - 20;
      // modèle 3D qui tourne
      this.modele(g, sortie, this.dx + 4, this.dy + 4, this.dw - 8, 74);
      int y = this.dy + 82;
      String nom = Dessins.couperEspace(this.font, sortie.getHoverName().getString(), Ui.BOLD, 0.9f, w / 1.3f);
      Dessins.titre(g, this.font, nom, Ui.BOLD, x, y, 1.3f, Ui.argb(Ui.TEXT, 255), 0.9f);
      y += 15;
      // pastilles : catégorie, calibre
      int chipX = x;
      TabConfig cfg = this.tacz().heralisz$onglets().get(r.getResult().getGroup());
      if (cfg != null) chipX += Ui.chip(g, this.font, cfg.getName().getString(), chipX, y, Ui.MUTED) + 4;
      String calibre = calibre(sortie);
      if (!calibre.isEmpty()) Ui.chip(g, this.font, Dessins.couperEspace(this.font, calibre, Ui.SMALL, 0.6f, x + w - chipX - 10), chipX, y, Ui.MUTED);
      y += 16;
      // caractéristiques (armes), effets (accessoires), quantité (munitions)
      y = this.dessinerCaracteristiques(g, sortie, x, y, w);
      // matériaux
      Ui.section(g, this.font, tr("materiaux"), x, y, w);
      y += 12;
      List<GunSmithTableIngredient> in = r.getInputs();
      boolean creatif = p.isCreative();
      int fin = this.dy + this.dh - 34;
      int ligne = Math.max(11, Math.min(14, (fin - y) / Math.max(1, in.size())));
      for (int i = 0; i < in.size() && y + ligne <= fin + 2; i++) {
         GunSmithTableIngredient m = in.get(i);
         ItemStack s = apercu(m);
         g.pose().pushPose();
         g.pose().translate(x, y + (ligne - 10) / 2f, 0);
         g.pose().scale(0.625f, 0.625f, 1);
         g.renderItem(s, 0, 0);
         g.pose().popPose();
         int a = this.dispo(i), besoin = m.getCount();
         Component droite = creatif ? Ui.lit("∞ / " + besoin, Ui.FONT)
            : Ui.lit(Ui.nombre(a), Ui.BOLD).withStyle(st -> st.withColor(a >= besoin ? Ui.OK : Ui.DANGER))
               .append(Ui.lit(" / " + Ui.nombre(besoin), Ui.FONT).withStyle(st -> st.withColor(Ui.MUTED)));
         int dw2 = this.font.width(droite);
         String n = Ui.ellipsis(this.font, s.isEmpty() ? "?" : s.getHoverName().getString(), Ui.FONT, w - 14 - dw2 - 6);
         g.drawString(this.font, Ui.lit(n, Ui.FONT), x + 14, y + (ligne - 8) / 2, Ui.argb(Ui.TEXT, 230), false);
         g.drawString(this.font, droite, x + w - dw2, y + (ligne - 8) / 2, Ui.argb(Ui.TEXT, 255), false);
         y += ligne;
      }
   }

   private int dessinerCaracteristiques(GuiGraphics g, ItemStack sortie, int x, int y, int w) {
      if (IGun.getIGunOrNull(sortie) != null) {
         IGun iGun = IGun.getIGunOrNull(sortie);
         GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(sortie)).map(CommonGunIndex::getGunData).orElse(null);
         String degats = "—", cadence = "—";
         for (Stats.Ligne l : Stats.calculer(sortie, null)) {
            if ("damage".equals(l.cle())) degats = l.valeur();
            else if ("rpm".equals(l.cle())) cadence = l.valeur().replaceAll("[^0-9]", "");
         }
         String chargeur = data == null ? "—" : String.valueOf(data.getAmmoAmount());
         int tw = (w - 8) / 3;
         Ui.tile(g, this.font, x, y, tw, 36, degats, tr("stat.degats"));
         Ui.tile(g, this.font, x + tw + 4, y, tw, 36, cadence, tr("stat.cadence"));
         Ui.tile(g, this.font, x + 2 * (tw + 4), y, tw, 36, chargeur, tr("stat.chargeur"));
         return y + 44;
      }
      if (IAttachment.getIAttachmentOrNull(sortie) != null) {
         int cx2 = x, cy2 = y;
         for (Effets.Effet e : Effets.de(sortie)) {
            int cw2 = Dessins.largeurChip(this.font, e.texte());
            if (cx2 + cw2 > x + w) {
               cx2 = x;
               cy2 += 14;
               if (cy2 > y + 14) break;
            }
            Ui.chip(g, this.font, e.texte(), cx2, cy2, e.couleur());
            cx2 += cw2 + 4;
         }
         return cy2 + 20;
      }
      if (sortie.getCount() > 1 || sortie.getItem() instanceof IAmmo) {
         g.drawString(this.font, Ui.lit(tr("par_fabrication", sortie.getCount()), Ui.FONT), x, y, Ui.argb(Ui.MUTED, 255), false);
         return y + 16;
      }
      return y + 4;
   }

   /** Modèle 3D de l'objet, qui tourne lentement (comme l'aperçu de TaCZ), dans le rectangle donné. */
   private void modele(GuiGraphics g, ItemStack stack, int x, int y, int w, int h) {
      if (stack.isEmpty()) return;
      RenderDistance.markGuiRenderTimestamp();
      g.enableScissor(x, y, x + w, y + h);
      g.pose().pushPose();
      g.pose().translate(x + w / 2f, y + h / 2f, 150);
      float echelle = Math.min(w * 0.42f, h * 0.9f);
      g.pose().scale(echelle, -echelle, echelle);
      float rot = (Util.getMillis() % 8000L) * (360.0F / 8000.0F);
      g.pose().mulPose(Axis.XP.rotationDegrees(15.0F));
      g.pose().mulPose(Axis.YP.rotationDegrees(rot));
      Lighting.setupForFlatItems();
      Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         g.pose(), g.bufferSource(), null, 0);
      g.flush();
      Lighting.setupFor3DItems();
      g.pose().popPose();
      g.disableScissor();
   }

   private static String calibre(ItemStack stack) {
      IGun iGun = IGun.getIGunOrNull(stack);
      if (iGun == null) return "";
      GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(stack)).map(CommonGunIndex::getGunData).orElse(null);
      if (data == null) return "";
      return TimelessAPI.getClientAmmoIndex(data.getAmmoId()).map(ClientAmmoIndex::getName).map(n -> Component.translatable(n).getString()).orElse("");
   }
}
