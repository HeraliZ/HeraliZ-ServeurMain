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
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
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
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Table d'armurier : remplace l'écran de fabrication de TaCZ par un écran au style des écrans de clan (maquette validée) :
 * catégories à gauche, objets en cartes au centre (« Fabricable » / « Il manque 2 »), à droite l'objet choisi en 3D,
 * ses caractéristiques, ses matériaux (« 9 / 16 ») et le bouton Fabriquer.
 * <p>
 * Sous-classe de GunSmithTableScreen qui PILOTE l'écran de TaCZ au lieu de le refaire : TaCZ classe toujours les
 * recettes (onglets de la table, filtre « arme en main »), compte les matériaux, et son bouton de fabrication (caché) est
 * celui qu'on presse : TaCZ Addon garde ainsi ses fonctions (matériaux des coffres proches, Maj + clic pour fabriquer en
 * série). Le serveur revérifie tout. Si l'écran plante, celui de TaCZ revient jusqu'au redémarrage du jeu.
 * <p>
 * Mise en page (Tactique 2.35.0) : chaque élément a sa place MESURÉE avec la police (rien ne déborde de sa case), textes
 * dans des polices à leur vraie taille (jamais agrandis), posés sur un point entier de l'écran. Les mêmes constantes et
 * les mêmes calculs sont dans {@code outils/apercu_table_armurier.py}, qui dessine l'écran hors du jeu et liste tout
 * texte qui sortirait de sa case : changer l'un, c'est changer l'autre.
 */
public final class TableArmurier extends GunSmithTableScreen {
   private static final Logger LOG = LogUtils.getLogger();
   private static boolean enPanne;
   /** Lecture des stocks des coffres proches de TaCZ Addon (méthode de son mixin sur l'écran) ; null : absente. */
   @Nullable
   private static Method stocksAddon;
   private static boolean stocksCherches;

   /** Polices à leur vraie taille (DM Sans) : gras 16 (titre, chiffres des tuiles), gras 14 (nom de l'objet), 8 (petites légendes). */
   static final ResourceLocation GROS = Ui.GROS, NOM = Ui.NOM, MINI = Ui.MINI;

   // ------------------------------------------------------------------ constantes de mise en page (= outils/apercu_table_armurier.py)
   static final int PANNEAU_L = 600, PANNEAU_H = 326, MARGE = 12;
   static final int CAT_X = 8, CAT_W = 104, GRILLE_X = 120;
   static final int CARTE_W = 60, CARTE_H = 62, ECART = 4, COLONNES = 4;
   /** Hauteur de l'en-tête sur une ligne / sur deux lignes (filtres sous la recherche). */
   static final int ENTETE_1 = 38, ENTETE_2 = 52;
   static final int RECHERCHE_Y = 15, RECHERCHE_H = 16, RECHERCHE_MIN = 150, LIGNE2_Y = 36, LIGNE2_H = 12;
   /** Case à cocher : côté, écart case-texte, écart entre filtres, écart recherche-filtres, écart avant « Fermer ». */
   static final int CASE = 9, CASE_TEXTE = 6, FILTRE_ECART = 12, APRES_RECHERCHE = 12, AVANT_FERMER = 14;
   static final int DETAIL_PAD = 8, TUILE_H = 36, TUILE_ECART = 4, TUILE_PAD = 6, TUILE_INTER = 6, BOUTON_H = 19;
   /** Milieux des capitales : petit titre, grand titre (= milieu du champ de recherche), touche « Échap » (haut). */
   static final int TITRE_PETIT_CY = 11, TITRE_CY = 23, FERMER_Y = 18;
   /** Cartes : milieu des capitales du nom et de l'état, sous le haut de la carte. */
   static final int CARTE_NOM_CY = 44, CARTE_ETAT_CY = 55;
   static final float ESP_TITRE_PETIT = 1.4f, ESP_TITRE = 1.0f, ESP_NOM = 0.9f, ESP_CHIP = 0.6f, ESP_SECTION = 1.0f, ESP_BOUTON = 1.2f;

