package fr.heraliz.tactique.client.couteaux;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Rang des skins de couteaux dans l'infobulle. 04/11 (proposition D validée par le propriétaire, maquette
 * {@code maquettes/etoiles_couteaux/propositions.png} ; avant : étoiles dorées lissées du 03/11, jugées « très moches ») :
 * le mot « Rang » puis quatre petits losanges en pixels, comme les lettres de l'infobulle, allumés de la couleur du rang
 * (1 gris, 2 bleu-vert, 3 violet, 4 rouge : celle du nom du skin), les autres sombres ; une ombre d'un point comme le texte.
 * <p>
 * Le texte des skins ne change pas (« §6★★★§8★ » puis « Exclusivité HeraliZ », écrit par skins_couteaux.py) : il reste le
 * secours. À la construction de l'infobulle, sa première ligne de description, si elle n'est faite que d'étoiles, est lue
 * ({@link #lire} : étoiles dorées = allumées) puis vidée ; le rang est dessiné à sa place ({@link #dessiner}) dans le
 * dessin de l'infobulle (couteaux TaCZ : ClientGunTooltipMixin ; couteaux de LR : AbstractClientItemTooltip). Aucune
 * texture : des carrés d'un point d'interface, nets à toutes les échelles.
 */
public final class EtoilesRang {
   /** Couleur des étoiles allumées dans le texte (§6). */
   private static final int OR = 0xFFAA00;
   private static final char ETOILE = '★';
   /** Losange de 5 x 5 points : largeur de chaque rangée (centrée). */
   private static final int[] LOSANGE = {1, 3, 5, 3, 1};
   /** Côté d'un losange et écart d'un losange au suivant (points d'interface). */
   private static final int COTE = 5, PAS = 7;
   /** Couleur du rang (celle du nom du skin, COULEURS_ETOILES de skins_couteaux.py : §7, §3, §5, §c). */
   private static final int[] COULEURS = {0xAAAAAA, 0xAAAAAA, 0x00AAAA, 0xAA00AA, 0xFF5555};
   private static final int ETEINT = 0x585268, ETEINT_OMBRE = 0x141018, GRIS = 0xAAAAAA;
   private static final String CLE = "tooltip.heraliz_tactique.rang";

   private EtoilesRang() {
   }

   /**
    * Rang lu dans une ligne faite seulement d'étoiles (espaces permis) : {allumées, total} ; null si la ligne contient
    * autre chose ou aucune étoile. Une seule fois par infobulle construite, jamais à chaque image.
    */
   @Nullable
   public static int[] lire(@Nullable FormattedCharSequence ligne) {
      if (ligne == null) {
         return null;
      }
      int[] compte = {0, 0, 0}; // allumées, total, autre caractère
      ligne.accept((index, style, point) -> {
         if (point == ETOILE) {
            compte[1]++;
            TextColor c = style.getColor();
            if (c != null && c.getValue() == OR) {
               compte[0]++;
            }
         } else if (!Character.isWhitespace(point)) {
            compte[2]++;
            return false;
         }
         return true;
      });
      return compte[2] == 0 && compte[1] > 0 ? new int[] {compte[0], compte[1]} : null;
   }

   private static Component mot() {
      return Component.translatableWithFallback(CLE, "Rang");
   }

   /** Largeur du rang (points d'interface) : le mot, une espace, puis les losanges. */
   public static int largeur(int total) {
      Font font = Minecraft.getInstance().font;
      return font.width(mot()) + font.width(" ") + total * PAS - (PAS - COTE);
   }

   /** Rang à la place de la ligne de texte qui commence en (x, y) (texte de 8 points de haut sur une ligne de 10). */
   public static void dessiner(GuiGraphics g, int x, int y, int allumees, int total) {
      Font font = Minecraft.getInstance().font;
      Component mot = mot();
      g.drawString(font, mot, x, y, 0xFF000000 | GRIS, true);
      int lx = x + font.width(mot) + font.width(" ");
      int c = COULEURS[Math.max(0, Math.min(COULEURS.length - 1, allumees))];
      for (int i = 0; i < total; i++) {
         boolean allume = i < allumees;
         int px = lx + i * PAS, py = y + 1;
         int face = allume ? c : ETEINT;
         int ombre = allume ? ombre(c) : ETEINT_OMBRE;
         losange(g, px + 1, py + 1, 0xFF000000 | ombre);
         losange(g, px, py, 0xFF000000 | face);
      }
   }

   private static void losange(GuiGraphics g, int x, int y, int argb) {
      for (int j = 0; j < LOSANGE.length; j++) {
         int l = LOSANGE[j], d = (COTE - l) / 2;
         g.fill(x + d, y + j, x + d + l, y + j + 1, argb);
      }
   }

   /** Ombre comme celle du texte de Minecraft : la couleur au quart. */
   private static int ombre(int rgb) {
      return ((rgb >> 16 & 0xFF) / 4) << 16 | ((rgb >> 8 & 0xFF) / 4) << 8 | (rgb & 0xFF) / 4;
   }
}
