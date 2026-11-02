package fr.heraliz.reanimation.serveur;

import fr.heraliz.reanimation.core.DownedManagerServeur;
import fr.heraliz.reanimation.network.ActionPacket;
import fr.heraliz.reanimation.network.ActionPacketServeur;
import fr.heraliz.reanimation.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de Reanimation (docs/METHODE_CLIENT_SERVEUR.md §4) : état à terre, saignement, fouille, réanimation,
 * exécutions, déblocages, commandes. Les écouteurs serveur (ServerEventsServeur) s'inscrivent seuls par leur
 * {@code @EventBusSubscriber(modid = "reanimation")}.
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      DownedManagerServeur.installer();
      ActionPacket.RECEPTION_SERVEUR.brancher(ActionPacketServeur::traiter);
   }
}
