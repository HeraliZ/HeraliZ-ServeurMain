package fr.heraliz.tactique.client;

import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.client.allonge.AllongeClient;
import fr.heraliz.tactique.client.allonge.RamperSansArme;
import fr.heraliz.tactique.client.couteaux.CouteauxClient;
import fr.heraliz.tactique.client.onglet.PanneauOnglet;
import fr.heraliz.tactique.client.son.SonLointainClient;
import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * Partie client du mod porteur : le panneau de filtres de l'onglet créatif commun ; s'allonger (touche, délai, message) ;
 * allongé sans arme à plat comme avec une arme (Player Animator) ; tirs silencieux lointains ; coups des couteaux TaCZ
 * (touches, animation, bruit) ; « Jeter cet objet ? » des objets à vie (EcranJeter) ;
 * hitbox « en morceaux » des joueurs avec F3+B (HitboxCorpsRendu) ; touche « Décharger l'arme » (DechargerClient) ;
 * anneau des soins et des grenades autour du réticule (HudSoins, refonte des HUD du 25/10).
 */
public final class ClientTactique implements Partie {
   @Override
   public void init(IEventBus busMod) {
      PanneauOnglet.installer(HeralizTactique.TAB, HeralizTactique.ONGLET, "onglet.heraliz_tactique.titre", "onglet.heraliz_tactique.tous");
      AllongeClient.brancher(busMod);
      if (ModList.get().isLoaded("playeranimator")) {
         RamperSansArme.brancher(busMod);
      }
      SonLointainClient.brancher();
      CouteauxClient.brancher();
      fr.heraliz.tactique.client.avie.EcranJeter.brancher();
      fr.heraliz.tactique.client.hitbox.HitboxCorpsRendu.brancher();
      fr.heraliz.tactique.armurier.EcranArmurier.brancher();
      fr.heraliz.tactique.client.decharger.DechargerClient.brancher(busMod);
      // anneau des soins et des grenades (refonte du 25/10, kit HUD du noyau) : remplace UsingProgressOverlay
      fr.heraliz.tactique.client.soins.HudSoins.brancher(busMod);
      // munitions de l'arme en main (26/10) : remplace l'interface d'arme de TaCZ
      fr.heraliz.tactique.client.hud.HudMunitions.brancher(busMod);
   }
}