   /** État d'une recette pour une carte : matériaux qui manquent (0 : fabricable). */
   private record Etat(int manquants) {}

   /** Texte ajusté à une place : police, espacement des capitales, texte (coupé avec « … » en dernier recours). */
   record Ajuste(String texte, ResourceLocation police, float espace) {}

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
   private int px, py, pw, ph, gx, gy, gw, cx, cy, cw, rangsVisibles, dx, dy, dw, dh, entete;

   public TableArmurier(GunSmithTableMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
      this.recherche = new HzField(0, 0, 10, RECHERCHE_H, 40, HzField.TOUT).placeholder(tr("recherche")).remplacerAuFocus(false);
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

   /** Texte sans codes de couleur « § » (dessinés lettre par lettre, ils laisseraient leur lettre : « ABALLE DE 9MM »). */
   static String brut(@Nullable String s) {
      if (s == null) return "";
      String b = ChatFormatting.stripFormatting(s);
      return b == null ? "" : b;
   }

   // ------------------------------------------------------------------ mesures
   private float largeurCaps(String s, ResourceLocation f, float esp) {
      return Ui.spacedWidth(this.font, s.toUpperCase(Locale.ROOT), f, esp);
   }

   private int largeur(String s, ResourceLocation f) {
      return this.font.width(Ui.lit(s, f));
   }

   /**
    * Capitales espacées qui tiennent dans {@code max} : l'espacement voulu, puis resserré, puis la police plus petite
    * ({@link #MINI}) si {@code reduire}, et en dernier recours coupé avec « … ».
    */
   Ajuste ajuster(String s, ResourceLocation police, float esp, float max, boolean reduire) {
      float[] espaces = {esp, esp / 2f, 0f};
      for (float e : espaces) {
         if (this.largeurCaps(s, police, e) <= max) return new Ajuste(s, police, e);
      }
      if (reduire && !police.equals(MINI)) {
         for (float e : espaces) {
            if (this.largeurCaps(s, MINI, e) <= max) return new Ajuste(s, MINI, e);
         }
         return new Ajuste(Dessins.couperEspace(this.font, s, MINI, 0f, max), MINI, 0f);
      }
      return new Ajuste(Dessins.couperEspace(this.font, s, police, 0f, max), police, 0f);
   }

   /** Texte ajusté : bord gauche en x, milieu des capitales sur cy. */
   private void dessiner(GuiGraphics g, Ajuste a, float x, float cy, int argb) {
      Ui.spacedMilieu(g, this.font, a.texte(), a.police(), x, cy, argb, a.espace());
   }

   // ------------------------------------------------------------------ ordre des recettes
   private static final Pattern PALIER = Pattern.compile("^(.*\\S)\\s+(I{1,3}|IV|V|VI{1,3}|IX|X)$");

   /** Clé de tri : nom sans son palier, valeur du palier en chiffres romains (0 : aucun), nom complet. */
   private record CleTri(String base, int palier, String nom) {}

   static int romain(String r) {
      return switch (r) {
         case "I" -> 1;
         case "II" -> 2;
         case "III" -> 3;
         case "IV" -> 4;
         case "V" -> 5;
         case "VI" -> 6;
         case "VII" -> 7;
         case "VIII" -> 8;
         case "IX" -> 9;
         case "X" -> 10;
         default -> 0;
      };
   }

   private static CleTri cleTri(String nom) {
      String n = brut(nom).trim();
      Matcher m = PALIER.matcher(n);
      if (m.matches()) return new CleTri(m.group(1), romain(m.group(2)), n);
      return new CleTri(n, 0, n);
   }

   private static Locale langueJoueur() {
      String code = Minecraft.getInstance().getLanguageManager().getSelected();
      return code == null || code.isEmpty() ? Locale.ROOT : Locale.forLanguageTag(code.replace('_', '-'));
   }

   /**
    * Ordre alphabétique du nom affiché, dans la langue du joueur, sans tenir compte des majuscules ni des accents ; les
    * paliers d'un même objet côte à côte et dans l'ordre (« Batterie II », « Batterie III », « Batterie IV », puis
    * « Caoutchouc II »…) : le nom sans palier d'abord, puis la valeur du palier (pas l'ordre des lettres).
    */
   private void trier(List<ResourceLocation> liste) {
      Locale langue = langueJoueur();
      Collator base = Collator.getInstance(langue);
      base.setStrength(Collator.PRIMARY);
      Collator fin = Collator.getInstance(langue);
      fin.setStrength(Collator.TERTIARY);
      Map<ResourceLocation, CleTri> cles = new HashMap<>();
      for (ResourceLocation id : liste) {
         GunSmithTableRecipe g = this.recettes.get(id);
         cles.put(id, cleTri(g == null ? id.toString() : g.getOutput().getHoverName().getString()));
      }
      Comparator<ResourceLocation> ordre = (a, b) -> {
         CleTri ka = cles.get(a), kb = cles.get(b);
         int c = base.compare(ka.base(), kb.base());
         if (c != 0) return c;
         c = Integer.compare(ka.palier(), kb.palier());
         if (c != 0) return c;
         c = fin.compare(ka.nom(), kb.nom());
         return c != 0 ? c : a.toString().compareTo(b.toString());
      };
      liste.sort(ordre);
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

   /** Case à cocher d'un filtre : rend la zone (texte coupé si la place manque, texte entier en infobulle). */
   private Zone filtre(int x, int y, int h, String texte, int maxTexte, java.util.function.BooleanSupplier coche, Runnable action) {
      String vu = Ui.ellipsis(this.font, texte, Ui.FONT, maxTexte);
      boolean coupe = !vu.equals(texte);
      int w = CASE + CASE_TEXTE + this.largeur(vu, Ui.FONT);
      return new Zone(x, y, w, h, (g, z, mx, my, pt) -> {
         // case et texte centrés sur le milieu de la zone (milieu des capitales)
         int by = Math.round(z.getY() + z.getHeight() / 2f - CASE / 2f);
         Ui.pastille(g, z.getX(), by, CASE, Ui.accent(), coche.getAsBoolean(), z.survol());
         Ui.gauche(g, this.font, Ui.lit(vu, Ui.FONT), Ui.FONT, z.getX() + CASE + CASE_TEXTE, by + CASE / 2f, Ui.argb(z.survol() ? Ui.TEXT : Ui.MUTED, 255));
         if (coupe && z.survol()) this.infobulle = List.of(Ui.lit(texte, Ui.FONT));
      }, action, null);
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
               if (g != null && brut(g.getOutput().getHoverName().getString()).toLowerCase(Locale.ROOT).contains(q)) liste.add(id);
            }
         }
      }
      liste.removeIf(id -> !this.recettes.containsKey(id));
      // ordre alphabétique, paliers côte à côte (gardé avec les filtres : ils ne font que retirer)
      this.trier(liste);
      // « avec les composants disponibles » : seulement ce qu'on peut fabriquer tout de suite
      if (dispoSeulement && !creatif) {
         liste.removeIf(id -> {
            Etat e = this.etats.get(id);
            return e == null || e.manquants() > 0;
         });
      }
      this.affichees = liste;

