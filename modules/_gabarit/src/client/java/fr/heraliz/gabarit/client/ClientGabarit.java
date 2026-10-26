package fr.heraliz.gabarit.client;

import fr.heraliz.gabarit.HeralizGabarit;
import fr.heraliz.gabarit.client.onglet.PanneauOnglet;
import fr.heraliz.gabarit.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client du mod porteur : le panneau de filtres de l'onglet créatif commun. */
public final class ClientGabarit implements Partie {
   @Override
   public void init(IEventBus busMod) {
      PanneauOnglet.installer(HeralizGabarit.TAB, HeralizGabarit.ONGLET, "onglet.heraliz_gabarit.titre", "onglet.heraliz_gabarit.tous");
   }
}
