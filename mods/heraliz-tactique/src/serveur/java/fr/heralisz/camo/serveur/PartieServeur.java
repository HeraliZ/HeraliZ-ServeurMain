package fr.heralisz.camo.serveur;

import fr.heralisz.camo.network.ApplyCamoPacket;
import fr.heralisz.camo.network.DecorPacket;
import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heralisz_camo (docs/METHODE_CLIENT_SERVEUR.md §4, §6) : application des camos et des décorations reçues
 * des joueurs (ReceptionServeur). Le compteur de kills des armes skinnées (KillCounter) et la reprise des objets retirés
 * (CamoMigration) s'inscrivent seuls par leur @Mod.EventBusSubscriber(modid = "heralisz_camo").
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      ApplyCamoPacket.RECEPTION_SERVEUR.brancher(ReceptionServeur::appliquerCamo);
      DecorPacket.RECEPTION_SERVEUR.brancher(ReceptionServeur::decor);
   }
}
