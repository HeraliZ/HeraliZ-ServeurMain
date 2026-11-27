package fr.heraliz.tactique.client.allonge;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.client.kit.Anim;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import fr.heraliz.tactique.allonge.Allonge;
import fr.heraliz.tactique.allonge.CompatReanimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.IEventBus;
import org.joml.Matrix4f;

/**
 * Icône « s'allonger » en bas à gauche (03/11, demande du propriétaire : voir sans essayer si on peut se rallonger).
 * v2 du 27/11 (retour du propriétaire : « plus propre, petit, une barre de recharge dessous comme celle des soins, on
 * voit au 1er coup d'oeil si on est en recharge » ; maquette validée {@code maquettes/icone_allonge/v2_barre_27nov.png}) :
 * plus de disque ni d'anneau ; une petite silhouette fine (15 x 5,5 unités) et, dessous, une barre penchée comme
 * {@link fr.heraliz.noyau.client.kit.BarreProgression} (même pente, même ombre, même piste), à {@link #GAUCHE} du bord
 * gauche et {@link #HAUT} au-dessus du bas.
 * <ul>
 *    <li><b>prêt</b> : silhouette claire, barre pleine claire ;</li>
 *    <li><b>délai</b> (on vient de se relever, ou le serveur a refusé) : silhouette grisée, barre ambre qui se remplit,
 *    secondes restantes à droite ;</li>
 *    <li><b>plongeon en recharge</b> (s'allonger sur place est permis, mais le délai entre deux plongeons, compté depuis le
 *    début du dernier, n'est pas écoulé) : silhouette claire, barre claire qui se remplit, secondes restantes à droite ;</li>
 *    <li><b>impossible</b> (à terre, en train de fouiller ou réanimer, en véhicule, dans l'eau) : silhouette grisée,
 *    barre rouge ;</li>
 *    <li><b>allongé</b>, ou en plein plongeon (de l'élan à la retombée) : silhouette et barre vertes.</li>
 * </ul>
 * L'état est celui que le jeu du joueur applique déjà à la touche (AllongeClient : même délai que le serveur, réglage
 * {@code delai_rallonger} et {@code delai_plongeon} envoyés par Forge, attentes annoncées par un refus du serveur, plongeon
 * accepté par le serveur) : aucun paquet de plus, quelques
 * lectures par image. Caché en F1, avec F3, en spectateur et derrière nos écrans plein écran (EcransPleins de
 * l'interface annule les surcouches). Formes puis texte, un vidage chacun ; aucune allocation par image.
 */
public final class HudAllonge {
   private HudAllonge() {
   }

   /**
    * Bord gauche de la silhouette, haut de la silhouette au-dessus du bas de l'écran ; barre : largeur, hauteur, écart
    * sous la silhouette ; écart entre la barre et les secondes. La voix (CaraCrea) s'affiche au-dessus (VoiceHud).
    */
   static final float GAUCHE = 8f, HAUT = 18f, L_BARRE = 16f, H_BARRE = 2f, ECART_BARRE = 2.2f, ECART_TEXTE = 3f;
   /** Opacités de l'ombre (silhouette et barre) et de la piste vide, comme BarreProgression. */
   static final float OMBRE_ALPHA = 0.35f, PISTE_ALPHA = 0.18f;
   private static final float PENTE = Formes.pente(fr.heraliz.noyau.client.kit.BarreProgression.INCLINAISON);

   private enum Etat { PRET, DELAI, RECHARGE, IMPOSSIBLE, ALLONGE }

   private static final Anim.Fondu FONDU = new Anim.Fondu();
   private static final TexteHud SECONDES = new TexteHud(PoliceHud.CHIFFRE_PETIT);
   private static long secondesAffichees = -1L;

