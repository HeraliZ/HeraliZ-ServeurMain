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
 * (touches, animation, bruit).
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
   }
}
