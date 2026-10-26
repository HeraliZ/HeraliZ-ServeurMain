package fr.heraliz.clansetbases.client;

import fr.heraliz.clansetbases.HeralizClansetbases;
import fr.heraliz.clansetbases.client.onglet.PanneauOnglet;
import fr.heraliz.clansetbases.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client du mod porteur : le panneau de filtres de l'onglet créatif commun. */
public final class ClientClansetbases implements Partie {
   @Override
   public void init(IEventBus busMod) {
      PanneauOnglet.installer(HeralizClansetbases.TAB, HeralizClansetbases.ONGLET, "onglet.heraliz_clansetbases.titre", "onglet.heraliz_clansetbases.tous");
   }
}
