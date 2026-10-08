package fr.heraliz.tactique.client.onglet;

import com.mojang.logging.LogUtils;
import fr.heraliz.tactique.client.kit.SonsUi;
import fr.heraliz.tactique.client.kit.Ui;
import fr.heraliz.tactique.onglet.OngletGroupe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Panneau vertical à gauche de l'écran créatif, visible quand NOTRE onglet est ouvert : une entrée « Tous » puis une entrée
 * par groupe (icône + nom). Un clic n'affiche que les objets de ce groupe. 100 % côté client, style des écrans de clan
 * (verre, DM Sans, capitales espacées, accent du clan), aucun fond uni, aucun bouton vanilla.
 * <p>
 * Composant réutilisable (gabarit) : il ne connaît que l'onglet et son {@link OngletGroupe}.
 * <pre>
 *    PanneauOnglet.installer(HeralizTactique.TAB, HeralizTactique.ONGLET, "onglet.heraliz_tactique.titre", "onglet.heraliz_tactique.tous");
 * </pre>
 * Le filtre agit sur la liste d'objets du menu créatif (menu.items) juste avant chaque image ; Minecraft la remplit à
 * nouveau quand on change d'onglet ou quand l'onglet se reconstruit, et le panneau refiltre aussitôt. Le dernier groupe
 * choisi est gardé pendant la partie.
 */
public final class PanneauOnglet {
   private static final Logger LOG = LogUtils.getLogger();
   /** CreativeModeInventoryScreen.selectedTab (statique) et scrollOffs : noms SRG (jeu réel), retrouvés en dev par Forge. */
   private static final Field ONGLET_CHOISI = champ("f_98507_");
   private static final Field DEFILEMENT = champ("f_98508_");

   /** Largeur normale du panneau, largeur réduite (icônes seules) si l'écran est trop étroit ; hauteur d'une entrée. */
   private static final int LARGEUR = 112, LARGEUR_ICONES = 28, ENTREE = 18, ENTETE = 20, ECART = 4;

   private final Supplier<CreativeModeTab> onglet;
   private final OngletGroupe groupes;
   private final String cleTitre, cleTous;
   /** Groupe affiché, null = tous. */
   private String filtre;
   /** Cache de la liste filtrée (évite de la recalculer à chaque image). */
   private List<ItemStack> cache;
   private String cacheFiltre;
   private Collection<ItemStack> cacheSource;
   private int cacheTaille = -1;

   private PanneauOnglet(Supplier<CreativeModeTab> onglet, OngletGroupe groupes, String cleTitre, String cleTous) {
      this.onglet = onglet;
      this.groupes = groupes;
      this.cleTitre = cleTitre;
      this.cleTous = cleTous;
   }

   /** Installe le panneau (partie client, une fois). */
   public static PanneauOnglet installer(Supplier<CreativeModeTab> onglet, OngletGroupe groupes, String cleTitre, String cleTous) {
      PanneauOnglet p = new PanneauOnglet(onglet, groupes, cleTitre, cleTous);
      if (ONGLET_CHOISI != null && DEFILEMENT != null) MinecraftForge.EVENT_BUS.register(p);
      return p;
   }

   private static Field champ(String srg) {
      try {
         return ObfuscationReflectionHelper.findField(CreativeModeInventoryScreen.class, srg);
      } catch (RuntimeException ex) {
         LOG.warn("[heraliz] écran créatif : champ {} introuvable, panneau de l'onglet désactivé ({})", srg, ex.toString());
         return null;
      }
   }

   // ------------------------------------------------------------------ état de l'écran créatif
   private boolean visible(CreativeModeInventoryScreen s) {
      try {
         return ONGLET_CHOISI.get(null) == onglet.get();
      } catch (IllegalAccessException | RuntimeException ex) {
         return false;
      }
   }

   private List<ItemStack> voulus(CreativeModeTab tab) {
      Collection<ItemStack> tous = tab.getDisplayItems();
      if (cache != null && Objects.equals(cacheFiltre, filtre) && cacheSource == tous && cacheTaille == tous.size()) return cache;
      List<ItemStack> l = new ArrayList<>(tous.size());
      for (ItemStack st : tous) if (filtre == null || filtre.equals(groupes.groupeDe(st))) l.add(st);
      cache = l;
      cacheFiltre = filtre;
      cacheSource = tous;
      cacheTaille = tous.size();
      return l;
   }

   private static boolean memes(List<ItemStack> a, List<ItemStack> b) {
      if (a.size() != b.size()) return false;
      for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
      return true;
   }

   /** Met la liste du menu en accord avec le filtre (ne touche à rien si c'est déjà le cas) ; liste changée = remontée en haut. */
   private void appliquer(CreativeModeInventoryScreen s) {
      List<ItemStack> v = voulus(onglet.get());
      NonNullList<ItemStack> items = s.getMenu().items;
      if (memes(items, v)) return;
      items.clear();
      items.addAll(v);
      s.getMenu().scrollTo(0);
      try {
         DEFILEMENT.setFloat(s, 0F);
      } catch (IllegalAccessException | RuntimeException ignore) {
         // le menu est déjà remonté ; seule la poignée de défilement resterait décalée
      }
   }

   // ------------------------------------------------------------------ géométrie
   private int largeur(CreativeModeInventoryScreen s) {
      return s.getGuiLeft() - ECART - 4 >= LARGEUR ? LARGEUR : LARGEUR_ICONES;
   }

   private int nbEntrees() {
      return 1 + groupes.groupes().size();
   }

