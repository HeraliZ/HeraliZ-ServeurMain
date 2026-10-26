package fr.heraliz.boss.client;

import fr.heraliz.boss.HeralizBoss;
import fr.heraliz.boss.client.onglet.PanneauOnglet;
import fr.heraliz.boss.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client du mod porteur : le panneau de filtres de l'onglet « HeraliZ · Boss ». */
public final class ClientBoss implements Partie {
   @Override
   public void init(IEventBus busMod) {
      PanneauOnglet.installer(HeralizBoss.TAB, HeralizBoss.ONGLET, "onglet.heraliz_boss.titre", "onglet.heraliz_boss.tous");
   }
}