   /** Branché par la partie client du mod porteur (ClientTactique). */
   public static void brancher(IEventBus busMod) {
      busMod.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("allonge", HudAllonge::rendre));
   }

   private static void rendre(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      float a = FONDU.maj(p != null && !p.isSpectator() && p.isAlive());
      if (!FONDU.visible() || a <= 0f || p == null || mc.options.hideGui || mc.options.renderDebug) return;

      long restant = AllongeClient.restantMs();
      Etat etat;
      if (AllongeClient.estAllonge() || AllongeClient.plongeonEnCours()) {
         // en plongeon (accepté par le serveur, corps à plat jusqu'à la retombée allongée) : comme allongé
         etat = Etat.ALLONGE;
      } else if (CompatReanimation.empeche(p) || p.isPassenger() || p.isInWater()) {
         etat = Etat.IMPOSSIBLE;
      } else if (restant > 0L) {
         etat = Etat.DELAI;
      } else {
         // s'allonger sur place est permis ; le plongeon (même touche en se déplaçant) a son propre délai
         restant = AllongeClient.plongeonRestantMs();
         etat = restant > 0L ? Etat.RECHARGE : Etat.PRET;
      }

      float ox = Formes.caler(GAUCHE), oy = Formes.caler(h - HAUT);
      float px = Formes.pixel();
      Matrix4f m = g.pose().last().pose();
      VertexConsumer vc = Formes.tampon(g);
      int silhouette, barre;
      float plein = 1f, opacite;
      switch (etat) {
         case PRET -> {
            silhouette = Palette.TEXTE;
            barre = Palette.TEXTE;
            opacite = 0.65f;
         }
         case DELAI -> {
            long total = Math.max(Allonge.delaiRallongerMs(), restant);
            plein = total <= 0L ? 1f : 1f - (float) restant / total;
            silhouette = Palette.ATTENUE;
            barre = Palette.ALERTE;
            opacite = 0.95f;
         }
         case RECHARGE -> {
            long total = Math.max(Allonge.delaiPlongeonMs(), restant);
            plein = total <= 0L ? 1f : 1f - (float) restant / total;
            silhouette = Palette.TEXTE;
            barre = Palette.TEXTE;
            opacite = 0.65f;
         }
         case ALLONGE -> {
            silhouette = Palette.OK;
            barre = Palette.OK;
            opacite = 0.92f;
         }
         default -> {
            silhouette = Palette.ATTENUE;
            barre = Palette.DANGER;
            opacite = 0.9f;
         }
      }
      // ombre d'un pixel d'écran, puis la silhouette (couleur opaque : les traits qui se chevauchent ne foncent pas)
      soldat(vc, m, ox + px, oy + px, Palette.argb(0x000000, OMBRE_ALPHA * a), px);
      soldat(vc, m, ox, oy, Palette.argb(silhouette, a), px);
      // barre penchée sous la silhouette, centrée sur elle
      float x0 = Formes.caler(ox + (15f - L_BARRE) / 2f), y0 = Formes.caler(oy + 5f + ECART_BARRE);
      float x1 = x0 + L_BARRE, y1 = y0 + H_BARRE, r = (y0 + y1) / 2f;
      float xp = x0 + L_BARRE * Math.max(0f, Math.min(1f, plein));
      Formes.parallelogramme(vc, m, x0 - px, y0 - px, x1 + px, y1 + px, PENTE, r, Palette.argb(0x000000, OMBRE_ALPHA * a), px);
      Formes.parallelogramme(vc, m, xp, y0, x1, y1, PENTE, r, Palette.argb(0xFFFFFF, PISTE_ALPHA * a), px);
      Formes.parallelogramme(vc, m, x0, y0, xp, y1, PENTE, r, Palette.argb(barre, opacite * a), px);
      g.flush();

      if (etat == Etat.DELAI || etat == Etat.RECHARGE) {
         long s = Math.max(1L, (restant + 999L) / 1000L);
         if (s != secondesAffichees) {
            SECONDES.texte(s + " s");
            secondesAffichees = s;
         }
         float bx = Formes.caler(ox + (15f - L_BARRE) / 2f) + L_BARRE + ECART_TEXTE;
         float by = Formes.caler(oy + 5f + ECART_BARRE) + H_BARRE / 2f;
         SECONDES.ajouter(g, bx, by, Palette.argb(Palette.ATTENUE, a), TexteHud.GAUCHE, true);
         g.flush();
      }
   }

   /**
    * Soldat à plat ventre, tête relevée, coude au sol, arme en joue (15 x 5,5 unités depuis le coin haut gauche
    * {@code (ox, oy)}), mêmes cotes que la maquette validée du 27/11.
    */
   private static void soldat(VertexConsumer vc, Matrix4f m, float ox, float oy, int c, float px) {
      capsule(vc, m, ox + 0.8f, oy + 4.4f, ox + 5.6f, oy + 3.9f, 0.55f, c, px);    // jambe arrière
      capsule(vc, m, ox + 1.1f, oy + 3.55f, ox + 5.6f, oy + 3.4f, 0.5f, c, px);    // jambe avant
      capsule(vc, m, ox + 0.35f, oy + 4.6f, ox + 0.9f, oy + 3.7f, 0.38f, c, px);   // pieds
      capsule(vc, m, ox + 5.4f, oy + 3.75f, ox + 9.3f, oy + 3.25f, 1.0f, c, px);   // buste
      Formes.disque(vc, m, ox + 10.85f, oy + 2.35f, 1.2f, c, px);                   // tête relevée
      capsule(vc, m, ox + 9.1f, oy + 3.5f, ox + 9.95f, oy + 4.6f, 0.42f, c, px);   // bras jusqu'au coude
      capsule(vc, m, ox + 9.95f, oy + 4.6f, ox + 11.9f, oy + 3.75f, 0.38f, c, px); // avant-bras
      capsule(vc, m, ox + 10.2f, oy + 3.45f, ox + 14.7f, oy + 3.45f, 0.3f, c, px); // fusil
      capsule(vc, m, ox + 9.7f, oy + 3.6f, ox + 10.7f, oy + 3.85f, 0.45f, c, px);  // crosse
      capsule(vc, m, ox + 12.3f, oy + 3.45f, ox + 12.45f, oy + 4.3f, 0.24f, c, px); // chargeur
   }

   /** Trait épais aux bouts arrondis, de rayon r. */
   private static void capsule(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float r, int c, float px) {
      Formes.segment(vc, m, x0, y0, x1, y1, 2f * r, c);
      Formes.disque(vc, m, x0, y0, r, c, px);
      Formes.disque(vc, m, x1, y1, r, c, px);
   }
}
