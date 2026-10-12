package fr.heraliz.tactique.client;

import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.client.allonge.AllongeClient;
import fr.heraliz.tactique.client.onglet.PanneauOnglet;
import fr.heraliz.tactique.client.son.SonLointainClient;
import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client du mod porteur : le panneau de filtres de l'onglet créatif commun ; s'allonger (touche, délai, message) ;
 * tirs silencieux lointains.
 */
public final class ClientTactique implements Partie {
   @Override
   public void init(IEventBus busMod) {
      PanneauOnglet.installer(HeralizTactique.TAB, HeralizTactique.ONGLET, "onglet.heraliz_tactique.titre", "onglet.heraliz_tactique.tous");
      AllongeClient.brancher(busMod);
      SonLointainClient.brancher();
   }
}
