package fr.heraliz.tactique.serveur;

import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie serveur du mod porteur : les règles de « s'allonger » (AllongeServeur). */
public final class ServeurTactique implements Partie {
   @Override
   public void init(IEventBus busMod) {
      AllongeServeur.brancher();
   }
}
