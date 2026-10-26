package fr.heraliz.tactique.armurier;

import fr.heralisz.camo.client.kit.Echelle;
import fr.heralisz.camo.client.kit.HzButton;
import fr.heralisz.camo.client.kit.HzField;
import fr.heralisz.camo.client.kit.SonsUi;
import fr.heralisz.camo.client.kit.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Registre de l'armurier (op ; maquette validée le 23/10, maquettes/registre_armurier.png ; style des écrans de clan) :
 * onglets de la Table d'armurier, liste des recettes à gauche (modifiée / ajoutée / supprimée), à droite ce que la
 * recette donne et ce qu'elle demande ; les objets se choisissent dans la barre d'objets (cases 1 à 9). Rien n'est décidé
 * ici : « Enregistrer », « Supprimer » et « Revenir au fichier » partent au serveur, qui revérifie tout et renvoie la
 * liste à jour.
 */
public final class EcranArmurier extends Screen {
   private static final int LIGNE_LISTE = 18, LIGNE = 18, CASE = 18;
   private static final int[] ETATS = {Ui.MUTED, Ui.AMBRE, Ui.OK, Ui.DANGER};

   /** Recette en cours d'édition (copie locale, envoyée seulement à « Enregistrer »). */
   private static final class Brouillon {
      String id = "";
      String onglet;
      byte etat = -1;
      ItemStack resultat = ItemStack.EMPTY;
      int quantite = 1;
      final List<Ligne> lignes = new ArrayList<>();
   }

   /** Ligne demandée : ingrédient d'origine gardé ({@code ingredient}) ou objet choisi dans la barre ({@code objet}). */
   private static final class Ligne {
      ItemStack icone;
      int quantite;
      String ingredient;
      ItemStack objet;

      Ligne(ItemStack icone, int quantite, String ingredient, ItemStack objet) {
         this.icone = icone;
         this.quantite = quantite;
         this.ingredient = ingredient;
         this.objet = objet;
      }
   }

   private PaquetEdition donnees;
   private int onglet, page, debutLignes, caseChoisie = -1;
   private Brouillon b;
   /** Caler la liste sur la page de la recette choisie (après une réception ; l'op feuillette ensuite librement). */
   private boolean calerPage;
   private boolean aRefaire;
   private int px, py, pw, ph, lx, ly, lw, lh, rx, rw;
   /** Place du nom de l'objet donné (à gauche des boutons de la recette) et texte de la barre d'objets, calculés dans init(). */
   private int nomMax, barreX;
   private String barreTexte = "";
   /** Boutons au texte coupé et leur texte complet (infobulle au survol). */
   private final List<HzButton> coupes = new ArrayList<>();
   private final List<String> textesCoupes = new ArrayList<>();

   private EcranArmurier(PaquetEdition d) {
      super(Component.translatable("gui.heraliz_tactique.armurier.titre"));
      this.donnees = d;
   }

   public static void brancher() {
      Armurier.EDITION_CLIENT.brancher(EcranArmurier::recevoir);
   }

