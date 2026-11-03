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
 * Petit disque de verre (rayon {@link #R}) avec un soldat allongé, à {@link #GAUCHE} du bord gauche, au milieu des cases
 * de la barre d'objets DayZ ({@link #MILIEU_BAS} du bas, comme l'usure à droite) ; rendus :
 * {@code rendus_03nov/armes-objets/allonge_*.png}.
 * <ul>
 *    <li><b>prêt</b> : soldat clair, anneau plein clair ;</li>
 *    <li><b>délai</b> (on vient de se relever, ou le serveur a refusé) : soldat grisé, anneau ambre qui se remplit de midi
 *    dans le sens horaire, secondes restantes à droite ;</li>
 *    <li><b>impossible</b> (à terre, en train de fouiller ou réanimer, en véhicule, dans l'eau) : soldat grisé, trait
 *    rouge ;</li>
 *    <li><b>allongé</b>, ou en plein plongeon (de l'élan à la retombée) : soldat et anneau verts.</li>
 * </ul>
 * L'état est celui que le jeu du joueur applique déjà à la touche (AllongeClient : même délai que le serveur, réglage
 * {@code delai_rallonger} envoyé par Forge, et attente annoncée par un refus du serveur) : aucun paquet de plus, quelques
 * lectures par image. Caché en F1, avec F3, en spectateur et derrière nos écrans plein écran (EcransPleins de
 * l'interface annule les surcouches). Formes puis texte, un vidage chacun ; aucune allocation par image.
 */
public final class HudAllonge {
   private HudAllonge() {
   }

   /** Rayon du disque de verre, rayon et épaisseur de l'anneau, cotes depuis le bord gauche et le bas. */
   static final float R = 9.5f, R_ANNEAU = 8f, EP_ANNEAU = 1.5f, GAUCHE = 8f, MILIEU_BAS = 12f, ECART_TEXTE = 3f;
   static final int VERRE_ALPHA = 150;
   /**
    * Soldat grisé pendant le délai : l'atténué mêlé au verre, opaque (le soldat est fait de plusieurs traits qui se
    * chevauchent : une couleur transparente foncerait leurs jointures).
    */
   private static final int GRIS_DELAI = Palette.mix(Palette.ATTENUE, Palette.VERRE, 0.4f);

   private enum Etat { PRET, DELAI, IMPOSSIBLE, ALLONGE }

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
         etat = Etat.PRET;
      }

      float cx = Formes.caler(GAUCHE + R), cy = Formes.caler(h - MILIEU_BAS);
      float px = Formes.pixel();
      Matrix4f m = g.pose().last().pose();
      VertexConsumer vc = Formes.tampon(g);
      Formes.disque(vc, m, cx, cy, R, Palette.argb(Palette.VERRE, (int) (VERRE_ALPHA * a)), px);
      Formes.arc(vc, m, cx, cy, R - px, R, 0f, 1f, Palette.argb(0xFFFFFF, (int) (Palette.BORD_ALPHA * a)), px);
      float r0 = R_ANNEAU - EP_ANNEAU / 2f, r1 = R_ANNEAU + EP_ANNEAU / 2f;
      Formes.arc(vc, m, cx, cy, r0, r1, 0f, 1f, Palette.argb(0xFFFFFF, 0.16f * a), px);
      int soldat;
      switch (etat) {
         case PRET -> {
            Formes.arc(vc, m, cx, cy, r0, r1, 0f, 1f, Palette.argb(Palette.TEXTE, 0.55f * a), px);
            soldat = Palette.argb(Palette.TEXTE, a);
         }
         case DELAI -> {
            long total = Math.max(Allonge.delaiRallongerMs(), restant);
            float fait = total <= 0L ? 1f : 1f - (float) restant / total;
            Formes.arc(vc, m, cx, cy, r0, r1, 0f, fait, Palette.argb(Palette.ALERTE, 0.95f * a), px);
            soldat = Palette.argb(GRIS_DELAI, a);
         }
         case ALLONGE -> {
            Formes.arc(vc, m, cx, cy, r0, r1, 0f, 1f, Palette.argb(Palette.OK, 0.9f * a), px);
            soldat = Palette.argb(Palette.OK, a);
         }
         default -> soldat = Palette.argb(Palette.TRES_ATTENUE, a);
      }
      soldat(vc, m, cx - 0.4f, cy - 0.3f, soldat, px);
      if (etat == Etat.IMPOSSIBLE) {
         Formes.segment(vc, m, cx - 5.6f, cy + 5.6f, cx + 5.6f, cy - 5.6f, 1.4f, Palette.argb(Palette.DANGER, 0.9f * a));
      }
      g.flush();

      if (etat == Etat.DELAI) {
         long s = Math.max(1L, (restant + 999L) / 1000L);
         if (s != secondesAffichees) {
            SECONDES.texte(s + " s");
            secondesAffichees = s;
         }
         SECONDES.ajouter(g, cx + R + ECART_TEXTE, cy, Palette.argb(Palette.ATTENUE, a), TexteHud.GAUCHE, true);
         g.flush();
      }
   }

   /** Soldat allongé à plat ventre, tête à droite, arme vers l'avant (13 x 5 unités), mêmes cotes que le rendu. */
   private static void soldat(VertexConsumer vc, Matrix4f m, float cx, float cy, int c, float px) {
      capsule(vc, m, cx - 5.8f, cy + 1.6f, cx - 1.6f, cy + 1.1f, 0.8f, c, px);   // jambe du dessous
      capsule(vc, m, cx - 5.4f, cy + 0.7f, cx - 1.6f, cy + 0.6f, 0.75f, c, px);  // jambe du dessus
      capsule(vc, m, cx - 1.6f, cy + 0.7f, cx + 2.4f, cy + 0.2f, 1.35f, c, px);  // buste
      Formes.disque(vc, m, cx + 3.9f, cy - 1.3f, 1.5f, c, px);                    // tête
      capsule(vc, m, cx + 2.0f, cy + 1.2f, cx + 4.4f, cy + 1.9f, 0.55f, c, px);  // bras
      capsule(vc, m, cx + 3.6f, cy + 1.0f, cx + 7.0f, cy + 1.0f, 0.4f, c, px);   // arme
   }

   /** Trait épais aux bouts arrondis, de rayon r. */
   private static void capsule(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float r, int c, float px) {
      Formes.segment(vc, m, x0, y0, x1, y1, 2f * r, c);
      Formes.disque(vc, m, x0, y0, r, c, px);
      Formes.disque(vc, m, x1, y1, r, c, px);
   }
}