      // cadre
      this.pw = Math.min(PANNEAU_L, this.width - 24);
      this.ph = Math.min(PANNEAU_H, this.height - 20);
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2;
      this.ajouter(new Bloc(this.px, this.py, this.pw, this.ph));
      this.cx = this.px + GRILLE_X;
      this.cw = COLONNES * CARTE_W + (COLONNES - 1) * ECART;

      // en-tête : titre à gauche, recherche, deux filtres, « Échap Fermer » à droite ; chaque place est mesurée
      String fermer = AtelierArme.tr("fermer"), echap = AtelierArme.tr("echap");
      int fw = this.largeur(echap, Ui.SMALL) + 8 + 5 + this.largeur(fermer, Ui.FONT);
      int fx = this.px + this.pw - MARGE - fw;
      int titreW = (int) Math.ceil(Math.max(this.largeurCaps(tr("titre_petit"), Ui.SMALL, ESP_TITRE_PETIT), this.largeurCaps(tr("titre"), GROS, ESP_TITRE)));
      int sx = Math.max(this.cx, this.px + MARGE + titreW + AVANT_FERMER);
      String enMain = tr("en_main"), dispo = tr("dispo");
      int ew = CASE + CASE_TEXTE + this.largeur(enMain, Ui.FONT), dw2 = CASE + CASE_TEXTE + this.largeur(dispo, Ui.FONT);
      int filtresW = ew + FILTRE_ECART + dw2;
      int uneLigne = fx - AVANT_FERMER - filtresW - APRES_RECHERCHE - sx;
      GunPackList filtres = t.heralisz$filtres();
      java.util.function.BooleanSupplier enMainCoche = () -> filtres != null && filtres.isByHandSelected();
      Runnable enMainAction = () -> {
         if (filtres == null) return;
         filtres.setByHandSelected(!filtres.isByHandSelected());
         this.defil = 0;
         this.rebuildWidgets();
      };
      Runnable dispoAction = () -> {
         dispoSeulement = !dispoSeulement;
         this.defil = 0;
         this.rebuildWidgets();
      };
      int rechercheW;
      if (uneLigne >= RECHERCHE_MIN) {
         // tout sur une ligne
         this.entete = ENTETE_1;
         rechercheW = Math.min(uneLigne, this.cw);
         int fx0 = sx + rechercheW + APRES_RECHERCHE;
         this.ajouter(this.filtre(fx0, this.py + RECHERCHE_Y, RECHERCHE_H, enMain, Integer.MAX_VALUE, enMainCoche, enMainAction));
         this.ajouter(this.filtre(fx0 + ew + FILTRE_ECART, this.py + RECHERCHE_Y, RECHERCHE_H, dispo, Integer.MAX_VALUE, () -> dispoSeulement, dispoAction));
      } else {
         // recherche sur la 1re ligne (alignée sur la grille), filtres dessous
         this.entete = ENTETE_2;
         rechercheW = Math.max(60, Math.min(this.cx + this.cw, fx - AVANT_FERMER) - sx);
         int place = this.px + this.pw - MARGE - sx;
         int maxTexte1 = Integer.MAX_VALUE, maxTexte2 = Integer.MAX_VALUE;
         if (filtresW > place) {
            int chacun = (place - FILTRE_ECART) / 2 - CASE - CASE_TEXTE;
            maxTexte1 = chacun;
            maxTexte2 = chacun;
         }
         Zone z1 = this.filtre(sx, this.py + LIGNE2_Y, LIGNE2_H, enMain, maxTexte1, enMainCoche, enMainAction);
         this.ajouter(z1);
         this.ajouter(this.filtre(sx + z1.getWidth() + FILTRE_ECART, this.py + LIGNE2_Y, LIGNE2_H, dispo, maxTexte2, () -> dispoSeulement, dispoAction));
      }
      this.recherche.setX(sx);
      this.recherche.setY(this.py + RECHERCHE_Y);
      this.recherche.setWidth(rechercheW);
      this.ajouter(this.recherche);
      this.ajouter(new Zone(fx, this.py + FERMER_Y, fw, 11, (g, z, mx, my, pt) -> {
         int kw = Dessins.touche(g, this.font, echap, z.getX(), z.getY(), z.survol());
         Ui.gauche(g, this.font, Ui.lit(fermer, Ui.FONT), Ui.FONT, z.getX() + kw + 5, z.getY() + 5.5f, Ui.argb(z.survol() ? Ui.TEXT : Ui.MUTED, 255));
      }, this::onClose, null));

