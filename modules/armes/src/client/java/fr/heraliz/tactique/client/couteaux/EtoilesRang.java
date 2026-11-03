package fr.heraliz.tactique.client.couteaux;

import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Rang des skins de couteaux dans l'infobulle (03/11, proposition A validée par le propriétaire) : étoiles nettes en or,
 * vides en contour, au lieu des « ★ » en points de la police de Minecraft.
 * <p>
 * Le texte des skins ne change pas (« §6★★★§8★ » puis « Exclusivité HeraliZ », écrit par skins_couteaux.py) : il reste le
 * secours. À la construction de l'infobulle, sa première ligne de description, si elle n'est faite que d'étoiles, est lue
 * ({@link #lire} : étoiles dorées = allumées) puis vidée ; les étoiles sont dessinées à sa place ({@link #dessiner}) dans
 * le dessin de l'infobulle (couteaux TaCZ : ClientGunTooltipMixin ; couteaux de LR : AbstractClientItemTooltip).
 * Texture : assets/heraliz_tactique/textures/gui/etoiles_rang.png, écrite par modules/armes/outils/etoiles_couteaux.py,
 * lue lissée (.mcmeta).
 */
public final class EtoilesRang {
   private static final ResourceLocation TEXTURE = new ResourceLocation("heraliz_tactique", "textures/gui/etoiles_rang.png");
   /** Côté d'une étoile (points d'interface), écart entre deux, côté d'une case de la texture (points de texture). */
   private static final int TAILLE = 9, PAS = 10, CASE = 36;
   /** Couleur des étoiles allumées dans le texte (§6). */
   private static final int OR = 0xFFAA00;
   private static final char ETOILE = '★';

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

   /** Largeur des étoiles (points d'interface). */
   public static int largeur(int total) {
      return total * PAS - (PAS - TAILLE);
   }

   /** Étoiles à la place de la ligne de texte qui commence en (x, y) (texte de 8 points de haut sur une ligne de 10). */
   public static void dessiner(GuiGraphics g, int x, int y, int allumees, int total) {
      for (int i = 0; i < total; i++) {
         g.blit(TEXTURE, x + i * PAS, y - 1, TAILLE, TAILLE, i < allumees ? 0.0F : (float) CASE, 0.0F, CASE, CASE, 2 * CASE, CASE);
      }
   }
}
