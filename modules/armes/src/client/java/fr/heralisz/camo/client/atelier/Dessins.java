package fr.heralisz.camo.client.atelier;

import fr.heralisz.camo.client.kit.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/** Petits dessins de l'atelier (pictogrammes en pixels, titres, paragraphes), dans la charte des écrans de clan. */
final class Dessins {
   private Dessins() {
   }

   /** Polices du kit à leur vraie taille, de la plus petite à la plus grande : moyennes 8, 9, 11 ; grasses 11, 14, 16. */
   private static final ResourceLocation[] MOYENNES = {Ui.MINI, Ui.SMALL, Ui.FONT}, GRASSES = {Ui.BOLD, Ui.NOM, Ui.GROS};
   private static final float[] TAILLES_MOYENNES = {8f, 9f, 11f}, TAILLES_GRASSES = {11f, 14f, 16f};

   /**
    * Titre en capitales espacées, {@code echelle} fois plus grand que la police {@code f} : tracé dans la plus grande police
    * du kit, de même graisse, qui ne dépasse pas cette taille (au demi-point près), à sa vraie taille (jamais agrandi),
    * chaque lettre posée sur un point de l'écran ; l'espace entre les lettres et le milieu des capitales restent ceux du
    * titre agrandi d'avant. Gras 11 x 1,45 (nom de l'arme) : gras 16 ; gras 11 x 1,15 (titres des panneaux) : gras 11 (le
    * kit n'a pas de 13 ; le 14 ferait toucher « Dans ton inventaire » sur la même ligne).
    */
   static void titre(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, float echelle, int argb, float espace) {
      Ui.spacedMilieu(g, font, s, police(f, echelle), x, y + echelle * Ui.milieu(f), argb, espace * echelle);
   }

   /** Police du kit pour un texte de {@code f} agrandi {@code echelle} fois (voir {@link #titre}). */
   private static ResourceLocation police(ResourceLocation f, float echelle) {
      boolean gras = f.equals(Ui.BOLD) || f.equals(Ui.NOM) || f.equals(Ui.GROS);
      ResourceLocation[] polices = gras ? GRASSES : MOYENNES;
      float[] tailles = gras ? TAILLES_GRASSES : TAILLES_MOYENNES;
      float voulue = Ui.capitales(f) / 0.53763f * echelle;
      ResourceLocation choix = polices[0];
      for (int i = 0; i < polices.length; i++) {
         if (tailles[i] <= voulue + 0.5f) choix = polices[i];
      }
      return choix;
   }

   /** Le texte (en capitales espacées) coupé avec « … » pour tenir dans {@code max} pixels. */
   static String couperEspace(Font font, String s, ResourceLocation f, float espace, float max) {
      if (Ui.spacedWidth(font, s.toUpperCase(Locale.ROOT), f, espace) <= max) return s;
      String t = s;
      while (t.length() > 1 && Ui.spacedWidth(font, (t + "…").toUpperCase(Locale.ROOT), f, espace) > max) {
         t = t.substring(0, t.length() - 1);
      }
      return t.trim() + "…";
   }

   /** Largeur d'une pastille {@link Ui#chip}. */
   static int largeurChip(Font font, String texte) {
      return (int) Ui.spacedWidth(font, texte.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) + 10;
   }

   /** Paragraphe coupé à la largeur ; rend la hauteur dessinée. */
   static int paragraphe(GuiGraphics g, Font font, Component c, int x, int y, int largeur, int argb, int interligne) {
      int yy = y;
      for (FormattedCharSequence l : font.split(c, largeur)) {
         g.drawString(font, l, x, yy, argb, false);
         yy += interligne;
      }
      return yy - y;
   }

   /** Hauteur qu'aurait le paragraphe. */
   static int hauteur(Font font, Component c, int largeur, int interligne) {
      List<FormattedCharSequence> l = font.split(c, largeur);
      return l.size() * interligne;
   }

   /** Case d'icône : fond léger et filet. */
   static void caseIcone(GuiGraphics g, int x, int y, int taille, boolean vive) {
      Ui.soft(g, x, y, taille, taille, Ui.argb(0xFFFFFF, vive ? 14 : 8));
      Ui.softOutline(g, x + 1, y + 1, taille - 2, taille - 2, Ui.argb(0xFFFFFF, vive ? 40 : 22));
   }