      // catégories, à gauche
      this.gx = this.px + CAT_X;
      this.gy = this.py + this.entete + 8;
      this.gw = CAT_W;
      int y = this.gy;
      int rang = Math.max(12, Math.min(15, (this.py + this.ph - 10 - this.gy) / Math.max(1, t.heralisz$onglets().size())));
      for (Map.Entry<ResourceLocation, TabConfig> e : t.heralisz$onglets().entrySet()) {
         if (y + rang - 1 > this.py + this.ph - 8) break;
         ResourceLocation id = e.getKey();
         TabConfig cfg = e.getValue();
         int n = t.heralisz$recettes().getOrDefault(id, List.of()).size();
         this.ajouter(new Zone(this.gx, y, this.gw, rang - 1, (g, z, mx, my, pt) -> this.dessinerCategorie(g, z, id, cfg, n),
            () -> this.choisirCategorie(id), null));
         y += rang;
      }

      // cartes, au centre
      this.cy = this.py + this.entete + 22;
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
      this.dy = this.py + this.entete + 8;
      this.dw = this.px + this.pw - 8 - this.dx;
      this.dh = this.py + this.ph - 8 - this.dy;
      if (choisie != null) {
         Etat e = this.etatChoisie(choisie, p);
         int bw = this.dw - 2 * DETAIL_PAD;
         String texte = e.manquants() == 0 ? tr("fabriquer") : this.texteManque(choisie, p);
         String vu = Dessins.couperEspace(this.font, texte, Ui.BOLD, ESP_BOUTON, bw - 12);
         boolean coupe = !vu.equals(texte);
         HzButton fabriquer = new HzButton(this.dx + DETAIL_PAD, this.dy + this.dh - 9 - BOUTON_H, bw, BOUTON_H,
            Component.literal(vu), Ui.accent(), this::fabriquer) {
            @Override
            protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
               super.renderWidget(g, mouseX, mouseY, partialTick);
               if (!this.isHovered()) return;
               if (coupe) TableArmurier.this.infobulle = List.of(Ui.lit(texte, Ui.FONT));
               else if (this.active && addonPresent()) TableArmurier.this.infobulle = List.of(Ui.lit(tr("serie"), Ui.FONT));
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
      return s.isEmpty() ? "?" : brut(s.getHoverName().getString());
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
         this.dessinerDetail(g, mouseX, mouseY);
         if (this.infobulle != null) Ui.tooltip(g, this.font, this.infobulle, mouseX, mouseY, Ui.accent());
      } catch (RuntimeException | LinkageError e) {
         this.secours(e);
      }
   }