   /** -2 : hors du panneau ; -1 : dans le panneau mais pas sur une entrée ; sinon indice (0 = Tous). */
   private int entreeSous(CreativeModeInventoryScreen s, double mx, double my) {
      int w = largeur(s), x = s.getGuiLeft() - ECART - w, y = s.getGuiTop(), h = ENTETE + nbEntrees() * ENTREE + 6;
      if (mx < x || mx >= x + w || my < y || my >= y + h) return -2;
      int i = (int) Math.floor((my - y - ENTETE) / ENTREE);
      return my < y + ENTETE || i < 0 || i >= nbEntrees() ? -1 : i;
   }

   // ------------------------------------------------------------------ événements
   @SubscribeEvent
   public void avantImage(ScreenEvent.Render.Pre e) {
      if (e.getScreen() instanceof CreativeModeInventoryScreen s && visible(s)) appliquer(s);
   }

   @SubscribeEvent
   public void clic(ScreenEvent.MouseButtonPressed.Pre e) {
      if (!(e.getScreen() instanceof CreativeModeInventoryScreen s) || !visible(s)) return;
      int i = entreeSous(s, e.getMouseX(), e.getMouseY());
      if (i == -2) return;
      e.setCanceled(true);                       // un clic sur le panneau ne jette jamais l'objet tenu
      if (i < 0 || e.getButton() != 0) return;
      String nouveau = i == 0 ? null : groupes.groupes().get(i - 1).id();
      if (Objects.equals(nouveau, filtre)) return;
      filtre = nouveau;
      SonsUi.clic(0.6F);
      appliquer(s);
   }

   /** Le relâchement d'un clic sur le panneau ne doit rien faire non plus dans l'écran créatif (jet d'objet, glisser). */
   @SubscribeEvent
   public void relache(ScreenEvent.MouseButtonReleased.Pre e) {
      if (e.getScreen() instanceof CreativeModeInventoryScreen s && visible(s) && entreeSous(s, e.getMouseX(), e.getMouseY()) != -2) e.setCanceled(true);
   }

   @SubscribeEvent
   public void apresImage(ScreenEvent.Render.Post e) {
      if (!(e.getScreen() instanceof CreativeModeInventoryScreen s) || !visible(s)) return;
      dessiner(e.getGuiGraphics(), s, e.getMouseX(), e.getMouseY());
   }

   // ------------------------------------------------------------------ dessin
   private void dessiner(GuiGraphics g, CreativeModeInventoryScreen s, int mx, int my) {
      Font font = Minecraft.getInstance().font;
      List<OngletGroupe.Groupe> gs = groupes.groupes();
      int w = largeur(s), x = s.getGuiLeft() - ECART - w, y = s.getGuiTop(), n = 1 + gs.size(), h = ENTETE + n * ENTREE + 6;
      boolean compact = w == LARGEUR_ICONES;
      int accent = Ui.accent();
      Ui.glass(g, x, y, w, h);
      String titre = Component.translatable(cleTitre).getString();
      if (compact) {
         Ui.hairline(g, x + 6, x + w - 6, y + 10, accent, 160, true, true);
      } else {
         Ui.spaced(g, font, titre, Ui.SMALL, x + 9, y + 7, Ui.argb(Ui.MUTED, 255), 1.0F);
         int tw = (int) Ui.spacedWidth(font, titre.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 1.0F);
         Ui.hairline(g, x + 9 + tw + 6, x + w - 8, y + 11, 0xFFFFFF, 40, false, true);
      }
      int survol = entreeSous(s, mx, my);
      Component bulle = null;
      for (int i = 0; i < n; i++) {
         int ey = y + ENTETE + i * ENTREE;
         String id = i == 0 ? null : gs.get(i - 1).id();
         boolean choisi = Objects.equals(id, filtre), sur = survol == i;
         if (choisi) {
            Ui.soft(g, x + 3, ey, w - 6, ENTREE - 1, Ui.argb(0xFFFFFF, 18));
            g.fill(x + 3, ey + 3, x + 5, ey + ENTREE - 4, Ui.argb(accent, 255));
         } else if (sur) {
            Ui.soft(g, x + 3, ey, w - 6, ENTREE - 1, Ui.argb(0xFFFFFF, 9));
         }
         int ix = x + (compact ? 6 : 9), iy = ey + 1;
         if (i == 0) toutes(g, ix, iy, choisi ? accent : 0xC8CCD4);
         else g.renderItem(gs.get(i - 1).icone().get(), ix, iy);
         Component nom = i == 0 ? Component.translatable(cleTous) : gs.get(i - 1).nom();
         if (compact) {
            if (sur) bulle = nom;
            continue;
         }
         String texte = nom.getString();
         int max = w - (ix - x) - 22 - 6;
         String coupe = Ui.ellipsis(font, texte, Ui.FONT, max);
         if (sur && !coupe.equals(texte)) bulle = nom;
         g.drawString(font, Ui.lit(coupe, Ui.FONT), ix + 21, ey + 5, Ui.argb(choisi ? Ui.TEXT : sur ? 0xD8DBE2 : 0xA9AEB8, 255), false);
      }
      if (bulle != null) Ui.tooltip(g, font, List.of(Ui.in(bulle, Ui.BOLD)), mx, my, accent);
   }

   /** Icône « tous » : quatre carrés adoucis (pas d'objet vanilla). */
   private static void toutes(GuiGraphics g, int x, int y, int rgb) {
      int c = Ui.argb(rgb, 230);
      Ui.soft(g, x + 2, y + 2, 5, 5, c);
      Ui.soft(g, x + 9, y + 2, 5, 5, c);
      Ui.soft(g, x + 2, y + 9, 5, 5, c);
      Ui.soft(g, x + 9, y + 9, 5, 5, c);
   }
}