   /** Cadenas, 8 x 10. */
   static void cadenas(GuiGraphics g, int x, int y, int argb) {
      g.fill(x + 2, y, x + 6, y + 1, argb);
      g.fill(x + 1, y + 1, x + 2, y + 4, argb);
      g.fill(x + 6, y + 1, x + 7, y + 4, argb);
      g.fill(x, y + 4, x + 8, y + 10, argb);
      g.fill(x + 3, y + 6, x + 5, y + 8, 0xFF101217);
   }

   /** Horloge (délai), 9 x 9. */
   static void horloge(GuiGraphics g, int x, int y, int argb) {
      g.fill(x + 3, y, x + 6, y + 1, argb);
      g.fill(x + 1, y + 1, x + 3, y + 2, argb);
      g.fill(x + 6, y + 1, x + 8, y + 2, argb);
      g.fill(x, y + 2, x + 1, y + 7, argb);
      g.fill(x + 8, y + 2, x + 9, y + 7, argb);
      g.fill(x + 1, y + 7, x + 3, y + 8, argb);
      g.fill(x + 6, y + 7, x + 8, y + 8, argb);
      g.fill(x + 3, y + 8, x + 6, y + 9, argb);
      g.fill(x + 4, y + 2, x + 5, y + 5, argb);
      g.fill(x + 5, y + 4, x + 7, y + 5, argb);
   }

   /** Flèche de changement (mode de tir), 7 x 7. */
   static void cycle(GuiGraphics g, int x, int y, int argb) {
      g.fill(x + 1, y, x + 5, y + 1, argb);
      g.fill(x, y + 1, x + 1, y + 5, argb);
      g.fill(x + 1, y + 5, x + 5, y + 6, argb);
      g.fill(x + 5, y + 4, x + 6, y + 5, argb);
      g.fill(x + 4, y - 1, x + 5, y + 2, argb);
      g.fill(x + 5, y, x + 6, y + 1, argb);
   }

   /**
    * Symbole « à vie » (∞, glyphe DM Sans) en gras 14 à sa vraie taille (avant : gras 11 agrandi 1,3 fois), posé sur un
    * point de l'écran ; son milieu reste à sa place d'avant (0,09 plus à droite, 0,14 plus haut, d'après le glyphe).
    */
   static void infini(GuiGraphics g, Font font, int x, int y, int argb) {
      g.pose().pushPose();
      Ui.placer(g, x + 0.09f, y - 0.14f);
      g.drawString(font, Ui.lit("∞", Ui.NOM), 0, 0, argb, false);
      g.pose().popPose();
   }

   /** Touche de clavier (« Échap ») : rend sa largeur. */
   static int touche(GuiGraphics g, Font font, String texte, int x, int y, boolean survol) {
      int w = font.width(Ui.lit(texte, Ui.SMALL)) + 8;
      Ui.soft(g, x, y, w, 11, Ui.argb(0xFFFFFF, survol ? 30 : 18));
      Ui.softOutline(g, x + 1, y + 1, w - 2, 9, Ui.argb(0xFFFFFF, survol ? 90 : 50));
      // centré dans la touche (largeur exacte, milieu des capitales ; Tactique 2.35.1)
      Ui.centre(g, font, Ui.lit(texte, Ui.SMALL), Ui.SMALL, x + w / 2f, y + 5.5f, Ui.argb(Ui.TEXT, 255));
      return w;
   }

   /** Ombre portée d'un bord (écran) : bande verticale qui s'efface vers l'intérieur. */
   static void bandeVerticale(GuiGraphics g, int x0, int x1, int h, int alpha, boolean sombreAGauche) {
      int w = x1 - x0;
      if (w <= 0) return;
      int n = Math.max(1, w / 3);
      for (int i = 0; i < n; i++) {
         float t = (i + 0.5f) / n;
         float a = sombreAGauche ? 1 - t : t;
         a = a * a * (3 - 2 * a);
         g.fill(x0 + i * w / n, 0, x0 + (i + 1) * w / n, h, Ui.argb(0, (int) (alpha * a)));
      }
   }
}
