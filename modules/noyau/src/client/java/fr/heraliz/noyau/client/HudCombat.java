package fr.heraliz.noyau.client;

import fr.heraliz.noyau.api.EtatClient;
import fr.heraliz.noyau.client.kit.TexteEspace;
import fr.heraliz.noyau.client.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Indicateur discret du tag de combat (hors match), coin bas droit au-dessus du compteur de munitions de TaCZ : point rouge
 * qui pulse, « EN COMBAT » en petites capitales espacées, secondes en gras, jauge fine qui se vide ; fondu à l'apparition et
 * dans la dernière demi-seconde. Aucun message dans le chat. Textes refaits seulement quand la seconde change.
 */
final class HudCombat {
   private HudCombat() {}

   private static int montre = -1;
   private static MutableComponent valeur = Component.empty();
   /** Largeur des secondes affichées et largeur de référence « 00 s » (la pastille ne change pas de taille). */
   private static int largeurValeur, largeurRef;
   private static final TexteEspace TITRE = new TexteEspace(Ui.SMALL, 1.0f);
   private static long debut;

   static void dessiner(GuiGraphics g, int w, int h) {
      long reste = EtatClient.combatRestantMs();
      Minecraft mc = Minecraft.getInstance();
      if (reste <= 0 || EtatClient.enMatch() || mc.options.hideGui) {
         montre = -1;
         return;
      }
      Font font = mc.font;
      long now = Util.getMillis();
      int s = (int) ((reste + 999) / 1000);
      if (s != montre) {
         if (montre < 0) {
            debut = now;
            TITRE.texte(Component.translatable("noyau.hud.combat_titre").getString());
            largeurRef = font.width(Ui.lit(Component.translatable("noyau.hud.secondes", "00").getString(), Ui.BOLD));
         }
         montre = s;
         // unite dans la langue du joueur (« 12 s », « 12 с »)
         valeur = Ui.lit(Component.translatable("noyau.hud.secondes", s).getString(), Ui.BOLD);
         largeurValeur = font.width(valeur);
      }
      int cw = 17 + (int) Math.ceil(TITRE.largeur()) + 10 + Math.max(largeurRef, largeurValeur) + 8, ch = 19;
      int x = w - 8 - cw, y = h - 50 - ch;
      float a = Math.min(Math.min(1f, (now - debut) / 180f), Math.min(1f, reste / 450f));
      int a255 = Math.max(4, (int) (255 * a));
      Ui.glass(g, x, y, cw, ch, (int) (180 * a));
      float pulse = 0.6f + 0.4f * (float) Math.cos((now % 1000) / 1000.0 * Math.PI * 2);
      Ui.soft(g, x + 7, y + 6, 5, 5, Ui.argb(Ui.DANGER, (int) (a255 * pulse)));
      TITRE.dessiner(g, x + 17, y + 5.5f, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.5f), a255));
      g.drawString(font, valeur, x + cw - 8 - largeurValeur, y + 5, Ui.argb(Ui.TEXT, a255), false);
      if (a > 0.5f) Ui.bar(g, x + 6, y + ch - 4, cw - 12, 1, EtatClient.combatRatio(), Ui.DANGER);
   }
}