   private static void recevoir(PaquetEdition d) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof EcranArmurier e) {
         e.donnees = d;
         e.choisir(d.choisie());
         e.aRefaire = true;
         return;
      }
      SonsUi.ouvrir(0.5F);
      EcranArmurier e = new EcranArmurier(d);
      e.choisir(d.choisie());
      mc.setScreen(e);
   }

   // ------------------------------------------------------------------ données
   private String ongletCourant() {
      return Armurier.ONGLETS.get(onglet);
   }

   private List<PaquetEdition.Recette> recettesOnglet() {
      List<PaquetEdition.Recette> l = new ArrayList<>();
      for (PaquetEdition.Recette r : donnees.recettes()) if (r.onglet().equals(ongletCourant())) l.add(r);
      return l;
   }

   private int lignesListe() {
      return Math.max(1, (lh - 26) / LIGNE_LISTE);
   }

   private int pages() {
      return Math.max(1, (recettesOnglet().size() + lignesListe() - 1) / lignesListe());
   }

   /** Sélectionne la recette {@code id} (onglet et page compris) ; garde l'actuelle si {@code id} est vide. */
   private void choisir(String id) {
      String cible = id.isEmpty() && b != null ? b.id : id;
      if (cible.isEmpty()) {
         if (b != null && b.id.isEmpty()) return; // nouvelle recette pas encore enregistrée : gardée
         b = null;
         return;
      }
      for (PaquetEdition.Recette r : donnees.recettes()) {
         if (!r.id().equals(cible)) continue;
         int o = Armurier.ONGLETS.indexOf(r.onglet());
         if (o >= 0) onglet = o;
         List<PaquetEdition.Recette> l = recettesOnglet();
         int i = l.indexOf(r);
         if (i >= 0) page = i / lignesListe();
         b = brouillon(r);
         calerPage = true;
         return;
      }
      b = null;
   }

   private static Brouillon brouillon(PaquetEdition.Recette r) {
      Brouillon n = new Brouillon();
      n.id = r.id();
      n.onglet = r.onglet();
      n.etat = r.etat();
      n.quantite = Math.max(1, r.resultat().getCount());
      n.resultat = r.resultat().copyWithCount(1);
      for (PaquetEdition.Entree e : r.entrees()) n.lignes.add(new Ligne(e.icone(), e.quantite(), e.ingredient(), ItemStack.EMPTY));
      return n;
   }

   private static ItemStack caseBarre(int k) {
      var p = Minecraft.getInstance().player;
      if (p == null) return ItemStack.EMPTY;
      ItemStack s = p.getInventory().getItem(k);
      return s.is(Armurier.REGISTRE.get()) ? ItemStack.EMPTY : s;
   }

   /** Case choisie : celle cliquée, sinon la première qui a un objet (-1 : aucune). */
   private int caseActive() {
      if (caseChoisie >= 0 && !caseBarre(caseChoisie).isEmpty()) return caseChoisie;
      for (int k = 0; k < 9; k++) if (!caseBarre(k).isEmpty()) return k;
      return -1;
   }

   private ItemStack objetChoisi() {
      int k = caseActive();
      return k < 0 ? ItemStack.EMPTY : caseBarre(k).copyWithCount(1);
   }

   private static String tr(String cle, Object... a) {
      return I18n.get("gui.heraliz_tactique.armurier." + cle, a);
   }

   private void envoyer(PaquetModif m) {
      Armurier.envoyerAuServeur(m);
      SonsUi.clic(0.5F);
   }

   // ------------------------------------------------------------------ widgets
   private HzField champ(int x, int y, int w, int valeur, java.util.function.IntConsumer apres) {
      // champ du kit (25/10, audit des écrans) : chiffres en DM Sans, plus la police pixel de Minecraft
      HzField e = new HzField(x, y, w, 16, 3, HzField.NOMBRE).accent(Ui.OR);
      e.setValue(String.valueOf(valeur));
      e.setResponder(s -> {
         try {
            apres.accept(Integer.parseInt(s));
         } catch (NumberFormatException ignore) {
         }
      });
      return addRenderableWidget(e);
   }

   @Override
   protected void init() {
      Echelle.forcer(this);
      pw = Math.min(620, width - 16);
      ph = Math.min(340, height - 12);
      px = (width - pw) / 2;
      py = (height - ph) / 2;
      lx = px + 14;
      ly = py + 66;
      lw = 196;
      lh = ph - 66 - 40;
      rx = lx + lw + 8;
      rw = px + pw - 14 - rx;
      // page de la recette choisie par le serveur (la hauteur de la liste n'est connue qu'ici)
      if (calerPage && b != null && !b.id.isEmpty()) {
         int i = recettesOnglet().stream().map(PaquetEdition.Recette::id).toList().indexOf(b.id);
         if (i >= 0) page = i / lignesListe();
      }
      calerPage = false;
      coupes.clear();
      textesCoupes.clear();
      // onglets (texte en capitales grasses espacées, mesuré comme HzButton le dessine) : si la ligne est trop courte,
      // les onglets courts gardent leur largeur, les autres se partagent le reste et leur nom est coupé avec « … »
      int x = px + 14, dispo = pw - 28, n = Armurier.ONGLETS.size();
      int[] nat = new int[n], larg = new int[n];
      boolean[] fixe = new boolean[n];
      for (int i = 0; i < n; i++) nat[i] = (int) Math.ceil(largeurOnglet(tr("onglet." + Armurier.ONGLETS.get(i)))) + 12;
      int reste = dispo - 3 * n, libres = n;
      for (boolean change = true; change && libres > 0; ) {
         change = false;
         int part = reste / libres;
         for (int i = 0; i < n; i++) {
            if (fixe[i] || nat[i] > part) continue;
            fixe[i] = true;
            larg[i] = nat[i];
            reste -= nat[i];
            libres--;
            change = true;
         }
      }
      for (int i = 0; i < n; i++) if (!fixe[i]) larg[i] = reste / libres;
      for (int i = 0; i < n; i++) {
         String complet = tr("onglet." + Armurier.ONGLETS.get(i)), nom = couperOnglet(complet, larg[i] - 10);
         final int index = i;
         // onglets en pastilles (petites capitales) : les dix tiennent en entier sur la ligne (avant : « MUNITI… »)
         HzButton bt = addRenderableWidget(new HzButton(x, py + 44, larg[i], 16, Component.literal(nom), i == onglet ? Ui.OR : Ui.ACCENT_NEUTRE, () -> {
            onglet = index;
            page = 0;
            if (b != null && !b.id.isEmpty()) b = null;
            aRefaire = true;
         }));
         bt.look(HzButton.Look.PILL);
         if (!nom.equals(complet)) coupe(bt, complet);
         x += larg[i] + 3;
      }
      // barre d'objets : place calculée pour le cas le plus large des boutons du bas (même place pour toutes les recettes)
      int boutonsBas = largeurBouton(tr("enregistrer")) + largeurBouton(tr("supprimer")) + largeurBouton(tr("revenir")) + 12;
      int placeTexte = px + pw - 14 - boutonsBas - 8 - 9 * CASE - 8 - (px + 14);
      barreTexte = placeTexte < 24 ? "" : Ui.ellipsis(font, tr("barre"), Ui.SMALL, placeTexte);
      barreX = px + 14 + (barreTexte.isEmpty() ? 0 : font.width(Ui.lit(barreTexte, Ui.SMALL)) + 8);
      String nouvelle = tr("nouvelle");
      addRenderableWidget(new HzButton(px + pw - 14 - largeurBouton(nouvelle), py + 12, largeurBouton(nouvelle), 16,
         Component.literal(nouvelle), Ui.OK, () -> {
            Brouillon nv = new Brouillon();
            nv.onglet = ongletCourant();
            nv.resultat = objetChoisi();
            b = nv;
            debutLignes = 0;
            aRefaire = true;
         }));
      // pages de la liste
      int bas = ly + lh - 18;
      page = Math.max(0, Math.min(page, pages() - 1));
      addRenderableWidget(new HzButton(lx + lw / 2 - 40, bas, 18, 16, Component.literal("←"), Ui.ACCENT_NEUTRE, () -> {
         page--;
         aRefaire = true;
      })).active = page > 0;
      addRenderableWidget(new HzButton(lx + lw / 2 + 22, bas, 18, 16, Component.literal("→"), Ui.ACCENT_NEUTRE, () -> {
         page++;
         aRefaire = true;
      })).active = page < pages() - 1;
      if (b != null) initRecette();
   }

   private void initRecette() {
      boolean supprimee = b.etat == PaquetEdition.SUPPRIMEE;
      int xd = rx + rw - 8;
      // ce que la recette donne
      int y = ly + 20;
      // choix de l'onglet : même largeur pour tous les onglets (le bouton ne bouge pas en les faisant défiler), au plus
      // ONGLET_MAX, nom coupé au-delà ; « Changer » à la largeur de son texte
      int wo = 0;
      for (String o : Armurier.ONGLETS) wo = Math.max(wo, largeurBouton(tr("onglet_choix", tr("onglet." + o))));
      wo = Math.min(wo, ONGLET_MAX);
      String choix = tr("onglet_choix", tr("onglet." + b.onglet)), choixCourt = couperBouton(choix, wo - 16);
      HzButton ong = addRenderableWidget(new HzButton(xd - wo, y + 5, wo, 16, Component.literal(choixCourt), Ui.ACCENT_NEUTRE, () -> {
         int i = (Armurier.ONGLETS.indexOf(b.onglet) + 1) % Armurier.ONGLETS.size();
         b.onglet = Armurier.ONGLETS.get(i);
         aRefaire = true;
      }));
      if (!choixCourt.equals(choix)) coupe(ong, choix);
      int wc = largeurBouton(tr("changer_barre"));
      HzButton chg = addRenderableWidget(new HzButton(xd - wo - 4 - wc, y + 5, wc, 16, Component.literal(tr("changer_barre")),
         Ui.ACCENT_NEUTRE, () -> {
            ItemStack s = objetChoisi();
            if (!s.isEmpty()) {
               b.resultat = s;
               aRefaire = true;
            }
         }));
      int xq = xd - wo - 4 - wc - 4 - 30;
      champ(xq, y + 5, 30, b.quantite, v -> b.quantite = v).active = !supprimee;
      nomMax = xq - 6 - (rx + 36);
      ong.active = !supprimee;
      chg.active = !supprimee;
      // ce qu'elle demande
      int y0 = ly + 70, visibles = lignesVisibles();
      debutLignes = Math.max(0, Math.min(debutLignes, Math.max(0, b.lignes.size() - visibles)));
      for (int i = debutLignes; i < Math.min(b.lignes.size(), debutLignes + visibles); i++) {
         Ligne l = b.lignes.get(i);
         int yl = y0 + (i - debutLignes) * LIGNE;
         final int index = i;
         HzButton x = addRenderableWidget(new HzButton(xd - 18, yl + 1, 18, 15, Component.literal("×"), Ui.DANGER, () -> {
            b.lignes.remove(index);
            aRefaire = true;
         }));
         int cw = largeurChanger();
         HzButton c = addRenderableWidget(new HzButton(xd - 18 - 4 - cw, yl + 1, cw, 15, Component.literal(tr("changer")), Ui.ACCENT_NEUTRE,
            () -> {
               ItemStack s = objetChoisi();
               if (!s.isEmpty()) {
                  l.objet = s;
                  l.icone = s;
                  l.ingredient = "";
                  aRefaire = true;
               }
            }));
         champ(xd - 18 - 4 - cw - 4 - 34, yl + 1, 34, l.quantite, v -> l.quantite = v).active = !supprimee;
         x.active = !supprimee;
         c.active = !supprimee;
      }
      int ya = y0 + Math.min(b.lignes.size(), visibles) * LIGNE + 2;
      HzButton aj = addRenderableWidget(new HzButton(rx + 8, ya, rw - 16, 15, Component.literal(tr("ajouter")), Ui.OK, () -> {
         ItemStack s = objetChoisi();
         if (!s.isEmpty() && b.lignes.size() < PaquetModif.MAX_LIGNES) {
            b.lignes.add(new Ligne(s, 1, "", s));
            debutLignes = Math.max(0, b.lignes.size() - lignesVisibles());
            aRefaire = true;
         }
      }).look(HzButton.Look.LINK));
      aj.active = !supprimee && b.lignes.size() < PaquetModif.MAX_LIGNES;
      // boutons du bas
      int bas = py + ph - 28, xb = px + pw - 14;
      if (supprimee) {
         String t = tr("remettre");
         addRenderableWidget(new HzButton(xb - largeurBouton(t), bas, largeurBouton(t), 18, Component.literal(t), Ui.OK,
            () -> envoyer(PaquetModif.simple(PaquetModif.REVENIR, b.id))).look(HzButton.Look.PRIMARY));
         return;
      }
      String t = tr("enregistrer");
      int w = largeurBouton(t);
      xb -= w;
      addRenderableWidget(new HzButton(xb, bas, w, 18, Component.literal(t), Ui.OK, this::enregistrer).look(HzButton.Look.PRIMARY));
      if (!b.id.isEmpty()) {
         t = tr("supprimer");
         w = largeurBouton(t);
         xb -= w + 6;
         addRenderableWidget(new HzButton(xb, bas, w, 18, Component.literal(t), Ui.DANGER,
            () -> envoyer(PaquetModif.simple(PaquetModif.SUPPRIMER, b.id))).danger());
      }
      if (b.etat == PaquetEdition.MODIFIEE) {
         t = tr("revenir");
         w = largeurBouton(t);
         xb -= w + 6;
         addRenderableWidget(new HzButton(xb, bas, w, 18, Component.literal(t), Ui.MUTED,
            () -> envoyer(PaquetModif.simple(PaquetModif.REVENIR, b.id))));
      }
   }

   // ------------------------------------------------------------------ mesures des boutons
   /** Largeur maximale du bouton de choix de l'onglet (le nom de l'objet donné garde sa place). */
   private static final int ONGLET_MAX = 120;

   /** Largeur du texte d'un bouton tel que HzButton le dessine (capitales grasses espacées). */
   /** Largeur d'un onglet en pastille (petites capitales, espacement 0,6, comme HzButton.Look.PILL). */
   private float largeurOnglet(String t) {
      return Ui.spacedWidth(font, t.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f);
   }

   private String couperOnglet(String t, int max) {
      if (largeurOnglet(t) <= max) return t;
      String s = t;
      while (!s.isEmpty() && largeurOnglet(s.trim() + "…") > max) s = s.substring(0, s.offsetByCodePoints(s.length(), -1));
      return s.trim() + "…";
   }

   private float largeurCaps(String t) {
      return Ui.spacedWidth(font, t.toUpperCase(Locale.ROOT), Ui.BOLD, 1.2f);
   }

   /** Largeur d'un bouton : son texte et 8 points de marge de chaque côté. */
   private int largeurBouton(String t) {
      return (int) Math.ceil(largeurCaps(t)) + 16;
   }

   /** Largeur du bouton « Changer » d'une ligne demandée. */
   private int largeurChanger() {
      return largeurBouton(tr("changer"));
   }

   /** Texte d'un bouton coupé avec « … » pour tenir dans {@code max} points. */
   private String couperBouton(String t, int max) {
      if (largeurCaps(t) <= max) return t;
      String s = t;
      while (!s.isEmpty() && largeurCaps(s.trim() + "…") > max) s = s.substring(0, s.offsetByCodePoints(s.length(), -1));
      return s.trim() + "…";
   }

   private void coupe(HzButton bt, String complet) {
      coupes.add(bt);
      textesCoupes.add(complet);
   }

   private int lignesVisibles() {
      return Math.max(1, (ly + lh - (ly + 70) - 20) / LIGNE);
   }

   private void enregistrer() {
      if (b == null || b.resultat.isEmpty() || b.lignes.isEmpty()) return;
      List<PaquetModif.Ligne> l = new ArrayList<>();
      for (Ligne x : b.lignes) l.add(new PaquetModif.Ligne(x.objet.isEmpty() ? x.ingredient : "", x.objet, x.quantite));
      envoyer(new PaquetModif(PaquetModif.ENREGISTRER, b.id, b.onglet, b.resultat, b.quantite, l));
   }

   @Override
   public void tick() {
      super.tick();
      for (var w : children()) if (w instanceof HzField e) e.tick();
      if (aRefaire) {
         aRefaire = false;
         rebuildWidgets();
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void removed() {
      Echelle.restaurer();
      super.removed();
   }

   // ------------------------------------------------------------------ souris
   @Override
   public boolean mouseClicked(double mx, double my, int bouton) {
      // liste des recettes
      if (mx >= lx && mx < lx + lw && my >= ly + 6 && my < ly + 6 + lignesListe() * LIGNE_LISTE) {
         List<PaquetEdition.Recette> l = recettesOnglet();
         int i = page * lignesListe() + (int) ((my - ly - 6) / LIGNE_LISTE);
         if (i >= 0 && i < l.size()) {
            b = brouillon(l.get(i));
            debutLignes = 0;
            SonsUi.clic(0.5F);
            aRefaire = true;
            return true;
         }
      }
      // barre d'objets
      int bx = barreX(), by = py + ph - 28;
      if (my >= by && my < by + CASE && mx >= bx && mx < bx + 9 * CASE) {
         int k = (int) ((mx - bx) / CASE);
         if (!caseBarre(k).isEmpty()) {
            caseChoisie = k;
            SonsUi.clic(0.5F);
         }
         return true;
      }
      return super.mouseClicked(mx, my, bouton);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double d) {
      if (b != null && mx >= rx && mx < rx + rw && my >= ly + 64 && my < ly + lh) {
         int avant = debutLignes;
         debutLignes = Math.max(0, Math.min(debutLignes - (int) Math.signum(d), Math.max(0, b.lignes.size() - lignesVisibles())));
         if (avant != debutLignes) aRefaire = true;
         return true;
      }
      return super.mouseScrolled(mx, my, d);
   }

   private int barreX() {
      return barreX;
   }

   // ------------------------------------------------------------------ dessin
   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      Font f = font;
      Ui.fond(g, width, height);
      Ui.glass(g, px, py, pw, ph, 215);
      // titre à la vraie taille de sa police (gras 14), plus de texte agrandi
      Ui.gauche(g, f, Ui.lit(tr("titre").toUpperCase(), Ui.NOM), Ui.NOM, px + 16, py + 18, Ui.argb(Ui.TEXT, 255));
      g.drawString(f, Ui.lit(Ui.ellipsis(f, tr("sous_titre"), Ui.SMALL, pw - 32), Ui.SMALL), px + 16, py + 30, Ui.argb(Ui.MUTED, 255), false);
      // liste
      Ui.glass(g, lx, ly, lw, lh, 120);
      List<PaquetEdition.Recette> l = recettesOnglet();
      List<Component> bulle = null;
      if (l.isEmpty()) g.drawString(f, Ui.lit(tr("vide"), Ui.SMALL), lx + 10, ly + 12, Ui.argb(Ui.FAINT, 255), false);
      int n0 = page * lignesListe();
      for (int i = n0; i < Math.min(l.size(), n0 + lignesListe()); i++) {
         PaquetEdition.Recette r = l.get(i);
         int y = ly + 6 + (i - n0) * LIGNE_LISTE;
         boolean sel = b != null && b.id.equals(r.id());
         boolean survol = mx >= lx && mx < lx + lw && my >= y && my < y + LIGNE_LISTE;
         if (sel || survol) Ui.soft(g, lx + 4, y, lw - 8, LIGNE_LISTE - 1, Ui.argb(sel ? 0x6FA8FF : 0xFFFFFF, sel ? 40 : 14));
         boolean sup = r.etat() == PaquetEdition.SUPPRIMEE;
         g.renderItem(r.resultat(), lx + 7, y);
         String etat = r.etat() > 0 ? tr("etat." + r.etat()) : "";
         int we = etat.isEmpty() ? 0 : (int) Ui.spacedWidth(f, etat, Ui.SMALL, 0.2f) + 6;
         String nom = Ui.ellipsis(f, r.resultat().getHoverName().getString(), Ui.FONT, lw - 34 - we - 8);
         g.drawString(f, Ui.lit(nom, Ui.FONT), lx + 28, y + 5, Ui.argb(sup ? Ui.FAINT : Ui.TEXT, 255), false);
         if (!etat.isEmpty()) Ui.spacedRight(g, f, etat, Ui.SMALL, lx + lw - 10, y + 6, Ui.argb(ETATS[r.etat()], 255), 0.2f);
      }
      Ui.centered(g, f, Ui.lit((page + 1) + " / " + pages(), Ui.SMALL), lx + lw / 2, ly + lh - 14, Ui.argb(Ui.MUTED, 255));
      // recette
      Ui.glass(g, rx, ly, rw, lh, 120);
      if (b == null) {
         g.drawString(f, Ui.lit(tr("choisir"), Ui.SMALL), rx + 10, ly + 12, Ui.argb(Ui.FAINT, 255), false);
      } else {
         Ui.spaced(g, f, tr("donne").toUpperCase(), Ui.SMALL, rx + 10, ly + 8, Ui.argb(Ui.MUTED, 255), 0.6f);
         int y = ly + 20;
         Ui.soft(g, rx + 6, y, rw - 12, 26, Ui.argb(0xFFFFFF, 10));
         Ui.soft(g, rx + 10, y + 3, 20, 20, Ui.argb(0x6FA8FF, 50));
         if (!b.resultat.isEmpty()) {
            g.renderItem(b.resultat, rx + 12, y + 5);
            if (mx >= rx + 10 && mx < rx + 30 && my >= y + 3 && my < y + 23) bulle = getTooltipFromItem(Minecraft.getInstance(), b.resultat);
         }
         String nom = b.resultat.isEmpty() ? tr("sans_objet") : b.resultat.getHoverName().getString();
         if (b.id.isEmpty()) nom = tr("nouvelle_titre") + " · " + nom;
         g.drawString(f, Ui.lit(Ui.ellipsis(f, nom, Ui.BOLD, nomMax), Ui.BOLD), rx + 36, y + 9, Ui.argb(Ui.TEXT, 255), false);
         Ui.spaced(g, f, tr("demande").toUpperCase(), Ui.SMALL, rx + 10, ly + 58, Ui.argb(Ui.MUTED, 255), 0.6f);
         // nom d'une ligne : jusqu'au champ de quantité (même calcul que initRecette)
         int y0 = ly + 70, vis = lignesVisibles(), nomLigne = rx + rw - 8 - 18 - 4 - largeurChanger() - 4 - 34 - 6 - (rx + 30);
         for (int i = debutLignes; i < Math.min(b.lignes.size(), debutLignes + vis); i++) {
            Ligne li = b.lignes.get(i);
            int yl = y0 + (i - debutLignes) * LIGNE;
            Ui.soft(g, rx + 6, yl, rw - 12, LIGNE - 1, Ui.argb(0xFFFFFF, 8));
            g.renderItem(li.icone, rx + 10, yl);
            String n = Ui.ellipsis(f, li.icone.getHoverName().getString(), Ui.FONT, nomLigne);
            g.drawString(f, Ui.lit(n, Ui.FONT), rx + 30, yl + 5, Ui.argb(Ui.TEXT, 255), false);
            if (mx >= rx + 10 && mx < rx + 26 && my >= yl && my < yl + 16) bulle = getTooltipFromItem(Minecraft.getInstance(), li.icone);
         }
         if (b.lignes.size() > vis) {
            Ui.right(g, f, Ui.lit((debutLignes + 1) + "–" + Math.min(b.lignes.size(), debutLignes + vis) + " / " + b.lignes.size(), Ui.SMALL),
               rx + rw - 10, ly + 58, Ui.argb(Ui.FAINT, 255));
         }
      }
      // barre d'objets
      int by = py + ph - 28, bx = barreX(), choisie = caseActive();
      if (!barreTexte.isEmpty()) g.drawString(f, Ui.lit(barreTexte, Ui.SMALL), px + 14, by + 5, Ui.argb(Ui.MUTED, 255), false);
      for (int k = 0; k < 9; k++) {
         int x = bx + k * CASE;
         ItemStack s = caseBarre(k);
         g.fill(x, by, x + CASE - 1, by + CASE - 1, Ui.argb(k == choisie ? Ui.OK : 0xFFFFFF, k == choisie ? 60 : 14));
         if (k == choisie) Ui.softOutline(g, x, by, CASE - 1, CASE - 1, Ui.argb(Ui.OK, 220));
         if (!s.isEmpty()) {
            g.renderItem(s, x + 1, by + 1);
            g.renderItemDecorations(f, s, x + 1, by + 1);
            if (mx >= x && mx < x + CASE && my >= by && my < by + CASE) bulle = getTooltipFromItem(Minecraft.getInstance(), s);
         }
      }
      super.render(g, mx, my, pt);
      // bouton au texte coupé : texte complet au survol
      for (int i = 0; i < coupes.size() && bulle == null; i++) {
         if (coupes.get(i).isHovered()) bulle = List.of(Component.literal(textesCoupes.get(i)));
      }
      if (bulle != null) Ui.tooltip(g, f, new ArrayList<>(bulle), mx, my, Ui.ACCENT_NEUTRE);
   }
}