   private void dessinerCadre(GuiGraphics g) {
      GunSmithTableScreenAccessor t = this.tacz();
      Ui.glass(g, this.px, this.py, this.pw, this.ph);
      Ui.spacedMilieu(g, this.font, tr("titre_petit"), Ui.SMALL, this.px + MARGE, this.py + TITRE_PETIT_CY, Ui.argb(Ui.MUTED, 255), ESP_TITRE_PETIT);
      Ui.spacedMilieu(g, this.font, tr("titre"), GROS, this.px + MARGE, this.py + TITRE_CY, Ui.argb(Ui.TEXT, 255), ESP_TITRE);
      Ui.hairline(g, this.px + 8, this.px + this.pw - 8, this.py + this.entete, 0xFFFFFF, 34, true, true);
      // titre de la grille et nombre d'objets
      ResourceLocation type = t.heralisz$type();
      String titre;
      if (!this.texteRecherche.trim().isEmpty()) titre = tr("resultats");
      else {
         TabConfig cfg = type == null ? null : t.heralisz$onglets().get(type);
         titre = cfg == null ? "" : brut(cfg.getName().getString());
      }
      String nb = String.valueOf(this.affichees.size());
      int nbW = (int) Math.ceil(this.largeurCaps(nb, Ui.SMALL, 0.8f));
      int sy = this.py + this.entete + 10;
      Ui.section(g, this.font, Dessins.couperEspace(this.font, titre, Ui.SMALL, ESP_SECTION, this.cw - nbW - 24), this.cx, sy, this.cw - nbW - 8);
      Ui.spacedRight(g, this.font, nb, Ui.SMALL, this.cx + this.cw, sy, Ui.argb(Ui.FAINT, 255), 0.8f);
      if (t.heralisz$onglets().isEmpty()) {
         Ui.centre(g, this.font, Ui.lit(Ui.ellipsis(this.font, tr("aucune"), Ui.FONT, this.cw - 8), Ui.FONT), Ui.FONT, this.cx + this.cw / 2f, this.cy + 30, Ui.argb(Ui.MUTED, 255));
      } else if (this.affichees.isEmpty()) {
         Ui.centre(g, this.font, Ui.lit(Ui.ellipsis(this.font, tr("aucun_resultat"), Ui.FONT, this.cw - 8), Ui.FONT), Ui.FONT, this.cx + this.cw / 2f, this.cy + 30, Ui.argb(Ui.MUTED, 255));
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
      String nombre = String.valueOf(n);
      int nw = this.largeur(nombre, Ui.SMALL);
      String complet = brut(cfg.getName().getString());
      String nom = Ui.ellipsis(this.font, complet, Ui.FONT, w - 19 - 5 - nw - 4);
      float cyl = y + h / 2f;
      Ui.gauche(g, this.font, Ui.lit(nom, Ui.FONT), Ui.FONT, x + 19, cyl, Ui.argb(actif ? Ui.TEXT : Ui.mix(Ui.MUTED, Ui.TEXT, 0.4f), 255));
      Ui.droite(g, this.font, Ui.lit(nombre, Ui.SMALL), Ui.SMALL, x + w - 5, cyl, Ui.argb(Ui.FAINT, 255));
      if (z.survol() && !nom.equals(complet)) this.infobulle = List.of(Ui.lit(complet, Ui.FONT));
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
         Ui.gauche(g, this.font, Ui.lit("×" + sortie.getCount(), Ui.SMALL), Ui.SMALL, x + 4, y + 7, Ui.argb(Ui.MUTED, 255));
         g.pose().popPose();
      }
      String complet = brut(sortie.getHoverName().getString());
      String nom = Ui.ellipsis(this.font, complet, Ui.FONT, w - 8);
      Ui.gauche(g, this.font, Ui.lit(nom, Ui.FONT), Ui.FONT, x + 4, y + CARTE_NOM_CY, Ui.argb(Ui.TEXT, 255));
      Etat e = this.etats.get(id);
      boolean ok = e != null && e.manquants() == 0;
      String st = ok ? tr("fabricable") : tr("manque", e == null ? "?" : String.valueOf(e.manquants()));
      int c = ok ? Ui.OK : Ui.FAINT;
      int pt = y + CARTE_ETAT_CY - 3;
      Ui.point(g, x + 4, pt, c, 255);
      Ajuste a = this.ajuster(st, Ui.SMALL, ESP_CHIP, w - 11 - 4, true);
      this.dessiner(g, a, x + 11, pt + 2.5f, Ui.argb(ok ? Ui.OK : Ui.MUTED, 255));
      if (z.survol()) this.infobulle = List.of(Ui.lit(complet, Ui.BOLD));
   }

