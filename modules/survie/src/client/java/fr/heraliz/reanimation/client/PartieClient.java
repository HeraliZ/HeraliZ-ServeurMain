package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.network.SyncPacket;
import fr.heraliz.reanimation.network.UnlocksPacket;
import fr.heraliz.reanimation.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de Reanimation (docs/METHODE_CLIENT_SERVEUR.md §7) : branche les paquets reçus. Poses, animations, HUD,
 * écrans et touches s'inscrivent seuls (ClientSetup, ClientEvents : {@code value = Dist.CLIENT}).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      SyncPacket.RECEPTION_CLIENT.brancher(ClientDowned::receive);
      UnlocksPacket.RECEPTION_CLIENT.brancher(ClientUnlocks::receive);
   }
}