   /** À droite : l'objet choisi en 3D, ses caractéristiques et ses matériaux. */
   private void dessinerDetail(GuiGraphics g, int mouseX, int mouseY) {
      GunSmithTableRecipe r = this.tacz().heralisz$recette();
      LocalPlayer p = Minecraft.getInstance().player;
      if (r == null || p == null) {
         int lw = this.dw - 2 * DETAIL_PAD;
         Component c = Ui.lit(tr("choisir"), Ui.FONT);
         List<net.minecraft.util.FormattedCharSequence> lignes = this.font.split(c, lw);
         float cyl = this.dy + this.dh / 2f - (lignes.size() - 1) * 11 / 2f;
         for (net.minecraft.util.FormattedCharSequence l : lignes) {
            g.pose().pushPose();
            Ui.placer(g, this.dx + (this.dw - this.font.getSplitter().stringWidth(l)) / 2f, cyl - Ui.milieu(Ui.FONT));
            g.drawString(this.font, l, 0, 0, Ui.argb(Ui.MUTED, 255), false);
            g.pose().popPose();
            cyl += 11;
         }
         return;
      }
      ItemStack sortie = r.getOutput();
      int x = this.dx + DETAIL_PAD, w = this.dw - 2 * DETAIL_PAD;
      // modèle 3D qui tourne (un peu plus petit si l'en-tête est sur deux lignes)
      int modeleH = Math.max(56, Math.min(74, this.dh - 190));
      this.modele(g, sortie, this.dx + 4, this.dy + 4, this.dw - 8, modeleH);
      int y = this.dy + modeleH + 8;
      String complet = brut(sortie.getHoverName().getString());
      String nom = Dessins.couperEspace(this.font, complet, NOM, ESP_NOM, w);
      Ui.spacedMilieu(g, this.font, nom, NOM, x, y + 5, Ui.argb(Ui.TEXT, 255), ESP_NOM);
      if (!nom.equals(complet) && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14) this.infobulle = List.of(Ui.lit(complet, Ui.BOLD));
      y += 16;
      // pastilles : catégorie, calibre (chacune coupée pour tenir sur la ligne)
      TabConfig cfg = this.tacz().heralisz$onglets().get(r.getResult().getGroup());
      String cat = cfg == null ? "" : brut(cfg.getName().getString());
      String calibre = brut(calibre(sortie));
      int catW = cat.isEmpty() ? 0 : Dessins.largeurChip(this.font, cat), calW = calibre.isEmpty() ? 0 : Dessins.largeurChip(this.font, calibre);
      if (catW > 0 && calW > 0 && catW + 4 + calW > w) {
         int moitie = (w - 4) / 2;
         if (catW <= moitie) calW = w - 4 - catW;
         else if (calW <= moitie) catW = w - 4 - calW;
         else {
            catW = moitie;
            calW = w - 4 - moitie;
         }
      } else {
         catW = Math.min(catW, w);
         calW = Math.min(calW, w);
      }
      int chipX = x;
      if (catW > 0) chipX += Ui.chip(g, this.font, Dessins.couperEspace(this.font, cat, Ui.SMALL, ESP_CHIP, catW - 9), chipX, y, Ui.MUTED) + 4;
      if (calW > 0) Ui.chip(g, this.font, Dessins.couperEspace(this.font, calibre, Ui.SMALL, ESP_CHIP, calW - 9), chipX, y, Ui.MUTED);
      y += 17;
      // caractéristiques (armes), effets (accessoires), quantité (munitions)
      y = this.dessinerCaracteristiques(g, sortie, x, y, w);
      // matériaux
      Ui.section(g, this.font, tr("materiaux"), x, y, w);
      y += 12;
      List<GunSmithTableIngredient> in = r.getInputs();
      boolean creatif = p.isCreative();
      int fin = this.dy + this.dh - 9 - BOUTON_H - 6;
      int ligne = Math.max(11, Math.min(14, (fin - y) / Math.max(1, in.size())));
      int lignes = Math.max(0, (fin - y) / ligne);
      int montrees = in.size() <= lignes ? in.size() : Math.max(0, lignes - 1);
      for (int i = 0; i < montrees; i++) {
         GunSmithTableIngredient m = in.get(i);
         ItemStack s = apercu(m);
         g.pose().pushPose();
         g.pose().translate(x, y + (ligne - 10) / 2f, 0);
         g.pose().scale(0.625f, 0.625f, 1);
         g.renderItem(s, 0, 0);
         g.pose().popPose();
         int a = this.dispo(i), besoin = m.getCount();
         Component droite = creatif ? Ui.lit("∞ / " + Ui.nombre(besoin), Ui.FONT)
            : Ui.lit(Ui.nombre(a), Ui.BOLD).withStyle(st -> st.withColor(a >= besoin ? Ui.OK : Ui.DANGER))
               .append(Ui.lit(" / " + Ui.nombre(besoin), Ui.FONT).withStyle(st -> st.withColor(Ui.MUTED)));
         int dw2 = this.font.width(droite);
         String n = Ui.ellipsis(this.font, s.isEmpty() ? "?" : brut(s.getHoverName().getString()), Ui.FONT, w - 14 - dw2 - 6);
         float cyl = y + ligne / 2f;
         Ui.gauche(g, this.font, Ui.lit(n, Ui.FONT), Ui.FONT, x + 14, cyl, Ui.argb(Ui.TEXT, 230));
         Ui.droite(g, this.font, droite, Ui.FONT, x + w, cyl, Ui.argb(Ui.TEXT, 255));
         y += ligne;
      }
      if (montrees < in.size()) {
         String plus = tr("autres", in.size() - montrees);
         Ui.gauche(g, this.font, Ui.lit(Ui.ellipsis(this.font, plus, Ui.FONT, w - 14), Ui.FONT), Ui.FONT, x + 14, y + ligne / 2f, Ui.argb(Ui.MUTED, 255));
      }
   }

   /** Tuile de statistique : chiffre en gras 16 (police à sa vraie taille), légende en petites capitales qui tient dans la tuile. */
   private void tuile(GuiGraphics g, int x, int y, int w, int h, String valeur, String legende) {
      Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 8));
      Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(0xFFFFFF, 14));
      int max = w - 2 * TUILE_PAD;
      ResourceLocation pv = this.largeur(valeur, GROS) <= max ? GROS : Ui.BOLD;
      String v = Ui.ellipsis(this.font, valeur, pv, max);
      Ajuste a = this.ajuster(legende, Ui.SMALL, 0.8f, max, true);
      // chiffre et légende : bloc centré en hauteur dans la tuile (des capitales du chiffre à celles de la légende)
      float hv = Ui.capitales(pv), hl = Ui.capitales(a.police());
      float haut = (h - (hv + TUILE_INTER + hl)) / 2f;
      Ui.gauche(g, this.font, Ui.lit(v, pv), pv, x + TUILE_PAD, y + haut + hv / 2f, Ui.argb(Ui.TEXT, 255));
      this.dessiner(g, a, x + TUILE_PAD, y + h - haut - hl / 2f, Ui.argb(Ui.MUTED, 255));
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
         int tw = (w - 2 * TUILE_ECART) / 3;
         this.tuile(g, x, y, tw, TUILE_H, degats, tr("stat.degats"));
         this.tuile(g, x + tw + TUILE_ECART, y, tw, TUILE_H, cadence, tr("stat.cadence"));
         this.tuile(g, x + 2 * (tw + TUILE_ECART), y, w - 2 * (tw + TUILE_ECART), TUILE_H, chargeur, tr("stat.chargeur"));
         return y + TUILE_H + 8;
      }
      if (IAttachment.getIAttachmentOrNull(sortie) != null) {
         int cx2 = x, cy2 = y;
         for (Effets.Effet e : Effets.de(sortie)) {
            String texte = Dessins.couperEspace(this.font, brut(e.texte()), Ui.SMALL, ESP_CHIP, w - 10);
            int cw2 = Dessins.largeurChip(this.font, texte);
            if (cx2 + cw2 > x + w) {
               cx2 = x;
               cy2 += 14;
               if (cy2 > y + 14) break;
            }
            Ui.chip(g, this.font, texte, cx2, cy2, e.couleur());
            cx2 += cw2 + 4;
         }
         return cy2 + 20;
      }
      if (sortie.getCount() > 1 || sortie.getItem() instanceof IAmmo) {
         Ui.gauche(g, this.font, Ui.lit(Ui.ellipsis(this.font, tr("par_fabrication", sortie.getCount()), Ui.FONT, w), Ui.FONT), Ui.FONT, x, y + 4, Ui.argb(Ui.MUTED, 255));
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
